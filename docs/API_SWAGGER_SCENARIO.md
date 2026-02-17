# Swagger API 호출 시나리오 (dev 프로필)

본 문서는 Swagger UI를 통해 API 호출 흐름을 단계별로 검증하는 시나리오를 제공합니다.

## 전제 조건

- 애플리케이션 실행 상태 (`http://localhost:8080`)
- `dev` 프로필 활성화 (기본값)

애플리케이션 실행:

```bash
docker compose up -d postgres
./gradlew bootRun
```

Swagger UI 접속: `http://localhost:8080/swagger-ui.html`

## E2E 시나리오

### 1단계. 개발용 JWT 토큰 발급

`POST /api/dev/tokens` 엔드포인트를 사용해 JWT 토큰을 발급합니다.

1. `POST /api/dev/tokens` 항목을 클릭
2. `Try it out` 클릭
3. Request body 입력 후 `Execute`

```json
{
  "userId": "user123",
  "username": "testuser"
}
```

4. 응답의 `token` 값을 복사

### 2단계. Authorize 설정

발급된 토큰을 Swagger UI에 등록하여 이후 요청에 자동 포함시킵니다.

1. 우측 상단 `Authorize` 클릭
2. 값 입력: `Bearer <복사한_token>`
3. `Authorize` 클릭 → `Close`

> `prod` 프로필에서는 Swagger/OpenAPI 및 `/api/dev/tokens` 엔드포인트가 비활성화됩니다.

### 3단계. 계좌 생성

`POST /api/accounts`로 송금 계좌와 수취 계좌를 각각 생성합니다.

**송금 계좌**:
```json
{
  "ownerName": "Sender"
}
```

**수취 계좌**:
```json
{
  "ownerName": "Receiver"
}
```

응답의 `id` 값을 각각 메모합니다.

### 4단계. 송금 계좌 입금

`POST /api/accounts/{id}/deposits`에서 `id`에 송금 계좌 ID를 입력 후 실행합니다.

```json
{
  "amount": 1000.00,
  "description": "Initial funding"
}
```

`GET /api/accounts/{id}`로 잔액이 반영됐는지 확인합니다.

### 5단계. 이체 실행

`POST /api/transfers` 호출 시 **`Idempotency-Key` 헤더가 필수**입니다.

1. `Try it out` 클릭
2. `Idempotency-Key` 헤더에 UUID 입력 (예: `550e8400-e29b-41d4-a716-446655440000`)
3. Request body 입력 후 `Execute`

```json
{
  "fromAccountId": <송금_계좌_ID>,
  "toAccountId": <수취_계좌_ID>,
  "amount": 250.00,
  "description": "Transfer via Swagger scenario"
}
```

### 6단계. 결과 검증

| 엔드포인트 | 확인 항목 |
|-----------|---------|
| `GET /api/transfers` | 이체 `status`가 `COMPLETED` |
| `GET /api/accounts/{id}/ledger-entries` | 송금 계좌에 `DEBIT` 기록 |
| `GET /api/accounts/{id}/ledger-entries` | 수취 계좌에 `CREDIT` 기록 |

## 자주 보는 응답 코드

| HTTP | 에러 코드 | 원인 |
|------|---------|------|
| `401` | `UNAUTHORIZED` | 토큰 누락/만료/형식 오류 |
| `400` | `VALIDATION_FAILED` | 요청 본문/파라미터 검증 실패 |
| `400` | `INSUFFICIENT_BALANCE` | 잔액 부족 |
| `409` | `DUPLICATE_TRANSFER` | 동일 `Idempotency-Key` 재사용 |
| `409` | `OPTIMISTIC_LOCK_FAILED` | 동시 수정 충돌 (재시도 필요) |

## 참고

- curl 시나리오: [API_CURL_SCENARIO.md](API_CURL_SCENARIO.md)
- 상세 API 명세: [API_REFERENCE.md](API_REFERENCE.md)
- 인증 가이드: [AUTHENTICATION_GUIDE.md](AUTHENTICATION_GUIDE.md)
