package partitioning.maxflow;

import java.util.*;
import java.util.stream.Collectors;

import graph.*;
import jakarta.validation.constraints.NotNull;
import partitioning.entities.SPTWithRegionWeights;
import partitioning.maxflow.shortestpathtree.ShortestPathTreeSearcher;
import partitioning.entities.FlowResult;
import partitioning.entities.NeighborSplit;
import partitioning.entities.DijkstraResult;
import readWrite.CoordinateConversion;
import readWrite.FlowWriter;

import static partitioning.maxflow.Dijkstra.dijkstraSingleSource;
import static partitioning.splitting.VertexSplitter.preprocessNeighborSplits;
import static partitioning.splitting.VertexSplitter.splitVertex;
import static partitioning.maxflow.Dijkstra.dijkstraMultiSource;

public class MaxFlowReif implements MaxFlow {
    Graph<Vertex> initGraph;
    Graph<VertexOfDualGraph> dualGraph;
    VertexOfDualGraph source;
    VertexOfDualGraph sink;
    double flow;
    CoordinateConversion conversion;
    HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph;

    private record PathCandidate(
            Vertex splitVertex1,
            Vertex splitVertex2,
            DijkstraResult path1ToBoundary,
            DijkstraResult path2ToBoundary,
            List<Vertex> combinedPath,
            List<Vertex> pathInOriginalGraph,
            double totalDistance
    ) {}

    public MaxFlowReif(Graph<Vertex> initGraph,
                       Graph<VertexOfDualGraph> dualGraph,
                       VertexOfDualGraph source,
                       VertexOfDualGraph sink,
                       HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph
    ) {
        this.initGraph = initGraph;
        this.dualGraph = dualGraph;
        this.source = source;
        this.sink = sink;
        this.conversion = new CoordinateConversion();
        this.comparisonForDualGraph = comparisonForDualGraph;
    }

    @Override
    public FlowResult findFlow() {
        // Подготовка данных
        HashSet<VertexOfDualGraph> sourceNeighbors = collectNeighbors(source);
        HashSet<VertexOfDualGraph> sinkNeighbors = collectNeighbors(sink);

        BoundariesData boundaries = computeBoundaries(
                sourceNeighbors, sinkNeighbors, comparisonForDualGraph
        );

        if (boundaries.externalBoundary().isEmpty()) {
            System.out.println("ERROR: External boundary is empty!");
            return new FlowResult(0, dualGraph, source, sink);
        }

        // Создание модифицированного графа
        Graph<Vertex> modifiedGraph = createModifiedGraph(
                boundaries, sourceNeighbors, sinkNeighbors
        );

        // Поиск кратчайшего пути
        Optional<DijkstraResult> shortestPathResultOpt = dijkstraMultiSource(
                modifiedGraph, boundaries.sourceBoundary(), boundaries.sinkBoundary()
        );

        if (shortestPathResultOpt.isEmpty()) {
            return handleNoShortestPath(boundaries, sourceNeighbors,
                                        sinkNeighbors, modifiedGraph);
        }

        DijkstraResult shortestPathResult = shortestPathResultOpt.get();
        if (shortestPathResult.path().isEmpty()) {
            return handleNoShortestPath(boundaries, sourceNeighbors,
                                        sinkNeighbors, modifiedGraph);
        }

        System.out.println("Found shortest path with " +
                                   shortestPathResult.path().size() +
                                   " vertices, distance: " + shortestPathResult.distance());

        // Препроцессинг разделения вершин
        Map<Long, NeighborSplit> neighborSplits = preprocessVertexSplits(
                modifiedGraph, shortestPathResult.path(), boundaries,
                sourceNeighbors, sinkNeighbors
        );

        // Разделение вершин пути
        // TODO покрыть тестами
        SplitVerticesData splitData = splitPathVertices(
                modifiedGraph, shortestPathResult.path(), neighborSplits
        );

        // Поиск лучшего пути через split-вершины
        IntersectionsData intersections = findAllIntersections(boundaries);
        System.out.println("Found " + intersections.sourceIntersections.size() +
                " source intersections and " + intersections.sinkIntersections.size() +
                " sink intersections on external boundary");
        Optional<PathCandidate> bestCandidate = findBestPathThroughSplits(
                splitData, modifiedGraph, boundaries.externalBoundary(),
                intersections, dualGraph
        );

        if (bestCandidate.isEmpty()) {
            return handleNoBestPath(boundaries, shortestPathResult.path(),
                                    neighborSplits, sourceNeighbors,
                                    sinkNeighbors, modifiedGraph);
        }

        // Заполнение потока
        PathCandidate best = bestCandidate.get();
        System.out.println("WEIGHTS:" + best.path1ToBoundary.weights().stream().toList());
        flow = fillFlowInDualGraph(best.pathInOriginalGraph(), dualGraph);

        // Визуализация
        dumpVisualization(boundaries, shortestPathResult.path(),
                          best.pathInOriginalGraph(), neighborSplits,
                          sourceNeighbors, sinkNeighbors, modifiedGraph,
                          best, splitData.splitToOriginalMap());

        return new FlowResult(flow, dualGraph, source, sink, best.pathInOriginalGraph);
                //FlowResult(flow, dualGraph, source, sink);
    }

