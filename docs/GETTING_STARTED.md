# Getting Started

본 문서는 Account Ledger Service를 로컬 환경에서 실행하는 방법을 설명합니다.

## Prerequisites

- **JDK 21** 이상
- **Docker** 및 **Docker Compose**

## 환경 설정

### 1. 환경변수 설정 (선택사항)

```bash
# .env 파일 생성 (기본값을 사용하려면 스킵 가능)
cp .env.example .env
# .env 파일을 편집하여 데이터베이스 자격증명 수정
```

### 2. PostgreSQL 실행

```bash
docker compose up -d postgres
```

### 3. 애플리케이션 실행

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

### 4. 접속

```
http://localhost:8080
```

## Docker로 실행

### Docker 이미지 빌드

```bash
docker build -t account-ledger-service:latest .
```

### PostgreSQL과 함께 실행 (Docker Compose 사용)

```bash
# PostgreSQL + 애플리케이션 모두 시작
docker compose up -d

# 로그 확인
docker compose logs -f app

# 종료
docker compose down
```

### 단독 실행 (PostgreSQL이 이미 실행 중인 경우)

```bash
docker run -d \
  --name account-ledger-service \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_USERNAME=ledger \
  -e DB_PASSWORD=ledger123 \
  -e R2DBC_URL=r2dbc:postgresql://host.docker.internal:5432/ledger \
  -e JDBC_URL=jdbc:postgresql://host.docker.internal:5432/ledger \
  -e JWT_SECRET=$(openssl rand -base64 32) \
  account-ledger-service:latest
```

### 헬스체크

```bash
# 애플리케이션 상태 확인
curl http://localhost:8080/actuator/health

# Liveness probe
curl http://localhost:8080/actuator/health/liveness

# Readiness probe
curl http://localhost:8080/actuator/health/readiness
```

## 프로파일별 설정

| 프로파일 | 용도 | 로깅 레벨 | R2DBC Pool | 특징 |
|---------|------|----------|-----------|------|
| **dev** | 로컬 개발 | DEBUG | 5-10 | Flyway clean 허용, 상세 로깅 |
| **prod** | 프로덕션 | INFO | 20-50 | 커넥션 풀 최적화, Graceful Shutdown |
| **test** | 자동화 테스트 | DEBUG | 2-5 | **Testcontainers 기반**, Docker만 필요 |

## 환경변수 설정

데이터베이스 자격증명은 환경변수를 통해 외부화할 수 있습니다:

| 환경변수 | 설명 | 기본값 |
|---------|------|--------|
| `DB_USERNAME` | 데이터베이스 사용자명 | `ledger` |
| `DB_PASSWORD` | 데이터베이스 비밀번호 | `ledger123` |
| `R2DBC_URL` | R2DBC 연결 URL | `r2dbc:postgresql://localhost:5432/ledger` |
| `JDBC_URL` | JDBC 연결 URL (Flyway용) | `jdbc:postgresql://localhost:5432/ledger` |
| `JWT_SECRET` | JWT 서명 비밀키 (최소 32자) | `dev-only-secret-...` (dev), **필수** (prod) |

**설정 방법:**
1. `.env.example`을 `.env`로 복사
2. `.env` 파일 수정 (이 파일은 Git에 커밋되지 않음)
3. Docker Compose가 자동으로 `.env` 파일 로드

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
│   │   │   │   └── out/persistence/
│   │   │   │       ├── adapter/          # Persistence Adapters
│   │   │   │       ├── entity/           # JPA/R2DBC Entities
│   │   │   │       └── repository/       # Spring Data Repositories
│   │   │   ├── application/
│   │   │   │   ├── service/              # Use Case Implementations
│   │   │   │   └── support/              # InMemoryFailureRegistry 등
│   │   │   ├── domain/
│   │   │   │   ├── model/                # Domain Models
│   │   │   │   ├── port/                 # Input/Output Ports (Interfaces)
│   │   │   │   └── exception/            # Domain Exceptions (7개)
│   │   │   ├── infrastructure/
│   │   │   │   ├── config/               # R2DBC, OpenAPI 등
│   │   │   │   ├── security/             # JWT, SecurityConfig, Filters
│   │   │   │   └── web/                  # RequestLoggingFilter 등
│   │   │   └── LedgerApplication.kt      # Main Application
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── application-test.yml
│   │       └── db/migration/             # Flyway SQL scripts
│   └── test/
│       └── kotlin/com/labs/ledger/
│           ├── adapter/
│           │   ├── in/web/               # Controller Tests (2)
│           │   └── out/persistence/      # Persistence Tests (4)
│           ├── application/
│           │   ├── service/              # Service Tests (11)
│           │   └── support/              # Support Tests (1)
│           ├── architecture/             # ArchUnit Tests (6)
│           ├── domain/model/             # Domain Tests (6)
│           ├── infrastructure/security/  # Security Tests (2)
│           └── support/                  # AbstractIntegrationTest
├── docs/                                 # Architecture docs
│   ├── SUSPEND_BEST_PRACTICES.md
│   ├── SUSPEND_FOR_JAVA_DEVELOPERS.md
│   └── POC_SUSPEND_VALIDATION_RESULT.md
├── build.gradle.kts
├── docker-compose.yml
├── Dockerfile
└── README.md
```

## 다음 단계

- **API 문서**: [API_REFERENCE.md](API_REFERENCE.md)
- **운영 가이드**: [OPERATIONS_GUIDE.md](OPERATIONS_GUIDE.md)
- **인증 가이드**: [AUTHENTICATION_GUIDE.md](AUTHENTICATION_GUIDE.md)
