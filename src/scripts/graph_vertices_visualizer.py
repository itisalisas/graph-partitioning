import networkx as nx
import folium
import os
import sys

if len(sys.argv) != 4:
    print("Usage: python graph_visualizer.py <directory_path_to_graph> <directory_path_to_vertices> <output_file.html>")
    sys.exit(1)
#graph
file_name_1 = sys.argv[1]
#vertices
file_name_2 = sys.argv[2]
output_file = sys.argv[3]

script_dir = os.path.dirname(os.path.abspath(__file__))
file_path_1 = os.path.join(script_dir, '..', 'main', 'resources', file_name_1)
file_path_2 = os.path.join(script_dir, '..', 'main', 'resources', file_name_2)


#graph
G = nx.Graph()
with open(file_path_1, "r") as file:
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
            neighbor_x = float(data[idx + 1].replace(',', '.'))
            neighbor_y = float(data[idx + 2].replace(',', '.'))
            length = float(data[idx + 3].replace(',', '.'))

            if neighbor_id not in G.nodes:
                G.add_node(neighbor_id, x=neighbor_x, y=neighbor_y)

            G.add_edge(main_vertex_id, neighbor_id, length=length)
            idx += 4


#vertices
vertices = nx.Graph()
with open(file_path_2, "r") as file:
    lines = file.readlines()
    num_vertices = int(lines[0].strip())

    for line in lines[1:]:
        data = line.split()

        main_vertex_id = int(data[0])
        main_x = float(data[1].replace(',', '.'))
        main_y = float(data[2].replace(',', '.'))
        vertices.add_node(main_vertex_id, x=main_x, y=main_y)


# Определение центральной точки для карты
x_coords = [G.nodes[node]["x"] for node in G]
y_coords = [G.nodes[node]["y"] for node in G]
center = (sum(y_coords) / len(y_coords), sum(x_coords) / len(x_coords))

map_osm = folium.Map(location=center, zoom_start=15)

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

for node, data in vertices.nodes(data=True):
    folium.CircleMarker(
        location=(data["y"], data["x"]),
        radius=1,
        color="red",
        fill=True,
        fill_color="red",
        tooltip=f"Index: {node}"
    ).add_to(map_osm)

map_osm.save(output_file)
