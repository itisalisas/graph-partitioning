package partitioning.shortestpathtree;

import java.util.*;

import graph.Edge;
import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;
import partitioning.entities.SPTWithRegionWeights;

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
            boolean isFirstSide
    ) {

        Set<Vertex> externalBoundarySet = new HashSet<>(externalBoundary);
        List<Vertex> boundaryVerticesInSPT = findBoundaryVerticesInSPT(
                graph, externalBoundarySet, previous);

        System.out.println("leaves: " + boundaryVerticesInSPT.size());

        Map<Long, Integer> boundaryOrderMap = buildBoundaryOrderMap(externalBoundary);
        TwoKeyVertices keyVertices = findTwoKeyVertices(
                sourceCorners, sinkCorners,
                boundaryOrderMap,
                externalBoundary.size(),
                isFirstSide);

        int boundarySegmentStart = determineSegmentStartFromVertices(
                keyVertices, boundaryOrderMap);

        sortBoundaryLeavesBySegment(boundaryVerticesInSPT, boundaryOrderMap,
                                    boundarySegmentStart, externalBoundary.size());

        RegionWeightsResult weightsResult = computeRegionWeightsByEulerTour(
                sourceVertex, previous, boundaryVerticesInSPT, graph, dualGraph);

        return new SPTWithRegionWeights(
                weightsResult.regions, weightsResult.weights,
                weightsResult.distances,
                sourceVertex, previous, null,
                boundaryVerticesInSPT,
                weightsResult.totalRegionWeight()
        );
    }

    private record RegionWeightsResult(
            List<VertexOfDualGraph> regions,
            List<Double> weights,
            List<Double> distances,
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
        final Set<Map.Entry<Vertex, Vertex>> sptEdges;
        final Set<Vertex> sptVertices;
        final Set<Vertex> boundaryLeaves;
        final Map<Vertex, HashMap<Vertex, VertexOfDualGraph>> edgeToLeftFace;
        final List<VertexOfDualGraph> regions;
        final List<Double> weights;
        final List<Double> distances;

        double cumulativeWeight;
        int currentLeafIndex;
        final Set<Map.Entry<Vertex, Vertex>> processedNonTreeEdges;
        final Set<VertexOfDualGraph> addedRegions;
        final Graph<VertexOfDualGraph> dualGraph;
        double currentBoundaryLength;

        EulerTourContext(
                Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedEdgesByVertex,
                Set<Map.Entry<Vertex, Vertex>> sptEdges,
                Set<Vertex> sptVertices,
                Set<Vertex> boundaryLeaves,
                Map<Vertex, HashMap<Vertex, VertexOfDualGraph>> edgeToLeftFace,
                Graph<VertexOfDualGraph> dualGraph) {

            this.sortedEdgesByVertex = sortedEdgesByVertex;
            this.sptEdges = sptEdges;
            this.sptVertices = sptVertices;
            this.boundaryLeaves = boundaryLeaves;
            this.edgeToLeftFace = edgeToLeftFace;
            this.regions = new ArrayList<>();
            this.weights = new ArrayList<>();
            this.distances = new ArrayList<>();
            this.cumulativeWeight = 0.0;
            this.currentLeafIndex = 0;
            this.processedNonTreeEdges = new HashSet<>();
            this.addedRegions = new HashSet<>();
            this.dualGraph = dualGraph;
            this.currentBoundaryLength = 0.0;
        }
    }

    /**
     * Две ключевых вершины, ограничивающих сегмент external boundary
     */
    private record TwoKeyVertices(
            Vertex sourceVertex,   // на стороне source
            Vertex sinkVertex      // на стороне sink
    ) {
        boolean isValid() {
            return sourceVertex != null && sinkVertex != null;
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
            return new RegionWeightsResult(List.of(), List.of(), List.of(), 0);
        }

        EulerTourContext context = prepareEulerTourContext(
                root, previous, boundaryLeaves, graph, dualGraph);

        logEulerTourStart(context);
        eulerTourIterative(root, context);
        logEulerTourEnd(context);

        return new RegionWeightsResult(
                context.regions,
                context.weights,
                context.distances,
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

        Set<Map.Entry<Vertex, Vertex>> sptEdges = buildSPTEdgesSet(previous);
        Map<Vertex, HashMap<Vertex, VertexOfDualGraph>> edgeToLeftFace = dualGraph.edgeToDualVertexMap();
        Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedEdgesByVertex = graph.arrangeByAngle();
        Set<Vertex> sptVertices = buildSPTVerticesSet(root, previous);

        return new EulerTourContext(
                sortedEdgesByVertex, sptEdges, sptVertices,
                new HashSet<>(boundaryLeaves), edgeToLeftFace, dualGraph);
    }

    /**
     * ИТЕРАТИВНЫЙ Euler tour обход используя стек
     */
    private static void eulerTourIterative(Vertex root, EulerTourContext context) {
        Deque<EulerTourFrame> stack = new ArrayDeque<>();
        stack.push(new EulerTourFrame(root, null, 0, false));

        while (!stack.isEmpty()) {
            EulerTourFrame frame = stack.pop();
            //System.out.println("Processing vertex: " + frame.vertex().getName());
            //System.out.println("Parent: " + (frame.parent() != null ? frame.parent().getName() : "null"));

            if (frame.isReturning()) {
                // Возврат из рекурсии - обрабатываем boundary leaf
                handleBoundaryLeaf(frame.vertex(), context);
                continue;
            }

            TreeSet<EdgeOfGraph<Vertex>> edgesFromCurrent =
                    context.sortedEdgesByVertex.get(frame.vertex());


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
        //System.out.println("Processing edge: " + edge.begin.getName() + " - " + edge.end.getName());

        // Пропускаем parent
        if (currentFrame.parent() != null &&
                neighbor.getName() == currentFrame.parent().getName()) {
            //System.out.println("Skipping parent: " + currentFrame.parent().getName());
            return;
        }

        boolean isTreeEdge = context.sptEdges.contains(Map.entry(currentFrame.vertex(), neighbor));

        if (isTreeEdge) {
            // Добавляем child в стек для обхода
            //System.out.println("Pushing child: " + neighbor.getName());
            stack.push(new EulerTourFrame(neighbor, currentFrame.vertex(), 0, false));
        } else {
            // Проверяем, не обработали ли мы это ребро уже
            Map.Entry<Vertex, Vertex> edgeKey1 = Map.entry(currentFrame.vertex(), neighbor);
            Map.Entry<Vertex, Vertex> edgeKey2 = Map.entry(neighbor, currentFrame.vertex());

            if (!context.processedNonTreeEdges.contains(edgeKey1) &&
                    !context.processedNonTreeEdges.contains(edgeKey2)) {

                //System.out.println("Processing non-tree edge: " + edge.begin.getName() + " - " + edge.end.getName());
                // Обрабатываем non-tree ребро
                processRightFace(currentFrame.vertex(), neighbor, context);

                // Отмечаем ребро как обработанное
                context.processedNonTreeEdges.add(edgeKey1);
            } else {
                //System.out.println("Skipping already processed non-tree edge: " + edge.begin.getName() + " - " + edge.end.getName());
            }
        }
        // TODO переход на другое ребро внешней границы
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
        if (context.boundaryLeaves.contains(vertex)) {
            //System.out.println("  Leaf " + context.currentLeafIndex +
            //                           " (vertex " + vertex.getName() +
            //                           "): cumulative=" + context.cumulativeWeight);
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

        VertexOfDualGraph rightFace = null;

        if (context.edgeToLeftFace.containsKey(neighbor)) {
            rightFace = context.edgeToLeftFace.get(neighbor).get(current);
        }
        Vertex newNeighbor = new Vertex(neighbor.name / 1000, neighbor.x, neighbor.y);
        Vertex newCurrent = new Vertex(current.name / 1000, current.x, current.y);
        if (rightFace == null && context.edgeToLeftFace.containsKey(newNeighbor)) {
            rightFace = context.edgeToLeftFace.get(newNeighbor).get(current);
        }
        else if (rightFace == null && context.edgeToLeftFace.containsKey(neighbor)) {
            rightFace = context.edgeToLeftFace.get(neighbor).get(newCurrent);
        }
        else if (rightFace == null && context.edgeToLeftFace.containsKey(newNeighbor)) {
            rightFace = context.edgeToLeftFace.get(newNeighbor).get(newCurrent);
        }
        if (rightFace != null) {
            context.cumulativeWeight += rightFace.getWeight();
            context.weights.add(context.cumulativeWeight);
            context.regions.add(rightFace);
            
            // Инкрементально обновляем длину границы
            double boundaryLength = updateBoundaryLength(rightFace, context);
            context.distances.add(boundaryLength);
            
            // Добавляем регион в множество (ВАЖНО: после вычисления!)
            context.addedRegions.add(rightFace);
            
            //System.out.println("    Region " + rightFace.getName() +
            //                   " added: weight=" + rightFace.getWeight() +
            //                   ", boundary_length=" + String.format("%.2f", boundaryLength));
        } else {
            //var c = new CoordinateConversion();
            //var cc = c.fromEuclidean(current);
            //System.out.println("cant find face: " + current.name + " " + cc.x + " " + cc.y + " " + neighbor.name);
        }
    }

    /**
     * Находит вершины границы, которые есть в SPT
     */
    private static List<Vertex> findBoundaryVerticesInSPT(
            Graph<Vertex> graph,
            Set<Vertex> externalBoundarySet,
            Map<Vertex, Vertex> previous) {

        List<Vertex> result = new ArrayList<>();

        for (Vertex v : graph.verticesArray()) {
            if (externalBoundarySet.contains(v) && previous.containsKey(v)) {
                result.add(v);
            }
        }

        return result;
    }

    /**
     * Находит два ключевых угла (source и sink), ограничивающих нужный сегмент external boundary
     * Логика: находим последнюю source vertex и первую sink vertex (или наоборот)
     * в зависимости от того, какая сторона нам нужна
     */
    private static TwoKeyVertices findTwoKeyVertices(
            List<Vertex> sourceIntersections,
            List<Vertex> sinkIntersections,
            Map<Long, Integer> boundaryOrderMap,
            int boundarySize,
            boolean isFirstSide) {

        if (sourceIntersections.isEmpty() || sinkIntersections.isEmpty()) {
            System.out.println("WARNING: No intersections found");
            return new TwoKeyVertices(null, null);
        }

        // Находим позиции всех source corners
        List<Integer> sourcePositions = sourceIntersections.stream()
                .map(v -> boundaryOrderMap.getOrDefault(v.getName(), -1))
                .filter(pos -> pos >= 0)
                .sorted()
                .toList();

        // Находим позиции всех sink corners
        List<Integer> sinkPositions = sinkIntersections.stream()
                .map(v -> boundaryOrderMap.getOrDefault(v.getName(), -1))
                .filter(pos -> pos >= 0)
                .sorted()
                .toList();

        if (sourcePositions.isEmpty() || sinkPositions.isEmpty()) {
            System.out.println("WARNING: No valid corner positions");
            return new TwoKeyVertices(null, null);
        }

        // Найти "переход" от source к sink на external boundary
        // Это пара (source vertex, sink vertex) с минимальным расстоянием между ними
        Vertex bestSourceCorner = null;
        Vertex bestSinkCorner = null;
        int minDistance = boundarySize;

        for (Vertex sCorner : sourceIntersections) {
            int sPos = boundaryOrderMap.getOrDefault(sCorner.getName(), -1);
            if (sPos < 0) continue;

            for (Vertex tCorner : sinkIntersections) {
                int tPos = boundaryOrderMap.getOrDefault(tCorner.getName(), -1);
                if (tPos < 0) continue;

                // Расстояние по часовой стрелке от source к sink
                int distance = (tPos - sPos + boundarySize) % boundarySize;

                if (distance > 0 && distance < minDistance) {
                    minDistance = distance;
                    bestSourceCorner = sCorner;
                    bestSinkCorner = tCorner;
                }
            }
        }

        // Для другой стороны (isSourceSide=false) ищем противоположную пару
        if (!isFirstSide) {
            // Ищем максимальное расстояние (другой сегмент)
            Vertex altSourceCorner = null;
            Vertex altSinkCorner = null;
            int maxDistance = 0;

            for (Vertex sCorner : sourceIntersections) {
                int sPos = boundaryOrderMap.getOrDefault(sCorner.getName(), -1);
                if (sPos < 0) continue;

                for (Vertex tCorner : sinkIntersections) {
                    int tPos = boundaryOrderMap.getOrDefault(tCorner.getName(), -1);
                    if (tPos < 0) continue;

                    // Расстояние по часовой стрелке от sink к source (обратный путь)
                    int distance = (sPos - tPos + boundarySize) % boundarySize;

                    if (distance > maxDistance) {
                        maxDistance = distance;
                        altSinkCorner = tCorner;
                        altSourceCorner = sCorner;
                    }
                }
            }

            if (altSourceCorner != null && altSinkCorner != null) {
                bestSourceCorner = altSourceCorner;
                bestSinkCorner = altSinkCorner;
            }
        }

        System.out.println("Selected key corners: source=" +
                (bestSourceCorner != null ? bestSourceCorner.getName() : "null") +
                ", sink=" +
                (bestSinkCorner != null ? bestSinkCorner.getName() : "null") +
                " (isSourceSide=" + isFirstSide + ")");

        return new TwoKeyVertices(bestSourceCorner, bestSinkCorner);
    }

    /**
     * Определяет начало сегмента на основе двух ключевых углов
     */
    private static int determineSegmentStartFromVertices(
            TwoKeyVertices corners,
            Map<Long, Integer> boundaryOrderMap) {

        if (!corners.isValid()) {
            System.out.println("WARNING: Invalid corners, using position 0");
            return 0;
        }

        int sourcePos = boundaryOrderMap.getOrDefault(corners.sourceVertex.getName(), 0);
        int sinkPos = boundaryOrderMap.getOrDefault(corners.sinkVertex.getName(), 0);

        // Начинаем с source corner (левый край сегмента)
        int segmentStart = sourcePos;

        System.out.println("Boundary segment: from source corner at " + sourcePos + " (name = " + corners.sourceVertex.name +
                ") to sink corner at " + sinkPos + " (name = " + corners.sinkVertex.name +
                ") -> segmentStart=" + segmentStart);

        return segmentStart;
    }

    private static void logEulerTourStart(EulerTourContext context) {
        System.out.println("SPT Region weights computation (Euler tour):");
        System.out.println("  SPT has " + context.sptEdges.size() / 2 + " edges");
        System.out.println("  SPT has " + context.sptVertices.size() + " vertices");
    }

    private static void logEulerTourEnd(EulerTourContext context) {
        System.out.println("  Total region weight: " + context.cumulativeWeight);
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
    private static Set<Map.Entry<Vertex, Vertex>> buildSPTEdgesSet(Map<Vertex, Vertex> previous) {
        Set<Map.Entry<Vertex, Vertex>> sptEdges = new HashSet<>();

        for (Map.Entry<Vertex, Vertex> entry : previous.entrySet()) {
            Vertex child = entry.getKey();
            Vertex parent = entry.getValue();

            if (parent != null) {
                sptEdges.add(Map.entry(child, parent));
                sptEdges.add(Map.entry(parent, child));
            }
        }

        return sptEdges;
    }

    /**
     * Строит set всех вершин в SPT
     */
    private static Set<Vertex> buildSPTVerticesSet(Vertex root, Map<Vertex, Vertex> previous) {
        Set<Vertex> vertices = new HashSet<>();
        vertices.add(root);
        vertices.addAll(new HashSet<>(previous.keySet()));
        return vertices;
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

    private static double updateBoundaryLength(
            VertexOfDualGraph newRegion,
            EulerTourContext context) {

        Map<VertexOfDualGraph, Edge> neighbors = context.dualGraph.getEdges().get(newRegion);

        if (neighbors == null) {
            return context.currentBoundaryLength;
        }

        double delta = 0.0;

        for (Map.Entry<VertexOfDualGraph, Edge> entry : neighbors.entrySet()) {
            VertexOfDualGraph neighborFace = entry.getKey();
            Edge edge = entry.getValue();
            double edgeLength = edge.length;

            if (context.addedRegions.contains(neighborFace)) {
                delta -= edgeLength;
            } else {
                delta += edgeLength;
            }
        }

        context.currentBoundaryLength += delta;
        return context.currentBoundaryLength;
    }
}