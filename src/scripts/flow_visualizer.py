import folium
import os
import sys

try:
    from shapely.geometry import Polygon as ShapelyPolygon, MultiPolygon
    from shapely.ops import unary_union
    SHAPELY_AVAILABLE = True
except ImportError:
    SHAPELY_AVAILABLE = False
    print("Warning: shapely not available, will draw individual polygons with visible internal edges")

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

def load_dual_graph(file_path):
    """Загружает двойственный граф"""
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
                if len(parts) >= 3:
                    from_id = int(parts[0])
                    to_id = int(parts[1])
                    bandwidth = float(parts[2])
                    edges.append({
                        'from': from_id,
                        'to': to_id,
                        'bandwidth': bandwidth
                    })
    
    print(f"Loaded dual graph: {len(vertices)} vertices, {len(edges)} edges")
    
    return vertices, edges


def load_spt(file_path):
    """Загружает Shortest Path Tree (SPT) с информацией о весах регионов"""
    spt_data = {
        'root': None,
        'total_region_weight': 0.0,
        'boundary_leaves': [],  # list of (id, lat, lon, cumulative_weight, boundary_index)
        'tree_edges': [],       # list of (from_id, from_lat, from_lon, to_id, to_lat, to_lon, distance)
        'vertices': {},         # id -> (lat, lon, distance)
        'region_weights': [],   # list of (region_idx, region_vertex_id, from_leaf_idx, to_leaf_idx, weight, centroid_lat, centroid_lon)
        'leaf_indices': [],     # list of (leaf_idx, region_idx)
        'leaf_group_boundaries': [],  # list of {group_idx, weight, boundary: [(lat, lon), ...]}
        'region_boundaries': []  # list of {region_idx, region_vertex_id, boundary: [(lat, lon), ...]}
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
            elif line == 'REGION_WEIGHTS':
                mode = 'REGION_WEIGHTS'
                continue
            elif line == 'LEAF_INDICES':
                mode = 'LEAF_INDICES'
                continue
            elif line == 'REGION_BOUNDARIES':
                mode = 'REGION_BOUNDARIES'
                current_region = None
                continue
            elif line == 'LEAF_GROUP_BOUNDARIES':
                mode = 'LEAF_GROUP_BOUNDARIES'
                current_group = None
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
            
            elif mode == 'REGION_WEIGHTS':
                parts = line.split()
                if len(parts) >= 7:
                    # New format with region_vertex_id
                    region_idx = int(parts[0])
                    region_vertex_id = int(parts[1])
                    from_leaf_idx = int(parts[2])
                    to_leaf_idx = int(parts[3])
                    weight = float(parts[4])
                    centroid_lon = float(parts[5])
                    centroid_lat = float(parts[6])
                    spt_data['region_weights'].append((region_idx, region_vertex_id, from_leaf_idx, to_leaf_idx, 
                                                       weight, centroid_lat, centroid_lon))
                elif len(parts) >= 6:
                    # Old format without region_vertex_id (backwards compatibility)
                    region_idx = int(parts[0])
                    from_leaf_idx = int(parts[1])
                    to_leaf_idx = int(parts[2])
                    weight = float(parts[3])
                    centroid_lon = float(parts[4])
                    centroid_lat = float(parts[5])
                    spt_data['region_weights'].append((region_idx, -1, from_leaf_idx, to_leaf_idx, 
                                                       weight, centroid_lat, centroid_lon))
            
            elif mode == 'LEAF_INDICES':
                parts = line.split()
                if len(parts) >= 2:
                    leaf_idx = int(parts[0])
                    region_idx = int(parts[1])
                    spt_data['leaf_indices'].append((leaf_idx, region_idx))
            
            elif mode == 'REGION_BOUNDARIES':
                if line == '---':
                    if current_region is not None:
                        spt_data['region_boundaries'].append(current_region)
                    current_region = None
                elif current_region is None:
                    parts = line.split()
                    if len(parts) >= 3:
                        current_region = {
                            'region_idx': int(parts[0]),
                            'region_vertex_id': int(parts[1]),
                            'boundary': []
                        }
                        num_verts = int(parts[2])
                    elif len(parts) >= 2:
                        current_region = {
                            'region_idx': int(parts[0]),
                            'region_vertex_id': int(parts[1]),
                            'boundary': []
                        }
                else:
                    parts = line.split()
                    if len(parts) >= 2:
                        longitude = float(parts[0])
                        latitude = float(parts[1])
                        current_region['boundary'].append((latitude, longitude))
            
            elif mode == 'LEAF_GROUP_BOUNDARIES':
                if line == '---':
                    if current_group is not None:
                        spt_data['leaf_group_boundaries'].append(current_group)
                    current_group = None
                elif current_group is None:
                    parts = line.split()
                    if len(parts) >= 3:
                        current_group = {
                            'group_idx': int(parts[0]),
                            'weight': float(parts[1]),
                            'boundary': []
                        }
                        num_verts = int(parts[2])
                    elif len(parts) >= 2:
                        current_group = {
                            'group_idx': int(parts[0]),
                            'weight': float(parts[1]),
                            'boundary': []
                        }
                else:
                    parts = line.split()
                    if len(parts) >= 2:
                        longitude = float(parts[0])
                        latitude = float(parts[1])
                        current_group['boundary'].append((latitude, longitude))
            
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
    
    # Build cumulative weights array from region_weights
    # weights[i] = sum of weights for regions 0..i
    weights = []
    cumulative = 0.0
    for region_data in spt_data['region_weights']:
        region_weight = region_data[4]  # weight is 5th element
        cumulative += region_weight
        weights.append(cumulative)
    spt_data['weights'] = weights
    
    print(f"Loaded SPT: root={spt_data['root']}, {len(spt_data['boundary_leaves'])} boundary leaves, "
          f"{len(spt_data['tree_edges'])} tree edges, {len(spt_data['region_weights'])} regions, "
          f"{len(spt_data['leaf_indices'])} leaf indices, "
          f"{len(spt_data['region_boundaries'])} region boundaries, "
          f"{len(spt_data['leaf_group_boundaries'])} group boundaries, "
          f"{len(weights)} cumulative weights, "
          f"total_weight={spt_data['total_region_weight']:.2f}")
    
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
    primal_vertices, primal_edges = load_primal_graph(os.path.join(directory_path, "primal_graph.txt"))
    dual_vertices, dual_edges = load_dual_graph(os.path.join(directory_path, "dual_graph.txt"))
    
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
    dual_graph_layer = folium.FeatureGroup(name='Dual Graph', show=False)
    external_layer = folium.FeatureGroup(name='External Boundary', show=True)
    source_layer = folium.FeatureGroup(name='Source Boundary', show=True)
    sink_layer = folium.FeatureGroup(name='Sink Boundary', show=True)
    st_path_layer = folium.FeatureGroup(name='S-T Path', show=True)
    best_path_layer = folium.FeatureGroup(name='Best Path (Result)', show=True)
    spt1_layer = folium.FeatureGroup(name='SPT 1 (Left Split)', show=False)
    spt2_layer = folium.FeatureGroup(name='SPT 2 (Right Split)', show=False)
    spt1_regions_layer = folium.FeatureGroup(name='SPT 1 Regions (Red→Green)', show=False)
    spt2_regions_layer = folium.FeatureGroup(name='SPT 2 Regions (Red→Green)', show=False)
    spt1_groups_layer = folium.FeatureGroup(name='SPT 1 Leaf Groups (Red→Green)', show=False)
    spt2_groups_layer = folium.FeatureGroup(name='SPT 2 Leaf Groups (Red→Green)', show=False)
    
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
                color='#144680',  # Синий цвет для исходного графа
                weight=1.5,
                opacity=0.6,
                tooltip=tooltip_text
            ).add_to(primal_graph_layer)
        
        # Рисуем вершины исходного графа
        for vertex_id, (lat, lon) in primal_vertices.items():
            folium.CircleMarker(
                location=(lat, lon),
                radius=2,
                color='#2E5C8A',
                fill=True,
                fill_color='#144680',
                fill_opacity=0.7,
                tooltip=f"Vertex {vertex_id}"
            ).add_to(primal_graph_layer)
    
    # 1. Двойственный граф
    if dual_vertices and dual_edges:
        print(f"Visualizing dual graph with {len(dual_vertices)} vertices and {len(dual_edges)} edges")
        
        # Рисуем рёбра двойственного графа (все одинаковые)
        edges_drawn = 0
        edges_skipped = 0
        
        for edge in dual_edges:
            from_id = edge['from']
            to_id = edge['to']
            bandwidth = edge['bandwidth']
            
            if from_id not in dual_vertices or to_id not in dual_vertices:
                edges_skipped += 1
                print(f"Warning: Edge {from_id}→{to_id} references missing vertex")
                continue
            
            from_lat, from_lon, _ = dual_vertices[from_id]
            to_lat, to_lon, _ = dual_vertices[to_id]
            
            tooltip_text = f"Edge {from_id}↔{to_id}<br>Capacity: {bandwidth:.2f}"
            
            folium.PolyLine(
                locations=[(from_lat, from_lon), (to_lat, to_lon)],
                color='#666666',
                weight=2,
                opacity=0.6,
                tooltip=tooltip_text
            ).add_to(dual_graph_layer)
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
    
    # 6. Shortest Path Trees (SPT) with region weights
    def visualize_spt(spt_data, layer, color_scheme, spt_name):
        """Визуализирует SPT с весами регионов"""
        if spt_data is None:
            return
        
        # Colors for the scheme
        edge_color = color_scheme['edge']
        root_color = color_scheme['root']
        leaf_color = color_scheme['leaf']
        vertex_color = color_scheme['vertex']
        region_label_color = color_scheme['region_label']
        
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
        
        # Draw boundary leaves (without labels - labels only on regions)
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
            
            # Draw leaf marker (no label - labels only on regions)
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
        
        # Draw region weight markers (hover to see info, no visible labels)
        if spt_data['region_weights']:
            for region_data in spt_data['region_weights']:
                if len(region_data) == 7:
                    region_idx, region_vertex_id, from_leaf_idx, to_leaf_idx, weight, centroid_lat, centroid_lon = region_data
                else:
                    region_idx, from_leaf_idx, to_leaf_idx, weight, centroid_lat, centroid_lon = region_data
                    region_vertex_id = -1
                
                weight_percentage = (weight / total_weight * 100) if total_weight > 0 else 0
                
                tooltip_text = (
                    f"<b>Region {region_idx}</b> ({spt_name})<br>"
                    f"Dual vertex ID: {region_vertex_id}<br>"
                    f"Between leaves L{from_leaf_idx} and L{to_leaf_idx}<br>"
                    f"Weight: {weight:.2f} ({weight_percentage:.1f}%)<br>"
                    f"Total: {total_weight:.2f}"
                )
                
                folium.CircleMarker(
                    location=(centroid_lat, centroid_lon),
                    radius=4,
                    color=region_label_color,
                    fill=True,
                    fill_color=region_label_color,
                    fill_opacity=0.5,
                    weight=1,
                    tooltip=tooltip_text
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
            'vertex': '#DDA0DD',    # Plum
            'region_label': '#8B008B'  # Dark magenta for region labels
        }, 'SPT1')
    
    # Visualize SPT 2 (teal/cyan scheme)
    if spt2:
        visualize_spt(spt2, spt2_layer, {
            'edge': '#008B8B',      # Dark cyan
            'root': '#006666',      # Darker teal
            'leaf': '#20B2AA',      # Light sea green
            'vertex': '#66CDAA',    # Medium aquamarine
            'region_label': '#006666'  # Darker teal for region labels
        }, 'SPT2')
    
    # 7.3. Individual regions from SPT (red-to-green gradient)
    def visualize_regions(spt_data, layer, spt_name):
        """Visualizes individual region boundaries from SPT.
        
        Each region is a face from the dual graph, and we draw its boundary.
        Colors gradient from red (first region in Euler tour) to green (last region).
        """
        if spt_data is None:
            return
        
        regions = spt_data.get('region_boundaries', [])
        region_weights = spt_data.get('region_weights', [])
        total_weight = spt_data.get('total_region_weight', 0.0)
        
        if not regions:
            print(f"  {spt_name}: No region boundaries data")
            return
        
        num_regions = len(regions)
        print(f"  {spt_name}: Visualizing {num_regions} individual regions")
        
        skipped_empty = 0
        skipped_small = 0
        drawn = 0
        
        for region_data in regions:
            region_idx = region_data['region_idx']
            region_vertex_id = region_data['region_vertex_id']
            boundary = region_data['boundary']
            
            if not boundary:
                skipped_empty += 1
                continue
            if len(boundary) < 3:
                skipped_small += 1
                continue
            
            # Red-to-green gradient: region 0 = red, last region = green
            t = region_idx / max(num_regions - 1, 1)
            r = int(255 * (1 - t))
            g = int(255 * t)
            color = f'#{r:02x}{g:02x}00'
            
            # Get weight for this region from region_weights
            region_weight = 0.0
            weight_pct = 0.0
            if region_idx < len(region_weights):
                region_weight_data = region_weights[region_idx]
                region_weight = region_weight_data[4]  # weight is 5th element
                weight_pct = (region_weight / total_weight * 100) if total_weight > 0 else 0
            
            tooltip_text = (
                f"<b>Region {region_idx}</b> ({spt_name})<br>"
                f"Dual vertex ID: {region_vertex_id}<br>"
                f"Weight: {region_weight:.2f} ({weight_pct:.1f}%)<br>"
                f"Total: {total_weight:.2f}"
            )
            
            popup_text = (
                f"<b>Region {region_idx} ({spt_name})</b><br>"
                f"Dual vertex: {region_vertex_id}<br>"
                f"<b>Weight: {region_weight:.2f}</b> ({weight_pct:.1f}% of {total_weight:.2f})"
            )
            
            # Draw the region boundary polygon
            folium.Polygon(
                locations=boundary,
                color=color,
                fill=True,
                fill_color=color,
                fill_opacity=0.25,
                weight=2,
                opacity=0.7,
                tooltip=tooltip_text,
                popup=folium.Popup(popup_text, max_width=300)
            ).add_to(layer)
            
            drawn += 1
        
        print(f"  {spt_name}: {drawn} drawn, {skipped_empty} empty, {skipped_small} too small (< 3 pts), total {num_regions} regions")
    
    # 7.5. Grouped regions between consecutive boundary leaves (red-to-green gradient)
    def visualize_leaf_groups(spt_data, layer, spt_name):
        """Visualizes groups of regions between consecutive boundary leaves.
        
        Each region is drawn individually, but all regions in the same group share the same color.
        Colors gradient from red (first group) to green (last group).
        """
        if spt_data is None:
            return
        
        region_boundaries = spt_data.get('region_boundaries', [])
        region_weights = spt_data.get('region_weights', [])
        leaf_indices = spt_data.get('leaf_indices', [])
        boundary_leaves = spt_data.get('boundary_leaves', [])
        total_weight = spt_data.get('total_region_weight', 0.0)
        
        if not region_boundaries or not leaf_indices:
            print(f"  {spt_name}: No region boundaries or leaf indices data for groups")
            return
        
        num_leaves = len(leaf_indices)
        num_groups = num_leaves + 1  # N leaves => N+1 groups
        print(f"  {spt_name}: Visualizing {num_groups} leaf groups using individual region boundaries")
        
        # Extract region indices from leaf_indices (which are tuples of (leaf_idx, region_idx))
        region_indices = [region_idx for (leaf_idx, region_idx) in leaf_indices]
        
        # Build mapping: region_idx -> group_idx
        region_to_group = {}
        num_regions = len(region_boundaries)
        
        for group_idx in range(num_groups):
            from_region, to_region = 0, -1
            
            if group_idx == 0:
                # Group 0: before first leaf (regions 0..leaf_indices[0])
                from_region = 0
                to_region = region_indices[0] if num_leaves > 0 else -1
            elif group_idx < num_groups - 1:
                # Group i: between leaf (i-1) and leaf i
                from_region = region_indices[group_idx - 1] + 1 if group_idx > 0 else 0
                to_region = region_indices[group_idx]
            else:
                # Last group: after last leaf
                from_region = region_indices[num_leaves - 1] + 1 if num_leaves > 0 else 0
                to_region = num_regions - 1
            
            # Assign all regions in range to this group
            for r in range(max(0, from_region), min(num_regions, to_region + 1)):
                region_to_group[r] = group_idx
        
        # Calculate group weights and labels
        group_info = {}
        for group_idx in range(num_groups):
            # Calculate group weight using region_indices
            group_weight = 0.0
            weights_list = spt_data.get('weights', [])
            if group_idx == 0:
                group_weight = weights_list[region_indices[0]] if region_indices[0] >= 0 and region_indices[0] < len(weights_list) else 0.0
            elif group_idx < num_groups - 1:
                prev_weight = weights_list[region_indices[group_idx - 1]] if region_indices[group_idx - 1] >= 0 and region_indices[group_idx - 1] < len(weights_list) else 0.0
                curr_weight = weights_list[region_indices[group_idx]] if region_indices[group_idx] >= 0 and region_indices[group_idx] < len(weights_list) else 0.0
                group_weight = curr_weight - prev_weight
            else:
                prev_weight = weights_list[region_indices[num_leaves - 1]] if num_leaves > 0 and region_indices[num_leaves - 1] >= 0 and region_indices[num_leaves - 1] < len(weights_list) else 0.0
                group_weight = total_weight - prev_weight
            
            # Generate label
            if group_idx == 0:
                right_leaf = boundary_leaves[0][0] if num_leaves > 0 else '?'
                label = f"Before L0 (v{right_leaf})"
            elif group_idx < num_groups - 1:
                left_leaf = boundary_leaves[group_idx - 1][0]
                right_leaf = boundary_leaves[group_idx][0]
                label = f"L{group_idx-1} (v{left_leaf}) → L{group_idx} (v{right_leaf})"
            else:
                left_leaf = boundary_leaves[num_leaves - 1][0]
                label = f"After L{num_leaves-1} (v{left_leaf})"
            
            # Red-to-green gradient: group 0 = red, last group = green
            t = group_idx / max(num_groups - 1, 1)
            r = int(255 * (1 - t))
            g = int(255 * t)
            color = f'#{r:02x}{g:02x}00'
            
            weight_pct = (group_weight / total_weight * 100) if total_weight > 0 else 0
            
            group_info[group_idx] = {
                'color': color,
                'weight': group_weight,
                'weight_pct': weight_pct,
                'label': label
            }
        
        # Group regions by group_idx for merged drawing
        groups_regions = {}
        for region_data in region_boundaries:
            region_idx = region_data['region_idx']
            region_vertex_id = region_data['region_vertex_id']
            boundary = region_data['boundary']
            
            if region_idx not in region_to_group:
                continue
            
            if not boundary or len(boundary) < 3:
                continue
            
            group_idx = region_to_group[region_idx]
            if group_idx not in groups_regions:
                groups_regions[group_idx] = []
            
            # Get region weight
            region_weight = 0.0
            if region_idx < len(region_weights):
                region_weight = region_weights[region_idx][4]
            
            groups_regions[group_idx].append({
                'region_idx': region_idx,
                'region_vertex_id': region_vertex_id,
                'boundary': boundary,
                'weight': region_weight
            })
        
        # Draw each group
        drawn = 0
        skipped = 0
        
        for group_idx in sorted(groups_regions.keys()):
            group = group_info[group_idx]
            regions = groups_regions[group_idx]
            
            if not regions:
                continue
            
            # Try to merge polygons if shapely is available
            if SHAPELY_AVAILABLE and len(regions) > 1:
                try:
                    # Convert to shapely polygons (note: shapely uses (lon, lat) order)
                    shapely_polys = []
                    for region in regions:
                        # Convert from (lat, lon) to (lon, lat) for shapely
                        coords = [(lon, lat) for (lat, lon) in region['boundary']]
                        shapely_polys.append(ShapelyPolygon(coords))
                    
                    # Merge polygons
                    merged = unary_union(shapely_polys)
                    
                    # Convert back to folium format (lat, lon)
                    if merged.geom_type == 'Polygon':
                        exterior_coords = [(lat, lon) for (lon, lat) in merged.exterior.coords]
                        
                        tooltip_text = (
                            f"<b>Group {group_idx}</b> ({spt_name})<br>"
                            f"{group['label']}<br>"
                            f"Regions: {len(regions)}<br>"
                            f"Group weight: {group['weight']:.2f} ({group['weight_pct']:.1f}%)<br>"
                            f"Total: {total_weight:.2f}"
                        )
                        
                        # Draw merged polygon
                        folium.Polygon(
                            locations=exterior_coords,
                            color=group['color'],
                            fill=True,
                            fill_color=group['color'],
                            fill_opacity=0.35,
                            weight=3,
                            opacity=0.8,
                            tooltip=tooltip_text
                        ).add_to(layer)
                        drawn += 1
                    elif merged.geom_type == 'MultiPolygon':
                        # Handle disconnected regions
                        for poly in merged.geoms:
                            exterior_coords = [(lat, lon) for (lon, lat) in poly.exterior.coords]
                            
                            tooltip_text = (
                                f"<b>Group {group_idx}</b> ({spt_name})<br>"
                                f"{group['label']}<br>"
                                f"Regions: {len(regions)}<br>"
                                f"Group weight: {group['weight']:.2f} ({group['weight_pct']:.1f}%)<br>"
                                f"Total: {total_weight:.2f}"
                            )
                            
                            folium.Polygon(
                                locations=exterior_coords,
                                color=group['color'],
                                fill=True,
                                fill_color=group['color'],
                                fill_opacity=0.35,
                                weight=3,
                                opacity=0.8,
                                tooltip=tooltip_text
                            ).add_to(layer)
                        drawn += len(merged.geoms)
                except Exception as e:
                    print(f"  Warning: Failed to merge polygons for group {group_idx}: {e}")
                    skipped += len(regions)
            else:
                # Draw individual regions without visible borders (weight=0)
                for region in regions:
                    region_weight_pct = (region['weight'] / total_weight * 100) if total_weight > 0 else 0
                    
                    tooltip_text = (
                        f"<b>Region {region['region_idx']}</b> (Group {group_idx}, {spt_name})<br>"
                        f"Group: {group['label']}<br>"
                        f"Dual vertex ID: {region['region_vertex_id']}<br>"
                        f"Region weight: {region['weight']:.2f} ({region_weight_pct:.1f}%)<br>"
                        f"Group weight: {group['weight']:.2f} ({group['weight_pct']:.1f}%)<br>"
                        f"Total: {total_weight:.2f}"
                    )
                    
                    # Draw without borders to hide internal edges
                    folium.Polygon(
                        locations=region['boundary'],
                        color=group['color'],
                        fill=True,
                        fill_color=group['color'],
                        fill_opacity=0.35,
                        weight=0,  # No border
                        opacity=0,
                        tooltip=tooltip_text
                    ).add_to(layer)
                    drawn += 1
        
        method = "merged" if SHAPELY_AVAILABLE else "individual (no borders)"
        print(f"  {spt_name}: {drawn} polygons drawn ({method}) in {num_groups} groups, {skipped} skipped")
    
    # Visualize individual regions first
    if spt1:
        visualize_regions(spt1, spt1_regions_layer, 'SPT1')
    if spt2:
        visualize_regions(spt2, spt2_regions_layer, 'SPT2')
    
    # Then visualize leaf groups
    if spt1:
        visualize_leaf_groups(spt1, spt1_groups_layer, 'SPT1')
    if spt2:
        visualize_leaf_groups(spt2, spt2_groups_layer, 'SPT2')
    
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
    external_layer.add_to(map_osm)
    source_layer.add_to(map_osm)
    sink_layer.add_to(map_osm)
    st_path_layer.add_to(map_osm)
    spt1_layer.add_to(map_osm)
    spt2_layer.add_to(map_osm)
    spt1_regions_layer.add_to(map_osm)
    spt2_regions_layer.add_to(map_osm)
    spt1_groups_layer.add_to(map_osm)
    spt2_groups_layer.add_to(map_osm)
    best_path_layer.add_to(map_osm)
    
    # Добавляем контроль слоев (переключатель в правом верхнем углу)
    folium.LayerControl(position='topright', collapsed=False).add_to(map_osm)
    
    # Настраиваем границы карты
    min_lat = min(p[0] for p in all_points)
    max_lat = max(p[0] for p in all_points)
    min_lon = min(p[1] for p in all_points)
    max_lon = max(p[1] for p in all_points)
    map_osm.fit_bounds([[min_lat, min_lon], [max_lat, max_lon]])

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
