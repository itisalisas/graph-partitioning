package readWrite;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import graph.Edge;
import graph.Graph;
import graph.Point;
import graph.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphReader {
	private static final Logger logger = LoggerFactory.getLogger(GraphReader.class);

	public CoordinateConversion coordConver;

	public GraphReader() {

	}
	
	public GraphReader(CoordinateConversion coordConver) {
		this.coordConver = coordConver;
	}

	public Vertex readVertex(Graph<Vertex> graph, Scanner sc, boolean geodetic) {
		long name = sc.nextLong();
		String xStr = sc.next().replace(',', '.');
		String yStr = sc.next().replace(',', '.');
		double x = Double.parseDouble(xStr);
		double y = Double.parseDouble(yStr);
		Vertex ans = new Vertex(name, x, y);
		if (geodetic) {
			coordConver.toEuclidean(ans);
		}
		return graph.addVertex(ans);
	}

	/**
	 * file format: n (Vertices number) name x y (of Vertex) n1 (Number of out
	 * edges) name1 x1 y1 (of out vertex) length1 (edge length) ... long double x2
	 * long long double x2 double
	 */
	public void readGraphFromFile(Graph<Vertex> graph, String inFilename, boolean geodetic) throws FileNotFoundException {
		graph.getEdges().clear();
		Scanner sc = new Scanner(new File(inFilename));
		int n = sc.nextInt();
		logger.info("Vertices num: {}", n);
		for (int i = 0; i < n && sc.hasNext(); i++) {
			readVertex(graph, sc, geodetic);
			sc.nextLine();
		}
		sc.close();

		sc = new Scanner(new File(inFilename));
		n = sc.nextInt();
        int ni;
        double length;
        Vertex vi, vj;
		logger.info("Read all vertices");

		for (int i = 0; i < n && sc.hasNext(); i++) {
			vi = readVertex(graph, sc, geodetic);
			ni = sc.nextInt();
			for (int j = 0; j < ni && sc.hasNext(); j++) {
				vj = readVertex(graph, sc, geodetic);
				String lengthStr = sc.next().replace(',', '.');
				length = Double.parseDouble(lengthStr);
				graph.getEdges().get(vi).put(vj, new Edge(length));
			}
		}
		sc.close();
	}
}