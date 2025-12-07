package org.ddse.ml.cef.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * Test configuration for DuckDB backend.
 * 
 * <h3>Design:</h3>
 * <p>For DuckDB backend, auto-configuration ({@code GraphStoreAutoConfiguration} and
 * {@code ChunkStoreAutoConfiguration}) provides the main beans when
 * {@code cef.graph.store=duckdb} and {@code cef.vector.store=duckdb} are set.</p>
 * 
 * <p>This TestConfiguration only provides:</p>
 * <ul>
 *   <li>{@code DataSourceInitializer} - to initialize DuckDB schema for benchmarks</li>
 * </ul>
 * 
 * <h3>Beans provided by auto-configuration:</h3>
 * <ul>
 *   <li>{@code GraphStore} - DuckDbGraphStore (SQL adjacency tables)</li>
 *   <li>{@code ChunkStore} - DuckDbChunkStore (VSS extension)</li>
 * </ul>
 * 
 * <h3>Beans provided by VllmTestConfiguration:</h3>
 * <ul>
 *   <li>{@code EmbeddingModel} - Ollama nomic-embed-text</li>
 *   <li>{@code ChatModel} - vLLM Qwen3-Coder</li>
 *   <li>{@code ChatClient.Builder}</li>
 * </ul>
 * 
 * <h3>Usage:</h3>
 * <pre>
 * &#64;SpringBootTest(classes = CefTestApplication.class, properties = {
 *     "cef.graph.store=duckdb",
 *     "cef.vector.store=duckdb"
 * })
 * &#64;Import({DuckDbTestConfiguration.class, VllmTestConfiguration.class})
 * &#64;ActiveProfiles({"vllm-integration", "duckdb"})
 * class DuckDbBenchmarkIT extends AbstractBenchmarkIT { ... }
 * </pre>
 * 
 * @author mrmanna
 * @since v0.6
 */
@TestConfiguration
public class DuckDbTestConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DuckDbTestConfiguration.class);

    /**
     * Initialize DuckDB schema with benchmark tables.
     * This is the only bean this config provides - auto-configuration handles the rest.
     * Only created when using DuckDB backend.
     */
    @Bean
    @ConditionalOnProperty(name = "cef.graph.store", havingValue = "duckdb")
    public DataSourceInitializer dataSourceInitializer(DataSource dataSource) {
        log.info("Initializing DuckDB schema for benchmark tests");
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(
                new ClassPathResource("schema-duckdb-benchmark.sql")));
        return initializer;
    }
}
