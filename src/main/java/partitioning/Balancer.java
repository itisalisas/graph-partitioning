package partitioning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

import org.junit.jupiter.api.Assertions;

import graph.Graph;
import graph.PartitionGraphVertex;
import graph.VertexOfDualGraph;

public class Balancer {

    Graph<PartitionGraphVertex> partitionGraph;
    Graph<VertexOfDualGraph> dualGraph;
    HashSet<HashSet<VertexOfDualGraph>> wasMerged = new HashSet<>();
    int maxWeight;

    public Balancer() {}

    public Balancer(Graph<PartitionGraphVertex> partitionGraph, Graph<VertexOfDualGraph> dualGraph, int maxWeight) {
        this.partitionGraph = partitionGraph;
        this.dualGraph = dualGraph;
        this.maxWeight = maxWeight;
    }

    public boolean rebalanceSmallestRegion() {
        PartitionGraphVertex smallestVertex = partitionGraph.smallestVertex().copy();
        PartitionGraphVertex biggestNeighbor = findBiggestUnmergedNeighbor(partitionGraph.smallestVertex()).copy();
        if (biggestNeighbor == null) {
            return false;
        }
        
        HashSet<VertexOfDualGraph> balancingVerticesSet = new HashSet<>();
        balancingVerticesSet.addAll(smallestVertex.vertices);
        balancingVerticesSet.addAll(biggestNeighbor.vertices);

        Graph<VertexOfDualGraph> regionsSubgraph = dualGraph.createSubgraph(balancingVerticesSet);
        Assertions.assertEquals(balancingVerticesSet.size(), regionsSubgraph.verticesNumber());
        Assertions.assertTrue(regionsSubgraph.isConnected());
        BalancedPartitioning bp = new BalancedPartitioning(new InertialFlowPartitioning(0.4));
        ArrayList<HashSet<VertexOfDualGraph>> newPartition = bp.partition(regionsSubgraph, maxWeight);
        Assertions.assertEquals(2, newPartition.size());

        smallestVertex.changeVertices(new ArrayList<>(newPartition.get(0)));

        biggestNeighbor.changeVertices(new ArrayList<>(newPartition.get(1)));

        ArrayList<HashSet<VertexOfDualGraph>> newParts = new ArrayList<>();
        for (PartitionGraphVertex v : partitionGraph.verticesArray()) {
            if (v == partitionGraph.smallestVertex() || v == findBiggestUnmergedNeighbor(partitionGraph.smallestVertex())) continue;
            newParts.add(new HashSet<>(v.vertices));
        }

        newParts.add(new HashSet<>(smallestVertex.vertices));
        newParts.add(new HashSet<>(biggestNeighbor.vertices));
        Assertions.assertEquals(partitionGraph.verticesNumber(), newParts.size());


        HashMap<VertexOfDualGraph, Integer> dualVertexToPartNumber = new HashMap<>();
		for (int i = 0; i < newParts.size(); i++) {
			for (VertexOfDualGraph vertex : newParts.get(i)) {
				dualVertexToPartNumber.put(vertex, i);
			}
		}

        Assertions.assertEquals(dualGraph.verticesNumber(), dualVertexToPartNumber.size());
        Graph<PartitionGraphVertex> newPartitionGraph = PartitionGraphVertex.buildPartitionGraph(dualGraph, newParts, dualVertexToPartNumber);
        if (newPartitionGraph.smallestVertex().getWeight() < this.partitionGraph.smallestVertex().getWeight()) {
            return false;
        }
        wasMerged.add(balancingVerticesSet);
        this.partitionGraph = newPartitionGraph;
        return true;
    }

    public ArrayList<HashSet<VertexOfDualGraph>> rebalancing() {
        while (removeSmallestRegion()) {}
        double threshold = partitionGraph.verticesWeight() * 0.1;
        double previousSmallestWeight = partitionGraph.smallestVertex().getWeight();
        double variance = calculateVariance();

        while (variance > threshold) {
            if (!rebalanceSmallestRegion()) {
                System.out.println("cant rebalance");
                break;
            }
            double currentSmallestWeight = partitionGraph.smallestVertex().getWeight();
            if (Math.abs(currentSmallestWeight - previousSmallestWeight) < 1e-8) {
                break;
            }
            previousSmallestWeight = currentSmallestWeight;
            variance = calculateVariance();
        }
        for (PartitionGraphVertex vertex : partitionGraph.verticesArray()) {
            Assertions.assertTrue(vertex.getWeight() <= maxWeight);
        }
        return new ArrayList<>(partitionGraph.verticesArray().stream().map(v -> new HashSet<VertexOfDualGraph>(v.vertices)).toList());
    }

