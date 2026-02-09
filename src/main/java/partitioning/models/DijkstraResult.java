package partitioning.models;

import java.util.List;
import java.util.Map;

import graph.Vertex;

public record DijkstraResult(
        List<Vertex> path,
        double distance,
        Map<Vertex, Vertex>previous,
        Map<Vertex, Double> distances,
        List<Vertex> boundaryLeaves,
        Map<Vertex, Double> leftRegionWeights,
        double totalRegionWeight
) { }
