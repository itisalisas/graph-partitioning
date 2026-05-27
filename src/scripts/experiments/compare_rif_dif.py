#!/usr/bin/env python3
"""
Compare RIF and DIF experiment results and generate an HTML table.

New directory layout (results_vN):
  <results_dir>/
    RIF/
      {city}_{size}_{weight}_{swr}_{lp}_{binary}/
        partition_info.json
    DIF/
      {city}_{size}_{weight}_{swr}_{lp}_{binary}/
        partition_info.json
    BUP/
      {city}_{size}_{weight}_{swr}_{lp}_{binary}/
        partition_info.json

Usage:
  python compare_rif_dif.py --results-dir src/main/output/results_v1
  python compare_rif_dif.py --results-dir src/main/output/results_v1 \\
      --coef 0.1 --length-priority 0.5 --sizes 1000 2000 \\
      --output comparison.html
  # Auto-detect latest results_vN:
  python compare_rif_dif.py --base-dir src/main/output
"""

import json
import os
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple
import argparse


# ---------------------------------------------------------------------------
# Leaf-name parsing  (new format: city_size_weight_swr_lp_binary)
# ---------------------------------------------------------------------------

_LEAF_RE = re.compile(
    r'^(?P<city>[^_]+(?:_[^_]+)*?)_'   # city (greedy, stops before size)
    r'(?P<size>\d+)_'                   # size (digits)
    r'(?P<weight>\d+)_'                 # weight (digits)
    r'(?P<swr>[0-9.]+)_'               # starting-weight-ratio
    r'(?P<lp>[0-9.]+)_'                # length-priority
    r'(?P<binary>true|false)$'          # binary flag
)


def parse_leaf_name(name: str) -> Optional[dict]:
    """
    Parse a new-style leaf directory name.
    Returns dict with keys: city, size, weight, swr, lp, binary
    or None if the name does not match.
    """
    m = _LEAF_RE.match(name)
    if not m:
        return None
    return {
        'city':   m.group('city'),
        'size':   m.group('size'),
        'weight': m.group('weight'),
        'swr':    m.group('swr'),
        'lp':     m.group('lp'),
        'binary': m.group('binary') == 'true',
    }


# ---------------------------------------------------------------------------
# Auto-detect latest results_vN directory
# ---------------------------------------------------------------------------

def detect_latest_results_dir(base_dir: str) -> Optional[str]:
    """Return the path to the highest-numbered results_vN directory."""
    base = Path(base_dir)
    if not base.exists():
        return None
    pattern = re.compile(r'^results_v(\d+)$')
    best_ver, best_path = -1, None
    for entry in base.iterdir():
        if entry.is_dir():
            m = pattern.match(entry.name)
            if m:
                ver = int(m.group(1))
                if ver > best_ver:
                    best_ver, best_path = ver, str(entry)
    return best_path


# ---------------------------------------------------------------------------
# Data loading
# ---------------------------------------------------------------------------

def load_algorithm_data(
    results_dir: str,
    algorithm: str,
    coef_filter: Optional[str] = None,
    lp_filter: Optional[str] = None,
    binary_filter: Optional[bool] = None,
    size_filter: Optional[List[str]] = None,
) -> Dict[str, Dict[str, Dict[str, dict]]]:
    """
    Load partition_info.json files for one algorithm from a results_vN directory.

    Returns nested dict:
      { swr: { lp: { dataset_key: metrics_dict } } }

    dataset_key = "{city}_{size}_{weight}"
    metrics_dict contains all JSON fields plus _meta keys.
    """
    algo_dir = Path(results_dir) / algorithm
    data: Dict[str, Dict[str, Dict[str, dict]]] = {}

    if not algo_dir.exists():
        return data

    for leaf in algo_dir.iterdir():
        if not leaf.is_dir():
            continue

        parsed = parse_leaf_name(leaf.name)
        if parsed is None:
            continue

        swr    = parsed['swr']
        lp     = parsed['lp']
        binary = parsed['binary']
        city   = parsed['city']
        size   = parsed['size']
        weight = parsed['weight']

        # Apply filters
        if coef_filter and swr != coef_filter:
            continue
        if lp_filter and lp != lp_filter:
            continue
        if binary_filter is not None and binary != binary_filter:
            continue
        if size_filter and size not in size_filter:
            continue

        info_file = leaf / 'partition_info.json'
        if not info_file.exists():
            continue

        try:
            with open(info_file) as f:
                metrics = json.load(f)
        except Exception as e:
            print(f"Warning: could not read {info_file}: {e}", file=sys.stderr)
            continue

        # Attach meta
        metrics['_city']   = city
        metrics['_size']   = size
        metrics['_weight'] = weight
        metrics['_swr']    = swr
        metrics['_lp']     = lp
        metrics['_binary'] = binary
        metrics['_leaf']   = leaf.name

        dataset_key = f"{city}_{size}_{weight}"

        data.setdefault(swr, {}).setdefault(lp, {})[dataset_key] = metrics

    return data


