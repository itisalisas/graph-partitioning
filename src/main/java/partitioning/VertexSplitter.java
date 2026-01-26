package partitioning;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;

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
                    // Убеждаемся, что prevVertexEdgeIdx < nextVertexEdgeIdx
                    // Если нет, то меняем их местами и инвертируем стороны
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

                        // Соседи между двумя рёбрами пути
                        boolean isBetween = (j > firstIdx && j < secondIdx);

                        if (shouldSwapSides) {
                            // Если мы поменяли индексы местами, то меняем и стороны
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
            } else {
                double pathDirX, pathDirY;
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

                double pathLen = Math.sqrt(pathDirX * pathDirX + pathDirY * pathDirY);
                pathDirX /= pathLen;
                pathDirY /= pathLen;

                Set<Long> pathVertexNames = path.stream().map(Vertex::getName).collect(Collectors.toSet());

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
            }

            System.out.println("Vertex " + currentVertex.getName() + ": Path=" + pathNeighbors.size() + ", Left=" + leftNeighbors.size() + ", Right=" + rightNeighbors.size());
            splits.put(currentVertex.getName(), new NeighborSplit(currentVertex, pathNeighbors, leftNeighbors, rightNeighbors));
        }

        System.out.println("Preprocessed " + splits.size() + " neighbor splits");
        return splits;
    }

    private static NeighborSplit handleBoundaryIntersectionVertex(Graph<Vertex> graph, Vertex currentVertex,
                                                           List<Vertex> sourceBoundary, List<Vertex> sinkBoundary) {
        var angle = graph.arrangeByAngle();
        List<Vertex> leftNeighbors = new ArrayList<>();
        List<Vertex> rightNeighbors = new ArrayList<>();
        List<EdgeOfGraph<Vertex>> neighbors = new ArrayList<>();
        for (var edge: angle.get(currentVertex)) {
            if (!edge.begin.equals(edge.end)) {
                neighbors.add(edge);
            }
        }

        if (neighbors.size() == 2) {
            return new NeighborSplit(currentVertex, List.of(), List.of(neighbors.get(0).end), List.of(neighbors.get(1).end));
        } else if (neighbors.size() == 3) {
            for (var n: neighbors) {
                if (sourceBoundary.contains(n.end) && sinkBoundary.contains(n.end)) {
                    boolean ok1 = false;
                    int sinkLen = sinkBoundary.size();
                    for (int ptr = 0; ptr < sinkBoundary.size(); ptr++) {
                        if (sinkBoundary.get(ptr).equals(n.end) &&
                                (sinkBoundary.get((ptr + 1) % sinkLen).equals(currentVertex)
                                        || sinkBoundary.get((ptr - 1 + sinkLen) % sinkLen).equals(currentVertex))) {
                            ok1 = true;
                            break;
                        }
                    }
                    boolean ok2 = false;
                    int sourceLen = sourceBoundary.size();
                    for (int ptr = 0; ptr < sourceBoundary.size(); ptr++) {
                        if (sourceBoundary.get(ptr).equals(n.end) &&
                                (sourceBoundary.get((ptr + 1) % sourceLen).equals(currentVertex)
                                        || sourceBoundary.get((ptr - 1 + sourceLen) % sourceLen).equals(currentVertex))) {
                            ok2 = true;
                            break;
                        }
                    }
                    if (ok1 && ok2) {
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

        int meetSink = 0, meetSource = 0, prevMeetSink = 0, prevMeetSource = 0;
        boolean isRight = true;
        System.out.println("split for " + currentVertex.name);
        for (var edge: angle.get(currentVertex)) {
            if (edge.begin.equals(edge.end)) {
                continue;
            }
            int newMeetSink = meetSink;
            if (sinkBoundary.contains(edge.end)) {
                boolean ok = false;
                int sinkLen = sinkBoundary.size();
                for (int ptr = 0; ptr < sinkBoundary.size(); ptr++) {
                    if (sinkBoundary.get(ptr).equals(edge.end) &&
                            (sinkBoundary.get((ptr + 1) % sinkLen).equals(currentVertex)
                                    || sinkBoundary.get((ptr - 1 + sinkLen) % sinkLen).equals(currentVertex))) {
                        ok = true;
                        break;
                    }
                }
                if (ok) {
                    newMeetSink++;
                }
            }
            int newMeetSource = meetSource;
            if (sourceBoundary.contains(edge.end)) {
                boolean ok = false;
                int sourceLen = sourceBoundary.size();
                for (int ptr = 0; ptr < sourceBoundary.size(); ptr++) {
                    if (sourceBoundary.get(ptr).equals(edge.end) &&
                            (sourceBoundary.get((ptr + 1) % sourceLen).equals(currentVertex)
                                    || sourceBoundary.get((ptr - 1 + sourceLen) % sourceLen).equals(currentVertex))) {
                        ok = true;
                        break;
                    }
                }
                if (ok) {
                    newMeetSource++;
                }
            }
            if (meetSink == newMeetSink && meetSource == newMeetSource) {
                if (isRight) {
                    System.out.println("to right without modifications, vertex " + edge.end.name + " " + meetSource + " " + meetSink + " " + prevMeetSource + " " + prevMeetSink);
                    rightNeighbors.add(edge.end);
                } else {
                    System.out.println("to left without modifications, vertex " + edge.end.name + " " + meetSource + " " + meetSink + " " + prevMeetSource + " " + prevMeetSink);
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
                System.out.println("to right with modifications, vertex " + edge.end.name + " " + meetSource + " " + meetSink + " " + prevMeetSource + " " + prevMeetSink);
                rightNeighbors.add(edge.end);
            } else {
                System.out.println("to left with modifications, vertex " + edge.end.name + " " + meetSource + " " + meetSink + " " + prevMeetSource + " " + prevMeetSink);
                leftNeighbors.add(edge.end);
            }
        }

        return new NeighborSplit(currentVertex, List.of(), leftNeighbors, rightNeighbors);
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
}
