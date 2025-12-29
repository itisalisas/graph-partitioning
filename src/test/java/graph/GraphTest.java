package graph;

import graphPreparation.GraphPreparation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import readWrite.GraphReader;
import readWrite.GraphWriter;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;


class GraphTest {

    private Graph<Vertex> graph;
    private List<Vertex> vs;
    private List<EdgeOfGraph<Vertex>> edges;
    private final GraphReader graphReader = new GraphReader();
    private final GraphWriter graphWriter = new GraphWriter();

    @BeforeEach
    void setUp() {
        graph = new Graph<>();
        vs = List.of(new Vertex(0, new Point(0, 0)),
                new Vertex(1, new Point(5, -1)),
                new Vertex(2, new Point(4, -10)),
                new Vertex(3, new Point(10, 3)),
                new Vertex(4, new Point(10, 0)),
                new Vertex(5, new Point(-10, 10)),
                new Vertex(6, new Point(10, 10)),
                new Vertex(7, new Point(10, -10)),
                new Vertex(8, new Point(-10, -10)));
        edges = List.of(new EdgeOfGraph<>(vs.get(0), vs.get(1), 1), // 11 undirected edges == 22 directed edges
                new EdgeOfGraph<>(vs.get(1), vs.get(2), 1),
                new EdgeOfGraph<>(vs.get(1), vs.get(3), 1),
                new EdgeOfGraph<>(vs.get(1), vs.get(4), 1),
                new EdgeOfGraph<>(vs.get(2), vs.get(7), 1),
                new EdgeOfGraph<>(vs.get(3), vs.get(6), 1),
                new EdgeOfGraph<>(vs.get(4), vs.get(3), 1),
                new EdgeOfGraph<>(vs.get(5), vs.get(8), 1),
                new EdgeOfGraph<>(vs.get(6), vs.get(5), 1),
                new EdgeOfGraph<>(vs.get(7), vs.get(4), 1),
                new EdgeOfGraph<>(vs.get(8), vs.get(2), 1));
        for (Vertex v : vs) {
            graph.addVertex(v);
        }
        for (EdgeOfGraph<Vertex> e : edges) {
            graph.addEdge(e.begin, e.end, e.length);
        }
    }

    @Test
    void testMakeUndirectedGraph() {
        Graph<Vertex> undirGraph = graph.makeUndirectedGraph();
        // вершины не теряются
        assertEquals(vs.size(), undirGraph.verticesNumber());
        assertEquals(22, undirGraph.edgesNumber());
        // обратные ребра не добавляются еще раз
        assertEquals(22, undirGraph.makeUndirectedGraph().edgesNumber());
    }


    @Test
    void testSizes() {
        assertEquals(vs.size(), graph.verticesNumber());
        Vertex newVertex = new Vertex();
        graph.addVertex(newVertex);
        assertEquals(vs.size() + 1, graph.verticesNumber());
        graph.deleteVertex(newVertex);
        assertEquals(vs.size(), graph.verticesNumber());
        // удаленная вершина не должна удаляться еще раз
        graph.deleteVertex(newVertex);
        assertEquals(vs.size(), graph.verticesNumber());

        assertEquals(edges.size() * 2, graph.edgesNumber());
        graph.deleteEdge(vs.get(5), vs.get(8));
        assertEquals((edges.size() - 1) * 2, graph.edgesNumber());
        // удаленное ребро не должно удаляться еще раз
        graph.deleteEdge(vs.get(5), vs.get(8));
        assertEquals((edges.size() - 1) * 2, graph.edgesNumber());
    }


    @Test
    void testSumVerticesWeight() {
        for (Vertex v : graph.verticesArray()) {
            v.setWeight(2);
        }
        assertEquals(vs.size() * 2, graph.verticesSumWeight());
    }


    @Test
    void testCreateSubgraph() {
        List<Vertex> subgraphVertices = List.of(vs.get(2), vs.get(3), vs.get(4), vs.get(5), vs.get(6), vs.get(7), vs.get(8));
        Graph<Vertex> subgraph = graph.createSubgraph(new HashSet<>(subgraphVertices));
        assertEquals(subgraphVertices.size(), subgraph.verticesNumber());
        assertEquals(14, subgraph.edgesNumber());
        graph.addEdge(vs.get(0), vs.get(3), 1);
        graph.addEdge(vs.get(0), vs.get(2), 1);
        // проверка, что если между вершинами есть ребро в не входящей в список грани, то ребро не появится
        List<List<Vertex>> faces = List.of(List.of(vs.get(0), vs.get(3), vs.get(1)),
                List.of(vs.get(2), vs.get(1), vs.get(4), vs.get(7)));
        Graph<Vertex> facesSubgraph = graph.createSubgraphFromFaces(faces);
        assertEquals(6, facesSubgraph.verticesNumber());
        facesSubgraph.makeUndirectedGraph();
        assertNull(facesSubgraph.getEdges().get(vs.get(0)).get(vs.get(2)));
    }


    @Test
    void testConnectivity() {
        assertTrue(graph.isConnected());
        graph.deleteVertex(vs.get(1));
        assertFalse(graph.isConnected());
        assertEquals(7, graph.getLargestConnectedComponent().verticesNumber());
        assertEquals(2, graph.splitForConnectedComponents().size());
    }


    @Test
    void testAngles() {
        HashMap<Vertex, TreeSet<EdgeOfGraph<Vertex>>> orderedEdges = graph.arrangeByAngle();
        TreeSet<EdgeOfGraph<Vertex>> orderedEdgesForV8 = orderedEdges.get(vs.get(8));
        assertEquals(2, orderedEdgesForV8.size());
        assertEquals(edges.get(10), orderedEdgesForV8.first());
    }


    @Test
    void testDualGraphSimple() throws IOException {
        Graph<Vertex> g = new Graph<>();
        graphReader.readGraphFromFile(g, "src/main/resources/testGraphs/test_graph_0.txt".replace('/', File.separatorChar), false);
        GraphPreparation preparation = new GraphPreparation();
        Graph<VertexOfDualGraph> dualGraph = preparation.prepareGraph(g, 1e-9, "", null);
        Assertions.assertEquals(1, dualGraph.verticesNumber());
    }


    @Test
    void testDualGraph() throws IOException {
        Graph<Vertex> g = new Graph<>();
        graphReader.readGraphFromFile(g, "src/main/resources/testGraphs/test_graph_1.txt".replace('/', File.separatorChar), false);
        GraphPreparation preparation = new GraphPreparation();
        Graph<VertexOfDualGraph> dualGraph = preparation.prepareGraph(g, 1e-9, "", null);
        graphWriter.printGraphToFile(dualGraph, "src/main/resources/testGraphs", "test_graph_1_dual.txt", false);
        Assertions.assertEquals(5, dualGraph.verticesNumber());
    }


    @Test
    void testDualGraphWithInnerEdge() throws IOException {
        Graph<Vertex> g = new Graph<>();
        graphReader.readGraphFromFile(g, "src/main/resources/testGraphs/test_graph_2.txt".replace('/', File.separatorChar), false);
        GraphPreparation preparation = new GraphPreparation();
        Graph<VertexOfDualGraph> dualGraph = preparation.prepareGraph(g, 1e-9, "", null);
        graphWriter.printGraphToFile(dualGraph, "src/main/resources/testGraphs", "test_graph_2_dual.txt", false);
        Assertions.assertEquals(3, dualGraph.verticesNumber());
    }

}