package org.ddse.ml.cef.config;

import org.ddse.ml.cef.config.CefGraphStoreProperties.StoreType;
import org.ddse.ml.cef.graph.*;
import org.ddse.ml.cef.repository.duckdb.DuckDbGraphStore;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Auto-configuration for CEF Graph Store selection.
 * 
 * <p>Automatically configures the appropriate GraphStore implementation based on
 * the application configuration:</p>
 * 
 * <pre>
 * cef:
 *   graph:
 *     store: duckdb | in-memory | neo4j | pg-sql | pg-age
 * </pre>
 * 
 * <h3>Conditional Bean Creation:</h3>
 * <ul>
 *   <li><b>duckdb</b> - Creates DuckDbGraphStore using embedded DuckDB SQL (default)</li>
 *   <li><b>in-memory</b> - Creates InMemoryGraphStore using JGraphT</li>
 *   <li><b>neo4j</b> - Creates Neo4jGraphStore with Neo4j driver</li>
 *   <li><b>pg-sql</b> - Creates PgSqlGraphStore using pure SQL adjacency tables</li>
 *   <li><b>pg-age</b> - Creates PgAgeGraphStore using Apache AGE extension</li>
 * </ul>
 * 
 * <p><b>Note:</b> Only ONE GraphStore bean should be active at a time based on the
 * cef.graph.store property. Do NOT use @Primary - rely on @ConditionalOnProperty.</p>
 * 
 * @author mrmanna
 * @since v0.6
 */
@Configuration
@EnableConfigurationProperties(CefGraphStoreProperties.class)
public class GraphStoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GraphStoreAutoConfiguration.class);

    /**
     * Neo4j Driver - created when cef.graph.store=neo4j and no Driver bean exists.
     * Tests can provide their own Driver via @TestConfiguration.
     */
    @Bean
    @ConditionalOnProperty(name = "cef.graph.store", havingValue = "neo4j")
    @ConditionalOnMissingBean(Driver.class)
    public Driver neo4jDriver(CefGraphStoreProperties properties) {
        log.info("Creating Neo4j Driver from properties");
        CefGraphStoreProperties.Neo4jConfig neo4jConfig = properties.getNeo4j();
        return GraphDatabase.driver(
            neo4jConfig.getUri(),
            AuthTokens.basic(neo4jConfig.getUsername(), neo4jConfig.getPassword())
        );
    }

    /**
     * Neo4j GraphStore - activated when cef.graph.store=neo4j
     * Uses Driver bean (either from auto-config or test config).
     */
    @Bean
    @ConditionalOnProperty(name = "cef.graph.store", havingValue = "neo4j")
    public GraphStore neo4jGraphStore(Driver driver) {
        log.info("Configuring Neo4jGraphStore - production graph database");
        return new Neo4jGraphStore(driver);
    }

    /**
     * PostgreSQL AGE GraphStore - activated when cef.graph.store=pg-age
     */
    @Bean
    @ConditionalOnProperty(name = "cef.graph.store", havingValue = "pg-age")
    public GraphStore pgAgeGraphStore(DataSource dataSource, CefGraphStoreProperties properties) {
        log.info("Configuring PgAgeGraphStore - Apache AGE Cypher on PostgreSQL");
        
        CefGraphStoreProperties.PostgresConfig pgConfig = properties.getPostgres();
        return new PgAgeGraphStore(dataSource, pgConfig.getGraphName());
    }

    /**
     * PostgreSQL SQL GraphStore - activated when cef.graph.store=pg-sql
     */
    @Bean
    @ConditionalOnProperty(name = "cef.graph.store", havingValue = "pg-sql")
    public GraphStore pgSqlGraphStore(DataSource dataSource, CefGraphStoreProperties properties) {
        log.info("Configuring PgSqlGraphStore - pure SQL adjacency tables on PostgreSQL");
        
        CefGraphStoreProperties.PostgresConfig pgConfig = properties.getPostgres();
        return new PgSqlGraphStore(dataSource, pgConfig.getMaxTraversalDepth());
    }

    /**
     * In-Memory Knowledge Graph - activated when cef.graph.store=in-memory.
     * Exposed as a bean for use by InMemoryGraphStore and other components.
     */
    @Bean
    @ConditionalOnProperty(name = "cef.graph.store", havingValue = "in-memory")
    public InMemoryKnowledgeGraph inMemoryKnowledgeGraph() {
        log.info("Creating InMemoryKnowledgeGraph bean");
        return new InMemoryKnowledgeGraph();
    }

    /**
     * In-Memory GraphStore - activated when cef.graph.store=in-memory
     */
    @Bean
    @ConditionalOnProperty(name = "cef.graph.store", havingValue = "in-memory")
    public GraphStore inMemoryGraphStore(InMemoryKnowledgeGraph knowledgeGraph) {
        log.info("Configuring InMemoryGraphStore - JGraphT in-memory graph");
        return new InMemoryGraphStore(knowledgeGraph);
    }

    /**
     * DuckDB GraphStore - activated when cef.graph.store=duckdb (DEFAULT)
     * This is the default graph store when no cef.graph.store property is specified.
     */
    @Bean
    @ConditionalOnProperty(name = "cef.graph.store", havingValue = "duckdb", matchIfMissing = true)
    public GraphStore duckDbGraphStore(JdbcTemplate jdbcTemplate) {
        log.info("Configuring DuckDbGraphStore - embedded SQL graph store (default)");
        return new DuckDbGraphStore(jdbcTemplate);
    }
}
