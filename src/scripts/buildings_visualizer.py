import folium
import pandas as pd
import os
import sys
import networkx as nx
from matplotlib import colors as mcolors
from matplotlib import pyplot as plt
from natsort import natsorted

from graph_visualizer import visualize_graph

def visualize_buildings(file_name, map_osm):
    G = nx.Graph()

    script_dir = os.path.dirname(os.path.abspath(__file__))
    file_path = os.path.join(script_dir, '..', '..', file_name)

    with open(file_path, "r") as file:
        lines = file.readlines()

        for line in lines[1:]:
            data = line.split()

            main_vertex_id = int(data[0])
            main_x = float(data[2].replace(',', '.'))
            main_y = float(data[1].replace(',', '.'))
            G.add_node(main_vertex_id, x=main_x, y=main_y)

    # Определение центральной точки для карты
    x_coords = [G.nodes[node]["x"] for node in G]
    y_coords = [G.nodes[node]["y"] for node in G]
    center = (sum(y_coords) / len(y_coords), sum(x_coords) / len(x_coords))

    # map_osm = folium.Map(location=center, zoom_start=15)

    for node, data in G.nodes(data=True):
        folium.CircleMarker(
            location=(data["y"], data["x"]),
            radius=2,
            color="red",
            fill=True,
            fill_color="red",
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

    # return map_osm

def main(points_file, graph_file, output_file):
    map_osm = visualize_graph(graph_file)
    visualize_buildings(points_file, map_osm)
    # Сохраняем результат
    map_osm.save(output_file)

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python buildings_visualizer.py <file_path> <graph_file>")
        sys.exit(1)

    directory_name = sys.argv[1]
    graph_file = sys.argv[2]
    output_file = "buildings.html"

    main(directory_name, graph_file, output_file)