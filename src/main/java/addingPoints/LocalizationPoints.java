package addingPoints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;
import graphPreparation.SweepLine;

public record LocalizationPoints(HashSet<Vertex> newVertices) {
    public HashMap<VertexOfDualGraph, ArrayList<Vertex>> findFacesForPoints(Graph<VertexOfDualGraph> dualGraph) {
        ArrayList<EdgeOfGraph<Vertex>> diagonalList = new ArrayList<>();
        HashMap<EdgeOfGraph<Vertex>, VertexOfDualGraph> returnFromSimplification = new HashMap<>();
        CoordinateConstraintsForFace coordConst;
        EdgeOfGraph<Vertex> diagonal;
        for (VertexOfDualGraph ver : dualGraph.getEdges().keySet()) {
            coordConst = new CoordinateConstraintsForFace(ver.getVerticesOfFace());
            diagonal = new EdgeOfGraph<>(
                    new Vertex(0, coordConst.getMinX(), coordConst.getMinY()),
                    new Vertex(0, coordConst.getMaxX(), coordConst.getMaxY()), 0
            );

            diagonalList.add(diagonal);
            returnFromSimplification.put(diagonal, ver);
        }
        SweepLine sp = new SweepLine();
        HashMap<Vertex, VertexOfDualGraph> ans = sp.findFacesOfVertices(
                diagonalList,
                returnFromSimplification,
                this.newVertices
        );
        HashMap<VertexOfDualGraph, ArrayList<Vertex>> ans1 = new HashMap<>();
        for (Vertex v : ans.keySet()) {
            VertexOfDualGraph face = ans.get(v);
            if (!ans1.containsKey(face)) {
                ans1.put(face, new ArrayList<>());
            }
            ans1.get(face).add(v);
        }
        return ans1;
    }
}
