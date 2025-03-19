package partitioning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;

public class BalancedPartitioning {
	public BalancedPartitioningOfPlanarGraphs bp;

	public double maxSumVerticesWeight;

	public Map<Set<VertexOfDualGraph>, Double> cutEdgesMap;

	public BalancedPartitioning(BalancedPartitioningOfPlanarGraphs bp) {
		this.bp = bp;
	}

	public ArrayList<HashSet<VertexOfDualGraph>> partition(Graph<VertexOfDualGraph> graph,
												int maxSumVerticesWeight) {
		bp.partition = new ArrayList<>();
		this.maxSumVerticesWeight = maxSumVerticesWeight;
		bp.balancedPartitionAlgorithm(graph, maxSumVerticesWeight);
		ArrayList<HashSet<VertexOfDualGraph>> partitionResult = bp.getPartition();
		calculateCutWeights(bp.graph, partitionResult);
		return partitionResult;
	}

	private void calculateCutWeights(Graph<VertexOfDualGraph> graph, List<HashSet<VertexOfDualGraph>> partitions) {
		cutEdgesMap = new HashMap<>();

		for (Set<VertexOfDualGraph> partition : partitions) {
			double cutEdgesWeightSum = 0;

			for (EdgeOfGraph<VertexOfDualGraph> edge : graph.edgesArray()) {
				VertexOfDualGraph u = edge.begin;
				VertexOfDualGraph v = edge.end;
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

	public HashMap<VertexOfDualGraph, Integer> dualVertexToPartNumber() {
		HashMap<VertexOfDualGraph, Integer> dualVertexToPartNumber = new HashMap<>();
		ArrayList<HashSet<VertexOfDualGraph>> partition = bp.partition;
		for (int i = 0; i < partition.size(); i++) {
			for (VertexOfDualGraph vertex : partition.get(i)) {
				dualVertexToPartNumber.put(vertex, i);
			}
		}
		return dualVertexToPartNumber;
	}

}