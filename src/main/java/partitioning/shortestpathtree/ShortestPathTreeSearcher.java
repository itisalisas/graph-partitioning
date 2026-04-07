package partitioning.shortestpathtree;

import java.util.*;

import graph.Edge;
import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;
import org.junit.jupiter.api.Assertions;
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
        List<Vertex> boundaryVerticesInSPT = findBoundaryVerticesInSPT(externalBoundarySet, previous);

        Map<Long, Integer> boundaryOrderMap = buildBoundaryOrderMap(externalBoundary);
        TwoKeyVertices keyVertices = findTwoKeyVertices(sourceCorners, sinkCorners, isFirstSide);

        int boundarySegmentStart = determineSegmentStartFromVertices(
                keyVertices, boundaryOrderMap);

        sortBoundaryLeavesBySegment(boundaryVerticesInSPT, boundaryOrderMap,
                                    boundarySegmentStart, externalBoundary.size());

        RegionWeightsResult weightsResult = computeRegionWeightsByEulerTour(
                sourceVertex, previous, boundaryVerticesInSPT, graph, dualGraph);
        Assertions.assertEquals(boundaryVerticesInSPT.size(), weightsResult.leafIndices.size());
        System.out.println("INDICES: ");
        for (int i = 0; i < weightsResult.leafIndices.size(); i++) {
            System.out.print(boundaryVerticesInSPT.get(i).name + ": " + weightsResult.leafIndices.get(i) + ". ");
        }
        System.out.println();

        return new SPTWithRegionWeights(
                weightsResult.regions, weightsResult.weights,
                weightsResult.distances,
                sourceVertex, previous, null,
                boundaryVerticesInSPT,
                weightsResult.leafIndices,
                weightsResult.totalRegionWeight()
        );
    }

    private record RegionWeightsResult(
            List<VertexOfDualGraph> regions,
            List<Double> weights,
            List<Double> distances,
            List<Integer> leafIndices,
            double totalRegionWeight
    ) {}

    /**
     * Состояние для итеративного Euler Tour.
     * Each frame holds the ordered edge list and a mutable index tracking
     * which edge to process next, so non-tree edges are interleaved correctly
     * with child subtree visits.
     */
    private static class EulerTourFrame {
        final Vertex vertex;
        final Vertex parent;
        final List<EdgeOfGraph<Vertex>> edges;
        int nextEdgeIdx;

        EulerTourFrame(Vertex vertex, Vertex parent, List<EdgeOfGraph<Vertex>> edges) {
            this.vertex = vertex;
            this.parent = parent;
            this.edges = edges;
            this.nextEdgeIdx = 0;
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
        final Map<Vertex, HashMap<Vertex, VertexOfDualGraph>> edgeToLeftFace;
        final List<VertexOfDualGraph> regions;
        final List<Double> weights;
        final List<Double> distances;
        final List<Integer> leafIndices;

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
            this.leafIndices = new ArrayList<>();
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
            return new RegionWeightsResult(List.of(), List.of(), List.of(), List.of(), 0);
        }

        EulerTourContext context = prepareEulerTourContext(
                root, previous, boundaryLeaves, graph, dualGraph);

        logEulerTourStart(context, dualGraph);
        eulerTourIterative(root, context);
        logEulerTourEnd(context);

        return new RegionWeightsResult(
                context.regions,
                context.weights,
                context.distances,
                context.leafIndices,
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
        graph.resetSortedEdgesCache();
        Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedEdgesByVertex = graph.arrangeByAngle();
        Set<Vertex> sptVertices = buildSPTVerticesSet(root, previous);

        return new EulerTourContext(
                sortedEdgesByVertex, sptEdges, sptVertices,
                new HashSet<>(boundaryLeaves), edgeToLeftFace, dualGraph);
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
        List<EdgeOfGraph<Vertex>> rootOrdered = getOrderedEdges(rootEdges, null);
        stack.push(new EulerTourFrame(root, null, rootOrdered));

        while (!stack.isEmpty()) {
            EulerTourFrame frame = stack.peek();

            if (frame.nextEdgeIdx >= frame.edges.size()) {
                // All edges of this vertex have been processed — leaving vertex
                stack.pop();
                handleBoundaryVertex(frame.vertex, context);
                continue;
            }

            EdgeOfGraph<Vertex> edge = frame.edges.get(frame.nextEdgeIdx);
            frame.nextEdgeIdx++;

            Vertex neighbor = edge.end;

            // Skip parent edge
            if (frame.parent != null && neighbor.getName() == frame.parent.getName()) {
                continue;
            }

            boolean isTreeEdge = context.sptEdges.contains(
                    Map.entry(frame.vertex, neighbor));

            if (isTreeEdge) {
                // Descend into child — push new frame (will be processed before
                // we return to the current frame's next edge)
                TreeSet<EdgeOfGraph<Vertex>> childEdges =
                        context.sortedEdgesByVertex.get(neighbor);
                List<EdgeOfGraph<Vertex>> childOrdered =
                        getOrderedEdges(childEdges, frame.vertex);
                stack.push(new EulerTourFrame(neighbor, frame.vertex, childOrdered));
            } else {
                // Non-tree edge — process the right face
                Map.Entry<Vertex, Vertex> edgeKey1 = Map.entry(frame.vertex, neighbor);
                Map.Entry<Vertex, Vertex> edgeKey2 = Map.entry(neighbor, frame.vertex);

                if (!context.processedNonTreeEdges.contains(edgeKey1) &&
                        !context.processedNonTreeEdges.contains(edgeKey2)) {
                    processRightFace(frame.vertex, neighbor, context);
                    context.processedNonTreeEdges.add(edgeKey1);
                }
            }
        }
    }

    /**
     * Получает упорядоченный список ребер начиная от parent
     */
    private static List<EdgeOfGraph<Vertex>> getOrderedEdges(
            TreeSet<EdgeOfGraph<Vertex>> edges,
            Vertex parent) {

        List<EdgeOfGraph<Vertex>> edgesList = new ArrayList<>(edges);
        int startIdx = findParentIndex(edges, parent);
        startIdx = (startIdx + 1) % edgesList.size();

        List<EdgeOfGraph<Vertex>> result = new ArrayList<>();
        for (int i = 0; i < edgesList.size(); i++) {
            result.add(edgesList.get((startIdx + i) % edgesList.size()));
        }
        return result;
    }

    /**
     * Обрабатывает boundary leaf вершину
     */
    private static void handleBoundaryVertex(Vertex vertex, EulerTourContext context) {
        if (context.boundaryLeaves.contains(vertex)) {
            context.leafIndices.add(context.weights.size() - 1);
            //System.out.println("  Leaf " + context.currentLeafIndex +
            //                           " (vertex " + vertex.getName() +
            //                           "): cumulative=" + context.cumulativeWeight);
            context.currentLeafIndex++;
        }
    }

    /**
     * Обработка правой грани non-tree ребра current→neighbor.
     * edgeToLeftFace[neighbor][current] = грань слева от neighbor→current
     *                                   = грань справа от current→neighbor.
     * При CW обходе рёбер это корректная грань.
     */
    private static void processRightFace(
            Vertex current,
            Vertex neighbor,
            EulerTourContext context) {

        VertexOfDualGraph face = null;

        if (context.edgeToLeftFace.containsKey(neighbor)) {
            face = context.edgeToLeftFace.get(neighbor).get(current);
        }
        Vertex newNeighbor = new Vertex(neighbor.name / 1000, neighbor.x, neighbor.y);
        Vertex newCurrent = new Vertex(current.name / 1000, current.x, current.y);
        if (face == null && context.edgeToLeftFace.containsKey(newNeighbor)) {
            face = context.edgeToLeftFace.get(newNeighbor).get(current);
        }
        else if (face == null && context.edgeToLeftFace.containsKey(neighbor)) {
            face = context.edgeToLeftFace.get(neighbor).get(newCurrent);
        }
        else if (face == null && context.edgeToLeftFace.containsKey(newNeighbor)) {
            face = context.edgeToLeftFace.get(newNeighbor).get(newCurrent);
        }
        if (face != null) {
            context.cumulativeWeight += face.getWeight();
            context.weights.add(context.cumulativeWeight);
            context.regions.add(face);
            
            double boundaryLength = updateBoundaryLength(face, context);
            context.distances.add(boundaryLength);
            
            context.addedRegions.add(face);
        } else {
            System.err.println("WARNING: no face found for non-tree edge "
                    + current.getName() + " -> " + neighbor.getName());
        }
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

    /**
     * Находит два ключевых угла (source и sink), ограничивающих нужный сегмент external boundary
     */
    private static TwoKeyVertices findTwoKeyVertices(
            List<Vertex> sourceIntersections,
            List<Vertex> sinkIntersections,
            boolean isFirstSide) {

        if (sourceIntersections.isEmpty() || sinkIntersections.isEmpty()) {
            System.out.println("WARNING: No intersections found");
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

        System.out.println("Boundary segment: from source corner at " + sourcePos + " (name = " + corners.sourceVertex.name +
                ") to sink corner at " + sinkPos + " (name = " + corners.sinkVertex.name +
                ") -> segmentStart=" + sourcePos);

        return sourcePos;
    }

    private static void logEulerTourStart(EulerTourContext context, Graph<VertexOfDualGraph> dualGraph) {
        System.out.println("SPT Region weights computation (Euler tour):");
        System.out.println("  SPT has " + context.sptEdges.size() / 2 + " edges");
        System.out.println("  SPT has " + context.sptVertices.size() + " vertices");
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
        System.out.println("EXPECTED " + faces.size() + " faces");
    }

    private static void logEulerTourEnd(EulerTourContext context) {
        System.out.println("  Total region weight: " + context.cumulativeWeight);
        System.out.println("  Total region number: " + context.regions.size());
        System.out.println("  Unique regions: " + new java.util.HashSet<>(context.regions).size());
        System.out.println("  Processed non-tree edges: " + context.processedNonTreeEdges.size());
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

        // Reverse order (CW traversal instead of CCW)
        boundaryVerticesInSPT.sort((a, b) -> {
            int posA = boundaryOrderMap.getOrDefault(a.getName(), 0);
            int posB = boundaryOrderMap.getOrDefault(b.getName(), 0);

            int adjA = (posA - boundarySegmentStart + n) % n;
            int adjB = (posB - boundarySegmentStart + n) % n;

            return Integer.compare(adjB, adjA);
        });

        System.out.println("Boundary leaves ordering (CW): segmentStart=" +
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