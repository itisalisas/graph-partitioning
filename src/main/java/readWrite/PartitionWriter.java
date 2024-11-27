package readWrite;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;

import graph.Vertex;

public class PartitionWriter {
	
	public static <T extends Vertex> void writePartitionToFile(HashSet<T> part, Double cutWeight, File outFile) throws IOException {
		FileWriter out = new FileWriter(outFile, false);
		out.write(String.format("%f\n", cutWeight));
		out.write(String.format("%d\n", part.size()));
		for (T v : part) {
			out.write(String.format("%d %f %f %f\n", v.getName(), v.getX(), v.getY(), v.getWeight()));
		}
		out.close();
	}

}
