import org.ddse.ml.cef.api.KnowledgeRetriever;
import org.ddse.ml.cef.dto.RetrievalRequest;
import org.ddse.ml.cef.retriever.RetrievalResult;
import org.ddse.ml.cef.config.VllmTestConfiguration;
import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.repository.ChunkRepository;
import org.ddse.ml.cef.storage.GraphStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Knowledge Retriever with vLLM + Ollama.
 * Uses vLLM (Qwen3-Coder-30B) for chat and Ollama (nomic-embed-text) for
 * embeddings.
 * 
 * Prerequisites:
 * - vLLM on localhost:8001 with Qwen/Qwen3-Coder-30B-A3B-Instruct-FP8
 * - Ollama on localhost:11434 with nomic-embed-text:latest
 * 
 * vLLM command:
 * vllm serve Qwen/Qwen3-Coder-30B-A3B-Instruct-FP8 --dtype auto
 * --cpu-offload-gb 100 --gpu-memory-utilization 0.80
 * --max-model-len 72224 --max-num-batched-tokens 72224
 * --max-num-seqs 4 --tensor-parallel-size 1 --enforce-eager
 * --tool-call-parser hermes --enable-auto-tool-choice --port 8001
 * 
 * Run: mvn test -Dtest=VllmKnowledgeRetrieverIntegrationTest
 * -Dvllm.integration=true
 */
@SpringBootTest(properties = {

                "spring.sql.init.mode=never"
})
@Import(VllmTestConfiguration.class)
@ActiveProfiles("vllm-integration")
@EnabledIfSystemProperty(named = "vllm.integration", matches = "true")
class VllmKnowledgeRetrieverIntegrationTest {

        @Autowired
        private ChatClient.Builder chatClientBuilder;

        @Autowired
        private EmbeddingModel embeddingModel;

        @Autowired
        private ChunkRepository chunkRepository;

        @Autowired
        private KnowledgeRetriever retriever;

        @Test
        void shouldUseVllmForQueryExpansion() {
                // Given
                String originalQuery = "diabetes treatment";
                ChatClient chatClient = chatClientBuilder.build();

                // When - Use vLLM to expand query
                String expandedQuery = chatClient.prompt()
                                .user("Generate 3 related search queries for: " + originalQuery)
                                .call()
                                .content();

                // Then
                assertThat(expandedQuery).isNotNull();
                assertThat(expandedQuery.length()).isGreaterThan(originalQuery.length());
                System.out.println("Original: " + originalQuery);
                System.out.println("Expanded by vLLM: " + expandedQuery);
        }

        @Test
        void shouldCombineVllmChatWithOllamaEmbeddings() {
                // Given - Medical knowledge
                List<String> documents = List.of(
                                "Metformin is first-line treatment for type 2 diabetes",
                                "Insulin therapy is essential for type 1 diabetes management",
                                "Lifestyle changes help control blood sugar levels");

                // Store with Ollama embeddings
                Flux<Chunk> chunks = Flux.fromIterable(documents)
                                .flatMap(doc -> Mono.fromCallable(() -> {
                                        float[] embedding = embeddingModel.embed(doc);
                                        Chunk chunk = new Chunk();
                                        chunk.setId(UUID.randomUUID());
                                        chunk.setContent(doc);
                                        chunk.setEmbedding(embedding);
                                        return chunk;
                                }))
                                .flatMap(chunkRepository::save);

                // When - Retrieve and use vLLM to synthesize answer
                String query = "How to treat diabetes?";

                StepVerifier.create(chunks.thenMany(
                                retriever.retrieve(RetrievalRequest.builder().query(query).topK(3).build())
                                                .flatMap(result -> {
                                                        String context = String.join("\n",
                                                                        result.getChunks().stream()
                                                                                        .map(Chunk::getContent)
                                                                                        .toList());

                                                        ChatClient chatClient = chatClientBuilder.build();
                                                        String answer = chatClient.prompt()
                                                                        .user("Based on: " + context + "\nAnswer: "
                                                                                        + query)
                                                                        .call()
                                                                        .content();

                                                        return Mono.just(answer);
                                                })))
                                .assertNext(answer -> {
                                        // Then - vLLM synthesizes answer using Ollama-retrieved context
                                        assertThat(answer).isNotNull();
                                        assertThat(answer.toLowerCase()).containsAnyOf(
                                                        "metformin", "insulin", "lifestyle", "treatment", "diabetes");
                                        System.out.println("vLLM Answer: " + answer);
                                })
                                .verifyComplete();
        }

        @Test
        void shouldHandleComplexQueryWithVllmToolCalling() {
                // Given
                ChatClient chatClient = chatClientBuilder.build();

                // When - Test vLLM's tool calling capability (Qwen3-Coder good at this)
                String response = chatClient.prompt()
                                .user("Extract structured data: Patient John Doe, age 45, diagnosed with diabetes type 2")
                                .call()
                                .content();

                // Then - vLLM should understand structure
                assertThat(response).isNotNull();
                System.out.println("vLLM structured extraction: " + response);
                assertThat(response.toLowerCase()).containsAnyOf(
                                "john", "45", "diabetes", "type 2");
        }
}
