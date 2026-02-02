package partitioning;

import java.util.*;
import java.util.stream.Collectors;

import graph.*;
import readWrite.CoordinateConversion;
import readWrite.FlowWriter;

import static partitioning.VertexSplitter.preprocessNeighborSplits;
import static partitioning.VertexSplitter.splitVertex;

public class MaxFlowReif implements MaxFlow {
    Graph<Vertex> initGraph;
    Graph<VertexOfDualGraph> dualGraph;
    VertexOfDualGraph source;
    VertexOfDualGraph sink;
    double flow;
    CoordinateConversion coordConversion;

    public MaxFlowReif(Graph<Vertex> initGraph, Graph<VertexOfDualGraph> dualGraph, VertexOfDualGraph source, VertexOfDualGraph sink) {
        this.initGraph = initGraph;
        this.dualGraph = dualGraph;
        this.source = source;
        this.sink = sink;
        this.coordConversion = new CoordinateConversion();
    }

    @Override
    public FlowResult findFlow() {
        HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph = buildComparisonMap();
        List<VertexOfDualGraph> allDualVertices = dualGraph.verticesArray();

        HashSet<VertexOfDualGraph> sourceNeighbors = collectNeighbors(source);
        HashSet<VertexOfDualGraph> sinkNeighbors = collectNeighbors(sink);
        
        long time00 = System.currentTimeMillis();

        long time01 = System.currentTimeMillis();

        List<Vertex> sourceBoundary = BoundSearcher.findBound(initGraph, sourceNeighbors, comparisonForDualGraph);
        List<Vertex> sinkBoundary = BoundSearcher.findBound(initGraph, sinkNeighbors, comparisonForDualGraph);

        long time02 = System.currentTimeMillis();

        HashSet<VertexOfDualGraph> allDualVerticesSet = new HashSet<>(allDualVertices);
        allDualVerticesSet.remove(source);
        allDualVerticesSet.remove(sink);
        List<Vertex> externalBoundary = BoundSearcher.findBound(initGraph, allDualVerticesSet, comparisonForDualGraph);

        if (externalBoundary.isEmpty()) {
            System.out.println("ERROR: External boundary is empty!");
            return new FlowResult(0, dualGraph, source, sink);
        }
        System.out.println("External boundary size: " + externalBoundary.size());

        long time03 = System.currentTimeMillis();

        Graph<Vertex> modifiedGraph = new Graph<>();
        createModifiedSubgraph(modifiedGraph, sourceBoundary, sinkBoundary, sourceNeighbors, sinkNeighbors, externalBoundary, dualGraph);
        modifiedGraph.setEdgeToDualVertexMap(initGraph.getEdgeToDualVertexMap());

        long time1 = System.currentTimeMillis();

        List<Vertex> sourceCorners = findCorners(externalBoundary, sourceBoundary);
        List<Vertex> sinkCorners = findCorners(externalBoundary, sinkBoundary);

        System.out.println("Found " + sourceCorners.size() + " source corners and " + sinkCorners.size() + " sink corners on external boundary");

        DijkstraResult shortestPathResult = dijkstraMultiSource(modifiedGraph, sourceBoundary, sinkBoundary);

        long time2 = System.currentTimeMillis();

        if (shortestPathResult == null || shortestPathResult.path.isEmpty()) {
            System.out.println("ERROR: No shortest path found between source and sink boundaries!");
            System.out.println("  modifiedGraph vertices: " + modifiedGraph.verticesArray().size());
            System.out.println("  sourceBoundary size: " + sourceBoundary.size());
            System.out.println("  sinkBoundary size: " + sinkBoundary.size());

            FlowWriter.dumpVisualizationData(externalBoundary, sourceBoundary, sinkBoundary, List.of(), List.of(), Map.of(), sourceNeighbors, sinkNeighbors, 0, modifiedGraph, dualGraph, source, sink, coordConversion);

            return new FlowResult(0, dualGraph, source, sink);
        }

        System.out.println("Found shortest path with " + shortestPathResult.path.size() + " vertices, distance: " + shortestPathResult.distance);

        List<Vertex> shortestPath = shortestPathResult.path;

        Map<Long, VertexSplitter.NeighborSplit> neighborSplits;
        try {
            neighborSplits = preprocessNeighborSplits(modifiedGraph, shortestPath, sourceBoundary, sinkBoundary);
            System.out.println("Created " + neighborSplits.size() + " neighbor splits for " + shortestPath.size() + " vertices in path");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("broken boundary")) {
                System.out.println("Broken boundary detected: " + e.getMessage());
                FlowWriter.dumpVisualizationData(externalBoundary, sourceBoundary, sinkBoundary, shortestPath, null, new HashMap<>(), sourceNeighbors, sinkNeighbors, 0, modifiedGraph, dualGraph, source, sink, coordConversion);
                return new FlowResult(0, dualGraph, source, sink);
            }
            throw e;
        }

