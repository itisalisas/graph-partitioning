import folium
import pandas as pd
import os
import sys
from matplotlib import colors as mcolors
from matplotlib import pyplot as plt

def load_vertex_data(file_path):
    with open(file_path, 'r') as file:
        lines = file.readlines()
        num_vertices = int(lines[0].strip())
        data = [[int(parts[0]), float(parts[1].replace(',', '.')), float(parts[2].replace(',', '.'))]
                for line in lines[1:num_vertices + 1]
                for parts in [line.strip().split()]]
    return pd.DataFrame(data, columns=['id', 'longitude', 'latitude'])

def generate_colors(n):
    cmap = plt.get_cmap('RdYlGn')
    return [mcolors.rgb2hex(cmap(i / (n - 1))) for i in range(n)]

if len(sys.argv) != 3:
    print("Usage: python script.py <directory_path> <output_file.html>")
    sys.exit(1)

directory_name = sys.argv[1]
output_file = sys.argv[2]

script_dir = os.path.dirname(os.path.abspath(__file__))
directory_path = os.path.join(script_dir, '..', 'main', 'output', directory_name)

if not os.path.exists(directory_path):
    print(f"Directory {directory_path} does not exist.")
    sys.exit(1)

file_paths = [os.path.join(directory_path, f) for f in os.listdir(directory_path)
              if f.startswith("partition_") and f.endswith(".txt")]

colors = generate_colors(len(file_paths))

m = folium.Map()

for i, file_path in enumerate(file_paths):
    vertices = load_vertex_data(file_path)
    color = colors[i % len(colors)]
    for _, row in vertices.iterrows():
        folium.CircleMarker(
            location=(row["latitude"], row["longitude"]),
            radius=3,
            color=color,
            fill=True,
            fill_color=color
        ).add_to(m)

m.save(os.path.join(directory_path, output_file))
print(f"Map saved to {output_file}")
