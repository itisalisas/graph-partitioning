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
	
	public VertexOfDualGraph(long name, Vertex center, double weightSum, ArrayList<Vertex> verticesOfFace) {
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
	
	public <T extends Vertex> VertexOfDualGraph(T v) {
		super(v.getName(), v.getX(), v.getY(), v.getWeight());
		this.verticesOfFace = null;
	}


	public static Vertex findCenter(ArrayList<Vertex> vertexIn) {
		Vertex center = new Vertex();
//		double minLengthSum = 0;
//		double lengthSum = 0;
//		for (Vertex begin : vertexIn) {
//			lengthSum = 0;
//			for (Vertex end : vertexIn) {
//				lengthSum = lengthSum + begin.getLength(end);
//			}
//			if (minLengthSum == 0) {
//				center = begin;
//				minLengthSum = lengthSum;
//			}
//			if (minLengthSum > lengthSum) {
//				center = begin;
//				minLengthSum = lengthSum;
//			}
//		}
		if (vertexIn.size() <= 0) {
			return null;
		} else if (vertexIn.size() == 1) {
			return vertexIn.get(0);
		} else if (vertexIn.size() == 2) {
			center = new Vertex(0, vertexIn.get(0).getX() + vertexIn.get(0).coordinateDistance(vertexIn.get(1)).getX() / 2, 
					vertexIn.get(0).getY() + vertexIn.get(0).coordinateDistance(vertexIn.get(1)).getY() / 2);
		} else {
			//change to func find longest edge
			Vertex begin = vertexIn.get(0);
			Vertex end = vertexIn.get(1);
			double maxLength = begin.getLength(end);
			for (int i = 1; i < vertexIn.size(); i++) {
				if (maxLength < vertexIn.get(i - 1).getLength(vertexIn.get(i))) {
					maxLength = vertexIn.get(i - 1).getLength(vertexIn.get(i));
					begin = vertexIn.get(i - 1);
					end = vertexIn.get(i);
				}
			}
			Vertex edgeCenter = new Vertex(0, begin.getX() + begin.coordinateDistance(end).getX() / 2, 
					begin.getY() + begin.coordinateDistance(end).getY() / 2);
			Point coordinateLength = begin.coordinateDistance(end);
			double normalDir = 0;
			if (coordinateLength.getY() == 0) {
				normalDir = 1;
				center = new Vertex(0, edgeCenter.getX(), edgeCenter.getY() + 0.000001);
			} else if (coordinateLength.getX() == 0) {
				normalDir = 0;
				center = new Vertex(0, edgeCenter.getX() + 0.000001, edgeCenter.getY());
			} else {
				normalDir = -1 / Math.atan2(coordinateLength.getY(), coordinateLength.getX());
				center = new Vertex(0, edgeCenter.getX() + 0.000001 / Math.sqrt(1 + normalDir * normalDir),
						edgeCenter.getY() + 0.000001 * normalDir / Math.sqrt(1 + normalDir * normalDir));
			}
			
			if (center.inFaceGeom(vertexIn)) {
				return center;
			} else {
				Point diff = center.coordinateDistance(edgeCenter);
				center.addCoordinateDistance(diff);
				center.addCoordinateDistance(diff);
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
