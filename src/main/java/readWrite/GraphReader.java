package readWrite;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import graph.*;

public class GraphReader {
	
	public Vertex readVertex(Graph<Vertex> graph, Scanner sc, boolean geodetic) {
		long name = sc.nextLong();
		String xStr = sc.next().replace(',', '.');
		String yStr = sc.next().replace(',', '.');
		double x = Double.parseDouble(xStr);
		double y = Double.parseDouble(yStr);
		Vertex ans = new Vertex(name, x, y);
		if (geodetic) {
			CoordinateConversion cc = new CoordinateConversion();
			cc.toEuclidean(ans, null);
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
		int n = 0;
		n = sc.nextInt();
		int ni = 0;
		double length = 0;
		Vertex vi;
		System.out.println("Vertices num: " + n);
		for (int i = 0; i < n && sc.hasNext(); i++) {
			// read Vertex..
			vi = readVertex(graph, sc, geodetic);
			sc.nextLine();
		}
		sc.close();

		sc = new Scanner(new File(inFilename));
		n = 0;
		n = sc.nextInt();
		ni = 0;
		length = 0;
		Vertex vj;
		System.out.println("Readed all Vertices");

		for (int i = 0; i < n && sc.hasNext(); i++) {
			// read Vertex..
			vi = readVertex(graph, sc, geodetic);
			ni = sc.nextInt();
			for (int j = 0; j < ni && sc.hasNext(); j++) {
				vj = readVertex(graph, sc, geodetic);
				String lengthStr = sc.next().replace(',', '.');
				length = Double.parseDouble(lengthStr);
				// if (length < 0.001) {
				// 	smallEdgesNum++;
				// }
				graph.getEdges().get(vi).put(vj, new Edge(length));
				// System.out.println(vi.getLength(vj) + " " + length);
				// if (Math.abs(vi.getLength(vj) - length) > 50) {
				// 	countUncorrectedges++;
				// }
			}
		}
		// System.out.println("small edges of graph number: " + smallEdgesNum);
		// System.out.println("num edges length err > 50    " + countUncorrectedges);
		sc.close();
	}
	
	public void readGraphFromOSM(Point center, int dist) {

	}
}
