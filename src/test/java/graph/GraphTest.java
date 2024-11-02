package graph;

import graphPreparation.GraphPreparation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import partitioning.BalancedPartitioning;
import partitioning.InertialFlowPartitioning;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphTest {

    @Test
    void testDualGraphSimple() throws IOException {
        Graph g = new Graph();
        g.readGraphFromFile("src/main/resources/testGraphs/test_graph_0.txt".replace('/', File.separatorChar));
        GraphPreparation preparation = new GraphPreparation();
        Graph dualGraph = preparation.prepareGraph(g, 1e-9);
        Assertions.assertEquals(1, dualGraph.verticesNumber());
    }

    @Test
    void testDualGraph() throws IOException {
        Graph g = new Graph();
        g.readGraphFromFile("src/main/resources/testGraphs/test_graph_1.txt".replace('/', File.separatorChar));
        GraphPreparation preparation = new GraphPreparation();
        Graph dualGraph = preparation.prepareGraph(g, 1e-9);
        dualGraph.printGraphToFile("src/main/resources/testGraphs/test_graph_1_dual.txt");
        Assertions.assertEquals(6, dualGraph.verticesNumber());
    }

    @Test
    void testDualGraphWithInnerEdge() throws IOException {
        Graph g = new Graph();
        g.readGraphFromFile("src/main/resources/testGraphs/test_graph_2.txt".replace('/', File.separatorChar));
        GraphPreparation preparation = new GraphPreparation();
        Graph dualGraph = preparation.prepareGraph(g, 1e-9);
        dualGraph.printGraphToFile("src/main/resources/testGraphs/test_graph_2_dual.txt");
        Assertions.assertEquals(3, dualGraph.verticesNumber());
    }

}