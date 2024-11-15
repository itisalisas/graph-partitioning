package graph;

import org.junit.jupiter.api.Assertions;

import java.util.*;
import java.util.stream.Collectors;

public class BoundSearcher {

    public static List<Vertex> findConvexHull(List<Vertex> vertices) {
        Vertex finalInitVertex = getInitVertex(vertices);

        vertices.sort((a, b) -> {
            double ax = a.getX() - finalInitVertex.getX();
            double ay = a.getY() - finalInitVertex.getY();
            double bx = b.getX() - finalInitVertex.getX();
            double by = b.getY() - finalInitVertex.getY();

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
            if (vertex.getX() < initVertex.getX() ||
                    (vertex.getX() == initVertex.getX() &&
                            vertex.getY() < initVertex.getY())) {
                initVertex = vertex;
            }
        }

        return initVertex;
    }

    private static double getCrossProduct(Vertex vertex, List<Vertex> hull) {
        Vertex last = hull.get(hull.size() - 1);
        Vertex secondLast = hull.get(hull.size() - 2);

        double lastVecX = last.getX() - secondLast.getX();
        double lastVecY = last.getY() - secondLast.getY();

        double newVecX = vertex.getX() - last.getX();
        double newVecY = vertex.getY() - last.getY();

        return lastVecX * newVecY - lastVecY * newVecX;
    }

    public static List<Vertex> findBound(Graph<Vertex> graph, HashSet<VertexOfDualGraph> part, HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph) {
        List<VertexOfDualGraph> orderedFaces = part.stream().toList();
        List<Vertex> bound = new ArrayList<>();
        List<List<Vertex>> verticesByFaces = new ArrayList<>();
        HashMap<Vertex, Integer> numberOfFaces = new HashMap<>();

        for (int i = 0; i < part.size(); i++) {
            VertexOfDualGraph v = orderedFaces.get(i);
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

        Graph<Vertex> partSubgraph = graph.createSubgraphFromFaces(verticesByFaces).makeUndirectedGraph();
        Assertions.assertTrue(partSubgraph.isConnected());

        HashMap<Vertex, TreeSet<EdgeOfGraph>> arrangedEdges = partSubgraph.arrangeByAngle();

        Vertex start = findLeftmostVertex(allVertices);
        //  System.out.println("start vertex = " + start.getName());
        bound.add(start);

        // start is the highest among the leftmost ones, all incident edges must lie in [0;pi/2) U [3pi/2; 2pi)
        EdgeOfGraph startEdge = findMaxEdgeLessThanPiOver2(arrangedEdges.get(start));

        Assertions.assertTrue((0 <= startEdge.getCorner() && startEdge.getCorner() < Math.PI / 2.0) ||
                (3.0 * Math.PI) / 2.0 <= startEdge.getCorner() && startEdge.getCorner() < 2 * Math.PI);

        // System.out.println(startEdge.getBegin().getName() + " -> " + startEdge.getEnd().getName());
        EdgeOfGraph prevEdge = new EdgeOfGraph(startEdge.getEnd(), startEdge.getBegin(), 0);
        Vertex current = startEdge.getEnd();
        Assertions.assertTrue(Arrays.stream(partSubgraph.edgesArray()).toList().contains(startEdge));
        int faceIndex = findCommonFace(startEdge.getBegin(), startEdge.getEnd(), verticesByFaces);
        while (!current.equals(start)) {
            bound.add(current);
            Vertex next;
            if (numberOfFaces.get(current) > 1) {
                // System.out.println("change face");
                EdgeOfGraph edge = findNextEdge(prevEdge, arrangedEdges.get(current));
                assert edge != null;
                faceIndex = findCommonFace(edge.getBegin(), edge.getEnd(), verticesByFaces);
                next = edge.getEnd();
            } else {
                next = verticesByFaces.get(faceIndex).get((verticesByFaces.get(faceIndex).indexOf(current) + 1) % verticesByFaces.get(faceIndex).size());
            }
            // System.out.println(current.getName() + " -> " + next.getName());
            prevEdge = new EdgeOfGraph(next, current, 0);
            current = next;
        }

        Assertions.assertTrue(bound.size() >= 3);

        // System.out.println("Bound size: " + bound.size());

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
        //System.out.println("Search common face for " + v1.getName() + " and " + v2.getName());
        for (List<Vertex> face : verticesByFaces) {
            for (int ptr = 0; ptr < face.size(); ptr++) {
                if ((face.get(ptr).equals(v1) && face.get((ptr + 1) % face.size()).equals(v2)) ||
                face.get(ptr).equals(v2) && face.get((ptr + 1) % face.size()).equals(v1)) {
                    if (faceNumber != -1 && faceNumber != verticesByFaces.indexOf(face)) {
                        throw new RuntimeException("Multiple common faces for edge");
                    }
                    faceNumber = verticesByFaces.indexOf(face);
                }
            }
        }

        if (faceNumber == -1) {
            throw new RuntimeException("Can't find common face for edge " + v1.getName() + " -> " + v2.getName());
        }
        return faceNumber;
    }

    private static Vertex findLeftmostVertex(Set<Vertex> partition) {
        Vertex leftmost = null;
        for (Vertex v : partition) {
            if (leftmost == null || v.getX() < leftmost.getX() ||
                    (v.getX() == leftmost.getX() &&
                            v.getY() > leftmost.getY())) {
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
