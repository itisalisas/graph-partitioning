import json
import os
import sys

import branca.colormap as cm
import folium
from matplotlib import colors as mcolors
from matplotlib import pyplot as plt
import numpy as np

from graph_visualizer import visualize_graph

def generate_colors(n):
    cmap = plt.get_cmap('RdYlGn')
    return [mcolors.rgb2hex(cmap(i / (n))) for i in range(n)]

def visualize_points(points_file, map_osm):
    with open(points_file, 'r') as f:
        faces = json.load(f)
    all_coords = []
    for face in faces:
        for v in face['vertices']:
            all_coords.append([v['y'], v['x']])
    mean_lat = np.mean([c[0] for c in all_coords])
    mean_lon = np.mean([c[1] for c in all_coords])
    colormap = generate_colors(len(faces))
    for i, face in enumerate(faces):
        for v in face['vertices']:
            folium.CircleMarker(
                location=[v['y'], v['x']],
                radius=3,
                color=colormap[i],
                fill=True,
                fill_color=colormap[i],
                tooltip=f'Vertex: {v["x"]:.5f}, {v["y"]:.5f}, color: {colormap[i]}'
            ).add_to(map_osm)


def main(graph_file, points_file):
    script_dir = os.path.dirname(os.path.abspath(__file__))
    directory_path = os.path.join(script_dir, '..', 'main', 'output')
    map_osm = visualize_graph(graph_file)
    visualize_points(os.path.join(directory_path, points_file), map_osm) 
    output_file = os.path.join(directory_path, f"local.html")
    map_osm.save(output_file)

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python localization_visualizer.py <graph_file> <points_file>")
        sys.exit(1)

    graph_file = sys.argv[1]
    points_file = sys.argv[2]

    main(graph_file, points_file)