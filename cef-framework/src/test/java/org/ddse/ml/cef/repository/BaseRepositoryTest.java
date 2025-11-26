package org.ddse.ml.cef.repository;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Base test class for repository tests with schema initialization.
 * Schema is applied before each test to ensure clean state.
 */
@DataR2dbcTest
@Testcontainers
public abstract class BaseRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://"
                + postgres.getHost() + ":" + postgres.getFirstMappedPort()
                + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired
    private DatabaseClient databaseClient;

    private static boolean schemaInitialized = false;

    @BeforeEach
    void initializeSchema() throws IOException {
        if (!schemaInitialized) {
            // Read schema from classpath
            String schema = Files.readString(Paths.get("src/test/resources/schema-test.sql"));

            // Split by semicolon and execute each statement
            String[] statements = schema.split(";");

            for (String statement : statements) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    databaseClient.sql(trimmed)
                            .fetch()
                            .rowsUpdated()
                            .block();
                }
            }
            schemaInitialized = true;
        }
    }
}
