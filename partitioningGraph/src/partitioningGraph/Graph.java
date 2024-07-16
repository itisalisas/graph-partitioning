package partitioningGraph;

import java.util.HashMap;
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
	
	
	public Graph()
	{
		this.edges = new HashMap<Vertex, HashMap<Vertex,Edge>>();
	}
	public Graph(HashMap<Vertex, HashMap<Vertex, Edge>> edges)
	{
		this.edges = edges;
	}
	public Vertex readVertex(Scanner sc) {
		long name = sc.nextLong();
		double x = sc.nextDouble();
		double y = sc.nextDouble();
		return addVertex(new Vertex(name, new Point(x, y)));
	}
	/*
	 * file format:
	 * n (Vertices number)
	 * name x y (of Vertex) n1 (Number of out edges) name1 x1 y1 (of out vertex) length1 (edge length) ...
	 * long double x2		long					long double x2				double
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
			//read Vertex..
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
			out.write(String.format("%d %f %f %d ", begin.getName(), begin.getPoint().getX(),
					begin.getPoint().getY(), edges.get(begin).size()));
			for (Vertex end : edges.get(begin).keySet()) {
				out.write(String.format("%d %f %f %f", end.getName(), end.getPoint().getX(),
						end.getPoint().getY(), edges.get(begin).get(end).getLength()));
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
	public Vertex[] verticesArray(){
		return (Vertex[]) edges.keySet().toArray();
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
				ans[iter] = new EdgeOfGraph(begin, end, edges.get(begin).get(end).getLength());
			}
		}
		return ans;
	}
	
	//planаr
}
