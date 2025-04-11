import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Assertions;

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

public class Main {

	public static void main(String[] args) throws RuntimeException, IOException {

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
			maxSumVerticesWeight = Integer.parseInt(args[2]);
		} catch (NumberFormatException e) {
			throw new RuntimeException("Can't parse max sum vertices weight");
		}

		GraphPreparation preparation = new GraphPreparation(false, false);

		Graph<VertexOfDualGraph> preparedGraph = preparation.prepareGraph(graph, 1, outputDirectory, cc);

		for (VertexOfDualGraph v : preparedGraph.verticesArray()) {
			Assertions.assertNotNull(v.getVerticesOfFace());
		}
    
		GraphWriter gw = new GraphWriter(cc);

		String pathToResultDirectory = args[3];

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