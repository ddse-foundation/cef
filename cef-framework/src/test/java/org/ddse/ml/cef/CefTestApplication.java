package org.ddse.ml.cef;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single bootstrap configuration for all CEF integration tests.
 * 
 * <h3>Bean Management Strategy:</h3>
 * <p>CEF uses <b>property-driven auto-configuration</b> for backend selection:</p>
 * <ul>
 *   <li>{@code cef.graph.store} → selects GraphStore implementation</li>
 *   <li>{@code cef.vector.store} → selects ChunkStore implementation</li>
 * </ul>
 * 
 * <p>Auto-configuration classes ({@code GraphStoreAutoConfiguration}, 
 * {@code ChunkStoreAutoConfiguration}) use {@code @ConditionalOnProperty} to create
 * exactly ONE bean of each type based on properties.</p>
 * 
 * <h3>Test Configuration Pattern:</h3>
 * <ol>
 *   <li>Set properties in {@code @SpringBootTest} to select backend</li>
 *   <li>Use {@code @ActiveProfiles} to load profile-specific YAML</li>
 *   <li>Import {@code @TestConfiguration} only for beans NOT handled by auto-config
 *       (e.g., external services like Neo4j Driver, LLM clients)</li>
 * </ol>
 * 
 * <h3>Example Usage:</h3>
 * <pre>
 * &#64;SpringBootTest(
 *     classes = CefTestApplication.class,
 *     properties = {
 *         "cef.graph.store=neo4j",
 *         "cef.vector.store=neo4j"
 *     }
 * )
 * &#64;Import({Neo4jDriverConfiguration.class, VllmTestConfiguration.class})
 * &#64;ActiveProfiles("neo4j")
 * class Neo4jBenchmarkIT extends AbstractBenchmarkIT { ... }
 * </pre>
 * 
 * <h3>What @TestConfiguration Should Provide:</h3>
 * <ul>
 *   <li>External connections (Neo4j Driver, DataSource for Testcontainers)</li>
 *   <li>LLM clients (ChatClient.Builder for vLLM/Ollama)</li>
 *   <li>Nothing else - let auto-configuration handle GraphStore, ChunkStore, etc.</li>
 * </ul>
 * 
 * @author mrmanna
 * @since v0.6
 */
@SpringBootApplication(exclude = {
    // Exclude R2DBC by default - PostgreSQL tests import it explicitly
    org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration.class,
    org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration.class,
    org.springframework.boot.autoconfigure.r2dbc.R2dbcTransactionManagerAutoConfiguration.class
})
public class CefTestApplication {
    // Single test bootstrap - no component scan filters needed.
    // Bean selection is driven by:
    // 1. cef.graph.store / cef.vector.store properties → auto-configuration
    // 2. @TestConfiguration via @Import → external services only
    // 3. application-{profile}.yml → additional properties
}
