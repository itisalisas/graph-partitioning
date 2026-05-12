package graphPreparation;

import graph.Graph;
import graph.Point;
import graph.Vertex;
import graph.VertexOfDualGraph;
import org.junit.jupiter.api.Test;
import readWrite.CoordinateConversion;

import java.util.ArrayList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class NestedFacesRemoverTest {

    @Test
    void testRemoveNestedFaces_ThreeFaces_OneBigTwoNested() {
        Graph<VertexOfDualGraph> dualGraph = new Graph<>();
        
        VertexOfDualGraph outerFace = createOuterFace();
        VertexOfDualGraph innerFace1 = createInnerFace1();
        VertexOfDualGraph innerFace2 = createInnerFace2();
        VertexOfDualGraph graphOuterFace = createGraphOuterFace();
        
        double expectedTotalWeight = outerFace.getWeight() + innerFace1.getWeight() + innerFace2.getWeight();
        
        System.out.println("=== Test: Three faces - one big with two nested inside ===");
        System.out.println("Outer face weight: " + outerFace.getWeight());
        System.out.println("Inner face 1 weight: " + innerFace1.getWeight());
        System.out.println("Inner face 2 weight: " + innerFace2.getWeight());
        System.out.println("Expected total weight: " + expectedTotalWeight);
        
        dualGraph.addVertex(outerFace);
        dualGraph.addVertex(innerFace1);
        dualGraph.addVertex(innerFace2);
        dualGraph.addVertex(graphOuterFace);
        
        dualGraph.addEdge(outerFace, innerFace1, 1.0);
        dualGraph.addEdge(innerFace1, outerFace, 1.0);
        dualGraph.addEdge(outerFace, innerFace2, 1.0);
        dualGraph.addEdge(innerFace2, outerFace, 1.0);
        dualGraph.addEdge(graphOuterFace, outerFace, 1.0);
        
        assertEquals(4, dualGraph.verticesNumber(), "Initially should have 3 faces");
        
        Set<VertexOfDualGraph> removed = NestedFacesRemover.removeNestedFaces(dualGraph, graphOuterFace);
        
        System.out.println("\n=== After removeNestedFaces ===");
        System.out.println("Remaining faces: " + dualGraph.verticesNumber());
        System.out.println("Removed faces: " + removed.size());
        
        assertEquals(2, dualGraph.verticesNumber(), "After removal should have 2 faces");
        assertEquals(2, removed.size(), "Should have removed 2 nested faces");
        
        VertexOfDualGraph remainingFace = dualGraph.verticesArray().get(0);
        System.out.println("Remaining face weight: " + remainingFace.getWeight());
        System.out.println("Remaining face ID: " + remainingFace.getName());
        
        assertEquals(expectedTotalWeight, remainingFace.getWeight(), 0.001, 
            "Remaining face weight should equal sum of all 3 faces");
        
        assertTrue(removed.contains(innerFace1), "Inner face 1 should be removed");
        assertTrue(removed.contains(innerFace2), "Inner face 2 should be removed");
        
        System.out.println("\n✓ Test PASSED: All assertions successful!");
    }
    
    @Test
    void testRemoveNestedFaces_NoNesting() {
        Graph<VertexOfDualGraph> dualGraph = new Graph<>();
        
        VertexOfDualGraph face1 = createSeparateFace1();
        VertexOfDualGraph face2 = createSeparateFace2();
        VertexOfDualGraph graphOuterFace = createGraphOuterFace();
        
        dualGraph.addVertex(face1);
        dualGraph.addVertex(face2);
        dualGraph.addVertex(graphOuterFace);
        
        dualGraph.addEdge(face1, face2, 1.0);
        dualGraph.addEdge(face2, face1, 1.0);
        dualGraph.addEdge(graphOuterFace, face1, 1.0);
        dualGraph.addEdge(graphOuterFace, face2, 1.0);
        
        double weight1Before = face1.getWeight();
        double weight2Before = face2.getWeight();
        
        Set<VertexOfDualGraph> removed = NestedFacesRemover.removeNestedFaces(dualGraph, graphOuterFace);
        
        assertEquals(3, dualGraph.verticesNumber(), "Should still have 3 faces (no nesting)");
        assertEquals(0, removed.size(), "Should have removed 0 faces");
        assertEquals(weight1Before, face1.getWeight(), 0.001, "Face 1 weight unchanged");
        assertEquals(weight2Before, face2.getWeight(), 0.001, "Face 2 weight unchanged");
    }
    
    private VertexOfDualGraph createOuterFace() {
        ArrayList<Vertex> vertices = new ArrayList<>();
        vertices.add(new Vertex(1, 0, 0, 1.0));
        vertices.add(new Vertex(2, 10, 0, 1.0));
        vertices.add(new Vertex(3, 10, 10, 1.0));
        vertices.add(new Vertex(4, 0, 10, 1.0));
        
        Point center = new Point(5, 5);
        double weight = 100.0;
        
        return new VertexOfDualGraph(100, center, weight, vertices);
    }
    
    private VertexOfDualGraph createInnerFace1() {
        ArrayList<Vertex> vertices = new ArrayList<>();
        vertices.add(new Vertex(5, 1, 1, 1.0));
        vertices.add(new Vertex(6, 4, 1, 1.0));
        vertices.add(new Vertex(7, 4, 4, 1.0));
        vertices.add(new Vertex(8, 1, 4, 1.0));
        
        Point center = new Point(2.5, 2.5);
        double weight = 25.0;
        
        return new VertexOfDualGraph(101, center, weight, vertices);
    }
    
    private VertexOfDualGraph createInnerFace2() {
        ArrayList<Vertex> vertices = new ArrayList<>();
        vertices.add(new Vertex(9, 6, 6, 1.0));
        vertices.add(new Vertex(10, 9, 6, 1.0));
        vertices.add(new Vertex(11, 9, 9, 1.0));
        vertices.add(new Vertex(12, 6, 9, 1.0));
        
        Point center = new Point(7.5, 7.5);
        double weight = 30.0;
        
        return new VertexOfDualGraph(102, center, weight, vertices);
    }
    
    private VertexOfDualGraph createSeparateFace1() {
        ArrayList<Vertex> vertices = new ArrayList<>();
        vertices.add(new Vertex(20, 0, 0, 1.0));
        vertices.add(new Vertex(21, 5, 0, 1.0));
        vertices.add(new Vertex(22, 5, 5, 1.0));
        vertices.add(new Vertex(23, 0, 5, 1.0));
        
        Point center = new Point(2.5, 2.5);
        double weight = 50.0;
        
        return new VertexOfDualGraph(200, center, weight, vertices);
    }
    
    private VertexOfDualGraph createSeparateFace2() {
        ArrayList<Vertex> vertices = new ArrayList<>();
        vertices.add(new Vertex(24, 10, 10, 1.0));
        vertices.add(new Vertex(25, 15, 10, 1.0));
        vertices.add(new Vertex(26, 15, 15, 1.0));
        vertices.add(new Vertex(27, 10, 15, 1.0));
        
        Point center = new Point(12.5, 12.5);
        double weight = 60.0;
        
        return new VertexOfDualGraph(201, center, weight, vertices);
    }

    private VertexOfDualGraph createGraphOuterFace() {
        ArrayList<Vertex> vertices = new ArrayList<>();
        vertices.add(new Vertex(30, 0, 0, 1.0));
        vertices.add(new Vertex(31, 20, 0, 1.0));
        vertices.add(new Vertex(32, 20, 20, 1.0));
        vertices.add(new Vertex(33, 0, 20, 1.0));

        Point center = new Point(10, 10);
        double weight = 10.0;

        return new VertexOfDualGraph(300, center, weight, vertices);
    }
}
