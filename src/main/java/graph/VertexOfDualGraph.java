package graph;

import java.util.ArrayList;

public class VertexOfDualGraph extends Vertex{
	
	private ArrayList<Vertex> verticesOfFace;
	
	public VertexOfDualGraph(Vertex center, double weightSum, ArrayList<Vertex> verticesOfFace) {
		super(center.getName(), center, weightSum);
		this.verticesOfFace = new ArrayList<Vertex>();
		this.verticesOfFace.addAll(verticesOfFace);
	}
	
	
	public VertexOfDualGraph(long name, Vertex center, double weightSum) {
		super(name, center, weightSum);
		this.verticesOfFace = null;
	}
	
	
	public VertexOfDualGraph(long name, Point center, double weightSum) {
		super(name, center, weightSum);
		this.verticesOfFace = null;
	}
	
	
	public VertexOfDualGraph(long name, Vertex center, double weightSum, ArrayList<Vertex> verticesOfFace) {
		super(name, center, weightSum);
		this.verticesOfFace = new ArrayList<Vertex>();
		this.verticesOfFace.addAll(verticesOfFace);
	}
	
	
	public VertexOfDualGraph(long name, Point center, double weightSum, ArrayList<Vertex> verticesOfFace) {
		super(name, center, weightSum);
		this.verticesOfFace = new ArrayList<Vertex>();
		this.verticesOfFace.addAll(verticesOfFace);
	}
	
	
	public VertexOfDualGraph(long name, double x, double y) {
		super(name, x , y);
		this.verticesOfFace = null;
	}
	
	public VertexOfDualGraph(long name) {
		super(name);
		this.verticesOfFace = null;
	}
	
	public VertexOfDualGraph(long name, double x, double y, double weight) {
		super(name, x , y, weight);
		this.verticesOfFace = null;
	}
	
	public VertexOfDualGraph(VertexOfDualGraph v) {
		super(v.getName(), v.x, v.y, v.getWeight());
		this.verticesOfFace = v.getVerticesOfFace();
	}


	public static double sumVertexWeight(ArrayList<Vertex> vertexIn) {
		double sum = 0;
		for (Vertex v : vertexIn) {
			sum = sum + v.getWeight();
		}
		return sum;
	}

	
	public ArrayList<Vertex> getVerticesOfFace() {
		return verticesOfFace;
	}

	@Override
	public VertexOfDualGraph copy() {
		return new VertexOfDualGraph(this.getName(),new Point(this.x, this.y), this.weight, this.verticesOfFace);
	}
	
	
}
