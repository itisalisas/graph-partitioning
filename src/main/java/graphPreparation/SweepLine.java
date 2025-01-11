package graphPreparation;

import graph.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.SortedMap;
import java.util.TreeMap;

import addingPoints.CoordinateConstraintsForFace;

enum ActionType {
	ADD, DELETE, POINT
}

record Action(double x, int edgeNum, Vertex vertex, ActionType type) {
}

public class SweepLine {
	double inaccuracy;

	public SweepLine() {
		this.inaccuracy = 0.000000000001;
	}

	public SweepLine(double inaccuracy) {
		this.inaccuracy = inaccuracy;
	}

	public void makePlanar(Graph<Vertex> gph) {
		EdgeOfGraph[] edgesList = gph.edgesArray();
		ArrayList<LinkedList<Vertex>> intersectionPoints = findPointsOfIntersection(edgesList);
		addIntersectionPoints(gph, edgesList, intersectionPoints);

	}

	private void addIntersectionPoints(Graph<Vertex> gph, EdgeOfGraph[] edgesList,
			ArrayList<LinkedList<Vertex>> intersectionPoints) {
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
						break;
					} else {
						intersectionPoints.get(i).remove(j);
						intersectionPoints.get(i).add(j, intersectionPoints.get(i).get(j - 1));
					}
					continue;
				}
				gph.addEdge(intersectionPoints.get(i).get(j - 1), intersectionPoints.get(i).get(j),
						intersectionPoints.get(i).get(j - 1).getLength(intersectionPoints.get(i).get(j)));
				uselessPointsNum = 0;
			}
		}

	}

	public ArrayList<LinkedList<Vertex>> findPointsOfIntersection(EdgeOfGraph[] edgesList) {
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