        long time3 = System.currentTimeMillis();

        Map<Vertex, Vertex> splitToOriginalMap = new HashMap<>();

        List<Map.Entry<Vertex, Vertex>> splitVertices = new ArrayList<>();
        for (Vertex pathVertex : shortestPath) {
            splitVertices.add(splitVertex(modifiedGraph, pathVertex, splitToOriginalMap, neighborSplits));
        }

        long time4 = System.currentTimeMillis();

        double minPathLength = Double.MAX_VALUE;
        List<Vertex> bestPath = null;
        DijkstraResult bestPath1ToBoundary = null;
        DijkstraResult bestPath2ToBoundary = null;
        Vertex bestSplitVertex1 = null;
        Vertex bestSplitVertex2 = null;

        for (Map.Entry<Vertex, Vertex> splitVertex : splitVertices) {
            Vertex splitVertex1 = splitVertex.getKey();
            Vertex splitVertex2 = splitVertex.getValue();

            DijkstraResult path1ToBoundary = dijkstraMultiSourceWithRegionWeights(
                    modifiedGraph, Collections.singletonList(splitVertex1), externalBoundary, dualGraph,
                    sourceCorners, sinkCorners, true);
            DijkstraResult path2ToBoundary = dijkstraMultiSourceWithRegionWeights(
                    modifiedGraph, Collections.singletonList(splitVertex2), externalBoundary, dualGraph,
                    sourceCorners, sinkCorners, false);

            if (path1ToBoundary == null || path2ToBoundary == null) {
                System.out.println("====================================");
                System.out.println("ERROR: Path to external boundary not found!");
                System.out.println("  Original vertex on shortest path: " + splitToOriginalMap.get(splitVertex1).getName());
                System.out.println("  Split vertex 1 ID: " + splitVertex1.getName());
                System.out.println("  Split vertex 2 ID: " + splitVertex2.getName());
                System.out.println("  Path1ToBoundary (left): " + (path1ToBoundary != null ? "FOUND" : "NULL"));
                System.out.println("  Path2ToBoundary (right): " + (path2ToBoundary != null ? "FOUND" : "NULL"));
                System.out.println("  External boundary size: " + externalBoundary.size());
                
                // Проверяем, есть ли split vertices в modifiedGraph
                boolean sv1InGraph = modifiedGraph.getEdges().containsKey(splitVertex1);
                boolean sv2InGraph = modifiedGraph.getEdges().containsKey(splitVertex2);
                System.out.println("  Split vertex 1 in graph: " + sv1InGraph);
                System.out.println("  Split vertex 2 in graph: " + sv2InGraph);
                
                if (sv1InGraph) {
                    int neighborsCount1 = modifiedGraph.getEdges().get(splitVertex1).size();
                    System.out.println("  Split vertex 1 neighbors: " + neighborsCount1);
                    if (neighborsCount1 > 0) {
                        System.out.println("    First 5 neighbors: " + 
                                modifiedGraph.getEdges().get(splitVertex1).keySet().stream()
                                        .limit(5).map(Vertex::getName).collect(Collectors.toList()));
                    }
                }
                
                if (sv2InGraph) {
                    int neighborsCount2 = modifiedGraph.getEdges().get(splitVertex2).size();
                    System.out.println("  Split vertex 2 neighbors: " + neighborsCount2);
                    if (neighborsCount2 > 0) {
                        System.out.println("    First 5 neighbors: " + 
                                modifiedGraph.getEdges().get(splitVertex2).keySet().stream()
                                        .limit(5).map(Vertex::getName).collect(Collectors.toList()));
                    }
                }
                
                System.out.println("====================================");
                continue;
            }

            double totalDistance = path1ToBoundary.distance + path2ToBoundary.distance;

            List<Vertex> combinedPath = new ArrayList<>();

            for (int i = path1ToBoundary.path.size() - 1; i >= 0; i--) {
                combinedPath.add(path1ToBoundary.path.get(i));
            }
            for (int i = 1; i < path2ToBoundary.path.size(); i++) {
                combinedPath.add(path2ToBoundary.path.get(i));
            }

            if (totalDistance < minPathLength) {
                minPathLength = totalDistance;

                List<Vertex> pathInOriginalGraph = new ArrayList<>();
                for (Vertex v : combinedPath) {
                    pathInOriginalGraph.add(splitToOriginalMap.getOrDefault(v, v));
                }
                bestPath = pathInOriginalGraph;
                bestPath1ToBoundary = path1ToBoundary;
                bestPath2ToBoundary = path2ToBoundary;
                bestSplitVertex1 = splitVertex1;
                bestSplitVertex2 = splitVertex2;
            }
        }

