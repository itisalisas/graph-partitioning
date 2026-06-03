import sys
import os
import subprocess
import shutil
from pathlib import Path
import traceback
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from threading import Lock
from datetime import datetime

parser = argparse.ArgumentParser(description='Run graph partitioning experiments on test datasets')
parser.add_argument('--algorithm', type=str, default='IF',
                    help='Algorithm to use (default: IF)')
parser.add_argument('--weights', type=str, nargs='+', default=['10000', '20000'],
                    help='Max weight values (default: 10000 20000)')
parser.add_argument('--starting-weight-ratio', '--coef', dest='coef', type=str, default='0.25',
                    help='Starting weight ratio / partition parameter (default: 0.25)')
parser.add_argument('--length-priority', type=str, default='0.5',
                    help='LENGTH_PRIORITY parameter (default: 0.5)')
parser.add_argument('--binary', action='store_true', default=False,
                    help='Use binary search (flag, default: false)')
parser.add_argument('--cutted-reif', action='store_true', default=False,
                    help='Use cutted Reif algorithm (single SPT from boundary segment, flag, default: false)')
parser.add_argument('--workers', type=int, default=1,
                    help='Number of parallel workers (default: 1)')
parser.add_argument('--no-post-processing', action='store_true', default=False,
                    help='Disable Java post-processing/rebalancing stage')
parser.add_argument('--output-base', type=str, default=None,
                    help='Base output directory for results '
                         '(default: src/main/output/<legacy-experiment-dir>). '
                         'When set, leaf dirs are named '
                         '{city}_{size}_{weight}_{coef}_{lp}_{binary}.')
# Legacy positional argument kept for backward compatibility (optional)
parser.add_argument('experiment_dir', type=str, nargs='?', default=None,
                    help='[Legacy] Name of experiment directory under src/main/output/')
args = parser.parse_args()

algorithm = args.algorithm
max_sum_vertices_weight = args.weights
coef = args.coef
length_priority = args.length_priority
use_binary = args.binary
use_cutted_reif = args.cutted_reif
num_workers = args.workers
no_post_processing = args.no_post_processing

max_region_radius_meters = "1000"
data_root = "src/main/resources/data"
data_relative_root = "data"
# visualization_script = "src/scripts/layers_visualizer.py"

# ---------------------------------------------------------------------------
# Determine output base directory
# ---------------------------------------------------------------------------
if args.output_base is not None:
    # New-style: caller supplies the full base path (e.g. results_v3/DIF)
    full_output_base = args.output_base          # absolute or relative path
    use_new_naming = True
else:
    # Legacy: positional experiment_dir under src/main/output/
    exp_dir_name = args.experiment_dir or "experiment"
    full_output_base = os.path.join("src", "main", "output", exp_dir_name)
    use_new_naming = False

Path(full_output_base).mkdir(parents=True, exist_ok=True)

visualization_errors_log = os.path.join(full_output_base, "visualization_errors.log")
with open(visualization_errors_log, "w") as log:
    log.write("Visualization Errors Log\n\n")

# Thread-safe print
print_lock = Lock()
errors_log_lock = Lock()


def safe_print(*a, **kw):
    with print_lock:
        print(*a, **kw)


def log_error(message):
    with errors_log_lock:
        with open(visualization_errors_log, "a") as err_log:
            err_log.write(message)


# ---------------------------------------------------------------------------
# Leaf directory name
# ---------------------------------------------------------------------------
def leaf_dir_name(city: str, size: str, weight: str) -> str:
    """Return the leaf directory name for one city/size/weight combination."""
    if use_new_naming:
        binary_str = 'true' if use_binary else 'false'
        cutted_str = 'true' if use_cutted_reif else 'false'
        return f"{city}_{size}_{weight}_{coef}_{length_priority}_{binary_str}"
    else:
        # Legacy naming kept for backward compatibility
        return f"max_weight_{weight}"


