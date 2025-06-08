import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import graph.*;
import org.junit.jupiter.api.Assertions;

import com.google.gson.Gson;

import addingPoints.LocalizationPoints;
import graphPreparation.GraphPreparation;
import partitioning.BalancedPartitioning;
import partitioning.Balancer;
import partitioning.BubblePartitioning;
import partitioning.BubblePartitioningSequentially;
import partitioning.InertialFlowPartitioning;
import readWrite.CoordinateConversion;
import readWrite.GraphReader;
import readWrite.GraphWriter;
import readWrite.PartitionWriter;
import readWrite.PointsReader;


public class Main {

		public static void saveFacesToJson(HashMap<VertexOfDualGraph, ArrayList<Vertex>> faceToVertices, String filename, boolean geodetic, CoordinateConversion cc) throws Exception {
		List<Map<String, Object>> facesData = new ArrayList<>();
		Gson gson = new Gson();

		for (Map.Entry<VertexOfDualGraph, ArrayList<Vertex>> entry : faceToVertices.entrySet()) {
			VertexOfDualGraph face = entry.getKey();
			ArrayList<Vertex> vertices = entry.getValue();

			List<Map<String, Double>> points = new ArrayList<>();
			for (Vertex vertex : vertices) {
				if (geodetic) {
					vertex = cc.fromEuclidean(vertex);
				}
				Map<String, Double> point = new HashMap<>();
				point.put("x", vertex.x);
				point.put("y", vertex.y);
				points.add(point);
			}

			Map<String, Object> faceEntry = new HashMap<>();
			faceEntry.put("faceId", face.name);
			faceEntry.put("vertices", points);
			facesData.add(faceEntry);
		}

		try (FileWriter writer = new FileWriter(filename)) {
			gson.toJson(facesData, writer);
		}
	}

