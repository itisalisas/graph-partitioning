package partitioning;

import graph.Edge;
import graph.Graph;
import graph.Vertex;

import java.util.*;

class FlowResult {
    double flowSize;
    Graph graphWithFlow;
    Vertex source;
    Vertex sink;

    public FlowResult(double flow, Graph graph, Vertex source, Vertex sink) {
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
    Graph graph;
    HashMap<Vertex, Integer> lastNeighbourIndex;
    Vertex source;
    Vertex sink;
    double flow;
    HashMap<Vertex, Integer> level;
    Queue<Vertex> queue;


    public MaxFlow(Graph graph, Vertex source, Vertex sink) {
        this.graph = graph;
        this.source = source;
        this.sink = sink;
        this.lastNeighbourIndex = new HashMap<>();
        for (Vertex vertex : graph.verticesArray()) {
            lastNeighbourIndex.put(vertex, 0);
        }
        this.flow = 0;
        this.level = new HashMap<>();
        this.queue = new LinkedList<>();
    }


    public FlowResult dinic() {
        while (bfs()) {

            lastNeighbourIndex.replaceAll((vertex, integer) -> 0);
            double pushed;
            while ((pushed = dfs(source, Integer.MAX_VALUE)) != 0) {
                flow += pushed;
            }
        }
        return new FlowResult(flow, graph, source, sink);
    }

    private boolean bfs() {
        for (Vertex v : graph.verticesArray()) {
            level.put(v, Integer.MAX_VALUE);
        }
        level.put(source, 0);
        queue.add(source);
        while (!queue.isEmpty()) {
            Vertex u = queue.peek();
            queue.remove();
            for (Map.Entry<Vertex, Edge> edge : graph.getEdges().get(u).entrySet()) {
                if (edge.getValue().flow < edge.getValue().getBandwidth() && level.get(edge.getKey()) == Integer.MAX_VALUE) {
                    level.put(edge.getKey(), level.get(u) + 1);
                    queue.add(edge.getKey());
                }
            }
        }
        return level.get(sink) != Integer.MAX_VALUE;
    }

    private double dfs(Vertex vertex, double flow) {
        if (vertex.equals(sink) || flow == 0) {
            return flow;
        }
        List<Map.Entry<Vertex, Edge>> neighbours = graph.getEdges().get(vertex).entrySet().stream().sorted(Comparator.comparing(v -> v.getKey().getName())).toList();
        for (; lastNeighbourIndex.get(vertex) < neighbours.size();
             lastNeighbourIndex.replace(vertex, lastNeighbourIndex.get(vertex), lastNeighbourIndex.get(vertex) + 1)) {
            Map.Entry<Vertex, Edge> entry = neighbours.get(lastNeighbourIndex.get(vertex));
            Vertex to = entry.getKey();
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
