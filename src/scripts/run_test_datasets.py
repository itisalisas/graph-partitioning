import os
import subprocess
from pathlib import Path

algorithm = "IF"
max_sum_vertices_weight = ["1000", "5000", "10000", "20000"]
max_region_radius_meters = "1000"
data_root = "src/main/resources/data"
data_relative_root = "data"
output_dir_base = "results"

Path(output_dir_base).mkdir(parents=True, exist_ok=True)

for city in os.listdir(data_root):
    city_path = os.path.join(data_root, city)
    if not os.path.isdir(city_path):
        continue

    for size in os.listdir(city_path):
        size_path = os.path.join(city_path, size)
        if not os.path.isdir(size_path):
            continue

        graph_file = None
        points_file = None

        for file in os.listdir(size_path):
            if file.startswith("graph") and file.endswith(".txt"):
                graph_file = file
            elif file.startswith("buildings") and file.endswith(".txt"):
                points_file = file

        if not graph_file or not points_file:
            print(f"Skipping {city}/{size}: missing files")
            continue

        graph_path = os.path.join(data_relative_root, city, size, graph_file)
        points_path = os.path.join(data_relative_root, city, size, points_file)

        for weight in max_sum_vertices_weight:
            weight_dir = f"max_weight_{weight}"
            output_dir = os.path.join(output_dir_base, city, size, weight_dir)

            Path(output_dir).mkdir(parents=True, exist_ok=True)

            args = (
                f"{algorithm} "
                f"{graph_path} "
                f"{points_path} "
                f"{weight} "
                f"{max_region_radius_meters} "
                f"{output_dir}"
            )

            cmd = ["./gradlew", "run", f"--args={args}"]
            log_dir = os.path.join("src", "main", "output", output_dir)
            Path(log_dir).mkdir(parents=True, exist_ok=True)
            log_file = os.path.join(log_dir, "run.log")

            with open(log_file, "w") as log:
                try:
                    print(f"Starting {city}/{size}/{weight_dir}...")
                    print(f"Command: {' '.join(cmd)}")

                    result = subprocess.run(
                        cmd,
                        stdout=log,
                        stderr=subprocess.STDOUT,
                        text=True,
                        check=False
                    )
                except Exception as e:
                    log.write(f"\n\nExecution error: {e}\n")
                    print(f"Error in {city}/{size}/{weight_dir}: {e}")

            print(f"Completed {city}/{size}/{weight_dir}")

print("All processing completed.")