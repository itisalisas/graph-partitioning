package graphPreparation;

import graph.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

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
				//check vertical
				if (edgesList[actions.get(i).edgeNum()].vertical() && edgesList[edgeNum].vertical()) {
					if (edgesList[actions.get(i).edgeNum()].includeForY(edgesList[edgeNum].begin)) {
						intersectionPoints.get(actions.get(i).edgeNum()).add(edgesList[edgeNum].begin);
					}
					if (edgesList[actions.get(i).edgeNum()].includeForY(edgesList[edgeNum].end)) {
						intersectionPoints.get(actions.get(i).edgeNum()).add(edgesList[edgeNum].end);
					}
					if (edgesList[edgeNum].includeForY(edgesList[actions.get(i).edgeNum()].begin)) {
						intersectionPoints.get(edgeNum).add(edgesList[actions.get(i).edgeNum()].begin);
					}
					if (edgesList[edgeNum].includeForY(edgesList[actions.get(i).edgeNum()].end)) {
						intersectionPoints.get(edgeNum).add(edgesList[actions.get(i).edgeNum()].end);
					}
				//check horizontal
				} else if (edgesList[actions.get(i).edgeNum()].horizontal() && edgesList[edgeNum].horizontal()) {
					if (edgesList[actions.get(i).edgeNum()].includeForX(edgesList[edgeNum].begin)) {
						intersectionPoints.get(actions.get(i).edgeNum()).add(edgesList[edgeNum].begin);
					}
					if (edgesList[actions.get(i).edgeNum()].includeForX(edgesList[edgeNum].end)) {
						intersectionPoints.get(actions.get(i).edgeNum()).add(edgesList[edgeNum].end);
					}
					if (edgesList[edgeNum].includeForX(edgesList[actions.get(i).edgeNum()].begin)) {
						intersectionPoints.get(edgeNum).add(edgesList[actions.get(i).edgeNum()].begin);
					}
					if (edgesList[edgeNum].includeForX(edgesList[actions.get(i).edgeNum()].end)) {
						intersectionPoints.get(edgeNum).add(edgesList[actions.get(i).edgeNum()].end);
					}
				//check normal (not vertical, not horizontal)
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

	private ArrayList<Action> initActions(EdgeOfGraph[] edgesList) {
		ArrayList<Action> result = new ArrayList<Action>();
		for (int i = 0; i < edgesList.length; i++) {
			result.add(new Action(Math.min(edgesList[i].begin.getX(), edgesList[i].end.getX()), i, ActionType.ADD));
			result.add(new Action(Math.max(edgesList[i].begin.getX(), edgesList[i].end.getX()), i, ActionType.DELETE));
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
		// System.out.println("action size: " + actions.size());
		Collections.sort(actions, new Comparator<Action>() {
			@Override
			public int compare(Action a1, Action a2) {
				return a1.x() < a2.x() ? -1
						: a1.x() > a2.x() ? 1 : a1.type() == ActionType.ADD ? 1 : a2.type() == ActionType.ADD ? -1 : 0;
			}
		});
		HashMap<Integer, EdgeOfGraph> actualEdge = new HashMap<Integer, EdgeOfGraph>();
		for (int i = 0; i < actions.size(); i++) {
			if (actions.get(i).type() == ActionType.ADD) {
				actualEdge.put(actions.get(i).edgeNum(), diagList[actions.get(i).edgeNum()]);
				for (int edgeNum : actualEdge.keySet()) {
					for (Vertex ver : newVertices) {
						// System.out.println("face: " +
						// returnFromSimplification.get(actualEdge.get(edgeNum)).getName() + " " +
						// "vertex: " + ver.getName());
						if (ver.inRectangle(actualEdge.get(edgeNum).begin, actualEdge.get(edgeNum).end)) {
							// System.out.println("vertex: " + ver.getName() + " in rect " +
							// returnFromSimplification.get(actualEdge.get(edgeNum)).getName());
							if (ver.inFaceGeom(
									returnFromSimplification.get(actualEdge.get(edgeNum)).getVerticesOfFace())) {
								// System.out.println("vertex: " + ver.getName() + " in face " +
								// returnFromSimplification.get(actualEdge.get(edgeNum)).getName());
								res.put(ver, returnFromSimplification.get(actualEdge.get(edgeNum)));
							}
						}
					}
				}
			} else {
				actualEdge.remove(actions.get(i).edgeNum());
			}
		}
		return res;
	}
}
