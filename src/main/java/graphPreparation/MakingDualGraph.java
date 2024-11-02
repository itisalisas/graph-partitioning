package graphPreparation;

import java.util.*;

import graph.*;
import org.junit.jupiter.api.Assertions;

public class MakingDualGraph {
	private HashMap<Vertex, VertexOfDualGraph> comparison;
	public MakingDualGraph() {
		this.comparison = new HashMap<Vertex, VertexOfDualGraph>();
	}

	public HashMap<Vertex, VertexOfDualGraph> getComparison() {
		return this.comparison;
	}
	
	public Graph buildDualGraph(Graph gph) {
		Graph res = new Graph();
		Graph undir = gph.makeUndirectedGraph();
		EdgeOfGraph[] edgesList = undir.edgesArray();
		HashMap<Vertex, Integer> vertexInFaceNumber = gph.initVertexInFaceCounter();
		HashMap<EdgeOfGraph, Vertex> inFace = new HashMap<EdgeOfGraph, Vertex>();
		HashMap<Vertex, TreeSet<EdgeOfGraph>> sortedGraph = undir.arrangeByAngle();
		buildDualVertices(res, inFace, sortedGraph, edgesList, vertexInFaceNumber);
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

	private void buildDualVertices(Graph res, HashMap<EdgeOfGraph, Vertex> inFace,
			HashMap<Vertex, TreeSet<EdgeOfGraph>> sortedGraph, EdgeOfGraph[] edgesList, HashMap<Vertex, Integer> vertexInFaceNumber) {
		ArrayList<Vertex> verticesOfFace = new ArrayList<Vertex>();
		HashSet<EdgeOfGraph> inActualFace = new HashSet<EdgeOfGraph>();
		long vertName = 0;
		for (int i = 0; i < edgesList.length; i++) {
			if (inFace.containsKey(edgesList[i])) {
				//System.out.println("already in partition: " + edgesList[i].getBegin().getName() + "->" + edgesList[i].getEnd().getName());
				continue;
			}
			findFace(verticesOfFace, inActualFace, sortedGraph, edgesList[i], vertexInFaceNumber);
			Assertions.assertTrue(verticesOfFace.size() >= 3);
			vertName++;
			//System.out.print(vertName + " ");
			VertexOfDualGraph vert = new VertexOfDualGraph(vertName, VertexOfDualGraph.findCenter(verticesOfFace, vertName),
					VertexOfDualGraph.sumVertexWeight(verticesOfFace), verticesOfFace);
			res.addVertex(vert);
			comparison.put(vert, vert);
			for (EdgeOfGraph edge : inActualFace) {
				inFace.put(edge, vert);
			}
			verticesOfFace.clear();
			inActualFace.clear();
		}
		//System.out.println("cnt_vert = " + cnt_vert);
		correctFacesWeight(res, sortedGraph, vertexInFaceNumber);
	}

	private void correctFacesWeight(Graph res, HashMap<Vertex, TreeSet<EdgeOfGraph>> sortedGraph,
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

		//verticesOfFace.add(prev);
		vertexInFaceNumber.put(prev, vertexInFaceNumber.get(prev) + 1);
		//verticesOfFace.add(begin);
		vertexInFaceNumber.put(begin, vertexInFaceNumber.get(begin) + 1);
		faceWeight += prev.getWeight();
		faceWeight += begin.getWeight();
		inActualFace.add(firstEdge);
		EdgeOfGraph actualEdge = null;
		//sortedGraph.get(prev).remove(firstEdge);
		do {
			//System.out.println("begin = " + begin.getName() + ", prev = " + prev.getName());
			if (sortedGraph.get(begin).isEmpty()) {
				//System.out.println("end");
				//System.out.println("empty vertex");
				return;
			}
			EdgeOfGraph back = new EdgeOfGraph(begin, prev, begin.getLength(prev));
			actualEdge = sortedGraph.get(begin).higher(back);
			/*
			for (EdgeOfGraph u : sortedGraph.get(begin)) {
				System.out.println("e: " + u.getBegin().getName() + " -> " + u.getEnd().getName());
			}
			 */
			if (actualEdge == null) {
				actualEdge = sortedGraph.get(begin).first();
			}
			prev = new Vertex(actualEdge.getBegin());
			begin = new Vertex(actualEdge.getEnd());
			verticesOfFace.add(begin);
			vertexInFaceNumber.put(begin, vertexInFaceNumber.get(begin) + 1);
			faceWeight = faceWeight + begin.getWeight();
			inActualFace.add(actualEdge);
			// sortedGraph.get(prev).remove(actualEdge);
			actualEdge = null;
		} while (!(begin.equals(firstEdge.getEnd()) && prev.equals(firstEdge.getBegin())));
		// System.out.println("begin = " + begin.getName() + ", prev = " + prev.getName());
	}


	public void removeExternalFace(Graph dualGraph) {
		Vertex leftTop = null;
		Vertex rightBottom = null;

		for (Vertex dualVertex : dualGraph.verticesArray()) {
			for (Vertex v : getComparison().get(dualVertex).getVerticesOfFace()) {
				Point point = v.getPoint();
				if (leftTop == null || (point.getX() < leftTop.getPoint().getX() ||
						(point.getX() == leftTop.getPoint().getX() && point.getY() > leftTop.getPoint().getY()))) {
					leftTop = v;
				}
				if (rightBottom == null || (point.getX() > rightBottom.getPoint().getX() ||
						(point.getX() == rightBottom.getPoint().getX() && point.getY() < rightBottom.getPoint().getY()))) {
					rightBottom = v;
				}
			}
		}

		if (leftTop == null || rightBottom == null) {
			throw new RuntimeException("Couldn't find external vertices");
		}

		// System.out.println("left top = " + leftTop.getName() + ", right bottom = " + rightBottom.getName());

		Vertex externalFaceVertex = null;

		for (Vertex dualVertex : dualGraph.verticesArray()) {
			if (comparison.get(dualVertex).getVerticesOfFace().contains(leftTop) &&
					comparison.get(dualVertex).getVerticesOfFace().contains(rightBottom)) {
				externalFaceVertex = dualVertex;
				break;
			}
		}

		if (externalFaceVertex == null) {
			throw new RuntimeException("Couldn't find external face");
		}

		dualGraph.deleteVertex(externalFaceVertex);
		comparison.remove(externalFaceVertex);
	}
}
