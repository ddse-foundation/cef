package org.ddse.ml.cef.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Test configuration for vLLM integration tests.
 * Uses vLLM OpenAI-compatible API with Qwen3-Coder-30B-A3B-Instruct-FP8.
 * 
 * Prerequisites:
 * - vLLM running on localhost:8001
 * - Model: Qwen/Qwen3-Coder-30B-A3B-Instruct-FP8
 * - Ollama running on localhost:11434 for embeddings (nomic-embed-text)
 * 
 * vLLM start command:
 * vllm serve Qwen/Qwen3-Coder-30B-A3B-Instruct-FP8
 * --dtype auto --cpu-offload-gb 100 --gpu-memory-utilization 0.80
 * --max-model-len 72224 --max-num-batched-tokens 72224 --max-num-seqs 4
 * --tensor-parallel-size 1 --enforce-eager --tool-call-parser hermes
 * --enable-auto-tool-choice --port 8001
 * 
 * @author mrmanna
 */
@TestConfiguration
@Profile("vllm-integration")
public class VllmTestConfiguration {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory());
    }

    @Bean
    public OpenAiApi vllmApi(RestClient.Builder restClientBuilder) {
        // vLLM provides OpenAI-compatible API
        // Using /v1 base URL to ensure compatibility
        // We pass the custom RestClient.Builder to ensure we use
        // SimpleClientHttpRequestFactory
        // which buffers the body and sets Content-Length, avoiding chunked encoding
        // issues with vLLM
        return new OpenAiApi("http://localhost:8001", "EMPTY", restClientBuilder, WebClient.builder(),
                org.springframework.ai.retry.RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
    }

    @Bean
    public ChatModel vllmChatModel(OpenAiApi vllmApi) {
        var options = OpenAiChatOptions.builder()
                .withModel("Qwen/Qwen3-Coder-30B-A3B-Instruct-FP8")
                .build();
        return new OpenAiChatModel(vllmApi, options);
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        // Still use Ollama for embeddings (vLLM focused on inference)
        var ollamaApi = new OllamaApi("http://localhost:11434");
        return OllamaEmbeddingModel.builder()
                .withOllamaApi(ollamaApi)
                .withDefaultOptions(OllamaOptions.builder()
                        .withModel("nomic-embed-text:latest")
                        .build())
                .build();
    }
}
