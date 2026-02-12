import folium
import os
import sys

def load_vertex_list(file_path):
    """Загружает список вершин из файла"""
    vertices = []
    if not os.path.exists(file_path):
        return vertices
    
    with open(file_path, 'r') as file:
        for line in file:
            parts = line.strip().split()
            if len(parts) >= 3:
                vertex_id = int(parts[0])
                # После fromEuclidean: x = longitude, y = latitude
                longitude = float(parts[1])  # x coordinate
                latitude = float(parts[2])   # y coordinate
                vertices.append((vertex_id, latitude, longitude))
    return vertices

def load_neighbor_splits(file_path):
    """Загружает информацию о разделении соседей"""
    splits = []
    if not os.path.exists(file_path):
        return splits
    
    with open(file_path, 'r') as file:
        current_vertex = None
        path_neighbors = []
        left_neighbors = []
        right_neighbors = []
        
        for line in file:
            line = line.strip()
            if not line:
                continue
                
            if line == '---':
                if current_vertex is not None:
                    splits.append({
                        'vertex': current_vertex,
                        'path': path_neighbors,
                        'left': left_neighbors,
                        'right': right_neighbors
                    })
                current_vertex = None
                path_neighbors = []
                left_neighbors = []
                right_neighbors = []
            elif line.startswith('PATH'):
                parts = line.split()
                neighbor_id = int(parts[1])
                longitude = float(parts[2])
                latitude = float(parts[3])
                path_neighbors.append((neighbor_id, latitude, longitude))
            elif line.startswith('LEFT'):
                parts = line.split()
                neighbor_id = int(parts[1])
                longitude = float(parts[2])
                latitude = float(parts[3])
                left_neighbors.append((neighbor_id, latitude, longitude))
            elif line.startswith('RIGHT'):
                parts = line.split()
                neighbor_id = int(parts[1])
                longitude = float(parts[2])
                latitude = float(parts[3])
                right_neighbors.append((neighbor_id, latitude, longitude))
            else:
                parts = line.split()
                if len(parts) >= 3:
                    vertex_id = int(parts[0])
                    longitude = float(parts[1])
                    latitude = float(parts[2])
                    current_vertex = (vertex_id, latitude, longitude)
        
        # Добавляем последнюю вершину если не было '---' в конце
        if current_vertex is not None:
            splits.append({
                'vertex': current_vertex,
                'path': path_neighbors,
                'left': left_neighbors,
                'right': right_neighbors
            })
    
    return splits

def load_primal_graph(file_path):
    """Загружает исходный (primal) граф"""
    vertices = {}
    edges = []
    
    if not os.path.exists(file_path):
        print(f"Warning: Primal graph file {file_path} not found")
        return vertices, edges
    
    with open(file_path, 'r') as file:
        mode = None
        for line in file:
            line = line.strip()
            if not line:
                continue
            
            if line == 'VERTICES':
                mode = 'VERTICES'
                continue
            elif line == 'EDGES':
                mode = 'EDGES'
                continue
            
            if mode == 'VERTICES':
                parts = line.split()
                if len(parts) >= 3:
                    vertex_id = int(parts[0])
                    longitude = float(parts[1])
                    latitude = float(parts[2])
                    vertices[vertex_id] = (latitude, longitude)
            
            elif mode == 'EDGES':
                parts = line.split()
                if len(parts) >= 3:
                    from_id = int(parts[0])
                    to_id = int(parts[1])
                    length = float(parts[2])
                    edges.append({
                        'from': from_id,
                        'to': to_id,
                        'length': length
                    })
    
    print(f"Loaded primal graph: {len(vertices)} vertices, {len(edges)} edges")
    return vertices, edges

def load_dual_graph_with_flow(file_path):
    """Загружает двойственный граф с информацией о потоках"""
    vertices = {}
    edges = []
    
    if not os.path.exists(file_path):
        print(f"Warning: Dual graph file {file_path} not found")
        return vertices, edges
    
    with open(file_path, 'r') as file:
        mode = None
        for line in file:
            line = line.strip()
            if not line:
                continue
            
            if line == 'VERTICES':
                mode = 'VERTICES'
                continue
            elif line == 'EDGES':
                mode = 'EDGES'
                continue
            
            if mode == 'VERTICES':
                parts = line.split()
                if len(parts) >= 4:
                    vertex_id = int(parts[0])
                    longitude = float(parts[1])
                    latitude = float(parts[2])
                    vertex_type = parts[3]
                    vertices[vertex_id] = (latitude, longitude, vertex_type)
            
            elif mode == 'EDGES':
                parts = line.split()
                if len(parts) >= 5:
                    from_id = int(parts[0])
                    to_id = int(parts[1])
                    bandwidth = float(parts[2])
                    flow = float(parts[3])
                    edge_type = parts[4]
                    edges.append({
                        'from': from_id,
                        'to': to_id,
                        'bandwidth': bandwidth,
                        'flow': flow,
                        'type': edge_type
                    })
    
    print(f"Loaded dual graph: {len(vertices)} vertices, {len(edges)} edges")
    if edges:
        saturated_count = sum(1 for e in edges if e['type'] == 'SATURATED')
        flow_count = sum(1 for e in edges if e['flow'] > 0)
        print(f"  - Saturated edges: {saturated_count}")
        print(f"  - Edges with flow: {flow_count}")
        print(f"  - Edges without flow: {len(edges) - flow_count}")
    
    return vertices, edges


