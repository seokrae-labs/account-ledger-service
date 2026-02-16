# 트랜잭션 전략 및 실패 처리

> 이체 서비스의 트랜잭션 관리 전략과 실패 시나리오 대응 방안

## 목차
1. [현재 트랜잭션 전략](#현재-트랜잭션-전략)
2. [실패 시나리오 및 대응](#실패-시나리오-및-대응)
3. [재시도 메커니즘](#재시도-메커니즘)
4. [Dead Letter Queue](#dead-letter-queue)
5. [향후 확장 계획](#향후-확장-계획)

---

## 현재 트랜잭션 전략

### 아키텍처 개요

```
┌─────────────────────────────────────────────────────┐
│ TransferService.execute()                           │
├─────────────────────────────────────────────────────┤
│                                                     │
│  try {                                              │
│    ┌─────────────────────────────────────┐         │
│    │ Main Transaction (메인 이체)        │         │
│    ├─────────────────────────────────────┤         │
│    │ 1. 멱등성 체크                      │         │
│    │ 2. PENDING 생성                     │         │
│    │ 3. 계좌 잠금 (FOR UPDATE)           │         │
│    │ 4. 출금/입금 (도메인 로직)          │         │
│    │ 5. 잔액 업데이트                    │         │
│    │ 6. 원장 엔트리 생성                 │         │
│    │ 7. COMPLETED 저장                   │         │
│    │ 8. 감사 이벤트 기록                 │         │
│    └─────────────────────────────────────┘         │
│                                                     │
│  } catch (비즈니스 예외) {                          │
│    ┌─────────────────────────────────────┐         │
│    │ Failure Transaction (실패 처리)     │  ← Phase 1: 재시도 추가
│    ├─────────────────────────────────────┤         │
│    │ 1. FAILED 상태 저장 (upsert)        │         │
│    │ 2. 감사 이벤트 기록                 │         │
│    └─────────────────────────────────────┘         │
│    throw e  // API 계약 유지                        │
│  }                                                  │
└─────────────────────────────────────────────────────┘
```

### 핵심 원칙

1. **메인 트랜잭션 격리**
   - 성공 경로만 처리
   - 비즈니스 예외 발생 시 즉시 롤백
   - Optimistic Lock으로 동시성 제어

2. **독립 실패 트랜잭션**
   - 메인 트랜잭션 롤백과 무관하게 실행
   - FAILED 상태 영구 저장 보장
   - 감사 이벤트와 원자적으로 커밋

3. **멱등성 보장**
   - Fast Path: 트랜잭션 밖에서 중복 체크
   - Double-Check: 트랜잭션 안에서 재확인
   - Race Condition 방지

---

## 실패 시나리오 및 대응

### 1. 비즈니스 예외 (현재 처리 중)

| 예외 | 원인 | 대응 | 상태 |
|------|------|------|------|
| `InsufficientBalanceException` | 잔액 부족 | FAILED 저장 + 감사 이벤트 | ✅ |
| `AccountNotFoundException` | 계좌 없음 | FAILED 저장 + 감사 이벤트 | ✅ |
| `InvalidAmountException` | 유효하지 않은 금액 | FAILED 저장 + 감사 이벤트 | ✅ |
| `InvalidAccountStatusException` | 비활성 계좌 | FAILED 저장 + 감사 이벤트 | ✅ |

**특징:**
- 예측 가능한 실패 (4xx 에러)
- 재시도 불필요 (비즈니스 규칙 위반)
- 클라이언트에게 명확한 에러 응답

### 2. 시스템 예외 (Phase 1에서 처리)

| 예외 | 원인 | 기존 동작 | Phase 1 개선 |
|------|------|-----------|--------------|
| `R2dbcDataIntegrityViolationException` | DB 제약 위반 | 롤백, 로그만 | 3회 재시도 → DLQ |
| `R2dbcTransientException` | 일시적 DB 오류 | 롤백, 로그만 | 3회 재시도 → DLQ |
| `TimeoutException` | DB 락 타임아웃 | 롤백, 로그만 | 3회 재시도 → DLQ |

**특징:**
- 일시적 실패 가능성
- 재시도로 복구 가능
- 최종 실패 시 수동 개입 필요

### 3. 실패 영속화 실패 (Phase 1 핵심 해결)

```kotlin
// 문제 시나리오
메인 트랜잭션 실패 (InsufficientBalance)
  → persistFailureAndAudit() 호출
    → DB 연결 실패 💥
      → FAILED 상태도 못 저장!
        → "실패한 사실"조차 유실 🔥
```

**Phase 1 해결책:**
```kotlin
private suspend fun persistFailureAndAudit(...) {
    retryPolicy.execute {  // ← 재시도 로직 추가
        transactionExecutor.execute {
            // FAILED 저장 + 감사 이벤트
        }
    } ?: run {
        // 최종 실패 시 DLQ 전송
        deadLetterQueue.send(...)
    }
}
```

---

## 재시도 메커니즘

### 재시도 정책

```kotlin
interface RetryPolicy {
    /**
     * 재시도 가능한 작업 실행
     * @return 성공 시 결과, 최종 실패 시 null
     */
    suspend fun <T> execute(operation: suspend () -> T): T?
}

class ExponentialBackoffRetry(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 100,
    val maxDelayMs: Long = 1000
) : RetryPolicy {
    override suspend fun <T> execute(operation: suspend () -> T): T? {
        repeat(maxAttempts) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                if (attempt == maxAttempts - 1) {
                    logger.error(e) { "Final retry attempt failed" }
                    return null
                }
                val delay = minOf(initialDelayMs * (1 shl attempt), maxDelayMs)
                delay(delay)
            }
        }
        return null
    }
}
```

### 재시도 전략

| 시도 | 대기 시간 | 누적 시간 |
|------|----------|-----------|
| 1차  | 0ms      | 0ms       |
| 2차  | 100ms    | 100ms     |
| 3차  | 200ms    | 300ms     |
| 실패 | -        | 300ms     |

**장점:**
- 일시적 네트워크/DB 오류 자동 복구
- 짧은 대기 시간 (300ms 이내)
- 사용자 경험 영향 최소화

**제약:**
- 비즈니스 예외는 재시도 안 함
- 멱등성 보장된 작업만 재시도
- 최대 3회로 무한 루프 방지

---

## Dead Letter Queue

### 개념

**DLQ (Dead Letter Queue)**: 최종 실패한 이벤트를 저장하는 테이블. 수동 또는 배치 작업으로 복구 가능.

### 테이블 스키마

```sql
CREATE TABLE transfer_dead_letter_queue (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(64) NOT NULL,      -- FAILURE_PERSISTENCE_FAILED
    payload JSONB NOT NULL,                -- 전체 컨텍스트 저장
    failure_reason TEXT,                   -- 마지막 에러 메시지
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_retry_at TIMESTAMP,
    processed BOOLEAN DEFAULT false,
    processed_at TIMESTAMP
);

CREATE INDEX idx_dlq_idempotency ON transfer_dead_letter_queue(idempotency_key);
CREATE INDEX idx_dlq_unprocessed ON transfer_dead_letter_queue(processed, created_at DESC);
```

### Payload 구조

```json
{
  "idempotencyKey": "key-123",
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": "1000.00",
  "description": "Transfer",
  "originalException": "InsufficientBalanceException",
  "originalMessage": "Insufficient balance: required 1000.00, available 500.00",
  "attemptedAt": "2026-02-16T12:00:00Z"
}
```

### 사용 시나리오

#### 1. 실패 기록 영속화 실패
```kotlin
// 재시도 3회 실패 후
deadLetterQueue.send(
    DeadLetterEvent(
        idempotencyKey = key,
        eventType = "FAILURE_PERSISTENCE_FAILED",
        payload = TransferContext(...),
        failureReason = "DB connection timeout after 3 retries"
    )
)
```

#### 2. 복구 프로세스

**수동 복구:**
```sql
-- DLQ 확인
SELECT * FROM transfer_dead_letter_queue
WHERE processed = false
ORDER BY created_at DESC;

-- 수동으로 transfers 테이블에 FAILED 삽입
INSERT INTO transfers (...) VALUES (...);

-- DLQ 처리 완료 표시
UPDATE transfer_dead_letter_queue
SET processed = true, processed_at = NOW()
WHERE id = 123;
```

**배치 복구 (Phase 2):**
```kotlin
@Scheduled(cron = "0 */10 * * * *")  // 10분마다
suspend fun processDLQ() {
    val unprocessed = dlqRepository.findUnprocessed(limit = 100)
    unprocessed.forEach { event ->
        try {
            retryFailedTransfer(event)
            dlqRepository.markProcessed(event.id)
        } catch (e: Exception) {
            logger.error(e) { "DLQ retry failed: ${event.id}" }
        }
    }
}
```

---

## 향후 확장 계획

### Phase 2: Saga Orchestrator (중기)

**목표**: 외부 시스템 연동 시 보상 트랜잭션 관리

```kotlin
class TransferSaga {
    suspend fun execute(command: TransferCommand): Transfer {
        return sagaOrchestrator.execute {
            step("withdraw") {
                action = { /* 출금 */ }
                compensation = { /* 입금으로 복구 */ }
            }
            step("deposit") {
                action = { /* 입금 */ }
                compensation = { /* 출금으로 복구 */ }
            }
            step("notify-external") {
                action = { /* 외부 API 호출 */ }
                compensation = { /* 취소 API 호출 */ }
            }
        }
    }
}
```

**DB 스키마:**
```sql
CREATE TABLE saga_execution_log (
    id BIGSERIAL PRIMARY KEY,
    saga_id VARCHAR(255) UNIQUE,
    saga_type VARCHAR(128),
    current_step INT,
    status VARCHAR(32),  -- IN_PROGRESS, COMPLETED, COMPENSATING, FAILED
    context JSONB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

### Phase 3: Event Sourcing (장기)

**목표**: 완전한 이벤트 기반 아키텍처

```kotlin
// 모든 상태 변경을 이벤트로 기록
sealed class TransferEvent {
    data class Initiated(...)
    data class AccountDebited(...)
    data class AccountCredited(...)
    data class Completed(...)
    data class Failed(...)
    data class Compensated(...)
}

// Aggregate Root
class Transfer(private val events: List<TransferEvent>) {
    fun apply(event: TransferEvent): Transfer {
        // 이벤트로 상태 재구성
    }
}
```

**장점:**
- 완전한 감사 추적
- 시간 여행 디버깅
- 이벤트 재생으로 복구

**단점:**
- 복잡도 증가
- 스냅샷 관리 필요
- 학습 곡선

---

## 의사결정 기록

### 왜 2PC를 선택하지 않았나?

| 기준 | 2PC | Saga Pattern |
|------|-----|--------------|
| 성능 | ❌ 느림 (2번 왕복) | ✅ 빠름 (병렬 가능) |
| 가용성 | ❌ 코디네이터 SPOF | ✅ 분산 실행 |
| 복잡도 | 🔶 중간 | 🔶 중간 |
| R2DBC 지원 | ❌ 미지원 | ✅ 가능 |

**결론**: Saga Pattern 선택

### 왜 Orchestration > Choreography?

| 기준 | Orchestration | Choreography |
|------|---------------|--------------|
| 흐름 파악 | ✅ 중앙 집중 | ❌ 분산 |
| 디버깅 | ✅ 쉬움 | ❌ 어려움 |
| 보상 로직 | ✅ 명시적 | 🔶 암묵적 |
| 결합도 | 🔶 중간 | ✅ 낮음 |

**결론**: 현재 단일 서비스 → Orchestration, 향후 MSA → Choreography 고려

---

## 모니터링 및 알림

### 주요 메트릭

```kotlin
// Micrometer 메트릭
counter("transfer.retry.attempts", "result", "success")
counter("transfer.retry.attempts", "result", "failed")
counter("transfer.dlq.events")
gauge("transfer.dlq.unprocessed.count") { dlqRepository.countUnprocessed() }
```

### 알림 규칙

| 조건 | 심각도 | 조치 |
|------|--------|------|
| DLQ 100건 초과 | WARNING | 원인 조사 |
| DLQ 1000건 초과 | CRITICAL | 즉시 대응 |
| 재시도 성공률 < 80% | WARNING | DB 상태 점검 |
| 실패 영속화 실패 발생 | CRITICAL | 시스템 점검 |

---

## 참고 문서

- [Transfer Failure Audit Design](./TRANSFER_FAILURE_AUDIT_DESIGN.md)
- [Suspend Best Practices](./SUSPEND_BEST_PRACTICES.md)
- [Saga Pattern - Chris Richardson](https://microservices.io/patterns/data/saga.html)
- [DLQ Pattern - AWS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html)

---

**작성일**: 2026-02-16
**버전**: 1.0 (Phase 1)
**다음 리뷰**: Phase 2 시작 시
