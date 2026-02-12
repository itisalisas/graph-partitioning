package partitioning.maxflow.shortestpathtree;

import java.util.*;
import java.util.stream.Collectors;

import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;
import partitioning.models.SPTWithRegionWeights;

public class ShortestPathTreeSearcher {

    /**
     * Builds SPT (Shortest Path Tree) with region weights
     */
    public static SPTWithRegionWeights buildSPTWithRegionWeights(
            Graph<Vertex> graph,
            Map<Vertex, Vertex> previous,
            Vertex sourceVertex,
            List<Vertex> externalBoundary,
            Graph<VertexOfDualGraph> dualGraph,
            List<Vertex> sourceCorners,
            List<Vertex> sinkCorners,
            boolean isSourceSide) {

        Map<Vertex, List<Vertex>> children = buildAndSortChildrenMap(graph, previous);
        List<Vertex> boundaryVerticesInSPT = findBoundaryVerticesInSPT(
                graph, externalBoundary, previous);

        Map<Long, Integer> boundaryOrderMap = buildBoundaryOrderMap(externalBoundary);
        List<Vertex> relevantCorners = selectRelevantCorners(
                sourceCorners, sinkCorners, isSourceSide);

        int boundarySegmentStart = determineBoundarySegment(
                externalBoundary, boundaryVerticesInSPT, relevantCorners,
                boundaryOrderMap, isSourceSide);

        sortBoundaryLeavesBySegment(boundaryVerticesInSPT, boundaryOrderMap,
                                    boundarySegmentStart, externalBoundary.size());

        RegionWeightsResult weightsResult = computeRegionWeightsByEulerTour(
                sourceVertex, previous, boundaryVerticesInSPT, graph, dualGraph);

        return new SPTWithRegionWeights(
                new ArrayList<>(), new ArrayList<>(),
                sourceVertex, previous, children,
                boundaryVerticesInSPT,
                weightsResult.leftRegionWeights(),
                weightsResult.totalRegionWeight()
        );
    }

    private record RegionWeightsResult(
            Map<Vertex, Double> leftRegionWeights,
            double totalRegionWeight
    ) {}

    /**
     * Состояние для итеративного Euler Tour
     */
    private record EulerTourFrame(
            Vertex vertex,
            Vertex parent,
            int edgeIndex,
            boolean isReturning
    ) {}

    /**
     * Контекст для Euler Tour обхода
     */
    private static class EulerTourContext {
        final Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedEdgesByVertex;
        final Set<String> sptEdges;
        final Set<Long> sptVertexNames;
        final Set<Long> boundaryLeafNames;
        final Map<String, VertexOfDualGraph> edgeToLeftFace;
        final Set<Long> visitedFaces;
        final Map<Vertex, Double> leftRegionWeights;

        double cumulativeWeight;
        int currentLeafIndex;

        EulerTourContext(
                Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedEdgesByVertex,
                Set<String> sptEdges,
                Set<Long> sptVertexNames,
                Set<Long> boundaryLeafNames,
                Map<String, VertexOfDualGraph> edgeToLeftFace) {

            this.sortedEdgesByVertex = sortedEdgesByVertex;
            this.sptEdges = sptEdges;
            this.sptVertexNames = sptVertexNames;
            this.boundaryLeafNames = boundaryLeafNames;
            this.edgeToLeftFace = edgeToLeftFace;
            this.visitedFaces = new HashSet<>();
            this.leftRegionWeights = new HashMap<>();
            this.cumulativeWeight = 0.0;
            this.currentLeafIndex = 0;
        }
    }

    /**
     * Вычисляет веса регионов используя итеративный Euler tour
     */
    private static RegionWeightsResult computeRegionWeightsByEulerTour(
            Vertex root,
            Map<Vertex, Vertex> previous,
            List<Vertex> boundaryLeaves,
            Graph<Vertex> graph,
            Graph<VertexOfDualGraph> dualGraph) {

        if (boundaryLeaves.isEmpty()) {
            return new RegionWeightsResult(new HashMap<>(), 0.0);
        }

        EulerTourContext context = prepareEulerTourContext(
                root, previous, boundaryLeaves, graph, dualGraph);

        logEulerTourStart(context);
        eulerTourIterative(root, context);
        logEulerTourEnd(context);

        return new RegionWeightsResult(
                context.leftRegionWeights,
                context.cumulativeWeight);
    }

