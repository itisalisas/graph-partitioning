package partitioning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.junit.jupiter.api.Assertions;

import graph.Graph;
import graph.PartitionGraphVertex;
import graph.VertexOfDualGraph;

public class Balancer {

    Graph<PartitionGraphVertex> partitionGraph;
    Graph<VertexOfDualGraph> dualGraph;

    public Balancer() {}

    public Balancer(Graph<PartitionGraphVertex> partitionGraph, Graph<VertexOfDualGraph> dualGraph) {
        this.partitionGraph = partitionGraph;
        this.dualGraph = dualGraph;
    }

    public void rebalanceSmallestRegion() {
        PartitionGraphVertex smallestVertex = partitionGraph.smallestVertex();
        PartitionGraphVertex biggestNeighbor = partitionGraph.findBiggestNeighbor(smallestVertex);
        
        HashSet<VertexOfDualGraph> balancingVerticesSet = new HashSet<>();
        balancingVerticesSet.addAll(smallestVertex.vertices);
        balancingVerticesSet.addAll(biggestNeighbor.vertices);

        Graph<VertexOfDualGraph> regionsSubgraph = dualGraph.createSubgraph(balancingVerticesSet);
        Assertions.assertEquals(balancingVerticesSet.size(), regionsSubgraph.verticesNumber());
        Assertions.assertTrue(regionsSubgraph.isConnected());
        BalancedPartitioning bp = new BalancedPartitioning(new InertialFlowPartitioning(0.25));
        ArrayList<HashSet<VertexOfDualGraph>> newPartition = bp.partition(regionsSubgraph, (int) regionsSubgraph.verticesSumWeight() - 1);
        Assertions.assertEquals(2, newPartition.size());

        smallestVertex.changeVertices(new ArrayList<>(newPartition.get(0)));

        biggestNeighbor.changeVertices(new ArrayList<>(newPartition.get(1)));

        ArrayList<HashSet<VertexOfDualGraph>> newParts = new ArrayList<>(partitionGraph.verticesArray().stream().map(v -> new HashSet<VertexOfDualGraph>(v.vertices)).toList());

        HashMap<VertexOfDualGraph, Integer> dualVertexToPartNumber = new HashMap<>();
		for (int i = 0; i < newParts.size(); i++) {
			for (VertexOfDualGraph vertex : newParts.get(i)) {
				dualVertexToPartNumber.put(vertex, i);
			}
		}

        Assertions.assertEquals(dualGraph.verticesNumber(), dualVertexToPartNumber.size());

        this.partitionGraph = PartitionGraphVertex.buildPartitionGraph(dualGraph, newParts, dualVertexToPartNumber);

        /* балансировка - берем как истоки все вершины меньшей части, как стоки - w_1 вершин второй части
        VertexOfDualGraph source = new VertexOfDualGraph(-1);
        VertexOfDualGraph sink = new VertexOfDualGraph(-2);

        Set<VertexOfDualGraph> sourceSet =  new HashSet<>(smallestVertex.vertices);
        double targetWeightSink = smallestVertex.getWeight();
        Set<VertexOfDualGraph> sinkSet = new HashSet<>();
        Set<VertexOfDualGraph> maxSinkSet = new HashSet<>();
        int index = 1;
        // TODO - select vertices for sink
        while (index <= vertices.size() && sinkSet.stream().mapToDouble(Vertex::getWeight).sum() < targetWeightSink) {
            sinkSet = selectVerticesForSet(vertices, vertices.size() - index, targetWeightSink, sourceSet, currentGraph);
            if (sinkSet.stream().mapToDouble(Vertex::getWeight).sum() >
                    maxSinkSet.stream().mapToDouble(Vertex::getWeight).sum()) {
                maxSinkSet = sinkSet;
            }
            index++;
        }

        Graph<VertexOfDualGraph> regionsSubgraphWithSourceSink = InertialFlowPartitioning.createGraphWithSourceSink(regionsSubgraph, sourceSet, source, sinkSet, sink);
        */
    }

    public ArrayList<HashSet<VertexOfDualGraph>> rebalancing() {
        rebalanceSmallestRegion();
        return new ArrayList<>(partitionGraph.verticesArray().stream().map(v -> new HashSet<VertexOfDualGraph>(v.vertices)).toList());
    }
    
}
