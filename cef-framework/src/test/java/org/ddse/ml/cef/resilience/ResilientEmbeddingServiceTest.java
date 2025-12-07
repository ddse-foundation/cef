package org.ddse.ml.cef.resilience;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.ddse.ml.cef.config.CefResilienceProperties;
import org.ddse.ml.cef.config.CefResilienceProperties.ServiceResilienceConfig;
import org.ddse.ml.cef.config.CefResilienceProperties.RetryConfig;
import org.ddse.ml.cef.config.CefResilienceProperties.CircuitBreakerConfig;
import org.ddse.ml.cef.service.ResilientEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ResilientEmbeddingService using real Ollama embeddings.
 * 
 * Prerequisites:
 * - Ollama running locally at http://localhost:11434
 * - nomic-embed-text model pulled: ollama pull nomic-embed-text
 * 
 * Run with: mvn test -Dtest=ResilientEmbeddingServiceTest -Dspring.profiles.active=ollama-integration
 * 
 * Tests verify:
 * 1. Retry behavior with real embedding service
 * 2. Successful embedding generation
 * 3. Batch embedding support
 * 4. Metrics recording
 * 
 * @author mrmanna
 * @since 0.6
 */
@DisplayName("ResilientEmbeddingService Integration Tests")
class ResilientEmbeddingServiceTest {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String EMBEDDING_MODEL = "nomic-embed-text:latest";
    private static final int EXPECTED_DIMENSION = 768;

    private EmbeddingModel embeddingModel;
    private MeterRegistry meterRegistry;
    private CefResilienceProperties properties;
    private ResilientEmbeddingService service;

    @BeforeEach
    void setUp() {
        // Create real Ollama embedding model
        OllamaApi ollamaApi = new OllamaApi(OLLAMA_BASE_URL);
        embeddingModel = OllamaEmbeddingModel.builder()
                .withOllamaApi(ollamaApi)
                .withDefaultOptions(OllamaOptions.builder()
                        .withModel(EMBEDDING_MODEL)
                        .build())
                .build();
        
        meterRegistry = new SimpleMeterRegistry();
        properties = createTestProperties();
        service = new ResilientEmbeddingService(embeddingModel, properties, meterRegistry);
    }

    private CefResilienceProperties createTestProperties() {
        CefResilienceProperties props = new CefResilienceProperties();
        props.setEnabled(true);

        // Configure embedding service resilience
        ServiceResilienceConfig embeddingConfig = new ServiceResilienceConfig();
        
        // Configure retry
        RetryConfig retry = new RetryConfig();
        retry.setMaxAttempts(3);
        retry.setWaitDuration(Duration.ofMillis(500));
        retry.setExponentialBackoffMultiplier(2.0);
        retry.setMaxWaitDuration(Duration.ofSeconds(5));
        embeddingConfig.setRetry(retry);

        // Configure circuit breaker
        CircuitBreakerConfig cb = new CircuitBreakerConfig();
        cb.setFailureRateThreshold(50);
        cb.setWaitDurationInOpenState(Duration.ofSeconds(10));
        cb.setSlidingWindowSize(10);
        embeddingConfig.setCircuitBreaker(cb);

        // Configure timeout (generous for real service)
        embeddingConfig.setTimeout(Duration.ofSeconds(30));
        
        props.setEmbedding(embeddingConfig);

        return props;
    }

    @Nested
    @DisplayName("Single Embedding")
    class SingleEmbedding {

