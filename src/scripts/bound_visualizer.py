import folium
import pandas as pd
import os
import sys

def load_vertex_data(file_path):
    with open(file_path, 'r') as file:
        lines = file.readlines()
        data = [[int(parts[0]), float(parts[1].replace(',', '.')), float(parts[2].replace(',', '.'))]
                for line in lines
                for parts in [line.strip().split()]]
    return pd.DataFrame(data, columns=['id', 'longitude', 'latitude'])

if len(sys.argv) != 3:
    print("Usage: python bound_visualizer.py <directory_path> <output_file.html>")
    sys.exit(1)

directory_name = sys.argv[1]
output_file = sys.argv[2]

script_dir = os.path.dirname(os.path.abspath(__file__))
directory_path = os.path.join(script_dir, '..', 'main', 'output', directory_name)

if not os.path.exists(directory_path):
    print(f"Directory {directory_path} does not exist.")
    sys.exit(1)

file_path = os.path.join(directory_path, 'bound.txt')

if not os.path.exists(file_path):
    print(f"File {file_path} does not exist.")
    sys.exit(1)

vertices = load_vertex_data(file_path)

m = folium.Map()

color = 'red'

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