    private double calculateVariance() {
        double sum = 0;
        double sumSquares = 0;
        int count = 0;

        for (PartitionGraphVertex vertex : partitionGraph.verticesArray()) {
            double weight = vertex.getWeight();
            sum += weight;
            sumSquares += weight * weight;
            count++;
        }

        double mean = sum / count;
        double meanSquares = sumSquares / count;
        return meanSquares - mean * mean;
    }

    private PartitionGraphVertex findBiggestUnmergedNeighbor(PartitionGraphVertex vertex) {
        PartitionGraphVertex neighbor = null;
        List<PartitionGraphVertex> neighbors = partitionGraph.sortNeighbors(vertex);
        for (PartitionGraphVertex currentNeighbor : neighbors) {
            HashSet<VertexOfDualGraph> mergeSet = new HashSet<>();
            mergeSet.addAll(currentNeighbor.vertices);
            mergeSet.addAll(vertex.vertices);
            if (!wasMerged.contains(mergeSet)) {
                neighbor = currentNeighbor;
                break;
            }
        }
        return neighbor;
    } 
    
    private boolean removeSmallestRegion() {
        PartitionGraphVertex smallestVertex = partitionGraph.smallestVertex().copy();
        List<PartitionGraphVertex> neighbors = new ArrayList<>();
        for (PartitionGraphVertex neighbor : partitionGraph.sortNeighbors(smallestVertex)) {
            neighbors.add(neighbor.copy());
        }
        double availableWeight = 0;
        for (PartitionGraphVertex currentNeighbor : neighbors) {
            availableWeight += currentNeighbor.getWeight();
        }
        if (availableWeight < smallestVertex.getWeight()) {
            return false;
        }

        List<VertexOfDualGraph> verticesToRedistribute = new ArrayList<>(smallestVertex.vertices);

        boolean progressMade;
        do {
            progressMade = false;
            for (VertexOfDualGraph vertex : new ArrayList<>(verticesToRedistribute)) {
                // Priority queue to choose the best neighbor
                PriorityQueue<PartitionGraphVertex> priorityQueue = new PriorityQueue<>(
                    Comparator.comparingDouble((PartitionGraphVertex neighbor) -> {
                        // Calculate priority based on:
                        // 1. Number of neighbors in the partition
                        // 2. Free space in the partition
                        double freeSpace = maxWeight - neighbor.getWeight();
                        // Combine factors (you can adjust weights as needed)
                        return - (freeSpace / maxWeight);
                    })
                );
    
                for (PartitionGraphVertex neighbor : neighbors) {
                    priorityQueue.add(neighbor);
                }
    
                while (!priorityQueue.isEmpty()) {
                    PartitionGraphVertex neighbor = priorityQueue.poll();
                    // Check if vertex has neighbors in the neighbor partition
                    boolean hasNeighborInPartition = false;
                    for (VertexOfDualGraph neighborVertex : neighbor.vertices) {
                        if (dualGraph.getEdges().get(vertex).containsKey(neighborVertex)) {
                            hasNeighborInPartition = true;
                            break;
                        }
                    }
                    if (!hasNeighborInPartition) {
                        continue;
                    }
                    // Check if moving this vertex to the neighbor partition won't exceed maxWeight
                    double newWeight = neighbor.getWeight() + vertex.getWeight();
                    if (newWeight <= maxWeight) {
                        neighbor.addVertex(vertex);
                        smallestVertex.removeVertex(vertex);
                        verticesToRedistribute.remove(vertex);
                        progressMade = true;
                        break;
                    }
                }
            }
        } while (progressMade && !verticesToRedistribute.isEmpty());
        
        // If there are still vertices left that couldn't be moved, return false
        if (!verticesToRedistribute.isEmpty()) {
            return false;
        }
        
        ArrayList<HashSet<VertexOfDualGraph>> newParts = new ArrayList<>();
        for (PartitionGraphVertex v : partitionGraph.verticesArray()) {
            if (v == partitionGraph.smallestVertex()) continue;
            if (!partitionGraph.sortNeighbors(partitionGraph.smallestVertex()).contains(v)) {
                newParts.add(new HashSet<>(v.vertices));
            }
        }

        for (PartitionGraphVertex v : neighbors) {
            newParts.add(new HashSet<>(v.vertices));
        }

        HashMap<VertexOfDualGraph, Integer> dualVertexToPartNumber = new HashMap<>();
        for (int i = 0; i < newParts.size(); i++) {
            for (VertexOfDualGraph vertex : newParts.get(i)) {
                dualVertexToPartNumber.put(vertex, i);
            }
        }

        Graph<PartitionGraphVertex> newPartitionGraph = PartitionGraphVertex.buildPartitionGraph(dualGraph, newParts, dualVertexToPartNumber);

        this.partitionGraph = newPartitionGraph;
        return true;
    }
}