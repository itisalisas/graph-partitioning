package partitioning;

import java.util.*;

import graph.*;

public class MaxFlowReif implements MaxFlow {
    Graph<Vertex> initGraph;
    Graph<VertexOfDualGraph> dualGraph;
    VertexOfDualGraph source;
    VertexOfDualGraph sink;
    double flow;

    public MaxFlowReif(Graph<Vertex> initGraph, Graph<VertexOfDualGraph> dualGraph, VertexOfDualGraph source, VertexOfDualGraph sink) {
        this.initGraph = initGraph;
        this.dualGraph = dualGraph;
        this.source = source;
        this.sink = sink;
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
        
        Set<Long> sourceFaceVertexNames = new HashSet<>();
        Set<Long> sinkFaceVertexNames = new HashSet<>();
        
        for (VertexOfDualGraph face : sourceNeighbors) {
            if (face.getVerticesOfFace() != null) {
                for (Vertex v : face.getVerticesOfFace()) {
                    sourceFaceVertexNames.add(v.getName());
                }
            }
        }
        
        for (VertexOfDualGraph face : sinkNeighbors) {
            if (face.getVerticesOfFace() != null) {
                for (Vertex v : face.getVerticesOfFace()) {
                    sinkFaceVertexNames.add(v.getName());
                }
            }
        }
        
        Set<Long> sourceBoundaryNames = new HashSet<>();
        Set<Long> sinkBoundaryNames = new HashSet<>();
        for (Vertex v : sourceBoundary) sourceBoundaryNames.add(v.getName());
        for (Vertex v : sinkBoundary) sinkBoundaryNames.add(v.getName());
        
        List<Vertex> verticesToRemove = new ArrayList<>();
        for (Vertex v : modifiedGraph.verticesArray()) {
            long name = v.getName();
            if (sourceFaceVertexNames.contains(name) && !sourceBoundaryNames.contains(name)) {
                verticesToRemove.add(v);
            }
            else if (sinkFaceVertexNames.contains(name) && !sinkBoundaryNames.contains(name)) {
                verticesToRemove.add(v);
            }
        }
        
        for (Vertex v : verticesToRemove) {
            modifiedGraph.deleteVertex(v);
        }
        
        List<Vertex> sourceBoundaryMapped = new ArrayList<>();
        List<Vertex> sinkBoundaryMapped = new ArrayList<>();
        for (Vertex v : modifiedGraph.verticesArray()) {
            if (sourceBoundary.stream().anyMatch(b -> b.getName() == v.getName())) {
                sourceBoundaryMapped.add(v);
            }
            if (sinkBoundary.stream().anyMatch(b -> b.getName() == v.getName())) {
                sinkBoundaryMapped.add(v);
            }
        }
        
        DijkstraResult shortestPathResult = dijkstraMultiSource(modifiedGraph, sourceBoundaryMapped, sinkBoundaryMapped);
        
        if (shortestPathResult == null || shortestPathResult.path.isEmpty()) {
            return new FlowResult(0, dualGraph, source, sink);
        }
        
        List<Vertex> shortestPath = shortestPathResult.path;
        
        HashSet<VertexOfDualGraph> allDualVerticesSet = new HashSet<>(allDualVertices);
        allDualVerticesSet.remove(source);
        allDualVerticesSet.remove(sink);
        List<Vertex> externalBoundary = BoundSearcher.findBound(initGraph, allDualVerticesSet, comparisonForDualGraph);
        
        double minPathLength = Double.MAX_VALUE;
        List<Vertex> bestPath = null;
        
        for (Vertex pathVertex : shortestPath) {
            if (sourceBoundaryMapped.contains(pathVertex) || sinkBoundaryMapped.contains(pathVertex)) {
                continue;
            }
            
            Graph<Vertex> splitGraph = modifiedGraph.clone();
            
            Set<Long> validVertexNames = new HashSet<>();
            for (Vertex v : splitGraph.verticesArray()) {
                validVertexNames.add(v.getName());
            }
            
            for (Vertex v : splitGraph.verticesArray()) {
                if (splitGraph.getEdges().get(v) == null) continue;
                
                List<Vertex> toRemove = new ArrayList<>();
                for (Vertex neighbor : splitGraph.getEdges().get(v).keySet()) {
                    if (!validVertexNames.contains(neighbor.getName())) {
                        toRemove.add(neighbor);
                    }
                }
                
                for (Vertex neighbor : toRemove) {
                    splitGraph.getEdges().get(v).remove(neighbor);
                }
            }
            
            Vertex splitVertex1 = new Vertex(pathVertex.getName() * 1000 + 1, pathVertex.x, pathVertex.y, pathVertex.getWeight());
            Vertex splitVertex2 = new Vertex(pathVertex.getName() * 1000 + 2, pathVertex.x, pathVertex.y, pathVertex.getWeight());
            
            splitGraph.addVertex(splitVertex1);
            splitGraph.addVertex(splitVertex2);
            
            List<Vertex> neighbors = new ArrayList<>();
            
            if (splitGraph.getEdges().get(pathVertex) != null) {
                neighbors.addAll(splitGraph.getEdges().get(pathVertex).keySet());
            }
            
            Set<Long> splitGraphVertexNames = new HashSet<>();
            for (Vertex v : splitGraph.verticesArray()) {
                splitGraphVertexNames.add(v.getName());
            }
            
            List<Vertex> validNeighbors = new ArrayList<>();
            for (Vertex neighbor : neighbors) {
                if (splitGraphVertexNames.contains(neighbor.getName())) {
                    validNeighbors.add(neighbor);
                }
            }
            
            int mid = validNeighbors.size() / 2;
            for (int i = 0; i < validNeighbors.size(); i++) {
                Vertex neighborOld = validNeighbors.get(i);
                double edgeLength = splitGraph.getEdges().get(pathVertex).get(neighborOld).length;
                
                Vertex neighborInGraph = null;
                for (Vertex v : splitGraph.verticesArray()) {
                    if (v.getName() == neighborOld.getName()) {
                        neighborInGraph = v;
                        break;
                    }
                }
                
                if (neighborInGraph == null) {
                    continue;
                }
                
                if (i < mid) {
                    splitGraph.addEdge(splitVertex1, neighborInGraph, edgeLength);
                } else {
                    splitGraph.addEdge(splitVertex2, neighborInGraph, edgeLength);
                }
            }
            
            splitGraph.deleteVertex(pathVertex);
            
            List<Vertex> boundaryInSplitGraph = new ArrayList<>();
            for (Vertex v : splitGraph.verticesArray()) {
                if (externalBoundary.stream().anyMatch(b -> b.getName() == v.getName())) {
                    boundaryInSplitGraph.add(v);
                }
            }
            
            DijkstraResult path1ToBoundary = dijkstraMultiSource(splitGraph, Arrays.asList(splitVertex1), boundaryInSplitGraph);
            DijkstraResult path2ToBoundary = dijkstraMultiSource(splitGraph, Arrays.asList(splitVertex2), boundaryInSplitGraph);
            
            if (path1ToBoundary == null || path2ToBoundary == null) {
                continue;
            }
            
            double totalDistance = path1ToBoundary.distance + path2ToBoundary.distance;

            List<Vertex> combinedPath = new ArrayList<>(path1ToBoundary.path);
            
            for (int i = path2ToBoundary.path.size() - 2; i >= 0; i--) {
                combinedPath.add(path2ToBoundary.path.get(i));
            }
            
            DijkstraResult result = new DijkstraResult(combinedPath, totalDistance);
            
            if (result != null && result.distance < minPathLength) {
                minPathLength = result.distance;
                List<Vertex> mappedPath = new ArrayList<>();
                for (int i = 0; i < result.path.size(); i++) {
                    Vertex v = result.path.get(i);
                    long vertexName = v.getName();
                    
                    if (vertexName >= 10000000) {
                        vertexName = vertexName / 10000000;
                    }
                    
                    for (Vertex orig : initGraph.verticesArray()) {
                        if (orig.getName() == vertexName) {
                            if (mappedPath.isEmpty() || !mappedPath.get(mappedPath.size() - 1).equals(orig)) {
                                mappedPath.add(orig);
                            }
                            break;
                        }
                    }
                }
                bestPath = mappedPath;
            }
        }
        
        if (bestPath == null) {
            bestPath = shortestPath;
        }
        
        List<Vertex> pathInOriginalGraph = new ArrayList<>();
        for (Vertex v : bestPath) {
            for (Vertex origV : initGraph.verticesArray()) {
                if (origV.getName() == v.getName()) {
                    pathInOriginalGraph.add(origV);
                    break;
                }
            }
        }
        
        flow = fillFlowInDualGraph(pathInOriginalGraph, dualGraph);

        return new FlowResult(flow, dualGraph, source, sink);
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
