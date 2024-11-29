package partitioning;

import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;
import readWrite.PartitionWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public class BalancedPartitioning {
	public BalancedPartitioningOfPlanarGraphs bp;

	public double maxSumVerticesWeight;

	public Map<Set<VertexOfDualGraph>, Double> cutEdgesMap;

	public double partitioningTime;

	public BalancedPartitioning(BalancedPartitioningOfPlanarGraphs bp) {
		this.bp = bp;
	}

	public ArrayList<HashSet<VertexOfDualGraph>> partition(Graph<VertexOfDualGraph> graph,
												int maxSumVerticesWeight) {
		bp.partition = new ArrayList<>();
		this.maxSumVerticesWeight = maxSumVerticesWeight;
		long startTime = System.currentTimeMillis();
		bp.balancedPartitionAlgorithm(graph, maxSumVerticesWeight);
		partitioningTime = ((double) (System.currentTimeMillis() - startTime)) / 1000;
		ArrayList<HashSet<VertexOfDualGraph>> partitionResult = bp.getPartition();
		calculateCutWeights(bp.graph, partitionResult);
		return partitionResult;
	}

	private void calculateCutWeights(Graph graph, List<HashSet<VertexOfDualGraph>> partitions) {
		cutEdgesMap = new HashMap<>();

		for (Set<VertexOfDualGraph> partition : partitions) {
			double cutEdgesWeightSum = 0;

			for (EdgeOfGraph edge : graph.edgesArray()) {
				Vertex u = edge.begin;
				Vertex v = edge.end;
				double weight = edge.getBandwidth();

				if (partition.contains(u) && !partition.contains(v)) {
					cutEdgesWeightSum += weight;
				}
			}

			cutEdgesMap.put(partition, cutEdgesWeightSum);
		}

	}

	public double calculateTotalCutEdgesLength() {
		return cutEdgesMap.values().stream().mapToDouble(Double::doubleValue).sum() / 2;
	}

	public int countEmptyParts(List<HashSet<VertexOfDualGraph>> partitionResult) {
		int ans = 0;
        for (HashSet<VertexOfDualGraph> vertices : partitionResult) {
            if (vertices.isEmpty()) ans++;
        }
		return ans;
	}
	
	public double countSumPartitioningWeight(List<HashSet<VertexOfDualGraph>> partitionResult) {
		double ans = 0;
        for (HashSet<VertexOfDualGraph> vertices : partitionResult) {
            for (Vertex ver : vertices) {
                ans = ans + ver.getWeight();
            }
        }
		return ans;
	}
}