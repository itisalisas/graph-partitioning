#!/usr/bin/env python3
"""
Генерация CSV файлов со статистикой для RIF и DIF алгоритмов.

Для каждого алгоритма создается отдельный CSV файл с метриками:
1. Относительный разброс весов (weightVariance / totalGraphWeight)
2. Относительная длина границы (totalBoundaryLength / размер графа)
3. estimatorRegionNumber

Агрегация производится по всем датасетам для каждой комбинации параметров.
"""

import json
import sys
import csv
import argparse
from pathlib import Path
from collections import defaultdict
from statistics import mean

# Импортируем функции парсинга из compare_rif_dif.py
sys.path.insert(0, 'src/scripts/experiments')
try:
    from compare_rif_dif import extract_coef_from_exp_name, extract_length_priority_from_exp_name, extract_version_from_exp_name
    HAS_COMPARE_MODULE = True
except ImportError:
    HAS_COMPARE_MODULE = False
    # Если импорт не удался, определяем базовые функции здесь (fallback)
    def extract_coef_from_exp_name(exp_name: str) -> str:
        parts = exp_name.split('_')
        if len(parts) >= 3:
            return f"{parts[1]}.{parts[2]}"
        return "unknown"
    
    def extract_length_priority_from_exp_name(exp_name: str) -> str:
        parts = exp_name.split('_')
        for i in range(len(parts)):
            if parts[i].startswith('lp'):
                lp_first = parts[i][2:]
                if i + 1 < len(parts):
                    lp_second = parts[i + 1]
                    if lp_second.isdigit() and int(lp_second) <= 75:
                        return f"{lp_first}.{lp_second}"
                return f"{lp_first}.0" if lp_first else "0.0"
        return ""
    
    def extract_version_from_exp_name(exp_name: str) -> str:
        parts = exp_name.split('_')
        lp_index = -1
        for i, part in enumerate(parts):
            if part.startswith('lp'):
                lp_index = i
                break
        if lp_index >= 0:
            version_index = lp_index + 2
            if version_index < len(parts) and parts[version_index].isdigit():
                return parts[version_index]
            return ""
        else:
            if len(parts) >= 4 and parts[-1].isdigit():
                return parts[-1]
            return ""

def load_experiments(base_dir, algorithm, version):
    """
    Загружает эксперименты для указанного алгоритма и версии.
    
    Возвращает словарь: {(coef, lp): [список метрик датасетов]}
    
    version: строка с версией (например, "26") или пустая строка "" для директорий без суффикса версии
    """
    data = {}
    base_path = Path(base_dir)
    
    # Ищем все директории алгоритма
    for exp_dir in base_path.glob(f'{algorithm}_*'):
        if not exp_dir.is_dir():
            continue
        
        # Извлекаем параметры используя функции парсинга
        exp_version = extract_version_from_exp_name(exp_dir.name)
        
        # Фильтруем по версии
        if exp_version != version:
            continue
        
        coef = extract_coef_from_exp_name(exp_dir.name)
        lp = extract_length_priority_from_exp_name(exp_dir.name)
        
        if not coef or coef == "unknown" or not lp:
            continue
        
        # Загружаем все JSON файлы
        metrics_list = []
        for json_file in exp_dir.rglob("partition_info.json"):
            try:
                with open(json_file, 'r') as f:
                    metrics = json.load(f)
                    metrics_list.append(metrics)
            except Exception as e:
                print(f"Warning: Failed to load {json_file}: {e}", file=sys.stderr)
        
        if metrics_list:
            key = (coef, lp)
            data[key] = metrics_list
    
    return data

def calculate_relative_metrics(metrics):
    """
    Вычисляет относительные метрики для одного датасета.
    
    Возвращает словарь с:
    - relative_weight_variance: weightVariance / totalGraphWeight
    - relative_boundary_length: totalBoundaryLength / dualVertexNumber (размер графа)
    - estimator_region_number: estimatorRegionNumber
    """
    result = {}
    
    # 1. Относительный разброс весов
    weight_variance = metrics.get('weightVariance')
    total_weight = metrics.get('totalGraphWeight')
    
    if weight_variance is not None and total_weight is not None and total_weight > 0:
        result['relative_weight_variance'] = weight_variance / total_weight
    else:
        result['relative_weight_variance'] = None
    
    # 2. Относительная длина границы
    # Делим на размер графа (число вершин двойственного графа)
    boundary_length = metrics.get('totalBoundaryLength')
    graph_size = metrics.get('dualVertexNumber')
    
    if boundary_length is not None and graph_size is not None and graph_size > 0:
        result['relative_boundary_length'] = boundary_length / graph_size
    else:
        result['relative_boundary_length'] = None
    
    # 3. estimatorRegionNumber
    estimator = metrics.get('estimatorRegionNumber')
    result['estimator_region_number'] = estimator
    
    return result

