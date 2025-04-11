package graphPreparation;

import graph.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.SortedMap;
import java.util.TreeMap;

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
			return o1.x > o2.x ? -1 : o1.x < o2.x ? 1 : 0;
		}
	};
	
	Comparator<Vertex> straightXComp = new Comparator<Vertex>(){
		@Override
		public int compare(Vertex o1, Vertex o2) {
			return o1.x < o2.x ? -1 : o1.x > o2.x ? 1 : 0;
		}
	};

	Comparator<Vertex> backYComp = new Comparator<Vertex>(){
		@Override
		public int compare(Vertex o1, Vertex o2) {
			return o1.y > o2.y ? -1 : o1.y < o2.y ? 1 : 0;
		}
	};

	Comparator<Vertex> straightYComp = new Comparator<Vertex>(){
		@Override
		public int compare(Vertex o1, Vertex o2) {
			return o1.y < o2.y ? -1 : o1.y > o2.y ? 1 : 0;
		}
	};
	

	public SweepLine() {
		this.inaccuracy = 0.000001;
	}


	public SweepLine(double inaccuracy) {
		this.inaccuracy = inaccuracy;
	}


	public Graph<Vertex> makePlanar(Graph<Vertex> gph) {
		
		ArrayList<EdgeOfGraph<Vertex>> edgesList = gph.undirEdgesArray();
		ArrayList<ArrayList<Vertex>> intersectionPoints = findPointsOfIntersection(edgesList);
		HashMap<Vertex, Vertex> copyPointsGraphPoints = checkCopyPoints(gph, intersectionPoints);
		
		addIntersectionPoints(gph, edgesList, intersectionPoints, copyPointsGraphPoints);
		
		int smallEdgesNum = 0;
		for (Vertex begin : gph.getEdges().keySet()) {
			for (Vertex end : gph.getEdges().get(begin).keySet()) {
				if (gph.getEdges().get(begin).get(end).length < 0.001) {
					smallEdgesNum++;
				}
			}
		}
		Graph<Vertex> graph = new Graph<Vertex>();
		int intersectNewName = 0;
		HashMap<Vertex, Vertex> verName = new HashMap<>();
		for (Vertex begin : gph.getEdges().keySet()) {
			if (begin.name == 0) {
				if (!verName.keySet().contains(begin)) {
					intersectNewName++;
					Vertex nBegin = new Vertex(intersectNewName, begin, begin.getWeight());
					verName.put(begin, nBegin);
					graph.addVertex(nBegin);	
				} else {
					verName.get(begin);
				}
			} else {
				graph.addVertex(begin);
			}
		}
		//System.out.println(verName);
		for (Vertex v : graph.getEdges().keySet()) {
			if (v.name == 0) {
				System.out.println(v.name);	 
			}
		}
		for (Vertex begin : gph.getEdges().keySet()) {
			Vertex newBegin = null;
			if (begin.name == 0) {
				newBegin = verName.get(begin);
			} else {
				newBegin = begin;
			}
			for (Vertex end : gph.getEdges().get(begin).keySet()) {
				if (end.name == 0) {
					graph.addEdge(newBegin, verName.get(end), gph.getEdges().get(begin).get(end).length);
				} else {
					graph.addEdge(newBegin, end, gph.getEdges().get(begin).get(end).length);
				}
			}
		}
		HashSet<Vertex> toDelete = new HashSet<>();
		for (Vertex v1 : graph.getEdges().keySet()) {
			if (v1.getWeight() != 0) continue;
			for (Vertex v2 : graph.getEdges().keySet()) {
				if (v2.getWeight() != 0) continue;
				if (v2.equals(v1)) continue;
				if (toDelete.contains(v1) || toDelete.contains(v2)) continue;
				if (v1.getLength(v2) <= inaccuracy) {
					HashMap<Vertex, Edge> tmp = graph.getEdges().get(v2);
					for (Vertex v : tmp.keySet()) {
						graph.addEdge(v1, v, tmp.get(v).length);
					}
					toDelete.add(v2);
				}
			}
		}
		for (Vertex v : toDelete) {
			graph.deleteVertex(v);
		}
	
	
		System.out.println("small edges num after sweepline: " + smallEdgesNum);
		return graph;
	}

	private HashMap<Vertex, Vertex> checkCopyPoints(Graph<Vertex> gph, ArrayList<ArrayList<Vertex>> intersectionPoints) {
		HashMap<Vertex, Vertex> ans = new HashMap<Vertex, Vertex>();
		for (ArrayList<Vertex> currArrayList : intersectionPoints) {
			for (Vertex curVertex : currArrayList) {
				for (Vertex verOfGraph : gph.getEdges().keySet()) {
					if (curVertex.getLength(verOfGraph) <= inaccuracy) {
						ans.put(curVertex, verOfGraph);
					}
				}
			}
		}
		
		return ans;
	}
	
	
	private void addIntersectionPoints(Graph<Vertex> gph,
									   ArrayList<EdgeOfGraph<Vertex>> edgesList,
									   ArrayList<ArrayList<Vertex>> intersectionPoints, 
									   HashMap<Vertex, Vertex> copyPoints) {
		for (int i = 0; i < edgesList.size(); i++) {
			//List of intersection points in edge i
			ArrayList<Vertex> currList = intersectionPoints.get(i);
			EdgeOfGraph<Vertex> currEdge = edgesList.get(i);
			if (currList == null ||
				(currList != null && currList.size() == 0) ||
				gph.getEdges().get(currEdge.begin) == null ||
				gph.getEdges().get(currEdge.begin).get(currEdge.end) == null) {
	
				continue;
			}
	
			gph.deleteEdge(currEdge.begin, currEdge.end);
			intersectionPoints.get(i).add(0, currEdge.begin);
	
			if (currEdge.begin.x > currEdge.end.x) {
				Collections.sort(currList, backXComp);
			} else if (currEdge.begin.x < currEdge.end.x) {
				Collections.sort(currList, straightXComp);
			} else {
				if (currEdge.begin.y < currEdge.end.y) {
					Collections.sort(currList, straightYComp);
				} else {
					Collections.sort(currList, backYComp);
				}
			}
			//Assert что первая точка begin
	
			currList.add(currList.size(), currEdge.end);
			Vertex prevVert = currEdge.begin;
			for (Vertex currVertex: currList) {
				if ((prevVert.getLength(currVertex) <= inaccuracy || currEdge.end.getLength(currVertex) <= inaccuracy) && 
					(currVertex != currEdge.end)) {
        			continue;
       		 	}
				if (copyPoints.containsKey(currVertex)) {
					gph.addVertex(prevVert);
        			double newLength = currEdge.length == 0 ? 0 : prevVert.getLength(currVertex);
					gph.addEdge(prevVert, copyPoints.get(currVertex), newLength);
        			prevVert = copyPoints.get(currVertex);
					//System.out.println(copyPoints.get(currVertex).name);
					continue;
				}
				gph.addVertex(prevVert);
				gph.addVertex(currVertex);
        		double newLength = currEdge.length == 0 ? 0 : prevVert.getLength(currVertex);
				gph.addEdge(prevVert, currVertex, newLength);
        		prevVert = currVertex;
    		}
		}

	}

	public ArrayList<ArrayList<Vertex>> findPointsOfIntersection(ArrayList<EdgeOfGraph<Vertex>> edgesList) {
			ArrayList<ArrayList<Vertex>> intersectionPoints = new ArrayList<ArrayList<Vertex>>();
			for (int i = 0; i < edgesList.size(); i++) {
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
				Action currAct = actions.get(i);
				int currEdgeInd = currAct.edgeNum();
				EdgeOfGraph<Vertex> currEdge = edgesList.get(currEdgeInd);
				if (currAct.type() != ActionType.ADD) {
					actualEdge.remove(currEdgeInd);
					continue;
				}
				for (int edgeNum : actualEdge.keySet()) {
					//EdgeOfGraph<Vertex> edgeToCheck = edgesList[edgeNum];
					EdgeOfGraph<Vertex> actEdge = actualEdge.get(edgeNum);
					if (currEdge.equals(actualEdge)) {
						continue;
					}
					if (!currEdge.intersect(actEdge)) {
						continue;
					}
					// check vertical
					if (currEdge.vertical() && actEdge.vertical()) {
						checkVerticalEdges(currEdgeInd, edgeNum, edgesList, intersectionPoints);
					// check horizontal
					} else if (currEdge.horizontal() && actEdge.horizontal()) {
						chechHorizontalEdges(currEdgeInd, edgeNum, edgesList, intersectionPoints);
					// check normal (not vertical, not horizontal)
					} else {
						Vertex intersecPoint = currEdge.intersectionPoint(actEdge);
						if (intersecPoint != null) {
							
							if ((actEdge.begin.x == intersecPoint.x && actEdge.begin.y == intersecPoint.y) || 
								(actEdge.end.x == intersecPoint.x && actEdge.end.y == intersecPoint.y) || 
								(currEdge.begin.x == intersecPoint.x && currEdge.begin.y == intersecPoint.y) || 
								(currEdge.end.x == intersecPoint.x && currEdge.end.y == intersecPoint.y)) {
									continue;
							}
							intersectionPoints.get(currEdgeInd).add(intersecPoint);
							intersectionPoints.get(edgeNum).add(intersecPoint);
						}
					}
	
				}
				actualEdge.put(actions.get(i).edgeNum(), edgesList.get(actions.get(i).edgeNum()));
		}
		return intersectionPoints;
	}

	//добавить пояснения
	private <T extends Vertex> void chechHorizontalEdges(int edgeNum1,
									  					int edgeNum2,
									  					ArrayList<EdgeOfGraph<T>> edgesList,
														ArrayList<ArrayList<Vertex>> intersectionPoints) {
		EdgeOfGraph<T> edge1 = edgesList.get(edgeNum2);
		EdgeOfGraph<T> edge2 = edgesList.get(edgeNum2);
		ArrayList<Vertex> intersectPointsEdge1 = intersectionPoints.get(edgeNum1);
		ArrayList<Vertex> intersectPointsEdge2 = intersectionPoints.get(edgeNum2);
		if (edge1.includeForX(edge2.begin)) {
			intersectPointsEdge1.add(edge2.begin);
		}
		if (edge1.includeForX(edge2.end)) {
			intersectPointsEdge1.add(edge2.end);
		}
		if (edge2.includeForX(edge1.begin)) {
			intersectPointsEdge2.add(edge1.begin);
		}
		if (edge2.includeForX(edge1.end)) {
			intersectPointsEdge2.add(edge1.end);
		}

	}

	//добавить пояснения
	private <T extends Vertex> void checkVerticalEdges(int edgeNum1,
													   int edgeNum2,
													   ArrayList<EdgeOfGraph<T>> edgesList,
													   ArrayList<ArrayList<Vertex>> intersectionPoints) {
		EdgeOfGraph<T> edge1 = edgesList.get(edgeNum1);
		EdgeOfGraph<T> edge2 = edgesList.get(edgeNum2);
		ArrayList<Vertex> intersectPointsEdge1 = intersectionPoints.get(edgeNum1);
		ArrayList<Vertex> intersectPointsEdge2 = intersectionPoints.get(edgeNum2);
		if (edge1.includeForY(edge2.begin)) {
			intersectPointsEdge1.add(edge2.begin);
		}
		if (edge1.includeForY(edge2.end)) {
			intersectPointsEdge1.add(edge2.end);
		}
		if (edge2.includeForY(edge1.begin)) {
			intersectPointsEdge2.add(edge1.begin);
		}
		if (edge2.includeForY(edge1.end)) {
			intersectPointsEdge2.add(edge1.end);
		}

	}


	private <T extends Vertex> ArrayList<Action> initActions(ArrayList<EdgeOfGraph<Vertex>> edgesList) {
			ArrayList<Action> result = new ArrayList<Action>();
			for (int i = 0; i < edgesList.size(); i++) {
				result.add(new Action(Math.min(edgesList.get(i).begin.x, edgesList.get(i).end.x),
								   i,
							null, 
								   ActionType.ADD));
				result.add(new Action(Math.max(edgesList.get(i).begin.x, edgesList.get(i).end.x), 
								i, 
								null,
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
	public HashMap<Vertex, VertexOfDualGraph> findFacesOfVertices(ArrayList<EdgeOfGraph<Vertex>> diagList,
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
				return a1.x() < a2.x() ? -1 : 
						a1.x() > a2.x() ? 1 :
						a1.type() == ActionType.ADD ? 1 : 
						a2.type() == ActionType.ADD ? -1 :
						0;
			}
		});
		TreeMap<Double, HashSet<EdgeOfGraph<Vertex>>> actualEdges = new TreeMap<Double, HashSet<EdgeOfGraph<Vertex>>>(
			new Comparator<Double>() {
				@Override
				public int compare(Double o1, Double o2) {
					return o1 < o2 ? -1 :
						   o1 > o2 ? 1 :
						   0;
				}
			});
		//HashMap<Integer, EdgeOfGraph> actualEdge = new HashMap<Integer, EdgeOfGraph>();
		for (int i = 0; i < actions.size(); i++) {
			if (actions.get(i).type() == ActionType.DELETE) {
				//System.out.println("DELETE");
				//actualEdge.remove(actions.get(i).edgeNum());

				EdgeOfGraph<Vertex> actionEdge = diagList.get(actions.get(i).edgeNum());
				SortedMap<Double, HashSet<EdgeOfGraph<Vertex>>> tmp = actualEdges.subMap(actionEdge.begin.y, 
																						 actionEdge.end.y);
				for (Double d : tmp.keySet()) {
					if (actualEdges.get(d) != null) {
						actualEdges.get(d).remove(actionEdge);
						// if (actualEdges.get(d).size() == 0) {
						// 	actualEdges.remove(d);
						// }
					}
				}
				
				if (actualEdges.get(actionEdge.begin.y) != null) {
					actualEdges.get(actionEdge.begin.y).remove(actionEdge);
					if (actualEdges.get(actionEdge.begin.y).size() == 0) {
						actualEdges.remove(actionEdge.begin.y);
					}
				}
				
				if (actualEdges.get(actionEdge.end.y) != null) {
					actualEdges.get(actionEdge.end.y).remove(actionEdge);
					if (actualEdges.get(actionEdge.end.y).size() == 0) {
						actualEdges.remove(actionEdge.end.y);
					}
				}				
				continue;
			}
			if (actions.get(i).type() == ActionType.ADD) {
				//System.out.println("ADD");

				//actualEdge.put(actions.get(i).edgeNum(), actionEdge);
				
				EdgeOfGraph<Vertex> actionEdge = diagList.get(actions.get(i).edgeNum());
				
				HashSet<EdgeOfGraph<Vertex>> intersectingFacesBegin = null;
				if (actualEdges.floorKey(actionEdge.begin.y) != null) {
					intersectingFacesBegin = actualEdges.get(actualEdges.floorKey(actionEdge.begin.y));
				}
				
				HashSet<EdgeOfGraph<Vertex>> intersectingFacesEnd = null;
				if (actualEdges.floorKey(actionEdge.end.y) !=null) {
					intersectingFacesEnd = actualEdges.get(actualEdges.floorKey(actionEdge.end.y));
				}
				
				if (intersectingFacesBegin == null) {
					intersectingFacesBegin = new HashSet<EdgeOfGraph<Vertex>>();
				}
				actualEdges.put(actionEdge.begin.y, intersectingFacesBegin);
				
				if (intersectingFacesEnd == null) {
					intersectingFacesEnd = new HashSet<EdgeOfGraph<Vertex>>();
				}
				actualEdges.put(actionEdge.end.y, intersectingFacesEnd);

				SortedMap<Double, HashSet<EdgeOfGraph<Vertex>>> tmp = actualEdges.subMap(actionEdge.begin.y, actionEdge.end.y);
				for (Double d : tmp.keySet()) {
					if (actionEdge != null) {
						//System.out.println("edge " + actionEdge.begin.y() + " " + actionEdge.end.y());
						actualEdges.get(d).add(actionEdge);
						//System.out.println(d + " " + actualEdges.get(d).size() + "  " + actualEdges.get(d));
					}
				}

				continue;
			}
			//System.out.println("POINT");
			Vertex vertex = actions.get(i).vertex();

			if (actualEdges.floorKey(vertex.y) == null) {
				continue;
			}
			for (EdgeOfGraph<Vertex> vert : actualEdges.get(actualEdges.floorKey(vertex.y))) {
				if (!vertex.inRectangle(vert.begin, vert.end)) {
					continue;
				}
				
				if (vertex.inFaceGeom(returnFromSimplification.get(vert).getVerticesOfFace())) {
					res.put(vertex, returnFromSimplification.get(vert));
				}
			}
		}
		return res;
	}

	private void addPointToActions(ArrayList<Action> actions, HashSet<Vertex> newVertices) {
		for (Vertex ver : newVertices) {
			actions.add(new Action(ver.x, -1, ver, ActionType.POINT));
		}

	}
}