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
    external_layer = folium.FeatureGroup(name='External Boundary', show=True)
    source_layer = folium.FeatureGroup(name='Source Boundary', show=True)
    sink_layer = folium.FeatureGroup(name='Sink Boundary', show=True)
    st_path_layer = folium.FeatureGroup(name='S-T Path', show=True)
    neighbor_splits_layer = folium.FeatureGroup(name='Neighbor Splits', show=True)
    best_path_layer = folium.FeatureGroup(name='Best Path (Result)', show=True)
    
    # 1. Внешняя граница графа (серый полигон)
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
    
    # 2. Граница source (зеленый)
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
    
    # 3. Граница sink (красный)
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
    
    # 4. Путь s-t между границами (синий)
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
                radius=4,
                color='blue',
                fill=True,
                fill_color='lightblue',
                tooltip=f"S-T path vertex {vertex_id} (step {i})"
            ).add_to(st_path_layer)
    
    # 4.5. Разделение соседей (стрелки от вершин к соседям)
    if neighbor_splits:
        for split in neighbor_splits:
            vertex_id, vertex_lat, vertex_lon = split['vertex']
            vertex_coords = (vertex_lat, vertex_lon)
            
            # Соседи на пути - желтые толстые стрелки (подключены к обеим частям)
            for neighbor_id, neighbor_lat, neighbor_lon in split['path']:
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
            
            # Левые соседи - фиолетовые стрелки
            for neighbor_id, neighbor_lat, neighbor_lon in split['left']:
                folium.PolyLine(
                    locations=[vertex_coords, (neighbor_lat, neighbor_lon)],
                    color='purple',
                    weight=2,
                    opacity=0.6,
                    tooltip=f"Left neighbor (split 1): {vertex_id} → {neighbor_id}"
                ).add_to(neighbor_splits_layer)
                
                # Добавляем стрелку в конце (маркер)
                folium.CircleMarker(
                    location=(neighbor_lat, neighbor_lon),
                    radius=2,
                    color='purple',
                    fill=True,
                    fill_color='purple',
                    fill_opacity=0.8
                ).add_to(neighbor_splits_layer)
            
            # Правые соседи - бирюзовые/голубые стрелки
            for neighbor_id, neighbor_lat, neighbor_lon in split['right']:
                folium.PolyLine(
                    locations=[vertex_coords, (neighbor_lat, neighbor_lon)],
                    color='cyan',
                    weight=2,
                    opacity=0.6,
                    tooltip=f"Right neighbor (split 2): {vertex_id} → {neighbor_id}"
                ).add_to(neighbor_splits_layer)
                
                # Добавляем стрелку в конце (маркер)
                folium.CircleMarker(
                    location=(neighbor_lat, neighbor_lon),
                    radius=2,
                    color='cyan',
                    fill=True,
                    fill_color='cyan',
                    fill_opacity=0.8
                ).add_to(neighbor_splits_layer)
    
    # 5. Лучший найденный путь (оранжевый/желтый - самый важный, рисуем последним)
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
        
        for i, (vertex_id, lat, lon) in enumerate(cleaned_best_path):
            folium.CircleMarker(
                location=(lat, lon),
                radius=5,
                color='darkorange',
                fill=True,
                fill_color='yellow',
                tooltip=f"Best path vertex {vertex_id} (step {i})"
            ).add_to(best_path_layer)
    
    # Добавляем все слои на карту
    external_layer.add_to(map_osm)
    source_layer.add_to(map_osm)
    sink_layer.add_to(map_osm)
    st_path_layer.add_to(map_osm)
    neighbor_splits_layer.add_to(map_osm)
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
                bottom: 50px; right: 50px; width: 280px; height: 290px; 
                background-color: white; border:2px solid grey; z-index:9999; 
                font-size:12px; padding: 10px">
    <p><b>MaxFlowReif Visualization</b></p>
    <p><span style="color:gray;">⬤</span> External Boundary</p>
    <p><span style="color:green;">⬤</span> Source Boundary</p>
    <p><span style="color:red;">⬤</span> Sink Boundary</p>
    <p><span style="color:blue;">━</span> S-T Path</p>
    <p><span style="color:gold;">⇒</span> Path Neighbors (both splits)</p>
    <p><span style="color:purple;">→</span> Left Neighbors (split 1)</p>
    <p><span style="color:cyan;">→</span> Right Neighbors (split 2)</p>
    <p><span style="color:orange;">━</span> <b>Best Path (Result)</b></p>
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

