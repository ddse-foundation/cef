package org.ddse.ml.cef.benchmark.runner;

import io.r2dbc.postgresql.codec.Json;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.r2dbc.spi.ConnectionFactory;
import org.ddse.ml.cef.CefTestApplication;
import org.ddse.ml.cef.api.KnowledgeRetriever;
import org.ddse.ml.cef.benchmark.core.*;
import org.ddse.ml.cef.benchmark.dataset.MedicalDataset;
import org.ddse.ml.cef.benchmark.dataset.SapDataset;
import org.ddse.ml.cef.config.VllmTestConfiguration;
import org.ddse.ml.cef.graph.GraphStore;
import org.ddse.ml.cef.indexer.DefaultKnowledgeIndexer;
import org.ddse.ml.cef.indexer.KnowledgeIndexer;
import org.ddse.ml.cef.repository.duckdb.ChunkStore;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Collections;

/**
 * Benchmark runner for PostgreSQL backend using live infrastructure.
 * Uses PostgreSQL for BOTH graph store (JDBC) AND vector store (R2DBC with pgvector).
 * 
 * <p><b>Architecture:</b> Query → LLM (vLLM/Qwen3) → MCP Tool Call → KnowledgeRetriever</p>
 * 
 * <p><b>Configuration:</b></p>
 * <ul>
 *   <li>Vector Store: PostgreSQL pgvector via R2DBC (cef.vector.store=postgresql)</li>
 *   <li>Graph Store: PostgreSQL SQL via JDBC (cef.graph.store=pg-sql)</li>
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
        "spring.main.allow-bean-definition-overriding=true",
        "cef.vector.store=postgresql",
        "cef.graph.store=pg-sql"
    }
)
@Import({
    VllmTestConfiguration.class,
    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
    org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration.class,
    org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration.class,
    org.springframework.boot.autoconfigure.r2dbc.R2dbcTransactionManagerAutoConfiguration.class
})
@ActiveProfiles({"vllm-integration", "pg-sql"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Benchmark: PostgreSQL Backend (Graph + Vectors)")
class PgSqlBenchmarkIT {

    private static final Logger logger = LoggerFactory.getLogger(PgSqlBenchmarkIT.class);
    private static final String BACKEND_NAME = "pgsql";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private GraphStore graphStore;

    @Autowired
    private ChunkStore chunkStore;

    @Autowired
    private KnowledgeRetriever retriever;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private DatabaseClient databaseClient;

    private KnowledgeIndexer indexer;
    private BenchmarkRunner runner;
    private BenchmarkResultWriter writer;
    private BenchmarkConfig config;

    private void initializeSchema() {
        logger.info("Initializing R2DBC schema for pgvector (768 dimensions)...");
        String schema = """
            CREATE EXTENSION IF NOT EXISTS vector;
            CREATE TABLE IF NOT EXISTS chunks (
                id UUID PRIMARY KEY,
                content TEXT,
                embedding vector(768),
                linked_node_id UUID,
                metadata JSONB,
                created TIMESTAMP
            );
            """;
        databaseClient.sql(schema).then().block();
    }

    @BeforeAll
    void setup() {
        logger.info("Setting up PostgreSQL benchmark with live infrastructure");
        logger.info("GraphStore type: {}", graphStore.getClass().getSimpleName());
        logger.info("ChunkStore type: {}", chunkStore.getClass().getSimpleName());

        // Initialize Schema (Vector Store)
        initializeSchema();

        // Initialize GraphStore (creates cef_nodes and cef_edges tables)
        graphStore.initialize(Collections.emptyList()).block();

        config = BenchmarkConfig.defaults()
                .withWarmupIterations(2)
                .withMeasuredIterations(5);

        indexer = new DefaultKnowledgeIndexer(graphStore, null, null, chunkStore, embeddingModel);
        
        // Use Direct mode: Query → KnowledgeRetriever
        runner = new BenchmarkRunner(BACKEND_NAME, graphStore, chunkStore, indexer, retriever, config);
        writer = new BenchmarkResultWriter(config);

        logger.info("PostgreSQL benchmark runner initialized in Direct Mode");
    }

    @AfterAll
    void teardown() {
        logger.info("PostgreSQL benchmark teardown complete");
    }

    @Test
    @Order(1)
    @DisplayName("Run Medical Benchmarks on PostgreSQL SQL")
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

        logger.info("PostgreSQL SQL medical benchmarks complete. Summary: avgChunkImprovement={}%, avgLatencyOverhead={}%",
                String.format("%.1f", result.getSummary().getAvgChunkImprovement()),
                String.format("%.1f", result.getSummary().getAvgLatencyOverhead()));
    }

    @Test
    @Order(2)
    @DisplayName("Run SAP Benchmarks on PostgreSQL SQL")
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

        logger.info("PostgreSQL SQL SAP benchmarks complete. Summary: avgChunkImprovement={}%, avgLatencyOverhead={}%",
                String.format("%.1f", result.getSummary().getAvgChunkImprovement()),
                String.format("%.1f", result.getSummary().getAvgLatencyOverhead()));
    }

    @TestConfiguration
    static class R2dbcConfig {
        @Bean
        public R2dbcCustomConversions r2dbcCustomConversions(ObjectMapper objectMapper) {
            List<Converter<?, ?>> converters = new ArrayList<>();
            converters.add(new MapToJsonConverter(objectMapper));
            converters.add(new JsonToMapConverter(objectMapper));
            converters.add(new VectorToFloatArrayConverter());
            return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, converters);
        }

        @WritingConverter
        public class MapToJsonConverter implements Converter<Map<String, Object>, Json> {
            private final ObjectMapper objectMapper;

            public MapToJsonConverter(ObjectMapper objectMapper) {
                this.objectMapper = objectMapper;
            }

            @Override
            public Json convert(Map<String, Object> source) {
                try {
                    return Json.of(objectMapper.writeValueAsString(source));
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException("Cannot convert map to JSON", e);
                }
            }
        }

        @ReadingConverter
        public class JsonToMapConverter implements Converter<Json, Map<String, Object>> {
            private final ObjectMapper objectMapper;

            public JsonToMapConverter(ObjectMapper objectMapper) {
                this.objectMapper = objectMapper;
            }

            @Override
            public Map<String, Object> convert(Json source) {
                try {
                    return objectMapper.readValue(source.asString(), new TypeReference<Map<String, Object>>() {});
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException("Cannot convert JSON to map", e);
                }
            }
        }

        @ReadingConverter
        public class VectorToFloatArrayConverter implements Converter<io.r2dbc.postgresql.codec.Vector, float[]> {
            @Override
            public float[] convert(io.r2dbc.postgresql.codec.Vector source) {
                return source.getVector();
            }
        }
    }
}
