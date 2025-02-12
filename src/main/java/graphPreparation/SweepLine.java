package graphPreparation;

import graph.*;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import addingPoints.CoordinateConstraintsForFace;

enum ActionType {
	ADD, DELETE, POINT
}

record Action(double x, int edgeNum, Vertex vertex, ActionType type) {
}




public class SweepLine {
	double inaccuracy;

	Comparator<Vertex> backXComp = new Comparator<Vertex>(){
		@Override
		public int compare(Vertex o1, Vertex o2) {
			return o1.getX() > o2.getX() ? -1 : o1.getX() < o2.getX() ? 1 : 0;
		}
	};
	
	Comparator<Vertex> straightXComp = new Comparator<Vertex>(){
		@Override
		public int compare(Vertex o1, Vertex o2) {
			return o1.getX() < o2.getX() ? -1 : o1.getX() > o2.getX() ? 1 : 0;
		}
	};

	Comparator<Vertex> backYComp = new Comparator<Vertex>(){
		@Override
		public int compare(Vertex o1, Vertex o2) {
			return o1.getY() > o2.getY() ? -1 : o1.getY() < o2.getY() ? 1 : 0;
		}
	};

	Comparator<Vertex> straightYComp = new Comparator<Vertex>(){
		@Override
		public int compare(Vertex o1, Vertex o2) {
			return o1.getY() < o2.getY() ? -1 : o1.getY() > o2.getY() ? 1 : 0;
		}
	};
	

	public SweepLine() {
		this.inaccuracy = 0.000001;
	}


	public SweepLine(double inaccuracy) {
		this.inaccuracy = inaccuracy;
	}


	public void makePlanar(Graph<Vertex> gph) {
		Point minPoint = findMinPoint(gph);
		Point maxPoint = findMaxPoint(gph);
		BufferedImage im = new BufferedImage((int) (maxPoint.x - minPoint.x) * 10,
											 (int) (maxPoint.y - minPoint.y) * 10,
											 BufferedImage.TYPE_INT_RGB);
		//drawGraph(im, minPoint, gph, "before");
		EdgeOfGraph<Vertex>[] edgesList = gph.edgesArray();
		ArrayList<ArrayList<Vertex>> intersectionPoints = findPointsOfIntersection(edgesList);
		addIntersectionPoints(gph, edgesList, intersectionPoints);
		// for (Vertex v : gph.getEdges().keySet()) {
		// 	if (v.getWeight() == 0) {
		// 		System.out.println("0 weight vertex: " + v.x + " " + v.y);
		// 	}
		// }
		//drawNewPoints(im, minPoint, gph, "after");
		//drawGraph(im, minPoint, gph, "after");
		int smallEdgesNum = 0;
		for (Vertex begin : gph.getEdges().keySet()) {
			for (Vertex end : gph.getEdges().get(begin).keySet()) {
				if (gph.getEdges().get(begin).get(end).length < 0.001) {
					smallEdgesNum++;
				}
			}
		}
		System.out.println("small edges num after sweepline: " + smallEdgesNum);
	}
	
		
	private void drawNewPoints(BufferedImage im, Point minPoint, Graph<Vertex> gph, String string) {
		Graphics g = im.getGraphics();
		g.setColor(Color.RED);
		g.fillOval(100, 100, 8, 8);
		for (Vertex v : gph.getEdges().keySet()) {
			if (v.getWeight() == 0) {
				g.setColor(Color.RED);
				g.fillOval((int) (v.x - minPoint.x) * 10, (int) (v.y - minPoint.y) * 10, 8, 8);
			}
		}
		g.dispose();
		try {
			ImageIO.write(im, "PNG", 
				new File(("src/main/output/drawSweepLine/" + string + ".png").replace('/', File.separatorChar)));
		} catch (IOException e) {
			System.out.println("write image error");
			e.printStackTrace();
		}
	}
		
		
	private void drawGraph(BufferedImage im, Point minPoint, Graph<Vertex> gph, String string) {
		Graphics g = im.getGraphics();
		g.setColor(Color.GREEN);
		for (Vertex v : gph.getEdges().keySet()) {
			g.setColor(Color.GREEN);
			g.fillOval((int) (v.x - minPoint.x) * 10, (int) (v.y - minPoint.y) * 10, 8, 8);
			for (Vertex ver : gph.getEdges().get(v).keySet()) {
				g.setColor(Color.BLUE);
				g.drawLine((int) (v.x - minPoint.x) * 10, (int) (v.y - minPoint.y) * 10, (int) (ver.x - minPoint.x) * 10, (int) (ver.y - minPoint.y) * 10);
			}
		}
        g.dispose();
		try {
			ImageIO.write(im, "PNG", 
				new File(("src/main/output/drawSweepLine/" + string + ".png").replace('/', File.separatorChar)));
		} catch (IOException e) {
			System.out.println("write image error");
			e.printStackTrace();
		}
	}

		
	private Point findMinPoint(Graph<Vertex> gph) {
		Point ans = new Point();
		boolean firstPoint = true;
		for (Vertex v : gph.getEdges().keySet()) {
			if (firstPoint) {
				ans.x = v.x;
				ans.y = v.y;
				firstPoint = false;
			} else {
				if (ans.x > v.x) {
					ans.x = v.x;
				}
				if (ans.y > v.y) {
					ans.y = v.y;
				}
			}
		}
		ans.x = ans.x - 10;
		ans.y = ans.y - 10;
		return ans;
	}


