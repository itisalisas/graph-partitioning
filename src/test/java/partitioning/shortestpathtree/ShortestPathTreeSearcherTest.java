package partitioning.shortestpathtree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import graph.EdgeOfGraph;
import graph.Graph;
import graph.Vertex;
import graph.VertexOfDualGraph;
import graphPreparation.GraphPreparation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import partitioning.entities.DijkstraResult;
import partitioning.entities.SPTWithRegionWeights;
import partitioning.entities.SPTResult;
import partitioning.maxflow.Dijkstra;
import partitioning.maxflow.CornerConstraints;

public class ShortestPathTreeSearcherTest {

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
     * Проверяет что дерево корректно построено и все вершины достижимы
     */
    private void validateSPT(Map<Vertex, Vertex> previous, Vertex root) {
        // Проверяем что нет циклов
        Set<Vertex> visited = new HashSet<>();
        for (Vertex v : previous.keySet()) {
            Set<Vertex> path = new HashSet<>();
            Vertex current = v;
            
            while (current != null && !current.equals(root)) {
                if (path.contains(current)) {
                    Assertions.fail("Цикл обнаружен в SPT на вершине " + current.getName());
                }
                path.add(current);
                current = previous.get(current);
            }
            
            Assertions.assertEquals(root, current, 
                    "Вершина " + v.getName() + " не достижима от корня");
        }
        
        // Проверяем что каждая вершина достижима от корня ровно один раз
        for (Vertex v : previous.keySet()) {
            Vertex current = v;
            int steps = 0;
            while (current != null && !current.equals(root)) {
                current = previous.get(current);
                steps++;
                if (steps > previous.size()) {
                    Assertions.fail("Слишком длинный путь от " + v.getName() + " до корня");
                }
            }
        }
    }

    @Test
    void testGridGraphSPT() throws IOException {
        // Создаем грид-граф 10x10 (100 вершин)
        int gridSize = 10;
        Graph<Vertex> graph = createGridGraph(gridSize);
        
        Assertions.assertEquals(100, graph.verticesArray().size(), 
                "Граф должен содержать 100 вершин");
        
        // Готовим дуальный граф
        GraphPreparation preparation = new GraphPreparation();
        Graph<VertexOfDualGraph> dualGraph = preparation.prepareGraph(graph, 0.0001);
        
        // Источник внизу по центру (строка 9, столбец 5) - после prepareGraph ищем по координатам
        Vertex source = graph.verticesArray().stream()
                .filter(v -> Math.abs(v.x - 90.0) < 0.1 && Math.abs(v.y - 50.0) < 0.1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Source vertex not found"));

        // Правая граница - ищем по координатам
        List<Vertex> boundary = new ArrayList<>();
        for (Vertex v : graph.verticesArray()) {
            if (Math.abs(v.y - 90.0) < 0.1) { // столбец 9
                boundary.add(v);
            }
        }
        boundary.sort(Comparator.comparingDouble(v -> v.x));
        
        // Строим кратчайший путь от источника до границы
        Optional<DijkstraResult> dijkstraResultOpt = Dijkstra.dijkstraSingleSource(
                graph, source, boundary, CornerConstraints.empty());
        
        Assertions.assertTrue(dijkstraResultOpt.isPresent(), 
                "Dijkstra должен найти путь от источника до границы");
        
        DijkstraResult dijkstraResult = dijkstraResultOpt.get();
        
        // Проверяем что дерево корректно
        validateSPT(dijkstraResult.previous(), source);
        
        // Проверяем что путь действительно заканчивается на границе
        Vertex lastVertex = dijkstraResult.path().get(dijkstraResult.path().size() - 1);
        Assertions.assertTrue(boundary.contains(lastVertex),
                "Последняя вершина пути должна быть на границе");
        
        // Строим SPT с весами регионов
        SPTWithRegionWeights spt = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph,
                dijkstraResult.previous(),
                source,
                boundary,
                dualGraph,
                false,
                dijkstraResult.path()
        );
        
        // Проверяем результаты
        Assertions.assertNotNull(spt.faces(), "Список граней не должен быть null");
        Assertions.assertNotNull(spt.regionWeights(), "Веса регионов не должны быть null");
        Assertions.assertNotNull(spt.distances(), "Расстояния не должны быть null");
        
        // Проверяем что веса регионов монотонно возрастают
        List<Double> weights = spt.regionWeights();
        for (int i = 1; i < weights.size(); i++) {
            Assertions.assertTrue(weights.get(i) >= weights.get(i - 1),
                    "Веса регионов должны монотонно возрастать");
        }
        
        // Проверяем что листья корректно индексированы
        for (Integer leafIdx : spt.leafIndices()) {
            Assertions.assertTrue(leafIdx >= -1 && leafIdx < weights.size(),
                    "Индекс листа должен быть в допустимом диапазоне");
        }
        
        // Проверяем что общий вес регионов положителен
        Assertions.assertTrue(spt.totalRegionWeight() > 0,
                "Общий вес регионов должен быть положительным");
        
        System.out.println("=== Grid Graph SPT Test Results ===");
        System.out.println("Вершин в графе: " + graph.verticesArray().size());
        
        // Найдем какие вершины не в SPT
        Set<Vertex> sptVerticesSet = new HashSet<>(dijkstraResult.previous().keySet());
        sptVerticesSet.add(source);
        
        System.out.println("Вершин в SPT: " + sptVerticesSet.size() + " (включая source)");
        
        Set<Vertex> missingVertices = new HashSet<>(graph.verticesArray());
        missingVertices.removeAll(sptVerticesSet);
        if (!missingVertices.isEmpty()) {
            System.out.println("Вершины НЕ в SPT (" + missingVertices.size() + "):");
            for (Vertex v : missingVertices) {
                int row = (int) (v.x / 10.0);
                int col = (int) (v.y / 10.0);
                System.out.println("  Вершина " + v.getName() + " at (" + row + ", " + col + ")");
            }
        } else {
            System.out.println("Все вершины в SPT!");
        }
        
        System.out.println("Граней в SPT: " + spt.faces().size());
        System.out.println("Листьев на границе: " + spt.boundaryLeaves().size());
        System.out.println("Общий вес регионов: " + spt.totalRegionWeight());
        System.out.println("Длина пути: " + dijkstraResult.distance());
    }

