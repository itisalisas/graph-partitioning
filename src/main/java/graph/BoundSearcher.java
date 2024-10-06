package graph;

import org.junit.jupiter.api.Assertions;

import java.util.*;
import java.util.stream.Collectors;

public class BoundSearcher {

    public static List<Vertex> findConvexHull(List<Vertex> vertices) {
        Vertex finalInitVertex = getInitVertex(vertices);

        vertices.sort((a, b) -> {
            double ax = a.getPoint().getX() - finalInitVertex.getPoint().getX();
            double ay = a.getPoint().getY() - finalInitVertex.getPoint().getY();
            double bx = b.getPoint().getX() - finalInitVertex.getPoint().getX();
            double by = b.getPoint().getY() - finalInitVertex.getPoint().getY();

            double crossProduct = ax * by - ay * bx;

            if (crossProduct == 0) {
                double distanceA = Math.sqrt(ax * ax + ay * ay);
                double distanceB = Math.sqrt(bx * bx + by * by);
                return Double.compare(distanceA, distanceB);
            }

            return -Double.compare(crossProduct, 0);
        });

        List<Vertex> hull = new ArrayList<>();

        for (Vertex vertex : vertices) {
            while (hull.size() >= 2) {

                double crossProduct = getCrossProduct(vertex, hull);

                if (crossProduct <= 0) {
                    hull.remove(hull.size() - 1);
                } else {
                    break;
                }
            }
            hull.add(vertex);
        }

        return hull;
    }

    private static Vertex getInitVertex(List<Vertex> vertices) {
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("Convex hull calculation requires at least 3 points");
        }

        Vertex initVertex = vertices.get(0);
        for (Vertex vertex : vertices) {
            if (vertex.getPoint().getX() < initVertex.getPoint().getX() ||
                    (vertex.getPoint().getX() == initVertex.getPoint().getX() &&
                            vertex.getPoint().getY() < initVertex.getPoint().getY())) {
                initVertex = vertex;
            }
        }

        return initVertex;
    }

    private static double getCrossProduct(Vertex vertex, List<Vertex> hull) {
        Vertex last = hull.get(hull.size() - 1);
        Vertex secondLast = hull.get(hull.size() - 2);

        double lastVecX = last.getPoint().getX() - secondLast.getPoint().getX();
        double lastVecY = last.getPoint().getY() - secondLast.getPoint().getY();

        double newVecX = vertex.getPoint().getX() - last.getPoint().getX();
        double newVecY = vertex.getPoint().getY() - last.getPoint().getY();

        return lastVecX * newVecY - lastVecY * newVecX;
    }

    public static List<Vertex> findBound(Graph graph, HashSet<Vertex> part, HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph) {
        List<Vertex> orderedFaces = part.stream().toList();
        List<Vertex> bound = new ArrayList<>();
        List<List<Vertex>> verticesByFaces = new ArrayList<>();
        HashMap<Vertex, Integer> numberOfFaces = new HashMap<>();

        for (int i = 0; i < part.size(); i++) {
            Vertex v = orderedFaces.get(i);
            verticesByFaces.add(comparisonForDualGraph.get(v).getVerticesOfFace());
            for (Vertex u : verticesByFaces.get(i)) {
                if (!numberOfFaces.containsKey(u)) {
                    numberOfFaces.put(u, 0);
                }
                numberOfFaces.replace(u, numberOfFaces.get(u), numberOfFaces.get(u) + 1);
            }
        }

        Set<Vertex> allVertices = verticesByFaces
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        Graph partSubgraph = graph.createSubgraph(allVertices).makeUndirectedGraph();
        Assertions.assertTrue(partSubgraph.isConnected());

        HashMap<Vertex, TreeSet<EdgeOfGraph>> arrangedEdges = partSubgraph.arrangeByAngle();

        Vertex start = findLeftmostVertex(allVertices);
        bound.add(start);

        // start is the highest among the leftmost ones, all incident edges must lie in [0;pi/2) U [3pi/2; 2pi)
        EdgeOfGraph startEdge = findMaxEdgeLessThanPiOver2(arrangedEdges.get(start));
        EdgeOfGraph prevEdge = new EdgeOfGraph(startEdge.getEnd(), startEdge.getBegin(), 0);
        Vertex current = startEdge.getEnd();
        int faceIndex = findCommonFace(startEdge.getBegin(), startEdge.getEnd(), verticesByFaces);
        while (!current.equals(start)) {
            bound.add(current);
            Vertex next;
            if (numberOfFaces.get(current) > 1) {
                EdgeOfGraph edge = findNextEdge(prevEdge, arrangedEdges.get(current));
                assert edge != null;
                faceIndex = findCommonFace(edge.getBegin(), edge.getEnd(), verticesByFaces);
                next = edge.getEnd();
            } else {
                next = verticesByFaces.get(faceIndex).get((verticesByFaces.get(faceIndex).indexOf(current) + 1) % verticesByFaces.get(faceIndex).size());
            }
            prevEdge = new EdgeOfGraph(next, current, 0);
            current = next;
        }

        System.out.println("Bound size: " + bound.size());

        return bound;
    }

    private static EdgeOfGraph findNextEdge(EdgeOfGraph prevEdge, TreeSet<EdgeOfGraph> orderedEdges) {
        if (orderedEdges.isEmpty()) {
            return null;
        }
        EdgeOfGraph result = orderedEdges.lower(prevEdge);
        return result == null ? orderedEdges.last() : result;
    }

    private static int findCommonFace(Vertex v1, Vertex v2, List<List<Vertex>> verticesByFaces) {
        int faceNumber = -1;
        for (List<Vertex> face : verticesByFaces) {
            Set<Vertex> verticesSet = new HashSet<>(face);
            if (verticesSet.contains(v1) && verticesSet.contains(v2)) {
                if (faceNumber != -1) {
                    throw new RuntimeException("Multiple common faces for edge");
                }
                faceNumber = verticesByFaces.indexOf(face);
            }
        }
        if (faceNumber == -1) {
            throw new RuntimeException("Can't find common face for edge");
        }
        return faceNumber;
    }

    private static Vertex findLeftmostVertex(Set<Vertex> partition) {
        Vertex leftmost = null;
        for (Vertex v : partition) {
            if (leftmost == null || v.getPoint().getX() < leftmost.getPoint().getX() ||
                    (v.getPoint().getX() == leftmost.getPoint().getX() &&
                            v.getPoint().getY() > leftmost.getPoint().getY())) {
                leftmost = v;
            }
        }
        return leftmost;
    }

    private static EdgeOfGraph findMaxEdgeLessThanPiOver2(TreeSet<EdgeOfGraph> sortedEdges) {
        EdgeOfGraph bestEdge = null;
        double bestAngle = -1;

        for (EdgeOfGraph edge : sortedEdges) {
            double angle = edge.getCorner();

            if (angle < Math.PI / 2 && angle > bestAngle) {
                bestAngle = angle;
                bestEdge = edge;
            }
        }
        return bestEdge == null? sortedEdges.last() : bestEdge;
    }

}
