package org.ddse.ml.cef.benchmark.dataset;

import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;

import java.util.Collections;
import java.util.List;

/**
 * Immutable holder for the medical benchmark dataset.
 * Pre-parsed from JSON, ready to ingest into any GraphStore.
 *
 * @author mrmanna
 * @since v0.6
 */
public class MedicalDataset {

    private final List<Node> nodes;
    private final List<Edge> edges;
    private final List<Chunk> chunks;
    private final List<String> labels;

    public MedicalDataset(List<Node> nodes, List<Edge> edges, List<Chunk> chunks, List<String> labels) {
        this.nodes = Collections.unmodifiableList(nodes);
        this.edges = Collections.unmodifiableList(edges);
        this.chunks = Collections.unmodifiableList(chunks);
        this.labels = Collections.unmodifiableList(labels);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public List<Chunk> getChunks() {
        return chunks;
    }

    public List<String> getLabels() {
        return labels;
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public int getEdgeCount() {
        return edges.size();
    }

    public int getChunkCount() {
        return chunks.size();
    }

    @Override
    public String toString() {
        return String.format("MedicalDataset{nodes=%d, edges=%d, chunks=%d, labels=%s}",
                nodes.size(), edges.size(), chunks.size(), labels);
    }
}