# ---------------------------------------------------------------------------
# Metric comparison helpers
# ---------------------------------------------------------------------------

def compare_metric(rif_value, dif_value, metric_name: str) -> str:
    """Return CSS class: 'better' if RIF wins, 'worse' if RIF loses, '' if equal."""
    if rif_value is None or dif_value is None:
        return ''

    lower_is_better = [
        'partitionTime(s)', 'regionCount', 'normalRegionCount',
        'weightVariance', 'weightStandardDeviation',
        'weightMeanAbsoluteDeviation', 'relativeWeightMeanAbsoluteDeviation',
        'totalBoundaryLength',
    ]
    closer_to_one = ['estimatorRegionNumber']

    try:
        rv = float(rif_value)
        dv = float(dif_value)
        if abs(rv - dv) < 1e-9:
            return ''
        if metric_name in lower_is_better:
            return 'better' if rv < dv else 'worse'
        if metric_name in closer_to_one:
            return 'better' if abs(rv - 1.0) < abs(dv - 1.0) else 'worse'
    except (ValueError, TypeError):
        pass
    return ''


# ---------------------------------------------------------------------------
# HTML generation
# ---------------------------------------------------------------------------

DATASET_INFO_METRICS = [
    'totalGraphWeight',
    'minRegionCountEstimate',
    'bigVerticesPartitions',
    'bigVerticesWeight',
]

COMPARE_METRICS = [
    'partitionTime(s)',
    'regionCount',
    'normalRegionCount',
    'estimatorRegionNumber',
    'averageWeight',
    'weightMeanAbsoluteDeviation',
    'relativeWeightMeanAbsoluteDeviation',
    'weightStandardDeviation',
    'totalBoundaryLength',
    'minRegionWeight',
    'maxRegionWeight',
]


def _fmt(val) -> str:
    if isinstance(val, float):
        return f"{val:.4f}"
    return str(val) if val is not None else 'N/A'


