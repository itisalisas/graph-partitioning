package readWrite;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import graph.Graph;
import graph.Vertex;

public class GraphWriter {

	public <T extends Vertex> void printGraphToFile(Graph<T> graph, String outFileName) throws IOException {
		FileWriter out = new FileWriter(outFileName, false);
		out.write(String.format("%d %n", graph.getEdges().size()));
		for (Vertex begin : graph.getEdges().keySet()) {
			out.write(String.format("%d %f %f %d ", begin.getName(), begin.getX(), begin.getY(), graph.getEdges().get(begin).size()));
			for (Vertex end : graph.getEdges().get(begin).keySet()) {
				out.write(String.format("%d %f %f %f ", end.getName(), end.getX(), end.getY(),
						graph.getEdges().get(begin).get(end).getLength()));
			}
			out.append('\n');
		}
		out.close();
	}
	
	public void printVerticesToFile(List<Vertex> vertices, File file) {
		for (Vertex vertex : vertices) {
			try {
				vertex.printVertexToFile(file);
			} catch (Exception e) {
				throw new RuntimeException("Can't print vertex to file " + file.getName());
			}
		}
	}
}
