package org.ddse.ml.cef.health;

import org.ddse.ml.cef.graph.InMemoryKnowledgeGraph;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Health indicator for the In-Memory Knowledge Graph.
 * 
 * <p>Only activated when using in-memory backend ({@code cef.graph.store=in-memory}).</p>
 * 
 * Reports:
 * - Node count and edge count
 * - Label distribution
 * - Graph status
 * 
 * Exposed at: /actuator/health/knowledgeGraph
 * 
 * @author mrmanna
 * @since 0.6
 */
@Component("knowledgeGraph")
@ConditionalOnBean(InMemoryKnowledgeGraph.class)
public class KnowledgeGraphHealthIndicator implements HealthIndicator {

    private final InMemoryKnowledgeGraph knowledgeGraph;

    public KnowledgeGraphHealthIndicator(InMemoryKnowledgeGraph knowledgeGraph) {
        this.knowledgeGraph = knowledgeGraph;
    }

    @Override
    public Health health() {
        try {
            long nodeCount = knowledgeGraph.getNodeCount();
            long edgeCount = knowledgeGraph.getEdgeCount();
            
            Health.Builder builder = Health.up()
                    .withDetail("nodeCount", nodeCount)
                    .withDetail("edgeCount", edgeCount)
                    .withDetail("labelDistribution", knowledgeGraph.getLabelCounts());

            // Warning if graph is empty
            if (nodeCount == 0) {
                builder.withDetail("warning", "Knowledge graph is empty");
            }

            return builder.build();
            
        } catch (Exception e) {
            return Health.down()
                    .withException(e)
                    .withDetail("error", "Failed to query knowledge graph")
                    .build();
        }
    }
}
