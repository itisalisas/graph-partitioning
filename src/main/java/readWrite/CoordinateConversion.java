package readWrite;

import java.util.Set;

import graph.Point;
import graph.Vertex;

public class CoordinateConversion {

    public Point referencePoint;
    private double c = 111321.377778;//meters in degree


    public CoordinateConversion() {
        referencePoint = new Point(30.32268115454809, 59.93893094417527);
    }


    public <T extends Vertex> CoordinateConversion(Set<T> vertexSet) {
        referencePoint = findCenter(vertexSet);
    }   


    private <T extends Vertex> Point findCenter(Set<T> vertexSet) {
        Point ans = new Point(0, 0);
        for (T ver : vertexSet) {
            ans.x = ans.x + ver.x;
            ans.y = ans.y + ver.y;
        }
        ans.x = ans.x / vertexSet.size();
        ans.y = ans.y / vertexSet.size();
        return ans;
    }
        
        
    public <T extends Vertex> void toEuclidean(T p) {
        double lat = p.x;
        double lon = p.y;
        p.x = c * Math.cos(referencePoint.y * Math.PI / 180) * (lat - referencePoint.x);
        p.y = c * (lon - referencePoint.y);
    }


    public <T extends Vertex> T fromEuclidean(T p) {
        T ver = (T) p.copy();
        double x = p.x;
        double y = p.y;
        ver.y = y / c + referencePoint.y;
        ver.x = x / c /  Math.cos(referencePoint.y * Math.PI / 180) + referencePoint.x;
        return ver;
    }
}
