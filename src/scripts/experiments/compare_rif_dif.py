#!/usr/bin/env python3
"""
Скрипт для сравнения результатов экспериментов RIF и DIF.
Генерирует HTML таблицу с подсветкой лучших/худших значений.
"""

import json
import os
import sys
from pathlib import Path
from typing import Dict, List, Tuple
import argparse


def parse_dataset_name(path: str) -> Tuple[str, str, str]:
    """
    Извлекает информацию о датасете из пути.
    Возвращает: (город, размер_вершин, макс_вес)
    
    Пример: moscow/1000/max_weight_10000/partition_info.json
    -> ("moscow", "1000", "10000")
    """
    parts = path.split('/')
    # Ожидаем: город/размер/max_weight_XXXX/partition_info.json
    if len(parts) >= 4:
        city = parts[0]
        size = parts[1]  # количество вершин (100, 500, 1000, 2000)
        max_weight_dir = parts[2]  # max_weight_10000 или max_weight_20000
        
        # Извлекаем числовое значение из max_weight_XXXX
        if max_weight_dir.startswith('max_weight_'):
            max_weight = max_weight_dir.replace('max_weight_', '')
            return (city, size, max_weight)
    
    return ("unknown", "unknown", "unknown")


def extract_coef_from_exp_name(exp_name: str) -> str:
    """
    Извлекает коэффициент из имени эксперимента.
    Пример: RIF_0_25_lp0_5_21 -> 0.25, RIF_0_1_3 -> 0.1, DIF_0_25 -> 0.25
    """
    parts = exp_name.split('_')
    if len(parts) >= 3:
        # Коэффициент всегда состоит из двух частей после алгоритма (индексы 1 и 2)
        # Останавливаемся когда встретили 'lp' или собрали 2 части
        coef_parts = []
        for i in range(1, len(parts)):
            # Останавливаемся если встретили маркер LENGTH_PRIORITY
            if parts[i].startswith('lp'):
                break
            coef_parts.append(parts[i])
            # Коэффициент всегда состоит из 2 частей
            if len(coef_parts) == 2:
                break
        if len(coef_parts) >= 2:
            return '.'.join(coef_parts[:2])
    return "unknown"


def extract_length_priority_from_exp_name(exp_name: str) -> str:
    """
    Извлекает LENGTH_PRIORITY из имени эксперимента.
    Примеры: 
      RIF_0_1_lp0_5_26 -> 0.5 (LP, затем версия)
      RIF_0_1_lp0_25_26 -> 0.25 (LP, затем версия)
      RIF_0_1_lp0_25 -> 0.25 (LP, нет версии)
      RIF_0_1_lp0_75 -> 0.75 (LP, нет версии)
      RIF_0_1_lp1_0 -> 1.0
      RIF_0_1_26 -> '' (нет LP)
    """
    parts = exp_name.split('_')
    # Ищем часть с префиксом 'lp'
    for i in range(len(parts)):
        if parts[i].startswith('lp'):
            # lp0 -> первая часть числа
            lp_first = parts[i][2:]  # убираем 'lp'
            # Следующая часть - вторая часть числа (после точки)
            if i + 1 < len(parts):
                lp_second = parts[i + 1]
                # Проверяем, что следующая часть - это число
                if lp_second.isdigit():
                    lp_second_val = int(lp_second)
                    # LP может быть 0, 25, 5, 50, 75 (от 0.0 до 1.0)
                    # Версия обычно >= 20
                    # Если есть еще одна часть после lp_second, то lp_second - это LP компонент
                    # Если lp_second - последняя часть, то нужно угадать:
                    #   - если <= 75, скорее всего LP
                    #   - если >= 20, может быть версией, НО если < 100 то скорее LP
                    has_part_after = (i + 2 < len(parts))
                    is_likely_lp = (lp_second_val <= 75 or has_part_after)
                    
                    if is_likely_lp:
                        return f"{lp_first}.{lp_second}"
            # Если следующей части нет, возвращаем X.0
            return f"{lp_first}.0" if lp_first else "0.0"
    return ""  # LENGTH_PRIORITY не указан


