package partitioning.models;

import java.util.List;
import java.util.Optional;

import graph.Vertex;

public record NeighborSplit(
        Vertex vertex,
        List<Vertex> pathNeighbors,
        List<Vertex> leftNeighbors,
        List<Vertex> rightNeighbors,
        Optional<Boolean> firstPartOnBoundary  // Optional - only for external boundary vertices
) {
    // Конструктор для вершин НЕ на external boundary
    public NeighborSplit(Vertex vertex, List<Vertex> pathNeighbors,
                         List<Vertex> leftNeighbors, List<Vertex> rightNeighbors) {
        this(vertex, pathNeighbors, leftNeighbors, rightNeighbors, Optional.empty());
    }
}
