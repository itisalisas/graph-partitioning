package partitioning.models;

import graph.Graph;
import graph.VertexOfDualGraph;

public record FlowResult(
        double flowSize,
        Graph<VertexOfDualGraph> graphWithFlow,
        VertexOfDualGraph source,
        VertexOfDualGraph sink
) {}
