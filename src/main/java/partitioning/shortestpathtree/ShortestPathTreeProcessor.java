package partitioning.shortestpathtree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import graph.Vertex;
import partitioning.entities.DijkstraResult;
import partitioning.entities.SPTResult;

public class ShortestPathTreeProcessor {

    private static final double ALPHA = 0.5; // баланс vs длина

    public SPTResult findBestPath(DijkstraResult result1, DijkstraResult result2) {
        int n1 = result1.leafIndices().size();
        int n2 = result2.leafIndices().size();
        if (n1 == 0 || n2 == 0) {
            return new SPTResult(
                    Double.MIN_VALUE,
                    result1.distance() + result2.distance(),
                    combinePaths(result1.path(), result2.path())
            );
        }

        int i1 = 0, i2 = 0;
        double bestScore = Double.MAX_VALUE;
        int bestI1 = 0, bestI2 = 0;

        while (i1 < n1 && i2 < n2) {
            double score1 = leafScore(result1, i1);
            double score2 = leafScore(result2, i2);

            double combined = score1 + score2;
            if (combined < bestScore) {
                bestScore = combined;
                bestI1 = i1;
                bestI2 = i2;
            }

            // шаг по тому указателю, у которого значение хуже
            if (score1 >= score2) {
                i1++;
            } else {
                i2++;
            }
        }

        List<Vertex> path1 = reconstructPathToLeaf(result1, bestI1);
        List<Vertex> path2 = reconstructPathToLeaf(result2, bestI2);

        double w1 = result1.weights().get(result1.leafIndices().get(bestI1));
        double w2 = result2.weights().get(result2.leafIndices().get(bestI2));

        double balance = Math.abs(2 * w1 - result1.totalRegionWeight())
                + Math.abs(2 * w2 - result2.totalRegionWeight());

        double totalDistance =
                result1.distances().get(result1.leafIndices().get(bestI1))
                        + result2.distances().get(result2.leafIndices().get(bestI2));

        return new SPTResult(balance, totalDistance, combinePaths(path1, path2));
    }

    private double leafScore(DijkstraResult result, int i) {
        double leftWeight = result.weights().get(result.leafIndices().get(i));
        double balance = Math.abs(2 * leftWeight - result.totalRegionWeight());
        double length = result.distances().get(result.leafIndices().get(i));
        return ALPHA * balance + (1 - ALPHA) * length;
    }

    private List<Vertex> reconstructPathToLeaf(DijkstraResult result, int leafIdx) {
        Vertex leaf = result.boundaryLeaves().get(leafIdx);
        List<Vertex> path = new ArrayList<>();
        Vertex current = leaf;
        while (current != null) {
            path.add(current);
            current = result.previous().get(current);
        }
        Collections.reverse(path);
        return path;
    }

    private List<Vertex> combinePaths(List<Vertex> path1, List<Vertex> path2) {
        List<Vertex> combined = new ArrayList<>();
        for (int i = path1.size() - 1; i >= 0; i--) {
            combined.add(path1.get(i));
        }
        for (int i = 1; i < path2.size(); i++) {
            combined.add(path2.get(i));
        }
        return combined;
    }
}