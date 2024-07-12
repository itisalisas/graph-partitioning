package partitioningGraph;

import java.util.HashMap;

public class Graph {
	// vertices - keys for HashMap
	//normal graph vertices have positive coordinates as point and containVertices = null
	//dual graph vertices have point (-1,-1)
	private HashMap<Vertex, HashMap<Vertex, Edge>> edges;
	
	public Graph()
	{
		this.edges = null;
	}
	public Graph(HashMap<Vertex, HashMap<Vertex, Edge>> edges)
	{
		this.edges = edges;
	}
	public void readGraphFromFile(String filename) {
		
	}
	public void readGraphFromOSM(Point center, int dist) {
		
	}
	public void addVertex(Vertex v) {
		
	}
	public void deleteVertex(Vertex v) {
		
	}
	public void addEdge(Edge edge) {
	
	}
	public void deleteEdge(Edge edge) {
	
	}
	public HashMap<Vertex, HashMap<Vertex, Edge>> getEdges() {
		return edges;
	}
	// depends on definition of the edge length in dual graph (multiple edges)
	public double edgeLengthBetweenVertices(Vertex v1, Vertex v2) {
		// for normal graph
		if (v1.getPoint().getX() > 0 && v1.getPoint().getY() > 0 &&
				v2.getPoint().getX() > 0 && v2.getPoint().getY() > 0) {
			return edges.get(v1).get(v2).getLength();
		} else {
			//for dual
			//
			return -1;
		}
	}
	//compare
	//
}
