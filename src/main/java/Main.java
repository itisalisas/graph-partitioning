import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;

import com.google.gson.Gson;

import addingPoints.LocalizationPoints;
import graph.BoundSearcher;
import graph.Graph;
import graph.PartitionGraphVertex;
import graph.Vertex;
import graph.VertexOfDualGraph;
import graphPreparation.GraphPreparation;
import partitioning.BalancedPartitioning;
import partitioning.Balancer;
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

		if (args.length < 5) {
			throw new RuntimeException("Use : <algorithm-name> <path-to-file> <path-to-points-file> <max-sum-vertices-weight> <output-directory-name> [param]");
		}

		String algorithmName = args[0];
		String pathToFile = args[1];
		String pathToPointsFile = args[2];

		String resourcesDirectory = "src/main/resources/".replace('/', File.separatorChar);
		String outputDirectory = "src/main/output/".replace('/', File.separatorChar);

		BalancedPartitioning partitioning;

		if (algorithmName.equals("IF")) {
			if (args.length < 6) {
				partitioning = new BalancedPartitioning(new InertialFlowPartitioning());
			} else {
				double partitionParameter;
				try {
					partitionParameter = Double.parseDouble(args[5]);
				} catch (NumberFormatException e) {
					throw new RuntimeException("Can't parse partition parameter");
				}
				partitioning = new BalancedPartitioning(new InertialFlowPartitioning(partitionParameter));
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

		graph = graph.getLargestConnectedComponent();

		GraphPreparation preparation = new GraphPreparation(false, false);

		Graph<VertexOfDualGraph> preparedGraph = preparation.prepareGraph(graph, 1, outputDirectory, cc);

		for (VertexOfDualGraph v : preparedGraph.verticesArray()) {
			Assertions.assertNotNull(v.getVerticesOfFace());
		}

		List<Vertex> weightedVertices = new ArrayList<>();

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
		} catch (Exception e) {
		}

		for (VertexOfDualGraph v: preparedGraph.verticesArray()) {
			v.setWeight(0);
			if (faceToVertices.containsKey(v)) {
				v.setWeight(faceToVertices.get(v).stream().mapToDouble(p -> p.getWeight()).sum());
			}
		}

    
		GraphWriter gw = new GraphWriter(cc);

		String pathToResultDirectory = args[4];

		long startTime = System.currentTimeMillis();
		ArrayList<HashSet<VertexOfDualGraph>> partitionResultForFaces = partitioning.partition(preparedGraph, maxSumVerticesWeight);
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
		HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph = preparation.getComparisonForDualGraph();
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
		List<List<Vertex>> bounds = new ArrayList<>();

		for (int i = 0; i < partitionResultForFaces.size(); i++) {
			partitionResult.add(new HashSet<>());
			for (VertexOfDualGraph face : partitionResultForFaces.get(i)) {
				partitionResult.get(i).addAll(comparisonForDualGraph.get(face).getVerticesOfFace());
			}
			bounds.add(BoundSearcher.findBound(graph, partitionResultForFaces.get(i), comparisonForDualGraph));
		}

		gw.printDualGraphToFile(preparedGraph, dualVertexToPartNumber, partitionResultForFaces.size(), outputDirectory + pathToResultDirectory, "dual.txt", true);
		PartitionWriter pw = new PartitionWriter();
		pw.savePartitionToDirectory(partitioning, partitioning.bp ,outputDirectory + pathToResultDirectory, partitionResultForFaces, true, partitioningTime);
		pw.printBound(bounds, outputDirectory + pathToResultDirectory, true);
    
	}

}