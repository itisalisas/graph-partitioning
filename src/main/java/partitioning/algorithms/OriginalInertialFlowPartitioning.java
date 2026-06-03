package partitioning.algorithms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graph.Edge;
import graph.Graph;
import graph.Point;
import graph.Vertex;
import graph.VertexOfDualGraph;
import partitioning.entities.FlowResult;
import partitioning.maxflow.MaxFlow;
import partitioning.maxflow.MaxFlowDinic;
import readWrite.CoordinateConversion;

public class OriginalInertialFlowPartitioning extends BalancedPartitioningOfPlanarGraphs {
    private static final Logger logger = LoggerFactory.getLogger(InertialFlowPartitioning.class);

    private static final int RANDOM_DIRECTIONS_COUNT = 10;
    private static final long RANDOM_SEED = 42L;

    private final double PARAMETER_SOURCE, PARAMETER_SINK;
    private final Random random;

    public OriginalInertialFlowPartitioning() {
        this(0.25, RANDOM_SEED);
    }

    public OriginalInertialFlowPartitioning(double parameter) {
        this(parameter, RANDOM_SEED);
    }

    public OriginalInertialFlowPartitioning(double parameter, long seed) {
        this.PARAMETER_SOURCE = parameter;
        this.PARAMETER_SINK = parameter;
        this.random = new Random(seed);
    }

    private static class Vector2D {
        Point secondPoint;
        boolean isVertical;
        double k;

        public Vector2D(Point secondPoint) {
            this.secondPoint = secondPoint;
            isVertical = secondPoint.x == 0;
            if (!isVertical) {
                k = (secondPoint.y) /
                        (secondPoint.x);
            }
        }

        public Point projectPoint(Point point) {
            double xProjection, yProjection;
            if (!isVertical) {
                xProjection = (point.x + k * point.y) / (1 + k * k);
                yProjection = k * xProjection;
            } else {
                xProjection = secondPoint.x;
                yProjection = point.y;
            }
            return new Point(xProjection, yProjection);
        }

    }

    private List<Vector2D> generateRandomLines() {
        List<Vector2D> randomLines = new ArrayList<>();
        for (int i = 0; i < RANDOM_DIRECTIONS_COUNT; i++) {
            double angle = random.nextDouble() * Math.PI;
            randomLines.add(new Vector2D(new Point(Math.cos(angle), Math.sin(angle))));
        }
        return randomLines;
    }

    @Override
    public void balancedPartitionAlgorithm(Graph<Vertex> simpleGraph, 
										   Graph<VertexOfDualGraph> graph, 
										   int maxSumVerticesWeight,
                                           CoordinateConversion coordinateConversion) {

        Stack<Graph<VertexOfDualGraph>> stack = new Stack<>();
        graph = graph.getLargestConnectedComponent();
        this.graph = graph;
        long startTime = System.currentTimeMillis();

        stack.push(graph);

        while (!stack.isEmpty()) {
            Graph<VertexOfDualGraph> currentGraph = stack.pop().makeUndirectedGraph();

            List<VertexOfDualGraph> vertices = new ArrayList<>(currentGraph.verticesArray());
            if (currentGraph.verticesWeight() < maxSumVerticesWeight) {
                partition.add(new HashSet<>(currentGraph.verticesArray()));
                continue;
            }

            long maxIndex = vertices.stream().max(Comparator.comparingLong(VertexOfDualGraph::getName)).get().getName();
            VertexOfDualGraph source = new VertexOfDualGraph(maxIndex + 1);
            VertexOfDualGraph sink = new VertexOfDualGraph(maxIndex + 2);

            long time1 = System.currentTimeMillis();

            FlowResult bestFlowResult = null;
            double bestCutSize = Double.MAX_VALUE;

            for (Vector2D line : generateRandomLines()) {
                List<VertexOfDualGraph> sortedVertices = new ArrayList<>(vertices);
                sortedVertices.sort(Comparator.comparing(v -> {
                    Point projected = line.projectPoint(v);
                    return line.isVertical ? projected.y : projected.x;
                }));

                double totalWeight = sortedVertices.stream().mapToDouble(VertexOfDualGraph::getWeight).sum();
                double targetWeightSource = PARAMETER_SOURCE * totalWeight;
                double targetWeightSink   = PARAMETER_SINK   * totalWeight;

                int maxSourceCount = Math.max(1, sortedVertices.size() / 2);

                HashSet<VertexOfDualGraph> sourceSet = new HashSet<>();
                double sourceWeight = 0;
                for (VertexOfDualGraph v : sortedVertices) {
                    if (sourceWeight >= targetWeightSource || sourceSet.size() >= maxSourceCount) break;
                    sourceSet.add(v);
                    sourceWeight += v.getWeight();
                }
                if (sourceSet.isEmpty()) {
                    sourceSet.add(sortedVertices.get(0));
                }

                HashSet<VertexOfDualGraph> sinkSet = new HashSet<>();
                double sinkWeight = 0;
                for (int i = sortedVertices.size() - 1; i >= 0; i--) {
                    VertexOfDualGraph v = sortedVertices.get(i);
                    if (sinkWeight >= targetWeightSink) break;
                    if (!sourceSet.contains(v)) {
                        sinkSet.add(v);
                        sinkWeight += v.getWeight();
                    }
                }

                if (sinkSet.isEmpty()) {
                    for (int i = sortedVertices.size() - 1; i >= 0; i--) {
                        VertexOfDualGraph v = sortedVertices.get(i);
                        if (!sourceSet.contains(v)) {
                            sinkSet.add(v);
                            break;
                        }
                    }
                }

                Graph<VertexOfDualGraph> copyGraph = createGraphWithSourceSink(currentGraph, sourceSet, source, sinkSet, sink);
                MaxFlow maxFlow = new MaxFlowDinic(copyGraph, source, sink);
                FlowResult flowResult = maxFlow.findFlow();
                logger.debug("Line {}: flow = {}", line.secondPoint, flowResult.flowSize());

                if (flowResult.flowSize() < bestCutSize) {
                    bestCutSize = flowResult.flowSize();
                    bestFlowResult = flowResult;
                }
            }

            long time2 = System.currentTimeMillis();
            logger.info("Time for source/sink selection and flow: {} seconds", (time2 - time1) / 1000.0);

            logger.debug("Best cut size: {}", bestCutSize);
            List<Graph<VertexOfDualGraph>> subpartition = partitionGraph(bestFlowResult);

            for (Graph<VertexOfDualGraph> subgraph : subpartition) {
                if (!subgraph.isConnected()) {
                    logger.warn("Subgraph is not connected");
                }
            }
            long time3 = System.currentTimeMillis();
            logger.info("Time for partitioning graph: {} seconds", (time3 - time2) / 1000.0);
            logger.info("SUBPARTITION SIZE: {}", subpartition.size());
            logger.info("Subgraph 0 vertices: {}, weight: {}", subpartition.get(0).verticesNumber(), subpartition.get(0).verticesWeight());
            logger.info("Subgraph 1 vertices: {}, weight: {}", subpartition.get(1).verticesNumber(), subpartition.get(1).verticesWeight());
            logger.info("Original graph vertices: {}, weight: {}\n\n", currentGraph.verticesNumber(), currentGraph.verticesWeight());

            for (Graph<VertexOfDualGraph> subgraph : subpartition) {
                stack.push(subgraph);
            }
        }
        long endTime = System.currentTimeMillis();
        logger.info("Total time in Inertial Flow: {} seconds", (endTime - startTime) / 1000.0);
    }