        long time5 = System.currentTimeMillis();

        if (bestPath == null) {
            System.out.println("No path found");
            FlowWriter.dumpVisualizationData(externalBoundary, sourceBoundary, sinkBoundary, shortestPath, bestPath, neighborSplits, sourceNeighbors, sinkNeighbors, 0, modifiedGraph, dualGraph, source, sink, coordConversion);
            return new FlowResult(0, dualGraph, source, sink);
        }

        flow = fillFlowInDualGraph(bestPath, dualGraph);

        long time6 = System.currentTimeMillis();

        FlowWriter.dumpVisualizationData(externalBoundary, sourceBoundary, sinkBoundary, shortestPath, bestPath, neighborSplits, sourceNeighbors, sinkNeighbors, flow, modifiedGraph, dualGraph, source, sink, coordConversion);
        FlowWriter.dumpSPTVisualizationData(
                bestPath1ToBoundary, bestPath2ToBoundary,
                bestSplitVertex1, bestSplitVertex2,
                splitToOriginalMap, sourceNeighbors, sinkNeighbors, flow, coordConversion);
        System.out.println("TIME: time01-time00: " + (time01-time00) + ", time02-time01:" + (time02-time01) + ", time03-time02:" + (time03-time02)  + ", time1-time03:" + (time1-time03) + ", time2-time1:" + (time2-time1) + ", time3-time2:" + (time3-time2) + ", time4-time3:" + (time4-time3) + ", time5-time4:" + (time5-time4) + ", time6-time5" + (time6-time5));


