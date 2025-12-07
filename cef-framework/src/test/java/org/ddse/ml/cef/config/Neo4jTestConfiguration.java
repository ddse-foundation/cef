package org.ddse.ml.cef.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration for Neo4j backend.
 * 
 * <h3>Design:</h3>
 * <p>For Neo4j backend, auto-configuration ({@code GraphStoreAutoConfiguration}) needs
 * a Neo4j {@code Driver} to create Neo4jGraphStore. This TestConfiguration provides
 * that driver for test environments.</p>
 * 
 * <p>This TestConfiguration provides:</p>
 * <ul>
 *   <li>{@code Driver} - Neo4j driver for test infrastructure</li>
 * </ul>
 * 
 * <h3>Beans provided by auto-configuration:</h3>
 * <ul>
 *   <li>{@code GraphStore} - Neo4jGraphStore (uses this Driver)</li>
 *   <li>{@code ChunkStore} - Neo4jChunkStore (uses this Driver)</li>
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
 *     "cef.graph.store=neo4j",
 *     "cef.vector.store=neo4j"
 * })
 * &#64;Import({Neo4jTestConfiguration.class, VllmTestConfiguration.class})
 * &#64;ActiveProfiles({"vllm-integration", "neo4j"})
 * class Neo4jBenchmarkIT extends AbstractBenchmarkIT { ... }
 * </pre>
 * 
 * <h3>Prerequisites:</h3>
 * <ul>
 *   <li>Neo4j 5.11+ running on localhost:7687 (for vector index support)</li>
 *   <li>Start: {@code docker run -d --name neo4j -p 7474:7474 -p 7687:7687 -e NEO4J_AUTH=none neo4j:5.18-community}</li>
 * </ul>
 * 
 * @author mrmanna
 * @since v0.6
 */
@TestConfiguration
public class Neo4jTestConfiguration {

    private static final Logger log = LoggerFactory.getLogger(Neo4jTestConfiguration.class);

    /**
     * Neo4j Driver - ONLY created when cef.graph.store=neo4j.
     * This prevents this bean from being created during other backend tests.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "cef.graph.store", havingValue = "neo4j")
    public Driver neo4jDriver(
            @Value("${cef.graph.neo4j.uri:bolt://localhost:7687}") String uri,
            @Value("${cef.graph.neo4j.username:neo4j}") String username,
            @Value("${cef.graph.neo4j.password:}") String password) {
        
        log.info("Creating Neo4j Driver for URI: {}", uri);
        
        Driver driver;
        if (password == null || password.isEmpty()) {
            driver = GraphDatabase.driver(uri, AuthTokens.none());
            log.info("Connected to Neo4j without authentication");
        } else {
            driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
            log.info("Connected to Neo4j with authentication (user: {})", username);
        }
        
        // Verify connectivity
        try {
            driver.verifyConnectivity();
            log.info("Neo4j connectivity verified successfully");
        } catch (Exception e) {
            log.error("Failed to connect to Neo4j at {}: {}", uri, e.getMessage());
            throw new RuntimeException("Neo4j connection failed. Ensure Neo4j is running: " +
                "docker run -d --name neo4j -p 7474:7474 -p 7687:7687 -e NEO4J_AUTH=none neo4j:5.18-community", e);
        }
        
        return driver;
    }
}
