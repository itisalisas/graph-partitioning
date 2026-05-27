#!/usr/bin/env python3
"""
Run multiple experiments with different algorithm and parameter combinations.

Algorithms:
  BUP  — no parameter sweep, uses defaults
  DIF  — sweep starting-weight-ratio: 0.1, 0.2, 0.3, 0.4
  RIF  — sweep starting-weight-ratio × length-priority × --binary (all combos)

Output layout:
  src/main/output/results_v{N}/
    {ALGORITHM}/
      {city}_{size}_{maxSumVerticesWeight}_{partitionParameter}_{lengthPriority}_{useBinarySearch}/
        partition_info.json  (written by Java)
        ...

Version N is determined automatically as max(existing versions) + 1.
Already-completed combinations (partition_info.json present) are skipped.
"""
import subprocess
import sys
import os
import re
import argparse
import urllib.request
import urllib.parse
import json as _json
from datetime import datetime
from pathlib import Path
from itertools import product

# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------
parser = argparse.ArgumentParser(description='Run all experiment combinations')
parser.add_argument('--workers', type=int, default=4,
                    help='Number of parallel workers per experiment (default: 4)')
parser.add_argument('--resume-version', type=int, default=None,
                    help='Reuse existing results_vN directory and run only missing experiments')
args = parser.parse_args()

# ---------------------------------------------------------------------------
# Fixed configuration
# ---------------------------------------------------------------------------
BASE_OUTPUT_DIR = "src/main/output"
RESULTS_PREFIX = "results_v"

WEIGHTS = ['10000', '15000', '20000']
num_workers = args.workers

run_script = 'src/scripts/experiments/run_test_datasets.py'
calc_stats_script = 'src/scripts/experiments/calculate_statistic.py'

# Default parameter values (must match Main.java @Option defaultValues)
DEFAULT_STARTING_WEIGHT_RATIO = '0.25'
DEFAULT_LENGTH_PRIORITY = '0.5'
DEFAULT_BINARY = False

# ---------------------------------------------------------------------------
# Parameter matrix per algorithm
# ---------------------------------------------------------------------------
# Each entry: (algorithm, starting_weight_ratio, length_priority, use_binary)
def build_experiment_matrix():
    combos = []

    # BUP — single run with all defaults
    combos.append(('BUS', DEFAULT_STARTING_WEIGHT_RATIO, DEFAULT_LENGTH_PRIORITY, False))

    # DIF — sweep starting-weight-ratio only
    for swr in ['0.1', '0.2', '0.3', '0.4']:
        combos.append(('DIF', swr, DEFAULT_LENGTH_PRIORITY, False))

    #for swr in ['0.1', '0.2', '0.3', '0.4']:
    #    combos.append(('IF', swr, DEFAULT_LENGTH_PRIORITY, False))

    # RIF — full cross-product
    for swr, lp, binary in product(
        ['0.1', '0.2', '0.3', '0.4'],
        ['0.0', '0.25', '0.5', '0.75', '1.0'],
        [True, False],
    ):
        combos.append(('RIF', swr, lp, binary))

    return combos

# ---------------------------------------------------------------------------
# Version detection
# ---------------------------------------------------------------------------
def detect_next_version(base_output_dir: str) -> int:
    """Return max existing results_vN version + 1 (starts at 1 if none exist)."""
    base = Path(base_output_dir)
    if not base.exists():
        return 1
    max_ver = 0
    pattern = re.compile(r'^results_v(\d+)$')
    for entry in base.iterdir():
        if entry.is_dir():
            m = pattern.match(entry.name)
            if m:
                max_ver = max(max_ver, int(m.group(1)))
    return max_ver + 1

# ---------------------------------------------------------------------------
# Skip-detection helpers
# ---------------------------------------------------------------------------
def result_dir_for(version_dir: str, algorithm: str,
                   city: str, size: str, weight: str,
                   swr: str, lp: str, binary: bool) -> Path:
    """Return the expected leaf output directory for one city/size/weight combo."""
    binary_str = 'true' if binary else 'false'
    leaf = f"{city}_{size}_{weight}_{swr}_{lp}_{binary_str}"
    return Path(version_dir) / algorithm / leaf


