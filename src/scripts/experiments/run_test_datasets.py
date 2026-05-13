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
parser.add_argument('experiment_dir', type=str, help='Name of experiment directory (e.g., res-09-05-only-dinic-coef-01)')
parser.add_argument('--algorithm', type=str, default='IF', help='Algorithm to use (default: IF)')
parser.add_argument('--weights', type=str, nargs='+', default=['10000', '20000'], help='Max weight values (default: 10000 20000)')
parser.add_argument('--coef', type=str, default='0.1', help='Coefficient parameter (default: 0.1)')
parser.add_argument('--workers', type=int, default=1, help='Number of parallel workers (default: 1)')
args = parser.parse_args()

algorithm = args.algorithm
max_sum_vertices_weight = args.weights
coef = args.coef
num_workers = args.workers

max_region_radius_meters = "1000"
data_root = "src/main/resources/data"
data_relative_root = "data"
base_output_dir = "src/main/output"
out_dir = os.path.join(base_output_dir, args.experiment_dir)
out_dir_base = args.experiment_dir
visualization_script = "src/scripts/layers_visualizer.py"

Path(out_dir).mkdir(parents=True, exist_ok=True)

visualization_errors_log = os.path.join(out_dir, "visualization_errors.log")
with open(visualization_errors_log, "w") as log:
    log.write("Visualization Errors Log\n\n")

# Thread-safe print
print_lock = Lock()
errors_log_lock = Lock()

def safe_print(*args, **kwargs):
    """Thread-safe print function."""
    with print_lock:
        print(*args, **kwargs)

def log_error(message):
    """Thread-safe error logging."""
    with errors_log_lock:
        with open(visualization_errors_log, "a") as err_log:
            err_log.write(message)

