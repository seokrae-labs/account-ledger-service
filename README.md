# Account Ledger & Transfer Service

> 실시간 계좌 잔액 관리와 안전한 이체 처리를 제공하는 Reactive 원장 서비스

## 주요 특징

- **Reactive Stack**: Spring WebFlux와 Kotlin Coroutines 기반의 비동기 처리
- **Hexagonal Architecture**: 도메인 중심의 계층형 아키텍처로 비즈니스 로직 분리
- **동시성 제어**: Optimistic Locking과 Deadlock Prevention으로 안전한 동시 처리
- **멱등성 보장**: Idempotency-Key 기반의 중복 이체 방지

## 기술 스택

| 카테고리 | 기술 | 버전 |
|---------|------|------|
| Language | Kotlin | 1.9.25 |
| Framework | Spring Boot | 3.4.2 |
| Reactive | WebFlux + Coroutines | 1.9.0 |
| Persistence | R2DBC + PostgreSQL | 1.0.7 / 16 |
| Build Tool | Gradle | 8.11.1 |
| JDK | OpenJDK | 21 |
| Testing | JUnit 5 + Testcontainers | 1.20.4 |
| Coverage | Kover | 0.9.4 |

## 아키텍처

본 프로젝트는 Hexagonal Architecture(포트-어댑터 패턴)를 따릅니다.

```mermaid
graph LR
    subgraph "Adapter In (Web)"
        A[AccountController]
        T[TransferController]
    end

    subgraph "Application Service"
        AS[AccountService]
        TS[TransferService]
    end

    subgraph "Domain (Core)"
        AC[Account]
        LE[LedgerEntry]
        TR[Transfer]
        P[Ports]
    end

    subgraph "Adapter Out (Persistence)"
        AA[AccountAdapter]
        LA[LedgerAdapter]
        TA[TransferAdapter]
        R[(PostgreSQL)]
    end

    A --> AS
    T --> TS
    AS --> AC
    TS --> TR
    AC --> P
    TR --> P
    P --> AA
    P --> LA
    P --> TA
    AA --> R
    LA --> R
    TA --> R
```

### 계층별 책임

- **Adapter In**: REST API 요청/응답 처리, DTO 변환
- **Application Service**: 유스케이스 조율, 트랜잭션 관리
- **Domain**: 핵심 비즈니스 로직 및 규칙
- **Adapter Out**: 데이터베이스 영속성 처리

## 시작하기

### Prerequisites

- **JDK 21** 이상
- **Docker** 및 **Docker Compose**

### 환경 설정

1. 환경변수 설정 (선택사항)
```bash
# .env 파일 생성 (기본값을 사용하려면 스킵 가능)
cp .env.example .env
# .env 파일을 편집하여 데이터베이스 자격증명 수정
```

2. PostgreSQL 실행
```bash
docker compose up -d
```

3. 애플리케이션 실행

**개발 환경 (기본)**
```bash
./gradlew bootRun
# 또는 명시적으로
./gradlew bootRun --args='--spring.profiles.active=dev'
```

**프로덕션 환경 (환경변수 사용)**
```bash
# Option 1: .env 파일 사용 (권장)
export $(cat .env | xargs) && ./gradlew bootRun --args='--spring.profiles.active=prod'

# Option 2: 직접 환경변수 설정
export DB_USERNAME=prod_user
export DB_PASSWORD=secure_password
export R2DBC_URL=r2dbc:postgresql://prod-host:5432/ledger
export JDBC_URL=jdbc:postgresql://prod-host:5432/ledger
./gradlew bootRun --args='--spring.profiles.active=prod'
```

**테스트 환경**
```bash
./gradlew test  # 자동으로 test 프로파일 적용
```

4. 접속
```
http://localhost:8080
```

### 프로파일별 설정

