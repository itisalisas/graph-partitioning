package graph;

import java.util.ArrayList;

import geometry.Geometry;

public class VertexOfDualGraph extends Vertex{
	
	private final ArrayList<Vertex> verticesOfFace;
	public double area;
	
	public VertexOfDualGraph(Vertex center, double weightSum, ArrayList<Vertex> verticesOfFace) {
		super(center.getName(), center, weightSum);
		this.verticesOfFace = new ArrayList<>();
		this.verticesOfFace.addAll(verticesOfFace);
		this.area = Geometry.area(verticesOfFace);
	}
	
	
	public VertexOfDualGraph(long name, Vertex center, double weightSum) {
		super(name, center, weightSum);
		this.verticesOfFace = new ArrayList<>();
	}
	
	
	public VertexOfDualGraph(long name, Point center, double weightSum) {
		super(name, center, weightSum);
		this.verticesOfFace = new ArrayList<>();
	}
	
	
	public VertexOfDualGraph(long name, Vertex center, double weightSum, ArrayList<Vertex> verticesOfFace) {
		super(name, center, weightSum);
		this.verticesOfFace = new ArrayList<>();
		this.verticesOfFace.addAll(verticesOfFace);
		this.area = Geometry.area(verticesOfFace);
	}
	
	
	public VertexOfDualGraph(long name, Point center, double weightSum, ArrayList<Vertex> verticesOfFace) {
		super(name, center, weightSum);
		this.verticesOfFace = new ArrayList<>();
		this.verticesOfFace.addAll(verticesOfFace);
		this.area = Geometry.area(verticesOfFace);

	}
	
	
	public VertexOfDualGraph(long name, double x, double y) {
		super(name, x , y);
		this.verticesOfFace = new ArrayList<>();
		area = 0.;
	}
	
	public VertexOfDualGraph(long name) {
		super(name);
		this.verticesOfFace = new ArrayList<>();
	}
	
	public VertexOfDualGraph(long name, double x, double y, double weight) {
		super(name, x , y, weight);
		this.verticesOfFace = new ArrayList<>();
	}
	
	public VertexOfDualGraph(VertexOfDualGraph v) {
		super(v.getName(), v.x, v.y, v.getWeight());
		this.verticesOfFace = v.getVerticesOfFace();
		area = v.area;
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

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (! (obj instanceof VertexOfDualGraph v)) return false;
        return v.x == this.x
				&& v.y == this.y && v.getName() == this.getName();
	}
}