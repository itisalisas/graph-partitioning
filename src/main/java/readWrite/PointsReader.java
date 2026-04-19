package readWrite;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import graph.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PointsReader {

    private static final Logger logger = LoggerFactory.getLogger(PointsReader.class);
    public CoordinateConversion coordinateConversion;
	
	public PointsReader(CoordinateConversion coordinateConversion) {
		this.coordinateConversion = coordinateConversion;
	}

	// TODO - fix order x - y
	public Vertex readVertex(Scanner sc, boolean geodetic) {
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
            coordinateConversion.toEuclidean(ans);
		}
		return ans;
	}

	public List<Vertex> readWeightedPoints(String inFilename, boolean geodetic) {
		List<Vertex> vertices = new ArrayList<>();
        try (Scanner sc = new Scanner(new File(inFilename))) {
            int n = sc.nextInt();
            for (int i = 0; i < n && sc.hasNext(); i++) {
                vertices.add(readVertex(sc, geodetic));
            }
        } catch (FileNotFoundException e) {
            logger.error("File for weighted points not found: {}", inFilename);
            throw new RuntimeException(e);
        }
		return vertices;
	}
}
