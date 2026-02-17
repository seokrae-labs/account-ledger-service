# API Reference

본 문서는 Account Ledger Service의 REST API 상세 명세를 제공합니다.

## API 문서

**Swagger UI**: http://localhost:8080/swagger-ui.html
**OpenAPI Spec**: http://localhost:8080/v3/api-docs

## 엔드포인트 요약

**8개 엔드포인트 제공**

| Method | Path | Status | 설명 |
|--------|------|--------|------|
| GET | `/api/accounts` | 200 | 계좌 목록 조회 (페이지네이션) |
| POST | `/api/accounts` | 201 | 계좌 생성 |
| GET | `/api/accounts/{id}` | 200 | 계좌 조회 |
| POST | `/api/accounts/{id}/deposits` | 200 | 입금 |
| GET | `/api/accounts/{id}/ledger-entries` | 200 | 원장 내역 조회 (페이지네이션) |
| PATCH | `/api/accounts/{id}/status` | 200 | 계좌 상태 변경 |
| GET | `/api/transfers` | 200 | 이체 목록 조회 (페이지네이션) |
| POST | `/api/transfers` | 201 | 이체 |

## 1. 계좌 생성

### Request

```bash
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "ownerName": "John Doe"
  }'
```

### Response (201 Created)

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

## 2. 계좌 조회

### Request

```bash
curl http://localhost:8080/api/accounts/1
```

### Response (200 OK)

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

## 3. 입금

### Request

```bash
curl -X POST http://localhost:8080/api/accounts/1/deposits \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 1000.00,
    "description": "Initial deposit"
  }'
```

### Response (200 OK)

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

## 4. 이체

### Request (Idempotency-Key 필수)

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

### Response (201 Created)

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

## 5. 페이지네이션 (리스트 조회)

모든 리스트 조회 API는 페이지네이션을 지원하며, **created_at DESC (최신순)** 으로 고정 정렬됩니다.

### 지원 엔드포인트

- `GET /api/accounts?page=0&size=20`
- `GET /api/transfers?page=0&size=20`
- `GET /api/accounts/{id}/ledger-entries?page=0&size=20`

### Request Parameters

| 파라미터 | 타입 | 기본값 | 제약 | 설명 |
|---------|------|-------|------|------|
| `page` | int | 0 | ≥ 0 | 페이지 번호 (0부터 시작) |
| `size` | int | 20 | 1-100 | 페이지 크기 |

### Example Request

```bash
curl "http://localhost:8080/api/transfers?page=0&size=10"
```

### Response (200 OK)

```json
{
  "content": [
    {
      "id": 100,
      "idempotencyKey": "...",
      "fromAccountId": 1,
      "toAccountId": 2,
      "amount": 500.00,
      "status": "COMPLETED",
      "createdAt": "2026-02-09T12:00:00",
      "updatedAt": "2026-02-09T12:00:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5
}
```

### 정렬 정책

- 모든 리스트는 `created_at DESC` (최신순) 고정
- 원장 서비스 특성상 시간순 조회가 표준
- 별도 정렬 파라미터 미지원

## 에러 응답

### Error Response Structure

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error message",
  "timestamp": "2026-02-09T10:00:00"
}
```

### Error Codes (12개+)

| HTTP Status | Error Code | 설명 |
|-------------|-----------|------|
| 400 | `INSUFFICIENT_BALANCE` | 잔액 부족 |
| 400 | `INVALID_ACCOUNT_STATUS` | 계좌 상태 오류 (폐쇄된 계좌 등) |
| 400 | `INVALID_AMOUNT` | 유효하지 않은 금액 (음수, 0 등) |
| 400 | `INVALID_REQUEST` | 잘못된 요청 파라미터 |
| 400 | `INVALID_INPUT` | 잘못된 요청 본문 또는 파라미터 |
| 400 | `VALIDATION_FAILED` | 요청 검증 실패 |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 403 | `ACCESS_DENIED` | 권한 없음 |
| 404 | `ACCOUNT_NOT_FOUND` | 계좌를 찾을 수 없음 |
| 405 | `METHOD_NOT_ALLOWED` | 허용되지 않은 HTTP 메서드 |
| 409 | `DUPLICATE_TRANSFER` | 중복 이체 요청 (동일한 Idempotency-Key) |
| 409 | `OPTIMISTIC_LOCK_FAILED` | 동시 수정 감지 (재시도 필요) |
| 409 | `INVALID_TRANSFER_STATUS_TRANSITION` | 유효하지 않은 이체 상태 전환 |
| 500 | `INTERNAL_ERROR` | 내부 서버 오류 |
| 503 | `DATABASE_ERROR` | 데이터베이스 일시적 오류 |

## 주요 헤더

### Idempotency-Key (이체 전용)

이체 API(`POST /api/transfers`)는 반드시 `Idempotency-Key` 헤더가 필요합니다.

```bash
# UUID 생성 예시
curl -X POST http://localhost:8080/api/transfers \
  -H "Idempotency-Key: $(uuidgen)" \
  ...
```

**멱등성 동작**:
- 동일한 `Idempotency-Key`로 요청 시 중복 처리 없이 이전 결과 반환
- 네트워크 재시도 또는 클라이언트 중복 요청 방지
- UUID 또는 고유한 문자열 사용 권장

### Authorization

모든 `/api/**` 엔드포인트는 JWT 토큰 인증이 필요합니다 (dev 토큰 발급 제외).

```bash
curl http://localhost:8080/api/accounts/1 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

자세한 내용은 [AUTHENTICATION_GUIDE.md](AUTHENTICATION_GUIDE.md)를 참조하세요.