def output_dir_for(city: str, size: str, weight: str) -> str:
    """
    Return the output directory path relative to the project root
    (i.e. what Java receives as pathToResultDirectory, which it prepends
    with src/main/output/).

    New-style: full_output_base already contains src/main/output prefix,
               so we strip it to get the relative part for Java.
    Legacy:    same as before — experiment_dir/city/size/max_weight_W
    """
    leaf = leaf_dir_name(city, size, weight)
    if use_new_naming:
        # full_output_base = "src/main/output/results_vN/ALGO"
        # Java prepends OUTPUT_DIRECTORY = "src/main/output/"
        # so pathToResultDirectory must be "results_vN/ALGO/leaf"
        rel = os.path.relpath(
            os.path.join(full_output_base, leaf),
            os.path.join("src", "main", "output")
        )
        return rel
    else:
        exp_dir_name = args.experiment_dir or "experiment"
        return os.path.join(exp_dir_name, city, size, leaf)


# ---------------------------------------------------------------------------
# Single experiment runner
# ---------------------------------------------------------------------------
def run_single_experiment(task):
    """
    Run a single experiment (city/size/weight combination).

    Args:
        task: tuple of (city, size, weight, graph_path, points_path,
                        graph_file, points_file)

    Returns:
        dict with status information
    """
    city, size, weight, graph_path, points_path, graph_file, points_file = task

    output_dir = output_dir_for(city, size, weight)
    full_output_dir = os.path.join("src", "main", "output", output_dir)

    task_id = f"{city}/{size}/{weight}"

    # Check if already completed
    done_marker = os.path.join(full_output_dir, "partition_info.json")
    if os.path.exists(done_marker):
        safe_print(f"[SKIP] {task_id} — partition_info.json already exists")
        return {"task": task_id, "status": "skipped", "reason": "already_exists"}

    # Clean existing directory
    if os.path.exists(full_output_dir):
        try:
            shutil.rmtree(full_output_dir)
            os.makedirs(full_output_dir, exist_ok=True)
        except Exception as e:
            error_msg = (f"Directory cleanup error for {task_id}: {e}\n"
                         f"{traceback.format_exc()}\n\n")
            log_error(error_msg)
            safe_print(f"[ERROR] {task_id} — failed to clean directory")
            return {"task": task_id, "status": "error", "reason": "cleanup_failed"}

    # Build Java argument string
    binary_flag = " --binary" if use_binary else ""
    cutted_reif_flag = " --cutted-reif" if use_cutted_reif else ""
    java_args = (
        f"{algorithm} "
        f"{graph_path} "
        f"{points_path} "
        f"{weight} "
        f"{max_region_radius_meters} "
        f"{output_dir} "
        f"-s {coef} "
        f"-l {length_priority}"
        f"{binary_flag}"
        f"{cutted_reif_flag}"
        f"{' --no-post-processing' if no_post_processing else ''}"
    )

    gradle_wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    cmd = [gradle_wrapper, "run", f"--args={java_args}"]
    log_dir = full_output_dir
    Path(log_dir).mkdir(parents=True, exist_ok=True)
    java_log_file = os.path.join(log_dir, "run.log")

    # Run Java
    java_success = False
    start_time = datetime.now()
    safe_print(f"[START] {task_id}")

    with open(java_log_file, "w") as log:
        try:
            result = subprocess.run(
                cmd,
                stdout=log,
                stderr=subprocess.STDOUT,
                text=True,
                check=False,
                timeout=600
            )

            if result.returncode == 0:
                java_success = True
                elapsed = (datetime.now() - start_time).total_seconds()
                safe_print(f"[JAVA OK] {task_id} ({elapsed:.1f}s)")
            else:
                safe_print(f"[JAVA FAIL] {task_id} — return code {result.returncode}")

        except subprocess.TimeoutExpired as e:
            log.write("\n\nExecution timed out after 600 seconds\n")
            log_error(f"Java timeout for {task_id}: {e}\n\n")
            safe_print(f"[TIMEOUT] {task_id}")
            return {"task": task_id, "status": "timeout"}

        except Exception as e:
            log.write(f"\n\nExecution error: {e}\n")
            log_error(f"Java error for {task_id}: {e}\n{traceback.format_exc()}\n\n")
            safe_print(f"[ERROR] {task_id} — Java execution error")
            return {"task": task_id, "status": "error", "reason": "java_failed"}

    if java_success:
        return {"task": task_id, "status": "success"}
    return {"task": task_id, "status": "java_failed"}

