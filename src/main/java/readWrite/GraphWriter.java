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

	public <T extends Vertex> void printGraphToFile(Graph<T> graph, String outputDirectory, String outFileName, boolean geodetic) throws IOException {
		PartitionWriter.createOutputDirectory(outputDirectory);
		FileWriter out = new FileWriter(outputDirectory + File.separator + outFileName, false);
		out.write(String.format("%d %n", graph.getEdges().size()));
		CoordinateConversion cc = null;
		if (geodetic) {
			cc = new CoordinateConversion();
		}
		for (Vertex begin : graph.getEdges().keySet()) {
			if (geodetic) {
				Vertex nBegin = cc.fromEuclidean(begin, null);
				out.write(String.format("%d %f %f %d ", begin.getName(), nBegin.getX(), nBegin.getY(),
						 graph.getEdges().get(begin).size()));
			}
			out.write(String.format("%d %f %f %d ", begin.getName(), begin.getX(), begin.getY(), 
					graph.getEdges().get(begin).size()));
			for (Vertex end : graph.getEdges().get(begin).keySet()) {
				if (geodetic) {
					Vertex nEnd = cc.fromEuclidean(end, null);
					out.write(String.format("%d %f %f %f ", end.getName(), nEnd.getX(), nEnd.getY(),
							graph.getEdges().get(begin).get(end).getLength()));
				}
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
														String outFileName,
														boolean geodetic) throws IOException {
		PartitionWriter.createOutputDirectory(outputDirectory);
		FileWriter out = new FileWriter(outputDirectory + File.separator + outFileName, false);
		out.write(String.format("%d %d\n", graph.getEdges().size(), partsNumber));
		CoordinateConversion cc = null;
		if (geodetic) {
			cc = new CoordinateConversion();
		}
		for (Vertex begin : graph.getEdges().keySet()) {
			if (geodetic) {
				Vertex nBegin = cc.fromEuclidean(begin, null);
				out.write(String.format("%d %d %f %f %d ", begin.getName(), dualVertexToPartNumber.get(begin), 
						nBegin.getX(), nBegin.getY(), graph.getEdges().get(begin).size()));
				continue;
			}
			out.write(String.format("%d %d %f %f %d ", begin.getName(), dualVertexToPartNumber.get(begin), 
					begin.getX(), begin.getY(), graph.getEdges().get(begin).size()));
			for (Vertex end : graph.getEdges().get(begin).keySet()) {
				if (geodetic) {
					Vertex nEnd = cc.fromEuclidean(end, null);
					out.write(String.format("%d %f %f %f ", end.getName(), nEnd.getX(), nEnd.getY(),
							graph.getEdges().get(begin).get(end).getLength()));
							continue;
				}
				out.write(String.format("%d %f %f %f ", end.getName(), end.getX(), end.getY(),
						graph.getEdges().get(begin).get(end).getLength()));
			}
			out.append('\n');
		}
		out.close();
	}
	
	public void printVerticesToFile(List<Vertex> vertices, File file, boolean geodetic) {
		CoordinateConversion cc = null;
		if (geodetic) {
			cc = new CoordinateConversion();
		}
		for (Vertex vertex : vertices) {
			try {
				if (geodetic) {
					Vertex nVertex = cc.fromEuclidean(vertex, null);
					nVertex.printVertexToFile(file);
				}
				vertex.printVertexToFile(file);
			} catch (Exception e) {
				throw new RuntimeException("Can't print vertex to file " + file.getName());
			}
		}
	}
}
