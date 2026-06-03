package partitioning.maxflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graph.BoundSearcher;
import graph.Edge;
import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;
import partitioning.entities.DijkstraResult;
import partitioning.entities.FlowResult;
import partitioning.entities.SPTWithRegionWeights;
import partitioning.shortestpathtree.ShortestPathTreeProcessor;
import partitioning.shortestpathtree.ShortestPathTreeSearcher;
import readWrite.CoordinateConversion;
import readWrite.FlowWriter;

public class MaxFlowCuttedReif implements MaxFlow {
    private static final Logger logger = LoggerFactory.getLogger(MaxFlowReif.class);
    Graph<Vertex> initGraph;
    Graph<VertexOfDualGraph> dualGraph;
    VertexOfDualGraph source;
    VertexOfDualGraph sink;
    double flow;
    double boundaryLength;
    CoordinateConversion conversion;
    int maxSumVerticesWeight;
    double lengthPriority;

    public MaxFlowCuttedReif(
        Graph<Vertex> initGraph,
        Graph<VertexOfDualGraph> dualGraph,
        VertexOfDualGraph source,
        VertexOfDualGraph sink,
        CoordinateConversion conversion,
        int maxSumVerticesWeight,
        double lengthPriority
    ) {
        this.initGraph = initGraph;
        this.dualGraph = dualGraph;
        this.source = source;
        this.sink = sink;
        this.conversion = conversion;
        this.maxSumVerticesWeight = maxSumVerticesWeight;
        this.lengthPriority = lengthPriority;
    }

