package partitioningGraph;

import java.util.ArrayList; 
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

public class Graph {
	/*
	 * vertices - keys for HashMap
	 */
	private HashMap<Vertex, HashMap<Vertex, Edge>> edges;

	public Graph() {
		this.edges = new HashMap<Vertex, HashMap<Vertex, Edge>>();
	}

	public Graph(HashMap<Vertex, HashMap<Vertex, Edge>> edges) {
		this.edges = edges;
	}

	public Vertex readVertex(Scanner sc) {
		long name = sc.nextLong();
		double x = sc.nextDouble();
		double y = sc.nextDouble();
		return addVertex(new Vertex(name, new Point(x, y)));
	}

	/*
	 * file format: n (Vertices number) name x y (of Vertex) n1 (Number of out
	 * edges) name1 x1 y1 (of out vertex) length1 (edge length) ... long double x2
	 * long long double x2 double
	 */
	public void readGraphFromFile(String inFilename) throws FileNotFoundException {
		edges.clear();
		Scanner sc = new Scanner(new File(inFilename));
		int n = 0;
		n = sc.nextInt();
		int ni = 0;
		double length = 0;
		Vertex vi;
		Vertex vj;
		for (int i = 0; i < n && sc.hasNext(); i++) {
			// read Vertex..
			vi = readVertex(sc);
			ni = sc.nextInt();
			for (int j = 0; j < ni && sc.hasNext(); j++) {
				vj = readVertex(sc);
				length = sc.nextDouble();
				edges.get(vi).put(vj, new Edge(length));
			}
		}
		sc.close();
	}

	public void printGraphToFile(String outFileName) throws IOException {
		FileWriter out = new FileWriter(outFileName, false);
		out.write(String.format("%d %n", edges.size()));
		for (Vertex begin : edges.keySet()) {
			out.write(String.format("%d %f %f %d ", begin.getName(), begin.getPoint().getX(), begin.getPoint().getY(),
					edges.get(begin).size()));
			for (Vertex end : edges.get(begin).keySet()) {
				out.write(String.format("%d %f %f %f", end.getName(), end.getPoint().getX(), end.getPoint().getY(),
						edges.get(begin).get(end).getLength()));
			}
			out.append('\n');
		}
		out.close();

	}

	public void readGraphFromOSM(Point center, int dist) {

	}

	public Vertex addVertex(Vertex v) {
		if (!edges.containsKey(v)) {
			edges.put(v, new HashMap<Vertex, Edge>());
		}
		return v;
	}

	public void deleteVertex(Vertex v) {
		edges.remove(v);
		for (Vertex begin : edges.keySet()) {
			edges.get(begin).remove(v);
		}

	}


	public void addEdge(Vertex begin, Vertex end, double length, int bandwidth) {
		addVertex(begin);
		addVertex(end);
		edges.get(begin).put(end, new Edge(length, bandwidth));
	}


	public void addEdge(Vertex begin, Vertex end, double length) {
		addVertex(begin);
		addVertex(end);
		edges.get(begin).put(end, new Edge(length));

	}

	public void deleteEdge(Vertex begin, Vertex end) {
		edges.get(begin).remove(end);
	}

	public HashMap<Vertex, HashMap<Vertex, Edge>> getEdges() {
		return edges;
	}

	public int verticesNumber() {
		return edges.size();
	}


	public List<Vertex> verticesArray(){
		return edges.keySet().stream().toList();
	}

	public int edgesNumber() {
		int res = 0;
		for (Vertex begin : edges.keySet()) {
			res = res + edges.get(begin).size();
		}
		return res;
	}

	public EdgeOfGraph[] edgesArray() {
		int iter = 0;
		EdgeOfGraph[] ans = new EdgeOfGraph[edgesNumber()];
		for (Vertex begin : edges.keySet()) {
			for (Vertex end : edges.get(begin).keySet()) {
				ans[iter++] = new EdgeOfGraph(begin, end, edges.get(begin).get(end).getLength(), edges.get(begin).get(end).flow, edges.get(begin).get(end).getBandwidth());
			}
		}
		return ans;
	}

