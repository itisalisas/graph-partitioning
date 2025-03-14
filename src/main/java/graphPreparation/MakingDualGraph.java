package graphPreparation;

import java.util.*;

import graph.*;
import org.junit.jupiter.api.Assertions;

import org.junit.jupiter.api.Assertions;

public class MakingDualGraph {
  private HashMap<Vertex, VertexOfDualGraph> comparison;
  public MakingDualGraph() {
    this.comparison = new HashMap<Vertex, VertexOfDualGraph>();
  }

  public HashMap<Vertex, VertexOfDualGraph> getComparison() {
    return this.comparison;
  }

  public Graph<VertexOfDualGraph> buildDualGraph(Graph<Vertex> gph) {
    Graph<VertexOfDualGraph> res = new Graph<>();
    Graph<Vertex> undir = gph.makeUndirectedGraph();
    EdgeOfGraph<Vertex>[] edgesList = undir.edgesArray();
    HashMap<Vertex, Integer> vertexInFaceNumber = gph.initVertexInFaceCounter();
    HashMap<EdgeOfGraph<Vertex>, VertexOfDualGraph> inFace = new HashMap<>();
    HashMap<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedGraph = undir.arrangeByAngle();
    buildDualVertices(res, inFace, sortedGraph, edgesList, vertexInFaceNumber);
    addDualEdges(res, inFace);
    return res;
  }

  private void addDualEdges(Graph<VertexOfDualGraph> res, HashMap<EdgeOfGraph<Vertex>, VertexOfDualGraph> inFace) {
    EdgeOfGraph back = null;
    double oldLength = 0;
    for (EdgeOfGraph edge : inFace.keySet()) {
      back = new EdgeOfGraph(edge.end, edge.begin, edge.getLength());
      if (inFace.get(edge).equals((inFace).get(back))) {
        continue;
      }
      oldLength = 0;
      if (res.getEdges().get(inFace.get(edge)).containsKey(inFace.get(back))) {
        oldLength = res.getEdges().get(inFace.get(edge)).get(inFace.get(back)).getLength();
        res.getEdges().get(inFace.get(edge)).remove(inFace.get(back));
        res.getEdges().get(inFace.get(back)).remove(inFace.get(edge));
      }
      res.getEdges().get(inFace.get(edge)).put(inFace.get(back), new Edge(oldLength + edge.getLength() / 2));
      res.getEdges().get(inFace.get(back)).put(inFace.get(edge), new Edge(oldLength + edge.getLength() / 2));
    }

  }

  private void buildDualVertices(Graph<VertexOfDualGraph> res, HashMap<EdgeOfGraph<Vertex>, VertexOfDualGraph> inFace,
                   HashMap<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedGraph, EdgeOfGraph<Vertex>[] edgesList, HashMap<Vertex, Integer> vertexInFaceNumber) {
    ArrayList<Vertex> verticesOfFace = new ArrayList<Vertex>();
    HashSet<EdgeOfGraph<Vertex>> inActualFace = new HashSet<EdgeOfGraph<Vertex>>();
    long vertName = 0;
    for (int i = 0; i < edgesList.length; i++) {
      if (inFace.containsKey(edgesList[i])) {
        //System.out.println("already in partition: " + edgesList[i].getBegin().getName() + "->" + edgesList[i].getEnd().getName());
        continue;
      }
      findFace(verticesOfFace, inActualFace, sortedGraph, edgesList[i], vertexInFaceNumber);
      Assertions.assertTrue(verticesOfFace.size() >= 3);
      vertName++;
      //System.out.print(vertName + " ");
      VertexOfDualGraph vert = new VertexOfDualGraph(vertName, Vertex.findCenter(verticesOfFace),
          VertexOfDualGraph.sumVertexWeight(verticesOfFace), verticesOfFace);
      res.addVertex(vert);
      comparison.put(vert, vert);
      for (EdgeOfGraph edge : inActualFace) {
        inFace.put(edge, vert);
      }
      verticesOfFace.clear();
      inActualFace.clear();
    }
    //System.out.println("cnt_vert = " + cnt_vert);
    correctFacesWeight(res, sortedGraph, vertexInFaceNumber);
  }

  private void correctFacesWeight(Graph<VertexOfDualGraph> res, HashMap<Vertex, TreeSet<EdgeOfGraph<Vertex>>> sortedGraph,
                  HashMap<Vertex, Integer> vertexInFaceNumber) {
    for (VertexOfDualGraph v : res.getEdges().keySet()) {
      v.setWeight(countFaceWeight(v, vertexInFaceNumber));
    }
    
  }

