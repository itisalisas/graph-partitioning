package partitioningGraph;


public class BalancedPartitoning {
	private BalancedPartitioningOfPlanarGraphs bp;
	public BalancedPartitoning(BalancedPartitioningOfPlanarGraphs bp) {
		this.bp = bp;
	}
	public Graph[] partition(Graph graph) {
		return bp.balancedPartitionAlgorithm(graph);
	}
}
