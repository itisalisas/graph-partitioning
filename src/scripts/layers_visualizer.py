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
            fill_color="blue",
            tooltip=f"Index: {node}"
        ).add_to(map_osm)

    for u, v, data in G.edges(data=True):
        u_coords = (G.nodes[u]["y"], G.nodes[u]["x"])
        v_coords = (G.nodes[v]["y"], G.nodes[v]["x"])
        folium.PolyLine(
            locations=[u_coords, v_coords],
            color="green",
            weight=2.5,
            tooltip=f"Length: {data['length']}"
        ).add_to(map_osm)

    return G

def add_bounds_layer(map_osm, bounds_dir):
    bounds_dir = resolve_path(bounds_dir, os.path.join("src", "main", "output"))
    def load_boundary(file_path):
        with open(file_path, 'r') as file:
            return [[float(coord.replace(',', '.')) for coord in line.strip().split()[1:3][::-1]]
                    for line in file]

    boundaries = []
    bounds_path = os.path.abspath(bounds_dir)

    for fname in natsorted(os.listdir(bounds_path)):
        if fname.startswith("bound_") and fname.endswith(".txt"):
            boundary = load_boundary(os.path.join(bounds_path, fname))
            boundaries.append(boundary)

    colors = generate_distinct_colors(len(boundaries))

    for i, boundary in enumerate(boundaries):
        folium.Polygon(
            locations=boundary,
            color='black',
            fill=True,
            fill_color=colors[i],
            fill_opacity=0.5,
            tooltip=f"Boundary {i+1}"
        ).add_to(map_osm)

    return boundaries

def add_points_layer(map_osm, points_file):
    points_file = resolve_path(points_file, os.path.join("src", "main", "resources"))
    def load_points(file_path):
        with open(file_path, 'r') as file:
            return [[float(coord.replace(',', '.')) for coord in line.strip().split()[1:3]]
                    for line in file.readlines()[1:]]

    points = load_points(os.path.abspath(points_file))

    for point in points:
        folium.CircleMarker(
            location=point,
            radius=3,
            color="red",
            fill=True,
            fill_color="red",
            fill_opacity=0.7
        ).add_to(map_osm)

    return points

def calculate_center(coordinates):
    lats = [coord[0] for coord in coordinates]
    lons = [coord[1] for coord in coordinates]
    min_lat, max_lat = min(lats), max(lats)
    min_lon, max_lon = min(lons), max(lons)

    return ((min_lat + max_lat) / 2,
           (min_lon + max_lon) / 2)

def main():
    args = parse_arguments()
    coordinates = []
    map_osm = folium.Map(location=[0, 0], zoom_start=12)

    if args.bounds:
        bounds = add_bounds_layer(map_osm, args.bounds)
        for boundary in bounds:
            coordinates.extend(boundary)

    if args.graph:
        G = add_graph_layer(map_osm, args.graph)
        coordinates.extend([(node["y"], node["x"]) for node in G.nodes.values()])

    if args.points:
        points = add_points_layer(map_osm, args.points)
        coordinates.extend(points)

    if coordinates:
        map_osm.location = calculate_center(coordinates)
        map_osm.fit_bounds([coordinates[0], coordinates[-1]])

    map_osm.save(args.output)
    print(f"Map saved to {args.output}")

if __name__ == "__main__":
    main()