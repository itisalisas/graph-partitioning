package graph;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Random;

import jakarta.validation.constraints.Size;


public class Vertex extends Point {

	static private final Random random = new Random(15);

	public long name;
	protected double weight;
    private boolean isOnBoundary = true; // по умолчанию хотим останавливаться в этих вершинах
	
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
	
	public <T extends Vertex> Vertex(T v) {
		super(v.x, v.y);
		this.name = v.getName();
		this.weight = v.getWeight();
	}

    public boolean getIsOnBoundary() {
        return isOnBoundary;
    }

    public void setIsOnBoundary(boolean isOnBoundary) {
        this.isOnBoundary = isOnBoundary;
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
	public static <T extends Vertex> Point findCenter(@Size(min = 1) ArrayList<T> vertexIn) {
		Point center;

		if (vertexIn.size() == 1) {
			return vertexIn.get(0);
		}
		if (vertexIn.size() == 2) {
			return new Point(	vertexIn.get(0).x + vertexIn.get(0).coordinateDistance(vertexIn.get(1)).x / 2,
					vertexIn.get(0).y + vertexIn.get(0).coordinateDistance(vertexIn.get(1)).y / 2);
		}
		
		HashMap<T, Double> edgeWeight = countEdgeWeightForVertices(vertexIn);
//		System.out.println(edgeWeight);
		center = countVerticesEdgeWeightCenter(vertexIn, edgeWeight);
		
		return center;
	}


	private static <T extends Vertex> HashMap<T, Double> countEdgeWeightForVertices(ArrayList<T> vertexIn) {
		HashMap<T, Double> edgeWeight = new HashMap<>();
		T vertex = vertexIn.get(0);
		T prev = vertexIn.get(vertexIn.size() - 1);
		T next = vertexIn.get(1);
		edgeWeight.put(vertex, vertex.getLength(prev) + vertex.getLength(next));
		for (int i = 1; i < vertexIn.size() - 1; i++) {
			prev = vertexIn.get(i - 1);
			next = vertexIn.get(i + 1);
			edgeWeight.put(vertexIn.get(i), vertexIn.get(i).getLength(prev) + vertexIn.get(i).getLength(next));
		}
		vertex = vertexIn.get(vertexIn.size() - 1);
		prev = vertexIn.get(vertexIn.size() - 2);
		next = vertexIn.get(0);	
		edgeWeight.put(vertex, vertex.getLength(prev) + vertex.getLength(next));
		return edgeWeight;
	}


	private static <T extends Vertex> Point countVerticesEdgeWeightCenter(ArrayList<T> vertexIn, HashMap<T, Double> edgeWeight) {
		Double xSum = 0.0;
		Double ySum = 0.0;
		Double fullLengthSum2 = 0.0;
		for (int i = 0; i < vertexIn.size(); i++) {
			xSum = xSum + vertexIn.get(i).x * edgeWeight.get(vertexIn.get(i));
			ySum = ySum + vertexIn.get(i).y * edgeWeight.get(vertexIn.get(i));
			fullLengthSum2 = fullLengthSum2 + edgeWeight.get(vertexIn.get(i));
		}
		return new Point(xSum / fullLengthSum2, ySum / fullLengthSum2);
	}


	public static double sumVertexWeight(ArrayList<Vertex> vertexIn) {
		double sum = 0;
		for (Vertex v : vertexIn) {
			sum = sum + v.getWeight();
		}
		return sum;
	}
	
	
	public Vertex copy() {
		return new Vertex(this.getName(), this, this.getWeight());
	}
}
