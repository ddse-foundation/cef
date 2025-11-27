package org.ddse.ml.cef.dto;

import java.util.List;

/**
 * Combine multiple graph patterns with logical operators.
 */
public record QueryCombinator(
        CombinatorType type,
        List<GraphPattern> patterns,
        String description) {
    public enum CombinatorType {
        INTERSECTION, // AND - all patterns must match
        UNION, // OR - any pattern matches
        SEQUENTIAL // Execute in order, feed results forward
    }
}
