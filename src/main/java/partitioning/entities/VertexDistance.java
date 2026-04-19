package partitioning.entities;

import graph.Vertex;

public record VertexDistance(
    Vertex vertex,
    double distance
) { }
