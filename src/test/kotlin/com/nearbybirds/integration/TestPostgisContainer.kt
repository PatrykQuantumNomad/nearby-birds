package com.nearbybirds.integration

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName

/**
 * Shared PostGIS container configuration for integration tests.
 * Uses Testcontainers to spin up a real PostGIS instance.
 */
abstract class TestPostgisContainer {

    companion object {
        @Container
        @JvmStatic
        val postgis: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"),
        )
            .withDatabaseName("nearby_birds_test")
            .withUsername("test")
            .withPassword("test")
            .apply { start() }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgis.jdbcUrl }
            registry.add("spring.datasource.username") { postgis.username }
            registry.add("spring.datasource.password") { postgis.password }
        }
    }
}
