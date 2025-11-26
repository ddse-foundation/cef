package org.ddse.ml.cef.domain;

/**
 * Semantic classification for graph traversal strategies.
 * Helps the retriever decide which relations to follow during graph
 * exploration.
 * 
 * @author mrmanna
 */
public enum RelationSemantics {
    /**
     * Hierarchical relationships (parent-child, is-a).
     * Examples: CONTAINS, IS_A, PART_OF
     */
    HIERARCHICAL,

    /**
     * Associative relationships (related-to, connected-to).
     * Examples: RELATED_TO, MENTIONS, REFERENCES
     */
    ASSOCIATIVE,

    /**
     * Causal relationships (causes, triggers).
     * Examples: CAUSES, TREATS, PRODUCES
     */
    CAUSAL,

    /**
     * Temporal relationships (before, after, during).
     * Examples: PRECEDES, FOLLOWS, CONCURRENT_WITH
     */
    TEMPORAL,

    /**
     * Spatial relationships (near, contains).
     * Examples: NEAR, ADJACENT_TO, LOCATED_IN
     */
    SPATIAL,

    /**
     * Domain-specific relationship that doesn't fit standard categories.
     * User defines custom traversal logic.
     */
    CUSTOM
}
