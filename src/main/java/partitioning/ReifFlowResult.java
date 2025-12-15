package partitioning;

import graph.Graph;
import graph.VertexOfDualGraph;

public final class ReifFlowResult extends FlowResult {
    public ReifFlowResult(double flow, Graph<VertexOfDualGraph> graph, VertexOfDualGraph source, VertexOfDualGraph sink) {
        super(flow, graph, source, sink);
    }
}
