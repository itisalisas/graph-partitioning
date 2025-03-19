package graph;

import static java.lang.Double.max;
import static java.lang.Math.abs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;

public class BoundSearcher {

    public static List<Vertex> findConvexHull(List<Vertex> vertices) {
        Vertex finalInitVertex = getInitVertex(vertices);

        vertices.sort((a, b) -> {
            
            Point coorDistA = finalInitVertex.coordinateDistance(a);
            Point coorDistB = finalInitVertex.coordinateDistance(b);

            double crossProduct = coorDistA.x * coorDistB.y - coorDistA.y * coorDistB.x;

            if (crossProduct == 0) {
                double distanceA = coorDistA.module();
                double distanceB = coorDistB.module();
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
            if (vertex.x < initVertex.x ||
                    (vertex.x == initVertex.x &&
                            vertex.y < initVertex.y)) {
                initVertex = vertex;
            }
        }

        return initVertex;
    }

    private static double getCrossProduct(Vertex vertex, List<Vertex> hull) {
        Vertex last = hull.get(hull.size() - 1);
        Vertex secondLast = hull.get(hull.size() - 2);

        Point lastVec = secondLast.coordinateDistance(last);
        Point newVec = last.coordinateDistance(vertex);

        return lastVec.x * newVec.y - lastVec.y * newVec.x;
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

        
        Assertions.assertTrue(graph.isConnected());
        Graph<Vertex> partSubgraph = graph.createSubgraphFromFaces(verticesByFaces).makeUndirectedGraph();
        Assertions.assertTrue(partSubgraph.isConnected());

        HashMap<Vertex, TreeSet<EdgeOfGraph<Vertex>>> arrangedEdges = partSubgraph.arrangeByAngle();

        Vertex start = findLeftmostVertex(allVertices);
        bound.add(start);

        // start is the highest among the leftmost ones, all incident edges must lie in [0;pi/2) U [3pi/2; 2pi)
        EdgeOfGraph<Vertex> startEdge = findMaxEdgeLessThanPiOver2(arrangedEdges.get(start));

        Assertions.assertTrue((0 <= startEdge.getCorner() && startEdge.getCorner() < Math.PI / 2.0) ||
                (3.0 * Math.PI) / 2.0 <= startEdge.getCorner() && startEdge.getCorner() < 2 * Math.PI);

        EdgeOfGraph<Vertex> prevEdge = new EdgeOfGraph<>(startEdge.end, startEdge.begin, 0);
        Vertex current = startEdge.end;
        Assertions.assertTrue(Arrays.stream(partSubgraph.edgesArray()).toList().contains(startEdge));
        int faceIndex = findCommonFace(startEdge.begin, startEdge.end, verticesByFaces);
        while (!current.equals(start)) {
            bound.add(current);
            Vertex next;
            if (numberOfFaces.get(current) > 1) {
                EdgeOfGraph<Vertex> edge = findNextEdge(prevEdge, arrangedEdges.get(current));
                assert edge != null;
                faceIndex = findCommonFace(edge.begin, edge.end, verticesByFaces);
                next = edge.end;
            } else {
                next = verticesByFaces.get(faceIndex).get((verticesByFaces.get(faceIndex).indexOf(current) + 1) % verticesByFaces.get(faceIndex).size());
            }
            
            prevEdge = new EdgeOfGraph<>(next, current, 0);
            current = next;
        }

        Assertions.assertTrue(bound.size() >= 3);

        return bound;
    }


    private static EdgeOfGraph<Vertex> findNextEdge(EdgeOfGraph<Vertex> prevEdge, TreeSet<EdgeOfGraph<Vertex>> orderedEdges) {
        if (orderedEdges.isEmpty()) {
            return null;
        }
        EdgeOfGraph<Vertex> result = orderedEdges.lower(prevEdge);
        return result == null ? orderedEdges.last() : result;
    }


    private static int findCommonFace(Vertex v1, Vertex v2, List<List<Vertex>> verticesByFaces) {
        int faceNumber = -1;

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
            if (leftmost == null || v.x < leftmost.x ||
                    (v.x == leftmost.x &&
                            v.y > leftmost.y)) {
                leftmost = v;
            }
        }
        return leftmost;
    }

    private static EdgeOfGraph<Vertex> findMaxEdgeLessThanPiOver2(TreeSet<EdgeOfGraph<Vertex>> sortedEdges) {
        EdgeOfGraph<Vertex> bestEdge = null;
        double bestAngle = -1;

        for (EdgeOfGraph<Vertex> edge : sortedEdges) {
            double angle = edge.getCorner();

            if (angle < Math.PI / 2 && angle > bestAngle) {
                bestAngle = angle;
                bestEdge = edge;
            }
        }
        return bestEdge == null? sortedEdges.last() : bestEdge;
    }

    private static double leftTurn(Point a, Point b, Point c) {
        return (c.x - a.x) * (b.y - a.y) - (c.y - a.y) * (b.x - a.x);
    }

    public static double findDiameter(List<Vertex> vertices) {
        List<Vertex> hull = findConvexHull(vertices);
        double diameter = 0.0;
        int n = hull.size();
        if (n == 1) return 0;
        if (n == 2) return hull.get(0).getLength(hull.get(0));
        int k = 1;
        while (abs(leftTurn(hull.get(n-1), hull.get(0), hull.get((k+1) % n))) > abs(leftTurn(hull.get(n-1), hull.get(0), hull.get(k)))) {
            k++;
        }
        for (int i = 0, j = k; i <= k && j < n; i++) {
            diameter = max(diameter, hull.get(i).getLength(hull.get(j)));
            while (j < n && abs(leftTurn(hull.get(i), hull.get((i + 1) % n), hull.get((j + 1) % n))) > abs(leftTurn(hull.get(i), hull.get((i + 1) % n), hull.get(j)))) {
                diameter = max(diameter, hull.get(i).getLength(hull.get((j + 1) % n)));
                j++;
            }
        }
        return diameter;
    }

}
