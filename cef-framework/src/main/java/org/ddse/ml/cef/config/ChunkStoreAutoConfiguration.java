package org.ddse.ml.cef.config;

import org.ddse.ml.cef.repository.duckdb.ChunkStore;
import org.ddse.ml.cef.repository.duckdb.DuckDbChunkStore;
import org.ddse.ml.cef.repository.inmemory.InMemoryChunkStore;
import org.ddse.ml.cef.repository.neo4j.Neo4jChunkStore;
import org.ddse.ml.cef.repository.postgres.ChunkRepository;
import org.ddse.ml.cef.repository.postgres.R2dbcChunkStore;
import org.neo4j.driver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Auto-configuration for CEF Chunk Store (Vector Store) selection.
 * 
 * <p>Automatically configures the appropriate ChunkStore implementation based on
 * the application configuration:</p>
 * 
 * <pre>
 * cef:
 *   vector:
 *     store: duckdb | in-memory | neo4j | postgresql
 * </pre>
 * 
 * <h3>Conditional Bean Creation:</h3>
 * <ul>
 *   <li><b>duckdb</b> - Creates DuckDbChunkStore using embedded DuckDB VSS (default)</li>
 *   <li><b>in-memory</b> - Creates InMemoryChunkStore using ConcurrentHashMap</li>
 *   <li><b>neo4j</b> - Creates Neo4jChunkStore with Neo4j vector indexes</li>
 *   <li><b>postgresql</b> - Creates R2dbcChunkStore using PostgreSQL pgvector</li>
 * </ul>
 * 
 * <p><b>Note:</b> Only ONE ChunkStore bean should be active at a time based on the
 * cef.vector.store property. All beans are created here - individual classes
 * should NOT have @Component annotations.</p>
 * 
 * @author mrmanna
 * @since v0.6
 */
@Configuration
@EnableConfigurationProperties(CefProperties.class)
public class ChunkStoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChunkStoreAutoConfiguration.class);

    /**
     * DuckDB ChunkStore - activated when cef.vector.store=duckdb (DEFAULT)
     * This is the default chunk store when no cef.vector.store property is specified.
     */
    @Bean
    @ConditionalOnProperty(name = "cef.vector.store", havingValue = "duckdb", matchIfMissing = true)
    public ChunkStore duckDbChunkStore(JdbcTemplate jdbcTemplate, CefProperties cefProperties) {
        log.info("Configuring DuckDbChunkStore - embedded VSS vector store (default)");
        return new DuckDbChunkStore(jdbcTemplate, cefProperties);
    }

    /**
     * In-Memory ChunkStore - activated when cef.vector.store=in-memory
     */
    @Bean
    @ConditionalOnProperty(name = "cef.vector.store", havingValue = "in-memory")
    public ChunkStore inMemoryChunkStore() {
        log.info("Configuring InMemoryChunkStore - ConcurrentHashMap-based vector store");
        return new InMemoryChunkStore();
    }

    /**
     * Neo4j ChunkStore - activated when cef.vector.store=neo4j
     */
    @Bean
    @ConditionalOnProperty(name = "cef.vector.store", havingValue = "neo4j")
    public ChunkStore neo4jChunkStore(Driver driver, CefProperties cefProperties) {
        log.info("Configuring Neo4jChunkStore - Neo4j native vector index store");
        return new Neo4jChunkStore(driver, cefProperties);
    }

    /**
     * PostgreSQL R2DBC ChunkStore - activated when cef.vector.store=postgresql
     */
    @Bean
    @ConditionalOnProperty(name = "cef.vector.store", havingValue = "postgresql")
    public ChunkStore postgresChunkStore(ChunkRepository chunkRepository) {
        log.info("Configuring R2dbcChunkStore - PostgreSQL pgvector store");
        return new R2dbcChunkStore(chunkRepository);
    }
}
