package org.ddse.ml.cef.retriever;

import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of context retrieval.
 * Contains ranked nodes, edges, and chunks.
 * 
 * MCP tool converts this to LLM-consumable format.
 *
 * @author mrmanna
 */
public class RetrievalResult {

    private List<Node> nodes = new ArrayList<>();
    private List<Edge> edges = new ArrayList<>();
    private List<Chunk> chunks = new ArrayList<>();
    private RetrievalStrategy strategy;
    private long retrievalTimeMs;

    public RetrievalResult() {
    }

    public RetrievalResult(List<Node> nodes, List<Edge> edges, List<Chunk> chunks, RetrievalStrategy strategy) {
        this.nodes = nodes;
        this.edges = edges;
        this.chunks = chunks;
        this.strategy = strategy;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public void setEdges(List<Edge> edges) {
        this.edges = edges;
    }

    public List<Chunk> getChunks() {
        return chunks;
    }

    public void setChunks(List<Chunk> chunks) {
        this.chunks = chunks;
    }

    public RetrievalStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(RetrievalStrategy strategy) {
        this.strategy = strategy;
    }

    public long getRetrievalTimeMs() {
        return retrievalTimeMs;
    }

    public void setRetrievalTimeMs(long retrievalTimeMs) {
        this.retrievalTimeMs = retrievalTimeMs;
    }

    public boolean isEmpty() {
        return nodes.isEmpty() && edges.isEmpty() && chunks.isEmpty();
    }

    public int getTotalResults() {
        return nodes.size() + edges.size() + chunks.size();
    }

    /**
     * Check if result is thin (below threshold).
     * Used to trigger fallback strategies.
     */
    public boolean isThin() {
        int totalResults = getTotalResults();
        return totalResults < 5; // Configurable threshold
    }

    @Override
    public String toString() {
        return "RetrievalResult{" +
                "nodes=" + nodes.size() +
                ", edges=" + edges.size() +
                ", chunks=" + chunks.size() +
                ", strategy=" + strategy +
                ", retrievalTimeMs=" + retrievalTimeMs +
                '}';
    }

    /**
     * Strategy used for retrieval (for diagnostics).
     */
    public enum RetrievalStrategy {
        GRAPH_ONLY,
        VECTOR_ONLY,
        HYBRID,
        EXPANSION
    }
}
