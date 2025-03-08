import os
import sys

import folium
from natsort import natsorted
from graph_visualizer import visualize_graph
from bound_visualizer import visualize_bounds

# Функция для загрузки рёбер
def load_edges_data(file_path):
    with open(file_path, 'r') as file:
        lines = file.readlines()
        data = []
        for line in lines:
            parts = line.strip().split()
            lat1, lon1, lat2, lon2 = map(float, parts)
            data.append((lat1, lon1, lat2, lon2))
    return data

# Функция для визуализации рёбер (стрелок)
def visualize_edges(edges_data, map_osm):
    for lat1, lon1, lat2, lon2 in edges_data:
        # Добавляем линию между вершинами (ребро)
        folium.PolyLine(
            locations=[(lon1, lat1), (lon2, lat2)],
            color='black',
            weight=2.5,
            opacity=1
        ).add_to(map_osm)

        # Добавляем стрелку, представляющую направление рёбер
        folium.RegularPolygonMarker(
            location=(lon2, lat2),
            radius=4,
            color="black"
        ).add_to(map_osm)

# Основная функция
def main(graph_file, bounds_directory, output_directory):
    # Для каждой директории removing_N создаем свой файл
    script_dir = os.path.dirname(os.path.abspath(__file__))
    directory_path = os.path.join(script_dir, '..', 'main', 'output', bounds_directory)
    for remove_dir in os.listdir(directory_path):
        if remove_dir.startswith("removing_"):
            # Визуализируем изначальный граф
            map_osm = visualize_graph(graph_file)
            # Визуализируем границы разбиения
            visualize_bounds(os.path.join(bounds_directory, remove_dir), map_osm)
            edges_file_path = os.path.join(directory_path, remove_dir, 'edges.txt')
            if os.path.exists(edges_file_path):
                edges_data = load_edges_data(edges_file_path)
                visualize_edges(edges_data, map_osm)
                # Сохраняем результат в директорию
                if not os.path.exists(os.path.join(directory_path, output_directory)):
                    os.makedirs(os.path.join(directory_path, output_directory))
                    print(f"Directory {bounds_directory} created.")
                output_file = os.path.join(directory_path, output_directory, f"{remove_dir}.html")
                map_osm.save(output_file)
                print(f"Map with edges saved to {output_file}")
            else:
                print(f"No edges file found in {remove_dir}, skipping...")

# изначальный граф
# покрасить цветами текущее разбиение
# ребра от регионов которые перемещали к вершинам к которым прилепили

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python edge_visualizer.py <graph_file> <bounds_directory>")
        sys.exit(1)

    graph_file = sys.argv[1]
    bounds_directory = sys.argv[2]

    main(graph_file, bounds_directory, "removing_viz")