package partitioning.splitting;

import java.util.*;
import java.util.stream.Collectors;

import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import partitioning.entities.NeighborSplit;

public class VertexSplitter {
    private static final Logger logger = LoggerFactory.getLogger(VertexSplitter.class);

    /**
     * Контекст для разделения вершины пути
     */
    private record PathVertexContext(
            Vertex current,
            Vertex previous,
            Vertex next,
            int index,
            boolean isFirst,
            boolean isLast,
            boolean isOnBothBoundaries
    ) {
        static PathVertexContext create(
                List<Vertex> path,
                int index,
                Set<Long> sourceBoundaryNames,
                Set<Long> sinkBoundaryNames) {

            Vertex current = path.get(index);
            Vertex previous = (index > 0) ? path.get(index - 1) : null;
            Vertex next = (index < path.size() - 1) ? path.get(index + 1) : null;

            boolean isFirst = (previous == null);
            boolean isLast = (next == null);
            boolean isOnBothBoundaries = sourceBoundaryNames.contains(current.getName())
                    && sinkBoundaryNames.contains(current.getName());

            return new PathVertexContext(
                    current, previous, next, index,
                    isFirst, isLast, isOnBothBoundaries);
        }
    }

    /**
     * Границы для preprocessing
     */
    private record BoundaryContext(
            List<Vertex> sourceBoundary,
            List<Vertex> sinkBoundary,
            Set<Long> sourceBoundaryNames,
            Set<Long> sinkBoundaryNames
    ) {
        static BoundaryContext create(List<Vertex> sourceBoundary, List<Vertex> sinkBoundary) {
            return new BoundaryContext(
                    sourceBoundary,
                    sinkBoundary,
                    extractNames(sourceBoundary),
                    extractNames(sinkBoundary)
            );
        }

        private static Set<Long> extractNames(List<Vertex> vertices) {
            return vertices.stream()
                    .map(Vertex::getName)
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Результат поиска соседей на границе
     */
    private record EdgeIndices(int prevIndex, int nextIndex) {
        boolean isValid() {
            return prevIndex != -1 && nextIndex != -1;
        }
    }

    /**
     * Препроцессинг разделений соседей для вершин пути
     */
    public static Map<Long, NeighborSplit> preprocessNeighborSplits(
            Graph<Vertex> graph,
            List<Vertex> path,
            List<Vertex> sourceBoundary,
            List<Vertex> sinkBoundary,
            List<Vertex> externalBoundary) {

        logger.debug("Preprocessing neighbor splits for {} vertices in path", path.size());

        BoundaryContext boundaryContext = BoundaryContext.create(sourceBoundary, sinkBoundary);
        Map<Vertex, java.util.TreeSet<EdgeOfGraph<Vertex>>> orderedEdges = graph.arrangeByAngle();
        Map<Long, NeighborSplit> splits = new HashMap<>();
        Set<Long> externalBoundaryNames = externalBoundary.stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());

        Map<Long, Boolean> boundaryFlagsMap = new HashMap<>(); // только для вершин на external boundary

        for (int i = 0; i < path.size(); i++) {
            PathVertexContext context = PathVertexContext.create(
                    path, i, boundaryContext.sourceBoundaryNames(),
                    boundaryContext.sinkBoundaryNames());

            boolean onExternalBoundary = externalBoundaryNames.contains(context.current().getName());
            logger.debug("Vertex {} on external boundary: {}", context.current().getName(), onExternalBoundary);

            if (onExternalBoundary) {
                Vertex prevPathVertex = context.previous(), nextPathVertex = context.next();

                Optional<Boolean> directionMatchesOpt = pathDirectionMatchesBoundaryTraversal(
                        context.current(), prevPathVertex, nextPathVertex, externalBoundary, sourceBoundary, sinkBoundary, orderedEdges);

                if (directionMatchesOpt.isPresent()) {
                    boundaryFlagsMap.put(context.current().getName(), directionMatchesOpt.get());

                    logger.debug("Vertex {} on external boundary, direction {}",
                            context.current().getName(), 
                            directionMatchesOpt.get() ? "matches" : "does not match");
                }
            }

            NeighborSplit split = processPathVertex(
                graph,
                context,
                path,
                boundaryContext,
                orderedEdges,
                externalBoundaryNames,
                boundaryFlagsMap
            );

            splits.put(context.current().getName(), split);
            //logSplitResult(context.current(), split);
        }

        logger.debug("Preprocessed {} neighbor splits", splits.size());
        return splits;
    }

    /**
     * Разделяет вершину на две
     */
    public static Map.Entry<Vertex, Vertex> splitVertex(
            Graph<Vertex> splitGraph,
            Vertex vertex,
            Map<Vertex, Vertex> splitToOriginalMap,
            Map<Long, NeighborSplit> neighborSplits) {

        NeighborSplit split = neighborSplits.get(vertex.getName());
        Optional<Boolean> boundaryFlags = (split != null) ? split.firstPartOnBoundary() : Optional.empty();

        Map.Entry<Vertex, Vertex> splitVertices = createSplitVertices(
                vertex, splitToOriginalMap, splitGraph, boundaryFlags);

        Vertex vertexInGraph = findVertexByName(splitGraph, vertex.getName());
        if (!isValidForSplitting(vertexInGraph, splitGraph)) {
            return splitVertices;
        }

        if (split != null) {
            connectSplitVertices(splitGraph, vertexInGraph, splitVertices, split);
        } else {
            logger.warn("No split found for vertex {}", vertex.getName());
        }

        splitGraph.deleteVertex(vertexInGraph);
        return splitVertices;
    }

    /**
     * Обрабатывает одну вершину пути
     */
    private static NeighborSplit processPathVertex(
            Graph<Vertex> graph,
            PathVertexContext context,
            List<Vertex> path,
            BoundaryContext boundaryContext,
            Map<Vertex, java.util.TreeSet<EdgeOfGraph<Vertex>>> orderedEdges,
            Set<Long> externalBoundaryNames,
            Map<Long, Boolean> boundaryFlagsMap) {

        Optional<Boolean> boundaryFlag = Optional.empty();
        if (externalBoundaryNames.contains(context.current().getName()) &&
                boundaryFlagsMap.containsKey(context.current().getName())) {
            boundaryFlag = Optional.of(boundaryFlagsMap.get(context.current().getName()));
        }

        // Специальный случай: вершина на пересечении границ
        if (shouldHandleAsIntersection(context, path)) {
            return handleBoundaryIntersectionVertex(
                    graph, context.current(),
                    boundaryContext.sourceBoundary(),
                    boundaryContext.sinkBoundary(),
                    boundaryFlag
            );
        }

        // Обычная обработка
        List<EdgeOfGraph<Vertex>> edges = orderedEdges.get(context.current()).stream().toList();

        NeighborLists neighbors = (!context.isFirst() && !context.isLast())
                ? splitNeighborsForMiddleVertex(context, edges)
                : splitNeighborsForEndVertex(graph, context, path, boundaryContext);

        return new NeighborSplit(
                context.current(),
                neighbors.pathNeighbors(),
                neighbors.leftNeighbors(),
                neighbors.rightNeighbors(),
                boundaryFlag
        );
    }

    /**
     * Проверяет нужно ли обрабатывать как intersection
     */
    private static boolean shouldHandleAsIntersection(PathVertexContext context, List<Vertex> path) {
        return context.isOnBothBoundaries() &&
                (context.isFirst() || context.isLast() || path.size() == 1);
    }

    /**
     * Списки соседей для разделения
     */
    private record NeighborLists(
            List<Vertex> pathNeighbors,
            List<Vertex> leftNeighbors,
            List<Vertex> rightNeighbors
    ) {
        static NeighborLists empty() {
            return new NeighborLists(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    /**
     * Разделяет соседей для вершины в середине пути
     */
    private static NeighborLists splitNeighborsForMiddleVertex(
            PathVertexContext context,
            List<EdgeOfGraph<Vertex>> curVertexEdges) {

        List<Vertex> pathNeighbors = List.of(context.previous(), context.next());
        EdgeIndices indices = findPathEdgeIndices(curVertexEdges, context.previous(), context.next());

        if (!indices.isValid()) {
            logger.warn("Could not find path edges for vertex {}", context.current().getName());
            return new NeighborLists(pathNeighbors, new ArrayList<>(), new ArrayList<>());
        }

        NeighborLists result = partitionNeighborsByAngle(
                curVertexEdges, indices, context.previous(), context.next());

        return new NeighborLists(pathNeighbors, result.leftNeighbors(), result.rightNeighbors());
    }

    /**
     * Находит индексы prev и next вершин в списке ребер
     */
    private static EdgeIndices findPathEdgeIndices(
            List<EdgeOfGraph<Vertex>> edges,
            Vertex prevVertex,
            Vertex nextVertex) {

        int prevIndex = -1;
        int nextIndex = -1;

        for (int j = 0; j < edges.size(); j++) {
            EdgeOfGraph<Vertex> edge = edges.get(j);
            if (edge.begin.equals(edge.end)) {
                continue;
            }

            if (edge.end.equals(prevVertex)) {
                prevIndex = j;
            }
            if (edge.end.equals(nextVertex)) {
                nextIndex = j;
            }
            if (prevIndex != -1 && nextIndex != -1) {
                break;
            }
        }

        return new EdgeIndices(prevIndex, nextIndex);
    }

    /**
     * Разделяет соседей по углу между prev и next
     */
    private static NeighborLists partitionNeighborsByAngle(
            List<EdgeOfGraph<Vertex>> edges,
            EdgeIndices indices,
            Vertex prevVertex,
            Vertex nextVertex) {

        List<Vertex> leftNeighbors = new ArrayList<>();
        List<Vertex> rightNeighbors = new ArrayList<>();

        int firstIdx = Math.min(indices.prevIndex(), indices.nextIndex());
        int secondIdx = Math.max(indices.prevIndex(), indices.nextIndex());
        boolean shouldSwapSides = (indices.prevIndex() > indices.nextIndex());

        for (int j = 0; j < edges.size(); j++) {
            EdgeOfGraph<Vertex> edge = edges.get(j);

            if (shouldSkipEdge(edge, prevVertex, nextVertex)) {
                continue;
            }

            boolean isBetween = (j > firstIdx && j < secondIdx);
            categorizeNeighbor(edge.end, isBetween, shouldSwapSides,
                               leftNeighbors, rightNeighbors);
        }

        return new NeighborLists(new ArrayList<>(), leftNeighbors, rightNeighbors);
    }

    /**
     * Проверяет нужно ли пропустить ребро
     */
    private static boolean shouldSkipEdge(
            EdgeOfGraph<Vertex> edge,
            Vertex prevVertex,
            Vertex nextVertex) {
        return edge.begin.equals(edge.end) ||
                edge.end.equals(prevVertex) ||
                edge.end.equals(nextVertex);
    }

    /**
     * Категоризирует соседа в left или right
     */
    private static void categorizeNeighbor(
            Vertex neighbor,
            boolean isBetween,
            boolean shouldSwapSides,
            List<Vertex> leftNeighbors,
            List<Vertex> rightNeighbors) {

        boolean addToLeft = shouldSwapSides ? isBetween : !isBetween;

        if (addToLeft) {
            leftNeighbors.add(neighbor);
        } else {
            rightNeighbors.add(neighbor);
        }
    }

    /**
     * Разделяет соседей для концевой вершины пути
     */
    private static NeighborLists splitNeighborsForEndVertex(
            Graph<Vertex> graph,
            PathVertexContext context,
            List<Vertex> path,
            BoundaryContext boundaryContext) {
        logger.debug("CURRENT TO SPLIT: {}", context.current.name);

        List<Vertex> boundaryToUse = context.isFirst()
                ? boundaryContext.sourceBoundary()
                : boundaryContext.sinkBoundary();

        logger.debug("BOUNDARY TO USE == SOURCE: {}", context.isFirst);

        List<Vertex> boundaryNeighbors = getBoundaryNeighbors(
                context.current(), boundaryToUse);

        validateBoundaryNeighbors(boundaryNeighbors);

        Set<Long> pathVertexNames = extractVertexNames(path);
        Set<Long> boundaryNames = extractVertexNames(boundaryNeighbors);

        var orderedEdges = graph.arrangeByAngle();
        var edgesList = orderedEdges.get(context.current()).stream().toList();

        int pathEdgeIdx = findPathEdgeIndex(edgesList, pathVertexNames);
        if (pathEdgeIdx == -1) {
            logger.warn("No path edge found for boundary vertex {}", context.current().getName());
            return NeighborLists.empty();
        }

        return partitionEndVertexNeighbors(
                edgesList, pathEdgeIdx, pathVertexNames, boundaryNames, context.current(), context.isFirst);
    }

    /**
     * Находит индекс ребра пути
     */
    private static int findPathEdgeIndex(
            List<EdgeOfGraph<Vertex>> edges,
            Set<Long> pathVertexNames) {

        for (int i = 0; i < edges.size(); i++) {
            if (pathVertexNames.contains(edges.get(i).end.getName())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Разделяет соседей концевой вершины
     */
    private static NeighborLists partitionEndVertexNeighbors(
            List<EdgeOfGraph<Vertex>> edges,
            int pathEdgeIdx,
            Set<Long> pathVertexNames,
            Set<Long> boundaryNames,
            Vertex currentVertex,
            boolean isSourceSide) {

        List<Vertex> pathNeighbors = new ArrayList<>();
        List<Vertex> leftNeighbors = new ArrayList<>();
        List<Vertex> rightNeighbors = new ArrayList<>();

        int n = edges.size();
        boolean inLeftRegion = isSourceSide;
        int boundaryEdgesCount = 0;

        for (int i = 1; i < n; i++) {
            int idx = (pathEdgeIdx + i) % n;
            EdgeOfGraph<Vertex> edge = edges.get(idx);
            Vertex neighbor = edge.end;

            if (neighbor.equals(currentVertex)) {
                continue;
            }

            // Нашли другой конец пути
            if (pathVertexNames.contains(neighbor.getName())) {
                pathNeighbors.add(neighbor);
                break;
            }

            // Обрабатываем граничное ребро
            if (boundaryNames.contains(neighbor.getName())) {
                handleBoundaryEdge(neighbor, inLeftRegion, leftNeighbors, rightNeighbors);
                boundaryEdgesCount++;
                if (boundaryEdgesCount == 1) {
                    inLeftRegion = !inLeftRegion;
                }
                continue;
            }

            // Обычный сосед
            if (inLeftRegion) {
                leftNeighbors.add(neighbor);
            } else {
                rightNeighbors.add(neighbor);
            }
        }

        logEndVertexResult(currentVertex, pathNeighbors, leftNeighbors, rightNeighbors);
        return new NeighborLists(pathNeighbors, leftNeighbors, rightNeighbors);
    }

    /**
     * Обрабатывает граничное ребро
     */
    private static void handleBoundaryEdge(
            Vertex neighbor,
            boolean inLeftRegion,
            List<Vertex> leftNeighbors,
            List<Vertex> rightNeighbors) {

        if (inLeftRegion) {
            leftNeighbors.add(neighbor);
        } else {
            rightNeighbors.add(neighbor);
        }
    }

    /**
     * Обрабатывает вершину на пересечении границ
     */
    private static NeighborSplit handleBoundaryIntersectionVertex(
            Graph<Vertex> graph,
            Vertex currentVertex,
            List<Vertex> sourceBoundary,
            List<Vertex> sinkBoundary,
            Optional<Boolean> boundaryFlag) {

        var angleOrderedEdges = graph.arrangeByAngle();

        logger.debug("handleBoundaryIntersectionVertex for vertex {}", currentVertex.getName());

        List<Vertex> leftNeighbors = new ArrayList<>();
        List<Vertex> rightNeighbors = new ArrayList<>();

        for (EdgeOfGraph<Vertex> edge : angleOrderedEdges.get(currentVertex)) {
            if (edge.begin.equals(edge.end)) {
                continue;
            }

            Vertex neighbor = edge.end;
            
            boolean toFirstPart = isNeighborToFirstPart(
                    currentVertex, neighbor, sourceBoundary, sinkBoundary, angleOrderedEdges);

            if (toFirstPart) {
                leftNeighbors.add(neighbor);
                logger.debug("  Neighbor {} -> LEFT", neighbor.getName());
            } else {
                rightNeighbors.add(neighbor);
                logger.debug("  Neighbor {} -> RIGHT", neighbor.getName());
            }
        }

        return new NeighborSplit(currentVertex, List.of(), leftNeighbors, rightNeighbors, boundaryFlag);
    }

    /**
     * Создает две split-вершины
     */
    private static Map.Entry<Vertex, Vertex> createSplitVertices(
            Vertex vertex,
            Map<Vertex, Vertex> splitToOriginalMap,
            Graph<Vertex> splitGraph,
            Optional<Boolean> boundaryFlags) {

        long originalName = vertex.getName();
        Vertex splitVertex1 = new Vertex(
                originalName * 1000 + 1, vertex.x, vertex.y, vertex.getWeight());
        Vertex splitVertex2 = new Vertex(
                originalName * 1000 + 2, vertex.x, vertex.y, vertex.getWeight());
        if (boundaryFlags.isPresent()) {
            boolean firstPartOnBoundary = boundaryFlags.get();
            splitVertex1.setIsOnBoundary(firstPartOnBoundary);
            splitVertex2.setIsOnBoundary(!firstPartOnBoundary);

            logger.debug("Split boundary vertex {} -> v1({}) onBoundary={}, v2({}) onBoundary={}", 
                    originalName, splitVertex1.getName(), firstPartOnBoundary, 
                    splitVertex2.getName(), !firstPartOnBoundary);
        }

        splitToOriginalMap.put(splitVertex1, vertex);
        splitToOriginalMap.put(splitVertex2, vertex);

        splitGraph.addVertex(splitVertex1);
        splitGraph.addVertex(splitVertex2);

        return new AbstractMap.SimpleEntry<>(splitVertex1, splitVertex2);
    }

    /**
     * Проверяет можно ли разделить вершину
     */
    private static boolean isValidForSplitting(Vertex vertex, Graph<Vertex> graph) {
        return vertex != null &&
                graph.getEdges().get(vertex) != null &&
                !graph.getEdges().get(vertex).isEmpty();
    }

    /**
     * Соединяет split-вершины с соседями
     */
    private static void connectSplitVertices(
            Graph<Vertex> splitGraph,
            Vertex originalVertex,
            Map.Entry<Vertex, Vertex> splitVertices,
            NeighborSplit split) {

        Vertex splitVertex1 = splitVertices.getKey();
        Vertex splitVertex2 = splitVertices.getValue();

        int connectedPathNeighbors = connectPathNeighbors(
                splitGraph, originalVertex, splitVertex1, splitVertex2, split);

        connectNeighborsToSplitVertex(splitGraph, originalVertex,
                                      splitVertex1, split.leftNeighbors());
        connectNeighborsToSplitVertex(splitGraph, originalVertex,
                                      splitVertex2, split.rightNeighbors());

        Assertions.assertEquals(split.pathNeighbors().size(), connectedPathNeighbors);
    }

    /**
     * Соединяет path neighbors с обеими split-вершинами
     */
    private static int connectPathNeighbors(
            Graph<Vertex> splitGraph,
            Vertex originalVertex,
            Vertex splitVertex1,
            Vertex splitVertex2,
            NeighborSplit split) {

        int count = 0;

        for (Vertex neighbor : split.pathNeighbors()) {
            connectSplitNeighbor(splitGraph, originalVertex,
                                 splitVertex1, splitVertex2, neighbor);
            count++;
        }

        return count;
    }

    /**
     * Соединяет с разделенным соседом
     */
    private static void connectSplitNeighbor(
            Graph<Vertex> splitGraph,
            Vertex originalVertex,
            Vertex splitVertex1,
            Vertex splitVertex2,
            Vertex neighbor) {

        Vertex splitNeighbor1 = findOrCreateVertex(splitGraph, neighbor.getName() * 1000 + 1, neighbor);
        Vertex splitNeighbor2 = findOrCreateVertex(splitGraph, neighbor.getName() * 1000 + 2, neighbor);

        double edgeLength = calculateDistance(originalVertex, splitNeighbor1);

        splitGraph.addEdge(splitVertex1, splitNeighbor1, edgeLength);
        splitGraph.addEdge(splitVertex2, splitNeighbor2, edgeLength);

        logger.debug("  Connected split vertices of {} with corresponding split versions of path neighbor {}",
                originalVertex.getName(), neighbor.getName());
    }

    /**
     * Соединяет соседей с одной split-вершиной
     */
    private static void connectNeighborsToSplitVertex(
            Graph<Vertex> splitGraph,
            Vertex originalVertex,
            Vertex splitVertex,
            List<Vertex> neighbors) {

        for (Vertex neighbor : neighbors) {
            Vertex neighborInGraph = findVertexByName(splitGraph, neighbor.getName());
            if (neighborInGraph != null) {
                double edgeLength = splitGraph.getEdges().get(originalVertex)
                        .get(neighborInGraph).length;
                splitGraph.addEdge(splitVertex, neighborInGraph, edgeLength);
            }
        }
    }

    /**
     * Находит соседей вершины на границе
     */
    private static List<Vertex> getBoundaryNeighbors(Vertex vertex, List<Vertex> boundary) {
        List<Vertex> neighbors = new ArrayList<>();

        for (int j = 0; j < boundary.size(); j++) {
            if (boundary.get(j).getName() == vertex.getName()) {
                neighbors.add(boundary.get((j + 1) % boundary.size()));
                neighbors.add(boundary.get((j - 1 + boundary.size()) % boundary.size()));
                break;
            }
        }

        return neighbors;
    }

    private static boolean isNeighborToFirstPart(
            Vertex current,
            Vertex neighbor,
            List<Vertex> sourceBoundary,
            List<Vertex> sinkBoundary,
            Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> orderedEdges) {

        TreeSet<EdgeOfGraph<Vertex>> edges = orderedEdges.get(current);
        if (edges == null || edges.isEmpty()) {
            logger.warn("No edges found for vertex {}", current.getName());
            return false;
        }

        // Находим рёбра на границах
        EdgeOfGraph<Vertex> sourceEdge = null;
        EdgeOfGraph<Vertex> sinkEdge = null;
        EdgeOfGraph<Vertex> neighborEdge = null;

        for (EdgeOfGraph<Vertex> edge : edges) {
            long targetId = edge.end.getName();
            
            if (targetId == neighbor.getName()) {
                neighborEdge = edge;
            }
            if (current.getName() == 1603311447L) {
                logger.warn("neighbor = {}, source size = {}, sink size = {}", neighbor.getName(), sourceBoundary.size(), sinkBoundary.size());
                logger.warn("Edge: {} -> {}, isEdgeCoDirectionalWithBoundary source = {}, sink = {}",
                        edge.begin.getName(),
                        edge.end.getName(),
                        isEdgeCoDirectionalWithBoundary(current, edge.end, sourceBoundary),
                        isEdgeCoDirectionalWithBoundary(current, edge.end, sinkBoundary)
                        );
            }
            
            // Проверяем, идёт ли это ребро на source boundary
            if (isEdgeCoDirectionalWithBoundary(current, edge.end, sourceBoundary)) {
                sourceEdge = edge;
            }
            
            // Проверяем, идёт ли это ребро на sink boundary
            if (isEdgeCoDirectionalWithBoundary(edge.end, current, sinkBoundary)) {
                sinkEdge = edge;
            }
        }

        if (neighborEdge == null) {
            logger.warn("Neighbor edge {} -> {} not found in ordered edges", 
                    current.getName(), neighbor.getName());
            return false;
        }

        if (isEdgeOnBoundary(current, neighbor, sourceBoundary)) {
            boolean isCoDirectional = isEdgeCoDirectionalWithBoundary(current, neighbor, sourceBoundary);
            logger.debug("Edge {} -> {} is on source boundary => !isCoDirectional: {}",
                    current.getName(), neighbor.getName(), !isCoDirectional);
            return !isCoDirectional;
        }

        if (isEdgeOnBoundary(current, neighbor, sinkBoundary)) {
            boolean isCoDirectional = isEdgeCoDirectionalWithBoundary(current, neighbor, sinkBoundary);
            logger.debug("Edge {} -> {} is on sink boundary => isCoDirectional: {}",
                    current.getName(), neighbor.getName(), isCoDirectional);
            return isCoDirectional;
        }

        // Если не на границах, используем секторный анализ
        if (sourceEdge != null && sinkEdge != null) {
            logger.info("check is edge {} -> {} between source and sink edges, source edge = {}, sink edge = {}",
                    current.getName(), neighbor.getName(), sourceEdge.end.getName(), sinkEdge.end.getName());
            boolean isBetween = isEdgeBetween(edges, neighborEdge, sourceEdge, sinkEdge);
            
            logger.debug("Edge {} -> {} is between source and sink edges: {} => {}",
                    current.getName(), neighbor.getName(), 
                    isBetween, isBetween ? "2" : "1");
            
            return !isBetween;
        }

        // Fallback: не смогли определить
        logger.warn("Could not determine side for edge {} -> {}, defaulting to LEFT", 
                current.getName(), neighbor.getName());
        return false;
    }

    /**
     * Проверяет, находится ли edge между sourceEdge и sinkEdge при обходе CCW
     */
    private static boolean isEdgeBetween(
            TreeSet<EdgeOfGraph<Vertex>> allEdges,
            EdgeOfGraph<Vertex> edge,
            EdgeOfGraph<Vertex> sourceEdge,
            EdgeOfGraph<Vertex> sinkEdge) {

        List<EdgeOfGraph<Vertex>> edgesList = new ArrayList<>(allEdges);
        
        int edgeIdx = edgesList.indexOf(edge);
        int sourceIdx = edgesList.indexOf(sourceEdge);
        int sinkIdx = edgesList.indexOf(sinkEdge);

        if (edgeIdx == -1 || sourceIdx == -1 || sinkIdx == -1) {
            return false;
        }

        // Проверяем, находится ли edge между source и sink при обходе CCW
        // CCW обход = от source к sink по возрастанию индексов (с wrap-around)
        int n = edgesList.size();
        
        // Нормализуем: считаем расстояние от sourceIdx
        int distToEdge = (edgeIdx - sourceIdx + n) % n;
        int distToSink = (sinkIdx - sourceIdx + n) % n;

        // edge между source и sink, если расстояние до edge меньше расстояния до sink
        return distToEdge > 0 && distToEdge < distToSink;
    }

    /**
     * Проверяет, находится ли ребро на границе (в любом направлении).
     * 
     * @param v1 первая вершина ребра
     * @param v2 вторая вершина ребра
     * @param boundary список вершин границы
     * @return true если ребро (v1, v2) или (v2, v1) присутствует на границе
     */
    private static boolean isEdgeOnBoundary(
            Vertex v1,
            Vertex v2,
            List<Vertex> boundary) {

        int boundarySize = boundary.size();
        for (int i = 0; i < boundarySize; i++) {
            Vertex currentInBoundary = boundary.get(i);
            Vertex nextInBoundary = boundary.get((i + 1) % boundarySize);

            // Проверяем ребро в обоих направлениях
            if ((currentInBoundary.getName() == v1.getName() && nextInBoundary.getName() == v2.getName()) ||
                (currentInBoundary.getName() == v2.getName() && nextInBoundary.getName() == v1.getName())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Проверяет, сонаправлено ли ребро (from -> to) с ребром на границе.
     * Граница идет ПРОТИВ часовой стрелки (CCW).
     * 
     * @return true если существует ребро (from -> to) на границе
     */
    private static boolean isEdgeCoDirectionalWithBoundary(
            Vertex from,
            Vertex to,
            List<Vertex> boundary) {

        int boundarySize = boundary.size();
        for (int i = 0; i < boundarySize; i++) {
            Vertex currentInBoundary = boundary.get(i);
            Vertex nextInBoundary = boundary.get((i + 1) % boundarySize);

            if (currentInBoundary.name == from.name && nextInBoundary.name == to.name) {
                return true;
            }
        }

        return false;
    }

    /**
     * Находит вершину по имени
     */
    private static Vertex findVertexByName(Graph<Vertex> graph, long name) {
        for (Vertex v : graph.verticesArray()) {
            if (v.getName() == name) {
                return v;
            }
        }
        return null;
    }

    /**
     * Находит или создает вершину по имени
     */
    private static Vertex findOrCreateVertex(Graph<Vertex> graph, long name, Vertex originalVertex) {
        for (Vertex v : graph.verticesArray()) {
            if (v.getName() == name) {
                return v;
            }
        }
        Vertex newVertex = new Vertex(name, originalVertex);
        graph.addVertex(newVertex);
        return newVertex;
    }

    /**
     * Извлекает имена вершин
     */
    private static Set<Long> extractVertexNames(List<Vertex> vertices) {
        return vertices.stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());
    }

    /**
     * Вычисляет расстояние между вершинами
     */
    private static double calculateDistance(Vertex v1, Vertex v2) {
        return Math.sqrt(
                Math.pow(v1.x - v2.x, 2) + Math.pow(v1.y - v2.y, 2)
        );
    }

    /**
     * Валидирует граничных соседей
     */
    private static void validateBoundaryNeighbors(List<Vertex> neighbors) {
        if (neighbors.size() < 2) {
            throw new RuntimeException(
                    "Expected >= 2 boundary neighbors, got: " + neighbors.size());
        }
    }

    /**
     * Определяет, совпадает ли направление пути с обходом external boundary.
     *
     * Геометрия (для CW обхода границы):
     * 1. В точке Current мы пришли от PrevBoundary.
     * 2. Внутренность полигона (Interior) находится "слева" от вектора (Current -> NextBoundary).
     * 3. Метод orderedEdges.higher() выполняет сканирование ПРОТИВ часовой стрелки (CCW/Left).
     * 4. Значит, сканируя от PrevBoundary CCW до NextBoundary, мы проходим внутренний сектор.
     *
     * Результат:
     * - Если оба ребра границы лежат МЕЖДУ рёбер пути -> путь снаружи -> FALSE.
     * - Иначе -> путь идет внутрь -> TRUE.
     */
    private static Optional<Boolean> pathDirectionMatchesBoundaryTraversal(
            Vertex currentVertex,
            Vertex prevPathVertex,
            Vertex nextPathVertex,
            List<Vertex> externalBoundary,
            List<Vertex> sourceBoundary,
            List<Vertex> sinkBoundary,
            Map<Vertex, java.util.TreeSet<EdgeOfGraph<Vertex>>> orderedEdges
    ) {
        if (prevPathVertex == null && nextPathVertex == null) {
            logger.debug("prevPathVertex and nextPathVertex are null");
            Set<Vertex> sourceBoundarySet = new HashSet<>(sourceBoundary);
            Set<Vertex> sinkBoundarySet = new HashSet<>(sinkBoundary);
            Set<Vertex> externalBoundarySet = new HashSet<>(externalBoundary);

            List<EdgeOfGraph<Vertex>> edgesList = new ArrayList<>(orderedEdges.get(currentVertex));
            int sourceEdgeIdx = -1, sinkEdgeIdx = -1;

            for (int i = 0; i < edgesList.size(); i++) {
                EdgeOfGraph<Vertex> edge = edgesList.get(i);
                if (externalBoundarySet.contains(edge.end) && sourceBoundarySet.contains(edge.end) && !sinkBoundarySet.contains(edge.end)) {
                    sourceEdgeIdx = i;
                }
                if (externalBoundarySet.contains(edge.end) && sinkBoundarySet.contains(edge.end) && !sourceBoundarySet.contains(edge.end)) {
                    sinkEdgeIdx = i;
                }
            }

            if (sourceEdgeIdx == -1 && sinkEdgeIdx == -1) {
                return Optional.empty();
            }

            if (sourceEdgeIdx == -1) {
                var sinkEdge = edgesList.get(sinkEdgeIdx);
                logger.debug("sinkEdge: {} -> {}", sinkEdge.begin.getName(), sinkEdge.end.getName());
                return Optional.of(isEdgeCoDirectionalWithBoundary(sinkEdge.begin, sinkEdge.end, externalBoundary));
            }

            if (sinkEdgeIdx == -1) {
                var sourceEdge = edgesList.get(sourceEdgeIdx);
                logger.debug("sourceEdge: {} -> {}", sourceEdge.begin.getName(), sourceEdge.end.getName());
                return Optional.of(!isEdgeCoDirectionalWithBoundary(sourceEdge.begin, sourceEdge.end, externalBoundary));
            }
            var sourceEdge = edgesList.get(sourceEdgeIdx);
            var sinkEdge = edgesList.get(sinkEdgeIdx);
            logger.debug("sourceEdge: {} -> {}, sinkEdge: {} -> {}", sourceEdge.begin.getName(), sourceEdge.end.getName(), sinkEdge.begin.getName(), sinkEdge.end.getName());

            int nextIdx = (sourceEdgeIdx + 1) % edgesList.size();
            logger.debug("sourceEdgeIdx: {}, sinkEdgeIdx: {}, nextIdx: {}", sourceEdgeIdx, sinkEdgeIdx, nextIdx);
            return Optional.of(sinkEdgeIdx != nextIdx);
        }

        int currentPosInBoundary = -1;
        for (int i = 0; i < externalBoundary.size(); i++) {
            if (externalBoundary.get(i).equals(currentVertex)) {
                currentPosInBoundary = i;
                break;
            }
        }

        if (currentPosInBoundary == -1) {
            logger.warn("vertex {} not found in external boundary", currentVertex.getName());
            return Optional.empty();
        }

        Vertex nextBoundaryVertex = externalBoundary.get((currentPosInBoundary + 1) % externalBoundary.size());
        Vertex prevBoundaryVertex = externalBoundary.get((currentPosInBoundary - 1 + externalBoundary.size()) % externalBoundary.size());

        if (prevPathVertex != null) {
            if (prevPathVertex.equals(nextBoundaryVertex)) {
                logger.debug("  Arrived from NextBoundary (Upstream) -> NOT match");
                return Optional.of(false);
            }
            if (prevPathVertex.equals(prevBoundaryVertex)) {
                logger.debug("  Arrived from PrevBoundary (Downstream) -> match");
                return Optional.of(true);
            }
        }

        if (nextPathVertex != null) {
            if (nextPathVertex.equals(nextBoundaryVertex)) {
                logger.debug("  Going forwards on boundary -> match");
                return Optional.of(true);
            }
            if (nextPathVertex.equals(prevBoundaryVertex)) {
                logger.debug("  Going backwards on boundary -> NOT match");
                return Optional.of(false);
            }
        }

        TreeSet<EdgeOfGraph<Vertex>> edges = orderedEdges.get(currentVertex);
        if (edges == null || edges.isEmpty()) {
            return Optional.empty();
        }

        EdgeOfGraph<Vertex> prevPathEdge = null;
        EdgeOfGraph<Vertex> nextPathEdge = null;
        EdgeOfGraph<Vertex> prevBoundaryEdge = null;
        EdgeOfGraph<Vertex> nextBoundaryEdge = null;

        for (EdgeOfGraph<Vertex> edge : edges) {
            if (edge.end.equals(prevPathVertex)) prevPathEdge = edge;
            if (edge.end.equals(nextPathVertex)) nextPathEdge = edge;
            if (edge.end.equals(prevBoundaryVertex)) prevBoundaryEdge = edge;
            if (edge.end.equals(nextBoundaryVertex)) nextBoundaryEdge = edge;
        }

        if (prevBoundaryEdge == null || nextBoundaryEdge == null) {
            logger.warn("boundary edges not found");
            return Optional.empty();
        }

        if (prevPathVertex == null && nextPathEdge != null) {
            boolean boundariesAfterPath = areCorrectSequence(
                    edges, nextPathEdge, nextBoundaryEdge, prevBoundaryEdge, sourceBoundary, false);

            logger.debug("Direction check for START vertex {}: nextPath={}, prevBoundary={}, nextBoundary={} -> boundaries after path edge: {} -> matches={}",
                    currentVertex.name, nextPathVertex.name, prevBoundaryVertex.name, nextBoundaryVertex.name, boundariesAfterPath, !boundariesAfterPath);

            return Optional.of(!boundariesAfterPath);
        }

        if (nextPathVertex == null && prevPathEdge != null) {
            boolean boundariesBeforePath = areCorrectSequence(
                    edges, prevPathEdge, prevBoundaryEdge, nextBoundaryEdge, sinkBoundary, true);

            logger.debug("Direction check for END vertex {}: prevPath={}, prevBoundary={}, nextBoundary={} -> boundaries after path edge: {} -> matches={}",
                    currentVertex.name, prevPathVertex.name, prevBoundaryVertex.name, nextBoundaryVertex.name, boundariesBeforePath, !boundariesBeforePath);

            return Optional.of(!boundariesBeforePath);
        }

        if (prevPathEdge != null && nextPathEdge != null) {
            boolean boundariesBetweenPath = areBothBoundariesBetweenPathEdges(
                    edges, prevPathEdge, nextPathEdge, prevBoundaryEdge, nextBoundaryEdge);

            logger.debug("Direction check for MIDDLE vertex {}: prevPath={}, nextPath={}, prevBoundary={}, nextBoundary={} -> boundaries between path edges: {} -> matches={}",
                    currentVertex.name, prevPathVertex.name, nextPathVertex.name, prevBoundaryVertex.name, nextBoundaryVertex.name, boundariesBetweenPath, !boundariesBetweenPath);

            return Optional.of(!boundariesBetweenPath);
        }

        logger.warn("unexpected case for vertex {}", currentVertex.name);
        return Optional.empty();
    }

    /**
     * Проверяет, лежат ли ОБА ребра границы между рёбрами пути при обходе CCW.
     * Идём от prevPathEdge до nextPathEdge (не включая их).
     */
    private static boolean areBothBoundariesBetweenPathEdges(
            TreeSet<EdgeOfGraph<Vertex>> edges,
            EdgeOfGraph<Vertex> prevPathEdge,
            EdgeOfGraph<Vertex> nextPathEdge,
            EdgeOfGraph<Vertex> prevBoundaryEdge,
            EdgeOfGraph<Vertex> nextBoundaryEdge) {

        EdgeOfGraph<Vertex> current = prevPathEdge;
        boolean foundPrevBoundary = false;
        boolean foundNextBoundary = false;

        int iterations = 0;
        int maxIterations = edges.size() + 1;

        while (iterations < maxIterations) {
            EdgeOfGraph<Vertex> next = edges.higher(current);
            if (next == null) next = edges.first();

            if (next.equals(nextPathEdge)) {
                // Дошли до второго ребра пути
                break;
            }

            if (next.equals(prevBoundaryEdge)) foundPrevBoundary = true;
            if (next.equals(nextBoundaryEdge)) foundNextBoundary = true;

            current = next;
            iterations++;
        }

        return foundPrevBoundary && foundNextBoundary;
    }

    private static boolean areCorrectSequence(
            TreeSet<EdgeOfGraph<Vertex>> edges,
            EdgeOfGraph<Vertex> startEdge,
            EdgeOfGraph<Vertex> firstBoundaryEdge,
            EdgeOfGraph<Vertex> secondBoundaryEdge,
            List<Vertex> boundaryVertices,
            boolean invertOrder) {

        EdgeOfGraph<Vertex> current = startEdge;
        boolean foundFirstEdge = false;

        int iterations = 0;
        int maxIterations = edges.size();

        while (iterations < maxIterations) {
            EdgeOfGraph<Vertex> next = invertOrder ? edges.lower(current) : edges.higher(current);
            if (next == null) next = edges.first();

            if (next.equals(startEdge)) {
                // Вернулись к началу
                break;
            }

            if (next.equals(secondBoundaryEdge) && !foundFirstEdge) return false;
            if (next.equals(firstBoundaryEdge)) return boundaryVertices.contains(next.end);

            current = next;
            iterations++;
        }

        return false;
    }

    private static void logSplitResult(Vertex vertex, NeighborSplit split) {
        logger.debug("Vertex {}: Path={} {}, Left={} {}, Right={} {}", 
                vertex.getName(), 
                split.pathNeighbors().size(), split.pathNeighbors().stream().map(Vertex::getName).toList(),
                split.leftNeighbors().size(), split.leftNeighbors().stream().map(Vertex::getName).toList(),
                split.rightNeighbors().size(), split.rightNeighbors().stream().map(Vertex::getName).toList());
    }

    private static void logEndVertexResult(
            Vertex vertex,
            List<Vertex> pathNeighbors,
            List<Vertex> leftNeighbors,
            List<Vertex> rightNeighbors) {

        logger.debug("End vertex {}: pathNeighbors={}, leftNeighbors={}, rightNeighbors={}", 
                vertex.getName(), pathNeighbors.size(), leftNeighbors.size(), rightNeighbors.size());
    }
}