package partitioning.algorithms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import graph.Vertex;
import org.junit.jupiter.api.Assertions;

import graph.Graph;
import graph.VertexOfDualGraph;

public abstract class BalancedPartitioningOfPlanarGraphs {

	public List<Set<VertexOfDualGraph>> partition;

	public Graph<VertexOfDualGraph> graph;

	public abstract void balancedPartitionAlgorithm(
            Graph<Vertex> simpleGraph,
            Map<Vertex, VertexOfDualGraph> comparisonForDualGraph,
            Graph<VertexOfDualGraph> graph,
            int maxSumVerticesWeight
    );

	public List<Set<VertexOfDualGraph>> getPartition() {
		for (Set<VertexOfDualGraph> hs : partition) {
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