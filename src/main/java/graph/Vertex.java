package graph;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;
import java.util.Random;


public class Vertex {

	static private final Random random = new Random();

	private long name;
	private Point point;
	private int weight;
	
	public Vertex() {
		this.name = -1;
		this.point = new Point();
		this.weight = random.nextInt(10, 40);
	}
	public Vertex(long name) {
		this.name = name;
		this.point = new Point();
		this.weight = random.nextInt(10, 40);
	}
	public Vertex(long name, Point point) {
		this.name = name;
		this.point = point;
		this.weight = random.nextInt(10, 40);
	}

	public Vertex(long name, Point point, int weight) {
		this.name = name;
		this.point = point;
		this.weight = weight;
	}

	public long getName() {
		return name;
	}
	public Point getPoint() {
		return point;
	}
	public int getWeight() {
		return weight;
	}
	public void setWeight(int weight) {
		this.weight = weight;
	}
	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (! (obj instanceof Vertex)) return false;
		Vertex v = (Vertex) obj;
		return v.name == this.name && v.point.getX() == this.point.getX()
				&& v.point.getY() == this.point.getY();
	}
	@Override
	public int hashCode() {
		return Objects.hash(name, point.getX(), point.getY());
	}

	@Override
	public Vertex clone() {
		return new Vertex(this.getName(), this.getPoint(), this.getWeight());
	}
	public double getLength(Vertex v) {
		return Math.sqrt(Math.pow(this.getPoint().getX() - v.getPoint().getX(), 2) + Math.pow(this.getPoint().getY() - v.getPoint().getY(), 2));
	}

	public void printVertexToFile(File outFile) throws IOException {
		FileWriter out = new FileWriter(outFile, true);
		out.write(String.format("%d %f %f %d\n", this.getName(), this.getPoint().getX(), this.getPoint().getY(), this.getWeight()));
		out.close();
	}
}
