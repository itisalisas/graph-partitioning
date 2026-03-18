package partitioning.entities;

import partitioning.BalancedPartitioning;
import partitioning.algorithms.BubblePartitioning;
import partitioning.algorithms.BubblePartitioningSequentially;
import partitioning.algorithms.InertialFlowPartitioning;

public enum Algorithm {
    IF,
    BUP,
    BUS;

    public static BalancedPartitioning getBalancedPartitioningByAlgorithmName(
            Algorithm algorithmName,
            double partitionParameter) {
        return switch (algorithmName) {
            case IF -> new BalancedPartitioning(
                    new InertialFlowPartitioning(partitionParameter));
            case BUP -> new BalancedPartitioning(
                    new BubblePartitioning());
            case BUS -> new BalancedPartitioning(
                    new BubblePartitioningSequentially());
        };
    }
}
