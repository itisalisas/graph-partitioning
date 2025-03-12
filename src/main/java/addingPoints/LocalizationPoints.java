package addingPoints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;
import graphPreparation.SweepLine;

public class LocalizationPoints {
	private HashSet<Vertex> newVertices;
	public LocalizationPoints(HashSet<Vertex> nV) {
		newVertices = nV;
	}
	public HashMap<Vertex, VertexOfDualGraph> findFacesForPoints(Graph<VertexOfDualGraph> dualGraph) {
		//change to func for find diagList and returnFromSimplification
		ArrayList<EdgeOfGraph<Vertex>> diagList = new ArrayList<>();
		HashMap<EdgeOfGraph<Vertex>, VertexOfDualGraph> returnFromSimplification = new HashMap<>();
		CoordinateConstraintsForFace coordConst = null;
		EdgeOfGraph<Vertex> diagonal = null;
		//int tmp = 0;
		for (VertexOfDualGraph ver : dualGraph.getEdges().keySet()) {
			coordConst = new CoordinateConstraintsForFace(ver.getVerticesOfFace());
			diagonal = new EdgeOfGraph<Vertex>(new Vertex(0, coordConst.getMinX(), coordConst.getMinY()),
					new Vertex(0, coordConst.getMaxX(), coordConst.getMaxY()), 0);

			//System.out.println(ver.getName() + " " + diagonal.getBegin().getX() + " " + diagonal.getBegin().getY() + " " + diagonal.getEnd().getX() + " " + diagonal.getEnd().getY());
			diagList.add(diagonal);
			//tmp++;
			returnFromSimplification.put(diagonal, ver);
		}
		SweepLine sp = new SweepLine();
		HashMap<Vertex, VertexOfDualGraph> ans = sp.findFacesOfVertices(diagList, returnFromSimplification, this.newVertices);
		return ans;	
	}
}