def combo_is_done(version_dir: str, algorithm: str,
                  swr: str, lp: str, binary: bool,
                  data_root: str = "src/main/resources/data") -> bool:
    """
    Return True only if ALL city/size/weight combinations for this parameter
    combo already have a partition_info.json.
    """
    data_path = Path(data_root)
    if not data_path.exists():
        return False
    for city in data_path.iterdir():
        if not city.is_dir():
            continue
        for size_dir in city.iterdir():
            if not size_dir.is_dir():
                continue
            size = size_dir.name
            for weight in WEIGHTS:
                leaf = result_dir_for(version_dir, algorithm,
                                      city.name, size, weight,
                                      swr, lp, binary)
                if not (leaf / "partition_info.json").exists():
                    return False
    return True

# ---------------------------------------------------------------------------
# Run one experiment combo
# ---------------------------------------------------------------------------
def run_experiment(version_dir: str, algorithm: str,
                   swr: str, lp: str, binary: bool) -> tuple[bool, str]:
    """Invoke run_test_datasets.py for one parameter combination."""
    binary_str = 'true' if binary else 'false'
    label = f"{algorithm}_swr{swr}_lp{lp}_bin{binary_str}"

    print("\n" + "=" * 80)
    print(f"Starting: {label}")
    print(f"  algorithm={algorithm}  starting-weight-ratio={swr}"
          f"  length-priority={lp}  binary={binary_str}")
    print(f"  output base: {version_dir}/{algorithm}/")
    print(f"  Timestamp: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 80 + "\n")

    cmd = [
        sys.executable,
        run_script,
        '--algorithm', algorithm,
        '--starting-weight-ratio', swr,
        '--length-priority', lp,
        '--workers', str(num_workers),
        '--weights', *WEIGHTS,
        '--output-base', os.path.join(version_dir, algorithm),
    ]
    if binary:
        cmd.append('--binary')

    print(f"Command: {' '.join(cmd)}\n")

    try:
        subprocess.run(cmd, check=True, text=True)
        print(f"\n✓ Experiment {label} completed successfully")
        return True, label
    except subprocess.CalledProcessError as e:
        print(f"\n✗ Experiment {label} failed (return code {e.returncode})")
        return False, label
    except Exception as e:
        print(f"\n✗ Experiment {label} failed: {e}")
        return False, label


# ---------------------------------------------------------------------------
# Calculate statistics for one algorithm directory
# ---------------------------------------------------------------------------
def run_statistics(version_dir: str, algorithm: str) -> bool:
    algo_dir = os.path.join(version_dir, algorithm)
    print(f"\n{'=' * 80}")
    print(f"Calculating statistics for: {algo_dir}")
    print(f"{'=' * 80}\n")

    stats_cmd = [sys.executable, calc_stats_script, algo_dir]
    print(f"Statistics command: {' '.join(stats_cmd)}\n")

    try:
        result = subprocess.run(stats_cmd, check=True, text=True, capture_output=True)
        print(result.stdout)
        print(f"✓ Statistics calculated for {algo_dir}")
        return True
    except subprocess.CalledProcessError as e:
        print(f"✗ Statistics failed for {algo_dir} (return code {e.returncode})")
        if e.stderr:
            print(f"Error: {e.stderr}")
        return False
    except Exception as e:
        print(f"✗ Statistics failed for {algo_dir}: {e}")
        return False


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    matrix = build_experiment_matrix()
    if args.resume_version is not None:
        version = args.resume_version
        version_dir = os.path.join(BASE_OUTPUT_DIR, f"{RESULTS_PREFIX}{version}")
        if not Path(version_dir).exists():
            print(f"Error: requested resume directory does not exist: {version_dir}", file=sys.stderr)
            return 1
    else:
        version = detect_next_version(BASE_OUTPUT_DIR)
        version_dir = os.path.join(BASE_OUTPUT_DIR, f"{RESULTS_PREFIX}{version}")

    print("\n" + "=" * 80)
    print("BATCH EXPERIMENT RUNNER")
    print("=" * 80)
    print(f"Mode            : {'resume existing version' if args.resume_version is not None else 'create new version'}")
    print(f"Output directory : {version_dir}")
    print(f"Total combos     : {len(matrix)}")
    print(f"Parallel workers : {num_workers}")
    print(f"Weights          : {', '.join(WEIGHTS)}")
    print("=" * 80)

    results = []
    start_time = datetime.now()

    for algorithm, swr, lp, binary in matrix:
        binary_str = 'true' if binary else 'false'
        label = f"{algorithm}_swr{swr}_lp{lp}_bin{binary_str}"

        # Skip if already fully done
        if combo_is_done(version_dir, algorithm, swr, lp, binary):
            print(f"\n[SKIP] {label} — all results already present")
            results.append((label, 'skipped'))
            continue

        success, lbl = run_experiment(version_dir, algorithm, swr, lp, binary)
        results.append((lbl, 'success' if success else 'failed'))

    # Statistics — one call per algorithm that had at least one run
    ran_algorithms = {alg for alg, swr, lp, binary in matrix}
    for algorithm in sorted(ran_algorithms):
        algo_dir = Path(version_dir) / algorithm
        if algo_dir.exists():
            run_statistics(version_dir, algorithm)

    # Summary
    end_time = datetime.now()
    duration = end_time - start_time

    print("\n" + "=" * 80)
    print("EXPERIMENT SUMMARY")
    print("=" * 80)
    print(f"Total time : {duration}")
    print(f"Output dir : {version_dir}")
    print(f"\nResults:")

    counts = {'success': 0, 'failed': 0, 'skipped': 0}
    for lbl, status in results:
        icon = {'success': '✓', 'failed': '✗', 'skipped': '–'}.get(status, '?')
        print(f"  {icon} {lbl:50s}  {status}")
        counts[status] = counts.get(status, 0) + 1

    print(f"\nTotal   : {len(results)}")
    print(f"Success : {counts.get('success', 0)}")
    print(f"Skipped : {counts.get('skipped', 0)}")
    print(f"Failed  : {counts.get('failed', 0)}")
    print("=" * 80 + "\n")

    # ---------------------------------------------------------------------------
    # Telegram notification
    # ---------------------------------------------------------------------------
    _send_telegram_notification(version_dir, duration, counts)

    return 0 if counts.get('failed', 0) == 0 else 1


# ---------------------------------------------------------------------------
# Telegram helpers
# ---------------------------------------------------------------------------
_TG_TOKEN   = "6837430956:AAFEALIwOxi5Xd_bnM9tflIoA9-MrsInqIc"
_TG_CHAT_ID = "780121928"


def _send_telegram_notification(version_dir: str, duration, counts: dict):
    """Send a brief experiment-completion summary to Telegram."""
    total   = sum(counts.values())
    success = counts.get('success', 0)
    skipped = counts.get('skipped', 0)
    failed  = counts.get('failed', 0)

    text = (
        "✅ *Эксперименты завершены*\n\n"
        f"📁 Директория: `{version_dir}`\n"
        f"⏱ Время: {str(duration).split('.')[0]}\n\n"
        f"📊 Результаты:\n"
        f"  ✓ Успешно: {success}\n"
        f"  – Пропущено: {skipped}\n"
        f"  ✗ Ошибок: {failed}\n"
        f"  Всего: {total}"
    )

    url = f"https://api.telegram.org/bot{_TG_TOKEN}/sendMessage"
    payload = _json.dumps({
        "chat_id":    _TG_CHAT_ID,
        "text":       text,
        "parse_mode": "Markdown",
    }).encode("utf-8")

    try:
        req = urllib.request.Request(
            url,
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=15) as resp:
            if resp.status == 200:
                print("✓ Telegram notification sent")
            else:
                print(f"✗ Telegram notification failed: HTTP {resp.status}")
    except Exception as e:
        print(f"✗ Telegram notification error: {e}")


if __name__ == '__main__':
    sys.exit(main())
