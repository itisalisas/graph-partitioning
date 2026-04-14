package readWrite;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import graph.BoundSearcher;
import graph.Edge;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;
import partitioning.entities.DijkstraResult;
import partitioning.entities.NeighborSplit;

public class FlowWriter {
    private static final Logger logger = LoggerFactory.getLogger(FlowWriter.class);

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

            logger.info("Visualization data saved to {}", outputDir);
        } catch (Exception e) {
            logger.error("Error saving visualization data: {}", e.getMessage());
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
        logger.debug("Writing neighbor splits, total: {}", neighborSplits.size());
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
            logger.debug("Neighbor splits file written");
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
            logger.debug("Wrote {} vertices to primal graph", vertexCount);

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
            logger.debug("Wrote {} edges to primal graph", edgeCount);
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
            CoordinateConversion coordConversion,
            Graph<Vertex> initGraph,
            Map<Vertex, VertexOfDualGraph> comparisonForDualGraph) {
        try {
            String subDirName = String.format("flow_%d_%d_%f", sourceNeighbors.size(), sinkNeighbors.size(), size);
            String baseDir = "src/main/output/reif_flow_debug/";
            String outputDir = baseDir + subDirName + "/";

            java.io.File dir = new java.io.File(outputDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Dump SPT 1 (from splitVertex1)
            writeSPTToFile(outputDir + "spt1.txt", spt1, root1, splitToOriginalMap, "SPT1",
                    coordConversion, initGraph, comparisonForDualGraph);

            // Dump SPT 2 (from splitVertex2)
            writeSPTToFile(outputDir + "spt2.txt", spt2, root2, splitToOriginalMap, "SPT2",
                    coordConversion, initGraph, comparisonForDualGraph);

            logger.info("SPT visualization data saved to {}", outputDir);
        } catch (Exception e) {
            logger.error("Error saving SPT visualization data: {}", e.getMessage(), e);
        }
    }

    private static void writeSPTToFile(String filename, DijkstraResult spt, Vertex root,
                                Map<Vertex, Vertex> splitToOriginalMap, String sptName,
                                CoordinateConversion coordConversion,
                                Graph<Vertex> initGraph,
                                Map<Vertex, VertexOfDualGraph> comparisonForDualGraph) throws java.io.IOException {
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

            logger.debug("Writing {} boundary leaves for {}", spt.boundaryLeaves().size(), sptName);
            logger.debug("IN LEAF INDICES {}", spt.leafIndices().size());
            logger.debug("  leftRegionWeights map has {} entries", spt.weights().size());

            int numLeaves = spt.leafIndices().size();
            int numWeights = spt.weights().size();

            for (int leafIdx = 0; leafIdx < numLeaves; leafIdx++) {
                Vertex leaf = spt.boundaryLeaves().get(leafIdx);
                Vertex originalLeaf = splitToOriginalMap.getOrDefault(leaf, leaf);
                Vertex geoLeaf = coordConversion.fromEuclidean(originalLeaf);

                double leftWeight = spt.leafIndices().get(leafIdx) < 0 ? 0 : spt.weights().get(spt.leafIndices().get(leafIdx));

                // Debug: check if leaf is in the map
                // System.out.println("  Leaf " + leafIdx + ": vertex " + leaf.getName() + ", leftWeight=" + leftWeight);

                writer.write(String.format("%d %.10f %.10f %.6f %d\n",
                        geoLeaf.getName(), geoLeaf.x, geoLeaf.y, leftWeight, leafIdx));
            }
            writer.write("\n");
            
            // Write region weights (weight between consecutive leaves)
            writer.write("REGION_WEIGHTS\n");
            writer.write("# region_index region_vertex_id from_leaf_index to_leaf_index weight centroid_lon centroid_lat\n");
            
            int numRegions = spt.regions() != null ? spt.regions().size() : 0;
            logger.debug("Writing {} regions for {}", numRegions, sptName);
            
            for (int regionIdx = 0; regionIdx < numWeights; regionIdx++) {
                int fromLeafIdx = regionIdx;
                int toLeafIdx = (regionIdx + 1) % numWeights;
                
                double fromWeight = spt.weights().get(fromLeafIdx);
                double toWeight = (toLeafIdx < numWeights) ?
                                  spt.weights().get(toLeafIdx) : spt.totalRegionWeight();
                
                // Handle wrap-around: last region weight
                double regionWeight;
                if (toLeafIdx == 0) {
                    // Last region: from last leaf to first leaf (wrapping around)
                    regionWeight = spt.totalRegionWeight() - fromWeight;
                } else {
                    regionWeight = toWeight - fromWeight;
                }
                
                // Use actual dual graph vertex coordinates (centroid of face)
                double centroidLon, centroidLat;
                long regionVertexId = -1;
                
                if (spt.regions() != null && regionIdx < spt.regions().size()) {
                    VertexOfDualGraph regionVertex = spt.regions().get(regionIdx);
                    regionVertexId = regionVertex.getName();
                    
                    // Convert from Euclidean to geographic coordinates
                    Vertex geoRegion = coordConversion.fromEuclidean(regionVertex);
                    centroidLon = geoRegion.x;
                    centroidLat = geoRegion.y;
                    
                    //System.out.println("  Region " + regionIdx + ": vertex " + regionVertexId +
                    //                   " at (" + centroidLon + ", " + centroidLat + "), weight=" + regionWeight);
                } else {
                    // Fallback: calculate centroid between the two leaves if regions list is not available
                    logger.warn("Region {} not found in regions list, using fallback", regionIdx);
                    Vertex fromLeaf = spt.boundaryLeaves().get(fromLeafIdx);
                    Vertex toLeaf = spt.boundaryLeaves().get(toLeafIdx);
                    Vertex originalFromLeaf = splitToOriginalMap.getOrDefault(fromLeaf, fromLeaf);
                    Vertex originalToLeaf = splitToOriginalMap.getOrDefault(toLeaf, toLeaf);
                    Vertex geoFromLeaf = coordConversion.fromEuclidean(originalFromLeaf);
                    Vertex geoToLeaf = coordConversion.fromEuclidean(originalToLeaf);
                    
                    centroidLon = (geoFromLeaf.x + geoToLeaf.x) / 2.0;
                    centroidLat = (geoFromLeaf.y + geoToLeaf.y) / 2.0;
                }
                
                writer.write(String.format("%d %d %d %d %.6f %.10f %.10f\n",
                        regionIdx, regionVertexId, fromLeafIdx, toLeafIdx, regionWeight, centroidLon, centroidLat));
            }
            writer.write("\n");

            // Write leaf indices (mapping from leaf index to region/weights index)
            writer.write("LEAF_INDICES\n");
            writer.write("# leaf_index region_index\n");
            for (int leafIdx = 0; leafIdx < spt.leafIndices().size(); leafIdx++) {
                writer.write(String.format("%d %d\n", leafIdx, spt.leafIndices().get(leafIdx)));
            }
            writer.write("\n");

            // Write leaf group boundaries (boundary polygons for regions between consecutive leaves)
            writer.write("LEAF_GROUP_BOUNDARIES\n");
            writer.write("# group_index weight num_vertices\n");
            writer.write("# lon lat (for each vertex)\n");
            writer.write("# ---\n");

            if (initGraph != null && comparisonForDualGraph != null
                    && spt.leafIndices() != null && spt.regions() != null) {
                int numRegionsTotal = spt.regions().size();
                // N leaves => N+1 linear groups (no wrap-around):
                //   Group 0: before leaf 0          → regions [0 .. leafIndices[0]]
                //   Group i: between leaf i-1 and i → regions [leafIndices[i-1]+1 .. leafIndices[i]]
                //   Group N: after leaf N-1          → regions [leafIndices[N-1]+1 .. end]
                int numGroups = numLeaves + 1;

                // Helper to safely read cumulative weight at a leafIndex (-1 means 0)
                java.util.function.IntFunction<Double> safeWeightAt = leafIdx -> {
                    int li = spt.leafIndices().get(leafIdx);
                    return (li >= 0 && li < spt.weights().size()) ? spt.weights().get(li) : 0.0;
                };

                // Assign each unique face to the group where it first appears (Euler tour order)
                java.util.HashMap<VertexOfDualGraph, Integer> faceToGroup = new java.util.HashMap<>();
                for (int groupIdx = 0; groupIdx < numGroups; groupIdx++) {
                    int from, to;
                    if (groupIdx == 0) {
                        // Before first leaf
                        from = 0;
                        to = Math.max(spt.leafIndices().get(0), -1);
                    } else if (groupIdx < numGroups - 1) {
                        // Between leaf (groupIdx-1) and leaf (groupIdx)
                        from = Math.max(spt.leafIndices().get(groupIdx - 1) + 1, 0);
                        to = spt.leafIndices().get(groupIdx);
                    } else {
                        // After last leaf
                        from = Math.max(spt.leafIndices().get(numLeaves - 1) + 1, 0);
                        to = numRegionsTotal - 1;
                    }
                    for (int r = from; r <= to; r++) {
                        faceToGroup.putIfAbsent(spt.regions().get(r), groupIdx);
                    }
                }

                // Invert: group -> set of faces
                java.util.HashMap<Integer, java.util.HashSet<VertexOfDualGraph>> groupToFaces = new java.util.HashMap<>();
                for (var entry : faceToGroup.entrySet()) {
                    groupToFaces.computeIfAbsent(entry.getValue(), k -> new java.util.HashSet<>())
                            .add(entry.getKey());
                }

                // Count faces not assigned to any group
                int totalDualFaces = 0;
                if (comparisonForDualGraph != null) {
                    totalDualFaces = comparisonForDualGraph.size();
                }
                logger.debug("Leaf group stats for {}: {} leaves, {} groups, {} total region entries, {} unique faces assigned, total dual faces: {}", 
                        sptName, numLeaves, numGroups, numRegionsTotal, faceToGroup.size(), totalDualFaces);

                int emptyGroups = 0;
                int failedGroups = 0;
                for (int groupIdx = 0; groupIdx < numGroups; groupIdx++) {
                    double groupWeight;
                    if (groupIdx == 0) {
                        groupWeight = safeWeightAt.apply(0);
                    } else if (groupIdx < numGroups - 1) {
                        groupWeight = safeWeightAt.apply(groupIdx) - safeWeightAt.apply(groupIdx - 1);
                    } else {
                        groupWeight = spt.totalRegionWeight() - safeWeightAt.apply(numLeaves - 1);
                    }

                    java.util.HashSet<VertexOfDualGraph> groupFaces =
                            groupToFaces.getOrDefault(groupIdx, new java.util.HashSet<>());

                    if (groupFaces.isEmpty()) {
                        emptyGroups++;
                        writer.write(String.format("%d %.6f 0\n", groupIdx, groupWeight));
                        writer.write("---\n");
                        continue;
                    }

                    try {
                        List<Vertex> boundary = BoundSearcher.findBound(initGraph, groupFaces, comparisonForDualGraph);
                        writer.write(String.format("%d %.6f %d\n", groupIdx, groupWeight, boundary.size()));
                        for (Vertex v : boundary) {
                            Vertex originalV = splitToOriginalMap.getOrDefault(v, v);
                            Vertex geoV = coordConversion.fromEuclidean(originalV);
                            writer.write(String.format("%.10f %.10f\n", geoV.x, geoV.y));
                        }
                    } catch (Exception e) {
                        failedGroups++;
                        logger.warn("Failed to compute boundary for group {} ({} faces): {}", groupIdx, groupFaces.size(), e.getMessage());
                        writer.write(String.format("%d %.6f 0\n", groupIdx, groupWeight));
                    }
                    writer.write("---\n");
                }
                logger.debug("  {}: {} empty groups, {} failed boundary computations", sptName, emptyGroups, failedGroups);
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

                double distance = spt.dijkstraDistances().getOrDefault(child, 0.0);

                writer.write(String.format("%d %.10f %.10f %d %.10f %.10f %.6f\n",
                        geoParent.getName(), geoParent.x, geoParent.y,
                        geoChild.getName(), geoChild.x, geoChild.y,
                        distance));
            }
                writer.write("\n");

            // Write all vertices in the SPT with their distances
                writer.write("VERTICES\n");
                writer.write("# vertex_id longitude latitude distance_from_root\n");
                for (Map.Entry<Vertex, Double> entry : spt.dijkstraDistances().entrySet()) {
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