'''
    # Run visualization if Java succeeded
    if java_success:
        try:
            map_output = os.path.join(full_output_dir, "map.html")

            viz_cmd = [
                sys.executable, visualization_script,
                "--graph", graph_path,
                "--bounds", output_dir,
                "--points", points_path,
                "--output", map_output
            ]

            viz_result = subprocess.run(
                viz_cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=True
            )

            viz_log_file = os.path.join(full_output_dir, "visualization.log")
            with open(viz_log_file, "w") as log:
                log.write("=== STDOUT ===\n")
                log.write(viz_result.stdout)
                log.write("\n=== STDERR ===\n")
                log.write(viz_result.stderr)

            total_elapsed = (datetime.now() - start_time).total_seconds()
            safe_print(f"[COMPLETE] {task_id} ({total_elapsed:.1f}s)")
            return {"task": task_id, "status": "success"}

        except subprocess.CalledProcessError as e:
            error_msg = (f"Visualization failed for {task_id} with code {e.returncode}\n"
                         f"STDOUT:\n{e.stdout}\n"
                         f"STDERR:\n{e.stderr}\n\n")
            log_error(error_msg)
            safe_print(f"[VIZ FAIL] {task_id}")
            return {"task": task_id, "status": "viz_failed"}

        except Exception as e:
            log_error(f"Visualization error for {task_id}: {e}\n{traceback.format_exc()}\n\n")
            safe_print(f"[VIZ ERROR] {task_id}")
            return {"task": task_id, "status": "viz_error"}
    else:
        safe_print(f"[SKIP VIZ] {task_id} — Java failed")
        return {"task": task_id, "status": "java_failed"}
'''

# ---------------------------------------------------------------------------
# Collect tasks
# ---------------------------------------------------------------------------
tasks = []

for city in os.listdir(data_root):
    city_path = os.path.join(data_root, city)
    if not os.path.isdir(city_path):
        continue

    for size in os.listdir(city_path):
        if size == '5000':
            continue
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
            safe_print(f"[SKIP] {city}/{size} — missing files")
            continue

        graph_path = os.path.join(data_relative_root, city, size, graph_file)
        points_path = os.path.join(data_relative_root, city, size, points_file)

        for weight in max_sum_vertices_weight:
            tasks.append((city, size, weight, graph_path, points_path,
                          graph_file, points_file))

# ---------------------------------------------------------------------------
# Print summary
# ---------------------------------------------------------------------------
binary_str = 'true' if use_binary else 'false'
cutted_reif_str = 'true' if use_cutted_reif else 'false'
safe_print("\n" + "=" * 80)
safe_print(f"Output base      : {full_output_base}")
safe_print(f"Algorithm        : {algorithm}")
safe_print(f"starting-weight-ratio : {coef}")
safe_print(f"length-priority  : {length_priority}")
safe_print(f"binary           : {binary_str}")
safe_print(f"cutted-reif      : {cutted_reif_str}")
safe_print(f"post-processing  : {'disabled' if no_post_processing else 'enabled'}")
safe_print(f"Workers          : {num_workers}")
safe_print(f"Total tasks      : {len(tasks)}")
safe_print("=" * 80 + "\n")

# ---------------------------------------------------------------------------
# Execute tasks
# ---------------------------------------------------------------------------
start_time = datetime.now()
results = []

if num_workers == 1:
    for task in tasks:
        results.append(run_single_experiment(task))
else:
    with ThreadPoolExecutor(max_workers=num_workers) as executor:
        future_to_task = {executor.submit(run_single_experiment, t): t for t in tasks}
        for future in as_completed(future_to_task):
            try:
                results.append(future.result())
            except Exception as e:
                t = future_to_task[future]
                task_id = f"{t[0]}/{t[1]}/{t[2]}"
                safe_print(f"[EXCEPTION] {task_id}: {e}")
                results.append({"task": task_id, "status": "exception", "error": str(e)})

# ---------------------------------------------------------------------------
# Final summary
# ---------------------------------------------------------------------------
elapsed_time = datetime.now() - start_time
safe_print("\n" + "=" * 80)
safe_print("EXECUTION SUMMARY")
safe_print("=" * 80)
safe_print(f"Total time   : {elapsed_time}")
safe_print(f"Total tasks  : {len(results)}")

status_counts: dict = {}
for r in results:
    s = r["status"]
    status_counts[s] = status_counts.get(s, 0) + 1

for status, count in sorted(status_counts.items()):
    safe_print(f"  {status}: {count}")

safe_print("=" * 80)
safe_print(f"Visualization errors logged to: {visualization_errors_log}")
