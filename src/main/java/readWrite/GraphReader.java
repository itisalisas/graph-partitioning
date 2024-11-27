package readWrite;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import graph.*;

public class GraphReader {
	
	public static Vertex readVertex(Graph<Vertex> graph, Scanner sc) {
		long name = sc.nextLong();
		String xStr = sc.next().replace(',', '.');
		String yStr = sc.next().replace(',', '.');
		double x = Double.parseDouble(xStr);
		double y = Double.parseDouble(yStr);
		Vertex ans = new Vertex(name, x, y);
		return graph.addVertex(ans);
	}

	/**
	 * file format: n (Vertices number) name x y (of Vertex) n1 (Number of out
	 * edges) name1 x1 y1 (of out vertex) length1 (edge length) ... long double x2
	 * long long double x2 double
	 */
	public static void readGraphFromFile(Graph<Vertex> graph, String inFilename) throws FileNotFoundException {
		graph.getEdges().clear();
		Scanner sc = new Scanner(new File(inFilename));
		int n = 0;
		n = sc.nextInt();
		int ni = 0;
		double length = 0;
		Vertex vi;
		for (int i = 0; i < n && sc.hasNext(); i++) {
			// read Vertex..
			vi = readVertex(graph, sc);
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
			vi = readVertex(graph, sc);
			ni = sc.nextInt();
			for (int j = 0; j < ni && sc.hasNext(); j++) {
				vj = readVertex(graph, sc);
				String lengthStr = sc.next().replace(',', '.');
				length = Double.parseDouble(lengthStr);
				graph.getEdges().get(vi).put(vj, new Edge(length));
			}
		}
		sc.close();
	}
	
	public void readGraphFromOSM(Point center, int dist) {

	}
}