	public boolean isPlanar() {
		// split for connectivity components
		ArrayList<HashSet<Vertex>> component = this.splitForConnectivityComponents();
		// check each component
		int n = component.size();
		// make undirected
		Graph undirGraph = makeUndirectedGraph();
		for (int i = 0; i < n; i++) {
			if (!componentIsPlanar(undirGraph, component.get(i)))
				return false;
		}
		return true;
	}

	private boolean componentIsPlanar(Graph undirGraph, HashSet<Vertex> vertexInComponent) {
		// make new components if there is bridge an check them
		// check component
		int edgesNumber = 0;
		for (Vertex begin : vertexInComponent) {
			for (Vertex end : undirGraph.edges.get(begin).keySet()) {
				if (vertexInComponent.contains(end)) {
					edgesNumber++;
				}
			}
		}
		// component = tree
		if (edgesNumber == (vertexInComponent.size() - 1) * 2)
			return true;

		cutBridges(undirGraph, vertexInComponent);
		ArrayList<HashSet<Vertex>> componentsWithoutBridges = new ArrayList<HashSet<Vertex>>();
		HashSet<Vertex> visited = new HashSet<Vertex>();
		HashSet<Vertex> actualComp = new HashSet<Vertex>();
		for (Vertex begin : vertexInComponent) {
			if (visited.contains(begin)) {
				continue;
			} else {
				actualComp.add(begin);
				visited.add(begin);
				undirGraph.dfsCompanentsWithoutBridges(undirGraph, vertexInComponent, begin, actualComp, visited);
				componentsWithoutBridges.add(actualComp);
				actualComp = new HashSet<Vertex>();
			}
		}
		// checking components which connective & not tree & without bridge
		for (int i = 0; i < componentsWithoutBridges.size(); i++) {
			if (!gammaAlgorithm(componentsWithoutBridges.get(i), undirGraph))
				return false;
		}
		return true;
	}