	private Point findMaxPoint(Graph<Vertex> gph) {
		Point ans = new Point();
		boolean firstPoint = true;
		for (Vertex v : gph.getEdges().keySet()) {
			if (firstPoint) {
				ans.x = v.x;
				ans.y = v.y;
				firstPoint = false;
			} else {
				if (ans.x < v.x) {
					ans.x = v.x;
				}
				if (ans.y < v.y) {
					ans.y = v.y;
				}
			}
		}
		ans.x = ans.x + 10;
		ans.y = ans.y + 10;
		return ans;
	}


	private void addIntersectionPoints(Graph<Vertex> gph,
									   EdgeOfGraph<Vertex>[] edgesList,
									   ArrayList<ArrayList<Vertex>> intersectionPoints) {
		for (int i = 0; i < edgesList.length; i++) {
			//List of intersection points in edge i
			ArrayList<Vertex> currList = intersectionPoints.get(i);
			if (currList == null ||
			   (currList != null && currList.size() == 0) ||
				gph.getEdges().get(edgesList[i].begin) == null ||
				gph.getEdges().get(edgesList[i].begin).get(edgesList[i].end) == null) {

				continue;
			}

			gph.deleteEdge(edgesList[i].begin, edgesList[i].end);
			//intersectionPoints.get(i).add(0, edgesList[i].begin);

			if (edgesList[i].begin.getX() > edgesList[i].end.getX()) {
				Collections.sort(currList, backXComp);
			} else if (edgesList[i].begin.getX() < edgesList[i].end.getX()) {
				Collections.sort(currList, straightXComp);
			} else {
				if (edgesList[i].begin.getY() < edgesList[i].end.getY()) {
					Collections.sort(currList, straightYComp);
				} else {
					Collections.sort(currList, backYComp);
				}
			}
			//Assert что первая точка begin

			currList.add(currList.size() - 1, edgesList[i].end);
			Vertex prevVert = edgesList[i].begin;
    		for (Vertex currVertex: currList) {
        		if (prevVert.getLength(currVertex) <= inaccuracy && (currVertex != edgesList[i].end)) {
        			continue;
       		 	}
        		gph.addEdge(prevVert, currVertex, prevVert.getLength(currVertex));
        		prevVert = currVertex;
    		}
		}

	}

