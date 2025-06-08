package partitioning;

import java.util.HashSet;

import graph.Graph;
import graph.VertexOfDualGraph;

public class Bubble {
    HashSet<VertexOfDualGraph> vertexSet;
    Double weight;
    Double borderLength;
    VertexOfDualGraph center;
    Boolean completed;
    Double area;

    public Bubble(VertexOfDualGraph center, Graph<VertexOfDualGraph> graph, int maxBubbleWeight) {
        this.center = center;
        this.weight = center.getWeight();
        this.borderLength = 0.0;
        if (!graph.getEdges().containsKey(center)) {
            System.out.println("Not possible to build bubble with this center in this graph. No such oint in graph.");
        } else {
            for (VertexOfDualGraph v : graph.getEdges().get(center).keySet()) {
                borderLength += graph.getEdges().get(center).get(v).length;
            }
        }  
        vertexSet = new HashSet<>();
        vertexSet.add(center); 
        if (this.weight >= maxBubbleWeight) {
            completed = true;
            System.out.println("center weight >= maxBubbleWeight");
        } else {
            completed = false;
        }
        this.area = center.area;
    }

    public Bubble(Bubble bubble) {
        this.borderLength = bubble.borderLength;
        this.center = new VertexOfDualGraph(bubble.center);
        this.completed = bubble.completed;
        this.vertexSet = new HashSet<>(bubble.vertexSet);
        this.weight = bubble.weight;
        this.area = bubble.area;
    }

    /**
     * check is it possible to add vertex to bubble, if it possible add this vertex
     * @param toAdd VertexOfDualGraph to add
     * @param graph
     * @param maxBubbleWeight
     * @return true - vertex was added, false - not possible to add this vertex
     */
    public boolean addVertexToBuble(VertexOfDualGraph toAdd, Graph<VertexOfDualGraph> graph, int maxBubbleWeight) {
        if (weight + toAdd.getWeight() > maxBubbleWeight) {
            return false;
        }
        weight = weight + toAdd.getWeight();
        borderLength = countNewPerimeter(toAdd, graph);
        vertexSet.add(toAdd);
        center = findNewCenter();
        area = area + toAdd.area;
        return true;
    }
    /**
     * @return VertexOfDualGraph which sum of dist from it to each vertex in bubble is the smallest
     */
    public VertexOfDualGraph findNewCenter() {
        VertexOfDualGraph center = null;
        Double minSumDistToCenter = null;
        for (VertexOfDualGraph ver : vertexSet) {
            if (center == null) {
                center = ver;
                minSumDistToCenter = sumDistToVertex(ver);
                continue;
            }
            Double newDist = sumDistToVertex(ver);
            if (newDist < minSumDistToCenter) {
                center = ver;
                minSumDistToCenter = newDist;
            }
        }
        return center;
    }

    /**
     * @param ver
     * @return sum of dist from ver to each vertex in bubble
     */
    private Double sumDistToVertex(VertexOfDualGraph ver) {
        Double ans = 0.0;
        for (VertexOfDualGraph v : vertexSet) {
            ans += v.getLength(ver);
        }
        return ans;
    }

    /**
     * @param toAdd
     * @param graph
     * @return perimetr of bubble with VertexOfDualGraph toAdd
     */
    public Double countNewPerimeter(VertexOfDualGraph toAdd, Graph<VertexOfDualGraph> graph) {
        Double ans = borderLength;
        for (VertexOfDualGraph v : graph.getEdges().get(toAdd).keySet()) {
            if (vertexSet.contains(v)) {
                ans -= graph.getEdges().get(toAdd).get(v).length;
            }
            ans += graph.getEdges().get(toAdd).get(v).length;
        }
        return ans;
    }

    /**
     * write down "Bubble weight > maxBubbleWeight!" if Bubble weight > maxBubbleWeight
     * @param maxBubbleWeight
     */
    public void checkBubbleWeight(int maxBubbleWeight) {
        if (weight > maxBubbleWeight) {
            System.out.println("Bubble weight > maxBubbleWeight!");
        }
    }

}
