package readWrite;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;

public class GraphWriter {

	public <T extends Vertex> void printGraphToFile(Graph<T> graph, String outputDirectory, String outFileName) throws IOException {
		PartitionWriter.createOutputDirectory(outputDirectory);
		FileWriter out = new FileWriter(outputDirectory + File.separator + outFileName, false);
		out.write(String.format("%d %n", graph.getEdges().size()));
		for (Vertex begin : graph.getEdges().keySet()) {
			out.write(String.format("%d %f %f %d %d ", begin.getName(), begin.getX(), begin.getY(), (int)begin.getWeight(), graph.getEdges().get(begin).size()));
			for (Vertex end : graph.getEdges().get(begin).keySet()) {
				out.write(String.format("%d %f %f %f ", end.getName(), end.getX(), end.getY(),
						graph.getEdges().get(begin).get(end).getLength()));
			}
			out.append('\n');
		}
		out.close();
	}

		public <T extends Vertex> void printDualGraphWithWeightsToFile(Graph<VertexOfDualGraph> graph, 
														HashMap<VertexOfDualGraph, Integer> dualVertexToPartNumber,
														int partsNumber,
														String outputDirectory, 
														String outFileName) throws IOException {
		PartitionWriter.createOutputDirectory(outputDirectory);
		FileWriter out = new FileWriter(outputDirectory + File.separator + outFileName, false);
		out.write(String.format("%d\n", graph.getEdges().size()));
		for (Vertex begin : graph.getEdges().keySet()) {
			out.write(String.format("%d %f %f %d %d ", begin.getName(), begin.getX(), begin.getY(), begin.getWeight(), graph.getEdges().get(begin).size()));
			for (Vertex end : graph.getEdges().get(begin).keySet()) {
				out.write(String.format("%d %f %f %f ", end.getName(), end.getX(), end.getY(),
						graph.getEdges().get(begin).get(end).getLength()));
			}
			out.append('\n');
		}
		out.close();
	}

	public <T extends Vertex> void printDualGraphToFile(Graph<VertexOfDualGraph> graph, 
														HashMap<VertexOfDualGraph, Integer> dualVertexToPartNumber,
														int partsNumber,
														String outputDirectory, 
														String outFileName) throws IOException {
		PartitionWriter.createOutputDirectory(outputDirectory);
		FileWriter out = new FileWriter(outputDirectory + File.separator + outFileName, false);
		out.write(String.format("%d %d\n", graph.getEdges().size(), partsNumber));
		for (Vertex begin : graph.getEdges().keySet()) {
			out.write(String.format("%d %d %f %f %d ", begin.getName(), dualVertexToPartNumber.get(begin), begin.getX(), begin.getY(), graph.getEdges().get(begin).size()));
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
