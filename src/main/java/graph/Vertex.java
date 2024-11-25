package graph;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;


public class Vertex extends Point {

	static private final Random random = new Random(15);

	private long name;
	protected double weight;
	
	public Vertex() {
		super();
		this.name = -1;
		this.weight = random.nextInt(10, 40);
	}
	
	
	public Vertex(long name) {
		super();
		this.name = name;
		this.weight = random.nextInt(10, 40);
	}
	
	
	public Vertex(long name, Point point) {
		super(point);
		this.name = name;
		this.weight = random.nextInt(10, 40);
	}
	
	
	public Vertex(long name, double x, double y) {
		super(x, y);
		this.name = name;
		this.weight = random.nextInt(10, 40);
	}

	public Vertex(long name, Point point, double weight) {
		super(point);
		this.name = name;
		this.weight = weight;
	}
	
	public Vertex(long name, double x, double y, double weight) {
		super(x, y);
		this.name = name;
		this.weight = weight;
	}

//	public Vertex(Vertex src) {
//		super(src);
//		this.name = src.name;
//		this.weight = src.weight;
//	}
	
	public <T extends Vertex> Vertex(T v) {
		super(v.x, v.y);
		this.name = v.getName();
		this.weight = v.getWeight();
	}

	
	public long getName() {
		return name;
	}
	

	public double getWeight() {
		return weight;
	}
	
	
	public void setWeight(double d) {
		this.weight = d;
	}
	
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (! (obj instanceof Vertex)) return false;
		Vertex v = (Vertex) obj;
		return v.x == this.x
				&& v.y == this.y && v.getName() == this.getName();
	}
	
	
	@Override
	public int hashCode() { 
		return Objects.hash(this.x, this.y, this.getName());
	}

	
	@Override
	public Vertex clone() {
		return new Vertex(this.getName(), this, this.getWeight());
	}

	
	public void printVertexToFile(File outFile) throws IOException {
		FileWriter out = new FileWriter(outFile, true);
		out.write(String.format("%d %f %f %f\n", this.getName(), this.x, this.y, this.getWeight()));
		out.close();
	}
	

	/**
	 * 
	 * @param <T extends Vertex>
	 * @param vertexIn the sequence of vertices of polygon
	 * @return Vertex distant from the middle of the longest edge by 0.000001
	 */
	public static <T extends Vertex> Point findCenter(ArrayList<T> vertexIn) {
		Point center = new Point();
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
			center = new Point(	vertexIn.get(0).x + vertexIn.get(0).coordinateDistance(vertexIn.get(1)).x / 2,
								vertexIn.get(0).y + vertexIn.get(0).coordinateDistance(vertexIn.get(1)).y / 2);
		} else {
			//change to func find longest edge
			Point begin = vertexIn.get(0);
			Vertex end = vertexIn.get(1);
			double maxLength = begin.getLength(end);
			for (int i = 1; i < vertexIn.size(); i++) {
				if (maxLength < vertexIn.get(i - 1).getLength(vertexIn.get(i))) {
					maxLength = vertexIn.get(i - 1).getLength(vertexIn.get(i));
					begin = vertexIn.get(i - 1);
					end = vertexIn.get(i);
				}
			}
			Point edgeCenter = new Point(begin.x + begin.coordinateDistance(end).x / 2,
										begin.y + begin.coordinateDistance(end).y / 2);
			Point coordinateLength = begin.coordinateDistance(end);
			double normalDir = 0;
			if (coordinateLength.y == 0) {
				normalDir = 1;
				center = new Point(edgeCenter.x, edgeCenter.y + 0.000001);
			} else if (coordinateLength.x == 0) {
				normalDir = 0;
				center = new Point(edgeCenter.x + 0.000001, edgeCenter.y);
			} else {
				normalDir = -1 / Math.atan2(coordinateLength.y, coordinateLength.x);
				center = new Point(edgeCenter.x + 0.000001 / Math.sqrt(1 + normalDir * normalDir),
									edgeCenter.y + 0.000001 * normalDir / Math.sqrt(1 + normalDir * normalDir));
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
}
