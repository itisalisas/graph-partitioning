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
import readWrite.CoordinateConversion;

public abstract class BalancedPartitioningOfPlanarGraphs {

	public List<Set<VertexOfDualGraph>> partition = new ArrayList<>();
	
	// Отдельное хранилище для "больших" вершин (извлеченных через extractBigVertices)
	public List<Set<VertexOfDualGraph>> bigVerticesPartitions = new ArrayList<>();

	public Graph<VertexOfDualGraph> graph;

	public abstract void balancedPartitionAlgorithm(
            Graph<Vertex> simpleGraph,
            Graph<VertexOfDualGraph> graph,
            int maxSumVerticesWeight,
            CoordinateConversion coordinateConversion
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
				// Добавляем в отдельное хранилище для больших вершин
                bigVerticesPartitions.add(Set.of(v));
				v.setWeight(v.getWeight() - maxSumVerticesWeight);
			}
		}
	}

}