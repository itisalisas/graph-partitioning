package graphPreparation;

import java.util.HashMap;

import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;

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
	
	public Graph prepareGraph(Graph gph, double inaccuracy) {
		if (!isPlanar) {
			SweepLine sl = new SweepLine(inaccuracy);
			sl.makePlanar(gph);
		}
		if (!isDual) {
			MakingDualGraph dg = new MakingDualGraph();
			Graph dualGraph = dg.buildDualGraph(gph);
			comparisonForDualGraph.clear();
			comparisonForDualGraph.putAll(dg.getComparison());
			return dualGraph;
		}
		return gph;	
	}
	
}
