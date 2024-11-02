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

	public static Vertex findCenter(ArrayList<Vertex> vertexIn, long id) {
		double sumX = 0;
		double sumY = 0;
		int n = vertexIn.size();

		for (Vertex v : vertexIn) {
			sumX += v.getPoint().getX();
			sumY += v.getPoint().getY();
		}

		double centerX = sumX / n;
		double centerY = sumY / n;

		return new Vertex(id, new Point(centerX, centerY));
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
