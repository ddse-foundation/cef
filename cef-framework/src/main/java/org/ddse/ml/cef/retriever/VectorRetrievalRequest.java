package org.ddse.ml.cef.retriever;

/**
 * Request for vector-only retrieval.
 *
 * @author mrmanna
 */
public class VectorRetrievalRequest {

    private String query;
    private int topK = 10;

    public VectorRetrievalRequest() {
    }

    public VectorRetrievalRequest(String query, int topK) {
        this.query = query;
        this.topK = topK;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }
}
