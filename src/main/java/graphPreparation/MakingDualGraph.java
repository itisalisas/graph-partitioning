package graphPreparation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

import graph.Edge;
import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;

public class MakingDualGraph {
	private HashMap<Vertex, VertexOfDualGraph> comparison;
	public MakingDualGraph() {
		this.comparison = new HashMap<Vertex, VertexOfDualGraph>();
	}

	public HashMap<Vertex, VertexOfDualGraph> getComparison() {
		return this.comparison;
	}
	
	public Graph<VertexOfDualGraph> buildDualGraph(Graph<Vertex> gph) {
		Graph<VertexOfDualGraph> res = new Graph<VertexOfDualGraph>();
		Graph<Vertex> undir = gph.makeUndirectedGraph();
		EdgeOfGraph[] edgesList = undir.edgesArray();
		HashMap<Vertex, Integer> vertexInFaceNumber = gph.initVertexInFaceCounter();
		HashMap<EdgeOfGraph, Vertex> inFace = new HashMap<EdgeOfGraph, Vertex>();
		HashMap<Vertex, TreeSet<EdgeOfGraph>> sortedGraph = arrangeByAngle(undir);
		buildDualVerteices(res, inFace, sortedGraph, edgesList, vertexInFaceNumber);
		addDualEdges(res, inFace);
		return res;
	}

	private void addDualEdges(Graph<VertexOfDualGraph> res, HashMap<EdgeOfGraph, Vertex> inFace) {
		EdgeOfGraph back = null;
		double oldLength = 0;
		for (EdgeOfGraph edge : inFace.keySet()) {
			back = new EdgeOfGraph(edge.getEnd(), edge.getBegin(), edge.getLength());
			if (inFace.get(edge).equals((inFace).get(back))) {
				continue;
			}
			oldLength = 0;
			if (res.getEdges().get(inFace.get(edge)).containsKey(inFace.get(back))) {
				oldLength = res.getEdges().get(inFace.get(edge)).get(inFace.get(back)).getLength();
				res.getEdges().get(inFace.get(edge)).remove(inFace.get(back));
				res.getEdges().get(inFace.get(back)).remove(inFace.get(edge));
			}
			res.getEdges().get(inFace.get(edge)).put((VertexOfDualGraph) inFace.get(back), new Edge(oldLength + edge.getLength() / 2));
			res.getEdges().get(inFace.get(back)).put((VertexOfDualGraph) inFace.get(edge), new Edge(oldLength + edge.getLength() / 2));
		}

	}

	private void buildDualVerteices(Graph<VertexOfDualGraph> res, HashMap<EdgeOfGraph, Vertex> inFace,
			HashMap<Vertex, TreeSet<EdgeOfGraph>> sortedGraph, EdgeOfGraph[] edgesList, HashMap<Vertex, Integer> vertexInFaceNumber) {
		ArrayList<Vertex> verticesOfFace = new ArrayList<Vertex>();
		HashSet<EdgeOfGraph> inActualFace = new HashSet<EdgeOfGraph>();
		long vertName = 0;
		for (int i = 0; i < edgesList.length; i++) {
			if (inFace.containsKey(edgesList[i])) {
				continue;
			}
			findFace(verticesOfFace, inActualFace, sortedGraph, edgesList[i], vertexInFaceNumber);
			vertName++;
			//System.out.print(vertName + " ");
			VertexOfDualGraph vert = new VertexOfDualGraph(vertName, VertexOfDualGraph.findCenter(verticesOfFace),
					VertexOfDualGraph.sumVertexWeight(verticesOfFace), verticesOfFace);
			res.addVertex(vert);
			comparison.put(vert, vert);
			for (EdgeOfGraph edge : inActualFace) {
				inFace.put(edge, vert);
			}
			verticesOfFace.clear();
			inActualFace.clear();
		}
		correctFacesWeight(res, sortedGraph, vertexInFaceNumber);
	}

	private void correctFacesWeight(Graph<VertexOfDualGraph> res, HashMap<Vertex, TreeSet<EdgeOfGraph>> sortedGraph,
			HashMap<Vertex, Integer> vertexInFaceNumber) {
		for (Vertex v : res.getEdges().keySet()) {
			v.setWeight(countFaceWeight(v, vertexInFaceNumber));
		}
		
	}

	private double countFaceWeight(Vertex v, HashMap<Vertex, Integer> vertexInFaceNumber) {
		double ans = 0;
		for (Vertex ver : comparison.get(v).getVerticesOfFace()) {
			ans = ans + ver.getWeight() / vertexInFaceNumber.get(ver);
		}
		return ans;
	}

	private void findFace(ArrayList<Vertex> verticesOfFace, HashSet<EdgeOfGraph> inActualFace,
			HashMap<Vertex, TreeSet<EdgeOfGraph>> sortedGraph, EdgeOfGraph firstEdge, HashMap<Vertex, Integer> vertexInFaceNumber) {
		double faceWeight = 0;
		Vertex prev = new Vertex(firstEdge.getBegin());
		Vertex begin = new Vertex(firstEdge.getEnd());
		verticesOfFace.add(prev);
		vertexInFaceNumber.put(prev, vertexInFaceNumber.get(prev) + 1);
		verticesOfFace.add(begin);
		vertexInFaceNumber.put(begin, vertexInFaceNumber.get(begin) + 1);
		faceWeight = faceWeight + prev.getWeight();
		faceWeight = faceWeight + begin.getWeight();
		inActualFace.add(firstEdge);
		EdgeOfGraph actualEdge = null;
		sortedGraph.get(prev).remove(firstEdge);
		while (!(begin.equals(firstEdge.getBegin()) && !prev.equals(firstEdge.getEnd()))) {
			if (sortedGraph.get(begin).isEmpty()) {
				//System.out.println("empty vertex");
				return;
			}
			EdgeOfGraph back = new EdgeOfGraph(begin, prev, begin.getLength(prev));
			actualEdge = sortedGraph.get(begin).higher(back);
			if (actualEdge == null) {
				actualEdge = sortedGraph.get(begin).first();
			}
			prev = new Vertex(actualEdge.getBegin());
			begin = new Vertex(actualEdge.getEnd());
			verticesOfFace.add(begin);
			vertexInFaceNumber.put(begin, vertexInFaceNumber.get(begin) + 1);
			faceWeight = faceWeight + begin.getWeight();
			inActualFace.add(actualEdge);
			sortedGraph.get(prev).remove(actualEdge);
			actualEdge = null;
		}

	}

	private HashMap<Vertex, TreeSet<EdgeOfGraph>> arrangeByAngle(Graph<Vertex> gph) {
		Comparator<EdgeOfGraph> edgeComp = new Comparator<EdgeOfGraph>() {
			@Override
			public int compare(EdgeOfGraph o1, EdgeOfGraph o2) {
				double a1 = o1.angle();
				double a2 = o2.angle();
				return a1 < a2 ? -1 : a1 > a2 ? 1 : 0;
			}
		};
		HashMap<Vertex, TreeSet<EdgeOfGraph>> res = new HashMap<Vertex, TreeSet<EdgeOfGraph>>();
		for (Vertex begin : gph.getEdges().keySet()) {
			res.put(begin, new TreeSet<EdgeOfGraph>(edgeComp));
			for (Vertex end : gph.getEdges().get(begin).keySet()) {
				res.get(begin).add(new EdgeOfGraph(begin, end, gph.getEdges().get(begin).get(end).getLength()));
			}
		}
		return res;
	}
}
