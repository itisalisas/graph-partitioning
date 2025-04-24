package partitioning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;

import graph.Graph;
import graph.Point;
import graph.VertexOfDualGraph;

public class BubblePartitioningSequentially extends BalancedPartitioningOfPlanarGraphs {

    Comparator<VertexOfDualGraph> vertexComparator = new Comparator<VertexOfDualGraph>() {

        @Override
        public int compare(VertexOfDualGraph o1, VertexOfDualGraph o2) {
            return o1.x < o2.x ? -1 : o1.x > o2.x ? 1 : 0;
        }

    };

    @Override
    public void balancedPartitionAlgorithm(Graph<VertexOfDualGraph> graph, int maxSumVerticesWeight) {
        this.graph = graph;

        TreeSet<VertexOfDualGraph> unused = new TreeSet<>(vertexComparator);
        HashMap<VertexOfDualGraph ,HashSet<VertexOfDualGraph>> bubbles = new HashMap<VertexOfDualGraph ,HashSet<VertexOfDualGraph>>();
        HashSet<VertexOfDualGraph> nextVertices = new HashSet<>();
        Double borderLength = 0.0;
        HashSet<VertexOfDualGraph> bubble = new HashSet<>();
        VertexOfDualGraph center = null;
        unused.addAll(graph.getEdges().keySet());
        int iterCounter = 0;
        while (!unused.isEmpty()) {
            if (iterCounter > graph.getEdges().keySet().size()) {
                System.out.println("    check while rule");
                break;
            }
            //metod prepare
            nextVertices.clear();
            borderLength = 0.0;
            bubble.clear();
            center = unused.first();
            growFullBubble(graph, maxSumVerticesWeight, nextVertices, borderLength, bubble, center, unused);
            bubbles.put(center, bubble);
            iterCounter++;
        }
        System.out.println("    bubbles were grown");
        //bubbles to partition
        if (bubbles != null) {
            for (VertexOfDualGraph seed : bubbles.keySet()) {
                partition.add(bubbles.get(seed));
            }
        }
        System.out.println("    end bubble");
        
    }
                
    private Double outEdgeLengthSum(VertexOfDualGraph ver, Graph<VertexOfDualGraph> graph) {
        Double ans = 0.0;
        for (VertexOfDualGraph v : graph.getEdges().get(ver).keySet()) {
            ans += graph.getEdges().get(ver).get(v).length;
        }
        return ans;
    }

    private VertexOfDualGraph updateSeed(VertexOfDualGraph center, 
                                              HashSet<VertexOfDualGraph>  bubble, 
                                              Graph<VertexOfDualGraph> graph, 
                                              HashSet<VertexOfDualGraph> nextVertices, 
                                              Double borderLength) {
        center = findCenter(graph, bubble);
        return center;
    }

    private VertexOfDualGraph findCenter(Graph<VertexOfDualGraph> graph, HashSet<VertexOfDualGraph> vertices) {
        VertexOfDualGraph center = null;
        Double minSumDistToCenter = null;
        for (VertexOfDualGraph ver : vertices) {
            if (center == null) {
                center = ver;
                minSumDistToCenter = sumDist(ver, vertices);
                continue;
            }
            Double newDist = sumDist(ver, vertices);
            if (newDist < minSumDistToCenter) {
                center = ver;
                minSumDistToCenter = newDist;
            }
        }
        return center;
    }

    private Double sumDist(VertexOfDualGraph ver, HashSet<VertexOfDualGraph> vertices) {
        Double ans = 0.0;
        for (VertexOfDualGraph v : vertices) {
            ans += v.getLength(ver);
        }
        return ans;
    }
    
    private void checkNextVerticesForUsed(HashSet<VertexOfDualGraph> nextVertices,
                                          TreeSet<VertexOfDualGraph> unused,
                                          boolean bubbleToClose) {
        if (bubbleToClose) return;
        HashSet<VertexOfDualGraph> verticiesToDelete = new HashSet<>();
        for (VertexOfDualGraph ver : nextVertices) {
            if (!unused.contains(ver)) {
                verticiesToDelete.add(ver);
                //nextVertices.get(ver).remove(v);
            }
        }
        for (VertexOfDualGraph v : verticiesToDelete) {
            nextVertices.remove(v);
        }
        if (nextVertices.isEmpty()) {
            bubbleToClose = true;
        }
    }

