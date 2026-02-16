# k6 부하 테스트

Account Ledger Service의 성능 및 동시성 검증을 위한 k6 부하 테스트 스크립트 모음

## 📋 목차

- [사전 준비](#사전-준비)
- [테스트 시나리오](#테스트-시나리오)
- [실행 방법](#실행-방법)
- [결과 분석](#결과-분석)
- [성능 기준](#성능-기준)

---

## 🛠️ 사전 준비

### 1. k6 설치

**macOS**:
```bash
brew install k6
```

**Linux**:
```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

**Windows**:
```powershell
choco install k6
```

**Docker**:
```bash
docker pull grafana/k6:latest
```

### 2. 애플리케이션 시작

```bash
# PostgreSQL 시작 (Docker Compose)
docker-compose up -d postgres

# 애플리케이션 실행
./gradlew bootRun
```

애플리케이션이 `http://localhost:8080`에서 실행 중인지 확인:
```bash
curl http://localhost:8080/actuator/health
```

---

## 📊 테스트 시나리오

### 1. `create-accounts.js` - 계좌 생성 Throughput

**목적**: 계좌 생성 API의 처리량 측정

**부하 프로파일**:
- Ramp-up: 0 → 20 VUs (30초)
- Sustain: 20 VUs (1분)
- Cool-down: 20 → 0 VUs (10초)

**검증 항목**:
- 응답 시간 p95 < 300ms
- 에러율 < 1%
- 최소 100개 계좌 생성

### 2. `deposit-load.js` - 고부하 입금

**목적**: 동시 입금 처리 성능 측정

**부하 프로파일**:
- Ramp-up: 0 → 30 VUs (20초)
- Sustain: 30 VUs (1분)
- Cool-down: 30 → 0 VUs (10초)

**검증 항목**:
- 응답 시간 p95 < 400ms, p99 < 800ms
- 에러율 < 1%
- 최소 200회 성공

### 3. `transfer-concurrent.js` - 동시 양방향 이체 (핵심)

**목적**: Deadlock 방지 메커니즘 검증

**부하 프로파일**:
- Warm-up: 0 → 10 VUs (30초)
- Ramp-up: 10 → 50 VUs (1분)
- Sustain: 50 VUs (2분)
- Cool-down: 50 → 0 VUs (30초)

**검증 항목**:
- **응답 시간 p95 < 500ms, p99 < 1s**
- **에러율 < 1%**
- Deadlock 미발생 (무한 대기 없음)

**특징**:
- 10개 계좌 간 랜덤 이체
- 양방향 이체 시도 (A→B, B→A 동시)
- Optimistic Locking 검증

### 4. `mixed-workload.js` - 혼합 워크로드

**목적**: 실제 운영 환경 시뮬레이션

**부하 프로파일**:
- Warm-up: 0 → 15 VUs (30초)
- Ramp-up: 15 → 40 VUs (2분)
- Sustain: 40 VUs (3분)
- Cool-down: 40 → 0 VUs (30초)

**작업 분포** (가중치):
- 계좌 생성: 10%
- 계좌 조회: 30%
- 입금: 30%
- 이체: 30%

**검증 항목**:
- 응답 시간 p95 < 600ms, p99 < 1.2s
- 에러율 < 2%

---

## 🚀 실행 방법

### 기본 실행

```bash
cd load-tests

# 1. 계좌 생성 Throughput
k6 run scripts/create-accounts.js

# 2. 고부하 입금
k6 run scripts/deposit-load.js

# 3. 동시 양방향 이체 (핵심)
k6 run scripts/transfer-concurrent.js

# 4. 혼합 워크로드
k6 run scripts/mixed-workload.js
```

### Docker로 실행

```bash
docker run --rm -i --network=host \
  -v $(pwd)/scripts:/scripts \
  grafana/k6:latest run /scripts/transfer-concurrent.js
```

### 커스텀 BASE_URL

```bash
# 다른 환경에서 실행
k6 run scripts/transfer-concurrent.js -e BASE_URL=http://staging.example.com:8080
```

### 부하 조정

```bash
# VU 수 조정
k6 run scripts/transfer-concurrent.js --vus 100 --duration 5m

# 단계별 조정
k6 run scripts/transfer-concurrent.js \
  --stage 1m:50 \
  --stage 3m:100 \
  --stage 1m:0
```

---

## 📈 결과 분석

### 콘솔 출력

테스트 실행 중 실시간으로 다음 메트릭이 출력됩니다:

```
scenarios: (100.00%) 1 scenario, 50 max VUs, 4m30s max duration
✓ transfer completed
✓ transfer has valid ID

checks.........................: 100.00% ✓ 1234      ✗ 0
data_received..................: 1.5 MB  8.3 kB/s
data_sent......................: 456 kB  2.5 kB/s
http_req_duration..............: avg=123.45ms p(95)=234.56ms p(99)=456.78ms
http_req_failed................: 0.12%   ✓ 3         ✗ 2497
http_reqs......................: 2500    13.8/s
transfer_duration_ms...........: avg=120.34ms p(95)=230.12ms
transfer_successes.............: 2497    13.8/s
transfer_failures..............: 3       0.02/s
```

### HTML 리포트 생성

```bash
k6 run scripts/transfer-concurrent.js --out json=results.json

# JSON 결과를 HTML로 변환 (k6-reporter 필요)
docker run --rm -v $(pwd):/k6 \
  loadimpact/k6-reporter:latest \
  /k6/results.json /k6/report.html
```

### 주요 메트릭 설명

| 메트릭 | 설명 | 목표 |
|--------|------|------|
| `http_req_duration` | HTTP 요청 응답 시간 | p95 < 500ms |
| `http_req_failed` | HTTP 요청 실패율 | < 1% |
| `transfer_successes` | 이체 성공 횟수 | - |
| `transfer_failures` | 이체 실패 횟수 | - |
| `checks` | 검증 통과율 | 100% |

---

## 🎯 성능 기준

### Critical Path (이체 API)

| 항목 | 목표 | 경고 | 위험 |
|------|------|------|------|
| p50 응답 시간 | < 100ms | < 200ms | ≥ 200ms |
| p95 응답 시간 | < 500ms | < 800ms | ≥ 800ms |
| p99 응답 시간 | < 1000ms | < 1500ms | ≥ 1500ms |
| 에러율 | < 0.5% | < 1% | ≥ 1% |
| Throughput | > 100 TPS | > 50 TPS | < 50 TPS |

### 기타 API

| API | p95 | 에러율 |
|-----|-----|--------|
| 계좌 생성 | < 300ms | < 1% |
| 계좌 조회 | < 100ms | < 0.1% |
| 입금 | < 400ms | < 1% |

---

## 🐛 트러블슈팅

### 1. 연결 거부 (Connection Refused)

**증상**:
```
WARN[0001] Request Failed error="Get \"http://localhost:8080/api/accounts\": dial tcp [::1]:8080: connect: connection refused"
```

**해결**:
```bash
# 애플리케이션이 실행 중인지 확인
curl http://localhost:8080/actuator/health

# 실행되지 않았다면
./gradlew bootRun
```

### 2. 높은 에러율

**원인**:
- 데이터베이스 연결 풀 부족
- 동시성 제어 실패 (Optimistic Lock)

**해결**:
```yaml
# application.yml
spring:
  r2dbc:
    pool:
      max-size: 20  # 기본값 증가
```

### 3. 타임아웃

**원인**:
- 부하가 너무 높음
- 데이터베이스 성능 문제

**해결**:
```bash
# VU 수 감소
k6 run scripts/transfer-concurrent.js --vus 20

# 데이터베이스 인덱스 확인
psql -U ledger -d ledger -c "\d accounts"
```

---

## 📚 참고 자료

- [k6 공식 문서](https://k6.io/docs/)
- [k6 Thresholds](https://k6.io/docs/using-k6/thresholds/)
- [k6 Metrics](https://k6.io/docs/using-k6/metrics/)
- [프로젝트 README](../README.md)

---

**작성일**: 2026-02-16
**버전**: 1.0.0
