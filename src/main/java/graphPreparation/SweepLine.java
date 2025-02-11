package graphPreparation;

import graph.*;
import graph.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.SortedMap;
import java.util.TreeMap;

import java.lang.Object;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import addingPoints.CoordinateConstraintsForFace;

enum ActionType {
	ADD, DELETE, POINT
}

record Action(double x, int edgeNum, Vertex vertex, ActionType type) {
}

public class SweepLine {
	double inaccuracy;

	public SweepLine() {
		this.inaccuracy = 0.00001;
	}

	public SweepLine(double inaccuracy) {
		this.inaccuracy = inaccuracy;
	}


	public void makePlanar(Graph<Vertex> gph) {
		Point minPoint = findMinPoint(gph);
		Point maxPoint = findMaxPoint(gph);
		BufferedImage im = new BufferedImage((int) (maxPoint.x - minPoint.x) * 10, (int) (maxPoint.y - minPoint.y) * 10,
					 BufferedImage.TYPE_INT_RGB);
		drawGraph(im, minPoint, gph);
		EdgeOfGraph[] edgesList = gph.edgesArray();
		ArrayList<LinkedList<Vertex>> intersectionPoints = findPointsOfIntersection(edgesList, im, minPoint);
		addIntersectionPoints(gph, edgesList, intersectionPoints, im, minPoint);
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

	private void drawGraph(BufferedImage im, Point minPoint, Graph<Vertex> gph) {
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
				new File("src/main/output/drawSweepLine/first.png".replace('/', File.separatorChar)));
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
		}ans.x = ans.x + 10;
		ans.y = ans.y + 10;
		return ans;
	}
		
	private void addIntersectionPoints(Graph<Vertex> gph, EdgeOfGraph[] edgesList,
		ArrayList<LinkedList<Vertex>> intersectionPoints, BufferedImage im, Point minPoint) {
			int imNum = 0;
		for (int i = 0; i < edgesList.length; i++) {
			if (intersectionPoints.get(i) == null
					|| (intersectionPoints.get(i) != null && intersectionPoints.get(i).size() == 0)) {
				continue;
			}
			gph.deleteEdge(edgesList[i].begin, edgesList[i].end);
			intersectionPoints.get(i).addFirst(edgesList[i].begin);
			if (edgesList[i].begin.getX() > edgesList[i].end.getX()) {
				Collections.sort(intersectionPoints.get(i), new Comparator<Vertex>() {

					@Override
					public int compare(Vertex o1, Vertex o2) {
						return o1.getX() > o2.getX() ? -1 : o1.getX() < o2.getX() ? 1 : 0;
					}

				});

			} else if (edgesList[i].begin.getX() < edgesList[i].end.getX()) {
				Collections.sort(intersectionPoints.get(i), new Comparator<Vertex>() {
					@Override
					public int compare(Vertex o1, Vertex o2) {
						return o1.getX() < o2.getX() ? -1 : o1.getX() > o2.getX() ? 1 : 0;
					}

				});
			} else {
				if (edgesList[i].begin.getY() < edgesList[i].end.getY()) {
					Collections.sort(intersectionPoints.get(i), new Comparator<Vertex>() {
						@Override
						public int compare(Vertex o1, Vertex o2) {
							return o1.getY() < o2.getY() ? -1 : o1.getY() > o2.getY() ? 1 : 0;
						}

					});
				} else {
					Collections.sort(intersectionPoints.get(i), new Comparator<Vertex>() {
						@Override
						public int compare(Vertex o1, Vertex o2) {
							return o1.getY() > o2.getY() ? -1 : o1.getY() < o2.getY() ? 1 : 0;
						}

					});
				}
			}
			intersectionPoints.get(i).addLast(edgesList[i].end);
			int uselessPointsNum = 0;
			for (int j = 1; j < intersectionPoints.get(i).size(); j++) {
				if (intersectionPoints.get(i).get(j - 1).getLength(intersectionPoints.get(i).get(j)) <= inaccuracy) {
					uselessPointsNum++;
					if (intersectionPoints.get(i).get(j - 1).getLength(edgesList[i].begin) <= inaccuracy) {
						intersectionPoints.get(i).remove(j);
						intersectionPoints.get(i).add(j, edgesList[i].begin);
						
					} else if (intersectionPoints.get(i).get(j).getLength(edgesList[i].end) <= inaccuracy) {
						gph.deleteEdge(intersectionPoints.get(i).get(j - uselessPointsNum - 1),
								intersectionPoints.get(i).get(j - uselessPointsNum));
						gph.deleteVertex(intersectionPoints.get(i).get(j - uselessPointsNum));
						gph.addEdge(intersectionPoints.get(i).get(j - uselessPointsNum - 1), edgesList[i].end,
								intersectionPoints.get(i).get(j - uselessPointsNum - 1).getLength(edgesList[i].end));

						if (imNum % 10 == 0) {
							Graphics g = im.getGraphics();
							g.setColor(Color.PINK);
							g.fillOval((int) (intersectionPoints.get(i).get(j - uselessPointsNum - 1).x - minPoint.x) * 10,
							 (int) (intersectionPoints.get(i).get(j - uselessPointsNum - 1).y - minPoint.y) * 10, 8, 8);
        					g.dispose();
							try {
								ImageIO.write(im, "PNG", 
									new File(("src/main/output/drawSweepLine/2_" + imNum + ".png").replace('/', File.separatorChar)));
							} catch (IOException e) {
								System.out.println("write image error");
								e.printStackTrace();
							}
						}
						imNum++;
						break;
					} else {
						intersectionPoints.get(i).remove(j);
						intersectionPoints.get(i).add(j, intersectionPoints.get(i).get(j - 1));
					}
					continue;
				}
				gph.addEdge(intersectionPoints.get(i).get(j - 1), intersectionPoints.get(i).get(j),
					intersectionPoints.get(i).get(j - 1).getLength(intersectionPoints.get(i).get(j)));
				if (imNum % 10 == 0) {
					Graphics g = im.getGraphics();
					g.setColor(Color.PINK);
					g.fillOval((int) (intersectionPoints.get(i).get(j - 1).x - minPoint.x) * 10,
					 (int) (intersectionPoints.get(i).get(j - 1).y - minPoint.y) * 10, 8, 8);
        			g.dispose();
					try {
						ImageIO.write(im, "PNG", 
							new File(("src/main/output/drawSweepLine/2_" + imNum + ".png").replace('/', File.separatorChar)));
					} catch (IOException e) {
						System.out.println("write image error");
						e.printStackTrace();
					}
				}
				imNum++;
				uselessPointsNum = 0;
			}
		}

	}

	public ArrayList<LinkedList<Vertex>> findPointsOfIntersection(EdgeOfGraph[] edgesList, BufferedImage im, Point minPoint) {
		ArrayList<LinkedList<Vertex>> intersectionPoints = new ArrayList<LinkedList<Vertex>>();
		for (int i = 0; i < edgesList.length; i++) {
			intersectionPoints.add(new LinkedList<Vertex>());
		}
		ArrayList<Action> actions = initActions(edgesList);
		Collections.sort(actions, new Comparator<Action>() {
			@Override
			public int compare(Action a1, Action a2) {
				return a1.x() < a2.x() ? -1
						: a1.x() > a2.x() ? 1 : a1.type() == ActionType.ADD ? 1 : a2.type() == ActionType.ADD ? -1 : 0;
			}
		});
		HashMap<Integer, EdgeOfGraph> actualEdge = new HashMap<Integer, EdgeOfGraph>();
		int imNum = 0;
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
						if (imNum % 10 == 0) {
							Graphics g = im.getGraphics();
							g.setColor(Color.RED);
							g.fillOval((int) (intersecPoint.x - minPoint.x) * 10, (int) (intersecPoint.y - minPoint.y) * 10, 8, 8);
        					g.dispose();
							try {
								ImageIO.write(im, "PNG", 
									new File(("src/main/output/drawSweepLine/" + imNum + ".png").replace('/', File.separatorChar)));
							} catch (IOException e) {
								System.out.println("write image error");
								e.printStackTrace();
							}
						}	
						imNum++;
						intersectionPoints.get(edgeNum).add(intersecPoint);
					}
				}

			}
			actualEdge.put(actions.get(i).edgeNum(), edgesList[actions.get(i).edgeNum()]);
		}
		return intersectionPoints;
	}

	private void chechHorizontalEdges(int edgeNum1, int edgeNum2, EdgeOfGraph[] edgesList,
			ArrayList<LinkedList<Vertex>> intersectionPoints) {
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

	private void checkVerticalEdges(int edgeNum1, int edgeNum2, EdgeOfGraph[] edgesList,
			ArrayList<LinkedList<Vertex>> intersectionPoints) {
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

	private ArrayList<Action> initActions(EdgeOfGraph[] edgesList) {
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
	public HashMap<Vertex, VertexOfDualGraph> findFacesOfVertices(EdgeOfGraph[] diagList,
			HashMap<EdgeOfGraph, VertexOfDualGraph> returnFromSimplification, HashSet<Vertex> newVertices) {
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
		TreeMap<Double, HashSet<EdgeOfGraph>> actualEdges = new TreeMap<Double, HashSet<EdgeOfGraph>>(
				new Comparator<Double>() {
					@Override
					public int compare(Double o1, Double o2) {
						return o1 < o2 ? -1 : o1 > o2 ? 1 : 0;
					}

				});
		//HashMap<Integer, EdgeOfGraph> actualEdge = new HashMap<Integer, EdgeOfGraph>();
		for (int i = 0; i < actions.size(); i++) {
//			System.out.println();
//			System.out.println();
//			System.out.println();
//			System.out.println("Action num: " + i);
//			System.out.println("ActualEdges size: " + actualEdges.size());
//			for (Double doub : actualEdges.keySet()) {
//				System.out.println("	edge " + doub + " " + actualEdges.get(doub).size() + " " + actualEdges.get(doub));
//			}
			
			if (actions.get(i).type() == ActionType.DELETE) {
				//System.out.println("DELETE");
				//actualEdge.remove(actions.get(i).edgeNum());

				EdgeOfGraph actionEdge = diagList[actions.get(i).edgeNum()];
				SortedMap<Double, HashSet<EdgeOfGraph>> tmp = actualEdges.subMap(actionEdge.begin.getY(), actionEdge.end.getY());
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
				
				EdgeOfGraph actionEdge = diagList[actions.get(i).edgeNum()];
				
				HashSet<EdgeOfGraph> intersectingFacesBegin = null;
				if (actualEdges.floorKey(actionEdge.begin.getY()) != null) {
					intersectingFacesBegin = actualEdges.get(actualEdges.floorKey(actionEdge.begin.getY()));
				}
				
				HashSet<EdgeOfGraph> intersectingFacesEnd = null;
				if (actualEdges.floorKey(actionEdge.end.getY()) !=null) {
					intersectingFacesEnd = actualEdges.get(actualEdges.floorKey(actionEdge.end.getY()));
				}
				
				if (intersectingFacesBegin == null) {
					intersectingFacesBegin = new HashSet<EdgeOfGraph>();
				}
				actualEdges.put(actionEdge.begin.getY(), intersectingFacesBegin);
				
				if (intersectingFacesEnd == null) {
					intersectingFacesEnd = new HashSet<EdgeOfGraph>();
				}
				actualEdges.put(actionEdge.end.getY(), intersectingFacesEnd);

				SortedMap<Double, HashSet<EdgeOfGraph>> tmp = actualEdges.subMap(actionEdge.begin.getY(), actionEdge.end.getY());
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
			for (EdgeOfGraph vert : actualEdges.get(actualEdges.floorKey(vertex.getY()))) {
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
