package partitioning.splitting;

import java.util.*;
import java.util.stream.Collectors;

import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import org.junit.jupiter.api.Assertions;
import partitioning.entities.NeighborSplit;

public class VertexSplitter {

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

        System.out.println("Preprocessing neighbor splits for " + path.size() + " vertices in path");

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

            if (onExternalBoundary) {
                Vertex prevPathVertex = context.previous(), nextPathVertex = context.next();

                Optional<Boolean> directionMatchesOpt = pathDirectionMatchesBoundaryTraversal(
                        context.current(), prevPathVertex, nextPathVertex, externalBoundary, orderedEdges);

                if (directionMatchesOpt.isPresent()) {
                    boundaryFlagsMap.put(context.current().getName(), directionMatchesOpt.get());

                    System.out.println("Vertex " + context.current().getName() +
                                               " on external boundary, direction " +
                                               (directionMatchesOpt.get() ? "matches" : "does not match"));
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

        System.out.println("Preprocessed " + splits.size() + " neighbor splits");
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
            System.out.println("No split found for vertex " + vertex.getName());
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
            System.err.println("Warning: could not find path edges for vertex " +
                                       context.current().getName());
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
        System.out.println("CURRENT TO SPLIT: " + context.current.name);

        List<Vertex> boundaryToUse = context.isFirst()
                ? boundaryContext.sourceBoundary()
                : boundaryContext.sinkBoundary();

        System.out.println("BOUNDARY TO USE == SOURCE:" + context.isFirst);

        List<Vertex> boundaryNeighbors = getBoundaryNeighbors(
                context.current(), boundaryToUse);

        validateBoundaryNeighbors(boundaryNeighbors);

        Set<Long> pathVertexNames = extractVertexNames(path);
        Set<Long> boundaryNames = extractVertexNames(boundaryNeighbors);

        var orderedEdges = graph.arrangeByAngle();
        var edgesList = orderedEdges.get(context.current()).stream().toList();

        int pathEdgeIdx = findPathEdgeIndex(edgesList, pathVertexNames);
        if (pathEdgeIdx == -1) {
            System.out.println("WARNING: No path edge found for boundary vertex " +
                                       context.current().getName());
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
        List<EdgeOfGraph<Vertex>> neighbors = collectNonSelfLoopEdges(
                angleOrderedEdges.get(currentVertex));

        Set<Vertex> sourceBoundarySet = new HashSet<>(sourceBoundary);
        Set<Vertex> sinkBoundarySet = new HashSet<>(sinkBoundary);

        int boundaryNeighborsCount = countBoundaryNeighbors(
                neighbors, sourceBoundarySet, sinkBoundarySet);

        return switch (boundaryNeighborsCount) {
            case 2 -> handleTwoNeighborsCase(currentVertex, neighbors, boundaryFlag);
            case 3 -> handleThreeNeighborsCase(currentVertex, neighbors,
                                               sourceBoundary, sinkBoundary, boundaryFlag);
            default -> handleManyNeighborsCase(currentVertex, neighbors,
                                               sourceBoundary, sinkBoundary,
                                               angleOrderedEdges, boundaryFlag);
        };
    }

    /**
     * Собирает ребра, исключая self-loops
     */
    private static List<EdgeOfGraph<Vertex>> collectNonSelfLoopEdges(
            java.util.TreeSet<EdgeOfGraph<Vertex>> edges) {

        return edges.stream()
                .filter(edge -> !edge.begin.equals(edge.end))
                .collect(Collectors.toList());
    }

    /**
     * Подсчитывает соседей на границах
     */
    private static int countBoundaryNeighbors(
            List<EdgeOfGraph<Vertex>> neighbors,
            Set<Vertex> sourceBoundarySet,
            Set<Vertex> sinkBoundarySet) {

        return (int) neighbors.stream()
                .filter(edge -> sourceBoundarySet.contains(edge.end) ||
                        sinkBoundarySet.contains(edge.end))
                .count();
    }

    /**
     * Обрабатывает случай с 2 соседями
     */
    private static NeighborSplit handleTwoNeighborsCase(
            Vertex currentVertex,
            List<EdgeOfGraph<Vertex>> neighbors,
            Optional<Boolean> boundaryFlag) {

        return new NeighborSplit(
                currentVertex,
                List.of(),
                List.of(neighbors.get(0).end),
                List.of(neighbors.get(1).end),
                boundaryFlag
        );
    }

    /**
     * Обрабатывает случай с 3 соседями
     */
    private static NeighborSplit handleThreeNeighborsCase(
            Vertex currentVertex,
            List<EdgeOfGraph<Vertex>> neighbors,
            List<Vertex> sourceBoundary,
            List<Vertex> sinkBoundary,
            Optional<Boolean> boundaryFlag) {

        System.out.println("handleThreeNeighborsCase");

        List<Vertex> leftNeighbors = new ArrayList<>();
        List<Vertex> rightNeighbors = new ArrayList<>();

        for (EdgeOfGraph<Vertex> edge : neighbors) {
            boolean isCommonBoundaryVertex = sourceBoundary.contains(edge.end) &&
                    sinkBoundary.contains(edge.end);

            if (isCommonBoundaryVertex) {
                boolean onSinkBoundary = isAdjacentOnBoundary(
                        currentVertex, edge.end, sinkBoundary);
                boolean onSourceBoundary = isAdjacentOnBoundary(
                        currentVertex, edge.end, sourceBoundary);

                if (onSinkBoundary && onSourceBoundary) {
                    rightNeighbors.add(edge.end);
                } else {
                    leftNeighbors.add(edge.end);
                }
            } else {
                leftNeighbors.add(edge.end);
            }
        }

        if (rightNeighbors.isEmpty()) {
            throw new RuntimeException("no common neighbor");
        }

        return new NeighborSplit(currentVertex, List.of(), leftNeighbors, rightNeighbors, boundaryFlag);
    }

    /**
     * Состояние для обработки многих соседей
     */
    private static class ManyNeighborsState {
        int meetSink = 0;
        int meetSource = 0;
        int prevMeetSink = 0;
        int prevMeetSource = 0;
        boolean isRight = true;
        final List<Vertex> leftNeighbors = new ArrayList<>();
        final List<Vertex> rightNeighbors = new ArrayList<>();
    }

    /**
     * Обрабатывает случай с многими соседями
     */
    private static NeighborSplit handleManyNeighborsCase(
            Vertex currentVertex,
            List<EdgeOfGraph<Vertex>> neighbors,
            List<Vertex> sourceBoundary,
            List<Vertex> sinkBoundary,
            Map<Vertex, java.util.TreeSet<EdgeOfGraph<Vertex>>> angleOrderedEdges, Optional<Boolean> boundaryFlag) {

        System.out.println("split for " + currentVertex.name);

        ManyNeighborsState state = new ManyNeighborsState();

        for (EdgeOfGraph<Vertex> edge : angleOrderedEdges.get(currentVertex)) {
            if (edge.begin.equals(edge.end)) {
                continue;
            }

            processManyNeighborsEdge(edge, currentVertex, sourceBoundary,
                                     sinkBoundary, state);
        }

        return new NeighborSplit(currentVertex, List.of(),
                                 state.leftNeighbors, state.rightNeighbors, boundaryFlag);
    }

    /**
     * Обрабатывает одно ребро в случае многих соседей
     */
    private static void processManyNeighborsEdge(
            EdgeOfGraph<Vertex> edge,
            Vertex currentVertex,
            List<Vertex> sourceBoundary,
            List<Vertex> sinkBoundary,
            ManyNeighborsState state) {

        int newMeetSink = state.meetSink;
        int newMeetSource = state.meetSource;

        if (sinkBoundary.contains(edge.end) &&
                isAdjacentOnBoundary(currentVertex, edge.end, sinkBoundary)) {
            newMeetSink++;
        }

        if (sourceBoundary.contains(edge.end) &&
                isAdjacentOnBoundary(currentVertex, edge.end, sourceBoundary)) {
            newMeetSource++;
        }

        // Если счетчики не изменились
        if (state.meetSink == newMeetSink && state.meetSource == newMeetSource) {
            addNeighborWithoutModification(edge, state);
            return;
        }

        // Обновляем направление
        updateDirection(state, newMeetSink, newMeetSource);

        // Добавляем сосед с модификацией
        addNeighborWithModification(edge, state);
    }

    /**
     * Добавляет соседа без изменения направления
     */
    private static void addNeighborWithoutModification(
            EdgeOfGraph<Vertex> edge,
            ManyNeighborsState state) {

        if (state.isRight) {
            System.out.println("to right without modifications, vertex " + edge.end.name +
                                       " " + state.meetSource + " " + state.meetSink + " " +
                                       state.prevMeetSource + " " + state.prevMeetSink);
            state.rightNeighbors.add(edge.end);
        } else {
            System.out.println("to left without modifications, vertex " + edge.end.name +
                                       " " + state.meetSource + " " + state.meetSink + " " +
                                       state.prevMeetSource + " " + state.prevMeetSink);
            state.leftNeighbors.add(edge.end);
        }

        state.prevMeetSink = state.meetSink;
        state.prevMeetSource = state.meetSource;
    }

    /**
     * Обновляет направление разделения
     */
    private static void updateDirection(
            ManyNeighborsState state,
            int newMeetSink,
            int newMeetSource) {

        state.isRight = !shouldSwitchToLeft(
                state.meetSink, state.meetSource,
                state.prevMeetSink, state.prevMeetSource,
                newMeetSink, newMeetSource
        );

        state.prevMeetSink = state.meetSink;
        state.prevMeetSource = state.meetSource;
        state.meetSink = newMeetSink;
        state.meetSource = newMeetSource;
    }

    /**
     * Проверяет нужно ли переключиться налево
     */
    private static boolean shouldSwitchToLeft(
            int meetSink, int meetSource,
            int prevMeetSink, int prevMeetSource,
            int newMeetSink, int newMeetSource) {

        return (newMeetSink == 2 && (newMeetSource < 2 ||
                (prevMeetSource == 1 && prevMeetSink == 1)))
                || (newMeetSource == 2 && (newMeetSink < 2 ||
                (prevMeetSink == 1 && prevMeetSource == 1)))
                || (newMeetSource == 2 && newMeetSink == 2 &&
                meetSource == 1 && meetSink == 1);
    }

    /**
     * Добавляет соседа с модификацией направления
     */
    private static void addNeighborWithModification(
            EdgeOfGraph<Vertex> edge,
            ManyNeighborsState state) {

        if (state.isRight) {
            System.out.println("to right with modifications, vertex " + edge.end.name +
                                       " " + state.meetSource + " " + state.meetSink + " " +
                                       state.prevMeetSource + " " + state.prevMeetSink);
            state.rightNeighbors.add(edge.end);
        } else {
            System.out.println("to left with modifications, vertex " + edge.end.name +
                                       " " + state.meetSource + " " + state.meetSink + " " +
                                       state.prevMeetSource + " " + state.prevMeetSink);
            state.leftNeighbors.add(edge.end);
        }
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

            System.out.println("Split boundary vertex " + originalName +
                                       " -> v1(" + splitVertex1.getName() + ") onBoundary=" + firstPartOnBoundary +
                                       ", v2(" + splitVertex2.getName() + ") onBoundary=" + !firstPartOnBoundary);
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
            Vertex neighborInGraph = findVertexByName(splitGraph, neighbor.getName());

            if (neighborInGraph != null) {
                count++;
                connectOriginalNeighbor(splitGraph, originalVertex,
                                        splitVertex1, splitVertex2, neighborInGraph);
            } else {
                connectSplitNeighbor(splitGraph, originalVertex,
                                     splitVertex1, splitVertex2, neighbor);
                count++;
            }
        }

        return count;
    }

    /**
     * Соединяет с оригинальным соседом
     */
    private static void connectOriginalNeighbor(
            Graph<Vertex> splitGraph,
            Vertex originalVertex,
            Vertex splitVertex1,
            Vertex splitVertex2,
            Vertex neighborInGraph) {

        double edgeLength = splitGraph.getEdges().get(originalVertex)
                .get(neighborInGraph).length;
        splitGraph.addEdge(splitVertex1, neighborInGraph, edgeLength);
        splitGraph.addEdge(splitVertex2, neighborInGraph, edgeLength);
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

        Vertex splitNeighbor1 = findVertexByName(splitGraph, neighbor.getName() * 1000 + 1);
        Vertex splitNeighbor2 = findVertexByName(splitGraph, neighbor.getName() * 1000 + 2);

        if (splitNeighbor1 != null && splitNeighbor2 != null) {
            double edgeLength = calculateDistance(originalVertex, splitNeighbor1);

            splitGraph.addEdge(splitVertex1, splitNeighbor1, edgeLength);
            splitGraph.addEdge(splitVertex2, splitNeighbor2, edgeLength);

            System.out.println("  Connected split vertices of " + originalVertex.getName() +
                                       " with corresponding split versions of path neighbor " +
                                       neighbor.getName());
        } else {
            System.out.println("  WARNING: Path neighbor " + neighbor.getName() +
                                       " not found (neither original nor split) for vertex " +
                                       originalVertex.getName());
        }
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

    /**
     * Проверяет смежность вершин на границе
     */
    private static boolean isAdjacentOnBoundary(
            Vertex vertex,
            Vertex neighbor,
            List<Vertex> boundary) {

        int boundarySize = boundary.size();
        for (int ptr = 0; ptr < boundarySize; ptr++) {
            if (boundary.get(ptr).equals(neighbor)) {
                Vertex next = boundary.get((ptr + 1) % boundarySize);
                Vertex prev = boundary.get((ptr - 1 + boundarySize) % boundarySize);

                if (next.equals(vertex) || prev.equals(vertex)) {
                    return true;
                }
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
            Map<Vertex, java.util.TreeSet<EdgeOfGraph<Vertex>>> orderedEdges) {

        // 1. Проверки на null и конец пути
        if (prevPathVertex == null && nextPathVertex == null) {
            return Optional.of(true);
        }

        long currentId = currentVertex.getName();

        // 2. Найти позицию currentVertex в external boundary
        int currentPosInBoundary = -1;
        for (int i = 0; i < externalBoundary.size(); i++) {
            if (externalBoundary.get(i).getName() == currentId) {
                currentPosInBoundary = i;
                break;
            }
        }

        if (currentPosInBoundary == -1) {
            System.out.println("WARNING: vertex " + currentId + " not found in external boundary");
            return Optional.empty();
        }

        // 3. Получить соседей на boundary
        Vertex nextInBoundaryV = externalBoundary.get((currentPosInBoundary + 1) % externalBoundary.size());
        Vertex prevInBoundaryV = externalBoundary.get((currentPosInBoundary - 1 + externalBoundary.size()) % externalBoundary.size());

        long nextInBoundaryId = nextInBoundaryV.getName();
        long prevInBoundaryId = prevInBoundaryV.getName();

        // 4. --- ЯВНЫЕ ГРАНИЧНЫЕ СЛУЧАИ ---

        // A. Если мы пришли "Против шерсти" (со стороны NextBoundary)
        if (prevPathVertex != null) {
            long prevPathId = prevPathVertex.getName();
            if (prevPathId == nextInBoundaryId) {
                // Пытаемся идти навстречу потоку границы
                System.out.println("  Arrived from NextBoundary (Upstream) -> NOT match");
                return Optional.of(false);
            }
            if (prevPathId == prevInBoundaryId) {
                System.out.println("  Arrived from PrevBoundary (Downstream) -> match");
                return Optional.of(true);
            }
        }

        if (nextPathVertex != null) {
            long nextPathId = nextPathVertex.getName();

            // 1. Идем далее по границе -> верно
            if (nextPathId == nextInBoundaryId) {
                System.out.println("  Going forwards on boundary -> match");
                return Optional.of(true);
            }
            // 2. Разворачиваемся назад по границе -> неверно
            if (nextPathId == prevInBoundaryId) {
                System.out.println("  Going backwards on boundary -> NOT match");
                return Optional.of(false);
            }
        }

        // 5. --- СЕКТОРНЫЙ АНАЛИЗ ---

        TreeSet<EdgeOfGraph<Vertex>> edges = orderedEdges.get(currentVertex);
        if (edges == null || edges.isEmpty()) {
            return Optional.empty();
        }

        Long prevPathId = (prevPathVertex != null) ? prevPathVertex.getName() : null;
        Long nextPathId = (nextPathVertex != null) ? nextPathVertex.getName() : null;

        // Найдём все рёбра
        EdgeOfGraph<Vertex> prevPathEdge = null;
        EdgeOfGraph<Vertex> nextPathEdge = null;
        EdgeOfGraph<Vertex> prevBoundaryEdge = null;
        EdgeOfGraph<Vertex> nextBoundaryEdge = null;

        for (EdgeOfGraph<Vertex> edge : edges) {
            long targetId = edge.end.getName();
            if (prevPathId != null && targetId == prevPathId) prevPathEdge = edge;
            if (nextPathId != null && targetId == nextPathId) nextPathEdge = edge;
            if (targetId == prevInBoundaryId) prevBoundaryEdge = edge;
            if (targetId == nextInBoundaryId) nextBoundaryEdge = edge;
        }

        // Если не все нужные рёбра границы найдены
        if (prevBoundaryEdge == null || nextBoundaryEdge == null) {
            System.out.println("WARNING: boundary edges not found");
            return Optional.empty();
        }

        // 6. --- ПРОВЕРКА СЕКТОРОВ ---

        // Случай 1: Начало пути (prevPathVertex == null)
        if (prevPathVertex == null && nextPathEdge != null) {
            // Проверяем: оба ребра границы лежат между nextPathEdge и nextPathEdge (по кругу)?
            // Т.е. если идём от nextPathEdge CCW, встретим ли оба ребра границы до возврата к nextPathEdge?
            boolean boundariesAfterPath = areBothBoundariesAfterEdge(
                    edges, nextPathEdge, prevBoundaryEdge, nextBoundaryEdge);

            System.out.println("Direction check for START vertex " + currentId +
                    ": nextPath=" + nextPathId +
                    ", prevBoundary=" + prevInBoundaryId +
                    ", nextBoundary=" + nextInBoundaryId +
                    " -> boundaries after path edge: " + boundariesAfterPath +
                    " -> matches=" + !boundariesAfterPath);

            return Optional.of(!boundariesAfterPath);
        }

        // Случай 2: Конец пути (nextPathVertex == null)
        if (nextPathVertex == null && prevPathEdge != null) {
            // Проверяем: оба ребра границы лежат после prevPathEdge (по часовой, т.е. до prevPathEdge при обходе назад)?
            boolean boundariesBeforePath = areBothBoundariesAfterEdge(
                    edges, prevPathEdge, prevBoundaryEdge, nextBoundaryEdge);

            System.out.println("Direction check for END vertex " + currentId +
                    ": prevPath=" + prevPathId +
                    ", prevBoundary=" + prevInBoundaryId +
                    ", nextBoundary=" + nextInBoundaryId +
                    " -> boundaries after path edge: " + boundariesBeforePath +
                    " -> matches=" + !boundariesBeforePath);

            return Optional.of(!boundariesBeforePath);
        }

        // Случай 3: Середина пути (оба ребра пути присутствуют)
        if (prevPathEdge != null && nextPathEdge != null) {
            // Проверяем: оба ребра границы между рёбрами пути?
            boolean boundariesBetweenPath = areBothBoundariesBetweenPathEdges(
                    edges, prevPathEdge, nextPathEdge, prevBoundaryEdge, nextBoundaryEdge);

            System.out.println("Direction check for MIDDLE vertex " + currentId +
                    ": prevPath=" + prevPathId +
                    ", nextPath=" + nextPathId +
                    ", prevBoundary=" + prevInBoundaryId +
                    ", nextBoundary=" + nextInBoundaryId +
                    " -> boundaries between path edges: " + boundariesBetweenPath +
                    " -> matches=" + !boundariesBetweenPath);

            return Optional.of(!boundariesBetweenPath);
        }

        System.out.println("WARNING: unexpected case for vertex " + currentId);
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

    /**
     * Проверяет, лежат ли ОБА ребра границы после заданного ребра при обходе CCW.
     * Используется для начала и конца пути.
     * Идём от startEdge CCW и проверяем, встретим ли оба ребра границы до возврата к startEdge.
     */
    private static boolean areBothBoundariesAfterEdge(
            TreeSet<EdgeOfGraph<Vertex>> edges,
            EdgeOfGraph<Vertex> startEdge,
            EdgeOfGraph<Vertex> prevBoundaryEdge,
            EdgeOfGraph<Vertex> nextBoundaryEdge) {

        EdgeOfGraph<Vertex> current = startEdge;
        boolean foundPrevBoundary = false;
        boolean foundNextBoundary = false;

        int iterations = 0;
        int maxIterations = edges.size();

        while (iterations < maxIterations) {
            EdgeOfGraph<Vertex> next = edges.higher(current);
            if (next == null) next = edges.first();

            if (next.equals(startEdge)) {
                // Вернулись к началу
                break;
            }

            if (next.equals(prevBoundaryEdge)) foundPrevBoundary = true;
            if (next.equals(nextBoundaryEdge)) foundNextBoundary = true;

            current = next;
            iterations++;
        }

        return foundPrevBoundary && foundNextBoundary;
    }

    private static void logSplitResult(Vertex vertex, NeighborSplit split) {
        System.out.println("Vertex " + vertex.getName() +
                                   ": Path=" + split.pathNeighbors().size() + " " +
                                   split.pathNeighbors().stream().map(Vertex::getName).toList() +
                                   ", Left=" + split.leftNeighbors().size() +
                                   split.leftNeighbors().stream().map(Vertex::getName).toList() +
                                   ", Right=" + split.rightNeighbors().size() +
                                   split.rightNeighbors().stream().map(Vertex::getName).toList());
    }

    private static void logEndVertexResult(
            Vertex vertex,
            List<Vertex> pathNeighbors,
            List<Vertex> leftNeighbors,
            List<Vertex> rightNeighbors) {

        System.out.println("End vertex " + vertex.getName() +
                                   ": pathNeighbors=" + pathNeighbors.size() +
                                   ", leftNeighbors=" + leftNeighbors.size() +
                                   ", rightNeighbors=" + rightNeighbors.size());
    }
}