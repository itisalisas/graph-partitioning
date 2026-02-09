package partitioning.algorithms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;

import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;

public class BubblePartitioning extends BalancedPartitioningOfPlanarGraphs {

    Comparator<VertexOfDualGraph> vertexComparator = (o1, o2) -> {
        double len1 = Math.pow(o1.x, 2) + Math.pow(o1.y, 2);
        double len2 = Math.pow(o2.x, 2) + Math.pow(o2.y, 2);
        return Double.compare(len1, len2);
    };

    @Override
    public void balancedPartitionAlgorithm(Graph<Vertex> simpleGraph, 
										   HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph, 
										   Graph<VertexOfDualGraph> graph, 
										   int maxSumVerticesWeight) {
        this.graph = graph;
        //find initial seeds
        int seedsNumber = findSeedsNumber(graph, maxSumVerticesWeight);
        Set<VertexOfDualGraph> seeds = findSeeds(graph.getEdges().keySet(), seedsNumber);
        System.out.println("    start seeds were found");
        //start data
        int iterCounter = 0;
        HashMap<VertexOfDualGraph ,HashSet<VertexOfDualGraph>> bubbles = new HashMap<>();
        HashMap<VertexOfDualGraph ,HashSet<VertexOfDualGraph>> nextVertices = new HashMap<>();
        HashMap<VertexOfDualGraph ,Double> borderLength = new HashMap<>();
        HashSet<VertexOfDualGraph> closedBubbles = new HashSet<>();
        HashSet<VertexOfDualGraph> used = new HashSet<>();
        for (VertexOfDualGraph ver : seeds) {
            bubbles.put(ver, new HashSet<>());
            bubbles.get(ver).add(ver);
            borderLength.put(ver, outEdgeLengthSum(ver, graph));
            nextVertices.put(ver, new HashSet<>(graph.getEdges().get(ver).keySet()));
            used.add(ver);
        }
        checkNextVerticesForUsed(closedBubbles, nextVertices, used);
        System.out.println("    start conditions were set");
        //main circle
        while (used.size() < graph.getEdges().size() && closedBubbles.size() < seedsNumber) {
            if (iterCounter > graph.getEdges().size()) {
                System.out.println("    check while rule");
                break;
            }
            growingBubblesStep(closedBubbles, bubbles, graph, used, nextVertices, borderLength, maxSumVerticesWeight);
            updateSeeds(closedBubbles, bubbles, nextVertices, borderLength);
            iterCounter++;
            // TODO: add ref point!!
            // PartitionWriter pw = new PartitionWriter();
            // String str = "src/main/output/testDumpBubbleParal/".replace('/', File.separatorChar) + iterCounter;
            // try {
            //     pw.printBound(bounds, str , true);
            // } catch (IOException e) {
            //     // TODO Auto-generated catch block
            //     e.printStackTrace();
            // }
            // pw.printCenter(bubbles.keySet(), str, true);            
        }
        System.out.println("    bubbles were grown");
        //bubbles to partition
        for (VertexOfDualGraph seed : bubbles.keySet()) {
            partition.add(bubbles.get(seed));
        }
        System.out.println("    end bubble");
        
    }
                
    private Double outEdgeLengthSum(VertexOfDualGraph ver, Graph<VertexOfDualGraph> graph) {
        double ans = 0.0;
        for (VertexOfDualGraph v : graph.getEdges().get(ver).keySet()) {
            ans += graph.getEdges().get(ver).get(v).length;
        }
        return ans;
    }

    private void updateSeeds(HashSet<VertexOfDualGraph> closedBubbles,
                             HashMap<VertexOfDualGraph, HashSet<VertexOfDualGraph>>  bubbles,
                             HashMap<VertexOfDualGraph ,HashSet<VertexOfDualGraph>> nextVertices,
                             HashMap<VertexOfDualGraph, Double> borderLength) {
        
        HashSet<VertexOfDualGraph> exSeeds = new HashSet<>(bubbles.keySet());
        for (VertexOfDualGraph exSeed : exSeeds) {
            if (closedBubbles.contains(exSeed)) {
                continue;
            }
            HashSet<VertexOfDualGraph> bubble = bubbles.get(exSeed);
            HashSet<VertexOfDualGraph> nextVer = nextVertices.get(exSeed);
            Double bourdLength = borderLength.get(exSeed);
            VertexOfDualGraph newSeed = findCenter(bubble);
            bubbles.remove(exSeed);
            bubbles.put(newSeed, bubble);
            nextVertices.remove(exSeed);
            nextVertices.put(newSeed, nextVer);
            borderLength.remove(exSeed);
            borderLength.put(newSeed, bourdLength);
        }
    }

    private VertexOfDualGraph findCenter(HashSet<VertexOfDualGraph> vertices) {
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
        double ans = 0.0;
        for (VertexOfDualGraph v : vertices) {
            ans += v.getLength(ver);
        }
        return ans;
    }

