package graph;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PointTest {
    @Test
    void testPointInТriangle() {
        Point p = new Point(3, 3);
        ArrayList<Point> polygon = new ArrayList<Point>();
        polygon.add(new Point(2, 4));
        polygon.add(new Point(4, 4));
        polygon.add(new Point(3, 2));
        assertTrue(p.inFaceGeom(polygon));
    }

    @Test
    void testPointOutТriangle() {
        Point p = new Point(5, 3);
        ArrayList<Point> polygon = new ArrayList<Point>();
        polygon.add(new Point(2, 4));
        polygon.add(new Point(4, 4));
        polygon.add(new Point(3, 2));
        assertFalse(p.inFaceGeom(polygon));
    }

    @Test
    void testPointOnLSegmentOfTriangle() {
        Point p = new Point(3, 4);
        ArrayList<Point> polygon = new ArrayList<Point>();
        polygon.add(new Point(2, 4));
        polygon.add(new Point(4, 4));
        polygon.add(new Point(3, 2));
        assertTrue(p.inFaceGeom(polygon));
    }

    @Test
    void testPointInConvexPolygon() {
        Point p = new Point(7, 7);
        ArrayList<Point> polygon = new ArrayList<Point>();
        polygon.add(new Point(6, 9));
        polygon.add(new Point(8, 8));
        polygon.add(new Point(9, 7));
        polygon.add(new Point(8, 6));
        polygon.add(new Point(6, 7));
        assertTrue(p.inFaceGeom(polygon));
    }

    @Test
    void testPointOutConvexPolygon() {
        Point p = new Point(5, 3);
        ArrayList<Point> polygon = new ArrayList<Point>();
        polygon.add(new Point(6, 9));
        polygon.add(new Point(8, 8));
        polygon.add(new Point(9, 7));
        polygon.add(new Point(8, 6));
        polygon.add(new Point(6, 7));
        assertFalse(p.inFaceGeom(polygon));
    }

    @Test
    void testPointOnSegmentOfConvexPolygon() {
        Point p = new Point(6, 8);
        ArrayList<Point> polygon = new ArrayList<Point>();
        polygon.add(new Point(6, 9));
        polygon.add(new Point(8, 8));
        polygon.add(new Point(9, 7));
        polygon.add(new Point(8, 6));
        polygon.add(new Point(6, 7));
        assertTrue(p.inFaceGeom(polygon));
    }

    @Test
    void testPointInNonConvexPolygon() {
        Point p = new Point(18, 6);
        ArrayList<Point> polygon = new ArrayList<Point>();
        polygon.add(new Point(16, 6));
        polygon.add(new Point(17, 9));
        polygon.add(new Point(19, 6));
        polygon.add(new Point(21, 9));
        polygon.add(new Point(22, 6));
        polygon.add(new Point(19, 4));
        assertTrue(p.inFaceGeom(polygon));
    }

    @Test
    void testPointOutNonConvexPolygon() {
        Point p = new Point(19, 8);
        ArrayList<Point> polygon = new ArrayList<Point>();
        polygon.add(new Point(16, 6));
        polygon.add(new Point(17, 9));
        polygon.add(new Point(19, 6));
        polygon.add(new Point(21, 9));
        polygon.add(new Point(22, 6));
        polygon.add(new Point(19, 4));
        assertFalse(p.inFaceGeom(polygon));
    }


}
