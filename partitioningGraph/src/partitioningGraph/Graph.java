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
	 * normal graph vertices have positive coordinates as point and containVertices = null
	 * dual graph vertices have point (-1,-1)
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
	/*
	 * file format:
	 * n (Vertices number)
	 * name x y (of Vertex) n1 (Number of out edges) name1 x1 y1 (of out vertex) length1 (edge length) ...
	 * int double x2		int						int double x2				double
	 */
	public void readGraphFromFile(String inFilename) throws FileNotFoundException {
		edges.clear();
		Scanner sc = new Scanner(new File(inFilename));
		int n = 0;
		n = sc.nextInt();
		int ni = 0;
		int namei = 0;
		double xi = 0;
		double yi = 0;
		double length = 0;
		Vertex vi;
		for (int i = 0; i < n && sc.hasNext(); i++) {
			//read Vertex..
			namei = sc.nextInt();
			xi = sc.nextDouble();
			yi = sc.nextDouble();
			ni = sc.nextInt();
			vi = new Vertex(namei, new Point(xi, yi));
			edges.put(vi, new HashMap<Vertex, Edge>());
			for (int j = 0; j < ni && sc.hasNext(); j++) {
				namei = sc.nextInt();
				xi = sc.nextDouble();
				yi = sc.nextDouble(); 
				length = sc.nextDouble();
				edges.get(vi).put(new Vertex(namei,  new Point(xi, yi)), new Edge(length));
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
	public void addVertex(Vertex v) {
		edges.put(v, new HashMap<Vertex, Edge>());
	}
	public void deleteVertex(Vertex v) {
		edges.remove(v);
		for (Vertex begin : edges.keySet()) {
			edges.get(begin).remove(v);
		}
	}
	public void addEdge(Vertex begin, Vertex end, double length) {
		if (edges.get(begin) == null) {
			edges.put(begin, new HashMap<Vertex,Edge>());
		}
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
	public int edgesNumber() {
		int res = 0;
		for (Vertex begin : edges.keySet()) {
			res = res + edges.get(begin).size();
		}
		return res;
	}
	
	//compare
	//planаr
}
