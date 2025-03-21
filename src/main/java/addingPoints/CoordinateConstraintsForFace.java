package addingPoints;

import java.util.ArrayList;

import graph.Vertex;

public class CoordinateConstraintsForFace {
	private double maxX;
	private double minX;
	private double maxY;
	private double minY;
	public CoordinateConstraintsForFace(ArrayList<Vertex> verticesOfFace) {
		for (int i = 0 ; i < verticesOfFace.size(); i++) {
			if (i == 0) {
				maxX = verticesOfFace.get(i).x;
				minX = verticesOfFace.get(i).x;
				maxY = verticesOfFace.get(i).y;
				minY = verticesOfFace.get(i).y;
				continue;
			}
			if (maxX < verticesOfFace.get(i).x) maxX = verticesOfFace.get(i).x;
			if (minX > verticesOfFace.get(i).x) minX = verticesOfFace.get(i).x;
			if (maxY < verticesOfFace.get(i).y) maxY = verticesOfFace.get(i).y;
			if (minY > verticesOfFace.get(i).y) minY = verticesOfFace.get(i).y;
		}
		
	}
	public double getMinX() {
		return minX;
	}
	public void setMinX(double minX) {
		this.minX = minX;
	}
	public double getMaxX() {
		return maxX;
	}
	public void setMaxX(double maxX) {
		this.maxX = maxX;
	}
	public double getMaxY() {
		return maxY;
	}
	public void setMaxY(double maxY) {
		this.maxY = maxY;
	}
	public double getMinY() {
		return minY;
	}
	public void setMinY(double minY) {
		this.minY = minY;
	}
}
