package org.ddse.ml.cef.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import static org.mockito.Mockito.mock;

/**
 * Test configuration for mocked embedding model (default).
 * Real Ollama is used only when 'ollama-integration' or 'embedding-integration'
 * profile is active.
 */
@TestConfiguration
@Profile("!embedding-integration & !ollama-integration & !vllm-integration")
public class MockEmbeddingConfiguration {

    @Bean
    @Primary
    public EmbeddingModel mockEmbeddingModel() {
        return mock(EmbeddingModel.class);
    }
}
