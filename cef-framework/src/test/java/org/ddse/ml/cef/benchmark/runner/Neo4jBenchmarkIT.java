package org.ddse.ml.cef.benchmark.runner;

import org.ddse.ml.cef.CefTestApplication;
import org.ddse.ml.cef.api.KnowledgeRetriever;
import org.ddse.ml.cef.benchmark.core.*;
import org.ddse.ml.cef.benchmark.dataset.MedicalDataset;
import org.ddse.ml.cef.benchmark.dataset.SapDataset;
import org.ddse.ml.cef.config.Neo4jTestConfiguration;
import org.ddse.ml.cef.config.VllmTestConfiguration;
import org.ddse.ml.cef.graph.GraphStore;
import org.ddse.ml.cef.indexer.DefaultKnowledgeIndexer;
import org.ddse.ml.cef.indexer.KnowledgeIndexer;
import org.ddse.ml.cef.mcp.McpContextTool;
import org.ddse.ml.cef.repository.duckdb.ChunkStore;
import org.junit.jupiter.api.*;
import org.neo4j.driver.Driver;
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
 * Benchmark runner for Neo4j backend using live Neo4j infrastructure.
 * Uses Neo4j for BOTH graph store AND vector store.
 * 
 * <h3>Industry Standard Pattern:</h3>
 * <p>This test follows Spring Boot testing best practices:</p>
 * <ul>
 *   <li>{@code CefTestApplication} - single bootstrap for all tests</li>
 *   <li>{@code Neo4jTestConfiguration} - backend-specific {@code @TestConfiguration}</li>
 *   <li>{@code application-neo4j.yml} - profile-based properties</li>
 *   <li>Property-driven bean selection via {@code cef.graph.store=neo4j}</li>
 * </ul>
 * 
 * <h3>Architecture:</h3>
 * <p>Query → LLM (vLLM/Qwen3) → MCP Tool Call → KnowledgeRetriever</p>
 * 
 * <h3>Prerequisites:</h3>
 * <ul>
 *   <li>Neo4j 5.11+ running on localhost:7687 (for vector index support)</li>
 *   <li>Ollama running on localhost:11434 with nomic-embed-text model</li>
 *   <li>vLLM running on localhost:8001 with Qwen3-Coder model</li>
 * </ul>
 * 
 * <h3>Start Neo4j with Docker:</h3>
 * <pre>
 * docker run -d --name neo4j \
 *   -p 7474:7474 -p 7687:7687 \
 *   -e NEO4J_AUTH=none \
 *   neo4j:5.18-community
 * </pre>
 * 
 * <h3>Configuration:</h3>
 * <ul>
 *   <li>Vector Store: Neo4j with vector indexes (cef.vector.store=neo4j)</li>
 *   <li>Graph Store: Neo4j native graph (cef.graph.store=neo4j)</li>
 *   <li>Embedding: Ollama nomic-embed-text (768 dimensions)</li>
 *   <li>LLM: vLLM Qwen3-Coder-30B with MCP tool calling</li>
 * </ul>
 *
 * @author mrmanna
 * @since v0.6
 */
@SpringBootTest(
    classes = CefTestApplication.class,
    properties = {
        "cef.graph.store=neo4j",
        "cef.vector.store=neo4j",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration,org.springframework.boot.actuate.autoconfigure.jdbc.DataSourceHealthContributorAutoConfiguration"
    }
)
@Import({Neo4jTestConfiguration.class, VllmTestConfiguration.class})
@ActiveProfiles({"vllm-integration", "neo4j"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Benchmark: Neo4j Backend (Graph + Vectors)")
class Neo4jBenchmarkIT {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jBenchmarkIT.class);
    private static final String BACKEND_NAME = "neo4j";

    // All beans injected via Spring - no manual creation!
    @Autowired
    private Driver neo4jDriver;

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

    private KnowledgeIndexer indexer;
    private BenchmarkRunner runner;
    private BenchmarkResultWriter writer;
    private BenchmarkConfig config;

    @BeforeAll
    void setup() {
        logger.info("Setting up Neo4j benchmark with live infrastructure");
        logger.info("Neo4j Driver connected: {}", neo4jDriver != null);
        logger.info("GraphStore type: {}", graphStore.getClass().getSimpleName());
        logger.info("ChunkStore type: {}", chunkStore.getClass().getSimpleName());

        config = BenchmarkConfig.defaults()
                .withWarmupIterations(2)
                .withMeasuredIterations(5);

        indexer = new DefaultKnowledgeIndexer(graphStore, null, null, chunkStore, embeddingModel);
        
        // Use Direct mode: Query → KnowledgeRetriever
        runner = new BenchmarkRunner(BACKEND_NAME, graphStore, chunkStore, indexer, retriever, config);
        writer = new BenchmarkResultWriter(config);

        logger.info("Neo4j benchmark runner initialized in Direct Mode");
    }

    @AfterAll
    void teardown() {
        // Driver lifecycle managed by Spring - no manual close needed
        logger.info("Neo4j benchmark teardown complete");
    }

    @Test
    @Order(1)
    @DisplayName("Run Medical Benchmarks on Neo4j")
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

        logger.info("Neo4j medical benchmarks complete. Summary: avgChunkImprovement={}%, avgLatencyOverhead={}%",
                String.format("%.1f", result.getSummary().getAvgChunkImprovement()),
                String.format("%.1f", result.getSummary().getAvgLatencyOverhead()));
    }

    @Test
    @Order(2)
    @DisplayName("Run SAP Benchmarks on Neo4j")
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

        logger.info("Neo4j SAP benchmarks complete. Summary: avgChunkImprovement={}%, avgLatencyOverhead={}%",
                String.format("%.1f", result.getSummary().getAvgChunkImprovement()),
                String.format("%.1f", result.getSummary().getAvgLatencyOverhead()));
    }
}
