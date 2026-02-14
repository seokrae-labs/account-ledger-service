package com.labs.ledger

import org.springframework.boot.test.autoconfigure.restdocs.RestDocsWebTestClientConfigurationCustomizer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.restdocs.operation.preprocess.Preprocessors

/**
 * REST Docs 전역 설정 (WebFlux용)
 * - 요청/응답 prettify
 * - host/scheme 제거 (문서 이식성)
 */
@TestConfiguration
class RestDocsConfiguration {

    @Bean
    fun restDocsWebTestClientConfigurationCustomizer(): RestDocsWebTestClientConfigurationCustomizer {
        return RestDocsWebTestClientConfigurationCustomizer { configurer ->
            configurer.operationPreprocessors()
                .withRequestDefaults(
                    Preprocessors.prettyPrint(),
                    Preprocessors.removeHeaders("Host", "Content-Length")
                )
                .withResponseDefaults(
                    Preprocessors.prettyPrint(),
                    Preprocessors.removeHeaders("Transfer-Encoding", "Date", "Keep-Alive", "Connection")
                )
        }
    }
}
