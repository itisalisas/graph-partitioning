package geometry;

import java.util.List;

import graph.Point;

public class Geometry {

    public static double area(Point p, Point q) {
        return (p.x * q.y - p.y * q.x) / 2;
    }

    public static double area(Point start, Point x, Point y) {
        return area(x.coordinateDistance(start), y.coordinateDistance(start));
    }

    public static boolean containsPoint(List<? extends Point> polygon, Point point) {
        int n = polygon.size();
        if (n < 3) return false;
        boolean inside = false;
        double x = point.x, y = point.y;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon.get(i).x, yi = polygon.get(i).y;
            double xj = polygon.get(j).x, yj = polygon.get(j).y;
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }

    public static boolean containsPointWinding(List<? extends Point> polygon, Point point) {
        int n = polygon.size();
        if (n < 3) return false;
        int winding = 0;
        double px = point.x, py = point.y;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon.get(i).x, yi = polygon.get(i).y;
            double xj = polygon.get(j).x, yj = polygon.get(j).y;
            if (yj <= py) {
                if (yi > py && crossZ(xj, yj, xi, yi, px, py) > 0) winding++;
            } else {
                if (yi <= py && crossZ(xj, yj, xi, yi, px, py) < 0) winding--;
            }
        }
        return winding != 0;
    }

    private static double crossZ(double x1, double y1, double x2, double y2, double px, double py) {
        return (x2 - x1) * (py - y1) - (px - x1) * (y2 - y1);
    }

    public static double area(List<? extends Point> polygon) {
        double ans = 0;
        if (polygon.size() <= 2) {
            return ans;
        }
        var boundaryIterator = polygon.iterator();
        var base = boundaryIterator.next();
        var curr = boundaryIterator.next().coordinateDistance(base);
        while (boundaryIterator.hasNext()) {
            var next = boundaryIterator.next().coordinateDistance(base);
            ans += area(curr, next);
            curr = next;
        }

        return ans;       
    }
}
