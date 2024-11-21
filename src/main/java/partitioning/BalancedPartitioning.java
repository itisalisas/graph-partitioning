package partitioning;

import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public class BalancedPartitioning {
	private BalancedPartitioningOfPlanarGraphs bp;

	private double maxSumVerticesWeight;

	private Map<Set<VertexOfDualGraph>, Double> cutEdgesMap;

	private double partitioningTime;

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

	private double calculateTotalCutEdgesLength() {
		return cutEdgesMap.values().stream().mapToDouble(Double::doubleValue).sum() / 2;
	}


	public void savePartitionToDirectory(String outputDirectory, List<HashSet<VertexOfDualGraph>> partitionResult) {
		createOutputDirectory(outputDirectory);
		File outputDirectoryFile = new File(outputDirectory);
		if (!outputDirectoryFile.exists()) {
			if (!outputDirectoryFile.mkdirs()) {
				throw new RuntimeException("Can't create output directory");
			}
		}

		for (int i = 0; i < partitionResult.size(); i++) {
			HashSet<VertexOfDualGraph> part = partitionResult.get(i);
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
		List<Double> weights = new ArrayList<>(partitionResult.stream()
				.map(set -> set.stream()
						.mapToDouble(Vertex::getWeight)
						.sum())
				.toList());
		try (FileWriter writer = new FileWriter(infoFile, false)) {

			double minSumWeight = weights.stream().mapToDouble(Double::doubleValue).min().orElse(0);
			double maxSumWeight = weights.stream().mapToDouble(Double::doubleValue).max().orElse(0);

			double wMean = weights.stream()
					.mapToDouble(Double::doubleValue)
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
			weights.sort(Double::compareTo);
			DecimalFormat df = new DecimalFormat("#.###");
			for (double weight : weights) {
				writer.write(df.format((double) weight / (double) maxSumVerticesWeight) + "\n");
			}
		} catch (Exception e) {
			throw new RuntimeException("Can't write ratio to file");
		}
		// System.out.println("Empty parts number: " + countEmptyParts(partitionResult));
	}

	public void printBound(List<List<Vertex>> bounds, String outputDirectory) {
		createOutputDirectory(outputDirectory);
		for (int i = 0; i < bounds.size(); i++) {
			File boundFile = new File(outputDirectory + File.separator + "bound_" + i + ".txt");
			try {
				boundFile.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException("Can't create bound file");
			}
			printVerticesToFile(bounds.get(i), boundFile);
		}
	}

	public void printHull(List<Vertex> hull, String outputDirectory, String fileName) {
		createOutputDirectory(outputDirectory);
		File boundFile = new File(outputDirectory + File.separator + fileName);
		try {
			boundFile.createNewFile();
		} catch (IOException e) {
			throw new RuntimeException("Can't create bound file");
		}
		printVerticesToFile(hull, boundFile);
	}

	private void printVerticesToFile(List<Vertex> vertices, File file) {
		for (Vertex vertex : vertices) {
			try {
				vertex.printVertexToFile(file);
			} catch (Exception e) {
				throw new RuntimeException("Can't print vertex to file " + file.getName());
			}
		}
	}

	private void createOutputDirectory(String outputDirectory) {
		File outputDirectoryFile = new File(outputDirectory);
		if (!outputDirectoryFile.exists()) {
			if (!outputDirectoryFile.mkdirs()) {
				throw new RuntimeException("Can't create output directory");
			}
		}
	}
	
	private int countEmptyParts(List<HashSet<VertexOfDualGraph>> partitionResult) {
		int ans = 0;
        for (HashSet<VertexOfDualGraph> vertices : partitionResult) {
            if (vertices.isEmpty()) ans++;
        }
		return ans;
	}
	
	private double countSumPartitioningWeight(List<HashSet<VertexOfDualGraph>> partitionResult) {
		double ans = 0;
        for (HashSet<VertexOfDualGraph> vertices : partitionResult) {
            for (Vertex ver : vertices) {
                ans = ans + ver.getWeight();
            }
        }
		return ans;
	}
}