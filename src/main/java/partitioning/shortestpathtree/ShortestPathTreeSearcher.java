package partitioning.shortestpathtree;

import java.util.*;

import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import partitioning.entities.SPTWithRegionWeights;
import partitioning.maxflow.CornerConstraints;
import readWrite.CoordinateConversion;

public class ShortestPathTreeSearcher {
    private static final Logger logger = LoggerFactory.getLogger(ShortestPathTreeSearcher.class);

    public static SPTWithRegionWeights buildSPTWithRegionWeights(
            Graph<Vertex> graph,
            Map<Vertex, Vertex> previous,
            Vertex sourceVertex,
            List<Vertex> externalBoundary,
            Graph<VertexOfDualGraph> dualGraph,
            boolean isFirstSide,
            List<Vertex> path
    ) {

        Set<Vertex> externalBoundarySet = new HashSet<>(externalBoundary);
        List<Vertex> boundaryVerticesInSPT = findBoundaryVerticesInSPT(externalBoundarySet, previous);

        RegionWeightsResult weightsResult = computeRegionWeightsByEulerTour(
                sourceVertex, previous, boundaryVerticesInSPT, graph, dualGraph, path, isFirstSide
        );

        if (logger.isDebugEnabled()) {
            StringBuilder indices = new StringBuilder("INDICES: ");
            for (int i = 0; i < weightsResult.leafIndices.size(); i++) {
                indices.append(weightsResult.tourOrderLeaves.get(i).name).append(": ")
                       .append(weightsResult.leafIndices.get(i)).append(". ");
            }
            logger.debug(indices.toString());
        }

        return new SPTWithRegionWeights(
                weightsResult.regions, weightsResult.weights,
                weightsResult.distances,
                sourceVertex, previous, null,
                weightsResult.tourOrderLeaves,
                weightsResult.leafIndices,
                weightsResult.totalRegionWeight()
        );
    }

    private record RegionWeightsResult(
            List<VertexOfDualGraph> regions,
            List<Double> weights,
            List<Double> distances,
            List<Integer> leafIndices,
            List<Vertex> tourOrderLeaves,
            double totalRegionWeight
    ) {}

    /**
     * Each frame holds the ordered edge list and a mutable index tracking
     * which edge to process next, so non-tree edges are interleaved correctly
     * with child subtree visits.
     */
    private static class EulerTourFrame {
        final Vertex vertex;
        final Vertex parent;
        final List<EdgeOfGraph<Vertex>> edges;
        int nextEdgeIdx;
        boolean enteredFromBoundary;

        EulerTourFrame(Vertex vertex, Vertex parent, List<EdgeOfGraph<Vertex>> edges, boolean enteredFromBoundary) {
            this.vertex = vertex;
            this.parent = parent;
            this.edges = edges;
            this.nextEdgeIdx = 0;
            this.enteredFromBoundary = enteredFromBoundary;
        }
    }

    /**
     * Контекст для Euler Tour обхода
     */
    private static class EulerTourContext {
        final Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedEdgesByVertex;
        final Set<Map.Entry<Vertex, Vertex>> sptEdges;
        final Set<Vertex> sptVertices;
        final Set<Vertex> boundaryLeaves;
        final Map<Vertex, Map<Vertex, VertexOfDualGraph>> edgeToLeftFace;
        final List<VertexOfDualGraph> regions;
        final List<Double> weights;
        final List<Double> distances;
        final List<Integer> leafIndices;
        final List<Vertex> tourOrderLeaves;

        double cumulativeWeight;
        int currentLeafIndex;
        final Set<Map.Entry<Vertex, Vertex>> processedNonTreeEdges;
        final Set<Map.Entry<Vertex, Vertex>> attemptedNonTreeEdges;
        final Set<VertexOfDualGraph> addedRegions;
        final Graph<VertexOfDualGraph> dualGraph;
        double currentBoundaryLength;
        List<Vertex> path;
        boolean isFirstSide;

