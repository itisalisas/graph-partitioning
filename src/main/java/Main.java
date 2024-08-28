
import graph.*;
import graphPreparation.GraphPreparation;
import partitioning.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;

public class Main {

	public static void main(String[] args) throws RuntimeException, FileNotFoundException {

		if (args.length < 4) {
			throw new RuntimeException("Use : <algorithm-name> <path-to-file> <max-sum-vertices-weight> <output-directory> [param] ...");
		}

		String algorithmName = args[0];
		String pathToFile = args[1];

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
			graph.readGraphFromFile("src\\main\\resources\\" + pathToFile);
		} catch (Exception e) {
			throw new RuntimeException("Can't read graph from file");
		}

		int maxSumVerticesWeight;
		try {
			maxSumVerticesWeight = Integer.parseInt(args[2]);
		} catch (NumberFormatException e) {
			throw new RuntimeException("Can't parse max sum vertices weight");
		}

		GraphPreparation preparation = new GraphPreparation();
		Graph preparedGraph = preparation.prepareGraph(graph, 0.00000000000001);
		
		ArrayList<HashSet<Vertex>> partitionResultForFaces;

		partitionResultForFaces = partitioning.partition(preparedGraph, maxSumVerticesWeight);
		
		ArrayList<HashSet<Vertex>> partitionResult = new ArrayList<HashSet<Vertex>>();
		HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph = preparation.getComparisonForDualGraph();
		for (int i = 0; i < partitionResultForFaces.size(); i++) {
			partitionResult.add(new HashSet<Vertex>());
			for (Vertex face : partitionResultForFaces.get(i)) {
				partitionResult.get(i).addAll(comparisonForDualGraph.get(face).getVerticesOfFace());
			}
		}

		String outputDirectory = args[3];

		File outputDirectoryFile = new File("src\\main\\output\\" + outputDirectory);
		if (!outputDirectoryFile.exists()) {
			if (!outputDirectoryFile.mkdirs()) {
				throw new RuntimeException("Can't create output directory");
			}
		}

		for (int i = 0; i < partitionResult.size(); i++) {
			File outputFile = new File("src\\main\\output\\" + outputDirectory + File.separator + "partition_" + i + ".txt");
			try {
				outputFile.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException("Can't create output file");
			}
			try {
				graph.writePartitionToFile(partitionResult.get(i), outputFile);
			} catch (Exception e) {
				throw new RuntimeException("Can't write partition to file");
			}
		}


	}

}
