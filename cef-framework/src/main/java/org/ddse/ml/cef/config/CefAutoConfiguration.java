package org.ddse.ml.cef.config;

import org.ddse.ml.cef.graph.GraphStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot auto-configuration for CEF framework.
 * 
 * Activated when application includes cef-framework dependency.
 * Configuration driven by application.yml properties.
 * 
 * <h3>Graph Store Selection:</h3>
 * <p>Graph stores are configured via {@link GraphStoreAutoConfiguration} based on
 * the {@code cef.graph.store} property:</p>
 * <ul>
 *   <li><b>neo4j</b> - Neo4jGraphStore (native graph DB)</li>
 *   <li><b>pg-age</b> - PgAgeGraphStore (Cypher on PostgreSQL)</li>
 *   <li><b>pg-sql</b> - PgSqlGraphStore (pure SQL)</li>
 *   <li><b>in-memory</b> - InMemoryGraphStore (JGraphT, default)</li>
 * </ul>
 * 
 * <h3>Vector Store:</h3>
 * <p>Configured via {@code cef.vector.store}: duckdb (default), postgresql, neo4j</p>
 *
 * @author mrmanna
 */
@AutoConfiguration
@EnableConfigurationProperties(CefProperties.class)
@ComponentScan(basePackages = "org.ddse.ml.cef")
public class CefAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CefAutoConfiguration.class);

    // Graph implementations are configured by GraphStoreAutoConfiguration:
    // - DuckDbGraphStore: Pure SQL (cef.graph.store=duckdb)
    // - Neo4jGraphStore: Native graph (cef.graph.store=neo4j)
    // - PgAgeGraphStore: Cypher via AGE (cef.graph.store=pg-age)
    // - PgSqlGraphStore: Pure SQL (cef.graph.store=pg-sql)
    // - InMemoryGraphStore: JGraphT (default/in-memory)

    /**
     * Log the configured GraphStore on startup.
     */
    @Bean
    public ApplicationRunner graphStoreLogger(
            @Autowired(required = false) GraphStore graphStore) {
        return args -> {
            if (graphStore != null) {
                log.info("✓ GraphStore configured: {}", graphStore.getClass().getSimpleName());
            } else {
                log.warn("⚠ No GraphStore configured");
            }
        };
    }
}
