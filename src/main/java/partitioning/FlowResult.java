package partitioning;

import graph.Graph;
import graph.VertexOfDualGraph;

public class FlowResult {
    double flowSize;
    Graph<VertexOfDualGraph> graphWithFlow;
    VertexOfDualGraph source;
    VertexOfDualGraph sink;

    public FlowResult(double flow, Graph<VertexOfDualGraph> graph, VertexOfDualGraph source, VertexOfDualGraph sink) {
        this.flowSize = flow;
        this.graphWithFlow = graph;
        this.source = source;
        this.sink = sink;
    }

    public double getFlowSize() {
        return flowSize;
    }
}
