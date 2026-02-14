package com.labs.ledger.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

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
        }
    }
}
