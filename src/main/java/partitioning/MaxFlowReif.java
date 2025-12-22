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

        Graph<Vertex> modifiedGraph = new Graph<>();
        createModifiedSubgraph(modifiedGraph, sourceBoundary, sinkBoundary, sourceNeighbors, sinkNeighbors, dualGraph);
        
        // Проверяем что границы присутствуют в modifiedGraph
        int sourceBoundaryInGraph = 0;
        int sinkBoundaryInGraph = 0;
        for (Vertex v : sourceBoundary) {
            if (modifiedGraph.getEdges().containsKey(v)) sourceBoundaryInGraph++;
        }
        for (Vertex v : sinkBoundary) {
            if (modifiedGraph.getEdges().containsKey(v)) sinkBoundaryInGraph++;
        }
        System.out.println("Boundaries in modifiedGraph: source=" + sourceBoundaryInGraph + "/" + sourceBoundary.size() + 
                           ", sink=" + sinkBoundaryInGraph + "/" + sinkBoundary.size());

        // Получаем externalBoundary заранее для дампа
        HashSet<VertexOfDualGraph> allDualVerticesSet = new HashSet<>(allDualVertices);
        allDualVerticesSet.remove(source);
        allDualVerticesSet.remove(sink);
        List<Vertex> externalBoundary = BoundSearcher.findBound(initGraph, allDualVerticesSet, comparisonForDualGraph);
        
        if (externalBoundary == null || externalBoundary.isEmpty()) {
            System.out.println("ERROR: External boundary is empty!");
            return new FlowResult(0, dualGraph, source, sink);
        }
        System.out.println("External boundary size: " + externalBoundary.size());

        DijkstraResult shortestPathResult = dijkstraMultiSource(modifiedGraph, sourceBoundary, sinkBoundary);

        if (shortestPathResult == null || shortestPathResult.path.isEmpty()) {
            System.out.println("ERROR: No shortest path found between source and sink boundaries!");
            System.out.println("  modifiedGraph vertices: " + modifiedGraph.verticesArray().size());
            System.out.println("  sourceBoundary size: " + sourceBoundary.size());
            System.out.println("  sinkBoundary size: " + sinkBoundary.size());
            
            // Дамп визуализации для отладки
            dumpVisualizationData(externalBoundary, sourceBoundary, sinkBoundary, 
                                 new ArrayList<>(), new ArrayList<>(), new HashMap<>(), 
                                 sourceNeighbors, sinkNeighbors, 0);
            
            return new FlowResult(0, dualGraph, source, sink);
        }
        
        System.out.println("Found shortest path with " + shortestPathResult.path.size() + " vertices, distance: " + shortestPathResult.distance);

        List<Vertex> shortestPath = shortestPathResult.path;

        Map<Long, NeighborSplit> neighborSplits;
        try {
            neighborSplits = preprocessNeighborSplits(modifiedGraph, shortestPath, sourceBoundary, sinkBoundary);
            System.out.println("Created " + neighborSplits.size() + " neighbor splits for " + shortestPath.size() + " vertices in path");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("broken boundary")) {
                System.out.println("Broken boundary detected: " + e.getMessage());
                dumpVisualizationData(externalBoundary, sourceBoundary, sinkBoundary, shortestPath, null, new HashMap<>(), sourceNeighbors, sinkNeighbors, 0);
                return new FlowResult(0, dualGraph, source, sink);
            }
            throw e;
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
                System.out.println("ERROR: Path to external boundary not found!");
                System.out.println("  Split vertex original ID: " + splitToOriginalMap.get(splitVertex1).getName());
                System.out.println("  Path1ToBoundary: " + (path1ToBoundary != null ? "found" : "NULL"));
                System.out.println("  Path2ToBoundary: " + (path2ToBoundary != null ? "found" : "NULL"));
                System.out.println("  External boundary size: " + externalBoundary.size());
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

    private void createModifiedSubgraph(Graph<Vertex> modifiedGraph,
                                        List<Vertex> sourceBoundary,
                                        List<Vertex> sinkBoundary,
                                        HashSet<VertexOfDualGraph> sourceNeighbors,
                                        HashSet<VertexOfDualGraph> sinkNeighbors,
                                        Graph<VertexOfDualGraph> dualGraph) {
        // Собираем все вершины из граней двойственного графа
        Set<Vertex> allowedVertices = new HashSet<>();
        for (VertexOfDualGraph face : dualGraph.verticesArray()) {
            if (face.getVerticesOfFace() != null) {
                allowedVertices.addAll(face.getVerticesOfFace());
            }
        }

        // Собираем вершины source и sink граней для исключения
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

        // Сначала добавляем границы
        modifiedGraph.addBoundEdges(sourceBoundary);
        modifiedGraph.addBoundEdges(sinkBoundary);

        int skippedZeroVertices = 0;
        int addedVertices = 0;
        for (Vertex v: initGraph.verticesArray()) {
            // Пропускаем вершины с name = 0, так как они дублируют существующие вершины
            if (v.getName() == 0) {
                skippedZeroVertices++;
                continue;
            }
            
            // Добавляем вершину только если она в allowedVertices и не в source/sink гранях
            if (allowedVertices.contains(v) && 
                !sourceFaceVertices.contains(v) && 
                !sinkFaceVertices.contains(v)) {
                modifiedGraph.addVertexInSubgraph(v, initGraph);
                addedVertices++;
            }
        }
        
        // Добавляем ребра от границ к их соседям (которые уже в графе)
        for (Vertex boundaryVertex : sourceBoundary) {
            if (initGraph.getEdges().containsKey(boundaryVertex)) {
                for (Map.Entry<Vertex, Edge> neighborEntry : initGraph.getEdges().get(boundaryVertex).entrySet()) {
                    Vertex neighbor = neighborEntry.getKey();
                    Edge edge = neighborEntry.getValue();
                    // Добавляем ребро только если сосед уже в modifiedGraph
                    if (modifiedGraph.getEdges().containsKey(neighbor)) {
                        modifiedGraph.addEdge(boundaryVertex, neighbor, edge.length, edge.getBandwidth());
                    }
                }
            }
        }
        
        for (Vertex boundaryVertex : sinkBoundary) {
            if (initGraph.getEdges().containsKey(boundaryVertex)) {
                for (Map.Entry<Vertex, Edge> neighborEntry : initGraph.getEdges().get(boundaryVertex).entrySet()) {
                    Vertex neighbor = neighborEntry.getKey();
                    Edge edge = neighborEntry.getValue();
                    // Добавляем ребро только если сосед уже в modifiedGraph
                    if (modifiedGraph.getEdges().containsKey(neighbor)) {
                        modifiedGraph.addEdge(boundaryVertex, neighbor, edge.length, edge.getBandwidth());
                    }
                }
            }
        }
        
        System.out.println("Modified graph: added " + addedVertices + " vertices from dual graph faces");
        if (skippedZeroVertices > 0) {
            System.out.println("Skipped " + skippedZeroVertices + " vertices with name=0 to avoid coordinate conflicts");
        }
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
            writeDualGraph(outputDir + "dual_graph.txt", dualGraph, source, sink);
            writeDualGraphWithFlow(outputDir + "dual_graph_flow.txt", dualGraph, source, sink);
            writePrimalGraph(outputDir + "primal_graph.txt", initGraph, sourceNeighbors, sinkNeighbors);

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
        System.out.println("Writing neighbor splits, total: " + neighborSplits.size());
        try (java.io.FileWriter writer = new java.io.FileWriter(filename)) {

            for (Map.Entry<Long, NeighborSplit> entry : neighborSplits.entrySet()) {
                long vertexId = entry.getKey();
                NeighborSplit split = entry.getValue();

                // Используем сохраненную в NeighborSplit вершину
                Vertex vertex = split.vertex;
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
            System.out.println("Neighbor splits file written");
        }
    }

    private void writePrimalGraph(String filename, Graph<Vertex> graph,
                                  HashSet<VertexOfDualGraph> sourceNeighbors,
                                  HashSet<VertexOfDualGraph> sinkNeighbors) throws java.io.IOException {
        // Внешняя грань существует только в двойственном графе, не в исходном
        // Поэтому включаем все вершины, включая с name = 0
        
        try (java.io.FileWriter writer = new java.io.FileWriter(filename)) {
            // Записываем все вершины
            writer.write("VERTICES\n");
            int vertexCount = 0;
            for (Vertex v : graph.verticesArray()) {
                Vertex geoVertex = coordConversion.fromEuclidean(v);
                writer.write(String.format("%d %.10f %.10f\n",
                        v.getName(), geoVertex.x, geoVertex.y));
                vertexCount++;
            }
            System.out.println("Wrote " + vertexCount + " vertices to primal graph");

            // Записываем рёбра (неориентированные, каждое только один раз)
            writer.write("EDGES\n");
            Set<String> recordedEdges = new HashSet<>();
            int edgeCount = 0;

            for (Vertex v1 : graph.verticesArray()) {
                if (graph.getEdges().get(v1) == null) continue;

                for (Vertex v2 : graph.getEdges().get(v1).keySet()) {
                    long id1 = v1.getName();
                    long id2 = v2.getName();
                    String edgeKey = (id1 < id2) ? (id1 + "_" + id2) : (id2 + "_" + id1);

                    if (!recordedEdges.contains(edgeKey)) {
                        recordedEdges.add(edgeKey);
                        double length = graph.getEdges().get(v1).get(v2).length;
                        writer.write(String.format("%d %d %.6f\n",
                                Math.min(id1, id2), Math.max(id1, id2), length));
                        edgeCount++;
                    }
                }
            }
            System.out.println("Wrote " + edgeCount + " edges to primal graph");
        }
    }

    private void writeDualGraph(String filename, Graph<VertexOfDualGraph> dualGraph,
                                VertexOfDualGraph source, VertexOfDualGraph sink) throws java.io.IOException {
        try (java.io.FileWriter writer = new java.io.FileWriter(filename)) {
            // Записываем вершины двойственного графа (центроиды граней)
            writer.write("VERTICES\n");
            for (VertexOfDualGraph face : dualGraph.verticesArray()) {
                List<Vertex> faceVertices = face.getVerticesOfFace();
                if (faceVertices == null || faceVertices.isEmpty()) continue;

                double sumX = 0, sumY = 0;
                for (Vertex v : faceVertices) {
                    sumX += v.x;
                    sumY += v.y;
                }
                double centroidX = sumX / faceVertices.size();
                double centroidY = sumY / faceVertices.size();

                Vertex euclideanCentroid = new Vertex(face.getName(), centroidX, centroidY, 0);
                Vertex geoCentroid = coordConversion.fromEuclidean(euclideanCentroid);

                String type = "NORMAL";
                if (face.equals(source)) type = "SOURCE";
                else if (face.equals(sink)) type = "SINK";

                writer.write(String.format("%d %.10f %.10f %s\n",
                        face.getName(), geoCentroid.x, geoCentroid.y, type));
            }

            // Записываем рёбра без информации о потоке
            writer.write("EDGES\n");
            Set<String> recordedEdges = new HashSet<>();

            for (VertexOfDualGraph face : dualGraph.verticesArray()) {
                if (dualGraph.getEdges().get(face) == null) continue;

                for (Map.Entry<VertexOfDualGraph, Edge> edgeEntry : dualGraph.getEdges().get(face).entrySet()) {
                    VertexOfDualGraph neighbor = edgeEntry.getKey();
                    Edge edge = edgeEntry.getValue();

                    long id1 = face.getName();
                    long id2 = neighbor.getName();
                    String edgeKey = (id1 < id2) ? (id1 + "_" + id2) : (id2 + "_" + id1);

                    if (!recordedEdges.contains(edgeKey)) {
                        recordedEdges.add(edgeKey);

                        double bandwidth = edge.getBandwidth();
                        writer.write(String.format("%d %d %.6f\n",
                                Math.min(id1, id2), Math.max(id1, id2), bandwidth));
                    }
                }
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

    private static class NeighborSplit {
        Vertex vertex;  // Сама вершина, которую разделяем
        List<Vertex> pathNeighbors;
        List<Vertex> leftNeighbors;
        List<Vertex> rightNeighbors;

        NeighborSplit(Vertex vertex, List<Vertex> path, List<Vertex> left, List<Vertex> right) {
            this.vertex = vertex;
            this.pathNeighbors = path;
            this.leftNeighbors = left;
            this.rightNeighbors = right;
        }
    }

    private Map<Long, NeighborSplit> preprocessNeighborSplits(Graph<Vertex> graph, List<Vertex> path,
                                                               List<Vertex> sourceBoundary, List<Vertex> sinkBoundary) {
        System.out.println("Preprocessing neighbor splits for " + path.size() + " vertices in path");
        Map<Long, NeighborSplit> splits = new HashMap<>();

        Set<Long> sourceBoundaryNames = sourceBoundary.stream().map(Vertex::getName).collect(Collectors.toSet());
        Set<Long> sinkBoundaryNames = sinkBoundary.stream().map(Vertex::getName).collect(Collectors.toSet());

        for (int i = 0; i < path.size(); i++) {
            Vertex currentVertex = path.get(i);

            Vertex prevVertex = (i > 0) ? path.get(i - 1) : null;
            Vertex nextVertex = (i < path.size() - 1) ? path.get(i + 1) : null;

            boolean isFirstVertex = (prevVertex == null);
            boolean isLastVertex = (nextVertex == null);
            boolean isOnBothBoundaries = sourceBoundaryNames.contains(currentVertex.getName())
                    && sinkBoundaryNames.contains(currentVertex.getName());

            if (isOnBothBoundaries && (isFirstVertex || isLastVertex || path.size() == 1)) {
                NeighborSplit split = handleBoundaryIntersectionVertex(graph, currentVertex,
                        sourceBoundary, sinkBoundary);
                splits.put(currentVertex.getName(), split);
                continue;
            }

            double pathDirX, pathDirY;

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

            System.out.println("Vertex " + currentVertex.getName() + ": Path=" + pathNeighbors.size() + ", Left=" + leftNeighbors.size() + ", Right=" + rightNeighbors.size());
            splits.put(currentVertex.getName(), new NeighborSplit(currentVertex, pathNeighbors, leftNeighbors, rightNeighbors));
        }

        System.out.println("Preprocessed " + splits.size() + " neighbor splits");
        return splits;
    }

    private NeighborSplit handleBoundaryIntersectionVertex(Graph<Vertex> graph, Vertex currentVertex,
                                                           List<Vertex> sourceBoundary, List<Vertex> sinkBoundary) {
        List<Vertex> sourceNeighbors = new ArrayList<>();
        List<Vertex> sinkNeighbors = new ArrayList<>();

        for (int j = 0; j < sourceBoundary.size(); j++) {
            Vertex bv = sourceBoundary.get(j);
            if (bv.getName() == currentVertex.getName()) {
                sourceNeighbors.add(sourceBoundary.get((j + 1) % sourceBoundary.size()));
                sourceNeighbors.add(sourceBoundary.get((j - 1 + sourceBoundary.size()) % sourceBoundary.size()));
                break;
            }
        }

        for (int j = 0; j < sinkBoundary.size(); j++) {
            Vertex bv = sinkBoundary.get(j);
            if (bv.getName() == currentVertex.getName()) {
                sinkNeighbors.add(sinkBoundary.get((j + 1) % sinkBoundary.size()));
                sinkNeighbors.add(sinkBoundary.get((j - 1 + sinkBoundary.size()) % sinkBoundary.size()));
                break;
            }
        }

        Set<Long> sourceNeighborNames = sourceNeighbors.stream().map(Vertex::getName).collect(Collectors.toSet());
        Set<Long> sinkNeighborNames = sinkNeighbors.stream().map(Vertex::getName).collect(Collectors.toSet());

        List<Vertex> commonNeighbors = new ArrayList<>();
        List<Vertex> onlySourceNeighbors = new ArrayList<>();
        List<Vertex> onlySinkNeighbors = new ArrayList<>();

        for (Vertex v : sourceNeighbors) {
            if (sinkNeighborNames.contains(v.getName())) {
                commonNeighbors.add(v);
            } else {
                onlySourceNeighbors.add(v);
            }
        }

        for (Vertex v : sinkNeighbors) {
            if (!sourceNeighborNames.contains(v.getName())) {
                onlySinkNeighbors.add(v);
            }
        }

        System.out.println("Intersection vertex " + currentVertex.getName() +
                ": common=" + commonNeighbors.size() +
                ", onlySource=" + onlySourceNeighbors.size() +
                ", onlySink=" + onlySinkNeighbors.size());

        List<Vertex> pathNeighbors = new ArrayList<>();
        List<Vertex> leftNeighbors = new ArrayList<>();
        List<Vertex> rightNeighbors = new ArrayList<>();

        if (commonNeighbors.size() == 1 && onlySourceNeighbors.size() == 1 && onlySinkNeighbors.size() == 1) {
            leftNeighbors.addAll(commonNeighbors);
            rightNeighbors.addAll(onlySourceNeighbors);
            rightNeighbors.addAll(onlySinkNeighbors);
        }
        else if (commonNeighbors.size() == 2 && onlySourceNeighbors.isEmpty() && onlySinkNeighbors.isEmpty()) {
            leftNeighbors.add(commonNeighbors.get(0));
            rightNeighbors.add(commonNeighbors.get(1));
        }
        else if (commonNeighbors.isEmpty() && onlySourceNeighbors.size() == 2 && onlySinkNeighbors.size() == 2) {
            // Случай когда границы не пересекаются в соседях (4 разных соседа)
            // Сортируем source и sink отдельно по углу
            onlySourceNeighbors.sort((v1, v2) -> {
                double angle1 = Math.atan2(v1.y - currentVertex.y, v1.x - currentVertex.x);
                double angle2 = Math.atan2(v2.y - currentVertex.y, v2.x - currentVertex.x);
                return Double.compare(angle1, angle2);
            });
            
            onlySinkNeighbors.sort((v1, v2) -> {
                double angle1 = Math.atan2(v1.y - currentVertex.y, v1.x - currentVertex.x);
                double angle2 = Math.atan2(v2.y - currentVertex.y, v2.x - currentVertex.x);
                return Double.compare(angle1, angle2);
            });
            
            // Один source + один sink в каждую сторону
            // Меняем порядок для sink: первый sink вправо, второй влево
            leftNeighbors.add(onlySourceNeighbors.get(0));
            leftNeighbors.add(onlySinkNeighbors.get(1));
            rightNeighbors.add(onlySourceNeighbors.get(1));
            rightNeighbors.add(onlySinkNeighbors.get(0));
        }
        else {
            leftNeighbors.addAll(onlySourceNeighbors);
            leftNeighbors.addAll(commonNeighbors);
            rightNeighbors.addAll(onlySinkNeighbors);
        }

        System.out.println("Split result: path=" + pathNeighbors.size() +
                ", left=" + leftNeighbors.size() +
                ", right=" + rightNeighbors.size());

        return new NeighborSplit(currentVertex, pathNeighbors, leftNeighbors, rightNeighbors);
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