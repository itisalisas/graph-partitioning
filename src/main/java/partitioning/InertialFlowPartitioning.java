package partitioning;

import graph.*;
import org.junit.jupiter.api.Assertions;

import java.util.*;
import java.util.Map.Entry;
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

    private static class Vector2D {
        Point secondPoint;
        boolean isVertical;
        double k;

        public Vector2D(Point secondPoint) {
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

    List<Vector2D> lines = Arrays.asList(
            new Vector2D(new Point(0, 1)),
            new Vector2D(new Point(1, 0)),
            new Vector2D(new Point(1, 1)),
            new Vector2D(new Point(1, -1))
    );

    private Graph<VertexOfDualGraph> getLargestConnectedComponent(Graph<VertexOfDualGraph> graph) {
        List<HashSet<VertexOfDualGraph>> connectivityComponents = graph.makeUndirectedGraph().splitForConnectedComponents();
        HashSet<VertexOfDualGraph> largestComponent = connectivityComponents.stream().max(Comparator.comparingInt(HashSet::size)).orElseThrow();
        return graph.createSubgraph(largestComponent);
    }

    @Override
    public void balancedPartitionAlgorithm(Graph<VertexOfDualGraph> graph, int maxSumVerticesWeight) {

        Stack<Graph<VertexOfDualGraph>> stack = new Stack<>();
        graph = getLargestConnectedComponent(graph);
        this.graph = graph;

        stack.push(graph);

        while (!stack.isEmpty()) {
            Graph<VertexOfDualGraph> currentGraph = stack.pop().makeUndirectedGraph();

            List<VertexOfDualGraph> vertices = new ArrayList<>(currentGraph.verticesArray());
            if (currentGraph.verticesWeight() < maxSumVerticesWeight) {
                partition.add(new HashSet<VertexOfDualGraph>(currentGraph.verticesArray()));
                continue;
            }

            Vector2D bestLine = null;
            double maxStretch = -1;

            for (Vector2D line : lines) {
                vertices.sort(Comparator.comparing(v -> {
                    Point p = v;
                    Point projected = line.projectPoint(p);
                    return line.isVertical ? projected.getY() : projected.getX();
                }));

                double minProjection = line.isVertical
                        ? line.projectPoint(vertices.get(0)).getY()
                        : line.projectPoint(vertices.get(0)).getX();

                double maxProjection = line.isVertical
                        ? line.projectPoint(vertices.get(vertices.size() - 1)).getY()
                        : line.projectPoint(vertices.get(vertices.size() - 1)).getX();

                double stretch = maxProjection - minProjection;
                if (stretch > maxStretch) {
                    maxStretch = stretch;
                    bestLine = line;
                }
            }

            Vector2D finalBestLine = bestLine;
            vertices.sort(Comparator.comparing(v -> {
                Point p = v;
                Point projected = finalBestLine.projectPoint(p);
                return finalBestLine.isVertical ? projected.getY() : projected.getX();
            }));


            double totalWeight = vertices.stream().mapToDouble(VertexOfDualGraph::getWeight).sum();
            double targetWeight = (int) (PARAMETER * totalWeight);

            long maxIndex = vertices.stream().max(Comparator.comparingLong(VertexOfDualGraph::getName)).get().getName();

            VertexOfDualGraph source = new VertexOfDualGraph(maxIndex + 1);
            VertexOfDualGraph sink = new VertexOfDualGraph(maxIndex + 2);

            Set<VertexOfDualGraph> sourceSet = new HashSet<>();
            int index = 0;
            while (index < vertices.size() && sourceSet.stream().mapToDouble(VertexOfDualGraph::getWeight).sum() < targetWeight) {
                sourceSet = selectVerticesForSet(vertices, index, targetWeight, new HashSet<>(), currentGraph);
                index++;
            }

            /* может возникнуть проблема, что набрав sourceSet
            связное множество нужного веса не наберется,
            тогда берем максимальное по весу
             */
            Set<VertexOfDualGraph> sinkSet = new HashSet<>();
            Set<VertexOfDualGraph> maxSinkSet = new HashSet<>();
            index = 1;
            while (index <= vertices.size() && sinkSet.stream().mapToDouble(Vertex::getWeight).sum() < targetWeight) {
                sinkSet = selectVerticesForSet(vertices, vertices.size() - index, targetWeight, sourceSet, currentGraph);
                if (sinkSet.stream().mapToDouble(Vertex::getWeight).sum() >
                        maxSinkSet.stream().mapToDouble(Vertex::getWeight).sum()) {
                    maxSinkSet = sinkSet;
                }
                index++;
            }


            sinkSet = maxSinkSet;

            Graph<VertexOfDualGraph> copyGraph = createGraphWithSourceSink(currentGraph, sourceSet, source, sinkSet, sink);

            Assertions.assertEquals(currentGraph.verticesNumber() + 2, copyGraph.verticesNumber());

            MaxFlow maxFlow = new MaxFlow(copyGraph, source, sink);
            FlowResult flowResult = maxFlow.dinic();

            List<Graph<VertexOfDualGraph>> subpartition = partitionGraph(flowResult);

            for (Graph<VertexOfDualGraph> subgraph : subpartition) {
                stack.push(subgraph);
            }
        }

    }

    private Set<VertexOfDualGraph> selectVerticesForSet(List<VertexOfDualGraph> vertices, int startIndex, double targetWeight, Set<VertexOfDualGraph> sourceSet, Graph<VertexOfDualGraph> currentGraph) {
        Set<VertexOfDualGraph> vertexSet = new HashSet<>();
        int currentWeight = 0;
        Queue<VertexOfDualGraph> queue = new LinkedList<>();
        VertexOfDualGraph startVertex = vertices.get(startIndex);
        queue.add(startVertex);

        while (!queue.isEmpty() && currentWeight < targetWeight) {
        	VertexOfDualGraph current = queue.poll();
            if (!vertexSet.contains(current) && !sourceSet.contains(current) && Math.abs(vertices.indexOf(current) - startIndex) < vertices.size() / 2) {
                vertexSet.add(current);
                currentWeight += current.getWeight();
                for (VertexOfDualGraph neighbor : currentGraph.getEdges().get(current).keySet()) {
                    if (!vertexSet.contains(neighbor) && !sourceSet.contains(current)) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        return vertexSet;
    }

    private List<Graph<VertexOfDualGraph>> partitionGraph(FlowResult flow) {
        Graph<VertexOfDualGraph> graph = flow.graphWithFlow;
        List<Graph<VertexOfDualGraph>> subpartition = new ArrayList<>();
        List<VertexOfDualGraph> vertices = graph.verticesArray();
        Map<VertexOfDualGraph, Boolean> isConnectedWithSource = vertices.stream().collect(Collectors.toMap(Function.identity(), v -> Boolean.FALSE));

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


    void markComponent(Graph<VertexOfDualGraph> graph, VertexOfDualGraph source, Map<VertexOfDualGraph, Boolean> isConnectedWithSource, VertexOfDualGraph sink) {
        LinkedList<VertexOfDualGraph> queue = new LinkedList<>();
        queue.add(source);
        isConnectedWithSource.put(source, true);
        while (!queue.isEmpty()) {
        	Vertex vertex = queue.poll();
            for (Entry<VertexOfDualGraph, Edge> connectedVertex : graph.getEdges().get(vertex).entrySet()) {
                if (!isConnectedWithSource.get(connectedVertex.getKey()) &&
                        connectedVertex.getValue().flow < connectedVertex.getValue().getBandwidth()) {
                    isConnectedWithSource.put(connectedVertex.getKey(), true);
                    queue.add(connectedVertex.getKey());
                }
            }
        }

    }

    public Graph<VertexOfDualGraph> createGraphWithSourceSink(Graph<VertexOfDualGraph> currentGraph, Set<VertexOfDualGraph> sourceSet, VertexOfDualGraph source, Set<VertexOfDualGraph> sinkSet, VertexOfDualGraph sink) {
        Graph<VertexOfDualGraph> newGraph = currentGraph.clone();

        for (VertexOfDualGraph s : sourceSet) {
            newGraph.addEdge(source, s, 0, Integer.MAX_VALUE);
            newGraph.addEdge(s, source, 0, Integer.MAX_VALUE);
        }

        for (VertexOfDualGraph t : sinkSet) {
            newGraph.addEdge(t, sink, 0, Integer.MAX_VALUE);
            newGraph.addEdge(sink, t, 0, Integer.MAX_VALUE);
        }

        return newGraph;
    }

}