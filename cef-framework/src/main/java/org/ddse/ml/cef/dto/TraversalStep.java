package org.ddse.ml.cef.dto;

/**
 * Single step in graph traversal pattern.
 */
public record TraversalStep(
        String sourceLabel, // null = use previous step result
        String relationType,
        String targetLabel,
        int stepIndex,
        Direction direction) {
    public TraversalStep(String sourceLabel, String relationType, String targetLabel, int stepIndex) {
        this(sourceLabel, relationType, targetLabel, stepIndex, Direction.OUTGOING);
    }

    public enum Direction {
        OUTGOING, // Follow edge in forward direction
        INCOMING, // Follow edge in reverse direction
        BOTH // Follow edge in either direction
    }
}
