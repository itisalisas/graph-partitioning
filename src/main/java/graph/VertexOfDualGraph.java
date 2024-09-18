package graph;

import java.util.ArrayList;

public class VertexOfDualGraph extends Vertex{
	private ArrayList<Vertex> verticesOfFace;
	public VertexOfDualGraph(Vertex center, double weightSum, ArrayList<Vertex> verticesOfFace) {
		super(center.getName(), center.getPoint(), weightSum);
		this.verticesOfFace = new ArrayList<Vertex>();
		this.verticesOfFace.addAll(verticesOfFace);
	}
	
	public VertexOfDualGraph(long name, Vertex center, double weightSum, ArrayList<Vertex> verticesOfFace) {
		super(name, center.getPoint(), weightSum);
		this.verticesOfFace = new ArrayList<Vertex>();
		this.verticesOfFace.addAll(verticesOfFace);
	}

	public static Vertex findCenter(ArrayList<Vertex> vertexIn) {
		Vertex center = new Vertex();
		double minLengthSum = 0;
		double lengthSum = 0;
		for (Vertex begin : vertexIn) {
			lengthSum = 0;
			for (Vertex end : vertexIn) {
				lengthSum = lengthSum + begin.getLength(end);
			}
			if (minLengthSum == 0) {
				center = begin;
				minLengthSum = lengthSum;
			}
			if (minLengthSum > lengthSum) {
				center = begin;
				minLengthSum = lengthSum;
			}
		}
		return center;
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

}
