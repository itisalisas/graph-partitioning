package partitioning.maxflow;

import graph.EdgeOfGraph;
import graph.Vertex;
import java.util.*;

public class CornerConstraints {
    private final Set<Long> cornerVertices;
    private final Map<Long, List<EdgeOfGraph<Vertex>>> allowedEdgesForCorner;

    public CornerConstraints(
            Set<Long> cornerVertices,
            Map<Long, List<EdgeOfGraph<Vertex>>> allowedEdgesForCorner) {
        this.cornerVertices = cornerVertices;
        this.allowedEdgesForCorner = allowedEdgesForCorner;
    }

    public Set<Long> getCornerVertices() {
        return cornerVertices;
    }

    public Map<Long, List<EdgeOfGraph<Vertex>>> getAllowedEdgesForCorner() {
        return allowedEdgesForCorner;
    }

    public boolean isCornerVertex(Vertex v) {
        return cornerVertices.contains(v.getName());
    }

    public boolean isNeighborAllowed(Vertex current, Vertex neighbor) {
        if (!isCornerVertex(current)) {
            return true;
        }

        List<EdgeOfGraph<Vertex>> allowedEdges = allowedEdgesForCorner.get(current.getName());
        if (allowedEdges == null) {
            return true;
        }

        for (EdgeOfGraph<Vertex> edge : allowedEdges) {
            if (edge.end.getName() == neighbor.getName()) {
                return true;
            }
        }
        return false;
    }

    public static CornerConstraints empty() {
        return new CornerConstraints(Set.of(), Map.of());
    }
}