def aggregate_metrics(metrics_list):
    """
    Агрегирует метрики по списку датасетов (вычисляет среднее).
    """
    relative_metrics = [calculate_relative_metrics(m) for m in metrics_list]
    
    aggregated = {}
    
    # Агрегируем каждую метрику
    for key in ['relative_weight_variance', 'relative_boundary_length', 'estimator_region_number']:
        values = [m[key] for m in relative_metrics if m[key] is not None]
        if values:
            aggregated[key] = mean(values)
        else:
            aggregated[key] = None
    
    return aggregated

def generate_csv(data, output_file, algorithm):
    """
    Генерирует CSV файл со статистикой.
    
    Колонки:
    - starting_weight_ratio (coefficient)
    - length_priority
    - relative_weight_variance
    - relative_boundary_length
    - estimator_region_number
    - dataset_count (количество датасетов)
    """
    # Сортируем по коэффициенту и LP
    sorted_keys = sorted(data.keys(), key=lambda x: (float(x[0]), float(x[1])))
    
    with open(output_file, 'w', newline='', encoding='utf-8') as f:
        fieldnames = [
            'starting_weight_ratio',
            'length_priority',
            'relative_weight_variance',
            'relative_boundary_length',
            'estimator_region_number',
            'dataset_count'
        ]
        
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        
        for (coef, lp) in sorted_keys:
            metrics_list = data[(coef, lp)]
            aggregated = aggregate_metrics(metrics_list)
            
            row = {
                'starting_weight_ratio': coef,
                'length_priority': lp,
                'relative_weight_variance': f"{aggregated['relative_weight_variance']:.6f}" if aggregated['relative_weight_variance'] is not None else 'N/A',
                'relative_boundary_length': f"{aggregated['relative_boundary_length']:.2f}" if aggregated['relative_boundary_length'] is not None else 'N/A',
                'estimator_region_number': f"{aggregated['estimator_region_number']:.4f}" if aggregated['estimator_region_number'] is not None else 'N/A',
                'dataset_count': len(metrics_list)
            }
            
            writer.writerow(row)
    
    print(f"✓ {algorithm} statistics saved to: {output_file}")
    print(f"  Total combinations: {len(sorted_keys)}")

def main():
    parser = argparse.ArgumentParser(
        description='Generate CSV statistics for RIF and DIF algorithms',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Generate statistics for version 25
  python3 generate_statistics_csv.py --version 25
  
  # Generate statistics for experiments WITHOUT version suffix (e.g., RIF_0_1_lp0_25)
  python3 generate_statistics_csv.py --version ""
  
  # Specify custom output directory
  python3 generate_statistics_csv.py --version 25 --output-dir results
  
  # Specify custom base directory for experiments
  python3 generate_statistics_csv.py --version 24 --base-dir src/main/output
        """
    )
    
    parser.add_argument(
        '--version',
        type=str,
        required=True,
        help='Experiment version (e.g., "26", "25") or empty string "" for directories without version suffix'
    )
    
    parser.add_argument(
        '--base-dir',
        type=str,
        default='src/main/output',
        help='Base directory containing experiment results (default: src/main/output)'
    )
    
    parser.add_argument(
        '--output-dir',
        type=str,
        default='.',
        help='Output directory for CSV files (default: current directory)'
    )
    
    args = parser.parse_args()
    
    print(f"="*70)
    print(f"Generating CSV statistics for version {args.version}")
    print(f"="*70)
    
    # Создаем выходную директорию если не существует
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Загружаем данные для RIF
    print(f"\nLoading RIF experiments from {args.base_dir}...")
    rif_data = load_experiments(args.base_dir, 'RIF', args.version)
    print(f"  Found {len(rif_data)} RIF parameter combinations")
    
    # Загружаем данные для DIF
    print(f"\nLoading DIF experiments from {args.base_dir}...")
    dif_data = load_experiments(args.base_dir, 'DIF', args.version)
    print(f"  Found {len(dif_data)} DIF parameter combinations")
    
    if not rif_data and not dif_data:
        print("\nError: No experiment data found for version " + args.version, file=sys.stderr)
        return 1
    
    # Генерируем CSV для RIF
    if rif_data:
        print(f"\nGenerating RIF statistics...")
        rif_output = output_dir / f"RIF_statistics_v{args.version}.csv"
        generate_csv(rif_data, rif_output, 'RIF')
    
    # Генерируем CSV для DIF
    if dif_data:
        print(f"\nGenerating DIF statistics...")
        dif_output = output_dir / f"DIF_statistics_v{args.version}.csv"
        generate_csv(dif_data, dif_output, 'DIF')
    
    print(f"\n{'='*70}")
    print("✓ All statistics generated successfully!")
    print(f"{'='*70}")
    
    return 0

if __name__ == '__main__':
    sys.exit(main())
