package readWrite;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import graph.Vertex;

public class PointsReader {

    public CoordinateConversion coordConver;

	public PointsReader() {

	}
	
	public PointsReader(CoordinateConversion coordConver) {
		this.coordConver = coordConver;
	}

	// TODO - fix order x - y
	public Vertex readVertex(Scanner sc, boolean geodetic, boolean first) {
		long name = sc.nextLong();
		String xStr = sc.next().replace(',', '.');
		String yStr = sc.next().replace(',', '.');
		double y = Double.parseDouble(xStr);
		double x = Double.parseDouble(yStr);
		int length = sc.nextInt();
		int width = sc.nextInt();
		double vertexWeight = length * width / 10.0;
		Vertex ans = new Vertex(name, x, y, vertexWeight);
		if (geodetic) {
			coordConver.toEuclidean(ans);
		}
		return ans;
	}

	public List<Vertex> readWeightedPoints(String inFilename, boolean geodetic) throws FileNotFoundException {
		List<Vertex> vertices = new ArrayList<>();
        try (Scanner sc = new Scanner(new File(inFilename))) {
            int n = sc.nextInt();
            for (int i = 0; i < n && sc.hasNext(); i++) {
                vertices.add(readVertex(sc, geodetic, i == 0));
            }
        }
		return vertices;
	}
}
