package partitioning.entities;

import java.util.List;

import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;

public record FlowResult(
        double flowSize,
        Graph<VertexOfDualGraph> graphWithFlow,
        VertexOfDualGraph source,
        VertexOfDualGraph sink,
        List<Vertex> pathInOriginalGraph
) {
    public FlowResult(double flowSize,
                      Graph<VertexOfDualGraph> graphWithFlow,
                      VertexOfDualGraph source,
                      VertexOfDualGraph sink
    ) {
        this(flowSize, graphWithFlow, source, sink, List.of());
    }

    public FlowResult(List<Vertex> pathInOriginalGraph) {
        this(0, null, null, null, pathInOriginalGraph);
    }

}
