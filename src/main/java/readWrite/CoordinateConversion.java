package readWrite;

import graph.Point;
import graph.Vertex;

public class CoordinateConversion {
    private Point referencePoint = new Point(30.32268115454809, 59.93893094417527);
    private double c = 111321.377778;//meters in degree
    public <T extends Vertex> void toEuclidean(T p, Point referPoint) {
        if (referPoint == null) {
            referPoint = referencePoint;
        }
        double lat = p.x;
        double lon = p.y;
        p.x = c * Math.cos(referPoint.y * Math.PI / 180) * (lat - referPoint.x);
        p.y = c * (lon - referPoint.y);
    }

    public <T extends Vertex> T fromEuclidean(T p, Point referPoint) {
        T ver = (T) p.copy();
        if (referPoint == null) {
            referPoint = referencePoint;
        }
        double x = p.x;
        double y = p.y;
        ver.y = y / c + referPoint.y;
        ver.x = x / c /  Math.cos(referPoint.y * Math.PI / 180) + referPoint.x;
        return ver;
    }
}