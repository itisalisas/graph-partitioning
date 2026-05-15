package partitioning.balancing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graph.Edge;
import graph.EdgeOfGraph;
import graph.Graph;
import graph.PartitionGraphVertex;
import graph.Vertex;
import graph.VertexOfDualGraph;
import jakarta.validation.constraints.NotNull;
import partitioning.BalancedPartitioning;
import partitioning.algorithms.InertialFlowPartitioning;
import readWrite.CoordinateConversion;

public class Balancer {
    private static final Logger logger = LoggerFactory.getLogger(Balancer.class);

    Graph<PartitionGraphVertex> partitionGraph;
    Graph<VertexOfDualGraph> dualGraph;
    Graph<Vertex> startGraph;
    Set<Set<VertexOfDualGraph>> wasMerged = new HashSet<>();
    int maxWeight;
    String pathToResultDirectory;
    CoordinateConversion cc;
    boolean useReif;
    double lengthPriority;

    public Balancer(
            Graph<PartitionGraphVertex> partitionGraph,
            Graph<VertexOfDualGraph> dualGraph,
            Graph<Vertex> startGraph,
            int maxWeight,
            String pathToResultDirectory,
            CoordinateConversion cc,
            boolean useReif,
            double lengthPriority
    ) {
        this.partitionGraph = partitionGraph;
        this.dualGraph = dualGraph;
        this.startGraph = startGraph.makeUndirectedGraph();
        this.maxWeight = maxWeight;
        this.pathToResultDirectory = pathToResultDirectory;
        this.cc = cc;
        this.useReif = useReif;
        this.lengthPriority = lengthPriority;
    }

