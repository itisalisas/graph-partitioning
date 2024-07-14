package partitioningGraph;

import java.util.Objects;

public class Vertex {
	private int name;
	private Point point;
	
	public Vertex() {
		this.name = -1;
		this.point = new Point();
	}
	public Vertex(int name) {
		this.name = name;
		this.point = new Point();
	}
	public Vertex(int name, Point point) {
		this.name = name;
		this.point = point;
	}
	public int getName() {
		return name;
	}
	public Point getPoint() {
		return point;
	}
	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if ((obj == null) || (obj.getClass() != this.getClass())) return false;
		Vertex v = (Vertex) obj;
		return v.name == this.name && v.point.getX() == this.point.getX()
				&& v.point.getY() == this.point.getY();
	}
	@Override
	public int hashCode() {
		return Objects.hash(name, point.getX(), point.getY());
	}
}
