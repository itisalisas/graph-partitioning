import osmnx
import folium
from geopy.distance import geodesic
import networkx as nx
from shapely.geometry import Point, box, Polygon, LineString
from math import atan2, degrees

center_lat = 59.93893094417527
center_lon = 30.32268115454809
d = 50

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

# Получаем граф с ребрами, проходящими через границу
G = osmnx.graph_from_point((center_lat, center_lon), dist=d, truncate_by_edge=True, simplify=False)

# Получаем границу bounding box
boundary_box = get_bounding_box((center_lat, center_lon), d)

boundary_box_coords = list(boundary_box.exterior.coords)[:-1]

nodes_to_remove = set()
nodes_to_add = []
nodes_to_add_coords = set()
edges_to_add = []
max_id = max(G.nodes)

edges_data = G.edges(data=True)

for u, v, data in edges_data:
    # Получаем координаты начальной и конечной точек ребра
    u_point = Point(G.nodes[u]["x"], G.nodes[u]["y"])
    v_point = Point(G.nodes[v]["x"], G.nodes[v]["y"])
    edge_line = LineString([u_point, v_point])

    if not boundary_box.contains(u_point) and not boundary_box.contains(v_point):
        nodes_to_remove.add(u)
        nodes_to_remove.add(v)
        continue

    # Проверяем, пересекает ли ребро границу
    if not boundary_box.contains(u_point) or not boundary_box.contains(v_point):
        intersection = boundary_box.intersection(edge_line)

        if not intersection.is_empty:
            if (isinstance(intersection, Point)):
                intersection_point = intersection
            elif (isinstance(intersection, LineString)):
                vertex = intersection.intersection(edge_line)
                intersection_points = [Point(pt) for pt in intersection.coords]
                if (vertex == intersection_points[0]):
                    intersection_point = intersection_points[1]
                else:
                    intersection_point = intersection_points[0]

            new_node_id = None
            for node_id, data in G.nodes(data=True):
                if Point(data['x'], data['y']).equals(intersection_point):
                    new_node_id = node_id
                    break

            if new_node_id is None:
                if intersection_point not in nodes_to_add_coords:
                    new_node_id = max_id + 1
                    nodes_to_add.append((new_node_id, intersection_point.x, intersection_point.y))
                    nodes_to_add_coords.add(intersection_point)
                    max_id += 1
                else:
                    for node_id, x, y in nodes_to_add:
                        if Point(x, y).equals(intersection_point):
                            new_node_id = node_id
                            break

            # Заменяем ребро на часть внутри границы
            if boundary_box.contains(u_point):
                nodes_to_remove.add(v)
                edges_to_add.append((u, new_node_id, {"length": u_point.distance(intersection_point)}))
            elif boundary_box.contains(v_point):
                nodes_to_remove.add(u)
                edges_to_add.append((new_node_id, v, {"length": v_point.distance(intersection_point)}))

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

for i in range(len(nodes_to_add)):
    node1 = nodes_to_add[i][0]
    node2 = nodes_to_add[(i + 1) % len(nodes_to_add)][0]
    distance = geodesic((nodes_to_add[i][1], nodes_to_add[i][2]), (nodes_to_add[(i + 1) % len(nodes_to_add)][1], nodes_to_add[(i + 1) % len(nodes_to_add)][2])).meters
    G.add_edge(node1, node2, length=distance)

file = open("graph_" + str(center_lat) + "_" + str(center_lon) + "_" + str(d) + ".txt", "w")
file.write(str(G.number_of_nodes()) + " ")
file.write('\n')

for line in nx.generate_adjlist(G):
    word_list = line.split()
    size = len(word_list) - 1
    first = True
    node = 0
    for num in word_list:
        file.write(num + " " + str(G.nodes[int(num)]["x"]).replace('.', ',') + " " + str(G.nodes[int(num)]["y"]).replace('.', ',') + " ")
        if first:
            file.write(str(size).replace('.', ',') + " ")
            node = int(num)
            first = False
        else:
            file.write(str(G.get_edge_data(node, int(num), 0)["length"]).replace('.', ',') + " ")

    file.write('\n')

file.close()