def generate_html_table(
    rif_data: dict,
    dif_data: dict,
    output_file: str,
    results_dir: str,
    coef_filter: Optional[str] = None,
    lp_filter: Optional[str] = None,
):
    """Generate an HTML comparison table for RIF vs DIF."""

    # Determine which (swr, lp) combos have data in both algorithms
    common_swrs = sorted(set(rif_data) & set(dif_data))
    if coef_filter:
        common_swrs = [s for s in common_swrs if s == coef_filter]

    combos: Dict[str, Dict[str, List[str]]] = {}  # swr -> lp -> [dataset_keys]
    for swr in common_swrs:
        common_lps = sorted(set(rif_data[swr]) & set(dif_data[swr]))
        if lp_filter:
            common_lps = [l for l in common_lps if l == lp_filter]
        for lp in common_lps:
            common_ds = sorted(set(rif_data[swr][lp]) & set(dif_data[swr][lp]))
            if common_ds:
                combos.setdefault(swr, {})[lp] = common_ds

    if not combos:
        print("No common datasets found between RIF and DIF.", file=sys.stderr)
        return

    default_swr = next(iter(combos))
    default_lp  = next(iter(combos[default_swr]))

    version_label = Path(results_dir).name  # e.g. "results_v1"

    # ---- Build HTML ----
    html_parts = []

    html_parts.append(f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>RIF vs DIF — {version_label}</title>
    <style>
        body {{
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            margin: 20px;
            background-color: #f5f5f5;
        }}
        h1 {{ color: #333; text-align: center; }}
        .controls {{
            background: white; padding: 20px; border-radius: 8px;
            margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,.1);
            display: flex; align-items: center; gap: 15px;
        }}
        .controls label {{ font-weight: bold; color: #333; }}
        .controls select {{
            padding: 8px 15px; border: 2px solid #4CAF50;
            border-radius: 4px; font-size: 16px; cursor: pointer; background: white;
        }}
        table {{
            border-collapse: collapse; width: 100%;
            background: white; box-shadow: 0 2px 4px rgba(0,0,0,.1);
            margin-bottom: 30px;
        }}
        th, td {{ border: 1px solid #ddd; padding: 12px; text-align: left; }}
        th {{
            background-color: #4CAF50; color: white; font-weight: bold;
            position: sticky; top: 0; z-index: 10;
        }}
        tr:nth-child(even) {{ background-color: #f9f9f9; }}
        tr:hover {{ background-color: #f1f1f1; }}
        .metric-name {{ font-weight: bold; background-color: #e8f5e9; }}
        .better {{ background-color: #c8e6c9 !important; font-weight: bold; }}
        .worse  {{ background-color: #ffcdd2 !important; font-weight: bold; }}
        .dataset-header {{
            background-color: #2196F3 !important; color: white !important;
            font-size: 1.1em; text-align: center;
        }}
        .legend {{
            display: flex; justify-content: center; gap: 30px; margin-bottom: 20px;
        }}
        .legend-item {{ display: flex; align-items: center; gap: 10px; }}
        .legend-box {{ width: 30px; height: 20px; border: 1px solid #ddd; }}
        .number {{ text-align: right; font-family: 'Courier New', monospace; }}
        .data-table {{ display: none; }}
        .data-table.active {{ display: table; }}
    </style>
    <script>
        function updateTable() {{
            const swr = document.getElementById('swr-sel').value;
            const lp  = document.getElementById('lp-sel').value;
            document.querySelectorAll('.data-table').forEach(t => t.classList.remove('active'));
            const id = 'tbl-' + swr.replace(/\\./g,'_') + '-' + lp.replace(/\\./g,'_');
            const t = document.getElementById(id);
            if (t) t.classList.add('active');
        }}
        function updateLpOptions() {{
            const swr = document.getElementById('swr-sel').value;
            const lpSel = document.getElementById('lp-sel');
            const opts = lpMapping[swr] || [];
            lpSel.innerHTML = '';
            opts.forEach(lp => {{
                const o = document.createElement('option');
                o.value = lp; o.text = 'LP ' + lp;
                lpSel.appendChild(o);
            }});
            updateTable();
        }}
        window.onload = updateLpOptions;
    </script>
</head>
<body>
    <h1>RIF vs DIF — {version_label}</h1>
    <div class="legend">
        <div class="legend-item">
            <div class="legend-box better"></div><span>RIF Better</span>
        </div>
        <div class="legend-item">
            <div class="legend-box worse"></div><span>RIF Worse</span>
        </div>
    </div>
    <div class="controls">
        <label for="swr-sel">Starting-weight-ratio:</label>
        <select id="swr-sel" onchange="updateLpOptions()">
""")

    for swr in combos:
        sel = ' selected' if swr == default_swr else ''
        html_parts.append(f'            <option value="{swr}"{sel}>{swr}</option>\n')

    html_parts.append("""        </select>
        <label for="lp-sel">Length-priority:</label>
        <select id="lp-sel" onchange="updateTable()"></select>
    </div>
    <script>
        const lpMapping = {
""")

    for swr, lp_dict in combos.items():
        lps_js = ', '.join(f'"{lp}"' for lp in lp_dict)
        html_parts.append(f'            "{swr}": [{lps_js}],\n')

    html_parts.append("        };\n    </script>\n")

    # ---- One table per (swr, lp) combo ----
    for swr, lp_dict in combos.items():
        for lp, datasets in lp_dict.items():
            is_default = (swr == default_swr and lp == default_lp)
            active = 'active' if is_default else ''
            tbl_id = f"tbl-{swr.replace('.','_')}-{lp.replace('.','_')}"

            html_parts.append(
                f'\n    <table id="{tbl_id}" class="data-table {active}">\n'
                '        <thead><tr>'
                '<th>Metric</th><th>RIF</th><th>DIF</th><th>Diff (%)</th>'
                '</tr></thead>\n        <tbody>\n'
            )

            for ds_key in datasets:
                rif_m = rif_data[swr][lp][ds_key]
                dif_m = dif_data[swr][lp][ds_key]

                city   = rif_m['_city'].capitalize()
                size   = rif_m['_size']
                weight = rif_m['_weight']

                html_parts.append(
                    f'            <tr><td colspan="4" class="dataset-header">'
                    f'{city} | Size: {size} vertices | Max Weight: {weight}'
                    f'</td></tr>\n'
                )

                # Dataset-level info (same for both)
                for metric in DATASET_INFO_METRICS:
                    val = rif_m.get(metric, 'N/A')
                    val_str = f"{val:.2f}" if isinstance(val, float) else str(val)
                    html_parts.append(
                        f'            <tr style="background-color:#f0f0f0;">'
                        f'<td class="metric-name">{metric}</td>'
                        f'<td colspan="3" class="number" style="text-align:center;font-style:italic;">'
                        f'{val_str} (dataset property)</td></tr>\n'
                    )

                # Comparison metrics
                for metric in COMPARE_METRICS:
                    rv = rif_m.get(metric)
                    dv = dif_m.get(metric)
                    css = compare_metric(rv, dv, metric)

                    diff_pct = ''
                    try:
                        if rv is not None and dv is not None:
                            rn, dn = float(rv), float(dv)
                            if dn != 0:
                                diff_pct = f"{((rn - dn) / dn * 100):+.2f}%"
                    except (ValueError, TypeError):
                        pass

                    html_parts.append(
                        f'            <tr>'
                        f'<td class="metric-name">{metric}</td>'
                        f'<td class="number {css}">{_fmt(rv)}</td>'
                        f'<td class="number">{_fmt(dv)}</td>'
                        f'<td class="number">{diff_pct}</td>'
                        f'</tr>\n'
                    )

            html_parts.append('        </tbody>\n    </table>\n')

    html_parts.append('</body>\n</html>\n')

    with open(output_file, 'w', encoding='utf-8') as f:
        f.writelines(html_parts)

    print(f"HTML comparison table generated: {output_file}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description='Compare RIF and DIF results from a results_vN directory'
    )
    parser.add_argument(
        '--results-dir', type=str, default=None,
        help='Path to results_vN directory (e.g. src/main/output/results_v1). '
             'If omitted, auto-detects the latest results_vN under --base-dir.'
    )
    parser.add_argument(
        '--base-dir', type=str, default='src/main/output',
        help='Base directory to search for results_vN (default: src/main/output)'
    )
    parser.add_argument(
        '--output', type=str, default=None,
        help='Output HTML file (default: <results_dir>/comparison_rif_dif.html)'
    )
    parser.add_argument(
        '--coef', type=str, default=None,
        help='Filter by starting-weight-ratio (e.g. 0.1). Shows all if omitted.'
    )
    parser.add_argument(
        '--length-priority', type=str, default=None,
        help='Filter by length-priority (e.g. 0.5). Shows all if omitted.'
    )
    parser.add_argument(
        '--binary', type=str, default=None, choices=['true', 'false'],
        help='Filter RIF by binary flag (true/false). Shows both if omitted.'
    )
    parser.add_argument(
        '--sizes', nargs='+', type=str, default=None,
        help='Filter by graph sizes (e.g. 1000 2000). All sizes if omitted.'
    )
    # Legacy --version argument kept for backward compatibility (ignored in new scheme)
    parser.add_argument('--version', type=str, default=None,
                        help='(Legacy, ignored) Experiment version.')

    args = parser.parse_args()

    # Resolve results directory
    results_dir = args.results_dir
    if results_dir is None:
        results_dir = detect_latest_results_dir(args.base_dir)
        if results_dir is None:
            print(f"Error: no results_vN directory found under {args.base_dir}",
                  file=sys.stderr)
            return 1
        print(f"Auto-detected results directory: {results_dir}")
    else:
        if not Path(results_dir).exists():
            print(f"Error: directory not found: {results_dir}", file=sys.stderr)
            return 1

    output_file = args.output or str(Path(results_dir) / 'comparison_rif_dif.html')

    binary_filter: Optional[bool] = None
    if args.binary == 'true':
        binary_filter = True
    elif args.binary == 'false':
        binary_filter = False

    print(f"Loading RIF data from {results_dir} ...")
    rif_data = load_algorithm_data(
        results_dir, 'RIF',
        coef_filter=args.coef,
        lp_filter=args.length_priority,
        binary_filter=binary_filter,
        size_filter=args.sizes,
    )
    rif_count = sum(len(ds) for lp_d in rif_data.values() for ds in lp_d.values())
    print(f"  Found {rif_count} RIF datasets across {len(rif_data)} swr values")

    print(f"Loading DIF data from {results_dir} ...")
    dif_data = load_algorithm_data(
        results_dir, 'DIF',
        coef_filter=args.coef,
        lp_filter=None,          # DIF doesn't use lp — use default lp key
        binary_filter=False,     # DIF is always non-binary
        size_filter=args.sizes,
    )
    # DIF leaf names use DEFAULT_LP (0.5); map them to every lp so they appear
    # in all (swr, lp) combos for the same swr
    dif_data_expanded: dict = {}
    LP_VALUES = ['0.0', '0.25', '0.5', '0.75', '1.0']
    for swr, lp_dict in dif_data.items():
        dif_data_expanded[swr] = {}
        # DIF results are stored under lp=DEFAULT_LP (0.5)
        default_lp_data = lp_dict.get('0.5', {})
        for lp in LP_VALUES:
            dif_data_expanded[swr][lp] = default_lp_data

    dif_count = sum(len(ds) for lp_d in dif_data.values() for ds in lp_d.values())
    print(f"  Found {dif_count} DIF datasets across {len(dif_data)} swr values")

    if not rif_data or not dif_data:
        print("Error: no data found for RIF or DIF.", file=sys.stderr)
        return 1

    generate_html_table(
        rif_data, dif_data_expanded, output_file,
        results_dir=results_dir,
        coef_filter=args.coef,
        lp_filter=args.length_priority,
    )
    return 0


if __name__ == '__main__':
    sys.exit(main())
