import networkx as nx
import folium
import os
import sys
from matplotlib import colors as mcolors
from matplotlib import pyplot as plt

def generate_colors(n):
    cmap = plt.get_cmap('RdYlGn')
    return [mcolors.rgb2hex(cmap(i / (n))) for i in range(n)]

if len(sys.argv) != 3:
    print("Usage: python dual_graph_visualizer.py <directory_path> <output_file.html>")
    sys.exit(1)

file_name = sys.argv[1]
output_file = sys.argv[2]

script_dir = os.path.dirname(os.path.abspath(__file__))
file_path = os.path.join(script_dir, '..', 'main', 'output', file_name, 'dual.txt')

G = nx.Graph()

vertex_part_map = dict()

with open(file_path, "r") as file:
    lines = file.readlines()
    first_line_data = lines[0].split()
    num_vertices = int(first_line_data[0])
    parts_number = int(first_line_data[1])

    for line in lines[1:]:
        data = line.split()

        main_vertex_id = int(data[0])
        main_part_num = int(data[1])
        main_x = float(data[2].replace(',', '.'))
        main_y = float(data[3].replace(',', '.'))
        G.add_node(main_vertex_id, x=main_x, y=main_y)
        vertex_part_map[main_vertex_id] = main_part_num

        num_edges = int(data[4])

        idx = 5
        for _ in range(num_edges):
            neighbor_id = int(data[idx])
            neighbor_x = float(data[idx + 1].replace(',', '.'))
            neighbor_y = float(data[idx + 2].replace(',', '.'))
            length = float(data[idx + 3].replace(',', '.'))

            if neighbor_id not in G.nodes:
                G.add_node(neighbor_id, x=neighbor_x, y=neighbor_y)

            G.add_edge(main_vertex_id, neighbor_id, length=length)
            idx += 4

# Определение центральной точки для карты
x_coords = [G.nodes[node]["x"] for node in G]
y_coords = [G.nodes[node]["y"] for node in G]
center = (sum(y_coords) / len(y_coords), sum(x_coords) / len(x_coords))

map_osm = folium.Map(location=center, zoom_start=15)

colors = generate_colors(parts_number)

for u, v, data in G.edges(data=True):
    u_coords = (G.nodes[u]["y"], G.nodes[u]["x"])
    v_coords = (G.nodes[v]["y"], G.nodes[v]["x"])

    folium.PolyLine(
        locations=[u_coords, v_coords],
        color="green",
        #opacity=0.3,
        weight=2.5
        # tooltip=f"Length: {data['length']}"
    ).add_to(map_osm)

for node, data in G.nodes(data=True):
    color = colors[vertex_part_map[node]]
    folium.CircleMarker(
        location=(data["y"], data["x"]),
        radius=2,
        color=color,
        fill=True,
        fill_color=color,
        tooltip=f"Part: {vertex_part_map[node]}\nVertex: {node}"
    ).add_to(map_osm)

map_osm.save(os.path.join(script_dir, '..', 'main', 'output', file_name, output_file))
