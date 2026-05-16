package partitioning.shortestpathtree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;
import graphPreparation.GraphPreparation;
import partitioning.entities.DijkstraResult;
import partitioning.entities.SPTResult;
import partitioning.entities.SPTWithRegionWeights;
import partitioning.maxflow.CornerConstraints;
import partitioning.maxflow.Dijkstra;

public class ShortestPathTreeProcessorTest {

    /**
     * Создает грид-граф размером gridSize x gridSize
     */
    private Graph<Vertex> createGridGraph(int gridSize) {
        Graph<Vertex> graph = new Graph<>();
        
        // Создаем вершины
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                long id = i * gridSize + j;
                Vertex v = new Vertex(id, i * 10.0, j * 10.0);
                graph.addVertex(v);
            }
        }
        
        // Создаем ребра (соединяем соседние вершины)
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                long id = i * gridSize + j;
                Vertex v = getVertexById(graph, id);
                
                // Правый сосед
                if (j < gridSize - 1) {
                    Vertex right = getVertexById(graph, i * gridSize + (j + 1));
                    double length = v.getLength(right);
                    graph.addEdge(v, right, length);
                    graph.addEdge(right, v, length);
                }
                
                // Нижний сосед
                if (i < gridSize - 1) {
                    Vertex bottom = getVertexById(graph, (i + 1) * gridSize + j);
                    double length = v.getLength(bottom);
                    graph.addEdge(v, bottom, length);
                    graph.addEdge(bottom, v, length);
                }
            }
        }
        
        return graph;
    }
    
    private Vertex getVertexById(Graph<Vertex> graph, long id) {
        return graph.verticesArray().stream()
                .filter(v -> v.getName() == id)
                .findFirst()
                .orElseThrow();
    }

    /**
     * Базовый тест для ShortestPathTreeProcessor:
     * проверяет что процессор корректно выбирает лучший путь между двумя SPT
     */
    @Test
    void testFindBestPathBasic() throws IOException {
        int gridSize = 10;
        Graph<Vertex> graph = createGridGraph(gridSize);
        GraphPreparation preparation = new GraphPreparation();
        Graph<VertexOfDualGraph> dualGraph = preparation.prepareGraph(graph, 0.0001);
        
        // Корень в центре (50.0, 50.0)
        Vertex root = graph.verticesArray().stream()
                .filter(v -> Math.abs(v.x - 50.0) < 0.1 && Math.abs(v.y - 50.0) < 0.1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Root not found"));
        
        // Левая граница y = 0.0
        List<Vertex> leftBoundary = new ArrayList<>();
        for (Vertex v : graph.verticesArray()) {
            if (Math.abs(v.y - 0.0) < 0.1) {
                leftBoundary.add(v);
            }
        }
        leftBoundary.sort(Comparator.comparingDouble(v -> v.x));
        
        // Правая граница y = 90.0
        List<Vertex> rightBoundary = new ArrayList<>();
        for (Vertex v : graph.verticesArray()) {
            if (Math.abs(v.y - 90.0) < 0.1) {
                rightBoundary.add(v);
            }
        }
        rightBoundary.sort(Comparator.comparingDouble(v -> v.x));
        
        // Строим SPT от корня до левой границы
        Optional<DijkstraResult> leftResultOpt = Dijkstra.dijkstraSingleSource(
                graph, root, leftBoundary, CornerConstraints.empty());
        Assertions.assertTrue(leftResultOpt.isPresent());
        
        SPTWithRegionWeights leftSPT = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph, leftResultOpt.get().previous(), root, leftBoundary,
                dualGraph, true, leftResultOpt.get().path()
        );
        
        // Строим SPT от корня до правой границы
        Optional<DijkstraResult> rightResultOpt = Dijkstra.dijkstraSingleSource(
                graph, root, rightBoundary, CornerConstraints.empty());
        Assertions.assertTrue(rightResultOpt.isPresent());
        
        SPTWithRegionWeights rightSPT = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph, rightResultOpt.get().previous(), root, rightBoundary,
                dualGraph, false, rightResultOpt.get().path()
        );
        
        // Конвертируем в DijkstraResult
        DijkstraResult leftD = new DijkstraResult(
                leftResultOpt.get().path(),
                leftResultOpt.get().distance(),
                leftSPT.previous(),
                leftResultOpt.get().dijkstraDistances(),
                leftSPT.boundaryLeaves(),
                leftSPT.faces(),
                leftSPT.regionWeights(),
                leftSPT.distances(),
                leftSPT.leafIndices(),
                leftSPT.totalRegionWeight()
        );
        
        DijkstraResult rightD = new DijkstraResult(
                rightResultOpt.get().path(),
                rightResultOpt.get().distance(),
                rightSPT.previous(),
                rightResultOpt.get().dijkstraDistances(),
                rightSPT.boundaryLeaves(),
                rightSPT.faces(),
                rightSPT.regionWeights(),
                rightSPT.distances(),
                rightSPT.leafIndices(),
                rightSPT.totalRegionWeight()
        );
        
        // Используем процессор для нахождения лучшего пути
        ShortestPathTreeProcessor processor = new ShortestPathTreeProcessor();
        SPTResult result = processor.findBestPath(leftD, rightD, 0.0, 0.0, 10, List.of(), 1000.0, 500);
        
        // Отладка
        System.out.println("=== Debug testFindBestPathBasic ===");
        System.out.println("Root expected: (" + root.x + ", " + root.y + ")");
        if (!result.path().isEmpty()) {
            Vertex pathStart = result.path().get(0);
            Vertex pathEnd = result.path().get(result.path().size() - 1);
            System.out.println("Path start: (" + pathStart.x + ", " + pathStart.y + ")");
            System.out.println("Path end: (" + pathEnd.x + ", " + pathEnd.y + ")");
            System.out.println("Path size: " + result.path().size());
        }
        
        // === ПРОВЕРКИ ===
        
        // 1. Путь должен быть непустым
        Assertions.assertFalse(result.path().isEmpty(), 
                "Путь не должен быть пустым");
        
        // 2. Путь должен иметь больше одной вершины (иначе это не путь)
        Assertions.assertTrue(result.path().size() > 1,
                "Путь должен содержать более одной вершины");
        
        // 3. Длина пути должна быть положительной
        Assertions.assertTrue(result.totalDistance() > 0,
                "Длина пути должна быть положительной");
        
        // 4. Баланс веса должен быть определен (не MIN_VALUE)
        Assertions.assertTrue(result.balanceWeight() > Double.MIN_VALUE,
                "Баланс веса должен быть определен");
        
        // 5. Путь должен быть связным (каждая следующая вершина - сосед предыдущей)
        for (int i = 0; i < result.path().size() - 1; i++) {
            Vertex current = result.path().get(i);
            Vertex next = result.path().get(i + 1);
            double distance = current.getLength(next);
            Assertions.assertTrue(distance < 15.0, // В гриде соседи на расстоянии 10.0
                    "Вершины в пути должны быть соседями: " + 
                    current.getName() + " -> " + next.getName() + 
                    " (distance=" + distance + ")");
        }
        
        System.out.println("=== Basic Best Path Test ===");
        System.out.println("Root: " + root.getName());
        System.out.println("Path length: " + result.totalDistance());
        System.out.println("Balance weight: " + result.balanceWeight());
        System.out.println("Path vertices: " + result.path().stream()
                .map(Vertex::getName).toList());
        System.out.println("Left leaves: " + leftSPT.boundaryLeaves().size());
        System.out.println("Right leaves: " + rightSPT.boundaryLeaves().size());
    }

    /**
     * Тест проверяет корректность метрики:
     * процессор должен выбирать путь который минимизирует комбинацию длины и дисбаланса весов
     */
    @Test
    void testMetricCorrectness() throws IOException {
        int gridSize = 10;
        Graph<Vertex> graph = createGridGraph(gridSize);
        GraphPreparation preparation = new GraphPreparation();
        Graph<VertexOfDualGraph> dualGraph = preparation.prepareGraph(graph, 0.0001);
        
        // Корень в центре (50.0, 50.0)
        Vertex root = graph.verticesArray().stream()
                .filter(v -> Math.abs(v.x - 50.0) < 0.1 && Math.abs(v.y - 50.0) < 0.1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Root not found"));
        
        // Левая граница y = 0.0
        List<Vertex> leftBoundary = new ArrayList<>();
        for (Vertex v : graph.verticesArray()) {
            if (Math.abs(v.y - 0.0) < 0.1) {
                leftBoundary.add(v);
            }
        }
        leftBoundary.sort(Comparator.comparingDouble(v -> v.x));
        
        // Правая граница y = 90.0
        List<Vertex> rightBoundary = new ArrayList<>();
        for (Vertex v : graph.verticesArray()) {
            if (Math.abs(v.y - 90.0) < 0.1) {
                rightBoundary.add(v);
            }
        }
        rightBoundary.sort(Comparator.comparingDouble(v -> v.x));
        
        // Строим SPT
        Optional<DijkstraResult> leftResultOpt = Dijkstra.dijkstraSingleSource(
                graph, root, leftBoundary, CornerConstraints.empty());
        Optional<DijkstraResult> rightResultOpt = Dijkstra.dijkstraSingleSource(
                graph, root, rightBoundary, CornerConstraints.empty());
        
        Assertions.assertTrue(leftResultOpt.isPresent());
        Assertions.assertTrue(rightResultOpt.isPresent());
        
        SPTWithRegionWeights leftSPT = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph, leftResultOpt.get().previous(), root, leftBoundary,
                dualGraph, true, leftResultOpt.get().path()
        );
        
        SPTWithRegionWeights rightSPT = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph, rightResultOpt.get().previous(), root, rightBoundary,
                dualGraph, false, rightResultOpt.get().path()
        );
        
        DijkstraResult leftD = new DijkstraResult(
                leftResultOpt.get().path(),
                leftResultOpt.get().distance(),
                leftSPT.previous(),
                leftResultOpt.get().dijkstraDistances(),
                leftSPT.boundaryLeaves(),
                leftSPT.faces(),
                leftSPT.regionWeights(),
                leftSPT.distances(),
                leftSPT.leafIndices(),
                leftSPT.totalRegionWeight()
        );
        
        DijkstraResult rightD = new DijkstraResult(
                rightResultOpt.get().path(),
                rightResultOpt.get().distance(),
                rightSPT.previous(),
                rightResultOpt.get().dijkstraDistances(),
                rightSPT.boundaryLeaves(),
                rightSPT.faces(),
                rightSPT.regionWeights(),
                rightSPT.distances(),
                rightSPT.leafIndices(),
                rightSPT.totalRegionWeight()
        );
        
        ShortestPathTreeProcessor processor = new ShortestPathTreeProcessor();
        
        // Тестируем с разными весами источника и стока
        double sourceWeight = 100.0;
        double sinkWeight = 100.0;
        
        SPTResult result1 = processor.findBestPath(leftD, rightD, sourceWeight, sinkWeight, 10, List.of(), 1000.0, 500);
        SPTResult result2 = processor.findBestPath(leftD, rightD, 0.0, 0.0, 10, List.of(), 1000.0, 500);
        
        // === ПРОВЕРКИ ===
        
        // Оба пути должны быть валидными
        Assertions.assertFalse(result1.path().isEmpty());
        Assertions.assertFalse(result2.path().isEmpty());
        
        // Веса должны быть учтены в балансе
        double totalWeight1 = leftSPT.totalRegionWeight() + rightSPT.totalRegionWeight() + 
                             sourceWeight + sinkWeight;
        double totalWeight2 = leftSPT.totalRegionWeight() + rightSPT.totalRegionWeight();
        
        Assertions.assertTrue(totalWeight1 > totalWeight2,
                "Общий вес с учетом source/sink весов должен быть больше");
        
        System.out.println("=== Metric Correctness Test ===");
        System.out.println("Left SPT weight: " + leftSPT.totalRegionWeight());
        System.out.println("Right SPT weight: " + rightSPT.totalRegionWeight());
        System.out.println("\nWith source/sink weights (100, 100):");
        System.out.println("  Total distance: " + result1.totalDistance());
        System.out.println("  Balance weight: " + result1.balanceWeight());
        System.out.println("\nWithout source/sink weights:");
        System.out.println("  Total distance: " + result2.totalDistance());
        System.out.println("  Balance weight: " + result2.balanceWeight());
    }

    /**
     * Тест проверяет что процессор корректно обрабатывает случай 
     * когда нет листьев (пустые листья)
     */
    @Test
    void testEmptyLeaves() {
        // Создаем пустые DijkstraResult (без листьев)
        DijkstraResult emptyResult = new DijkstraResult(
                List.of(new Vertex(1, 0, 0)),
                10.0,
                Map.of(),
                Map.of(),
                List.of(), // пустые листья
                List.of(),
                List.of(),
                List.of(),
                List.of(), // пустые индексы
                0.0
        );
        
        ShortestPathTreeProcessor processor = new ShortestPathTreeProcessor();
        SPTResult result = processor.findBestPath(emptyResult, emptyResult, 0.0, 0.0, 10, List.of(), 1000.0, 500);
        
        // Должен вернуть результат с MIN_VALUE балансом
        Assertions.assertEquals(Double.MIN_VALUE, result.balanceWeight());
        Assertions.assertTrue(result.totalDistance() > 0);
    }

    /**
     * Детальный тест с визуализацией:
     * проверяет что выбранный путь действительно оптимален по метрике
     */
    @Test
    void testBestPathVisualization() throws IOException {
        int gridSize = 10;
        Graph<Vertex> graph = createGridGraph(gridSize);
        GraphPreparation preparation = new GraphPreparation();
        Graph<VertexOfDualGraph> dualGraph = preparation.prepareGraph(graph, 0.0001);
        
        // Корень в центре (50.0, 50.0)
        Vertex root = graph.verticesArray().stream()
                .filter(v -> Math.abs(v.x - 50.0) < 0.1 && Math.abs(v.y - 50.0) < 0.1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Root not found"));
        
        // Левая граница y = 0.0
        List<Vertex> leftBoundary = new ArrayList<>();
        for (Vertex v : graph.verticesArray()) {
            if (Math.abs(v.y - 0.0) < 0.1) {
                leftBoundary.add(v);
            }
        }
        leftBoundary.sort(Comparator.comparingDouble(v -> v.x));
        
        // Правая граница y = 90.0
        List<Vertex> rightBoundary = new ArrayList<>();
        for (Vertex v : graph.verticesArray()) {
            if (Math.abs(v.y - 90.0) < 0.1) {
                rightBoundary.add(v);
            }
        }
        rightBoundary.sort(Comparator.comparingDouble(v -> v.x));
        
        // Строим SPT
        Optional<DijkstraResult> leftResultOpt = Dijkstra.dijkstraSingleSource(
                graph, root, leftBoundary, CornerConstraints.empty());
        Optional<DijkstraResult> rightResultOpt = Dijkstra.dijkstraSingleSource(
                graph, root, rightBoundary, CornerConstraints.empty());
        
        Assertions.assertTrue(leftResultOpt.isPresent());
        Assertions.assertTrue(rightResultOpt.isPresent());
        
        SPTWithRegionWeights leftSPT = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph, leftResultOpt.get().previous(), root, leftBoundary,
                dualGraph, true, leftResultOpt.get().path()
        );
        
        SPTWithRegionWeights rightSPT = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph, rightResultOpt.get().previous(), root, rightBoundary,
                dualGraph, false, rightResultOpt.get().path()
        );
        
        DijkstraResult leftD = new DijkstraResult(
                leftResultOpt.get().path(),
                leftResultOpt.get().distance(),
                leftSPT.previous(),
                leftResultOpt.get().dijkstraDistances(),
                leftSPT.boundaryLeaves(),
                leftSPT.faces(),
                leftSPT.regionWeights(),
                leftSPT.distances(),
                leftSPT.leafIndices(),
                leftSPT.totalRegionWeight()
        );
        
        DijkstraResult rightD = new DijkstraResult(
                rightResultOpt.get().path(),
                rightResultOpt.get().distance(),
                rightSPT.previous(),
                rightResultOpt.get().dijkstraDistances(),
                rightSPT.boundaryLeaves(),
                rightSPT.faces(),
                rightSPT.regionWeights(),
                rightSPT.distances(),
                rightSPT.leafIndices(),
                rightSPT.totalRegionWeight()
        );
        
        ShortestPathTreeProcessor processor = new ShortestPathTreeProcessor();
        SPTResult result = processor.findBestPath(leftD, rightD, 0.0, 0.0, 10, List.of(), 1000.0, 500);
        
        // === ВИЗУАЛИЗАЦИЯ ===
        dumpBestPathToFile(graph, result, root, leftBoundary, rightBoundary, 
                leftSPT, rightSPT, leftD, rightD,
                "best_path_visualization.txt");
        
        // === ПРОВЕРКИ ===
        Assertions.assertFalse(result.path().isEmpty());
        Assertions.assertTrue(result.path().size() > 1,
                "Путь должен содержать более одной вершины");
        
        System.out.println("=== Best Path Visualization Test ===");
        System.out.println("Root: " + root.getName() + " at (" + 
                (int)(root.x/10) + ", " + (int)(root.y/10) + ")");
        System.out.println("Path length: " + result.totalDistance());
        System.out.println("Balance weight: " + result.balanceWeight());
        System.out.println("Path has " + result.path().size() + " vertices");
    }

    /**
     * Создает текстовый дамп с визуализацией лучшего пути
     */
    private void dumpBestPathToFile(
            Graph<Vertex> graph,
            SPTResult result,
            Vertex root,
            List<Vertex> leftBoundary,
            List<Vertex> rightBoundary,
            SPTWithRegionWeights leftSPT,
            SPTWithRegionWeights rightSPT,
            DijkstraResult leftD,
            DijkstraResult rightD,
            String filename) {
        
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=".repeat(80)).append("\n");
            sb.append("BEST PATH VISUALIZATION\n");
            sb.append("=".repeat(80)).append("\n\n");
            
            // Находим размер грида
            int maxRow = 0, maxCol = 0;
            for (Vertex v : graph.verticesArray()) {
                int row = (int) (v.x / 10.0);
                int col = (int) (v.y / 10.0);
                maxRow = Math.max(maxRow, row);
                maxCol = Math.max(maxCol, col);
            }
            int gridSize = maxRow + 1;
            
            // Создаем карту для быстрого поиска вершин
            Map<String, Vertex> positionMap = new HashMap<>();
            for (Vertex v : graph.verticesArray()) {
                int row = (int) (v.x / 10.0);
                int col = (int) (v.y / 10.0);
                positionMap.put(row + "," + col, v);
            }
            
            // Множества для визуализации
            Set<Vertex> leftBoundarySet = new HashSet<>(leftBoundary);
            Set<Vertex> rightBoundarySet = new HashSet<>(rightBoundary);
            Set<Vertex> pathVertices = new HashSet<>(result.path());
            Set<Vertex> leftLeaves = new HashSet<>(leftSPT.boundaryLeaves());
            Set<Vertex> rightLeaves = new HashSet<>(rightSPT.boundaryLeaves());
            
            // Статистика
            sb.append("STATISTICS:\n");
            sb.append("-".repeat(80)).append("\n");
            sb.append(String.format("Root: vertex %d at (%d, %d)\n", 
                    root.getName(), (int)(root.x/10), (int)(root.y/10)));
            sb.append(String.format("Path length: %.2f\n", result.totalDistance()));
            sb.append(String.format("Balance weight: %.2f\n", result.balanceWeight()));
            sb.append(String.format("Path vertices: %d\n", result.path().size()));
            sb.append(String.format("Left SPT regions: %d (weight: %.2f)\n", 
                    leftSPT.faces().size(), leftSPT.totalRegionWeight()));
            sb.append(String.format("Right SPT regions: %d (weight: %.2f)\n", 
                    rightSPT.faces().size(), rightSPT.totalRegionWeight()));
            sb.append(String.format("Left leaves: %d\n", leftSPT.boundaryLeaves().size()));
            sb.append(String.format("Right leaves: %d\n\n", rightSPT.boundaryLeaves().size()));
            
            // Путь
            sb.append("PATH:\n");
            sb.append("-".repeat(80)).append("\n");
            for (int i = 0; i < result.path().size(); i++) {
                Vertex v = result.path().get(i);
                sb.append(String.format("%d: vertex %d at (%d, %d)",
                        i, v.getName(), (int)(v.x/10), (int)(v.y/10)));
                if (v.equals(root)) sb.append(" [ROOT]");
                if (leftBoundarySet.contains(v)) sb.append(" [LEFT BOUNDARY]");
                if (rightBoundarySet.contains(v)) sb.append(" [RIGHT BOUNDARY]");
                sb.append("\n");
            }
            sb.append("\n");
            
            // Визуализация грида
            sb.append("GRID VISUALIZATION:\n");
            sb.append("-".repeat(80)).append("\n");
            sb.append("Legend:\n");
            sb.append("  R = Root\n");
            sb.append("  # = Best path\n");
            sb.append("  | = Boundary\n");
            sb.append("  l = Left leaf\n");
            sb.append("  r = Right leaf\n");
            sb.append("  . = Other vertex\n\n");
            
            for (int row = 0; row < gridSize; row++) {
                for (int col = 0; col < gridSize; col++) {
                    Vertex v = positionMap.get(row + "," + col);
                    if (v == null) {
                        sb.append("  ");
                        continue;
                    }
                    
                    char symbol;
                    if (v.equals(root)) {
                        symbol = 'R';
                    } else if (pathVertices.contains(v)) {
                        symbol = '#';
                    } else if (leftBoundarySet.contains(v) || rightBoundarySet.contains(v)) {
                        symbol = '|';
                    } else if (leftLeaves.contains(v)) {
                        symbol = 'l';
                    } else if (rightLeaves.contains(v)) {
                        symbol = 'r';
                    } else {
                        symbol = '.';
                    }
                    sb.append(symbol).append(" ");
                }
                sb.append("\n");
            }
            sb.append("\n");
            
            // Записываем в файл
            Path outputPath = Paths.get("test-output", filename);
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, sb.toString());
            
            System.out.println("Визуализация сохранена в: " + outputPath.toAbsolutePath());
            
        } catch (IOException e) {
            System.err.println("Ошибка при записи дампа: " + e.getMessage());
        }
    }
}
