package graph;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
				&& v.getY() == this.getY();
	}
	
	
	@Override
	public int hashCode() {
		return Objects.hash(this.getX(), this.getY());
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
}
