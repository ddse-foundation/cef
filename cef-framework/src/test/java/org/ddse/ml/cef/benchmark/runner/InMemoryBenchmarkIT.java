package org.ddse.ml.cef.benchmark.runner;

import org.ddse.ml.cef.CefTestApplication;
import org.ddse.ml.cef.api.KnowledgeRetriever;
import org.ddse.ml.cef.benchmark.core.*;
import org.ddse.ml.cef.benchmark.dataset.MedicalDataset;
import org.ddse.ml.cef.benchmark.dataset.SapDataset;
import org.ddse.ml.cef.config.InMemoryTestConfiguration;
import org.ddse.ml.cef.config.VllmTestConfiguration;
import org.ddse.ml.cef.graph.GraphStore;
import org.ddse.ml.cef.indexer.DefaultKnowledgeIndexer;
import org.ddse.ml.cef.indexer.KnowledgeIndexer;
import org.ddse.ml.cef.mcp.McpContextTool;
import org.ddse.ml.cef.repository.duckdb.ChunkStore;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

/**
 * Benchmark runner for fully in-memory backend (graph + vectors).
 * Zero external dependencies - no database required.
 * 
 * <h3>Industry Standard Pattern:</h3>
 * <p>This test follows Spring Boot testing best practices:</p>
 * <ul>
 *   <li>{@code CefTestApplication} - single bootstrap for all tests</li>
 *   <li>{@code InMemoryTestConfiguration} - backend-specific {@code @TestConfiguration}</li>
 *   <li>{@code application-inmemory.yml} - profile-based properties</li>
 *   <li>Property-driven bean selection via {@code cef.graph.store=in-memory}</li>
 * </ul>
 * 
 * <h3>Architecture:</h3>
 * <p>Query → LLM (vLLM/Qwen3) → MCP Tool Call → KnowledgeRetriever</p>
 * 
 * <h3>Prerequisites:</h3>
 * <ul>
 *   <li>Ollama running on localhost:11434 with nomic-embed-text model</li>
 *   <li>vLLM running on localhost:8001 with Qwen3-Coder model</li>
 * </ul>
 * 
 * <h3>Configuration:</h3>
 * <ul>
 *   <li>Vector Store: InMemory ConcurrentHashMap (cef.vector.store=in-memory)</li>
 *   <li>Graph Store: InMemory JGraphT (cef.graph.store=in-memory)</li>
 *   <li>Embedding: Ollama nomic-embed-text (768 dimensions)</li>
 *   <li>LLM: vLLM Qwen3-Coder-30B with MCP tool calling</li>
 * </ul>
 * 
 * <h3>Use Cases:</h3>
 * <ul>
 *   <li>Development and prototyping</li>
 *   <li>Unit testing without database setup</li>
 *   <li>CI/CD pipelines</li>
 *   <li>Small datasets (&lt;10k chunks)</li>
 * </ul>
 *
 * @author mrmanna
 * @since v0.6
 */
@SpringBootTest(
    classes = CefTestApplication.class,
    properties = {
        "cef.graph.store=in-memory",
        "cef.vector.store=in-memory",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.actuate.autoconfigure.jdbc.DataSourceHealthContributorAutoConfiguration"
    }
)
@Import({InMemoryTestConfiguration.class, VllmTestConfiguration.class})
@ActiveProfiles({"vllm-integration", "inmemory"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Benchmark: InMemory Backend (Graph + Vectors)")
class InMemoryBenchmarkIT {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryBenchmarkIT.class);
    private static final String BACKEND_NAME = "inmemory";

    // All beans injected via Spring - consistent with other backends
    @Autowired
    private GraphStore graphStore;

    @Autowired
    private ChunkStore chunkStore;

    @Autowired
    private KnowledgeRetriever retriever;

    @Autowired
    private EmbeddingModel embeddingModel;

    // @Autowired
    // private ChatClient.Builder chatClientBuilder;

    // @Autowired
    // private McpContextTool mcpContextTool;

    @org.springframework.boot.test.mock.mockito.MockBean
    private javax.sql.DataSource dataSource;

    private KnowledgeIndexer indexer;
    private BenchmarkRunner runner;
    private BenchmarkResultWriter writer;
    private BenchmarkConfig config;

    @BeforeAll
    void setup() {
        logger.info("Setting up InMemory benchmark");
        logger.info("GraphStore type: {}", graphStore.getClass().getSimpleName());
        logger.info("ChunkStore type: {}", chunkStore.getClass().getSimpleName());

        config = BenchmarkConfig.defaults()
                .withWarmupIterations(2)
                .withMeasuredIterations(5);

        indexer = new DefaultKnowledgeIndexer(graphStore, null, null, chunkStore, embeddingModel);
        
        // Use Direct mode: Query → KnowledgeRetriever (No LLM/MCP)
        runner = new BenchmarkRunner(BACKEND_NAME, graphStore, chunkStore, indexer, retriever, config);
        writer = new BenchmarkResultWriter(config);

        logger.info("InMemory benchmark runner initialized in Direct Mode (No LLM/MCP)");
    }

    @Test
    @Order(1)
    @DisplayName("Run Medical Benchmarks on InMemory Graph")
    void runMedicalBenchmarks() throws IOException {
        // Get cached dataset
        MedicalDataset dataset = BenchmarkDataCache.getInstance().getMedicalDataset();
        logger.info("Using cached medical dataset: {}", dataset);

        // Run benchmarks
        BenchmarkResult result = runner.runMedicalBenchmarks(dataset);

        // Write results
        writer.writeResults(result);

        // Assertions
        Assertions.assertNotNull(result);
        Assertions.assertEquals(BACKEND_NAME, result.getBackend());
        Assertions.assertEquals("medical", result.getDataset());
        Assertions.assertFalse(result.getScenarios().isEmpty());

        logger.info("InMemory medical benchmarks complete. Summary: avgChunkImprovement={}%, avgLatencyOverhead={}%",
                String.format("%.1f", result.getSummary().getAvgChunkImprovement()),
                String.format("%.1f", result.getSummary().getAvgLatencyOverhead()));
    }

    @Test
    @Order(2)
    @DisplayName("Run SAP Benchmarks on InMemory Graph")
    void runSapBenchmarks() throws IOException {
        // Get cached dataset
        SapDataset dataset = BenchmarkDataCache.getInstance().getSapDataset();
        logger.info("Using cached SAP dataset: {}", dataset);

        // Run benchmarks
        BenchmarkResult result = runner.runSapBenchmarks(dataset);

        // Write results
        writer.writeResults(result);

        // Assertions
        Assertions.assertNotNull(result);
        Assertions.assertEquals(BACKEND_NAME, result.getBackend());
        Assertions.assertEquals("sap", result.getDataset());
        Assertions.assertFalse(result.getScenarios().isEmpty());

        logger.info("InMemory SAP benchmarks complete. Summary: avgChunkImprovement={}%, avgLatencyOverhead={}%",
                String.format("%.1f", result.getSummary().getAvgChunkImprovement()),
                String.format("%.1f", result.getSummary().getAvgLatencyOverhead()));
    }
}
