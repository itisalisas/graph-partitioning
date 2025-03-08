package partitioning;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;

import graph.BoundSearcher;
import graph.Edge;
import graph.Graph;
import graph.PartitionGraphVertex;
import graph.Vertex;
import graph.VertexOfDualGraph;
import partitioning.Balancer.Comp;
import readWrite.PartitionWriter;

public class Balancer {

    Graph<PartitionGraphVertex> partitionGraph;
    Graph<VertexOfDualGraph> dualGraph;
    Graph<Vertex> startGraph;
    HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph;
    HashSet<HashSet<VertexOfDualGraph>> wasMerged = new HashSet<>();
    int maxWeight;
    String pathToResultDirectory;

    public Balancer() {}

    public Balancer(Graph<PartitionGraphVertex> partitionGraph, Graph<VertexOfDualGraph> dualGraph, Graph<Vertex> startGraph, int maxWeight, HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph, String pathToResultDirectory) {
        this.partitionGraph = partitionGraph;
        this.dualGraph = dualGraph;
        this.startGraph = startGraph.makeUndirectedGraph();
        this.maxWeight = maxWeight;
        this.comparisonForDualGraph = comparisonForDualGraph;
        this.pathToResultDirectory = pathToResultDirectory;
    }

    private boolean rebalanceSmallestRegion() {
        PartitionGraphVertex smallestVertex = partitionGraph.smallestVertex().copy();

        double smallestVertexWeight = smallestVertex.getWeight();

        PartitionGraphVertex biggestNeighbor = findBiggestUnmergedNeighbor(partitionGraph.smallestVertex());
        if (biggestNeighbor == null) {
            return false;
        }
        biggestNeighbor = biggestNeighbor.copy();

        HashSet<VertexOfDualGraph> balancingVerticesSet = new HashSet<>();
        balancingVerticesSet.addAll(smallestVertex.vertices);
        balancingVerticesSet.addAll(biggestNeighbor.vertices);

        Graph<VertexOfDualGraph> regionsSubgraph = dualGraph.createSubgraph(balancingVerticesSet);
        Assertions.assertEquals(balancingVerticesSet.size(), regionsSubgraph.verticesNumber());
        Assertions.assertTrue(regionsSubgraph.isConnected());
        System.out.println("rebalance: " + smallestVertex.getName() + " and " + biggestNeighbor.getName());
        System.out.println("smallset w: " + smallestVertex.getWeight() + ", biggest w: " + biggestNeighbor.getWeight() + ", sum: " + (biggestNeighbor.getWeight() + smallestVertex.getWeight()));
        double coefficient = 1 - (double) maxWeight / (balancingVerticesSet.stream().mapToDouble(v -> v.getWeight()).sum());
        System.out.println("coefficient = " + coefficient);
        BalancedPartitioning bp = new BalancedPartitioning(new InertialFlowPartitioning(coefficient));
        ArrayList<HashSet<VertexOfDualGraph>> newPartition = bp.partition(regionsSubgraph, maxWeight);
        Assertions.assertEquals(2, newPartition.size());

        smallestVertex.changeVertices(new ArrayList<>(newPartition.get(0)));

        biggestNeighbor.changeVertices(new ArrayList<>(newPartition.get(1)));

        if (smallestVertex.getWeight() < smallestVertexWeight || biggestNeighbor.getWeight() < smallestVertexWeight) {
            return false;
        }

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
        wasMerged.add(balancingVerticesSet);
        this.partitionGraph = newPartitionGraph;
        return true;
    }

