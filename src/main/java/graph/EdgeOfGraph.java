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
	public EdgeOfGraph(Vertex begin, Vertex end, double length, double flow, int bandwidth) {
		super(length, flow, bandwidth);
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
		return intersectForOneCoordinate(this.begin.getPoint().getX(), this.end.getPoint().getX(), edge.begin.getPoint().getX(), edge.end.getPoint().getX()) 
				&& intersectForOneCoordinate(this.begin.getPoint().getY(), this.end.getPoint().getY(), edge.begin.getPoint().getY(), edge.end.getPoint().getY())
				&& this.area(edge.begin.getPoint()) * this.area(edge.end.getPoint()) < 0
				&& edge.area(this.begin.getPoint()) * edge.area(this.end.getPoint()) < 0;
	}
	private double area(Point point) {
		return (this.end.getPoint().getX() - this.begin.getPoint().getX()) * (point.getY() - this.begin.getPoint().getY())
				- (this.end.getPoint().getY() - this.begin.getPoint().getY()) * (point.getX() - this.begin.getPoint().getX());
	}
	private boolean intersectForOneCoordinate(double bx1, double ex1,double bx2, double ex2) {
		double tmp = 0;
		if (bx1 > ex1) {
			tmp = bx1;
			bx1 = ex1;
			ex1 = tmp;
		}
		if (bx2 > ex2) {
			tmp = bx2;
			bx2 = ex2;
			ex2 = tmp;
		}
		return Math.max(bx1, bx2) <= Math.min(ex1, ex2);
	}
	public Vertex intersectionPoint(EdgeOfGraph edge) {
		if (!intersect(edge)) return null;
		if (this.getBegin().getPoint().getX() == this.getEnd().getPoint().getX() && edge.getBegin().getPoint().getX() == edge.getEnd().getPoint().getX()) {
			if (this.getBegin().getPoint().getX() != edge.getBegin().getPoint().getX() ||
					(Math.max(this.getBegin().getPoint().getY(), this.getEnd().getPoint().getY()) < Math.min(edge.getBegin().getPoint().getY(), edge.getEnd().getPoint().getY())
							|| Math.min(this.getBegin().getPoint().getY(), this.getEnd().getPoint().getY()) > Math.max(edge.getBegin().getPoint().getY(), edge.getEnd().getPoint().getY()))) {
				return new Vertex(0, new Point(-1, -1));
			}
			return new Vertex(0, new Point(this.getBegin().getPoint().getX(), 
					Math.max(this.getBegin().getPoint().getY(), this.getEnd().getPoint().getY()) 
					< Math.max(edge.getBegin().getPoint().getY(), edge.getEnd().getPoint().getY()) 
					? (Math.max(this.getBegin().getPoint().getY(), this.getEnd().getPoint().getY()) +  Math.min(edge.getBegin().getPoint().getY(), edge.getEnd().getPoint().getY())) / 2
					: (Math.min(this.getBegin().getPoint().getY(), this.getEnd().getPoint().getY()) +  Math.max(edge.getBegin().getPoint().getY(), edge.getEnd().getPoint().getY())) / 2));
		}
		if (this.getBegin().getPoint().getX() == this.getEnd().getPoint().getX()) {
			return new Vertex(0, new Point(this.getBegin().getPoint().getX(), edge.getYForEdge(this.getBegin().getPoint().getX())));
		}
		if (edge.getBegin().getPoint().getX() == edge.getEnd().getPoint().getX()) {
			return new Vertex(0, new Point(edge.getBegin().getPoint().getX(), this.getYForEdge(edge.getBegin().getPoint().getX())));
		}
		double k1 = (this.getBegin().getPoint().getY() - this.getEnd().getPoint().getY()) / (this.getBegin().getPoint().getX() - this.getEnd().getPoint().getX());
		double k2 = (edge.getBegin().getPoint().getY() - edge.getEnd().getPoint().getY()) / (edge.getBegin().getPoint().getX() - edge.getEnd().getPoint().getX());
		double b1 = this.getBegin().getPoint().getY() - k1 * this.getBegin().getPoint().getX();
		double b2 = edge.getBegin().getPoint().getY() - k2 * edge.getBegin().getPoint().getX();		
		
		if (k1 == k2) {
			return new Vertex(0, new Point(-1, -1));
		}
		return new Vertex(0, new Point((b2 - b1) / (k1 - k2), this.getYForEdge((b2 - b1) / (k1 - k2))));
	}
	public double getYForEdge(double x) {
		if (this.getBegin().getPoint().getX() == this.getEnd().getPoint().getX()) return this.getBegin().getPoint().getY();
		return this.getBegin().getPoint().getY() + (this.getEnd().getPoint().getY() - this.getBegin().getPoint().getY()) 
				* (x - this.getBegin().getPoint().getX()) / (this.getEnd().getPoint().getX() - this.getBegin().getPoint().getX()) ;
	}
}
