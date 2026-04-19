package partitioning.algorithms;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graph.*;
import org.junit.jupiter.api.Assertions;
import partitioning.entities.FlowResult;
import partitioning.maxflow.MaxFlow;
import partitioning.maxflow.MaxFlowDinic;
import partitioning.maxflow.MaxFlowReif;

public class InertialFlowPartitioning extends BalancedPartitioningOfPlanarGraphs {
    private static final Logger logger = LoggerFactory.getLogger(InertialFlowPartitioning.class);

    private final double PARAMETER_SOURCE, PARAMETER_SINK;
    private final boolean USE_REIF;

    public InertialFlowPartitioning() {
        this.PARAMETER_SOURCE = 0.25;
        this.PARAMETER_SINK = 0.25;
        this.USE_REIF = true;
    }

    public InertialFlowPartitioning(double parameter, boolean useReif) {
        this.PARAMETER_SOURCE = parameter;
        this.PARAMETER_SINK = parameter;
        this.USE_REIF = useReif;
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
										   Map<Vertex, VertexOfDualGraph> comparisonForDualGraph,
										   Graph<VertexOfDualGraph> graph, 
										   int maxSumVerticesWeight) {

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

            Vector2D bestLine = lines.get(0);
            double maxStretch = -1;

            for (Vector2D line : lines) {
                vertices.sort(Comparator.comparing(v -> {
                    Point projected = line.projectPoint(v);
                    return line.isVertical ? projected.y : projected.x;
                }));

                double minProjection = line.isVertical
                        ? line.projectPoint(vertices.get(0)).y
                        : line.projectPoint(vertices.get(0)).x;

                double maxProjection = line.isVertical
                        ? line.projectPoint(vertices.get(vertices.size() - 1)).y
                        : line.projectPoint(vertices.get(vertices.size() - 1)).x;

                double stretch = maxProjection - minProjection;
                if (stretch > maxStretch) {
                    maxStretch = stretch;
                    bestLine = line;
                }
            }

            Vector2D finalBestLine = bestLine;
            vertices.sort(Comparator.comparing(v -> {
                Point projected = finalBestLine.projectPoint(v);
                return finalBestLine.isVertical ? projected.y : projected.x;
            }));


            double totalWeight = vertices.stream().mapToDouble(VertexOfDualGraph::getWeight).sum();
            double targetWeightSource = PARAMETER_SOURCE * totalWeight;
            double targetWeightSink = PARAMETER_SINK * totalWeight;

            long maxIndex = vertices.stream().max(Comparator.comparingLong(VertexOfDualGraph::getName)).get().getName();

            long time1 = System.currentTimeMillis();

            VertexOfDualGraph source = new VertexOfDualGraph(maxIndex + 1);
            VertexOfDualGraph sink = new VertexOfDualGraph(maxIndex + 2);
            List<VertexOfDualGraph> startVertices = selectSourceSink(bestLine, currentGraph);

            HashSet<VertexOfDualGraph> sourceSet = new HashSet<>();
            HashSet<VertexOfDualGraph> maxSourceSet = new HashSet<>();
            int index = 0;
            while (index < vertices.size() && sourceSet.stream().mapToDouble(VertexOfDualGraph::getWeight).sum() < targetWeightSource) {
                sourceSet = selectVerticesForSet(vertices, index, targetWeightSource, new HashSet<>(), currentGraph, startVertices.get(0));
                if (sourceSet.stream().mapToDouble(VertexOfDualGraph::getWeight).sum()
                        > maxSourceSet.stream().mapToDouble(VertexOfDualGraph::getWeight).sum()) {
                    maxSourceSet = new HashSet<>(sourceSet);
                }
                index++;
            }
            if (!maxSourceSet.isEmpty()) {
                sourceSet = new HashSet<>(maxSourceSet);
            }

            HashSet<VertexOfDualGraph> sinkSet = new HashSet<>();
            HashSet<VertexOfDualGraph> maxSinkSet = new HashSet<>();
            index = 1;
            while (index <= vertices.size() && sinkSet.stream().mapToDouble(Vertex::getWeight).sum() < targetWeightSink) {
                int currentIndex = vertices.size() - index;
                VertexOfDualGraph candidate = vertices.get(currentIndex);

                if (sourceSet.contains(candidate)) {
                    index++;
                    continue;
                }

                VertexOfDualGraph startVertex = null;
                if (startVertices.size() > 1) {
                    startVertex = startVertices.get(1);
                }
                logger.debug("graph size = {}, startVertices size = {}", currentGraph.verticesArray().size(), startVertices.size());
                sinkSet = selectVerticesForSet(vertices, currentIndex, targetWeightSink, sourceSet, currentGraph, startVertex);

                boolean isDisjoint = Collections.disjoint(sinkSet, sourceSet);
                if (isDisjoint && sinkSet.stream().mapToDouble(Vertex::getWeight).sum()
                        > maxSinkSet.stream().mapToDouble(Vertex::getWeight).sum()) {
                    maxSinkSet = new HashSet<>(sinkSet);
                }
                index++;
            }

            if (!maxSinkSet.isEmpty()) {
                sinkSet = new HashSet<>(maxSinkSet);
            } else {
                sourceSet = new HashSet<>();
                sinkSet = new HashSet<>();

                List<VertexOfDualGraph> nonLeafVertices = vertices.stream()
                        .filter(v -> currentGraph.getEdges().get(v).size() >= 2)
                        .toList();

                if (nonLeafVertices.size() >= 2) {
                    sourceSet.add(nonLeafVertices.get(0));
                    sinkSet.add(nonLeafVertices.get(1));
                } else if (nonLeafVertices.size() == 1) {
                    sourceSet.add(nonLeafVertices.get(0));
                    vertices.stream()
                            .filter(v -> !v.equals(nonLeafVertices.get(0)))
                            .findFirst()
                            .ifPresent(sinkSet::add);
                } else {
                    if (vertices.size() >= 2) {
                        sourceSet.add(vertices.get(0));
                        sinkSet.add(vertices.get(1));
                    } else {
                        throw new IllegalStateException("Graph contains less than 2 vertices");
                    }
                }
            }

            // Если sourceSet пустой, выбираем самую левую вершину (минимальная проекция)
            if (sourceSet.isEmpty()) {
                logger.warn("Source set is empty, selecting extremum vertex");
                selectExtremumVertex(vertices, sinkSet, bestLine, true)
                        .ifPresent(sourceSet::add);
                logger.warn("Found source extremum vertex {}", sourceSet.iterator().next().name);
            }
            
            // Если sinkSet пустой, выбираем самую правую вершину (максимальная проекция)
            if (sinkSet.isEmpty()) {
                logger.warn("Sink set is empty, selecting extremum vertex");
                selectExtremumVertex(vertices, sourceSet, bestLine, false)
                        .ifPresent(sinkSet::add);
                logger.warn("Found sink extremum vertex {}", sinkSet.iterator().next().name);
            }

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
                maxFlow = new MaxFlowReif(simpleGraph, copyGraph, source, sink, comparisonForDualGraph);
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
            Graph<VertexOfDualGraph> currentGraph,
            VertexOfDualGraph initVertex
    ) {
        HashSet<VertexOfDualGraph> vertexSet = new HashSet<>();
        double currentWeight = 0;
        Queue<VertexOfDualGraph> queue = new LinkedList<>();
        VertexOfDualGraph startVertex = initVertex != null ? initVertex : vertices.get(startIndex);
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
     * Это устраняет "мертвые зоны" между внешней границей и множествами.
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

    public List<VertexOfDualGraph> selectSourceSink(Vector2D line, Graph<VertexOfDualGraph> currentGraph) {
        List<VertexOfDualGraph> dualVertices = new ArrayList<>(currentGraph.verticesArray());

        if (dualVertices.size() < 2) {
            throw new RuntimeException("too few vertices in dual graph");
        }

        VertexOfDualGraph sourceVertex = null;
        VertexOfDualGraph sinkVertex = null;
        double minProjection = Double.MAX_VALUE;
        double maxProjection = -Double.MAX_VALUE;

        for (VertexOfDualGraph dualVertex : dualVertices) {
            double minProjValue = calculateVertexProjection(dualVertex, line, true);
            double maxProjValue = calculateVertexProjection(dualVertex, line, false);

            if (minProjValue < minProjection && !dualVertex.equals(sinkVertex)) {
                minProjection = minProjValue;
                sourceVertex = dualVertex;
            }
            
            if (maxProjValue > maxProjection && !dualVertex.equals(sourceVertex)) {
                maxProjection = maxProjValue;
                sinkVertex = dualVertex;
            }
        }

        List<VertexOfDualGraph> result = new ArrayList<>();
        if (sourceVertex != null) {
            result.add(sourceVertex);
        }
        if (sinkVertex != null && !sinkVertex.equals(sourceVertex)) {
            result.add(sinkVertex);
        }

        return result;
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