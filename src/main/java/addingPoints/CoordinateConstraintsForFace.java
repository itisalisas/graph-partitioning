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
				maxX = verticesOfFace.get(i).getX();
				minX = verticesOfFace.get(i).getX();
				maxY = verticesOfFace.get(i).getY();
				minY = verticesOfFace.get(i).getY();
				continue;
			}
			if (maxX < verticesOfFace.get(i).getX()) maxX = verticesOfFace.get(i).getX();
			if (minX > verticesOfFace.get(i).getX()) minX = verticesOfFace.get(i).getX();
			if (maxY < verticesOfFace.get(i).getY()) maxY = verticesOfFace.get(i).getY();
			if (minY > verticesOfFace.get(i).getY()) minY = verticesOfFace.get(i).getY();
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
