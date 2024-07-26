package partitioningGraph;

import java.util.ArrayList;
import java.util.HashSet;

import java.util.List;

public abstract class BalancedPartitioningOfPlanarGraphs {

	//public abstract Graph[] balancedPartitionAlgorithm(Graph graph);
	public abstract ArrayList<HashSet<Vertex>> balancedPartitionAlgorithm(Graph graph, int maxVerticesNumber,
			int maxSumVerticesWeight);

	//return name of failed condition or ""
	//public abstract String checkPartitionConditions();
	//public abstract double numericalEvaluationOfPartition();
}
