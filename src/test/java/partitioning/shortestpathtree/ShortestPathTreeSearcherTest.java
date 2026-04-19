package partitioning.shortestpathtree;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;
import graphPreparation.GraphPreparation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import partitioning.entities.DijkstraResult;
import partitioning.entities.SPTWithRegionWeights;
import partitioning.maxflow.Dijkstra;
import partitioning.maxflow.CornerConstraints;

public class ShortestPathTreeSearcherTest {

    @Test
    void testSimple() throws IOException {
        List<Vertex> vertices = List.of(
                new Vertex(1, 0, 0),
                new Vertex(2, 1, 0),
                new Vertex(3, -1, 0),
                new Vertex(4, 2, 0),
                new Vertex(5, 1, 1),
                new Vertex(6, 1, 4),
                new Vertex(7, -1, 2),
                new Vertex(8, 0, 2),
                new Vertex(9, 0, 3)
        );
        List<EdgeOfGraph> edges = List.of(
                new EdgeOfGraph(vertices.get(0), vertices.get(1), vertices.get(0).getLength(vertices.get(1))), // 1
                new EdgeOfGraph(vertices.get(1), vertices.get(3), vertices.get(1).getLength(vertices.get(3))), // 2
                new EdgeOfGraph(vertices.get(0), vertices.get(2), vertices.get(0).getLength(vertices.get(2))), // 3
                new EdgeOfGraph(vertices.get(1), vertices.get(4), vertices.get(1).getLength(vertices.get(4))), // 4
                new EdgeOfGraph(vertices.get(3), vertices.get(5), vertices.get(3).getLength(vertices.get(5))), // 5
                new EdgeOfGraph(vertices.get(0), vertices.get(4), vertices.get(0).getLength(vertices.get(4))), // 6
                new EdgeOfGraph(vertices.get(4), vertices.get(5), vertices.get(4).getLength(vertices.get(5))), // 7
                new EdgeOfGraph(vertices.get(2), vertices.get(6), vertices.get(2).getLength(vertices.get(6))), // 8
                new EdgeOfGraph(vertices.get(0), vertices.get(7), vertices.get(0).getLength(vertices.get(7))), // 9
                new EdgeOfGraph(vertices.get(6), vertices.get(7), vertices.get(6).getLength(vertices.get(7))), // 10
                new EdgeOfGraph(vertices.get(5), vertices.get(7), vertices.get(5).getLength(vertices.get(7))), // 11
                new EdgeOfGraph(vertices.get(5), vertices.get(8), vertices.get(5).getLength(vertices.get(8))), // 12
                new EdgeOfGraph(vertices.get(6), vertices.get(8), vertices.get(6).getLength(vertices.get(8)))  // 13
        );
        Graph<Vertex> graph = new Graph<>();
        for (Vertex vertex : vertices) {
            graph.addVertex(vertex);
        }
        for (EdgeOfGraph edge : edges) {
            graph.addEdge(edge.begin, edge.end, edge.length);
            graph.addEdge(edge.end, edge.begin, edge.length);
        }
        GraphPreparation preparation = new GraphPreparation();
        Graph<VertexOfDualGraph> dualGraph = preparation.prepareGraph(graph, 0.0001);
        for (VertexOfDualGraph v: dualGraph.verticesArray()) {
            System.out.println(v.getName() + " : " + v.getVerticesOfFace().stream().map(Vertex::getName).toList() + ", w = " + v.getWeight());
        }

        Optional<DijkstraResult> dijkstraResultOpt = Dijkstra.dijkstraSingleSource(graph, vertices.get(0), List.of(vertices.get(6), vertices.get(8), vertices.get(5)), CornerConstraints.empty());
        Assertions.assertTrue(dijkstraResultOpt.isPresent());
        DijkstraResult dijkstraResult = dijkstraResultOpt.get();
        for (Vertex vertex : dijkstraResult.previous().keySet()) {
            System.out.println(vertex.getName() + ": " + dijkstraResult.previous().get(vertex).getName());
        }

        //Assertions.assertTrue(dijkstraResult.get().path().contains(vertices.get(6)));

        SPTWithRegionWeights spt = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph,
                dijkstraResult.previous(),
                vertices.get(0),
                List.of(vertices.get(6), vertices.get(8), vertices.get(5)),
                dualGraph,
                List.of(vertices.get(6)),
                List.of(vertices.get(5)),
                false,
                CornerConstraints.empty(),
                dijkstraResult.path()
        );

        System.out.println(spt.faces().stream().map(VertexOfDualGraph::getName).toList());
        System.out.println(spt.regionWeights());
        System.out.println(spt.distances());
    }
}