    private record BoundariesData(
            List<Vertex> sourceBoundary,
            List<Vertex> sinkBoundary,
            List<Vertex> externalBoundary
    ) {}

    private record IntersectionsData(
            List<Vertex> sourceIntersections,
            List<Vertex> sinkIntersections
    ) {}

    private record SplitVerticesData(
            List<Map.Entry<Vertex, Vertex>> splitVertices,
            Map<Vertex, Vertex> splitToOriginalMap
    ) {}

    /**
     * Вычисляет все границы
     */
    private BoundariesData computeBoundaries(
            HashSet<VertexOfDualGraph> sourceNeighbors,
            HashSet<VertexOfDualGraph> sinkNeighbors,
            HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph) {

        List<Vertex> sourceBoundary = BoundSearcher.findBound(
                initGraph, sourceNeighbors, comparisonForDualGraph
        );
        List<Vertex> sinkBoundary = BoundSearcher.findBound(
                initGraph, sinkNeighbors, comparisonForDualGraph
        );

        HashSet<VertexOfDualGraph> allDualVerticesSet = new HashSet<>(
                dualGraph.verticesArray()
        );
        allDualVerticesSet.remove(source);
        allDualVerticesSet.remove(sink);

        List<Vertex> externalBoundary = BoundSearcher.findBound(
                initGraph, allDualVerticesSet, comparisonForDualGraph
        );

        return new BoundariesData(sourceBoundary, sinkBoundary, externalBoundary);
    }

    /**
     * Создает модифицированный граф
     */
    private Graph<Vertex> createModifiedGraph(
            BoundariesData boundaries,
            HashSet<VertexOfDualGraph> sourceNeighbors,
            HashSet<VertexOfDualGraph> sinkNeighbors) {

        Graph<Vertex> modifiedGraph = new Graph<>();
        createModifiedSubgraph(
                modifiedGraph,
                boundaries.sourceBoundary(),
                boundaries.sinkBoundary(),
                sourceNeighbors,
                sinkNeighbors,
                boundaries.externalBoundary(),
                dualGraph
        );
        return modifiedGraph;
    }

    /**
     * Находит все углы на границах
     */
    private IntersectionsData findAllIntersections(BoundariesData boundaries) {
        List<Vertex> sourceIntersections = findIntersections(
                boundaries.externalBoundary(),
                boundaries.sourceBoundary()
        );
        List<Vertex> sinkIntersections = findIntersections(
                boundaries.externalBoundary(),
                boundaries.sinkBoundary()
        );
        return new IntersectionsData(sourceIntersections, sinkIntersections);
    }

