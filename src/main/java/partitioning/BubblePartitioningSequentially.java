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
        HashMap<VertexOfDualGraph ,HashSet<VertexOfDualGraph>> bubbles = new HashMap<VertexOfDualGraph ,HashSet<VertexOfDualGraph>>();
        HashSet<VertexOfDualGraph> bubble = new HashSet<>();
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
            bubble.clear();
            center = unused.first();
            growFullBubble(iterCounter, bounds, simpleGraph, comparisonForDualGraph, graph, maxSumVerticesWeight, bubble, center, unused);
            bubbles.put(center, new HashSet<VertexOfDualGraph>(bubble));
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
    


    //bubble class 
    private Double addVertexToBubble(Graph<VertexOfDualGraph> graph,
                                   VertexOfDualGraph seed, 
                                   HashSet<VertexOfDualGraph> bubble,
                                   TreeSet<VertexOfDualGraph> unused, 
                                   HashSet<VertexOfDualGraph> nextVertices, 
                                   Double borderLength,
                                   double sumBubbleWeight,
                                   int maxBubbleWeight) {
        Double coefWeight = 0.0;
        // Double coefPerimeter = 10.0;
        Double coefPerimeter = Math.sqrt(graph.verticesSumWeight() / maxBubbleWeight);
        Double coefDistToCenter = 1.0;
        //count vertex rating
        HashMap<VertexOfDualGraph, Double> ratingVertices = new HashMap<>();
        // mute bad weight
        for (VertexOfDualGraph ver : nextVertices) {
            Double rating = countRating(coefWeight, coefPerimeter, coefDistToCenter, borderLength, ver, graph, bubble, seed, maxBubbleWeight);
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
        if (sumBubbleWeight + vertexToAdd.getWeight() > maxBubbleWeight) {
            return sumBubbleWeight + vertexToAdd.getWeight();
        } 
        unused.remove(vertexToAdd);
        nextVertices.remove(vertexToAdd);
        bubble.add(vertexToAdd);
        for (VertexOfDualGraph v : graph.getEdges().get(vertexToAdd).keySet()) {
            if (unused.contains(v)) {
                nextVertices.add(v);
            }
        }
        borderLength = countNewPerimeter(borderLength, vertexToAdd, graph, bubble);
        return sumBubbleWeight + vertexToAdd.getWeight();
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
                                HashSet<VertexOfDualGraph> bubble, 
                                VertexOfDualGraph center, 
                                TreeSet<VertexOfDualGraph> unused) {
        //prepare
        int iter = 0;
        bubble.add(center);
        unused.remove(center);
        Double borderLength = outEdgeLengthSum(center, graph);
        HashSet<VertexOfDualGraph> nextVertices = new HashSet<VertexOfDualGraph>();
        for (VertexOfDualGraph v : graph.getEdges().get(center).keySet()) {
            if (unused.contains(v)) {
                nextVertices.add(v);
            }
        }
        if (nextVertices.isEmpty()) {
            System.out.println("check 1 vertex bubble");
            return;
        }
        double sumBubbleWeight = center.getWeight();
        //main cicle
        while (!unused.isEmpty() && !nextVertices.isEmpty()) {
            iter++;
            if (sumBubbleWeight > maxSumVerticesWeight) {
                return;
            }
            Double tmp = addVertexToBubble(graph, 
                                            center, 
                                            bubble, 
                                            unused, 
                                            nextVertices, 
                                            borderLength,
                                            sumBubbleWeight,
                                            maxSumVerticesWeight);
            //step picture
            bounds.add(Map.entry(BoundSearcher.findBound(simpleGraph, bubble, comparisonForDualGraph), bubble.stream().mapToDouble(Vertex::getWeight).sum()));

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
            if (tmp > maxSumVerticesWeight) {
                return;
            } else {
                sumBubbleWeight = tmp;
                //step picture
               // bounds.remove(bounds.size() - 1);
            }
            updateSeed(center, bubble, graph, nextVertices, borderLength);
        }
    }

    private Double countRating(Double coefWeight, 
                               Double coefPerimeter, 
                               Double coefDistToCenter, 
                               Double borderLength,
                               VertexOfDualGraph ver, 
                               Graph<VertexOfDualGraph> graph, 
                               HashSet<VertexOfDualGraph> bubble, 
                               VertexOfDualGraph seed, 
                               int maxBubbleWeight) {
        // return ver.getWeight() * (coefPerimeter * (countNewPerimeter(borderLength, ver, graph, bubble) - borderLength) + 
        //                           coefDistToCenter * seed.getLength(ver));
        // return (1 - ver.getWeight() / maxBubbleWeight) * (coefPerimeter * (countNewPerimeter(borderLength, ver, graph, bubble) - borderLength) + 
        //                           coefDistToCenter * seed.getLength(ver));
        // return Math.pow(1 - ver.getWeight() / maxBubbleWeight, 2) * (coefPerimeter * (countNewPerimeter(borderLength, ver, graph, bubble) - borderLength) + 
        //                           coefDistToCenter * seed.getLength(ver));
        double blength = 0;
        for (VertexOfDualGraph v : bubble) {
            if (graph.getEdges().get(v).containsKey(ver)) {
                blength = blength + graph.getEdges().get(v).get(ver).length;
            }
        }
        return (maxBubbleWeight - sumWeight(bubble)) * Math.pow(countNewPerimeter(borderLength, ver, graph, bubble), 2) / blength;
    }

    private double sumWeight(HashSet<VertexOfDualGraph> bubble) {
        double sum = 0;
        for (VertexOfDualGraph v : bubble) {
            sum = sum + v.getWeight();
        }
        return sum;
    }
    
}