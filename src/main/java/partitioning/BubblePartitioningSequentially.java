package partitioning;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;

import graph.BoundSearcher;
import graph.Graph;
import graph.Point;
import graph.Vertex;
import graph.VertexOfDualGraph;
import readWrite.PartitionWriter;

public class BubblePartitioningSequentially extends BalancedPartitioningOfPlanarGraphs {

    Comparator<VertexOfDualGraph> vertexComparator = new Comparator<VertexOfDualGraph>() {

        @Override
        public int compare(VertexOfDualGraph o1, VertexOfDualGraph o2) {
            return o1.x < o2.x ? -1 : o1.x > o2.x ? 1 : 0;
        }

    };

    @Override
    public void balancedPartitionAlgorithm(Graph<Vertex> simpleGraph, 
										   HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph, 
										   Graph<VertexOfDualGraph> graph, 
								           int maxSumVerticesWeight) {
        this.graph = graph;

        //list
        TreeSet<VertexOfDualGraph> unused = new TreeSet<>(vertexComparator);
        HashMap<VertexOfDualGraph, Bubble> bubbles = new HashMap<>();
        //HashMap<VertexOfDualGraph ,HashSet<VertexOfDualGraph>> bubbles = new HashMap<VertexOfDualGraph ,HashSet<VertexOfDualGraph>>();
        Bubble bubble = null;
		List<Map.Entry<List<Vertex>, Double>> bounds = new ArrayList<>();
        VertexOfDualGraph center = null;
        unused.addAll(graph.getEdges().keySet());
        int iterCounter = 0;
        while (!unused.isEmpty()) {
            if (iterCounter > graph.getEdges().keySet().size()) {
                System.out.println("    check while rule");
                break;
            }
            //metod prepare
            center = unused.first();
            bubble = new Bubble(center, graph, maxSumVerticesWeight);
            growFullBubble(iterCounter, bounds, simpleGraph, comparisonForDualGraph, graph, maxSumVerticesWeight, bubble, unused);
            bubbles.put(center, new Bubble(bubble));
            iterCounter++;
        }
        System.out.println("    bubbles were grown");
        // HashMap<VertexOfDualGraph, Bubble> zeroBubbles = findZeroWeightBubbles(bubbles);
        // //find close bubble to zero bubble
        // HashSet<VertexOfDualGraph> closeToZeroBubble = new HashSet<>();
        // for (VertexOfDualGraph zeroCenter : zeroBubbles.keySet()) {
        //     for (VertexOfDualGraph vertFromZeroBubble : zeroBubbles.get(zeroCenter).vertexSet) {
        //         for (VertexOfDualGraph bubbleCenter : bubbles.keySet()) {
        //             if (connected(vertFromZeroBubble, bubbles.get(bubbleCenter), graph)) {
        //                 closeToZeroBubble.add(bubbleCenter);
        //             }
        //         }
        //     }
        //     HashMap<VertexOfDualGraph, Double> perimetr = new HashMap<>();
        //     for (VertexOfDualGraph bubbleCenter : closeToZeroBubble) {
        //         Bubble tmp = new Bubble(bubbles.get(bubbleCenter));
        //         for (VertexOfDualGraph vertFromZeroBubble : zeroBubbles.get(zeroCenter).vertexSet) {
        //             tmp.addVertexToBuble(vertFromZeroBubble, graph, maxSumVerticesWeight);
        //         }
        //         perimetr.put(bubbleCenter, tmp.borderLength);
        //     }
        //     Double minPerimetr = 0.0;
        //     VertexOfDualGraph optimBubbleCenter = null;
        //     for (VertexOfDualGraph bubbleCenter : closeToZeroBubble) {
        //         if (optimBubbleCenter == null) {
        //             optimBubbleCenter = bubbleCenter;
        //             minPerimetr = perimetr.get(bubbleCenter);
        //             continue;
        //         }
        //         if (minPerimetr > perimetr.get(bubbleCenter)) {
        //             optimBubbleCenter = bubbleCenter;
        //             minPerimetr = perimetr.get(bubbleCenter);
        //         }
        //     }
        //     for (VertexOfDualGraph vertFromZeroBubble : zeroBubbles.get(zeroCenter).vertexSet) {
        //         bubbles.get(optimBubbleCenter).addVertexToBuble(vertFromZeroBubble, graph, maxSumVerticesWeight);
        //     }
        //     closeToZeroBubble.clear();
        // }
        if (bubbles != null) {
            for (VertexOfDualGraph seed : bubbles.keySet()) {
                partition.add(bubbles.get(seed).vertexSet);
            }
        }
        System.out.println("    end bubble");
        
    }
                
