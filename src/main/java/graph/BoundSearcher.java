package graph;

import static java.lang.Double.max;
import static java.lang.Math.abs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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

    public static List<Vertex> findBound(Graph<Vertex> graph,
                                         HashSet<VertexOfDualGraph> part,
                                         HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph) {

        final int partSize = part.size();
        final int estimatedVertices = partSize * 4;

        List<VertexOfDualGraph> orderedFaces = new ArrayList<>(part);
        List<List<Vertex>> verticesByFaces = new ArrayList<>(partSize);
        List<HashMap<Vertex, Integer>> vertexPositionInFace = new ArrayList<>(partSize);
        HashMap<Vertex, Integer> numberOfFaces = new HashMap<>(estimatedVertices);
        HashMap<Long, Integer> edgeToFaceIndex = new HashMap<>(estimatedVertices * 2);
        Set<Vertex> allVertices = new HashSet<>(estimatedVertices);

        for (int i = 0; i < partSize; i++) {
            List<Vertex> faceVertices = comparisonForDualGraph.get(orderedFaces.get(i)).getVerticesOfFace();
            verticesByFaces.add(faceVertices);

            final int faceSize = faceVertices.size();
            HashMap<Vertex, Integer> posMap = new HashMap<>(faceSize + faceSize / 3); // load factor

            for (int j = 0; j < faceSize; j++) {
                Vertex current = faceVertices.get(j);
                numberOfFaces.merge(current, 1, Integer::sum);
                edgeToFaceIndex.put(computeEdgeKey(current, faceVertices.get((j + 1) % faceSize)), i);
                allVertices.add(current);
                posMap.put(current, j);
            }
            vertexPositionInFace.add(posMap);
        }

        //Assertions.assertTrue(graph.isConnected());
        Graph<Vertex> partSubgraph = graph.createSubgraphFromFaces(verticesByFaces).makeUndirectedGraph();
        //Assertions.assertTrue(partSubgraph.isConnected());

        HashMap<Vertex, TreeSet<EdgeOfGraph<Vertex>>> arrangedEdges = partSubgraph.arrangeByAngle();

        Vertex start = findLeftmostVertex(allVertices);
        List<Vertex> bound = new ArrayList<>(allVertices.size());
        bound.add(start);

        EdgeOfGraph<Vertex> startEdge = findMaxEdgeLessThanPiOver2(arrangedEdges.get(start));

        Assertions.assertTrue((0 <= startEdge.getCorner() && startEdge.getCorner() < Math.PI / 2.0) ||
                (3.0 * Math.PI / 2.0) <= startEdge.getCorner() && startEdge.getCorner() < 2 * Math.PI);

        EdgeOfGraph<Vertex> prevEdge = new EdgeOfGraph<>(startEdge.end, startEdge.begin, 0);
        Vertex current = startEdge.end;

        int faceIndex = findCommonFaceFast(startEdge.begin, startEdge.end, edgeToFaceIndex);

        while (!current.equals(start)) {
            bound.add(current);
            Vertex next;

            if (numberOfFaces.get(current) > 1) {
                EdgeOfGraph<Vertex> edge = findNextEdge(prevEdge, arrangedEdges.get(current));
                assert edge != null;
                faceIndex = findCommonFaceFast(edge.begin, edge.end, edgeToFaceIndex);
                next = edge.end;
            } else {
                List<Vertex> face = verticesByFaces.get(faceIndex);
                int currentPos = vertexPositionInFace.get(faceIndex).get(current);
                next = face.get((currentPos + 1) % face.size());
            }

            prevEdge = new EdgeOfGraph<>(next, current, 0);
            current = next;
        }

        Assertions.assertTrue(bound.size() >= 3);

        return bound;
    }

    private static long computeEdgeKey(Vertex v1, Vertex v2) {
        String s = v1.name + "_" + v2.name;
        return s.hashCode();
    }

    private static int findCommonFaceFast(Vertex v1, Vertex v2,
                                          HashMap<Long, Integer> edgeToFaceIndex) {
        long key = computeEdgeKey(v1, v2);
        Integer faceIndex = edgeToFaceIndex.get(key);

        if (faceIndex == null) {
            key = computeEdgeKey(v2, v1);
            faceIndex = edgeToFaceIndex.get(key);
        }

        if (faceIndex == null) {
            throw new RuntimeException("Can't find common face for edge " +
                    v1.getName() + " -> " + v2.getName());
        }
        return faceIndex;
    }

    private static EdgeOfGraph<Vertex> findNextEdge(EdgeOfGraph<Vertex> prevEdge, TreeSet<EdgeOfGraph<Vertex>> orderedEdges) {
        if (orderedEdges.isEmpty()) {
            return null;
        }
        EdgeOfGraph<Vertex> result = orderedEdges.lower(prevEdge);
        return result == null ? orderedEdges.last() : result;
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

    public static double findRadius(List<Vertex> vertices) {
        if (vertices.size() < 2) {
            return 0.0;
        } else if (vertices.size() == 2) {
            Vertex a = vertices.get(0);
            Vertex b = vertices.get(1);
            return Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2)) / 2.0;
        }

        Point center = findMinEnclosingCircleCenter(vertices);
        double maxRadius = 0.0;

        for (Vertex vertex : vertices) {
            double distance = Math.sqrt(Math.pow(vertex.x - center.x, 2) +
                    Math.pow(vertex.y - center.y, 2));
            if (distance > maxRadius) {
                maxRadius = distance;
            }
        }

        return maxRadius;
    }

    private static Point findMinEnclosingCircleCenter(List<Vertex> vertices) {
        double sumX = 0.0;
        double sumY = 0.0;

        for (Vertex vertex : vertices) {
            sumX += vertex.x;
            sumY += vertex.y;
        }

        double centerX = sumX / vertices.size();
        double centerY = sumY / vertices.size();

        return new Point(centerX, centerY);
    }

}