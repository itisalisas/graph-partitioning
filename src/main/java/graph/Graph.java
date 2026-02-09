package graph;

import java.util.*;

public class Graph<T extends Vertex> {
    /*
     * vertices - keys for HashMap
     */
    private final HashMap<T, HashMap<T, Edge>> edges;
    private HashMap<Vertex, HashMap<Vertex, VertexOfDualGraph>> edgeToDualVertex = new HashMap<>();

    public Graph() {
        this.edges = new HashMap<>();
    }

    public Graph(HashMap<T, HashMap<T, Edge>> edges) {
        this.edges = edges;
    }

    @Override
    public Graph<T> clone() {
        Graph<T> result = new Graph<T>();
        HashMap<T, T> oldNewVertices = new HashMap<>();
        //copy vertices
        for (T begin : this.edges.keySet()) {
            T newVertex = (T) begin.copy();
            result.edges.put(newVertex, new HashMap<>());
            oldNewVertices.put(begin, newVertex);
        }
        //copy edges
        for (T begin : this.edges.keySet()) {
            for (T end : this.edges.get(begin).keySet()) {
                Edge originalEdge = this.edges.get(begin).get(end);
                result.edges.get(oldNewVertices.get(begin)).put(oldNewVertices.get(end), originalEdge.clone());
            }
        }
        return result;
    }

    public T addVertex(T v) {
        if (!edges.containsKey(v)) {
            edges.put(v, new HashMap<>());
        }
        return v;
    }

    public void deleteVertex(T v) {
        edges.remove(v);
        for (T begin : edges.keySet()) {
            edges.get(begin).remove(v);
        }

    }

    public Double verticesWeight() {
        return verticesArray().stream().mapToDouble(T::getWeight).sum();
    }

    public void addEdge(T begin, T end, double length, double bandwidth) {
        if (begin.equals(end)) return;
        addVertex(begin);
        addVertex(end);
        edges.get(begin).put(end, new Edge(length, bandwidth));
        edges.get(end).put(end, new Edge(length, bandwidth));
    }

    public void addEdge(T begin, T end, double length) {
        if (begin.equals(end)) return;
        addVertex(begin);
        addVertex(end);
        edges.get(begin).put(end, new Edge(length));
        edges.get(end).put(begin, new Edge(length));
    }

    public void deleteEdge(T begin, T end) {
        if (edges.get(begin) != null) {
            edges.get(begin).remove(end);
        }
        if (edges.get(end) != null) {
            edges.get(end).remove(begin);
        }
    }

    public HashMap<T, HashMap<T, Edge>> getEdges() {
        return edges;
    }

    public int verticesNumber() {
        return edges.size();
    }

    public List<T> verticesArray() {
        return edges.keySet().stream().toList();
    }

    public int edgesNumber() {
        int res = 0;
        for (T begin : edges.keySet()) {
            res = res + edges.get(begin).size();
        }
        return res;
    }

    public EdgeOfGraph<T>[] edgesArray() {
        int iter = 0;
        EdgeOfGraph<T>[] ans = new EdgeOfGraph[edgesNumber()];
        for (T begin : edges.keySet()) {
            for (T end : edges.get(begin).keySet()) {
                ans[iter++] = new EdgeOfGraph<T>((T) begin,
                        (T) end,
                        edges.get(begin).get(end).length,
                        edges.get(begin).get(end).flow,
                        edges.get(begin).get(end).getBandwidth());
            }
        }
        return ans;
    }


    public ArrayList<EdgeOfGraph<T>> undirEdgesArray() {
        HashSet<EdgeOfGraph<T>> back = new HashSet<EdgeOfGraph<T>>();
        ArrayList<EdgeOfGraph<T>> ans = new ArrayList<EdgeOfGraph<T>>();
        for (T begin : edges.keySet()) {
            for (T end : edges.get(begin).keySet()) {
                EdgeOfGraph<T> tmp = new EdgeOfGraph<T>((T) begin,
                        (T) end,
                        edges.get(begin).get(end).length,
                        edges.get(begin).get(end).flow,
                        edges.get(begin).get(end).getBandwidth());
                if (back.contains(tmp)) continue;
                back.add(new EdgeOfGraph<T>((T) begin,
                        (T) end,
                        edges.get(begin).get(end).length,
                        edges.get(begin).get(end).flow,
                        edges.get(begin).get(end).getBandwidth()));
                ans.add(tmp);
            }
        }
        return ans;
    }


    public int edgesNumberInComponentUndirGraph(HashSet<T> vertexInComponent) {
        int edgesNumber = 0;
        for (T begin : vertexInComponent) {
            if (edges.get(begin) == null)
                continue;
            for (T end : edges.get(begin).keySet()) {
                if (vertexInComponent.contains(end)) {
                    edgesNumber++;
                }
            }
        }
        return edgesNumber;
    }

