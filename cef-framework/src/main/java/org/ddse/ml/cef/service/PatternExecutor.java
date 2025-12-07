package org.ddse.ml.cef.service;

import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.dto.*;
import org.ddse.ml.cef.graph.GraphStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for executing graph patterns.
 * Used internally by KnowledgeRetrieverImpl for pattern-based queries.
 * 
 * <p>This class uses the unified GraphStore interface, supporting all backends:
 * InMemory, Neo4j, PgSql, and PgAge.</p>
 */
@Component
public class PatternExecutor {

    private static final Logger log = LoggerFactory.getLogger(PatternExecutor.class);

    private final GraphStore graphStore;
    private final ConstraintEvaluator constraintEvaluator;

    public PatternExecutor(GraphStore graphStore, ConstraintEvaluator constraintEvaluator) {
        this.graphStore = graphStore;
        this.constraintEvaluator = constraintEvaluator;
    }

    /**
     * Execute single pattern starting from entry points.
     */
    public reactor.core.publisher.Flux<MatchedPath> executePattern(
            GraphPattern pattern,
            Set<UUID> entryPoints,
            int maxPaths,
            RankingStrategy rankingStrategy) {
        log.debug("Executing pattern {} with {} entry points", pattern.patternId(), entryPoints.size());

        return reactor.core.publisher.Flux.fromIterable(entryPoints)
                .flatMap(entryPoint -> traverseSteps(entryPoint, pattern.steps(), pattern.constraints()))
                .map(path -> {
                    double score = scorePath(path, rankingStrategy);
                    return new MatchedPath(
                            pattern.patternId(),
                            path.nodeIds(),
                            path.relationTypes(),
                            path.properties(),
                            score,
                            "Matched pattern: " + pattern.description());
                })
                .sort(Comparator.comparingDouble(MatchedPath::score).reversed())
                .take(maxPaths)
                .doOnComplete(() -> log.debug("Pattern {} execution completed", pattern.patternId()));
    }

    /**
     * Step-by-step traversal with constraint filtering.
     */
    private reactor.core.publisher.Flux<PathState> traverseSteps(
            UUID startNode,
            List<TraversalStep> steps,
            List<Constraint> constraints) {
        
        reactor.core.publisher.Flux<PathState> currentPaths = reactor.core.publisher.Flux.just(
                new PathState(List.of(startNode), List.of(), Map.of()));

        for (int i = 0; i < steps.size(); i++) {
            currentPaths = processStep(currentPaths, steps.get(i), constraints, i);
        }

        return currentPaths;
    }

    private reactor.core.publisher.Flux<PathState> processStep(
            final reactor.core.publisher.Flux<PathState> currentPaths,
            final TraversalStep step,
            final List<Constraint> constraints,
            final int stepIndex) {
        
        final String targetLabel = step.targetLabel();
        final String relationType = step.relationType();
        final TraversalStep.Direction direction = step.direction();

        return currentPaths.flatMap(path -> {
            UUID currentNode = path.nodeIds().get(path.nodeIds().size() - 1);

            return getEdgesReactive(currentNode, relationType, direction)
                    .flatMap(edge -> {
                        UUID nextNodeId = edge.getTargetNodeId();
                        if (direction == TraversalStep.Direction.INCOMING) {
                            nextNodeId = edge.getSourceNodeId();
                        }

                        return graphStore.getNode(nextNodeId)
                                .filter(node -> {
                                    // Check label matches
                                    if (targetLabel != null && !targetLabel.equals("*")
                                            && !targetLabel.equals(node.getLabel())) {
                                        return false;
                                    }
                                    // Apply constraints
                                    return constraintEvaluator.evaluate(node, constraints, stepIndex);
                                })
                                .map(node -> {
                                    List<UUID> newNodeIds = new ArrayList<>(path.nodeIds());
                                    newNodeIds.add(node.getId());

                                    List<String> newRelations = new ArrayList<>(path.relationTypes());
                                    newRelations.add(relationType);

                                    return new PathState(newNodeIds, newRelations, Map.of());
                                });
                    });
        });
    }

    private reactor.core.publisher.Flux<Edge> getEdgesReactive(UUID nodeId, String relationType, TraversalStep.Direction direction) {
        return graphStore.getEdgesForNode(nodeId)
                .filter(edge -> {
                    if (relationType != null && !relationType.equals(edge.getRelationType())) {
                        return false;
                    }
                    return switch (direction) {
                        case OUTGOING -> edge.getSourceNodeId().equals(nodeId);
                        case INCOMING -> edge.getTargetNodeId().equals(nodeId);
                        case BOTH -> true;
                    };
                });
    }

    private double scorePath(PathState path, RankingStrategy strategy) {
        return switch (strategy) {
            case PATH_LENGTH -> 1.0 / path.nodeIds().size(); // Shorter = higher score
            case EDGE_WEIGHT -> 1.0; // TODO: sum edge weights if available
            case NODE_CENTRALITY -> 1.0; // TODO: calculate centrality
            case SEMANTIC_SCORE -> 1.0; // Will be combined with vector scores later
            case HYBRID -> 1.0 / path.nodeIds().size(); // Default to path length
        };
    }

    private record PathState(
            List<UUID> nodeIds,
            List<String> relationTypes,
            Map<String, Object> properties) {
    }
}
