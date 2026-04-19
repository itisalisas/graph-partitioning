package graph;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class Point {
	
	public double x;
	public double y;
	
	public Point() {
		this.x = -1;
		this.y = -1;
	}
	
	public Point(Point point) {
		this.x = point.x;
		this.y = point.y;
	}
	
	
	public Point(double x, double y) {
		this.x = x;
		this.y = y;
	}
	
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if ((obj == null) || (obj.getClass() != this.getClass())) return false;
		Point v = (Point) obj;
		return v.x == this.x && v.y == this.y;
	}
	
	
	public double getLength(Point p) {
		return Math.sqrt(Math.pow(this.x - p.x, 2) + Math.pow(this.y - p.y, 2));
	}
	
	
	/**
	 * @return Point - coordinate difference between the argument and "this"
	 */
	public Point coordinateDistance(Point p) {
		return new Point(p.x - this.x, p.y - this.y);
	}
	
	
	/**
	 * add to this point coordinates of p point
	 */
	public void addCoordinateDistance(Point p) {
		this.x = this.x + p.x;
		this.y = this.y + p.y;
	}
	
	/**
	 * @return is point in segment
	 */
	public boolean inSegment(Point a, Point b) {
		if ((a.x - this.x) * (b.x - this.x) > 0 || (a.y - this.y) * (b.y - this.y) > 0) {
			return false;
		} 
		if ((a.x - this.x) == 0) {
            return (a.y - this.y) == 0 || (b.x - this.x) == 0;
		}
		if (a.x - b.x == 0) {
			return false;
		}
        return (this.y - a.y) * (b.x - a.x) == (b.y - a.y) * (this.x - a.x);
    }
	
	/**
	 * @return is point in rectangle
	 */
	public boolean inRectangle(Point a, Point b) {
		return (a.x - this.x) * (b.x - this.x) <= 0 && (a.y - this.y) * (b.y - this.y) <= 0;
	}
	
	/**
	 * @return vector length
	 */
	public double module() {
		return Math.sqrt(x * x + y * y);
	}
	
	/**
	 * @return vertex is in polygon
	 */
	public <T extends Point> boolean inFaceGeom(ArrayList<T> vertexIn) {
		Point begin = vertexIn.get(vertexIn.size() - 1);
		int count = 0;
        for (T t : vertexIn) {
            if (this.inSegment(begin, t)) {
                return true;
            }
            if (begin.y == t.y) {
                begin = t;
                continue;
            }
            if (this.y == Math.max(begin.y, t.y)
                    && this.x < Math.min(begin.x, t.x)) {
                count++;
                begin = t;
                continue;
            }
            if (this.y == Math.min(begin.y, t.y)) {
                begin = t;
                continue;
            }
            if ((this.y - begin.y) * (this.y - t.y) < 0 &&
                    this.x < begin.x + (this.y - begin.y) * (t.x - begin.x) / (t.y - begin.y)) {
                count++;
                begin = t;
                continue;
            }

            begin = t;
        }
		//System.out.println(count);
        return count % 2 != 0;
	}

	public void printPointToFile(File outFile) throws IOException {
		FileWriter out = new FileWriter(outFile, true);
		out.write(String.format("%f %f\n", this.x, this.y));
		out.close();
	}

	public Point copy() {
		return new Point(this);
	}
}
