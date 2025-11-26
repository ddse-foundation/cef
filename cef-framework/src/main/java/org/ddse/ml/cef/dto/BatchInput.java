package org.ddse.ml.cef.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Input DTO for batch indexing operations.
 * Represents a batch of nodes, edges, and chunks to be indexed together.
 */
public record BatchInput(
        List<NodeInput> nodes,
        List<EdgeInput> edges,
        List<ChunkInput> chunks) {
    public BatchInput {
        nodes = nodes != null ? List.copyOf(nodes) : List.of();
        edges = edges != null ? List.copyOf(edges) : List.of();
        chunks = chunks != null ? List.copyOf(chunks) : List.of();
    }

    public int totalItems() {
        return nodes.size() + edges.size() + chunks.size();
    }

    public boolean isEmpty() {
        return nodes.isEmpty() && edges.isEmpty() && chunks.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<NodeInput> nodes = new ArrayList<>();
        private List<EdgeInput> edges = new ArrayList<>();
        private List<ChunkInput> chunks = new ArrayList<>();

        public Builder nodes(List<NodeInput> nodes) {
            this.nodes = new ArrayList<>(nodes);
            return this;
        }

        public Builder addNode(NodeInput node) {
            this.nodes.add(node);
            return this;
        }

        public Builder edges(List<EdgeInput> edges) {
            this.edges = new ArrayList<>(edges);
            return this;
        }

        public Builder addEdge(EdgeInput edge) {
            this.edges.add(edge);
            return this;
        }

        public Builder chunks(List<ChunkInput> chunks) {
            this.chunks = new ArrayList<>(chunks);
            return this;
        }

        public Builder addChunk(ChunkInput chunk) {
            this.chunks.add(chunk);
            return this;
        }

        public BatchInput build() {
            return new BatchInput(nodes, edges, chunks);
        }
    }
}
