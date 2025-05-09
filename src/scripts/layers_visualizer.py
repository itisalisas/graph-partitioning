import argparse
import os
import sys
import folium
import pandas as pd
import networkx as nx
from matplotlib import colors as mcolors
from matplotlib import pyplot as plt
from natsort import natsorted
from math import ceil

def get_project_root():
    """Возвращает абсолютный путь к корню проекта"""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    return os.path.abspath(os.path.join(script_dir, '..', '..'))

def resolve_path(path, path_part):
    """Преобразует относительный путь в абсолютный относительно корня проекта"""
    if os.path.isabs(path):
        return path
    return os.path.join(get_project_root(), path_part, path)

def parse_arguments():
    parser = argparse.ArgumentParser(description="Visualization tool for maps with multiple layers")
    parser.add_argument("-g", "--graph", help="Path to graph file")
    parser.add_argument("-b", "--bounds", help="Path to directory with boundary files")
    parser.add_argument("-p", "--points", help="Path to points file with weights")
    parser.add_argument("-d", "--dual", action='store_true', help="Enable dual graph visualization")
    parser.add_argument("-o", "--output", required=True, help="Output HTML file")
    return parser.parse_args()

def generate_distinct_colors(n):
    if n == 0:
        return []

    hues = [i/n for i in range(n)]
    saturation = 0.7 + 0.3 * (n % 3) / 3
    lightness = 0.5 + 0.2 * (n % 2) / 2

    colors = []
    for hue in hues:
        rgb = mcolors.hsv_to_rgb((hue, saturation, lightness))
        colors.append(mcolors.rgb2hex(rgb))

    return colors

def add_graph_layer(map_osm, graph_path):
    graph_path = resolve_path(graph_path, os.path.join("src", "main", "resources"))
    G = nx.Graph()

    graph_group = folium.FeatureGroup(name='Graph', show=True)

    with open(graph_path, "r") as file:
        lines = file.readlines()
        num_vertices = int(lines[0].strip())

        for line in lines[1:]:
            data = line.split()
            main_vertex_id = int(data[0])
            main_x = float(data[1].replace(',', '.'))
            main_y = float(data[2].replace(',', '.'))
            G.add_node(main_vertex_id, x=main_x, y=main_y)

            num_edges = int(data[3])
            idx = 4
            for _ in range(num_edges):
                neighbor_id = int(data[idx])
                neighbor_x = float(data[idx+1].replace(',', '.'))
                neighbor_y = float(data[idx+2].replace(',', '.'))
                length = float(data[idx+3].replace(',', '.'))

                if neighbor_id not in G.nodes:
                    G.add_node(neighbor_id, x=neighbor_x, y=neighbor_y)

                G.add_edge(main_vertex_id, neighbor_id, length=length)
                idx += 4

    for node, data in G.nodes(data=True):
        folium.CircleMarker(
            location=(data["y"], data["x"]),
            radius=2,
            color="blue",
            fill=True,
            fill_color="blue"
        ).add_to(graph_group)

    for u, v, data in G.edges(data=True):
        u_coords = (G.nodes[u]["y"], G.nodes[u]["x"])
        v_coords = (G.nodes[v]["y"], G.nodes[v]["x"])
        folium.PolyLine(
            locations=[u_coords, v_coords],
            color="green",
            weight=2.5,
            popup=f"Length: {data['length']}"
        ).add_to(graph_group)

    graph_group.add_to(map_osm)
    return G

def add_bounds_layer(map_osm, bounds_dir):
    bounds_dir = resolve_path(bounds_dir, os.path.join("src", "main", "output"))
    bounds_group = folium.FeatureGroup(name='Bounds', show=True)

    def load_boundary(file_path):
        with open(file_path, 'r') as file:
            lines = file.readlines()
            if not lines:
                return [], 0.0

            coords = [
                [float(coord.replace(',', '.')) for coord in line.strip().split()[1:3][::-1]]
                for line in lines[:-1]
            ]

            try:
                weight = float(lines[-1].strip().replace(',', '.'))
            except (ValueError, IndexError):
                weight = 0.0

            return coords, weight

    def calculate_polygon_center(points):
        lats = [p[0] for p in points]
        lons = [p[1] for p in points]
        return (sum(lats)/len(lats), sum(lons)/len(lons))

    boundaries = []
    bounds_path = os.path.abspath(bounds_dir)

    for fname in natsorted(os.listdir(bounds_path)):
        if fname.startswith("bound_") and fname.endswith(".txt"):
            file_path = os.path.join(bounds_path, fname)
            boundary_coords, weight = load_boundary(file_path)

            if not boundary_coords:
                continue

            boundaries.append((boundary_coords, weight))

            center = calculate_polygon_center(boundary_coords)
            folium.Marker(
                location=center,
                icon=folium.Icon(color='darkblue', icon='crosshairs', prefix='fa')
            ).add_to(bounds_group)

    colors = generate_distinct_colors(len(boundaries))

    for i, (boundary_coords, weight) in enumerate(boundaries):
        folium.Polygon(
            locations=boundary_coords,
            color='black',
            fill=True,
            fill_color=colors[i],
            fill_opacity=0.5,
            popup=f"Boundary {i+1}\nWeight: {weight:.2f}"
        ).add_to(bounds_group)

    bounds_group.add_to(map_osm)
    return boundaries

