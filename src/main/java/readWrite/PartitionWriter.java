package readWrite;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import static java.lang.Math.sqrt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.GsonBuilder;

import graph.BoundSearcher;
import graph.Vertex;
import graph.VertexOfDualGraph;
import partitioning.BalancedPartitioning;
import partitioning.BalancedPartitioningOfPlanarGraphs;

public class PartitionWriter {
	
	public static <T extends Vertex> void writePartitionToFile(HashSet<T> part, Double cutWeight, File outFile, boolean geodetic) throws IOException {
		FileWriter out = new FileWriter(outFile, false);
		out.write(String.format("%f\n", cutWeight));
		out.write(String.format("%d\n", part.size()));
		CoordinateConversion cc = null;
		if (geodetic) {
			cc = new CoordinateConversion();
		}
		for (T v : part) {
			if (geodetic) {
				T nV = cc.fromEuclidean(v);
				out.write(String.format("%d %f %f %f\n", v.getName(), nV.x, nV.y, v.getWeight()));
				continue;
			}
			out.write(String.format("%d %f %f %f\n", v.getName(), v.x, v.y, v.getWeight()));
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
	

	public void printHull(List<Vertex> hull, String outputDirectory, String fileName, boolean geodetic) {
		createOutputDirectory(outputDirectory);
		File boundFile = new File(outputDirectory + File.separator + fileName);
		try {
			boundFile.createNewFile();
		} catch (IOException e) {
			throw new RuntimeException("Can't create bound file");
		}
		GraphWriter gw = new GraphWriter();
		gw.printVerticesToFile(hull, boundFile, geodetic);
	}
	
	
	public void printBound(List<List<Vertex>> bounds, String outputDirectory, boolean geodetic) {
		createOutputDirectory(outputDirectory);
		for (int i = 0; i < bounds.size(); i++) {
			File boundFile = new File(outputDirectory + File.separator + "bound_" + i + ".txt");
			try {
				boundFile.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException("Can't create bound file");
			}
			GraphWriter gw = new GraphWriter();
			gw.printVerticesToFile(bounds.get(i), boundFile, geodetic);
		}
	}


	public void savePartitionToDirectory(BalancedPartitioning balancedPartitioning, BalancedPartitioningOfPlanarGraphs bp, String outputDirectory, List<HashSet<VertexOfDualGraph>> partitionResult, boolean geodetic, double partitionTime) {
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
				PartitionWriter.writePartitionToFile(part, balancedPartitioning.cutEdgesMap.get(part), outputFile, geodetic);
			} catch (Exception e) {
				throw new RuntimeException("Can't write partition to file: " + e.getMessage());
			}
		}

		printStat(outputDirectory, partitionResult, balancedPartitioning, bp, partitionTime);

		System.out.println("Empty parts number: " + balancedPartitioning.countEmptyParts(partitionResult));
		System.out.println("Graph weight after: " + balancedPartitioning.countSumPartitioningWeight(partitionResult));
	}

	private void printStat(String outputDirectory, List<HashSet<VertexOfDualGraph>> partitionResult, BalancedPartitioning balancedPartitioning, BalancedPartitioningOfPlanarGraphs bp, double partitionTime) {
		List<Double> weights = partitionResult.stream()
            .map(set -> set.stream().mapToDouble(Vertex::getWeight).sum())
            .collect(Collectors.toList());

			List<Double> cutLengths = new ArrayList<>(balancedPartitioning.cutEdgesMap.values());
			double totalGraphWeight = bp.graph.verticesArray().stream()
					.mapToDouble(VertexOfDualGraph::getWeight)
					.sum();
			
			List<Double> diameters = partitionResult.stream()
            .map(p -> BoundSearcher.findDiameter(p.stream().flatMap(v -> v.getVerticesOfFace().stream()).collect(Collectors.toList())))
            .collect(Collectors.toList());

			Map<String, Object> jsonData = new LinkedHashMap<>();

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

			List<Double> ratios = weights.stream()
					.sorted()
					.map(w -> Math.round((w / balancedPartitioning.maxSumVerticesWeight) * 1000.0) / 1000.0)
					.collect(Collectors.toList());


			jsonData.put("partitionTime", partitionTime);
    		jsonData.put("dualVertexNumber", bp.graph.verticesNumber());
   			jsonData.put("totalGraphWeight", totalGraphWeight);
    		jsonData.put("regionCount", partitionResult.size());
			jsonData.put("minRegionCountEstimate", (int) Math.ceil(totalGraphWeight / balancedPartitioning.maxSumVerticesWeight));
			jsonData.put("minRegionWeight", Collections.min(weights));
    		jsonData.put("maxRegionWeight", Collections.max(weights));
    		jsonData.put("totalBoundaryLength", balancedPartitioning.calculateTotalCutEdgesLength());
			jsonData.put("averageWeight", wMean);
			jsonData.put("weightVariance", sqrt(wVariance));
			
			jsonData.put("averageBoundary", cutMean);
			jsonData.put("boundaryVariance", sqrt(cutVariance));

			jsonData.put("regionBoundaries", cutLengths);
			jsonData.put("regionDiameters", diameters);
			jsonData.put("ratios", ratios);

			try (FileWriter writer = new FileWriter(outputDirectory + "/partition_info.json")) {
				new GsonBuilder()
					.setPrettyPrinting()
					.create()
					.toJson(jsonData, writer);
			} catch (IOException e) {
				throw new RuntimeException("JSON write error", e);
			}

	}

	public void printRedistributedVerticesDirections(Map<VertexOfDualGraph, VertexOfDualGraph> vertexToBestNeighbor, String outputDirectory, boolean geodetic) {
		createOutputDirectory(outputDirectory);
		File edgesFile = new File(outputDirectory + File.separator + "edges.txt");
		try {
			edgesFile.createNewFile();
		} catch (IOException e) {
			throw new RuntimeException("Can't create edges file");
		}
		try (FileWriter writer = new FileWriter(edgesFile, false)) {
			CoordinateConversion cc = null;
			if (geodetic) {
				cc = new CoordinateConversion();
			}
			for (Map.Entry<VertexOfDualGraph, VertexOfDualGraph> edge: vertexToBestNeighbor.entrySet()) {
				VertexOfDualGraph start = edge.getKey();
				VertexOfDualGraph end = edge.getValue();
				if (geodetic) {
					start = cc.fromEuclidean(start);
					end = cc.fromEuclidean(end);
				}
				writer.write(start.x + " " + start.y + " " + end.x + " " + end.y + "\n");
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Can't write edges to file: ");
		}
	}


}
