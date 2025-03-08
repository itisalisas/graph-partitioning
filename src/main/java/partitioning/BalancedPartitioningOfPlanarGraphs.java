package partitioning;

import java.util.ArrayList;
import java.util.HashSet;

import org.junit.jupiter.api.Assertions;

import graph.Graph;
import graph.VertexOfDualGraph;

public abstract class BalancedPartitioningOfPlanarGraphs {

	ArrayList<HashSet<VertexOfDualGraph>> partition;

	public Graph<VertexOfDualGraph> graph;

	//public abstract Graph[] balancedPartitionAlgorithm(Graph graph);
	public abstract void balancedPartitionAlgorithm(Graph<VertexOfDualGraph> graph, int maxSumVerticesWeight);

	public ArrayList<HashSet<VertexOfDualGraph>> getPartition() {
		for (HashSet<VertexOfDualGraph> hs : partition) {
			for (VertexOfDualGraph v : hs) {
				Assertions.assertNotNull(v.getVerticesOfFace());
			}
		}
		return partition;
	}
	//return name of failed condition or ""
	//public abstract String checkPartitionConditions();
	//public abstract double numericalEvaluationOfPartition();
}