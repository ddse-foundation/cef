package org.ddse.ml.cef.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Result of executing a graph pattern.
 * Contains the specific path found, not just a subgraph dump.
 */
public record MatchedPath(
        String patternId,
        List<UUID> nodeIds,
        List<String> relationTypes,
        Map<String, Object> pathProperties,
        double score,
        String explanation) {
    public UUID getNodeAtStep(int step) {
        if (step < 0 || step >= nodeIds.size()) {
            throw new IllegalArgumentException("Step " + step + " out of bounds [0, " + nodeIds.size() + ")");
        }
        return nodeIds.get(step);
    }

    public String toPathString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nodeIds.size(); i++) {
            sb.append(nodeIds.get(i));
            if (i < relationTypes.size()) {
                sb.append(" →[").append(relationTypes.get(i)).append("]→ ");
            }
        }
        return sb.toString();
    }
}
