import os
import json
import pandas as pd
import numpy as np
import argparse
from collections import defaultdict

parser = argparse.ArgumentParser(description='Calculate statistics for graph partitioning experiments')
parser.add_argument('experiment_dir', type=str, help='Name of experiment directory (e.g., res-09-05-only-reif-coef-01)')
args = parser.parse_args()

base_dir = "src/main/output"
experiment_path = os.path.join(base_dir, args.experiment_dir)
data_root = "src/main/resources/data"

if not os.path.exists(experiment_path):
    print(f"Error: Experiment directory not found: {experiment_path}")
    exit(1)

cities = [d for d in os.listdir(experiment_path) if os.path.isdir(os.path.join(experiment_path, d))]
sizes = ["1000", "2000"]
weights = [10000, 20000]


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
            dir_path = os.path.join(experiment_path, city, size, f"max_weight_{weight}")
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
                    "vertex_count": data.get("dualVertexNumber", None),
                    "graph_weight": data.get("totalGraphWeight", None),
                    "weight": weight,
                    "time": data["partitionTime(s)"],
                    "memory": data["usedMemory(MB)"],
                    "regions": data["regionCount"],
                    "optimal_regions": data["minRegionCountEstimate"],
                    "region_ratio": region_ratio,
                    "avg_load": avg_load,
                    "time_per_edge": time_per_edge,
                    "weight_variance": data.get("weightVariance", None),
                    "cut_length": data.get("totalBoundaryLength", None)
                }
                all_data.append(record)
                city_stats[(city, weight)].append(record)

            except Exception as e:
                print(f"Error processing {file_path}: {str(e)}")

df = pd.DataFrame(all_data)

if df.empty:
    print("No data found for statistics")
    exit(0)

# Prepare consolidated output
output_lines = []
output_lines.append(f"Experiment: {args.experiment_dir}\n")
output_lines.append("="*80 + "\n")

for (city, weight), records in sorted(city_stats.items()):
    city_df = pd.DataFrame(records).sort_values('size')

    result_df = pd.DataFrame({
        'Size': city_df['size'],
        'Vertices': city_df['vertex_count'],
        'Graph Edges': city_df['graph_size'],
        'Graph Weight': city_df['graph_weight'].round(1),
        'Time (s)': city_df['time'].round(3),
        'Memory (MB)': city_df['memory'],
        'Regions': city_df['regions'],
        'Regions/Optimal': city_df['region_ratio'].round(3),
        'Avg Load': city_df['avg_load'].round(3),
        'Weight Var': city_df['weight_variance'].round(1),
        'Cut Length': city_df['cut_length'].round(1)
    })

    output_lines.append(f"\nCity: {city}, Max Weight: {weight}\n")
    output_lines.append(result_df.to_string(index=False) + "\n")
    output_lines.append("\n" + "="*80 + "\n")

# Save to single file in base_dir
output_filename = os.path.join(experiment_path, f"{args.experiment_dir}_statistics.txt")
with open(output_filename, 'w') as f:
    f.writelines(output_lines)

print(f"Statistics saved to: {output_filename}")
print(f"Processed {len(all_data)} experiments across {len(cities)} cities")

# Print to console as well
for line in output_lines:
    print(line, end='')