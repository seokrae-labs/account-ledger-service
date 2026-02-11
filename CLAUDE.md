# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Account Ledger & Transfer Service

실시간 계좌 잔액 관리와 안전한 이체 처리를 제공하는 Reactive 원장 서비스

## 🏗️ 기술 스택

- **Language**: Kotlin 1.9.25 + Coroutines
- **Framework**: Spring Boot 3.4.2
- **Reactive Stack**: WebFlux + R2DBC
- **Database**: PostgreSQL 16
- **Build**: Gradle 8.11.1
- **Testing**: JUnit 5 + Testcontainers
- **Coverage**: Kover (최소 70%, 현재 93%+)

## 🛠️ 주요 명령어

### 환경 및 실행
```bash
# PostgreSQL 시작
docker-compose up -d

# 애플리케이션 실행
./gradlew bootRun

# 클린 빌드
./gradlew clean build
```

### 테스트
```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "TransferServiceTest"

# 특정 테스트 메서드 실행
./gradlew test --tests "TransferServiceTest.동시 이체 시 deadlock 방지"
```

### 커버리지
```bash
# HTML 리포트 생성
./gradlew koverHtmlReport
# → build/reports/kover/html/index.html

# 콘솔 출력
./gradlew koverLog

# 검증 (최소 70%)
./gradlew koverVerify
```

## 📐 아키텍처

### Hexagonal Architecture (Port-Adapter Pattern)

```
adapter/in/web/                    # Web Adapter (REST API)
├── AccountController.kt           # 계좌 API
├── TransferController.kt          # 이체 API
├── GlobalExceptionHandler.kt      # 예외 처리 (도메인 예외 → HTTP)
└── dto/                           # Request/Response DTO

application/service/               # Application Services (Use Cases)
├── AccountService.kt              # 계좌 생성/조회/입금
└── TransferService.kt             # 이체 처리

domain/                            # Domain Layer (Core Business Logic)
├── model/                         # Domain Models (순수 함수, suspend 없음)
│   ├── Account.kt                 # 계좌 (deposit/withdraw)
│   ├── LedgerEntry.kt             # 원장 엔트리
│   └── Transfer.kt                # 이체
├── port/                          # Ports (suspend interfaces)
│   ├── in/                        # Input Ports (Use Cases)
│   └── out/                       # Output Ports (Repositories)
└── exception/                     # Domain Exceptions (sealed class)

adapter/out/persistence/           # Persistence Adapter
├── adapter/                       # Repository 구현
├── entity/                        # Database Entities
└── repository/                    # R2DBC Repositories

infrastructure/                    # Technical Infrastructure
├── config/                        # Configuration (R2DBC, UseCase)
└── web/                           # Web Infrastructure (RequestLoggingFilter)
```

### 레이어별 Suspend 사용 규칙

| 레이어 | suspend 사용 | 이유 |
|--------|:----------:|------|
| **Domain Models** | ❌ NO | 순수 비즈니스 로직, I/O 무관 |
| **Domain Ports** | ✅ YES | I/O 경계 정의 (suspend interface) |
| **Application Services** | ✅ YES | Port 조합, 트랜잭션 관리 |
| **Adapters** | ✅ YES | 실제 I/O 수행 (DB, Network) |
| **Web Controllers** | ✅ YES | Spring WebFlux 자동 변환 |

**참고**: 상세한 suspend 함수 분석은 `docs/SUSPEND_BEST_PRACTICES.md` 참조

## 🎯 핵심 설계 패턴

### 1. Optimistic Locking (동시성 제어)
```kotlin
@Version
val version: Long = 0  // 수정 시마다 자동 증가
```
- 동시 수정 감지 시 `OptimisticLockException` (409 Conflict)
- 클라이언트가 최신 데이터 재조회 후 재시도

### 2. Idempotency (멱등성 보장)
```kotlin
// Fast Path: 트랜잭션 밖에서 중복 체크 (성능 최적화)
val existing = transferRepository.findByIdempotencyKey(key)
if (existing != null) return existing

// Double-Check: 트랜잭션 내 재확인 (race condition 방지)
transactionExecutor.execute {
    val recheck = transferRepository.findByIdempotencyKey(key)
    if (recheck != null) throw DuplicateTransferException()
    // 이체 처리
}
```
- `Idempotency-Key` 헤더 필수
- 중복 요청 시 동일 결과 반환

### 3. Deadlock Prevention (교착상태 방지)
```kotlin
// 계좌 ID 정렬 → 항상 동일한 순서로 잠금 획득
val sortedIds = listOf(fromAccountId, toAccountId).sorted()
val accounts = accountRepository.findByIdsForUpdate(sortedIds)
```
- `FOR UPDATE` + `ORDER BY id` 조합
- 순환 대기 상태 원천 차단

### 4. Transactional Pattern (명시적 트랜잭션)
```kotlin
// ✅ TransactionalOperator.executeAndAwait (권장)
transactionExecutor.execute {
    // 트랜잭션 범위 명확
}

// ❌ @Transactional (R2DBC + Coroutine 환경에서 불안정)
```
- Hexagonal Architecture 준수 (도메인이 Spring에 무의존)
- R2DBC + Coroutine context 전파 문제 회피

