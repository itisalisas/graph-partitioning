package readWrite;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import graph.Edge;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;
import partitioning.models.DijkstraResult;
import partitioning.models.NeighborSplit;

public class FlowWriter {

    public static void dumpVisualizationData(List<Vertex> externalBoundary, List<Vertex> sourceBoundary,
                                                  List<Vertex> sinkBoundary, List<Vertex> stPath, List<Vertex> bestPath,
                                                  Map<Long, NeighborSplit> neighborSplits,
                                                  HashSet<VertexOfDualGraph> sourceNeighbors,
                                                  HashSet<VertexOfDualGraph> sinkNeighbors,
                                                  double size,
                                                  Graph<Vertex> graph,
                                                  Graph<VertexOfDualGraph> dualGraph,
                                                  VertexOfDualGraph source,
                                                  VertexOfDualGraph sink,
                                                  CoordinateConversion coordConversion) {
        try {
            String subDirName = String.format("flow_%d_%d_%f", sourceNeighbors.size(), sinkNeighbors.size(), size);
            String baseDir = "src/main/output/reif_flow_debug/";
            String outputDir = baseDir + subDirName + "/";

            java.io.File dir = new java.io.File(outputDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            writeVertexListToFile(outputDir + "external_boundary.txt", externalBoundary, coordConversion);
            writeVertexListToFile(outputDir + "source_boundary.txt", sourceBoundary, coordConversion);
            writeVertexListToFile(outputDir + "sink_boundary.txt", sinkBoundary, coordConversion);
            writeVertexListToFile(outputDir + "st_path.txt", stPath, coordConversion);

            if (bestPath != null) {
                writeVertexListToFile(outputDir + "best_path.txt", bestPath, coordConversion);
            }

            writeNeighborSplitsToFile(outputDir + "neighbor_splits.txt", neighborSplits, coordConversion);
            writeDualGraph(outputDir + "dual_graph.txt", dualGraph, source, sink, coordConversion);
            writeDualGraphWithFlow(outputDir + "dual_graph_flow.txt", dualGraph, source, sink, coordConversion);
            writePrimalGraph(outputDir + "primal_graph.txt", graph, sourceNeighbors, sinkNeighbors, coordConversion);

            System.out.println("Visualization data saved to " + outputDir);
        } catch (Exception e) {
            System.err.println("Error saving visualization data: " + e.getMessage());
        }
    }

    private static void writeVertexListToFile(String filename, List<Vertex> vertices, CoordinateConversion coordConversion) throws java.io.IOException {
        try (java.io.FileWriter writer = new java.io.FileWriter(filename)) {
            for (Vertex v : vertices) {
                Vertex geoVertex = coordConversion.fromEuclidean(v);
                writer.write(String.format("%d %.10f %.10f\n", geoVertex.getName(), geoVertex.x, geoVertex.y));
            }
        }
    }

    private static void writeNeighborSplitsToFile(String filename, Map<Long, NeighborSplit> neighborSplits, CoordinateConversion coordConversion) throws java.io.IOException {
        System.out.println("Writing neighbor splits, total: " + neighborSplits.size());
        try (java.io.FileWriter writer = new java.io.FileWriter(filename)) {

            for (Map.Entry<Long, NeighborSplit> entry : neighborSplits.entrySet()) {
                NeighborSplit split = entry.getValue();

                // Используем сохраненную в NeighborSplit вершину
                Vertex vertex = split.vertex();
                Vertex geoVertex = coordConversion.fromEuclidean(vertex);
                writer.write(String.format("%d %.10f %.10f\n", geoVertex.getName(), geoVertex.x, geoVertex.y));

                for (Vertex neighbor : split.pathNeighbors()) {
                    Vertex geoNeighbor = coordConversion.fromEuclidean(neighbor);
                    writer.write(String.format("PATH %d %.10f %.10f\n",
                            geoNeighbor.getName(), geoNeighbor.x, geoNeighbor.y));
                }

                for (Vertex neighbor : split.leftNeighbors()) {
                    Vertex geoNeighbor = coordConversion.fromEuclidean(neighbor);
                    writer.write(String.format("LEFT %d %.10f %.10f\n",
                            geoNeighbor.getName(), geoNeighbor.x, geoNeighbor.y));
                }

                for (Vertex neighbor : split.rightNeighbors()) {
                    Vertex geoNeighbor = coordConversion.fromEuclidean(neighbor);
                    writer.write(String.format("RIGHT %d %.10f %.10f\n",
                            geoNeighbor.getName(), geoNeighbor.x, geoNeighbor.y));
                }

                writer.write("---\n");
            }
            System.out.println("Neighbor splits file written");
        }
    }

    private static void writePrimalGraph(String filename, Graph<Vertex> graph,
                                         HashSet<VertexOfDualGraph> sourceNeighbors,
                                         HashSet<VertexOfDualGraph> sinkNeighbors,
                                         CoordinateConversion coordConversion) throws java.io.IOException {
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

    private static void writeDualGraph(String filename, Graph<VertexOfDualGraph> dualGraph,
                                       VertexOfDualGraph source, VertexOfDualGraph sink,
                                       CoordinateConversion coordConversion) throws java.io.IOException {
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

    private static void writeDualGraphWithFlow(String filename, Graph<VertexOfDualGraph> dualGraph,
                                               VertexOfDualGraph source, VertexOfDualGraph sink,
                                               CoordinateConversion coordConversion) throws java.io.IOException {
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
    public static void dumpSPTVisualizationData(
            DijkstraResult spt1, DijkstraResult spt2,
            Vertex root1, Vertex root2,
            Map<Vertex, Vertex> splitToOriginalMap,
            HashSet<VertexOfDualGraph> sourceNeighbors,
            HashSet<VertexOfDualGraph> sinkNeighbors,
            double size,
            CoordinateConversion coordConversion) {
        try {
            String subDirName = String.format("flow_%d_%d_%f", sourceNeighbors.size(), sinkNeighbors.size(), size);
            String baseDir = "src/main/output/reif_flow_debug/";
            String outputDir = baseDir + subDirName + "/";

            java.io.File dir = new java.io.File(outputDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Dump SPT 1 (from splitVertex1)
            writeSPTToFile(outputDir + "spt1.txt", spt1, root1, splitToOriginalMap, "SPT1", coordConversion);

            // Dump SPT 2 (from splitVertex2)
            writeSPTToFile(outputDir + "spt2.txt", spt2, root2, splitToOriginalMap, "SPT2", coordConversion);

            System.out.println("SPT visualization data saved to " + outputDir);
        } catch (Exception e) {
            System.err.println("Error saving SPT visualization data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void writeSPTToFile(String filename, DijkstraResult spt, Vertex root,
                                Map<Vertex, Vertex> splitToOriginalMap, String sptName, CoordinateConversion coordConversion) throws java.io.IOException {
        try (java.io.FileWriter writer = new java.io.FileWriter(filename)) {
            // Header with metadata
            writer.write("# " + sptName + " - Shortest Path Tree with Region Weights\n");
            writer.write("# Format: SECTION_NAME followed by data\n\n");

            // Get original root vertex
            Vertex originalRoot = splitToOriginalMap.getOrDefault(root, root);
            Vertex geoRoot = coordConversion.fromEuclidean(originalRoot);

            // Write root information
            writer.write("ROOT\n");
            writer.write(String.format("%d %.10f %.10f\n", geoRoot.getName(), geoRoot.x, geoRoot.y));
            writer.write("\n");

            // Write total region weight
            writer.write("TOTAL_REGION_WEIGHT\n");
            writer.write(String.format("%.6f\n", spt.totalRegionWeight()));
            writer.write("\n");

            // Write boundary leaves with their cumulative region weights
            writer.write("BOUNDARY_LEAVES\n");
            writer.write("# vertex_id longitude latitude cumulative_left_weight boundary_index\n");

            System.out.println("Writing " + spt.boundaryLeaves().size() + " boundary leaves for " + sptName);
            System.out.println("  leftRegionWeights map has " + spt.leftRegionWeights().size() + " entries");

            for (int leafIdx = 0; leafIdx < spt.boundaryLeaves().size(); leafIdx++) {
                Vertex leaf = spt.boundaryLeaves().get(leafIdx);
                Vertex originalLeaf = splitToOriginalMap.getOrDefault(leaf, leaf);
                Vertex geoLeaf = coordConversion.fromEuclidean(originalLeaf);
                double leftWeight = spt.leftRegionWeights().getOrDefault(leaf, -1.0);

                // Debug: check if leaf is in the map
                boolean foundInMap = spt.leftRegionWeights().containsKey(leaf);
                System.out.println("  Leaf " + leafIdx + ": vertex " + leaf.getName() +
                        ", inMap=" + foundInMap + ", leftWeight=" + leftWeight);

                writer.write(String.format("%d %.10f %.10f %.6f %d\n",
                        geoLeaf.getName(), geoLeaf.x, geoLeaf.y, leftWeight, leafIdx));
            }
            writer.write("\n");

            // Write tree edges (parent -> child relationships)
            writer.write("TREE_EDGES\n");
            writer.write("# from_id from_lon from_lat to_id to_lon to_lat distance\n");

            Set<String> writtenEdges = new HashSet<>();
                for (Map.Entry<Vertex, Vertex> entry : spt.previous().entrySet()) {
                Vertex child = entry.getKey();
                Vertex parent = entry.getValue();

                if (parent == null) continue;

                // Get original vertices
                Vertex originalChild = splitToOriginalMap.getOrDefault(child, child);
                Vertex originalParent = splitToOriginalMap.getOrDefault(parent, parent);

                // Create unique edge key to avoid duplicates
                long id1 = originalChild.getName();
                long id2 = originalParent.getName();
                String edgeKey = Math.min(id1, id2) + "_" + Math.max(id1, id2);

                if (writtenEdges.contains(edgeKey)) continue;
                writtenEdges.add(edgeKey);

                Vertex geoChild = coordConversion.fromEuclidean(originalChild);
                Vertex geoParent = coordConversion.fromEuclidean(originalParent);

                double distance = spt.distances().getOrDefault(child, 0.0);

                writer.write(String.format("%d %.10f %.10f %d %.10f %.10f %.6f\n",
                        geoParent.getName(), geoParent.x, geoParent.y,
                        geoChild.getName(), geoChild.x, geoChild.y,
                        distance));
            }
                writer.write("\n");

            // Write all vertices in the SPT with their distances
                writer.write("VERTICES\n");
                writer.write("# vertex_id longitude latitude distance_from_root\n");
                for (Map.Entry<Vertex, Double> entry : spt.distances().entrySet()) {
                Vertex v = entry.getKey();
                Double dist = entry.getValue();

                if (dist == Double.MAX_VALUE) continue;  // Skip unreachable vertices

                Vertex originalV = splitToOriginalMap.getOrDefault(v, v);
                Vertex geoV = coordConversion.fromEuclidean(originalV);

                writer.write(String.format("%d %.10f %.10f %.6f\n",
                        geoV.getName(), geoV.x, geoV.y, dist));
            }
        }
    }
}