    /**
     * Подготавливает контекст для Euler tour
     */
    private static EulerTourContext prepareEulerTourContext(
            Vertex root,
            Map<Vertex, Vertex> previous,
            List<Vertex> boundaryLeaves,
            Graph<Vertex> graph,
            Graph<VertexOfDualGraph> dualGraph) {

        Set<String> sptEdges = buildSPTEdgesSet(previous);
        Map<String, VertexOfDualGraph> edgeToLeftFace = buildEdgeToLeftFaceMap(dualGraph);
        Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedEdgesByVertex = graph.arrangeByAngle();
        Set<Long> sptVertexNames = buildSPTVertexNames(root, previous);
        Set<Long> boundaryLeafNames = extractVertexNames(boundaryLeaves);

        return new EulerTourContext(
                sortedEdgesByVertex, sptEdges, sptVertexNames,
                boundaryLeafNames, edgeToLeftFace);
    }

    /**
     * ИТЕРАТИВНЫЙ Euler tour обход используя стек
     */
    private static void eulerTourIterative(Vertex root, EulerTourContext context) {
        Deque<EulerTourFrame> stack = new ArrayDeque<>();
        stack.push(new EulerTourFrame(root, null, 0, false));

        while (!stack.isEmpty()) {
            EulerTourFrame frame = stack.pop();

            if (frame.isReturning()) {
                // Возврат из рекурсии - обрабатываем boundary leaf
                handleBoundaryLeaf(frame.vertex(), context);
                continue;
            }

            TreeSet<EdgeOfGraph<Vertex>> edgesFromCurrent =
                    context.sortedEdgesByVertex.get(frame.vertex());

            if (edgesFromCurrent == null || edgesFromCurrent.isEmpty()) {
                handleBoundaryLeaf(frame.vertex(), context);
                continue;
            }

            // Добавляем фрейм для обработки после обхода детей
            stack.push(new EulerTourFrame(frame.vertex(), frame.parent(), 0, true));

            // Обрабатываем ребра в обратном порядке (т.к. стек)
            List<EdgeOfGraph<Vertex>> edges = getOrderedEdges(
                    edgesFromCurrent, frame.parent());

            for (int i = edges.size() - 1; i >= 0; i--) {
                EdgeOfGraph<Vertex> edge = edges.get(i);
                processEdgeIterative(frame, edge, stack, context);
            }
        }
    }

    /**
     * Обрабатывает одно ребро в итеративном обходе
     */
    private static void processEdgeIterative(
            EulerTourFrame currentFrame,
            EdgeOfGraph<Vertex> edge,
            Deque<EulerTourFrame> stack,
            EulerTourContext context) {

        Vertex neighbor = edge.end;

        // Пропускаем parent
        if (currentFrame.parent() != null &&
                neighbor.getName() == currentFrame.parent().getName()) {
            return;
        }

        String edgeKey = buildEdgeKey(currentFrame.vertex(), neighbor);
        boolean isTreeEdge = context.sptEdges.contains(edgeKey);
        boolean neighborInSPT = context.sptVertexNames.contains(neighbor.getName());

        if (isTreeEdge && neighborInSPT) {
            // Добавляем child в стек для обхода
            stack.push(new EulerTourFrame(neighbor, currentFrame.vertex(), 0, false));
        } else if (!isTreeEdge) {
            // Обрабатываем non-tree ребро
            processRightFace(currentFrame.vertex(), neighbor, context);
        }
    }

    /**
     * Получает упорядоченный список ребер начиная от parent
     */
    private static List<EdgeOfGraph<Vertex>> getOrderedEdges(
            TreeSet<EdgeOfGraph<Vertex>> edges,
            Vertex parent) {

        List<EdgeOfGraph<Vertex>> edgesList = new ArrayList<>(edges);
        int startIdx = findStartIndex(edges, parent);

        List<EdgeOfGraph<Vertex>> result = new ArrayList<>();
        for (int i = 0; i < edgesList.size(); i++) {
            result.add(edgesList.get((startIdx + i) % edgesList.size()));
        }
        return result;
    }

