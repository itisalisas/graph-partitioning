package partitioning.models;

import java.util.List;
import java.util.Map;

import graph.Vertex;
import graph.VertexOfDualGraph;

public record SPTWithRegionWeights(
        List<VertexOfDualGraph> faces,
        List<Double> regionWeights,
        Vertex root,
        Map<Vertex, Vertex> previous,
        Map<Vertex, List<Vertex>> children,
        List<Vertex> boundaryLeaves,
        Map<Vertex, Double> leftRegionWeights,
        double totalRegionWeight
) { }
