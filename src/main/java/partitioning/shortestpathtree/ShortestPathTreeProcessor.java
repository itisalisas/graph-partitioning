package partitioning.shortestpathtree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import graph.Vertex;
import partitioning.entities.DijkstraResult;
import partitioning.entities.SPTResult;

public class ShortestPathTreeProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ShortestPathTreeProcessor.class);

    private static final double ALPHA = 0.5, BETA = 0.5; // коэф

    public SPTResult findBestPath(DijkstraResult result1, DijkstraResult result2, double sourceWeight, double sinkWeight) {
        int n1 = result1.leafIndices().size();
        int n2 = result2.leafIndices().size();
        if (n1 == 0 || n2 == 0) {
            return new SPTResult(
                    Double.MIN_VALUE,
                    result1.distance() + result2.distance(),
                    combinePaths(result1.path(), result2.path())
            );
        }

        ScoreResult bestScore = new ScoreResult(Double.MAX_VALUE, 0, 0);
        int bestI1 = 0, bestI2 = 0;
        for (int i1 = 0; i1 < n1; i1++) {
            for (int i2 = 0; i2 < n2; i2++) {
                ScoreResult score = leafScore(result1, i1, result2, i2, sourceWeight, sinkWeight);
                if (score.score < bestScore.score) {
                    bestScore = score;
                    bestI1 = i1;
                    bestI2 = i2;
                    logger.debug("New best score: {} at i1 = {}, i2 = {}, weight balance = {}, length = {}", 
                            bestScore.score, i1, i2, bestScore.weightBalance, bestScore.length);
                }
            }
        }

        List<Vertex> path1 = reconstructPathToLeaf(result1, bestI1);
        List<Vertex> path2 = reconstructPathToLeaf(result2, bestI2);

        return new SPTResult(bestScore.weightBalance, bestScore.length, combinePaths(path1, path2));
    }

    private ScoreResult leafScore(DijkstraResult result1, int i1, DijkstraResult result2, int i2, double sourceWeight, double sinkWeight) {
        // TODO - разные порядки длины и баланса???
        if (result1.leafIndices().get(i1) == -1
                || result2.leafIndices().get(i2) == -1
        ) {
            return new ScoreResult(Double.MAX_VALUE, 0, 0);
        }
        double length = result1.distances().get(result1.leafIndices().get(i1))
                + result2.distances().get(result2.leafIndices().get(i2));
        double totalWeight = result1.totalRegionWeight() + result2.totalRegionWeight() + sourceWeight + sinkWeight;
        double leftWeight = result1.weights().get(result1.leafIndices().get(i1))
                + (result2.totalRegionWeight() - result2.weights().get(result2.leafIndices().get(i2)))
                + sourceWeight;
        double balance = Math.abs(ALPHA * totalWeight - leftWeight);
        //System.out.println("length = " + length + ", balance = " + balance + ", totalWeight = " + totalWeight + ", score = " + (BETA * length + (1 - BETA) * balance));
        return new ScoreResult(BETA * length + (1 - BETA) * balance, length, balance);
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

    record ScoreResult(
            double score,
            double length,
            double weightBalance
    )
    { }
}