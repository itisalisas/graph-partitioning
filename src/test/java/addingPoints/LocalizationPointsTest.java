package addingPoints;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import graph.EdgeOfGraph;
import graph.Graph;
import graph.Point;
import graph.Vertex;
import graph.VertexOfDualGraph;
import graphPreparation.GraphPreparation;

import static org.junit.jupiter.api.Assertions.*;

public class LocalizationPointsTest {
    private Graph<Vertex> graph;
    private List<Vertex> vs;
    private List<EdgeOfGraph<Vertex>> edges;
    private Graph<VertexOfDualGraph> dualGraph;


    @BeforeEach
    void setUp() {
        buildTestGraph();
        GraphPreparation preparation = new GraphPreparation();
        dualGraph = preparation.prepareGraph(graph, 1e-9);
    }

    @Test
    void testOnePointInFace() {
        HashSet<Vertex> vertices = new HashSet<Vertex>();
        vertices.add(new Vertex(12, new Point(10, 2)));
        LocalizationPoints lp = new LocalizationPoints(vertices);
        HashMap<Vertex, VertexOfDualGraph> ans = lp.findFacesForPoints(dualGraph);
        assertEquals(1, ans.size());
        for (Vertex ver : vertices) {
            VertexOfDualGraph face = ans.get(ver);
            assertTrue(ver.inFaceGeom(face.getVerticesOfFace()));
        }
    }

    @Test
    void testOnePointOutFace() {
        HashSet<Vertex> vertices = new HashSet<Vertex>();
        vertices.add(new Vertex(12, new Point(2, 15)));
        LocalizationPoints lp = new LocalizationPoints(vertices);
        HashMap<Vertex, VertexOfDualGraph> ans = lp.findFacesForPoints(dualGraph);
        assertEquals(0, ans.size());
    }

    @Test
    void testAllPointsInFaces() {
        HashSet<Vertex> vertices = new HashSet<Vertex>();
        vertices.add(new Vertex(12, new Point(6, 6)));
        vertices.add(new Vertex(13, new Point(2, 6)));
        vertices.add(new Vertex(14, new Point(3, 5)));
        vertices.add(new Vertex(15, new Point(2, 3)));
        vertices.add(new Vertex(16, new Point(5, 3)));
        vertices.add(new Vertex(17, new Point(7, 3)));
        vertices.add(new Vertex(18, new Point(9, 4)));
        vertices.add(new Vertex(19, new Point(5, 5)));
        vertices.add(new Vertex(20, new Point(10, 5)));
        vertices.add(new Vertex(21, new Point(13, 8)));
        vertices.add(new Vertex(22, new Point(8, 7)));
        vertices.add(new Vertex(23, new Point(4.4, 7)));
        vertices.add(new Vertex(24, new Point(10, 2)));
        vertices.add(new Vertex(25, new Point(11, 2)));
        vertices.add(new Vertex(26, new Point(14, 6)));
        vertices.add(new Vertex(27, new Point(12, 4)));
        vertices.add(new Vertex(28, new Point(7, 5)));
        vertices.add(new Vertex(29, new Point(14, 9)));
        LocalizationPoints lp = new LocalizationPoints(vertices);
        HashMap<Vertex, VertexOfDualGraph> ans = lp.findFacesForPoints(dualGraph);
        assertEquals(18, ans.size());
        for (Vertex ver : vertices) {
            VertexOfDualGraph face = ans.get(ver);
            assertTrue(ver.inFaceGeom(face.getVerticesOfFace()));
        }
    }