| 프로파일 | 용도 | 로깅 레벨 | R2DBC Pool | 특징 |
|---------|------|----------|-----------|------|
| **dev** | 로컬 개발 | DEBUG | 5-10 | Flyway clean 허용, 상세 로깅 |
| **prod** | 프로덕션 | INFO | 20-50 | 커넥션 풀 최적화, Graceful Shutdown |
| **test** | 자동화 테스트 | DEBUG | 2-5 | Testcontainers, 빠른 시작 |

### 환경변수 설정

데이터베이스 자격증명은 환경변수를 통해 외부화할 수 있습니다:

| 환경변수 | 설명 | 기본값 |
|---------|------|--------|
| `DB_USERNAME` | 데이터베이스 사용자명 | `ledger` |
| `DB_PASSWORD` | 데이터베이스 비밀번호 | `ledger123` |
| `R2DBC_URL` | R2DBC 연결 URL | `r2dbc:postgresql://localhost:5432/ledger` |
| `JDBC_URL` | JDBC 연결 URL (Flyway용) | `jdbc:postgresql://localhost:5432/ledger` |

**설정 방법:**
1. `.env.example`을 `.env`로 복사
2. `.env` 파일 수정 (이 파일은 Git에 커밋되지 않음)
3. Docker Compose가 자동으로 `.env` 파일 로드

## 운영 특징

### R2DBC Connection Pool

환경별로 최적화된 R2DBC 커넥션 풀 설정을 제공합니다.

**설정 비교:**

| 설정 | Dev | Prod | Test | 설명 |
|-----|-----|------|------|------|
| `initial-size` | 5 | 20 | 2 | 시작 시 생성되는 커넥션 수 |
| `max-size` | 10 | 50 | 5 | 최대 커넥션 수 |
| `max-idle-time` | 30m | 30m | 10m | 유휴 커넥션 유지 시간 |
| `max-lifetime` | 60m | 60m | - | 커넥션 최대 수명 |
| `max-acquire-time` | 3s | 5s | 3s | 커넥션 획득 최대 대기 시간 |
| `validation-query` | SELECT 1 | SELECT 1 | - | 커넥션 검증 쿼리 |

**설정 예제 (application-prod.yml):**
```yaml
spring:
  r2dbc:
    pool:
      enabled: true
      initial-size: 20
      max-size: 50
      max-idle-time: 30m
      max-lifetime: 60m
      max-acquire-time: 5s
      validation-query: SELECT 1
```

**Benefits:**
- 🚀 성능: 커넥션 재사용으로 응답 시간 단축
- 📊 안정성: 최대 커넥션 수 제한으로 리소스 보호
- 🔍 신뢰성: Validation query로 불량 커넥션 감지
- ⚙️ 유연성: 환경별 맞춤 설정

### Timeout Configuration

모든 레이어에서 적절한 타임아웃을 설정하여 무한 대기를 방지합니다.

**타임아웃 설정 요약:**

| 레이어 | 타임아웃 | Dev | Prod | 목적 |
|-------|---------|-----|------|------|
| HTTP Connection | `server.netty.connection-timeout` | 10s | 10s | TCP 연결 수립 타임아웃 |
| HTTP Request | `TimeoutFilter` | 60s | 60s | 전체 요청 처리 타임아웃 |
| R2DBC Statement | `spring.r2dbc.properties.statement-timeout` | 30s | 60s | 쿼리 실행 타임아웃 |
| Transaction | `TransactionalOperator` | 30s | 30s | 트랜잭션 타임아웃 |
| Connection Acquire | `spring.r2dbc.pool.max-acquire-time` | 3s | 5s | 커넥션 획득 타임아웃 |

**설정 예제:**
```yaml
# application.yml
server:
  netty:
    connection-timeout: 10s

# application-prod.yml
spring:
  r2dbc:
    properties:
      statement-timeout: 60s
    pool:
      max-acquire-time: 5s
```

