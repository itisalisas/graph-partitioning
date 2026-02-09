package partitioning.models;

import graph.Vertex;

public record VertexDistance(
    Vertex vertex,
    double distance
) {
}
