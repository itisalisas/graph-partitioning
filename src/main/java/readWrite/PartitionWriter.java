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
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.GsonBuilder;

import graph.BoundSearcher;
import graph.Point;
import graph.Vertex;
import graph.VertexOfDualGraph;
import partitioning.BalancedPartitioning;
import partitioning.BalancedPartitioningOfPlanarGraphs;

public class PartitionWriter {

	static CoordinateConversion cc;

	public PartitionWriter(CoordinateConversion cc) {
		this.cc = cc;
	}
	
	public static <T extends Vertex> void writePartitionToFile(HashSet<T> part, Double cutWeight, File outFile, boolean geodetic, Point center) throws IOException {
		FileWriter out = new FileWriter(outFile, false);
		out.write(String.format("%f\n", cutWeight));
		out.write(String.format("%d\n", part.size()));
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

	public void printPartCenters(List<Point> centers, String outputDirectory, String fileName, boolean geodetic, Point refPoint) {
		createOutputDirectory(outputDirectory);
		File centersFile = new File(outputDirectory + File.separator + fileName);
		try {
			centersFile.createNewFile();
		} catch (IOException e) {
			throw new RuntimeException("Can't create centers file: " + e.getMessage());
		}
		GraphWriter gw = new GraphWriter();
		gw.printPointsToFile(centers, centersFile, geodetic, refPoint);
	}
	

	public void printHull(List<Vertex> hull, String outputDirectory, String fileName, boolean geodetic, Point refPoint) {
		createOutputDirectory(outputDirectory);
		File boundFile = new File(outputDirectory + File.separator + fileName);
		try {
			boundFile.createNewFile();
		} catch (IOException e) {
			throw new RuntimeException("Can't create bound file");
		}
		GraphWriter gw = new GraphWriter();
		gw.printVerticesToFile(hull, boundFile, geodetic, refPoint);
	}
	
	
	public void printBound(List<Map.Entry<List<Vertex>, Double>> bounds, String outputDirectory, boolean geodetic, Point refPoint) throws IOException {
		createOutputDirectory(outputDirectory);
		for (int i = 0; i < bounds.size(); i++) {
			File boundFile = new File(outputDirectory + File.separator + "bound_" + i + ".txt");
			try {
				boundFile.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException("Can't create bound file");
			}
			GraphWriter gw = new GraphWriter();
			gw.printVerticesToFile(bounds.get(i).getKey(), boundFile, geodetic, refPoint);
			FileWriter out = new FileWriter(boundFile, true);
			out.write(String.format("%f", bounds.get(i).getValue()));
			out.close();
		}
	}

	public void printCenter(Set<VertexOfDualGraph> center, String outputDirectory, boolean geodetic, Point refPoint) {
		GraphWriter gw = new GraphWriter();
		List<Vertex> centerList = new ArrayList<Vertex>(center);
		File outputDirectoryFile = new File(outputDirectory);
		if (!outputDirectoryFile.exists()) {
			if (!outputDirectoryFile.mkdirs()) {
				throw new RuntimeException("Can't create output directory");
			}
		}
		File centerFile = new File(outputDirectory + File.separatorChar + "center.txt");
		try {
			centerFile.createNewFile();
		} catch (IOException e) {
			throw new RuntimeException("Can't create bound file");
		}
		FileWriter out;
		try {
			out = new FileWriter(centerFile, true);
			out.write(String.valueOf(centerList.size() + "\n"));
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		gw.printVerticesToFile(centerList, centerFile, true, refPoint);
	}


	public void savePartitionToDirectory(BalancedPartitioning balancedPartitioning, BalancedPartitioningOfPlanarGraphs bp, String outputDirectory, List<HashSet<VertexOfDualGraph>> partitionResult, boolean geodetic, double partitionTime, Point refPoint, long memory) {
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
				PartitionWriter.writePartitionToFile(part, balancedPartitioning.cutEdgesMap.get(part), outputFile, geodetic, refPoint);
			} catch (Exception e) {
				throw new RuntimeException("Can't write partition to file: " + e.getMessage());
			}
		}

		printStat(outputDirectory, partitionResult, balancedPartitioning, bp, partitionTime, memory);

		System.out.println("Empty parts number: " + balancedPartitioning.countEmptyParts(partitionResult));
		System.out.println("Graph weight after: " + balancedPartitioning.countSumPartitioningWeight(partitionResult));
	}

	private void printStat(String outputDirectory, List<HashSet<VertexOfDualGraph>> partitionResult, BalancedPartitioning balancedPartitioning, BalancedPartitioningOfPlanarGraphs bp, double partitionTime, long memory) {
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


			jsonData.put("partitionTime(s)", partitionTime);
			jsonData.put("usedMemory(MB)", memory);
			
			jsonData.put("estimatorRegionNumber", partitionResult.size()/ Math.ceil(totalGraphWeight / balancedPartitioning.maxSumVerticesWeight));
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