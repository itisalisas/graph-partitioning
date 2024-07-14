package partitioningGraph;

import java.io.IOException;

public class Main {

	public static void main(String[] args) throws IOException {
		Graph gph = new Graph();
		/*
		 * file format:
		 * n (Vertices number)
		 * name x y (of Vertex) n1 (Number of out edges) name1 x1 y1 (of out vertex) length1 (edge length) ...
		 * int double x2		int						int double x2				double
		 */
		gph.readGraphFromFile("src\\partitioningGraph\\input.txt");
		Vertex a = new Vertex(44, new Point(5.6,7.8));
		gph.addVertex(a);
		gph.deleteVertex(new Vertex(11, new Point(2.1,3.1)));
		gph.addEdge(a, new Vertex(11, new Point(2.1,3.1)), 13);
		//gph.deleteEdge(a, new Vertex(11, new Point(2.1,3.1)));
		gph.deleteVertex(new Vertex(11, new Point(2.1,3.1)));
		gph.printGraphToFile("src\\partitioningGraph\\output.txt");
		/*
		 * Graph[] resultBubblePartiton;
		 * BalabcedPartitoning bubblePartition = new BalabcedPartitoning(*BubblePartitoning object*);
		 * resultBubblePartiton = bubblePartiton.partition(gph);
		 */
		/*
		 * Graph[] resultInertialFlowPartiton;
		 * BalabcedPartitoning InertialFlowPartiton = new BalabcedPartitoning(*InertialFlowPartiton object*);
		 * resultInertialFlowPartiton = InertialFlowPartiton.partition(gph);
		 */

	}

}