    /**
     * Препроцессинг разделений вершин с обработкой ошибок
     */
    private Map<Long, NeighborSplit> preprocessVertexSplits(
            Graph<Vertex> modifiedGraph,
            List<Vertex> path,
            BoundariesData boundaries,
            HashSet<VertexOfDualGraph> sourceNeighbors,
            HashSet<VertexOfDualGraph> sinkNeighbors) {

        try {
            return preprocessNeighborSplits(
                    modifiedGraph, path,
                    boundaries.sourceBoundary(),
                    boundaries.sinkBoundary(),
                    boundaries.externalBoundary()
            );
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("broken boundary")) {
                System.out.println("Broken boundary detected: " + e.getMessage());
                FlowWriter.dumpVisualizationData(
                        boundaries.externalBoundary(),
                        boundaries.sourceBoundary(),
                        boundaries.sinkBoundary(),
                        path, null, new HashMap<>(),
                        sourceNeighbors, sinkNeighbors, 0,
                        modifiedGraph, dualGraph, source, sink, conversion
                );
            }
            throw e;
        }
    }

    /**
     * Разделяет вершины пути
     */
    private SplitVerticesData splitPathVertices(
            Graph<Vertex> modifiedGraph,
            List<Vertex> path,
            Map<Long, NeighborSplit> neighborSplits) {

        Map<Vertex, Vertex> splitToOriginalMap = new HashMap<>();
        List<Map.Entry<Vertex, Vertex>> splitVertices = new ArrayList<>();

        for (Vertex pathVertex : path) {
            Map.Entry<Vertex, Vertex> splitted = splitVertex(
                    modifiedGraph, pathVertex, splitToOriginalMap, neighborSplits
            );
            splitVertices.add(splitted);
        }

        return new SplitVerticesData(splitVertices, splitToOriginalMap);
    }

    /**
     * Находит лучший путь через все split-вершины
     */
    private Optional<PathCandidate> findBestPathThroughSplits(
            SplitVerticesData splitData,
            Graph<Vertex> modifiedGraph,
            List<Vertex> externalBoundary,
            IntersectionsData intersections,
            Graph<VertexOfDualGraph> dualGraph) {

        double minPathLength = Double.MAX_VALUE;
        PathCandidate bestCandidate = null;
        // TODO бинпоиск по весу, баланс между длиной и весом
        // alpha * length + beta * |2p-1|

        for (Map.Entry<Vertex, Vertex> splitVertex : splitData.splitVertices()) {
            Optional<PathCandidate> candidate = evaluateSplitVertex(
                    splitVertex,
                    modifiedGraph,
                    externalBoundary,
                    dualGraph,
                    intersections.sourceIntersections(),
                    intersections.sinkIntersections(),
                    splitData.splitToOriginalMap()
            );

            if (candidate.isPresent() &&
                    candidate.get().totalDistance() < minPathLength) {
                minPathLength = candidate.get().totalDistance();
                bestCandidate = candidate.get();
            }
        }

        return Optional.ofNullable(bestCandidate);
    }

    /**
     * Оценивает одну split-вершину и возвращает кандидата на лучший путь
     */
    private Optional<PathCandidate> evaluateSplitVertex(
            Map.Entry<Vertex, Vertex> splitVertex,
            Graph<Vertex> modifiedGraph,
            List<Vertex> externalBoundary,
            Graph<VertexOfDualGraph> dualGraph,
            List<Vertex> sourceIntersections,
            List<Vertex> sinkIntersections,
            Map<Vertex, Vertex> splitToOriginalMap) {

        Vertex splitVertex1 = splitVertex.getKey();
        Vertex splitVertex2 = splitVertex.getValue();

        List<Vertex> targetSegment1 = extractBoundarySegment(
                externalBoundary, sourceIntersections, sinkIntersections, true);
        List<Vertex> targetSegment2 = extractBoundarySegment(
                externalBoundary, sourceIntersections, sinkIntersections, false);


        // Поиск путей от обеих split-вершин к границе
        Optional<DijkstraResult> path1ToBoundaryOpt = dijkstraSingleSourceWithRegionWeights(
                modifiedGraph, splitVertex1,
                targetSegment1, dualGraph, sourceIntersections, sinkIntersections, true
        );

        Optional<DijkstraResult> path2ToBoundaryOpt = dijkstraSingleSourceWithRegionWeights(
                modifiedGraph, splitVertex2,
                targetSegment2, dualGraph, sourceIntersections, sinkIntersections, false
        );

        if (path1ToBoundaryOpt.isEmpty() || path2ToBoundaryOpt.isEmpty()) {
            logMissingPath(splitVertex1, splitVertex2, splitToOriginalMap,
                           path1ToBoundaryOpt.isPresent(), path2ToBoundaryOpt.isPresent());
            return Optional.empty();
        }
        // TODO закэшировать порядок детей
        // TODO - новый класс который будет получать дерево

        DijkstraResult path1ToBoundary = path1ToBoundaryOpt.get();
        DijkstraResult path2ToBoundary = path2ToBoundaryOpt.get();

        double totalDistance = path1ToBoundary.distance() + path2ToBoundary.distance();

        List<Vertex> combinedPath = combinePaths(
                path1ToBoundary.path(),
                path2ToBoundary.path()
        );

        List<Vertex> pathInOriginalGraph = mapToOriginalGraph(
                combinedPath,
                splitToOriginalMap
        );

        return Optional.of(new PathCandidate(
                splitVertex1, splitVertex2,
                path1ToBoundary, path2ToBoundary,
                combinedPath, pathInOriginalGraph,
                totalDistance
        ));
    }

    /**
     * Извлекает сегмент external boundary между двумя углами
     * @param externalBoundary полная external boundary
     * @param sourceIntersections все source corners
     * @param sinkIntersections все sink corners
     * @param isFirstSide какую сторону нужно (true = верхняя, false = нижняя)
     * @return список вершин сегмента
     */
    private List<Vertex> extractBoundarySegment(
            List<Vertex> externalBoundary,
            List<Vertex> sourceIntersections,
            List<Vertex> sinkIntersections,
            boolean isFirstSide) {

        if (sourceIntersections.isEmpty() || sinkIntersections.isEmpty()) {
            System.out.println("WARNING: No intersections found, using full boundary");
            return externalBoundary;
        }

        // Создаем map позиций
        Map<Long, Integer> positionMap = new HashMap<>();
        for (int i = 0; i < externalBoundary.size(); i++) {
            positionMap.put(externalBoundary.get(i).getName(), i);
        }

        // Находим два ключевых угла (используем ту же логику что в ShortestPathTreeSearcher)
        Vertex sourceCorner = null;
        Vertex sinkCorner = null;
        int minDistance = externalBoundary.size();

        for (Vertex sCorner : sourceIntersections) {
            int sPos = positionMap.getOrDefault(sCorner.getName(), -1);
            if (sPos < 0) continue;

            for (Vertex tCorner : sinkIntersections) {
                int tPos = positionMap.getOrDefault(tCorner.getName(), -1);
                if (tPos < 0) continue;

                // Расстояние по часовой стрелке от source к sink
                int distance = (tPos - sPos + externalBoundary.size()) % externalBoundary.size();

                if (distance > 0 && distance < minDistance) {
                    minDistance = distance;
                    sourceCorner = sCorner;
                    sinkCorner = tCorner;
                }
            }
        }

        // Для другой стороны берем противоположную пару
        if (!isFirstSide && sourceCorner != null && sinkCorner != null) {
            // Меняем местами для противоположного сегмента
            Vertex temp = sourceCorner;
            sourceCorner = sinkCorner;
            sinkCorner = temp;
        }

        if (sourceCorner == null || sinkCorner == null) {
            System.out.println("WARNING: Could not find corner pair, using full boundary");
            return externalBoundary;
        }

        int startPos = positionMap.get(sourceCorner.getName());
        int endPos = positionMap.get(sinkCorner.getName());

        // Извлекаем сегмент от startPos до endPos (по кругу)
        List<Vertex> segment = new ArrayList<>();
        int pos = startPos;
        int iterations = 0;
        int maxIterations = externalBoundary.size() + 1;

        while (iterations < maxIterations) {
            segment.add(externalBoundary.get(pos));

            if (pos == endPos) {
                break;
            }

            pos = (pos + 1) % externalBoundary.size();
            iterations++;
        }

        System.out.println("Extracted boundary segment: " + segment.size() + " vertices " +
                "(from " + sourceCorner.getName() + " to " + sinkCorner.getName() +
                ", isFirstSide=" + isFirstSide + ")");

        return segment;
    }

    /**
     * Объединяет два пути (первый в обратном порядке + второй)
     */
    private List<Vertex> combinePaths(List<Vertex> path1, List<Vertex> path2) {
        List<Vertex> combined = new ArrayList<>();

        for (int i = path1.size() - 1; i >= 0; i--) {
            combined.add(path1.get(i));
        }
        for (int i = 1; i < path2.size(); i++) {
            combined.add(path2.get(i));
        }

        return combined;
    }

    /**
     * Преобразует путь split-вершин обратно в оригинальные вершины
     */
    private List<Vertex> mapToOriginalGraph(
            List<Vertex> path,
            Map<Vertex, Vertex> splitToOriginalMap) {

        List<Vertex> originalPath = new ArrayList<>();
        for (Vertex v : path) {
            originalPath.add(splitToOriginalMap.getOrDefault(v, v));
        }
        return originalPath;
    }

    /**
     * Логирование отсутствующего пути
     */
    private void logMissingPath(
            Vertex splitVertex1,
            Vertex splitVertex2,
            Map<Vertex, Vertex> splitToOriginalMap,
            boolean isPath1Found,
            boolean isPath2Found) {

        System.out.println("ERROR: Path to external boundary not found!");
        System.out.println("  Original vertex on shortest path: " +
                                   splitToOriginalMap.get(splitVertex1).getName());
        System.out.println("  Split vertex 1 ID: " + splitVertex1.getName());
        System.out.println("  Split vertex 2 ID: " + splitVertex2.getName());
        System.out.println("  Path1ToBoundary (left): " +
                                   (isPath1Found ? "FOUND" : "NULL"));
        System.out.println("  Path2ToBoundary (right): " +
                                   (isPath2Found ? "FOUND" : "NULL"));
    }

    /**
     * Обработка случая когда нет кратчайшего пути
     */
    private FlowResult handleNoShortestPath(
            BoundariesData boundaries,
            HashSet<VertexOfDualGraph> sourceNeighbors,
            HashSet<VertexOfDualGraph> sinkNeighbors,
            Graph<Vertex> modifiedGraph) {

        System.out.println("ERROR: No shortest path found between source and sink boundaries!");
        FlowWriter.dumpVisualizationData(
                boundaries.externalBoundary(),
                boundaries.sourceBoundary(),
                boundaries.sinkBoundary(),
                List.of(), List.of(), Map.of(),
                sourceNeighbors, sinkNeighbors, 0,
                modifiedGraph, dualGraph, source, sink, conversion
        );

        return new FlowResult(0, dualGraph, source, sink);
    }

    /**
     * Обработка случая когда не найден лучший путь
     */
    private FlowResult handleNoBestPath(
            BoundariesData boundaries,
            List<Vertex> shortestPath,
            Map<Long, NeighborSplit> neighborSplits,
            HashSet<VertexOfDualGraph> sourceNeighbors,
            HashSet<VertexOfDualGraph> sinkNeighbors,
            Graph<Vertex> modifiedGraph) {

        System.out.println("ERROR: No best path found to external boundary!");
        FlowWriter.dumpVisualizationData(
                boundaries.externalBoundary(),
                boundaries.sourceBoundary(),
                boundaries.sinkBoundary(),
                shortestPath, null, neighborSplits,
                sourceNeighbors, sinkNeighbors, 0,
                modifiedGraph, dualGraph, source, sink, conversion
        );
        return new FlowResult(0, dualGraph, source, sink);
    }

    /**
     * Вывод визуализации
     */
    private void dumpVisualization(
            BoundariesData boundaries,
            List<Vertex> shortestPath,
            List<Vertex> bestPath,
            Map<Long, NeighborSplit> neighborSplits,
            HashSet<VertexOfDualGraph> sourceNeighbors,
            HashSet<VertexOfDualGraph> sinkNeighbors,
            Graph<Vertex> modifiedGraph,
            PathCandidate best,
            Map<Vertex, Vertex> splitToOriginalMap) {

        FlowWriter.dumpVisualizationData(
                boundaries.externalBoundary(),
                boundaries.sourceBoundary(),
                boundaries.sinkBoundary(),
                shortestPath, bestPath, neighborSplits,
                sourceNeighbors, sinkNeighbors, flow,
                modifiedGraph, dualGraph, source, sink, conversion
        );

        FlowWriter.dumpSPTVisualizationData(
                best.path1ToBoundary(),
                best.path2ToBoundary(),
                best.splitVertex1(),
                best.splitVertex2(),
                splitToOriginalMap,
                sourceNeighbors, sinkNeighbors, flow, conversion
        );
    }

    private Optional<DijkstraResult> dijkstraSingleSourceWithRegionWeights(
            Graph<Vertex> graph,
            Vertex sourceVertex,
            List<Vertex> targetBoundary,
            @NotNull Graph<VertexOfDualGraph> dualGraph,
            List<Vertex> sourceIntersections,
            List<Vertex> sinkIntersections,
            boolean isFirstSide) {

        Optional<DijkstraResult> defaultResultOpt = dijkstraSingleSource(
                graph, sourceVertex, targetBoundary
        );

        if (defaultResultOpt.isEmpty()) {
            return Optional.empty();
        }

        DijkstraResult defaultResult = defaultResultOpt.get();

        SPTWithRegionWeights spt = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph, defaultResult.previous(), sourceVertex, targetBoundary,
                dualGraph, sourceIntersections, sinkIntersections, isFirstSide
        );

        return Optional.of(new DijkstraResult(
                defaultResult.path(),
                defaultResult.distance(),
                defaultResult.previous(),
                defaultResult.dijkstraDistances(),
                spt.boundaryLeaves(),
                spt.faces(),
                spt.regionWeights(),
                spt.distances(),
                spt.totalRegionWeight()
        ));
    }

    /**
     * Заполняет поток в dual графе вдоль пути
     */
    private double fillFlowInDualGraph(List<Vertex> path, Graph<VertexOfDualGraph> dualGraph) {
        if (path.size() < 2) {
            return 0.0;
        }

        double totalFlow = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            totalFlow += processDualEdge(path.get(i), path.get(i + 1), dualGraph);
        }

        return totalFlow;
    }

    /**
     * Обрабатывает одно ребро dual графа и возвращает поток через него
     */
    private double processDualEdge(Vertex v1, Vertex v2, Graph<VertexOfDualGraph> dualGraph) {
        Map<Vertex, HashMap<Vertex, VertexOfDualGraph>> map = dualGraph.edgeToDualVertexMap();
        VertexOfDualGraph face1 = map.get(v1).get(v2);
        VertexOfDualGraph face2 = map.get(v2).get(v1);

        if (face1 == null || face2 == null || dualGraph.getEdges().get(face1) == null) {
            return 0.0;
        }

        if (!dualGraph.getEdges().get(face1).containsKey(face2)) {
            return 0.0;
        }

        Edge dualEdge1 = dualGraph.getEdges().get(face1).get(face2);
        double bandwidth = dualEdge1.getBandwidth();
        dualEdge1.flow = bandwidth;

        // Обратное ребро
        if (dualGraph.getEdges().get(face2) != null &&
                dualGraph.getEdges().get(face2).containsKey(face1)) {
            Edge dualEdge2 = dualGraph.getEdges().get(face2).get(face1);
            dualEdge2.flow = dualEdge2.getBandwidth();
        }

        return bandwidth;
    }

    // === Вспомогательные методы ===

    private HashSet<VertexOfDualGraph> collectNeighbors(VertexOfDualGraph vertex) {
        HashSet<VertexOfDualGraph> neighbors = new HashSet<>();
        for (VertexOfDualGraph neighbor : dualGraph.getEdges().get(vertex).keySet()) {
            if (!neighbor.equals(sink) && !neighbor.equals(source)) {
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }

    // множественные пересечения с external boundary???
    private List<Vertex> findIntersections(List<Vertex> externalBoundary, List<Vertex> targetBoundary) {
        Set<Long> targetBoundaryNames = targetBoundary.stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());

        List<Vertex> intersections = new ArrayList<>();
        for (Vertex v : externalBoundary) {
            if (targetBoundaryNames.contains(v.getName())) {
                intersections.add(v);
            }
        }
        return intersections;
    }

    /**
     * Создает модифицированный подграф
     */
    private void createModifiedSubgraph(
            Graph<Vertex> modifiedGraph,
            List<Vertex> sourceBoundary,
            List<Vertex> sinkBoundary,
            HashSet<VertexOfDualGraph> sourceNeighbors,
            HashSet<VertexOfDualGraph> sinkNeighbors,
            List<Vertex> externalBoundary,
            Graph<VertexOfDualGraph> dualGraph) {

        Set<Vertex> allowedVertices = collectFaceVertices(dualGraph.verticesArray());
        Set<Vertex> sourceFaceVertices = collectFaceVertices(sourceNeighbors);
        Set<Vertex> sinkFaceVertices = collectFaceVertices(sinkNeighbors);

        Set<Vertex> sourceBoundarySet = new HashSet<>(sourceBoundary);
        Set<Vertex> sinkBoundarySet = new HashSet<>(sinkBoundary);
        Set<Vertex> externalBoundarySet = new HashSet<>(externalBoundary);

        // Добавляем границы
        modifiedGraph.addBoundEdges(sourceBoundary);
        modifiedGraph.addBoundEdges(sinkBoundary);
        modifiedGraph.addBoundEdges(externalBoundary);

        // Добавляем внутренние вершины
        for (Vertex v : initGraph.verticesArray()) {
            if (shouldAddVertexToModifiedGraph(v, allowedVertices,
                                               sourceFaceVertices, sinkFaceVertices,
                                               sourceBoundarySet, sinkBoundarySet, externalBoundarySet)) {
                modifiedGraph.addVertexInSubgraph(v, initGraph);
            }
        }
    }

    /**
     * Собирает все вершины из граней
     */
    private Set<Vertex> collectFaceVertices(Iterable<VertexOfDualGraph> faces) {
        Set<Vertex> vertices = new HashSet<>();
        for (VertexOfDualGraph face : faces) {
            if (face.getVerticesOfFace() != null) {
                vertices.addAll(face.getVerticesOfFace());
            }
        }
        return vertices;
    }

    /**
     * Проверяет нужно ли добавлять вершину в модифицированный граф
     */
    private boolean shouldAddVertexToModifiedGraph(
            Vertex v,
            Set<Vertex> allowedVertices,
            Set<Vertex> sourceFaceVertices,
            Set<Vertex> sinkFaceVertices,
            Set<Vertex> sourceBoundarySet,
            Set<Vertex> sinkBoundarySet,
            Set<Vertex> externalBoundarySet) {

        return v.getName() != 0 &&
                allowedVertices.contains(v) &&
                !sourceFaceVertices.contains(v) &&
                !sinkFaceVertices.contains(v) &&
                !sourceBoundarySet.contains(v) &&
                !sinkBoundarySet.contains(v) &&
                !externalBoundarySet.contains(v);
    }
}