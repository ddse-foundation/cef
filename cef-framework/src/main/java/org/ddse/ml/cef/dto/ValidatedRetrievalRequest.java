package org.ddse.ml.cef.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/**
 * Validated DTO for retrieval requests with comprehensive bean validation.
 * 
 * <p>This class adds JSR-380 validation to retrieval requests to ensure
 * data integrity before processing. Use this class in Spring controllers
 * and services with @Validated annotation.
 * 
 * <h2>Validation Rules</h2>
 * <ul>
 *   <li>query: Required, non-blank, max 10,000 characters</li>
 *   <li>topK: 1-1000 range (reasonable for LLM context)</li>
 *   <li>maxTokenBudget: 0-200,000 tokens (covers large models)</li>
 *   <li>maxGraphNodes: 1-10,000 for graph traversal</li>
 * </ul>
 * 
 * @author mrmanna
 * @since v0.6
 */
public record ValidatedRetrievalRequest(
        
        @Valid
        GraphQuery graphQuery,
        
        @NotBlank(message = "Query cannot be blank")
        @Size(max = 10000, message = "Query must not exceed 10,000 characters")
        String query,
        
        @Min(value = 0, message = "Max token budget cannot be negative")
        @Max(value = 200000, message = "Max token budget cannot exceed 200,000")
        int maxTokenBudget,
        
        @Min(value = 1, message = "TopK must be at least 1")
        @Max(value = 1000, message = "TopK cannot exceed 1,000")
        int topK,
        
        @Min(value = 1, message = "Max graph nodes must be at least 1")
        @Max(value = 10000, message = "Max graph nodes cannot exceed 10,000")
        int maxGraphNodes,
        
        @Size(max = 50, message = "Semantic keywords cannot exceed 50 items")
        List<@NotBlank @Size(max = 200) String> semanticKeywords
) {
    
    // Compact constructor with defaults
    public ValidatedRetrievalRequest {
        if (topK == 0) topK = 10;
        if (maxGraphNodes == 0) maxGraphNodes = 100;
        if (maxTokenBudget == 0) maxTokenBudget = 4000;
    }
    
    /**
     * Converts to standard RetrievalRequest for backward compatibility.
     */
    public RetrievalRequest toRetrievalRequest() {
        return new RetrievalRequest(graphQuery, query, maxTokenBudget, topK, maxGraphNodes, semanticKeywords);
    }
    
    /**
     * Creates from standard RetrievalRequest.
     */
    public static ValidatedRetrievalRequest from(RetrievalRequest request) {
        return new ValidatedRetrievalRequest(
                request.graphQuery(),
                request.query(),
                request.maxTokenBudget(),
                request.topK(),
                request.maxGraphNodes(),
                request.semanticKeywords()
        );
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private GraphQuery graphQuery;
        private String query;
        private int maxTokenBudget = 4000;
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
        
        public ValidatedRetrievalRequest build() {
            return new ValidatedRetrievalRequest(
                    graphQuery, query, maxTokenBudget, topK, maxGraphNodes, semanticKeywords
            );
        }
    }
}