    private boolean rebalanceSmallestRegion() {
        List<PartitionGraphVertex> ver = PartitionGraphVertex.smallestVertex(partitionGraph);
        for (PartitionGraphVertex sm : ver) {
            PartitionGraphVertex smallestVertex = sm.copy();

            double smallestVertexWeight = smallestVertex.getWeight();

            List<PartitionGraphVertex> neighbors = findBiggestUnmergedNeighbor(sm);
            if (neighbors.isEmpty()) {
                continue;
            }

            PartitionGraphVertex nonModifiedBestNeighbor = null;
            PartitionGraphVertex bestNeighbor = null;
            List<Set<VertexOfDualGraph>> bestPartition = null;
            Set<VertexOfDualGraph> bestVerticesSet = null;
            double bestDiff = Double.MAX_VALUE;
            double bestMin = smallestVertexWeight;

            for (PartitionGraphVertex neighbor: neighbors) {
                PartitionGraphVertex biggestNeighbor = neighbor.copy();

                Set<VertexOfDualGraph> balancingVerticesSet = new HashSet<>();
                balancingVerticesSet.addAll(smallestVertex.vertices);
                balancingVerticesSet.addAll(biggestNeighbor.vertices);

                Graph<VertexOfDualGraph> regionsSubgraph = dualGraph.createSubgraph(balancingVerticesSet);
                Map<Set<VertexOfDualGraph>, Double> cutBefore = calculateCutWeights(regionsSubgraph, List.of(new HashSet<>(smallestVertex.vertices), new HashSet<>(biggestNeighbor.vertices)));

                Assertions.assertEquals(balancingVerticesSet.size(), regionsSubgraph.verticesNumber());
                Assertions.assertTrue(regionsSubgraph.isConnected());
                double coefficient = 1 - (double) maxWeight / (balancingVerticesSet.stream().mapToDouble(Vertex::getWeight).sum());
                BalancedPartitioning bp = new BalancedPartitioning(new InertialFlowPartitioning(coefficient, useReif, lengthPriority));
                List<Set<VertexOfDualGraph>> newPartition = bp.partition(startGraph, regionsSubgraph, maxWeight, cc);
                if (newPartition.size() > 2) {
                    logger.error("partition size in balancer > 2, skip");
                    continue;
                }

                if (newPartition.size() == 1) {
                    Set<VertexOfDualGraph> mergedPart = newPartition.get(0);

                    List<Set<VertexOfDualGraph>> newParts = new ArrayList<>();
                    for (PartitionGraphVertex v : partitionGraph.verticesArray()) {
                        if (v == sm || v == neighbor)
                            continue;
                        newParts.add(new HashSet<>(v.vertices));
                    }
                    newParts.add(mergedPart);

                    Assertions.assertEquals(partitionGraph.verticesNumber() - 1, newParts.size());

                    Map<VertexOfDualGraph, Integer> dualVertexToPartNumber = new HashMap<>();
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

                Map<Set<VertexOfDualGraph>, Double> cutAfter = calculateCutWeights(regionsSubgraph, newPartition);
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

                List<Set<VertexOfDualGraph>> newParts = new ArrayList<>();
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

    public List<Set<VertexOfDualGraph>> rebalancing() {
        while (removeSmallestRegion()) {
            // сontinue removing until no more regions can be removed
        }
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
        return partitionGraph.verticesArray()
                .stream()
                .map(v -> new HashSet<>(v.vertices))
                .collect(Collectors.toList());
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
            Set<VertexOfDualGraph> mergeSet = new HashSet<>();
            mergeSet.addAll(currentNeighbor.vertices);
            mergeSet.addAll(vertex.vertices);
            if (!wasMerged.contains(mergeSet)) {
                goodNeighbors.add(currentNeighbor);
            }
        }
        return goodNeighbors;
    }
    
    private boolean removeSmallestRegion() {
        List<PartitionGraphVertex> ver = PartitionGraphVertex.bestVertex(partitionGraph, maxWeight);
        for (PartitionGraphVertex sm : ver) {
            PartitionGraphVertex smallestVertex = sm.copy();
            List<PartitionGraphVertex> sortedNeighbors = partitionGraph.sortNeighbors(smallestVertex);
            List<PartitionGraphVertex> neighbors = new ArrayList<>(sortedNeighbors.size());
            for (PartitionGraphVertex neighbor : sortedNeighbors) {
                neighbors.add(neighbor.copy());
            }
            
            // TODO - копировать после проверки
            double availableWeight = neighbors.stream().mapToDouble(vertex -> maxWeight - vertex.getWeight()).sum();
            if (availableWeight < smallestVertex.getWeight()) {
                return false;
            }

            List<VertexOfDualGraph> verticesToRedistribute = new ArrayList<>(smallestVertex.vertices);
            int numVerticesToRedistribute = verticesToRedistribute.size();
            Set<VertexOfDualGraph> wasRedistributed = new HashSet<>(numVerticesToRedistribute);
            Map<VertexOfDualGraph, VertexOfDualGraph> vertexToBestNeighbor = new HashMap<>(numVerticesToRedistribute);

            int expectedQueueSize = numVerticesToRedistribute * neighbors.size();
            PriorityQueue<Comp> priorityQueue = new PriorityQueue<>(
                expectedQueueSize,
                Comparator.comparingDouble((Comp comp) -> - comp.ratio)
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
                    Map<VertexOfDualGraph, Edge> vertexEdges = dualGraph.getEdges().get(vertex);
                    for (VertexOfDualGraph dualVertex : verticesToRedistribute) {
                        if (!wasRedistributed.contains(dualVertex) && vertexEdges.containsKey(dualVertex)) {
                            priorityQueue.add(new Comp(dualVertex, smallestVertex, neighbor));
                        }
                    }
                }
            }
            // If there are still vertices left that couldn't be moved, return false
            if (wasRedistributed.size() != verticesToRedistribute.size()) {
                continue;
            }
            
            int estimatedNewPartsSize = partitionGraph.verticesNumber() + neighbors.size();
            List<Set<VertexOfDualGraph>> newParts = new ArrayList<>(estimatedNewPartsSize);
            for (PartitionGraphVertex v : partitionGraph.verticesArray()) {
                if (v == sm) continue;
                if (!partitionGraph.sortNeighbors(sm).contains(v)) {
                    newParts.add(new HashSet<>(v.vertices));
                }
            }


            for (PartitionGraphVertex v : neighbors) {
                newParts.add(new HashSet<>(v.vertices));
            }


            int totalVertices = dualGraph.verticesNumber();
            Map<VertexOfDualGraph, Integer> dualVertexToPartNumber = new HashMap<>(totalVertices);
            for (int i = 0; i < newParts.size(); i++) {
                for (VertexOfDualGraph vertex : newParts.get(i)) {
                    dualVertexToPartNumber.put(vertex, i);
                }
            }

            this.partitionGraph = PartitionGraphVertex.buildPartitionGraph(dualGraph, newParts, dualVertexToPartNumber);

            return true;
        }
        return false;
    }

    class Comp {
        VertexOfDualGraph vertex;
        PartitionGraphVertex neighbor;
        VertexOfDualGraph bestNeighborVertex;
        double ratio;

        public Comp(VertexOfDualGraph vertex, PartitionGraphVertex part, PartitionGraphVertex neighbor) {
            this.vertex = vertex;
            this.neighbor = neighbor;
            double countInnerEdges = 0;
            double countOuterEdges = 0;
            double bestEdgeLength = 0;
            
            Set<VertexOfDualGraph> neighborVerticesSet = new HashSet<>(neighbor.vertices);
            Set<VertexOfDualGraph> partVerticesSet = new HashSet<>(part.vertices);
            
            Map<VertexOfDualGraph, Edge> vertexEdges = dualGraph.getEdges().get(vertex);
            for (Map.Entry<VertexOfDualGraph, Edge> entry : vertexEdges.entrySet()) {
                VertexOfDualGraph neighborVertex = entry.getKey();
                Edge edge = entry.getValue();
                if (neighborVerticesSet.contains(neighborVertex)) {
                    countOuterEdges += edge.length;
                    if (edge.length > bestEdgeLength) {
                        bestEdgeLength = edge.length;
                        bestNeighborVertex = neighborVertex;
                    }
                } else if (partVerticesSet.contains(neighborVertex)) {
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

    private Map<Set<VertexOfDualGraph>, Double> calculateCutWeights(@NotNull Graph<VertexOfDualGraph> graph, List<Set<VertexOfDualGraph>> partitions) {
        Map<Set<VertexOfDualGraph>, Double> cutEdgesMap = new HashMap<>();
        if (graph == null) {
            logger.warn("graph is null (check 1)");
        }
        for (Set<VertexOfDualGraph> partition : partitions) {
            double cutEdgesWeightSum = 0;
            if (graph == null) {
                logger.warn("graph is null (check 2)");
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