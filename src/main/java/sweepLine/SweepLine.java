package sweepLine;

import graph.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class SweepLine {
	double inaccuracy;

	public SweepLine(double inaccuracy) {
		this.inaccuracy = inaccuracy;
	}

	public void makePlanar(Graph gph) {
		EdgeOfGraph[] edgesList = gph.edgesArray();
		ArrayList<ArrayList<Vertex>> intersectionPoints = findPointsOfIntersection(edgesList);
		addIntersectionPoints(gph, edgesList, intersectionPoints);

	}

	private void addIntersectionPoints(Graph gph, EdgeOfGraph[] edgesList,
			ArrayList<ArrayList<Vertex>> intersectionPoints) {
		for (int i = 0; i < edgesList.length; i++) {
			if (intersectionPoints.get(i) == null
					|| (intersectionPoints.get(i) != null && intersectionPoints.get(i).size() == 0)) {
				continue;
			}
			gph.deleteEdge(edgesList[i].getBegin(), edgesList[i].getEnd());
			if (edgesList[i].getBegin().getPoint().getX() > edgesList[i].getEnd().getPoint().getX()) {
				gph.addEdge(edgesList[i].getBegin(), intersectionPoints.get(i).get(intersectionPoints.get(i).size() - 1),
						edgesList[i].getBegin().getLength(intersectionPoints.get(i).get(intersectionPoints.get(i).size() - 1)));
				gph.addEdge(intersectionPoints.get(i).get(0), edgesList[i].getEnd(),
						edgesList[i].getEnd()
								.getLength(intersectionPoints.get(i).get(0)));
				for (int j = 1; j < intersectionPoints.get(i).size(); j++) {
					gph.addEdge(intersectionPoints.get(i).get(j), intersectionPoints.get(i).get(j - 1),
							intersectionPoints.get(i).get(j - 1).getLength(intersectionPoints.get(i).get(j)));
				}
			} else {
				gph.addEdge(edgesList[i].getBegin(), intersectionPoints.get(i).get(0),
						edgesList[i].getBegin().getLength(intersectionPoints.get(i).get(0)));
				gph.addEdge(intersectionPoints.get(i).get(intersectionPoints.get(i).size() - 1), edgesList[i].getEnd(),
						edgesList[i].getEnd()
								.getLength(intersectionPoints.get(i).get(intersectionPoints.get(i).size() - 1)));
				for (int j = 1; j < intersectionPoints.get(i).size(); j++) {
					gph.addEdge(intersectionPoints.get(i).get(j - 1), intersectionPoints.get(i).get(j),
							intersectionPoints.get(i).get(j - 1).getLength(intersectionPoints.get(i).get(j)));
				}
			}
		}

	}

	public ArrayList<ArrayList<Vertex>> findPointsOfIntersection(EdgeOfGraph[] edgesList) {
		ArrayList<ArrayList<Vertex>> intersectionPoints = new ArrayList<ArrayList<Vertex>>();
		for (int i = 0; i < edgesList.length; i++) {
			intersectionPoints.add(new ArrayList<Vertex>());
		}
		ArrayList<Action> actions = initActions(edgesList);
		Collections.sort(actions, new Comparator<Action>() {
			@Override
			public int compare(Action a1, Action a2) {
				return a1.getX() < a2.getX() ? -1 : a1.getX() > a2.getX() ? 1 : a1.getType() == ActionType.ADD ? 1 : a2.getType() == ActionType.ADD ? -1 : 0;
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
						Vertex intersecPoint = edgesList[actions.get(i).getEdgeNum()]
								.intersectionPoint(actualEdge.get(edgeNum));
						intersectionPoints.get(actions.get(i).getEdgeNum()).add(intersecPoint);
						intersectionPoints.get(edgeNum).add(intersecPoint);
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