	private boolean gammaAlgorithm(HashSet<Vertex> component, Graph undirGraph) {
		HashSet<Vertex> verticesOnPlane = new HashSet<Vertex>();
		ArrayList<Graph> faces = new ArrayList<Graph>();
		if (findСycle(undirGraph, component, faces))
			return true;
		for (Vertex inCycle : faces.get(0).edges.keySet()) {
			verticesOnPlane.add(inCycle);
		}
		// edges not on plane (f)
		Graph remains = new Graph();
		for (Vertex begin : component) {
			for (Vertex end : component) {
				for (int i = 0; i < faces.size(); i++) {
					if (faces.get(i).edges.get(begin).containsKey(end)) {
						continue;
					}
					if (undirGraph.edges.get(begin).containsKey(end)) {
						remains.addEdge(begin, end, undirGraph.edges.get(begin).get(end).getLength());
					}
				}
			}
		}
		for (Vertex begin : component) {
			if (remains.edges.get(begin).isEmpty())
				return true;
		}
		// find segment (f)
		HashSet<Vertex> visited = new HashSet<Vertex>();
		int segmentNumber = -1;
		int allSegmentsNumber = 0;
		ArrayList<Graph> segment = new ArrayList<Graph>();
		for (Vertex begin : component) {
			if (visited.contains(begin))
				continue;
			allSegmentsNumber++;
			segmentNumber++;
			segment.add(new Graph());
			segment.get(segmentNumber).addVertex(begin);
			visited.add(begin);
			remains.dfsFindSegments(begin, segmentNumber, allSegmentsNumber, visited, verticesOnPlane, segment);
		}
		while (segment.size() > 0) {
			int[] fasesNumberForSegment = new int[segment.size()];
			//
			// count connection vertex & fasesNumberForSegment
			HashSet<Vertex> connectedVertexInSegment = new HashSet<Vertex>();
			int minFacesNumber = 0;
			int iterMinFacesNumber = 0;
			boolean vertexInFace = true;
			for (int i = 0; i < segment.size(); i++) {
				for (Vertex conVertex : segment.get(i).edges.keySet()) {
					if (verticesOnPlane.contains(conVertex)) {
						connectedVertexInSegment.add(conVertex);
					}
				}
				fasesNumberForSegment[i] = 0;
				fasesNumberForSegment[i] = 0;
				for (int j = 0; j < faces.size(); j++) {
					vertexInFace = true;
					for (Vertex ver : connectedVertexInSegment) {
						if (!faces.get(j).edges.containsKey(ver))
							vertexInFace = false;
					}
					if (vertexInFace)
						fasesNumberForSegment[i]++;
				}
				if (fasesNumberForSegment[i] == 0)
					return false;
				if (minFacesNumber == 0)
					minFacesNumber = fasesNumberForSegment[i];
				if (minFacesNumber > fasesNumberForSegment[i]) {
					minFacesNumber = fasesNumberForSegment[i];
					iterMinFacesNumber = i;
				}
				connectedVertexInSegment.clear();
			}
			Graph actualSegment = segment.get(iterMinFacesNumber);
			Graph actualFace = null;
			segment.remove(iterMinFacesNumber);

			for (int j = 0; j < faces.size(); j++) {
				vertexInFace = true;
				for (Vertex ver : connectedVertexInSegment) {
					if (!faces.get(j).edges.containsKey(ver))
						vertexInFace = false;
				}
				if (vertexInFace) {
					actualFace = faces.get(j);
					faces.remove(j);
					break;
				}
			}
			Vertex chainStart = null;
			Vertex chainEnd = null;
			findVertexForChain(verticesOnPlane, actualSegment, chainStart, chainEnd);
			if (chainStart == null || chainEnd == null)
				System.out.println("260");
			ArrayList<Vertex> chain = new ArrayList<Vertex>();
			dfsFindChain(chainStart, chainEnd, actualSegment, chain, false);
			// add chain
			Graph newFace1 = actualFace;
			Graph newFace2 = new Graph();
			Vertex tmp = chainStart;
			HashSet<Vertex> prev = new HashSet<Vertex>();
			while (tmp != chainEnd) {
				prev.add(tmp);
				for (Vertex end : actualFace.edges.get(tmp).keySet()) {
					if (prev.contains(end)) {
						continue;
					}
					newFace1.deleteEdge(tmp, end);
					newFace1.deleteEdge(end, tmp);
					newFace2.addEdge(tmp, end, actualFace.edges.get(tmp).get(end).getLength());
					newFace2.addEdge(end, tmp, actualFace.edges.get(tmp).get(end).getLength());
					tmp = end;
					break;
				}
			}
			for (int i = 0; i < chain.size() - 1; i++) {
				verticesOnPlane.add(chain.get(i));
				newFace1.addEdge(chain.get(i), chain.get(i + 1),
						actualSegment.edges.get(chain.get(i)).get(chain.get(i + 1)).getLength());
				newFace1.addEdge(chain.get(i + 1), chain.get(i),
						actualSegment.edges.get(chain.get(i)).get(chain.get(i + 1)).getLength());
				newFace2.addEdge(chain.get(i), chain.get(i + 1),
						actualSegment.edges.get(chain.get(i)).get(chain.get(i + 1)).getLength());
				newFace2.addEdge(chain.get(i + 1), chain.get(i),
						actualSegment.edges.get(chain.get(i)).get(chain.get(i + 1)).getLength());

			}
			faces.add(newFace1);
			faces.add(newFace2);
			// upd segments
			visited = new HashSet<Vertex>();
			allSegmentsNumber = segment.size();
			segmentNumber = segment.size() - 1;
			for (Vertex begin : actualSegment.edges.keySet()) {
				if (visited.contains(begin))
					continue;
				allSegmentsNumber++;
				segmentNumber++;
				segment.add(new Graph());
				segment.get(segmentNumber).addVertex(begin);
				visited.add(begin);
				actualSegment.dfsFindSegments(begin, segmentNumber, allSegmentsNumber, visited, verticesOnPlane,
						segment);
			}
		}
		return false;
	}

