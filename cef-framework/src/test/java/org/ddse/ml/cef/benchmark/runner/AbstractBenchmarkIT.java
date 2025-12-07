package org.ddse.ml.cef.benchmark.runner;

import org.ddse.ml.cef.api.KnowledgeRetriever;
import org.ddse.ml.cef.benchmark.core.*;
import org.ddse.ml.cef.benchmark.dataset.MedicalDataset;
import org.ddse.ml.cef.benchmark.dataset.SapDataset;
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

import java.io.IOException;

/**
 * Abstract base class for all benchmark integration tests.
 * 
 * <h3>Purpose:</h3>
 * <p>Provides a consistent pattern for benchmark tests across all backends.
 * All backend-specific beans are injected via Spring - no manual bean creation.</p>
 * 
 * <h3>Bean Management Strategy:</h3>
 * <ul>
 *   <li>Auto-configuration selects beans via {@code cef.graph.store} and {@code cef.vector.store} properties</li>
 *   <li>Backend-specific {@code @TestConfiguration} only overrides what's necessary</li>
 *   <li>LLM beans provided by {@code VllmTestConfiguration} or {@code OllamaLlmTestConfiguration}</li>
 *   <li>No duplicate {@code @Primary} beans - rely on property-driven selection</li>
 * </ul>
 * 
 * <h3>Usage Pattern:</h3>
 * <pre>
 * &#64;SpringBootTest(classes = CefTestApplication.class, properties = {
 *     "cef.graph.store=neo4j",
 *     "cef.vector.store=neo4j"
 * })
 * &#64;Import({Neo4jTestConfiguration.class, VllmTestConfiguration.class})
 * &#64;ActiveProfiles({"neo4j"})
 * class Neo4jBenchmarkIT extends AbstractBenchmarkIT {
 *     &#64;Override
 *     protected String getBackendName() { return "neo4j"; }
 * }
 * </pre>
 * 
 * @author mrmanna
 * @since v0.6
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractBenchmarkIT {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // ==================== Injected Beans ====================
    // All beans come from auto-configuration + @TestConfiguration
    // No manual creation - Spring manages everything

    @Autowired
    protected GraphStore graphStore;

    @Autowired
    protected ChunkStore chunkStore;

    @Autowired
    protected KnowledgeRetriever retriever;

    @Autowired
    protected EmbeddingModel embeddingModel;

    @Autowired
    protected ChatClient.Builder chatClientBuilder;

    @Autowired
    protected McpContextTool mcpContextTool;

    // ==================== Benchmark Infrastructure ====================

    protected BenchmarkRunner runner;
    protected BenchmarkResultWriter writer;
    protected BenchmarkConfig config;

    /**
     * Returns the backend name for result labeling.
     * Subclasses must implement this.
     */
    protected abstract String getBackendName();

    /**
     * Optional hook for backend-specific setup.
     * Called after Spring context is initialized but before benchmark runs.
     */
    protected void onSetup() {
        // Override in subclass if needed
    }

    /**
     * Optional hook for backend-specific teardown.
     */
    protected void onTeardown() {
        // Override in subclass if needed
    }

    @BeforeAll
    void setup() {
        logger.info("=== Setting up {} benchmark ===", getBackendName());
        logger.info("GraphStore: {}", graphStore.getClass().getSimpleName());
        logger.info("ChunkStore: {}", chunkStore.getClass().getSimpleName());
        logger.info("Retriever: {}", retriever.getClass().getSimpleName());
        logger.info("EmbeddingModel: {}", embeddingModel.getClass().getSimpleName());

        config = createBenchmarkConfig();

        // Create indexer - uses injected beans
        KnowledgeIndexer indexer = new DefaultKnowledgeIndexer(
                graphStore, null, null, chunkStore, embeddingModel);

                // Create runner with Direct mode: Query → KnowledgeRetriever
        runner = new BenchmarkRunner(
                getBackendName(),
                graphStore,
                chunkStore,
                indexer,
                retriever,
                config);


        writer = new BenchmarkResultWriter(config);

        // Call subclass hook
        onSetup();

        logger.info("=== {} benchmark setup complete ===", getBackendName());
    }

    @AfterAll
    void teardown() {
        logger.info("=== Tearing down {} benchmark ===", getBackendName());
        onTeardown();
        logger.info("=== {} benchmark teardown complete ===", getBackendName());
    }

    /**
     * Creates benchmark configuration.
     * Override to customize warmup/measured iterations.
     */
    protected BenchmarkConfig createBenchmarkConfig() {
        return BenchmarkConfig.defaults()
                .withWarmupIterations(2)
                .withMeasuredIterations(5);
    }

    // ==================== Standard Benchmark Tests ====================

    @Test
    @Order(1)
    @DisplayName("Run Medical Benchmarks")
    void runMedicalBenchmarks() throws IOException {
        MedicalDataset dataset = BenchmarkDataCache.getInstance().getMedicalDataset();
        logger.info("Running medical benchmarks with {} nodes, {} edges, {} chunks",
                dataset.getNodeCount(), dataset.getEdgeCount(), dataset.getChunks().size());

        BenchmarkResult result = runner.runMedicalBenchmarks(dataset);
        writer.writeResults(result);

        // Assertions
        Assertions.assertNotNull(result);
        Assertions.assertEquals(getBackendName(), result.getBackend());
        Assertions.assertEquals("medical", result.getDataset());
        Assertions.assertFalse(result.getScenarios().isEmpty());

        logSummary(result);
    }

    @Test
    @Order(2)
    @DisplayName("Run SAP Benchmarks")
    void runSapBenchmarks() throws IOException {
        SapDataset dataset = BenchmarkDataCache.getInstance().getSapDataset();
        logger.info("Running SAP benchmarks with {} nodes, {} edges, {} chunks",
                dataset.getNodeCount(), dataset.getEdgeCount(), dataset.getChunks().size());

        BenchmarkResult result = runner.runSapBenchmarks(dataset);
        writer.writeResults(result);

        // Assertions
        Assertions.assertNotNull(result);
        Assertions.assertEquals(getBackendName(), result.getBackend());
        Assertions.assertEquals("sap", result.getDataset());
        Assertions.assertFalse(result.getScenarios().isEmpty());

        logSummary(result);
    }

    private void logSummary(BenchmarkResult result) {
        logger.info("=== {} {} benchmarks complete ===", getBackendName(), result.getDataset());
        logger.info("Summary: avgChunkImprovement={}%, avgLatencyOverhead={}%",
                String.format("%.1f", result.getSummary().getAvgChunkImprovement()),
                String.format("%.1f", result.getSummary().getAvgLatencyOverhead()));
    }
}