    /**
     * Обрабатывает boundary leaf вершину
     */
    private static void handleBoundaryLeaf(Vertex vertex, EulerTourContext context) {
        if (context.boundaryLeafNames.contains(vertex.getName())) {
            context.leftRegionWeights.put(vertex, context.cumulativeWeight);
            System.out.println("  Leaf " + context.currentLeafIndex +
                                       " (vertex " + vertex.getName() +
                                       "): cumulative=" + context.cumulativeWeight);
            context.currentLeafIndex++;
        }
    }

    /**
     * Обработка правой грани при обходе non-tree ребра
     */
    private static void processRightFace(
            Vertex current,
            Vertex neighbor,
            EulerTourContext context) {

        String reverseEdgeKey = buildEdgeKey(neighbor, current);
        VertexOfDualGraph rightFace = context.edgeToLeftFace.get(reverseEdgeKey);

        if (rightFace != null && !context.visitedFaces.contains(rightFace.getName())) {
            context.visitedFaces.add(rightFace.getName());
            context.cumulativeWeight += rightFace.getWeight();
        }
    }

    /**
     * Находит вершины границы, которые есть в SPT
     */
    private static List<Vertex> findBoundaryVerticesInSPT(
            Graph<Vertex> graph,
            List<Vertex> externalBoundary,
            Map<Vertex, Vertex> previous) {

        Set<Long> boundaryNames = extractVertexNames(externalBoundary);
        List<Vertex> result = new ArrayList<>();

        for (Vertex v : graph.verticesArray()) {
            if (boundaryNames.contains(v.getName()) && previous.containsKey(v)) {
                result.add(v);
            }
        }

        return result;
    }

    /**
     * Выбирает relevantCorners в зависимости от стороны
     */
    private static List<Vertex> selectRelevantCorners(
            List<Vertex> sourceCorners,
            List<Vertex> sinkCorners,
            boolean isSourceSide) {
        return isSourceSide ? sourceCorners : sinkCorners;
    }

