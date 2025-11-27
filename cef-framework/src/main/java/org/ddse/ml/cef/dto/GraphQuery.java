package org.ddse.ml.cef.dto;

import java.util.List;

/**
 * ENHANCED: Graph query now supports structured patterns alongside targets.
 * KnowledgeRetriever processes both for improved multi-hop reasoning.
 */
public record GraphQuery(
                // Existing fields (backward compatible)
                List<ResolutionTarget> targets,
                TraversalHint traversal,

                // NEW: Pattern-based traversal
                List<GraphPattern> patterns,
                QueryCombinator combinator,
                RankingStrategy rankingStrategy) {
        /**
         * Backward compatible constructor (existing code)
         */
        public GraphQuery(List<ResolutionTarget> targets, TraversalHint traversal) {
                this(targets, traversal, null, null, RankingStrategy.HYBRID);
        }

        /**
         * Pattern-based constructor
         */
        public GraphQuery(List<GraphPattern> patterns) {
                this(null, null, patterns, null, RankingStrategy.HYBRID);
        }

        /**
         * Combinator constructor
         */
        public GraphQuery(QueryCombinator combinator) {
                this(null, null, null, combinator, RankingStrategy.HYBRID);
        }

        /**
         * Check if patterns are provided for enhanced traversal
         */
        public boolean hasPatterns() {
                return patterns != null && !patterns.isEmpty();
        }

        /**
         * Check if multi-pattern combination requested
         */
        public boolean hasCombinator() {
                return combinator != null && combinator.patterns() != null &&
                                !combinator.patterns().isEmpty();
        }

        /**
         * Use patterns if available, otherwise fall back to targets
         */
        public boolean usePatternsForTraversal() {
                return hasPatterns() || hasCombinator();
        }
}
