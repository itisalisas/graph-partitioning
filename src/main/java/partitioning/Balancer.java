package partitioning;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import graph.*;
import org.junit.jupiter.api.Assertions;

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
        List<PartitionGraphVertex> ver = PartitionGraphVertex.smallestVertex(partitionGraph, maxWeight);
        for (PartitionGraphVertex sm : ver) {
            PartitionGraphVertex smallestVertex = sm.copy();

            double smallestVertexWeight = smallestVertex.getWeight();

            List<PartitionGraphVertex> neighbors = findBiggestUnmergedNeighbor(sm);
            if (neighbors.isEmpty()) {
                continue;
            }

            PartitionGraphVertex nonModifiedBestNeighbor = null;
            PartitionGraphVertex bestNeighbor = null;
            List<HashSet<VertexOfDualGraph>> bestPartition = null;
            HashSet<VertexOfDualGraph> bestVerticesSet = null;
            double bestDiff = Double.MAX_VALUE;
            double bestMin = smallestVertexWeight;

            for (PartitionGraphVertex neighbor: neighbors) {
                PartitionGraphVertex biggestNeighbor = neighbor.copy();

                HashSet<VertexOfDualGraph> balancingVerticesSet = new HashSet<>();
                balancingVerticesSet.addAll(smallestVertex.vertices);
                balancingVerticesSet.addAll(biggestNeighbor.vertices);

                Graph<VertexOfDualGraph> regionsSubgraph = dualGraph.createSubgraph(balancingVerticesSet);
                HashMap<HashSet<VertexOfDualGraph>, Double> cutBefore = calculateCutWeights(regionsSubgraph, List.of(new HashSet<>(smallestVertex.vertices), new HashSet<>(biggestNeighbor.vertices)));

                Assertions.assertEquals(balancingVerticesSet.size(), regionsSubgraph.verticesNumber());
                Assertions.assertTrue(regionsSubgraph.isConnected());
                double coefficient = 1 - (double) maxWeight / (balancingVerticesSet.stream().mapToDouble(v -> v.getWeight()).sum());
                BalancedPartitioning bp = new BalancedPartitioning(new InertialFlowPartitioning(coefficient));
                ArrayList<HashSet<VertexOfDualGraph>> newPartition = bp.partition(startGraph, comparisonForDualGraph, regionsSubgraph, maxWeight);
                if (newPartition.size() > 2) {
                    continue;
                }

                if (newPartition.size() == 1) {
                    HashSet<VertexOfDualGraph> mergedPart = newPartition.get(0);

                    ArrayList<HashSet<VertexOfDualGraph>> newParts = new ArrayList<>();
                    for (PartitionGraphVertex v : partitionGraph.verticesArray()) {
                        if (v == sm || v == neighbor)
                            continue;
                        newParts.add(new HashSet<>(v.vertices));
                    }
                    newParts.add(mergedPart);

                    Assertions.assertEquals(partitionGraph.verticesNumber() - 1, newParts.size());

                    HashMap<VertexOfDualGraph, Integer> dualVertexToPartNumber = new HashMap<>();
                    for (int i = 0; i < newParts.size(); i++) {
                        for (VertexOfDualGraph vertex : newParts.get(i)) {
                            dualVertexToPartNumber.put(vertex, i);
                        }
                    }

                    Assertions.assertEquals(dualGraph.verticesNumber(), dualVertexToPartNumber.size());

                    Graph<PartitionGraphVertex> newPartitionGraph = PartitionGraphVertex.buildPartitionGraph(
                            dualGraph,
                            newParts,
                            dualVertexToPartNumber
                    );

                    wasMerged.add(balancingVerticesSet);
                    this.partitionGraph = newPartitionGraph;
                    return true;
                }

                double w1 = newPartition.get(0).stream().mapToDouble(Vertex::getWeight).sum();
                double w2 = newPartition.get(1).stream().mapToDouble(Vertex::getWeight).sum();
                double minNew = Math.min(w1, w2);
                double maxNew = Math.max(w1, w2);
                double diff = maxNew - minNew;

                HashMap<HashSet<VertexOfDualGraph>, Double> cutAfter = calculateCutWeights(regionsSubgraph, newPartition);
                if (cutBefore.values().stream().mapToDouble(v -> v).sum() * ((sm.getWeight() < 0.5 * maxWeight && minNew >= 0.5 * maxWeight) ? 1.5 : 1) < cutAfter.values().stream().mapToDouble(v -> v).sum()) {
                    continue;
                }

                if (sm.getWeight() < 0.5 * maxWeight && minNew >= 0.5 * maxWeight && minNew > bestMin) {
                    bestNeighbor = biggestNeighbor;
                    nonModifiedBestNeighbor = neighbor;
                    bestVerticesSet = balancingVerticesSet;
                    bestPartition = newPartition;
                    bestMin = minNew;
                } else if (diff < bestDiff) {
                    bestDiff = diff;
                    bestNeighbor = biggestNeighbor;
                    nonModifiedBestNeighbor = neighbor;
                    bestVerticesSet = balancingVerticesSet;
                    bestPartition = newPartition;
                }
            }

            if (bestNeighbor != null) {

                Assertions.assertEquals(2, bestPartition.size());

                smallestVertex.changeVertices(new ArrayList<>(bestPartition.get(0)));

                bestNeighbor.changeVertices(new ArrayList<>(bestPartition.get(1)));

                ArrayList<HashSet<VertexOfDualGraph>> newParts = new ArrayList<>();
                for (PartitionGraphVertex v : partitionGraph.verticesArray()) {
                    if (v == sm || v == nonModifiedBestNeighbor)
                        continue;
                    newParts.add(new HashSet<>(v.vertices));
                }


                newParts.add(new HashSet<>(smallestVertex.vertices));
                newParts.add(new HashSet<>(bestNeighbor.vertices));
                Assertions.assertEquals(partitionGraph.verticesNumber(), newParts.size());


                HashMap<VertexOfDualGraph, Integer> dualVertexToPartNumber = new HashMap<>();
                for (int i = 0; i < newParts.size(); i++) {
                    for (VertexOfDualGraph vertex : newParts.get(i)) {
                        dualVertexToPartNumber.put(vertex, i);
                    }
                }

                Assertions.assertEquals(dualGraph.verticesNumber(), dualVertexToPartNumber.size());
                Graph<PartitionGraphVertex> newPartitionGraph = PartitionGraphVertex.buildPartitionGraph(dualGraph, newParts, dualVertexToPartNumber);
                wasMerged.add(bestVerticesSet);
                this.partitionGraph = newPartitionGraph;
                return true;
            }
        }
        return false;
    }


    public ArrayList<HashSet<VertexOfDualGraph>> rebalancing() throws IOException {
        while (removeSmallestRegion()) { }
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
        return partitionGraph.verticesArray().stream().map(v -> new HashSet<VertexOfDualGraph>(v.vertices)).collect(Collectors.toCollection(ArrayList::new));
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

    private List<PartitionGraphVertex> findBiggestUnmergedNeighbor(PartitionGraphVertex vertex) {
        List<PartitionGraphVertex> goodNeighbors = new ArrayList<>();
        List<PartitionGraphVertex> neighbors = partitionGraph.sortNeighbors(vertex);
        for (PartitionGraphVertex currentNeighbor : neighbors) {
            HashSet<VertexOfDualGraph> mergeSet = new HashSet<>();
            mergeSet.addAll(currentNeighbor.vertices);
            mergeSet.addAll(vertex.vertices);
            if (!wasMerged.contains(mergeSet)) {
                goodNeighbors.add(currentNeighbor);
            }
        }
        return goodNeighbors;
    }
    
    private boolean removeSmallestRegion() throws IOException {
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
                
                VertexOfDualGraph vertex = comp.vertex;
                PartitionGraphVertex neighbor = comp.neighbor;
                VertexOfDualGraph bestNeighborVertex = comp.bestNeighborVertex;
                
                if (wasRedistributed.contains(vertex) || comp.ratio == 0.0) {
                    continue;
                }

                double newWeight = neighbor.getWeight() + vertex.getWeight();
                
                if (newWeight <= maxWeight) {
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
                    countOuterEdges += edge.length;
                    if (edge.length > bestEdgeLength) {
                        bestEdgeLength = edge.length;
                        bestNeighborVertex = neighborVertex;
                    }
                } else if (part.vertices.stream().collect(Collectors.toSet()).contains(neighborVertex)) {
                    countInnerEdges += edge.length;
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

    private HashMap<HashSet<VertexOfDualGraph>, Double> calculateCutWeights(Graph<VertexOfDualGraph> graph, List<HashSet<VertexOfDualGraph>> partitions) {
        HashMap<HashSet<VertexOfDualGraph>, Double> cutEdgesMap = new HashMap<>();
        if (graph == null) {
            System.out.println("graph - null1");
        }
        for (HashSet<VertexOfDualGraph> partition : partitions) {
            double cutEdgesWeightSum = 0;
            if (graph == null) {
                System.out.println("graph - null2");
            }
            for (EdgeOfGraph<VertexOfDualGraph> edge : graph.edgesArray()) {
                VertexOfDualGraph u = edge.begin;
                VertexOfDualGraph v = edge.end;
                double weight = edge.getBandwidth();

                if (partition.contains(u) && !partition.contains(v)) {
                    cutEdgesWeightSum += weight;
                }
            }

            cutEdgesMap.put(partition, cutEdgesWeightSum);
        }
        return cutEdgesMap;
    }
}