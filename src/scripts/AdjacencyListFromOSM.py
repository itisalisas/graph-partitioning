import osmnx
import networkx as nx
from shapely.geometry import Point, box

x = 59.93893094417527
y = 30.32268115454809
d = 50
buffer = 200

print("write x coordinate or -:")
inputX = input()
if inputX != '-':
    x = float(inputX)

print("write y coordinate or -:")
inputY = input()
if inputY != '-':
    y = float(inputY)

print("write dist or -:")
inputDist = input()
if inputDist != '-':
    d = int(inputDist)

meters_per_degree_lat = 111_000
meters_per_degree_lon = 67_000

d_lat = d / meters_per_degree_lat
d_lon = d / meters_per_degree_lon
buffer_lat = buffer / meters_per_degree_lat
buffer_lon = buffer / meters_per_degree_lon

d_total = d + buffer
G = osmnx.graph_from_point((x, y), dist=d_total)

boundary_box = box(y - d_lon, x - d_lat, y + d_lon, x + d_lat)

nodes_to_remove = [node for node, data in G.nodes(data=True)
                   if not boundary_box.contains(Point(data["x"], data["y"]))]

G.remove_nodes_from(nodes_to_remove)

file = open("graph_" + str(x) + " " + str(y) + "_" + str(d) + ".txt", "w")
file.write(str(G.number_of_nodes()) + " ")
file.write('\n')
print(G.number_of_nodes())

for line in nx.generate_adjlist(G):
    word_list = line.split()
    size = len(word_list) - 1
    first = True
    node = 0
    for num in word_list:
        print(int(num), G.nodes[int(num)]["x"], G.nodes[int(num)]["y"])
        file.write(num + " " + str(G.nodes[int(num)]["x"]).replace('.', ',') + " " + str(G.nodes[int(num)]["y"]).replace('.', ',') + " ")
        if first:
            print(size)
            file.write(str(size).replace('.', ',') + " ")
            node = int(num)
            first = False
        else:
            print(G.get_edge_data(node, int(num), 0)["length"])
            file.write(str(G.get_edge_data(node, int(num), 0)["length"]).replace('.', ',') + " ")

    file.write('\n')

file.close()

