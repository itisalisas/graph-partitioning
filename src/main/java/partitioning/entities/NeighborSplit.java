package partitioning.entities;

import java.util.List;
import java.util.Optional;

import graph.Vertex;

public record NeighborSplit(
        Vertex vertex,
        List<Vertex> pathNeighbors,
        List<Vertex> leftNeighbors,
        List<Vertex> rightNeighbors,
        Optional<Boolean> firstPartOnBoundary  // Optional - only for external boundary vertices
) { }
