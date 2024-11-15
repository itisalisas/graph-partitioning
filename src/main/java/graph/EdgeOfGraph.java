package graph;

import java.util.Objects;

public class EdgeOfGraph extends Edge {
	
	private Vertex begin;
	private Vertex end;
	
	public EdgeOfGraph(Vertex begin,Vertex end, double length) {
		super(length);
		this.begin = begin;
		this.end = end;
	}
	
	
	public EdgeOfGraph(Vertex begin, Vertex end, double length, double flow, double bandwidth) {
		super(length, flow, bandwidth);
		this.begin = begin;
		this.end = end;
	}
	
	
	public EdgeOfGraph(Vertex begin, Vertex end, double length, double flow, double bandwidth, boolean road) {
		super(length, flow, bandwidth, road);
		this.begin = begin;
		this.end = end;
	}
	
	
	public Vertex getBegin() {
		return begin;
	}
	
	
	public Vertex getEnd() {
		return end;
	}
	
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if ((obj == null) || (obj.getClass() != this.getClass())) return false;
		EdgeOfGraph v = (EdgeOfGraph) obj;
		return v.begin.equals(this.begin) && v.end.equals(this.end);
	}
	
	
	@Override
	public int hashCode() {
		return Objects.hash(begin, end);
	}
	
	
	public boolean intersect(EdgeOfGraph edge) {
		return intersectForOneCoordinate(this.begin.getX(), this.end.getX(), edge.begin.getX(), edge.end.getX()) 
				&& intersectForOneCoordinate(this.begin.getY(), this.end.getY(), edge.begin.getY(), edge.end.getY())
				&& this.area(edge.begin) * this.area(edge.end) <= 0
				&& edge.area(this.begin) * edge.area(this.end) <= 0;
	}
	
	
	private double area(Point point) {
		return (this.end.getX() - this.begin.getX()) * (point.getY() - this.begin.getY())
				- (this.end.getY() - this.begin.getY()) * (point.getX() - this.begin.getX());
	}
	
	
	private boolean intersectForOneCoordinate(double begin1x, double end1x, double begin2x, double end2x) {
		double tmp = 0;
		if (begin1x > end1x) {
			tmp = begin1x;
			begin1x = end1x;
			end1x = tmp;
		}
		if (begin2x > end2x) {
			tmp = begin2x;
			begin2x = end2x;
			end2x = tmp;
		}
		return Math.max(begin1x, begin2x) <= Math.min(end1x, end2x);
	}
	
	
	public Vertex intersectionPoint(EdgeOfGraph edge) {
		if (!intersect(edge)) return null;
		
		if (this.begin.getX() == this.end.getX() && edge.begin.getX() == edge.end.getX()) {
			if (this.begin.getX() != edge.begin.getX() ||
					(Math.max(this.begin.getY(), this.end.getY()) < Math.min(edge.begin.getY(), edge.end.getY())
							|| Math.min(this.begin.getY(), this.end.getY()) > Math.max(edge.begin.getY(), edge.end.getY()))) {
				//System.out.println("one edge higher then other");
				return null;
			}
			return new Vertex(0, new Point(this.begin.getX(), 
					Math.max(this.begin.getY(), this.end.getY()) 
					< Math.max(edge.begin.getY(), edge.end.getY()) 
					? (Math.max(this.begin.getY(), this.end.getY()) +  Math.min(edge.begin.getY(), edge.end.getY())) / 2
					: (Math.min(this.begin.getY(), this.end.getY()) +  Math.max(edge.begin.getY(), edge.end.getY())) / 2), 0);
		}
		
		if (this.begin.getX() == this.end.getX()) {
			return new Vertex(0, new Point(this.begin.getX(), edge.getYForEdge(this.begin.getX())), 0);
		}
		
		if (edge.begin.getX() == edge.end.getX()) {
			return new Vertex(0, new Point(edge.begin.getX(), this.getYForEdge(edge.begin.getX())), 0);
		}
		
		double k1 = (this.begin.getY() - this.end.getY()) / (this.begin.getX() - this.end.getX());
		double k2 = (edge.begin.getY() - edge.end.getY()) / (edge.begin.getX() - edge.end.getX());
		double b1 = this.begin.getY() - k1 * this.begin.getX();
		double b2 = edge.begin.getY() - k2 * edge.begin.getX();		
		
		if (k1 == k2) {
			//System.out.println("parallel");
			return null;
		}
		
		return new Vertex(0, new Point((b2 - b1) / (k1 - k2), this.getYForEdge((b2 - b1) / (k1 - k2))), 0);
	}
	
	
	public double getYForEdge(double x) {
		if (this.begin.getX() == this.end.getX()) return this.begin.getY();
		return this.begin.getY() + (this.end.getY() - this.begin.getY()) 
				* (x - this.begin.getX()) / (this.end.getX() - this.begin.getX()) ;
	}

	public double getCorner() {
		double deltaX = this.end.getX() - this.begin.getX();
		double deltaY = this.end.getY() - this.begin.getY();
		double angle = Math.atan2(deltaY, deltaX); // Угол в диапазоне [-pi, pi]
		return angle < 0 ? angle + 2 * Math.PI : angle; // Преобразуем к диапазону [0, 2pi]
	}
	
	public boolean vertical() {
		return this.begin.getX() == this.end.getX();
	}
	
	
	public boolean includeForY(Vertex vert) {
		return (vert.getY() - this.begin.getY()) * (vert.getY() - this.end.getY()) < 0;
	}
	
	
	public boolean horizontal() {
		return this.begin.getY() == this.end.getY();
	}
	
	
	public boolean includeForX(Vertex vert) {
		return (vert.getX() - this.begin.getX()) * (vert.getX() - this.end.getX()) < 0;
	}
	
	/**
	 * angle for edge of graph
	 * @return arctan(from -PI to PI) + PI
	 */
	public double angle() {
		Point vector = this.begin.coordinateDistance(this.end);
		return Math.atan2(vector.getY(), vector.getX()) + Math.PI;
	}
}
