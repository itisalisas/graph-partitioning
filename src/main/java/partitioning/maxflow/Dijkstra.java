package partitioning.maxflow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

import graph.Edge;
import graph.Graph;
import graph.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import partitioning.entities.DijkstraResult;
import partitioning.entities.VertexDistance;

public class Dijkstra {
    private static final Logger logger = LoggerFactory.getLogger(Dijkstra.class);

    /**
     * Внутреннее состояние алгоритма Dijkstra
     */
    private static class DijkstraState {
        final Map<Vertex, Double> distances;
        final Map<Vertex, Vertex> previous;
        final PriorityQueue<VertexDistance> queue;

        Vertex targetVertex;
        double minDistance;

        DijkstraState() {
            this.distances = new HashMap<>();
            this.previous = new HashMap<>();
            this.queue = new PriorityQueue<>(Comparator.comparingDouble(VertexDistance::distance));
            this.targetVertex = null;
            this.minDistance = Double.MAX_VALUE;
        }
    }

    /**
     * Находит кратчайший путь от множества источников до целевой границы
     */
    public static Optional<DijkstraResult> dijkstraMultiSource(
            Graph<Vertex> graph,
            List<Vertex> sourceVertices,
            List<Vertex> targetBoundary,
            CornerConstraints cornerConstraints) {

        logDebugInfo(graph, sourceVertices, targetBoundary, cornerConstraints);

        DijkstraState state = new DijkstraState();
        initializeDistances(state, graph, sourceVertices);

        processGraph(state, graph, targetBoundary, cornerConstraints);

        if (state.targetVertex == null) {
            logger.debug("  No target vertex found!");
            return Optional.empty();
        }

        List<Vertex> path = reconstructPath(state.previous, state.targetVertex);

        return Optional.of(createResult(path, state));
    }

    /**
     * Логирует отладочную информацию
     */
    private static void logDebugInfo(
            Graph<Vertex> graph,
            List<Vertex> sourceVertices,
            List<Vertex> targetBoundary,
            CornerConstraints cornerConstraints) {

        logger.debug("=== Dijkstra Multi-Source Debug ===");
        logger.debug("  Graph vertices: {}", graph.verticesArray().size());
        logger.debug("  Source vertices: {} {}", sourceVertices.size(), 
                sourceVertices.stream().map(Vertex::getName).toList());
        logger.debug("  Target boundary: {} vertices", targetBoundary.size());
        logger.debug("  Corner constraints: {}", cornerConstraints.getCornerVertices().stream().toList());
        for (var v: cornerConstraints.getCornerVertices()) {
            var u = cornerConstraints.getAllowedEdgesForCorner();
            for (var e: u.get(v)) {
                logger.debug("    {} -> {}", e.begin.name, e.end.name);
            }
        }

        // logSourceVerticesInfo(graph, sourceVertices);
    }

    /**
     * Логирует информацию об источниках
     */
    private static void logSourceVerticesInfo(Graph<Vertex> graph, List<Vertex> sourceVertices) {
        for (Vertex src : sourceVertices) {
            if (graph.getEdges().containsKey(src)) {
                List<Long> neighborIds = graph.getEdges().get(src).keySet().stream()
                        .limit(10)
                        .map(Vertex::getName)
                        .toList();


                logger.debug("  Source vertex {} (isOnBoundary)= {} has {} neighbors: {}", 
                        src.getName(), src.getIsOnBoundary(), graph.getEdges().get(src).size(), neighborIds);

            } else {
                logger.debug("  Source vertex {} NOT IN GRAPH!", src.getName());
            }
        }
    }

    /**
     * Инициализирует расстояния для всех вершин
     */
    private static void initializeDistances(
            DijkstraState state,
            Graph<Vertex> graph,
            List<Vertex> sourceVertices) {

        // Устанавливаем бесконечное расстояние для всех вершин
        for (Vertex v : graph.verticesArray()) {
            state.distances.put(v, Double.MAX_VALUE);
        }

        // Устанавливаем нулевое расстояние для источников
        for (Vertex sourceVertex : sourceVertices) {
            state.distances.put(sourceVertex, 0.0);
            state.queue.add(new VertexDistance(sourceVertex, 0.0));
        }
    }

