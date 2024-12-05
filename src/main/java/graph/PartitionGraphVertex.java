package graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class PartitionGraphVertex extends Vertex {
    public ArrayList<VertexOfDualGraph> vertices;

    public PartitionGraphVertex(long partitionId) {
        super(partitionId, 0, 0, 0);
        this.vertices = new ArrayList<>();
    }

    public PartitionGraphVertex(long partitionId, ArrayList<VertexOfDualGraph> vertices) {
        super(partitionId);
        this.vertices = new ArrayList<>(vertices);
        this.weight = calculateTotalWeight(vertices);
        calculateCenter();
    }

    public <T extends Vertex> PartitionGraphVertex(T v) {
        super(v.getName(), v.x, v.y, v.getWeight());
        this.vertices = null;
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

    public void changeVertices(ArrayList<VertexOfDualGraph> vertices) {
        this.vertices = vertices;
        this.weight = calculateTotalWeight(this.vertices);
        calculateCenter();
    }

    private void calculateCenter() {
        Point center = Vertex.findCenter(this.vertices);
        this.x = center.x;
        this.y = center.y;
    }

    static public Graph<PartitionGraphVertex> buildPartitionGraph(Graph<VertexOfDualGraph> dualGraph,
                                                                  ArrayList<HashSet<VertexOfDualGraph>> partition,
                                                                  HashMap<VertexOfDualGraph, Integer> dualVertexToPartNumber) {
        Graph<PartitionGraphVertex> partitionGraph = new Graph<>();

        ArrayList<PartitionGraphVertex> vertices = new ArrayList<>();

        for (int i = 0; i < partition.size(); i++) {
            PartitionGraphVertex v = new PartitionGraphVertex(i, new ArrayList<>(partition.get(i)));
            vertices.add(v);
            partitionGraph.addVertex(v);
        }

        for (VertexOfDualGraph v1 : dualGraph.getEdges().keySet()) {
            int partition1 = dualVertexToPartNumber.get(v1);
            for (VertexOfDualGraph v2 : dualGraph.getEdges().get(v1).keySet()) {
                int partition2 = dualVertexToPartNumber.get(v2);
                if (partition1 != partition2) {
                    double edgeLength = dualGraph.getEdges().get(v1).get(v2).getLength();
                    PartitionGraphVertex pv1 = vertices.get(partition1);
                    PartitionGraphVertex pv2 = vertices.get(partition2);

                    if (!partitionGraph.getEdges().get(pv2).containsKey(pv1)) {
                        partitionGraph.addEdge(pv1, pv2, edgeLength);
                        partitionGraph.addEdge(pv2, pv1, edgeLength);
                    }
                }
            }
        }

        return partitionGraph;
    }
    
    @Override
    public PartitionGraphVertex copy() {
    	return new PartitionGraphVertex(this.getName(), this.vertices);
    }

}