    private void growingBubblesStep(HashSet<VertexOfDualGraph> closedBubbles, 
                                    HashMap<VertexOfDualGraph, HashSet<VertexOfDualGraph>> bubbles, 
                                    Graph<VertexOfDualGraph> graph, 
                                    HashSet<VertexOfDualGraph> used, 
                                    HashMap<VertexOfDualGraph, HashSet<VertexOfDualGraph>> nextVertices, 
                                    HashMap<VertexOfDualGraph, Double> borderLength, 
                                    int maxSumVerticesWeight) {
        // P
        for (VertexOfDualGraph seed : bubbles.keySet()) {
            if (closedBubbles.contains(seed)) {
                continue;
            }
            addVertexToBubble(graph, 
                              seed, 
                              bubbles.get(seed), 
                              used, 
                              nextVertices.get(seed), 
                              borderLength.get(seed),
                              maxSumVerticesWeight);
            checkNextVerticesForUsed(closedBubbles, nextVertices, used);
            for (VertexOfDualGraph v : nextVertices.keySet()) {
                if (nextVertices.get(v).isEmpty()) {
                    closedBubbles.add(v);
                }
            }
        }
    }

    
    private void checkNextVerticesForUsed(HashSet<VertexOfDualGraph> closedBubbles, 
                                          HashMap<VertexOfDualGraph, HashSet<VertexOfDualGraph>> nextVertices,
                                          HashSet<VertexOfDualGraph> used) {
        HashMap<VertexOfDualGraph, HashSet<VertexOfDualGraph>> verticesToDelete = new HashMap<>();
        for (VertexOfDualGraph ver : nextVertices.keySet()) {
            for (VertexOfDualGraph v : nextVertices.get(ver)) {
                if (used.contains(v)) {
                    if (!verticesToDelete.containsKey(ver)) {
                        verticesToDelete.put(ver, new HashSet<>());
                    }
                    verticesToDelete.get(ver).add(v);
                    //nextVertices.get(ver).remove(v);
                }
            }
        }
        for (VertexOfDualGraph ver : verticesToDelete.keySet()) {
            for (VertexOfDualGraph v : verticesToDelete.get(ver)) {
                nextVertices.get(ver).remove(v);
            }
            if (nextVertices.get(ver).isEmpty()) {
                nextVertices.remove(ver);
                closedBubbles.add(ver);
            }
        }
    }

    private void addVertexToBubble(Graph<VertexOfDualGraph> graph,
                                   VertexOfDualGraph seed, 
                                   HashSet<VertexOfDualGraph> bubble,
                                   HashSet<VertexOfDualGraph> used, 
                                   HashSet<VertexOfDualGraph> nextVertices, 
                                   Double borderLength, 
                                   int maxSumVerticesWeight) {
        // norm region
        Double cWeight = 0.0;
        // /P * sqrt(n)
        // Double coefPerimeter = 10.0;
        Double cPerimeter = Math.sqrt(graph.verticesSumWeight() / maxSumVerticesWeight);
        Double cDistToCenter = 1.0;
        //count vertex rating
        HashMap<VertexOfDualGraph, Double> ratingVertices = new HashMap<>();
        // all rating
        for (VertexOfDualGraph ver : nextVertices) {
            Double rating = countRating(cWeight, cPerimeter, cDistToCenter, borderLength, ver, graph, bubble, seed, maxSumVerticesWeight);
                            // coefWeight * ver.getWeight() + 
                            // coefPerimeter * countNewPerimeter(borderLength, ver, graph, bubble) + 
                            // coefDistToCenter * seed.getLength(ver);
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
        used.add(vertexToAdd);
        bubble.add(vertexToAdd);
        nextVertices.addAll(graph.getEdges().get(vertexToAdd).keySet());
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
        return Math.pow(1 - ver.getWeight() / maxBubbleWeight, 2) * (coefPerimeter * (countNewPerimeter(borderLength, ver, graph, bubble) - borderLength) + 
                                  coefDistToCenter * seed.getLength(ver));
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

    private Set<VertexOfDualGraph> findSeeds(Set<VertexOfDualGraph> vertices, int seedsNumber) {
        Set<VertexOfDualGraph> seeds = new HashSet<>();
        ArrayList<VertexOfDualGraph> vertList = new ArrayList<>(vertices);
        vertList.sort(vertexComparator);
        int step = vertList.size() / seedsNumber;
        for (int i = 0; i < seedsNumber; i++) {
            seeds.add(vertList.get(i * step));
        }
        Assertions.assertEquals(seeds.size(), seedsNumber);
        return seeds;
    }

    private int findSeedsNumber(Graph<VertexOfDualGraph> graph, int maxSumVerticesWeight) {
        return (int)(graph.verticesSumWeight() / (maxSumVerticesWeight * 0.9));
    }
    
}
