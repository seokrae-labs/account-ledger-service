#!/bin/bash

# Account Ledger Service - API Test Script
# 사용법: ./scripts/test-api.sh [scenario]
# 시나리오: all, validation, transfer, basic

BASE_URL="http://localhost:8080"
TEMP_DIR="/tmp/ledger-test"

# 색상 정의
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 임시 디렉토리 생성
mkdir -p "$TEMP_DIR"

# 헬퍼 함수
print_header() {
    echo -e "\n${BLUE}================================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ️  $1${NC}"
}

# 1. 계좌 생성
test_create_account() {
    print_header "TEST 1: 계좌 생성"

    echo '{"ownerName":"Alice"}' > "$TEMP_DIR/alice.json"
    local response=$(curl -s -X POST "$BASE_URL/api/accounts" \
        -H 'Content-Type: application/json' \
        -d @"$TEMP_DIR/alice.json")

    local id=$(echo "$response" | jq -r '.id')
    if [ "$id" != "null" ] && [ -n "$id" ]; then
        print_success "Alice 계좌 생성 성공 (ID: $id)"
        echo "$response" | jq .
        echo "$id"
    else
        print_error "계좌 생성 실패"
        echo "$response" | jq .
        echo "0"
    fi
}

# 2. Validation 실패 테스트
test_validation_failures() {
    print_header "TEST 2: Validation 실패 시나리오"

    # 2-1. 빈 ownerName
    print_info "2-1. 빈 ownerName 검증"
    echo '{"ownerName":""}' > "$TEMP_DIR/empty.json"
    curl -s -X POST "$BASE_URL/api/accounts" \
        -H 'Content-Type: application/json' \
        -d @"$TEMP_DIR/empty.json" | jq .

    # 2-2. 음수 금액
    print_info "2-2. 음수 금액 검증"
    echo '{"amount":-100}' > "$TEMP_DIR/negative.json"
    curl -s -X POST "$BASE_URL/api/accounts/1/deposits" \
        -H 'Content-Type: application/json' \
        -d @"$TEMP_DIR/negative.json" | jq .

    # 2-3. 누락된 필드
    print_info "2-3. 누락된 이체 필드 검증"
    echo '{"amount":100}' > "$TEMP_DIR/missing.json"
    curl -s -X POST "$BASE_URL/api/transfers" \
        -H 'Content-Type: application/json' \
        -H 'Idempotency-Key: missing-test' \
        -d @"$TEMP_DIR/missing.json" | jq .
}

# 3. 입금 테스트
test_deposit() {
    local account_id=$1
    local amount=$2

    print_header "TEST 3: 입금 (Account $account_id, Amount: $amount)"

    echo "{\"amount\":$amount}" > "$TEMP_DIR/deposit.json"
    local response=$(curl -s -X POST "$BASE_URL/api/accounts/$account_id/deposits" \
        -H 'Content-Type: application/json' \
        -d @"$TEMP_DIR/deposit.json")

    local balance=$(echo "$response" | jq -r '.balance')
    if [ "$balance" != "null" ]; then
        print_success "입금 성공 (잔액: $balance)"
        echo "$response" | jq .
    else
        print_error "입금 실패"
        echo "$response" | jq .
    fi
}

# 4. 이체 테스트
test_transfer() {
    local from_id=$1
    local to_id=$2
    local amount=$3

    print_header "TEST 4: 이체 (From: $from_id, To: $to_id, Amount: $amount)"

    local idempotency_key="transfer-$(date +%s)-$RANDOM"
    echo "{\"fromAccountId\":$from_id,\"toAccountId\":$to_id,\"amount\":$amount}" > "$TEMP_DIR/transfer.json"

    local response=$(curl -s -X POST "$BASE_URL/api/transfers" \
        -H 'Content-Type: application/json' \
        -H "Idempotency-Key: $idempotency_key" \
        -d @"$TEMP_DIR/transfer.json")

    local transfer_id=$(echo "$response" | jq -r '.id')
    if [ "$transfer_id" != "null" ] && [ -n "$transfer_id" ]; then
        print_success "이체 성공 (Transfer ID: $transfer_id)"
        echo "$response" | jq .
    else
        print_error "이체 실패"
        echo "$response" | jq .
    fi
}

