# Operations Guide

본 문서는 Account Ledger Service의 운영 특징과 모니터링 방법을 설명합니다.

## R2DBC Connection Pool

환경별로 최적화된 R2DBC 커넥션 풀 설정을 제공합니다.

### 설정 비교

| 설정 | Dev | Prod | Test | 설명 |
|-----|-----|------|------|------|
| `initial-size` | 5 | 20 | 2 | 시작 시 생성되는 커넥션 수 |
| `max-size` | 10 | 50 | 5 | 최대 커넥션 수 |
| `max-idle-time` | 30m | 30m | 10m | 유휴 커넥션 유지 시간 |
| `max-lifetime` | 60m | 60m | - | 커넥션 최대 수명 |
| `max-acquire-time` | 3s | 5s | 3s | 커넥션 획득 최대 대기 시간 |
| `validation-query` | SELECT 1 | SELECT 1 | - | 커넥션 검증 쿼리 |

### 설정 예제 (application-prod.yml)

```yaml
spring:
  r2dbc:
    pool:
      enabled: true
      initial-size: 20
      max-size: 50
      max-idle-time: 30m
      max-lifetime: 60m
      max-acquire-time: 5s
      validation-query: SELECT 1
```

### Benefits

- 🚀 성능: 커넥션 재사용으로 응답 시간 단축
- 📊 안정성: 최대 커넥션 수 제한으로 리소스 보호
- 🔍 신뢰성: Validation query로 불량 커넥션 감지
- ⚙️ 유연성: 환경별 맞춤 설정

## Timeout Configuration

모든 레이어에서 적절한 타임아웃을 설정하여 무한 대기를 방지합니다.

### 타임아웃 설정 요약

| 레이어 | 타임아웃 | Dev | Prod | 목적 |
|-------|---------|-----|------|------|
| HTTP Connection | `server.netty.connection-timeout` | 10s | 10s | TCP 연결 수립 타임아웃 |
| HTTP Request | `TimeoutFilter` | 60s | 60s | 전체 요청 처리 타임아웃 |
| R2DBC Statement | `spring.r2dbc.properties.statement-timeout` | 30s | 60s | 쿼리 실행 타임아웃 |
| Transaction | `TransactionalOperator` | 30s | 30s | 트랜잭션 타임아웃 |
| Connection Acquire | `spring.r2dbc.pool.max-acquire-time` | 3s | 5s | 커넥션 획득 타임아웃 |

### 설정 예제

```yaml
# application.yml
server:
  netty:
    connection-timeout: 10s

# application-prod.yml
spring:
  r2dbc:
    properties:
      statement-timeout: 60s
    pool:
      max-acquire-time: 5s
```

### 타임아웃 계층 구조

```
HTTP Request Timeout (60s)
  └─ Transaction Timeout (30s)
      └─ R2DBC Statement Timeout (30s/60s)
          └─ Connection Acquire Timeout (3s/5s)
```

### Benefits

- ⏱️ 무한 대기 방지
- 🛡️ 리소스 보호 (스레드, 커넥션)
- 🚨 빠른 실패 및 복구
- 📊 예측 가능한 응답 시간

## Coroutine MDC Context

코루틴 환경에서 MDC(Mapped Diagnostic Context)가 올바르게 전파되도록 설정되어 있습니다.

### 구현

```kotlin
// RequestLoggingFilter
withContext(MDCContext()) {
    chain.filter(exchange)  // MDC가 하위 코루틴으로 전파됨
}
```

### 로그 출력 예시

```
16:23:45.123 [a1b2c3d4e5f6] INFO  AccountController - Creating account
16:23:45.234 [a1b2c3d4e5f6] DEBUG AccountService - Validating account
16:23:45.345 [a1b2c3d4e5f6] DEBUG AccountRepository - Saving account
```

### Benefits

- 🔍 요청 추적: 동일한 traceId로 전체 요청 흐름 추적
- 🧵 코루틴 안전: 비동기 작업에서도 MDC 유지
- 📊 분산 추적: 마이크로서비스 간 요청 추적 가능

## Graceful Shutdown

프로덕션 환경에서 애플리케이션 종료 시 진행 중인 요청을 안전하게 완료합니다.

### 설정

```yaml
# application.yml (공통)
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

# application-prod.yml (프로덕션)
server:
  shutdown: graceful
```

### 동작 방식

1. 종료 신호 수신 (SIGTERM)
2. 새로운 요청 거부
3. 진행 중인 요청 완료 대기 (최대 30초)
4. 타임아웃 초과 시 강제 종료
5. 리소스 정리 및 종료

### 사용 사례

- 무중단 배포 (Blue-Green, Rolling Update)
- 컨테이너 재시작 시 데이터 손실 방지
- 이체 트랜잭션 중 강제 종료 방지

## Actuator & Health Check

운영 환경에서 애플리케이션 상태를 모니터링할 수 있는 엔드포인트를 제공합니다.

### 사용 가능한 엔드포인트

| Endpoint | Method | 설명 | Dev | Prod |
|----------|--------|------|-----|------|
| `/actuator/health` | GET | 헬스체크 (DB, 디스크 등) | ✅ | ✅ |
| `/actuator/health/liveness` | GET | Liveness probe (K8s) | ✅ | ✅ |
| `/actuator/health/readiness` | GET | Readiness probe (K8s) | ✅ | ✅ |
| `/actuator/info` | GET | 빌드 정보 (버전, 시간) | ✅ | ✅ |
| `/actuator/metrics` | GET | 메트릭 목록 | ✅ | ❌ |

### Health Check 응답 예시

```bash
curl http://localhost:8080/actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

### Build Info 응답 예시

```bash
curl http://localhost:8080/actuator/info
```

```json
{
  "build": {
    "artifact": "account-ledger-service",
    "name": "account-ledger-service",
    "version": "0.0.1-SNAPSHOT",
    "group": "com.labs"
  }
}
```

### Kubernetes Probes

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 20
  periodSeconds: 5
```