def extract_version_from_exp_name(exp_name: str) -> str:
    """
    Извлекает версию из имени эксперимента.
    Примеры:
      RIF_0_1_lp0_0_26 -> '26' (версия после LP)
      RIF_0_1_lp0_25 -> '' (нет версии после LP)
      RIF_0_1_26 -> '26' (нет LP, версия в конце)
      RIF_0_1 -> '' (нет версии)
    """
    parts = exp_name.split('_')
    
    # Ищем маркер 'lp' в имени
    lp_index = -1
    for i, part in enumerate(parts):
        if part.startswith('lp'):
            lp_index = i
            break
    
    if lp_index >= 0:
        # Есть LENGTH_PRIORITY
        # LP состоит из 2 частей: lpX и Y, например lp0 и 25
        # Версия должна быть ПОСЛЕ LP компонентов (т.е. после lp_index + 1)
        version_index = lp_index + 2
        if version_index < len(parts) and parts[version_index].isdigit():
            # Проверяем что это действительно версия (обычно >= 20)
            # или это единственная часть после LP
            return parts[version_index]
        return ""
    else:
        # Нет LENGTH_PRIORITY, версия - последняя цифровая часть
        # Пропускаем первые 3 части (алгоритм + коэффициент из 2 частей)
        if len(parts) >= 4 and parts[-1].isdigit():
            return parts[-1]
        return ""


def load_experiment_data(base_dir: str, algorithm: str, coef_filter: str = None, lp_filter: str = None, size_filter: List[str] = None, version_filter: str = None) -> Dict[str, Dict]:
    """
    Загружает данные экспериментов для указанного алгоритма.
    Возвращает словарь: {coef: {lp: {dataset_key: metrics}}}
    """
    data = {}
    base_path = Path(base_dir)
    
    # Ищем директории вида DIF_* или RIF_*
    pattern = f"{algorithm}_*"
    for exp_dir in base_path.glob(pattern):
        if not exp_dir.is_dir():
            continue
        
        # Извлекаем коэффициент, length_priority и версию
        coef = extract_coef_from_exp_name(exp_dir.name)
        lp = extract_length_priority_from_exp_name(exp_dir.name)
        version = extract_version_from_exp_name(exp_dir.name)
        
        # Если lp пустой, значит это старый эксперимент без length_priority, используем "default"
        if not lp:
            lp = "default"
        
        # Фильтруем по коэффициенту если указан
        if coef_filter and coef != coef_filter:
            continue
        
        # Фильтруем по length_priority если указан
        if lp_filter and lp != lp_filter:
            continue
        
        # Фильтруем по версии если указан
        if version_filter is not None and version != version_filter:
            continue
        
        # Инициализируем вложенную структуру для коэффициента и lp
        if coef not in data:
            data[coef] = {}
        if lp not in data[coef]:
            data[coef][lp] = {}
        
        # Ищем все partition_info.json в поддиректориях
        for json_file in exp_dir.rglob("partition_info.json"):
            try:
                with open(json_file, 'r') as f:
                    metrics = json.load(f)
                
                # Извлекаем информацию о датасете
                relative_path = str(json_file.relative_to(exp_dir))
                city, size, max_weight = parse_dataset_name(relative_path)
                
                # Фильтруем по размеру графа если указан
                if size_filter and size not in size_filter:
                    continue
                
                dataset_key = f"{city}_{size}_{max_weight}"
                
                # Добавляем информацию об эксперименте
                metrics['_experiment'] = exp_dir.name
                metrics['_dataset'] = dataset_key
                metrics['_city'] = city
                metrics['_size'] = size
                metrics['_max_weight'] = max_weight
                metrics['_coef'] = coef
                metrics['_length_priority'] = lp
                metrics['_version'] = version
                
                data[coef][lp][dataset_key] = metrics
            except Exception as e:
                print(f"Error loading {json_file}: {e}", file=sys.stderr)
    
    return data


