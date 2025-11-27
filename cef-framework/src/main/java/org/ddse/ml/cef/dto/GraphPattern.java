package org.ddse.ml.cef.dto;

import java.util.List;

/**
 * Structured graph pattern for multi-hop traversal.
 * Used within GraphQuery for enhanced hybrid retrieval.
 */
public record GraphPattern(
        String patternId,
        List<TraversalStep> steps,
        List<Constraint> constraints,
        String description) {
    /**
     * Create single-hop pattern
     */
    public static GraphPattern singleHop(String sourceLabel, String relationType, String targetLabel) {
        return new GraphPattern(
                "single_hop",
                List.of(new TraversalStep(sourceLabel, relationType, targetLabel, 0)),
                List.of(),
                "Single hop traversal");
    }

    /**
     * Create multi-hop pattern without constraints
     */
    public static GraphPattern multiHop(String patternId, List<TraversalStep> steps, String description) {
        return new GraphPattern(patternId, steps, List.of(), description);
    }
}