    @Override
    public FlowResult findFlow() {
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

        IntersectionsData intersections = findAllIntersections(boundaries);

        List<Vertex> targetSegment1 = extractBoundarySegment(
                boundaries.externalBoundary, intersections.sourceIntersections, intersections.sinkIntersections, true);
        List<Vertex> targetSegment2 = extractBoundarySegment(
                boundaries.externalBoundary, intersections.sourceIntersections, intersections.sinkIntersections, false).reversed();

        Set<Long> sourceIntersectionNames = intersections.sourceIntersections.stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());
        Set<Long> sinkIntersectionNames = intersections.sinkIntersections.stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());
        
        Set<Long> commonIntersections = new HashSet<>(sourceIntersectionNames);
        commonIntersections.retainAll(sinkIntersectionNames);

        CornerConstraints cornerConstraints;

        if (!commonIntersections.isEmpty() && targetSegment2.size() == 1 && commonIntersections.contains(targetSegment2.get(0).getName())) {
            // Source и sink пересекаются на границе от которой запускаемся - создаем ограничения для вершины пересечения
            logger.debug("Source and sink intersect on boundary at vertices: {}", commonIntersections);
            cornerConstraints = buildCornerConstraintsForIntersection(
                    modifiedGraph, commonIntersections, boundaries, intersections.sourceIntersections, intersections.sinkIntersections
            );
        } else {
            TwoKeyVertices keyVertices1 = findTwoKeyVerticesForConstraints(
                    intersections.sourceIntersections, intersections.sinkIntersections, true
            );
            CornerConstraints constraints1 = buildCornerConstraintsForKeyVertices(
                    modifiedGraph, keyVertices1, boundaries, true
            );
            TwoKeyVertices keyVertices2 = findTwoKeyVerticesForConstraints(
                    intersections.sourceIntersections, intersections.sinkIntersections, false
            );
            CornerConstraints constraints2 = buildCornerConstraintsForKeyVertices(
                    modifiedGraph, keyVertices2, boundaries, false
            );
            cornerConstraints = CornerConstraints.merge(constraints1, constraints2);
        }

        for (var key: cornerConstraints.getAllowedEdgesForCorner().keySet()) {
            logger.debug("Allowed edges for corner {} are: {}", key, cornerConstraints.getAllowedEdgesForCorner().get(key).stream().map(v -> v.end.getName()).collect(Collectors.toList()));
        }

        long time3 = System.currentTimeMillis();
        logger.info("Time for computing corner constraints: {} seconds", (time3 - time2) / 1000.0);

        logger.info("Building SPT with segment 1 ({} vertices) as initial path, expanding to segment 2", targetSegment1.size());
        
        Optional<DijkstraResult> sptResultOpt = dijkstraWithInitialPath(
                modifiedGraph,
                targetSegment2,
                targetSegment1,
                cornerConstraints
        );
        long time4 = System.currentTimeMillis();
        logger.info("Time for building SPT with initial path: {} seconds", (time4 - time3) / 1000.0);

        if (sptResultOpt.isEmpty()) {
            logger.error("No SPT found from segment 1 to segment 2!");
            return new FlowResult(0, dualGraph, source, sink);
        }

        DijkstraResult sptResult = sptResultOpt.get();
        logger.info("Built SPT with distance: {}", sptResult.distance());
        logger.info("SPT has {} vertices in previous map", sptResult.previous().size());

        double alpha = ShortestPathTreeProcessor.calculateAlpha(dualGraph.verticesWeight(), maxSumVerticesWeight);
        double totalWeight = dualGraph.verticesWeight();
        double sourceWeight = sourceNeighbors.stream().mapToDouble(VertexOfDualGraph::getWeight).sum();
        double sinkWeight = sinkNeighbors.stream().mapToDouble(VertexOfDualGraph::getWeight).sum();

        Vertex root = targetSegment2.get(targetSegment2.size() - 1);
        logger.debug("Processing SPT with root vertex: {}", root.getName());

        SPTWithRegionWeights spt = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                modifiedGraph, sptResult.previous(), root,
                targetSegment1, dualGraph, true, targetSegment2
        );

        DijkstraResult sptWithWeights = new DijkstraResult(
                sptResult.path(),
                sptResult.distance(),
                sptResult.previous(),
                sptResult.dijkstraDistances(),
                spt.boundaryLeaves(),
                spt.faces(),
                spt.regionWeights(),
                spt.distances(),
                spt.leafIndices(),
                spt.totalRegionWeight()
        );

        logger.debug("SPT from root {}: {} leaves, {} regions, total weight {}",
                root.getName(), sptWithWeights.boundaryLeaves().size(),
                sptWithWeights.regions().size(), sptWithWeights.totalRegionWeight());

        PathResult bestPathResult = findBestLeafInSPT(
                sptWithWeights, alpha, totalWeight, sourceWeight, sinkWeight, boundaryLength,
                boundaries.externalBoundary()
        );
        
        long time5 = System.currentTimeMillis();
        logger.info("Time for finding best path across all roots: {} seconds", (time5 - time4) / 1000.0);

        if (bestPathResult == null) {
            logger.error("No valid path found!");
            return new FlowResult(0, dualGraph, source, sink);
        }

        logger.info("Best path found with score: {}, distance: {}, balance: {}",
                bestPathResult.score, bestPathResult.totalDistance, bestPathResult.balanceWeight);

        flow = fillFlowInDualGraph(bestPathResult.path, dualGraph);
        long time6 = System.currentTimeMillis();
        logger.info("Time for filling flow in dual graph: {} seconds", (time6 - time5) / 1000.0);

        dumpVisualization(boundaries, bestPathResult.path, sourceNeighbors, sinkNeighbors, modifiedGraph, sptWithWeights, root);

        return new FlowResult(flow, dualGraph, source, sink, bestPathResult.path);
    }

    private record PathResult(
            List<Vertex> path,
            double score,
            double totalDistance,
            double balanceWeight
    ) {}

    private Optional<DijkstraResult> dijkstraWithInitialPath(
            Graph<Vertex> graph,
            List<Vertex> initialPath,
            List<Vertex> targetBoundary,
            CornerConstraints cornerConstraints
    ) {
        if (initialPath.isEmpty()) {
            logger.error("Initial path is empty!");
            return Optional.empty();
        }

        Map<Vertex, Double> distances = new HashMap<>();
        Map<Vertex, Vertex> previous = new HashMap<>();
        java.util.PriorityQueue<partitioning.entities.VertexDistance> queue = 
            new java.util.PriorityQueue<>(java.util.Comparator.comparingDouble(partitioning.entities.VertexDistance::distance));

        for (Vertex v : graph.verticesArray()) {
            distances.put(v, Double.MAX_VALUE);
        }

        for (int i = 0; i < initialPath.size(); i++) {
            Vertex current = initialPath.get(i);
            distances.put(current, 0.0);
            queue.add(new partitioning.entities.VertexDistance(current, 0.0));

            if (i > 0) {
                previous.put(current, initialPath.get(i - 1));
            }

            logger.debug("Initial path vertex {}: distance=0", current.getName());
        }

        logger.info("Initialized SPT with path of {} vertices, all at distance 0", initialPath.size());

        Vertex targetVertex = null;
        double minDistance = Double.MAX_VALUE;

        while (!queue.isEmpty()) {
            partitioning.entities.VertexDistance current = queue.poll();

            if (current.distance() > distances.get(current.vertex())) {
                continue;
            }

            if (isBoundaryContainsVertex(targetBoundary, current.vertex()) && current.vertex().getIsOnBoundary()) {
                if (current.distance() < minDistance) {
                    minDistance = current.distance();
                    targetVertex = current.vertex();
                    logger.debug("Found target vertex: {} with distance: {}", targetVertex.getName(), minDistance);
                }
            }

            Map<Vertex, Edge> neighbors = graph.getEdges().get(current.vertex());
            if (neighbors == null) {
                continue;
            }

            for (Map.Entry<Vertex, Edge> entry : neighbors.entrySet()) {
                Vertex neighbor = entry.getKey();

                if (!cornerConstraints.isNeighborAllowed(current.vertex(), neighbor)) {
                    continue;
                }

                double currentDistance = distances.get(current.vertex());
                double edgeLength = entry.getValue().length;
                double newDistance = currentDistance + edgeLength;

                if (newDistance < distances.get(neighbor)) {
                    distances.put(neighbor, newDistance);
                    previous.put(neighbor, current.vertex());
                    queue.add(new partitioning.entities.VertexDistance(neighbor, newDistance));
                }
            }
        }

        if (targetVertex == null) {
            logger.error("No target vertex found!");
            return Optional.empty();
        }

        List<Vertex> path = reconstructPath(previous, targetVertex);

        return Optional.of(new DijkstraResult(
                path,
                minDistance,
                previous,
                distances,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0.0
        ));
    }

    private List<Vertex> reconstructPath(Map<Vertex, Vertex> previous, Vertex targetVertex) {
        List<Vertex> path = new ArrayList<>();
        Vertex current = targetVertex;
        while (current != null) {
            path.add(0, current);
            current = previous.get(current);
        }
        return path;
    }

    private boolean isBoundaryContainsVertex(List<Vertex> boundary, Vertex vertex) {
        if (boundary.contains(vertex)) {
            return true;
        }
        Vertex vertexMain = new Vertex(vertex.name / 1000, vertex.x, vertex.y);
        return boundary.contains(vertexMain);
    }

    private PathResult findBestLeafInSPT(
            DijkstraResult sptWithWeights,
            double alpha,
            double totalWeight,
            double sourceWeight,
            double sinkWeight,
            double boundaryLength,
            List<Vertex> externalBoundary
    ) {
        int n = sptWithWeights.leafIndices().size();
        if (n == 0) {
            logger.warn("No leaves in SPT, returning empty path");
            return new PathResult(List.of(), Double.MAX_VALUE, 0.0, 0.0);
        }

        Set<Long> externalBoundaryNames = externalBoundary.stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());

        double bestScore = Double.MAX_VALUE;
        int bestLeafIdx = 0;
        List<Vertex> bestPath = List.of();

        for (int i = 0; i < n; i++) {
            Vertex leaf = sptWithWeights.boundaryLeaves().get(i);
            Double distance = sptWithWeights.dijkstraDistances().get(leaf);

            if (distance == null || distance == Double.MAX_VALUE) {
                continue;
            }

            int idx = sptWithWeights.leafIndices().get(i);
            double regionWeight = idx == -1 ? 0.0 : sptWithWeights.weights().get(idx);
            double leftWeight = regionWeight + sourceWeight;
            double balance = Math.abs(alpha * totalWeight - leftWeight);

            double normalizedLength = distance / boundaryLength;
            double normalizedBalance = balance / totalWeight;
            double score = lengthPriority * normalizedLength + (1 - lengthPriority) * normalizedBalance;

            List<Vertex> path = reconstructPathToLeaf(sptWithWeights, i);
            int externalSegments = countExternalBoundarySegments(path, externalBoundaryNames);

            if (externalSegments > 2) {
                double penalty = (externalSegments - 2) * 10.0;
                score += penalty;
            }

            logger.debug("Leaf {}: distance={}, regionWeight={}, balance={}, score={}", 
                    leaf.getName(), distance, regionWeight, balance, score);

            if (score < bestScore) {
                bestScore = score;
                bestLeafIdx = i;
                bestPath = path;
                logger.info("New best leaf: {} with score {}, distance {}, balance {}",
                        leaf.getName(), score, distance, balance);
            }
        }

        Vertex bestLeaf = sptWithWeights.boundaryLeaves().get(bestLeafIdx);
        Double bestDistance = sptWithWeights.dijkstraDistances().get(bestLeaf);
        int bestIdx = sptWithWeights.leafIndices().get(bestLeafIdx);
        double bestRegionWeight = bestIdx == -1 ? 0.0 : sptWithWeights.weights().get(bestIdx);
        double bestLeftWeight = bestRegionWeight + sourceWeight;
        double bestBalance = Math.abs(alpha * totalWeight - bestLeftWeight);

        return new PathResult(bestPath, bestScore, bestDistance, bestBalance);
    }

    private List<Vertex> reconstructPathToLeaf(DijkstraResult result, int leafIdx) {
        Vertex leaf = result.boundaryLeaves().get(leafIdx);
        List<Vertex> path = new ArrayList<>();
        Vertex current = leaf;
        while (current != null) {
            path.add(current);
            current = result.previous().get(current);
        }
        java.util.Collections.reverse(path);
        return path;
    }

    private int countExternalBoundarySegments(List<Vertex> path, Set<Long> externalBoundaryNames) {
        int segments = 0;
        boolean inSegment = false;
        for (Vertex v : path) {
            boolean onBoundary = externalBoundaryNames.contains(v.name) || externalBoundaryNames.contains(v.name / 1000);
            if (onBoundary) {
                if (!inSegment) {
                    segments++;
                    inSegment = true;
                }
            } else {
                inSegment = false;
            }
        }
        return segments;
    }

    private double fillFlowInDualGraph(List<Vertex> path, Graph<VertexOfDualGraph> dualGraph) {
        if (path.size() < 2) {
            return 0.0;
        }

        double totalFlow = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            Vertex v1 = path.get(i);
            Vertex v2 = path.get(i + 1);
            totalFlow += processDualEdge(v1, v2, dualGraph);
        }

        return totalFlow;
    }

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
        if (dualEdge1.flow == dualEdge1.getBandwidth()) {
            return 0;
        }
        double bandwidth = dualEdge1.getBandwidth();
        dualEdge1.flow = bandwidth;

        if (dualGraph.getEdges().get(face2) != null &&
                dualGraph.getEdges().get(face2).containsKey(face1)) {
            Edge dualEdge2 = dualGraph.getEdges().get(face2).get(face1);
            dualEdge2.flow = dualEdge2.getBandwidth();
        }

        return bandwidth;
    }

    private void dumpVisualization(
            BoundariesData boundaries,
            List<Vertex> bestPath,
            HashSet<VertexOfDualGraph> sourceNeighbors,
            HashSet<VertexOfDualGraph> sinkNeighbors,
            Graph<Vertex> modifiedGraph,
            DijkstraResult sptWithWeights,
            Vertex root) {

        FlowWriter.dumpVisualizationData(
                boundaries.externalBoundary(),
                boundaries.sourceBoundary(),
                boundaries.sinkBoundary(),
                List.of(), bestPath,
                sourceNeighbors, sinkNeighbors, flow,
                initGraph, modifiedGraph, dualGraph, source, sink, conversion
        );

        FlowWriter.dumpSPTVisualizationData(
                sptWithWeights, null,
                root, null,
                Map.of(),
                sourceNeighbors, sinkNeighbors, flow, conversion, initGraph
        );
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

    /**
     * Вычисляет все границы
     */
    public BoundariesData computeBoundaries(
            Set<VertexOfDualGraph> sourceNeighbors,
            Set<VertexOfDualGraph> sinkNeighbors
    ) {

        List<Vertex> sourceBoundary = BoundSearcher.findBound(initGraph, sourceNeighbors);
        List<Vertex> sinkBoundary = BoundSearcher.findBound(initGraph, sinkNeighbors);

        HashSet<VertexOfDualGraph> allDualVerticesSet = new HashSet<>(dualGraph.verticesNumber() - 2);
        for (VertexOfDualGraph v : dualGraph.verticesArray()) {
            if (v != source && v != sink) {
                allDualVerticesSet.add(v);
            }
        }

        List<Vertex> externalBoundary = BoundSearcher.findBound(initGraph, allDualVerticesSet);
        Map<Vertex, Map<Vertex, Edge>> edges = initGraph.getEdges();
        for (int i = 0; i < externalBoundary.size(); i++) {
            Vertex v1 = externalBoundary.get(i);
            Vertex v2 = externalBoundary.get((i + 1) % externalBoundary.size());
            
            Map<Vertex, Edge> v1Edges = edges.get(v1);
            if (v1Edges != null) {
                Edge edge = v1Edges.get(v2);
                if (edge != null) {
                    boundaryLength += edge.length;
                }
            }
        }

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
        Set<Map.Entry<Vertex, Vertex>> joinedSourceSinkInnerEdges = new HashSet<>(sourceInnerEdges);
        joinedSourceSinkInnerEdges.addAll(sinkInnerEdges);
        Set<Map.Entry<Vertex, Vertex>> innerEdges = collectFaceEdges(dualGraph.verticesArray());

        Set<Vertex> sourceBoundarySet = new HashSet<>(sourceBoundary);
        Set<Vertex> sinkBoundarySet = new HashSet<>(sinkBoundary);
        Set<Vertex> externalBoundarySet = new HashSet<>(externalBoundary);

        // Добавляем границы
        modifiedGraph.addBoundEdgesWithConstraints(sourceBoundary, initGraph, joinedSourceSinkInnerEdges, externalBoundarySet);
        modifiedGraph.addBoundEdgesWithConstraints(sinkBoundary, initGraph, joinedSourceSinkInnerEdges, externalBoundarySet);
        modifiedGraph.addBoundEdges(externalBoundary, initGraph);

        for (Vertex sourceVertex : sourceBoundary) {
            Map<Vertex, Edge> neighbors = initGraph.getEdges().get(sourceVertex);
            if (neighbors == null) {
                continue;
            }
            for (Vertex sinkVertex : sinkBoundary) {
                if (externalBoundarySet.contains(sourceVertex) && externalBoundarySet.contains(sinkVertex)) {
                    continue;
                }
                if (sourceInnerEdges.contains(Map.entry(sourceVertex, sinkVertex))
                        || sourceInnerEdges.contains(Map.entry(sinkVertex, sourceVertex))) {
                    continue;
                }
                if (sinkInnerEdges.contains(Map.entry(sourceVertex, sinkVertex))
                        || sinkInnerEdges.contains(Map.entry(sinkVertex, sourceVertex))) {
                    continue;
                }
                Edge edge = neighbors.get(sinkVertex);
                if (edge != null) {
                    modifiedGraph.addVertex(sourceVertex);
                    modifiedGraph.addVertex(sinkVertex);
                    modifiedGraph.addEdge(sourceVertex, sinkVertex, edge.length);
                }
            }
        }

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


    public record BoundariesData(
            List<Vertex> sourceBoundary,
            List<Vertex> sinkBoundary,
            List<Vertex> externalBoundary
    ) {}

    private record IntersectionsData(
            List<Vertex> sourceIntersections,
            List<Vertex> sinkIntersections
    ) {}

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

    private record TwoKeyVertices(
            Vertex sourceVertex,
            Vertex sinkVertex
    ) {
        boolean isValid() {
            return sourceVertex != null && sinkVertex != null;
        }
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