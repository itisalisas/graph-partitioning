package partitioning.algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
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
import partitioning.maxflow.MaxFlowReif;
import readWrite.CoordinateConversion;

public class InertialFlowPartitioning extends BalancedPartitioningOfPlanarGraphs {
    private static final Logger logger = LoggerFactory.getLogger(InertialFlowPartitioning.class);

    private final double PARAMETER_SOURCE, PARAMETER_SINK;
    private final boolean USE_REIF;
    private final double LENGTH_PRIORITY;

    public InertialFlowPartitioning(boolean useReif) {
        this.PARAMETER_SOURCE = 0.5;
        this.PARAMETER_SINK = 0.5;
        this.USE_REIF = useReif;
        this.LENGTH_PRIORITY = 0.5;
    }

    public InertialFlowPartitioning(double parameter, boolean useReif) {
        this.PARAMETER_SOURCE = parameter;
        this.PARAMETER_SINK = parameter;
        this.USE_REIF = useReif;
        this.LENGTH_PRIORITY = 0.5;
    }

    public InertialFlowPartitioning(double parameter, boolean useReif, double lengthPriority) {
        this.PARAMETER_SOURCE = parameter;
        this.PARAMETER_SINK = parameter;
        this.USE_REIF = useReif;
        this.LENGTH_PRIORITY = lengthPriority;
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

    List<Vector2D> lines = Arrays.asList(
            new Vector2D(new Point(0, 1)),
            new Vector2D(new Point(1, 0)),
            new Vector2D(new Point(1, 1)),
            new Vector2D(new Point(1, -1)),
            new Vector2D(new Point(1, 2)),
            new Vector2D(new Point(2, 1)),
            new Vector2D(new Point(-1, 2)),
            new Vector2D(new Point(2, -1))
    );

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

            // Compute stretch for each line and sort descending
            List<Map.Entry<Vector2D, Double>> linesByStretch = new ArrayList<>();
            for (Vector2D line : lines) {
                vertices.sort(Comparator.comparing(v -> {
                    Point projected = line.projectPoint(v);
                    return line.isVertical ? projected.y : projected.x;
                }));
                double minP = line.isVertical ? line.projectPoint(vertices.get(0)).y : line.projectPoint(vertices.get(0)).x;
                double maxP = line.isVertical ? line.projectPoint(vertices.get(vertices.size() - 1)).y : line.projectPoint(vertices.get(vertices.size() - 1)).x;
                linesByStretch.add(Map.entry(line, maxP - minP));
            }
            linesByStretch.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            List<VertexOfDualGraph> boundaryCandidates =
                    getBoundaryCandidatesBySingleFaceEdges(vertices);

            // Pick first line where sourceInit != sinkInit
            Vector2D chosenLine = linesByStretch.get(0).getKey();
            VertexOfDualGraph sourceInitVertex = null;
            VertexOfDualGraph sinkInitVertex = null;
            for (Map.Entry<Vector2D, Double> entry : linesByStretch) {
                Vector2D line = entry.getKey();
                VertexOfDualGraph src = selectExtremumVertex(boundaryCandidates, Set.of(), line, true).orElse(null);
                if (src == null) continue;
                VertexOfDualGraph snk = selectExtremumVertex(boundaryCandidates, Set.of(src), line, false).orElse(null);
                if (snk != null && !snk.equals(src)) {
                    chosenLine = line;
                    sourceInitVertex = src;
                    sinkInitVertex = snk;
                    logger.debug("chosen line stretch={}: source={} sink={}", entry.getValue(), src.getName(), snk.getName());
                    break;
                }
                logger.debug("line stretch={}: source==sink or sink null, trying next", entry.getValue());
            }

            Vector2D finalChosenLine = chosenLine;
            vertices.sort(Comparator.comparing(v -> {
                Point projected = finalChosenLine.projectPoint(v);
                return finalChosenLine.isVertical ? projected.y : projected.x;
            }));

            double totalWeight = vertices.stream().mapToDouble(VertexOfDualGraph::getWeight).sum();
            double targetWeightSource = PARAMETER_SOURCE * totalWeight;
            double targetWeightSink = PARAMETER_SINK * totalWeight;

            long maxIndex = vertices.stream().max(Comparator.comparingLong(VertexOfDualGraph::getName)).get().getName();

            long time1 = System.currentTimeMillis();

            VertexOfDualGraph source = new VertexOfDualGraph(maxIndex + 1);
            VertexOfDualGraph sink = new VertexOfDualGraph(maxIndex + 2);

            int sourceInitIndex = vertices.indexOf(sourceInitVertex);
            int sinkInitIndex = vertices.indexOf(sinkInitVertex);

            HashSet<VertexOfDualGraph> sourceSet = selectVerticesForSet(
                    vertices, sourceInitIndex, targetWeightSource, Set.of(sinkInitVertex), currentGraph);
            logger.debug("source init={}, index={}, set size={}, weight={}", sourceInitVertex.getName(),
                    sourceInitIndex, sourceSet.size(), sourceSet.stream().mapToDouble(VertexOfDualGraph::getWeight).sum());

            HashSet<VertexOfDualGraph> sinkSet = selectVerticesForSet(
                    vertices, sinkInitIndex, targetWeightSink, sourceSet, currentGraph);
            logger.debug("sink init={}, index={}, set size={}, weight={}", sinkInitVertex.getName(),
                    sinkInitIndex, sinkSet.size(), sinkSet.stream().mapToDouble(VertexOfDualGraph::getWeight).sum());

            // Добираем вершины, достижимые только из одного множества
            expandSetsWithUnreachableRegions(currentGraph, sourceSet, sinkSet);

            long time2 = System.currentTimeMillis();
            logger.info("Time for selecting source and sink: {} seconds", (time2 - time1) / 1000.0);

            logger.debug("sourceSet size = {}, sinkSet size = {}", sourceSet.size(), sinkSet.size());
            Graph<VertexOfDualGraph> copyGraph = createGraphWithSourceSink(currentGraph, sourceSet, source, sinkSet, sink);

            long time3 = System.currentTimeMillis();
            logger.info("Time for creating graph with source and sink: {} seconds", (time3 - time2) / 1000.0);

            Assertions.assertEquals(currentGraph.verticesNumber() + 2, copyGraph.verticesNumber());

            MaxFlow maxFlow;
            if (USE_REIF) {
                maxFlow = new MaxFlowReif(simpleGraph, copyGraph, source, sink, coordinateConversion, maxSumVerticesWeight, LENGTH_PRIORITY);
            } else {
                maxFlow = new MaxFlowDinic(copyGraph, source, sink);
            }
            FlowResult flowResult = maxFlow.findFlow();
            logger.debug("Flow size: {}", flowResult.flowSize());
            long time4 = System.currentTimeMillis();
            logger.info("Time for finding flow: {} seconds", (time4 - time3) / 1000.0);

            List<Graph<VertexOfDualGraph>> subpartition;
            if (USE_REIF) {
                subpartition = partitionGraphReif(flowResult);
            } else {
                subpartition = partitionGraph(flowResult);
            }
            for (Graph<VertexOfDualGraph> subgraph : subpartition) {
                if (!subgraph.isConnected()) {
                    logger.warn("Subgraph is not connected");
                    CoordinateConversion cc = new CoordinateConversion();
                    for (var s : subgraph.splitForConnectedComponents()) {
                        logger.warn("Part: {}", s.stream().map(Vertex::getName).collect(Collectors.toList()));
                        if (s.size() == 1) {
                            var vertex = s.iterator().next();
                            logger.warn("Vertex: {} ({} {}), neighbors: {}", vertex.name, cc.fromEuclidean(vertex).y, cc.fromEuclidean(vertex).x, subgraph.getEdges().get(vertex).keySet().stream().map(Vertex::getName).collect(Collectors.toList()));
                        }
                    }
                    // TODO - странный путь, когда станет понятно почему, пролемы быть не должно
                }
            }
            long time5 = System.currentTimeMillis();
            logger.info("Time for partitioning graph: {} seconds", (time5 - time4) / 1000.0);
            logger.debug("SUBPARTITION SIZE: {}", subpartition.size());
            logger.debug("Subgraph 0 vertices: {}, weight: {}", subpartition.get(0).verticesNumber(), subpartition.get(0).verticesWeight());
            logger.debug("Subgraph 1 vertices: {}, weight: {}", subpartition.get(1).verticesNumber(), subpartition.get(1).verticesWeight());
            logger.debug("Original graph vertices: {}, weight: {}\n\n", currentGraph.verticesNumber(), currentGraph.verticesWeight());

            for (Graph<VertexOfDualGraph> subgraph : subpartition) {
                stack.push(subgraph);
            }
        }
        long endTime = System.currentTimeMillis();
        logger.info("Total time in Inertial Flow: {} seconds", (endTime - startTime) / 1000.0);
    }

    public HashSet<VertexOfDualGraph> selectVerticesForSet(
            List<VertexOfDualGraph> vertices,
            int startIndex,
            double targetWeight,
            Set<VertexOfDualGraph> sourceSet,
            Graph<VertexOfDualGraph> currentGraph
    ) {
        HashSet<VertexOfDualGraph> vertexSet = new HashSet<>();
        double currentWeight = 0;
        Queue<VertexOfDualGraph> queue = new LinkedList<>();
        VertexOfDualGraph startVertex = vertices.get(startIndex);
        queue.add(startVertex);

        while (!queue.isEmpty() && currentWeight < targetWeight) {
        	VertexOfDualGraph current = queue.poll();
            if (!vertexSet.contains(current) && !sourceSet.contains(current) && Math.abs(vertices.indexOf(current) - startIndex) < vertices.size() / 2) {
                vertexSet.add(current);
                currentWeight += current.getWeight();
                for (VertexOfDualGraph neighbor : currentGraph.getEdges().get(current).keySet()) {
                    if (!vertexSet.contains(neighbor) && !sourceSet.contains(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        return vertexSet;
    }

    /**
     * Расширяет sourceSet и sinkSet, добавляя вершины, достижимые только из одного множества.
     */
    private void expandSetsWithUnreachableRegions(
            Graph<VertexOfDualGraph> graph,
            HashSet<VertexOfDualGraph> sourceSet,
            HashSet<VertexOfDualGraph> sinkSet) {

        // Находим вершины, достижимые только из sourceSet
        Set<VertexOfDualGraph> reachableFromSource = findReachableVertices(graph, sourceSet, sinkSet);
        Set<VertexOfDualGraph> onlyFromSource = new HashSet<>(reachableFromSource);
        onlyFromSource.removeAll(sourceSet);

        // Находим вершины, достижимые только из sinkSet
        Set<VertexOfDualGraph> reachableFromSink = findReachableVertices(graph, sinkSet, sourceSet);
        Set<VertexOfDualGraph> onlyFromSink = new HashSet<>(reachableFromSink);
        onlyFromSink.removeAll(sinkSet);

        // Исключаем вершины, достижимые из обоих множеств
        onlyFromSource.removeAll(reachableFromSink);
        onlyFromSink.removeAll(reachableFromSource);

        if (!onlyFromSource.isEmpty() || !onlyFromSink.isEmpty()) {
            logger.debug("Expanding sets: adding {} vertices only reachable from source, {} only from sink",
                    onlyFromSource.size(), onlyFromSink.size());
        }

        sourceSet.addAll(onlyFromSource);
        sinkSet.addAll(onlyFromSink);
    }

    /**
     * Находит все вершины, достижимые из startSet, не проходя через blockedSet
     */
    private Set<VertexOfDualGraph> findReachableVertices(
            Graph<VertexOfDualGraph> graph,
            Set<VertexOfDualGraph> startSet,
            Set<VertexOfDualGraph> blockedSet) {

        Set<VertexOfDualGraph> reachable = new HashSet<>(startSet);
        Queue<VertexOfDualGraph> queue = new LinkedList<>(startSet);

        while (!queue.isEmpty()) {
            VertexOfDualGraph current = queue.poll();

            if (!graph.getEdges().containsKey(current)) {
                continue;
            }

            for (VertexOfDualGraph neighbor : graph.getEdges().get(current).keySet()) {
                if (blockedSet.contains(neighbor)) {
                    continue;
                }

                if (!reachable.contains(neighbor)) {
                    reachable.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return reachable;
    }

    /**
     * Выбирает вершину с экстремальной проекцией на линию (самую левую или правую)
     * @param vertices список вершин для выбора
     * @param excludeSet множество вершин, которые нужно исключить
     * @param line линия для проекции
     * @param selectMin true - выбрать минимум (самую левую), false - максимум (самую правую)
     * @return Optional с выбранной вершиной
     */
    private Optional<VertexOfDualGraph> selectExtremumVertex(
            List<VertexOfDualGraph> vertices,
            Set<VertexOfDualGraph> excludeSet,
            Vector2D line,
            boolean selectMin) {

        VertexOfDualGraph selectedVertex = null;
        double extremumProjection = selectMin ? Double.MAX_VALUE : -Double.MAX_VALUE;

        for (VertexOfDualGraph vertex : vertices) {
            // Пропускаем вершины из excludeSet
            if (excludeSet.contains(vertex)) {
                continue;
            }

            double projValue = calculateVertexProjection(vertex, line, selectMin);

            // Проверяем, является ли текущая проекция более экстремальной
            boolean isMoreExtreme = selectMin 
                    ? (projValue < extremumProjection) 
                    : (projValue > extremumProjection);

            if (isMoreExtreme) {
                extremumProjection = projValue;
                selectedVertex = vertex;
            }
        }

        return Optional.ofNullable(selectedVertex);
    }

    /**
     * Вычисляет экстремальную проекцию вершины на линию.
     * Если у вершины есть грани, берёт минимальную (для source) или максимальную (для sink) 
     * проекцию среди всех вершин грани.
     */
    private double calculateVertexProjection(VertexOfDualGraph vertex, Vector2D line, boolean selectMin) {
        ArrayList<Vertex> faceVertices = vertex.getVerticesOfFace();

        if (faceVertices == null || faceVertices.isEmpty()) {
            return getProjectionValue(vertex, line);
        }

        // Для вершин с гранями берём экстремальную проекцию
        double extremumProjection = selectMin ? Double.MAX_VALUE : -Double.MAX_VALUE;
        for (Vertex v : faceVertices) {
            double projValue = getProjectionValue(v, line);
            
            if (selectMin) {
                extremumProjection = Math.min(extremumProjection, projValue);
            } else {
                extremumProjection = Math.max(extremumProjection, projValue);
            }
        }
        return extremumProjection;
    }

    /**
     * Вычисляет значение проекции точки на линию
     */
    private double getProjectionValue(Point point, Vector2D line) {
        Point projected = line.projectPoint(point);
        return line.isVertical ? projected.y : projected.x;
    }

    private List<VertexOfDualGraph> getBoundaryCandidatesBySingleFaceEdges(
            List<VertexOfDualGraph> vertices
    ) {
        Map<Map.Entry<Vertex, Vertex>, Integer> edgeFaceCount = countInternalFaceIncidences(vertices);

        List<VertexOfDualGraph> result = new ArrayList<>();

        for (VertexOfDualGraph face : vertices) {
            boolean hasBoundaryEdge = false;

            for (Map.Entry<Vertex, Vertex> edge : getFaceEdges(face)) {
                if (edgeFaceCount.getOrDefault(edge, 0) == 1) {
                    hasBoundaryEdge = true;
                    break;
                }
            }

            if (hasBoundaryEdge) {
                result.add(face);
            }
        }

        return result;
    }

    private Map<Map.Entry<Vertex, Vertex>, Integer> countInternalFaceIncidences(
            Collection<VertexOfDualGraph> dualVertices
    ) {
        Map<Map.Entry<Vertex, Vertex>, Integer> edgeFaceCount = new HashMap<>();

        for (VertexOfDualGraph face : dualVertices) {
            for (Map.Entry<Vertex, Vertex> edge : getFaceEdges(face)) {
                edgeFaceCount.merge(edge, 1, Integer::sum);
            }
        }

        return edgeFaceCount;
    }

    private List<Map.Entry<Vertex, Vertex>> getFaceEdges(VertexOfDualGraph face) {
        ArrayList<Vertex> vs = face.getVerticesOfFace();

        if (vs == null || vs.size() < 2) {
            return List.of();
        }

        List<Map.Entry<Vertex, Vertex>> edges = new ArrayList<>();

        for (int i = 0; i < vs.size(); i++) {
            Vertex a = vs.get(i);
            Vertex b = vs.get((i + 1) % vs.size());

            if (!a.equals(b)) {
                edges.add(Map.entry(a, b));
                edges.add(Map.entry(b, a));
            }
        }

        return edges;
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

    private List<Graph<VertexOfDualGraph>> partitionGraphReif(FlowResult flow) {
        Graph<VertexOfDualGraph> graph = flow.graphWithFlow().clone();
        
        VertexOfDualGraph source = flow.source();
        VertexOfDualGraph sink = flow.sink();
        
        List<VertexOfDualGraph> allVertices = new ArrayList<>(graph.verticesArray());

        for (VertexOfDualGraph v : allVertices) {
            if (graph.getEdges().get(v) == null) continue;
            
            List<VertexOfDualGraph> neighbors = new ArrayList<>(graph.getEdges().get(v).keySet());
            for (VertexOfDualGraph neighbor : neighbors) {
                Edge edge = graph.getEdges().get(v).get(neighbor);
                if (edge.flow >= edge.getBandwidth()) {
                    graph.deleteEdge(v, neighbor);
                }
            }
        }

        graph.deleteVertex(source);
        graph.deleteVertex(sink);
        
        List<Set<VertexOfDualGraph>> components = graph.splitForConnectedComponents();

        if (components.size() > 2) {
            logger.warn("Total components: {}", components.size());
        }
        for (int i = 0; i < components.size(); i++) {
            Set<VertexOfDualGraph> component = components.get(i);

            if (i > 1) {
                logger.warn("Component {} vertices: {}", i + 1,
                        component.stream().map(v -> v.name).limit(20).toArray());
            }
        }

        List<Graph<VertexOfDualGraph>> subpartition = new ArrayList<>();
        
        if (components.isEmpty()) {
            logger.warn("No components found, returning empty graphs");
            subpartition.add(new Graph<>());
            subpartition.add(new Graph<>());
        } else if (components.size() == 1) {
            logger.warn("Only one component, graph not separated");
            subpartition.add(flow.graphWithFlow().createSubgraph(components.get(0)));
            subpartition.add(new Graph<>());
        } else {
            components.sort((a, b) -> Integer.compare(b.size(), a.size()));

            subpartition.add(flow.graphWithFlow().createSubgraph(components.get(0)));
            subpartition.add(flow.graphWithFlow().createSubgraph(components.get(1)));

            for (int i = 2; i < components.size(); i++) {
                Graph<VertexOfDualGraph> smallComponent = flow.graphWithFlow().createSubgraph(components.get(i));
                for (VertexOfDualGraph v : smallComponent.verticesArray()) {
                    components.get(0).add(v);
                }
            }
            
            subpartition.set(0, flow.graphWithFlow().createSubgraph(components.get(0)));
        }
        
        return subpartition;
    }
}