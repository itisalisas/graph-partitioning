package partitioning.maxflow;

import java.util.*;
import java.util.stream.Collectors;

import graph.*;
import jakarta.validation.constraints.NotNull;
import partitioning.entities.SPTResult;
import partitioning.entities.SPTWithRegionWeights;
import partitioning.shortestpathtree.ShortestPathTreeProcessor;
import partitioning.shortestpathtree.ShortestPathTreeSearcher;
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
    Map<Vertex, VertexOfDualGraph> comparisonForDualGraph;

    private record PathCandidate(
            Vertex splitVertex1,
            Vertex splitVertex2,
            DijkstraResult path1ToBoundary,
            DijkstraResult path2ToBoundary,
            List<Vertex> combinedPath,
            List<Vertex> pathInOriginalGraph,
            double totalDistance,
            double balanceWeight
    ) {}

    public MaxFlowReif(Graph<Vertex> initGraph,
                       Graph<VertexOfDualGraph> dualGraph,
                       VertexOfDualGraph source,
                       VertexOfDualGraph sink,
                       Map<Vertex, VertexOfDualGraph> comparisonForDualGraph
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
                modifiedGraph, boundaries.sourceBoundary(), boundaries.sinkBoundary(), CornerConstraints.empty()
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
                splitData, modifiedGraph, boundaries,
                intersections, dualGraph
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

        return new FlowResult(flow, dualGraph, source, sink, best.pathInOriginalGraph);
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

    private record TwoKeyVertices(
            Vertex sourceVertex,
            Vertex sinkVertex
    ) {
        boolean isValid() {
            return sourceVertex != null && sinkVertex != null;
        }
    }

    private record SplitVerticesData(
            List<Map.Entry<Vertex, Vertex>> splitVertices,
            Map<Vertex, Vertex> splitToOriginalMap
    ) {}

    /**
     * Вычисляет все границы
     */
    private BoundariesData computeBoundaries(
            Set<VertexOfDualGraph> sourceNeighbors,
            Set<VertexOfDualGraph> sinkNeighbors,
            Map<Vertex, VertexOfDualGraph> comparisonForDualGraph) {

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
            BoundariesData boundaries,
            IntersectionsData intersections,
            Graph<VertexOfDualGraph> dualGraph) {

        List<Map.Entry<Vertex, Vertex>> splits = splitData.splitVertices();
        if (splits.isEmpty()) return Optional.empty();

        int lo = 0, hi = splits.size() - 1;

        while (lo < hi) {
            int mid = (lo + hi) / 2;
            Optional<PathCandidate> midOpt = evalAt(mid, splits, splitData,
                    modifiedGraph, boundaries, intersections, dualGraph);
            if (midOpt.isEmpty()) {
                lo = mid + 1;
                continue;
            }
            double diff = midOpt.get().path1ToBoundary().totalRegionWeight()
                    - midOpt.get().path2ToBoundary().totalRegionWeight();
            if (diff < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }

        Optional<PathCandidate> atLo = evalAt(lo, splits, splitData,
                modifiedGraph, boundaries, intersections, dualGraph);
        Optional<PathCandidate> atPrev = lo > 0
                ? evalAt(lo - 1, splits, splitData, modifiedGraph, boundaries, intersections, dualGraph)
                : Optional.empty();

        if (atLo.isEmpty()) return atPrev;
        if (atPrev.isEmpty()) return atLo;

        return atLo.get().balanceWeight() <= atPrev.get().balanceWeight() ? atLo : atPrev;
    }

    private Optional<PathCandidate> evalAt(
            int idx,
            List<Map.Entry<Vertex, Vertex>> splits,
            SplitVerticesData splitData,
            Graph<Vertex> modifiedGraph,
            BoundariesData boundaries,
            IntersectionsData intersections,
            Graph<VertexOfDualGraph> dualGraph) {
        return evaluateSplitVertex(
                splits.get(idx),
                modifiedGraph,
                boundaries,
                dualGraph,
                intersections.sourceIntersections(),
                intersections.sinkIntersections(),
                splitData.splitToOriginalMap()
        );
    }
    /**
     * Оценивает одну split-вершину и возвращает кандидата на лучший путь
     */
    private Optional<PathCandidate> evaluateSplitVertex(
            Map.Entry<Vertex, Vertex> splitVertex,
            Graph<Vertex> modifiedGraph,
            BoundariesData boundaries,
            Graph<VertexOfDualGraph> dualGraph,
            List<Vertex> sourceIntersections,
            List<Vertex> sinkIntersections,
            Map<Vertex, Vertex> splitToOriginalMap) {

        Vertex splitVertex1 = splitVertex.getKey();
        Vertex splitVertex2 = splitVertex.getValue();

        List<Vertex> targetSegment1 = extractBoundarySegment(
                boundaries.externalBoundary, sourceIntersections, sinkIntersections, true);
        List<Vertex> targetSegment2 = extractBoundarySegment(
                boundaries.externalBoundary, sourceIntersections, sinkIntersections, false);


        // Поиск путей от обеих split-вершин к границе
        Optional<DijkstraResult> path1ToBoundaryOpt = dijkstraSingleSourceWithRegionWeights(
                modifiedGraph,
                splitVertex1,
                targetSegment1,
                boundaries,
                dualGraph,
                sourceIntersections,
                sinkIntersections,
                true
        );

        Optional<DijkstraResult> path2ToBoundaryOpt = dijkstraSingleSourceWithRegionWeights(
                modifiedGraph,
                splitVertex2,
                targetSegment2,
                boundaries,
                dualGraph,
                sourceIntersections,
                sinkIntersections,
                false
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

        ShortestPathTreeProcessor sptProcessor = new ShortestPathTreeProcessor();
        SPTResult result = sptProcessor.findBestPath(path1ToBoundary, path2ToBoundary, source.getWeight(), sink.getWeight());

        List<Vertex> pathInOriginalGraph = mapToOriginalGraph(
                result.path(),
                splitToOriginalMap
        );

        return Optional.of(new PathCandidate(
                splitVertex1, splitVertex2,
                path1ToBoundary, path2ToBoundary,
                result.path(), pathInOriginalGraph,
                result.totalDistance(), result.balanceWeight()
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

        Map<Long, Integer> positionMap = new HashMap<>();
        for (int i = 0; i < externalBoundary.size(); i++) {
            positionMap.put(externalBoundary.get(i).getName(), i);
        }

        TwoKeyVertices keyVertices = findTwoKeyVerticesHelper(sourceIntersections, sinkIntersections, isFirstSide);

        Vertex sourceCorner = keyVertices.sourceVertex();
        Vertex sinkCorner = keyVertices.sinkVertex();

        if (sourceCorner == null || sinkCorner == null) {
            System.out.println("WARNING: Could not find corner pair, using full boundary");
            return externalBoundary;
        }

        int startPos = positionMap.get(sourceCorner.getName());
        int endPos = positionMap.get(sinkCorner.getName());
        if (!isFirstSide) {
            int tmp = startPos;
            startPos = endPos;
            endPos = tmp;
        }

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
     * Находит два ключевых угла из множества пересечений
     */
    private TwoKeyVertices findTwoKeyVerticesHelper(
            List<Vertex> sourceIntersections,
            List<Vertex> sinkIntersections,
            boolean isFirstSide) {

        if (sourceIntersections.isEmpty() || sinkIntersections.isEmpty()) {
            return new TwoKeyVertices(null, null);
        }

        Vertex firstSourceCorner = sourceIntersections.get(0);
        Vertex lastSourceCorner = sourceIntersections.get(sourceIntersections.size() - 1);
        Vertex firstSinkCorner = sinkIntersections.get(0);
        Vertex lastSinkCorner = sinkIntersections.get(sinkIntersections.size() - 1);

        TwoKeyVertices pair1 = new TwoKeyVertices(firstSourceCorner, lastSinkCorner);
        TwoKeyVertices pair2 = new TwoKeyVertices(lastSourceCorner, firstSinkCorner);

        System.out.println("Pair 1 (first source + last sink): source=" + pair1.sourceVertex().getName() +
                ", sink=" + pair1.sinkVertex().getName());
        System.out.println("Pair 2 (last source + first sink): source=" + pair2.sourceVertex().getName() +
                ", sink=" + pair2.sinkVertex().getName());

        return !isFirstSide ? pair1 : pair2;
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
                sourceNeighbors, sinkNeighbors, flow, conversion,
                initGraph, comparisonForDualGraph
        );
    }

    private Optional<DijkstraResult> dijkstraSingleSourceWithRegionWeights(
            Graph<Vertex> graph,
            Vertex sourceVertex,
            List<Vertex> targetSegment,
            BoundariesData boundaries,
            @NotNull Graph<VertexOfDualGraph> dualGraph,
            List<Vertex> sourceIntersections,
            List<Vertex> sinkIntersections,
            boolean isFirstSide) {

        // Находим две ключевые угловые вершины для этой стороны
        TwoKeyVertices keyVertices = findTwoKeyVerticesForConstraints(
                sourceIntersections, sinkIntersections, isFirstSide
        );

        // Создаем ограничения только для этих двух ключевых вершин
        CornerConstraints cornerConstraints = buildCornerConstraintsForKeyVertices(
                graph, keyVertices, boundaries, isFirstSide
        );

        Optional<DijkstraResult> defaultResultOpt = dijkstraSingleSource(
                graph, sourceVertex, targetSegment, cornerConstraints
        );

        if (defaultResultOpt.isEmpty()) {
            return Optional.empty();
        }

        DijkstraResult defaultResult = defaultResultOpt.get();

        SPTWithRegionWeights spt = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph, defaultResult.previous(), sourceVertex, targetSegment,
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
                spt.leafIndices(),
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
        // Строим битовую маску принадлежности
        List<Integer> mask = externalBoundary.stream()
                .map(v -> targetBoundaryNames.contains(v.getName()) ? 1 : 0)
                .collect(Collectors.toList());

        // Находим стартовый индекс циклического сдвига
        int startIndex = findCyclicShiftStart(mask);

        // Применяем сдвиг и собираем пересечения
        List<Vertex> intersections = new ArrayList<>();
        int n = externalBoundary.size();

        for (int i = 0; i < n; i++) {
            Vertex v = externalBoundary.get((startIndex + i) % n);
            if (targetBoundaryNames.contains(v.getName())) {
                intersections.add(v);
            }
        }

        return intersections;
    }

    /**
     * Находит индекс начала циклического сдвига,
     * чтобы все единицы шли подряд.
     */
    private int findCyclicShiftStart(List<Integer> mask) {
        int n = mask.size();
        int onesCount = (int) mask.stream().filter(x -> x == 1).count();

        if (onesCount == 0 || onesCount == n) {
            return 0;
        }

        for (int i = 0; i < n; i++) {
            boolean allOnes = true;
            for (int j = 0; j < onesCount; j++) {
                if (mask.get((i + j) % n) != 1) {
                    allOnes = false;
                    break;
                }
            }
            if (allOnes) {
                return i;
            }
        }

        return 0; // fallback
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
        modifiedGraph.addBoundEdges(sourceBoundary, initGraph);
        modifiedGraph.addBoundEdges(sinkBoundary, initGraph);
        modifiedGraph.addBoundEdges(externalBoundary, initGraph);

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

    /**
     * Создает ограничения только для двух ключевых угловых вершин
     */
    private CornerConstraints buildCornerConstraintsForKeyVertices(
            Graph<Vertex> graph,
            TwoKeyVertices keyVertices,
            BoundariesData boundaries,
            boolean isFirstSide) {

        if (!keyVertices.isValid()) {
            System.out.println("WARNING: Invalid key vertices, no constraints");
            return CornerConstraints.empty();
        }

        Set<Long> cornerVertices = new HashSet<>();
        Map<Long, List<EdgeOfGraph<Vertex>>> allowedEdgesForCorner = new HashMap<>();

        Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedEdgesByVertex = graph.arrangeByAngle();

        // Обрабатываем source corner (ключевая вершина на source стороне)
        Vertex sourceCorner = keyVertices.sourceVertex();
        cornerVertices.add(sourceCorner.getName());
        List<EdgeOfGraph<Vertex>> allowedEdgesForSource = findAllowedEdgesForCorner(
                sourceCorner, sortedEdgesByVertex, boundaries,
                isFirstSide, true  // true = это source corner
        );
        allowedEdgesForCorner.put(sourceCorner.getName(), allowedEdgesForSource);

        // Обрабатываем sink corner (ключевая вершина на sink стороне)
        Vertex sinkCorner = keyVertices.sinkVertex();
        cornerVertices.add(sinkCorner.getName());
        List<EdgeOfGraph<Vertex>> allowedEdgesForSink = findAllowedEdgesForCorner(
                sinkCorner, sortedEdgesByVertex, boundaries,
                isFirstSide, false  // false = это sink corner
        );
        allowedEdgesForCorner.put(sinkCorner.getName(), allowedEdgesForSink);

        return new CornerConstraints(cornerVertices, allowedEdgesForCorner);
    }

    private List<EdgeOfGraph<Vertex>> findAllowedEdgesForCorner(
            Vertex corner,
            Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedEdgesByVertex,
            BoundariesData boundaries,
            boolean isFirstSide,
            boolean isSourceCorner) {
        Set<Vertex> externalBoundarySet = new HashSet<>(boundaries.externalBoundary);
        Set<Vertex> targetBoundarySet = new HashSet<>(isSourceCorner ? boundaries.sourceBoundary : boundaries.sinkBoundary);

        TreeSet<EdgeOfGraph<Vertex>> allEdges = sortedEdgesByVertex.get(corner);
        if (allEdges == null || allEdges.isEmpty()) {
            return List.of();
        }

        List<EdgeOfGraph<Vertex>> edgesList = new ArrayList<>(allEdges);

        // Находим индексы ключевых рёбер
        int externalBoundaryEdgeIdx = -1;
        int sourceSinkEdgeIdx = -1;

        for (int i = 0; i < edgesList.size(); i++) {
            EdgeOfGraph<Vertex> edge = edgesList.get(i);

            // Ребро внешней границы (к другой boundary вершине)
            if (externalBoundarySet.contains(edge.end) && targetBoundarySet.contains(edge.end)) {
                externalBoundaryEdgeIdx = i;
            }

            // Ребро к source/sink boundary или к source vertex
            if (!externalBoundarySet.contains(edge.end) && targetBoundarySet.contains(edge.end)) {
                sourceSinkEdgeIdx = i;
            }
        }

        if (externalBoundaryEdgeIdx == -1 || sourceSinkEdgeIdx == -1) {
            System.out.println("WARNING: Corner " + corner.getName() +
                    " - не найдены граничные рёбра, разрешаем все");
            return edgesList;
        }

        int startIdx, endIdx;

        if (isSourceCorner) {
            if (isFirstSide) {
                startIdx = sourceSinkEdgeIdx;
                endIdx = externalBoundaryEdgeIdx;
            } else {
                startIdx = externalBoundaryEdgeIdx;
                endIdx = sourceSinkEdgeIdx;
            }
        } else {
            if (isFirstSide) {
                startIdx = externalBoundaryEdgeIdx;
                endIdx = sourceSinkEdgeIdx;
            } else {
                startIdx = sourceSinkEdgeIdx;
                endIdx = externalBoundaryEdgeIdx;
            }
        }

        // Собираем рёбра от start к end (по кругу, против часовой)
        List<EdgeOfGraph<Vertex>> allowedEdges = new ArrayList<>();
        int currentIdx = startIdx;

        for (int iter = 0; iter <= edgesList.size(); iter++) {
            if (currentIdx == startIdx && currentIdx == externalBoundaryEdgeIdx) {
                currentIdx = (currentIdx + 1) % edgesList.size();
                continue;
            }

            if (currentIdx == endIdx && currentIdx == externalBoundaryEdgeIdx) {
                break;
            }

            allowedEdges.add(edgesList.get(currentIdx));

            currentIdx = (currentIdx + 1) % edgesList.size();
        }

        System.out.println("Corner " + corner.getName() +
                " (type=" + (isSourceCorner ? "SOURCE" : "SINK") +
                ", isFirstSide=" + isFirstSide + "): " +
                "allowed " + allowedEdges.size() + "/" + edgesList.size() + " edges " +
                "(from idx " + startIdx + " to " + endIdx + ")");

        return allowedEdges;
    }

    /**
     * Находит два ключевых угла (source и sink), ограничивающих нужный сегмент external boundary
     */
    /**
     * Находит два ключевых угла (source и sink), ограничивающих нужный сегмент external boundary
     * Использует ТУ ЖЕ логику что extractBoundarySegment
     */
    private TwoKeyVertices findTwoKeyVerticesForConstraints(
            List<Vertex> sourceIntersections,
            List<Vertex> sinkIntersections,
            boolean isFirstSide) {

        if (sourceIntersections.isEmpty() || sinkIntersections.isEmpty()) {
            return new TwoKeyVertices(null, null);
        }

        Vertex firstSourceCorner = sourceIntersections.get(0);
        Vertex lastSourceCorner = sourceIntersections.get(sourceIntersections.size() - 1);
        Vertex firstSinkCorner = sinkIntersections.get(0);
        Vertex lastSinkCorner = sinkIntersections.get(sinkIntersections.size() - 1);

        TwoKeyVertices pair1 = new TwoKeyVertices(firstSourceCorner, lastSinkCorner);
        TwoKeyVertices pair2 = new TwoKeyVertices(lastSourceCorner, firstSinkCorner);

        return !isFirstSide ? pair1 : pair2;
    }
}