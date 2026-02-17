# curl API 호출 시나리오 (dev 프로필)

본 문서는 Swagger 없이 터미널에서 API 호출 흐름을 검증하는 E2E 시나리오를 제공합니다.

## 전제 조건

- 애플리케이션 실행 상태 (`http://localhost:8080`)
- `dev` 프로필 활성화 (기본값)
- `jq` 설치

애플리케이션 실행:

```bash
docker compose up -d postgres
./gradlew bootRun
```

## E2E 시나리오

아래 스크립트를 그대로 실행하면 토큰 발급부터 이체/원장 검증까지 한 번에 확인할 수 있습니다.

```bash
set -euo pipefail

BASE_URL="http://localhost:8080"

echo "[1] Dev 토큰 발급"
TOKEN=$(curl -s -X POST "$BASE_URL/api/dev/tokens" \
  -H "Content-Type: application/json" \
  -d '{"userId":"user123","username":"testuser"}' | jq -r '.token')
echo "TOKEN issued: ${TOKEN:0:20}..."

echo "[2] 송금 계좌/수취 계좌 생성"
FROM_ACCOUNT_ID=$(curl -s -X POST "$BASE_URL/api/accounts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"Sender"}' | jq -r '.id')

TO_ACCOUNT_ID=$(curl -s -X POST "$BASE_URL/api/accounts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"Receiver"}' | jq -r '.id')

echo "FROM_ACCOUNT_ID=$FROM_ACCOUNT_ID"
echo "TO_ACCOUNT_ID=$TO_ACCOUNT_ID"

echo "[3] 송금 계좌 입금"
curl -s -X POST "$BASE_URL/api/accounts/$FROM_ACCOUNT_ID/deposits" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount":1000.00,"description":"Initial funding"}' | jq

echo "[4] 이체 실행 (Idempotency-Key 필수)"
IDEMPOTENCY_KEY=$(uuidgen)
curl -s -X POST "$BASE_URL/api/transfers" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"fromAccountId\": $FROM_ACCOUNT_ID,
    \"toAccountId\": $TO_ACCOUNT_ID,
    \"amount\": 250.00,
    \"description\": \"Transfer via curl scenario\"
  }" | jq

echo "[5] 결과 확인 - 이체 목록"
curl -s "$BASE_URL/api/transfers?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN" | jq

echo "[6] 결과 확인 - 각 계좌 원장"
curl -s "$BASE_URL/api/accounts/$FROM_ACCOUNT_ID/ledger-entries?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN" | jq

curl -s "$BASE_URL/api/accounts/$TO_ACCOUNT_ID/ledger-entries?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN" | jq
```

## 검증 포인트

- 이체 응답의 `status`가 `COMPLETED`
- 송금/수취 계좌 원장에 각각 `DEBIT`/`CREDIT` 기록 존재
- 동일 `Idempotency-Key` 재사용 시 `409 DUPLICATE_TRANSFER` 응답

## 참고

- Swagger 시나리오: `README.md`
- 상세 API 명세: `docs/API_REFERENCE.md`
- 인증 가이드: `docs/AUTHENTICATION_GUIDE.md`

