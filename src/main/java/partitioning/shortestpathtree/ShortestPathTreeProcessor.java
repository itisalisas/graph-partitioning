package partitioning.shortestpathtree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graph.Vertex;
import partitioning.entities.DijkstraResult;
import partitioning.entities.SPTResult;

public class ShortestPathTreeProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ShortestPathTreeProcessor.class);

    private static final double EXTERNAL_BOUNDARY_PENALTY = 10.0;
    private final double lengthPriority;
    private final double alpha;

    public ShortestPathTreeProcessor(double lengthPriority, double alpha) {
        this.lengthPriority = lengthPriority;
        this.alpha = alpha;
    }

    public ShortestPathTreeProcessor(double alpha) {
        this.lengthPriority = 0.5;
        this.alpha = alpha;
    }

    public static double calculateAlpha(double totalWeight, int maxWeight) {
        int k = (int) Math.ceil(totalWeight / maxWeight);
        int leftParts = k / 2;  // floor(k/2)
        logger.info("Dynamic alpha calculation: totalWeight={}, maxWeight={}, k={}, leftParts={}, rightParts={}, alpha={}", 
                    totalWeight, maxWeight, k, leftParts, (k - leftParts), (double) leftParts / k);
        return (double) leftParts / k;
    }

    public SPTResult findBestPath(
            DijkstraResult result1, 
            DijkstraResult result2, 
            double sourceWeight, 
            double sinkWeight, 
            double boundaryLength,
            List<Vertex> externalBoundary,
            double totalWeight
        ) {
        
        int n1 = result1.leafIndices().size();
        int n2 = result2.leafIndices().size();
        if (n1 == 0 || n2 == 0) {
            logger.warn("! return fallback");
            return new SPTResult(
                    Double.MAX_VALUE,
                    Double.MIN_VALUE,
                    result1.distance() + result2.distance(),
                    combinePaths(result1.path(), result2.path()),
                    true
            );
        }

        Set<Vertex> externalBoundarySet = externalBoundary != null 
            ? new HashSet<>(externalBoundary) 
            : new HashSet<>();
        Set<Long> externalBoundaryNamesSet = externalBoundarySet.stream().map(Vertex::getName).collect(Collectors.toSet());

        List<List<Vertex>> paths1 = new ArrayList<>(n1);
        int[] externalCounts1 = new int[n1];
        for (int i = 0; i < n1; i++) {
            List<Vertex> path = reconstructPathToLeaf(result1, i);
            paths1.add(path);
            externalCounts1[i] = countExternalBoundarySegments(path, externalBoundaryNamesSet);
        }

        List<List<Vertex>> paths2 = new ArrayList<>(n2);
        int[] externalCounts2 = new int[n2];
        for (int i = 0; i < n2; i++) {
            List<Vertex> path = reconstructPathToLeaf(result2, i);
            paths2.add(path);
            externalCounts2[i] = countExternalBoundarySegments(path, externalBoundaryNamesSet);
        }

        ScoreResult bestScore = new ScoreResult(Double.MAX_VALUE, 0, 0, false);
        int bestI1 = 0, bestI2 = 0;
        double bestLength = Double.MAX_VALUE;
        boolean isBestPathPositiveBalance = false;
        
        for (int i1 = 0; i1 < n1; i1++) {
            for (int i2 = 0; i2 < n2; i2++) {
                int totalExternalCount = externalCounts1[i1] + externalCounts2[i2];
                if (!paths1.get(i1).isEmpty() && !paths2.get(i2).isEmpty()) {
                    Vertex root1 = paths1.get(i1).get(0);
                    Vertex root2 = paths2.get(i2).get(0);
                    if ((root1.name / 1000 == root2.name / 1000) && (externalBoundaryNamesSet.contains(root1.name) || externalBoundaryNamesSet.contains(root1.name / 1000))) {
                        totalExternalCount--;
                    }
                }
                
                ScoreResult score = leafScore(
                    result1, i1, 
                    result2, i2, 
                    sourceWeight, sinkWeight, boundaryLength,
                    totalWeight, totalExternalCount
                );
                
                if (score.score < bestScore.score) {
                    bestScore = score;
                    bestI1 = i1;
                    bestI2 = i2;
                    logger.debug("New best score: {} at i1 = {}, i2 = {}, weight balance = {}, length = {}",
                            bestScore.score, i1, i2, bestScore.weightBalance, bestScore.length);
                }
                if (bestLength > score.length) {
                    bestLength = score.length;
                    isBestPathPositiveBalance = score.isPositive;
                }
            }
        }

        return new SPTResult(
            bestScore.score, 
            bestScore.weightBalance, 
            bestScore.length, 
            combinePaths(paths1.get(bestI1), paths2.get(bestI2)), 
            isBestPathPositiveBalance
        );
    }

    private int countExternalBoundarySegments(List<Vertex> path, Set<Long> externalBoundaryNames) {
        int segments = 0;
        boolean inSegment = false;
        for (Vertex v : path) {
            boolean onBoundary = externalBoundaryNames.contains(v.name) || externalBoundaryNames.contains(v.name / 1000);
            if (onBoundary) {
                if (!inSegment) {
                    segments++;
                    inSegment = true;
                }
            } else {
                inSegment = false;
            }
        }
        return segments;
    }

    private ScoreResult leafScore(
            DijkstraResult result1, 
            int i1, 
            DijkstraResult result2, 
            int i2, 
            double sourceWeight, 
            double sinkWeight, 
            double boundaryLength,
            double totalWeight,
            double externalVerticesCount
        ) {
        
        Vertex leaf1 = result1.boundaryLeaves().get(i1);
        Vertex leaf2 = result2.boundaryLeaves().get(i2);
        Double d1 = result1.dijkstraDistances() == null ? null : result1.dijkstraDistances().get(leaf1);
        Double d2 = result2.dijkstraDistances() == null ? null : result2.dijkstraDistances().get(leaf2);
        
        if (d1 == null || d2 == null || d1 == Double.MAX_VALUE || d2 == Double.MAX_VALUE) {
            return new ScoreResult(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, true);
        }

        double length = d1 + d2;
        int idx1 = result1.leafIndices().get(i1);
        int idx2 = result2.leafIndices().get(i2);

        double res1Weight = idx1 == -1 ? 0. : result1.weights().get(idx1);
        double res2Weight = idx2 == -1 ? 0. : result2.weights().get(idx2);
        double leftRes1Weight = result1.totalRegionWeight() == 0 ? totalWeight - sourceWeight - sinkWeight - result2.totalRegionWeight() - res1Weight : result1.totalRegionWeight() - res1Weight;
        double leftWeight = res2Weight + leftRes1Weight + sourceWeight;

        double balance = Math.abs(alpha * totalWeight - leftWeight);
        if (Math.abs((1 - alpha) * totalWeight - leftWeight) < balance) {
            balance = Math.abs(alpha * totalWeight - leftWeight);
        }
        double normalizedLength = length / boundaryLength;
        double normalizedBalance = balance / totalWeight;
        
        double score = lengthPriority * normalizedLength + (1 - lengthPriority) * normalizedBalance;
        
        double penalizedScore = score;
        if (externalVerticesCount > 2) {
             double penalty = (externalVerticesCount - 2) * EXTERNAL_BOUNDARY_PENALTY;
             penalizedScore = score + penalty;
        }
        
        logger.debug("Length: {}, normalized length: {}, balance: {}, normalized balance: {}, score: {}, penalized score: {}", 
            length, normalizedLength, balance, normalizedBalance, score, penalizedScore);
        
        return new ScoreResult(
            penalizedScore, 
            length, 
            balance, 
            alpha * totalWeight - leftWeight > 0
        );
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
            double weightBalance,
            boolean isPositive
    )
    { }
}