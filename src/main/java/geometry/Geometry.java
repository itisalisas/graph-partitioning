package geometry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        double px = point.x, py = point.y;
        int count = 0;
        for (int i = 0; i < n; i++) {
            double ax = polygon.get(i).x, ay = polygon.get(i).y;
            double bx = polygon.get((i + 1) % n).x, by = polygon.get((i + 1) % n).y;
            double minX, minY, maxX, maxY;
            if (ay <= by) { minX = ax; minY = ay; maxX = bx; maxY = by; }
            else          { minX = bx; minY = by; maxX = ax; maxY = ay; }
            if (minY == maxY) continue;                  // horizontal edge
            if (py < minY || py >= maxY) continue;       // half-open [minY, maxY)
            double cross = (maxX - minX) * (py - minY) - (maxY - minY) * (px - minX);
            if (cross == 0) return true;                 // point on edge
            if (cross > 0) count++;
        }
        return count % 2 != 0;
    }

    // Splits self-intersecting polygon into simple sub-polygons at repeated vertices.
    // Pattern ...->A->B->...->C->A->... splits into inner loop [A,B,...,C] and outer [... A ...].
    public static List<List<Point>> decomposePolygon(List<? extends Point> polygon) {
        List<List<Point>> result = new ArrayList<>();
        decomposeRecursive(new ArrayList<>(polygon), result);
        return result;
    }

    private static void decomposeRecursive(List<Point> polygon, List<List<Point>> result) {
        int n = polygon.size();
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            Point p = polygon.get(i);
            String key = p.x + " " + p.y;
            Integer prev = seen.get(key);
            if (prev != null) {
                int j = prev;
                // inner loop: polygon[j..i-1]
                List<Point> inner = new ArrayList<>(polygon.subList(j, i));
                // outer: polygon[0..j] + polygon[i+1..n-1]
                List<Point> outer = new ArrayList<>();
                outer.addAll(polygon.subList(0, j + 1));
                outer.addAll(polygon.subList(i + 1, n));
                decomposeRecursive(inner, result);
                decomposeRecursive(outer, result);
                return;
            }
            seen.put(key, i);
        }
        if (n >= 3) result.add(polygon);
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
