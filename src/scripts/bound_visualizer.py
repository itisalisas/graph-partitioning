import folium
import pandas as pd
import os
import sys
from matplotlib import colors as mcolors
from matplotlib import pyplot as plt

def load_vertex_data(file_path):
    with open(file_path, 'r') as file:
        lines = file.readlines()
        data = [[int(parts[0]), float(parts[1].replace(',', '.')), float(parts[2].replace(',', '.'))]
                for line in lines
                for parts in [line.strip().split()]]
    return pd.DataFrame(data, columns=['id', 'latitude', 'longitude'])

def generate_colors(n):
    cmap = plt.get_cmap('RdYlGn')
    return [mcolors.rgb2hex(cmap(i / (n))) for i in range(n)]

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

file_paths = [os.path.join(directory_path, f) for f in os.listdir(directory_path)
              if f.startswith("bound_") and f.endswith(".txt")]

colors = generate_colors(len(file_paths))

end_bound = os.path.join(directory_path, "end_bound.txt")

m = folium.Map()

color = 'red'
end_color = 'blue'

for i, file_path in enumerate(file_paths):
    vertices = load_vertex_data(file_path)
    points = []
    color = colors[i % len(colors)]
    # Добавление точек на карту
    for _, row in vertices.iterrows():
        folium.CircleMarker(
            location=(row["latitude"], row["longitude"]),
            radius=2,
            color=color,
            fill=True,
            fill_color=color
        ).add_to(m)
        points.append((row["latitude"], row["longitude"]))

    # if (i % 3 == 0):
        # Соединение точек i и i+1, последнюю с первой
    for j in range(len(points)):
        folium.PolyLine([points[j], points[(j + 1) % len(points)]], color=color).add_to(m)

end_bound_vertices = load_vertex_data(end_bound)

end_points = []
for _, row in end_bound_vertices.iterrows():
    folium.CircleMarker(
        location=(row["latitude"], row["longitude"]),
        radius=2,
        color=end_color,
        fill=True,
        fill_color=end_color
    ).add_to(m)
    end_points.append((row["latitude"], row["longitude"]))

for j in range(len(end_points)):
    folium.PolyLine([end_points[j], end_points[(j + 1) % len(end_points)]], color=end_color).add_to(m)

m.save(os.path.join(directory_path, output_file))
print(f"Map saved to {output_file}")