### 5. Exception Translation (예외 변환)
```kotlin
// Domain Exception (sealed class)
sealed class DomainException(message: String) : RuntimeException(message)

// GlobalExceptionHandler (sealed when 패턴)
@ExceptionHandler(DomainException::class)
fun handleDomainException(e: DomainException): ResponseEntity<ErrorResponse> {
    val (status, errorCode) = when (e) {
        is AccountNotFoundException -> NOT_FOUND to "ACCOUNT_NOT_FOUND"
        is InsufficientBalanceException -> BAD_REQUEST to "INSUFFICIENT_BALANCE"
        // ... 컴파일 타임 exhaustive check
    }
}
```

## 📋 API 엔드포인트

| Method | Path | Headers | 설명 |
|--------|------|---------|------|
| POST | `/api/accounts` | - | 계좌 생성 |
| GET | `/api/accounts/{id}` | - | 계좌 조회 |
| POST | `/api/accounts/{id}/deposits` | - | 입금 |
| POST | `/api/transfers` | `Idempotency-Key` (필수) | 이체 |

### 주요 에러 코드

| HTTP Status | Error Code | 설명 |
|-------------|-----------|------|
| 400 | `INSUFFICIENT_BALANCE` | 잔액 부족 |
| 400 | `INVALID_AMOUNT` | 유효하지 않은 금액 |
| 404 | `ACCOUNT_NOT_FOUND` | 계좌 없음 |
| 409 | `DUPLICATE_TRANSFER` | 중복 이체 (동일 Idempotency-Key) |
| 409 | `OPTIMISTIC_LOCK_FAILED` | 동시 수정 감지 (재시도 필요) |

**참고**: 상세한 API 문서는 `README.md` 참조

## 🔧 코드 작성 가이드

### Domain Models (순수 함수)
```kotlin
// ✅ GOOD: 순수 함수 (suspend 없음)
fun deposit(amount: BigDecimal): Account {
    require(amount > BigDecimal.ZERO)
    return copy(balance = balance + amount)
}

// ❌ BAD: 도메인에 I/O 의존
suspend fun deposit(amount: BigDecimal): Account  // 도메인이 I/O를 알 필요 없음
```

### Port Interfaces
```kotlin
// ✅ GOOD: suspend + 도메인 모델 반환
interface AccountRepository {
    suspend fun findById(id: Long): Account?
    suspend fun save(account: Account): Account
}

// ❌ BAD: Mono/Flux 노출
interface AccountRepository {
    fun findById(id: Long): Mono<Account?>  // Reactor 타입 노출
}
```

### Flow → List 변환 (Adapter 경계)
```kotlin
// ✅ GOOD: Flow는 Adapter 내부에서 List로 변환
override suspend fun findByAccountId(accountId: Long): List<LedgerEntry> {
    return repository.findByAccountId(accountId)  // Flow<Entity>
        .map { toDomain(it) }
        .toList()  // Flow → List (suspend)
}
```

### 새 도메인 예외 추가 시
1. `domain/exception/DomainException.kt`에 예외 클래스 추가
2. `GlobalExceptionHandler.kt`의 `when` 표현식에 매핑 추가
3. 컴파일러가 exhaustive check 수행 (누락 시 컴파일 에러)

## 🧪 테스트 전략

### 테스트 계층

| 계층 | 유형 | 특징 |
|-----|------|------|
| Domain | 단위 테스트 | 순수 함수, 코루틴 불필요 |
| Service | 통합 테스트 | Testcontainers + PostgreSQL |
| Controller | API 테스트 | WebTestClient |

### 동시성 테스트 예제
```kotlin
@Test
fun `동시 입금 시 optimistic locking 동작`() = runBlocking {
    val results = (1..10).map {
        async { accountService.deposit(accountId = 1L, amount = 100.toBigDecimal()) }
    }.awaitAll()

    val account = accountRepository.findById(1L)
    assertThat(account?.balance).isEqualTo(1000.toBigDecimal())
}
```

## 📚 참고 문서

- **아키텍처 분석**: Issue #29 (GlobalExceptionHandler 패키지 배치)
- **Suspend Best Practices**: `docs/SUSPEND_BEST_PRACTICES.md`
- **프로젝트 개요**: `README.md`
- **GitHub Issues**: https://github.com/seokrae-labs/account-ledger-service/issues

## 🚨 중요 원칙

### Issue-Driven Development
**모든 코드 변경은 반드시 GitHub Issue 생성 후 진행**

```bash
# 1. Issue 생성
gh issue create --title "..." --label "..."

# 2. 브랜치 생성
git checkout -b feature/issue-{번호}-{설명}

# 3. 커밋 (Issue 번호 포함)
git commit -m "feat: 설명 (#이슈번호)"

# 4. PR 생성 (Issue 링크)
gh pr create --body "Closes #{이슈번호}"
```

### 패키지 배치 기준

| 기준 | 패키지 | 예시 |
|-----|--------|------|
| **도메인 의존 O** | `adapter/in/web/` | Controller, ExceptionHandler |
| **도메인 의존 X** | `infrastructure/web/` | RequestLoggingFilter |
| **비즈니스 로직** | `domain/model/` | Account, Transfer |
| **I/O 경계** | `domain/port/` | Ports (interfaces) |

### Dispatcher 사용 금지
```kotlin
// ❌ BAD: R2DBC는 이미 non-blocking
suspend fun save(account: Account) = withContext(Dispatchers.IO) {
    repository.save(account)
}

// ✅ GOOD: 기본 Reactor event loop 사용
suspend fun save(account: Account): Account {
    return repository.save(account)
}
```

---

**마지막 업데이트**: 2026-02-11
**커버리지**: 93.53%
**상태**: ✅ 전체 개발 완료