**타임아웃 계층 구조:**
```
HTTP Request Timeout (60s)
  └─ Transaction Timeout (30s)
      └─ R2DBC Statement Timeout (30s/60s)
          └─ Connection Acquire Timeout (3s/5s)
```

**Benefits:**
- ⏱️ 무한 대기 방지
- 🛡️ 리소스 보호 (스레드, 커넥션)
- 🚨 빠른 실패 및 복구
- 📊 예측 가능한 응답 시간

### Graceful Shutdown

프로덕션 환경에서 애플리케이션 종료 시 진행 중인 요청을 안전하게 완료합니다.

**설정:**
```yaml
# application.yml (공통)
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

# application-prod.yml (프로덕션)
server:
  shutdown: graceful
```

**동작 방식:**
1. 종료 신호 수신 (SIGTERM)
2. 새로운 요청 거부
3. 진행 중인 요청 완료 대기 (최대 30초)
4. 타임아웃 초과 시 강제 종료
5. 리소스 정리 및 종료

**사용 사례:**
- 무중단 배포 (Blue-Green, Rolling Update)
- 컨테이너 재시작 시 데이터 손실 방지
- 이체 트랜잭션 중 강제 종료 방지

## API 엔드포인트

### 엔드포인트 요약

| Method | Path | Status | 설명 |
|--------|------|--------|------|
| POST | `/api/accounts` | 201 | 계좌 생성 |
| GET | `/api/accounts/{id}` | 200 | 계좌 조회 |
| POST | `/api/accounts/{id}/deposits` | 200 | 입금 |
| POST | `/api/transfers` | 201 | 이체 |

### 1. 계좌 생성

**Request**
```bash
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "ownerName": "John Doe"
  }'
```

**Response (201 Created)**
```json
{
  "id": 1,
  "ownerName": "John Doe",
  "balance": 0.00,
  "status": "ACTIVE",
  "version": 0,
  "createdAt": "2026-02-09T10:00:00",
  "updatedAt": "2026-02-09T10:00:00"
}
```

### 2. 계좌 조회

**Request**
```bash
curl http://localhost:8080/api/accounts/1
```

**Response (200 OK)**
```json
{
  "id": 1,
  "ownerName": "John Doe",
  "balance": 1000.00,
  "status": "ACTIVE",
  "version": 2,
  "createdAt": "2026-02-09T10:00:00",
  "updatedAt": "2026-02-09T10:05:00"
}
```

### 3. 입금

**Request**
```bash
curl -X POST http://localhost:8080/api/accounts/1/deposits \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 1000.00,
    "description": "Initial deposit"
  }'
```

**Response (200 OK)**
```json
{
  "id": 1,
  "ownerName": "John Doe",
  "balance": 1000.00,
  "status": "ACTIVE",
  "version": 1,
  "createdAt": "2026-02-09T10:00:00",
  "updatedAt": "2026-02-09T10:01:00"
}
```

### 4. 이체

**Request (Idempotency-Key 필수)**
```bash
curl -X POST http://localhost:8080/api/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "fromAccountId": 1,
    "toAccountId": 2,
    "amount": 500.00,
    "description": "Payment for service"
  }'
```

**Response (201 Created)**
```json
{
  "id": 1,
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 500.00,
  "status": "COMPLETED",
  "description": "Payment for service",
  "createdAt": "2026-02-09T10:10:00",
  "updatedAt": "2026-02-09T10:10:00"
}
```

### 에러 응답

**Error Response Structure**
```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error message",
  "timestamp": "2026-02-09T10:00:00"
}
```

**Error Codes**

