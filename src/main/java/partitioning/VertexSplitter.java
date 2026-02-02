package partitioning;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import org.junit.jupiter.api.Assertions;

public class VertexSplitter {

    public static class NeighborSplit {
        public Vertex vertex;  // Сама вершина, которую разделяем
        public List<Vertex> pathNeighbors;
        public List<Vertex> leftNeighbors;
        public List<Vertex> rightNeighbors;

        NeighborSplit(Vertex vertex, List<Vertex> path, List<Vertex> left, List<Vertex> right) {
            this.vertex = vertex;
            this.pathNeighbors = path;
            this.leftNeighbors = left;
            this.rightNeighbors = right;
        }
    }

    public static Map<Long, NeighborSplit> preprocessNeighborSplits(Graph<Vertex> graph, List<Vertex> path,
                                                              List<Vertex> sourceBoundary, List<Vertex> sinkBoundary) {
        System.out.println("Preprocessing neighbor splits for " + path.size() + " vertices in path");
        Map<Long, NeighborSplit> splits = new HashMap<>();

        Set<Long> sourceBoundaryNames = sourceBoundary.stream().map(Vertex::getName).collect(Collectors.toSet());
        Set<Long> sinkBoundaryNames = sinkBoundary.stream().map(Vertex::getName).collect(Collectors.toSet());
        var orderedEdges = graph.arrangeByAngle();

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

            var curVertexEdges = orderedEdges.get(currentVertex).stream().toList();
            List<Vertex> pathNeighbors = new ArrayList<>();
            List<Vertex> leftNeighbors = new ArrayList<>();
            List<Vertex> rightNeighbors = new ArrayList<>();

            if (!isFirstVertex && !isLastVertex) {
                splitNeighborsForMiddleVertex(prevVertex, nextVertex, curVertexEdges, 
                        pathNeighbors, leftNeighbors, rightNeighbors, currentVertex);
            } else {
                List<Vertex> boundaryToUse = isFirstVertex ? sourceBoundary : sinkBoundary;
                // List<Vertex> otherBoundary TODO
                splitNeighborsForEndVertex(graph, currentVertex, path, boundaryToUse,
                        pathNeighbors, leftNeighbors, rightNeighbors);
            }

            System.out.println("Vertex " + currentVertex.getName() + ": Path=" + pathNeighbors.size() + " " + pathNeighbors.stream().map(Vertex::getName).toList() + ", Left=" + leftNeighbors.size() + leftNeighbors.stream().map(Vertex::getName).toList() + ", Right=" + rightNeighbors.size() + rightNeighbors.stream().map(Vertex::getName).toList());
            splits.put(currentVertex.getName(), new NeighborSplit(currentVertex, pathNeighbors, leftNeighbors, rightNeighbors));
        }

