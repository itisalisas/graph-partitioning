package graphPreparation;

import geometry.Geometry;
import graph.Graph;
import graph.Point;
import graph.Vertex;
import graph.VertexOfDualGraph;
import readWrite.CoordinateConversion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class NestedFacesRemover {

    private static final Logger logger = LoggerFactory.getLogger(NestedFacesRemover.class);

    public static Set<VertexOfDualGraph> removeNestedFaces(
            Graph<VertexOfDualGraph> dualGraph,
            VertexOfDualGraph outerFace
    ) {
        Set<VertexOfDualGraph> totalRemoved = new HashSet<>();

        logger.debug("removeNestedFaces start: dualGraph has {} vertices", dualGraph.verticesNumber());

        boolean changed = true;
        int iteration = 0;
        while (changed) {
            changed = false;
            iteration++;
            Set<VertexOfDualGraph> articulationPoints = findArticulationPoints(dualGraph);

            logger.debug("iteration {}: found {} articulation points", iteration, articulationPoints.size());

            Map<VertexOfDualGraph, Set<VertexOfDualGraph>> apToNested = new LinkedHashMap<>();
            for (VertexOfDualGraph ap : articulationPoints) {
                Set<VertexOfDualGraph> nested = findNestedVertices(dualGraph, ap, outerFace);
                logger.debug("AP name={}: found {} nested vertices {}", ap.getName(), nested.size(), nested.stream().map(Vertex::getName).toList());
                if (!nested.isEmpty()) {
                    apToNested.put(ap, nested);
                }
            }

            Set<VertexOfDualGraph> toRemove = new HashSet<>();
            for (Map.Entry<VertexOfDualGraph, Set<VertexOfDualGraph>> entry : apToNested.entrySet()) {
                VertexOfDualGraph ap = entry.getKey();
                Set<VertexOfDualGraph> nested = entry.getValue();
                double addedWeight = nested.stream().mapToDouble(VertexOfDualGraph::getWeight).sum();
                ap.setWeight(ap.getWeight() + addedWeight);
                logger.debug("AP name={}: adding weight {} from {} nested vertices", ap.getName(), addedWeight, nested.size());
                toRemove.addAll(nested);
            }

            if (!toRemove.isEmpty()) {
                logger.debug("iteration {}: removing {} vertices", iteration, toRemove.size());
                for (VertexOfDualGraph v : toRemove) {
                    dualGraph.deleteVertex(v);
                }
                totalRemoved.addAll(toRemove);
                changed = true;
            }
        }

        logger.debug("removeNestedFaces end: removed {} total, dualGraph now has {} vertices",
                totalRemoved.size(), dualGraph.verticesNumber());
        return totalRemoved;
    }

    private static Set<VertexOfDualGraph> findNestedVertices(
            Graph<VertexOfDualGraph> dualGraph,
            VertexOfDualGraph ap,
            VertexOfDualGraph outerFace) {

        Set<VertexOfDualGraph> allowed = new HashSet<>(dualGraph.verticesArray());
        allowed.remove(ap);

        List<Set<VertexOfDualGraph>> components =
                findComponents(dualGraph, allowed);

        Set<VertexOfDualGraph> nested = new HashSet<>();

        for (Set<VertexOfDualGraph> component : components) {

            // component connected to outer face is NOT nested
            if (component.contains(outerFace)) {
                continue;
            }

            // every other component is enclosed
            nested.addAll(component);
        }

        return nested;
    }

    private static List<Set<VertexOfDualGraph>> findComponents(
            Graph<VertexOfDualGraph> dualGraph,
            Set<VertexOfDualGraph> allowedVertices) {

        List<Set<VertexOfDualGraph>> components = new ArrayList<>();
        Set<VertexOfDualGraph> visited = new HashSet<>();

        for (VertexOfDualGraph v : allowedVertices) {
            if (!visited.contains(v)) {
                Set<VertexOfDualGraph> component = new HashSet<>();
                bfsComponent(dualGraph, v, allowedVertices, component, visited);
                components.add(component);
            }
        }

        return components;
    }

    private static void bfsComponent(
            Graph<VertexOfDualGraph> dualGraph,
            VertexOfDualGraph start,
            Set<VertexOfDualGraph> allowedVertices,
            Set<VertexOfDualGraph> component,
            Set<VertexOfDualGraph> visited) {

        Queue<VertexOfDualGraph> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            VertexOfDualGraph current = queue.poll();
            component.add(current);

            Map<VertexOfDualGraph, ?> neighbors = dualGraph.getEdges().get(current);
            if (neighbors == null) continue;
            for (VertexOfDualGraph neighbor : neighbors.keySet()) {
                if (!visited.contains(neighbor) && allowedVertices.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
    }

    private static Set<VertexOfDualGraph> findArticulationPoints(Graph<VertexOfDualGraph> dualGraph) {
        Set<VertexOfDualGraph> articulationPoints = new HashSet<>();
        List<VertexOfDualGraph> vertices = dualGraph.verticesArray();
        if (vertices.isEmpty()) return articulationPoints;

        Map<VertexOfDualGraph, Integer> disc = new HashMap<>();
        Map<VertexOfDualGraph, Integer> low = new HashMap<>();
        int[] timer = {0};

        for (VertexOfDualGraph start : vertices) {
            if (!disc.containsKey(start)) {
                findAPDFS(dualGraph, start, disc, low, articulationPoints, timer);
            }
        }

        return articulationPoints;
    }

    private static void findAPDFS(
            Graph<VertexOfDualGraph> dualGraph,
            VertexOfDualGraph start,
            Map<VertexOfDualGraph, Integer> disc,
            Map<VertexOfDualGraph, Integer> low,
            Set<VertexOfDualGraph> articulationPoints,
            int[] timer) {

        record Frame(VertexOfDualGraph v, VertexOfDualGraph parent,
                     Iterator<VertexOfDualGraph> iter, int[] childCount) {}

        disc.put(start, timer[0]);
        low.put(start, timer[0]);
        timer[0]++;

        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(start, null,
                dualGraph.getEdges().get(start).keySet().iterator(), new int[]{0}));

        while (!stack.isEmpty()) {
            Frame frame = stack.peek();
            VertexOfDualGraph u = frame.v();

            boolean pushed = false;
            while (frame.iter().hasNext()) {
                VertexOfDualGraph v = frame.iter().next();
                if (!disc.containsKey(v)) {
                    frame.childCount()[0]++;
                    disc.put(v, timer[0]);
                    low.put(v, timer[0]);
                    timer[0]++;
                    stack.push(new Frame(v, u,
                            dualGraph.getEdges().get(v).keySet().iterator(), new int[]{0}));
                    pushed = true;
                    break;
                } else if (!v.equals(frame.parent())) {
                    low.put(u, Math.min(low.get(u), disc.get(v)));
                }
            }

            if (!pushed) {
                stack.pop();
                if (!stack.isEmpty()) {
                    Frame parentFrame = stack.peek();
                    VertexOfDualGraph p = parentFrame.v();
                    low.put(p, Math.min(low.get(p), low.get(u)));

                    if (parentFrame.parent() == null) {
                        // root: AP iff more than one DFS tree child
                        if (parentFrame.childCount()[0] > 1) {
                            articulationPoints.add(p);
                        }
                    } else {
                        if (low.get(u) >= disc.get(p)) {
                            articulationPoints.add(p);
                        }
                    }
                }
            }
        }
    }
}