def run_single_experiment(task):
    """
    Run a single experiment (city/size/weight combination).
    
    Args:
        task: tuple of (city, size, weight, graph_path, points_path, graph_file, points_file)
    
    Returns:
        dict with status information
    """
    city, size, weight, graph_path, points_path, graph_file, points_file = task
    
    weight_dir = f"max_weight_{weight}"
    output_dir = os.path.join(out_dir_base, city, size, weight_dir)
    full_output_dir = os.path.join("src", "main", "output", output_dir)
    
    task_id = f"{city}/{size}/{weight_dir}"
    
    # Check if already completed
    map_output = os.path.join(full_output_dir, "map.html")
    if os.path.exists(map_output):
        safe_print(f"[SKIP] {task_id} - map.html already exists")
        return {"task": task_id, "status": "skipped", "reason": "already_exists"}
    
    # Clean existing directory
    if os.path.exists(full_output_dir):
        try:
            shutil.rmtree(full_output_dir)
            os.makedirs(full_output_dir, exist_ok=True)
        except Exception as e:
            error_msg = f"Directory cleanup error for {task_id}: {e}\n{traceback.format_exc()}\n\n"
            log_error(error_msg)
            safe_print(f"[ERROR] {task_id} - Failed to clean directory")
            return {"task": task_id, "status": "error", "reason": "cleanup_failed"}
    
    # Prepare arguments
    java_args = (
        f"{algorithm} "
        f"{graph_path} "
        f"{points_path} "
        f"{weight} "
        f"{max_region_radius_meters} "
        f"{output_dir} "
        f"-p {coef}"
    )
    
    gradle_wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    cmd = [gradle_wrapper, "run", f"--args={java_args}"]
    log_dir = os.path.join("src", "main", "output", output_dir)
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
                safe_print(f"[JAVA FAIL] {task_id} - return code {result.returncode}")
                
        except subprocess.TimeoutExpired as e:
            log.write(f"\n\nExecution timed out after 900 seconds\n")
            error_msg = f"Java timeout for {task_id}: {e}\n\n"
            log_error(error_msg)
            safe_print(f"[TIMEOUT] {task_id}")
            return {"task": task_id, "status": "timeout"}
            
        except Exception as e:
            log.write(f"\n\nExecution error: {e}\n")
            error_msg = f"Java error for {task_id}: {e}\n{traceback.format_exc()}\n\n"
            log_error(error_msg)
            safe_print(f"[ERROR] {task_id} - Java execution error")
            return {"task": task_id, "status": "error", "reason": "java_failed"}
    
    # Run visualization if Java succeeded
    if java_success:
        try:
            bounds_dir = os.path.join(output_dir)
            map_output = os.path.join("src", "main", "output", bounds_dir, "map.html")
            
            viz_cmd = [
                sys.executable, visualization_script,
                "--graph", graph_path,
                "--bounds", bounds_dir,
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
            
            viz_log_file = os.path.join("src", "main", "output", bounds_dir, "visualization.log")
            with open(viz_log_file, "w") as log:
                log.write("=== STDOUT ===\n")
                log.write(viz_result.stdout)
                log.write("\n=== STDERR ===\n")
                log.write(viz_result.stderr)
            
            total_elapsed = (datetime.now() - start_time).total_seconds()
            safe_print(f"[COMPLETE] {task_id} ({total_elapsed:.1f}s)")
            return {"task": task_id, "status": "success"}
            
        except subprocess.CalledProcessError as e:
            error_msg = f"Visualization failed for {task_id} with code {e.returncode}\n"
            error_msg += f"STDOUT:\n{e.stdout}\n"
            error_msg += f"STDERR:\n{e.stderr}\n\n"
            log_error(error_msg)
            safe_print(f"[VIZ FAIL] {task_id}")
            return {"task": task_id, "status": "viz_failed"}
            
        except Exception as e:
            error_msg = f"Visualization error for {task_id}: {str(e)}\n{traceback.format_exc()}\n\n"
            log_error(error_msg)
            safe_print(f"[VIZ ERROR] {task_id}")
            return {"task": task_id, "status": "viz_error"}
    else:
        safe_print(f"[SKIP VIZ] {task_id} - Java failed")
        return {"task": task_id, "status": "java_failed"}

# Collect all tasks
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
            safe_print(f"[SKIP] {city}/{size} - missing files")
            continue
        
        graph_path = os.path.join(data_relative_root, city, size, graph_file)
        points_path = os.path.join(data_relative_root, city, size, points_file)
        
        for weight in max_sum_vertices_weight:
            tasks.append((city, size, weight, graph_path, points_path, graph_file, points_file))

# Print summary
safe_print("\n" + "="*80)
safe_print(f"Experiment: {args.experiment_dir}")
safe_print(f"Algorithm: {algorithm}, Coefficient: {coef}")
safe_print(f"Workers: {num_workers}")
safe_print(f"Total tasks: {len(tasks)}")
safe_print("="*80 + "\n")

# Run tasks in parallel
start_time = datetime.now()
results = []

if num_workers == 1:
    # Sequential execution
    for task in tasks:
        result = run_single_experiment(task)
        results.append(result)
else:
    # Parallel execution
    with ThreadPoolExecutor(max_workers=num_workers) as executor:
        future_to_task = {executor.submit(run_single_experiment, task): task for task in tasks}
        
        for future in as_completed(future_to_task):
            try:
                result = future.result()
                results.append(result)
            except Exception as e:
                task = future_to_task[future]
                task_id = f"{task[0]}/{task[1]}/max_weight_{task[2]}"
                safe_print(f"[EXCEPTION] {task_id}: {e}")
                results.append({"task": task_id, "status": "exception", "error": str(e)})

# Print final summary
elapsed_time = datetime.now() - start_time
safe_print("\n" + "="*80)
safe_print("EXECUTION SUMMARY")
safe_print("="*80)
safe_print(f"Total time: {elapsed_time}")
safe_print(f"Total tasks: {len(results)}")

status_counts = {}
for result in results:
    status = result["status"]
    status_counts[status] = status_counts.get(status, 0) + 1

for status, count in sorted(status_counts.items()):
    safe_print(f"  {status}: {count}")

safe_print("="*80)
safe_print(f"Visualization errors logged to: {visualization_errors_log}")
