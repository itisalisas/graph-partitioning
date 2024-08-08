package partitioning;

import graph.*;
import java.util.ArrayList;
import java.util.HashSet;

public class BalancedPartitioning {
	private BalancedPartitioningOfPlanarGraphs bp;
	public BalancedPartitioning(BalancedPartitioningOfPlanarGraphs bp) {
		this.bp = bp;
	}
	public ArrayList<HashSet<Vertex>> partition(Graph graph,
			int maxSumVerticesWeight) {
		return bp.balancedPartitionAlgorithm(graph, maxSumVerticesWeight);
	}
}