| HTTP Status | Error Code | 설명 |
|-------------|-----------|------|
| 400 | `INSUFFICIENT_BALANCE` | 잔액 부족 |
| 400 | `INVALID_ACCOUNT_STATUS` | 계좌 상태 오류 (폐쇄된 계좌 등) |
| 400 | `INVALID_AMOUNT` | 유효하지 않은 금액 (음수, 0 등) |
| 400 | `INVALID_REQUEST` | 잘못된 요청 파라미터 |
| 404 | `ACCOUNT_NOT_FOUND` | 계좌를 찾을 수 없음 |
| 409 | `DUPLICATE_TRANSFER` | 중복 이체 요청 (동일한 Idempotency-Key) |
| 409 | `OPTIMISTIC_LOCK_FAILED` | 동시 수정 감지 (재시도 필요) |
| 500 | `INTERNAL_ERROR` | 내부 서버 오류 |

## 핵심 설계 패턴

### 1. Optimistic Locking

데이터베이스의 `@Version` 컬럼을 활용한 낙관적 잠금으로 동시성을 제어합니다.

```kotlin
// Account 엔티티
@Version
val version: Long = 0
```

- 계좌 수정 시 버전 체크
- 버전 불일치 시 `OptimisticLockException` 발생 (409 Conflict)
- 클라이언트는 최신 데이터를 다시 조회하여 재시도

### 2. Idempotency (멱등성)

이체 API는 `Idempotency-Key` 헤더를 통해 중복 처리를 방지합니다.

**Two-Phase Check**

1. **Fast Path**: 트랜잭션 밖에서 완료된 이체 조회 (성능 최적화)
2. **Double-Check**: 트랜잭션 내에서 재확인 (Race Condition 방지)

```kotlin
// Fast path: 트랜잭션 밖
val existing = transferRepository.findByIdempotencyKey(idempotencyKey)
if (existing != null && existing.status == COMPLETED) {
    return existing // 멱등 응답
}

// Double-check: 트랜잭션 내
@Transactional
suspend fun executeTransfer(...) {
    val recheck = transferRepository.findByIdempotencyKey(idempotencyKey)
    if (recheck != null) throw DuplicateTransferException()
    // 이체 처리
}
```

### 3. Deadlock Prevention

이체 시 두 계좌를 동시에 잠그는 과정에서 발생할 수 있는 교착 상태를 방지합니다.

**정렬 기반 잠금 순서 보장**

```kotlin
// 항상 작은 ID → 큰 ID 순서로 잠금
val (firstId, secondId) = if (fromAccountId < toAccountId) {
    fromAccountId to toAccountId
} else {
    toAccountId to fromAccountId
}

val first = accountRepository.findByIdForUpdate(firstId)
val second = accountRepository.findByIdForUpdate(secondId)
```

- 모든 트랜잭션이 동일한 순서로 잠금 획득
- 순환 대기 상태 원천 차단

## 테스트

### 실행

```bash
# 전체 테스트 실행
./gradlew test

# 커버리지 리포트 생성 (HTML)
./gradlew koverHtmlReport
# → build/reports/kover/html/index.html

# 커버리지 검증 (최소 70%)
./gradlew koverVerify
```

### 커버리지

- **현재 커버리지**: 93.53%
- **최소 요구사항**: 70%
- **제외 대상**: Configuration 클래스, DTO, Entity

### 테스트 구성

| 계층 | 파일 수 | 설명 |
|-----|--------|------|
| Domain | 3 | Account, LedgerEntry, Transfer 단위 테스트 |
| Service | 4 | AccountService, TransferService 통합 테스트 |
| Controller | 2 | AccountController, TransferController API 테스트 |

## 프로젝트 구조

