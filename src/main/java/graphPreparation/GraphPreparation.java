package graphPreparation;

import java.util.HashMap;

import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;

public class GraphPreparation {
	private boolean isPlanar;
	private boolean isDual;
	private long faceMaxWreight;
	private HashMap<Vertex, VertexOfDualGraph> comparisonForDualGraph;
	public GraphPreparation() {
		this.isPlanar = false;
		this.isDual = false;
		this.faceMaxWreight = 100;
		comparisonForDualGraph = new HashMap<Vertex, VertexOfDualGraph>();
	}
	public GraphPreparation(boolean isPlanar, boolean isDual, long faceMaxWreight) {
		this.isPlanar = isPlanar;
		this.isDual = isDual;
		this.faceMaxWreight = faceMaxWreight;
		comparisonForDualGraph = new HashMap<Vertex, VertexOfDualGraph>();
	}
	
	public HashMap<Vertex, VertexOfDualGraph> getComparisonForDualGraph() {
		return this.comparisonForDualGraph;
	}
	
	public Graph prepareGraph(Graph gph, double inaccuracy) {
		gph.correctVerticesWeight();
		System.out.println("Start graph weight: " + gph.verticesSumWeight());
		if (!isPlanar) {
			SweepLine sl = new SweepLine(inaccuracy);
			sl.makePlanar(gph);
		}
		System.out.println("After sweepline graph weight: " + gph.verticesSumWeight());
		if (!isDual) {
			MakingDualGraph dg = new MakingDualGraph();
			Graph dualGraph = dg.buildDualGraph(gph, faceMaxWreight);
			comparisonForDualGraph.clear();
			comparisonForDualGraph.putAll(dg.getComparison());
			System.out.println("Dual graph weight: " + dualGraph.verticesSumWeight());
			return dualGraph;
		}

		return gph;	
	}
	
}