    private boolean connected(VertexOfDualGraph vertFromZeroBubble, Bubble bubble, Graph<VertexOfDualGraph> graph) {
        boolean connected = false;
        for (VertexOfDualGraph v : bubble.vertexSet) {
            if (graph.getEdges().get(vertFromZeroBubble).containsKey(v)) {
                connected = true;
                break;
            }
        }
        return connected;
    }

    private HashMap<VertexOfDualGraph, Bubble> findZeroWeightBubbles(HashMap<VertexOfDualGraph, Bubble> bubbles) {
        HashSet<VertexOfDualGraph> toRemove = new HashSet<>();
        HashMap<VertexOfDualGraph, Bubble> zeroBubbles = new HashMap<>();
        for (VertexOfDualGraph v : bubbles.keySet()) {
            if (bubbles.get(v).weight == 0) {
                zeroBubbles.put(v, bubbles.get(v));
                toRemove.add(v);
            }
        }
        for (VertexOfDualGraph v : toRemove) {
            
            bubbles.remove(v);
        }
        return zeroBubbles;
    }



    //bubble class 
    private boolean findAddVertexToBubble(Graph<VertexOfDualGraph> graph,
                                        Bubble bubble,
                                        TreeSet<VertexOfDualGraph> unused, 
                                        HashSet<VertexOfDualGraph> nextVertices, 
                                        int maxBubbleWeight) {
        boolean vertexAdded = false;
        Double coefWeight = 0.0;
        // Double coefPerimeter = 10.0;
        Double coefPerimeter = Math.sqrt(graph.verticesSumWeight() / maxBubbleWeight);
        Double coefDistToCenter = 1.0;
        //count vertex rating
        HashMap<VertexOfDualGraph, Double> ratingVertices = new HashMap<>();
        // mute bad weight
        for (VertexOfDualGraph ver : nextVertices) {
            Double rating = countRating(coefWeight, coefPerimeter, coefDistToCenter, ver, graph, bubble, maxBubbleWeight);
            // coefWeight * ver.getWeight() + 
            // // delta perimetr
            //                 coefPerimeter * countNewPerimeter(borderLength, ver, graph, bubble) + 
            //                 coefDistToCenter * seed.getLength(ver);
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
        if (bubble.addVertexToBuble(vertexToAdd, graph, maxBubbleWeight)) {
            vertexAdded = true;
            unused.remove(vertexToAdd);
            nextVertices.remove(vertexToAdd);
            for (VertexOfDualGraph v : graph.getEdges().get(vertexToAdd).keySet()) {
                if (unused.contains(v)) {
                    nextVertices.add(v);
                }
            }
        }
        // for (VertexOfDualGraph ver : nextVertices) {
        //     if (bubble.countNewPerimeter(ver, graph) <= 0) {
        //         if (bubble.addVertexToBuble(ver, graph, maxBubbleWeight)) {
        //             vertexAdded = true;
        //             unused.remove(ver);
        //             nextVertices.remove(ver);
        //             for (VertexOfDualGraph v : graph.getEdges().get(ver).keySet()) {
        //                 if (unused.contains(v)) {
        //                     nextVertices.add(v);
        //                 }
        //             }
        //         }
        //     }
        // }
        
        return vertexAdded;
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

    private void growFullBubble(int bubbleNumber,
                                List<Map.Entry<List<Vertex>, Double>> bounds, 
                                Graph<Vertex> simpleGraph, 
                                HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph,
                                Graph<VertexOfDualGraph> graph,
                                int maxSumVerticesWeight, 
                                Bubble bubble,
                                TreeSet<VertexOfDualGraph> unused) {
        //prepare
        int iter = 0;
        unused.remove(bubble.center);
        HashSet<VertexOfDualGraph> nextVertices = new HashSet<VertexOfDualGraph>();
        for (VertexOfDualGraph v : graph.getEdges().get(bubble.center).keySet()) {
            if (unused.contains(v)) {
                nextVertices.add(v);
            }
        }
        if (nextVertices.isEmpty()) {
            System.out.println("check 1 vertex bubble");
            return;
        }
        //main cicle
        while (!unused.isEmpty() && !nextVertices.isEmpty()) {
            iter++;
            if (bubble.weight >= maxSumVerticesWeight) {
                System.out.println("bubble.weight >= maxSumVerticesWeight");
                return;
            }
            boolean tmp = findAddVertexToBubble(graph, 
                                            bubble, 
                                            unused, 
                                            nextVertices, 
                                            maxSumVerticesWeight);
            //step picture
            bounds.add(Map.entry(BoundSearcher.findBound(simpleGraph, bubble.vertexSet, comparisonForDualGraph), bubble.vertexSet.stream().mapToDouble(Vertex::getWeight).sum()));

            // PartitionWriter pw = new PartitionWriter();
            // String str = "src/main/output/testDumpBubbleSeq/".replace('/', File.separatorChar) + bubbleNumber + "_" + center.name + "_"+ iter;
            // HashSet<VertexOfDualGraph> centerToFile = new HashSet<>();
            // centerToFile.add(center);
            // try {
            //     pw.printBound(bounds, str , true);
            // } catch (IOException e) {
            //     // TODO Auto-generated catch block
            //     e.printStackTrace();
            // }
            // pw.printCenter(centerToFile, str, true);
            if (bubble.weight > maxSumVerticesWeight) {
                return;
            } else {
                //step picture
               // bounds.remove(bounds.size() - 1);
            }
            bubble.findNewCenter();
            if (!tmp) return;
        }
    }

    private Double countRating(Double coefWeight, 
                               Double coefPerimeter, 
                               Double coefDistToCenter, 
                               VertexOfDualGraph ver, 
                               Graph<VertexOfDualGraph> graph, 
                               Bubble bubble, 
                               int maxBubbleWeight) {
        // return ver.getWeight() * (coefPerimeter * (countNewPerimeter(borderLength, ver, graph, bubble) - borderLength) + 
        //                           coefDistToCenter * seed.getLength(ver));
        // return (1 - ver.getWeight() / maxBubbleWeight) * (coefPerimeter * (countNewPerimeter(borderLength, ver, graph, bubble) - borderLength) + 
        //                           coefDistToCenter * seed.getLength(ver));
        // return Math.pow(1 - ver.getWeight() / maxBubbleWeight, 2) * (coefPerimeter * (countNewPerimeter(borderLength, ver, graph, bubble) - borderLength) + 
        //                           coefDistToCenter * seed.getLength(ver));
        double blength = 0;
        for (VertexOfDualGraph v : bubble.vertexSet) {
            if (graph.getEdges().get(v).containsKey(ver)) {
                blength = blength + graph.getEdges().get(v).get(ver).length;
            }
        }
        return (maxBubbleWeight - bubble.weight) * Math.pow(bubble.countNewPerimeter(ver, graph), 2) / blength;
    }

    private double sumWeight(HashSet<VertexOfDualGraph> bubble) {
        double sum = 0;
        for (VertexOfDualGraph v : bubble) {
            sum = sum + v.getWeight();
        }
        return sum;
    }
    
}
