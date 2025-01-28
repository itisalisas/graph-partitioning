package graph;

import java.util.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

public class Graph<T extends Vertex> {
	/*
	 * vertices - keys for HashMap
	 */
	private HashMap<T, HashMap<T, Edge>> edges;

	public Graph() {
		this.edges = new HashMap<T, HashMap<T, Edge>>();
	}

	public Graph(HashMap<T, HashMap<T, Edge>> edges) {
		this.edges = edges;
	}

	@Override
	public Graph<T> clone() {
		Graph<T> result = new Graph<T>();
		HashMap<T, T> oldNewVertices = new HashMap<T, T>();
		//copy vertices
		for (T begin : this.edges.keySet()) {
			T newVertex = (T) begin.copy();
			result.edges.put(newVertex , new HashMap<T, Edge>());
			oldNewVertices.put(begin, newVertex);
		}
		//copy edges
		for (T begin : this.edges.keySet()) {
			for (T end : this.edges.get(begin).keySet()) {
				Edge originalEdge = this.edges.get(begin).get(end);
				result.edges.get(oldNewVertices.get(begin)).put(oldNewVertices.get(end), originalEdge.clone());
			}
		}
		// for (T begin : this.edges.keySet()) {
		// 	if (begin instanceof VertexOfDualGraph) {
		// 		result.edges.put((T) new VertexOfDualGraph(begin.clone()), new HashMap<T, Edge>());
		// 	} else {
		// 		result.edges.put((T) new Vertex(begin.clone()), new HashMap<T, Edge>());
		// 	}
		// 	for (T end : this.edges.get(begin).keySet()) {
		// 		Edge originalEdge = this.edges.get(begin).get(end);
		// 		if (begin instanceof VertexOfDualGraph) {
		// 			result.edges.get(begin).put((T) new VertexOfDualGraph(end.clone()), originalEdge.clone());
		// 		} else {
		// 			result.edges.get(begin).put((T) new Vertex(end.clone()), originalEdge.clone());
		// 		}
		// 	}
		// }
		return result;
	}

	public T addVertex(T v) {
		if (!edges.containsKey(v)) {
			edges.put(v, new HashMap<T, Edge>());
		}
		return v;
	}

	public void deleteVertex(T v) {
		edges.remove(v);
		for (T begin : edges.keySet()) {
			edges.get(begin).remove(v);
		}

	}

	public Double verticesWeight() {
		return verticesArray().stream().mapToDouble(T::getWeight).sum();
	}

	public void addEdge(T begin, T end, double length, double bandwidth) {
		addVertex(begin);
		addVertex(end);
		edges.get(begin).put(end, new Edge(length, bandwidth));
	}

	public void addEdge(T begin, T end, double length) {
		addVertex(begin);
		addVertex(end);
		edges.get(begin).put(end, new Edge(length));

	}

	public void deleteEdge(T begin, T end) {
		if (edges.get(begin) != null) {
			edges.get(begin).remove(end);
		}
	}

	public HashMap<T, HashMap<T, Edge>> getEdges() {
		return edges;
	}

	public int verticesNumber() {
		return edges.size();
	}

	public List<T> verticesArray() {
		return edges.keySet().stream().toList();
	}

	public int edgesNumber() {
		int res = 0;
		for (Vertex begin : edges.keySet()) {
			res = res + edges.get(begin).size();
		}
		return res;
	}

	public EdgeOfGraph<T>[] edgesArray() {
		int iter = 0;
		EdgeOfGraph<T>[] ans = new EdgeOfGraph[edgesNumber()];
		for (T begin : edges.keySet()) {
			for (T end : edges.get(begin).keySet()) {
				ans[iter++] = new EdgeOfGraph((T)begin, (T)end, edges.get(begin).get(end).getLength(),
						edges.get(begin).get(end).flow, edges.get(begin).get(end).getBandwidth());
			}
		}
		return ans;
	}

	public int edgesNumberInComponentUndirGraph(HashSet<T> vertexInComponent) {
		int edgesNumber = 0;
		for (T begin : vertexInComponent) {
			if (edges.get(begin) == null)
				continue;
			for (T end : edges.get(begin).keySet()) {
				if (vertexInComponent.contains(end)) {
					edgesNumber++;
				}
			}
		}
		return edgesNumber;
	}

	public void deleteEmptyVertexUndirGraph() {
		ArrayList<T> deleteV = new ArrayList<T>();
		for (T v : this.edges.keySet()) {
			if (this.edges.get(v).isEmpty())
				deleteV.add(v);
		}
		for (int i = 0; i < deleteV.size(); i++) {
			this.deleteVertex(deleteV.get(i));
		}
	}

