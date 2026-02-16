# 이체 실패 내구성 및 감사 추적 설계

작성일: 2026-02-16
상태: 제안(Proposed)
범위: `TransferService` 실패 상태 영속성과 감사 가능성

## 1. 문제 정의

현재 이체 흐름은 같은 트랜잭션 안에서 `FAILED` 상태를 저장한 뒤 예외를 다시 던집니다.

- `src/main/kotlin/com/labs/ledger/application/service/TransferService.kt`
- `src/main/kotlin/com/labs/ledger/adapter/out/persistence/adapter/R2dbcTransactionExecutor.kt`

현재 트랜잭션 동작(예외 시 롤백) 기준으로 보면 `FAILED` 저장도 함께 롤백될 수 있습니다.  
즉, API는 실패를 반환했지만 DB에는 실패 이력이 남지 않을 수 있습니다.

이 결론은 코드/트랜잭션 의미론에 따른 추론이며, 롤백 테스트로도 뒷받침됩니다.

- `src/test/kotlin/com/labs/ledger/adapter/out/persistence/adapter/TransactionIntegrationTest.kt`

## 2. 설계 목표

1. 비즈니스 실패 결과(`FAILED`)를 내구적으로 저장한다.
2. 기존 API 동작(현재 HTTP 에러 매핑)을 유지한다.
3. 운영/감사를 위한 변경 불가능(append-only) 추적 이력을 제공한다.
4. `Idempotency-Key` 의미를 유지한다.
5. Hexagonal/ArchUnit 아키텍처 경계를 지킨다.

## 3. 대안 분석

### 옵션 A: `transfers`만 유지하고 `FAILED` 저장을 독립 트랜잭션으로 분리

- 패턴: 트랜잭션 경계 분리 (`REQUIRES_NEW` 유사)
- 장점: 스키마 변경이 작고 빠르게 내구성 문제를 해결
- 단점: 상태 최종값만 남고 감사 이력(이벤트 히스토리)은 부족

### 옵션 B: 감사 테이블만 추가(실패 상태 내구성은 미해결)

- 장점: 규정 준수 관점의 append-only 로그 확보
- 단점: 멱등성 fast path가 `transfers`를 보므로 실패 row 자체는 여전히 누락 가능

### 옵션 C: 하이브리드(권장)

- 실패 상태는 독립 트랜잭션으로 내구화
- 감사 테이블은 append-only로 이력 축적
- 장점: 정합성 + 관측성/감사성 동시 해결
- 단점: 구현 복잡도 소폭 증가

### 옵션 D: Outbox + 비동기 소비자

- 장점: 확장성/이벤트 중심 구조
- 단점: 현재 범위 대비 과설계, 최종 일관성/운영 복잡도 증가

## 4. 권장 아키텍처 (옵션 C)

### 4.1 실패 분류

- 아래 비즈니스 예외는 `FAILED`로 내구 저장
  - `InsufficientBalanceException`
  - `InvalidAmountException`
  - `InvalidAccountStatusException`
  - `AccountNotFoundException`
- 1차 단계에서 인프라 예외는 강제로 `FAILED` 전이하지 않음
  - 기존 `DATABASE_ERROR` 동작 유지
  - 감사 테이블에는 `SYSTEM_ERROR` 이벤트로 기록

### 4.2 트랜잭션 전략

1. 메인 이체 트랜잭션(`mainTx`)
   - 멱등성 재확인
   - `PENDING` 생성
   - 계좌/원장 반영
   - `COMPLETED` 전이
2. 비즈니스 예외 발생 시
   - `mainTx` 롤백
   - 외부 catch에서 `failureTx`(독립 트랜잭션) 시작
   - 실패 상태 내구 저장
   - 감사 이벤트 기록
   - 원래 예외 재던짐(기존 API 계약 유지)

### `failureTx` 내 내구 저장 알고리즘 (upsert)

1. `idempotencyKey`로 조회
2. 기존 row가 `PENDING`이면 `FAILED`로 업데이트
3. 기존 row가 `COMPLETED`/`FAILED`면 no-op(멱등)
4. row가 없으면(메인 트랜잭션 롤백 케이스) `FAILED` 신규 insert

이렇게 하면 동일 키 재조회 시 항상 안정된 실패 결과를 반환할 수 있습니다.