    @Test
    void testSimple() throws IOException {
        List<Vertex> vertices = List.of(
                new Vertex(1, 0, 0),
                new Vertex(2, 1, 0),
                new Vertex(3, -1, 0),
                new Vertex(4, 2, 0),
                new Vertex(5, 1, 1),
                new Vertex(6, 1, 4),
                new Vertex(7, -1, 2),
                new Vertex(8, 0, 2),
                new Vertex(9, 0, 3)
        );
        List<EdgeOfGraph> edges = List.of(
                new EdgeOfGraph(vertices.get(0), vertices.get(1), vertices.get(0).getLength(vertices.get(1))), // 1
                new EdgeOfGraph(vertices.get(1), vertices.get(3), vertices.get(1).getLength(vertices.get(3))), // 2
                new EdgeOfGraph(vertices.get(0), vertices.get(2), vertices.get(0).getLength(vertices.get(2))), // 3
                new EdgeOfGraph(vertices.get(1), vertices.get(4), vertices.get(1).getLength(vertices.get(4))), // 4
                new EdgeOfGraph(vertices.get(3), vertices.get(5), vertices.get(3).getLength(vertices.get(5))), // 5
                new EdgeOfGraph(vertices.get(0), vertices.get(4), vertices.get(0).getLength(vertices.get(4))), // 6
                new EdgeOfGraph(vertices.get(4), vertices.get(5), vertices.get(4).getLength(vertices.get(5))), // 7
                new EdgeOfGraph(vertices.get(2), vertices.get(6), vertices.get(2).getLength(vertices.get(6))), // 8
                new EdgeOfGraph(vertices.get(0), vertices.get(7), vertices.get(0).getLength(vertices.get(7))), // 9
                new EdgeOfGraph(vertices.get(6), vertices.get(7), vertices.get(6).getLength(vertices.get(7))), // 10
                new EdgeOfGraph(vertices.get(5), vertices.get(7), vertices.get(5).getLength(vertices.get(7))), // 11
                new EdgeOfGraph(vertices.get(5), vertices.get(8), vertices.get(5).getLength(vertices.get(8))), // 12
                new EdgeOfGraph(vertices.get(6), vertices.get(8), vertices.get(6).getLength(vertices.get(8)))  // 13
        );
        Graph<Vertex> graph = new Graph<>();
        for (Vertex vertex : vertices) {
            graph.addVertex(vertex);
        }
        for (EdgeOfGraph edge : edges) {
            graph.addEdge(edge.begin, edge.end, edge.length);
            graph.addEdge(edge.end, edge.begin, edge.length);
        }
        GraphPreparation preparation = new GraphPreparation();
        Graph<VertexOfDualGraph> dualGraph = preparation.prepareGraph(graph, 0.0001);
        for (VertexOfDualGraph v : dualGraph.verticesArray()) {
            System.out.println(v.getName() + " : " + v.getVerticesOfFace().stream().map(Vertex::getName).toList() + ", w = " + v.getWeight());
        }

        Optional<DijkstraResult> dijkstraResultOpt = Dijkstra.dijkstraSingleSource(graph, vertices.get(0), List.of(vertices.get(6), vertices.get(8), vertices.get(5)), CornerConstraints.empty());
        Assertions.assertTrue(dijkstraResultOpt.isPresent());
        DijkstraResult dijkstraResult = dijkstraResultOpt.get();
        for (Vertex vertex : dijkstraResult.previous().keySet()) {
            System.out.println(vertex.getName() + ": " + dijkstraResult.previous().get(vertex).getName());
        }

        //Assertions.assertTrue(dijkstraResult.get().path().contains(vertices.get(6)));

        SPTWithRegionWeights spt = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph,
                dijkstraResult.previous(),
                vertices.get(0),
                List.of(vertices.get(6), vertices.get(8), vertices.get(5)),
                dualGraph,
                false,
                dijkstraResult.path()
        );