    public void deleteEmptyVertexUndirGraph() {
        ArrayList<T> deleteV = new ArrayList<>();
        for (T v : this.edges.keySet()) {
            if (this.edges.get(v).isEmpty())
                deleteV.add(v);
        }
        for (T t : deleteV) {
            this.deleteVertex(t);
        }
    }

    public ArrayList<HashSet<T>> splitForConnectedComponents() {
        // make undirected
        Graph<T> undirGraph = makeUndirectedGraph();
        ArrayList<HashSet<T>> component = new ArrayList<HashSet<T>>();
        HashSet<T> visited = new HashSet<T>();
        HashSet<T> actualComp = new HashSet<T>();
        for (T begin : edges.keySet()) {
            if (visited.contains(begin)) {
                continue;
            } else {
                actualComp.add(begin);
                visited.add(begin);
                undirGraph.dfsComponents(begin, actualComp, visited);
                component.add(actualComp);
                actualComp = new HashSet<T>();
            }
        }
        return component;
    }

    private void dfsComponents(T begin, HashSet<T> actualComp, HashSet<T> visited) {
        Stack<T> stack = new Stack<>();
        stack.push(begin);
        visited.add(begin);

        while (!stack.isEmpty()) {
            T current = stack.pop();
            actualComp.add(current);

            if (edges.get(current) != null) {
                for (T neighbor : edges.get(current).keySet()) {
                    if (!visited.contains(neighbor)) {
                        stack.push(neighbor);
                        visited.add(neighbor);
                    }
                }
            }
        }
    }

    public Graph<T> makeUndirectedGraph() {
        Graph<T> graph = new Graph<>();
        for (T vertex : edges.keySet()) {
            if (vertex instanceof VertexOfDualGraph vertexOfDualGraph) {
                graph.addVertex((T) (new VertexOfDualGraph(vertexOfDualGraph)));
            } else {
                graph.addVertex((T) (new Vertex(vertex.clone())));
            }
        }
        for (T begin : edges.keySet()) {
            for (T end : edges.get(begin).keySet()) {
                graph.addEdge(begin, end, edges.get(begin).get(end).length);
                graph.addEdge(end, begin, edges.get(begin).get(end).length);
            }
        }
        return graph;
    }

    public void dfsBridges(HashSet<T> vertexInComponent,
                           T begin,
                           T prev,
                           HashSet<T> used,
                           int timer,
                           HashMap<T, Integer> inTime,
                           HashMap<T, Integer> returnTime,
                           ArrayList<EdgeOfGraph<T>> bridges) {
        used.add(begin);
        timer++;
        inTime.put(begin, timer);
        returnTime.put(begin, timer);
        if (edges.get(begin) == null) {
            return;
        }
        for (T out : edges.get(begin).keySet()) {
            if (!vertexInComponent.contains(out))
                continue;
            if (out.equals(prev))
                continue;
            if (used.contains(out)) {
                if (inTime.containsKey(out) && inTime.get(out) < returnTime.get(begin)) {
                    returnTime.replace(begin, returnTime.get(begin), inTime.get(out));
                }
            } else {
                this.dfsBridges(vertexInComponent, out, begin, used, timer, inTime, returnTime, bridges);
                if (returnTime.containsKey(out) && returnTime.get(out) < returnTime.get(begin)) {
                    returnTime.replace(begin, returnTime.get(begin), returnTime.get(out));
                }
                if (!returnTime.containsKey(out) || (returnTime.containsKey(out) && inTime.get(begin) < returnTime.get(out))) {
                    // delete bridge
                    bridges.add(new EdgeOfGraph<T>(begin, out, edges.get(begin).get(out).length));
//					undirGraph.deleteEdge(begin, out);
//					undirGraph.deleteEdge(out, begin);
                }

            }
        }
    }

    public Graph<T> createSubgraph(Set<T> verticesOfSubgraph) {
        Graph<T> subgraph = new Graph<>();
        List<EdgeOfGraph<T>> edges = Arrays.stream(edgesArray()).toList();
        List<T> vertices = new ArrayList<>(verticesArray());

        for (T vertex : vertices) {
            if (verticesOfSubgraph.contains(vertex)) {
                subgraph.addVertex(vertex);
            }
        }

        for (EdgeOfGraph<T> edge : edges) {
            if (verticesOfSubgraph.contains(edge.begin) && verticesOfSubgraph.contains(edge.end)) {
                if (edge.begin instanceof VertexOfDualGraph vertexOfDualGraph1 && edge.end instanceof VertexOfDualGraph vertexOfDualGraph2) {
                    subgraph.addEdge((T) new VertexOfDualGraph(vertexOfDualGraph1),
                            (T) new VertexOfDualGraph(vertexOfDualGraph2),
                            edge.length);
                } else if (edge.begin instanceof PartitionGraphVertex) {
                    subgraph.addEdge((T) new PartitionGraphVertex(edge.begin),
                            (T) new PartitionGraphVertex(edge.end),
                            edge.length);
                } else {
                    subgraph.addEdge((T) new Vertex(edge.begin), (T) new Vertex(edge.end), edge.length);
                }
            }
        }

        return subgraph;
    }