    /**
     * Основной алгоритм Dijkstra - обрабатывает граф
     */
    private static void processGraph(
            DijkstraState state,
            Graph<Vertex> graph,
            List<Vertex> targetBoundary,
            CornerConstraints cornerConstraints) {

        while (!state.queue.isEmpty()) {
            VertexDistance current = state.queue.poll();

            // Пропускаем устаревшие записи в очереди
            if (isOutdated(current, state.distances)) {
                continue;
            }

            // Проверяем достигли ли целевой границы
            if (isBoundaryContainsVertex(targetBoundary, current.vertex()) && current.vertex().getIsOnBoundary()) {
                updateTarget(state, current);
                // continue;
            }

            // Обрабатываем соседей текущей вершины
            processNeighbors(state, graph, current, cornerConstraints);
        }
    }

    private static boolean isBoundaryContainsVertex(List<Vertex> boundary, Vertex vertex) {
        if (boundary.contains(vertex)) {
            return true;
        }
        // проверяем что основная вершина для разделенной вершины на границе
        Vertex vertexMain = new Vertex(vertex.name / 1000, vertex.x, vertex.y);
        return boundary.contains(vertexMain);
    }

    /**
     * Проверяет является ли запись в очереди устаревшей
     */
    private static boolean isOutdated(VertexDistance current, Map<Vertex, Double> distances) {
        return current.distance() > distances.get(current.vertex());
    }

    /**
     * Обновляет целевую вершину если найдена лучшая
     */
    private static void updateTarget(DijkstraState state, VertexDistance current) {
        if (current.distance() < state.minDistance) {
            state.minDistance = current.distance();
            state.targetVertex = current.vertex();
            logger.debug("  Found target vertex: {} with distance: {}", 
                    current.vertex().getName(), current.distance());
        }
    }

    /**
     * Обрабатывает всех соседей текущей вершины
     */
    private static void processNeighbors(
            DijkstraState state,
            Graph<Vertex> graph,
            VertexDistance current,
            CornerConstraints cornerConstraints) {

        Map<Vertex, Edge> neighbors = graph.getEdges().get(current.vertex());
        if (neighbors == null) {
            return;
        }

        for (Map.Entry<Vertex, Edge> entry : neighbors.entrySet()) {
            Vertex neighbor = entry.getKey();

            if (!cornerConstraints.isNeighborAllowed(current.vertex(), neighbor)) {
                continue;
            }
            processNeighbor(state, current.vertex(), entry.getKey(), entry.getValue());
        }
    }

    /**
     * Обрабатывает одного соседа текущей вершины
     */
    private static void processNeighbor(
            DijkstraState state,
            Vertex currentVertex,
            Vertex neighbor,
            Edge edge) {

        double currentDistance = state.distances.get(currentVertex);
        double edgeLength = edge.length;
        double newDistance = currentDistance + edgeLength;

        if (newDistance < state.distances.get(neighbor)) {
            state.distances.put(neighbor, newDistance);
            state.previous.put(neighbor, currentVertex);
            state.queue.add(new VertexDistance(neighbor, newDistance));
        }
    }

    /**
     * Восстанавливает путь от источника до целевой вершины
     */
    private static List<Vertex> reconstructPath(
            Map<Vertex, Vertex> previous,
            Vertex targetVertex) {

        List<Vertex> path = new ArrayList<>();
        Vertex current = targetVertex;

        while (current != null) {
            path.add(0, current);
            current = previous.get(current);
        }

        return path;
    }

    /**
     * Создает результат алгоритма Dijkstra
     */
    private static DijkstraResult createResult(List<Vertex> path, DijkstraState state) {
        return new DijkstraResult(
                path,
                state.minDistance,
                state.previous,
                state.distances,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0.0
        );
    }

    /**
     * Находит кратчайший путь от одного источника до целевой границы
     */
    public static Optional<DijkstraResult> dijkstraSingleSource(
            Graph<Vertex> graph,
            Vertex sourceVertex,
            List<Vertex> targetBoundary,
            CornerConstraints cornerConstraints) {
        return dijkstraMultiSource(graph, List.of(sourceVertex), targetBoundary, cornerConstraints);
    }

}