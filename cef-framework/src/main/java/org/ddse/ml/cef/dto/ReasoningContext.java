package org.ddse.ml.cef.dto;

import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;

import java.util.Set;

public record ReasoningContext(
        Set<Node> relatedNodes,
        Set<Edge> relatedEdges,
        Set<Chunk> relatedChunks) {
}
