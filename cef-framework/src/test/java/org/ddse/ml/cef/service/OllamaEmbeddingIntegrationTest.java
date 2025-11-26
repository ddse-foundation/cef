package org.ddse.ml.cef.service;

import org.ddse.ml.cef.config.EmbeddingIntegrationTestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for real Ollama embedding service.
 * Requires Ollama running locally with nomic-embed-text model.
 * Spring AI auto-configuration creates EmbeddingModel bean from properties
 * 
 * Run with: mvn test -Dtest=OllamaEmbeddingIntegrationTest
 * -Dembedding.integration=true
 */
@SpringBootTest(classes = { EmbeddingIntegrationTestConfig.class }, properties = {

        "spring.sql.init.mode=never",
        "spring.ai.ollama.base-url=http://localhost:11434",
        "spring.ai.ollama.embedding.options.model=nomic-embed-text:latest"
})
@ActiveProfiles("embedding-integration")
@EnabledIfSystemProperty(named = "embedding.integration", matches = "true")
class OllamaEmbeddingIntegrationTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void shouldGenerateRealEmbeddingFromOllama() {
        // Given
        String text = "This is a test document about machine learning";

        // When - Direct Spring AI call
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getResults()).hasSize(1);

        float[] embedding = response.getResults().get(0).getOutput();
        assertThat(embedding).isNotNull();
        assertThat(embedding.length).isEqualTo(768); // nomic-embed-text dimension

        // Verify non-zero embeddings
        double sum = 0;
        for (float v : embedding) {
            sum += Math.abs(v);
        }
        assertThat(sum).isGreaterThan(0);
    }

    @Test
    void shouldGenerateEmbeddingViaCEFService() {
        // Given
        String text = "Knowledge graph with semantic search";

        // When - CEF wrapper call
        StepVerifier.create(embeddingService.embed(text))
                .assertNext(embedding -> {
                    assertThat(embedding).isNotNull();
                    assertThat(embedding.length).isEqualTo(768);

                    // Verify embeddings are normalized/non-zero
                    double sum = 0;
                    for (float v : embedding) {
                        sum += Math.abs(v);
                    }
                    assertThat(sum).isGreaterThan(0);
                })
                .verifyComplete();
    }

    @Test
    void shouldGenerateBatchEmbeddings() {
        // Given
        List<String> texts = List.of(
                "First document about AI",
                "Second document about databases",
                "Third document about search");

        // When
        StepVerifier.create(embeddingService.embedBatch(texts))
                .assertNext(embeddings -> {
                    assertThat(embeddings).hasSize(3);

                    for (float[] embedding : embeddings) {
                        assertThat(embedding.length).isEqualTo(768);

                        double sum = 0;
                        for (float v : embedding) {
                            sum += Math.abs(v);
                        }
                        assertThat(sum).isGreaterThan(0);
                    }
                })
                .verifyComplete();
    }

    @Test
    void shouldProduceDifferentEmbeddingsForDifferentTexts() {
        // Given
        String text1 = "Machine learning and artificial intelligence";
        String text2 = "Database management systems";

        // When
        StepVerifier.create(
                embeddingService.embed(text1)
                        .zipWith(embeddingService.embed(text2)))
                .assertNext(tuple -> {
                    float[] emb1 = tuple.getT1();
                    float[] emb2 = tuple.getT2();

                    // Calculate cosine similarity (should be low for different topics)
                    double dotProduct = 0;
                    double norm1 = 0;
                    double norm2 = 0;

                    for (int i = 0; i < emb1.length; i++) {
                        dotProduct += emb1[i] * emb2[i];
                        norm1 += emb1[i] * emb1[i];
                        norm2 += emb2[i] * emb2[i];
                    }

                    double cosineSimilarity = dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));

                    // Different topics should have lower similarity
                    assertThat(cosineSimilarity).isBetween(-1.0, 1.0);
                    System.out.println("Cosine similarity: " + cosineSimilarity);
                })
                .verifyComplete();
    }
}
