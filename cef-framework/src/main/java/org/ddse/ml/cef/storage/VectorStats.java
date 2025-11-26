package org.ddse.ml.cef.storage;

/**
 * Vector store statistics for monitoring.
 *
 * @author mrmanna
 */
public class VectorStats {

    private final long totalChunks;
    private final long chunksWithEmbeddings;
    private final int embeddingDimension;
    private final String indexType;

    public VectorStats(long totalChunks, long chunksWithEmbeddings,
            int embeddingDimension, String indexType) {
        this.totalChunks = totalChunks;
        this.chunksWithEmbeddings = chunksWithEmbeddings;
        this.embeddingDimension = embeddingDimension;
        this.indexType = indexType;
    }

    public long getTotalChunks() {
        return totalChunks;
    }

    public long getChunksWithEmbeddings() {
        return chunksWithEmbeddings;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public String getIndexType() {
        return indexType;
    }

    @Override
    public String toString() {
        return "VectorStats{" +
                "totalChunks=" + totalChunks +
                ", chunksWithEmbeddings=" + chunksWithEmbeddings +
                ", embeddingDimension=" + embeddingDimension +
                ", indexType='" + indexType + '\'' +
                '}';
    }
}
