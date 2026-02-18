# Logging Guide

Account Ledger Service 로깅 기준 및 컨벤션

## 개요

| 구분 | 기술 |
|------|------|
| 라이브러리 | [KotlinLogging](https://github.com/oshai/kotlin-logging) 7.0.3 |
| 백엔드 | SLF4J + Logback (Spring Boot 기본) |
| 분산 추적 | MDC `traceId` (RequestLoggingFilter 자동 생성) |
| Coroutine 전파 | `kotlinx-coroutines-slf4j` + `MDCContext()` |

---

## 로거 선언

```kotlin
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}
```

**규칙**: 항상 파일 최상단 `private val`로 선언 (companion object 불필요)

---

## 로그 레벨 선택 기준

### ERROR - 즉각 대응 필요

시스템 오류, 데이터 손실 위험, 서비스 불능 상태

```kotlin
// ✅ DB 저장 실패
logger.error { "Failed to persist failure to DB: key=$idempotencyKey" }

// ✅ 보안 검증 실패 (시작 중단)
logger.error("JWT_SECRET is a placeholder. Production startup aborted.")

// ✅ 재시도 모두 소진
logger.error { "Optimistic lock conflict exhausted all $maxAttempts attempts" }

// ❌ 비즈니스 로직 실패 (WARN 사용)
logger.error { "Insufficient balance for accountId=$accountId" }
```

**기준**:
- 운영자 알림 트리거가 필요한 수준
- 자동 복구 불가능
- 데이터 무결성 위협

---

### WARN - 비정상이지만 처리됨

비즈니스 규칙 위반, 재시도, 중복 요청 등 서비스는 계속 동작하는 상태

```kotlin
// ✅ 중복 이체 감지 (멱등성 처리)
logger.warn { "Duplicate transfer attempt (idempotent): key=$idempotencyKey" }

// ✅ 낙관적 잠금 충돌 후 재시도
logger.warn { "Optimistic lock conflict, retrying... (attempt $attemptNumber/$maxAttempts)" }

// ✅ 이체 실패 메모리 등록
logger.warn { "Transfer failure registered in memory: key=$idempotencyKey, reason=$reason" }

// ✅ DLQ 저장 (최종 폴백)
logger.warn { "Failure persisted to DLQ: key=$idempotencyKey" }

// ✅ 도메인 예외 (잔액 부족, 계좌 없음)
logger.warn { "INSUFFICIENT_BALANCE: accountId=$accountId, required=$amount, available=$balance" }

// ❌ 정상 흐름 (INFO 사용)
logger.warn { "Transfer completed: id=$id" }
```

**기준**:
- 예상된 비정상 케이스 (사용자 오류, 경합 조건)
- 시스템은 정상 동작
- 모니터링 임계치 설정 대상

---

### INFO - 주요 비즈니스 이벤트

정상 흐름에서 추적 가치가 있는 상태 변화

```kotlin
// ✅ 계좌 생성
logger.info { "Account created: id=${saved.id}, owner=$ownerName" }

// ✅ 입금 완료
logger.info { "Deposit completed: accountId=$accountId, amount=$amount" }

// ✅ 이체 완료
logger.info { "Transfer completed: id=${saved.id}, from=$fromAccountId, to=$toAccountId, amount=$amount" }

// ✅ HTTP 요청 (RequestLoggingFilter)
logger.info { "$method $path $statusCode ${duration}ms" }

// ✅ 이체 실패 DB 영속화 완료
logger.info { "Transfer failure persisted to DB: key=$idempotencyKey" }

// ❌ 캐시 히트/미스 (DEBUG 사용)
logger.info { "Cache hit: key=$key" }
```

**기준**:
- 비즈니스 트랜잭션의 시작/완료
- 감사(Audit) 목적으로 필요한 이벤트
- 1분에 수백 번 발생하지 않는 빈도

---

### DEBUG - 내부 상태 (개발/QA 전용)

캐시 동작, 인증 세부, 상태 전이 등 prod에서 불필요한 정보

```kotlin
// ✅ 캐시 히트/미스
logger.debug { "Failure cache hit: key=$idempotencyKey" }
logger.debug { "Failure record evicted: key=$key, cause=$cause" }

// ✅ 인증 성공 세부
logger.debug { "Authenticated user: ${userPrincipal.userId}" }

// ✅ 내부 상태 등록
logger.debug { "Failure registered in memory: key=$idempotencyKey, size=$cacheSize" }

// ❌ 외부 요청 완료 (INFO 사용)
logger.debug { "Transfer completed: id=$id" }
```

**기준**:
- prod에서 꺼도 서비스 운영에 문제 없는 정보
- 개발/테스트 시 원인 파악용
- 초당 수십 번 발생 가능한 내부 루프

---

## 메시지 포맷 규칙

### 형식

```
"{행동}: {컨텍스트 필드}"
```

### 필수 포함 필드

| 이벤트 유형 | 포함 필드 |
|------------|---------|
| 이체 | `id`, `from`, `to`, `amount` |
| 입금/출금 | `accountId`, `amount` |
| 계좌 | `id`, `owner` |
| 실패/예외 | `key` 또는 `id`, `reason` |
| 재시도 | `attempt`, `maxAttempts` |
| HTTP 요청 | `method`, `path`, `statusCode`, `duration` |

### 예시

```kotlin
// ✅ GOOD
logger.info { "Transfer completed: id=${saved.id}, from=$fromAccountId, to=$toAccountId, amount=$amount" }
logger.warn { "Duplicate transfer: key=$idempotencyKey, existingStatus=${existing.status}" }
logger.error { "DB save failed: key=$idempotencyKey, cause=${e.message}" }

// ❌ BAD - 컨텍스트 부족
logger.info { "Transfer done" }
logger.error { "Error occurred" }

// ❌ BAD - 예외 스택 없음 (ERROR 레벨에는 예외 전달)
logger.error { "DB error: ${e.message}" }

// ✅ GOOD - 예외 포함
logger.error(e) { "DB save failed: key=$idempotencyKey" }
```

---

## 민감정보 로깅 금지

다음 정보는 **절대 로그에 포함 금지**:

| 금지 항목 | 대안 |
|----------|------|
| JWT 토큰 전체 | `"token=Bearer ***"` 또는 로그 생략 |
| 비밀번호 | 로그 생략 |
| 계좌번호 전체 | 마지막 4자리만: `"****1234"` |
| 개인식별정보 (주민번호 등) | 로그 생략 |
| DB 연결 문자열 (password 포함) | 로그 생략 |

```kotlin
// ❌ BAD
logger.info { "User login: userId=$userId, password=$password" }
logger.debug { "JWT token: $token" }

// ✅ GOOD
logger.info { "User authenticated: userId=$userId" }
logger.debug { "JWT validation passed for userId=$userId" }
```

---

## 환경별 설정

### dev (기본)
- 형식: 텍스트 (`%d{HH:mm:ss.SSS} [traceId] LEVEL logger - message`)
- 레벨: `com.labs.ledger=DEBUG`, R2DBC쿼리 DEBUG

### test
- 형식: 텍스트
- 레벨: `com.labs.ledger=DEBUG`, Testcontainers=INFO

### prod
- 형식: **JSON (ECS)** — `logging.structured.format.console: ecs`
- 레벨: `com.labs.ledger=INFO`, R2DBC=WARN

#### prod JSON 출력 예시
```json
{
  "@timestamp": "2026-02-18T17:30:45.123Z",
  "log.level": "INFO",
  "message": "Transfer completed: id=42, from=1, to=2, amount=1000",
  "log.logger": "com.labs.ledger.application.service.TransferService",
  "process.thread.name": "reactor-http-epoll-1",
  "service.name": "account-ledger-service",
  "traceId": "a1b2c3d4e5f6g7h8"
}
```

> `traceId`는 MDC에서 자동 포함됨 (ECS format이 MDC 필드를 top-level로 포함)

---

## MDC traceId 활용

`RequestLoggingFilter`가 모든 요청에 자동으로 `traceId`를 생성하고 MDC에 등록합니다.

```kotlin
// RequestLoggingFilter (자동 처리)
MDC.put("traceId", traceId)
withContext(MDCContext()) {  // Coroutine 전파
    chain.filter(exchange)
}
```

**직접 traceId를 생성하거나 MDC를 조작하지 마세요.** RequestLoggingFilter가 처리합니다.

### 클라이언트 요청 시
```
X-Trace-Id: a1b2c3d4e5f6g7h8  # 요청 헤더 (없으면 자동 생성)
```

### 응답에서 확인
```
X-Trace-Id: a1b2c3d4e5f6g7h8  # 응답 헤더에 자동 포함
```

---

## 참고

- **RequestLoggingFilter**: `infrastructure/web/RequestLoggingFilter.kt`
- **환경별 설정**: `application-{dev,prod,test}.yml`
- **예외 처리 로깅**: `adapter/in/web/GlobalExceptionHandler.kt`
