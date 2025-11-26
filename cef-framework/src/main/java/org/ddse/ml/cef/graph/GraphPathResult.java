package org.ddse.ml.cef.graph;

import org.jgrapht.GraphPath;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Result of a graph path query containing node IDs, relation types, and
 * metrics.
 * 
 * @param nodeIds       List of node UUIDs in the path (from source to target)
 * @param relationTypes List of relation type names for each edge in the path
 * @param totalWeight   Total weight of the path (sum of edge weights)
 * @param length        Number of edges in the path
 * 
 * @author mrmanna
 */
public record GraphPathResult(
        List<UUID> nodeIds,
        List<String> relationTypes,
        double totalWeight,
        int length) {
    /**
     * Convert from JGraphT GraphPath to our DTO.
     */
    public static GraphPathResult fromJGraphTPath(GraphPath<UUID, EdgeWrapper> path) {
        if (path == null) {
            return null;
        }

        List<UUID> nodeIds = path.getVertexList();
        List<String> relationTypes = path.getEdgeList().stream()
                .map(wrapper -> wrapper.getEdge().getRelationType())
                .collect(Collectors.toList());

        double totalWeight = path.getWeight();
        int length = path.getLength();

        return new GraphPathResult(nodeIds, relationTypes, totalWeight, length);
    }
}