def compare_metric(rif_value, dif_value, metric_name: str) -> str:
    """
    Сравнивает значения метрики и возвращает CSS класс для подсветки.
    
    Возвращает: 'better' если RIF лучше, 'worse' если RIF хуже, '' если равны
    """
    if rif_value is None or dif_value is None:
        return ''
    
    # Метрики где меньше = лучше
    lower_is_better = [
        'partitionTime(s)', 'regionCount', 'normalRegionCount',
        'weightVariance', 'totalBoundaryLength'
    ]
    
    # Метрики где ближе к 1 = лучше
    closer_to_one = ['estimatorRegionNumber']
    
    # Метрики где больше = лучше (более сбалансированное разбиение)
    higher_is_better = ['averageWeight', 'minRegionWeight', 'maxRegionWeight']
    
    try:
        rif_val = float(rif_value)
        dif_val = float(dif_value)
        
        if abs(rif_val - dif_val) < 1e-9:  # практически равны
            return ''
        
        if metric_name in lower_is_better:
            return 'better' if rif_val < dif_val else 'worse'
        elif metric_name in closer_to_one:
            rif_diff = abs(rif_val - 1.0)
            dif_diff = abs(dif_val - 1.0)
            return 'better' if rif_diff < dif_diff else 'worse'
        elif metric_name in higher_is_better:
            return 'better' if rif_val > dif_val else 'worse'
        else:
            return ''
    except (ValueError, TypeError):
        return ''


def detect_available_versions(base_dir: str) -> List[str]:
    """
    Определяет доступные версии экспериментов в директории.
    Возвращает отсортированный список версий в порядке убывания (самая новая первая).
    """
    versions = set()
    base_path = Path(base_dir)
    
    # Ищем только директории RIF_* и DIF_*
    for pattern in ["RIF_*", "DIF_*"]:
        for exp_dir in base_path.glob(pattern):
            if exp_dir.is_dir():
                version = extract_version_from_exp_name(exp_dir.name)
                versions.add(version)
    
    # Сортируем по убыванию: самая большая версия первая, пустая строка в конце
    sorted_versions = sorted([v for v in versions if v], key=int, reverse=True)
    if "" in versions:
        sorted_versions.append("")
    
    return sorted_versions


