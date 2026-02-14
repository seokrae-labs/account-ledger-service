package com.labs.ledger.infrastructure.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI 3.0 자동 생성 설정
 *
 * - Controller 메서드 자동 스캔 (어노테이션 불필요!)
 * - Request/Response 타입 자동 분석
 * - Swagger UI 자동 제공: /swagger-ui.html
 * - OpenAPI Spec: /v3/api-docs
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Account Ledger & Transfer Service API")
                    .version("1.0.0")
                    .description(
                        """
                        실시간 계좌 잔액 관리와 안전한 이체 처리를 제공하는 Reactive 원장 서비스

                        ## 주요 기능
                        - 계좌 관리: 생성, 조회, 입금, 상태 변경
                        - 이체 처리: 멱등성 보장 (Idempotency-Key 헤더 필수)
                        - 원장 조회: 계좌별 거래 내역

                        ## 동시성 제어
                        - Optimistic Locking: 동시 수정 감지 (409 Conflict 시 재시도)
                        - Deadlock Prevention: 계좌 ID 정렬 후 FOR UPDATE
                        """.trimIndent()
                    )
            )
    }
}
