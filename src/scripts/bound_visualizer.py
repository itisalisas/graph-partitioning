import folium
import pandas as pd
import os
import sys
from matplotlib import colors as mcolors
from matplotlib import pyplot as plt
from natsort import natsorted

def load_vertex_data(file_path):
    with open(file_path, 'r') as file:
        lines = file.readlines()
        data = [[int(parts[0]), float(parts[1].replace(',', '.')), float(parts[2].replace(',', '.'))]
                for line in lines
                for parts in [line.strip().split()]]
    return pd.DataFrame(data, columns=['id', 'longitude', 'latitude'])

def generate_colors(n):
    cmap = plt.get_cmap('RdYlGn')
    return [mcolors.rgb2hex(cmap(i / (n))) for i in range(n)]

def visualize_bounds(directory_name, map_osm):
    script_dir = os.path.dirname(os.path.abspath(__file__))
    directory_path = os.path.join(script_dir, '..', 'main', 'output', directory_name)
    file_paths = [os.path.join(directory_path, f) for f in os.listdir(directory_path)
                  if f.startswith("bound_") and f.endswith(".txt")]

    file_paths = natsorted(file_paths)

    colors = generate_colors(len(file_paths))

    all_points = []

    # Считываем все точки, чтобы установить диапазон карты
    for file_path in file_paths:
        vertices = load_vertex_data(file_path)
        for _, row in vertices.iterrows():
            all_points.append((row["latitude"], row["longitude"]))

    end_color = 'blue'

    # Наложение границ на карту
    for i, file_path in enumerate(file_paths):
        vertices = load_vertex_data(file_path)
        points = []
        color = colors[i % len(colors)]

        for _, row in vertices.iterrows():
            points.append((row["latitude"], row["longitude"]))

        folium.Polygon(
            locations=points,
            color=color,
            fill=True,
            fill_color=color,
            fill_opacity=0.5,
            tooltip=f"Part {i}"
        ).add_to(map_osm)

    min_lat = min(p[0] for p in all_points)
    max_lat = max(p[0] for p in all_points)
    min_lon = min(p[1] for p in all_points)
    max_lon = max(p[1] for p in all_points)

    map_osm.fit_bounds([[min_lat, min_lon], [max_lat, max_lon]])

def main(bounds_directory, output_file):
    # Сначала визуализируем граф
    map_osm = folium.Map(location=(0, 0), zoom_start=15)

    # Накладываем границы
    visualize_bounds(bounds_directory, map_osm)

    script_dir = os.path.dirname(os.path.abspath(__file__))
    directory_path = os.path.join(script_dir, '..', 'main', 'output', bounds_directory)
    # Сохраняем финальную карту
    map_osm.save(os.path.join(directory_path, output_file))
    print(f"Map with boundaries saved to {output_file}")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python bound_visualizer.py <directory_path> <output_file.html>")
        sys.exit(1)

    directory_name = sys.argv[1]
    output_file = sys.argv[2]

    main(directory_name, output_file)
