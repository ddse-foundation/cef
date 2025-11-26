package org.ddse.ml.cef.indexer;

/**
 * Indexing statistics for monitoring.
 *
 * @author mrmanna
 */
public class IndexStats {

    private final long totalNodes;
    private final long totalEdges;
    private final long totalChunks;
    private final long nodesWithEmbeddings;
    private final long chunksWithEmbeddings;

    public IndexStats(long totalNodes, long totalEdges, long totalChunks,
            long nodesWithEmbeddings, long chunksWithEmbeddings) {
        this.totalNodes = totalNodes;
        this.totalEdges = totalEdges;
        this.totalChunks = totalChunks;
        this.nodesWithEmbeddings = nodesWithEmbeddings;
        this.chunksWithEmbeddings = chunksWithEmbeddings;
    }

    public long getTotalNodes() {
        return totalNodes;
    }

    public long getTotalEdges() {
        return totalEdges;
    }

    public long getTotalChunks() {
        return totalChunks;
    }

    public long getNodesWithEmbeddings() {
        return nodesWithEmbeddings;
    }

    public long getChunksWithEmbeddings() {
        return chunksWithEmbeddings;
    }

    @Override
    public String toString() {
        return "IndexStats{" +
                "totalNodes=" + totalNodes +
                ", totalEdges=" + totalEdges +
                ", totalChunks=" + totalChunks +
                ", nodesWithEmbeddings=" + nodesWithEmbeddings +
                ", chunksWithEmbeddings=" + chunksWithEmbeddings +
                '}';
    }
}
