package geometry;

import java.util.List;

import graph.Point;

public class Geometry {

    public static double area(Point p, Point q){
        return (p.x * q.y - p.y * q.x)/2;
    }

    public static double area(Point start, Point x, Point y){
        return area(x.coordinateDistance(start), y.coordinateDistance(start));
        
    }

    public static double area(List<? extends Point> polygon){
        double ans = 0;
        if (polygon.size() <= 2){
            return ans;
        }
        var boundaryIterator = polygon.iterator();
        var base = boundaryIterator.next();
        var curr = boundaryIterator.next().coordinateDistance(base);
        while (boundaryIterator.hasNext()){
            var next = boundaryIterator.next().coordinateDistance(base);
            ans += area(curr, next);
            curr = next;
        }

        return ans;       

    }
    
}
