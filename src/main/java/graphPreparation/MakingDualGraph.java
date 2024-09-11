package graphPreparation;

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
	
	public Graph buildDualGraph(Graph gph, long maxFaceWeight) {
		Graph res = new Graph();
		Graph undir = gph.makeUndirectedGraph();
		EdgeOfGraph[] edgesList = undir.edgesArray();
		HashMap<EdgeOfGraph, Vertex> inFace = new HashMap<EdgeOfGraph, Vertex>();
		HashMap<Vertex, TreeSet<EdgeOfGraph>> sortedGraph = arrangeByAngle(undir);
		buildDualVerteices(res, inFace, sortedGraph, edgesList,maxFaceWeight);
		addDualEdges(res, inFace);
		return res;
	}

	private void addDualEdges(Graph res, HashMap<EdgeOfGraph, Vertex> inFace) {
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
			res.getEdges().get(inFace.get(edge)).put(inFace.get(back), new Edge(oldLength + edge.getLength() / 2));
			res.getEdges().get(inFace.get(back)).put(inFace.get(edge), new Edge(oldLength + edge.getLength() / 2));
		}

	}

	private void buildDualVerteices(Graph res, HashMap<EdgeOfGraph, Vertex> inFace,
			HashMap<Vertex, TreeSet<EdgeOfGraph>> sortedGraph, EdgeOfGraph[] edgesList, long maxFaceWeight) {
		HashSet<Vertex> verticesOfFace = new HashSet<Vertex>();
		HashSet<EdgeOfGraph> inActualFace = new HashSet<EdgeOfGraph>();
		long vertName = 0;
		for (int i = 0; i < edgesList.length; i++) {
			if (inFace.containsKey(edgesList[i])) {
				continue;
			}
			findFace(verticesOfFace, inActualFace, sortedGraph, edgesList[i], maxFaceWeight);
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
	}

	private void findFace(HashSet<Vertex> verticesOfFace, HashSet<EdgeOfGraph> inActualFace,
			HashMap<Vertex, TreeSet<EdgeOfGraph>> sortedGraph, EdgeOfGraph firstEdge, long maxFaceWeight) {
		long faceWeight = 0;
		Vertex prev = new Vertex(firstEdge.getBegin());
		Vertex begin = new Vertex(firstEdge.getEnd());
		verticesOfFace.add(prev);
		verticesOfFace.add(begin);
		faceWeight = faceWeight + prev.getWeight();
		faceWeight = faceWeight + begin.getWeight();
		inActualFace.add(firstEdge);
		EdgeOfGraph actualEdge = null;
		sortedGraph.get(prev).remove(firstEdge);
		while (!(begin.equals(firstEdge.getBegin()) && !prev.equals(firstEdge.getEnd())) && faceWeight < maxFaceWeight) {
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
			faceWeight = faceWeight + begin.getWeight();
			inActualFace.add(actualEdge);
			sortedGraph.get(prev).remove(actualEdge);
			actualEdge = null;
		}

	}

	private HashMap<Vertex, TreeSet<EdgeOfGraph>> arrangeByAngle(Graph gph) {
		Comparator<EdgeOfGraph> edgeComp = new Comparator<EdgeOfGraph>() {
			@Override
			public int compare(EdgeOfGraph o1, EdgeOfGraph o2) {
				double a1 = o1.getCorner();
				double a2 = o2.getCorner();
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