    private boolean addVertexToBubble(Graph<VertexOfDualGraph> graph,
                                   VertexOfDualGraph seed, 
                                   HashSet<VertexOfDualGraph> bubble,
                                   TreeSet<VertexOfDualGraph> unused, 
                                   HashSet<VertexOfDualGraph> nextVertices, 
                                   Double borderLength,
                                   boolean bubbleToClose,
                                   double sumBubbleWeight,
                                   int maxBubbleWeight) {
        if (bubbleToClose) return true;
        Double coefWeight = -1.0;
        Double coefPerimeter = 1.0;
        Double coefDistToCenter = 1.0;
        //count vertex rating
        HashMap<VertexOfDualGraph, Double> ratingVertices = new HashMap<>();
        for (VertexOfDualGraph ver : nextVertices) {
            Double rating = coefWeight * ver.getWeight() + 
                            coefPerimeter * countNewPerimeter(borderLength, ver, graph, bubble) + 
                            coefDistToCenter * seed.getLength(ver);
            ratingVertices.put(ver, rating);
        }
        //find vertex with min rating
        VertexOfDualGraph vertexToAdd = null;
        Double minRating = 0.0;
        for (VertexOfDualGraph ver : ratingVertices.keySet()) {
            if (vertexToAdd == null) {
                vertexToAdd = ver;
                minRating = ratingVertices.get(ver);
                continue;
            }
            if (ratingVertices.get(ver) < minRating) {
                vertexToAdd = ver;
                minRating = ratingVertices.get(ver);
            }
        }
        //update position
        if (sumBubbleWeight + vertexToAdd.getWeight() > maxBubbleWeight) {
            bubbleToClose = true;
            return true;
        } 
        unused.remove(vertexToAdd);
        bubble.add(vertexToAdd);
        nextVertices.addAll(graph.getEdges().get(vertexToAdd).keySet());
        borderLength = countNewPerimeter(borderLength, vertexToAdd, graph, bubble);
        sumBubbleWeight = sumBubbleWeight + vertexToAdd.getWeight();
        return false;
    }

           


    private Double countNewPerimeter(Double borderLength, 
                                    VertexOfDualGraph ver, 
                                    Graph<VertexOfDualGraph> graph,
                                    HashSet<VertexOfDualGraph> bubble) {
        Double ans = borderLength;
        for (VertexOfDualGraph v : graph.getEdges().get(ver).keySet()) {
            if (bubble.contains(v)) {
                ans -= graph.getEdges().get(ver).get(v).length;
            }
            ans += graph.getEdges().get(ver).get(v).length;
        }
        return ans;
    }

    private void growFullBubble(Graph<VertexOfDualGraph> graph,
                                int maxSumVerticesWeight, 
                                HashSet<VertexOfDualGraph> nextVertices, 
                                Double borderLength, 
                                HashSet<VertexOfDualGraph> bubble, 
                                VertexOfDualGraph center, 
                                TreeSet<VertexOfDualGraph> unused) {
        bubble.add(center);
        borderLength = outEdgeLengthSum(center, graph);
        nextVertices.addAll(graph.getEdges().get(center).keySet());
        unused.remove(center);
        int sumBubbleWeight = 0;
        boolean bubbleToClose = false;
        while (!unused.isEmpty()) {
            if (sumBubbleWeight > maxSumVerticesWeight) {
                return;
            }
            if (addVertexToBubble(graph, 
                              center, 
                              bubble, 
                              unused, 
                              nextVertices, 
                              borderLength,
                              bubbleToClose,
                              sumBubbleWeight,
                              maxSumVerticesWeight)) return;
            if (bubbleToClose) {
                System.out.println("bubble was grown");
                return;
            }
            checkNextVerticesForUsed(nextVertices, unused, bubbleToClose);
            if (bubbleToClose) {
                System.out.println("bubble was grown");
                return;
            }
            updateSeed(center, bubble, graph, nextVertices, borderLength);
        }
        System.out.println("bubble was grown");
    }
    
}
