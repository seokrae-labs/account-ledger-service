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

## 📋 개발 진행 상황

### ✅ Phase 1: 프로젝트 기반 설정
- [x] Issue #1: 프로젝트 스캐폴딩 및 빌드 환경 구성

### Phase 2: 도메인 모델
- [ ] Issue #2: Account 도메인 모델 구현
- [ ] Issue #3: LedgerEntry 도메인 모델 구현
- [ ] Issue #4: Transfer 도메인 모델 구현

### Phase 3: 영속성 레이어
- [ ] Issue #5: R2DBC 설정 및 Account 영속성 구현
- [ ] Issue #6: LedgerEntry 영속성 구현
- [ ] Issue #7: Transfer 영속성 및 트랜잭션 처리 구현

### Phase 4: 애플리케이션 서비스
- [ ] Issue #8: 계좌 생성/조회 UseCase 구현
- [ ] Issue #9: 입금 UseCase 구현
- [ ] Issue #10: 이체 UseCase 구현

### Phase 5: Web API
- [ ] Issue #11: Account REST API 구현
- [ ] Issue #12: Transfer REST API 구현
- [ ] Issue #13: 글로벌 예외 처리 및 에러 응답

## 🔗 참고 자료
- GitHub Issues: https://github.com/seokrae-labs/account-ledger-service/issues
- Spring Data R2DBC: https://spring.io/projects/spring-data-r2dbc
- Kotlin Coroutines: https://kotlinlang.org/docs/coroutines-guide.html

---
**마지막 업데이트**: 2025-02-09