    @Test
    void testMixedPointsInOutFaces() {
        HashSet<Vertex> vertices = new HashSet<Vertex>();
        vertices.add(new Vertex(12, new Point(6, 6)));
        vertices.add(new Vertex(13, new Point(2, 10)));
        vertices.add(new Vertex(14, new Point(3, 5)));
        vertices.add(new Vertex(15, new Point(2, 14)));
        vertices.add(new Vertex(16, new Point(5, 3)));
        vertices.add(new Vertex(17, new Point(8, 19)));
        vertices.add(new Vertex(18, new Point(9, 4)));
        vertices.add(new Vertex(19, new Point(5, 13)));
        vertices.add(new Vertex(20, new Point(10, 5)));
        vertices.add(new Vertex(21, new Point(13, 8)));
        vertices.add(new Vertex(22, new Point(8, 7)));
        vertices.add(new Vertex(23, new Point(4.4, 7)));
        vertices.add(new Vertex(24, new Point(10, 2)));
        vertices.add(new Vertex(25, new Point(11, 2)));
        vertices.add(new Vertex(26, new Point(14, 6)));
        vertices.add(new Vertex(27, new Point(12, 4)));
        vertices.add(new Vertex(28, new Point(7, 5)));
        vertices.add(new Vertex(29, new Point(14, 9)));
        LocalizationPoints lp = new LocalizationPoints(vertices);
        HashMap<Vertex, VertexOfDualGraph> ans = lp.findFacesForPoints(dualGraph);
        assertEquals(14, ans.size());
        for (Vertex ver : vertices) {
            VertexOfDualGraph face = ans.get(ver);
            assertTrue(ver.inFaceGeom(face.getVerticesOfFace()));
        }
    }

    @Test
    void testAllPointsOutFaces() {
        HashSet<Vertex> vertices = new HashSet<Vertex>();
        vertices.add(new Vertex(12, new Point(20, 6)));
        vertices.add(new Vertex(13, new Point(2, 10)));
        vertices.add(new Vertex(14, new Point(0, 5)));
        vertices.add(new Vertex(15, new Point(2, 14)));
        vertices.add(new Vertex(16, new Point(15, 3)));
        vertices.add(new Vertex(17, new Point(7, 0)));
        vertices.add(new Vertex(18, new Point(16, 4)));
        vertices.add(new Vertex(19, new Point(5, 13)));;
        LocalizationPoints lp = new LocalizationPoints(vertices);
        HashMap<Vertex, VertexOfDualGraph> ans = lp.findFacesForPoints(dualGraph);
        assertEquals(0, ans.size());
    }

    private void buildTestGraph() {
        graph = new Graph<Vertex>();
        vs = List.of(new Vertex(0, new Point(12, 1)),
                new Vertex(1, new Point(1, 1)),
                new Vertex(2, new Point(4, 1)),
                new Vertex(3, new Point(1, 4)),
                new Vertex(4, new Point(4, 4)),
                new Vertex(5, new Point(1, 7)),
                new Vertex(6, new Point(4, 7)),
                new Vertex(7, new Point(8, 4)),
                new Vertex(8, new Point(5, 8)),
                new Vertex(9, new Point(11, 8)),
                new Vertex(10, new Point(15, 11)),
                new Vertex(11, new Point(15, 5)));
        edges = List.of(new EdgeOfGraph<Vertex>(vs.get(1), vs.get(2), 1),
                new EdgeOfGraph<Vertex>(vs.get(4), vs.get(2), 1),
                new EdgeOfGraph<Vertex>(vs.get(1), vs.get(3), 1),
                new EdgeOfGraph<Vertex>(vs.get(3), vs.get(4), 1),
                new EdgeOfGraph<Vertex>(vs.get(5), vs.get(3), 1),
                new EdgeOfGraph<Vertex>(vs.get(5), vs.get(6), 1),
                new EdgeOfGraph<Vertex>(vs.get(6), vs.get(4), 1),
                new EdgeOfGraph<Vertex>(vs.get(7), vs.get(4), 1),
                new EdgeOfGraph<Vertex>(vs.get(4), vs.get(8), 1),
                new EdgeOfGraph<Vertex>(vs.get(6), vs.get(8), 1),
                new EdgeOfGraph<Vertex>(vs.get(8), vs.get(9), 1),
                new EdgeOfGraph<Vertex>(vs.get(7), vs.get(9), 1),
                new EdgeOfGraph<Vertex>(vs.get(11), vs.get(9), 1),
                new EdgeOfGraph<Vertex>(vs.get(11), vs.get(10), 1),
                new EdgeOfGraph<Vertex>(vs.get(10), vs.get(11), 1),
                new EdgeOfGraph<Vertex>(vs.get(11), vs.get(2), 1),
                new EdgeOfGraph<Vertex>(vs.get(2), vs.get(0), 1),
                new EdgeOfGraph<Vertex>(vs.get(0), vs.get(11), 1));
        for (Vertex v : vs) {
            graph.addVertex(v);
        }
        for (EdgeOfGraph<Vertex> e : edges) {
            graph.addEdge(e.begin, e.end, e.getLength());
        }
    }
}
