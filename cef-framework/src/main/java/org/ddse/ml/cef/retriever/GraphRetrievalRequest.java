package org.ddse.ml.cef.retriever;

import java.util.List;
import java.util.Map;

/**
 * Request for graph-only retrieval.
 *
 * @author mrmanna
 */
public class GraphRetrievalRequest {

    private List<String> startNodeLabels;
    private List<String> relationTypes;
    private Map<String, Object> nodeFilters;
    private int depth = 2;

    public GraphRetrievalRequest() {
    }

    public GraphRetrievalRequest(List<String> startNodeLabels, List<String> relationTypes, int depth) {
        this(startNodeLabels, relationTypes, null, depth);
    }

    public GraphRetrievalRequest(List<String> startNodeLabels, List<String> relationTypes,
            Map<String, Object> nodeFilters, int depth) {
        this.startNodeLabels = startNodeLabels;
        this.relationTypes = relationTypes;
        this.nodeFilters = nodeFilters;
        this.depth = depth;
    }

    public List<String> getStartNodeLabels() {
        return startNodeLabels;
    }

    public void setStartNodeLabels(List<String> startNodeLabels) {
        this.startNodeLabels = startNodeLabels;
    }

    public List<String> getRelationTypes() {
        return relationTypes;
    }

    public void setRelationTypes(List<String> relationTypes) {
        this.relationTypes = relationTypes;
    }

    public Map<String, Object> getNodeFilters() {
        return nodeFilters;
    }

    public void setNodeFilters(Map<String, Object> nodeFilters) {
        this.nodeFilters = nodeFilters;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
