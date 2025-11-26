package org.ddse.ml.cef.dto;

import java.util.List;

public record RetrievalRequest(
        GraphQuery graphQuery,
        String query,
        int maxTokenBudget,
        int topK,
        int maxGraphNodes,
        List<String> semanticKeywords) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private GraphQuery graphQuery;
        private String query;
        private int maxTokenBudget;
        private int topK = 10;
        private int maxGraphNodes = 100;
        private List<String> semanticKeywords;

        public Builder graphQuery(GraphQuery graphQuery) {
            this.graphQuery = graphQuery;
            return this;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder maxTokenBudget(int maxTokenBudget) {
            this.maxTokenBudget = maxTokenBudget;
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder maxGraphNodes(int maxGraphNodes) {
            this.maxGraphNodes = maxGraphNodes;
            return this;
        }

        public Builder semanticKeywords(List<String> semanticKeywords) {
            this.semanticKeywords = semanticKeywords;
            return this;
        }

        public RetrievalRequest build() {
            return new RetrievalRequest(graphQuery, query, maxTokenBudget, topK, maxGraphNodes, semanticKeywords);
        }
    }
}
