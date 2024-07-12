package partitioningGraph;


public class BalabcedPartitoning {
	private BalancedPartitioningOfPlanarGraphs bp;
	public BalabcedPartitoning(BalancedPartitioningOfPlanarGraphs bp) {
		this.bp = bp;
	}
	public Graph[] partition(Graph graph) {
		return bp.balancedPartitionAlgorithm(graph);
	}
}
