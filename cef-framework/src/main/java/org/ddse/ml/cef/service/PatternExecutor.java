package org.ddse.ml.cef.service;

import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.dto.*;
import org.ddse.ml.cef.graph.InMemoryKnowledgeGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for executing graph patterns.
 * Used internally by KnowledgeRetrieverImpl for pattern-based queries.
 */
@Component
public class PatternExecutor {

    private static final Logger log = LoggerFactory.getLogger(PatternExecutor.class);

    private final InMemoryKnowledgeGraph graph;
    private final ConstraintEvaluator constraintEvaluator;

    public PatternExecutor(InMemoryKnowledgeGraph graph, ConstraintEvaluator constraintEvaluator) {
        this.graph = graph;
        this.constraintEvaluator = constraintEvaluator;
    }

    /**
     * Execute single pattern starting from entry points.
     */
    public List<MatchedPath> executePattern(
            GraphPattern pattern,
            Set<UUID> entryPoints,
            int maxPaths,
            RankingStrategy rankingStrategy) {
        log.debug("Executing pattern {} with {} entry points", pattern.patternId(), entryPoints.size());

        List<MatchedPath> matchedPaths = new ArrayList<>();

        for (UUID entryPoint : entryPoints) {
            // Execute step-by-step traversal
            List<PathState> paths = traverseSteps(entryPoint, pattern.steps(), pattern.constraints());

            // Convert to MatchedPath and score
            for (PathState path : paths) {
                double score = scorePath(path, rankingStrategy);
                matchedPaths.add(new MatchedPath(
                        pattern.patternId(),
                        path.nodeIds(),
                        path.relationTypes(),
                        path.properties(),
                        score,
                        "Matched pattern: " + pattern.description()));
            }

            if (matchedPaths.size() >= maxPaths)
                break;
        }

        // Sort by score and return top maxPaths
        List<MatchedPath> result = matchedPaths.stream()
                .sorted(Comparator.comparingDouble(MatchedPath::score).reversed())
                .limit(maxPaths)
                .toList();

        log.debug("Pattern {} matched {} paths", pattern.patternId(), result.size());
        return result;
    }

    /**
     * Step-by-step traversal with constraint filtering.
     */
    private List<PathState> traverseSteps(
            UUID startNode,
            List<TraversalStep> steps,
            List<Constraint> constraints) {
        List<PathState> currentPaths = List.of(new PathState(List.of(startNode), List.of(), Map.of()));

        for (int i = 0; i < steps.size(); i++) {
            TraversalStep step = steps.get(i);
            List<PathState> nextPaths = new ArrayList<>();

            log.debug("Step {}: {} --{}-->  {} (current paths: {})", i, step.sourceLabel(), step.relationType(),
                    step.targetLabel(), currentPaths.size());

            for (PathState path : currentPaths) {
                UUID currentNode = path.nodeIds().get(path.nodeIds().size() - 1);

                // Get neighbors via this relation type
                Set<Edge> edges = getEdges(currentNode, step.relationType(), step.direction());
                log.debug("  Node {} has {} edges with relation {}", currentNode, edges.size(), step.relationType());

                for (Edge edge : edges) {
                    UUID nextNodeId = edge.getTargetNodeId();
                    Node nextNode = graph.findNode(nextNodeId).orElse(null);

                    if (nextNode == null)
                        continue;

                    // Check label matches (support "*" wildcard for any label)
                    if (step.targetLabel() != null && !step.targetLabel().equals("*")
                            && !step.targetLabel().equals(nextNode.getLabel())) {
                        continue;
                    }

                    // Apply constraints for this step
                    if (!constraintEvaluator.evaluate(nextNode, constraints, i)) {
                        continue;
                    }

                    // Valid path - add to next
                    List<UUID> newNodeIds = new ArrayList<>(path.nodeIds());
                    newNodeIds.add(nextNodeId);

                    List<String> newRelations = new ArrayList<>(path.relationTypes());
                    newRelations.add(step.relationType());

                    nextPaths.add(new PathState(newNodeIds, newRelations, Map.of()));
                }
            }

            currentPaths = nextPaths;
            if (currentPaths.isEmpty())
                break; // No valid paths
        }

        return currentPaths;
    }

    private Set<Edge> getEdges(UUID nodeId, String relationType, TraversalStep.Direction direction) {
        // Get all edges connected to this node
        Set<Edge> allEdges = graph.getEdges(nodeId);
        log.debug("    getEdges({}, {}, {}) - graph returned {} total edges", nodeId, relationType, direction,
                allEdges.size());

        Set<Edge> filtered = switch (direction) {
            case OUTGOING -> allEdges.stream()
                    .filter(e -> e.getSourceNodeId().equals(nodeId))
                    .filter(e -> relationType == null || relationType.equals(e.getRelationType()))
                    .collect(Collectors.toSet());
            case INCOMING -> allEdges.stream()
                    .filter(e -> e.getTargetNodeId().equals(nodeId))
                    .filter(e -> relationType == null || relationType.equals(e.getRelationType()))
                    .collect(Collectors.toSet());
            case BOTH -> allEdges.stream()
                    .filter(e -> relationType == null || relationType.equals(e.getRelationType()))
                    .collect(Collectors.toSet());
        };

        log.debug("    After filtering: {} edges match", filtered.size());
        return filtered;
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
