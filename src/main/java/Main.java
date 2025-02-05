import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

		Graph<Vertex> graph = new Graph<Vertex>();

		try {
			GraphReader gr = new GraphReader();
			gr.readGraphFromFile(graph, resourcesDirectory + pathToFile);
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

		Graph<VertexOfDualGraph> preparedGraph = preparation.prepareGraph(graph, 0.0000001);

		for (VertexOfDualGraph v : preparedGraph.verticesArray()) {
			Assertions.assertNotNull(v.getVerticesOfFace());
		}

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
		Graph<PartitionGraphVertex> partitionGraph = PartitionGraphVertex.buildPartitionGraph(preparedGraph, partitionResultForFaces, dualVertexToPartNumber);
		System.err.println("smallest vertex before = " + partitionGraph.smallestVertex().getWeight());
		System.err.println("Partition size: " + partitionResultForFaces.size());
		Balancer balancer = new Balancer(partitionGraph, preparedGraph, graph, maxSumVerticesWeight);
		partitionResultForFaces = balancer.rebalancing();
		HashMap<VertexOfDualGraph, Integer> newDualVertexToPartNumber = new HashMap<>();
		for (int i = 0; i < partitionResultForFaces.size(); i++) {
			for (VertexOfDualGraph vertex : partitionResultForFaces.get(i)) {
				newDualVertexToPartNumber.put(vertex, i);
			}
		}
		partitionGraph = PartitionGraphVertex.buildPartitionGraph(preparedGraph, partitionResultForFaces, newDualVertexToPartNumber);
		System.err.println("smallest vertex after = " + partitionGraph.smallestVertex().getWeight());
		gw.printGraphToFile(partitionGraph,  outputDirectory + pathToResultDirectory, "part_graph.txt");

		System.out.println("Partition size: " + partitionResultForFaces.size());

		ArrayList<HashSet<Vertex>> partitionResult = new ArrayList<HashSet<Vertex>>();
		HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph = preparation.getComparisonForDualGraph();
		List<List<Vertex>> bounds = new ArrayList<>();

		long startBoundTime = System.currentTimeMillis();

		for (int i = 0; i < partitionResultForFaces.size(); i++) {
			partitionResult.add(new HashSet<Vertex>());
			for (VertexOfDualGraph face : partitionResultForFaces.get(i)) {
				partitionResult.get(i).addAll(comparisonForDualGraph.get(face).getVerticesOfFace());
			}
			bounds.add(BoundSearcher.findBound(graph, partitionResultForFaces.get(i), comparisonForDualGraph));
			// System.out.println("Added " + (i + 1) + " bound");
		}

		long endBoundTime = System.currentTimeMillis();
		System.out.println("bound search: " + ((double) (endBoundTime - startBoundTime)) / 1000 + " sec");

		List<Vertex> graphBoundEnd = BoundSearcher.findConvexHull(
				partitionResult.stream()
						.flatMap(Set::stream)
						.collect(Collectors.toList())
		);

		gw.printDualGraphToFile(preparedGraph, newDualVertexToPartNumber, partitionResultForFaces.size(), outputDirectory + pathToResultDirectory, "dual.txt");
		// partitioning.savePartitionToDirectory(outputDirectory + pathToResultDirectory, partitionResult);
		PartitionWriter pw = new PartitionWriter();
		pw.savePartitionToDirectory(partitioning, partitioning.bp ,outputDirectory + pathToResultDirectory, partitionResultForFaces);
		pw.printBound(bounds, outputDirectory + pathToResultDirectory);
		// partitioning.printHull(graphBoundEnd, outputDirectory + pathToResultDirectory, "end_bound.txt");
		/*
		ArrayList<HashSet<VertexOfDualGraph>> partitionResultForFacesKahip = new ArrayList<>();

		Scanner sc = new Scanner(new File("./kahip_prepare/kahip_partition.txt"));
        int n = Integer.parseInt(sc.nextLine().trim());
        
        // Временное хранилище для сбора информации о частях
        Map<Integer, HashSet<VertexOfDualGraph>> tempMap = new HashMap<>();
        
        // Чтение данных о разбиении
        while(sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if(line.isEmpty()) continue;
            
            String[] parts = line.split(" ");
            int oldName = Integer.parseInt(parts[0]);
            int part = Integer.parseInt(parts[1]);
            
            // Получаем вершину по имени
            VertexOfDualGraph vertex = preparedGraph.getVertexByName(oldName);
            
            // Добавляем в соответствующую часть
            tempMap.computeIfAbsent(part, k -> new HashSet<>()).add(vertex);
        }
        sc.close();
        
        // Определение максимального номера части
        int maxPart = tempMap.keySet().stream()
            .max(Comparator.naturalOrder())
            .orElse(-1);
        
        // Заполнение результирующего списка
        for(int i = 0; i <= maxPart; i++) {
            partitionResultForFacesKahip.add(tempMap.getOrDefault(i, new HashSet<>()));
        }

		List<List<Vertex>> boundsKahip = new ArrayList<>();

		for (int i = 0; i < partitionResultForFacesKahip.size(); i++) {
			//boundsKahip.add(BoundSearcher.findBound(graph, partitionResultForFacesKahip.get(i), comparisonForDualGraph));
			// System.out.println("Added " + (i + 1) + " bound");
		}

		// pw.printBound(boundsKahip, outputDirectory + pathToResultDirectory + "/kahip");
		*/

	}

}