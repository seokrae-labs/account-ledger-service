package com.labs.ledger.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * 통합 테스트 베이스 클래스
 *
 * Testcontainers PostgreSQL을 Singleton으로 시작하고
 * R2DBC + JDBC(Flyway) 연결 정보를 자동 주입합니다.
 *
 * 사용법:
 * ```kotlin
 * class MyIntegrationTest : AbstractIntegrationTest() {
 *     @Autowired
 *     private lateinit var adapter: MyAdapter
 *
 *     @Test
 *     fun `test name`() = runBlocking {
 *         // ...
 *     }
 * }
 * ```
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestSecurityConfig::class)
@Testcontainers
@Sql(
    scripts = ["/schema-reset.sql"],
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
abstract class AbstractIntegrationTest {

    companion object {
        // Singleton PostgreSQL Container (전체 테스트에서 재사용)
        private val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("ledger")
            withUsername("ledger")
            withPassword("ledger123")
            withReuse(true)
            start()
        }

        /**
         * Spring Boot가 시작되기 전에 동적으로 R2DBC + JDBC 연결 정보를 주입
         */
        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            // R2DBC (WebFlux + Data Layer)
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}"
            }
            registry.add("spring.r2dbc.username") { postgres.username }
            registry.add("spring.r2dbc.password") { postgres.password }

            // JDBC (Flyway Migration)
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }
}
