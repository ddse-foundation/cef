package org.ddse.ml.cef.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * Spring AI M4 milestone requires explicit bean wiring.
 * Production code uses Spring AI auto-config from properties.
 * This is ONLY for test - reading from same spring.ai.* properties.
 */
@TestConfiguration
@Profile("embedding-integration")
public class EmbeddingIntegrationTestConfig {

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${spring.ai.ollama.base-url}") String baseUrl,
            @Value("${spring.ai.ollama.embedding.options.model}") String model) {
        var api = new OllamaApi(baseUrl);
        return OllamaEmbeddingModel.builder()
                .withOllamaApi(api)
                .withDefaultOptions(OllamaOptions.builder()
                        .withModel(model)
                        .build())
                .build();
    }
}
