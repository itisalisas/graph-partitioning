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
	 * @return vertex is in polygon
	 */
	public boolean inFaceGeom(ArrayList<Vertex> vertexIn) {
		Vertex begin = vertexIn.get(vertexIn.size() - 1);
		int count = 0;
		for (int i = 0; i < vertexIn.size(); i++) {
			if (this.inSegment(begin, vertexIn.get(i))) {
				begin = vertexIn.get(i);
				return true;
			}
			if (begin.y == vertexIn.get(i).y) {
				begin = vertexIn.get(i);
				continue;
			}
			if (this.y == Math.max(begin.y, vertexIn.get(i).y) 
					&& this.x < Math.min(begin.x, vertexIn.get(i).x)) {
				count++;
				begin = vertexIn.get(i);
				continue;
			}
			if (this.y == Math.min(begin.y, vertexIn.get(i).y)) {
				begin = vertexIn.get(i);
				continue;
			}
			if ((this.y - begin.y) * (this.y - vertexIn.get(i).y) < 0 &&
					this.x < begin.x + (this.y - begin.y) * (vertexIn.get(i).x - begin.x) / (vertexIn.get(i).y - begin.y)) {
				count++;
				begin = vertexIn.get(i);
				continue;
			}
		}
		if (count % 2 == 0) {
			return false;
		} else {
			return true;
		}
	}
}
