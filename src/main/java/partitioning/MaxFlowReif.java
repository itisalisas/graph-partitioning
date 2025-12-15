package partitioning;

import java.util.*;
import java.util.stream.Collectors;

import graph.*;
import readWrite.CoordinateConversion;

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
        HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph = new HashMap<>();
        List<VertexOfDualGraph> allDualVertices = dualGraph.verticesArray();

        for (VertexOfDualGraph dualVertex : allDualVertices) {
            if (!dualVertex.equals(source) && !dualVertex.equals(sink)) {
                comparisonForDualGraph.put(dualVertex, dualVertex);
            }
        }

        HashSet<VertexOfDualGraph> sourceNeighbors = new HashSet<>();
        HashSet<VertexOfDualGraph> sinkNeighbors = new HashSet<>();

        for (VertexOfDualGraph neighbor : dualGraph.getEdges().get(source).keySet()) {
            if (neighbor.equals(sink) || neighbor.equals(source)) {
                continue;
            }

            sourceNeighbors.add(neighbor);
        }

        for (VertexOfDualGraph neighbor : dualGraph.getEdges().get(sink).keySet()) {
            if (neighbor.equals(source) || neighbor.equals(sink)) {
                continue;
            }

            sinkNeighbors.add(neighbor);
        }

        List<Vertex> sourceBoundary = BoundSearcher.findBound(initGraph, sourceNeighbors, comparisonForDualGraph);
        List<Vertex> sinkBoundary = BoundSearcher.findBound(initGraph, sinkNeighbors, comparisonForDualGraph);

        Graph<Vertex> modifiedGraph = initGraph.clone();

        Set<Vertex> sourceFaceVertices = new HashSet<>();
        Set<Vertex> sinkFaceVertices = new HashSet<>();

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

        List<Vertex> verticesToRemove = new ArrayList<>();
        for (Vertex v : modifiedGraph.verticesArray()) {
            if (sourceFaceVertices.contains(v) && !sourceBoundary.contains(v)) {
                verticesToRemove.add(v);
            }
            else if (sinkFaceVertices.contains(v) && !sinkBoundary.contains(v)) {
                verticesToRemove.add(v);
            }
        }

        for (Vertex v : verticesToRemove) {
            modifiedGraph.deleteVertex(v);
        }

        keepOnlyBoundaryCycleEdges(modifiedGraph, sourceBoundary);
        keepOnlyBoundaryCycleEdges(modifiedGraph, sinkBoundary);

        DijkstraResult shortestPathResult = dijkstraMultiSource(modifiedGraph, sourceBoundary, sinkBoundary);

        if (shortestPathResult == null || shortestPathResult.path.isEmpty()) {
            return new FlowResult(0, dualGraph, source, sink);
        }

        List<Vertex> shortestPath = shortestPathResult.path;

        HashSet<VertexOfDualGraph> allDualVerticesSet = new HashSet<>(allDualVertices);
        allDualVerticesSet.remove(source);
        allDualVerticesSet.remove(sink);
        List<Vertex> externalBoundary = BoundSearcher.findBound(initGraph, allDualVerticesSet, comparisonForDualGraph);

        Map<Long, NeighborSplit> neighborSplits;
        try {
            neighborSplits = preprocessNeighborSplits(modifiedGraph, shortestPath, sourceBoundary, sinkBoundary);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("broken boundary")) {
                System.out.println("⚠ Broken boundary detected: " + e.getMessage());
                // Дампим визуализацию с тем что есть (без neighborSplits и bestPath)
                dumpVisualizationData(externalBoundary, sourceBoundary, sinkBoundary, shortestPath, null, new HashMap<>(), sourceNeighbors, sinkNeighbors, 0);
                return new FlowResult(0, dualGraph, source, sink);
            }
            throw e; // Если это другое исключение - пробрасываем дальше
        }

        Map<Vertex, Vertex> splitToOriginalMap = new HashMap<>();

        List<Map.Entry<Vertex, Vertex>> splitVertices = new ArrayList<>();
        for (Vertex pathVertex : shortestPath) {
            splitVertices.add(splitVertex(modifiedGraph, pathVertex, splitToOriginalMap, neighborSplits));
        }

        double minPathLength = Double.MAX_VALUE;
        List<Vertex> bestPath = null;

        for (Map.Entry<Vertex, Vertex> splitVertex : splitVertices) {
            Vertex splitVertex1 = splitVertex.getKey();
            Vertex splitVertex2 = splitVertex.getValue();

            DijkstraResult path1ToBoundary = dijkstraMultiSource(modifiedGraph, Collections.singletonList(splitVertex1), externalBoundary);
            DijkstraResult path2ToBoundary = dijkstraMultiSource(modifiedGraph, Collections.singletonList(splitVertex2), externalBoundary);

            if (path1ToBoundary == null || path2ToBoundary == null) {
                System.out.println("Null path: " + (path1ToBoundary == null) + " " + (path2ToBoundary == null));
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
            }
        }

        if (bestPath == null) {
            System.out.println("No path found");
            dumpVisualizationData(externalBoundary, sourceBoundary, sinkBoundary, shortestPath, bestPath, neighborSplits, sourceNeighbors, sinkNeighbors, 0);
            return new FlowResult(0, dualGraph, source, sink);
        }

        flow = fillFlowInDualGraph(bestPath, dualGraph);

        dumpVisualizationData(externalBoundary, sourceBoundary, sinkBoundary, shortestPath, bestPath, neighborSplits, sourceNeighbors, sinkNeighbors, flow);

        return new FlowResult(flow, dualGraph, source, sink);
    }

    private void dumpVisualizationData(List<Vertex> externalBoundary, List<Vertex> sourceBoundary,
                                       List<Vertex> sinkBoundary, List<Vertex> stPath, List<Vertex> bestPath,
                                       Map<Long, NeighborSplit> neighborSplits,
                                       HashSet<VertexOfDualGraph> sourceNeighbors,
                                       HashSet<VertexOfDualGraph> sinkNeighbors,
                                       double size) {
        try {
            String subDirName = String.format("flow_%d_%d_%f", sourceNeighbors.size(), sinkNeighbors.size(), size);
            String baseDir = "src/main/output/reif_flow_debug/";
            String outputDir = baseDir + subDirName + "/";

            java.io.File dir = new java.io.File(outputDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            writeVertexListToFile(outputDir + "external_boundary.txt", externalBoundary);
            writeVertexListToFile(outputDir + "source_boundary.txt", sourceBoundary);
            writeVertexListToFile(outputDir + "sink_boundary.txt", sinkBoundary);
            writeVertexListToFile(outputDir + "st_path.txt", stPath);

            if (bestPath != null) {
                writeVertexListToFile(outputDir + "best_path.txt", bestPath);
            }

            writeNeighborSplitsToFile(outputDir + "neighbor_splits.txt", neighborSplits);
            writeDualGraphWithFlow(outputDir + "dual_graph_flow.txt", dualGraph, source, sink);

            System.out.println("Visualization data saved to " + outputDir);
        } catch (Exception e) {
            System.err.println("Error saving visualization data: " + e.getMessage());
        }
    }

    private void writeVertexListToFile(String filename, List<Vertex> vertices) throws java.io.IOException {
        try (java.io.FileWriter writer = new java.io.FileWriter(filename)) {
            for (Vertex v : vertices) {
                Vertex geoVertex = coordConversion.fromEuclidean(v);
                writer.write(String.format("%d %.10f %.10f\n", geoVertex.getName(), geoVertex.x, geoVertex.y));
            }
        }
    }

    private void writeNeighborSplitsToFile(String filename, Map<Long, NeighborSplit> neighborSplits) throws java.io.IOException {
        try (java.io.FileWriter writer = new java.io.FileWriter(filename)) {

            for (Map.Entry<Long, NeighborSplit> entry : neighborSplits.entrySet()) {
                long vertexId = entry.getKey();
                NeighborSplit split = entry.getValue();

                Vertex vertex = null;
                for (Vertex v : initGraph.verticesArray()) {
                    if (v.getName() == vertexId) {
                        vertex = v;
                        break;
                    }
                }

                if (vertex == null) continue;

                Vertex geoVertex = coordConversion.fromEuclidean(vertex);
                writer.write(String.format("%d %.10f %.10f\n", geoVertex.getName(), geoVertex.x, geoVertex.y));

                for (Vertex neighbor : split.pathNeighbors) {
                    Vertex geoNeighbor = coordConversion.fromEuclidean(neighbor);
                    writer.write(String.format("PATH %d %.10f %.10f\n",
                            geoNeighbor.getName(), geoNeighbor.x, geoNeighbor.y));
                }

                for (Vertex neighbor : split.leftNeighbors) {
                    Vertex geoNeighbor = coordConversion.fromEuclidean(neighbor);
                    writer.write(String.format("LEFT %d %.10f %.10f\n",
                            geoNeighbor.getName(), geoNeighbor.x, geoNeighbor.y));
                }

                for (Vertex neighbor : split.rightNeighbors) {
                    Vertex geoNeighbor = coordConversion.fromEuclidean(neighbor);
                    writer.write(String.format("RIGHT %d %.10f %.10f\n",
                            geoNeighbor.getName(), geoNeighbor.x, geoNeighbor.y));
                }

                writer.write("---\n");
            }
        }
    }

    private void writeDualGraphWithFlow(String filename, Graph<VertexOfDualGraph> dualGraph,
                                        VertexOfDualGraph source, VertexOfDualGraph sink) throws java.io.IOException {
        try (java.io.FileWriter writer = new java.io.FileWriter(filename)) {
            // Сначала записываем вершины двойственного графа (центроиды граней)
            writer.write("VERTICES\n");
            for (VertexOfDualGraph face : dualGraph.verticesArray()) {
                // Вычисляем центроид грани
                List<Vertex> faceVertices = face.getVerticesOfFace();
                if (faceVertices == null || faceVertices.isEmpty()) continue;

                double sumX = 0, sumY = 0;
                for (Vertex v : faceVertices) {
                    sumX += v.x;
                    sumY += v.y;
                }
                double centroidX = sumX / faceVertices.size();
                double centroidY = sumY / faceVertices.size();

                // Конвертируем в географические координаты
                Vertex euclideanCentroid = new Vertex(face.getName(), centroidX, centroidY, 0);
                Vertex geoCentroid = coordConversion.fromEuclidean(euclideanCentroid);

                // Определяем тип вершины
                String type = "NORMAL";
                if (face.equals(source)) type = "SOURCE";
                else if (face.equals(sink)) type = "SINK";

                writer.write(String.format("%d %.10f %.10f %s\n",
                        face.getName(), geoCentroid.x, geoCentroid.y, type));
            }

            // Затем записываем рёбра с информацией о потоке
            writer.write("EDGES\n");
            Set<String> recordedEdges = new HashSet<>();

            for (VertexOfDualGraph face : dualGraph.verticesArray()) {
                if (dualGraph.getEdges().get(face) == null) continue;

                for (Map.Entry<VertexOfDualGraph, Edge> edgeEntry : dualGraph.getEdges().get(face).entrySet()) {
                    VertexOfDualGraph neighbor = edgeEntry.getKey();
                    Edge edge = edgeEntry.getValue();

                    // Создаём уникальный ключ для ребра (неориентированного)
                    long id1 = face.getName();
                    long id2 = neighbor.getName();
                    String edgeKey = (id1 < id2) ? (id1 + "_" + id2) : (id2 + "_" + id1);

                    // Записываем каждое ребро только один раз (как неориентированное)
                    if (!recordedEdges.contains(edgeKey)) {
                        recordedEdges.add(edgeKey);

                        double bandwidth = edge.getBandwidth();
                        double flow = edge.flow;
                        boolean isSaturated = Math.abs(flow - bandwidth) < 1e-9 && flow > 1e-9;

                        // Записываем в порядке возрастания ID для единообразия
                        writer.write(String.format("%d %d %.6f %.6f %s\n",
                                Math.min(id1, id2), Math.max(id1, id2), bandwidth, flow,
                                isSaturated ? "SATURATED" : "NORMAL"));
                    }
                }
            }
        }
    }

    private void keepOnlyBoundaryCycleEdges(Graph<Vertex> graph, List<Vertex> boundary) {
        if (boundary.size() < 2) return;

        Set<Long> boundaryNames = boundary.stream()
                .map(Vertex::getName)
                .collect(Collectors.toSet());

        for (Vertex v: boundary) {
            if (graph.getEdges().get(v) == null) {
                continue;
            }

            List<Vertex> edgesToDelete = new ArrayList<>();

            for (Vertex neighbor: graph.getEdges().get(v).keySet()) {
                if (boundaryNames.contains(neighbor.getName())) {
                    edgesToDelete.add(neighbor);
                }
            }

            for (Vertex neighbor : edgesToDelete) {
                graph.deleteEdge(v, neighbor);
            }
        }

        for (int i = 0; i < boundary.size(); i++) {
            Vertex current = boundary.get(i);
            Vertex next = boundary.get((i + 1) % boundary.size());

            Vertex currentInGraph = null;
            Vertex nextInGraph = null;

            for (Vertex v : graph.verticesArray()) {
                if (v.getName() == current.getName()) {
                    currentInGraph = v;
                }
                if (v.getName() == next.getName()) {
                    nextInGraph = v;
                }
            }

            if (currentInGraph != null && nextInGraph != null) {
                double edgeLength = currentInGraph.getLength(nextInGraph);
                graph.addEdge(currentInGraph, nextInGraph, edgeLength);
            }
        }
    }


    private static class NeighborSplit {
        List<Vertex> pathNeighbors;
        List<Vertex> leftNeighbors;
        List<Vertex> rightNeighbors;

        NeighborSplit(List<Vertex> path, List<Vertex> left, List<Vertex> right) {
            this.pathNeighbors = path;
            this.leftNeighbors = left;
            this.rightNeighbors = right;
        }
    }

    private Map<Long, NeighborSplit> preprocessNeighborSplits(Graph<Vertex> graph, List<Vertex> path,
                                                              List<Vertex> sourceBoundary, List<Vertex> sinkBoundary) {
        Map<Long, NeighborSplit> splits = new HashMap<>();

        for (int i = 0; i < path.size(); i++) {
            Vertex currentVertex = path.get(i);

            Vertex prevVertex = (i > 0) ? path.get(i - 1) : null;
            Vertex nextVertex = (i < path.size() - 1) ? path.get(i + 1) : null;

            double pathDirX, pathDirY;
            boolean isFirstVertex = (prevVertex == null);
            boolean isLastVertex = (nextVertex == null);

            if (!isFirstVertex && !isLastVertex) {
                pathDirX = (nextVertex.x - prevVertex.x);
                pathDirY = (nextVertex.y - prevVertex.y);
            } else {
                List<Vertex> boundaryToUse = isFirstVertex ? sourceBoundary : sinkBoundary;
                List<Vertex> boundaryNeighbors = new ArrayList<>();
                for (int j = 0; j < boundaryToUse.size(); j++) {
                    Vertex bv = boundaryToUse.get(j);
                    if (bv.getName() == currentVertex.getName()) {
                        boundaryNeighbors.add(boundaryToUse.get((j + 1) % boundaryToUse.size()));
                        boundaryNeighbors.add(boundaryToUse.get((j - 1 + boundaryToUse.size()) % boundaryToUse.size()));
                        break;
                    }
                }

                System.out.println("Boundary neighbors: " + boundaryNeighbors.size());
                if (boundaryNeighbors.size() == 2) {
                    Vertex bn1 = boundaryNeighbors.get(0);
                    Vertex bn2 = boundaryNeighbors.get(1);

                    double vec1X = bn1.x - currentVertex.x;
                    double vec1Y = bn1.y - currentVertex.y;
                    double vec2X = bn2.x - currentVertex.x;
                    double vec2Y = bn2.y - currentVertex.y;

                    double len1 = Math.sqrt(vec1X * vec1X + vec1Y * vec1Y);
                    double len2 = Math.sqrt(vec2X * vec2X + vec2Y * vec2Y);
                    if (len1 > 1e-10) { vec1X /= len1; vec1Y /= len1; }
                    if (len2 > 1e-10) { vec2X /= len2; vec2Y /= len2; }

                    pathDirX = vec1X + vec2X;
                    pathDirY = vec1Y + vec2Y;
                } else {
                    System.out.println("BROKEN BOUNDARY FOR VERTEX " + currentVertex.getName() + " NEIGHBORS: " + boundaryNeighbors.stream().map(Vertex::getName).collect(Collectors.toList()));
                    for (Vertex neighbor : boundaryToUse) {
                        if (graph.getEdges().get(neighbor) != null) {
                            System.out.println("vertex: " + neighbor.getName() + " neighbors size: " + graph.getEdges().get(neighbor).keySet().size() + " neighbors: " + graph.getEdges().get(neighbor).keySet().stream().map(Vertex::getName).collect(Collectors.toList()));
                        } else {
                            System.out.println("vertex: " + neighbor.getName() + " neighbors size: 0");
                        }
                    }
                    throw new RuntimeException("broken boundary, neighbors: " + boundaryNeighbors.size() + " neighbors: " + boundaryNeighbors.stream().map(Vertex::getName).collect(Collectors.toList()) + " currentVertex: " + currentVertex.getName());
                }
            }

            double pathLen = Math.sqrt(pathDirX * pathDirX + pathDirY * pathDirY);
            pathDirX /= pathLen;
            pathDirY /= pathLen;

            Set<Long> pathVertexNames = path.stream().map(Vertex::getName).collect(Collectors.toSet());

            List<Vertex> pathNeighbors = new ArrayList<>();
            List<Vertex> leftNeighbors = new ArrayList<>();
            List<Vertex> rightNeighbors = new ArrayList<>();

            for (Vertex neighbor : graph.getEdges().get(currentVertex).keySet()) {
                if (pathVertexNames.contains(neighbor.getName())) {
                    pathNeighbors.add(neighbor);
                } else {
                    double toNeighborX = neighbor.x - currentVertex.x;
                    double toNeighborY = neighbor.y - currentVertex.y;

                    double crossProduct = pathDirX * toNeighborY - pathDirY * toNeighborX;

                    if (crossProduct >= 0) {
                        leftNeighbors.add(neighbor);
                    } else {
                        rightNeighbors.add(neighbor);
                    }
                }
            }

            System.out.println("Path neighbors: " + pathNeighbors.size());
            System.out.println("Left neighbors: " + leftNeighbors.size());
            System.out.println("Right neighbors: " + rightNeighbors.size());
            splits.put(currentVertex.getName(), new NeighborSplit(pathNeighbors, leftNeighbors, rightNeighbors));
        }

        return splits;
    }

    private Map.Entry<Vertex, Vertex> splitVertex(Graph<Vertex> splitGraph, Vertex vertex,
                                                  Map<Vertex, Vertex> splitToOriginalMap,
                                                  Map<Long, NeighborSplit> neighborSplits) {
        long originalName = vertex.getName();
        Vertex splitVertex1 = new Vertex(originalName * 1000 + 1, vertex.x, vertex.y, vertex.getWeight());
        Vertex splitVertex2 = new Vertex(originalName * 1000 + 2, vertex.x, vertex.y, vertex.getWeight());

        splitToOriginalMap.put(splitVertex1, vertex);
        splitToOriginalMap.put(splitVertex2, vertex);

        splitGraph.addVertex(splitVertex1);
        splitGraph.addVertex(splitVertex2);

        Vertex vertexInGraph = null;
        for (Vertex v : splitGraph.verticesArray()) {
            if (v.getName() == originalName) {
                vertexInGraph = v;
                break;
            }
        }

        if (vertexInGraph == null || splitGraph.getEdges().get(vertexInGraph) == null) {
            return new AbstractMap.SimpleEntry<>(splitVertex1, splitVertex2);
        }

        List<Vertex> neighbors = new ArrayList<>(splitGraph.getEdges().get(vertexInGraph).keySet());

        if (neighbors.isEmpty()) {
            splitGraph.deleteVertex(vertexInGraph);
            return new AbstractMap.SimpleEntry<>(splitVertex1, splitVertex2);
        }

        NeighborSplit split = neighborSplits.get(originalName);

        if (split != null) {
            for (Vertex neighbor : split.pathNeighbors) {
                Vertex neighborInGraph = null;
                for (Vertex v : splitGraph.verticesArray()) {
                    if (v.getName() == neighbor.getName()) {
                        neighborInGraph = v;
                        break;
                    }
                }

                if (neighborInGraph != null) {
                    double edgeLength = splitGraph.getEdges().get(vertexInGraph).get(neighborInGraph).length;
                    splitGraph.addEdge(splitVertex1, neighborInGraph, edgeLength);
                    splitGraph.addEdge(splitVertex2, neighborInGraph, edgeLength);
                }
            }

            for (Vertex neighbor : split.leftNeighbors) {
                Vertex neighborInGraph = null;
                for (Vertex v : splitGraph.verticesArray()) {
                    if (v.getName() == neighbor.getName()) {
                        neighborInGraph = v;
                        break;
                    }
                }

                if (neighborInGraph != null) {
                    double edgeLength = splitGraph.getEdges().get(vertexInGraph).get(neighborInGraph).length;
                    splitGraph.addEdge(splitVertex1, neighborInGraph, edgeLength);
                }
            }

            for (Vertex neighbor : split.rightNeighbors) {
                Vertex neighborInGraph = null;
                for (Vertex v : splitGraph.verticesArray()) {
                    if (v.getName() == neighbor.getName()) {
                        neighborInGraph = v;
                        break;
                    }
                }

                if (neighborInGraph != null) {
                    double edgeLength = splitGraph.getEdges().get(vertexInGraph).get(neighborInGraph).length;
                    splitGraph.addEdge(splitVertex2, neighborInGraph, edgeLength);
                }
            }
        } else {
            System.out.println("No split found for vertex " + vertex.getName());
        }

        splitGraph.deleteVertex(vertexInGraph);

        return new AbstractMap.SimpleEntry<>(splitVertex1, splitVertex2);
    }


    private DijkstraResult dijkstraMultiSource(Graph<Vertex> graph, List<Vertex> sourceVertices, List<Vertex> targetBoundary) {
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

        while (!queue.isEmpty()) {
            VertexDistance current = queue.poll();

            if (current.distance > distances.get(current.vertex)) {
                continue;
            }

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

        return new DijkstraResult(path, minDistance);
    }

    private double fillFlowInDualGraph(List<Vertex> path, Graph<VertexOfDualGraph> dualGraph) {
        if (path.size() < 2) {
            return 0.0;
        }
        double totalFlow = 0.0;

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

    private static class VertexDistance {
        Vertex vertex;
        double distance;

        VertexDistance(Vertex vertex, double distance) {
            this.vertex = vertex;
            this.distance = distance;
        }
    }

    private static class DijkstraResult {
        List<Vertex> path;
        double distance;

        DijkstraResult(List<Vertex> path, double distance) {
            this.path = path;
            this.distance = distance;
        }
    }
}