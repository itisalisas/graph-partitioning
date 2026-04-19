import os
import json
import pandas as pd
import numpy as np
from collections import defaultdict

base_dir = "src/main/output/res"
data_root = "src/main/resources/data"
cities = [d for d in os.listdir(base_dir) if os.path.isdir(os.path.join(base_dir, d)) and d != 'spb']
sizes = ["1000", "2000", "5000"]
weights = [5000, 10000]


def get_graph_size(city, size):
    graph_dir = os.path.join(data_root, city, size)
    graph_files = [f for f in os.listdir(graph_dir) if f.startswith("graph") and f.endswith(".txt")]

    if not graph_files:
        print(f"Graph file not found in {graph_dir}")
        return None

    graph_file = os.path.join(graph_dir, graph_files[0])

    try:
        with open(graph_file, 'r') as f:
            num_edges = int(f.readline().split()[0])
        return num_edges
    except Exception as e:
        print(f"Error reading graph file {graph_file}: {str(e)}")
        return None

all_data = []
city_stats = defaultdict(list)

for city in cities:
    for size in sizes:
        graph_size = get_graph_size(city, size)
        if graph_size is None:
            print(f"Skipping {city}/{size} - graph size not found")
            continue

        for weight in weights:
            dir_path = os.path.join(base_dir, city, size, f"max_weight_{weight}")
            file_path = os.path.join(dir_path, "partition_info.json")

            if not os.path.exists(file_path):
                print(f"Partition info not found: {file_path}")
                continue

            try:
                with open(file_path, 'r') as f:
                    data = json.load(f)

                region_ratio = data["regionCount"] / data["minRegionCountEstimate"]
                avg_load = data["averageWeight"] / weight
                time_per_edge = data["partitionTime(s)"] / graph_size

                record = {
                    "city": city,
                    "size": int(size),
                    "graph_size": graph_size,
                    "weight": weight,
                    "time": data["partitionTime(s)"],
                    "memory": data["usedMemory(MB)"],
                    "regions": data["regionCount"],
                    "optimal_regions": data["minRegionCountEstimate"],
                    "region_ratio": region_ratio,
                    "avg_load": avg_load,
                    "time_per_edge": time_per_edge
                }
                all_data.append(record)
                city_stats[(city, weight)].append(record)

            except Exception as e:
                print(f"Error processing {file_path}: {str(e)}")

df = pd.DataFrame(all_data)

for (city, weight), records in city_stats.items():
    city_df = pd.DataFrame(records).sort_values('size')

    result_df = pd.DataFrame({
        'Size': city_df['size'],
        'Graph Edges': city_df['graph_size'],
        'Time (s)': city_df['time'].round(3),
        'Memory (MB)': city_df['memory'],
        'Regions': city_df['regions'],
        'Regions/Optimal': city_df['region_ratio'].round(3),
        'Avg Load': city_df['avg_load'].round(3)
    })

    filename = f"{city}_max_weight_{weight}.csv"
    result_df.to_csv(filename, index=False)
    print(f"Saved: {filename}")

    print(f"\nCity: {city}, Max Weight: {weight}")
    print(result_df.to_string(index=False))
    print("\n" + "="*80 + "\n")

if not df.empty:
    stats = [
        ("Max Time (s)", df['time'].max()),
        ("Max Memory (MB)", df['memory'].max()),
        ("Avg Time/Edge (s/edge)", df['time_per_edge'].mean()),
        ("Min Regions/Optimal", df['region_ratio'].min()),
        ("Avg Regions/Optimal", df['region_ratio'].mean()),
        ("Max Regions/Optimal", df['region_ratio'].max()),
    ]

    for weight in weights:
        weight_data = df[df['weight'] == weight]
        if not weight_data.empty:
            stats.append((f"Avg Load (max_weight={weight})", weight_data['avg_load'].mean()))

    stats_df = pd.DataFrame(stats, columns=['Metric', 'Value'])
    stats_df['Value'] = stats_df['Value'].round(6)

    stats_df.to_csv("overall_stats.csv", index=False)
    print("Saved: overall_stats.csv")

    print("\nOverall Statistics:")
    print(stats_df.to_string(index=False))
else:
    print("No data found for statistics")