package partitioningGraph;

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
	public boolean equals(Point p) {
		return this.x == p.x && this.y == p.y;
	}
}