    public Graph<T> createSubgraphFromFaces(List<List<T>> faces) {
        Graph<T> subgraph = new Graph<T>();

        // Добавляем вершины в подграф
        for (List<T> face : faces) {
            for (T vertex : face) {
                if (!subgraph.getEdges().containsKey(vertex)) {
                    subgraph.addVertex(vertex);
                }
            }
        }
        // Добавляем рёбра в подграф
        for (List<T> face : faces) {
            for (int i = 0; i < face.size(); i++) {
                T v1 = face.get(i);
                T v2 = face.get((i + 1) % face.size());

                subgraph.addEdge(v1, v2, v1.getLength(v2));
            }
        }

        return subgraph;
    }


    public Graph<T> getLargestConnectedComponent() {
        List<HashSet<T>> connectivityComponents = this.makeUndirectedGraph().splitForConnectedComponents();
        HashSet<T> largestComponent = connectivityComponents.stream().max(Comparator.comparingInt(HashSet::size)).orElseThrow();
        return this.createSubgraph(largestComponent);
    }


    public boolean isConnected() {
        return splitForConnectedComponents().size() == 1;
    }


    public double verticesSumWeight() {
        double ans = 0;
        for (Vertex ver : edges.keySet()) {
            ans = ans + ver.getWeight();
        }
        return ans;
    }


    public void correctVerticesWeight() {
        for (T begin : edges.keySet()) {
            for (T end : edges.get(begin).keySet()) {
                for (T check : edges.keySet()) {
                    if (end.equals(check)) {
                        end.setWeight(check.getWeight());
                    }
                }
            }
        }
    }


    public int countZeroWeightVertices() {
        int ans = 0;
        for (Vertex v : edges.keySet()) {
            if (v.getWeight() == 0) {
                ans++;
            }
        }
        return ans;
    }


    public HashMap<T, Integer> initVertexInFaceCounter() {
        HashMap<T, Integer> res = new HashMap<T, Integer>();
        for (T v : this.edges.keySet()) {
            res.put(v, 0);
        }
        return res;
    }


    public HashMap<T, TreeSet<EdgeOfGraph<T>>> arrangeByAngle() {
        Comparator<EdgeOfGraph<T>> edgeComp = (o1, o2) -> {
            double a1 = o1.getCorner();
            double a2 = o2.getCorner();
            return Double.compare(a1, a2);
        };
        HashMap<T, TreeSet<EdgeOfGraph<T>>> res = new HashMap<T, TreeSet<EdgeOfGraph<T>>>();
        for (T begin : this.getEdges().keySet()) {
            res.put(begin, new TreeSet<EdgeOfGraph<T>>(edgeComp));
            for (T end : this.getEdges().get(begin).keySet()) {
                res.get(begin).add(new EdgeOfGraph<T>(begin, end, this.getEdges().get(begin).get(end).length));
            }
        }
        return res;
    }


    public T smallestVertex() {
        return edges.keySet().stream().min(Comparator.comparingDouble(v -> v.weight)).orElse(null);
    }


    public List<T> sortNeighbors(T vertex) {
        return edges.get(vertex).keySet().stream().sorted(Comparator.comparingDouble(v -> -v.weight)).toList();
    }

    public void addBoundEdges(List<T> boundVertices) {
        for (int i = 0; i < boundVertices.size(); i++) {
            T v = boundVertices.get(i);
            T next = boundVertices.get((i + 1) % boundVertices.size());
            addVertex(v);
            addVertex(next);
            addEdge(v, next, v.getLength(next));
        }
    }

    public void addVertexInSubgraph(T v, Graph<T> mainGraph) {
        addVertex(v);
        for (T neighbor : mainGraph.getEdges().get(v).keySet()) {
            if (edges.containsKey(neighbor)) {
                addEdge(v, neighbor, v.getLength(neighbor));
            }
        }
    }

    public void buildEdgeToDualVertexMap(Graph<VertexOfDualGraph> dualGraph) {
        for (var v: dualGraph.verticesArray()) {
            List<Vertex> faceVertices = v.getVerticesOfFace();
            for (int i = 0; i < faceVertices.size(); i++) {
                var cur = faceVertices.get(i);
                var next = faceVertices.get((i + 1) % faceVertices.size());
                if (!edgeToDualVertex.containsKey(cur)) {
                    edgeToDualVertex.put(cur, new HashMap<>());
                }
                HashMap<Vertex, VertexOfDualGraph> map = edgeToDualVertex.get(cur);
                map.put(next, v);
            }
        }
    }

    public HashMap<Vertex, HashMap<Vertex, VertexOfDualGraph>> getEdgeToDualVertexMap() {
        return edgeToDualVertex;
    }

    public void setEdgeToDualVertexMap(HashMap<Vertex, HashMap<Vertex, VertexOfDualGraph>> map) {
        edgeToDualVertex = map;
    }

}
