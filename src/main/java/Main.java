import graph.*;
import graph.Vertex;
import partitioning.BalancedPartitioning;
import partitioning.InertialFlowPartitioning;
import graphPreparation.GraphPreparation;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

	public static void main(String[] args) throws RuntimeException, FileNotFoundException {

		if (args.length < 4) {
			throw new RuntimeException("Use : <algorithm-name> <path-to-file> <max-sum-vertices-weight> <output-directory-name> [param]");
		}

		String algorithmName = args[0];
		String pathToFile = args[1];

		String resourcesDirectory = "src/main/resources/".replace('/', File.separatorChar);
		String outputDirectory = "src/main/output/".replace('/', File.separatorChar);

		BalancedPartitioning partitioning;

		if (algorithmName.equals("IF")) {
			if (args.length < 5) {
				partitioning = new BalancedPartitioning(new InertialFlowPartitioning());
			} else {
				double partitionParameter;
				try {
					partitionParameter = Double.parseDouble(args[4]);
				} catch (NumberFormatException e) {
					throw new RuntimeException("Can't parse partition parameter");
				}
				partitioning = new BalancedPartitioning(new InertialFlowPartitioning(partitionParameter));
			}
		} else {
			throw new RuntimeException("No such partition algorithm");
		}

		Graph graph = new Graph();

		try {
			graph.readGraphFromFile(resourcesDirectory + pathToFile);
		} catch (Exception e) {
			throw new RuntimeException("Can't read graph from file: " + e.getMessage());
		}

		int maxSumVerticesWeight;
		try {
			maxSumVerticesWeight = Integer.parseInt(args[2]);
		} catch (NumberFormatException e) {
			throw new RuntimeException("Can't parse max sum vertices weight");
		}


		System.out.println("Graph weight before: " + graph.verticesSumWeight());
		
		GraphPreparation preparation = new GraphPreparation();
		Graph preparedGraph = preparation.prepareGraph(graph, 0.00000000000001);

		ArrayList<HashSet<Vertex>> partitionResultForFaces = partitioning.partition(preparedGraph, maxSumVerticesWeight);


		ArrayList<HashSet<Vertex>> partitionResult = new ArrayList<HashSet<Vertex>>();
		HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph = preparation.getComparisonForDualGraph();
		for (int i = 0; i < partitionResultForFaces.size(); i++) {
			partitionResult.add(new HashSet<Vertex>());
			for (Vertex face : partitionResultForFaces.get(i)) {
				partitionResult.get(i).addAll(comparisonForDualGraph.get(face).getVerticesOfFace());
			}
		}

		List<Vertex> bound = Graph.findBound(partitionResult);

		String pathToResultDirectory = args[3];

		// partitioning.savePartitionToDirectory(outputDirectory + pathToResultDirectory, partitionResult);
		partitioning.savePartitionToDirectory(outputDirectory + pathToResultDirectory, partitionResultForFaces);
		partitioning.printBound(bound, outputDirectory + pathToResultDirectory);

	}

}