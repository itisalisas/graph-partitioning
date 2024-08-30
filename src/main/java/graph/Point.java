package graph;


public class Point {
	private double x;
	private double y;
	
	public Point() {
		this.x = -1;
		this.y = -1;
	}
	public Point(double x, double y) {
		this.x = x;
		this.y = y;
	}
	public double getX() {
		return x;
	}
	public double getY() {
		return y;
	}
	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if ((obj == null) || (obj.getClass() != this.getClass())) return false;
		Point v = (Point) obj;
		return v.getX() == this.getX() && v.getY() == this.getY();
	}
}
