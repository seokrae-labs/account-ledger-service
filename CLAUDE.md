# Account Ledger & Transfer Service

## 🎯 프로젝트 목표
실시간 계좌 잔액 관리와 안전한 이체 처리를 제공하는 원장 서비스

## 🏗️ 기술 스택
- **Language**: Kotlin 1.9.25
- **Framework**: Spring Boot 3.4.2
- **Reactive Stack**: WebFlux + Kotlin Coroutines
- **Persistence**: R2DBC + PostgreSQL 16
- **Build**: Gradle 8.11.1

## 🛠️ 주요 명령어

### 환경 시작
```bash
# PostgreSQL 실행
docker-compose up -d

# 서비스 실행
./gradlew bootRun
```

### 빌드 & 테스트
```bash
# 빌드
./gradlew build

# 테스트
./gradlew test

# 클린 빌드
./gradlew clean build
```

### 커버리지 (Kover)
```bash
# HTML 리포트 생성
./gradlew koverHtmlReport
# → build/reports/kover/html/index.html

# 콘솔 출력
./gradlew koverLog

# 검증 (최소 70% 필요)
./gradlew koverVerify
```

## 📋 개발 진행 상황

### ✅ Phase 1: 프로젝트 기반 설정
- [x] Issue #1: 프로젝트 스캐폴딩 및 빌드 환경 구성

### ✅ Phase 2: 도메인 모델
- [x] Issue #2: Account 도메인 모델 구현
- [x] Issue #3: LedgerEntry 도메인 모델 구현
- [x] Issue #4: Transfer 도메인 모델 구현

### ✅ Phase 3: 영속성 레이어
- [x] Issue #5: R2DBC 설정 및 Account 영속성 구현
- [x] Issue #6: LedgerEntry 영속성 구현
- [x] Issue #7: Transfer 영속성 및 트랜잭션 처리 구현

### ✅ Phase 4: 애플리케이션 서비스
- [x] Issue #8: 계좌 생성/조회 UseCase 구현
- [x] Issue #9: 입금 UseCase 구현
- [x] Issue #10: 이체 UseCase 구현 (핵심 로직)

### ✅ Phase 5: Web API
- [x] Issue #11: Account REST API 구현
- [x] Issue #12: Transfer REST API 구현
- [x] Issue #13: 글로벌 예외 처리 및 에러 응답

**🎉 전체 개발 완료! (13/13 Issues)**

## 📋 API 엔드포인트

### 계좌 관리
```bash
# 계좌 생성
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"John Doe"}'

# 입금
curl -X POST http://localhost:8080/api/accounts/1/deposits \
  -H "Content-Type: application/json" \
  -d '{"amount":1000.00,"description":"Initial deposit"}'

# 잔액 조회
curl http://localhost:8080/api/accounts/1
```

### 이체
```bash
# 이체 실행 (Idempotency-Key 필수)
curl -X POST http://localhost:8080/api/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "fromAccountId":1,
    "toAccountId":2,
    "amount":500.00,
    "description":"Payment"
  }'
```

## 🎯 핵심 설계 패턴

### Optimistic Locking
- `@Version`으로 동시성 제어
- 동시 수정 시 `OptimisticLockException` (409 Conflict)

### Idempotency
- Fast path: 트랜잭션 밖 조회
- Double-check: 트랜잭션 내 재확인 (race condition 방지)
- 완료된 이체는 동일 키로 재요청 시 멱등 응답

### Deadlock Prevention
- 계좌 ID 정렬 후 FOR UPDATE
- 항상 동일한 순서로 잠금 획득

## 🔗 참고 자료
- GitHub Issues: https://github.com/seokrae-labs/account-ledger-service/issues
- Spring Data R2DBC: https://spring.io/projects/spring-data-r2dbc
- Kotlin Coroutines: https://kotlinlang.org/docs/coroutines-guide.html

---
**마지막 업데이트**: 2025-02-09
**상태**: ✅ 전체 개발 완료 (Issue #1~#13)
