import os
import subprocess
import shutil
from pathlib import Path
import traceback

algorithm = "IF"
max_sum_vertices_weight = ["5000", "10000"]
max_region_radius_meters = "1000"
data_root = "src/main/resources/data"
data_relative_root = "data"
out_dir = "src/main/output/res"
out_dir_base = "res"
visualization_script = "src/scripts/layers_visualizer.py"


Path(out_dir).mkdir(parents=True, exist_ok=True)

visualization_errors_log = os.path.join(out_dir, "visualization_errors.log")
with open(visualization_errors_log, "w") as log:
    log.write("Visualization Errors Log\n\n")

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
            output_dir = os.path.join(out_dir_base, city, size, weight_dir)
            full_output_dir = os.path.join("src", "main", "output", output_dir)

            map_output = os.path.join(full_output_dir, "map.html")
            if os.path.exists(map_output):
                print(f"Skipping {city}/{size}/{weight_dir} - map.html already exists")
                continue

            if os.path.exists(full_output_dir):
                print(f"Cleaning existing directory {full_output_dir}")
                try:
                    shutil.rmtree(full_output_dir)
                    os.makedirs(full_output_dir, exist_ok=True)
                except Exception as e:
                    print(f"Failed to clean directory {full_output_dir}: {e}")
                    with open(visualization_errors_log, "a") as err_log:
                        err_log.write(f"Directory cleanup error for {city}/{size}/{weight_dir}: {e}\n{traceback.format_exc()}\n\n")
                    continue

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
            java_log_file = os.path.join(log_dir, "run.log")

            java_success = False
            with open(java_log_file, "w") as log:
                try:
                    print(f"\n{'='*50}")
                    print(f"Starting Java for {city}/{size}/{weight_dir}...")
                    print(f"Command: {' '.join(cmd)}")

                    result = subprocess.run(
                        cmd,
                        stdout=log,
                        stderr=subprocess.STDOUT,
                        text=True,
                        check=False
                    )

                    if result.returncode == 0:
                        print(f"Java completed successfully for {city}/{size}/{weight_dir}")
                        java_success = True
                    else:
                        print(f"Java failed with code {result.returncode} for {city}/{size}/{weight_dir}")

                except Exception as e:
                    log.write(f"\n\nExecution error: {e}\n")
                    print(f"Error in Java for {city}/{size}/{weight_dir}: {e}")
                    with open(visualization_errors_log, "a") as err_log:
                        err_log.write(f"Java error for {city}/{size}/{weight_dir}: {e}\n{traceback.format_exc()}\n\n")

            if java_success:
                try:
                    print(f"\nStarting visualization for {city}/{size}/{weight_dir}...")

                    bounds_dir = os.path.join(output_dir)

                    map_output = os.path.join("src", "main", "output", bounds_dir, "map.html")

                    viz_cmd = [
                        "python3", visualization_script,
                        "--graph", graph_path,
                        "--bounds", bounds_dir,
                        "--points", points_path,
                        "--output", map_output
                    ]

                    print(f"Visualization command: {' '.join(viz_cmd)}")

                    viz_result = subprocess.run(
                        viz_cmd,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,
                        text=True,
                        check=True
                    )

                    viz_log_file = os.path.join("src", "main", "output", bounds_dir, "visualization.log")
                    with open(viz_log_file, "w") as log:
                        log.write("=== STDOUT ===\n")
                        log.write(viz_result.stdout)
                        log.write("\n=== STDERR ===\n")
                        log.write(viz_result.stderr)

                    print(f"Visualization completed for {city}/{size}/{weight_dir}")
                    print(f"Map saved to: {map_output}")

                except subprocess.CalledProcessError as e:
                    error_msg = f"Visualization failed for {city}/{size}/{weight_dir} with code {e.returncode}"
                    print(error_msg)
                    with open(visualization_errors_log, "a") as err_log:
                        err_log.write(f"{error_msg}\n")
                        err_log.write(f"STDOUT:\n{e.stdout}\n")
                        err_log.write(f"STDERR:\n{e.stderr}\n\n")

                except Exception as e:
                    error_msg = f"Visualization error for {city}/{size}/{weight_dir}: {str(e)}"
                    print(error_msg)
                    with open(visualization_errors_log, "a") as err_log:
                        err_log.write(f"{error_msg}\n{traceback.format_exc()}\n\n")
            else:
                print(f"Skipping visualization for {city}/{size}/{weight_dir} due to Java failure")

            print(f"Completed {city}/{size}/{weight_dir}")
            print(f"{'='*50}\n")

print("All processing completed.")
print(f"Visualization errors logged to: {visualization_errors_log}")