def load_spt(file_path):
    """Загружает Shortest Path Tree (SPT) с информацией о весах регионов"""
    spt_data = {
        'root': None,
        'total_region_weight': 0.0,
        'boundary_leaves': [],  # list of (id, lat, lon, cumulative_weight)
        'tree_edges': [],       # list of (from_id, from_lat, from_lon, to_id, to_lat, to_lon, distance)
        'vertices': {}          # id -> (lat, lon, distance)
    }
    
    if not os.path.exists(file_path):
        print(f"Warning: SPT file {file_path} not found")
        return None
    
    with open(file_path, 'r') as file:
        mode = None
        for line in file:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            
            if line == 'ROOT':
                mode = 'ROOT'
                continue
            elif line == 'TOTAL_REGION_WEIGHT':
                mode = 'TOTAL_REGION_WEIGHT'
                continue
            elif line == 'BOUNDARY_LEAVES':
                mode = 'BOUNDARY_LEAVES'
                continue
            elif line == 'TREE_EDGES':
                mode = 'TREE_EDGES'
                continue
            elif line == 'VERTICES':
                mode = 'VERTICES'
                continue
            
            if mode == 'ROOT':
                parts = line.split()
                if len(parts) >= 3:
                    vertex_id = int(parts[0])
                    longitude = float(parts[1])
                    latitude = float(parts[2])
                    spt_data['root'] = (vertex_id, latitude, longitude)
            
            elif mode == 'TOTAL_REGION_WEIGHT':
                spt_data['total_region_weight'] = float(line)
            
            elif mode == 'BOUNDARY_LEAVES':
                parts = line.split()
                if len(parts) >= 4:
                    vertex_id = int(parts[0])
                    longitude = float(parts[1])
                    latitude = float(parts[2])
                    cumulative_weight = float(parts[3])
                    # boundary_index is optional (for backwards compatibility)
                    boundary_index = int(parts[4]) if len(parts) >= 5 else -1
                    spt_data['boundary_leaves'].append((vertex_id, latitude, longitude, cumulative_weight, boundary_index))
            
            elif mode == 'TREE_EDGES':
                parts = line.split()
                if len(parts) >= 7:
                    from_id = int(parts[0])
                    from_lon = float(parts[1])
                    from_lat = float(parts[2])
                    to_id = int(parts[3])
                    to_lon = float(parts[4])
                    to_lat = float(parts[5])
                    distance = float(parts[6])
                    spt_data['tree_edges'].append({
                        'from_id': from_id,
                        'from_lat': from_lat,
                        'from_lon': from_lon,
                        'to_id': to_id,
                        'to_lat': to_lat,
                        'to_lon': to_lon,
                        'distance': distance
                    })
            
            elif mode == 'VERTICES':
                parts = line.split()
                if len(parts) >= 4:
                    vertex_id = int(parts[0])
                    longitude = float(parts[1])
                    latitude = float(parts[2])
                    distance = float(parts[3])
                    spt_data['vertices'][vertex_id] = (latitude, longitude, distance)
    
    print(f"Loaded SPT: root={spt_data['root']}, {len(spt_data['boundary_leaves'])} boundary leaves, "
          f"{len(spt_data['tree_edges'])} tree edges, total_weight={spt_data['total_region_weight']:.2f}")
    
    return spt_data