# 5. 계좌 조회
test_get_account() {
    local account_id=$1

    print_header "TEST 5: 계좌 조회 (Account $account_id)"

    local response=$(curl -s -X GET "$BASE_URL/api/accounts/$account_id")
    local balance=$(echo "$response" | jq -r '.balance')

    if [ "$balance" != "null" ]; then
        print_success "조회 성공 (잔액: $balance)"
        echo "$response" | jq .
    else
        print_error "조회 실패"
        echo "$response" | jq .
    fi
}

# 6. 멱등성 테스트
test_idempotency() {
    local from_id=$1
    local to_id=$2

    print_header "TEST 6: 멱등성 (동일 Idempotency-Key로 2번 요청)"

    local idempotency_key="idempotent-test-$(date +%s)"
    echo "{\"fromAccountId\":$from_id,\"toAccountId\":$to_id,\"amount\":50}" > "$TEMP_DIR/idem.json"

    print_info "첫 번째 요청"
    curl -s -X POST "$BASE_URL/api/transfers" \
        -H 'Content-Type: application/json' \
        -H "Idempotency-Key: $idempotency_key" \
        -d @"$TEMP_DIR/idem.json" | jq .

    sleep 1

    print_info "두 번째 요청 (동일 키)"
    curl -s -X POST "$BASE_URL/api/transfers" \
        -H 'Content-Type: application/json' \
        -H "Idempotency-Key: $idempotency_key" \
        -d @"$TEMP_DIR/idem.json" | jq .
}

# 전체 시나리오 실행
run_full_scenario() {
    print_header "🚀 전체 API 테스트 시작"

    # 1. 계좌 2개 생성
    alice_id=$(test_create_account)
    sleep 1

    echo '{"ownerName":"Bob"}' > "$TEMP_DIR/bob.json"
    bob_response=$(curl -s -X POST "$BASE_URL/api/accounts" \
        -H 'Content-Type: application/json' \
        -d @"$TEMP_DIR/bob.json")
    bob_id=$(echo "$bob_response" | jq -r '.id')
    print_success "Bob 계좌 생성 (ID: $bob_id)"
    sleep 1

    # 2. Validation 테스트
    test_validation_failures
    sleep 1

    # 3. Alice에게 1000 입금
    test_deposit "$alice_id" 1000
    sleep 1

    # 4. Alice → Bob 300 이체
    test_transfer "$alice_id" "$bob_id" 300
    sleep 1

    # 5. 잔액 확인
    test_get_account "$alice_id"
    test_get_account "$bob_id"
    sleep 1

    # 6. 멱등성 테스트
    test_idempotency "$alice_id" "$bob_id"

    print_header "✅ 전체 테스트 완료"
}

# 메인 실행
case "${1:-all}" in
    all)
        run_full_scenario
        ;;
    validation)
        test_validation_failures
        ;;
    transfer)
        if [ -z "$2" ] || [ -z "$3" ] || [ -z "$4" ]; then
            echo "사용법: $0 transfer <from_id> <to_id> <amount>"
            exit 1
        fi
        test_transfer "$2" "$3" "$4"
        ;;
    basic)
        alice_id=$(test_create_account)
        sleep 1
        test_deposit "$alice_id" 500
        ;;
    *)
        echo "사용법: $0 {all|validation|transfer|basic}"
        echo "  all        - 전체 시나리오 실행"
        echo "  validation - 검증 실패 테스트만"
        echo "  transfer   - 이체 테스트 (from_id to_id amount 필요)"
        echo "  basic      - 기본 테스트 (계좌 생성 + 입금)"
        exit 1
        ;;
esac
