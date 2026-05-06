package partitioning.maxflow;

import java.util.*;
import java.util.stream.Collectors;

import graph.*;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(MaxFlowReif.class);
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
            double totalDistance,
            double balanceWeight,
            boolean isPositive
    ) {}

    public MaxFlowReif(Graph<Vertex> initGraph,
                       Graph<VertexOfDualGraph> dualGraph,
                       VertexOfDualGraph source,
                       VertexOfDualGraph sink
    ) {
        this.initGraph = initGraph;
        this.dualGraph = dualGraph;
        this.source = source;
        this.sink = sink;
        this.conversion = new CoordinateConversion();
    }

    @Override
    public FlowResult findFlow() {
        // Подготовка данных
        HashSet<VertexOfDualGraph> sourceNeighbors = collectNeighbors(source);
        HashSet<VertexOfDualGraph> sinkNeighbors = collectNeighbors(sink);
        long startTime = System.currentTimeMillis();

        BoundariesData boundaries = computeBoundaries(sourceNeighbors, sinkNeighbors);
        long time1 = System.currentTimeMillis();
        logger.info("Time for computing boundaries: {} seconds", (time1 - startTime) / 1000.0);

        if (boundaries.externalBoundary().isEmpty()) {
            logger.error("External boundary is empty!");
            return new FlowResult(0, dualGraph, source, sink);
        }

        // Создание модифицированного графа
        Graph<Vertex> modifiedGraph = createModifiedGraph(
                boundaries, sourceNeighbors, sinkNeighbors
        );
        long time2 = System.currentTimeMillis();
        logger.info("Time for creating modified graph: {} seconds", (time2 - time1) / 1000.0);

        // Поиск кратчайшего пути
        Optional<DijkstraResult> shortestPathResultOpt = dijkstraMultiSource(
                modifiedGraph, boundaries.sourceBoundary(), boundaries.sinkBoundary(), CornerConstraints.empty()
        );
        long time3 = System.currentTimeMillis();
        logger.info("Time for finding shortest path between source and sink: {} seconds", (time3 - time2) / 1000.0);

        if (shortestPathResultOpt.isEmpty()) {
            return handleNoShortestPath(boundaries, sourceNeighbors,
                                        sinkNeighbors, modifiedGraph);
        }

        DijkstraResult shortestPathResult = shortestPathResultOpt.get();
        if (shortestPathResult.path().isEmpty()) {
            return handleNoShortestPath(boundaries, sourceNeighbors,
                                        sinkNeighbors, modifiedGraph);
        }

        logger.info("Found shortest path with {} vertices, distance: {}", 
                shortestPathResult.path().size(), shortestPathResult.distance());

        // Препроцессинг разделения вершин
        Map<Long, NeighborSplit> neighborSplits = preprocessVertexSplits(
                modifiedGraph, shortestPathResult.path(), boundaries,
                sourceNeighbors, sinkNeighbors
        );

        long time4 = System.currentTimeMillis();
        logger.info("Time for preprocessing vertex splits: {} seconds", (time4 - time3) / 1000.0);

        // Разделение вершин пути
        SplitVerticesData splitData = splitPathVertices(
                modifiedGraph, shortestPathResult.path(), neighborSplits
        );
        long time5 = System.currentTimeMillis();
        logger.info("Time for splitting path vertices: {} seconds", (time5 - time4) / 1000.0);

        // Поиск лучшего пути через split-вершины
        IntersectionsData intersections = findAllIntersections(boundaries);
        logger.debug("Found {} source intersections and {} sink intersections on external boundary",
                intersections.sourceIntersections.size(), intersections.sinkIntersections.size());
        Optional<PathCandidate> bestCandidate = findBestPathThroughSplits(
                splitData, modifiedGraph, boundaries,
                intersections, dualGraph, shortestPathResult.path()
        );
        long time6 = System.currentTimeMillis();
        logger.info("Time for finding best path through splits: {} seconds", (time6 - time5) / 1000.0);

        if (bestCandidate.isEmpty()) {
            return handleNoBestPath(boundaries, shortestPathResult.path(),
                                    sourceNeighbors, sinkNeighbors, modifiedGraph);
        }

        // Заполнение потока
        PathCandidate best = bestCandidate.get();
        flow = fillFlowInDualGraph(best.pathInOriginalGraph(), dualGraph);
        long time7 = System.currentTimeMillis();
        logger.info("Time for filling flow in dual graph: {} seconds", (time7 - time6) / 1000.0);

        // Визуализация
        dumpVisualization(boundaries, shortestPathResult.path(),
                          best.pathInOriginalGraph(),
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
            Set<VertexOfDualGraph> sinkNeighbors
    ) {

        List<Vertex> sourceBoundary = BoundSearcher.findBound(initGraph, sourceNeighbors);
        List<Vertex> sinkBoundary = BoundSearcher.findBound(initGraph, sinkNeighbors);

        HashSet<VertexOfDualGraph> allDualVerticesSet = new HashSet<>(
                dualGraph.verticesArray()
        );
        allDualVerticesSet.remove(source);
        allDualVerticesSet.remove(sink);

        List<Vertex> externalBoundary = BoundSearcher.findBound(initGraph, allDualVerticesSet);

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
                logger.error("Broken boundary detected: {}", e.getMessage());
                FlowWriter.dumpVisualizationData(
                        boundaries.externalBoundary(),
                        boundaries.sourceBoundary(),
                        boundaries.sinkBoundary(),
                        path, null,
                        sourceNeighbors, sinkNeighbors, 0,
                        initGraph, modifiedGraph, dualGraph, source, sink, conversion
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
            Graph<VertexOfDualGraph> dualGraph,
            List<Vertex> path) {

        List<Map.Entry<Vertex, Vertex>> splits = splitData.splitVertices();
        if (splits.isEmpty()) return Optional.empty();

        int lo = 0, hi = splits.size() - 1;

        while (lo < hi) {
            int mid = (lo + hi) / 2;
            Optional<PathCandidate> midOpt = evalAt(mid, splits, splitData,
                    modifiedGraph, boundaries, intersections, dualGraph, path);
            if (midOpt.isEmpty()) {
                lo = mid + 1;
                continue;
            }
            double diff = midOpt.get().balanceWeight();
            logger.info("eval spt at vertex of path number {}, diff in weight {}, isPositive {}", mid, diff, midOpt.get().isPositive);
            if (midOpt.get().isPositive) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }

        Optional<PathCandidate> atLo = evalAt(lo, splits, splitData,
                modifiedGraph, boundaries, intersections, dualGraph, path);
        Optional<PathCandidate> atPrev = lo > 0
                ? evalAt(lo - 1, splits, splitData, modifiedGraph, boundaries, intersections, dualGraph, path)
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
            Graph<VertexOfDualGraph> dualGraph,
            List<Vertex> path
    ) {
        return evaluateSplitVertex(
                splits.get(idx),
                modifiedGraph,
                boundaries,
                dualGraph,
                intersections.sourceIntersections(),
                intersections.sinkIntersections(),
                splitData.splitToOriginalMap(),
                path
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
            Map<Vertex, Vertex> splitToOriginalMap,
            List<Vertex> path) {

        Vertex splitVertex1 = splitVertex.getKey();
        Vertex splitVertex2 = splitVertex.getValue();

        List<Vertex> targetSegment1 = extractBoundarySegment(
                boundaries.externalBoundary, sourceIntersections, sinkIntersections, true);
        List<Vertex> targetSegment2 = extractBoundarySegment(
                boundaries.externalBoundary, sourceIntersections, sinkIntersections, false);

        long startTime = System.currentTimeMillis();

        // Поиск путей от обеих split-вершин к границе
        Optional<DijkstraResult> path1ToBoundaryOpt = dijkstraSingleSourceWithRegionWeights(
                modifiedGraph,
                splitVertex1,
                targetSegment1,
                boundaries,
                dualGraph,
                sourceIntersections,
                sinkIntersections,
                true,
                path
        );

        long time1 = System.currentTimeMillis();
        logger.info("Time for build 1st spt: {} seconds", (time1 - startTime) / 1000.0);

        Optional<DijkstraResult> path2ToBoundaryOpt = dijkstraSingleSourceWithRegionWeights(
                modifiedGraph,
                splitVertex2,
                targetSegment2,
                boundaries,
                dualGraph,
                sourceIntersections,
                sinkIntersections,
                false,
                path
        );

        long time2 = System.currentTimeMillis();
        logger.info("Time for build 2nd spt: {} seconds", (time2 - time1) / 1000.0);

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
        long time3 = System.currentTimeMillis();
        logger.info("Time for find best path in spt: {} seconds", (time3 - time2) / 1000.0);

        List<Vertex> pathInOriginalGraph = mapToOriginalGraph(
                result.path(),
                splitToOriginalMap
        );

        return Optional.of(new PathCandidate(
                splitVertex1, splitVertex2,
                path1ToBoundary, path2ToBoundary,
                result.path(), pathInOriginalGraph,
                result.totalDistance(), result.balanceWeight(),
                result.isPositive()
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
            logger.warn("No intersections found, using full boundary");
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
            logger.warn("Could not find corner pair, using full boundary");
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

        logger.debug("Extracted boundary segment: {} vertices (from {} to {}, isFirstSide={})", 
                segment.size(), sourceCorner.getName(), sinkCorner.getName(), isFirstSide);

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

        logger.debug("Pair 1 (first source + last sink): source={}, sink={}", 
                pair1.sourceVertex().getName(), pair1.sinkVertex().getName());
        logger.debug("Pair 2 (last source + first sink): source={}, sink={}", 
                pair2.sourceVertex().getName(), pair2.sinkVertex().getName());

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

        logger.error("Path to external boundary not found!");
        logger.error("  Original vertex on shortest path: {}", splitToOriginalMap.get(splitVertex1).getName());
        logger.error("  Split vertex 1 ID: {}", splitVertex1.getName());
        logger.error("  Split vertex 2 ID: {}", splitVertex2.getName());
        logger.error("  Path1ToBoundary (left): {}", isPath1Found ? "FOUND" : "NULL");
        logger.error("  Path2ToBoundary (right): {}", isPath2Found ? "FOUND" : "NULL");
    }

    /**
     * Обработка случая когда нет кратчайшего пути
     */
    private FlowResult handleNoShortestPath(
            BoundariesData boundaries,
            HashSet<VertexOfDualGraph> sourceNeighbors,
            HashSet<VertexOfDualGraph> sinkNeighbors,
            Graph<Vertex> modifiedGraph) {

        logger.error("No shortest path found between source and sink boundaries!");
        FlowWriter.dumpVisualizationData(
                boundaries.externalBoundary(),
                boundaries.sourceBoundary(),
                boundaries.sinkBoundary(),
                List.of(), List.of(),
                sourceNeighbors, sinkNeighbors, 0,
                initGraph, modifiedGraph, dualGraph, source, sink, conversion
        );

        return new FlowResult(0, dualGraph, source, sink);
    }

    /**
     * Обработка случая когда не найден лучший путь
     */
    private FlowResult handleNoBestPath(
            BoundariesData boundaries,
            List<Vertex> shortestPath,
            HashSet<VertexOfDualGraph> sourceNeighbors,
            HashSet<VertexOfDualGraph> sinkNeighbors,
            Graph<Vertex> modifiedGraph) {

        logger.error("No best path found to external boundary!");
        FlowWriter.dumpVisualizationData(
                boundaries.externalBoundary(),
                boundaries.sourceBoundary(),
                boundaries.sinkBoundary(),
                shortestPath, null,
                sourceNeighbors, sinkNeighbors, 0,
                initGraph, modifiedGraph, dualGraph, source, sink, conversion
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
            HashSet<VertexOfDualGraph> sourceNeighbors,
            HashSet<VertexOfDualGraph> sinkNeighbors,
            Graph<Vertex> modifiedGraph,
            PathCandidate best,
            Map<Vertex, Vertex> splitToOriginalMap) {

        FlowWriter.dumpVisualizationData(
                boundaries.externalBoundary(),
                boundaries.sourceBoundary(),
                boundaries.sinkBoundary(),
                shortestPath, bestPath,
                sourceNeighbors, sinkNeighbors, flow,
                initGraph,
                modifiedGraph, dualGraph, source, sink, conversion
        );

        FlowWriter.dumpSPTVisualizationData(
                best.path1ToBoundary(),
                best.path2ToBoundary(),
                best.splitVertex1(),
                best.splitVertex2(),
                splitToOriginalMap,
                sourceNeighbors, sinkNeighbors, flow, conversion,
                initGraph
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
            boolean isFirstSide,
            List<Vertex> path
    ) {

        // Если вершина уже на external boundary, не строим SPT - путь = 0
        Set<Long> externalBoundaryNames = boundaries.externalBoundary().stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());
        if (sourceVertex.getIsOnBoundary() && externalBoundaryNames.contains(sourceVertex.getName() / 1000)) {
            logger.info("Split vertex {} is already on external boundary, skipping SPT construction (path = 0)", 
                    sourceVertex.getName());
            
            // Возвращаем результат с нулевым расстоянием
            return Optional.of(new DijkstraResult(
                    List.of(sourceVertex),
                    0.0,
                    new HashMap<>(),
                    new HashMap<>(),
                    List.of(sourceVertex),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    0.0
            ));
        }

        // Проверяем, пересекаются ли source и sink на границе
        Set<Long> sourceIntersectionNames = sourceIntersections.stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());
        Set<Long> sinkIntersectionNames = sinkIntersections.stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());
        
        Set<Long> commonIntersections = new HashSet<>(sourceIntersectionNames);
        commonIntersections.retainAll(sinkIntersectionNames);
        
        CornerConstraints cornerConstraints;
        
        if (!commonIntersections.isEmpty()) {
            // Source и sink пересекаются на границе - создаем ограничения для вершины пересечения
            logger.debug("Source and sink intersect on boundary at vertices: {}", commonIntersections);
            cornerConstraints = buildCornerConstraintsForIntersection(
                    graph, commonIntersections, boundaries, sourceIntersections, sinkIntersections
            );
        } else {
            // Стандартный случай - находим две ключевые угловые вершины
            TwoKeyVertices keyVertices = findTwoKeyVerticesForConstraints(
                    sourceIntersections, sinkIntersections, isFirstSide
            );
            cornerConstraints = buildCornerConstraintsForKeyVertices(
                    graph, keyVertices, boundaries, isFirstSide
            );
        }

        for (var key: cornerConstraints.getAllowedEdgesForCorner().keySet()) {
            logger.debug("Allowed edges for corner {} are: {}", key, cornerConstraints.getAllowedEdgesForCorner().get(key).stream().map(v -> v.end.getName()).collect(Collectors.toList()));
        }

        Optional<DijkstraResult> defaultResultOpt = dijkstraSingleSource(
                graph, sourceVertex, targetSegment, cornerConstraints
        );

        if (defaultResultOpt.isEmpty()) {
            logger.warn("Dijkstra failed for source vertex {}", sourceVertex.getName());
            return Optional.empty();
        }

        DijkstraResult defaultResult = defaultResultOpt.get();

        SPTWithRegionWeights spt = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph, defaultResult.previous(), sourceVertex, targetSegment,
                dualGraph, isFirstSide, path
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
        Map<Vertex, Map<Vertex, VertexOfDualGraph>> map = dualGraph.edgeToDualVertexMap();
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
        Set<Map.Entry<Vertex, Vertex>> sinkInnerEdges = collectFaceEdges(sinkNeighbors);
        Set<Map.Entry<Vertex, Vertex>> sourceInnerEdges = collectFaceEdges(sourceNeighbors);
        Set<Map.Entry<Vertex, Vertex>> innerEdges = collectFaceEdges(dualGraph.verticesArray());

        Set<Vertex> sourceBoundarySet = new HashSet<>(sourceBoundary);
        Set<Vertex> sinkBoundarySet = new HashSet<>(sinkBoundary);

        // Добавляем границы
        modifiedGraph.addBoundEdgesWithConstraints(sourceBoundary, initGraph, sourceInnerEdges);
        modifiedGraph.addBoundEdgesWithConstraints(sinkBoundary, initGraph, sinkInnerEdges);
        modifiedGraph.addBoundEdges(externalBoundary, initGraph);

        // Добавляем внутренние вершины
        for (Vertex v : initGraph.verticesArray()) {
            if (shouldAddVertexToModifiedGraph(v, allowedVertices,
                                               sourceFaceVertices, sinkFaceVertices,
                                               sourceBoundarySet, sinkBoundarySet)) {
                modifiedGraph.addVertexInSubgraph(v, initGraph, innerEdges);
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
     * Собирает все ребра из граней
     */
    private Set<Map.Entry<Vertex, Vertex>> collectFaceEdges(Iterable<VertexOfDualGraph> faces) {
        Set<Map.Entry<Vertex, Vertex>> edges = new HashSet<>();
        for (VertexOfDualGraph face : faces) {
            if (face.getVerticesOfFace() != null) {
                for (int i = 0; i < face.getVerticesOfFace().size(); i++) {
                    var v1 = face.getVerticesOfFace().get(i);
                    var v2 = face.getVerticesOfFace().get((i + 1) % face.getVerticesOfFace().size());
                    edges.add(Map.entry(v1, v2));
                    edges.add(Map.entry(v2, v1));
                }
            }
        }
        return edges;
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
            Set<Vertex> sinkBoundarySet
    ) {

        return v.getName() != 0 &&
                allowedVertices.contains(v) &&
                !sourceFaceVertices.contains(v) &&
                !sinkFaceVertices.contains(v) &&
                !sourceBoundarySet.contains(v) &&
                !sinkBoundarySet.contains(v);
    }

    /**
     * Создает ограничения для вершины пересечения source и sink на external boundary
     */
    private CornerConstraints buildCornerConstraintsForIntersection(
            Graph<Vertex> graph,
            Set<Long> commonIntersections,
            BoundariesData boundaries,
            List<Vertex> sourceIntersections,
            List<Vertex> sinkIntersections
    ) {
        Set<Long> cornerVertices = new HashSet<>(commonIntersections);
        Map<Long, List<EdgeOfGraph<Vertex>>> allowedEdgesForCorner = new HashMap<>();
        
        Set<Long> externalBoundaryNames = boundaries.externalBoundary().stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());
        Set<Long> sourceBoundaryNames = boundaries.sourceBoundary().stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());
        Set<Long> sinkBoundaryNames = boundaries.sinkBoundary().stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());
        
        Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedEdgesByVertex = graph.arrangeByAngle();
        
        for (Long intersectionName : commonIntersections) {
            Vertex intersectionVertex = findVertexByNameInList(sourceIntersections, intersectionName);
            if (intersectionVertex == null) {
                intersectionVertex = findVertexByNameInList(sinkIntersections, intersectionName);
            }
            
            if (intersectionVertex == null) {
                logger.warn("Intersection vertex {} not found", intersectionName);
                continue;
            }
            
            TreeSet<EdgeOfGraph<Vertex>> allEdges = sortedEdgesByVertex.get(intersectionVertex);
            if (allEdges == null || allEdges.isEmpty()) {
                // Пробуем найти split-вершины
                Vertex split1 = new Vertex(intersectionName * 1000 + 1, intersectionVertex);
                Vertex split2 = new Vertex(intersectionName * 1000 + 2, intersectionVertex);
                allEdges = new TreeSet<>(sortedEdgesByVertex.getOrDefault(split1, new TreeSet<>()));
                allEdges.addAll(sortedEdgesByVertex.getOrDefault(split2, new TreeSet<>()));
            }
            
            if (allEdges == null || allEdges.isEmpty()) {
                logger.warn("No edges found for intersection vertex {}", intersectionName);
                continue;
            }
            
            // Фильтруем рёбра: разрешены те, что идут к соседям
            // НЕ в externalBoundary И (в sourceBoundary ИЛИ в sinkBoundary)
            List<EdgeOfGraph<Vertex>> allowedEdges = new ArrayList<>();
            for (EdgeOfGraph<Vertex> edge : allEdges) {
                logger.debug("Intersection {}: edge to {}", intersectionName, edge.end.getName());
                long targetName = edge.end.getName();
                long originalTargetName = targetName / 1000;
                
                boolean notInExternal = !externalBoundaryNames.contains(targetName) 
                        && !externalBoundaryNames.contains(originalTargetName);
                boolean bothVerticesInSourceOrSink = (
                        sourceBoundaryNames.contains(targetName)
                        || sourceBoundaryNames.contains(originalTargetName)
                )
                        && (sinkBoundaryNames.contains(targetName)
                        || sinkBoundaryNames.contains(originalTargetName)
                ) && (
                        sinkBoundaryNames.contains(intersectionName)
                        || sinkBoundaryNames.contains(intersectionName * 1000 + 1)
                        || sinkBoundaryNames.contains(intersectionName * 1000 + 2)
                        )
                    && (
                        sourceBoundaryNames.contains(intersectionName)
                        || sourceBoundaryNames.contains(intersectionName * 1000 + 1)
                        || sourceBoundaryNames.contains(intersectionName * 1000 + 2)
                        );
                boolean inSourceOrSink = sourceBoundaryNames.contains(targetName) 
                        || sourceBoundaryNames.contains(originalTargetName)
                        || sinkBoundaryNames.contains(targetName)
                        || sinkBoundaryNames.contains(originalTargetName);

                if ((notInExternal || bothVerticesInSourceOrSink) && inSourceOrSink) {
                    allowedEdges.add(edge);
                    logger.debug("Intersection {}: allowed edge to {}", intersectionName, targetName);
                }
            }
            
            allowedEdgesForCorner.put(intersectionName, allowedEdges);
            logger.debug("Intersection vertex {} has {} allowed edges", intersectionName, allowedEdges.size());
        }
        
        return new CornerConstraints(cornerVertices, allowedEdgesForCorner);
    }
    
    private Vertex findVertexByNameInList(List<Vertex> vertices, long name) {
        return vertices.stream()
                .filter(v -> v.getName() == name)
                .findFirst()
                .orElse(null);
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
            logger.warn("Invalid key vertices, no constraints");
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
            boolean isSourceCorner
    ) {
        Set<Vertex> externalBoundarySet = new HashSet<>(boundaries.externalBoundary);
        Set<Vertex> targetBoundarySet = new HashSet<>(isSourceCorner ? boundaries.sourceBoundary : boundaries.sinkBoundary);

        TreeSet<EdgeOfGraph<Vertex>> allEdges = sortedEdgesByVertex.get(corner);
        if (allEdges == null) {
            Vertex splittedCorner = new Vertex(corner.getName() * 1000 + (isFirstSide ? 1 : 2), corner);
            allEdges = sortedEdgesByVertex.get(splittedCorner);
            Vertex splittedCorner2 = new Vertex(corner.getName() * 1000 + (isFirstSide ? 2 : 1), corner);
            allEdges.addAll(sortedEdgesByVertex.get(splittedCorner2));
            if (allEdges == null || allEdges.isEmpty()) {
                logger.warn("allEdges are {}", allEdges == null ? "null" : "empty");
                return List.of();
            }
        }

        List<EdgeOfGraph<Vertex>> edgesList = new ArrayList<>(allEdges);

        // Находим индексы ключевых рёбер
        int externalBoundaryEdgeIdx = -1;
        int sourceSinkEdgeIdx = -1;

        for (int i = 0; i < edgesList.size(); i++) {
            EdgeOfGraph<Vertex> edge = edgesList.get(i);
            Vertex endVertex = edge.end;
            Vertex splittedEndVertex = new Vertex(endVertex.getName() / 1000, endVertex);

            // Ребро внешней границы (к другой boundary вершине)
            boolean isExternalBoundaryEdge = 
                (externalBoundarySet.contains(edge.end) && targetBoundarySet.contains(edge.end)) ||
                (externalBoundarySet.contains(splittedEndVertex) && targetBoundarySet.contains(splittedEndVertex));
            
            if (isExternalBoundaryEdge) {
                if (externalBoundaryEdgeIdx != -1) {
                    if (isSequentialOnExternalBoundary(edge, boundaries.externalBoundary, isSourceCorner, isFirstSide)) {
                        sourceSinkEdgeIdx = externalBoundaryEdgeIdx;
                        externalBoundaryEdgeIdx = i;
                    } else {
                        sourceSinkEdgeIdx = i;
                    }
                } else {
                    externalBoundaryEdgeIdx = i;
                }
            }

            // Ребро к source/sink boundary или к source vertex
            if ((!externalBoundarySet.contains(edge.end) && targetBoundarySet.contains(edge.end))
            || (!externalBoundarySet.contains(splittedEndVertex) && targetBoundarySet.contains(splittedEndVertex))) {
                sourceSinkEdgeIdx = i;
            }
        }

        if (externalBoundaryEdgeIdx == -1 || sourceSinkEdgeIdx == -1) {
            logger.warn("""
            Corner {} - no external boundary edge or source/sink edge,
            (externalBoundaryEdgeIdx = {}, sourceSinkEdgeIdx = {}), allow all edges""",
                    corner.getName(), externalBoundaryEdgeIdx, sourceSinkEdgeIdx);
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

        logger.debug("Corner {} (type={}, isFirstSide={}): allowed {}/{} edges (from idx {} to {})", 
                corner.getName(), isSourceCorner ? "SOURCE" : "SINK", isFirstSide, 
                allowedEdges.size(), edgesList.size(), startIdx, endIdx);

        return allowedEdges;
    }

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

    private boolean isSequentialOnExternalBoundary(
            EdgeOfGraph<Vertex> edge,
            List<Vertex> externalBoundary,
            boolean isSourceCorner,
            boolean isFirstSide) {

        int endVertexPos = findVertexPositionInBoundary(edge.end, externalBoundary);
        if (endVertexPos == -1) {
            return false;
        }

        int expectedNeighborOffset = calculateExpectedNeighborOffset(isSourceCorner, isFirstSide);
        
        int neighborPos = (endVertexPos + expectedNeighborOffset + externalBoundary.size()) % externalBoundary.size();
        Vertex expectedNeighbor = externalBoundary.get(neighborPos);

        return expectedNeighbor.equals(edge.begin);
    }

    private int findVertexPositionInBoundary(Vertex vertex, List<Vertex> boundary) {
        for (int i = 0; i < boundary.size(); i++) {
            if (boundary.get(i).equals(vertex)) {
                return i;
            }
        }
        return -1;
    }

    private int calculateExpectedNeighborOffset(boolean isSourceCorner, boolean isFirstSide) {
        return (isSourceCorner != isFirstSide) ? 1 : -1;
    }
}