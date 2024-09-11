package partitioning;

import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public class BalancedPartitioning {
	private BalancedPartitioningOfPlanarGraphs bp;

	private int maxSumVerticesWeight;

	private Map<Set<Vertex>, Double> cutEdgesMap;

	private double partitioningTime;

	public BalancedPartitioning(BalancedPartitioningOfPlanarGraphs bp) {
		this.bp = bp;
	}
	public ArrayList<HashSet<Vertex>> partition(Graph graph,
												int maxSumVerticesWeight) {
		bp.partition = new ArrayList<>();
		this.maxSumVerticesWeight = maxSumVerticesWeight;
		long startTime = System.currentTimeMillis();
		bp.balancedPartitionAlgorithm(graph, maxSumVerticesWeight);
		partitioningTime = ((double) (System.currentTimeMillis() - startTime)) / 1000;
		ArrayList<HashSet<Vertex>> partitionResult = bp.getPartition();
		calculateCutWeights(bp.graph, partitionResult);
		return partitionResult;
	}

	private void calculateCutWeights(Graph graph, List<HashSet<Vertex>> partitions) {
		cutEdgesMap = new HashMap<>();

		for (Set<Vertex> partition : partitions) {
			double cutEdgesWeightSum = 0;

			for (EdgeOfGraph edge : graph.edgesArray()) {
				Vertex u = edge.getBegin();
				Vertex v = edge.getEnd();
				double weight = edge.getBandwidth();

				if (partition.contains(u) && !partition.contains(v)) {
					cutEdgesWeightSum += weight;
				}
			}

			cutEdgesMap.put(partition, cutEdgesWeightSum);
		}

	}

	private double calculateTotalCutEdgesLength() {
		return cutEdgesMap.values().stream().mapToDouble(Double::doubleValue).sum() / 2;
	}


	public void savePartitionToDirectory(String outputDirectory, List<HashSet<Vertex>> partitionResult) {
		File outputDirectoryFile = new File(outputDirectory);
		if (!outputDirectoryFile.exists()) {
			if (!outputDirectoryFile.mkdirs()) {
				throw new RuntimeException("Can't create output directory");
			}
		}

		for (int i = 0; i < partitionResult.size(); i++) {
			HashSet<Vertex> part = partitionResult.get(i);
			File outputFile = new File(outputDirectory + File.separator + "partition_" + i + ".txt");
			try {
				outputFile.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException("Can't create output file");
			}
			try {
				bp.graph.writePartitionToFile(part, cutEdgesMap.get(part), outputFile);
			} catch (Exception e) {
				throw new RuntimeException("Can't write partition to file: " + e.getMessage());
			}
		}

		File infoFile = new File(outputDirectory + File.separator + "info.txt");
		try {
			infoFile.createNewFile();
		} catch (IOException e) {
			throw new RuntimeException("Can't create info file");
		}
		List<Integer> weights = new ArrayList<>(partitionResult.stream()
				.map(set -> set.stream()
						.mapToInt(Vertex::getWeight)
						.sum())
				.toList());
		try (FileWriter writer = new FileWriter(infoFile, false)) {

			int minSumWeight = weights.stream().mapToInt(Integer::intValue).min().orElse(0);
			int maxSumWeight = weights.stream().mapToInt(Integer::intValue).max().orElse(0);

			double wMean = weights.stream()
					.mapToInt(Integer::intValue)
					.average()
					.orElse(0.0);

			double wVariance = weights.stream()
					.mapToDouble(weight -> Math.pow(weight - wMean, 2))
					.average()
					.orElse(0.0);

			double cutMean = cutEdgesMap.values()
					.stream()
					.mapToDouble(Double::doubleValue)
					.average()
					.orElse(0.0);

			double cutVariance = cutEdgesMap.values()
					.stream()
					.mapToDouble(cutSize -> Math.pow(cutSize - cutMean, 2))
					.average()
					.orElse(0.0);

			double wStandardDeviation = Math.sqrt(wVariance);
			double cutStandardDeviation = Math.sqrt(cutVariance);

			writer.write("VERTEX NUMBER = " + bp.graph.verticesArray().size() + "\n");
			writer.write("NUMBER OF PARTS = " + partitionResult.size() + "\n");
			writer.write("MIN = " + minSumWeight + "\n");
			writer.write("MAX = " + maxSumWeight + "\n");
			writer.write("AVERAGE = " + wMean + "\n");
			writer.write("VARIANCE = " + wStandardDeviation + "\n");
			writer.write("CV = " + wStandardDeviation / wMean + "\n");
			writer.write("TOTAL CUT LENGTH = " + calculateTotalCutEdgesLength() + "\n");
			writer.write("AVERAGE CUT LENGTH = " + cutMean + "\n");
			writer.write("VARIANCE CUT LENGTH = " + cutStandardDeviation + "\n");
			writer.write("CV CUT LENGTH = " + cutStandardDeviation / cutMean + "\n");
			writer.write("TIME = " + partitioningTime + "\n");
			writer.flush();
		} catch (Exception e) {
			throw new RuntimeException("Can't write info to file");
		}

		System.out.println("Empty parts number: " + countEmptyParts(partitionResult));
		System.out.println("Graph weight after: " + countSumPartitioningWeight(partitionResult));

		File ratioFile = new File(outputDirectory + File.separator + "ratio.txt");
		try {
			ratioFile.createNewFile();
		} catch (IOException e) {
			throw new RuntimeException("Can't create ratio file");
		}
		try (FileWriter writer = new FileWriter(ratioFile, false)) {
			weights.sort(Integer::compareTo);
			DecimalFormat df = new DecimalFormat("#.###");
			for (int weight : weights) {
				writer.write(df.format((double) weight / (double) maxSumVerticesWeight) + "\n");
			}
		} catch (Exception e) {
			throw new RuntimeException("Can't write ratio to file");
		}
		// System.out.println("Empty parts number: " + countEmptyParts(partitionResult));
	}

	public void printBound(List<Vertex> bound, String outputDirectory) {
			File boundFile = new File(outputDirectory + File.separator + "bound.txt");
			try {
				boundFile.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException("Can't create bound file");
			}
			for (Vertex vertex : bound) {
				try {
					vertex.printVertexToFile(boundFile);
				} catch (Exception e) {
					throw new RuntimeException("Can't print bound to file");
				}
			}
	}
	
	private int countEmptyParts(List<HashSet<Vertex>> partitionResult) {
		int ans = 0;
		for (int i = 0; i < partitionResult.size(); i++) {
			if (partitionResult.get(i).isEmpty()) ans++; 
		}
		return ans;
	}
	
	private long countSumPartitioningWeight(List<HashSet<Vertex>> partitionResult) {
		long ans = 0;
		for (int i = 0; i < partitionResult.size(); i++) {
			for (Vertex ver : partitionResult.get(i)) {
				ans = ans + ver.getWeight();
			}
		}
		return ans;
	}
}