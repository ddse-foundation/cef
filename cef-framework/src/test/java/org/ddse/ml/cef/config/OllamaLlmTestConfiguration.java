package org.ddse.ml.cef.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration for Ollama LLM integration tests.
 * Uses qwq:32b for chat (better tool calling) and nomic-embed-text for
 * embeddings.
 * 
 * Prerequisites:
 * - Ollama running on localhost:11434
 * - Models available: qwq:32b, nomic-embed-text:latest
 * 
 * @author mrmanna
 */
@TestConfiguration
@Profile("ollama-integration")
public class OllamaLlmTestConfiguration {

    @Bean
    public OllamaApi ollamaApi() {
        return new OllamaApi("http://localhost:11434");
    }

    @Bean
    public ChatModel ollamaChatModel(OllamaApi ollamaApi) {
        return OllamaChatModel.builder()
                .withOllamaApi(ollamaApi)
                .withDefaultOptions(OllamaOptions.builder()
                        .withModel("qwq:32b")
                        .withTemperature(0.7)
                        .build())
                .build();
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public EmbeddingModel ollamaEmbeddingModel(OllamaApi ollamaApi) {
        return OllamaEmbeddingModel.builder()
                .withOllamaApi(ollamaApi)
                .withDefaultOptions(OllamaOptions.builder()
                        .withModel("nomic-embed-text:latest")
                        .build())
                .build();
    }
}
