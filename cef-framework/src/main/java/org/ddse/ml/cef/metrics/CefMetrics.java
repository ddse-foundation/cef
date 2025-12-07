package org.ddse.ml.cef.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.ddse.ml.cef.graph.InMemoryKnowledgeGraph;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Custom metrics for CEF Framework monitoring (in-memory backend only).
 * 
 * <p>Only activated when using in-memory backend ({@code cef.graph.store=in-memory}).</p>
 * 
 * Exports:
 * - cef.graph.nodes - Number of nodes in knowledge graph
 * - cef.graph.edges - Number of edges in knowledge graph
 * - cef.graph.labels - Number of distinct labels
 * 
 * View at: /actuator/prometheus or /actuator/metrics
 * 
 * @author mrmanna
 * @since 0.6
 */
@Component
@ConditionalOnBean(InMemoryKnowledgeGraph.class)
public class CefMetrics implements MeterBinder {

    private final InMemoryKnowledgeGraph knowledgeGraph;

    public CefMetrics(InMemoryKnowledgeGraph knowledgeGraph) {
        this.knowledgeGraph = knowledgeGraph;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        // Graph size metrics
        Gauge.builder("cef.graph.nodes", knowledgeGraph, InMemoryKnowledgeGraph::getNodeCount)
                .description("Number of nodes in the knowledge graph")
                .tag("component", "graph")
                .register(registry);

        Gauge.builder("cef.graph.edges", knowledgeGraph, InMemoryKnowledgeGraph::getEdgeCount)
                .description("Number of edges in the knowledge graph")
                .tag("component", "graph")
                .register(registry);

        Gauge.builder("cef.graph.labels", knowledgeGraph, 
                graph -> graph.getLabelCounts().size())
                .description("Number of distinct labels in the knowledge graph")
                .tag("component", "graph")
                .register(registry);
    }
}
