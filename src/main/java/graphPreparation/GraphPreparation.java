package graphPreparation;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;
import readWrite.CoordinateConversion;
import readWrite.GraphWriter;

import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphPreparation {
	private static final Logger logger = LoggerFactory.getLogger(GraphPreparation.class);
	private final boolean isPlanar;
	private final boolean isDual;
	private final HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph;
	public GraphPreparation() {
		this.isPlanar = false;
		this.isDual = false;
		comparisonForDualGraph = new HashMap<>();
	}
	public GraphPreparation(boolean isPlanar, boolean isDual) {
		this.isPlanar = isPlanar;
		this.isDual = isDual;
		comparisonForDualGraph = new HashMap<>();
	}
	
	public HashMap<Vertex, VertexOfDualGraph> getComparisonForDualGraph() {
		return this.comparisonForDualGraph;
	}
	
	public Graph<VertexOfDualGraph> prepareGraph(Graph<Vertex> gph, double inaccuracy) throws IOException {
        long startTime = System.currentTimeMillis();
		logger.info("Number of 0 weight vertex, before correction: {}", gph.countZeroWeightVertices());
		gph.correctVerticesWeight();
        long time1 = System.currentTimeMillis();
        logger.info("Time for correcting vertices weight: {} seconds", (time1 - startTime) / 1000.0);
		logger.info("Number of 0 weight vertex, before sweepLine: {}", gph.countZeroWeightVertices());
		logger.info("Start graph weight: {}", gph.verticesSumWeight());
		
        Assertions.assertTrue(gph.isConnected());
		Graph<Vertex> graph;
        if (!isPlanar) {
            SweepLine sl = new SweepLine(inaccuracy);
            graph = sl.makePlanar(gph);
            gph.replaceWith(graph);
        } else {
            graph = gph;
        }
		
        Assertions.assertTrue(graph.isConnected());

        ArrayList<Vertex> zeroWeight = new ArrayList<>();
		for (Vertex v : graph.getEdges().keySet()) {
			if (v.getWeight() == 0) {
				zeroWeight.add(v);
			}
		}
        long time2 = System.currentTimeMillis();
        logger.info("Time for sweepLine: {} seconds", (time2 - time1) / 1000.0);
		
		logger.info("Number of 0 weight vertex, after sweepLine: {}", gph.countZeroWeightVertices());
		logger.info("After sweepline graph weight: {}", gph.verticesSumWeight());

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
		logger.info("Dual graph weight: {}", dualGraph.verticesSumWeight());
		return dualGraph;
	}
	
}