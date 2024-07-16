package partitioningGraph;

import java.util.ArrayList;
import java.util.HashSet;

public class BalancedPartitoning {
	private BalancedPartitioningOfPlanarGraphs bp;
	public BalancedPartitoning(BalancedPartitioningOfPlanarGraphs bp) {
		this.bp = bp;
	}
	public ArrayList<HashSet<Vertex>> partition(Graph graph, int maxVerticesNumber,
			int maxSumVerticesWeight) {
		return bp.balancedPartitionAlgorithm(graph, maxVerticesNumber, maxSumVerticesWeight);
	}
}