        return new FlowResult(flow, dualGraph, source, sink);
    }

    private void createModifiedSubgraph(Graph<Vertex> modifiedGraph,
                                        List<Vertex> sourceBoundary,
                                        List<Vertex> sinkBoundary,
                                        HashSet<VertexOfDualGraph> sourceNeighbors,
                                        HashSet<VertexOfDualGraph> sinkNeighbors,
                                        List<Vertex> externalBoundary,
                                        Graph<VertexOfDualGraph> dualGraph) {
        Set<Vertex> allowedVertices = new HashSet<>();
        for (VertexOfDualGraph face : dualGraph.verticesArray()) {
            if (face.getVerticesOfFace() != null) {
                allowedVertices.addAll(face.getVerticesOfFace());
            }
        }

        Set<Vertex> sourceFaceVertices = new HashSet<>();
        Set<Vertex> sinkFaceVertices = new HashSet<>();
        Set<Vertex> sourceBoundarySet = new HashSet<>(sourceBoundary);
        Set<Vertex> sinkBoundarySet = new HashSet<>(sinkBoundary);

        for (VertexOfDualGraph face : sourceNeighbors) {
            if (face.getVerticesOfFace() != null) {
                sourceFaceVertices.addAll(face.getVerticesOfFace());
            }
        }

        for (VertexOfDualGraph face : sinkNeighbors) {
            if (face.getVerticesOfFace() != null) {
                sinkFaceVertices.addAll(face.getVerticesOfFace());
            }
        }

        modifiedGraph.addBoundEdges(sourceBoundary);
        modifiedGraph.addBoundEdges(sinkBoundary);
        modifiedGraph.addBoundEdges(externalBoundary);

        int skippedZeroVertices = 0;
        int addedVertices = 0;
        for (Vertex v: initGraph.verticesArray()) {
            if (v.getName() == 0) {
                skippedZeroVertices++;
                continue;
            }
            
            if (allowedVertices.contains(v) &&
                !sourceFaceVertices.contains(v) && 
                !sinkFaceVertices.contains(v) &&
                !sinkBoundarySet.contains(v) &&
                !sourceBoundarySet.contains(v) &&
                !externalBoundary.contains(v)) {
                modifiedGraph.addVertexInSubgraph(v, initGraph);
                addedVertices++;
            }
        }

        System.out.println("Modified graph: added " + addedVertices + " vertices from dual graph faces");
        if (skippedZeroVertices > 0) {
            System.out.println("Skipped " + skippedZeroVertices + " vertices with name=0 to avoid coordinate conflicts");
        }
    }

    private DijkstraResult dijkstraMultiSource(Graph<Vertex> graph, List<Vertex> sourceVertices, List<Vertex> targetBoundary) {
        System.out.println("=== Dijkstra Multi-Source Debug ===");
        System.out.println("  Graph vertices: " + graph.verticesArray().size());
        System.out.println("  Source vertices: " + sourceVertices.size() + " " + 
                sourceVertices.stream().map(Vertex::getName).limit(5).collect(Collectors.toList()));
        System.out.println("  Target boundary: " + targetBoundary.size() + " vertices");
        
        // Проверяем соседей у source vertices
        for (Vertex src : sourceVertices) {
            if (graph.getEdges().containsKey(src)) {
                System.out.println("  Source vertex " + src.getName() + " has " + 
                        graph.getEdges().get(src).size() + " neighbors: " + 
                        graph.getEdges().get(src).keySet().stream()
                                .limit(10).map(Vertex::getName).collect(Collectors.toList()));
            } else {
                System.out.println("  Source vertex " + src.getName() + " NOT IN GRAPH!");
            }
        }
        
        Map<Vertex, Double> distances = new HashMap<>();
        Map<Vertex, Vertex> previous = new HashMap<>();
        PriorityQueue<VertexDistance> queue = new PriorityQueue<>(Comparator.comparingDouble(vd -> vd.distance));

        for (Vertex v : graph.verticesArray()) {
            distances.put(v, Double.MAX_VALUE);
        }

        for (Vertex sourceVertex : sourceVertices) {
            distances.put(sourceVertex, 0.0);
            queue.add(new VertexDistance(sourceVertex, 0.0));
        }

        Vertex targetVertex = null;
        double minDistance = Double.MAX_VALUE;
        int visitedVertices = 0;
        Set<Long> processedVertices = new HashSet<>();

        while (!queue.isEmpty()) {
            visitedVertices++;
            VertexDistance current = queue.poll();

            if (current.distance > distances.get(current.vertex)) {
                continue;
            }

            processedVertices.add(current.vertex.getName());

            if (targetBoundary.contains(current.vertex)) {
                if (current.distance < minDistance) {
                    minDistance = current.distance;
                    targetVertex = current.vertex;
                }
                continue;
            }

            if (graph.getEdges().get(current.vertex) != null) {
                for (Map.Entry<Vertex, Edge> entry : graph.getEdges().get(current.vertex).entrySet()) {
                    Vertex neighbor = entry.getKey();
                    double edgeLength = entry.getValue().length;
                    double newDistance = distances.get(current.vertex) + edgeLength;

                    if (newDistance < distances.get(neighbor)) {
                        distances.put(neighbor, newDistance);
                        previous.put(neighbor, current.vertex);
                        queue.add(new VertexDistance(neighbor, newDistance));
                    }
                }
            }
        }

        if (targetVertex == null) {
            return null;
        }

        List<Vertex> path = new ArrayList<>();
        Vertex current = targetVertex;
        while (current != null) {
            path.add(0, current);
            current = previous.get(current);
        }

        return new DijkstraResult(path, minDistance, previous, distances, new ArrayList<>());
    }

    private DijkstraResult dijkstraMultiSourceWithRegionWeights(
            Graph<Vertex> graph,
            List<Vertex> sourceVertices,
            List<Vertex> targetBoundary,
            Graph<VertexOfDualGraph> dualGraph,
            List<Vertex> sourceCorners,
            List<Vertex> sinkCorners,
            boolean isSourceSide) {

        DijkstraResult defaultResult = dijkstraMultiSource(graph, sourceVertices, targetBoundary);
        if (defaultResult == null) {
            return null;
        }

        Set<Long> boundaryNames = targetBoundary.stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());

        List<Vertex> boundaryLeaves = new ArrayList<>();
        for (Vertex v : graph.verticesArray()) {
            if (boundaryNames.contains(v.getName()) && defaultResult.distances.get(v) < Double.MAX_VALUE) {
                boundaryLeaves.add(v);
            }
        }

        defaultResult.boundaryLeaves = boundaryLeaves;

        Vertex singleSource = sourceVertices.size() == 1 ? sourceVertices.get(0) : null;
        if (singleSource != null && dualGraph != null) {
            SPTWithRegionWeights spt = buildSPTWithRegionWeights(
                    graph, defaultResult.previous, singleSource, targetBoundary, dualGraph,
                    sourceCorners, sinkCorners, isSourceSide);
            defaultResult.leftRegionWeights = spt.leftRegionWeights;
            defaultResult.boundaryLeaves = spt.boundaryLeaves;
            defaultResult.totalRegionWeight = spt.totalRegionWeight;
        }

        return defaultResult;
    }

    private double fillFlowInDualGraph(List<Vertex> path, Graph<VertexOfDualGraph> dualGraph) {
        if (path.size() < 2) {
            return 0.0;
        }
        double totalFlow = 0.0;
        var edgeToDualVertexMap = initGraph.getEdgeToDualVertexMap();

        for (int i = 0; i < path.size() - 1; i++) {
            Vertex v1 = path.get(i);
            Vertex v2 = path.get(i + 1);

            VertexOfDualGraph face1 = findFaceContainingEdge(v1, v2, dualGraph);
            VertexOfDualGraph face2 = findFaceContainingEdge(v2, v1, dualGraph);

            if (face1 != null && face2 != null && dualGraph.getEdges().get(face1) != null) {
                if (dualGraph.getEdges().get(face1).containsKey(face2)) {
                    Edge dualEdge1 = dualGraph.getEdges().get(face1).get(face2);
                    double bandwidth1 = dualEdge1.getBandwidth();
                    dualEdge1.flow = bandwidth1;

                    if (dualGraph.getEdges().get(face2) != null && dualGraph.getEdges().get(face2).containsKey(face1)) {
                        Edge dualEdge2 = dualGraph.getEdges().get(face2).get(face1);
                        dualEdge2.flow = dualEdge2.getBandwidth();
                    }

                    totalFlow += bandwidth1;
                }
            }
        }

        return totalFlow;
    }

    private VertexOfDualGraph findFaceContainingEdge(Vertex v1, Vertex v2, Graph<VertexOfDualGraph> dualGraph) {
        for (VertexOfDualGraph face : dualGraph.verticesArray()) {
            List<Vertex> faceVertices = face.getVerticesOfFace();
            if (faceVertices != null) {
                for (int i = 0; i < faceVertices.size(); i++) {
                    Vertex fv1 = faceVertices.get(i);
                    Vertex fv2 = faceVertices.get((i + 1) % faceVertices.size());
                    if (fv1.equals(v1) && fv2.equals(v2)) {
                        return face;
                    }
                }
            }
        }
        return null;
    }

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

    private void buildAndSortChildrenMap(SPTWithRegionWeights spt, Graph<Vertex> graph, Map<Vertex, Vertex> previous) {
        for (Vertex v : graph.verticesArray()) {
            spt.children.put(v, new ArrayList<>());
        }
        
        for (Map.Entry<Vertex, Vertex> entry : previous.entrySet()) {
            Vertex child = entry.getKey();
            Vertex parent = entry.getValue();
            if (parent != null) {
                spt.children.get(parent).add(child);
            }
        }
        
        var orderedEdges = graph.arrangeByAngle();
        for (Vertex v : spt.children.keySet()) {
            List<Vertex> childrenList = spt.children.get(v);
            if (childrenList.size() > 1 && orderedEdges.containsKey(v)) {
                var angleOrderedEdges = orderedEdges.get(v).stream().toList();
                
                Map<Long, Integer> vertexToAngleIndex = new HashMap<>();
                for (int i = 0; i < angleOrderedEdges.size(); i++) {
                    vertexToAngleIndex.put(angleOrderedEdges.get(i).end.getName(), i);
                }
                
                childrenList.sort((a, b) -> {
                    int idxA = vertexToAngleIndex.getOrDefault(a.getName(), 0);
                    int idxB = vertexToAngleIndex.getOrDefault(b.getName(), 0);
                    return Integer.compare(idxA, idxB);
                });
            }
        }
    }

    private Map<Long, Integer> buildBoundaryOrderMap(List<Vertex> externalBoundary) {
        Map<Long, Integer> boundaryOrderMap = new HashMap<>();
        for (int i = 0; i < externalBoundary.size(); i++) {
            boundaryOrderMap.put(externalBoundary.get(i).getName(), i);
        }
        return boundaryOrderMap;
    }

    private int determineBoundarySegment(
            List<Vertex> externalBoundary,
            List<Vertex> boundaryVerticesInSPT,
            List<Vertex> relevantCorners,
            Map<Long, Integer> boundaryOrderMap,
            boolean isSourceSide) {
        
        int boundarySegmentStart = 0;
        int n = externalBoundary.size();
        
        if (relevantCorners.size() >= 2) {
            List<Integer> cornerPositions = new ArrayList<>();
            for (Vertex corner : relevantCorners) {
                Integer pos = boundaryOrderMap.get(corner.getName());
                if (pos != null) {
                    cornerPositions.add(pos);
                }
            }
            
            if (cornerPositions.size() >= 2) {
                Collections.sort(cornerPositions);
                int firstCornerPos = cornerPositions.get(0);
                int lastCornerPos = cornerPositions.get(cornerPositions.size() - 1);
                
                int countBetween = 0;
                int countOutside = 0;
                
                for (Vertex leaf : boundaryVerticesInSPT) {
                    int pos = boundaryOrderMap.getOrDefault(leaf.getName(), 0);
                    if (pos >= firstCornerPos && pos <= lastCornerPos) {
                        countBetween++;
                    } else {
                        countOutside++;
                    }
                }
                
                if (countBetween >= countOutside) {
                    boundarySegmentStart = firstCornerPos;
                } else {
                    boundarySegmentStart = lastCornerPos;
                }
                
                System.out.println("Boundary segment: corners at " + firstCornerPos + ", " + lastCornerPos + 
                        " -> using start=" + boundarySegmentStart + " (isSourceSide=" + isSourceSide + ")");
            }
        }
        
        return boundarySegmentStart;
    }

    private void sortBoundaryLeavesBySegment(
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
        
        System.out.println("Boundary leaves ordering: segmentStart=" + boundarySegmentStart + 
                ", numLeaves=" + boundaryVerticesInSPT.size());
    }

    private Set<String> buildSPTEdgesSet(Map<Vertex, Vertex> previous) {
        Set<String> sptEdges = new HashSet<>();
        for (Map.Entry<Vertex, Vertex> entry : previous.entrySet()) {
            Vertex child = entry.getKey();
            Vertex parent = entry.getValue();
            if (parent != null) {
                sptEdges.add(child.getName() + "_" + parent.getName());
                sptEdges.add(parent.getName() + "_" + child.getName());
            }
        }
        return sptEdges;
    }

    private Map<String, VertexOfDualGraph> buildEdgeToLeftFaceMap(Graph<VertexOfDualGraph> dualGraph) {
        Map<String, VertexOfDualGraph> edgeToLeftFace = new HashMap<>();
        for (VertexOfDualGraph face : dualGraph.verticesArray()) {
            List<Vertex> faceVertices = face.getVerticesOfFace();
            if (faceVertices == null || faceVertices.isEmpty()) continue;

                for (int i = 0; i < faceVertices.size(); i++) {
                Vertex v1 = faceVertices.get(i);
                Vertex v2 = faceVertices.get((i + 1) % faceVertices.size());
                String edgeKey = v1.getName() + "_" + v2.getName();
                edgeToLeftFace.put(edgeKey, face);
            }
        }
        return edgeToLeftFace;
    }

    private static class VertexDistance {
        Vertex vertex;
        double distance;

        VertexDistance(Vertex vertex, double distance) {
            this.vertex = vertex;
            this.distance = distance;
        }
    }

    public static class DijkstraResult {
        List<Vertex> path;
        double distance;
        public Map<Vertex, Vertex> previous;
        public Map<Vertex, Double> distances;
        public List<Vertex> boundaryLeaves;
        public Map<Vertex, Double> leftRegionWeights;
        public double totalRegionWeight;

        DijkstraResult(List<Vertex> path, double distance, Map<Vertex, Vertex> previous, 
                       Map<Vertex, Double> distances, List<Vertex> boundaryLeaves) {
            this.path = path;
            this.distance = distance;
            this.previous = previous;
            this.distances = distances;
            this.boundaryLeaves = boundaryLeaves;
            this.leftRegionWeights = new HashMap<>();
            this.totalRegionWeight = 0.0;
        }
    }

    private static class SPTWithRegionWeights {
        List<VertexOfDualGraph> faces;
        List<Double> regionWeights;
        Vertex root;
        Map<Vertex, Vertex> previous;
        Map<Vertex, List<Vertex>> children;
        List<Vertex> boundaryLeaves;
        Map<Vertex, Double> leftRegionWeights;
        double totalRegionWeight;

        SPTWithRegionWeights(Vertex root) {
            this.root = root;
            this.previous = new HashMap<>();
            this.children = new HashMap<>();
            this.boundaryLeaves = new ArrayList<>();
            this.leftRegionWeights = new HashMap<>();
            this.totalRegionWeight = 0.0;
            this.faces = new ArrayList<>();
            this.regionWeights = new ArrayList<>();
        }
    }

    private SPTWithRegionWeights buildSPTWithRegionWeights(
            Graph<Vertex> graph,
            Map<Vertex, Vertex> previous,
            Vertex sourceVertex,
            List<Vertex> externalBoundary,
            Graph<VertexOfDualGraph> dualGraph,
            List<Vertex> sourceCorners,
            List<Vertex> sinkCorners,
            boolean isSourceSide) {

        SPTWithRegionWeights spt = new SPTWithRegionWeights(sourceVertex);
        spt.previous = new HashMap<>(previous);

        buildAndSortChildrenMap(spt, graph, previous);
        
        Set<Long> boundaryNames = externalBoundary.stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());
        
        List<Vertex> boundaryVerticesInSPT = new ArrayList<>();
        for (Vertex v : graph.verticesArray()) {
            if (boundaryNames.contains(v.getName()) && previous.containsKey(v)) {
                boundaryVerticesInSPT.add(v);
            }
        }
        
        Map<Long, Integer> boundaryOrderMap = buildBoundaryOrderMap(externalBoundary);
        
        List<Vertex> relevantCorners = isSourceSide ? sourceCorners : sinkCorners;
        int boundarySegmentStart = determineBoundarySegment(
                externalBoundary, boundaryVerticesInSPT, relevantCorners, 
                boundaryOrderMap, isSourceSide);
        
        sortBoundaryLeavesBySegment(boundaryVerticesInSPT, boundaryOrderMap, 
                boundarySegmentStart, externalBoundary.size());
        
        spt.boundaryLeaves = boundaryVerticesInSPT;
        
        computeRegionWeightsByEulerTour(spt, graph, dualGraph);
        
        return spt;
    }

    private void computeRegionWeightsByEulerTour(
            SPTWithRegionWeights spt,
            Graph<Vertex> graph,
            Graph<VertexOfDualGraph> dualGraph) {

        if (spt.boundaryLeaves.isEmpty()) {
            return;
        }

        Set<String> sptEdges = buildSPTEdgesSet(spt.previous);
        Map<String, VertexOfDualGraph> edgeToLeftFace = buildEdgeToLeftFaceMap(dualGraph);

        var sortedEdgesByVertex = graph.arrangeByAngle();

        Set<Long> sptVertexNames = new HashSet<>();
        sptVertexNames.add(spt.root.getName());
        for (Vertex v : spt.previous.keySet()) {
            sptVertexNames.add(v.getName());
        }

        Set<Long> boundaryLeafNames = spt.boundaryLeaves.stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());

        Set<Long> visitedFaces = new HashSet<>();

        int[] currentLeafIndex = {0};
        double[] cumulativeWeight = {0.0};

        System.out.println("SPT Region weights computation (Euler tour):");
        System.out.println("  SPT has " + sptEdges.size() / 2 + " edges");
        System.out.println("  SPT has " + sptVertexNames.size() + " vertices");

        eulerTourDFS(
                spt.root,
                null,
                spt,
                sortedEdgesByVertex,
                sptEdges,
                sptVertexNames,
                boundaryLeafNames,
                edgeToLeftFace,
                visitedFaces,
                cumulativeWeight,
                currentLeafIndex
        );

        spt.totalRegionWeight = cumulativeWeight[0];

        System.out.println("  Total region weight: " + spt.totalRegionWeight);
        System.out.println("  Visited faces: " + visitedFaces.size());
    }

    private void eulerTourDFS(
            Vertex current,
            Vertex parent,
            SPTWithRegionWeights spt,
            Map<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedEdgesByVertex,
            Set<String> sptEdges,
            Set<Long> sptVertexNames,
            Set<Long> boundaryLeafNames,
            Map<String, VertexOfDualGraph> edgeToLeftFace,
            Set<Long> visitedFaces,
            double[] cumulativeWeight,
            int[] currentLeafIndex) {

        TreeSet<EdgeOfGraph<Vertex>> edgesFromCurrent = sortedEdgesByVertex.get(current);
        if (edgesFromCurrent == null || edgesFromCurrent.isEmpty()) {
            return;
        }

        EdgeOfGraph<Vertex> startEdge = null;
        if (parent != null) {
            for (EdgeOfGraph<Vertex> edge : edgesFromCurrent) {
                if (edge.end.getName() == parent.getName()) {
                    startEdge = edge;
                    break;
                }
            }
        }

        List<EdgeOfGraph<Vertex>> edgesList = new ArrayList<>(edgesFromCurrent);
        int startIdx = 0;
        if (startEdge != null) {
            for (int i = 0; i < edgesList.size(); i++) {
                if (edgesList.get(i).end.getName() == parent.getName()) {
                    startIdx = (i + 1) % edgesList.size();
                    break;
                }
            }
        }

        for (int i = 0; i < edgesList.size(); i++) {
            int idx = (startIdx + i) % edgesList.size();
            EdgeOfGraph<Vertex> edge = edgesList.get(idx);
            Vertex neighbor = edge.end;

            if (parent != null && neighbor.getName() == parent.getName()) {
                continue;
            }

            String edgeKey = current.getName() + "_" + neighbor.getName();
            boolean isTreeEdge = sptEdges.contains(edgeKey);
            boolean neighborInSPT = sptVertexNames.contains(neighbor.getName());

            if (isTreeEdge && neighborInSPT) {
                eulerTourDFS(
                        neighbor,
                        current,
                        spt,
                        sortedEdgesByVertex,
                        sptEdges,
                        sptVertexNames,
                        boundaryLeafNames,
                        edgeToLeftFace,
                        visitedFaces,
                        cumulativeWeight,
                        currentLeafIndex
                );
            } else if (!isTreeEdge) {
                String reverseEdgeKey = neighbor.getName() + "_" + current.getName();
                VertexOfDualGraph rightFace = edgeToLeftFace.get(reverseEdgeKey);

                if (rightFace != null && !visitedFaces.contains(rightFace.getName())) {
                    visitedFaces.add(rightFace.getName());

                    cumulativeWeight[0] += rightFace.getWeight();

                }
            }
        }

        if (boundaryLeafNames.contains(current.getName())) {
            spt.leftRegionWeights.put(current, cumulativeWeight[0]);
            System.out.println("  Leaf " + currentLeafIndex[0] + " (vertex " + current.getName() +
                    "): cumulative=" + cumulativeWeight[0]);
            currentLeafIndex[0]++;
        }
    }
}