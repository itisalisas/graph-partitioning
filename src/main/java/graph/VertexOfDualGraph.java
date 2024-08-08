package graph;

import java.util.HashSet;

public class VertexOfDualGraph extends Vertex{
	private HashSet<Vertex> verticesofFace;
	boolean clockwise;
	public VertexOfDualGraph(Vertex center, int weightSum) {
		super(center.getName(), center.getPoint(), weightSum);
	}

	private Vertex findCenter(HashSet<Vertex> vertexIn) {
		// TODO Auto-generated method stub
		return null;
	}

	private int sumVertexWeight(HashSet<Vertex> vertexIn) {
		int sum = 0;
		for (Vertex v : vertexIn) {
			sum = sum + v.getWeight();
		}
		return 0;
	}
}