### 4.3 감사 모델

`transfer_audit_events` append-only 테이블을 도입합니다.  
애플리케이션 경로에서는 update/delete를 하지 않습니다.

이벤트 예시:

- `TRANSFER_REQUESTED`
- `TRANSFER_PENDING_CREATED`
- `TRANSFER_COMPLETED`
- `TRANSFER_FAILED_BUSINESS`
- `TRANSFER_FAILED_SYSTEM`

## 5. 스키마 제안

```sql
CREATE TABLE transfer_audit_events (
    id BIGSERIAL PRIMARY KEY,
    transfer_id BIGINT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    transfer_status VARCHAR(32) NULL,
    reason_code VARCHAR(128) NULL,
    reason_message TEXT NULL,
    trace_id VARCHAR(64) NULL,
    metadata JSONB NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_transfer
        FOREIGN KEY (transfer_id) REFERENCES transfers(id)
);

CREATE INDEX idx_transfer_audit_idempotency_key
    ON transfer_audit_events(idempotency_key);

CREATE INDEX idx_transfer_audit_transfer_id
    ON transfer_audit_events(transfer_id);

CREATE INDEX idx_transfer_audit_created_at
    ON transfer_audit_events(created_at DESC);

CREATE INDEX idx_transfer_audit_event_type
    ON transfer_audit_events(event_type);
```

비고:

- 실패 저장 시점에 `transfers` row가 없을 수 있으므로 `transfer_id`는 nullable 유지
- `metadata`에는 마스킹된 요청 스냅샷, 재시도 횟수, 환경 태그 저장 가능

## 6. 애플리케이션 설계

추가 포트 제안:

- `TransferFailureRepository` (domain port): idempotency key 기준 실패 상태 내구 upsert
- `TransferAuditRepository` (domain port): 감사 이벤트 append
- `IndependentTransactionExecutor` (domain port): 독립 트랜잭션 경계 실행

예상 어댑터:

- `TransferFailurePersistenceAdapter`
- `TransferAuditPersistenceAdapter`
- `R2dbcIndependentTransactionExecutor`

서비스 오케스트레이션:

- `TransferService`는 유스케이스 조율 역할 유지
- 비즈니스 예외 catch 시 `independentTransactionExecutor.execute { ... }` 호출

이 방식은 application 레이어가 Spring 트랜잭션 세부 구현에 직접 의존하지 않게 해줍니다.

## 7. 테스트 전략

### 7.1 신규 통합 테스트(필수)

1. `비즈니스 실패 -> 예외 발생 후에도 FAILED row 유지`
2. `비즈니스 실패 -> mainTx 롤백 후에도 audit 이벤트 유지`
3. `동일 idempotency key 재요청 -> 동일 FAILED 결과 반환`
4. `성공 이체 -> REQUESTED/PENDING/COMPLETED 이벤트 순서 검증`

### 7.2 기존 테스트 보강

- `schema-reset.sql`에 감사 테이블 truncate/sequence reset 추가
- 이체 통합 테스트에 내구성 검증 assert 보강

## 8. 롤아웃 계획

1. 마이그레이션 추가
   - `V3__create_transfer_audit_events.sql`
2. 코드 배포 + 기능 플래그
   - `app.transfer.audit.enabled=true`
3. 관측 지표 확인
   - `transfer.failure.persist.success`
   - `transfer.audit.write.fail`
4. 운영 단계적 활성화

## 9. 리스크 및 대응

1. 실패 상태 저장 + 감사 이벤트 저장의 이중 쓰기 불일치
   - 대응: 둘 다 동일 `failureTx` 안에서 처리
2. 감사 테이블 증가
   - 대응: 보관 주기/파티셔닝(월 단위) 계획 수립
3. 동일 idempotency key 동시성 충돌
   - 대응: `transfers.idempotency_key` 유니크 + failureTx upsert/no-op 규칙

## 10. 용어 정리

이 설계는 두 패턴의 결합입니다.

1. 독립 트랜잭션 패턴 (`REQUIRES_NEW` 스타일 경계 분리)
2. 감사 테이블(Audit Trail) 설계 (append-only 이벤트 이력)

감사 테이블만으로는 실패 상태 내구성이 충분하지 않으며,  
실패 상태 보존에는 트랜잭션 경계 분리가 함께 필요합니다.
