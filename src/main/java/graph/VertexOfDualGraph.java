package graph;

import java.util.HashSet;

public class VertexOfDualGraph extends Vertex{
	private HashSet<Vertex> verticesOfFace;
	public VertexOfDualGraph(Vertex center, int weightSum, HashSet<Vertex> verticesOfFace) {
		super(center.getName(), center.getPoint(), weightSum);
		this.verticesOfFace = new HashSet<Vertex>();
		this.verticesOfFace.addAll(verticesOfFace);
	}
	
	public VertexOfDualGraph(long name, Vertex center, int weightSum, HashSet<Vertex> verticesOfFace) {
		super(name, center.getPoint(), weightSum);
		this.verticesOfFace = new HashSet<Vertex>();
		this.verticesOfFace.addAll(verticesOfFace);
	}

	public static Vertex findCenter(HashSet<Vertex> vertexIn) {
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

	public static int sumVertexWeight(HashSet<Vertex> vertexIn) {
		int sum = 0;
		for (Vertex v : vertexIn) {
			sum = sum + v.getWeight();
		}
		return sum;
	}

	public HashSet<Vertex> getVerticesOfFace() {
		return verticesOfFace;
	}

}
