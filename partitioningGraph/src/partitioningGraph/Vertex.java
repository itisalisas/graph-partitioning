package partitioningGraph;

import java.util.ArrayList;

public class Vertex {
	private int name;
	private Point point;
	private ArrayList<Vertex> containedVertices;
	
	public Vertex() {
		this.name = -1;
		this.point = new Point();
		this.containedVertices = null;
	}
	public Vertex(int name) {
		this.name = name;
		this.point = new Point();
		this.containedVertices = null;
	}
	public Vertex(int name, Point point) {
		this.name = name;
		this.point = point;
		this.containedVertices = null;
	}
	public Vertex(int name, Point point, ArrayList<Vertex> contVer) {
		this.name = name;
		this.point = point;
		this.containedVertices = contVer;
	}
	public int getName() {
		return name;
	}
	public Point getPoint() {
		return point;
	}
	public ArrayList<Vertex> getContainedVertices() {
		return containedVertices;
	}
	public void addContainedVertex(Vertex v) {
		
	}
	public void deleteContainedVertex(Vertex v) {
		
	}
}
