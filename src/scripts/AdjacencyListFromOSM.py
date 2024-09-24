import osmnx
import networkx

x = 59.93893094417527
y = 30.32268115454809
d = 50 + 200

print("write x coordinate or -:")
inputX = input()
if (inputX != '-'):
    x = inputX
    
print("write y coordinate or -:")
inputY = input()
if (inputY != '-'):
    y = inputY
    
print("write dist or -:")
inputDist = input()
if (inputDist != '-'):
    d = inputDist + 200
    


G = osmnx.graph_from_point((x, y), dist=d)

file = open("graph_" + str(x) + " " +  str(y) + "_" + str(d) + ".txt", "w")
file.write(str(G.number_of_nodes()) + " ")
file.write('\n')
print(G.number_of_nodes())
for line in networkx.generate_adjlist(G):
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
            print(G.get_edge_data(node, int(num),0)["length"])
            file.write(str(G.get_edge_data(node, int(num),0)["length"]).replace('.', ',') + " ")
    
    file.write('\n')

file.close()
            
        
