import graph.*;
import graph.Vertex;
import partitioning.BalancedPartitioning;
import partitioning.InertialFlowPartitioning;
import graphPreparation.GraphPreparation;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

	public static void main(String[] args) throws RuntimeException {

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
		List<Vertex> bound = new ArrayList<>();
		Graph preparedGraph = preparation.prepareGraph(graph, 1e-9);

		ArrayList<HashSet<Vertex>> partitionResultForFaces = partitioning.partition(preparedGraph, maxSumVerticesWeight);
		System.out.println("Partition size: " + partitionResultForFaces.size());

		ArrayList<HashSet<Vertex>> partitionResult = new ArrayList<HashSet<Vertex>>();
		HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph = preparation.getComparisonForDualGraph();
		List<List<Vertex>> bounds = new ArrayList<>();

		for (int i = 0; i < partitionResultForFaces.size(); i++) {
			partitionResult.add(new HashSet<Vertex>());
			for (Vertex face : partitionResultForFaces.get(i)) {
				partitionResult.get(i).addAll(comparisonForDualGraph.get(face).getVerticesOfFace());
			}
			bounds.add(BoundSearcher.findBound(graph, partitionResultForFaces.get(i), comparisonForDualGraph));
			// System.out.println("Added " + (i + 1) + " bound");
		}

		List<Vertex> graphBoundEnd = BoundSearcher.findConvexHull(
				partitionResult.stream()
						.flatMap(Set::stream)
						.collect(Collectors.toList())
		);

		String pathToResultDirectory = args[3];

		// partitioning.savePartitionToDirectory(outputDirectory + pathToResultDirectory, partitionResult);
		partitioning.savePartitionToDirectory(outputDirectory + pathToResultDirectory, partitionResultForFaces);
		partitioning.printBound(bounds, outputDirectory + pathToResultDirectory);
		// partitioning.printHull(graphBoundEnd, outputDirectory + pathToResultDirectory, "end_bound.txt");
	}

}