package partitioning;

import graph.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MaxFlowReifTest {

    /**
     * Creates a simple 3x3 grid graph
     * 0---1---2
     * |   |   |
     * 3---4---5
     * |   |   |
     * 6---7---8
     */
    private Graph<Vertex> createGridGraph3x3() {
        Graph<Vertex> graph = new Graph<>();
        List<Vertex> vertices = new ArrayList<>();
        
        // Create 3x3 grid vertices
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int index = i * 3 + j;
                Vertex v = new Vertex(index, new Point(j * 10.0, i * 10.0), 1.0);
                vertices.add(v);
                graph.addVertex(v);
            }
        }
        
        // Add horizontal edges
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 2; j++) {
                int v1 = i * 3 + j;
                int v2 = i * 3 + j + 1;
                graph.addEdge(vertices.get(v1), vertices.get(v2), 10.0);
                graph.addEdge(vertices.get(v2), vertices.get(v1), 10.0);
            }
        }
        
        // Add vertical edges
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) {
                int v1 = i * 3 + j;
                int v2 = (i + 1) * 3 + j;
                graph.addEdge(vertices.get(v1), vertices.get(v2), 10.0);
                graph.addEdge(vertices.get(v2), vertices.get(v1), 10.0);
            }
        }
        
        return graph;
    }

    /**
     * Creates a dual graph for 3x3 grid with 4 faces plus source and sink
     * Face layout:
     * +---+---+
     * | 0 | 1 |
     * +---+---+
     * | 2 | 3 |
     * +---+---+
     */
    private Graph<VertexOfDualGraph> createDualGraphFor3x3Grid(Graph<Vertex> primalGraph) {
        Graph<VertexOfDualGraph> dualGraph = new Graph<>();
        List<Vertex> vertices = primalGraph.verticesArray();
        
        // Create source and sink
        VertexOfDualGraph source = new VertexOfDualGraph(-1, new Point(-10, 10), 1.0);
        VertexOfDualGraph sink = new VertexOfDualGraph(-2, new Point(30, 10), 1.0);
        
        // Create 4 interior faces
        ArrayList<Vertex> face0Vertices = new ArrayList<>();
        face0Vertices.add(vertices.get(0));
        face0Vertices.add(vertices.get(1));
        face0Vertices.add(vertices.get(4));
        face0Vertices.add(vertices.get(3));
        VertexOfDualGraph face0 = new VertexOfDualGraph(0, new Point(5, 5), 4.0, face0Vertices);
        
        ArrayList<Vertex> face1Vertices = new ArrayList<>();
        face1Vertices.add(vertices.get(1));
        face1Vertices.add(vertices.get(2));
        face1Vertices.add(vertices.get(5));
        face1Vertices.add(vertices.get(4));
        VertexOfDualGraph face1 = new VertexOfDualGraph(1, new Point(15, 5), 4.0, face1Vertices);
        
        ArrayList<Vertex> face2Vertices = new ArrayList<>();
        face2Vertices.add(vertices.get(3));
        face2Vertices.add(vertices.get(4));
        face2Vertices.add(vertices.get(7));
        face2Vertices.add(vertices.get(6));
        VertexOfDualGraph face2 = new VertexOfDualGraph(2, new Point(5, 15), 4.0, face2Vertices);
        
        ArrayList<Vertex> face3Vertices = new ArrayList<>();
        face3Vertices.add(vertices.get(4));
        face3Vertices.add(vertices.get(5));
        face3Vertices.add(vertices.get(8));
        face3Vertices.add(vertices.get(7));
        VertexOfDualGraph face3 = new VertexOfDualGraph(3, new Point(15, 15), 4.0, face3Vertices);
        
        dualGraph.addVertex(source);
        dualGraph.addVertex(sink);
        dualGraph.addVertex(face0);
        dualGraph.addVertex(face1);
        dualGraph.addVertex(face2);
        dualGraph.addVertex(face3);
        
        // Connect source to left faces (0, 2)
        dualGraph.addEdge(source, face0, 1.0, 10.0);
        dualGraph.addEdge(face0, source, 1.0, 10.0);
        dualGraph.addEdge(source, face2, 1.0, 10.0);
        dualGraph.addEdge(face2, source, 1.0, 10.0);
        
        // Connect sink to right faces (1, 3)
        dualGraph.addEdge(sink, face1, 1.0, 10.0);
        dualGraph.addEdge(face1, sink, 1.0, 10.0);
        dualGraph.addEdge(sink, face3, 1.0, 10.0);
        dualGraph.addEdge(face3, sink, 1.0, 10.0);
        
        // Connect neighboring faces
        // face0 - face1 (horizontal)
        dualGraph.addEdge(face0, face1, 1.0, 10.0);
        dualGraph.addEdge(face1, face0, 1.0, 10.0);
        
        // face2 - face3 (horizontal)
        dualGraph.addEdge(face2, face3, 1.0, 10.0);
        dualGraph.addEdge(face3, face2, 1.0, 10.0);
        
        // face0 - face2 (vertical)
        dualGraph.addEdge(face0, face2, 1.0, 10.0);
        dualGraph.addEdge(face2, face0, 1.0, 10.0);
        
        // face1 - face3 (vertical)
        dualGraph.addEdge(face1, face3, 1.0, 10.0);
        dualGraph.addEdge(face3, face1, 1.0, 10.0);
        
        return dualGraph;
    }

    @Test
    void testFindFlowOnGridGraph() {
        Graph<Vertex> primalGraph = createGridGraph3x3();
        Graph<VertexOfDualGraph> dualGraph = createDualGraphFor3x3Grid(primalGraph);
        
        VertexOfDualGraph source = null;
        VertexOfDualGraph sink = null;
        
        for (VertexOfDualGraph v : dualGraph.verticesArray()) {
            if (v.getName() == -1) source = v;
            if (v.getName() == -2) sink = v;
        }
        
        assertNotNull(source, "Source should exist in dual graph");
        assertNotNull(sink, "Sink should exist in dual graph");
        
        MaxFlowReif maxFlow = new MaxFlowReif(primalGraph, dualGraph, source, sink);
        FlowResult result = maxFlow.findFlow();
        
        assertNotNull(result, "Flow result should not be null");
        assertTrue(result.getFlowSize() > 0, "Flow size should be positive");
        
        // Check that some edges are saturated
        int saturatedEdges = 0;
        for (VertexOfDualGraph v : dualGraph.verticesArray()) {
            if (dualGraph.getEdges().get(v) != null) {
                for (Map.Entry<VertexOfDualGraph, Edge> entry : dualGraph.getEdges().get(v).entrySet()) {
                    Edge edge = entry.getValue();
                    if (Math.abs(edge.flow - edge.getBandwidth()) < 1e-9) {
                        saturatedEdges++;
                    }
                }
            }
        }
        
        assertTrue(saturatedEdges > 0, "At least some edges should be saturated");
    }

    @Test
    void testFlowConservation() {
        Graph<Vertex> primalGraph = createGridGraph3x3();
        Graph<VertexOfDualGraph> dualGraph = createDualGraphFor3x3Grid(primalGraph);
        
        VertexOfDualGraph source = null;
        VertexOfDualGraph sink = null;
        
        for (VertexOfDualGraph v : dualGraph.verticesArray()) {
            if (v.getName() == -1) source = v;
            if (v.getName() == -2) sink = v;
        }
        
        MaxFlowReif maxFlow = new MaxFlowReif(primalGraph, dualGraph, source, sink);
        FlowResult result = maxFlow.findFlow();
        
        // For intermediate nodes (not source or sink), flow in = flow out
        for (VertexOfDualGraph v : dualGraph.verticesArray()) {
            if (v.equals(source) || v.equals(sink)) continue;
            
            double flowIn = 0.0;
            double flowOut = 0.0;
            
            // Calculate outgoing flow
            if (dualGraph.getEdges().get(v) != null) {
                for (Edge edge : dualGraph.getEdges().get(v).values()) {
                    flowOut += edge.flow;
                }
            }
            
            // Calculate incoming flow
            for (VertexOfDualGraph other : dualGraph.verticesArray()) {
                if (dualGraph.getEdges().get(other) != null && 
                    dualGraph.getEdges().get(other).containsKey(v)) {
                    flowIn += dualGraph.getEdges().get(other).get(v).flow;
                }
            }
            
            assertEquals(flowIn, flowOut, 1e-6, 
                "Flow conservation violated at vertex " + v.getName());
        }
    }

    @Test
    void testGridGraphVerticesCount() {
        Graph<Vertex> grid = createGridGraph3x3();
        assertEquals(9, grid.verticesNumber(), "3x3 grid should have 9 vertices");
    }

    @Test
    void testGridGraphEdgesCount() {
        Graph<Vertex> grid = createGridGraph3x3();
        // 3x3 grid: 6 horizontal edges (2 per row) + 6 vertical edges (2 per column)
        // Each edge is bidirectional, so 24 total
        assertEquals(24, grid.edgesNumber(), "3x3 grid should have 24 directed edges");
    }

    @Test
    void testDualGraphStructure() {
        Graph<Vertex> primalGraph = createGridGraph3x3();
        Graph<VertexOfDualGraph> dualGraph = createDualGraphFor3x3Grid(primalGraph);
        
        // 4 faces + source + sink = 6 vertices
        assertEquals(6, dualGraph.verticesNumber(), 
            "Dual graph should have 6 vertices (4 faces + source + sink)");
        
        // Check that source and sink exist
        boolean hasSource = false;
        boolean hasSink = false;
        
        for (VertexOfDualGraph v : dualGraph.verticesArray()) {
            if (v.getName() == -1) hasSource = true;
            if (v.getName() == -2) hasSink = true;
        }
        
        assertTrue(hasSource, "Dual graph should have source vertex");
        assertTrue(hasSink, "Dual graph should have sink vertex");
    }
}


