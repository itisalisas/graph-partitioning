package partitioningGraph;

import java.util.ArrayList;
import java.util.HashSet;

import java.util.List;

public class BalancedPartitioning {
	private BalancedPartitioningOfPlanarGraphs bp;
	public BalancedPartitioning(BalancedPartitioningOfPlanarGraphs bp) {
		this.bp = bp;
	}
	public ArrayList<HashSet<Vertex>> partition(Graph graph, int maxVerticesNumber,
			int maxSumVerticesWeight) {
		return bp.balancedPartitionAlgorithm(graph, maxVerticesNumber, maxSumVerticesWeight);
	}
}
