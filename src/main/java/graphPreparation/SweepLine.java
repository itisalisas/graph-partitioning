package graphPreparation;

import graph.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private static final Logger logger = LoggerFactory.getLogger(SweepLine.class);
	double inaccuracy;

	Comparator<Vertex> backXComp = (o1, o2) -> Double.compare(o2.x, o1.x);
	
	Comparator<Vertex> straightXComp = Comparator.comparingDouble(o -> o.x);

	Comparator<Vertex> backYComp = (o1, o2) -> Double.compare(o2.y, o1.y);

	Comparator<Vertex> straightYComp = Comparator.comparingDouble(o -> o.y);
	

	public SweepLine() {
		this.inaccuracy = 0.000001;
	}


	public SweepLine(double inaccuracy) {
		this.inaccuracy = inaccuracy;
	}


	public Graph<Vertex> makePlanar(Graph<Vertex> gph) {
        long t0 = System.currentTimeMillis();
		ArrayList<EdgeOfGraph<Vertex>> edgesList = gph.undirEdgesArray();
		ArrayList<ArrayList<Vertex>> intersectionPoints = findPointsOfIntersection(edgesList);
        long t1 = System.currentTimeMillis();
        logger.info("findPointsOfIntersection: {} ms, edges: {}", t1 - t0, edgesList.size());
		HashMap<Vertex, Vertex> copyPointsGraphPoints = checkCopyPoints(gph, intersectionPoints);
        long t2 = System.currentTimeMillis();
        logger.info("checkCopyPoints: {} ms", t2 - t1);
		
		addIntersectionPoints(gph, edgesList, intersectionPoints, copyPointsGraphPoints);
        long t3 = System.currentTimeMillis();
        logger.info("addIntersectionPoints: {} ms", t3 - t2);
		
		int smallEdgesNum = 0;
		for (Vertex begin : gph.getEdges().keySet()) {
			for (Vertex end : gph.getEdges().get(begin).keySet()) {
				if (gph.getEdges().get(begin).get(end).length < 0.001) {
					smallEdgesNum++;
				}
			}
		}
		Graph<Vertex> graph = new Graph<>();
		int intersectNewName = 0;
		HashMap<Vertex, Vertex> verName = new HashMap<>();
		for (Vertex begin : gph.getEdges().keySet()) {
			if (begin.name == 0) {
				if (!verName.containsKey(begin)) {
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
				logger.debug("Found vertex with name 0: {}", v.name);	 
			}
		}
		for (Vertex begin : gph.getEdges().keySet()) {
			Vertex newBegin;
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
        ArrayList<Vertex> zeroWeightVertices = new ArrayList<>();
        for (Vertex v : graph.getEdges().keySet()) {
            if (v.getWeight() == 0) {
                zeroWeightVertices.add(v);
            }
        }

        // Сортируем по X для эффективного поиска близких вершин
        zeroWeightVertices.sort(Comparator.comparingDouble(v -> v.x));

        for (int i = 0; i < zeroWeightVertices.size(); i++) {
            Vertex v1 = zeroWeightVertices.get(i);
            if (toDelete.contains(v1)) continue;

            // Проверяем только вершины, близкие по X
            for (int j = i + 1; j < zeroWeightVertices.size(); j++) {
                Vertex v2 = zeroWeightVertices.get(j);
                if (v2.x - v1.x > inaccuracy) break;
                
                if (toDelete.contains(v2)) continue;
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
        long t4 = System.currentTimeMillis();
        logger.info("vertex merging + graph rebuild: {} ms", t4 - t3);
        logger.info("makePlanar total: {} ms", t4 - t0);
	
	
		logger.info("small edges num after sweepline: {}", smallEdgesNum);
		return graph;
	}

    private HashMap<Vertex, Vertex> checkCopyPoints(Graph<Vertex> gph, ArrayList<ArrayList<Vertex>> intersectionPoints) {
        HashMap<Vertex, Vertex> ans = new HashMap<>();
        HashMap<Vertex, Double> bestDist = new HashMap<>();

        // Собираем и сортируем вершины графа по X
        ArrayList<Vertex> graphVertices = new ArrayList<>(gph.getEdges().keySet());
        Comparator<Vertex> byX = Comparator.comparingDouble(v -> v.x);
        graphVertices.sort(byX);

        // Фиктивная вершина для бинарного поиска
        Vertex searchKey = new Vertex(0, 0, 0);

        for (ArrayList<Vertex> currArrayList : intersectionPoints) {
            for (Vertex curVertex : currArrayList) {
                // Бинарный поиск: находим начало диапазона вершин, близких по X
                searchKey.x = curVertex.x - inaccuracy;
                int idx = Collections.binarySearch(graphVertices, searchKey, byX);
                int left = idx >= 0 ? idx : -idx - 1;
                
                // Проверяем только вершины в диапазоне [x - inaccuracy, x + inaccuracy]
                for (int i = left; i < graphVertices.size(); i++) {
                    Vertex verOfGraph = graphVertices.get(i);
                    if (verOfGraph.x > curVertex.x + inaccuracy) break;
                    
                    double dist = curVertex.getLength(verOfGraph);
                    if (dist <= inaccuracy) {
                        Double prev = bestDist.get(curVertex);
                        if (prev == null || dist < prev) {
                            ans.put(curVertex, verOfGraph);
                            bestDist.put(curVertex, dist);
                        }
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
				(currList != null && currList.isEmpty()) ||
				gph.getEdges().get(currEdge.begin) == null ||
				gph.getEdges().get(currEdge.begin).get(currEdge.end) == null) {
	
				continue;
			}
	
			gph.deleteEdge(currEdge.begin, currEdge.end);
			intersectionPoints.get(i).add(0, currEdge.begin);
	
			if (currEdge.begin.x > currEdge.end.x) {
				currList.sort(backXComp);
			} else if (currEdge.begin.x < currEdge.end.x) {
				currList.sort(straightXComp);
			} else {
				if (currEdge.begin.y < currEdge.end.y) {
					currList.sort(straightYComp);
				} else {
					currList.sort(backYComp);
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
        			double newLength = currEdge.length < 1 ? currEdge.length : prevVert.getLength(currVertex);
					gph.addEdge(prevVert, copyPoints.get(currVertex), newLength);
        			prevVert = copyPoints.get(currVertex);
					//System.out.println(copyPoints.get(currVertex).name);
					continue;
				}
				gph.addVertex(prevVert);
				gph.addVertex(currVertex);
        		double newLength = currEdge.length < 1 ? currEdge.length : prevVert.getLength(currVertex);
				gph.addEdge(prevVert, currVertex, newLength);
        		prevVert = currVertex;
    		}
		}

	}

	/**
	 * Sweep line с Interval Tree для индексации по Y.
	 * Гарантированно находит все пересечения.
	 * Асимптотика: O(n log n + k), где k — число пересечений.
	 * 
	 * Interval Tree позволяет:
	 * - Вставка интервала: O(log n)
	 * - Удаление интервала: O(log n)  
	 * - Поиск всех пересекающихся интервалов: O(log n + k)
	 */
	public ArrayList<ArrayList<Vertex>> findPointsOfIntersection(ArrayList<EdgeOfGraph<Vertex>> edgesList) {
		ArrayList<ArrayList<Vertex>> intersectionPoints = new ArrayList<>();
		for (int i = 0; i < edgesList.size(); i++) {
			intersectionPoints.add(new ArrayList<>());
		}

		if (edgesList.isEmpty()) {
			return intersectionPoints;
		}

		IntervalTree yIntervalTree = new IntervalTree();
		HashMap<Integer, double[]> edgeYIntervals = new HashMap<>();

		ArrayList<Action> actions = initActions(edgesList);
		actions.sort((a1, a2) -> a1.x() < a2.x() ? -1
				: a1.x() > a2.x() ? 1
				: a1.type() == ActionType.ADD ? 1
				: a2.type() == ActionType.ADD ? -1 : 0);

		HashMap<Integer, EdgeOfGraph<Vertex>> actualEdge = new HashMap<>();

		for (Action currAct : actions) {
			int currEdgeInd = currAct.edgeNum();
			EdgeOfGraph<Vertex> currEdge = edgesList.get(currEdgeInd);

			if (currAct.type() != ActionType.ADD) {
				actualEdge.remove(currEdgeInd);
				double[] interval = edgeYIntervals.remove(currEdgeInd);
				if (interval != null) {
					yIntervalTree.delete(interval[0], currEdgeInd);
				}
				continue;
			}

			double edgeMinY = Math.min(currEdge.begin.y, currEdge.end.y);
			double edgeMaxY = Math.max(currEdge.begin.y, currEdge.end.y);

			HashSet<Integer> candidateEdges = new HashSet<>();
			yIntervalTree.queryOverlapping(edgeMinY, edgeMaxY, candidateEdges);

			for (int edgeNum : candidateEdges) {
				EdgeOfGraph<Vertex> actEdge = actualEdge.get(edgeNum);
				if (actEdge == null || currEdge.equals(actEdge)) {
					continue;
				}
				if (!currEdge.intersect(actEdge)) {
					continue;
				}

				if (currEdge.vertical() && actEdge.vertical()) {
					checkVerticalEdges(currEdgeInd, edgeNum, edgesList, intersectionPoints);
				} else if (currEdge.horizontal() && actEdge.horizontal()) {
					checkHorizontalEdges(currEdgeInd, edgeNum, edgesList, intersectionPoints);
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

			actualEdge.put(currEdgeInd, currEdge);
			yIntervalTree.insert(edgeMinY, edgeMaxY, currEdgeInd);
			edgeYIntervals.put(currEdgeInd, new double[]{edgeMinY, edgeMaxY});
		}

		return intersectionPoints;
	}

	//добавить пояснения
	private <T extends Vertex> void checkHorizontalEdges(int edgeNum1,
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


	private <T extends Vertex> ArrayList<Action> initActions(ArrayList<EdgeOfGraph<T>> edgesList) {
			ArrayList<Action> result = new ArrayList<>();
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
		HashMap<Vertex, VertexOfDualGraph> res = new HashMap<>();
		ArrayList<Action> actions = initActions(diagList);
		addPointToActions(actions, newVertices);

		actions.sort((a1, a2) -> a1.x() < a2.x() ? -1 :
                a1.x() > a2.x() ? 1 :
                        a1.type() == ActionType.ADD ? 1 :
                                a2.type() == ActionType.ADD ? -1 :
                                        0);
		TreeMap<Double, HashSet<EdgeOfGraph<Vertex>>> actualEdges = new TreeMap<>(
                Double::compareTo);

        for (Action action : actions) {
            if (action.type() == ActionType.DELETE) {
                EdgeOfGraph<Vertex> actionEdge = diagList.get(action.edgeNum());
                SortedMap<Double, HashSet<EdgeOfGraph<Vertex>>> tmp = actualEdges.subMap(actionEdge.begin.y,
                        actionEdge.end.y);
                for (Double d : tmp.keySet()) {
                    if (actualEdges.get(d) != null) {
                        actualEdges.get(d).remove(actionEdge);
                    }
                }

                if (actualEdges.get(actionEdge.begin.y) != null) {
                    actualEdges.get(actionEdge.begin.y).remove(actionEdge);
                    if (actualEdges.get(actionEdge.begin.y).isEmpty()) {
                        actualEdges.remove(actionEdge.begin.y);
                    }
                }

                if (actualEdges.get(actionEdge.end.y) != null) {
                    actualEdges.get(actionEdge.end.y).remove(actionEdge);
                    if (actualEdges.get(actionEdge.end.y).isEmpty()) {
                        actualEdges.remove(actionEdge.end.y);
                    }
                }
                continue;
            }
            if (action.type() == ActionType.ADD) {
                EdgeOfGraph<Vertex> actionEdge = diagList.get(action.edgeNum());

                HashSet<EdgeOfGraph<Vertex>> intersectingFacesBegin = null;
                if (actualEdges.floorKey(actionEdge.begin.y) != null) {
                    intersectingFacesBegin = actualEdges.get(actualEdges.floorKey(actionEdge.begin.y));
                }

                HashSet<EdgeOfGraph<Vertex>> intersectingFacesEnd = null;
                if (actualEdges.floorKey(actionEdge.end.y) != null) {
                    intersectingFacesEnd = actualEdges.get(actualEdges.floorKey(actionEdge.end.y));
                }

                if (intersectingFacesBegin == null) {
                    intersectingFacesBegin = new HashSet<>();
                }
                actualEdges.put(actionEdge.begin.y, intersectingFacesBegin);

                if (intersectingFacesEnd == null) {
                    intersectingFacesEnd = new HashSet<>();
                }
                actualEdges.put(actionEdge.end.y, intersectingFacesEnd);

                SortedMap<Double, HashSet<EdgeOfGraph<Vertex>>> tmp = actualEdges.subMap(actionEdge.begin.y, actionEdge.end.y);
                for (Double d : tmp.keySet()) {
                    actualEdges.get(d).add(actionEdge);
                }

                continue;
            }

            Vertex vertex = action.vertex();

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