  private double countFaceWeight(VertexOfDualGraph v, HashMap<Vertex, Integer> vertexInFaceNumber) {
    double ans = 0;
    for (Vertex ver : comparison.get(v).getVerticesOfFace()) {
      ans = ans + ver.getWeight() / vertexInFaceNumber.get(ver);
    }
    return ans;
  }


  public <T extends Vertex> void findFace(ArrayList<T> verticesOfFace, HashSet<EdgeOfGraph<T>> inActualFace,
              HashMap<T, TreeSet<EdgeOfGraph<T>>> sortedGraph, EdgeOfGraph<T> firstEdge, HashMap<T, Integer> vertexInFaceNumber) {
    double faceWeight = 0;
    T prev = firstEdge.begin;
    T begin = firstEdge.end;

    //verticesOfFace.add(prev);
    vertexInFaceNumber.put(prev, vertexInFaceNumber.get(prev) + 1);
    //verticesOfFace.add(begin);
    vertexInFaceNumber.put(begin, vertexInFaceNumber.get(begin) + 1);
    faceWeight += prev.getWeight();
    faceWeight += begin.getWeight();
    inActualFace.add(firstEdge);
    EdgeOfGraph<T> actualEdge = null;
    //sortedGraph.get(prev).remove(firstEdge);
    do {
      // System.out.println("begin = " + begin.getName() + ", prev = " + prev.getName());
      if (sortedGraph.get(begin).isEmpty()) {
        //System.out.println("end");
        //System.out.println("empty vertex");
        return;
      }
      EdgeOfGraph<T> back = new EdgeOfGraph<>(begin, prev, begin.getLength(prev));
      actualEdge = sortedGraph.get(begin).higher(back);
      /*
      for (EdgeOfGraph u : sortedGraph.get(begin)) {
        System.out.println("e: " + u.getBegin().getName() + " -> " + u.getEnd().getName());
      }
      */
      if (actualEdge == null) {
        actualEdge = sortedGraph.get(begin).first();
      }
      prev = actualEdge.begin;
      begin = actualEdge.end;
      verticesOfFace.add(begin);
      vertexInFaceNumber.put(begin, vertexInFaceNumber.get(begin) + 1);
      faceWeight = faceWeight + begin.getWeight();
      inActualFace.add(actualEdge);
      // sortedGraph.get(prev).remove(actualEdge);
      actualEdge = null;
    } while (!(begin.equals(firstEdge.end) && prev.equals(firstEdge.begin)));
    //System.out.println("begin = " + begin.getName() + ", prev = " + prev.getName());
    //System.out.println("begin = " + begin.getX() + "y = " + begin.getY() + ", prev = " + prev.getName());
    //System.out.println("firstEdge.begin = " + firstEdge.getBegin().getName() + ", end = " + firstEdge.getEnd().getName());
  }


  public void removeExternalFace(Graph<VertexOfDualGraph> dualGraph) {
    Vertex leftTop = null;
    Vertex rightBottom = null;

    for (VertexOfDualGraph dualVertex : dualGraph.verticesArray()) {
      for (Vertex v : dualVertex.getVerticesOfFace()) {
        Point point = v;
        if (leftTop == null || (point.getX() < leftTop.getX() ||
            (point.getX() == leftTop.getX() && point.getY() > leftTop.getY()))) {
          leftTop = v;
        }
        if (rightBottom == null || (point.getX() > rightBottom.getX() ||
            (point.getX() == rightBottom.getX() && point.getY() < rightBottom.getY()))) {
          rightBottom = v;
        }
      }
    }

    if (leftTop == null || rightBottom == null) {
      throw new RuntimeException("Couldn't find external vertices");
    }

    // System.out.println("left top = " + leftTop.getName() + ", right bottom = " + rightBottom.getName());

    VertexOfDualGraph externalFaceVertex = null;

    for (VertexOfDualGraph dualVertex : dualGraph.verticesArray()) {
      if (dualVertex.getVerticesOfFace().contains(leftTop) &&
          dualVertex.getVerticesOfFace().contains(rightBottom)) {
        externalFaceVertex = dualVertex;
        break;
      }
    }

    if (externalFaceVertex == null) {
      throw new RuntimeException("Couldn't find external face");
    }

    dualGraph.deleteVertex(externalFaceVertex);
  }
}