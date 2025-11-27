package org.ddse.ml.cef.dto;

import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;

import java.util.List;
import java.util.Set;

/**
 * ENHANCED: Reasoning context now includes matched paths
 * when patterns are provided in GraphQuery
 */
public record ReasoningContext(
                Set<Node> relatedNodes,
                Set<Edge> relatedEdges,
                Set<Chunk> relatedChunks,
                List<MatchedPath> matchedPaths // NEW: Pattern-matched paths
) {
        /**
         * Backward compatible constructor (existing code)
         */
        public ReasoningContext(Set<Node> relatedNodes, Set<Edge> relatedEdges, Set<Chunk> relatedChunks) {
                this(relatedNodes, relatedEdges, relatedChunks, List.of());
        }

        /**
         * Check if pattern-based paths were matched
         */
        public boolean hasMatchedPaths() {
                return matchedPaths != null && !matchedPaths.isEmpty();
        }
}
