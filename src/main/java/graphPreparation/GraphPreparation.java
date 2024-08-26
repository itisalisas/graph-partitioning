package graphPreparation;

import graph.Graph;
import makingDualGraph.MakingDualGraph;
import sweepLine.SweepLine;

public class GraphPreparation {
	private boolean isPlanar;
	private boolean isDual;
	public GraphPreparation() {
		this.isPlanar = false;
		this.isDual = false;
	}
	public GraphPreparation(boolean isPlanar, boolean isDual) {
		this.isPlanar = isPlanar;
		this.isDual = isDual;
	}
	
	public Graph prepareGraph(Graph gph, double inaccuracy) {
		if (!isPlanar) {
			SweepLine sl = new SweepLine(inaccuracy);
			sl.makePlanar(gph);
		}
		if (!isDual) {
			MakingDualGraph dg = new MakingDualGraph();
			return dg.buildDualGraph(gph);
		}
		return gph;	
	}
	
}
