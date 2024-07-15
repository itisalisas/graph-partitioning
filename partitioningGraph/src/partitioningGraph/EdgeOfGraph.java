package partitioningGraph;

public class EdgeOfGraph extends Edge {
	private Vertex begin;
	private Vertex end;
	public EdgeOfGraph(Vertex begin,Vertex end, double length) {
		super(length);
		this.begin = begin;
		this.end = end;
	}
	public Vertex getBegin() {
		return begin;
	}
	public Vertex getEnd() {
		return end;
	}

}