        System.out.println("Preprocessed " + splits.size() + " neighbor splits");
        return splits;
    }

    private static List<Vertex> getBoundaryNeighbors(Vertex currentVertex, List<Vertex> boundary) {
        List<Vertex> boundaryNeighbors = new ArrayList<>();
        for (int j = 0; j < boundary.size(); j++) {
            Vertex bv = boundary.get(j);
            if (bv.getName() == currentVertex.getName()) {
                boundaryNeighbors.add(boundary.get((j + 1) % boundary.size()));
                boundaryNeighbors.add(boundary.get((j - 1 + boundary.size()) % boundary.size()));
                break;
            }
        }
        return boundaryNeighbors;
    }

    private static double[] computePathDirectionFromBoundary(
            Vertex currentVertex, List<Vertex> boundaryNeighbors, 
            Graph<Vertex> graph, List<Vertex> boundaryToUse) {
        
        System.out.println("Boundary neighbors: " + boundaryNeighbors.size());
        
        if (boundaryNeighbors.size() != 2) {
            System.out.println("BROKEN BOUNDARY FOR VERTEX " + currentVertex.getName() + 
                    " NEIGHBORS: " + boundaryNeighbors.stream().map(Vertex::getName).collect(Collectors.toList()));
            for (Vertex neighbor : boundaryToUse) {
                if (graph.getEdges().get(neighbor) != null) {
                    System.out.println("vertex: " + neighbor.getName() + " neighbors size: " + 
                            graph.getEdges().get(neighbor).keySet().size() + " neighbors: " + 
                            graph.getEdges().get(neighbor).keySet().stream().map(Vertex::getName).collect(Collectors.toList()));
                } else {
                    System.out.println("vertex: " + neighbor.getName() + " neighbors size: 0");
                }
            }
            throw new RuntimeException("broken boundary, neighbors: " + boundaryNeighbors.size() + 
                    " neighbors: " + boundaryNeighbors.stream().map(Vertex::getName).collect(Collectors.toList()) + 
                    " currentVertex: " + currentVertex.getName());
        }

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

        double pathDirX = vec1X + vec2X;
        double pathDirY = vec1Y + vec2Y;

        double pathLen = Math.sqrt(pathDirX * pathDirX + pathDirY * pathDirY);
        pathDirX /= pathLen;
        pathDirY /= pathLen;

        return new double[]{pathDirX, pathDirY};
    }

    private static void splitNeighborsForEndVertex(
            Graph<Vertex> graph, Vertex currentVertex, List<Vertex> path,
            List<Vertex> boundaryToUse, List<Vertex> pathNeighbors,
            List<Vertex> leftNeighbors, List<Vertex> rightNeighbors) {

        List<Vertex> boundaryNeighbors = getBoundaryNeighbors(currentVertex, boundaryToUse);

        if (boundaryNeighbors.size() < 2) {
            throw new RuntimeException("Expected > 2 boundary neighbors, got: " + boundaryNeighbors.size());
        }

        Set<Long> pathVertexNames = path.stream().map(Vertex::getName).collect(Collectors.toSet());
        Set<Long> boundaryNames = new HashSet<>();
        for (Vertex bn : boundaryNeighbors) {
            boundaryNames.add(bn.getName());
        }

        var orderedEdges = graph.arrangeByAngle();
        var edgesList = orderedEdges.get(currentVertex).stream().toList();

        int pathEdgeIdx = -1;
        for (int i = 0; i < edgesList.size(); i++) {
            if (pathVertexNames.contains(edgesList.get(i).end.getName())) {
                pathEdgeIdx = i;
                break;
            }
        }

        if (pathEdgeIdx == -1) {
            System.out.println("WARNING: No path edge found for boundary vertex " + currentVertex.getName());
            return;
        }

        int n = edgesList.size();
        boolean inLeftRegion = true;
        int boundaryEdgesCount = 0;

        for (int i = 1; i < n; i++) {
            int idx = (pathEdgeIdx + i) % n;
            EdgeOfGraph<Vertex> edge = edgesList.get(idx);
            Vertex neighbor = edge.end;

            if (neighbor.equals(currentVertex)) {
                continue;
            }

            if (pathVertexNames.contains(neighbor.getName())) {
                pathNeighbors.add(neighbor);
                break;
            }

            if (boundaryNames.contains(neighbor.getName())) {
                boundaryEdgesCount++;
                if (inLeftRegion) {
                    leftNeighbors.add(neighbor);
                } else {
                    rightNeighbors.add(neighbor);
                }
                if (boundaryEdgesCount == 1) {
                    inLeftRegion = false;
                }
                continue;
            }

            if (inLeftRegion) {
                leftNeighbors.add(neighbor);
            } else {
                rightNeighbors.add(neighbor);
            }
        }

        System.out.println("End vertex " + currentVertex.getName() +
                ": pathNeighbors=" + pathNeighbors.size() +
                ", leftNeighbors=" + leftNeighbors.size() +
                ", rightNeighbors=" + rightNeighbors.size());
    }

    private static void splitNeighborsForMiddleVertex(
            Vertex prevVertex, Vertex nextVertex,
            List<EdgeOfGraph<Vertex>> curVertexEdges,
            List<Vertex> pathNeighbors,
            List<Vertex> leftNeighbors,
            List<Vertex> rightNeighbors,
            Vertex currentVertex) {
        
        pathNeighbors.add(prevVertex);
        pathNeighbors.add(nextVertex);

        int prevVertexEdgeIdx = -1;
        int nextVertexEdgeIdx = -1;

        for (int j = 0; j < curVertexEdges.size(); j++) {
            var edge = curVertexEdges.get(j);
            if (edge.begin.equals(edge.end)) {
                continue;
            }

            if (edge.end.equals(prevVertex)) {
                prevVertexEdgeIdx = j;
            }
            if (edge.end.equals(nextVertex)) {
                nextVertexEdgeIdx = j;
            }
            if (prevVertexEdgeIdx != -1 && nextVertexEdgeIdx != -1) {
                break;
            }
        }

        if (prevVertexEdgeIdx != -1 && nextVertexEdgeIdx != -1) {
            int firstIdx = prevVertexEdgeIdx;
            int secondIdx = nextVertexEdgeIdx;
            boolean shouldSwapSides = false;

            if (firstIdx > secondIdx) {
                int temp = firstIdx;
                firstIdx = secondIdx;
                secondIdx = temp;
                shouldSwapSides = true;
            }

            for (int j = 0; j < curVertexEdges.size(); j++) {
                var edge = curVertexEdges.get(j);
                if (edge.begin.equals(edge.end)) {
                    continue;
                }

                if (edge.end.equals(prevVertex) || edge.end.equals(nextVertex)) {
                    continue;
                }

                boolean isBetween = (j > firstIdx && j < secondIdx);

                if (shouldSwapSides) {
                    if (isBetween) {
                        leftNeighbors.add(edge.end);
                    } else {
                        rightNeighbors.add(edge.end);
                    }
                } else {
                    if (isBetween) {
                        rightNeighbors.add(edge.end);
                    } else {
                        leftNeighbors.add(edge.end);
                    }
                }
            }
        } else {
            System.err.println("Warning: could not find path edges for vertex " + currentVertex.getName());
        }
    }

    private static boolean isAdjacentOnBoundary(Vertex vertex, Vertex neighbor, List<Vertex> boundary) {
        int boundarySize = boundary.size();
        for (int ptr = 0; ptr < boundarySize; ptr++) {
            if (boundary.get(ptr).equals(neighbor) &&
                    (boundary.get((ptr + 1) % boundarySize).equals(vertex)
                            || boundary.get((ptr - 1 + boundarySize) % boundarySize).equals(vertex))) {
                return true;
            }
        }
        return false;
    }

    private static NeighborSplit handleTwoNeighborsCase(Vertex currentVertex, List<EdgeOfGraph<Vertex>> neighbors) {
        return new NeighborSplit(currentVertex, List.of(), 
                List.of(neighbors.get(0).end), List.of(neighbors.get(1).end));
    }

    private static NeighborSplit handleThreeNeighborsCase(
            Vertex currentVertex, List<EdgeOfGraph<Vertex>> neighbors,
            List<Vertex> sourceBoundary, List<Vertex> sinkBoundary) {
        
        List<Vertex> leftNeighbors = new ArrayList<>();
        List<Vertex> rightNeighbors = new ArrayList<>();
        System.out.println("handleThreeNeighborsCase");
        for (var n: neighbors) {
            if (sourceBoundary.contains(n.end) && sinkBoundary.contains(n.end)) {
                boolean onSinkBoundary = isAdjacentOnBoundary(currentVertex, n.end, sinkBoundary);
                boolean onSourceBoundary = isAdjacentOnBoundary(currentVertex, n.end, sourceBoundary);
                
                if (onSinkBoundary && onSourceBoundary) {
                    rightNeighbors.add(n.end);
                } else {
                    leftNeighbors.add(n.end);
                }
            } else {
                leftNeighbors.add(n.end);
            }
        }
        
        if (rightNeighbors.isEmpty()) {
            throw new RuntimeException("no common neighbor");
        }

        return new NeighborSplit(currentVertex, List.of(), leftNeighbors, rightNeighbors);
    }

    private static NeighborSplit handleBoundaryIntersectionVertex(Graph<Vertex> graph, Vertex currentVertex,
                                                           List<Vertex> sourceBoundary, List<Vertex> sinkBoundary) {
        var angle = graph.arrangeByAngle();
        List<EdgeOfGraph<Vertex>> neighbors = new ArrayList<>();
        int boundaryNeighbors = 0;
        Set<Vertex> sourceBoundarySet = sourceBoundary.stream().collect(Collectors.toSet());
        Set<Vertex> sinkBoundarySet = sinkBoundary.stream().collect(Collectors.toSet());
        for (var edge: angle.get(currentVertex)) {
            if (!edge.begin.equals(edge.end)) {
                neighbors.add(edge);
                if (sinkBoundarySet.contains(edge.end) || sourceBoundarySet.contains(edge.end)) {
                    boundaryNeighbors++;
                }
            }
        }

        if (boundaryNeighbors == 2) {
            return handleTwoNeighborsCase(currentVertex, neighbors);
        } else if (boundaryNeighbors == 3) {
            return handleThreeNeighborsCase(currentVertex, neighbors, sourceBoundary, sinkBoundary);
        }

        return handleManyNeighborsCase(currentVertex, neighbors, sourceBoundary, sinkBoundary, angle);
    }

    private static NeighborSplit handleManyNeighborsCase(
            Vertex currentVertex, List<EdgeOfGraph<Vertex>> neighbors,
            List<Vertex> sourceBoundary, List<Vertex> sinkBoundary,
            Map<Vertex, java.util.TreeSet<EdgeOfGraph<Vertex>>> angle) {
        
        List<Vertex> leftNeighbors = new ArrayList<>();
        List<Vertex> rightNeighbors = new ArrayList<>();
        int meetSink = 0, meetSource = 0, prevMeetSink = 0, prevMeetSource = 0;
        boolean isRight = true;
        System.out.println("split for " + currentVertex.name);
        
        for (var edge: angle.get(currentVertex)) {
            if (edge.begin.equals(edge.end)) {
                continue;
            }
            
            int newMeetSink = meetSink;
            if (sinkBoundary.contains(edge.end) && isAdjacentOnBoundary(currentVertex, edge.end, sinkBoundary)) {
                newMeetSink++;
            }
            
            int newMeetSource = meetSource;
            if (sourceBoundary.contains(edge.end) && isAdjacentOnBoundary(currentVertex, edge.end, sourceBoundary)) {
                newMeetSource++;
            }
            
            if (meetSink == newMeetSink && meetSource == newMeetSource) {
                if (isRight) {
                    System.out.println("to right without modifications, vertex " + edge.end.name + 
                            " " + meetSource + " " + meetSink + " " + prevMeetSource + " " + prevMeetSink);
                    rightNeighbors.add(edge.end);
                } else {
                    System.out.println("to left without modifications, vertex " + edge.end.name + 
                            " " + meetSource + " " + meetSink + " " + prevMeetSource + " " + prevMeetSink);
                    leftNeighbors.add(edge.end);
                }
                prevMeetSink = meetSink;
                prevMeetSource = meetSource;
                continue;
            }
            
            isRight = !((newMeetSink == 2 && (newMeetSource < 2 || (prevMeetSource == 1 && prevMeetSink == 1)))
                    || (newMeetSource == 2 && (newMeetSink < 2 || (prevMeetSink == 1 && prevMeetSource == 1)))
                    || (newMeetSource == 2 && newMeetSink == 2 && meetSource == 1 && meetSink == 1));
            
            prevMeetSink = meetSink;
            prevMeetSource = meetSource;
            meetSink = newMeetSink;
            meetSource = newMeetSource;
            
            if (isRight) {
                System.out.println("to right with modifications, vertex " + edge.end.name + 
                        " " + meetSource + " " + meetSink + " " + prevMeetSource + " " + prevMeetSink);
                rightNeighbors.add(edge.end);
            } else {
                System.out.println("to left with modifications, vertex " + edge.end.name + 
                        " " + meetSource + " " + meetSink + " " + prevMeetSource + " " + prevMeetSink);
                leftNeighbors.add(edge.end);
            }
        }

        return new NeighborSplit(currentVertex, List.of(), leftNeighbors, rightNeighbors);
    }

    private static Vertex findVertexByName(Graph<Vertex> graph, long name) {
        for (Vertex v : graph.verticesArray()) {
            if (v.getName() == name) {
                return v;
            }
        }
        return null;
    }

    private static void connectNeighborsToSplitVertex(
            Graph<Vertex> splitGraph, Vertex originalVertex,
            Vertex splitVertex, List<Vertex> neighbors) {
        
        for (Vertex neighbor : neighbors) {
            Vertex neighborInGraph = findVertexByName(splitGraph, neighbor.getName());
            if (neighborInGraph != null) {
                double edgeLength = splitGraph.getEdges().get(originalVertex).get(neighborInGraph).length;
                splitGraph.addEdge(splitVertex, neighborInGraph, edgeLength);
            }
        }
    }

    public static Map.Entry<Vertex, Vertex> splitVertex(Graph<Vertex> splitGraph, Vertex vertex,
                                                  Map<Vertex, Vertex> splitToOriginalMap,
                                                  Map<Long, NeighborSplit> neighborSplits) {
        long originalName = vertex.getName();
        Vertex splitVertex1 = new Vertex(originalName * 1000 + 1, vertex.x, vertex.y, vertex.getWeight());
        Vertex splitVertex2 = new Vertex(originalName * 1000 + 2, vertex.x, vertex.y, vertex.getWeight());

        splitToOriginalMap.put(splitVertex1, vertex);
        splitToOriginalMap.put(splitVertex2, vertex);

        splitGraph.addVertex(splitVertex1);
        splitGraph.addVertex(splitVertex2);

        Vertex vertexInGraph = findVertexByName(splitGraph, originalName);

        if (vertexInGraph == null || splitGraph.getEdges().get(vertexInGraph) == null) {
            return new AbstractMap.SimpleEntry<>(splitVertex1, splitVertex2);
        }

        List<Vertex> neighbors = new ArrayList<>(splitGraph.getEdges().get(vertexInGraph).keySet());

        if (neighbors.isEmpty()) {
            splitGraph.deleteVertex(vertexInGraph);
            return new AbstractMap.SimpleEntry<>(splitVertex1, splitVertex2);
        }

        NeighborSplit split = neighborSplits.get(originalName);

        int cnt = 0;
        if (split != null) {
            for (Vertex neighbor : split.pathNeighbors) {
                Vertex neighborInGraph = findVertexByName(splitGraph, neighbor.getName());
                
                if (neighborInGraph != null) {
                    cnt++;
                    double edgeLength = splitGraph.getEdges().get(vertexInGraph).get(neighborInGraph).length;
                    splitGraph.addEdge(splitVertex1, neighborInGraph, edgeLength);
                    splitGraph.addEdge(splitVertex2, neighborInGraph, edgeLength);
                } else {
                    Vertex splitNeighbor1 = findVertexByName(splitGraph, neighbor.getName() * 1000 + 1);
                    Vertex splitNeighbor2 = findVertexByName(splitGraph, neighbor.getName() * 1000 + 2);
                    
                    if (splitNeighbor1 != null && splitNeighbor2 != null) {
                        cnt++;
                        double edgeLength = Math.sqrt(
                            Math.pow(vertexInGraph.x - splitNeighbor1.x, 2) + 
                            Math.pow(vertexInGraph.y - splitNeighbor1.y, 2)
                        );
                        
                        splitGraph.addEdge(splitVertex1, splitNeighbor1, edgeLength);
                        splitGraph.addEdge(splitVertex2, splitNeighbor2, edgeLength);
                        
                        System.out.println("  Connected split vertices of " + originalName + 
                                " with corresponding split versions of path neighbor " + neighbor.getName());
                    } else {
                        System.out.println("  WARNING: Path neighbor " + neighbor.getName() + 
                                " not found (neither original nor split) for vertex " + originalName);
                    }
                }
            }

            connectNeighborsToSplitVertex(splitGraph, vertexInGraph, splitVertex1, split.leftNeighbors);
            connectNeighborsToSplitVertex(splitGraph, vertexInGraph, splitVertex2, split.rightNeighbors);
        } else {
            System.out.println("No split found for vertex " + vertex.getName());
        }

        if (split != null) {
            Assertions.assertEquals(split.pathNeighbors.size(), cnt);
        }
        splitGraph.deleteVertex(vertexInGraph);

        return new AbstractMap.SimpleEntry<>(splitVertex1, splitVertex2);
    }
}
