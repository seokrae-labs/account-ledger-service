package com.labs.ledger.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Base class for integration tests using Testcontainers.
 *
 * Provides:
 * - Singleton PostgreSQL container (shared across all test classes)
 * - Automatic R2DBC + JDBC connection configuration via @ServiceConnection
 * - Schema reset before each test method
 *
 * All integration tests should extend this class instead of using @SpringBootTest directly.
 *
 * Note: Requires Docker to be running. The container is started once per JVM and reused
 * across all test classes for performance.
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = ["/schema-reset.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
abstract class AbstractIntegrationTest {

    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine")
        ).apply {
            withDatabaseName("ledger")
            withUsername("ledger")
            withPassword("ledger123")
            start()
        }
    }
}
