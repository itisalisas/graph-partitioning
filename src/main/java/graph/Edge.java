package graph;

public class Edge {
	public double length;
	private double bandwidth;
	public double flow;
	private boolean road;
	
	public Edge() {
		this.length = -1;
		this.bandwidth = 1;
		this.flow = 0;
		this.road = true;
	}	
	public Edge(double length) {
		this.length = length;
		this.bandwidth = length;
		this.flow = 0;
		this.road = true;
	}

	public Edge(double length, double bandwidth) {
		this.length = length;
		this.bandwidth = bandwidth;
		this.flow = 0;
		this.road = true;
	}
	public Edge(double length, double flow, double bandwidth) {
		this.length = length;
		this.bandwidth = bandwidth;
		this.flow = flow;
		this.road = true;
	}
	public Edge(double length, double flow, double bandwidth, boolean road) {
		this.length = length;
		this.bandwidth = bandwidth;
		this.flow = flow;
		this.road = road;
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
	
	public boolean isRoad() {
		return road;
	}
	public void notRoad() {
		this.road = false;
	}

}