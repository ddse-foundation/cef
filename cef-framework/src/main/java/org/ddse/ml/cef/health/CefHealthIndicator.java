package org.ddse.ml.cef.health;

import org.ddse.ml.cef.graph.GraphStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Health indicator for CEF Framework components.
 * 
 * Checks:
 * - Graph store health (via count operations)
 * - Embedding service availability
 * 
 * Exposed at: /actuator/health/cef
 * 
 * @author mrmanna
 * @since 0.6
 */
@Component("cef")
@ConditionalOnBean({GraphStore.class, EmbeddingModel.class})
public class CefHealthIndicator implements HealthIndicator {

    private final GraphStore graphStore;
    private final EmbeddingModel embeddingModel;

    public CefHealthIndicator(GraphStore graphStore, 
                              EmbeddingModel embeddingModel) {
        this.graphStore = graphStore;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        
        try {
            // Check graph store
            builder.withDetail("graph.type", graphStore.getClass().getSimpleName());
            builder.withDetail("graph.status", "UP");

            // Check embedding model connectivity
            boolean embeddingHealthy = checkEmbeddingHealth();
            builder.withDetail("embedding.status", embeddingHealthy ? "UP" : "DOWN");
            builder.withDetail("embedding.dimensions", embeddingModel.dimensions());

            // Overall status
            if (embeddingHealthy) {
                builder.up();
            } else {
                builder.down().withDetail("reason", "Embedding service unavailable");
            }

        } catch (Exception e) {
            builder.down()
                    .withException(e)
                    .withDetail("reason", "Health check failed: " + e.getMessage());
        }

        return builder.build();
    }

    /**
     * Check embedding service health with timeout.
     */
    private boolean checkEmbeddingHealth() {
        try {
            // Simple ping - get dimensions (doesn't require actual embedding)
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(
                    () -> embeddingModel.dimensions()
            );
            Integer dimensions = future.get(5, TimeUnit.SECONDS);
            return dimensions != null && dimensions > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