	private void dfsFindChain(Vertex begin, Vertex chainEnd, Graph actualSegment, ArrayList<Vertex> chain,
			boolean done) {
		if (done) return;
		chain.add(begin);
		for (Vertex end : actualSegment.edges.get(begin).keySet()) {
			if (end.equals(chainEnd)) {
				chain.add(end);
				done = true;
			}
			if (chain.contains(end)) {
				continue;
			} else {
				dfsFindChain(end, chainEnd, actualSegment, chain, done);
			}
			if (done)
				return;
		}
		chain.remove(chain.size() - 1);
		
	}

	private void findVertexForChain(HashSet<Vertex> verticesOnPlane, Graph actualSegment, Vertex chainStart,
			Vertex chainEnd) {
		for (Vertex v : actualSegment.edges.keySet()) {
			if (verticesOnPlane.contains(v)) {
				if (chainStart == null) {
					chainStart = v;
				} else {
					chainEnd = v;
					return;
				}
			}
		}
		
	}

	private void dfsFindSegments(Vertex begin, int segmentNumber, int allSegmentsNumber, HashSet<Vertex> visited,
			HashSet<Vertex> verticesOnPlane, ArrayList<Graph> segment) {
		for (Vertex end : edges.get(begin).keySet()) {
			if (visited.contains(end)) {
				continue;
			} else {
				segment.get(segmentNumber).addEdge(begin, end, edges.get(begin).get(end).getLength());
				segment.get(segmentNumber).addEdge(end, begin, edges.get(begin).get(end).getLength());
				visited.add(end);
				if (verticesOnPlane.contains(end)) {
					allSegmentsNumber++;
					segment.add(new Graph());
					segment.get(allSegmentsNumber - 1).addVertex(end);
					dfsFindSegments(begin, allSegmentsNumber - 1, allSegmentsNumber, visited, verticesOnPlane, segment);
				} else {
					dfsFindSegments(begin, segmentNumber, allSegmentsNumber, visited, verticesOnPlane, segment);
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
				dfsFindCycle(undirGraph, component, used, path, cycle, begin);
				if (cycle)
					break;
			}

		}
		if (!cycle)
			return false;
		int cycleIter = path.size() - 2;
		Vertex to = path.get(path.size() - 1);
		while (path.get(cycleIter) != to)
			cycleIter--;
		Graph gph = new Graph();
		gph.addVertex(to);
		for (; cycleIter < path.size() - 2; cycleIter--) {
			gph.addEdge(path.get(cycleIter), path.get(cycleIter + 1),
					undirGraph.edges.get(path.get(cycleIter)).get(path.get(cycleIter + 1)).getLength());
			gph.addEdge(path.get(cycleIter + 1), path.get(cycleIter),
					undirGraph.edges.get(path.get(cycleIter)).get(path.get(cycleIter + 1)).getLength());
		}
		faces.add(gph);
		faces.add(gph);
		return true;

	}

	private void dfsFindCycle(Graph undirGraph, HashSet<Vertex> component, HashMap<Vertex, Integer> used,
			ArrayList<Vertex> path, boolean cycle, Vertex begin) {
		if (cycle)
			return;
		used.put(begin, 0);
		path.add(begin);
		for (Vertex end : undirGraph.edges.get(begin).keySet()) {
			if (used.containsKey(end) && used.get(end) == 0) {
				path.add(end);
				cycle = true;
				return;
			} else {
				dfsFindCycle(undirGraph, component, used, path, cycle, end);
			}
			if (cycle)
				return;
		}
		used.replace(begin, 0, 1);
		path.remove(path.size() - 1);

	}

	private void dfsCompanentsWithoutBridges(Graph undirGraph, HashSet<Vertex> vertexInComponent, Vertex begin,
			HashSet<Vertex> actualComp, HashSet<Vertex> visited) {
		for (Vertex end : vertexInComponent) {
			if (visited.contains(end)) {
				continue;
			} else {
				actualComp.add(end);
				visited.add(end);
				this.dfsCompanentsWithoutBridges(undirGraph, vertexInComponent, end, actualComp, visited);
			}
		}

	}

	private void cutBridges(Graph undirGraph, HashSet<Vertex> vertexInComponent) {
		int timer = 0;
		HashMap<Vertex, Integer> tin = new HashMap<Vertex, Integer>();
		HashMap<Vertex, Integer> fup = new HashMap<Vertex, Integer>();
		HashSet<Vertex> used = new HashSet<Vertex>();
		for (Vertex begin : vertexInComponent) {
			if (!used.contains(begin)) {
				dfsBridges(undirGraph, vertexInComponent, begin, null, used, timer, tin, fup);
			}
		}

	}

	private void dfsBridges(Graph undirGraph, HashSet<Vertex> vertexInComponent, Vertex begin, Vertex prev,
			HashSet<Vertex> used, int timer, HashMap<Vertex, Integer> tin, HashMap<Vertex, Integer> fup) {
		used.add(begin);
		timer++;
		tin.put(begin, timer);
		fup.put(begin, timer);
		for (Vertex out : undirGraph.edges.get(begin).keySet()) {
			if (vertexInComponent.contains(out))
				continue;
			if (out.equals(prev))
				continue;
			if (used.contains(out)) {
				if (tin.containsKey(out) && tin.get(out) < fup.get(begin)) {
					fup.replace(begin, fup.get(begin), tin.get(out));
				}
			} else {
				dfsBridges(undirGraph, vertexInComponent, out, begin, used, timer, tin, fup);
				if (fup.containsKey(out) && fup.get(out) < fup.get(begin)) {
					fup.replace(begin, fup.get(begin), fup.get(out));
					if (fup.containsKey(out) && tin.get(begin) < fup.get(out)) {
						// delete bridge
						deleteEdge(begin, out);
						deleteEdge(out, begin);
					}
				}
			}
		}
	}

	private ArrayList<HashSet<Vertex>> splitForConnectivityComponents() {
		// make undirected
		Graph undirGraph = makeUndirectedGraph();
		ArrayList<HashSet<Vertex>> component = new ArrayList<HashSet<Vertex>>();
		HashSet<Vertex> visited = new HashSet<Vertex>();
		HashSet<Vertex> actualComp = new HashSet<Vertex>();
		for (Vertex begin : edges.keySet()) {
			if (visited.contains(begin)) {
				continue;
			} else {
				actualComp.add(begin);
				visited.add(begin);
				undirGraph.dfsCompanents(begin, actualComp, visited);
				component.add(actualComp);
				actualComp = new HashSet<Vertex>();
			}
		}
		return component;
	}

	private void dfsCompanents(Vertex begin, HashSet<Vertex> actualComp, HashSet<Vertex> visited) {
		for (Vertex end : edges.get(begin).keySet()) {
			if (visited.contains(end)) {
				continue;
			} else {
				actualComp.add(end);
				visited.add(end);
				this.dfsCompanents(end, actualComp, visited);
			}
		}

	}

	public Graph makeUndirectedGraph() {
		Graph graph = new Graph();
		for (Vertex begin : edges.keySet()) {
			for (Vertex end : edges.get(begin).keySet()) {
				graph.addEdge(begin, end, edges.get(begin).get(end).getLength());
				graph.addEdge(end, begin, edges.get(begin).get(end).getLength());
			}
		}
		return graph;
	}
}
