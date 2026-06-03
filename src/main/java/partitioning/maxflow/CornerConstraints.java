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
        return cornerVertices.contains(v.getName()) ||
                (cornerVertices.contains(v.getName() / 1000) && (v.getName() % 1000 == 1 || v.getName() % 1000 == 2));
    }

    public boolean isNeighborAllowed(Vertex current, Vertex neighbor) {
        if (!isCornerVertex(current)) {
            return true;
        }

        List<EdgeOfGraph<Vertex>> allowedEdges = allowedEdgesForCorner.get(current.getName());
        if (allowedEdges == null) {
            allowedEdges = allowedEdgesForCorner.get(current.getName() / 1000);
            if (allowedEdges == null) {
                return true;
            }
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

    public static CornerConstraints merge(CornerConstraints a, CornerConstraints b) {
        Set<Long> mergedCorners = new HashSet<>(a.cornerVertices);
        mergedCorners.addAll(b.cornerVertices);

        Map<Long, List<EdgeOfGraph<Vertex>>> mergedEdges = new HashMap<>(a.allowedEdgesForCorner);
        for (Map.Entry<Long, List<EdgeOfGraph<Vertex>>> entry : b.allowedEdgesForCorner.entrySet()) {
            mergedEdges.merge(entry.getKey(), entry.getValue(), (existing, incoming) -> {
                Set<Long> seen = new HashSet<>();
                List<EdgeOfGraph<Vertex>> combined = new ArrayList<>(existing);
                for (EdgeOfGraph<Vertex> e : existing) seen.add(e.end.getName());
                for (EdgeOfGraph<Vertex> e : incoming) {
                    if (seen.add(e.end.getName())) combined.add(e);
                }
                return combined;
            });
        }
        return new CornerConstraints(mergedCorners, mergedEdges);
    }
}