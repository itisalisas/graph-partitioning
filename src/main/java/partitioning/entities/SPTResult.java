package partitioning.entities;

import java.util.List;

import graph.Vertex;

public record SPTResult(
        double balanceWeight,
        double totalDistance,
        List<Vertex> path
) {
}
