package partitioningGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

public class SweepLine {
	double inaccuracy;
	public SweepLine(double inaccuracy) {
		this.inaccuracy = inaccuracy;
	}
	public void makePlanar(Graph gph) {
		EdgeOfGraph[] edgesList = gph.edgesArray();
		HashMap<Integer, ArrayList<EdgeOfGraph>> intersectionPoints = findPointsOfIntersection(edgesList);
		addIntersectionPoints(gph, edgesList, intersectionPoints);
		
	}
	private void addIntersectionPoints(Graph gph, EdgeOfGraph[] edgesList,
			HashMap<Integer, ArrayList<EdgeOfGraph>> intersectionPoints) {
		HashMap<EdgeOfGraph, ArrayList<Vertex>> edgesVertices = new HashMap<EdgeOfGraph, ArrayList<Vertex>>();
		for (int i = 0; i < edgesList.length; i++) {
			edgesVertices.put(edgesList[i], new ArrayList<Vertex>());
			edgesVertices.get(edgesList[i]).add(edgesList[i].getBegin());
			edgesVertices.get(edgesList[i]).add(edgesList[i].getEnd());
		}
		for (int i = 0; i < edgesList.length; i++) {
			if (intersectionPoints.get(i) == null || (intersectionPoints.get(i) != null && intersectionPoints.get(i).size() == 0)) {
				continue;
			}
			for (int j = 0; j < intersectionPoints.get(i).size(); j++) {
				Vertex intersectionPoint = edgesList[i].intersectionPoint(intersectionPoints.get(i).get(j));
				if (intersectionPoint.getPoint().getX() == -1 || intersectionPoint.getPoint().getY() == -1) continue;
				Vertex begin1 = maxBegin(intersectionPoint.getPoint().getX(), edgesVertices.get(edgesList[i]));
				Vertex begin2 = maxBegin(edgesList[i].getBegin().getPoint().getX() < edgesList[i].getEnd().getPoint().getX() ? edgesList[i].getBegin().getPoint().getX() : edgesList[i].getEnd().getPoint().getX(),
						edgesVertices.get(intersectionPoints.get(i).get(j)));
				Vertex end1 = minEnd(intersectionPoint.getPoint().getX(), edgesVertices.get(edgesList[i]));
				Vertex end2 = minEnd(edgesList[i].getBegin().getPoint().getX() < edgesList[i].getEnd().getPoint().getX() ? edgesList[i].getBegin().getPoint().getX() : edgesList[i].getEnd().getPoint().getX(),
						edgesVertices.get(intersectionPoints.get(i).get(j)));

				edgesVertices.get(edgesList[i]).add(intersectionPoint);
				edgesVertices.get(intersectionPoints.get(i).get(j)).add(intersectionPoint);
				gph.addVertex(intersectionPoint);
				if (edgesList[i].getBegin().getPoint().getX() > edgesList[i].getEnd().getPoint().getX()) {
					Vertex tmp = begin1;
					begin1 = end1;
					end1 = tmp;
				}
				if (intersectionPoints.get(i).get(j).getBegin().getPoint().getX() > intersectionPoints.get(i).get(j).getEnd().getPoint().getX()) {
					Vertex tmp = begin2;
					begin2 = end2;
					end2 = tmp;
				}
				gph.deleteEdge(begin1, end1);
				gph.deleteEdge(begin2, end2);
				gph.addEdge(begin1, intersectionPoint, begin1.getLength(intersectionPoint));
				gph.addEdge(begin2, intersectionPoint, begin2.getLength(intersectionPoint));
				gph.addEdge(intersectionPoint, end1, end1.getLength(intersectionPoint));
				gph.addEdge(intersectionPoint, end2, end2.getLength(intersectionPoint));
			}
		}
		
	}
	private Vertex minEnd(double x, ArrayList<Vertex> arrayList) {
		if (arrayList.isEmpty()) return null;
		Vertex ans = new Vertex(0, new Point(-1, -1));
		for (int i = 0; i < arrayList.size(); i++) {
			if (x < arrayList.get(i).getPoint().getX() && ans.getPoint().getX() == -1) {
				ans = arrayList.get(i);
			}
			if ( ans.getPoint().getX() != -1 && x < arrayList.get(i).getPoint().getX() && arrayList.get(i).getPoint().getX() < ans.getPoint().getX()) {
				ans = arrayList.get(i);
			}	
		}
		return ans;
	}
	private Vertex maxBegin(double x, ArrayList<Vertex> arrayList) {
		if (arrayList.isEmpty()) return null;
		Vertex ans = new Vertex(0, new Point(-1, -1));
		for (int i = 0; i < arrayList.size(); i++) {
			if (x > arrayList.get(i).getPoint().getX() && arrayList.get(i).getPoint().getX() > ans.getPoint().getX()) {
				ans = arrayList.get(i);
			}	
		}
		return ans;
	}
	public HashMap<Integer, ArrayList<EdgeOfGraph>> findPointsOfIntersection(EdgeOfGraph[] edgesList) {
		HashMap<Integer, ArrayList<EdgeOfGraph>> intersectionPoints = new HashMap <Integer, ArrayList<EdgeOfGraph>>();
		ArrayList<Action> actions = initActions(edgesList);
		Collections.sort(actions, new Comparator<Action>() {
			@Override
			public int compare(Action a1, Action a2) {
				return a1.getX() < a2.getX() ? -1 : a1.getX() > a2.getX() ? 1 : 0;
			}
		});
		HashSet<EdgeOfGraph> actualEdge = new HashSet<EdgeOfGraph>();
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
				intersectionPoints.put(actions.get(i).getEdgeNum(), new ArrayList<EdgeOfGraph>());
				for (EdgeOfGraph edge : actualEdge) {				
					if (edgesList[actions.get(i).getEdgeNum()].intersect(edge)) {
						intersectionPoints.get(actions.get(i).getEdgeNum()).add(edge);
					}
				}
				actualEdge.add(edgesList[actions.get(i).getEdgeNum()]);
			} else {
				actualEdge.remove(edgesList[actions.get(i).getEdgeNum()]);
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
			result.add(new Action(Math.min(edgesList[i].getBegin().getPoint().getX(),edgesList[i].getEnd().getPoint().getX()), i, ActionType.ADD));
			result.add(new Action(Math.max(edgesList[i].getBegin().getPoint().getX(),edgesList[i].getEnd().getPoint().getX()), i, ActionType.DELETE));
		}
		return result;
	}
}
