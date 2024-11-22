package graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class PartitionGraphVertex extends Vertex {
    ArrayList<VertexOfDualGraph> vertices;

    public PartitionGraphVertex(long partitionId) {
        super(partitionId, 0, 0, 0);
        this.vertices = new ArrayList<>();
    }

    public PartitionGraphVertex(long partitionId, ArrayList<VertexOfDualGraph> vertices) {
        super(partitionId, 0, 0);
        this.vertices = new ArrayList<>(vertices);
        this.weight = calculateTotalWeight(vertices);
    }

    public void addVertex(VertexOfDualGraph vertex) {
        this.vertices.add(vertex);
        this.weight += vertex.getWeight();
    }

    public void removeVertex(VertexOfDualGraph vertex) {
        if (this.vertices.remove(vertex)) {
            this.weight -= vertex.getWeight();
        }
    }

    private double calculateTotalWeight(ArrayList<VertexOfDualGraph> vertices) {
        return vertices.stream().mapToDouble(VertexOfDualGraph::getWeight).sum();
    }

    static public Graph<PartitionGraphVertex> buildPartitionGraph(Graph<VertexOfDualGraph> dualGraph,
                                                                  ArrayList<HashSet<VertexOfDualGraph>> partition) {
        Graph<PartitionGraphVertex> partitionGraph = new Graph<>();

        for (int i = 0; i < partition.size(); i++) {
            partitionGraph.addVertex(new PartitionGraphVertex(i, new ArrayList<>(partition.get(i))));
        }

        HashMap<VertexOfDualGraph, Integer> vertexToPartition = new HashMap<>();
        for (int i = 0; i < partition.size(); i++) {
            for (VertexOfDualGraph vertex : partition.get(i)) {
                vertexToPartition.put(vertex, i);
            }
        }

        for (VertexOfDualGraph v1 : dualGraph.getEdges().keySet()) {
            int partition1 = vertexToPartition.get(v1);
            for (VertexOfDualGraph v2 : dualGraph.getEdges().get(v1).keySet()) {
                int partition2 = vertexToPartition.get(v2);
                if (partition1 != partition2) {
                    double edgeLength = dualGraph.getEdges().get(v1).get(v2).getLength();
                    PartitionGraphVertex pv1 = partitionGraph.verticesArray().get(partition1);
                    PartitionGraphVertex pv2 = partitionGraph.verticesArray().get(partition2);

                    if (!partitionGraph.getEdges().containsKey(pv1) ||
                            !partitionGraph.getEdges().get(pv1).containsKey(pv2)) {
                        partitionGraph.addEdge(pv1, pv2, edgeLength);
                        partitionGraph.addEdge(pv2, pv1, edgeLength);
                    }
                }
            }
        }

        return partitionGraph;
    }

}
