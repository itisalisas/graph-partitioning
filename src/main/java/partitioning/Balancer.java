package partitioning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import org.junit.jupiter.api.Assertions;

import graph.Graph;
import graph.PartitionGraphVertex;
import graph.Vertex;
import graph.VertexOfDualGraph;

public class Balancer {

    Graph<PartitionGraphVertex> partitionGraph;
    Graph<VertexOfDualGraph> dualGraph;
    Graph<Vertex> startGraph;
    HashSet<HashSet<VertexOfDualGraph>> wasMerged = new HashSet<>();
    int maxWeight;

    public Balancer() {}

    public Balancer(Graph<PartitionGraphVertex> partitionGraph, Graph<VertexOfDualGraph> dualGraph, Graph<Vertex> startGraph, int maxWeight) {
        this.partitionGraph = partitionGraph;
        this.dualGraph = dualGraph;
        this.startGraph = startGraph.makeUndirectedGraph();
        this.maxWeight = maxWeight;
    }

    private boolean rebalanceSmallestRegion() {
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
        long bal = System.currentTimeMillis();
        while (removeSmallestRegion()) { System.out.println("New iteration of removing, size = " + partitionGraph.verticesNumber());}
        System.out.println("removing time = " + (System.currentTimeMillis() - bal) / 1000 + " sec");
        System.out.println("End of removing");
        double threshold = partitionGraph.verticesWeight() * 0.1;
        double previousSmallestWeight = partitionGraph.smallestVertex().getWeight();
        double variance = calculateVariance();

        while (variance > threshold) {
            if (!rebalanceSmallestRegion()) {
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
        long time1 = System.currentTimeMillis();
        PartitionGraphVertex smallestVertex = partitionGraph.smallestVertex().copy();
        List<PartitionGraphVertex> neighbors = new ArrayList<>();
        for (PartitionGraphVertex neighbor : partitionGraph.sortNeighbors(smallestVertex)) {
            neighbors.add(neighbor.copy());
        }
        long time2 = System.currentTimeMillis();
        System.out.println("time2 - time1 = " + (double) (time2 - time1) / 1000);
        double availableWeight = neighbors.stream().mapToDouble(PartitionGraphVertex::getWeight).sum();
        if (availableWeight < smallestVertex.getWeight()) {
            return false;
        }
        long time3 = System.currentTimeMillis();
        System.out.println("time3 - time2 = " + (double) (time3 - time2) / 1000);

        List<VertexOfDualGraph> verticesToRedistribute = new ArrayList<>(smallestVertex.vertices);
        HashSet<VertexOfDualGraph> wasRedistributed = new HashSet<>();

        PriorityQueue<Comp> priorityQueue = new PriorityQueue<>(
            Comparator.comparingDouble((Comp comp) -> {
                return - comp.ratio;
            })
        );

        long time4 = System.currentTimeMillis();
        System.out.println("time4 - time3 = " + (double) (time4 - time3) / 1000);

        for (VertexOfDualGraph vertex : verticesToRedistribute) {
            for (PartitionGraphVertex neighbor : neighbors) {
                priorityQueue.add(new Comp(vertex, neighbor));
            }
        }

        long time5 = System.currentTimeMillis();
        System.out.println("time5 - time4 = " + (double) (time5 - time4) / 1000);

        while (!priorityQueue.isEmpty()) {
            Comp comp = priorityQueue.poll();
            VertexOfDualGraph vertex = comp.vertex;
            PartitionGraphVertex neighbor = comp.part;
            
            if (wasRedistributed.contains(vertex) || comp.ratio == 0.0) {
                continue;
            }

            double newWeight = neighbor.getWeight() + vertex.getWeight();
            
            if (newWeight <= maxWeight) {
                neighbor.addVertex(vertex);
                smallestVertex.removeVertex(vertex);
                wasRedistributed.add(vertex);
                for (VertexOfDualGraph dualVertex : verticesToRedistribute) {
                    if (verticesToRedistribute.contains(dualVertex) && !wasRedistributed.contains(dualVertex) && dualGraph.getEdges().get(vertex).containsKey(dualVertex)) {
                        for (PartitionGraphVertex part : neighbors) {
                            priorityQueue.add(new Comp(dualVertex, part));
                        }
                    }
                }
            }
            System.out.println("queue size = " + priorityQueue.size() + ", need to move = " + (verticesToRedistribute.size() - wasRedistributed.size()));
            
        }

        long time6 = System.currentTimeMillis();
        System.out.println("time6 - time5 = " + (double) (time6 - time5) / 1000);
        
        // If there are still vertices left that couldn't be moved, return false
        if (wasRedistributed.size() != verticesToRedistribute.size()) {
            return false;
        }
        
        ArrayList<HashSet<VertexOfDualGraph>> newParts = new ArrayList<>();
        for (PartitionGraphVertex v : partitionGraph.verticesArray()) {
            if (v == partitionGraph.smallestVertex()) continue;
            if (!partitionGraph.sortNeighbors(partitionGraph.smallestVertex()).contains(v)) {
                newParts.add(new HashSet<>(v.vertices));
            }
        }

        long time7 = System.currentTimeMillis();
        System.out.println("time7 - time6 = " + (double) (time7 - time6) / 1000);

        for (PartitionGraphVertex v : neighbors) {
            newParts.add(new HashSet<>(v.vertices));
        }

        long time8 = System.currentTimeMillis();
        System.out.println("time8 - time7 = " + (double) (time8 - time7) / 1000);

        HashMap<VertexOfDualGraph, Integer> dualVertexToPartNumber = new HashMap<>();
        for (int i = 0; i < newParts.size(); i++) {
            for (VertexOfDualGraph vertex : newParts.get(i)) {
                dualVertexToPartNumber.put(vertex, i);
            }
        }

        long time9 = System.currentTimeMillis();
        System.out.println("time9 - time8 = " + (double) (time9 - time8) / 1000);

        Graph<PartitionGraphVertex> newPartitionGraph = PartitionGraphVertex.buildPartitionGraph(dualGraph, newParts, dualVertexToPartNumber);

        this.partitionGraph = newPartitionGraph;

        long time10 = System.currentTimeMillis();
        System.out.println("time10 - time9 = " + (double) (time10 - time9) / 1000);
        return true;
    }

    class Comp {
        VertexOfDualGraph vertex;
        PartitionGraphVertex part;
        double ratio;

        public Comp() {
        }

        public Comp(VertexOfDualGraph vertex, PartitionGraphVertex part) { 
            this.vertex = vertex;
            this.part = part;
            List<Vertex> face = vertex.getVerticesOfFace();
            PartitionGraphVertex neighbor = part;
            Set<Vertex> neighborVertices = Set.copyOf(neighbor.vertices.stream().flatMap(v -> v.getVerticesOfFace().stream()).toList());
            double countInnerEdges = 0;
            double countOuterEdges = 0;
            for (int i = 0; i < face.size(); i++) {
                if (neighborVertices.contains(face.get(i)) &&
                    neighborVertices.contains(face.get((i + 1) % face.size())) &&
                    startGraph.getEdges().get(face.get(i)).containsKey(face.get((i + 1) % face.size()))) {
                    countInnerEdges += 1;
                } else {
                    countOuterEdges += 1;
                }
            }
            this.ratio = countInnerEdges / countOuterEdges;
            System.out.println("ratio = " + ratio);
        }
    }
}