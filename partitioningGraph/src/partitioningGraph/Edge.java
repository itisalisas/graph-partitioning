package partitioningGraph;

public class Edge {
	private double length;
	private int bandwidth;
	public double flow;
	
	public Edge() {
		this.length = -1;
		this.bandwidth = 1;
		this.flow = 0;
	}	
	public Edge(double length) {
		this.length = length;
		this.bandwidth = 1;
		this.flow = 0;
	}
	public double getLength() {
		return length;
	}
	public int getBandwidth() {
		return bandwidth;
	}
}
