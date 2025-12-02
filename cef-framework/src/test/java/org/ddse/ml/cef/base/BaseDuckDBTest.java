package org.ddse.ml.cef.base;

import org.ddse.ml.cef.repository.postgres.ChunkRepository;
import org.ddse.ml.cef.repository.postgres.EdgeRepository;
import org.ddse.ml.cef.repository.postgres.NodeRepository;
import org.ddse.ml.cef.repository.postgres.RelationTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for DuckDB in-memory database tests.
 * Provides consistent configuration and cleanup for fast component tests.
 * 
 * <p>
 * <b>Usage:</b> Extend this class for repository and component tests that need
 * a real database but don't require PostgreSQL-specific features.
 * 
 * <p>
 * <b>Database:</b> DuckDB in-memory (r2dbc:pool:duckdb:mem:testdb)
 * <p>
 * <b>Schema:</b> Loaded from classpath:schema-duckdb.sql
 * <p>
 * <b>Lifecycle:</b> Each test gets a clean database via
 * {@link #cleanDatabase()}
 * 
 * <p>
 * <b>Example:</b>
 * 
 * <pre>
 * class MyRepositoryTest extends BaseDuckDBTest {
 *     &#64;Autowired
 *     private NodeRepository nodeRepository;
 * 
 *     &#64;Test
 *     void shouldSaveNode() {
 *         Node node = TestDataBuilder.node()
 *                 .label("Test")
 *                 .build();
 * 
 *         StepVerifier.create(nodeRepository.save(node))
 *                 .assertNext(saved -> assertThat(saved.getId()).isNotNull())
 *                 .verifyComplete();
 *     }
 * }
 * </pre>
 * 
 * @author mrmanna
 */
@DataR2dbcTest
public abstract class BaseDuckDBTest {

    @Autowired
    protected DatabaseClient databaseClient;

    @Autowired(required = false)
    protected NodeRepository nodeRepository;

    @Autowired(required = false)
    protected EdgeRepository edgeRepository;

    @Autowired(required = false)
    protected ChunkRepository chunkRepository;

    @Autowired(required = false)
    protected RelationTypeRepository relationTypeRepository;

    @DynamicPropertySource
    static void configureDuckDB(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:pool:duckdb:mem:testdb");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:schema-duckdb.sql");
    }

    /**
     * Cleans all database tables before each test.
     * Ensures test isolation by removing data from previous tests.
     */
    @BeforeEach
    void cleanDatabase() {
        // Clean in reverse dependency order
        if (chunkRepository != null) {
            chunkRepository.deleteAll().block();
        }
        if (edgeRepository != null) {
            edgeRepository.deleteAll().block();
        }
        if (nodeRepository != null) {
            nodeRepository.deleteAll().block();
        }
        if (relationTypeRepository != null) {
            relationTypeRepository.deleteAll().block();
        }
    }
}
