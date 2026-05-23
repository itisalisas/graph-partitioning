#!/usr/bin/env python3
"""
Run multiple experiments with different algorithm and coefficient combinations.
"""
import subprocess
import sys
import argparse
from datetime import datetime

parser = argparse.ArgumentParser(description='Run all experiment combinations')
parser.add_argument('--workers', type=int, default=4, help='Number of parallel workers per experiment (default: 4)')
args = parser.parse_args()

# Configuration
algorithms = ['DIF', 'RIF']
coefficients = ['0.1', '0.2', '0.3', '0.4']
length_priorities = ['0.0', '0.25', '0.5', '0.75', '1.0']
weights = ['10000', '20000']
num_workers = args.workers

run_script = 'src/scripts/experiments/run_test_datasets.py'
calc_stats_script = 'src/scripts/experiments/calculate_statistic.py'
compare_script = 'src/scripts/experiments/compare_rif_dif.py'

def run_experiment(algorithm, coef, length_priority):
    """Run a single experiment with given parameters."""
    # NOTE: Change version suffix (_3) here if needed for new experiment runs
    lp_suffix = length_priority.replace('.', '_')
    experiment_dir = f"{algorithm}_{coef.replace('.', '_')}_lp{lp_suffix}"
    
    print("\n" + "="*80)
    print(f"Starting experiment: {experiment_dir}")
    print(f"Algorithm: {algorithm}, Coefficient: {coef}, LENGTH_PRIORITY: {length_priority}, Workers: {num_workers}")
    print(f"Timestamp: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("="*80 + "\n")
    
    # Run the experiment
    cmd = [
        sys.executable,
        run_script,
        experiment_dir,
        '--algorithm', algorithm,
        '--coef', coef,
        '--length-priority', length_priority,
        '--workers', str(num_workers),
        '--weights'
    ] + weights
    
    print(f"Command: {' '.join(cmd)}\n")
    
    try:
        result = subprocess.run(
            cmd,
            check=True,
            text=True
        )
        print(f"\n✓ Experiment {experiment_dir} completed successfully")
        
        # Calculate statistics for the completed experiment
        print(f"\n{'='*80}")
        print(f"Calculating statistics for: {experiment_dir}")
        print(f"{'='*80}\n")
        
        stats_cmd = [
            sys.executable,
            calc_stats_script,
            experiment_dir
        ]
        
        print(f"Statistics command: {' '.join(stats_cmd)}\n")
        
        stats_result = subprocess.run(
            stats_cmd,
            check=True,
            text=True,
            capture_output=True
        )
        
        print(stats_result.stdout)
        print(f"✓ Statistics calculated for {experiment_dir}")
        
        return (True, experiment_dir)
        
    except subprocess.CalledProcessError as e:
        if 'run_test_datasets' in str(cmd):
            print(f"\n✗ Experiment {experiment_dir} failed with return code {e.returncode}")
        else:
            print(f"\n✗ Statistics calculation for {experiment_dir} failed with return code {e.returncode}")
            if hasattr(e, 'stderr') and e.stderr:
                print(f"Error: {e.stderr}")
        return (False, experiment_dir)
    except Exception as e:
        print(f"\n✗ Experiment {experiment_dir} failed with error: {e}")
        return (False, experiment_dir)

def main():
    """Run all experiment combinations."""
    print("\n" + "="*80)
    print("BATCH EXPERIMENT RUNNER")
    print("="*80)
    print(f"Total experiments: {len(algorithms) * len(coefficients) * len(length_priorities)}")
    print(f"Algorithms: {', '.join(algorithms)}")
    print(f"Coefficients: {', '.join(coefficients)}")
    print(f"LENGTH_PRIORITY: {', '.join(length_priorities)}")
    print(f"Weights: {', '.join(weights)}")
    print(f"Parallel workers per experiment: {num_workers}")
    print("="*80)
    
    results = []
    experiment_dirs = []
    start_time = datetime.now()
    
    for algorithm in algorithms:
        for coef in coefficients:
            for length_priority in length_priorities:
                experiment_name = f"{algorithm}_{coef.replace('.', '_')}_lp{length_priority.replace('.', '_')}"
                success, exp_dir = run_experiment(algorithm, coef, length_priority)
                results.append((experiment_name, success))
                if success:
                    experiment_dirs.append(exp_dir)
    
    end_time = datetime.now()
    duration = end_time - start_time
    
    # Print summary
    print("\n" + "="*80)
    print("EXPERIMENT SUMMARY")
    print("="*80)
    print(f"Total time: {duration}")
    print(f"\nResults:")
    
    successful = 0
    failed = 0
    
    for exp_name, success in results:
        status = "✓ SUCCESS" if success else "✗ FAILED"
        print(f"  {exp_name:15} {status}")
        if success:
            successful += 1
        else:
            failed += 1
    
    print(f"\nTotal: {len(results)} experiments")
    print(f"Successful: {successful}")
    print(f"Failed: {failed}")
    print("="*80 + "\n")
    
    # Generate comparison HTML tables
    if successful > 0:
        print("\n" + "="*80)
        print("GENERATING COMPARISON TABLES")
        print("="*80 + "\n")
        
        # Extract version from the first successful experiment directory name
        version = ""  # Default to empty string (no version suffix)
        if experiment_dirs:
            # Import and use the fixed version extraction function
            import sys
            sys.path.insert(0, 'src/scripts/experiments')
            from compare_rif_dif import extract_version_from_exp_name
            version = extract_version_from_exp_name(experiment_dirs[0])
        
        # version is now either a version number string (e.g. "26") or empty string ""
        version_suffix = f"_v{version}" if version else ""
        version_arg = ['--version', version]  # Always pass version (even if empty string)
        version_msg = f" (version {version})" if version else " (no version suffix)"
        
        print(f"Detected experiment version: {version if version else 'no version suffix'}")
        
        # Generate comparison for each coefficient and length_priority combination (only for sizes 1000 and 2000)
        for coef in coefficients:
            for lp in length_priorities:
                lp_suffix = lp.replace('.', '_')
                output_file = f"src/main/output/comparison_coef_{coef.replace('.', '_')}_lp{lp_suffix}{version_suffix}.html"
                print(f"Generating comparison for coefficient {coef}, LENGTH_PRIORITY {lp} (sizes: 1000, 2000){version_msg}...")
                
                compare_cmd = [
                    sys.executable,
                    compare_script,
                    '--base-dir', 'src/main/output',
                    '--output', output_file,
                    '--coef', coef,
                    '--length-priority', lp,
                    '--sizes', '1000', '2000'
                ] + version_arg
                
                try:
                    result = subprocess.run(
                        compare_cmd,
                        check=True,
                        text=True,
                        capture_output=True
                    )
                    print(result.stdout)
                    print(f"✓ Comparison table generated: {output_file}")
                except subprocess.CalledProcessError as e:
                    print(f"✗ Failed to generate comparison for coefficient {coef}, LENGTH_PRIORITY {lp}")
                    if e.stderr:
                        print(f"Error: {e.stderr}")
        
        # Generate combined comparison with pickers (all coefficients and length_priorities, only sizes 1000 and 2000)
        output_file = f"src/main/output/comparison_all{version_suffix}.html"
        print(f"\nGenerating combined comparison with coefficient and LENGTH_PRIORITY pickers (sizes: 1000, 2000){version_msg}...")
        
        compare_cmd = [
            sys.executable,
            compare_script,
            '--base-dir', 'src/main/output',
            '--output', output_file,
            '--sizes', '1000', '2000'
        ] + version_arg
        
        try:
            result = subprocess.run(
                compare_cmd,
                check=True,
                text=True,
                capture_output=True
            )
            print(result.stdout)
            print(f"✓ Combined comparison table generated: {output_file}")
        except subprocess.CalledProcessError as e:
            print(f"✗ Failed to generate combined comparison")
            if e.stderr:
                print(f"Error: {e.stderr}")
        
        print("="*80 + "\n")
    
    return 0 if failed == 0 else 1

if __name__ == '__main__':
    sys.exit(main())
