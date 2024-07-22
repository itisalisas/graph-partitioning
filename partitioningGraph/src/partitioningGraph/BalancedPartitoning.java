package partitioningGraph;


import java.util.List;

public class BalancedPartitoning {
	private BalancedPartitioningOfPlanarGraphs bp;
	public BalancedPartitoning(BalancedPartitioningOfPlanarGraphs bp) {
		this.bp = bp;
	}
	public List<Graph> partition(Graph graph) {
		return bp.balancedPartitionAlgorithm(graph);
	}
}
