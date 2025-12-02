package org.ddse.ml.cef.config;

import org.ddse.ml.cef.repository.postgres.DualPersistenceGraphStore;
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
 * Default configuration:
 * - Graph: InMemoryKnowledgeGraph with JGraphT (<100K nodes)
 * - VectorStore: Postgres with pgvector
 * - Embedding: OpenAI (configurable to Ollama)
 *
 * @author mrmanna
 */
@AutoConfiguration
@EnableConfigurationProperties(CefProperties.class)
@ComponentScan(basePackages = "org.ddse.ml.cef")
public class CefAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CefAutoConfiguration.class);

    // Graph implementations are auto-discovered via @Component scan:
    // - InMemoryKnowledgeGraph: Synchronous JGraphT implementation
    // - InMemoryGraphStore: Reactive adapter wrapping InMemoryKnowledgeGraph
    //
    // Additional graph implementations will be added in future phases:
    // - Neo4j integration (for millions of nodes)
    // - TinkerPop/Gremlin support

    // VectorStore implementations are auto-discovered via @Component scan:
    // - PostgresVectorStore (@Component)
    //
    // Additional VectorStore implementations will be added in future phases:
    // - DuckDBVectorStore
    // - QdrantVectorStore
    // - PineconeVectorStore

    /**
     * Initialize in-memory graph store on application startup.
     * Loads all nodes and edges from database into memory.
     * 
     * This ensures dual persistence is maintained across application restarts.
     */
    @Bean
    public ApplicationRunner graphStoreInitializer(
            @Autowired(required = false) DualPersistenceGraphStore graphStore) {
        return args -> {
            if (graphStore != null) {
                log.info("Initializing in-memory graph from database...");
                graphStore.loadFromDatabase().block();
                log.info("✓ In-memory graph initialized from database");
            }
        };
    }
}