	public ArrayList<ArrayList<Vertex>> findPointsOfIntersection(EdgeOfGraph<Vertex>[] edgesList) {
		ArrayList<ArrayList<Vertex>> intersectionPoints = new ArrayList<ArrayList<Vertex>>();
		for (int i = 0; i < edgesList.length; i++) {
			intersectionPoints.add(new ArrayList<Vertex>());
		}
		ArrayList<Action> actions = initActions(edgesList);
		Collections.sort(actions, new Comparator<Action>() {
			@Override
			public int compare(Action a1, Action a2) {
				return a1.x() < a2.x() ? -1
						: a1.x() > a2.x() ? 1 : a1.type() == ActionType.ADD ? 1 : a2.type() == ActionType.ADD ? -1 : 0;
			}
		});
		HashMap<Integer, EdgeOfGraph<Vertex>> actualEdge = new HashMap<Integer, EdgeOfGraph<Vertex>>();
		for (int i = 0; i < actions.size(); i++) {
			if (actions.get(i).type() != ActionType.ADD) {
				actualEdge.remove(actions.get(i).edgeNum());
				continue;
			}
			for (int edgeNum : actualEdge.keySet()) {
				if (!edgesList[actions.get(i).edgeNum()].intersect(actualEdge.get(edgeNum))) {
					continue;
				}
				// check vertical
				if (edgesList[actions.get(i).edgeNum()].vertical() && edgesList[edgeNum].vertical()) {
					checkVerticalEdges(actions.get(i).edgeNum(), edgeNum, edgesList, intersectionPoints);
					// check horizontal
				} else if (edgesList[actions.get(i).edgeNum()].horizontal() && edgesList[edgeNum].horizontal()) {
					chechHorizontalEdges(actions.get(i).edgeNum(), edgeNum, edgesList, intersectionPoints);
					// check normal (not vertical, not horizontal)
				} else {
					Vertex intersecPoint = edgesList[actions.get(i).edgeNum()]
							.intersectionPoint(actualEdge.get(edgeNum));
					if (intersecPoint != null) {
						intersectionPoints.get(actions.get(i).edgeNum()).add(intersecPoint);
						intersectionPoints.get(edgeNum).add(intersecPoint);
					}
				}

			}
			actualEdge.put(actions.get(i).edgeNum(), edgesList[actions.get(i).edgeNum()]);
		}
		return intersectionPoints;
	}

	private <T extends Vertex> void chechHorizontalEdges(int edgeNum1,
									  int edgeNum2,
									  EdgeOfGraph<T>[] edgesList,
									  ArrayList<ArrayList<Vertex>> intersectionPoints) {
		if (edgesList[edgeNum1].includeForX(edgesList[edgeNum2].begin)) {
			intersectionPoints.get(edgeNum1).add(edgesList[edgeNum2].begin);
		}
		if (edgesList[edgeNum1].includeForX(edgesList[edgeNum2].end)) {
			intersectionPoints.get(edgeNum1).add(edgesList[edgeNum2].end);
		}
		if (edgesList[edgeNum2].includeForX(edgesList[edgeNum1].begin)) {
			intersectionPoints.get(edgeNum2).add(edgesList[edgeNum1].begin);
		}
		if (edgesList[edgeNum2].includeForX(edgesList[edgeNum1].end)) {
			intersectionPoints.get(edgeNum2).add(edgesList[edgeNum1].end);
		}

	}

	private <T extends Vertex> void checkVerticalEdges(int edgeNum1,
									int edgeNum2,
									EdgeOfGraph<T>[] edgesList,
									ArrayList<ArrayList<Vertex>> intersectionPoints) {
		if (edgesList[edgeNum1].includeForY(edgesList[edgeNum2].begin)) {
			intersectionPoints.get(edgeNum1).add(edgesList[edgeNum2].begin);
		}
		if (edgesList[edgeNum1].includeForY(edgesList[edgeNum2].end)) {
			intersectionPoints.get(edgeNum1).add(edgesList[edgeNum2].end);
		}
		if (edgesList[edgeNum2].includeForY(edgesList[edgeNum1].begin)) {
			intersectionPoints.get(edgeNum2).add(edgesList[edgeNum1].begin);
		}
		if (edgesList[edgeNum2].includeForY(edgesList[edgeNum1].end)) {
			intersectionPoints.get(edgeNum2).add(edgesList[edgeNum1].end);
		}

	}

	private <T extends Vertex> ArrayList<Action> initActions(EdgeOfGraph<T>[] edgesList) {
		ArrayList<Action> result = new ArrayList<Action>();
		for (int i = 0; i < edgesList.length; i++) {
			result.add(
					new Action(Math.min(edgesList[i].begin.getX(), edgesList[i].end.getX()), i, null, ActionType.ADD));
			result.add(new Action(Math.max(edgesList[i].begin.getX(), edgesList[i].end.getX()), i, null,
					ActionType.DELETE));
		}
		return result;
	}

