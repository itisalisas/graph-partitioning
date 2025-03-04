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
import graphPreparation.SweepLine;

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

		long time1 = System.currentTimeMillis();

		Graph<Vertex> graph = new Graph<>();

		try {
			GraphReader gr = new GraphReader(new CoordinateConversion());
			gr.readGraphFromFile(graph, resourcesDirectory + pathToFile, true);
		} catch (Exception e) {
			throw new RuntimeException("Can't read graph from file: " + e.getMessage());
		}

		long time2 = System.currentTimeMillis();
		System.out.println("time2 - time1 = " + (double) (time2 - time1) / 1000);

		int maxSumVerticesWeight;
		try {
			maxSumVerticesWeight = Integer.parseInt(args[2]);
		} catch (NumberFormatException e) {
			throw new RuntimeException("Can't parse max sum vertices weight");
		}


		System.out.println("Graph weight before: " + graph.verticesSumWeight());

		long time3 = System.currentTimeMillis();
		System.out.println("time3 - time2 = " + (double) (time3 - time2) / 1000);

		GraphPreparation preparation = new GraphPreparation(false, false);

		Graph<VertexOfDualGraph> preparedGraph = preparation.prepareGraph(graph, 1, outputDirectory);

		for (VertexOfDualGraph v : preparedGraph.verticesArray()) {
			Assertions.assertNotNull(v.getVerticesOfFace());
		}

		long time4 = System.currentTimeMillis();
		System.out.println("time4 - time3 = " + (double) (time4 - time3) / 1000);

		GraphWriter gw = new GraphWriter();
		gw.printGraphToFile(preparedGraph, outputDirectory, "for_kahip.graph");
		String pathToResultDirectory = args[3];

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
		long time5 = System.currentTimeMillis();
		System.out.println("time5 - time4 = " + (double) (time5 - time4) / 1000);
		Graph<PartitionGraphVertex> partitionGraph = PartitionGraphVertex.buildPartitionGraph(preparedGraph, partitionResultForFaces, dualVertexToPartNumber);
		System.err.println("smallest vertex before = " + partitionGraph.smallestVertex().getWeight());
		System.err.println("Partition size: " + partitionResultForFaces.size());
		Balancer balancer = new Balancer(partitionGraph, preparedGraph, graph, maxSumVerticesWeight, comparisonForDualGraph, outputDirectory + pathToResultDirectory);
		partitionResultForFaces = balancer.rebalancing();
		HashMap<VertexOfDualGraph, Integer> newDualVertexToPartNumber = new HashMap<>();
		for (int i = 0; i < partitionResultForFaces.size(); i++) {
			for (VertexOfDualGraph vertex : partitionResultForFaces.get(i)) {
				newDualVertexToPartNumber.put(vertex, i);
			}
		}
		partitionGraph = PartitionGraphVertex.buildPartitionGraph(preparedGraph, partitionResultForFaces, newDualVertexToPartNumber);
		System.err.println("smallest vertex after = " + partitionGraph.smallestVertex().getWeight());

		gw.printGraphToFile(partitionGraph,  outputDirectory + pathToResultDirectory, "part_graph.txt", true);
		long time6 = System.currentTimeMillis();
		System.out.println("time6 - time5 = " + (double) (time6 - time5) / 1000);

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

		long time7 = System.currentTimeMillis();
		System.out.println("time7 - time6 = " + (double) (time7 - time6) / 1000);

		gw.printDualGraphToFile(preparedGraph, dualVertexToPartNumber, partitionResultForFaces.size(), outputDirectory + pathToResultDirectory, "dual.txt", true);
		// partitioning.savePartitionToDirectory(outputDirectory + pathToResultDirectory, partitionResult);
		PartitionWriter pw = new PartitionWriter();
		pw.savePartitionToDirectory(partitioning, partitioning.bp ,outputDirectory + pathToResultDirectory, partitionResultForFaces, true);
		pw.printBound(bounds, outputDirectory + pathToResultDirectory, true);
		// partitioning.printHull(graphBoundEnd, outputDirectory + pathToResultDirectory, "end_bound.txt");
    
    
		long time8 = System.currentTimeMillis();
		System.out.println("time8 - time7 = " + (double) (time8 - time7) / 1000);

	}

}