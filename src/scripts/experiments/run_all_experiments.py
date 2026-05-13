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
coefficients = ['0.1', '0.25']
weights = ['10000', '20000']
num_workers = args.workers

run_script = 'src/scripts/experiments/run_test_datasets.py'
calc_stats_script = 'src/scripts/experiments/calculate_statistic.py'
compare_script = 'src/scripts/experiments/compare_rif_dif.py'

def run_experiment(algorithm, coef):
    """Run a single experiment with given parameters."""
    # NOTE: Change version suffix (_3) here if needed for new experiment runs
    experiment_dir = f"{algorithm}_{coef.replace('.', '_')}"
    
    print("\n" + "="*80)
    print(f"Starting experiment: {experiment_dir}")
    print(f"Algorithm: {algorithm}, Coefficient: {coef}, Workers: {num_workers}")
    print(f"Timestamp: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("="*80 + "\n")
    
    # Run the experiment
    cmd = [
        sys.executable,
        run_script,
        experiment_dir,
        '--algorithm', algorithm,
        '--coef', coef,
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
    print(f"Total experiments: {len(algorithms) * len(coefficients)}")
    print(f"Algorithms: {', '.join(algorithms)}")
    print(f"Coefficients: {', '.join(coefficients)}")
    print(f"Weights: {', '.join(weights)}")
    print(f"Parallel workers per experiment: {num_workers}")
    print("="*80)
    
    results = []
    experiment_dirs = []
    start_time = datetime.now()
    
    for algorithm in algorithms:
        for coef in coefficients:
            experiment_name = f"{algorithm}_{coef.replace('.', '_')}"
            success, exp_dir = run_experiment(algorithm, coef)
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
        version = None
        if experiment_dirs:
            # Example: DIF_0_1_3 -> parts = ['DIF', '0', '1', '3'] -> version = '3'
            parts = experiment_dirs[0].split('_')
            if len(parts) >= 4 and parts[-1].isdigit():
                version = parts[-1]
        
        version_suffix = f"_v{version}" if version else ""
        version_arg = ['--version', version] if version else []
        version_msg = f" (version {version})" if version else ""
        
        print(f"Detected experiment version: {version if version else 'no version suffix'}\n")
        
        # Generate comparison for each coefficient (only for sizes 1000 and 2000)
        for coef in coefficients:
            output_file = f"src/main/output/comparison_coef_{coef.replace('.', '_')}{version_suffix}.html"
            print(f"Generating comparison for coefficient {coef} (sizes: 1000, 2000){version_msg}...")
            
            compare_cmd = [
                sys.executable,
                compare_script,
                '--base-dir', 'src/main/output',
                '--output', output_file,
                '--coef', coef,
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
                print(f"✗ Failed to generate comparison for coefficient {coef}")
                if e.stderr:
                    print(f"Error: {e.stderr}")
        
        # Generate combined comparison with picker (all coefficients, only sizes 1000 and 2000)
        output_file = f"src/main/output/comparison_all{version_suffix}.html"
        print(f"\nGenerating combined comparison with coefficient picker (sizes: 1000, 2000){version_msg}...")
        
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
