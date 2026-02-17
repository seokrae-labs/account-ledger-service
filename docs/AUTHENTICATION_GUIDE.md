# Authentication Guide

본 문서는 Account Ledger Service의 JWT 토큰 기반 인증 방법을 설명합니다.

## JWT 토큰 기반 인증

모든 `/api/**` 엔드포인트는 **JWT 토큰 인증이 필수**입니다 (dev 토큰 발급 제외).

## 1. 개발용 토큰 발급 (dev 프로필 전용)

로컬 개발 환경에서 API 테스트를 위한 토큰을 발급받을 수 있습니다.

### 토큰 발급 요청

```bash
curl -X POST http://localhost:8080/api/dev/tokens \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "username": "testuser"
  }'
```

### 응답

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMTIzIiwidXNlcm5hbWUiOiJ0ZXN0dXNlciIsImlhdCI6MTcwOTk2NzYwMCwiZXhwIjoxNzEwMDU0MDAwfQ.xxx",
  "expiresIn": 86400000
}
```

⚠️ **주의**: `/api/dev/**` 엔드포인트는 **dev 프로필에서만** 동작합니다. prod 환경에서는 404를 반환합니다.

## 2. 토큰 사용 방법

발급받은 토큰을 `Authorization` 헤더에 `Bearer {token}` 형식으로 추가합니다.

### 예제: 토큰 발급 및 사용

```bash
# 1. 토큰 발급
TOKEN=$(curl -s -X POST http://localhost:8080/api/dev/tokens \
  -H "Content-Type: application/json" \
  -d '{"userId": "user123", "username": "testuser"}' \
  | jq -r '.token')

# 2. 토큰을 사용하여 API 호출
curl http://localhost:8080/api/accounts/1 \
  -H "Authorization: Bearer $TOKEN"
```

## 3. 인증이 필요 없는 엔드포인트 (Public)

다음 엔드포인트는 인증 없이 접근 가능합니다:

| 경로 | 설명 |
|-----|------|
| `/actuator/health/**` | 헬스체크 |
| `/actuator/info` | 빌드 정보 |
| `/swagger-ui.html` | Swagger UI |
| `/v3/api-docs/**` | OpenAPI 스펙 |
| `/api/dev/**` | 개발용 토큰 발급 (dev 프로필 전용) |

## 4. 인증 오류 응답

### 401 Unauthorized - 인증 실패

토큰이 없거나 유효하지 않은 경우:

```json
{
  "error": "UNAUTHORIZED",
  "message": "Full authentication is required to access this resource",
  "timestamp": "2026-02-16T10:00:00"
}
```

### 403 Forbidden - 권한 없음

인증은 되었으나 권한이 없는 경우:

```json
{
  "error": "ACCESS_DENIED",
  "message": "Access is denied. You do not have permission to access this resource.",
  "timestamp": "2026-02-16T10:00:00"
}
```

## JWT 설정

### 환경변수

| 환경변수 | 설명 | 필수 |
|---------|------|------|
| `JWT_SECRET` | JWT 서명 비밀키 (최소 32자) | dev: 기본값 제공<br>prod: **필수** |

### 프로덕션 환경 설정

프로덕션 환경에서는 반드시 강력한 JWT 비밀키를 설정해야 합니다:

```bash
# 안전한 랜덤 비밀키 생성
export JWT_SECRET=$(openssl rand -base64 32)

# 또는 .env 파일에 설정
echo "JWT_SECRET=$(openssl rand -base64 32)" >> .env
```

⚠️ **보안 주의사항**:
- JWT_SECRET은 절대 Git에 커밋하지 마세요
- 프로덕션 환경에서는 최소 256비트(32바이트) 이상의 강력한 키를 사용하세요
- 키가 유출되면 모든 토큰이 무효화되고 새로운 키로 재발급해야 합니다
