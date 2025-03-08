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
import java.util.Set;
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


	public Graph<Vertex> makePlanar(Graph<Vertex> gph) {
		// Point minPoint = findMinPoint(gph);
		// Point maxPoint = findMaxPoint(gph);
		// BufferedImage im = new BufferedImage((int) (maxPoint.x - minPoint.x) * 10,
		// 									 (int) (maxPoint.y - minPoint.y) * 10,
		// 									 BufferedImage.TYPE_INT_RGB);
		//drawGraph(im, minPoint, gph, "before");
		ArrayList<EdgeOfGraph<Vertex>> edgesList = gph.undirEdgesArray();
		ArrayList<ArrayList<Vertex>> intersectionPoints = findPointsOfIntersection(edgesList);
		HashMap<Vertex, Vertex> copyPointsGraphPoints = checkCopyPoints(gph, intersectionPoints);
		// for (int i = 0; i < intersectionPoints.size(); i++) {
		// 	if (intersectionPoints.get(i).size() == 0) {
		// 		continue;
		// 	}
		// 	System.out.println("edge: " + i);
		// 	System.out.println("begin: " + "name:" + edgesList.get(i).begin.getName() +" x:" + edgesList.get(i).begin.x + " y:" + edgesList.get(i).begin.y + " ");
		// 	for (int j = 0; j < intersectionPoints.get(i).size(); j++) {
		// 		System.out.print("name:" + intersectionPoints.get(i).get(j).getName() +" x:" + intersectionPoints.get(i).get(j).x + " y:" + intersectionPoints.get(i).get(j).y + " ");
		// 	}
		// 	System.out.println();
		// 	System.out.println("end: " + "name:" + edgesList.get(i).end.getName() +" x:" + edgesList.get(i).end.x + " y:" + edgesList.get(i).end.y + " ");
		// 	System.out.println();
		// }
		addIntersectionPoints(gph, edgesList, intersectionPoints, copyPointsGraphPoints);
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
						graph.addEdge(v1, v, tmp.get(v).getLength());
					}
					toDelete.add(v2);
				}
			}
		}
		for (Vertex v : toDelete) {
			graph.deleteVertex(v);
		}
	
		/**
		Vertex tmpVertex1 = null;
		Vertex tmpVertex2 = null;
		for (Vertex v1 : graph.getEdges().keySet()) {
			if (v1.getWeight() != 0) continue;
			for (Vertex v2 : graph.getEdges().keySet()) {
				if (v2.getWeight() != 0) continue;
				if (v2.equals(v1)) continue;
				if (v1.name == 31 && v2.name == 33) {
					graph.addEdge(v2, v1, v2.getLength(v1));
					for (Vertex v :graph.getEdges().get(v2).keySet()) {
						if (v.name == 808071516) {
							tmpVertex1 = v;
							tmpVertex2 = v2;
						}
					}
					break;
				}
			}
		}
		graph.deleteEdge(tmpVertex1, tmpVertex2);
		*/
		// Vertex tmp = null;
		// for (Vertex ver : graph.getEdges().keySet()) {
		// 	if (ver.name == 2423332599L) {
		// 		for (Vertex vr : graph.getEdges().keySet()) {
		// 			if (vr.name == 20) {
		// 				for (Vertex v : graph.getEdges().get(vr).keySet()) {
		// 					graph.addEdge(ver, v, ver.getLength(v));
		// 				}
		// 				tmp = vr;
		// 				break;
		// 			}
		// 		}
		// 	}
			
		// }
		//graph.deleteVertex(tmp);
		// for (Vertex v : graph.getEdges().keySet()) {
		// 	if (v.name == 0) {
		// 		System.out.println(v.name);	 
		// 	}
		// }
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
		// for (int i = 0; i < intersectionPoints.size(); i++) {
		// 	for (int j = 0; j < intersectionPoints.get(i).size(); j ++) {
		// 		for (Vertex verOfGraph : gph.getEdges().keySet()) {
		// 			if (intersectionPoints.get(i).get(j).getLength(verOfGraph) <= 1) {
		// 				intersectionPoints.get(i).get(j) = verOfGraph;
		// 			}
		// 		}
		// 	}
		// }
		return ans;
	}
	
		
	private void drawNewPoints(BufferedImage im, Point minPoint, Graph<Vertex> gph, String string) {
		Graphics g = im.getGraphics();
		for (Vertex v : gph.getEdges().keySet()) {
			if (v.getWeight() == 0) {
				g.setColor(Color.RED);
				g.fillOval((int) (v.x - minPoint.x) * 10, (int) (v.y - minPoint.y) * 10, 8, 8);
			}
		}
		g.dispose();
		try {
			ImageIO.write(im, "jpg", 
				new File(("src/main/output/drawSweepLine/500_" + string + ".jpg").replace('/', File.separatorChar)));
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
			ImageIO.write(im, "jpg", 
				new File(("src/main/output/drawSweepLine/500_" + string + ".jpg").replace('/', File.separatorChar)));
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
	
			if (currEdge.begin.getX() > currEdge.end.getX()) {
				Collections.sort(currList, backXComp);
			} else if (currEdge.begin.getX() < currEdge.end.getX()) {
				Collections.sort(currList, straightXComp);
			} else {
				if (currEdge.begin.getY() < currEdge.end.getY()) {
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
        			gph.addEdge(prevVert, copyPoints.get(currVertex), prevVert.getLength(currVertex));
        			prevVert = copyPoints.get(currVertex);
					//System.out.println(copyPoints.get(currVertex).name);
					continue;
				}
				gph.addVertex(prevVert);
				gph.addVertex(currVertex);
        		gph.addEdge(prevVert, currVertex, prevVert.getLength(currVertex));
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
				SortedMap<Double, HashSet<EdgeOfGraph<Vertex>>> tmp = actualEdges.subMap(actionEdge.begin.getY(), 
																						 actionEdge.end.getY());
				for (Double d : tmp.keySet()) {
					if (actualEdges.get(d) != null) {
						actualEdges.get(d).remove(actionEdge);
						// if (actualEdges.get(d).size() == 0) {
						// 	actualEdges.remove(d);
						// }
					}
				}
				
				if (actualEdges.get(actionEdge.begin.getY()) != null) {
					actualEdges.get(actionEdge.begin.getY()).remove(actionEdge);
					if (actualEdges.get(actionEdge.begin.getY()).size() == 0) {
						actualEdges.remove(actionEdge.begin.getY());
					}
				}
				
				if (actualEdges.get(actionEdge.end.getY()) != null) {
					actualEdges.get(actionEdge.end.getY()).remove(actionEdge);
					if (actualEdges.get(actionEdge.end.getY()).size() == 0) {
						actualEdges.remove(actionEdge.end.getY());
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

				//actualEdge.put(actions.get(i).edgeNum(), actionEdge);
				
				EdgeOfGraph<Vertex> actionEdge = diagList.get(actions.get(i).edgeNum());
				
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