    /**
     * Извлекает имена вершин в Set
     */
    private static Set<Long> extractVertexNames(List<Vertex> vertices) {
        return vertices.stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());
    }

    /**
     * Строит ключ ребра
     */
    private static String buildEdgeKey(Vertex v1, Vertex v2) {
        return v1.getName() + "_" + v2.getName();
    }

    private static void logEulerTourStart(EulerTourContext context) {
        System.out.println("SPT Region weights computation (Euler tour):");
        System.out.println("  SPT has " + context.sptEdges.size() / 2 + " edges");
        System.out.println("  SPT has " + context.sptVertexNames.size() + " vertices");
    }

    private static void logEulerTourEnd(EulerTourContext context) {
        System.out.println("  Total region weight: " + context.cumulativeWeight);
        System.out.println("  Visited faces: " + context.visitedFaces.size());
    }

    /**
     * Строит и сортирует map детей
     */
    private static Map<Vertex, List<Vertex>> buildAndSortChildrenMap(
            Graph<Vertex> graph,
            Map<Vertex, Vertex> previous) {

        Map<Vertex, List<Vertex>> children = initializeChildrenMap(graph);
        populateChildren(children, previous);
        sortChildrenByAngle(children, graph);

        return children;
    }

    /**
     * Инициализирует пустую map детей
     */
    private static Map<Vertex, List<Vertex>> initializeChildrenMap(Graph<Vertex> graph) {
        Map<Vertex, List<Vertex>> children = new HashMap<>();
        for (Vertex v : graph.verticesArray()) {
            children.put(v, new ArrayList<>());
        }
        return children;
    }

    /**
     * Заполняет map детей на основе previous
     */
    private static void populateChildren(
            Map<Vertex, List<Vertex>> children,
            Map<Vertex, Vertex> previous) {

        for (Map.Entry<Vertex, Vertex> entry : previous.entrySet()) {
            Vertex child = entry.getKey();
            Vertex parent = entry.getValue();
            if (parent != null && children.containsKey(parent)) {
                children.get(parent).add(child);
            }
        }
    }

    /**
     * Сортирует детей каждой вершины по углу
     */
    private static void sortChildrenByAngle(
            Map<Vertex, List<Vertex>> children,
            Graph<Vertex> graph) {

        Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> orderedEdges = graph.arrangeByAngle();

        for (Map.Entry<Vertex, List<Vertex>> entry : children.entrySet()) {
            Vertex vertex = entry.getKey();
            List<Vertex> childrenList = entry.getValue();

            if (shouldSkipSorting(childrenList, orderedEdges, vertex)) {
                continue;
            }

            sortChildrenList(childrenList, orderedEdges.get(vertex));
        }
    }

    /**
     * Проверяет нужно ли пропустить сортировку
     */
    private static boolean shouldSkipSorting(
            List<Vertex> childrenList,
            Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> orderedEdges,
            Vertex vertex) {
        return childrenList.size() <= 1 || !orderedEdges.containsKey(vertex);
    }

    /**
     * Сортирует список детей по углу
     */
    private static void sortChildrenList(
            List<Vertex> childrenList,
            TreeSet<EdgeOfGraph<Vertex>> angleOrderedEdges) {

        Map<Long, Integer> vertexToAngleIndex = buildVertexToAngleIndex(
                new ArrayList<>(angleOrderedEdges));

        childrenList.sort(Comparator.comparingInt(
                v -> vertexToAngleIndex.getOrDefault(v.getName(), 0)));
    }

    /**
     * Строит map: имя вершины -> индекс в упорядоченном по углу списке
     */
    private static Map<Long, Integer> buildVertexToAngleIndex(
            List<EdgeOfGraph<Vertex>> angleOrderedEdges) {

        Map<Long, Integer> vertexToAngleIndex = new HashMap<>();
        for (int i = 0; i < angleOrderedEdges.size(); i++) {
            vertexToAngleIndex.put(angleOrderedEdges.get(i).end.getName(), i);
        }
        return vertexToAngleIndex;
    }

    /**
     * Строит map: позиция на границе для каждой вершины
     */
    private static Map<Long, Integer> buildBoundaryOrderMap(List<Vertex> externalBoundary) {
        Map<Long, Integer> map = new HashMap<>();
        for (int i = 0; i < externalBoundary.size(); i++) {
            map.put(externalBoundary.get(i).getName(), i);
        }
        return map;
    }

    /**
     * Определяет начало сегмента границы
     */
    private static int determineBoundarySegment(
            List<Vertex> externalBoundary,
            List<Vertex> boundaryVerticesInSPT,
            List<Vertex> relevantCorners,
            Map<Long, Integer> boundaryOrderMap,
            boolean isSourceSide) {

        if (relevantCorners.size() < 2) {
            return 0;
        }

        List<Integer> cornerPositions = extractCornerPositions(
                relevantCorners, boundaryOrderMap);

        if (cornerPositions.size() < 2) {
            return 0;
        }

        return selectBoundarySegmentStart(
                cornerPositions, boundaryVerticesInSPT,
                boundaryOrderMap, isSourceSide);
    }

    /**
     * Выбирает начало сегмента на основе распределения вершин
     */
    private static int selectBoundarySegmentStart(
            List<Integer> cornerPositions,
            List<Vertex> boundaryVerticesInSPT,
            Map<Long, Integer> boundaryOrderMap,
            boolean isSourceSide) {

        Collections.sort(cornerPositions);
        int firstCornerPos = cornerPositions.get(0);
        int lastCornerPos = cornerPositions.get(cornerPositions.size() - 1);

        int countBetween = countVerticesBetweenCorners(
                boundaryVerticesInSPT, boundaryOrderMap,
                firstCornerPos, lastCornerPos);
        int countOutside = boundaryVerticesInSPT.size() - countBetween;

        int segmentStart = (countBetween >= countOutside) ? firstCornerPos : lastCornerPos;

        System.out.println("Boundary segment: corners at " + firstCornerPos +
                                   ", " + lastCornerPos +
                                   " -> using start=" + segmentStart +
                                   " (isSourceSide=" + isSourceSide + ")");

        return segmentStart;
    }

    /**
     * Извлекает позиции углов на границе
     */
    private static List<Integer> extractCornerPositions(
            List<Vertex> corners,
            Map<Long, Integer> boundaryOrderMap) {

        return corners.stream()
                .map(corner -> boundaryOrderMap.get(corner.getName()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Подсчитывает вершины между двумя углами
     */
    private static int countVerticesBetweenCorners(
            List<Vertex> vertices,
            Map<Long, Integer> boundaryOrderMap,
            int firstCorner,
            int lastCorner) {

        return (int) vertices.stream()
                .map(v -> boundaryOrderMap.getOrDefault(v.getName(), 0))
                .filter(pos -> pos >= firstCorner && pos <= lastCorner)
                .count();
    }

    /**
     * Сортирует boundary leaves по сегменту границы
     */
    private static void sortBoundaryLeavesBySegment(
            List<Vertex> boundaryVerticesInSPT,
            Map<Long, Integer> boundaryOrderMap,
            int boundarySegmentStart,
            int n) {

        boundaryVerticesInSPT.sort((a, b) -> {
            int posA = boundaryOrderMap.getOrDefault(a.getName(), 0);
            int posB = boundaryOrderMap.getOrDefault(b.getName(), 0);

            int adjA = (posA - boundarySegmentStart + n) % n;
            int adjB = (posB - boundarySegmentStart + n) % n;

            return Integer.compare(adjA, adjB);
        });

        System.out.println("Boundary leaves ordering: segmentStart=" +
                                   boundarySegmentStart +
                                   ", numLeaves=" + boundaryVerticesInSPT.size());
    }

    /**
     * Строит set всех ребер в SPT (в обе стороны)
     */
    private static Set<String> buildSPTEdgesSet(Map<Vertex, Vertex> previous) {
        Set<String> sptEdges = new HashSet<>();

        for (Map.Entry<Vertex, Vertex> entry : previous.entrySet()) {
            Vertex child = entry.getKey();
            Vertex parent = entry.getValue();

            if (parent != null) {
                sptEdges.add(buildEdgeKey(child, parent));
                sptEdges.add(buildEdgeKey(parent, child));
            }
        }

        return sptEdges;
    }

    /**
     * Строит set всех имен вершин в SPT
     */
    private static Set<Long> buildSPTVertexNames(Vertex root, Map<Vertex, Vertex> previous) {
        Set<Long> names = new HashSet<>();
        names.add(root.getName());
        names.addAll(previous.keySet().stream()
                             .map(Vertex::getName)
                             .collect(Collectors.toSet()));
        return names;
    }

    /**
     * Находит начальный индекс для обхода ребер (от parent)
     */
    private static int findStartIndex(
            TreeSet<EdgeOfGraph<Vertex>> edgesFromCurrent,
            Vertex parent) {

        if (parent == null) {
            return 0;
        }

        List<EdgeOfGraph<Vertex>> edgesList = new ArrayList<>(edgesFromCurrent);
        for (int i = 0; i < edgesList.size(); i++) {
            if (edgesList.get(i).end.getName() == parent.getName()) {
                return (i + 1) % edgesList.size();
            }
        }

        return 0;
    }

    /**
     * Строит map: ребро -> левая грань
     */
    private static Map<String, VertexOfDualGraph> buildEdgeToLeftFaceMap(
            Graph<VertexOfDualGraph> dualGraph) {

        Map<String, VertexOfDualGraph> edgeToLeftFace = new HashMap<>();

        for (VertexOfDualGraph face : dualGraph.verticesArray()) {
            addFaceEdgesToMap(face, edgeToLeftFace);
        }

        return edgeToLeftFace;
    }

    /**
     * Добавляет все ребра одной грани в map
     */
    private static void addFaceEdgesToMap(
            VertexOfDualGraph face,
            Map<String, VertexOfDualGraph> edgeToLeftFace) {

        List<Vertex> faceVertices = face.getVerticesOfFace();

        if (faceVertices == null || faceVertices.isEmpty()) {
            return;
        }

        for (int i = 0; i < faceVertices.size(); i++) {
            Vertex v1 = faceVertices.get(i);
            Vertex v2 = faceVertices.get((i + 1) % faceVertices.size());

            String edgeKey = buildEdgeKey(v1, v2);
            edgeToLeftFace.put(edgeKey, face);
        }
    }
}