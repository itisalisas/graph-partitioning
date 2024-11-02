package partitioning;

import graph.Graph;
import graph.Vertex;

import java.util.ArrayList;
import java.util.HashSet;

public abstract class BalancedPartitioningOfPlanarGraphs {

	ArrayList<HashSet<Vertex>> partition;

	public Graph graph;

	//public abstract Graph[] balancedPartitionAlgorithm(Graph graph);
	public abstract void balancedPartitionAlgorithm(Graph graph, double maxSumVerticesWeight);

	public ArrayList<HashSet<Vertex>> getPartition() {
		return partition;
	}
	//return name of failed condition or ""
	//public abstract String checkPartitionConditions();
	//public abstract double numericalEvaluationOfPartition();
}