	boolean findСycle(Graph<T> undirGraph, HashSet<T> component, ArrayList<Graph<T>> faces) {
		HashMap<T, Integer> used = new HashMap<T, Integer>();
		boolean cycle = false;
		ArrayList<T> path = new ArrayList<T>();
		for (T begin : component) {
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
		T to = path.get(path.size() - 1);
		while (path.get(cycleIter) != to)
			cycleIter--;
		Graph<T> gph = new Graph<T>();
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

	private boolean dfsFindCycle(Graph<T> undirGraph, HashSet<T> component, HashMap<T, Integer> used, ArrayList<T> path,
			boolean cycle, T begin, T prev) {
		if (cycle)
			return true;
		if (prev != null)
			used.put(begin, 0);
		path.add(begin);
		for (T end : undirGraph.edges.get(begin).keySet()) {
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

	public ArrayList<HashSet<T>> splitForConnectedComponents() {
		// make undirected
		Graph<T> undirGraph = makeUndirectedGraph();
		ArrayList<HashSet<T>> component = new ArrayList<HashSet<T>>();
		HashSet<T> visited = new HashSet<T>();
		HashSet<T> actualComp = new HashSet<T>();
		for (T begin : edges.keySet()) {
			if (visited.contains(begin)) {
				continue;
			} else {
				actualComp.add(begin);
				visited.add(begin);
				undirGraph.dfsComponents(begin, actualComp, visited);
				component.add(actualComp);
				actualComp = new HashSet<T>();
			}
		}
		return component;
	}

	private void dfsComponents(T begin, HashSet<T> actualComp, HashSet<T> visited) {
		Stack<T> stack = new Stack<>();
		stack.push(begin);
		visited.add(begin);

		while (!stack.isEmpty()) {
			T current = stack.pop();
			actualComp.add(current);

			if (edges.get(current) != null) {
				for (T neighbor : edges.get(current).keySet()) {
					if (!visited.contains(neighbor)) {
						stack.push(neighbor);
						visited.add(neighbor);
					}
				}
			}
		}
	}

	public Graph<T> makeUndirectedGraph() {
		Graph<T> graph = new Graph<T>();
		for (T vertex : edges.keySet()) {
			if (vertex instanceof VertexOfDualGraph) {
				graph.addVertex((T) (new VertexOfDualGraph(vertex.clone())));
			} else {
				graph.addVertex((T) (new Vertex(vertex.clone())));
			}
		}
		for (T begin : edges.keySet()) {
			for (T end : edges.get(begin).keySet()) {
				graph.addEdge(begin, end, edges.get(begin).get(end).getLength());
				graph.addEdge(end, begin, edges.get(begin).get(end).getLength());
			}
		}
		return graph;
	}
	
	public void dfsBridges(HashSet<T> vertexInComponent, T begin, T prev,
			HashSet<T> used, int timer, HashMap<T, Integer> inTime, HashMap<T, Integer> returnTime,
			ArrayList<EdgeOfGraph> bridges) {
		used.add(begin);
		timer++;
		inTime.put(begin, timer);
		returnTime.put(begin, timer);
		if (edges.get(begin) == null) {
			return;
		}
		for (T out : edges.get(begin).keySet()) {
			if (!vertexInComponent.contains(out))
				continue;
			if (out.equals(prev))
				continue;
			if (used.contains(out)) {
				if (inTime.containsKey(out) && inTime.get(out) < returnTime.get(begin)) {
					returnTime.replace(begin, returnTime.get(begin), inTime.get(out));
				}
			} else {
				this.dfsBridges(vertexInComponent, out, begin, used, timer, inTime, returnTime, bridges);
				if (returnTime.containsKey(out) && returnTime.get(out) < returnTime.get(begin)) {
					returnTime.replace(begin, returnTime.get(begin), returnTime.get(out));
				}
				if (!returnTime.containsKey(out) || (returnTime.containsKey(out) && inTime.get(begin) < returnTime.get(out))) {
					// delete bridge
					bridges.add(new EdgeOfGraph(begin, out, edges.get(begin).get(out).getLength()));
//					undirGraph.deleteEdge(begin, out);
//					undirGraph.deleteEdge(out, begin);
				}

			}
		}
	}

	public Graph<T> createSubgraph(Set<T> verticesOfSubgraph) {
		Graph<T> subgraph = new Graph<T>();
		List<EdgeOfGraph<T>> edges = Arrays.stream(edgesArray()).toList();
		List<T> vertices = new ArrayList<>(verticesArray());

		for (T vertex : vertices) {
			if (verticesOfSubgraph.contains(vertex)) {
				subgraph.addVertex(vertex);
			}
		}

		for (EdgeOfGraph edge : edges) {
			if (verticesOfSubgraph.contains(edge.begin) && verticesOfSubgraph.contains(edge.end)) {
				if (edge.begin instanceof VertexOfDualGraph) {
					subgraph.addEdge((T) new VertexOfDualGraph(edge.begin), (T)  new VertexOfDualGraph(edge.end), edge.getLength());
				} else if (edge.begin instanceof PartitionGraphVertex) {
					subgraph.addEdge((T) new PartitionGraphVertex(edge.begin), (T)  new PartitionGraphVertex(edge.end), edge.getLength());
				} else {
					subgraph.addEdge((T) new Vertex(edge.begin), (T)  new Vertex(edge.end), edge.getLength());
				}
			}
		}

		return subgraph;
	}


	public Graph<T> createSubgraphFromFaces(List<List<T>> faces) {
		Graph<T> subgraph = new Graph<T>();

		for (List<T> face : faces) {
			for (T vertex : face) {
				if (vertex instanceof VertexOfDualGraph) {
					subgraph.addVertex((T) new VertexOfDualGraph(vertex.getName(), vertex, vertex.getWeight()));
				} else {
					subgraph.addVertex((T) new Vertex(vertex.getName(), vertex, vertex.getWeight()));
				}
			}
		}

		for (List<T> face : faces) {
			for (int i = 0; i < face.size(); i++) {
				T v1 = face.get(i);
				T v2 = face.get((i + 1) % face.size());

				EdgeOfGraph edge = findEdge(v1, v2);
				if (edge != null) {
					subgraph.addEdge(v1, v2, edge.getLength());
				}
			}
		}

		return subgraph;
	}

	private EdgeOfGraph findEdge(Vertex v1, Vertex v2) {
		for (EdgeOfGraph edge : edgesArray()) {
			if ((edge.begin.equals(v1) && edge.end.equals(v2)) ||
					(edge.begin.equals(v2) && edge.end.equals(v1))) {
				return edge;
			}
		}
		return null;
	}

	public Graph<T> getLargestConnectedComponent() {
		List<HashSet<T>> connectivityComponents = this.makeUndirectedGraph().splitForConnectedComponents();
		HashSet<T> largestComponent = connectivityComponents.stream().max(Comparator.comparingInt(HashSet::size)).orElseThrow();
		return this.createSubgraph(largestComponent);
	}

	public boolean isConnected() {
		return splitForConnectedComponents().size() == 1;
	}
	
	public double verticesSumWeight() {
		double ans = 0;
		for (Vertex ver : edges.keySet()) {
			ans = ans + ver.getWeight();
		}
		return ans;
	}

	public void correctVerticesWeight() {
		for (Vertex begin : edges.keySet()) {
			for (Vertex end : edges.get(begin).keySet()) {
				for (Vertex check : edges.keySet()) {
					if (end.equals(check)) {
						end.setWeight(check.getWeight());
					}
				}
			}
		}	
	}
	

	public int countZeroWeightVertices() {
		int ans = 0;
		for (Vertex v : edges.keySet()) {
			if (v.getWeight() == 0) {
				ans++;
			}
		}
		return ans;
	}
	
	
	public HashMap<Vertex, Integer> initVertexInFaceCounter() {
		HashMap<Vertex, Integer> res = new HashMap<Vertex, Integer>();
		for (Vertex v : this.edges.keySet()) {
			res.put(v, 0);
		}
		return res;
	}

	public HashMap<Vertex, TreeSet<EdgeOfGraph>> arrangeByAngle() {
		Comparator<EdgeOfGraph> edgeComp = (o1, o2) -> {
            double a1 = o1.getCorner();
            double a2 = o2.getCorner();
            return Double.compare(a1, a2);
        };
		HashMap<Vertex, TreeSet<EdgeOfGraph>> res = new HashMap<Vertex, TreeSet<EdgeOfGraph>>();
		for (Vertex begin : this.getEdges().keySet()) {
			res.put(begin, new TreeSet<EdgeOfGraph>(edgeComp));
			for (Vertex end : this.getEdges().get(begin).keySet()) {
				res.get(begin).add(new EdgeOfGraph(begin, end, this.getEdges().get(begin).get(end).getLength()));
			}
		}
		return res;
	}

	public T smallestVertex() {
		return edges.keySet().stream().min(Comparator.comparingDouble(v -> v.weight)).orElse(null);
	}

	public List<T> sortNeighbors(T vertex) {
		return edges.get(vertex).keySet().stream().sorted(Comparator.comparingDouble(v -> -v.weight)).toList();
	}

}
