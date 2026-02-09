package partitioning.maxflow;

import java.util.*;
import java.util.stream.Collectors;

import graph.*;
import jakarta.validation.constraints.NotNull;
import partitioning.models.SPTWithRegionWeights;
import partitioning.maxflow.shortestpathtree.ShortestPathTreeSearcher;
import partitioning.models.FlowResult;
import partitioning.models.NeighborSplit;
import partitioning.models.DijkstraResult;
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

    private record PathCandidate(
            Vertex splitVertex1,
            Vertex splitVertex2,
            DijkstraResult path1ToBoundary,
            DijkstraResult path2ToBoundary,
            List<Vertex> combinedPath,
            List<Vertex> pathInOriginalGraph,
            double totalDistance
    ) {}

    public MaxFlowReif(Graph<Vertex> initGraph, Graph<VertexOfDualGraph> dualGraph,
                       VertexOfDualGraph source, VertexOfDualGraph sink) {
        this.initGraph = initGraph;
        this.dualGraph = dualGraph;
        this.source = source;
        this.sink = sink;
        this.conversion = new CoordinateConversion();
    }

    @Override
    public FlowResult findFlow() {
        // Подготовка данных
        HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph = buildComparisonMap();
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

        CornersData corners = findAllCorners(boundaries);
        System.out.println("Found " + corners.sourceCorners().size() +
                                   " source corners and " + corners.sinkCorners().size() +
                                   " sink corners on external boundary");

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

        // Разделение вершин
        Map<Long, NeighborSplit> neighborSplits = preprocessVertexSplits(
                modifiedGraph, shortestPathResult.path(), boundaries,
                sourceNeighbors, sinkNeighbors
        );

        // Разделение вершин пути
        SplitVerticesData splitData = splitPathVertices(
                modifiedGraph, shortestPathResult.path(), neighborSplits
        );

        // Поиск лучшего пути через split-вершины
        Optional<PathCandidate> bestCandidate = findBestPathThroughSplits(
                splitData, modifiedGraph, boundaries.externalBoundary(),
                corners, dualGraph
        );

        if (bestCandidate.isEmpty()) {
            return handleNoBestPath(boundaries, shortestPathResult.path(),
                                    neighborSplits, sourceNeighbors,
                                    sinkNeighbors, modifiedGraph);
        }

        // Заполнение потока
        PathCandidate best = bestCandidate.get();
        flow = fillFlowInDualGraph(best.pathInOriginalGraph(), dualGraph);

        // Визуализация
        dumpVisualization(boundaries, shortestPathResult.path(),
                          best.pathInOriginalGraph(), neighborSplits,
                          sourceNeighbors, sinkNeighbors, modifiedGraph,
                          best, splitData.splitToOriginalMap());

        return new FlowResult(flow, dualGraph, source, sink);
    }

    private record BoundariesData(
            List<Vertex> sourceBoundary,
            List<Vertex> sinkBoundary,
            List<Vertex> externalBoundary
    ) {}

    private record CornersData(
            List<Vertex> sourceCorners,
            List<Vertex> sinkCorners
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
        modifiedGraph.setEdgeToDualVertexMap(initGraph.getEdgeToDualVertexMap());
        return modifiedGraph;
    }

    /**
     * Находит все углы на границах
     */
    private CornersData findAllCorners(BoundariesData boundaries) {
        List<Vertex> sourceCorners = findCorners(
                boundaries.externalBoundary(),
                boundaries.sourceBoundary()
        );
        List<Vertex> sinkCorners = findCorners(
                boundaries.externalBoundary(),
                boundaries.sinkBoundary()
        );
        return new CornersData(sourceCorners, sinkCorners);
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
            Map<Long, NeighborSplit> splits = preprocessNeighborSplits(
                    modifiedGraph, path,
                    boundaries.sourceBoundary(),
                    boundaries.sinkBoundary(),
                    boundaries.externalBoundary()
            );
            System.out.println("Created " + splits.size() +
                                       " neighbor splits for " + path.size() +
                                       " vertices in path");
            return splits;
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
            splitVertices.add(splitVertex(
                    modifiedGraph, pathVertex, splitToOriginalMap, neighborSplits
            ));
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
            CornersData corners,
            Graph<VertexOfDualGraph> dualGraph) {

        double minPathLength = Double.MAX_VALUE;
        PathCandidate bestCandidate = null;

        for (Map.Entry<Vertex, Vertex> splitVertex : splitData.splitVertices()) {
            Optional<PathCandidate> candidate = evaluateSplitVertex(
                    splitVertex,
                    modifiedGraph,
                    externalBoundary,
                    dualGraph,
                    corners.sourceCorners(),
                    corners.sinkCorners(),
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
            List<Vertex> sourceCorners,
            List<Vertex> sinkCorners,
            Map<Vertex, Vertex> splitToOriginalMap) {

        Vertex splitVertex1 = splitVertex.getKey();
        Vertex splitVertex2 = splitVertex.getValue();

        // Поиск путей от обеих split-вершин к границе
        Optional<DijkstraResult> path1ToBoundaryOpt = dijkstraSingleSourceWithRegionWeights(
                modifiedGraph, splitVertex1,
                externalBoundary, dualGraph, sourceCorners, sinkCorners, true
        );

        Optional<DijkstraResult> path2ToBoundaryOpt = dijkstraSingleSourceWithRegionWeights(
                modifiedGraph, splitVertex2,
                externalBoundary, dualGraph, sourceCorners, sinkCorners, false
        );

        if (path1ToBoundaryOpt.isEmpty() || path2ToBoundaryOpt.isEmpty()) {
            logMissingPath(splitVertex1, splitVertex2, splitToOriginalMap,
                           path1ToBoundaryOpt, path2ToBoundaryOpt);
            return Optional.empty();
        }

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
     * Объединяет два пути (первый в обратном порядке + второй)
     */
    private List<Vertex> combinePaths(List<Vertex> path1, List<Vertex> path2) {
        List<Vertex> combined = new ArrayList<>();

        // Добавляем path1 в обратном порядке
        for (int i = path1.size() - 1; i >= 0; i--) {
            combined.add(path1.get(i));
        }

        // Добавляем path2 без первой вершины (она совпадает с последней path1)
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
            Optional<DijkstraResult> path1Opt,
            Optional<DijkstraResult> path2Opt) {

        System.out.println("ERROR: Path to external boundary not found!");
        System.out.println("  Original vertex on shortest path: " +
                                   splitToOriginalMap.get(splitVertex1).getName());
        System.out.println("  Split vertex 1 ID: " + splitVertex1.getName());
        System.out.println("  Split vertex 2 ID: " + splitVertex2.getName());
        System.out.println("  Path1ToBoundary (left): " +
                                   (path1Opt.isPresent() ? "FOUND" : "NULL"));
        System.out.println("  Path2ToBoundary (right): " +
                                   (path2Opt.isPresent() ? "FOUND" : "NULL"));
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
        System.out.println("  modifiedGraph vertices: " + modifiedGraph.verticesArray().size());
        System.out.println("  sourceBoundary size: " + boundaries.sourceBoundary().size());
        System.out.println("  sinkBoundary size: " + boundaries.sinkBoundary().size());

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

        System.out.println("No path found");
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
            List<Vertex> sourceCorners,
            List<Vertex> sinkCorners,
            boolean isSourceSide) {

        Optional<DijkstraResult> defaultResultOpt = dijkstraSingleSource(
                graph, sourceVertex, targetBoundary
        );

        if (defaultResultOpt.isEmpty()) {
            return Optional.empty();
        }

        DijkstraResult defaultResult = defaultResultOpt.get();

        SPTWithRegionWeights spt = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph, defaultResult.previous(), sourceVertex, targetBoundary,
                dualGraph, sourceCorners, sinkCorners, isSourceSide
        );

        return Optional.of(new DijkstraResult(
                defaultResult.path(),
                defaultResult.distance(),
                defaultResult.previous(),
                defaultResult.distances(),
                spt.boundaryLeaves(),
                spt.leftRegionWeights(),
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
        VertexOfDualGraph face1 = findFaceContainingEdge(v1, v2, dualGraph);
        VertexOfDualGraph face2 = findFaceContainingEdge(v2, v1, dualGraph);

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

    /**
     * Находит грань содержащую направленное ребро (v1 -> v2)
     */
    private VertexOfDualGraph findFaceContainingEdge(
            Vertex v1, Vertex v2, Graph<VertexOfDualGraph> dualGraph) {

        for (VertexOfDualGraph face : dualGraph.verticesArray()) {
            List<Vertex> faceVertices = face.getVerticesOfFace();
            if (faceVertices != null && faceContainsEdge(faceVertices, v1, v2)) {
                return face;
            }
        }
        return null;
    }

    /**
     * Проверяет содержит ли грань направленное ребро (v1 -> v2)
     */
    private boolean faceContainsEdge(List<Vertex> faceVertices, Vertex v1, Vertex v2) {
        for (int i = 0; i < faceVertices.size(); i++) {
            Vertex fv1 = faceVertices.get(i);
            Vertex fv2 = faceVertices.get((i + 1) % faceVertices.size());
            if (fv1.equals(v1) && fv2.equals(v2)) {
                return true;
            }
        }
        return false;
    }

    // === Вспомогательные методы ===

    private HashMap<Vertex, VertexOfDualGraph> buildComparisonMap() {
        HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph = new HashMap<>();
        for (VertexOfDualGraph dualVertex : dualGraph.verticesArray()) {
            if (!dualVertex.equals(source) && !dualVertex.equals(sink)) {
                comparisonForDualGraph.put(dualVertex, dualVertex);
            }
        }
        return comparisonForDualGraph;
    }

    private HashSet<VertexOfDualGraph> collectNeighbors(VertexOfDualGraph vertex) {
        HashSet<VertexOfDualGraph> neighbors = new HashSet<>();
        for (VertexOfDualGraph neighbor : dualGraph.getEdges().get(vertex).keySet()) {
            if (!neighbor.equals(sink) && !neighbor.equals(source)) {
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }

    private List<Vertex> findCorners(List<Vertex> externalBoundary, List<Vertex> targetBoundary) {
        Set<Long> targetBoundaryNames = targetBoundary.stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());

        List<Vertex> corners = new ArrayList<>();
        for (Vertex v : externalBoundary) {
            if (targetBoundaryNames.contains(v.getName())) {
                corners.add(v);
            }
        }
        return corners;
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