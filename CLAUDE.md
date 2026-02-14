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
docker-compose up -d              # PostgreSQL 시작
./gradlew bootRun                 # 애플리케이션 실행
./gradlew clean build             # 클린 빌드
```

### 테스트
```bash
./gradlew test                                                      # 전체 테스트
./gradlew test --tests "TransferServiceTest"                        # 특정 클래스
./gradlew test --tests "TransferServiceTest.동시 이체 시 deadlock 방지" # 특정 메서드
```

### 커버리지
```bash
./gradlew koverHtmlReport  # → build/reports/kover/html/index.html
./gradlew koverLog         # 콘솔 출력
./gradlew koverVerify      # 검증 (최소 70%)
```

## 📐 아키텍처

### Hexagonal Architecture (Port-Adapter Pattern)

```
adapter/in/web/              # REST API, ExceptionHandler, DTO
application/service/         # Use Cases, 트랜잭션 관리
domain/
  ├── model/                 # 순수 비즈니스 로직 (suspend 없음)
  ├── port/in/               # Input Ports (Use Cases)
  ├── port/out/              # Output Ports (Repositories)
  └── exception/             # Domain Exceptions (sealed class)
adapter/out/persistence/     # Repository 구현, Entity, R2DBC
infrastructure/              # 기술 인프라 (Config, Filter)
```

### 레이어별 Suspend 사용 규칙

| 레이어 | suspend | 이유 |
|--------|:------:|------|
| Domain Models | ❌ | 순수 비즈니스 로직, I/O 무관 |
| Domain Ports | ✅ | I/O 경계 정의 |
| Application Services | ✅ | Port 조합, 트랜잭션 관리 |
| Adapters | ✅ | 실제 I/O 수행 |
| Controllers | ✅ | WebFlux 자동 변환 |

**참고**: `docs/SUSPEND_BEST_PRACTICES.md` 참조

## 🎯 핵심 설계 패턴

### 1. Optimistic Locking (동시성 제어)
```kotlin
@Version val version: Long = 0
```
- 동시 수정 감지 시 `OptimisticLockException` (409)
- 클라이언트 재조회 후 재시도

### 2. Idempotency (멱등성)
```kotlin
// Fast Path: 트랜잭션 밖에서 중복 체크 (성능)
val existing = transferRepository.findByIdempotencyKey(key)
if (existing != null) return existing

// Double-Check: 트랜잭션 내 재확인 (race condition 방지)
transactionExecutor.execute {
    val recheck = transferRepository.findByIdempotencyKey(key)
    if (recheck != null) throw DuplicateTransferException()
}
```
- `Idempotency-Key` 헤더 필수

### 3. Deadlock Prevention (교착상태 방지)
```kotlin
val sortedIds = listOf(fromAccountId, toAccountId).sorted()
val accounts = accountRepository.findByIdsForUpdate(sortedIds)
```
- 계좌 ID 정렬 → 동일 순서 잠금 획득 → 순환 대기 차단

### 4. Transactional Pattern
```kotlin
// ✅ TransactionalOperator.executeAndAwait (권장)
transactionExecutor.execute { /* ... */ }

// ❌ @Transactional (R2DBC + Coroutine 환경 불안정)
```

### 5. Exception Translation
```kotlin
sealed class DomainException(message: String) : RuntimeException(message)

@ExceptionHandler(DomainException::class)
fun handle(e: DomainException) = when (e) {
    is AccountNotFoundException -> NOT_FOUND to "ACCOUNT_NOT_FOUND"
    is InsufficientBalanceException -> BAD_REQUEST to "INSUFFICIENT_BALANCE"
    // 컴파일 타임 exhaustive check
}
```

## 📋 API 엔드포인트

4개의 주요 엔드포인트 제공:
- `POST /api/accounts` - 계좌 생성
- `GET /api/accounts/{id}` - 계좌 조회
- `POST /api/accounts/{id}/deposits` - 입금
- `POST /api/transfers` - 이체 (**Idempotency-Key 필수**)

### 주요 에러 코드

| HTTP | Code | 설명 |
|------|------|------|
| 400 | INSUFFICIENT_BALANCE | 잔액 부족 |
| 400 | INVALID_AMOUNT | 유효하지 않은 금액 |
| 404 | ACCOUNT_NOT_FOUND | 계좌 없음 |
| 409 | DUPLICATE_TRANSFER | 중복 이체 |
| 409 | OPTIMISTIC_LOCK_FAILED | 동시 수정 (재시도 필요) |

**상세 API 문서**: `README.md` 참조 또는 `/docs/index.html` (Swagger UI)

## 🔧 코드 작성 가이드

### Domain Models (순수 함수)
```kotlin
// ✅ GOOD: suspend 없음
fun deposit(amount: BigDecimal): Account {
    require(amount > BigDecimal.ZERO)
    return copy(balance = balance + amount)
}

// ❌ BAD: 도메인에 I/O 의존
suspend fun deposit(amount: BigDecimal): Account
```

### Port Interfaces
```kotlin
// ✅ GOOD: suspend + 도메인 모델
interface AccountRepository {
    suspend fun findById(id: Long): Account?
}

// ❌ BAD: Reactor 타입 노출
fun findById(id: Long): Mono<Account?>
```

### Flow → List 변환
```kotlin
// ✅ Adapter 경계에서 변환
override suspend fun findByAccountId(accountId: Long): List<LedgerEntry> {
    return repository.findByAccountId(accountId)  // Flow
        .map { toDomain(it) }
        .toList()  // suspend
}
```

### 새 도메인 예외 추가
1. `domain/exception/DomainException.kt`에 클래스 추가
2. `GlobalExceptionHandler.kt` when 표현식 매핑
3. 컴파일러 exhaustive check (누락 시 에러)

## 🧪 테스트 전략

| 계층 | 유형 | 도구 |
|-----|------|------|
| Domain | 단위 테스트 | 순수 함수 |
| Service | 통합 테스트 | Testcontainers + PostgreSQL |
| Controller | API 테스트 | WebTestClient |

**동시성 테스트**: `runBlocking` + `async/awaitAll` 패턴

## 📚 참고 문서

- **Suspend Best Practices**: `docs/SUSPEND_BEST_PRACTICES.md`
- **프로젝트 개요**: `README.md`
- **GitHub Issues**: https://github.com/seokrae-labs/account-ledger-service/issues

## 🚨 중요 원칙

### Issue-Driven Development
**모든 코드 변경은 반드시 GitHub Issue 생성 후 진행**

```bash
gh issue create --title "..." --label "..."          # 1. Issue 생성
git checkout -b feature/issue-{번호}-{설명}           # 2. 브랜치
git commit -m "feat: 설명 (#이슈번호)"                # 3. 커밋
gh pr create --body "Closes #{이슈번호}"             # 4. PR
```

### 패키지 배치 기준

| 기준 | 패키지 | 예시 |
|-----|--------|------|
| 도메인 의존 O | `adapter/in/web/` | Controller, ExceptionHandler |
| 도메인 의존 X | `infrastructure/web/` | RequestLoggingFilter |
| 비즈니스 로직 | `domain/model/` | Account, Transfer |
| I/O 경계 | `domain/port/` | Ports (interfaces) |

### Dispatcher 사용 금지
```kotlin
// ❌ BAD: R2DBC는 이미 non-blocking
withContext(Dispatchers.IO) { repository.save() }

// ✅ GOOD: 기본 Reactor event loop
suspend fun save(): Account = repository.save(account)
```

---

**마지막 업데이트**: 2026-02-14
**커버리지**: 93.53%
**상태**: ✅ 전체 개발 완료
