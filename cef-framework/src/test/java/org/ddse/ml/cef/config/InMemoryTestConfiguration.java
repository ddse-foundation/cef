package org.ddse.ml.cef.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;

/**
 * Test configuration for In-Memory backend.
 * 
 * <h3>Design:</h3>
 * <p>For in-memory backend, auto-configuration ({@code GraphStoreAutoConfiguration} and
 * {@code ChunkStoreAutoConfiguration}) provides ALL necessary beans when
 * {@code cef.graph.store=in-memory} and {@code cef.vector.store=in-memory} are set.</p>
 * 
 * <p>This TestConfiguration is intentionally minimal - it exists only for consistency
 * with other backend configurations and to provide a place for any test-specific
 * customizations if needed in the future.</p>
 * 
 * <h3>Beans provided by auto-configuration:</h3>
 * <ul>
 *   <li>{@code GraphStore} - InMemoryGraphStore (JGraphT-based)</li>
 *   <li>{@code ChunkStore} - InMemoryChunkStore (ConcurrentHashMap-based)</li>
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
 *     "cef.graph.store=in-memory",
 *     "cef.vector.store=in-memory"
 * })
 * &#64;Import({InMemoryTestConfiguration.class, VllmTestConfiguration.class})
 * &#64;ActiveProfiles({"vllm-integration", "inmemory"})
 * class InMemoryBenchmarkIT extends AbstractBenchmarkIT { ... }
 * </pre>
 * 
 * @author mrmanna
 * @since v0.6
 */
@TestConfiguration
public class InMemoryTestConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTestConfiguration.class);

    // No beans needed - auto-configuration provides everything for in-memory backend
    // This class exists for:
    // 1. Consistency with other backend TestConfigurations
    // 2. Placeholder for any future test-specific customizations
}
