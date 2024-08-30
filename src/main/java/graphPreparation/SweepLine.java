package graphPreparation;

import graph.*;

<<<<<<< HEAD:src/main/java/graphPreparation/SweepLine.java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
=======
import java.util.*;
>>>>>>> inertial-flow:src/main/java/sweepLine/SweepLine.java

public class SweepLine {
	double inaccuracy;

	public SweepLine() {
		this.inaccuracy = 0.00000000000001;
	}
	public SweepLine(double inaccuracy) {
		this.inaccuracy = inaccuracy;
	}

	public void makePlanar(Graph gph) {
		EdgeOfGraph[] edgesList = gph.edgesArray();
		ArrayList<LinkedList<Vertex>> intersectionPoints = findPointsOfIntersection(edgesList);
		addIntersectionPoints(gph, edgesList, intersectionPoints);

	}

	private void addIntersectionPoints(Graph gph, EdgeOfGraph[] edgesList,
			ArrayList<LinkedList<Vertex>> intersectionPoints) {
		for (int i = 0; i < edgesList.length; i++) {
			if (intersectionPoints.get(i) == null
					|| (intersectionPoints.get(i) != null && intersectionPoints.get(i).size() == 0)) {
				continue;
			}
			gph.deleteEdge(edgesList[i].getBegin(), edgesList[i].getEnd());
			if (edgesList[i].getBegin().getPoint().getX() > edgesList[i].getEnd().getPoint().getX()) {
//				if (edgesList[i].getBegin().getLength(intersectionPoints.get(i).get(intersectionPoints.get(i).size() - 1)) <= inaccuracy) {
//					intersectionPoints.get(i).removeLast();
//					intersectionPoints.get(i).addLast(edgesList[i].getBegin());
//				} else {
//					gph.addEdge(edgesList[i].getBegin(), intersectionPoints.get(i).get(intersectionPoints.get(i).size() - 1),
//							edgesList[i].getBegin().getLength(intersectionPoints.get(i).get(intersectionPoints.get(i).size() - 1)));
//				}
//				if (edgesList[i].getEnd().getLength(intersectionPoints.get(i).get(0)) <= inaccuracy) {
//					intersectionPoints.get(i).removeFirst();
//					intersectionPoints.get(i).addFirst(edgesList[i].getEnd());
//				} else {
//					gph.addEdge(intersectionPoints.get(i).get(0), edgesList[i].getEnd(),
//							edgesList[i].getEnd().getLength(intersectionPoints.get(i).get(0)));
//				}
				Collections.sort(intersectionPoints.get(i), new Comparator<Vertex>() {

					@Override
					public int compare(Vertex o1, Vertex o2) {
						return o1.getPoint().getX() > o2.getPoint().getX() ? -1
								: o1.getPoint().getX() < o2.getPoint().getX() ? 1 : 0;
					}

				});

			} else if (edgesList[i].getBegin().getPoint().getX() < edgesList[i].getEnd().getPoint().getX()) {
//				if (edgesList[i].getBegin().getLength(intersectionPoints.get(i).get(0)) <= inaccuracy) {
//					intersectionPoints.get(i).removeFirst();
//					intersectionPoints.get(i).addFirst(edgesList[i].getBegin());
//				} else {
//					gph.addEdge(edgesList[i].getBegin(), intersectionPoints.get(i).get(0),
//									edgesList[i].getBegin().getLength(intersectionPoints.get(i).get(0)));
//				}
//				if (edgesList[i].getEnd().getLength(intersectionPoints.get(i).get(intersectionPoints.get(i).size() - 1)) <= inaccuracy) {
//					intersectionPoints.get(i).removeLast();
//					intersectionPoints.get(i).addLast(edgesList[i].getEnd());
//				} else {
//					gph.addEdge(intersectionPoints.get(i).get(intersectionPoints.get(i).size() - 1), edgesList[i].getEnd(),
//							edgesList[i].getEnd()
//									.getLength(intersectionPoints.get(i).get(intersectionPoints.get(i).size() - 1)));
//				}

				Collections.sort(intersectionPoints.get(i), new Comparator<Vertex>() {
					@Override
					public int compare(Vertex o1, Vertex o2) {
						return o1.getPoint().getX() < o2.getPoint().getX() ? -1
								: o1.getPoint().getX() > o2.getPoint().getX() ? 1 : 0;
					}

				});
			} else {
				if (edgesList[i].getBegin().getPoint().getY() < edgesList[i].getEnd().getPoint().getY()) {
					Collections.sort(intersectionPoints.get(i), new Comparator<Vertex>() {
						@Override
						public int compare(Vertex o1, Vertex o2) {
							return o1.getPoint().getY() < o2.getPoint().getY() ? -1
									: o1.getPoint().getY() > o2.getPoint().getY() ? 1 : 0;
						}

					});
				} else {
					Collections.sort(intersectionPoints.get(i), new Comparator<Vertex>() {
						@Override
						public int compare(Vertex o1, Vertex o2) {
							return o1.getPoint().getY() > o2.getPoint().getY() ? -1
									: o1.getPoint().getY() < o2.getPoint().getY() ? 1 : 0;
						}

					});
				}
			}
			intersectionPoints.get(i).addFirst(edgesList[i].getBegin());
			intersectionPoints.get(i).addLast(edgesList[i].getEnd());
			int uselessPointsNum = 0;
			for (int j = 1; j < intersectionPoints.get(i).size(); j++) {
				if (intersectionPoints.get(i).get(j - 1).getLength(intersectionPoints.get(i).get(j)) <= inaccuracy) {
					uselessPointsNum++;
					if (intersectionPoints.get(i).get(j - 1).getLength(edgesList[i].getBegin()) <= inaccuracy) {
						intersectionPoints.get(i).remove(j);
						intersectionPoints.get(i).add(j, edgesList[i].getBegin());
					} else if (intersectionPoints.get(i).get(j).getLength(edgesList[i].getEnd()) <= inaccuracy) {
						gph.deleteEdge(intersectionPoints.get(i).get(j - uselessPointsNum - 1),
								intersectionPoints.get(i).get(j - uselessPointsNum));
						gph.deleteVertex(intersectionPoints.get(i).get(j - uselessPointsNum));
						gph.addEdge(intersectionPoints.get(i).get(j - uselessPointsNum - 1), edgesList[i].getEnd(),
								intersectionPoints.get(i).get(j - uselessPointsNum - 1)
										.getLength(edgesList[i].getEnd()));
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
				return a1.getX() < a2.getX() ? -1
						: a1.getX() > a2.getX() ? 1
								: a1.getType() == ActionType.ADD ? 1 : a2.getType() == ActionType.ADD ? -1 : 0;
			}
		});
		HashMap<Integer, EdgeOfGraph> actualEdge = new HashMap<Integer, EdgeOfGraph>();
//		TreeSet<EdgeOfGraph> actualEdge = new TreeSet<EdgeOfGraph>(new Comparator<EdgeOfGraph>() {
//			@Override
//			public int compare(EdgeOfGraph o1, EdgeOfGraph o2) {
//				double x = Math.max(Math.min(o1.getBegin().getPoint().getX(), o1.getEnd().getPoint().getX()), 
//						Math.min(o2.getBegin().getPoint().getX(), o2.getEnd().getPoint().getX()));
//				return getYForEdge(x, o1) < getYForEdge(x, o2) ? -1 : getYForEdge(x, o1) > getYForEdge(x, o2) ? 1 : 0;
//			}
//		});
		for (int i = 0; i < actions.size(); i++) {
			if (actions.get(i).getType() == ActionType.ADD) {
				for (int edgeNum : actualEdge.keySet()) {
					if (edgesList[actions.get(i).getEdgeNum()].intersect(actualEdge.get(edgeNum))) {
						if (edgesList[actions.get(i).getEdgeNum()].vertical() && edgesList[edgeNum].vertical()) {
							if (edgesList[actions.get(i).getEdgeNum()].includeForY(edgesList[edgeNum].getBegin())) {
								intersectionPoints.get(actions.get(i).getEdgeNum()).add(edgesList[edgeNum].getBegin());
							}
							if (edgesList[actions.get(i).getEdgeNum()].includeForY(edgesList[edgeNum].getEnd())) {
								intersectionPoints.get(actions.get(i).getEdgeNum()).add(edgesList[edgeNum].getEnd());
							}
							if (edgesList[edgeNum].includeForY(edgesList[actions.get(i).getEdgeNum()].getBegin())) {
								intersectionPoints.get(edgeNum).add(edgesList[actions.get(i).getEdgeNum()].getBegin());
							}
							if (edgesList[edgeNum].includeForY(edgesList[actions.get(i).getEdgeNum()].getEnd())) {
								intersectionPoints.get(edgeNum).add(edgesList[actions.get(i).getEdgeNum()].getEnd());
							}
						} else if (edgesList[actions.get(i).getEdgeNum()].horizontal()
								&& edgesList[edgeNum].horizontal()) {
							if (edgesList[actions.get(i).getEdgeNum()].includeForX(edgesList[edgeNum].getBegin())) {
								intersectionPoints.get(actions.get(i).getEdgeNum()).add(edgesList[edgeNum].getBegin());
							}
							if (edgesList[actions.get(i).getEdgeNum()].includeForX(edgesList[edgeNum].getEnd())) {
								intersectionPoints.get(actions.get(i).getEdgeNum()).add(edgesList[edgeNum].getEnd());
							}
							if (edgesList[edgeNum].includeForX(edgesList[actions.get(i).getEdgeNum()].getBegin())) {
								intersectionPoints.get(edgeNum).add(edgesList[actions.get(i).getEdgeNum()].getBegin());
							}
							if (edgesList[edgeNum].includeForX(edgesList[actions.get(i).getEdgeNum()].getEnd())) {
								intersectionPoints.get(edgeNum).add(edgesList[actions.get(i).getEdgeNum()].getEnd());
							}
						} else {
							Vertex intersecPoint = edgesList[actions.get(i).getEdgeNum()]
									.intersectionPoint(actualEdge.get(edgeNum));
							if (intersecPoint != null) {
								intersectionPoints.get(actions.get(i).getEdgeNum()).add(intersecPoint);
								intersectionPoints.get(edgeNum).add(intersecPoint);
							}
						}
					}
				}
				actualEdge.put(actions.get(i).getEdgeNum(), edgesList[actions.get(i).getEdgeNum()]);
			} else {
				actualEdge.remove(actions.get(i).getEdgeNum());
//				for (EdgeOfGraph edge : actualEdge) {				
//					if (edgesList[actions.get(i).getEdgeNum()].intersect(edge)) {
//						intersectionPoints.get(actions.get(i).getEdgeNum()).add(edge);
//					}
//				}
			}
		}
		return intersectionPoints;
	}

	private ArrayList<Action> initActions(EdgeOfGraph[] edgesList) {
		ArrayList<Action> result = new ArrayList<Action>();
		for (int i = 0; i < edgesList.length; i++) {
			result.add(new Action(
					Math.min(edgesList[i].getBegin().getPoint().getX(), edgesList[i].getEnd().getPoint().getX()), i,
					ActionType.ADD));
			result.add(new Action(
					Math.max(edgesList[i].getBegin().getPoint().getX(), edgesList[i].getEnd().getPoint().getX()), i,
					ActionType.DELETE));
		}
		return result;
	}
}
