package graph;

public class Edge {
	private double length;
	private double bandwidth;
	public double flow;
	
	public Edge() {
		this.length = -1;
		this.bandwidth = 1;
		this.flow = 0;
	}	
	public Edge(double length) {
		this.length = length;
		this.bandwidth = length;
		this.flow = 0;
	}

	public Edge(double length, double bandwidth) {
		this.length = length;
		this.bandwidth = bandwidth;
		this.flow = 0;
	}
	public Edge(double length, double flow, double bandwidth) {
		this.length = length;
		this.bandwidth = bandwidth;
		this.flow = flow;
	}
	public double getLength() {
		return length;
	}
	public double getBandwidth() {
		return bandwidth;
	}

	@Override
	public Edge clone() {
		return new Edge(this.length, this.flow, this.bandwidth);
	}

}