    public ArrayList<HashSet<VertexOfDualGraph>> rebalancing() {
        long bal = System.currentTimeMillis();
        while (removeSmallestRegion()) { 
            System.out.println("New iteration of removing, size = " + partitionGraph.verticesNumber());
        }
        System.out.println("removing time = " + (System.currentTimeMillis() - bal) / 1000 + " sec");
        System.out.println("End of removing");
        double threshold = partitionGraph.verticesWeight() * 0.1;
        double variance = calculateVariance();

        while (variance > threshold) {
            if (!rebalanceSmallestRegion()) {
                break;
            }
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
        List<PartitionGraphVertex> ver = PartitionGraphVertex.bestVertex(partitionGraph, maxWeight);
        for (PartitionGraphVertex sm : ver) {
            PartitionGraphVertex smallestVertex = sm.copy();
            List<PartitionGraphVertex> neighbors = new ArrayList<>();
            for (PartitionGraphVertex neighbor : partitionGraph.sortNeighbors(smallestVertex)) {
                neighbors.add(neighbor.copy());
            }
            
            // TODO - копировать после проверки
            double availableWeight = neighbors.stream().mapToDouble(vertex -> maxWeight - vertex.getWeight()).sum();
            if (availableWeight < smallestVertex.getWeight()) {
                return false;
            }

            List<VertexOfDualGraph> verticesToRedistribute = new ArrayList<>(smallestVertex.vertices);
            HashSet<VertexOfDualGraph> wasRedistributed = new HashSet<>();
            Map<VertexOfDualGraph, VertexOfDualGraph> vertexToBestNeighbor = new HashMap<>();

            PriorityQueue<Comp> priorityQueue = new PriorityQueue<>(
                Comparator.comparingDouble((Comp comp) -> {
                    return - comp.ratio;
                })
            );

            // TODO - не добавлять пары, которые не смежны
            for (VertexOfDualGraph vertex : verticesToRedistribute) {
                for (PartitionGraphVertex neighbor : neighbors) {
                    priorityQueue.add(new Comp(vertex, smallestVertex, neighbor));
                }
            }


            while (!priorityQueue.isEmpty()) {
                Comp comp = priorityQueue.poll();
                // System.out.println("ratio = " + comp.ratio);
                VertexOfDualGraph vertex = comp.vertex;
                PartitionGraphVertex neighbor = comp.neighbor;
                VertexOfDualGraph bestNeighborVertex = comp.bestNeighborVertex;
                
                if (wasRedistributed.contains(vertex) || comp.ratio == 0.0) {
                    continue;
                }

                double newWeight = neighbor.getWeight() + vertex.getWeight();
                
                if (newWeight <= maxWeight) {
                    System.out.println(neighbor.getName() + ": " + neighbor.getWeight() + " -> " + newWeight + " ratio = " + comp.ratio);
                    neighbor.addVertex(vertex);
                    smallestVertex.removeVertex(vertex);
                    wasRedistributed.add(vertex);
                    vertexToBestNeighbor.put(vertex, bestNeighborVertex);
                    for (VertexOfDualGraph dualVertex : verticesToRedistribute) {
                        if (!wasRedistributed.contains(dualVertex) && dualGraph.getEdges().get(vertex).containsKey(dualVertex)) {
                            priorityQueue.add(new Comp(dualVertex, smallestVertex, neighbor));
                        }
                    }
                }
            }


            List<List<Vertex>> bounds = new ArrayList<>();
            List<PartitionGraphVertex> parts = partitionGraph.verticesArray().stream().sorted(Comparator.comparing(c -> c.getName())).toList();
            for (int i = 0; i < parts.size(); i++) {
                bounds.add(BoundSearcher.findBound(startGraph, new HashSet<>(parts.get(i).vertices), comparisonForDualGraph));
            }

            PartitionWriter pw = new PartitionWriter();
            String currentRemovingPath = pathToResultDirectory + File.separatorChar + "removing_" +  parts.size();
            pw.printBound(bounds, currentRemovingPath, true);
            pw.printRedistributedVerticesDirections(vertexToBestNeighbor, currentRemovingPath, true);
            
            // If there are still vertices left that couldn't be moved, return false
            if (wasRedistributed.size() != verticesToRedistribute.size()) {
                continue;
            }
            
            ArrayList<HashSet<VertexOfDualGraph>> newParts = new ArrayList<>();
            for (PartitionGraphVertex v : partitionGraph.verticesArray()) {
                if (v == sm) continue;
                if (!partitionGraph.sortNeighbors(sm).contains(v)) {
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
        return false;
    }

    class Comp {
        VertexOfDualGraph vertex;
        PartitionGraphVertex neighbor;
        VertexOfDualGraph bestNeighborVertex;
        double ratio;

        public Comp() {}

        public Comp(VertexOfDualGraph vertex, PartitionGraphVertex part, PartitionGraphVertex neighbor) { 
            this.vertex = vertex;
            this.neighbor = neighbor;
            double countInnerEdges = 0;
            double countOuterEdges = 0;
            double bestEdgeLength = 0;
            for (VertexOfDualGraph neighborVertex : dualGraph.getEdges().get(vertex).keySet()) {
                Edge edge = dualGraph.getEdges().get(vertex).get(neighborVertex);
                if (neighbor.vertices.stream().collect(Collectors.toSet()).contains(neighborVertex)) {
                    countOuterEdges += edge.getLength();
                    if (edge.getLength() > bestEdgeLength) {
                        bestEdgeLength = edge.getLength();
                        bestNeighborVertex = neighborVertex;
                    }
                } else if (part.vertices.stream().collect(Collectors.toSet()).contains(neighborVertex)) {
                    countInnerEdges += edge.getLength();
                }
            }
            if (countOuterEdges == 0) {
                this.ratio = 0;
            } else if (countInnerEdges == 0) {
                this.ratio = countOuterEdges * maxWeight / neighbor.getWeight();
            } else {
                this.ratio = countOuterEdges / countInnerEdges * maxWeight / neighbor.getWeight();
            }
        }
    }
}