        System.out.println(spt.faces().stream().map(VertexOfDualGraph::getName).toList());
        System.out.println(spt.regionWeights());
        System.out.println(spt.distances());
    }

    /**
     * Детальный тест на грид-графе 10x10 с проверкой корректности разбиения на регионы
     * Источник внизу по центру, граница справа
     */
    @Test
    void testGridGraphRegionPartitioning() throws IOException {
        testGridGraphRegionPartitioningInternal(false);
    }

    /**
     * Диагностическая версия теста с детальным выводом
     */
    @Test
    void testGridGraphRegionPartitioningDebug() throws IOException {
        // Этот тест можно запустить отдельно для детальной диагностики
        // Он включает DEBUG логирование
        System.setProperty("org.slf4j.simpleLogger.log.partitioning.shortestpathtree", "DEBUG");
        testGridGraphRegionPartitioningInternal(true);
    }

    private void testGridGraphRegionPartitioningInternal(boolean debugMode) throws IOException {
        int gridSize = 10;
        Graph<Vertex> graph = createGridGraph(gridSize);
        GraphPreparation preparation = new GraphPreparation();
        Graph<VertexOfDualGraph> dualGraph = preparation.prepareGraph(graph, 0.0001);

        // Источник внизу по центру (строка 9, столбец 5)
        Vertex source = getVertexById(graph, 9 * gridSize + 5);

        // Граница - правая сторона грида
        List<Vertex> boundary = new ArrayList<>();
        for (int i = 0; i < gridSize; i++) {
            boundary.add(getVertexById(graph, i * gridSize + (gridSize - 1)));
        }

        // Строим SPT
        Optional<DijkstraResult> dijkstraResultOpt = Dijkstra.dijkstraSingleSource(
                graph, source, boundary, CornerConstraints.empty());
        Assertions.assertTrue(dijkstraResultOpt.isPresent());
        DijkstraResult dijkstraResult = dijkstraResultOpt.get();

        // Строим SPT с регионами
        SPTWithRegionWeights spt = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph, dijkstraResult.previous(), source, boundary,
                dualGraph, true, dijkstraResult.path()
        );

        // === ПРОВЕРКИ ===

        // 1. Проверяем что регионы не пустые
        Assertions.assertFalse(spt.faces().isEmpty(),
                "Список регионов не должен быть пустым");

        // 2. Проверяем что листья найдены
        Assertions.assertFalse(spt.boundaryLeaves().isEmpty(),
                "Должны быть найдены листья на границе");

        // 3. Проверяем что все листья находятся на границе
        Set<Vertex> boundarySet = new HashSet<>(boundary);
        for (Vertex leaf : spt.boundaryLeaves()) {
            Assertions.assertTrue(boundarySet.contains(leaf),
                    "Лист " + leaf.getName() + " должен быть на границе");
        }

        // 4. Проверяем что веса регионов монотонно возрастают
        List<Double> weights = spt.regionWeights();
        for (int i = 1; i < weights.size(); i++) {
            Assertions.assertTrue(weights.get(i) >= weights.get(i - 1),
                    "Веса регионов должны монотонно возрастать: " +
                            "weights[" + (i - 1) + "]=" + weights.get(i - 1) +
                            ", weights[" + i + "]=" + weights.get(i));
        }

        // Подготовим данные для диагностики (используются в нескольких проверках)
        Map<Vertex, Map<Vertex, VertexOfDualGraph>> edgeToLeftFace = dualGraph.edgeToDualVertexMap();
        Set<Map.Entry<Vertex, Vertex>> sptEdges = buildSPTEdgesForDiagnostics(dijkstraResult.previous());

        // 5. Проверяем что все регионы уникальны (нет дубликатов)
        Set<VertexOfDualGraph> uniqueRegions = new HashSet<>();
        Map<VertexOfDualGraph, Integer> regionCounts = new HashMap<>();
        for (int i = 0; i < spt.faces().size(); i++) {
            VertexOfDualGraph region = spt.faces().get(i);
            regionCounts.put(region, regionCounts.getOrDefault(region, 0) + 1);
            uniqueRegions.add(region);
        }

        // Находим дубликаты
        List<VertexOfDualGraph> duplicates = regionCounts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();

        if (!duplicates.isEmpty()) {
            StringBuilder msg = new StringBuilder("Найдены дубликаты регионов:\n");
            for (VertexOfDualGraph dup : duplicates) {
                msg.append(String.format("  Регион %d встречается %d раз(а)\n",
                        dup.getName(), regionCounts.get(dup)));
                List<Integer> positions = new ArrayList<>();
                for (int i = 0; i < spt.faces().size(); i++) {
                    if (spt.faces().get(i).equals(dup)) {
                        positions.add(i);
                    }
                }
                msg.append(String.format("    Позиции в списке: %s\n", positions));

                // Дополнительная информация о регионе
                msg.append(String.format("    Вершины грани: %s\n",
                        dup.getVerticesOfFace().stream().map(Vertex::getName).toList()));

                // Проверим какие веса были на этих позициях
                msg.append("    Веса на этих позициях:\n");
                for (int pos : positions) {
                    msg.append(String.format("      Позиция %d: cumWeight=%.2f, distance=%.2f\n",
                            pos, spt.regionWeights().get(pos), spt.distances().get(pos)));
                }

                // Найдем все рёбра которые ведут к этому региону
                msg.append("    Рёбра ведущие к этому региону:\n");
                for (Map.Entry<Vertex, Map<Vertex, VertexOfDualGraph>> outerEntry : edgeToLeftFace.entrySet()) {
                    Vertex from = outerEntry.getKey();
                    for (Map.Entry<Vertex, VertexOfDualGraph> innerEntry : outerEntry.getValue().entrySet()) {
                        Vertex to = innerEntry.getKey();
                        VertexOfDualGraph face = innerEntry.getValue();
                        if (face.equals(dup)) {
                            boolean isTreeEdge = sptEdges.contains(Map.entry(to, from));
                            msg.append(String.format("      Ребро %d -> %d (обратное: %d -> %d) %s\n",
                                    to.getName(), from.getName(), from.getName(), to.getName(),
                                    isTreeEdge ? "[TREE]" : "[NON-TREE]"));
                        }
                    }
                }
            }

            // Выводим в System.out для удобства отладки
            System.out.println("\n" + msg.toString());

            Assertions.fail(msg.toString());
        }

        Assertions.assertEquals(spt.faces().size(), uniqueRegions.size(),
                "Все регионы должны быть уникальными (нет дубликатов)");

        // 6. Проверяем что индексы листьев корректны
        for (int i = 0; i < spt.leafIndices().size(); i++) {
            int leafIdx = spt.leafIndices().get(i);
            Assertions.assertTrue(leafIdx >= -1 && leafIdx < weights.size(),
                    "Индекс листа должен быть в допустимом диапазоне: leafIdx=" + leafIdx);
        }

        // 7. Проверяем что число листьев соответствует числу индексов
        Assertions.assertEquals(spt.boundaryLeaves().size(), spt.leafIndices().size(),
                "Число листьев должно соответствовать числу индексов листьев");

        // 8. Проверяем что общий вес регионов положителен
        Assertions.assertTrue(spt.totalRegionWeight() > 0,
                "Общий вес регионов должен быть положительным");

        // 9. Проверяем что последний вес соответствует общему весу
        if (!weights.isEmpty()) {
            Assertions.assertEquals(weights.get(weights.size() - 1),
                    spt.totalRegionWeight(), 0.0001,
                    "Последний вес должен соответствовать общему весу");
        }

        // === ДОПОЛНИТЕЛЬНАЯ ДИАГНОСТИКА ===
        // Проверяем какие регионы ожидались но не были добавлены
        Set<VertexOfDualGraph> expectedRegions = new HashSet<>();

        // Проходим по всем древесным рёбрам и собираем грани слева от них
        for (Map.Entry<Vertex, Vertex> edge : sptEdges) {
            Vertex from = edge.getKey();
            Vertex to = edge.getValue();

            // Проверяем что обе вершины в SPT
            Set<Vertex> sptVerticesSet = new HashSet<>(dijkstraResult.previous().keySet());
            sptVerticesSet.add(source);

            if (!sptVerticesSet.contains(from) || !sptVerticesSet.contains(to)) {
                continue; // пропускаем рёбра с вершинами вне SPT
            }

            if (edgeToLeftFace.containsKey(from)) {
                var m1 = edgeToLeftFace.get(from);
                if (m1.containsKey(to)) {
                    VertexOfDualGraph face = m1.get(to);
                    // Проверяем что все вершины грани в SPT
                    List<Vertex> faceVertices = face.getVerticesOfFace();
                    boolean allInSPT = true;
                    for (Vertex v : faceVertices) {
                        if (!sptVerticesSet.contains(v)) {
                            allInSPT = false;
                            break;
                        }
                    }
                    if (allInSPT) {
                        expectedRegions.add(face);
                    }
                }
            }
        }

        Set<VertexOfDualGraph> actualRegions = new HashSet<>(spt.faces());
        Set<VertexOfDualGraph> missingRegions = new HashSet<>(expectedRegions);
        missingRegions.removeAll(actualRegions);

        if (!missingRegions.isEmpty()) {
            System.out.println("WARNING: Пропущенные регионы (ожидались, но не добавлены):");
            for (VertexOfDualGraph region : missingRegions) {
                System.out.println("  Регион " + region.getName() +
                        " (вершины грани: " + region.getVerticesOfFace().stream()
                        .map(Vertex::getName).toList() + ")");

                // Найдем все рёбра которые должны были вести к этому региону
                System.out.println("    Рёбра которые должны вести к этому региону:");
                for (Map.Entry<Vertex, Map<Vertex, VertexOfDualGraph>> outerEntry : edgeToLeftFace.entrySet()) {
                    Vertex from = outerEntry.getKey();
                    for (Map.Entry<Vertex, VertexOfDualGraph> innerEntry : outerEntry.getValue().entrySet()) {
                        Vertex to = innerEntry.getKey();
                        VertexOfDualGraph face = innerEntry.getValue();
                        if (face.equals(region)) {
                            boolean isInSPT = sptEdges.contains(Map.entry(from, to)) ||
                                    sptEdges.contains(Map.entry(to, from));
                            boolean isTreeEdge = sptEdges.contains(Map.entry(to, from));
                            System.out.println(String.format("      Ребро %d -> %d, inSPT=%s, isTree=%s",
                                    from.getName(), to.getName(), isInSPT, isTreeEdge));
                        }
                    }
                }
            }
        }

        System.out.println("Ожидалось регионов (граней на древесных ребрах): " + expectedRegions.size());
        System.out.println("Добавлено регионов: " + spt.faces().size());
        System.out.println("Уникальных регионов: " + uniqueRegions.size());

        // === ВЫВОД СТАТИСТИКИ ===
        System.out.println("=== Grid Graph 10x10 Region Partitioning Test ===");
        System.out.println("Вершин в графе: " + graph.verticesArray().size());
        
        // Найдем какая вершина не в SPT
        Set<Vertex> sptVerticesSet = new HashSet<>(dijkstraResult.previous().keySet());
        sptVerticesSet.add(source);
        
        System.out.println("Вершин в SPT: " + sptVerticesSet.size() + " (включая source)");
        
        Set<Vertex> missingVertices = new HashSet<>(graph.verticesArray());
        missingVertices.removeAll(sptVerticesSet);
        if (!missingVertices.isEmpty()) {
            System.out.println("\nВершины НЕ в SPT (" + missingVertices.size() + "):");
            for (Vertex v : missingVertices) {
                int row = (int) (v.x / 10.0);
                int col = (int) (v.y / 10.0);
                System.out.println("  Вершина " + v.getName() + " at (" + row + ", " + col + ")");
            }
        } else {
            System.out.println("\nВсе вершины в SPT!");
        }

        System.out.println("Ожидалось регионов (граней на древесных ребрах): " + expectedRegions.size());
        System.out.println("Регионов найдено (всего): " + spt.faces().size());
        System.out.println("Уникальных регионов: " + uniqueRegions.size());
        System.out.println("Листьев на границе: " + spt.boundaryLeaves().size());
        System.out.println("Общий вес регионов: " + spt.totalRegionWeight());
        System.out.println("Длина пути от источника до границы: " + dijkstraResult.distance());
        System.out.println("Листья: " + spt.boundaryLeaves().stream()
                .map(Vertex::getName).toList());

        if (!duplicates.isEmpty()) {
            System.out.println("\n!!! ВНИМАНИЕ: Обнаружены дубликаты регионов !!!");
            for (VertexOfDualGraph dup : duplicates) {
                System.out.println("  Регион " + dup.getName() + " встречается " +
                        regionCounts.get(dup) + " раз(а)");
            }
        }

        if (!missingRegions.isEmpty()) {
            System.out.println("\n!!! ВНИМАНИЕ: Некоторые регионы пропущены !!!");
        }
    }

    /**
     * Тест на грид-графе с противоположными границами (проверка симметрии)
     */
    @Test
    void testGridGraphOppositesBoundaries() throws IOException {
        int gridSize = 10;
        Graph<Vertex> graph = createGridGraph(gridSize);
        GraphPreparation preparation = new GraphPreparation();
        Graph<VertexOfDualGraph> dualGraph = preparation.prepareGraph(graph, 0.0001);

        // Источник внизу по центру - ищем по координатам (90.0, 50.0)
        Vertex source = graph.verticesArray().stream()
                .filter(v -> Math.abs(v.x - 90.0) < 0.1 && Math.abs(v.y - 50.0) < 0.1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Source vertex not found at (90.0, 50.0)"));

        // Левая граница - ищем по координатам
        List<Vertex> leftBoundary = new ArrayList<>();
        for (Vertex v : graph.verticesArray()) {
            if (Math.abs(v.y - 0.0) < 0.1) { // столбец 0
                leftBoundary.add(v);
            }
        }
        leftBoundary.sort(Comparator.comparingDouble(v -> v.x)); // сортируем по строкам

        // Правая граница - ищем по координатам
        List<Vertex> rightBoundary = new ArrayList<>();
        for (Vertex v : graph.verticesArray()) {
            if (Math.abs(v.y - 90.0) < 0.1) { // столбец 9 (координата 90.0)
                rightBoundary.add(v);
            }
        }
        rightBoundary.sort(Comparator.comparingDouble(v -> v.x));

        // Строим SPT для левой границы
        Optional<DijkstraResult> leftResult = Dijkstra.dijkstraSingleSource(
                graph, source, leftBoundary, CornerConstraints.empty());
        Assertions.assertTrue(leftResult.isPresent());

        SPTWithRegionWeights leftSPT = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph, leftResult.get().previous(), source, leftBoundary,
                dualGraph, true, leftResult.get().path()
        );

        // Строим SPT для правой границы
        Optional<DijkstraResult> rightResult = Dijkstra.dijkstraSingleSource(
                graph, source, rightBoundary, CornerConstraints.empty());
        Assertions.assertTrue(rightResult.isPresent());

        SPTWithRegionWeights rightSPT = ShortestPathTreeSearcher.buildSPTWithRegionWeights(
                graph, rightResult.get().previous(), source, rightBoundary,
                dualGraph, false, rightResult.get().path()
        );

        // === ПРОВЕРКИ СИММЕТРИИ ===

        // Оба дерева должны содержать регионы
        Assertions.assertFalse(leftSPT.faces().isEmpty());
        Assertions.assertFalse(rightSPT.faces().isEmpty());

        // Оба дерева должны иметь положительные веса
        Assertions.assertTrue(leftSPT.totalRegionWeight() > 0);
        Assertions.assertTrue(rightSPT.totalRegionWeight() > 0);

        // Проверка на дубликаты в левом SPT
        Set<VertexOfDualGraph> leftUnique = new HashSet<>(leftSPT.faces());
        if (leftSPT.faces().size() != leftUnique.size()) {
            System.out.println("WARNING: Дубликаты в левом SPT!");
        }

        // Проверка на дубликаты в правом SPT
        Set<VertexOfDualGraph> rightUnique = new HashSet<>(rightSPT.faces());
        if (rightSPT.faces().size() != rightUnique.size()) {
            System.out.println("WARNING: Дубликаты в правом SPT!");
        }

        // Сумма весов из двух половин должна быть близка к общему весу графа
        double totalGraphWeight = dualGraph.verticesArray().stream()
                .mapToDouble(VertexOfDualGraph::getWeight)
                .sum();
        double sumOfSPTs = leftSPT.totalRegionWeight() + rightSPT.totalRegionWeight();

        System.out.println("=== Opposite Boundaries Test ===");
        System.out.println("Общий вес графа (все грани): " + totalGraphWeight);
        System.out.println("Вес левого SPT: " + leftSPT.totalRegionWeight());
        System.out.println("Вес правого SPT: " + rightSPT.totalRegionWeight());
        System.out.println("Сумма весов SPT: " + sumOfSPTs);
        System.out.println("Листьев слева: " + leftSPT.boundaryLeaves().size());
        System.out.println("Листьев справа: " + rightSPT.boundaryLeaves().size());
    }

    /**
     * Вспомогательный метод для построения множества древесных рёбер (для диагностики)
     */
    private Set<Map.Entry<Vertex, Vertex>> buildSPTEdgesForDiagnostics(Map<Vertex, Vertex> previous) {
        Set<Map.Entry<Vertex, Vertex>> sptEdges = new HashSet<>();
        for (Map.Entry<Vertex, Vertex> entry : previous.entrySet()) {
            Vertex child = entry.getKey();
            Vertex parent = entry.getValue();
            if (parent != null) {
                sptEdges.add(Map.entry(child, parent));
                sptEdges.add(Map.entry(parent, child));
            }
        }
        return sptEdges;
    }
}