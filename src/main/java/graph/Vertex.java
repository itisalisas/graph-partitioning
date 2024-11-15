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
	private double weight;
	
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
		super(v.getX(), v.getY());
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
		return v.getX() == this.getX()
				&& v.getY() == this.getY() && v.getName() == this.getName();
	}
	
	
	@Override
	public int hashCode() { 
		return Objects.hash(this.getX(), this.getY(), this.getName());
	}

	
	@Override
	public Vertex clone() {
		return new Vertex(this.getName(), this, this.getWeight());
	}

	
	public void printVertexToFile(File outFile) throws IOException {
		FileWriter out = new FileWriter(outFile, true);
		out.write(String.format("%d %f %f %f\n", this.getName(), this.getX(), this.getY(), this.getWeight()));
		out.close();
	}

	/**
	 * @return vertex is in polygon
	 */
	public boolean inFace(ArrayList<Vertex> vertexIn) {
		Vertex begin = vertexIn.get(vertexIn.size() - 1);
		int count = 0;
		for (int i = 0; i < vertexIn.size(); i++) {
			if (this.inSegment(begin, vertexIn.get(i))) {
				return true;
			}
			if (begin.getY() == vertexIn.get(i).getY()) {
				continue;
			}
			if (this.getY() == Math.max(begin.getY(), vertexIn.get(i).getY()) 
					&& this.getX() < Math.min(begin.getX(), vertexIn.get(i).getX())) {
				count++;
			}
			if (this.getY() == Math.min(begin.getY(), vertexIn.get(i).getY())) {
				continue;
			}
			if ((this.getY() - begin.getY()) * (this.getY() - vertexIn.get(i).getY()) < 0 &&
					this.getX() < begin.getX() + (this.getY() - begin.getY()) * (vertexIn.get(i).getX() - begin.getX()) / (vertexIn.get(i).getY() - begin.getY())) {
				count++;
			}
		}
		if (count % 2 == 0) {
			return false;
		} else {
			return true;
		}
	}
}
