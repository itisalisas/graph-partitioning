package partitioning;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import graph.Edge;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;

class FlowResult {
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

public class MaxFlow {
    Graph<VertexOfDualGraph> graph;
    HashMap<VertexOfDualGraph, Integer> lastNeighbourIndex;
    VertexOfDualGraph source;
    VertexOfDualGraph sink;
    double flow;
    HashMap<VertexOfDualGraph, Integer> level;
    Queue<VertexOfDualGraph> queue;


    public MaxFlow(Graph<VertexOfDualGraph> graph, VertexOfDualGraph source, VertexOfDualGraph sink) {
        this.graph = graph;
        this.source = source;
        this.sink = sink;
        this.lastNeighbourIndex = new HashMap<>();
        for (VertexOfDualGraph vertex : graph.verticesArray()) {
            lastNeighbourIndex.put(vertex, 0);
        }
        this.flow = 0;
        this.level = new HashMap<>();
        this.queue = new LinkedList<>();
    }


    public FlowResult dinic() {
        int countIterations = 0;
        while (bfs()) {
            countIterations++;
            lastNeighbourIndex.replaceAll((vertex, integer) -> 0);
            double pushed;
            while ((pushed = dfs(source, Integer.MAX_VALUE)) != 0) {
                flow += pushed;
            }
        }
        System.out.println("Number of bfs iterations in dinic: " + countIterations + ", vertices: " + graph.verticesNumber() + ", edges: " + graph.edgesNumber());
        return new FlowResult(flow, graph, source, sink);
    }

    private boolean bfs() {
        for (VertexOfDualGraph v : graph.verticesArray()) {
            level.put(v, Integer.MAX_VALUE);
        }
        level.put(source, 0);
        queue.add(source);
        while (!queue.isEmpty()) {
            Vertex u = queue.peek();
            queue.remove();
            for (Map.Entry<VertexOfDualGraph, Edge> edge : graph.getEdges().get(u).entrySet()) {
                if (edge.getValue().flow < edge.getValue().getBandwidth() && level.get(edge.getKey()) == Integer.MAX_VALUE) {
                    level.put(edge.getKey(), level.get(u) + 1);
                    queue.add(edge.getKey());
                }
            }
        }
        return level.get(sink) != Integer.MAX_VALUE;
    }

    private double dfs(VertexOfDualGraph vertex, double flow) {
        if (vertex.equals(sink) || flow == 0) {
            return flow;
        }
        List<Map.Entry<VertexOfDualGraph, Edge>> neighbours = graph.getEdges().get(vertex).entrySet().stream().sorted(Comparator.comparing(v -> v.getKey().getName())).toList();
        for (; lastNeighbourIndex.get(vertex) < neighbours.size();
             lastNeighbourIndex.replace(vertex, lastNeighbourIndex.get(vertex), lastNeighbourIndex.get(vertex) + 1)) {
            Map.Entry<VertexOfDualGraph, Edge> entry = neighbours.get(lastNeighbourIndex.get(vertex));
            VertexOfDualGraph to = entry.getKey();
            Edge edge = entry.getValue();
            if (level.get(to) == level.get(vertex) + 1 && edge.flow < edge.getBandwidth()) {
                double pushed = dfs(to, Math.min(flow, edge.getBandwidth() - edge.flow));
                if (pushed > 0) {
                    edge.flow += pushed;
                    graph.getEdges().get(to).get(vertex).flow -= pushed;
                    return pushed;
                }
            }
        }
        return 0;
    }
}