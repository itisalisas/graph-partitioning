package graphPreparation;

import graph.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class GammaAlgorithm {
	public GammaAlgorithm() {
		
	}
	public boolean isPlanar(Graph gph){
		// split for connectivity components
		ArrayList<HashSet<Vertex>> component = gph.splitForConnectedComponents();
		// check each component
		int n = component.size();
		// make undirected
		Graph undirGraph = gph.makeUndirectedGraph();
		for (int i = 0; i < n; i++) {
			if (!componentIsPlanar(undirGraph, component.get(i)))
				return false;
		}
		return true;
	}
	
	private boolean componentIsPlanar(Graph undirGraph, HashSet<Vertex> vertexInComponent) {
		// make new components if there is bridge an check them
		// check component
		// undir - edgesNumber x2
		int edgesNumber = undirGraph.edgesNumberInCompanentUndirGraph(vertexInComponent);
		// component = tree
		if (undirTree(edgesNumber, vertexInComponent.size()))
			return true;
		// cutBridges for new component
		cutBridges(undirGraph, vertexInComponent);
		ArrayList<HashSet<Vertex>> componentsWithoutBridges = makeNewComponentsWithoutBridges(undirGraph,
				vertexInComponent);

		// checking components which connective & not tree & without bridge
		for (int i = 0; i < componentsWithoutBridges.size(); i++) {
			if (!gammaAlgorithm(componentsWithoutBridges.get(i), undirGraph))
				return false;
		}
		return true;
	}
	public boolean undirTree(int edgesNumber, int size) {
		return edgesNumber == (size - 1) * 2;
	}
	private void cutBridges(Graph undirGraph, HashSet<Vertex> vertexInComponent) {
		int timer = 0;
		HashMap<Vertex, Integer> inTime = new HashMap<Vertex, Integer>();
		HashMap<Vertex, Integer> returnTime = new HashMap<Vertex, Integer>();
		HashSet<Vertex> used = new HashSet<Vertex>();
		ArrayList<EdgeOfGraph> bridges = new ArrayList<EdgeOfGraph>();
		for (Vertex begin : vertexInComponent) {
			if (!used.contains(begin)) {
				undirGraph.dfsBridges(vertexInComponent, begin, null, used, timer, inTime, returnTime, bridges);
			}
		}
		for (int i = 0; i < bridges.size(); i++) {
			undirGraph.deleteEdge(bridges.get(i).getBegin(), bridges.get(i).getEnd());
			undirGraph.deleteEdge(bridges.get(i).getEnd(), bridges.get(i).getBegin());
		}

	}

	private ArrayList<HashSet<Vertex>> makeNewComponentsWithoutBridges(Graph undirGraph,
			HashSet<Vertex> vertexInComponent) {
		ArrayList<HashSet<Vertex>> componentsWithoutBridges = new ArrayList<HashSet<Vertex>>();
		HashSet<Vertex> visited = new HashSet<Vertex>();
		HashSet<Vertex> actualComp = new HashSet<Vertex>();
		for (Vertex begin : vertexInComponent) {
			if (visited.contains(begin)) {
				continue;
			} else {
				actualComp.add(begin);
				visited.add(begin);
				dfsCompanentsWithoutBridges(undirGraph, vertexInComponent, begin, actualComp, visited);
				componentsWithoutBridges.add(actualComp);
				actualComp = new HashSet<Vertex>();
			}
		}
		return componentsWithoutBridges;
	}
	private void dfsCompanentsWithoutBridges(Graph undirGraph, HashSet<Vertex> vertexInComponent, Vertex begin,
			HashSet<Vertex> actualComp, HashSet<Vertex> visited) {
		if (undirGraph.getEdges().get(begin) == null)
			return;
		for (Vertex end : undirGraph.getEdges().get(begin).keySet()) {
			if (visited.contains(end)) {
				continue;
			} else {
				actualComp.add(end);
				visited.add(end);
				dfsCompanentsWithoutBridges(undirGraph, vertexInComponent, end, actualComp, visited);
			}
		}

	}
	private boolean gammaAlgorithm(HashSet<Vertex> component, Graph undirGraph) {
		// small component, number of vertices less 4 => trivial go on Plane
		if (component.size() <= 3) {
			return true;
		}

		// init gammaAlgorithm
		HashSet<Vertex> verticesOnPlane = new HashSet<Vertex>();
		ArrayList<Graph> faces = new ArrayList<Graph>();

		// no cycle = > tree => trivial go on Plane
		// add cycle to first 2 faces
		boolean cycle = findСycle(undirGraph, component, faces);
		if (!cycle)
			return true;

		// add vertices in face on plane
		for (Vertex inCycle : faces.get(0).getEdges().keySet()) {
			verticesOnPlane.add(inCycle);
		}

		// edges not on plane
		Graph remains = new Graph();
		addEdgesNotOnPlane(remains, component, faces, undirGraph);

//		for (Vertex begin : component) {
//			if (remains.edges.get(begin) == null) continue;
//			if (remains.edges.get(begin).isEmpty())
//				return true;
//		}

		// init segments and find segment (f)
		ArrayList<Graph> segment = new ArrayList<Graph>();
		findSegments(segment, remains, verticesOnPlane);

		//
		while (segment.size() > 0) {
			System.out.println(segment.size());
			// count connection vertex & fasesNumberForSegment
			// find min number of faces for segment
			// if min number == 0 => not planar
			int minFacesNumber = 0;
			int iterMinFacesNumber = 0;
			int facesNumber = 0;
			;
			for (int i = 0; i < segment.size(); i++) {
				facesNumber = facesNumberOfSegment(segment.get(i), verticesOnPlane, faces);
				if (facesNumber == 0) {
					System.out.println("No face for segment with contact vertices:");
					for (Vertex v : segment.get(i).getEdges().keySet()) {
						if (verticesOnPlane.contains(v)) {
							System.out.print(v.getName() + " ");
						}
					}
					System.out.println();
					return false;
				}
				if (minFacesNumber == 0)
					minFacesNumber = facesNumber;
				if (minFacesNumber > facesNumber) {
					minFacesNumber = facesNumber;
					iterMinFacesNumber = i;
				}
			}
			System.out.println("init actual face segment");
			// init actual segment
			Graph actualSegment = segment.get(iterMinFacesNumber);
			segment.remove(iterMinFacesNumber);

			// init actual face
			Graph actualFace = null;
			int actualFaceNum = findActualFace(actualSegment, verticesOnPlane, faces);
			if (actualFaceNum != -1) {
				actualFace = faces.get(actualFaceNum);
				faces.remove(actualFaceNum);
			} else {
				return false;
			}

			System.out.println("find chain");
			// init and find chain
			Vertex chainStart = null;
			Vertex chainEnd = null;
			chainStart = findVertexForChain(verticesOnPlane, actualSegment, chainStart, chainEnd);
			chainEnd = findVertexForChain(verticesOnPlane, actualSegment, chainStart, chainEnd);
			if (chainStart == null || chainEnd == null)
				return false;
			ArrayList<Vertex> chain = new ArrayList<Vertex>();
			HashSet<Vertex> visited = new HashSet<Vertex>();
			dfsFindChain(chainStart, chainEnd, actualSegment, chain, false, null, visited);

			// add chain
			// init new faces
			Graph newFace1 = new Graph();
			newFace1 = actualFace.clone();
			Graph newFace2 = new Graph();

			System.out.println("split face");
			// split actualFace
			splitActualFace(actualFace, chainStart, chainEnd, newFace1, newFace2);

			// check empty Vertex
			newFace1.deleteEmptyVertexUndirGraph();
			newFace2.deleteEmptyVertexUndirGraph();

			// add chain to new faces
			addChain(newFace1, verticesOnPlane, chain, actualSegment);
			addChain(newFace2, verticesOnPlane, chain, actualSegment);

			// add new faces on plane
			faces.add(newFace1);
			faces.add(newFace2);

			// upd segments
			// upd remains

			System.out.println("upd remains");
			deleteNewEdgesOnPlane(remains, chain);
//			for (Vertex begin : actualSegment.edges.keySet()) {
//				for (Vertex end : actualSegment.edges.keySet()) {
//					for (int i = 0; i < faces.size(); i++) {
//						if (faces.get(i).edges.get(begin) != null) {
//							if (faces.get(i).edges.get(begin).containsKey(end)) {
//								remains.deleteEdge(begin, end);
//								break;
//							}
//						}
//					}
//
//				}
//			}

//			for (Vertex begin : actualSegment.edges.keySet()) {
//			if (remains.edges.get(begin) == null) continue;
//			if (remains.edges.get(begin).isEmpty())
//				return true;
//			}

			// find segment
			segment.clear();

			System.out.println("upd segments");
			findSegments(segment, remains, verticesOnPlane);

		}
		return true;
	}

	private void deleteNewEdgesOnPlane(Graph remains, ArrayList<Vertex> chain) {
		for (int i = 0; i < chain.size() - 1; i++) {
			remains.deleteEdge(chain.get(i), chain.get(i + 1));
			remains.deleteEdge(chain.get(i + 1), chain.get(i));
		}

	}

	private void addChain(Graph face, HashSet<Vertex> verticesOnPlane, ArrayList<Vertex> chain, Graph actualSegment) {
		for (int i = 0; i < chain.size() - 1; i++) {
			verticesOnPlane.add(chain.get(i));
			face.addEdge(chain.get(i), chain.get(i + 1),
					actualSegment.getEdges().get(chain.get(i)).get(chain.get(i + 1)).getLength());
			face.addEdge(chain.get(i + 1), chain.get(i),
					actualSegment.getEdges().get(chain.get(i)).get(chain.get(i + 1)).getLength());
		}

	}


	private void splitActualFace(Graph oldFace, Vertex chainStart, Vertex chainEnd, Graph newFace1, Graph newFace2) {
		Vertex tmp = chainStart;
		HashSet<Vertex> prev = new HashSet<Vertex>();
		while (tmp != chainEnd) {
			prev.add(tmp);
			for (Vertex end : oldFace.getEdges().get(tmp).keySet()) {
				if (prev.contains(end)) {
					continue;
				} else {
					newFace2.addEdge(tmp, end, oldFace.getEdges().get(tmp).get(end).getLength());
					newFace2.addEdge(end, tmp, oldFace.getEdges().get(tmp).get(end).getLength());
					newFace1.deleteEdge(tmp, end);
					newFace1.deleteEdge(end, tmp);
					tmp = end;
					break;
				}
			}
		}

	}

	private int findActualFace(Graph actualSegment, HashSet<Vertex> verticesOnPlane, ArrayList<Graph> faces) {
		HashSet<Vertex> connectedVertexInSegment = new HashSet<Vertex>();
		boolean vertexInFace = true;
		for (Vertex v : actualSegment.getEdges().keySet()) {
			if (verticesOnPlane.contains(v)) {
				connectedVertexInSegment.add(v);
			}
		}
		for (int j = 0; j < faces.size(); j++) {
			vertexInFace = true;
			for (Vertex ver : connectedVertexInSegment) {
				if (!faces.get(j).getEdges().containsKey(ver))
					vertexInFace = false;
			}
			if (vertexInFace) {
				return j;
			}
		}
		return -1;
	}

	private int facesNumberOfSegment(Graph segment, HashSet<Vertex> verticesOnPlane, ArrayList<Graph> faces) {
		int ans = 0;
		boolean vertexInFace = true;
		HashSet<Vertex> connectedVertexInSegment = new HashSet<Vertex>();
		for (Vertex conVertex : segment.getEdges().keySet()) {
			if (verticesOnPlane.contains(conVertex)) {
				connectedVertexInSegment.add(conVertex);
			}
		}
		for (int j = 0; j < faces.size(); j++) {
			vertexInFace = true;
			for (Vertex ver : connectedVertexInSegment) {
				if (!faces.get(j).getEdges().containsKey(ver))
					vertexInFace = false;
			}
			if (vertexInFace)
				ans++;
		}
		return ans;
	}

	private void findSegments(ArrayList<Graph> segment, Graph remains, HashSet<Vertex> verticesOnPlane) {
		HashSet<Vertex> visited = new HashSet<Vertex>();
		int segmentNumber = segment.size() - 1;
		int allSegmentsNumber = segment.size();
		for (Vertex begin : remains.getEdges().keySet()) {
			if (visited.contains(begin) || remains.getEdges().get(begin).isEmpty())
				continue;
			allSegmentsNumber++;
			segmentNumber++;
			segment.add(new Graph());
			segment.get(segmentNumber).addVertex(begin);
			visited.add(begin);
			dfsFindSegments(remains, begin, segmentNumber, allSegmentsNumber, visited, verticesOnPlane, segment, null);
		}
	}

	private void addEdgesNotOnPlane(Graph remains, HashSet<Vertex> component, ArrayList<Graph> faces, Graph undirGraph) {
		for (Vertex begin : component) {
			for (Vertex end : component) {
				for (int i = 0; i < faces.size(); i++) {

					if (faces.get(i).getEdges().get(begin) != null && faces.get(i).getEdges().get(begin).containsKey(end)) {
						continue;
					}
					if (undirGraph.getEdges().get(begin).containsKey(end)) {
						remains.addEdge(begin, end, undirGraph.getEdges().get(begin).get(end).getLength());
					}
				}
			}
		}
	}

	private boolean dfsFindChain(Vertex begin, Vertex chainEnd, Graph actualSegment, ArrayList<Vertex> chain,
			boolean done, Vertex prev, HashSet<Vertex> visited) {
		chain.add(begin);
		visited.add(begin);
		for (Vertex end : actualSegment.getEdges().get(begin).keySet()) {
			if (end.equals(prev)) {
				continue;
			}
			if (end.equals(chainEnd)) {
				chain.add(end);
				done = true;
				return true;
			}
			if (chain.contains(end) || visited.contains(end)) {
				continue;
			} else {
				done = dfsFindChain(end, chainEnd, actualSegment, chain, done, begin, visited);
				if (done)
					return done;
			}
		}
		chain.remove(chain.size() - 1);
		return false;

	}

	private Vertex findVertexForChain(HashSet<Vertex> verticesOnPlane, Graph actualSegment, Vertex chainStart,
			Vertex chainEnd) {
		for (Vertex v : actualSegment.getEdges().keySet()) {
			if (verticesOnPlane.contains(v)) {
				if (chainStart == null) {
					chainStart = v;
					return chainStart;
				} else if (!v.equals(chainStart)) {
					chainEnd = v;
					return chainEnd;
				}
			}
		}

		return chainStart;

	}

	private void dfsFindSegments(Graph undirGraph, Vertex begin, int segmentNumber, int allSegmentsNumber, HashSet<Vertex> visited,
			HashSet<Vertex> verticesOnPlane, ArrayList<Graph> segment, Vertex prev) {
		visited.add(begin);
		if (undirGraph.getEdges().get(begin) == null)
			return;
		for (Vertex end : undirGraph.getEdges().get(begin).keySet()) {
			segment.get(segmentNumber).addEdge(begin, end, undirGraph.getEdges().get(begin).get(end).getLength());
			segment.get(segmentNumber).addEdge(end, begin, undirGraph.getEdges().get(begin).get(end).getLength());
			if (visited.contains(end) || end.equals(prev)) {
				continue;
			} else {
				visited.add(end);
				if (!verticesOnPlane.contains(end)) {
//					allSegmentsNumber++;
//					segment.add(new Graph());
//					segment.get(allSegmentsNumber - 1).addVertex(end);
//					dfsFindSegments(begin, allSegmentsNumber - 1, allSegmentsNumber, visited, verticesOnPlane, segment, begin);
//				} else {
					dfsFindSegments(undirGraph, end, segmentNumber, allSegmentsNumber, visited, verticesOnPlane, segment, begin);
				}
			}
		}

	}

	boolean findСycle(Graph undirGraph, HashSet<Vertex> component, ArrayList<Graph> faces) {
		HashMap<Vertex, Integer> used = new HashMap<Vertex, Integer>();
		boolean cycle = false;
		ArrayList<Vertex> path = new ArrayList<Vertex>();
		for (Vertex begin : component) {
			if (!used.containsKey(begin)) {
				cycle = dfsFindCycle(undirGraph, component, used, path, cycle, begin, null);
			}
			if (cycle)
				break;

			break;

		}
		if (!cycle)
			return false;
		int cycleIter = path.size() - 2;
		Vertex to = path.get(path.size() - 1);
		while (path.get(cycleIter) != to)
			cycleIter--;
		Graph gph = new Graph();
		gph.addVertex(to);
		for (; cycleIter <= path.size() - 2; cycleIter++) {
			gph.addEdge(path.get(cycleIter), path.get(cycleIter + 1),
					undirGraph.getEdges().get(path.get(cycleIter)).get(path.get(cycleIter + 1)).getLength());
			gph.addEdge(path.get(cycleIter + 1), path.get(cycleIter),
					undirGraph.getEdges().get(path.get(cycleIter)).get(path.get(cycleIter + 1)).getLength());
		}
		faces.add(gph);
		faces.add(gph);
		return true;

	}

	private boolean dfsFindCycle(Graph undirGraph, HashSet<Vertex> component, HashMap<Vertex, Integer> used,
			ArrayList<Vertex> path, boolean cycle, Vertex begin, Vertex prev) {
		if (cycle)
			return true;
		if (prev != null)
			used.put(begin, 0);
		path.add(begin);
		for (Vertex end : undirGraph.getEdges().get(begin).keySet()) {
			if (end.equals(prev)) {
				continue;
			}
			if (used.containsKey(end) && used.get(end) == 0) {
				path.add(end);
				cycle = true;
				return true;
			} else {
				cycle = dfsFindCycle(undirGraph, component, used, path, cycle, end, begin);
				if (cycle)
					return true;
			}
		}
		used.replace(begin, 0, 1);
		path.remove(path.size() - 1);
		return false;

	}


}