        EulerTourContext(
                Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedEdgesByVertex,
                Set<Map.Entry<Vertex, Vertex>> sptEdges,
                Set<Vertex> sptVertices,
                Set<Vertex> boundaryLeaves,
                Map<Vertex, Map<Vertex, VertexOfDualGraph>> edgeToLeftFace,
                Graph<VertexOfDualGraph> dualGraph,
                List<Vertex> path,
                boolean isFirstSide
        ) {

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
            this.attemptedNonTreeEdges = new HashSet<>();
            this.addedRegions = new HashSet<>();
            this.dualGraph = dualGraph;
            this.currentBoundaryLength = 0.0;
            this.leafIndices = new ArrayList<>();
            this.tourOrderLeaves = new ArrayList<>();
            this.path = path;
            this.isFirstSide = isFirstSide;
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
            Graph<VertexOfDualGraph> dualGraph,
            List<Vertex> path,
            boolean isFirstSide
    ) {

        if (boundaryLeaves.isEmpty()) {
            return new RegionWeightsResult(List.of(), List.of(), List.of(), List.of(), List.of(), 0);
        }

        EulerTourContext context = prepareEulerTourContext(
                root, previous, boundaryLeaves, graph, dualGraph, path, isFirstSide
        );

        logEulerTourStart(context, dualGraph);
        eulerTourIterative(root, context);
        logEulerTourEnd(context);

        return new RegionWeightsResult(
                context.regions,
                context.weights,
                context.distances,
                context.leafIndices,
                context.tourOrderLeaves,
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
            Graph<VertexOfDualGraph> dualGraph,
            List<Vertex> path,
            boolean isFirstSide
    ) {

        Set<Map.Entry<Vertex, Vertex>> sptEdges = buildSPTEdgesSet(previous);
        Map<Vertex, Map<Vertex, VertexOfDualGraph>> edgeToLeftFace = dualGraph.edgeToDualVertexMap();
        graph.resetSortedEdgesCache();
        Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedEdgesByVertex = graph.arrangeByAngle();
        Set<Vertex> sptVertices = buildSPTVerticesSet(root, previous);

        return new EulerTourContext(
                sortedEdgesByVertex, sptEdges, sptVertices,
                new HashSet<>(boundaryLeaves), edgeToLeftFace, dualGraph, path, isFirstSide
        );
    }

    /**
     * ИТЕРАТИВНЫЙ Euler tour обход используя стек.
     *
     * Each frame keeps a pointer (nextEdgeIdx) into its ordered edge list.
     * On every iteration we peek the top frame and advance one edge:
     *   - non-tree edge → process the right face immediately, advance pointer
     *   - tree edge → push child frame (will be processed before we return here)
     *   - all edges done → pop frame, handle boundary leaf
     *
     * This guarantees that non-tree edges between two tree children are processed
     * in between the two subtree visits, preserving the correct Euler tour order.
     */
    private static void eulerTourIterative(Vertex root, EulerTourContext context) {
        Deque<EulerTourFrame> stack = new ArrayDeque<>();

        TreeSet<EdgeOfGraph<Vertex>> rootEdges = context.sortedEdgesByVertex.get(root);
        List<EdgeOfGraph<Vertex>> rootOrdered = getOrderedEdges(rootEdges, null, root, context.path, context.isFirstSide);
        stack.push(new EulerTourFrame(root, null, rootOrdered, false));

        while (!stack.isEmpty()) {
            EulerTourFrame frame = stack.peek();
            logger.debug("Processing vertex {}", frame.vertex.getName());

            if (frame.nextEdgeIdx >= frame.edges.size()) {
                // All edges of this vertex have been processed — leaving vertex
                stack.pop();
                continue;
            }

            EdgeOfGraph<Vertex> edge = frame.edges.get(frame.nextEdgeIdx);
            frame.nextEdgeIdx++;

            Vertex neighbor = edge.end;

            // Skip parent edge
            if (frame.parent != null && neighbor.getName() == frame.parent.getName()) {
                continue;
            }

            if (!context.sptVertices.contains(neighbor)) {
                // edge to external vertex not in tree
                continue;
            }

            boolean isTreeEdge = context.sptEdges.contains(
                    Map.entry(frame.vertex, neighbor));

            if (isTreeEdge) {
                logger.debug("Processing tree edge {} -> {}", frame.vertex.getName(), neighbor.getName());
                boolean neighborIsBoundary = context.boundaryLeaves.contains(neighbor);
                boolean currentIsBoundary = context.boundaryLeaves.contains(frame.vertex);

                boolean newFlag = frame.enteredFromBoundary || neighborIsBoundary;

                if (neighborIsBoundary && !currentIsBoundary && !frame.enteredFromBoundary) {
                    context.tourOrderLeaves.add(neighbor);
                    context.leafIndices.add(context.weights.size() - 1);
                }

                TreeSet<EdgeOfGraph<Vertex>> childEdges =
                        context.sortedEdgesByVertex.get(neighbor);
                List<EdgeOfGraph<Vertex>> childOrdered =
                        getOrderedEdges(childEdges, frame.vertex, null, null, context.isFirstSide);

                stack.push(new EulerTourFrame(neighbor, frame.vertex, childOrdered, newFlag));
            } else {
                logger.debug("Processing non-tree edge {} -> {}", frame.vertex.getName(), neighbor.getName());
                // Non-tree edge — process the right face
                Map.Entry<Vertex, Vertex> edgeKey1 = Map.entry(frame.vertex, neighbor);
                Map.Entry<Vertex, Vertex> edgeKey2 = Map.entry(neighbor, frame.vertex);

                boolean alreadyProcessed = context.processedNonTreeEdges.contains(edgeKey1)
                        || context.processedNonTreeEdges.contains(edgeKey2);

                if (!alreadyProcessed) {
                    boolean faceFound = processRightFace(frame.vertex, neighbor, context);
                    if (faceFound) {
                        context.processedNonTreeEdges.add(edgeKey1);
                    }
                }
            }
        }
    }

    /**
     * Получает упорядоченный список ребер начиная от parent
     */
    private static List<EdgeOfGraph<Vertex>> getOrderedEdges(
            TreeSet<EdgeOfGraph<Vertex>> edges,
            Vertex parent,
            Vertex start,
            List<Vertex> path,
            boolean isFirstSide
    ) {
        List<EdgeOfGraph<Vertex>> edgesList = new ArrayList<>(edges);
        int startIdx = -1;
        if (parent != null) {
            startIdx = findParentIndex(edges, parent);
            startIdx = (startIdx + 1) % edgesList.size();
        } else {
            var splittedVertex = new Vertex(start.name / 1000, start);
            for (int i = 0; i < path.size(); i++) {
                if (path.get(i).equals(start) || path.get(i).equals(splittedVertex)) {
                    Vertex nextVertex = isFirstSide ? path.get((i + 1) % path.size()) : path.get((i - 1 + path.size()) % path.size());
                    startIdx = findParentIndex(edges, nextVertex);
                }
            }
        }

        List<EdgeOfGraph<Vertex>> result = new ArrayList<>();
        for (int i = 0; i < edgesList.size(); i++) {
            result.add(edgesList.get((startIdx + i) % edgesList.size()));
        }
        return result;
    }

    /**
     * Обработка правой грани non-tree ребра current→neighbor.
     * edgeToLeftFace[neighbor][current] = грань слева от neighbor→current
     *                                   = грань справа от current→neighbor.
     * При CW обходе рёбер это корректная грань.
     *
     * Возвращает true, если грань нашлась и регион добавлен. False — если нет
     * (внешняя грань, недостижимая через эту сторону): помечать ребро processed
     * не нужно, чтобы вторая встреча со встречного конца попробовала
     * противоположное направление лукапа.
     */
    private static boolean processRightFace(
            Vertex current,
            Vertex neighbor,
            EulerTourContext context) {

        Vertex newNeighbor = new Vertex(neighbor.name / 1000, neighbor.x, neighbor.y);
        Vertex newCurrent = new Vertex(current.name / 1000, current.x, current.y);

        VertexOfDualGraph face = lookupFace(context, neighbor, current);
        if (face == null) face = lookupFace(context, newNeighbor, current);
        if (face == null) face = lookupFace(context, neighbor, newCurrent);
        if (face == null) face = lookupFace(context, newNeighbor, newCurrent);
        if (face == null) {
            Map.Entry<Vertex, Vertex> reverseAttempt = Map.entry(neighbor, current);
            if (context.attemptedNonTreeEdges.contains(reverseAttempt)) {
                CoordinateConversion cc = new CoordinateConversion();
                logger.warn("No face found for non-tree edge {} ({}, {}) <-> {} from either direction",
                        current.getName(), cc.fromEuclidean(current).x, cc.fromEuclidean(current).y, neighbor.getName());
            } else {
                context.attemptedNonTreeEdges.add(Map.entry(current, neighbor));
            }
            return false;
        }

        context.cumulativeWeight += face.getWeight();
        context.weights.add(context.cumulativeWeight);
        context.regions.add(face);

        double boundaryLength = updateBoundaryLength(face, context);
        context.distances.add(boundaryLength);

        context.addedRegions.add(face);
        logger.debug("  Non-tree edge {} -> {}: cumulative={}, face={}",
                current.getName(), neighbor.getName(), context.cumulativeWeight, face.getName());
        return true;
    }

    private static VertexOfDualGraph lookupFace(EulerTourContext context, Vertex from, Vertex to) {
        var inner = context.edgeToLeftFace.get(from);
        return inner == null ? null : inner.get(to);
    }

    /**
     * Находит вершины границы, которые есть в SPT
     */
    private static List<Vertex> findBoundaryVerticesInSPT(
            Set<Vertex> externalBoundarySet,
            Map<Vertex, Vertex> previous) {

        List<Vertex> result = new ArrayList<>();
        Set<Vertex> inTree = new HashSet<>(previous.values());

        for (Vertex v: externalBoundarySet) {
            if (previous.containsKey(v) || inTree.contains(v)) {
                result.add(v);
            }
        }

        return result;
    }

    private static void logEulerTourStart(EulerTourContext context, Graph<VertexOfDualGraph> dualGraph) {
        logger.debug("SPT Region weights computation (Euler tour):");
        logger.debug("  SPT has {} edges", context.sptEdges.size() / 2);
        logger.debug("  SPT has {} vertices", context.sptVertices.size());
        var map = dualGraph.edgeToDualVertexMap();
        Set<VertexOfDualGraph> faces = new HashSet<>();
        for (var edge: context.sptEdges) {
            if (map.containsKey(edge.getKey())) {
                var m1 = map.get(edge.getKey());
                if (m1.containsKey(edge.getValue())) {
                    faces.add(m1.get(edge.getValue()));
                }
            }
        }
        logger.debug("EXPECTED {} faces", faces.size());
    }

    private static void logEulerTourEnd(EulerTourContext context) {
        logger.debug("  Total region weight: {}", context.cumulativeWeight);
        logger.debug("  Total region number: {}", context.regions.size());
        logger.debug("  Unique regions: {}", new java.util.HashSet<>(context.regions).size());
        logger.debug("  Processed non-tree edges: {}", context.processedNonTreeEdges.size());

        // non-tree edges between SPT vertices that were never processed
        Set<String> seen = new java.util.HashSet<>();
        int totalNonTree = 0, missedNonTree = 0;
        for (Vertex u : context.sptVertices) {
            var edgesFromU = context.sortedEdgesByVertex.get(u);
            if (edgesFromU == null) continue;
            for (var e : edgesFromU) {
                Vertex v = e.end;
                if (!context.sptVertices.contains(v)) continue;
                if (context.sptEdges.contains(Map.entry(u, v))) continue;
                String key = Math.min(u.getName(), v.getName()) + "-" + Math.max(u.getName(), v.getName());
                if (!seen.add(key)) continue;
                totalNonTree++;
                if (!context.processedNonTreeEdges.contains(Map.entry(u, v))
                        && !context.processedNonTreeEdges.contains(Map.entry(v, u))) {
                    missedNonTree++;
                }
            }
        }
        if (missedNonTree > 0) {
            logger.warn("MISSED NON-TREE EDGES between SPT vertices: {}/{} — traversal bug",
                    missedNonTree, totalNonTree);
        }
    }

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

    private static Set<Vertex> buildSPTVerticesSet(Vertex root, Map<Vertex, Vertex> previous) {
        Set<Vertex> vertices = new HashSet<>(previous.keySet());
        vertices.add(root);
        return vertices;
    }

    /**
     * Находит индекс ребра к parent в CCW-отсортированном списке.
     * Если parent == null (корень), возвращает 0.
     */
    private static int findParentIndex(
            TreeSet<EdgeOfGraph<Vertex>> edgesFromCurrent,
            Vertex parent) {

        if (parent == null) {
            return 0;
        }

        List<EdgeOfGraph<Vertex>> edgesList = new ArrayList<>(edgesFromCurrent);
        for (int i = 0; i < edgesList.size(); i++) {
            if (edgesList.get(i).end.getName() == parent.getName()) {
                return i;
            }
        }

        return 0;
    }

    /**
     * Обновляет периметр объединения добавленных граней при добавлении newRegion.
     *
     * Идём по primal-рёбрам грани (последовательным парам в getVerticesOfFace).
     * Для каждого primal-ребра a-b определяем грань с противоположной стороны
     * через edgeToLeftFace[b][a]. Если соседняя грань уже в addedRegions —
     * это бывшее граничное ребро становится внутренним (вычитаем). Иначе —
     * новое граничное ребро (прибавляем). Когда соседней грани нет (внешняя
     * удалённая грань), всё равно прибавляем — primal-ребро на внешнем контуре
     * принадлежит периметру.
     */
    private static double updateBoundaryLength(
            VertexOfDualGraph newRegion,
            EulerTourContext context) {

        List<Vertex> faceVertices = newRegion.getVerticesOfFace();
        if (faceVertices == null || faceVertices.size() < 2) {
            return context.currentBoundaryLength;
        }

        double delta = 0.0;
        int n = faceVertices.size();
        for (int i = 0; i < n; i++) {
            Vertex a = faceVertices.get(i);
            Vertex b = faceVertices.get((i + 1) % n);

            VertexOfDualGraph otherFace = lookupFace(context, b, a);
            if (otherFace == null) {
                Vertex newA = new Vertex(a.name / 1000, a.x, a.y);
                Vertex newB = new Vertex(b.name / 1000, b.x, b.y);
                otherFace = lookupFace(context, newB, a);
                if (otherFace == null) otherFace = lookupFace(context, b, newA);
                if (otherFace == null) otherFace = lookupFace(context, newB, newA);
            }

            double edgeLength = a.getLength(b);

            if (otherFace != null && context.addedRegions.contains(otherFace)) {
                delta -= edgeLength;
            } else {
                delta += edgeLength;
            }
        }

        context.currentBoundaryLength += delta;
        return context.currentBoundaryLength;
    }
}