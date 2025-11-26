package org.ddse.ml.cef.service;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Simple alias for Spring AI's EmbeddingModel with reactive wrapper.
 * 
 * CEF framework directly uses Spring AI's EmbeddingModel interface.
 * All configuration (provider, model, dimensions) is handled by Spring AI's
 * auto-configuration through application.yml.
 * 
 * Spring AI configuration example:
 * 
 * <pre>
 * spring:
 *   ai:
 *     ollama:
 *       base-url: http://localhost:11434
 *       embedding:
 *         options:
 *           model: nomic-embed-text:latest
 *     openai:
 *       api-key: ${OPENAI_API_KEY}
 *       embedding:
 *         options:
 *           model: text-embedding-ada-002
 *           dimensions: 1536
 * </pre>
 * 
 * @author mrmanna
 * @see org.springframework.ai.embedding.EmbeddingModel
 */
public interface EmbeddingService {

    /**
     * Generate embedding for a single text.
     *
     * @param text the text to embed
     * @return embedding vector as float array
     */
    Mono<float[]> embed(String text);

    /**
     * Generate embeddings for multiple texts in batch.
     *
     * @param texts list of texts to embed
     * @return list of embedding vectors in same order as input
     */
    Mono<List<float[]>> embedBatch(List<String> texts);
}