package org.ddse.ml.cef.base;

import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.fixtures.LegalDomainFixtures;
import org.ddse.ml.cef.fixtures.MedicalDomainFixtures;
import org.ddse.ml.cef.repository.postgres.ChunkRepository;
import org.ddse.ml.cef.repository.postgres.EdgeRepository;
import org.ddse.ml.cef.repository.postgres.NodeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Base class for LLM integration tests with real AI services.
 * Extends DuckDB test with LLM-specific setup and fixtures.
 * 
 * <p>
 * <b>Prerequisites:</b>
 * <ul>
 * <li>Ollama running on localhost:11434 with qwq:32b and nomic-embed-text
 * models</li>
 * <li>vLLM running on localhost:8001 with Qwen3-Coder-30B (for vLLM tests)</li>
 * <li>System property: -Dollama.integration=true or
 * -Dvllm.integration=true</li>
 * </ul>
 * 
 * <p>
 * <b>Features:</b>
 * <ul>
 * <li>Automatic LLM service verification before tests run</li>
 * <li>Pre-configured domain fixtures (Medical, Legal)</li>
 * <li>Database cleanup after each test</li>
 * <li>Empirical logging for review documentation</li>
 * </ul>
 * 
 * <p>
 * <b>Usage:</b>
 * 
 * <pre>
 * &#64;EnabledIfSystemProperty(named = "ollama.integration", matches = "true")
 * class MyLLMIntegrationTest extends BaseLLMIntegrationTest {
 * 
 *     &#64;Test
 *     void shouldGenerateEmbedding() {
 *         StepVerifier.create(
 *                 Mono.fromCallable(() -> embeddingModel.embed("test query")))
 *                 .assertNext(embedding -> {
 *                     assertThat(embedding).hasSize(768); // nomic-embed-text dimension
 *                 })
 *                 .verifyComplete();
 *     }
 * }
 * </pre>
 * 
 * @author mrmanna
 */
@SpringBootTest(properties = {
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-postgresql-simple.sql",
        "cef.database.type=postgresql"
})
@Testcontainers
public abstract class BaseLLMIntegrationTest {

    @Container
    protected static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" +
                postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    protected EmbeddingModel embeddingModel;

    @Autowired(required = false)
    protected ChatClient.Builder chatClientBuilder;

    @Autowired
    protected NodeRepository nodeRepository;

    @Autowired
    protected EdgeRepository edgeRepository;

    @Autowired
    protected ChunkRepository chunkRepository;

    @Autowired(required = false)
    protected MedicalDomainFixtures medicalFixtures;

    @Autowired(required = false)
    protected LegalDomainFixtures legalFixtures;

    /**
     * Verifies LLM services are reachable before running tests.
     * Fails fast if services are not available.
     */
    @BeforeAll
    static void verifyLLMServices() {
        Logger log = LoggerFactory.getLogger(BaseLLMIntegrationTest.class);

        // Check Ollama
        boolean ollamaEnabled = "true".equals(System.getProperty("ollama.integration"));
        if (ollamaEnabled) {
            log.info("Verifying Ollama service at localhost:11434...");
            if (!isServiceReachable("http://localhost:11434/api/tags", 5000)) {
                throw new IllegalStateException(
                        "Ollama is not reachable at localhost:11434. " +
                                "Start Ollama and ensure models are pulled: " +
                                "ollama pull qwq:32b && ollama pull nomic-embed-text:latest");
            }
            log.info("✓ Ollama service verified");
        }

        // Check vLLM
        boolean vllmEnabled = "true".equals(System.getProperty("vllm.integration"));
        if (vllmEnabled) {
            log.info("Verifying vLLM service at localhost:8001...");
            if (!isServiceReachable("http://localhost:8001/health", 5000)) {
                throw new IllegalStateException(
                        "vLLM is not reachable at localhost:8001. " +
                                "Start vLLM server with Qwen3-Coder-30B model.");
            }
            log.info("✓ vLLM service verified");
        }
    }

    /**
     * Checks if HTTP service is reachable.
     */
    private static boolean isServiceReachable(String url, int timeoutMs) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Cleans database before each test.
     * Ensures isolated test execution.
     */
    @BeforeEach
    void setupTestEnvironment() {
        logger.debug("Cleaning database before test...");

        chunkRepository.deleteAll().block();
        edgeRepository.deleteAll().block();
        nodeRepository.deleteAll().block();

        logger.debug("Database cleaned");
    }

    /**
     * Cleans up after each test.
     * Removes test data to prevent interference with subsequent tests.
     */
    @AfterEach
    void cleanupTestData() {
        logger.debug("Cleaning up test data...");

        chunkRepository.deleteAll().block();
        edgeRepository.deleteAll().block();
        nodeRepository.deleteAll().block();

        logger.debug("Test data cleaned");
    }

    /**
     * Helper method to save multiple nodes in batch.
     */
    protected void saveNodes(List<Node> nodes) {
        Flux.fromIterable(nodes)
                .flatMap(nodeRepository::save)
                .blockLast();

        logger.debug("Saved {} nodes", nodes.size());
    }

    /**
     * Helper method to save multiple edges in batch.
     */
    protected void saveEdges(List<Edge> edges) {
        Flux.fromIterable(edges)
                .flatMap(edgeRepository::save)
                .blockLast();

        logger.debug("Saved {} edges", edges.size());
    }

    /**
     * Helper method to save multiple chunks in batch.
     */
    protected void saveChunks(List<Chunk> chunks) {
        Flux.fromIterable(chunks)
                .flatMap(chunkRepository::save)
                .blockLast();

        logger.debug("Saved {} chunks", chunks.size());
    }

    /**
     * Calculates cosine similarity between two embedding vectors.
     * Useful for verifying semantic relevance in tests.
     * 
     * @param a First embedding vector
     * @param b Second embedding vector
     * @return Cosine similarity in range [-1, 1]
     */
    protected double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have same dimension");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Logs empirical test results for documentation.
     * Use this to capture evidence for international review.
     * 
     * @param testName Name of the test
     * @param query    User query
     * @param result   Result summary
     */
    protected void logEmpiricalResult(String testName, String query, String result) {
        logger.info("=== Empirical Test Result ===");
        logger.info("Test: {}", testName);
        logger.info("Query: {}", query);
        logger.info("Result: {}", result);
        logger.info("=============================");
    }
}
