package com.labs.ledger.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class for integration tests using Testcontainers.
 *
 * Provides:
 * - Singleton PostgreSQL container (shared across all test classes)
 * - Automatic R2DBC + JDBC connection configuration via @DynamicPropertySource
 * - Schema reset before each test method
 *
 * All integration tests should extend this class instead of using @SpringBootTest directly.
 *
 * Note: Requires Docker to be running. The container is started once per JVM and reused
 * across all test classes for performance.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Sql(scripts = ["/schema-reset.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
abstract class AbstractIntegrationTest {

    companion object {
        @JvmStatic
        private val postgres: PostgreSQLContainer<*> by lazy {
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("ledger")
                .withUsername("ledger")
                .withPassword("ledger123")
                .withReuse(true)
                .apply { start() }
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            // R2DBC configuration
            registry.add("spring.r2dbc.url") { postgres.jdbcUrl.replace("jdbc:", "r2dbc:") }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)

            // JDBC configuration (for Flyway)
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