def visualize_reif_flow(directory_name, output_file):
    """Визуализирует результаты работы MaxFlowReif"""
    
    script_dir = os.path.dirname(os.path.abspath(__file__))
    directory_path = os.path.join(script_dir, '..', 'main', 'output', directory_name)
    
    if not os.path.exists(directory_path):
        print(f"Directory {directory_path} does not exist.")
        sys.exit(1)
    
    # Загружаем все данные
    external_boundary = load_vertex_list(os.path.join(directory_path, "external_boundary.txt"))
    source_boundary = load_vertex_list(os.path.join(directory_path, "source_boundary.txt"))
    sink_boundary = load_vertex_list(os.path.join(directory_path, "sink_boundary.txt"))
    st_path = load_vertex_list(os.path.join(directory_path, "st_path.txt"))
    best_path = load_vertex_list(os.path.join(directory_path, "best_path.txt"))
    neighbor_splits = load_neighbor_splits(os.path.join(directory_path, "neighbor_splits.txt"))
    primal_vertices, primal_edges = load_primal_graph(os.path.join(directory_path, "primal_graph.txt"))
    dual_vertices, dual_edges = load_dual_graph_with_flow(os.path.join(directory_path, "dual_graph_flow.txt"))
    
    # Load SPT data
    spt1 = load_spt(os.path.join(directory_path, "spt1.txt"))
    spt2 = load_spt(os.path.join(directory_path, "spt2.txt"))
    
    # Собираем все точки для определения границ карты
    all_points = []
    for vertex_list in [external_boundary, source_boundary, sink_boundary, st_path, best_path]:
        for _, lat, lon in vertex_list:
            all_points.append((lat, lon))
    
    if not all_points:
        print("No data to visualize")
        sys.exit(1)
    
    # Создаем карту
    center_lat = sum(p[0] for p in all_points) / len(all_points)
    center_lon = sum(p[1] for p in all_points) / len(all_points)
    map_osm = folium.Map(location=(center_lat, center_lon), zoom_start=15)
    
    # Создаем слои (Feature Groups) для каждого типа данных
    primal_graph_layer = folium.FeatureGroup(name='Primal Graph (Original)', show=False)
    dual_graph_layer = folium.FeatureGroup(name='Dual Graph with Flow', show=False)
    dual_graph_no_saturated_layer = folium.FeatureGroup(name='Dual Graph (No Saturated Edges)', show=False)
    external_layer = folium.FeatureGroup(name='External Boundary', show=True)
    source_layer = folium.FeatureGroup(name='Source Boundary', show=True)
    sink_layer = folium.FeatureGroup(name='Sink Boundary', show=True)
    st_path_layer = folium.FeatureGroup(name='S-T Path', show=True)
    neighbor_splits_layer = folium.FeatureGroup(name='Neighbor Splits', show=True)
    best_path_layer = folium.FeatureGroup(name='Best Path (Result)', show=True)
    spt1_layer = folium.FeatureGroup(name='SPT 1 (Left Split)', show=False)
    spt2_layer = folium.FeatureGroup(name='SPT 2 (Right Split)', show=False)
    
    # 0. Исходный (primal) граф
    if primal_vertices and primal_edges:
        print(f"Visualizing primal graph with {len(primal_vertices)} vertices and {len(primal_edges)} edges")
        
        # Рисуем рёбра исходного графа
        for edge in primal_edges:
            from_id = edge['from']
            to_id = edge['to']
            length = edge['length']
            
            if from_id not in primal_vertices or to_id not in primal_vertices:
                continue
            
            from_lat, from_lon = primal_vertices[from_id]
            to_lat, to_lon = primal_vertices[to_id]
            
            tooltip_text = f"Edge {from_id}↔{to_id}<br>Length: {length:.2f}m"
            
            folium.PolyLine(
                locations=[(from_lat, from_lon), (to_lat, to_lon)],
                color='#4A90E2',  # Синий цвет для исходного графа
                weight=1.5,
                opacity=0.4,
                tooltip=tooltip_text
            ).add_to(primal_graph_layer)
        
        # Рисуем вершины исходного графа
        for vertex_id, (lat, lon) in primal_vertices.items():
            folium.CircleMarker(
                location=(lat, lon),
                radius=2,
                color='#2E5C8A',
                fill=True,
                fill_color='#4A90E2',
                fill_opacity=0.6,
                tooltip=f"Vertex {vertex_id}"
            ).add_to(primal_graph_layer)
    
    # 1. Двойственный граф с потоками
    if dual_vertices and dual_edges:
        print(f"Visualizing dual graph with {len(dual_vertices)} vertices and {len(dual_edges)} edges")
        
        # Рисуем рёбра двойственного графа
        edges_drawn = 0
        edges_skipped = 0
        
        for edge in dual_edges:
            from_id = edge['from']
            to_id = edge['to']
            bandwidth = edge['bandwidth']
            flow = edge['flow']
            edge_type = edge['type']
            
            if from_id not in dual_vertices or to_id not in dual_vertices:
                edges_skipped += 1
                print(f"Warning: Edge {from_id}→{to_id} references missing vertex")
                continue
            
            from_lat, from_lon, _ = dual_vertices[from_id]
            to_lat, to_lon, _ = dual_vertices[to_id]
            
            # Определяем цвет и толщину в зависимости от потока
            if edge_type == 'SATURATED':
                color = '#FF0000'  # Красный для насыщенных рёбер
                weight = 6
                opacity = 1.0
                dash_array = None
            elif flow > 0:
                # Рёбра с потоком, но не насыщенные - оранжевые
                color = '#FF8800'
                weight = 4
                opacity = 0.8
                dash_array = None
            else:
                # Рёбра без потока - тёмно-серые пунктирные
                color = '#888888'
                weight = 2
                opacity = 0.6
                dash_array = '5, 5'
            
            tooltip_text = f"Edge {from_id}↔{to_id}<br>Flow: {flow:.2f}<br>Capacity: {bandwidth:.2f}"
            if edge_type == 'SATURATED':
                tooltip_text += "<br><b>SATURATED</b>"
            
            polyline = folium.PolyLine(
                locations=[(from_lat, from_lon), (to_lat, to_lon)],
                color=color,
                weight=weight,
                opacity=opacity,
                tooltip=tooltip_text
            )
            
            if dash_array:
                polyline.options['dashArray'] = dash_array
            
            polyline.add_to(dual_graph_layer)
            edges_drawn += 1
        
        print(f"Drew {edges_drawn} edges, skipped {edges_skipped} edges")
        
        # Рисуем вершины двойственного графа (центроиды граней)
        for vertex_id, (lat, lon, vertex_type) in dual_vertices.items():
            if vertex_type == 'SOURCE':
                color = 'green'
                radius = 8
                tooltip_text = f"Face {vertex_id} (SOURCE)"
            elif vertex_type == 'SINK':
                color = 'red'
                radius = 8
                tooltip_text = f"Face {vertex_id} (SINK)"
            else:
                color = '#666666'
                radius = 4
                tooltip_text = f"Face {vertex_id}"
            
            folium.CircleMarker(
                location=(lat, lon),
                radius=radius,
                color=color,
                fill=True,
                fill_color=color,
                fill_opacity=0.7,
                tooltip=tooltip_text
            ).add_to(dual_graph_layer)
    
    # 1.5. Двойственный граф БЕЗ насыщенных рёбер (только рёбра с flow < bandwidth)
    if dual_vertices and dual_edges:
        print(f"Visualizing dual graph without saturated edges")
        
        # Рисуем только ненасыщенные рёбра
        non_saturated_count = 0
        
        for edge in dual_edges:
            from_id = edge['from']
            to_id = edge['to']
            bandwidth = edge['bandwidth']
            flow = edge['flow']
            edge_type = edge['type']
            
            # Пропускаем насыщенные рёбра
            if edge_type == 'SATURATED':
                continue
            
            if from_id not in dual_vertices or to_id not in dual_vertices:
                continue
            
            from_lat, from_lon, _ = dual_vertices[from_id]
            to_lat, to_lon, _ = dual_vertices[to_id]
            
            # Определяем цвет и толщину
            if flow > 0:
                # Рёбра с потоком, но не насыщенные - оранжевые
                color = '#FF8800'
                weight = 4
                opacity = 0.8
            else:
                # Рёбра без потока - серые
                color = '#888888'
                weight = 2
                opacity = 0.6
            
            tooltip_text = f"Edge {from_id}↔{to_id}<br>Flow: {flow:.2f}<br>Capacity: {bandwidth:.2f}"
            
            folium.PolyLine(
                locations=[(from_lat, from_lon), (to_lat, to_lon)],
                color=color,
                weight=weight,
                opacity=opacity,
                tooltip=tooltip_text
            ).add_to(dual_graph_no_saturated_layer)
            
            non_saturated_count += 1
        
        print(f"Drew {non_saturated_count} non-saturated edges")
        
        # Рисуем вершины для этого слоя тоже
        for vertex_id, (lat, lon, vertex_type) in dual_vertices.items():
            if vertex_type == 'SOURCE':
                color = 'green'
                radius = 8
                tooltip_text = f"Face {vertex_id} (SOURCE)"
            elif vertex_type == 'SINK':
                color = 'red'
                radius = 8
                tooltip_text = f"Face {vertex_id} (SINK)"
            else:
                color = '#666666'
                radius = 4
                tooltip_text = f"Face {vertex_id}"
            
            folium.CircleMarker(
                location=(lat, lon),
                radius=radius,
                color=color,
                fill=True,
                fill_color=color,
                fill_opacity=0.7,
                tooltip=tooltip_text
            ).add_to(dual_graph_no_saturated_layer)
    
    # 2. Внешняя граница графа (серый полигон)
    if external_boundary:
        boundary_coords = [(lat, lon) for _, lat, lon in external_boundary]
        folium.Polygon(
            locations=boundary_coords,
            color='gray',
            fill=True,
            fill_color='gray',
            fill_opacity=0.1,
            weight=2,
            tooltip="External Boundary"
        ).add_to(external_layer)
        
        # Добавляем точки границы
        for vertex_id, lat, lon in external_boundary:
            folium.CircleMarker(
                location=(lat, lon),
                radius=3,
                color='gray',
                fill=True,
                fill_color='gray',
                tooltip=f"Boundary vertex {vertex_id}"
            ).add_to(external_layer)
    
    # 3. Граница source (зеленый)
    if source_boundary:
        source_coords = [(lat, lon) for _, lat, lon in source_boundary]
        folium.Polygon(
            locations=source_coords,
            color='green',
            fill=True,
            fill_color='green',
            fill_opacity=0.3,
            weight=3,
            tooltip="Source Boundary"
        ).add_to(source_layer)
        
        for vertex_id, lat, lon in source_boundary:
            folium.CircleMarker(
                location=(lat, lon),
                radius=4,
                color='darkgreen',
                fill=True,
                fill_color='green',
                tooltip=f"Source boundary vertex {vertex_id}"
            ).add_to(source_layer)
    
    # 4. Граница sink (красный)
    if sink_boundary:
        sink_coords = [(lat, lon) for _, lat, lon in sink_boundary]
        folium.Polygon(
            locations=sink_coords,
            color='red',
            fill=True,
            fill_color='red',
            fill_opacity=0.3,
            weight=3,
            tooltip="Sink Boundary"
        ).add_to(sink_layer)
        
        for vertex_id, lat, lon in sink_boundary:
            folium.CircleMarker(
                location=(lat, lon),
                radius=4,
                color='darkred',
                fill=True,
                fill_color='red',
                tooltip=f"Sink boundary vertex {vertex_id}"
            ).add_to(sink_layer)
    
    # 5. Путь s-t между границами (синий)
    if st_path:
        st_coords = [(lat, lon) for _, lat, lon in st_path]
        folium.PolyLine(
            locations=st_coords,
            color='blue',
            weight=4,
            opacity=0.7,
            tooltip="S-T Path"
        ).add_to(st_path_layer)
        
        for i, (vertex_id, lat, lon) in enumerate(st_path):
            folium.CircleMarker(
                location=(lat, lon),
                radius=5,
                color='blue',
                fill=True,
                fill_color='lightblue',
                fill_opacity=0.8,
                weight=2,
                tooltip=f"S-T path vertex {vertex_id}",
                popup=folium.Popup(f"<b>Vertex ID: {vertex_id}</b>", max_width=200)
            ).add_to(st_path_layer)
    
    # 6. Разделение соседей (стрелки от вершин к соседям)
    print(f"Neighbor splits loaded: {len(neighbor_splits) if neighbor_splits else 0}")
    if neighbor_splits:
        print(f"First split: {neighbor_splits[0] if neighbor_splits else 'None'}")
        # Проверяем, состоит ли путь из одной вершины
        single_vertex_path = (len(st_path) == 1) if st_path else False
        print(f"Single vertex path: {single_vertex_path}, st_path length: {len(st_path) if st_path else 0}")
        
        items_added = 0
        for split in neighbor_splits:
            vertex_id, vertex_lat, vertex_lon = split['vertex']
            vertex_coords = (vertex_lat, vertex_lon)
            print(f"Processing split for vertex {vertex_id} at ({vertex_lat}, {vertex_lon})")
            print(f"  - Path neighbors: {len(split['path'])}")
            print(f"  - Left neighbors: {len(split['left'])}")
            print(f"  - Right neighbors: {len(split['right'])}")
            
            # Для одновершинного пути создаем визуальное разделение
            if single_vertex_path:
                # Смещение в метрах (примерно 10 метров в градусах)
                offset = 0.0001
                
                # Левая копия вершины (для left neighbors)
                left_copy_coords = (vertex_lat - offset, vertex_lon - offset)
                # Правая копия вершины (для right neighbors)
                right_copy_coords = (vertex_lat + offset, vertex_lon + offset)
                
                # Рисуем две копии вершины
                folium.CircleMarker(
                    location=left_copy_coords,
                    radius=8,
                    color='purple',
                    fill=True,
                    fill_color='purple',
                    fill_opacity=0.7,
                    weight=3,
                    tooltip=f"Split 1 (left): Vertex {vertex_id}"
                ).add_to(neighbor_splits_layer)
                
                folium.CircleMarker(
                    location=right_copy_coords,
                    radius=8,
                    color='cyan',
                    fill=True,
                    fill_color='cyan',
                    fill_opacity=0.7,
                    weight=3,
                    tooltip=f"Split 2 (right): Vertex {vertex_id}"
                ).add_to(neighbor_splits_layer)
                
                # Линия между копиями
                folium.PolyLine(
                    locations=[left_copy_coords, right_copy_coords],
                    color='gray',
                    weight=2,
                    opacity=0.5,
                    dash_array='5, 5'
                ).add_to(neighbor_splits_layer)
            else:
                left_copy_coords = vertex_coords
                right_copy_coords = vertex_coords
            
            # Соседи на пути - желтые толстые стрелки (подключены к обеим частям)
            for neighbor_id, neighbor_lat, neighbor_lon in split['path']:
                # Для одновершинного пути рисуем от обеих копий
                if single_vertex_path:
                    folium.PolyLine(
                        locations=[left_copy_coords, (neighbor_lat, neighbor_lon)],
                        color='gold',
                        weight=2,
                        opacity=0.7,
                        tooltip=f"Path neighbor: {vertex_id} → {neighbor_id}"
                    ).add_to(neighbor_splits_layer)
                    folium.PolyLine(
                        locations=[right_copy_coords, (neighbor_lat, neighbor_lon)],
                        color='gold',
                        weight=2,
                        opacity=0.7,
                        tooltip=f"Path neighbor: {vertex_id} → {neighbor_id}"
                    ).add_to(neighbor_splits_layer)
                else:
                    folium.PolyLine(
                        locations=[vertex_coords, (neighbor_lat, neighbor_lon)],
                        color='gold',
                        weight=3,
                        opacity=0.9,
                        tooltip=f"Path neighbor (both splits): {vertex_id} → {neighbor_id}"
                    ).add_to(neighbor_splits_layer)
                
                # Добавляем стрелку в конце (маркер)
                folium.CircleMarker(
                    location=(neighbor_lat, neighbor_lon),
                    radius=3,
                    color='gold',
                    fill=True,
                    fill_color='yellow',
                    fill_opacity=0.9
                ).add_to(neighbor_splits_layer)
            
            # Левые соседи - фиолетовые стрелки (от левой копии)
            for neighbor_id, neighbor_lat, neighbor_lon in split['left']:
                folium.PolyLine(
                    locations=[left_copy_coords, (neighbor_lat, neighbor_lon)],
                    color='purple',
                    weight=3 if single_vertex_path else 2,
                    opacity=0.8 if single_vertex_path else 0.6,
                    tooltip=f"Left neighbor (split 1): {vertex_id} → {neighbor_id}"
                ).add_to(neighbor_splits_layer)
                
                # Добавляем стрелку в конце (маркер)
                folium.CircleMarker(
                    location=(neighbor_lat, neighbor_lon),
                    radius=3 if single_vertex_path else 2,
                    color='purple',
                    fill=True,
                    fill_color='purple',
                    fill_opacity=0.9 if single_vertex_path else 0.8
                ).add_to(neighbor_splits_layer)
            
            # Правые соседи - бирюзовые/голубые стрелки (от правой копии)
            for neighbor_id, neighbor_lat, neighbor_lon in split['right']:
                folium.PolyLine(
                    locations=[right_copy_coords, (neighbor_lat, neighbor_lon)],
                    color='cyan',
                    weight=3 if single_vertex_path else 2,
                    opacity=0.8 if single_vertex_path else 0.6,
                    tooltip=f"Right neighbor (split 2): {vertex_id} → {neighbor_id}"
                ).add_to(neighbor_splits_layer)
                
                # Добавляем стрелку в конце (маркер)
                folium.CircleMarker(
                    location=(neighbor_lat, neighbor_lon),
                    radius=3 if single_vertex_path else 2,
                    color='cyan',
                    fill=True,
                    fill_color='cyan',
                    fill_opacity=0.9 if single_vertex_path else 0.8
                ).add_to(neighbor_splits_layer)
                items_added += 1
        
        print(f"Added {items_added} items to neighbor_splits_layer")
    
    # 7. Shortest Path Trees (SPT) with region weights
    def visualize_spt(spt_data, layer, color_scheme, spt_name):
        """Визуализирует SPT с весами регионов"""
        if spt_data is None:
            return
        
        # Colors for the scheme
        edge_color = color_scheme['edge']
        root_color = color_scheme['root']
        leaf_color = color_scheme['leaf']
        vertex_color = color_scheme['vertex']
        
        # Draw tree edges
        for edge in spt_data['tree_edges']:
            folium.PolyLine(
                locations=[
                    (edge['from_lat'], edge['from_lon']),
                    (edge['to_lat'], edge['to_lon'])
                ],
                color=edge_color,
                weight=2,
                opacity=0.7,
                tooltip=f"Tree edge: {edge['from_id']} → {edge['to_id']}<br>Distance: {edge['distance']:.2f}"
            ).add_to(layer)
        
        # Draw root vertex
        if spt_data['root']:
            root_id, root_lat, root_lon = spt_data['root']
            folium.CircleMarker(
                location=(root_lat, root_lon),
                radius=10,
                color=root_color,
                fill=True,
                fill_color=root_color,
                fill_opacity=0.9,
                weight=3,
                tooltip=f"ROOT ({spt_name}): Vertex {root_id}<br>Total region weight: {spt_data['total_region_weight']:.2f}"
            ).add_to(layer)
        
        # Draw boundary leaves with region weights
        total_weight = spt_data['total_region_weight']
        num_leaves = len(spt_data['boundary_leaves'])
        
        for i, leaf_data in enumerate(spt_data['boundary_leaves']):
            # Handle both old (4 fields) and new (5 fields) format
            if len(leaf_data) >= 5:
                leaf_id, leaf_lat, leaf_lon, cumulative_weight, boundary_index = leaf_data
            else:
                leaf_id, leaf_lat, leaf_lon, cumulative_weight = leaf_data
                boundary_index = i
            
            # Calculate percentage for color intensity
            weight_percentage = (cumulative_weight / total_weight * 100) if total_weight > 0 else 0
            right_weight = total_weight - cumulative_weight
            
            # Also show position-based percentage (index / total)
            position_percentage = ((i + 1) / num_leaves * 100) if num_leaves > 0 else 0
            
            # Create tooltip with detailed info
            tooltip_text = (
                f"<b>Boundary Leaf L{i}</b>: Vertex {leaf_id}<br>"
                f"Position: {i+1}/{num_leaves} ({position_percentage:.1f}%)<br>"
                f"Boundary index: {boundary_index}<br>"
                f"Left weight (R0..R{i}): {cumulative_weight:.2f} ({weight_percentage:.1f}%)<br>"
                f"Right weight: {right_weight:.2f} ({100-weight_percentage:.1f}%)<br>"
                f"Total: {total_weight:.2f}"
            )
            
            # Draw leaf marker
            folium.CircleMarker(
                location=(leaf_lat, leaf_lon),
                radius=7,
                color=leaf_color,
                fill=True,
                fill_color=leaf_color,
                fill_opacity=0.8,
                weight=2,
                tooltip=tooltip_text
            ).add_to(layer)
            
            # Add label showing both position and weight percentage
            # Use position-based label to show ordering is correct
            label_text = f"L{i}"
            if total_weight > 0 and cumulative_weight >= 0:
                label_text = f"{weight_percentage:.0f}%"
            
            folium.Marker(
                location=(leaf_lat, leaf_lon),
                icon=folium.DivIcon(
                    html=f'<div style="font-size: 9px; color: {leaf_color}; font-weight: bold; '
                         f'text-shadow: 1px 1px white, -1px -1px white, 1px -1px white, -1px 1px white;">'
                         f'{label_text}</div>',
                    icon_size=(30, 15),
                    icon_anchor=(15, 0)
                )
            ).add_to(layer)
        
        # Draw intermediate vertices (smaller, less prominent)
        for vertex_id, (lat, lon, distance) in spt_data['vertices'].items():
            # Skip root and boundary leaves (already drawn)
            is_root = spt_data['root'] and vertex_id == spt_data['root'][0]
            is_leaf = any(leaf[0] == vertex_id for leaf in spt_data['boundary_leaves'])
            
            if is_root or is_leaf:
                continue
            
            folium.CircleMarker(
                location=(lat, lon),
                radius=3,
                color=vertex_color,
                fill=True,
                fill_color=vertex_color,
                fill_opacity=0.5,
                weight=1,
                tooltip=f"SPT vertex {vertex_id}<br>Distance from root: {distance:.2f}"
            ).add_to(layer)
    
    # Visualize SPT 1 (purple/magenta scheme)
    if spt1:
        visualize_spt(spt1, spt1_layer, {
            'edge': '#9932CC',      # Dark orchid
            'root': '#8B008B',      # Dark magenta
            'leaf': '#DA70D6',      # Orchid
            'vertex': '#DDA0DD'     # Plum
        }, 'SPT1')
    
    # Visualize SPT 2 (teal/cyan scheme)
    if spt2:
        visualize_spt(spt2, spt2_layer, {
            'edge': '#008B8B',      # Dark cyan
            'root': '#006666',      # Darker teal
            'leaf': '#20B2AA',      # Light sea green
            'vertex': '#66CDAA'     # Medium aquamarine
        }, 'SPT2')
    
    # 8. Лучший найденный путь (оранжевый/желтый - самый важный, рисуем последним)
    if best_path:
        # Убираем последовательные дубликаты и проверяем на замыкание цикла
        cleaned_best_path = []
        for vertex_id, lat, lon in best_path:
            # Добавляем вершину только если она отличается от предыдущей
            if not cleaned_best_path or cleaned_best_path[-1] != (vertex_id, lat, lon):
                cleaned_best_path.append((vertex_id, lat, lon))
        
        # Проверяем, не замыкается ли путь (первая == последняя)
        if len(cleaned_best_path) > 1 and cleaned_best_path[0][0] == cleaned_best_path[-1][0]:
            # Удаляем последнюю вершину если она совпадает с первой
            cleaned_best_path = cleaned_best_path[:-1]
            print(f"Warning: Best path was a cycle, removed last vertex")
        
        best_coords = [(lat, lon) for _, lat, lon in cleaned_best_path]
        folium.PolyLine(
            locations=best_coords,
            color='orange',
            weight=6,
            opacity=0.9,
            tooltip="Best Path (Final Result)"
        ).add_to(best_path_layer)
        
        # Добавляем маркеры для каждой вершины в лучшем пути
        for i, (vertex_id, lat, lon) in enumerate(cleaned_best_path):
            # Определяем тип вершины (начало/конец/промежуточная)
            if i == 0:
                marker_color = 'green'
                marker_text = f"START: Vertex {vertex_id}"
                icon_color = 'lightgreen'
            elif i == len(cleaned_best_path) - 1:
                marker_color = 'red'
                marker_text = f"END: Vertex {vertex_id}"
                icon_color = 'lightcoral'
            else:
                marker_color = 'orange'
                marker_text = f"Vertex {vertex_id}"
                icon_color = 'lightyellow'
            
            folium.CircleMarker(
                location=(lat, lon),
                radius=6,
                color=marker_color,
                fill=True,
                fill_color=icon_color,
                fill_opacity=0.9,
                weight=2,
                tooltip=marker_text,
                popup=folium.Popup(f"<b>Vertex ID: {vertex_id}</b>", max_width=200)
            ).add_to(best_path_layer)
    
    # Добавляем все слои на карту
    primal_graph_layer.add_to(map_osm)
    dual_graph_layer.add_to(map_osm)
    dual_graph_no_saturated_layer.add_to(map_osm)
    external_layer.add_to(map_osm)
    source_layer.add_to(map_osm)
    sink_layer.add_to(map_osm)
    st_path_layer.add_to(map_osm)
    neighbor_splits_layer.add_to(map_osm)
    spt1_layer.add_to(map_osm)
    spt2_layer.add_to(map_osm)
    best_path_layer.add_to(map_osm)
    
    # Добавляем контроль слоев (переключатель в правом верхнем углу)
    folium.LayerControl(position='topright', collapsed=False).add_to(map_osm)
    
    # Настраиваем границы карты
    min_lat = min(p[0] for p in all_points)
    max_lat = max(p[0] for p in all_points)
    min_lon = min(p[1] for p in all_points)
    max_lon = max(p[1] for p in all_points)
    map_osm.fit_bounds([[min_lat, min_lon], [max_lat, max_lon]])
    
    # Добавляем легенду
    legend_html = '''
    <div style="position: fixed; 
                bottom: 50px; right: 50px; width: 380px; height: 680px; 
                background-color: white; border:2px solid grey; z-index:9999; 
                font-size:11px; padding: 10px; overflow-y: auto;">
    <p><b>MaxFlowReif Visualization</b></p>
    <p style="margin-bottom: 6px; font-size: 10px;"><b>Primal Graph (Original, toggle):</b></p>
    <p style="margin-left: 15px;"><span style="color:#4A90E2;">━</span> Edges</p>
    <p style="margin-left: 15px;"><span style="color:#4A90E2;">⬤</span> Vertices</p>
    <p style="margin-bottom: 6px; margin-top: 6px; font-size: 10px;"><b>Dual Graph (Full, toggle):</b></p>
    <p style="margin-left: 15px;"><span style="color:#FF0000; font-weight: bold;">━━━</span> Saturated (flow = capacity)</p>
    <p style="margin-left: 15px;"><span style="color:#FF8800; font-weight: bold;">━━</span> With flow (flow &lt; capacity)</p>
    <p style="margin-left: 15px;"><span style="color:#888888;">╌╌</span> No flow (capacity only)</p>
    <p style="margin-bottom: 6px; margin-top: 6px; font-size: 10px;"><b>Algorithm Data:</b></p>
    <p><span style="color:gray;">⬤</span> External Boundary</p>
    <p><span style="color:green;">⬤</span> Source Boundary</p>
    <p><span style="color:red;">⬤</span> Sink Boundary</p>
    <p><span style="color:blue;">━ ⬤</span> S-T Path</p>
    <p><span style="color:gold;">⇒</span> Path Neighbors (both splits)</p>
    <p><span style="color:purple;">⬤ →</span> Left Neighbors (split 1)</p>
    <p><span style="color:cyan;">⬤ →</span> Right Neighbors (split 2)</p>
    <p style="margin-bottom: 6px; margin-top: 6px; font-size: 10px;"><b>SPT 1 - Left Split (toggle):</b></p>
    <p style="margin-left: 15px;"><span style="color:#9932CC;">━</span> Tree edges</p>
    <p style="margin-left: 15px;"><span style="color:#8B008B;">⬤</span> Root vertex</p>
    <p style="margin-left: 15px;"><span style="color:#DA70D6;">⬤</span> Boundary leaves (% = left weight)</p>
    <p style="margin-bottom: 6px; margin-top: 6px; font-size: 10px;"><b>SPT 2 - Right Split (toggle):</b></p>
    <p style="margin-left: 15px;"><span style="color:#008B8B;">━</span> Tree edges</p>
    <p style="margin-left: 15px;"><span style="color:#006666;">⬤</span> Root vertex</p>
    <p style="margin-left: 15px;"><span style="color:#20B2AA;">⬤</span> Boundary leaves (% = left weight)</p>
    <p style="margin-left: 15px; font-size: 9px; font-style: italic;">
    Li → weight(R0+...+Ri) / total<br>
    Shows cumulative region weight split
    </p>
    <p><span style="color:orange;">━ ⬤</span> <b>Best Path</b></p>
    </div>
    '''
    map_osm.get_root().html.add_child(folium.Element(legend_html))
    
    # Сохраняем карту
    output_path = os.path.join(directory_path, output_file)
    map_osm.save(output_path)
    print(f"Map saved to {output_path}")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python flow_visualizer.py <directory_name> <output_file.html>")
        print("Example: python flow_visualizer.py reif_flow_debug flow_visualization.html")
        sys.exit(1)
    
    directory_name = sys.argv[1]
    output_file = sys.argv[2]
    
    visualize_reif_flow(directory_name, output_file)

