package readWrite;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import graph.Vertex;
import graph.VertexOfDualGraph;
import partitioning.BalancedPartitioning;
import partitioning.BalancedPartitioningOfPlanarGraphs;

public class PartitionWriter {
	
	public static <T extends Vertex> void writePartitionToFile(HashSet<T> part, Double cutWeight, File outFile) throws IOException {
		FileWriter out = new FileWriter(outFile, false);
		out.write(String.format("%f\n", cutWeight));
		out.write(String.format("%d\n", part.size()));
		for (T v : part) {
			out.write(String.format("%d %f %f %f\n", v.getName(), v.getX(), v.getY(), v.getWeight()));
		}
		out.close();
	}
	
	public static void createOutputDirectory(String outputDirectory) {
		File outputDirectoryFile = new File(outputDirectory);
		if (!outputDirectoryFile.exists()) {
			if (!outputDirectoryFile.mkdirs()) {
				throw new RuntimeException("Can't create output directory");
			}
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
		GraphWriter gw = new GraphWriter();
		gw.printVerticesToFile(hull, boundFile);
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
			GraphWriter gw = new GraphWriter();
			gw.printVerticesToFile(bounds.get(i), boundFile);
		}
	}


	public void savePartitionToDirectory(BalancedPartitioning balancedPartitioning, BalancedPartitioningOfPlanarGraphs bp, String outputDirectory, List<HashSet<VertexOfDualGraph>> partitionResult) {
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
				PartitionWriter.writePartitionToFile(part, balancedPartitioning.cutEdgesMap.get(part), outputFile);
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

			double cutMean = balancedPartitioning.cutEdgesMap.values()
					.stream()
					.mapToDouble(Double::doubleValue)
					.average()
					.orElse(0.0);

			double cutVariance = balancedPartitioning.cutEdgesMap.values()
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
			writer.write("TOTAL CUT LENGTH = " + balancedPartitioning.calculateTotalCutEdgesLength() + "\n");
			writer.write("AVERAGE CUT LENGTH = " + cutMean + "\n");
			writer.write("VARIANCE CUT LENGTH = " + cutStandardDeviation + "\n");
			writer.write("CV CUT LENGTH = " + cutStandardDeviation / cutMean + "\n");
			writer.write("TIME = " + balancedPartitioning.partitioningTime + "\n");
			writer.flush();
		} catch (Exception e) {
			throw new RuntimeException("Can't write info to file");
		}

		System.out.println("Empty parts number: " + balancedPartitioning.countEmptyParts(partitionResult));
		System.out.println("Graph weight after: " + balancedPartitioning.countSumPartitioningWeight(partitionResult));

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
				writer.write(df.format((double) weight / (double) balancedPartitioning.maxSumVerticesWeight) + "\n");
			}
		} catch (Exception e) {
			throw new RuntimeException("Can't write ratio to file");
		}
		// System.out.println("Empty parts number: " + countEmptyParts(partitionResult));
	}


}
