import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import graph.*;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import addingPoints.LocalizationPoints;
import graphPreparation.GraphPreparation;
import partitioning.BalancedPartitioning;
import partitioning.balancing.Balancer;
import partitioning.entities.Algorithm;
import readWrite.CoordinateConversion;
import readWrite.GraphReader;
import readWrite.GraphWriter;
import readWrite.PartitionWriter;
import readWrite.PointsReader;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "graph-partitioning", mixinStandardHelpOptions = true, version = "1.0",
        description = "Application for balanced partitioning of planar graphs")
public class Main implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final String BASE_DIRECTORY = "src" + File.separatorChar + "main" + File.separatorChar;
    private static final String RESOURCES_DIRECTORY = BASE_DIRECTORY + "resources" + File.separatorChar;
    private static final String OUTPUT_DIRECTORY = BASE_DIRECTORY + "output" + File.separatorChar;

    @Parameters(index = "0", description = "Algorithm name (IF, BUP, BUS)")
    private Algorithm algorithmName;

    @Parameters(index = "1", description = "Path to graph file (from resources/)")
    private String pathToFile;

    @Parameters(index = "2", description = "Path to buildings file (from resources/)")
    private String pathToPointsFile;

    @Parameters(index = "3", description = "Maximum sum of vertices weight")
    private int maxSumVerticesWeight;

    @Parameters(index = "4", description = "Maximum region radius in meters")
    private int maxRegionRadiusMeters;

    @Parameters(index = "5", description = "Output directory name")
    private String pathToResultDirectory;

    @Option(names = {"-p", "--param"}, defaultValue = "0.25",
            description = "Partition parameter (default: ${DEFAULT-VALUE})")
    private double partitionParameter;

    @Override
    public void run() throws RuntimeException {
        BalancedPartitioning partitioning = Algorithm.getBalancedPartitioningByAlgorithmName(
                algorithmName,
                partitionParameter
        );

        Graph<Vertex> graph = new Graph<>();
        Graph<Vertex> geodeticGraph = new Graph<>();

        try {
            GraphReader geodeticgr = new GraphReader();
            geodeticgr.readGraphFromFile(geodeticGraph, RESOURCES_DIRECTORY + pathToFile, false);
        } catch (Exception e) {
            throw new RuntimeException("Can't read graph from file: " + e.getMessage());
        }

        CoordinateConversion cc = new CoordinateConversion(geodeticGraph.getEdges().keySet());
        try {
            GraphReader gr = new GraphReader(cc);
            gr.readGraphFromFile(graph, RESOURCES_DIRECTORY + pathToFile, true);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Can't read graph from file: " + e.getMessage());
        }

        graph = graph.getLargestConnectedComponent();

        GraphPreparation preparation = new GraphPreparation(false, false);

        Graph<VertexOfDualGraph> preparedGraph;
        try {
            preparedGraph = preparation.prepareGraph(graph, 1, OUTPUT_DIRECTORY, cc);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (VertexOfDualGraph v : preparedGraph.verticesArray()) {
            Assertions.assertNotNull(v.getVerticesOfFace());
        }

        List<Vertex> weightedVertices;

        try {
            PointsReader pr = new PointsReader(cc);
            weightedVertices = pr.readWeightedPoints(RESOURCES_DIRECTORY + pathToPointsFile, true);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Can't read points from file: " + e.getMessage());
        }

        LocalizationPoints lp = new LocalizationPoints(new HashSet<>(weightedVertices));
        HashMap<VertexOfDualGraph, ArrayList<Vertex>> faceToVertices = lp.findFacesForPoints(preparedGraph);

        for (VertexOfDualGraph v: preparedGraph.verticesArray()) {
            v.setWeight(0);
            if (faceToVertices.containsKey(v)) {
                v.setWeight(faceToVertices.get(v).stream().mapToDouble(Vertex::getWeight).sum());
            }
        }

        partitioning.bp.extractBigVertices(preparedGraph, maxSumVerticesWeight);

        GraphWriter gw = new GraphWriter(cc);

        long startTime = System.currentTimeMillis();

        HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph = preparation.getComparisonForDualGraph();
        List<Set<VertexOfDualGraph>> partitionResultForFaces = partitioning.partition(graph, comparisonForDualGraph, preparedGraph, maxSumVerticesWeight);
        for (Set<VertexOfDualGraph> hs : partitionResultForFaces) {
            for (VertexOfDualGraph v : hs) {
                Assertions.assertNotNull(v.getVerticesOfFace());
            }
        }
        HashMap<VertexOfDualGraph, Integer> dualVertexToPartNumber = partitioning.dualVertexToPartNumber();
        for (Set<VertexOfDualGraph> hs : partitionResultForFaces) {
            for (VertexOfDualGraph v : hs) {
                Assertions.assertNotNull(v.getVerticesOfFace());
            }
        }
        Graph<PartitionGraphVertex> partitionGraph = PartitionGraphVertex.buildPartitionGraph(preparedGraph, partitionResultForFaces, dualVertexToPartNumber);
        Balancer balancer = new Balancer(partitionGraph, preparedGraph, graph, maxSumVerticesWeight, comparisonForDualGraph, OUTPUT_DIRECTORY + pathToResultDirectory);
        partitionResultForFaces = balancer.rebalancing();
        HashMap<VertexOfDualGraph, Integer> newDualVertexToPartNumber = new HashMap<>();
        for (int i = 0; i < partitionResultForFaces.size(); i++) {
            for (VertexOfDualGraph vertex : partitionResultForFaces.get(i)) {
                newDualVertexToPartNumber.put(vertex, i);
            }
        }
        partitionGraph = PartitionGraphVertex.buildPartitionGraph(preparedGraph, partitionResultForFaces, newDualVertexToPartNumber);
        try {
            gw.printGraphToFile(partitionGraph,  OUTPUT_DIRECTORY + pathToResultDirectory, "part_graph.txt", true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        double partitioningTime = ((double)(System.currentTimeMillis() - startTime)) / 1000.0;

        logger.info("Partition size: {}", partitionResultForFaces.size());

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

        logger.info("Number of parts: {}, number of parts with radius > max: {}", partitionResultForFaces.size(), countPartsWithNonFittingRadius);

        List<Point> centers = BalancedPartitioning.calculatePartCenters(partitionResultForFaces);

        try {
            gw.printDualGraphWithWeightsToFile(preparedGraph, OUTPUT_DIRECTORY + pathToResultDirectory, "dual.txt", true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024; // convert to MB
        logger.info("Without GC, used memory: {} MB", usedMemory);
        PartitionWriter pw = new PartitionWriter(cc);
        pw.savePartitionToDirectory(partitioning, partitioning.bp,OUTPUT_DIRECTORY + pathToResultDirectory, partitionResultForFaces, true, partitioningTime, cc.referencePoint, usedMemory);
        try {
            pw.printBound(bounds, OUTPUT_DIRECTORY + pathToResultDirectory, true, cc.referencePoint);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        pw.printPartCenters(centers, OUTPUT_DIRECTORY + pathToResultDirectory, "centers.txt", true, cc.referencePoint);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}