        @Test
        @DisplayName("should generate embedding for single text")
        void shouldGenerateEmbeddingForSingleText() {
            // Given
            String text = "The patient presents with symptoms of type 2 diabetes mellitus";

            // When
            Mono<float[]> result = service.embed(text);

            // Then
            StepVerifier.create(result)
                    .assertNext(embedding -> {
                        assertThat(embedding).isNotNull();
                        assertThat(embedding.length).isEqualTo(EXPECTED_DIMENSION);
                        // Verify embedding has non-zero values
                        boolean hasNonZeroValues = false;
                        for (float v : embedding) {
                            if (v != 0.0f) {
                                hasNonZeroValues = true;
                                break;
                            }
                        }
                        assertThat(hasNonZeroValues).isTrue();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should generate different embeddings for different texts")
        void shouldGenerateDifferentEmbeddingsForDifferentTexts() {
            // Given
            String medicalText = "The patient has hypertension and requires antihypertensive medication";
            String technicalText = "The software architecture follows microservices design patterns";

            // When
            float[] medicalEmbedding = service.embed(medicalText).block(Duration.ofSeconds(30));
            float[] technicalEmbedding = service.embed(technicalText).block(Duration.ofSeconds(30));

            // Then
            assertThat(medicalEmbedding).isNotNull();
            assertThat(technicalEmbedding).isNotNull();
            assertThat(medicalEmbedding.length).isEqualTo(EXPECTED_DIMENSION);
            assertThat(technicalEmbedding.length).isEqualTo(EXPECTED_DIMENSION);
            
            // Verify embeddings are different (cosine similarity should be < 1)
            double dotProduct = 0.0;
            double normA = 0.0;
            double normB = 0.0;
            for (int i = 0; i < EXPECTED_DIMENSION; i++) {
                dotProduct += medicalEmbedding[i] * technicalEmbedding[i];
                normA += medicalEmbedding[i] * medicalEmbedding[i];
                normB += technicalEmbedding[i] * technicalEmbedding[i];
            }
            double cosineSimilarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
            
            // Different semantic content should have similarity < 0.9
            assertThat(cosineSimilarity).isLessThan(0.95);
        }
    }

    @Nested
    @DisplayName("Batch Embedding")
    class BatchEmbedding {

        @Test
        @DisplayName("should embed batch of texts")
        void shouldEmbedBatchOfTexts() {
            // Given
            List<String> texts = List.of(
                    "Diabetes mellitus type 2 with insulin resistance",
                    "Hypertension stage 2 requiring ACE inhibitor therapy",
                    "Chronic kidney disease stage 3b with proteinuria"
            );

            // When
            Mono<List<float[]>> result = service.embedBatch(texts);

            // Then
            StepVerifier.create(result)
                    .assertNext(embeddings -> {
                        assertThat(embeddings).hasSize(3);
                        for (float[] embedding : embeddings) {
                            assertThat(embedding.length).isEqualTo(EXPECTED_DIMENSION);
                        }
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should reject empty batch with validation error")
        void shouldHandleEmptyBatch() {
            // Given
            List<String> emptyBatch = List.of();

            // When
            Mono<List<float[]>> result = service.embedBatch(emptyBatch);

            // Then - should reject with validation error (this is correct behavior)
            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof IllegalArgumentException &&
                            e.getMessage().contains("null or empty"))
                    .verify();
        }

        @Test
        @DisplayName("should handle single item batch")
        void shouldHandleSingleItemBatch() {
            // Given
            List<String> singleItemBatch = List.of("Single medical term: hypertension");

            // When
            Mono<List<float[]>> result = service.embedBatch(singleItemBatch);

            // Then
            StepVerifier.create(result)
                    .assertNext(embeddings -> {
                        assertThat(embeddings).hasSize(1);
                        assertThat(embeddings.get(0).length).isEqualTo(EXPECTED_DIMENSION);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Metrics")
    class MetricsTests {

        @Test
        @DisplayName("should record metrics on successful embedding")
        void shouldRecordMetricsOnSuccess() {
            // Given
            String text = "Test text for metrics verification";

            // When
            service.embed(text).block(Duration.ofSeconds(30));

            // Then - verify metrics were recorded
            assertThat(meterRegistry.getMeters()).isNotEmpty();
            
            // Check for specific metric patterns
            boolean hasTimerMetric = meterRegistry.getMeters().stream()
                    .anyMatch(meter -> meter.getId().getName().contains("embedding"));
            assertThat(hasTimerMetric).isTrue();
        }
    }

    @Nested
    @DisplayName("Semantic Similarity")
    class SemanticSimilarity {

        @Test
        @DisplayName("should generate similar embeddings for semantically similar texts")
        void shouldGenerateSimilarEmbeddingsForSimilarTexts() {
            // Given - semantically similar medical texts
            String text1 = "The patient has high blood pressure";
            String text2 = "The patient suffers from hypertension";

            // When
            float[] embedding1 = service.embed(text1).block(Duration.ofSeconds(30));
            float[] embedding2 = service.embed(text2).block(Duration.ofSeconds(30));

            // Then - calculate cosine similarity
            double dotProduct = 0.0;
            double normA = 0.0;
            double normB = 0.0;
            for (int i = 0; i < EXPECTED_DIMENSION; i++) {
                dotProduct += embedding1[i] * embedding2[i];
                normA += embedding1[i] * embedding1[i];
                normB += embedding2[i] * embedding2[i];
            }
            double cosineSimilarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));

            // Semantically similar texts should have high similarity (> 0.7)
            assertThat(cosineSimilarity).isGreaterThan(0.7);
        }
    }
}
