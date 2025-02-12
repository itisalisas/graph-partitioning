package graph;

import java.util.Objects;

public class EdgeOfGraph<T extends Vertex> extends Edge {
	
	public T begin;
	public T end;
	
	public EdgeOfGraph(T begin, T end, double length) {
		super(length);
		this.begin = begin;
		this.end = end;
	}
	
	
	public EdgeOfGraph(T begin, T end, double length, double flow, double bandwidth) {
		super(length, flow, bandwidth);
		this.begin = begin;
		this.end = end;
	}
	
	
	public EdgeOfGraph(T begin, T end, double length, double flow, double bandwidth, boolean road) {
		super(length, flow, bandwidth, road);
		this.begin = begin;
		this.end = end;
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
		return intersectForOneCoordinate(this.begin.x, this.end.x, edge.begin.x, edge.end.x) 
				&& intersectForOneCoordinate(this.begin.y, this.end.y, edge.begin.y, edge.end.y)
				&& this.area(edge.begin) * this.area(edge.end) < 0
				&& edge.area(this.begin) * edge.area(this.end) < 0;
	}
	
	
	private double area(Point point) {
		return (this.end.x - this.begin.x) * (point.y - this.begin.y)
				- (this.end.y - this.begin.y) * (point.x - this.begin.x);
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
		return Math.max(begin1x, begin2x) < Math.min(end1x, end2x);
	}
	
	
	public Vertex intersectionPoint(EdgeOfGraph edge) {
		if (!intersect(edge)) return null;
		
		if (this.begin.x == this.end.x && edge.begin.x == edge.end.x) {
			if (this.begin.x != edge.begin.x ||
					(Math.max(this.begin.y, this.end.y) < Math.min(edge.begin.y, edge.end.y)
					|| Math.min(this.begin.y, this.end.y) > Math.max(edge.begin.y, edge.end.y))) {
				//System.out.println("one edge higher then other");
				return null;
			}
			return new Vertex(0, 
							new Point(this.begin.x, 
					        Math.max(this.begin.y, this.end.y) < Math.max(edge.begin.y, edge.end.y) 
					        ? (Math.max(this.begin.y, this.end.y) +  Math.min(edge.begin.y, edge.end.y)) / 2
					        : (Math.min(this.begin.y, this.end.y) +  Math.max(edge.begin.y, edge.end.y)) / 2), 
							0);
		}
		
		if (this.begin.x == this.end.x) {
			return new Vertex(0, new Point(this.begin.x, edge.getYForEdge(this.begin.x)), 0);
		}
		
		if (edge.begin.x == edge.end.x) {
			return new Vertex(0, new Point(edge.begin.x, this.getYForEdge(edge.begin.x)), 0);
		}
		
		//add fun for find k and b
		double k1 = this.tang();
		double k2 = edge.tang();
		double b1 = this.lineRise();
		double b2 = edge.lineRise();		
		
		if (k1 == k2) {
			//System.out.println("parallel");
			return null;
		}
		
		return new Vertex(0, new Point((b2 - b1) / (k1 - k2), this.getYForEdge((b2 - b1) / (k1 - k2))), 0);
	}
	
	/**
	 * @return b (y = kx + b)
	 */
	private double lineRise() {
		return this.begin.y - tang() * this.begin.x;
	}


	/**
	 * @return slope tangent
	 */
	private double tang() {
		return (this.begin.y - this.end.y) / (this.begin.x - this.end.x);
	}


	public double getYForEdge(double x) {
		if (this.begin.x == this.end.x) return this.begin.y;
		return this.begin.y + (this.end.y - this.begin.y) 
				* (x - this.begin.x) / (this.end.x - this.begin.x) ;
	}

	public double getCorner() {
		double deltaX = this.end.x - this.begin.x;
		double deltaY = this.end.y - this.begin.y;
		double angle = Math.atan2(deltaY, deltaX); // Угол в диапазоне [-pi, pi]
		return angle < 0 ? angle + 2 * Math.PI : angle; // Преобразуем к диапазону [0, 2pi]
	}
	
	public boolean vertical() {
		return this.begin.x == this.end.x;
	}
	
	
	public boolean includeForY(Vertex vert) {
		return (vert.y - this.begin.y) * (vert.y - this.end.y) < 0;
	}
	
	
	public boolean horizontal() {
		return this.begin.y == this.end.y;
	}
	
	
	public boolean includeForX(Vertex vert) {
		return (vert.x - this.begin.x) * (vert.x - this.end.x) < 0;
	}
	
	/**
	 * angle for edge of graph
	 * @return arctan(from -PI to PI) + PI
	 */
	public double angle() {
		Point vector = this.begin.coordinateDistance(this.end);
		return Math.atan2(vector.y, vector.x) + Math.PI;
	}
}
