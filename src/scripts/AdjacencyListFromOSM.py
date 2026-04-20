import osmnx
import folium
from geopy.distance import geodesic
import networkx as nx
from shapely.geometry import Point, box, Polygon, LineString
from math import atan2, degrees

center_lat = 59.93893094417527
center_lon = 30.32268115454809
d = 50
withRivers = False

def get_bounding_box(center, distance):
    lat, lon = center
    north = geodesic(meters=distance).destination((lat, lon), 0).latitude
    south = geodesic(meters=distance).destination((lat, lon), 180).latitude
    east = geodesic(meters=distance).destination((lat, lon), 90).longitude
    west = geodesic(meters=distance).destination((lat, lon), 270).longitude
    return Polygon([(west, north), (east, north), (east, south), (west, south)])

print("write x coordinate or -:")
inputX = input()
if inputX != '-':
    center_lat = float(inputX)

print("write y coordinate or -:")
inputY = input()
if inputY != '-':
    center_lon = float(inputY)

print("write dist or -:")
inputDist = input()
if inputDist != '-':
    d = int(inputDist)

print("with rivers [Y/N]")
inputRivers = input()
if inputRivers == "Y":
    withRivers = True

# Получаем граф с ребрами, проходящими через границу
network_graph = osmnx.graph_from_point((center_lat, center_lon), dist=d, truncate_by_edge=True, simplify=False)

G = network_graph

if withRivers:
    water_graph = nx.MultiDiGraph()
    try:
        # Получаем граф с ребрами водоемов
        water_graph = osmnx.graph_from_point((center_lat, center_lon), dist=d, truncate_by_edge=True, simplify=False, retain_all=True, custom_filter=['[waterway=river]','[waterway=canal]'])

        for u, v, data in water_graph.edges(data=True):
            data['length'] = 0.1
            data['water'] = True
    except ValueError:
        water_graph = nx.MultiDiGraph()

    # объединяем графы
    G = nx.compose_all([G, water_graph])

# Получаем границу bounding box
boundary_box = get_bounding_box((center_lat, center_lon), d)

boundary_box_coords = list(boundary_box.exterior.coords)[:-1]

nodes_to_remove = set()
nodes_to_add = []
added_coord_to_id = {}
edges_to_add = []
max_id = max(G.nodes)

# O(1) coordinate lookup for existing nodes (replaces per-edge O(N) scan)
coord_to_node = {(ndata['x'], ndata['y']): nid for nid, ndata in G.nodes(data=True)}

edges_data = list(G.edges(data=True))

for u, v, edata in edges_data:
    is_water = edata.get('water', False)
    # Получаем координаты начальной и конечной точек ребра
    ux, uy = G.nodes[u]["x"], G.nodes[u]["y"]
    vx, vy = G.nodes[v]["x"], G.nodes[v]["y"]
    u_point = Point(ux, uy)
    v_point = Point(vx, vy)
    edge_line = LineString([u_point, v_point])

    u_inside = boundary_box.contains(u_point)
    v_inside = boundary_box.contains(v_point)

    if not u_inside and not v_inside:
        nodes_to_remove.add(u)
        nodes_to_remove.add(v)
        continue

    # Проверяем, пересекает ли ребро границу
    if not u_inside or not v_inside:
        intersection = boundary_box.intersection(edge_line)

        if not intersection.is_empty:
            if isinstance(intersection, Point):
                intersection_point = intersection
            elif isinstance(intersection, LineString):
                vertex = intersection.intersection(edge_line)
                intersection_points = [Point(pt) for pt in intersection.coords]
                if vertex == intersection_points[0]:
                    intersection_point = intersection_points[1]
                else:
                    intersection_point = intersection_points[0]

            ip_key = (intersection_point.x, intersection_point.y)
            new_node_id = coord_to_node.get(ip_key)

            if new_node_id is None:
                new_node_id = added_coord_to_id.get(ip_key)
                if new_node_id is None:
                    new_node_id = max_id + 1
                    nodes_to_add.append((new_node_id, intersection_point.x, intersection_point.y))
                    added_coord_to_id[ip_key] = new_node_id
                    max_id += 1

            # Заменяем ребро на часть внутри границы
            if u_inside:
                distance = 0 if is_water else geodesic((uy, ux), (intersection_point.y, intersection_point.x)).meters
                nodes_to_remove.add(v)
                edges_to_add.append((u, new_node_id, {"length": distance}))
            elif v_inside:
                distance = 0 if is_water else geodesic((vy, vx), (intersection_point.y, intersection_point.x)).meters
                nodes_to_remove.add(u)
                edges_to_add.append((new_node_id, v, {"length": distance}))

for node_id, x_coord, y_coord in nodes_to_add:
    if node_id not in G.nodes:
        G.add_node(node_id, x=x_coord, y=y_coord)

for i, coords in enumerate(boundary_box_coords):
    G.add_node(max_id + 1 + i, x=coords[0], y=coords[1])
    nodes_to_add.append((max_id + 1 + i, coords[0], coords[1]))

G.remove_nodes_from(nodes_to_remove)

G.add_edges_from(edges_to_add)

def calculate_angle(point):
    px, py = point[1], point[2]
    return degrees(atan2(py - center_lat, px - center_lon))

nodes_to_add = sorted(nodes_to_add, key=calculate_angle)

n_boundary = len(nodes_to_add)
for i in range(n_boundary):
    id1, x1, y1 = nodes_to_add[i]
    id2, x2, y2 = nodes_to_add[(i + 1) % n_boundary]
    distance = geodesic((y1, x1), (y2, x2)).meters
    G.add_edge(id1, id2, length=distance)

node_xy = {nid: (ndata["x"], ndata["y"]) for nid, ndata in G.nodes(data=True)}

out_path = "graph_" + str(center_lat) + "_" + str(center_lon) + "_" + str(d) + ".txt"
with open(out_path, "w") as file:
    parts = [str(G.number_of_nodes()), " \n"]
    for line in nx.generate_adjlist(G):
        word_list = line.split()
        ids = [int(w) for w in word_list]
        head = ids[0]
        hx, hy = node_xy[head]
        parts.append(f"{head} {hx} {hy} {len(ids) - 1} ")
        for nb in ids[1:]:
            nx_, ny_ = node_xy[nb]
            length = G.get_edge_data(head, nb, 0)["length"]
            parts.append(f"{nb} {nx_} {ny_} {length} ")
        parts.append("\n")
    file.write("".join(parts))
