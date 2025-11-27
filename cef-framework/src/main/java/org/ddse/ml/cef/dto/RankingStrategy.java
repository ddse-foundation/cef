package org.ddse.ml.cef.dto;

/**
 * Strategy for ranking matched paths.
 */
public enum RankingStrategy {
    PATH_LENGTH, // Prefer shorter paths
    EDGE_WEIGHT, // Sum edge weights
    NODE_CENTRALITY, // Prefer paths through high-centrality nodes
    SEMANTIC_SCORE, // Combine with vector similarity
    HYBRID // Weighted combination
}
