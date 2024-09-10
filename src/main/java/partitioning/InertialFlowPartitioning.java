package partitioning;

import graph.*;
import org.junit.jupiter.api.Assertions;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class InertialFlowPartitioning extends BalancedPartitioningOfPlanarGraphs {

    private final double PARAMETER;

    public InertialFlowPartitioning() {
        this.PARAMETER = 0.25;
    }

    public InertialFlowPartitioning(double parameter) {
        this.PARAMETER = parameter;
    }

    // lines kx - y = 0
    // TODO : change to Direction
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

    private Graph getLargestConnectedComponent(Graph graph) {
        List<HashSet<Vertex>> connectivityComponents = graph.makeUndirectedGraph().splitForConnectedComponents();
        HashSet<Vertex> largestComponent = connectivityComponents.stream().max(Comparator.comparingInt(HashSet::size)).orElseThrow();
        return graph.createSubgraph(largestComponent);
    }

    @Override
    public void balancedPartitionAlgorithm(Graph graph, int maxSumVerticesWeight) {

        Stack<Graph> stack = new Stack<>();
        graph = getLargestConnectedComponent(graph);
        this.graph = graph;

        stack.push(graph);

        while (!stack.isEmpty()) {
            Graph currentGraph = stack.pop().makeUndirectedGraph();

            List<Vertex> vertices = new ArrayList<>(currentGraph.verticesArray());
            if (currentGraph.verticesWeight() < maxSumVerticesWeight) {
                partition.add(new HashSet<>(currentGraph.verticesArray()));
                continue;
            }

            Line bestLine = null;
            double maxStretch = -1;

            for (Line line : lines) {
                vertices.sort(Comparator.comparing(v -> {
                    Point p = v.getPoint();
                    Point projected = line.projectPoint(p);
                    return line.isVertical ? projected.getY() : projected.getX();
                }));

                double minProjection = line.isVertical
                        ? line.projectPoint(vertices.get(0).getPoint()).getY()
                        : line.projectPoint(vertices.get(0).getPoint()).getX();

                double maxProjection = line.isVertical
                        ? line.projectPoint(vertices.get(vertices.size() - 1).getPoint()).getY()
                        : line.projectPoint(vertices.get(vertices.size() - 1).getPoint()).getX();

                double stretch = maxProjection - minProjection;
                if (stretch > maxStretch) {
                    maxStretch = stretch;
                    bestLine = line;
                }
            }

            Line finalBestLine = bestLine;
            vertices.sort(Comparator.comparing(v -> {
                Point p = v.getPoint();
                Point projected = finalBestLine.projectPoint(p);
                return finalBestLine.isVertical ? projected.getY() : projected.getX();
            }));


            int totalWeight = vertices.stream().mapToInt(Vertex::getWeight).sum();
            int targetWeight = (int) (PARAMETER * totalWeight);

            long maxIndex = vertices.stream().max(Comparator.comparingLong(Vertex::getName)).get().getName();

            Vertex source = new Vertex(maxIndex + 1);
            Vertex sink = new Vertex(maxIndex + 2);

            Set<Vertex> sourceSet = new HashSet<>();
            int index = 0;
            while (index < vertices.size() && sourceSet.stream().mapToDouble(Vertex::getWeight).sum() < targetWeight) {
                sourceSet = selectVerticesForSet(vertices, index, targetWeight, new HashSet<>(), currentGraph);
                index++;
            }
            Set<Vertex> sinkSet = new HashSet<>();
            index = 1;
            while (index <= vertices.size() && sinkSet.stream().mapToDouble(Vertex::getWeight).sum() < targetWeight) {
                sinkSet = selectVerticesForSet(vertices, vertices.size() - index, targetWeight, sourceSet, currentGraph);
                index++;
            }

            Graph copyGraph = createGraphWithSourceSink(currentGraph, sourceSet, source, sinkSet, sink);
            Assertions.assertEquals(currentGraph.verticesNumber() + 2, copyGraph.verticesNumber());

            MaxFlow maxFlow = new MaxFlow(copyGraph, source, sink);
            FlowResult flowResult = maxFlow.dinic();

            List<Graph> subpartition = partitionGraph(flowResult);

            for (Graph subgraph : subpartition) {
                stack.push(subgraph);
            }
        }

    }

    private Set<Vertex> selectVerticesForSet(List<Vertex> vertices, int startIndex, int targetWeight, Set<Vertex> dontUse, Graph graph) {
        Set<Vertex> vertexSet = new HashSet<>();
        int currentWeight = 0;
        Queue<Vertex> queue = new LinkedList<>();
        Vertex startVertex = vertices.get(startIndex);
        queue.add(startVertex);

        while (!queue.isEmpty() && currentWeight < targetWeight) {
            Vertex current = queue.poll();
            if (!vertexSet.contains(current) && !dontUse.contains(current) && Math.abs(vertices.indexOf(current) - startIndex) < vertices.size() / 2) {
                vertexSet.add(current);
                currentWeight += current.getWeight();
                for (Vertex neighbor : graph.getEdges().get(current).keySet()) {
                    if (!vertexSet.contains(neighbor) && !dontUse.contains(current)) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        return vertexSet;
    }

    private List<Graph> partitionGraph(FlowResult flow) {
        Graph graph = flow.graphWithFlow;
        List<Graph> subpartition = new ArrayList<>();
        List<Vertex> vertices = graph.verticesArray();
        Map<Vertex, Boolean> isConnectedWithSource = vertices.stream().collect(Collectors.toMap(Function.identity(), v -> Boolean.FALSE));

        markComponent(graph, flow.source, isConnectedWithSource, flow.sink);

        graph.deleteVertex(flow.source);
        graph.deleteVertex(flow.sink);

        for (int i = 0; i < 2; i++) {
            subpartition.add(graph.createSubgraph(i == 0 ?
                    isConnectedWithSource.keySet().stream().filter(isConnectedWithSource::get).collect(Collectors.toSet()) :
                    isConnectedWithSource.keySet().stream().filter(v -> !isConnectedWithSource.get(v)).collect(Collectors.toSet())));
        }

        Assertions.assertEquals(graph.verticesNumber(), subpartition.get(0).verticesNumber() + subpartition.get(1).verticesNumber());

        return subpartition;
    }


    void markComponent(Graph graph, Vertex source, Map<Vertex, Boolean> isConnectedWithSource, Vertex sink) {
        LinkedList<Vertex> queue = new LinkedList<>();
        queue.add(source);
        isConnectedWithSource.put(source, true);
        while (!queue.isEmpty()) {
            Vertex vertex = queue.poll();
            for (Map.Entry<Vertex, Edge> connectedVertex : graph.getEdges().get(vertex).entrySet()) {
                if (!isConnectedWithSource.get(connectedVertex.getKey()) &&
                        connectedVertex.getValue().flow < connectedVertex.getValue().getBandwidth()) {
                    isConnectedWithSource.put(connectedVertex.getKey(), true);
                    queue.add(connectedVertex.getKey());
                }
            }
        }

    }

    public Graph createGraphWithSourceSink(Graph graph, Set<Vertex> sourceSet, Vertex source, Set<Vertex> sinkSet, Vertex sink) {
        Graph newGraph = graph.clone();

        for (Vertex s : sourceSet) {
            newGraph.addEdge(source, s, 0, Integer.MAX_VALUE);
            newGraph.addEdge(s, source, 0, Integer.MAX_VALUE);
        }

        for (Vertex t : sinkSet) {
            newGraph.addEdge(t, sink, 0, Integer.MAX_VALUE);
            newGraph.addEdge(sink, t, 0, Integer.MAX_VALUE);
        }

        return newGraph;
    }

}