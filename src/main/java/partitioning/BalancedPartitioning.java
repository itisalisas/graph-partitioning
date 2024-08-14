package partitioning;

import graph.Graph;
import graph.Vertex;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class BalancedPartitioning {
	private BalancedPartitioningOfPlanarGraphs bp;
	public BalancedPartitioning(BalancedPartitioningOfPlanarGraphs bp) {
		this.bp = bp;
	}
	public ArrayList<HashSet<Vertex>> partition(Graph graph,
												int maxSumVerticesWeight) {
		bp.partition = new ArrayList<>();
		bp.balancedPartitionAlgorithm(graph, maxSumVerticesWeight);
		return bp.getPartition();
	}

	public void savePartitionToDirectory(String outputDirectory, List<HashSet<Vertex>> partitionResult) {
		File outputDirectoryFile = new File(outputDirectory);
		if (!outputDirectoryFile.exists()) {
			if (!outputDirectoryFile.mkdirs()) {
				throw new RuntimeException("Can't create output directory");
			}
		}

		for (int i = 0; i < partitionResult.size(); i++) {
			File outputFile = new File(outputDirectory + File.separator + "partition_" + i + ".txt");
			try {
				outputFile.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException("Can't create output file");
			}
			try {
				bp.graph.writePartitionToFile(partitionResult.get(i), outputFile);
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
		try (FileWriter writer = new FileWriter(infoFile, false)) {
			List<Integer> weights = partitionResult.stream()
					.map(set -> set.stream()
							.mapToInt(Vertex::getWeight)
							.sum())
					.toList();

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

			double wStandardDeviation = Math.sqrt(wVariance);

			writer.write("MIN = " + minSumWeight + "\n");
			writer.write("MAX = " + maxSumWeight + "\n");
			writer.write("AVERAGE = " + wMean + "\n");
			writer.write("VARIANCE = " + wVariance + "\n");
			writer.write("CV = " + wStandardDeviation / wMean + "\n");
			writer.flush();
		} catch (Exception e) {
			throw new RuntimeException("Can't write info to file");
		}
	}
}
