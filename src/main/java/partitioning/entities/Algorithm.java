package partitioning.entities;

import partitioning.BalancedPartitioning;
import partitioning.algorithms.BubblePartitioning;
import partitioning.algorithms.BubblePartitioningSequentially;
import partitioning.algorithms.InertialFlowPartitioning;

public enum Algorithm {
    DIF,
    RIF,
    BUP,
    BUS;

    public static BalancedPartitioning getBalancedPartitioningByAlgorithmName(
            Algorithm algorithmName,
            double partitionParameter) {
        return switch (algorithmName) {
            case DIF -> new BalancedPartitioning(
                    new InertialFlowPartitioning(partitionParameter, false));
            case RIF -> new BalancedPartitioning(
                    new InertialFlowPartitioning(partitionParameter, true));
            case BUP -> new BalancedPartitioning(
                    new BubblePartitioning());
            case BUS -> new BalancedPartitioning(
                    new BubblePartitioningSequentially());
        };
    }
}
