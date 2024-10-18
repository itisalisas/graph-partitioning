package graphPreparation;

import java.util.HashMap;

import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;

public class GraphPreparation {
	private boolean isPlanar;
	private HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph;
	public GraphPreparation() {
		this.isPlanar = false;
		comparisonForDualGraph = new HashMap<Vertex, VertexOfDualGraph>();
	}
	public GraphPreparation(boolean isPlanar, boolean isDual) {
		this.isPlanar = isPlanar;
		comparisonForDualGraph = new HashMap<Vertex, VertexOfDualGraph>();
	}
	
	public HashMap<Vertex, VertexOfDualGraph> getComparisonForDualGraph() {
		return this.comparisonForDualGraph;
	}
	
	public Graph<VertexOfDualGraph> prepareGraph(Graph<Vertex> gph, double inaccuracy) {
		System.out.println("Number of 0 weight vertex, before correction: " + gph.countZeroWeightVertices());
		gph.correctVerticesWeight();
		System.out.println("Number of 0 weight vertex, before sweepLine: " + gph.countZeroWeightVertices());
		System.out.println("Start graph weight: " + gph.verticesSumWeight());
		if (!isPlanar) {
			SweepLine sl = new SweepLine(inaccuracy);
			sl.makePlanar(gph);
		}
		System.out.println("Number of 0 weight vertex, after sweepLine: " + gph.countZeroWeightVertices());
		System.out.println("After sweepline graph weight: " + gph.verticesSumWeight());
		
		MakingDualGraph dg = new MakingDualGraph();
		Graph<VertexOfDualGraph> dualGraph = dg.buildDualGraph(gph);
		comparisonForDualGraph.clear();
		comparisonForDualGraph.putAll(dg.getComparison());
		System.out.println("Dual graph weight: " + dualGraph.verticesSumWeight());
		return dualGraph;
		
	}
	
}
