import org.ddse.ml.cef.api.KnowledgeRetriever;
import org.ddse.ml.cef.dto.RetrievalRequest;
import org.ddse.ml.cef.retriever.RetrievalResult;
import org.ddse.ml.cef.config.OllamaLlmTestConfiguration;
import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.repository.duckdb.ChunkStore;
import org.ddse.ml.cef.graph.GraphStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Knowledge Retriever with real Ollama services.
 * Tests semantic search and retrieval with live qwq:32b LLM and
 * nomic-embed-text embeddings.
 * 
 * Prerequisites:
 * - Ollama running on localhost:11434
 * - Models: qwq:32b, nomic-embed-text:latest
 * 
 * Run: mvn test -Dtest=OllamaKnowledgeRetrieverIntegrationTest
 * -Dollama.integration=true
 */
@SpringBootTest(properties = {

        "spring.sql.init.mode=never"
})
@Import(OllamaLlmTestConfiguration.class)
@ActiveProfiles("ollama-integration")
@EnabledIfSystemProperty(named = "ollama.integration", matches = "true")
class OllamaKnowledgeRetrieverIntegrationTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private GraphStore graphStore;

    @Autowired
    private ChunkStore chunkStore;

    @Autowired
    private KnowledgeRetriever retriever;

    @Test
    void shouldGenerateEmbeddingsForSemanticSearch() {
        // Given
        String query = "What are the symptoms of diabetes?";

        // When - Generate embeddings using real Ollama
        StepVerifier.create(
                Mono.fromCallable(() -> embeddingModel.embed(query)))
                .assertNext(embedding -> {
                    // Then - Verify real 768-dim nomic-embed-text embedding
                    assertThat(embedding).isNotNull();
                    assertThat(embedding).hasSize(768);

                    double sum = 0;
                    for (float v : embedding) {
                        sum += Math.abs(v);
                    }
                    assertThat(sum).isGreaterThan(0);
                })
                .verifyComplete();
    }

    @Test
    void shouldRetrieveSemanticallySimilarChunks() {
        // Given - Store test chunks with embeddings
        UUID nodeId = UUID.randomUUID();
        Node node = new Node();
        node.setId(nodeId);
        node.setLabel("MedicalCondition");
        node.setProperties(Map.of("name", "Diabetes"));

        // Medical knowledge chunks
        List<String> documents = List.of(
                "Diabetes symptoms include increased thirst, frequent urination, and fatigue",
                "Type 2 diabetes is characterized by high blood sugar and insulin resistance",
                "Weather forecast shows sunny conditions tomorrow" // Unrelated
        );

        // When - Store with real embeddings
        Flux<Chunk> chunks = Flux.fromIterable(documents)
                .flatMap(doc -> Mono.fromCallable(() -> {
                    float[] embedding = embeddingModel.embed(doc);
                    Chunk chunk = new Chunk();
                    chunk.setId(UUID.randomUUID());
                    chunk.setContent(doc);
                    chunk.setEmbedding(embedding);
                    chunk.setLinkedNodeId(nodeId);
                    return chunk;
                }))
                .flatMap(chunkStore::save);

        // Then - Query should find diabetes-related chunks, not weather
        String query = "What are diabetes symptoms?";

        StepVerifier.create(chunks.then(
                retriever.retrieve(RetrievalRequest.builder().query(query).topK(2).build())))
                .assertNext(result -> {
                    assertThat(result.getChunks()).hasSize(2);
                    assertThat(result.getStrategy()).isEqualTo(
                            RetrievalResult.RetrievalStrategy.VECTOR_ONLY);

                    // Top results should be about diabetes, not weather
                    String content1 = result.getChunks().get(0).getContent();
                    String content2 = result.getChunks().get(1).getContent();

                    assertThat(content1.toLowerCase()).contains("diabetes");
                    assertThat(content2.toLowerCase()).contains("diabetes");
                    assertThat(content1).doesNotContain("weather");
                    assertThat(content2).doesNotContain("weather");
                })
                .verifyComplete();
    }

    @Test
    void shouldCalculateCosineSimilarity() {
        // Given - Two semantically similar queries
        String query1 = "machine learning algorithms";
        String query2 = "AI and deep learning methods";
        String query3 = "cooking recipes for pasta";

        // When - Generate embeddings
        float[] emb1 = embeddingModel.embed(query1);
        float[] emb2 = embeddingModel.embed(query2);
        float[] emb3 = embeddingModel.embed(query3);

        // Then - Similar topics should have high cosine similarity
        double similarity12 = cosineSimilarity(emb1, emb2);
        double similarity13 = cosineSimilarity(emb1, emb3);

        System.out.println("ML vs AI similarity: " + similarity12);
        System.out.println("ML vs Cooking similarity: " + similarity13);

        assertThat(similarity12).isGreaterThan(similarity13);
        assertThat(similarity12).isGreaterThan(0.5); // Related topics
        assertThat(similarity13).isLessThan(0.5); // Unrelated topics
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
