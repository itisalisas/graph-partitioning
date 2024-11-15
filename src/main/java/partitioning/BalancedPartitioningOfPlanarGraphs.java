package partitioning;

import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;

import java.util.ArrayList;
import java.util.HashSet;

public abstract class BalancedPartitioningOfPlanarGraphs {

	ArrayList<HashSet<VertexOfDualGraph>> partition;

	public Graph<VertexOfDualGraph> graph;

	//public abstract Graph[] balancedPartitionAlgorithm(Graph graph);
	public abstract void balancedPartitionAlgorithm(Graph<VertexOfDualGraph> graph, int maxSumVerticesWeight);

	public ArrayList<HashSet<VertexOfDualGraph>> getPartition() {
		return partition;
	}
	//return name of failed condition or ""
	//public abstract String checkPartitionConditions();
	//public abstract double numericalEvaluationOfPartition();
}