import os
import subprocess
from pathlib import Path
import traceback

project_root = Path(__file__).resolve().parent.parent

cities = {
    "novgorod": (58.52112, 31.27529),
    "spb": (59.93893, 30.32268),
    "krasnoyarsk": (56.00864, 92.8683),
    "moscow": (55.75023, 37.61673),
    "arkhangelsk": (64.543, 40.537),
    "yaroslavl": (57.6269, 39.8945),
    "vladivostok": (43.1156, 131.8831),
    "khabarovsk": (48.4855, 135.0798)
}

sizes = [100, 500, 1000, 2000, 5000]

scripts_dir = project_root / "scripts"
data_root = project_root / "main" / "resources" / "data"

data_root.mkdir(parents=True, exist_ok=True)

error_log = data_root / "dataset_errors.log"
with open(error_log, "w") as log:
    log.write("Dataset Generation Errors Log\n\n")

for city, coords in cities.items():
    lat, lon = coords
    for size in sizes:
        city_size_dir = data_root / city / str(size)
        city_size_dir.mkdir(parents=True, exist_ok=True)

        print(f"\n{'='*50}")
        print(f"Processing: {city} ({lat}, {lon}), size {size}")
        print(f"Directory: {city_size_dir}")
        print(f"{'='*50}")

        graph_script_path = scripts_dir / "AdjacencyListFromOSM.py"
        buildings_script_path = scripts_dir / "buildings_from_osm.py"

        if not graph_script_path.exists():
            error_msg = f"ERROR: Graph script not found: {graph_script_path}"
            print(error_msg)
            with open(error_log, "a") as log:
                log.write(f"{error_msg}\n")
            continue

        if not buildings_script_path.exists():
            error_msg = f"ERROR: Buildings script not found: {buildings_script_path}"
            print(error_msg)
            with open(error_log, "a") as log:
                log.write(f"{error_msg}\n")
            continue

        try:
            print(f"Starting graph creation for {city}/{size}...")
            graph_process = subprocess.Popen(
                ["python3", str(graph_script_path)],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                cwd=str(city_size_dir)
            )
            graph_input = f"{lat}\n{lon}\n{size}\nY\n"
            stdout, stderr = graph_process.communicate(input=graph_input)

            with open(city_size_dir / "graph_creation.log", "w") as log:
                log.write(f"=== STDOUT ===\n{stdout}\n")
                log.write(f"\n=== STDERR ===\n{stderr}\n")

            if graph_process.returncode != 0:
                error_msg = f"Graph script failed for {city}/{size} with exit code {graph_process.returncode}"
                print(error_msg)
                with open(error_log, "a") as log:
                    log.write(f"{error_msg}\nSTDERR:\n{stderr}\n\n")
            else:
                print(f"Graph created successfully for {city}/{size}")

        except Exception as e:
            error_msg = f"EXCEPTION in graph script for {city}/{size}: {str(e)}"
            print(error_msg)
            with open(error_log, "a") as log:
                log.write(f"{error_msg}\n{traceback.format_exc()}\n\n")

        try:
            print(f"Starting buildings creation for {city}/{size}...")
            buildings_process = subprocess.Popen(
                ["python3", str(buildings_script_path)],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                cwd=str(city_size_dir))
            buildings_input = f"{lat}\n{lon}\n{size}\n"
            stdout, stderr = buildings_process.communicate(input=buildings_input)

            with open(city_size_dir / "buildings_creation.log", "w") as log:
                log.write(f"=== STDOUT ===\n{stdout}\n")
                log.write(f"\n=== STDERR ===\n{stderr}\n")

            if buildings_process.returncode != 0:
                error_msg = f"Buildings script failed for {city}/{size} with exit code {buildings_process.returncode}"
                print(error_msg)
                with open(error_log, "a") as log:
                    log.write(f"{error_msg}\nSTDERR:\n{stderr}\n\n")
            else:
                print(f"Buildings created successfully for {city}/{size}")

        except Exception as e:
            error_msg = f"EXCEPTION in buildings script for {city}/{size}: {str(e)}"
            print(error_msg)
            with open(error_log, "a") as log:
                log.write(f"{error_msg}\n{traceback.format_exc()}\n\n")

print("\nAll datasets processed! Check error log for details:")
print(error_log)