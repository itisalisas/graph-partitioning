package partitioning.entities;

import java.util.List;
import java.util.Map;

import graph.Vertex;
import graph.VertexOfDualGraph;

public record SPTWithRegionWeights(
        List<VertexOfDualGraph> faces,
        List<Double> regionWeights,
        List<Double> distances,
        Vertex root,
        Map<Vertex, Vertex> previous,
        Map<Vertex, List<Vertex>> children,
        List<Vertex> boundaryLeaves,
        double totalRegionWeight
) { }