    private List<Graph<VertexOfDualGraph>> partitionGraph(FlowResult flow) {
        Graph<VertexOfDualGraph> graphWithFlow = flow.graphWithFlow();
        List<Graph<VertexOfDualGraph>> subpartition = new ArrayList<>();
        List<VertexOfDualGraph> vertices = graphWithFlow.verticesArray();
        Map<VertexOfDualGraph, Boolean> isConnectedWithSource = vertices.stream().collect(Collectors.toMap(Function.identity(), v -> Boolean.FALSE));

        markComponent(graphWithFlow, flow.source(), isConnectedWithSource);

        graphWithFlow.deleteVertex(flow.source());
        graphWithFlow.deleteVertex(flow.sink());

        for (int i = 0; i < 2; i++) {
            subpartition.add(graphWithFlow.createSubgraph(i == 0 ?
                    isConnectedWithSource.keySet().stream().filter(isConnectedWithSource::get).collect(Collectors.toSet()) :
                    isConnectedWithSource.keySet().stream().filter(v -> !isConnectedWithSource.get(v)).collect(Collectors.toSet())));
        }

        Assertions.assertEquals(graphWithFlow.verticesNumber(), subpartition.get(0).verticesNumber() + subpartition.get(1).verticesNumber());

        return subpartition;
    }


    void markComponent(
            Graph<VertexOfDualGraph> graph,
            VertexOfDualGraph source,
            Map<VertexOfDualGraph, Boolean> isConnectedWithSource
    ) {
        LinkedList<VertexOfDualGraph> queue = new LinkedList<>();
        queue.add(source);
        isConnectedWithSource.put(source, true);
        while (!queue.isEmpty()) {
        	VertexOfDualGraph vertex = queue.poll();
            for (Entry<VertexOfDualGraph, Edge> connectedVertex : graph.getEdges().get(vertex).entrySet()) {
                if (!isConnectedWithSource.get(connectedVertex.getKey()) &&
                        connectedVertex.getValue().flow < connectedVertex.getValue().getBandwidth()) {
                    isConnectedWithSource.put(connectedVertex.getKey(), true);
                    queue.add(connectedVertex.getKey());
                }
            }
        }

    }

    public static Graph<VertexOfDualGraph> createGraphWithSourceSink(
            Graph<VertexOfDualGraph> currentGraph,
            Set<VertexOfDualGraph> sourceSet,
            VertexOfDualGraph source,
            Set<VertexOfDualGraph> sinkSet,
            VertexOfDualGraph sink
    ) {
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