	public static void main(String[] args) throws RuntimeException, IOException {

		if (args.length < 6) {
			throw new RuntimeException("Use : <algorithm-name> <path-to-file> <path-to-points-file> <max-sum-vertices-weight> <max-region-radius-meters> <output-directory-name> [param]");
		}

		String algorithmName = args[0];
		String pathToFile = args[1];
		String pathToPointsFile = args[2];

		String resourcesDirectory = "src/main/resources/".replace('/', File.separatorChar);
		String outputDirectory = "src/main/output/".replace('/', File.separatorChar);

		BalancedPartitioning partitioning;

		if (algorithmName.equals("IF")) {
			if (args.length < 7) {
				partitioning = new BalancedPartitioning(new InertialFlowPartitioning());
			} else {
				double partitionParameter;
				try {
					partitionParameter = Double.parseDouble(args[6]);
				} catch (NumberFormatException e) {
					throw new RuntimeException("Can't parse partition parameter");
				}
				partitioning = new BalancedPartitioning(new InertialFlowPartitioning(partitionParameter));
			}
		} else if (algorithmName.equals("BUP")) {
			if (args.length < 7) {
				partitioning = new BalancedPartitioning(new BubblePartitioning());
			} else {
				System.out.println("check param");
				partitioning = new BalancedPartitioning(new BubblePartitioning());
			}
		} else if (algorithmName.equals("BUS")) {
			if (args.length < 7) {
				partitioning = new BalancedPartitioning(new BubblePartitioningSequentially());
			} else {
				System.out.println("check param");
				partitioning = new BalancedPartitioning(new BubblePartitioningSequentially());
			}
		} else {
			throw new RuntimeException("No such partition algorithm");
		}

		Graph<Vertex> graph = new Graph<>();
		Graph<Vertex> geodeticGraph = new Graph<>();

		try {
			GraphReader geodeticgr = new GraphReader();
			geodeticgr.readGraphFromFile(geodeticGraph, resourcesDirectory + pathToFile, false);
		} catch (Exception e) {
			throw new RuntimeException("Can't read graph from file: " + e.getMessage());
		}

		CoordinateConversion cc = new CoordinateConversion(geodeticGraph.getEdges().keySet());
		try {
			GraphReader gr = new GraphReader(cc);
			gr.readGraphFromFile(graph, resourcesDirectory + pathToFile, true);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Can't read graph from file: " + e.getMessage());
		}

		int maxSumVerticesWeight;
		try {
			maxSumVerticesWeight = Integer.parseInt(args[3]);
		} catch (NumberFormatException e) {
			throw new RuntimeException("Can't parse max sum vertices weight");
		}

		int maxRegionRadiusMeters;
		try {
			maxRegionRadiusMeters = Integer.parseInt(args[4]);
		} catch (NumberFormatException e) {
			throw new RuntimeException("Can't parse max region radius");
		}

		graph = graph.getLargestConnectedComponent();

		GraphPreparation preparation = new GraphPreparation(false, false);

		Graph<VertexOfDualGraph> preparedGraph = preparation.prepareGraph(graph, 1, outputDirectory, cc);

		for (VertexOfDualGraph v : preparedGraph.verticesArray()) {
			Assertions.assertNotNull(v.getVerticesOfFace());
		}

		List<Vertex> weightedVertices;

		try {
			PointsReader pr = new PointsReader(cc);
			weightedVertices = pr.readWeightedPoints(resourcesDirectory + pathToPointsFile, true);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Can't read points from file: " + e.getMessage());
		}

		LocalizationPoints lp = new LocalizationPoints(new HashSet<>(weightedVertices));
		HashMap<VertexOfDualGraph, ArrayList<Vertex>> faceToVertices = lp.findFacesForPoints(preparedGraph);
		try {
			saveFacesToJson(faceToVertices, "faces.json", true, cc);
		} catch (Exception ignored) {
		}

		for (VertexOfDualGraph v: preparedGraph.verticesArray()) {
			v.setWeight(0);
			if (faceToVertices.containsKey(v)) {
				v.setWeight(faceToVertices.get(v).stream().mapToDouble(Vertex::getWeight).sum());
			}
		}

		partitioning.bp.extractBigVertices(preparedGraph, maxSumVerticesWeight);
    
		GraphWriter gw = new GraphWriter(cc);

		String pathToResultDirectory = args[5];

		long startTime = System.currentTimeMillis();
		
		HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph = preparation.getComparisonForDualGraph();
		ArrayList<HashSet<VertexOfDualGraph>> partitionResultForFaces = partitioning.partition(graph, comparisonForDualGraph, preparedGraph, maxSumVerticesWeight);
		for (HashSet<VertexOfDualGraph> hs : partitionResultForFaces) {
			for (VertexOfDualGraph v : hs) {
				Assertions.assertNotNull(v.getVerticesOfFace());
			}
		}
		HashMap<VertexOfDualGraph, Integer> dualVertexToPartNumber = partitioning.dualVertexToPartNumber();
		for (HashSet<VertexOfDualGraph> hs : partitionResultForFaces) {
			for (VertexOfDualGraph v : hs) {
				Assertions.assertNotNull(v.getVerticesOfFace());
			}
		}
		Graph<PartitionGraphVertex> partitionGraph = PartitionGraphVertex.buildPartitionGraph(preparedGraph, partitionResultForFaces, dualVertexToPartNumber);
		Balancer balancer = new Balancer(partitionGraph, preparedGraph, graph, maxSumVerticesWeight, comparisonForDualGraph, outputDirectory + pathToResultDirectory);
		partitionResultForFaces = balancer.rebalancing();
		HashMap<VertexOfDualGraph, Integer> newDualVertexToPartNumber = new HashMap<>();
		for (int i = 0; i < partitionResultForFaces.size(); i++) {
			for (VertexOfDualGraph vertex : partitionResultForFaces.get(i)) {
				newDualVertexToPartNumber.put(vertex, i);
			}
		}
		partitionGraph = PartitionGraphVertex.buildPartitionGraph(preparedGraph, partitionResultForFaces, newDualVertexToPartNumber);
		gw.printGraphToFile(partitionGraph,  outputDirectory + pathToResultDirectory, "part_graph.txt", true);
		double partitioningTime = ((double)(System.currentTimeMillis() - startTime)) / 1000.0;

		System.out.println("Partition size: " + partitionResultForFaces.size());

		ArrayList<HashSet<Vertex>> partitionResult = new ArrayList<>();
		List<Map.Entry<List<Vertex>, Double>> bounds = new ArrayList<>();

		int countPartsWithNonFittingRadius = 0;
		for (int i = 0; i < partitionResultForFaces.size(); i++) {
			partitionResult.add(new HashSet<>());
			for (VertexOfDualGraph face : partitionResultForFaces.get(i)) {
				partitionResult.get(i).addAll(comparisonForDualGraph.get(face).getVerticesOfFace());
			}
			if (BoundSearcher.findRadius(new ArrayList<>(partitionResult.get(i))) > maxRegionRadiusMeters) {
				countPartsWithNonFittingRadius++;
			}
			bounds.add(Map.entry(BoundSearcher.findBound(graph, partitionResultForFaces.get(i), comparisonForDualGraph), partitionResultForFaces.get(i).stream().mapToDouble(Vertex::getWeight).sum()));
		}

		System.out.println("Number of parts: " + partitionResultForFaces.size() + ", number of parts with radius > max: " + countPartsWithNonFittingRadius);

		List<Point> centers = BalancedPartitioning.calculatePartCenters(partitionResultForFaces);

		gw.printDualGraphWithWeightsToFile(preparedGraph, outputDirectory + pathToResultDirectory, "dual.txt", true);

		PartitionWriter pw = new PartitionWriter(cc);
		pw.savePartitionToDirectory(partitioning, partitioning.bp,outputDirectory + pathToResultDirectory, partitionResultForFaces, true, partitioningTime, cc.referencePoint);
		pw.printBound(bounds, outputDirectory + pathToResultDirectory, true, cc.referencePoint);
    pw.printPartCenters(centers, outputDirectory + pathToResultDirectory, "centers.txt", true, cc.referencePoint);
	}

}