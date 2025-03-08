package graphPreparation;

import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;
import readWrite.GraphWriter;

import org.junit.jupiter.api.Assertions;

public class GraphPreparation {
	private boolean isPlanar;
	private boolean isDual;
	private HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph;
	public GraphPreparation() {
		this.isPlanar = false;
		this.isDual = false;
		comparisonForDualGraph = new HashMap<Vertex, VertexOfDualGraph>();
	}
	public GraphPreparation(boolean isPlanar, boolean isDual) {
		this.isPlanar = isPlanar;
		this.isDual = isDual;
		comparisonForDualGraph = new HashMap<Vertex, VertexOfDualGraph>();
	}
	
	public HashMap<Vertex, VertexOfDualGraph> getComparisonForDualGraph() {
		return this.comparisonForDualGraph;
	}
	
	public Graph<VertexOfDualGraph> prepareGraph(Graph<Vertex> gph, double inaccuracy, String outputDirectory) throws IOException {
		System.out.println("Number of 0 weight vertex, before correction: " + gph.countZeroWeightVertices());
		gph.correctVerticesWeight();
		System.out.println("Number of 0 weight vertex, before sweepLine: " + gph.countZeroWeightVertices());
		System.out.println("Start graph weight: " + gph.verticesSumWeight());
		
		// draw swepline
		GraphWriter gw = new GraphWriter();
		Path dirPath = Paths.get("./SweepLine");
		try {
            Files.createDirectories(dirPath); 
        } catch (IOException e) {
            e.printStackTrace(); 
        }
		gw.printGraphToFile(gph, outputDirectory + "//SweepLine", "beforeSweepLine.txt", true);
		
        Assertions.assertTrue(gph.isConnected());
		Graph<Vertex> graph = null;
		if (!isPlanar) {
			SweepLine sl = new SweepLine(inaccuracy);
			graph = sl.makePlanar(gph);
		} else {
			graph = gph;
		}
		
        Assertions.assertTrue(graph.isConnected());
		// draw swepline
		gw.printGraphToFile(graph, outputDirectory + "//SweepLine", "afterSweepLine.txt", true);
	
		ArrayList<Vertex> zeroWeight = new ArrayList<Vertex>();
		for (Vertex v : graph.getEdges().keySet()) {
			if (v.getWeight() == 0) {
				zeroWeight.add(v);
				//System.out.println(v.name);	 
			}
		}
		
		// draw swepline
		File file = new File(outputDirectory + "SweepLine\\0vertex.txt");
		file.delete();
		file = new File(outputDirectory + "SweepLine\\0vertex.txt");
		FileWriter out = new FileWriter(file, true);
		out.write(zeroWeight.size() + "\n");
		out.close();
		gw.printVerticesToFile(zeroWeight, file, true);
		
		System.out.println("Number of 0 weight vertex, after sweepLine: " + gph.countZeroWeightVertices());
		System.out.println("After sweepline graph weight: " + gph.verticesSumWeight());

		
		ArrayList<Vertex> cicle = new ArrayList<>();
		cicle.add(new Vertex(22, 250.919893, -402.661115, 0));
		cicle.add(new Vertex(1570463146l, 435.882832, -448.207049, 21.0));
		// cicle.add(new Vertex(1589052150l, 27.387946870432913, -40.681749091539324, 15.0));
		// cicle.add(new Vertex(4179710957l, 37.49785371845775, 1.5981101882654032, 31.0));
		GraphWriter gw2 = new GraphWriter();
		File file2 = new File(outputDirectory + "MakingDual\\cicleVertex.txt");
		file2.delete();
		file2 = new File(outputDirectory + "MakingDual\\cicleVertex.txt");
		FileWriter out2 = new FileWriter(file2, true);
		out2.write(cicle.size() + "\n");
		out2.close();
		gw2.printVerticesToFile(cicle, file2, true);
		


		MakingDualGraph dg = new MakingDualGraph();
		Graph<VertexOfDualGraph> dualGraph = dg.buildDualGraph(graph);
        Assertions.assertTrue(graph.isConnected());
		for (VertexOfDualGraph v : dualGraph.verticesArray()) {
			Assertions.assertNotNull(v.getVerticesOfFace());
		}
		Assertions.assertTrue(dualGraph.isConnected());
		dg.removeExternalFace(dualGraph);
		Assertions.assertTrue(dualGraph.isConnected());
		comparisonForDualGraph.clear();
		comparisonForDualGraph.putAll(dg.getComparison());
		System.out.println("Dual graph weight: " + dualGraph.verticesSumWeight());
		return dualGraph;
	}
	
}
