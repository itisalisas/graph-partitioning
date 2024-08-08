package graph;

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

	public Edge(double length, int bandwidth) {
		this.length = length;
		this.bandwidth = bandwidth;
		this.flow = 0;
	}
	public Edge(double length, double flow, int bandwidth) {
		this.length = length;
		this.bandwidth = bandwidth;
		this.flow = flow;
	}
	public double getLength() {
		return length;
	}
	public int getBandwidth() {
		return bandwidth;
	}
}
