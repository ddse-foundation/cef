package org.ddse.ml.cef.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Default implementation using Spring AI's EmbeddingModel.
 * Spring AI auto-configures the appropriate provider based on application.yml.
 * 
 * @author mrmanna
 */
public class DefaultEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public DefaultEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public Mono<float[]> embed(String text) {
        return Mono.fromCallable(() -> {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            return response.getResults().get(0).getOutput();
        });
    }

    @Override
    public Mono<List<float[]>> embedBatch(List<String> texts) {
        return Mono.fromCallable(() -> {
            EmbeddingResponse response = embeddingModel.embedForResponse(texts);
            return response.getResults().stream()
                    .map(result -> result.getOutput())
                    .collect(Collectors.toList());
        });
    }
}
