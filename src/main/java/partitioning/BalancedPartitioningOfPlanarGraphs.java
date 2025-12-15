package partitioning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.junit.jupiter.api.Assertions;

import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;

public abstract class BalancedPartitioningOfPlanarGraphs {

	ArrayList<HashSet<VertexOfDualGraph>> partition;

	public Graph<VertexOfDualGraph> graph;

	public abstract void balancedPartitionAlgorithm(Graph<Vertex> simpleGraph, 
													HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph, 
													Graph<VertexOfDualGraph> graph, 
													int maxSumVerticesWeight);

	public ArrayList<HashSet<VertexOfDualGraph>> getPartition() {
		for (HashSet<VertexOfDualGraph> hs : partition) {
			for (VertexOfDualGraph v : hs) {
				Assertions.assertNotNull(v.getVerticesOfFace());
			}
		}
		return partition;
	}

	public void extractBigVertices(Graph<VertexOfDualGraph> dualGraph, int maxSumVerticesWeight) {
		for (VertexOfDualGraph v: dualGraph.verticesArray()) {
			while (v.getWeight() >= maxSumVerticesWeight) {
				v.setWeight(v.getWeight() - maxSumVerticesWeight);
			}
		}
	}

}