```
account-ledger-service/
├── src/
│   ├── main/
│   │   ├── kotlin/com/labs/ledger/
│   │   │   ├── adapter/
│   │   │   │   ├── in/web/
│   │   │   │   │   ├── AccountController.kt
│   │   │   │   │   ├── TransferController.kt
│   │   │   │   │   ├── GlobalExceptionHandler.kt
│   │   │   │   │   └── dto/
│   │   │   │   │       ├── AccountDto.kt
│   │   │   │   │       ├── TransferDto.kt
│   │   │   │   │       └── ErrorResponse.kt
│   │   │   │   └── out/persistence/
│   │   │   │       ├── AccountPersistenceAdapter.kt
│   │   │   │       ├── LedgerPersistenceAdapter.kt
│   │   │   │       ├── TransferPersistenceAdapter.kt
│   │   │   │       ├── entity/
│   │   │   │       │   ├── AccountEntity.kt
│   │   │   │       │   ├── LedgerEntryEntity.kt
│   │   │   │       │   └── TransferEntity.kt
│   │   │   │       └── repository/
│   │   │   │           ├── R2dbcAccountRepository.kt
│   │   │   │           ├── R2dbcLedgerRepository.kt
│   │   │   │           └── R2dbcTransferRepository.kt
│   │   │   ├── application/
│   │   │   │   ├── service/
│   │   │   │   │   ├── AccountService.kt
│   │   │   │   │   └── TransferService.kt
│   │   │   │   └── port/
│   │   │   │       ├── in/
│   │   │   │       │   ├── CreateAccountUseCase.kt
│   │   │   │       │   ├── GetAccountUseCase.kt
│   │   │   │       │   ├── DepositUseCase.kt
│   │   │   │       │   └── TransferUseCase.kt
│   │   │   │       └── out/
│   │   │   │           ├── LoadAccountPort.kt
│   │   │   │           ├── SaveAccountPort.kt
│   │   │   │           ├── LoadLedgerPort.kt
│   │   │   │           ├── SaveLedgerPort.kt
│   │   │   │           ├── LoadTransferPort.kt
│   │   │   │           └── SaveTransferPort.kt
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   ├── Account.kt
│   │   │   │   │   ├── LedgerEntry.kt
│   │   │   │   │   └── Transfer.kt
│   │   │   │   └── exception/
│   │   │   │       ├── AccountNotFoundException.kt
│   │   │   │       ├── InsufficientBalanceException.kt
│   │   │   │       ├── InvalidAccountStatusException.kt
│   │   │   │       ├── InvalidAmountException.kt
│   │   │   │       ├── DuplicateTransferException.kt
│   │   │   │       └── OptimisticLockException.kt
│   │   │   ├── infrastructure/
│   │   │   │   └── config/
│   │   │   │       └── R2dbcConfiguration.kt
│   │   │   └── AccountLedgerServiceApplication.kt
│   │   └── resources/
│   │       ├── application.yml
│   │       └── schema.sql
│   └── test/
│       └── kotlin/com/labs/ledger/
│           ├── domain/model/
│           │   ├── AccountTest.kt
│           │   ├── LedgerEntryTest.kt
│           │   └── TransferTest.kt
│           ├── application/service/
│           │   ├── AccountServiceTest.kt
│           │   ├── TransferServiceTest.kt
│           │   ├── DepositConcurrencyTest.kt
│           │   └── TransferConcurrencyTest.kt
│           └── adapter/in/web/
│               ├── AccountControllerTest.kt
│               └── TransferControllerTest.kt
├── build.gradle.kts
├── docker-compose.yml
└── README.md
```

## 개발 이력

본 프로젝트는 Issue-Driven Development 방식으로 개발되었습니다.

### Completed Phases

- ✅ **Phase 1**: 프로젝트 기반 설정 (#1)
- ✅ **Phase 2**: 도메인 모델 (#2~#4)
- ✅ **Phase 3**: 영속성 레이어 (#5~#7)
- ✅ **Phase 4**: 애플리케이션 서비스 (#8~#10)
- ✅ **Phase 5**: Web API (#11~#13)
- ✅ **Phase 6**: 품질 개선 (#14~#16)

**전체 이슈**: [GitHub Issues](https://github.com/seokrae-labs/account-ledger-service/issues)

## 라이선스

이 프로젝트는 학습 및 포트폴리오 목적으로 작성되었습니다.

---

**마지막 업데이트**: 2026-02-09
**커버리지**: 93.53%
**상태**: ✅ 전체 개발 완료
