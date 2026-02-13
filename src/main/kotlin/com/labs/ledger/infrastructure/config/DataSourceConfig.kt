package com.labs.ledger.infrastructure.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import javax.sql.DataSource

/**
 * JDBC DataSource configuration for Flyway migrations.
 *
 * This is required because R2DBC does not provide a JDBC DataSource,
 * but Flyway needs one to execute migrations.
 *
 * The configuration reads connection details from spring.datasource properties
 * in application.yml and creates a HikariCP connection pool specifically for Flyway.
 */
@Configuration
class DataSourceConfig(private val environment: Environment) {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    fun hikariConfig(): HikariConfig {
        val config = HikariConfig()
        config.jdbcUrl = environment.getProperty("spring.datasource.url")
        config.username = environment.getProperty("spring.datasource.username")
        config.password = environment.getProperty("spring.datasource.password")
        config.driverClassName = environment.getProperty("spring.datasource.driver-class-name")
        return config
    }

    @Bean
    fun dataSource(hikariConfig: HikariConfig): DataSource {
        return HikariDataSource(hikariConfig)
    }
}
