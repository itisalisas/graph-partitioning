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

	@Override
	public Graph clone() {
		Graph result = new Graph();
		for (Vertex begin : this.edges.keySet()) {
			result.edges.put(begin, new HashMap<Vertex, Edge>());
			for (Vertex end : this.edges.get(begin).keySet()) {
				result.edges.get(begin).put(end, this.edges.get(begin).get(end));
			}
		}
		return result;
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
				out.write(String.format("%d %f %f %f ", end.getName(), end.getPoint().getX(), end.getPoint().getY(),
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
		if (edges.get(begin) != null) {
			edges.get(begin).remove(end);
		}
	}

	public HashMap<Vertex, HashMap<Vertex, Edge>> getEdges() {
		return edges;
	}

	public int verticesNumber() {
		return edges.size();
	}

	public List<Vertex> verticesArray() {
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
				ans[iter++] = new EdgeOfGraph(begin, end, edges.get(begin).get(end).getLength(),
						edges.get(begin).get(end).flow, edges.get(begin).get(end).getBandwidth());
			}
		}
		return ans;
	}


	public int edgesNumberInCompanentUndirGraph(HashSet<Vertex> vertexInComponent) {
		int edgesNumber = 0;
		for (Vertex begin : vertexInComponent) {
			if (edges.get(begin) == null)
				continue;
			for (Vertex end : edges.get(begin).keySet()) {
				if (vertexInComponent.contains(end)) {
					edgesNumber++;
				}
			}
		}
		return edgesNumber;
	}


	public void deleteEmptyVertexUndirGraph() {
		ArrayList<Vertex> deleteV = new ArrayList<Vertex>();
		for (Vertex v : this.edges.keySet()) {
			if (this.edges.get(v).isEmpty())
				deleteV.add(v);
		}
		for (int i = 0; i < deleteV.size(); i++) {
			this.deleteVertex(deleteV.get(i));
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
					undirGraph.edges.get(path.get(cycleIter)).get(path.get(cycleIter + 1)).getLength());
			gph.addEdge(path.get(cycleIter + 1), path.get(cycleIter),
					undirGraph.edges.get(path.get(cycleIter)).get(path.get(cycleIter + 1)).getLength());
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
		for (Vertex end : undirGraph.edges.get(begin).keySet()) {
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


	public ArrayList<HashSet<Vertex>> splitForConnectivityComponents() {
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
		if (edges.get(begin) == null)
			return;
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