def add_points_layer(map_osm, points_file):
    points_file = resolve_path(points_file, os.path.join("src", "main", "resources"))
    points_group = folium.FeatureGroup(name='Points', show=True)

    def load_points(file_path):
        points_data = []
        with open(file_path, 'r') as file:
            next(file)
            for line in file:
                parts = line.strip().split()
                if len(parts) < 5:
                    continue

                try:
                    lat = float(parts[1].replace(',', '.'))
                    lon = float(parts[2].replace(',', '.'))
                    value1 = float(parts[3].replace(',', '.'))
                    value2 = float(parts[4].replace(',', '.'))

                    weight = (value1 * value2) / 10.0
                    points_data.append({
                        'location': (lat, lon),
                        'weight': round(weight, 2)
                    })
                except (ValueError, IndexError) as e:
                    print(f"Error parsing line: {line.strip()} - {e}")
        return points_data

    points = load_points(os.path.abspath(points_file))

    for point in points:
        folium.CircleMarker(
            location=point['location'],
            radius=3,
            color="red",
            fill=True,
            fill_color="red",
            fill_opacity=0.7,
            popup=f"Weight: {point['weight']}"
        ).add_to(points_group)

    points_group.add_to(map_osm)
    return points

def calculate_center(coordinates):
    lats = [coord[0] for coord in coordinates]
    lons = [coord[1] for coord in coordinates]
    min_lat, max_lat = min(lats), max(lats)
    min_lon, max_lon = min(lons), max(lons)

    return ((min_lat + max_lat) / 2,
           (min_lon + max_lon) / 2)

def add_dual_graph_layer(map_osm, bounds_dir):
    bounds_dir = resolve_path(bounds_dir, os.path.join("src", "main", "output"))
    dual_group = folium.FeatureGroup(name='Dual Graph', show=False)

    dual_path = os.path.join(bounds_dir, "dual.txt")
    if not os.path.exists(dual_path):
        print(f"Dual graph file not found: {dual_path}")
        return dual_group

    G = nx.Graph()

    with open(dual_path, 'r', encoding='utf-8') as file:
        lines = [line.strip() for line in file if line.strip()]
        if not lines:
            return dual_group

        try:
            num_edges = int(lines[0])
        except ValueError:
            print("Invalid dual graph file format")
            return dual_group

        for line in lines[1:]:
            parts = line.split()
            if len(parts) < 9:
                continue

            try:
                # Парсим основную вершину
                main_id = int(parts[0])
                main_x = float(parts[1].replace(',', '.'))
                main_y = float(parts[2].replace(',', '.'))
                main_weight = float(parts[3].replace(',', '.'))
                num_neighbors = int(parts[4])

                G.add_node(main_id,
                         x=main_x,
                         y=main_y,
                         weight=main_weight,
                         name=f"{main_id}")

                idx = 5
                for _ in range(num_neighbors):
                    if idx+3 >= len(parts):
                        break

                    neighbor_id = int(parts[idx])
                    neighbor_x = float(parts[idx+1].replace(',', '.'))
                    neighbor_y = float(parts[idx+2].replace(',', '.'))
                    length = float(parts[idx+3].replace(',', '.'))

                    G.add_node(neighbor_id,
                             x=neighbor_x,
                             y=neighbor_y,
                             name=f"{neighbor_id}")

                    G.add_edge(main_id, neighbor_id,
                             length=length,
                             length_display=round(length, 2))

                    idx += 4

            except (ValueError, IndexError) as e:
                print(f"Error parsing line: {line[:50]}... - {e}")
                continue

    # Визуализация вершин с улучшенными попапами
    for node, data in G.nodes(data=True):
        folium.CircleMarker(
            location=(data['y'], data['x']),
            radius=6,
            color='#8A2BE2',  # Фиолетовый
            fill=True,
            fill_color='#9370DB',  # Светло-фиолетовый
            fill_opacity=0.7,
            popup=folium.Popup(
                f"ID: {data['name']}<br>"
                f"Weight: {data.get('weight', 'N/A')}<br>",
                max_width=250
            )
        ).add_to(dual_group)

    # Визуализация ребер с улучшенными попапами
    for u, v, data in G.edges(data=True):
        u_coords = (G.nodes[u]['y'], G.nodes[u]['x'])
        v_coords = (G.nodes[v]['y'], G.nodes[v]['x'])

        folium.PolyLine(
            locations=[u_coords, v_coords],
            color='#FF1493',  # Ярко-розовый
            weight=2,
            dash_array='5,5',
            popup=folium.Popup(
                f"From: {G.nodes[u]['name']}<br>"
                f"To: {G.nodes[v]['name']}<br>"
                f"Length: {data.get('length_display', 'N/A')}",
                max_width=250
            )
        ).add_to(dual_group)

    dual_group.add_to(map_osm)
    return G

def main():
    args = parse_arguments()
    coordinates = []
    map_osm = folium.Map(location=[0, 0], zoom_start=12)

    if args.bounds:
        bounds = add_bounds_layer(map_osm, args.bounds)

    if args.graph:
        G = add_graph_layer(map_osm, args.graph)
        coordinates.extend([(node["y"], node["x"]) for node in G.nodes.values()])

    if args.points:
        points = add_points_layer(map_osm, args.points)

    if args.dual:
        dual_graph = add_dual_graph_layer(map_osm, args.bounds)

    folium.LayerControl(collapsed=False).add_to(map_osm)

    if coordinates:
        map_osm.location = calculate_center(coordinates)
        map_osm.fit_bounds([coordinates[0], coordinates[-1]])

    map_osm.save(args.output)
    print(f"Map saved to {args.output}")

if __name__ == "__main__":
    main()