	/**
	 * @param diagList                 - array diagonals of rectangles containing
	 *                                 faces
	 * @param returnFromSimplification - diagonal and face matching
	 * @param newVertices              - vertices for which we determine the
	 *                                 position
	 * @return matching: vertex - face
	 */
	public HashMap<Vertex, VertexOfDualGraph> findFacesOfVertices(EdgeOfGraph<Vertex>[] diagList,
																  HashMap<EdgeOfGraph<Vertex>,
																  VertexOfDualGraph> returnFromSimplification,
																  HashSet<Vertex> newVertices) {
		HashMap<Vertex, VertexOfDualGraph> res = new HashMap<Vertex, VertexOfDualGraph>();
		ArrayList<Action> actions = initActions(diagList);
		addPointToActions(actions, newVertices);
		// System.out.println("action size: " + actions.size());
		Collections.sort(actions, new Comparator<Action>() {
			@Override
			public int compare(Action a1, Action a2) {
				return a1.x() < a2.x() ? -1
						: a1.x() > a2.x() ? 1 : a1.type() == ActionType.ADD ? 1 : a2.type() == ActionType.ADD ? -1 : 0;
			}
		});
		TreeMap<Double, HashSet<EdgeOfGraph<Vertex>>> actualEdges = new TreeMap<Double, HashSet<EdgeOfGraph<Vertex>>>(
				new Comparator<Double>() {
					@Override
					public int compare(Double o1, Double o2) {
						return o1 < o2 ? -1 : o1 > o2 ? 1 : 0;
					}

				});
		//HashMap<Integer, EdgeOfGraph> actualEdge = new HashMap<Integer, EdgeOfGraph>();
		for (int i = 0; i < actions.size(); i++) {
			if (actions.get(i).type() == ActionType.DELETE) {
				//System.out.println("DELETE");
				//actualEdge.remove(actions.get(i).edgeNum());

				EdgeOfGraph<Vertex> actionEdge = diagList[actions.get(i).edgeNum()];
				SortedMap<Double, HashSet<EdgeOfGraph<Vertex>>> tmp = actualEdges.subMap(actionEdge.begin.getY(), actionEdge.end.getY());
				for (Double d : tmp.keySet()) {
					if (actualEdges.get(d) != null) {
						actualEdges.get(d).remove(diagList[actions.get(i).edgeNum()]);
						// if (actualEdges.get(d).size() == 0) {
						// 	actualEdges.remove(d);
						// }
					}
				}
				
				if (actualEdges.get(diagList[actions.get(i).edgeNum()].begin.getY()) != null) {
					actualEdges.get(diagList[actions.get(i).edgeNum()].begin.getY()).remove(diagList[actions.get(i).edgeNum()]);
					if (actualEdges.get(diagList[actions.get(i).edgeNum()].begin.getY()).size() == 0) {
						actualEdges.remove(diagList[actions.get(i).edgeNum()].begin.getY());
					}
				}
				
				if (actualEdges.get(diagList[actions.get(i).edgeNum()].end.getY()) != null) {
					actualEdges.get(diagList[actions.get(i).edgeNum()].end.getY()).remove(diagList[actions.get(i).edgeNum()]);
					if (actualEdges.get(diagList[actions.get(i).edgeNum()].end.getY()).size() == 0) {
						actualEdges.remove(diagList[actions.get(i).edgeNum()].end.getY());
					}
				}				
				// for (Double d : actualEdges.keySet()) {
				// 	System.out.println("	key:" + d + " :");
				// 	for (EdgeOfGraph ed : actualEdges.get(d)) {
				// 		System.out.println("	 " + returnFromSimplification.get(ed).getName());
				// 		System.out.print("	");
				// 		for (Vertex v : returnFromSimplification.get(ed).getVerticesOfFace()) {
				// 			System.out.print(" " + v.getName());
				// 		}
				// 		System.out.println();
				// 	}
				// 	System.out.println();
				// }
				// System.out.println(actualEdges);
				continue;
			}
			if (actions.get(i).type() == ActionType.ADD) {
				//System.out.println("ADD");

				//actualEdge.put(actions.get(i).edgeNum(), diagList[actions.get(i).edgeNum()]);
				
				EdgeOfGraph<Vertex> actionEdge = diagList[actions.get(i).edgeNum()];
				
				HashSet<EdgeOfGraph<Vertex>> intersectingFacesBegin = null;
				if (actualEdges.floorKey(actionEdge.begin.getY()) != null) {
					intersectingFacesBegin = actualEdges.get(actualEdges.floorKey(actionEdge.begin.getY()));
				}
				
				HashSet<EdgeOfGraph<Vertex>> intersectingFacesEnd = null;
				if (actualEdges.floorKey(actionEdge.end.getY()) !=null) {
					intersectingFacesEnd = actualEdges.get(actualEdges.floorKey(actionEdge.end.getY()));
				}
				
				if (intersectingFacesBegin == null) {
					intersectingFacesBegin = new HashSet<EdgeOfGraph<Vertex>>();
				}
				actualEdges.put(actionEdge.begin.getY(), intersectingFacesBegin);
				
				if (intersectingFacesEnd == null) {
					intersectingFacesEnd = new HashSet<EdgeOfGraph<Vertex>>();
				}
				actualEdges.put(actionEdge.end.getY(), intersectingFacesEnd);

				SortedMap<Double, HashSet<EdgeOfGraph<Vertex>>> tmp = actualEdges.subMap(actionEdge.begin.getY(), actionEdge.end.getY());
				for (Double d : tmp.keySet()) {
					if (actionEdge != null) {
						//System.out.println("edge " + actionEdge.begin.getY() + " " + actionEdge.end.getY());
						actualEdges.get(d).add(actionEdge);
						//System.out.println(d + " " + actualEdges.get(d).size() + "  " + actualEdges.get(d));
					}
				}

				// for (Double d : actualEdges.keySet()) {
				// 	System.out.println("	key:" + d + " size " + actualEdges.get(d).size() +  " :");
				// 	for (EdgeOfGraph ed : actualEdges.get(d)) {
				// 		System.out.println("	 " + returnFromSimplification.get(ed).getName());
				// 		System.out.print("	");
				// 		for (Vertex v : returnFromSimplification.get(ed).getVerticesOfFace()) {
				// 			System.out.print(" " + v.getName());
				// 		}
				// 		System.out.println();
				// 	}
				// 	System.out.println();
				// }
				// System.out.println(actualEdges);
				continue;
			}
			//System.out.println("POINT");
			Vertex vertex = actions.get(i).vertex();
			
			// for (Double d : actualEdges.keySet()) {
			// 	System.out.println("	key:" + d + " :");
			// 	for (EdgeOfGraph ed : actualEdges.get(d)) {
			// 		System.out.println("	 " + returnFromSimplification.get(ed).getName());
			// 		System.out.print("	");
			// 		for (Vertex v : returnFromSimplification.get(ed).getVerticesOfFace()) {
			// 			System.out.print(" " + v.getName());
			// 		}
			// 		System.out.println();
			// 	}
			// 	System.out.println();
			// }
			// System.out.println(actualEdges);
			if (actualEdges.floorKey(vertex.getY()) == null) {
				continue;
			}
			for (EdgeOfGraph<Vertex> vert : actualEdges.get(actualEdges.floorKey(vertex.getY()))) {
				// for (int k = 0; k < returnFromSimplification.get(vert).getVerticesOfFace().size(); k++) {
				// 	System.out.print(" " + returnFromSimplification.get(vert).getVerticesOfFace().get(k).getName());

				// }
				// System.out.println();
				if (!vertex.inRectangle(vert.begin, vert.end)) {
					continue;
				}
				
				if (vertex.inFaceGeom(returnFromSimplification.get(vert).getVerticesOfFace())) {
					res.put(vertex, returnFromSimplification.get(vert));
				}
			}
//			for (int edgeNum : actualEdge.keySet()) {
//				Vertex ver = actions.get(i).vertex();
//				// System.out.println("face: " +
//				// returnFromSimplification.get(actualEdge.get(edgeNum)).getName() + " " +
//				// "vertex: " + ver.getName());
//				if (ver.inRectangle(actualEdge.get(edgeNum).begin, actualEdge.get(edgeNum).end)) {
//					// System.out.println("vertex: " + ver.getName() + " in rect " +
//					// returnFromSimplification.get(actualEdge.get(edgeNum)).getName());
//					if (ver.inFaceGeom(returnFromSimplification.get(actualEdge.get(edgeNum)).getVerticesOfFace())) {
//						// System.out.println("vertex: " + ver.getName() + " in face " +
//						// returnFromSimplification.get(actualEdge.get(edgeNum)).getName());
//						res.put(ver, returnFromSimplification.get(actualEdge.get(edgeNum)));
//					}
//				}
//			}
		}
		return res;
	}

	private void addPointToActions(ArrayList<Action> actions, HashSet<Vertex> newVertices) {
		for (Vertex ver : newVertices) {
			actions.add(new Action(ver.getX(), -1, ver, ActionType.POINT));
		}

	}
}