def generate_html_table(rif_data: Dict, dif_data: Dict, output_file: str, coef_filter: str = None, lp_filter: str = None, version: str = None):
    """
    Генерирует HTML таблицу со сравнением метрик.
    rif_data: {coef: {lp: {dataset_key: metrics}}}
    dif_data: {coef: {lp: {dataset_key: metrics}}}
    """
    # Находим общие коэффициенты
    common_coefs = sorted(set(rif_data.keys()) & set(dif_data.keys()))
    
    if not common_coefs:
        print("No common coefficients found between RIF and DIF!", file=sys.stderr)
        return
    
    # Если указан фильтр коэффициента, используем только его
    if coef_filter:
        if coef_filter in common_coefs:
            common_coefs = [coef_filter]
        else:
            print(f"Coefficient {coef_filter} not found in data!", file=sys.stderr)
            return
    
    # Для каждого коэффициента находим общие LENGTH_PRIORITY значения
    coef_lp_datasets = {}
    for coef in common_coefs:
        common_lps = sorted(set(rif_data[coef].keys()) & set(dif_data[coef].keys()))
        
        # Если указан фильтр lp, используем только его
        if lp_filter:
            if lp_filter in common_lps:
                common_lps = [lp_filter]
            else:
                continue  # Пропускаем этот коэффициент
        
        for lp in common_lps:
            common_datasets = sorted(set(rif_data[coef][lp].keys()) & set(dif_data[coef][lp].keys()))
            if common_datasets:
                if coef not in coef_lp_datasets:
                    coef_lp_datasets[coef] = {}
                coef_lp_datasets[coef][lp] = common_datasets
    
    if not coef_lp_datasets:
        print("No common datasets found between RIF and DIF!", file=sys.stderr)
        return
    
    # Метрики датасета (не сравниваются, одинаковые для RIF и DIF)
    dataset_info_metrics = [
        'totalGraphWeight',
        'minRegionCountEstimate',
        'bigVerticesPartitions',
        'bigVerticesWeight'
    ]
    
    # Определяем метрики для сравнения
    metrics_to_compare = [
        'partitionTime(s)',
        'regionCount',
        'normalRegionCount',
        'estimatorRegionNumber',
        'averageWeight',
        'weightVariance',
        'totalBoundaryLength',
        'minRegionWeight',
        'maxRegionWeight'
    ]
    
    # Определяем значения по умолчанию
    default_coef = '0.25' if '0.25' in common_coefs else common_coefs[0]
    default_lp = '0.0' if default_coef in coef_lp_datasets and '0.0' in coef_lp_datasets[default_coef] else list(coef_lp_datasets[default_coef].keys())[0]
    
    version_title = f" (Version {version})" if version else ""
    
    html = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>RIF vs DIF Comparison""" + version_title + """</title>
    <style>
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            margin: 20px;
            background-color: #f5f5f5;
        }
        h1 {
            color: #333;
            text-align: center;
        }
        .controls {
            background: white;
            padding: 20px;
            border-radius: 8px;
            margin-bottom: 20px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            display: flex;
            align-items: center;
            gap: 15px;
        }
        .controls label {
            font-weight: bold;
            color: #333;
        }
        .controls select {
            padding: 8px 15px;
            border: 2px solid #4CAF50;
            border-radius: 4px;
            font-size: 16px;
            cursor: pointer;
            background: white;
        }
        .summary {
            background: white;
            padding: 20px;
            border-radius: 8px;
            margin-bottom: 20px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        table {
            border-collapse: collapse;
            width: 100%;
            background: white;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            margin-bottom: 30px;
        }
        th, td {
            border: 1px solid #ddd;
            padding: 12px;
            text-align: left;
        }
        th {
            background-color: #4CAF50;
            color: white;
            font-weight: bold;
            position: sticky;
            top: 0;
            z-index: 10;
        }
        tr:nth-child(even) {
            background-color: #f9f9f9;
        }
        tr:hover {
            background-color: #f1f1f1;
        }
        .metric-name {
            font-weight: bold;
            background-color: #e8f5e9;
        }
        .better {
            background-color: #c8e6c9 !important;
            font-weight: bold;
        }
        .worse {
            background-color: #ffcdd2 !important;
            font-weight: bold;
        }
        .dataset-header {
            background-color: #2196F3 !important;
            color: white !important;
            font-size: 1.1em;
            text-align: center;
        }
        .legend {
            display: flex;
            justify-content: center;
            gap: 30px;
            margin-bottom: 20px;
        }
        .legend-item {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .legend-box {
            width: 30px;
            height: 20px;
            border: 1px solid #ddd;
        }
        .number {
            text-align: right;
            font-family: 'Courier New', monospace;
        }
        .data-table {
            display: none;
        }
        .data-table.active {
            display: table;
        }
    </style>
    <script>
        function updateTable() {
            const coefSelector = document.getElementById('coef-selector');
            const lpSelector = document.getElementById('lp-selector');
            const selectedCoef = coefSelector.value;
            const selectedLp = lpSelector.value;
            
            // Скрываем все таблицы
            document.querySelectorAll('.data-table').forEach(table => {
                table.classList.remove('active');
            });
            
            // Показываем выбранную таблицу
            const tableId = 'table-' + selectedCoef.replace(/\./g, '_') + '-' + selectedLp.replace(/\./g, '_');
            const selectedTable = document.getElementById(tableId);
            if (selectedTable) {
                selectedTable.classList.add('active');
            }
        }
        
        function updateLpOptions() {
            const coefSelector = document.getElementById('coef-selector');
            const lpSelector = document.getElementById('lp-selector');
            const selectedCoef = coefSelector.value;
            
            // Получаем данные о доступных LP для выбранного коэффициента
            const lpOptions = coefLpMapping[selectedCoef] || [];
            
            // Очищаем текущие опции
            lpSelector.innerHTML = '';
            
            // Добавляем новые опции
            lpOptions.forEach(lp => {
                const option = document.createElement('option');
                option.value = lp;
                option.text = 'LENGTH_PRIORITY ' + lp;
                lpSelector.appendChild(option);
            });
            
            // Обновляем таблицу
            updateTable();
        }
        
        // Инициализация при загрузке страницы
        window.onload = function() {
            updateLpOptions();
        };
    </script>
</head>
<body>
    <h1>RIF vs DIF Comparison""" + version_title + """</h1>
    
    <div class="legend">
        <div class="legend-item">
            <div class="legend-box better"></div>
            <span>RIF Better</span>
        </div>
        <div class="legend-item">
            <div class="legend-box worse"></div>
            <span>RIF Worse</span>
        </div>
    </div>
    
    <div class="controls">
        <label for="coef-selector">Select Coefficient:</label>
        <select id="coef-selector" onchange="updateLpOptions()">
"""
    
    # Добавляем опции для коэффициентов
    for coef in common_coefs:
        selected = ' selected' if coef == default_coef else ''
        html += f'            <option value="{coef}"{selected}>Coefficient {coef}</option>\n'
    
    html += """        </select>
        <label for="lp-selector">Select LENGTH_PRIORITY:</label>
        <select id="lp-selector" onchange="updateTable()">
        </select>
    </div>
    
    <script>
        // Маппинг коэффициентов к доступным LENGTH_PRIORITY
        const coefLpMapping = {
"""
    
    # Добавляем маппинг коэффициентов к LP значениям
    for coef in common_coefs:
        lps = sorted(coef_lp_datasets[coef].keys())
        lps_str = ', '.join(f'"{lp}"' for lp in lps)
        html += f'            "{coef}": [{lps_str}],\n'
    
    html += """        };
    </script>
"""
    
    # Генерируем таблицы для каждой комбинации коэффициента и LENGTH_PRIORITY
    for coef in common_coefs:
        for lp in sorted(coef_lp_datasets[coef].keys()):
            common_datasets = coef_lp_datasets[coef][lp]
            is_default = (coef == default_coef and lp == default_lp)
            active_class = 'active' if is_default else ''
            table_id = f"table-{coef.replace('.', '_')}-{lp.replace('.', '_')}"
            
            html += f"""    
    <table id="{table_id}" class="data-table {active_class}" data-summary="{len(common_datasets)}">
        <thead>
            <tr>
                <th>Metric</th>
                <th>RIF</th>
                <th>DIF</th>
                <th>Diff (%)</th>
            </tr>
        </thead>
        <tbody>
"""
            
            for dataset in common_datasets:
                rif_metrics = rif_data[coef][lp][dataset]
                dif_metrics = dif_data[coef][lp][dataset]
                
                # Заголовок датасета
                html += f"""
            <tr>
                <td colspan="4" class="dataset-header">
                    {rif_metrics['_city'].capitalize()} | Size: {rif_metrics['_size']} vertices | Max Weight: {rif_metrics['_max_weight']}
                </td>
            </tr>
"""
                
                # Информация о датасете (одинаковая для RIF и DIF)
                for metric in dataset_info_metrics:
                    value = rif_metrics.get(metric, 'N/A')
                    if isinstance(value, float):
                        value_str = f"{value:.2f}"
                    else:
                        value_str = str(value)
                    
                    html += f"""
            <tr style="background-color: #f0f0f0;">
                <td class="metric-name">{metric}</td>
                <td colspan="3" class="number" style="text-align: center; font-style: italic;">{value_str} (dataset property)</td>
            </tr>
"""
                
                # Сравниваем каждую метрику
                for metric in metrics_to_compare:
                    rif_val = rif_metrics.get(metric, 'N/A')
                    dif_val = dif_metrics.get(metric, 'N/A')
                    
                    # Вычисляем разницу в процентах
                    diff_pct = ''
                    if rif_val != 'N/A' and dif_val != 'N/A':
                        try:
                            rif_num = float(rif_val)
                            dif_num = float(dif_val)
                            if dif_num != 0:
                                diff = ((rif_num - dif_num) / dif_num) * 100
                                diff_pct = f"{diff:+.2f}%"
                        except (ValueError, TypeError):
                            pass
                    
                    # Определяем класс для подсветки
                    css_class = compare_metric(rif_val, dif_val, metric)
                    
                    # Форматируем значения
                    if isinstance(rif_val, float):
                        rif_val_str = f"{rif_val:.4f}"
                    else:
                        rif_val_str = str(rif_val)
                    
                    if isinstance(dif_val, float):
                        dif_val_str = f"{dif_val:.4f}"
                    else:
                        dif_val_str = str(dif_val)
                    
                    html += f"""
            <tr>
                <td class="metric-name">{metric}</td>
                <td class="number {css_class}">{rif_val_str}</td>
                <td class="number">{dif_val_str}</td>
                <td class="number">{diff_pct}</td>
            </tr>
"""
        
        html += """
        </tbody>
    </table>
"""
    
    html += """
</body>
</html>
"""
    
    # Сохраняем HTML
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(html)
    
    print(f"HTML comparison table generated: {output_file}")


def main():
    parser = argparse.ArgumentParser(
        description='Compare RIF and DIF experiment results and generate HTML table'
    )
    parser.add_argument(
        '--base-dir',
        type=str,
        default='src/main/output',
        help='Base directory containing RIF_* and DIF_* subdirectories (default: src/main/output)'
    )
    parser.add_argument(
        '--output',
        type=str,
        default='comparison.html',
        help='Output HTML file (default: comparison.html)'
    )
    parser.add_argument(
        '--coef',
        type=str,
        default=None,
        help='Filter by coefficient (e.g., 0.1, 0.25, 0.4). If not specified, all coefficients will be shown with a picker (default: None, shows all with 0.25 as default)'
    )
    parser.add_argument(
        '--sizes',
        nargs='+',
        type=str,
        default=None,
        help='Filter by graph sizes (e.g., 1000 2000). If not specified, all sizes are included (default: None)'
    )
    parser.add_argument(
        '--version',
        type=str,
        default=None,
        help='Filter by experiment version (e.g., 2, 3). If not specified, latest version is used (default: None, auto-detect latest)'
    )
    parser.add_argument(
        '--length-priority',
        type=str,
        default=None,
        help='Filter by LENGTH_PRIORITY value (e.g., 0.0, 0.5, 1.0). If not specified, all LENGTH_PRIORITY values will be shown with a picker (default: None)'
    )
    
    args = parser.parse_args()
    
    # Если версия не указана, определяем последнюю доступную версию
    if args.version is None:
        available_versions = detect_available_versions(args.base_dir)
        if available_versions:
            # Берем последнюю версию (самую большую цифру или пустую строку)
            args.version = available_versions[0] if available_versions[0] else ""
            print(f"Auto-detected experiment version: {args.version if args.version else 'no version suffix'}")
        else:
            args.version = ""
    
    size_filter_msg = f" (filtered by sizes: {', '.join(args.sizes)})" if args.sizes else ""
    version_msg = f" version {args.version}" if args.version else " (no version suffix)"
    lp_filter_msg = f" (filtered by LENGTH_PRIORITY: {args.length_priority})" if args.length_priority else ""
    
    print(f"Loading RIF data from {args.base_dir}{size_filter_msg}{lp_filter_msg}, {version_msg}...")
    rif_data = load_experiment_data(args.base_dir, 'RIF', args.coef, getattr(args, 'length_priority', None), args.sizes, args.version)
    
    # Подсчет общего количества датасетов (с учетом вложенной структуры)
    rif_dataset_count = sum(len(lp_datasets) for coef_data in rif_data.values() for lp_datasets in coef_data.values())
    print(f"Found {rif_dataset_count} RIF datasets across {len(rif_data)} coefficients")
    
    print(f"Loading DIF data from {args.base_dir}{size_filter_msg}{lp_filter_msg}, {version_msg}...")
    dif_data = load_experiment_data(args.base_dir, 'DIF', args.coef, getattr(args, 'length_priority', None), args.sizes, args.version)
    
    dif_dataset_count = sum(len(lp_datasets) for coef_data in dif_data.values() for lp_datasets in coef_data.values())
    print(f"Found {dif_dataset_count} DIF datasets across {len(dif_data)} coefficients")
    
    if not rif_data or not dif_data:
        print("Error: No data found for RIF or DIF", file=sys.stderr)
        return 1
    
    generate_html_table(rif_data, dif_data, args.output, args.coef, getattr(args, 'length_priority', None), args.version)
    return 0


if __name__ == '__main__':
    sys.exit(main())
