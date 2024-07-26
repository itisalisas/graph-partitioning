package partitioningGraph;

import org.junit.jupiter.api.Assertions;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class InertialFlowPartitioning extends BalancedPartitioningOfPlanarGraphs {

    private final List<Graph> partition = new ArrayList<>();

    private final double PARAMETER;

    private final int MAX_VERTICES_NUMBER = 1000;

    public InertialFlowPartitioning() {
        this.PARAMETER = 0.25;
    }

    public InertialFlowPartitioning(double parameter) {
        this.PARAMETER = parameter;
    }

    // lines kx - y = 0
    private static class Line {
        Point secondPoint;
        boolean isVertical;
        double k;

        public Line(Point secondPoint) {
            this.secondPoint = secondPoint;
            isVertical = secondPoint.getX() == 0;
            if (!isVertical) {
                k = (secondPoint.getY()) /
                        (secondPoint.getX());
            }
        }

        public Point projectPoint(Point point) {
            double xProjection, yProjection;
            if (!isVertical) {
                xProjection = (point.getX() + k * point.getY()) / (1 + k * k);
                yProjection = k * xProjection;
            } else {
                xProjection = secondPoint.getX();
                yProjection = point.getY();
            }
            return new Point(xProjection, yProjection);
        }

    }

    List<Line> lines = Arrays.asList(
            new Line(new Point(0, 1)),
            new Line(new Point(1, 0)),
            new Line(new Point(1, 1)),
            new Line(new Point(1, -1))
            );

    @Override
    public List<Graph> balancedPartitionAlgorithm(Graph graph) {
        balancedPartitionAlgorithmHelper(graph);
        return partition;
    }

    private void balancedPartitionAlgorithmHelper(Graph graph) {

        List<Vertex> vertices = new ArrayList<>(graph.verticesArray());
        if (graph.verticesNumber() < MAX_VERTICES_NUMBER) {
            partition.add(graph);
            return;
        }
        long maxIndex = vertices.stream().max(Comparator.comparingLong(Vertex::getName)).get().getName();
        List<FlowResult> results = new ArrayList<>();

        for (Line line : lines) {
            vertices.sort(Comparator.comparing(v -> {
                Point p = v.getPoint();
                Point projected = line.projectPoint(p);
                return line.isVertical ? projected.getY() : projected.getX();
            }));

            int sourceLimit = (int) (PARAMETER * vertices.size());
            int sinkLimit = vertices.size() - sourceLimit;

            Set<Vertex> sourceSet = new HashSet<>(vertices.subList(0, sourceLimit));
            Set<Vertex> sinkSet = new HashSet<>(vertices.subList(sinkLimit, vertices.size()));
            Vertex source = new Vertex(maxIndex + 1);
            Vertex sink = new Vertex(maxIndex + 2);

            Graph copyGraph = copyGraph(graph, sourceSet, source, sinkSet, sink);
            Assertions.assertEquals(graph.verticesNumber() + 2, copyGraph.verticesNumber());

            MaxFlow maxFlow = new MaxFlow(copyGraph, source, sink);
            FlowResult flowResult = maxFlow.dinic(line);
            results.add(flowResult);
        }

        FlowResult bestFlow = results.stream().max(Comparator.comparing(FlowResult::getFlowSize)).orElseThrow();

        List<Graph> subpartition = partitionGraph(bestFlow);

        for (Graph subgraph : subpartition) {
            balancedPartitionAlgorithmHelper(subgraph);
        }

    }

    private List<Graph> partitionGraph(FlowResult flow) {
        Graph graph = flow.graphWithFlow;
        List<Graph> subpartition = new ArrayList<>();
        List<Vertex> vertices = graph.verticesArray();
        Map<Vertex, Boolean> isConnectedWithSource = vertices.stream().collect(Collectors.toMap(Function.identity(), v -> Boolean.FALSE));

        markComponent(graph, flow.source, isConnectedWithSource);

        graph.deleteVertex(flow.source);
        graph.deleteVertex(flow.sink);
        subpartition.add(new Graph());
        subpartition.add(new Graph());

        for (Vertex vertex : graph.verticesArray()) {
            subpartition.get(isConnectedWithSource.get(vertex) ? 1 : 0).addVertex(vertex);
        }

        for (int i = 0; i < 2; i++) {
            for (Vertex vertex : subpartition.get(i).verticesArray()) {
                for (Map.Entry<Vertex, Edge> entry : graph.getEdges().get(vertex).entrySet()) {
                        if (isConnectedWithSource.get(entry.getKey()) == isConnectedWithSource.get(vertex)) {
                            subpartition.get(i).addEdge(vertex, entry.getKey(), entry.getValue().getLength());
                        }
                }
            }
        }

        Assertions.assertEquals(graph.verticesNumber(), subpartition.get(0).verticesNumber() + subpartition.get(1).verticesNumber());

        return subpartition;
    }

    void markComponent(Graph graph, Vertex source, Map<Vertex, Boolean> isConnectedWithSource) {
        LinkedList<Vertex> queue = new LinkedList<>();
        queue.add(source);
        isConnectedWithSource.put(source, true);
        while (!queue.isEmpty()) {
            Vertex vertex = queue.poll();
            for (Map.Entry<Vertex, Edge> connectedVertex : graph.getEdges().get(vertex).entrySet()) {
                if (!isConnectedWithSource.get(connectedVertex.getKey()) && connectedVertex.getValue().flow != connectedVertex.getValue().getBandwidth()) {
                    isConnectedWithSource.put(connectedVertex.getKey(), true);
                    queue.add(connectedVertex.getKey());
                }
            }
        }

    }

    public Graph copyGraph(Graph graph, Set<Vertex> sourceSet, Vertex source, Set<Vertex> sinkSet, Vertex sink) {
        Graph newGraph = new Graph();
        List<EdgeOfGraph> edges = Arrays.stream(graph.edgesArray()).toList();
        List<Vertex> vertices = new ArrayList<>(graph.verticesArray());

        for (Vertex vertex : vertices) {
            newGraph.addVertex(new Vertex(vertex.getName(), vertex.getPoint()));
        }

        for (EdgeOfGraph edge : edges) {
            EdgeOfGraph newEdge;
            newEdge = new EdgeOfGraph(edge.getBegin(), edge.getEnd(), edge.getLength());
            newGraph.addEdge(newEdge.getBegin(), newEdge.getEnd(), newEdge.getLength());
        }

        for (Vertex s : sourceSet) {
            newGraph.addEdge(source, s, 0, Integer.MAX_VALUE);
        }

        for (Vertex t : sinkSet) {
            newGraph.addEdge(t, sink, 0, Integer.MAX_VALUE);
        }

        return newGraph;

    }

    private static class MaxFlow {
        Graph graph;
        HashMap<Vertex, Integer> ptr;
        Vertex source;
        Vertex sink;
        double flow;
        HashMap<Vertex, Integer> level;
        Queue<Vertex> queue;

        public MaxFlow(Graph graph, Vertex source, Vertex sink) {
            this.graph = graph;
            this.source = source;
            this.sink = sink;
            this.ptr = new HashMap<>();
            for (Vertex vertex : graph.verticesArray()) {
                ptr.put(vertex, 0);
            }
            this.flow = 0;
            this.level = new HashMap<>();
            this.queue = new LinkedList<>();
        }


        public FlowResult dinic(Line line) {
            while (bfs()) {
                ptr.replaceAll((vertex, integer) -> 0);
                double pushed;
                while ((pushed = dfs(source, Integer.MAX_VALUE)) != 0) {
                    flow += pushed;
                }
            }
            return new FlowResult(line, flow, graph, source, sink);
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
            if (vertex == sink || flow == 0) {
                return flow;
            }
            for (; ptr.get(vertex) < graph.getEdges().get(vertex).size(); ptr.replace(vertex, ptr.get(vertex), ptr.get(vertex) + 1)) {
                Map.Entry<Vertex, Edge> entry = graph.getEdges().get(vertex).entrySet().stream().toList().get(ptr.get(vertex));
                Vertex to = entry.getKey();
                Edge edge = entry.getValue();
                if (level.get(to) == level.get(vertex) + 1 && edge.flow < edge.getBandwidth()) {
                    double pushed = dfs(to, Math.min(flow, edge.getBandwidth() - edge.flow));
                    if (pushed > 0) {
                        edge.flow += pushed;
                        if (graph.getEdges().get(to).containsKey(vertex)) {
                            graph.getEdges().get(to).get(vertex).flow -= pushed;
                        }
                        return pushed;
                    }
                }
            }
            return 0;
        }
    }

    private static class FlowResult {
        Line line;
        double flowSize;
        Graph graphWithFlow;
        Vertex source;
        Vertex sink;

        public FlowResult(Line line, double flow, Graph graph, Vertex source, Vertex sink) {
            this.line = line;
            this.flowSize = flow;
            this.graphWithFlow = graph;
            this.source = source;
            this.sink = sink;
        }

        public double getFlowSize() {
            return flowSize;
        }
    }

}
