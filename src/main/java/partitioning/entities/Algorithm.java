package partitioning.entities;

import partitioning.BalancedPartitioning;
import partitioning.algorithms.BubblePartitioning;
import partitioning.algorithms.BubblePartitioningSequentially;
import partitioning.algorithms.InertialFlowPartitioning;
import partitioning.algorithms.OriginalInertialFlowPartitioning;

public enum Algorithm {
    IF,
    DIF,
    RIF,
    BUP,
    BUS;

    public static BalancedPartitioning getBalancedPartitioningByAlgorithmName(
            Algorithm algorithmName,
            double partitionParameter,
            double lengthPriority,
            boolean useBinarySearch) {
        return switch (algorithmName) {
            case IF -> new BalancedPartitioning(
                new OriginalInertialFlowPartitioning(partitionParameter)
            );
            case DIF -> new BalancedPartitioning(
                new InertialFlowPartitioning(partitionParameter, false)
            );
            case RIF -> new BalancedPartitioning(
                new InertialFlowPartitioning(partitionParameter, true, lengthPriority, useBinarySearch)
            );
            case BUP -> new BalancedPartitioning(
                new BubblePartitioning()
            );
            case BUS -> new BalancedPartitioning(
                new BubblePartitioningSequentially()
            );
        };
    }
}
