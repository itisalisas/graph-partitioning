package partitioning.entities;

import java.util.List;
import java.util.Map;

import graph.Vertex;
import graph.VertexOfDualGraph;

public record DijkstraResult(
        List<Vertex> path,
        double distance,
        Map<Vertex, Vertex> previous,
        Map<Vertex, Double> dijkstraDistances,
        List<Vertex> boundaryLeaves,
        List<VertexOfDualGraph> regions,
        List<Double> weights,
        List<Double> distances,
        List<Integer> leafIndices,
        double totalRegionWeight
) { }
