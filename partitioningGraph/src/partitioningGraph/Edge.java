package partitioningGraph;

public class Edge {
	private double length;
	
	public Edge() {
		this.length = -1;
	}	
	public Edge(double length) {
		this.length = length;
	}
	public double getLength() {
		return length;
	}
}
