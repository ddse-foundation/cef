package org.ddse.ml.cef.graph;

import org.ddse.ml.cef.domain.Edge;

import java.util.Objects;

/**
 * Wrapper class for Edge to allow multiple edges between the same nodes in
 * JGraphT.
 * DirectedWeightedPseudograph requires edge objects to be distinct.
 * 
 * @author mrmanna
 */
public class EdgeWrapper {

    private final Edge edge;

    public EdgeWrapper(Edge edge) {
        this.edge = Objects.requireNonNull(edge, "Edge cannot be null");
    }

    public Edge getEdge() {
        return edge;
    }

    public double getWeight() {
        return edge.getWeight() != null ? edge.getWeight() : 1.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        EdgeWrapper that = (EdgeWrapper) o;
        return Objects.equals(edge.getId(), that.edge.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(edge.getId());
    }

    @Override
    public String toString() {
        return "EdgeWrapper{" +
                "edgeId=" + edge.getId() +
                ", relationType='" + edge.getRelationType() + '\'' +
                ", weight=" + getWeight() +
                '}';
    }
}
