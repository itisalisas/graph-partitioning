#!/usr/bin/env python3
"""
Generate a results summary table (xlsx) from a results_vN experiment directory.

Output table layout (mirrors "Новая таблица (1).xlsx"):

  Col A: starting_weight_ratio  — merged per swr group
  Col B: length_priority        — merged per (swr, lp) sub-group
  Col C: use_binary_search      — True/False for RIF; empty for BUP/DIF/IF
  Col D: Algorithm              — BUP / DIF / IF / RIF
  Col E: relative_weight_linear_deviation
  Col F: relative_boundary_length
  Col G: estimator_region_number
  Col H: average_time_per_vertex

Row structure per swr value (e.g. swr=0.1):
  lp=0.0  → BUP  (binary=—)      ← lp merged across 5 rows
            DIF  (binary=—)
            IF   (binary=—)
            RIF  binary=True
            RIF  binary=False
  lp=0.25 → RIF  binary=True     ← lp merged across 2 rows
             RIF  binary=False
  lp=0.5  → RIF  binary=True
             RIF  binary=False
  lp=0.75 → ...
  lp=1.0  → ...

Metrics are averaged across all city/size/weight leaf directories found.

  relative_weight_linear_deviation = mean(weightMeanAbsoluteDeviation / totalGraphWeight)
  relative_boundary_length         = mean(totalBoundaryLength / (dataset_length * 2))
  estimator_region_number          = mean(estimatorRegionNumber)
  average_time_per_vertex          = mean(partitionTime(s) / dualVertexNumber)

Usage:
  python generate_results_table.py <results_dir> [--output table.xlsx]

  <results_dir>  path to results_vN directory
                 (e.g. src/main/output/results_v1)
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

try:
    import openpyxl
    from openpyxl.styles import Alignment, Font, PatternFill, Border, Side
    from openpyxl.utils import get_column_letter
except ImportError:
    print("openpyxl is required: pip install openpyxl")
    sys.exit(1)

# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------
parser = argparse.ArgumentParser(description='Generate results xlsx table from results_vN directory')
parser.add_argument('results_dir', type=str,
                    help='Path to results_vN directory (e.g. src/main/output/results_v1)')
parser.add_argument('--output', type=str, default=None,
                    help='Output xlsx file path (default: <results_dir>/results_table.xlsx)')
args = parser.parse_args()

results_dir = Path(args.results_dir)
if not results_dir.exists():
    print(f"Error: directory not found: {results_dir}")
    sys.exit(1)

output_path = Path(args.output) if args.output else results_dir / "results_table.xlsx"

# ---------------------------------------------------------------------------
# Parameter definitions (must match run_all_experiments.py)
# ---------------------------------------------------------------------------
SWR_VALUES   = ['0.1', '0.2', '0.3', '0.4']
LP_VALUES    = ['0.0', '0.25', '0.5', '0.75', '1.0']
BINARY_VALUES = [True, False]

DEFAULT_SWR = '0.25'
DEFAULT_LP  = '0.5'

# ---------------------------------------------------------------------------
# Data loading
# ---------------------------------------------------------------------------
def mean_present(values):
    """Return mean of non-None values, or None if there are no values."""
    present = [value for value in values if value is not None]
    if not present:
        return None
    return sum(present) / len(present)


def load_metrics(algo_dir: Path, swr: str, lp: str, binary: bool):
    """
    Load and average metrics from all leaf partition_info.json files
    matching the given parameter combination.

    New-style leaf name: {city}_{dataset_length}_{weight}_{swr}_{lp}_{binary}
    """
    binary_str = 'true' if binary else 'false'
    pattern = re.compile(
        r'^(?P<city>[^_]+)_(?P<dataset_length>\d+)_(?P<weight>\d+)_'
        + re.escape(swr) + r'_'
        + re.escape(lp) + r'_'
        + re.escape(binary_str) + r'$'
    )

    records = []
    if not algo_dir.exists():
        return None

    for leaf in algo_dir.iterdir():
        if not leaf.is_dir():
            continue
        match = pattern.match(leaf.name)
        if not match:
            continue
        info_file = leaf / 'partition_info.json'
        if not info_file.exists():
            continue
        try:
            with open(info_file) as f:
                d = json.load(f)

            dataset_length = float(match.group('dataset_length'))
            boundary_denominator = dataset_length * 2
            total_weight = d.get('totalGraphWeight', 0)
            vertex_count = d.get('dualVertexNumber', 0)
            partition_time = d.get('partitionTime(s)')
            k = d.get('regionCount', 1) or 1

            weight_deviation = d.get('weightMeanAbsoluteDeviation', d.get('weightVariance', 0))
            records.append({
                'rel_weight_linear_deviation': (
                    weight_deviation / total_weight * k
                    if total_weight and total_weight > 0
                    else None
                ),
                'rel_boundary': (
                    d.get('totalBoundaryLength', 0) / boundary_denominator / (k ** 0.5)
                    if boundary_denominator > 0
                    else None
                ),
                'estimator': d.get('estimatorRegionNumber', 1.0),
                'avg_time_per_vertex': (
                    partition_time / vertex_count
                    if partition_time is not None and vertex_count and vertex_count > 0
                    else None
                ),
            })
        except Exception as e:
            print(f"  Warning: could not read {info_file}: {e}")

    if not records:
        return None

    n = len(records)
    return {
        'rel_weight_linear_deviation': mean_present(r['rel_weight_linear_deviation'] for r in records),
        'rel_boundary':                mean_present(r['rel_boundary'] for r in records),
        'estimator':                   mean_present(r['estimator'] for r in records),
        'avg_time_per_vertex':         mean_present(r['avg_time_per_vertex'] for r in records),
        'n':                           n,
    }


def fmt(value, decimals=4):
    """Format a float for display, or return '—' if None."""
    if value is None:
        return '—'
    return round(value, decimals)


# ---------------------------------------------------------------------------
# Build row data
# ---------------------------------------------------------------------------
# Each row: (swr, lp, binary_or_none, algorithm, metrics_or_none)
# binary_or_none = True/False for RIF, None for BUP/DIF/IF

Row = tuple  # (swr, lp, binary, algorithm, metrics)

def build_rows():
    rows = []
    for swr in SWR_VALUES:
        # --- lp = 0.0 block: BUP, DIF, IF, RIF(True), RIF(False) ---
        lp = '0.0'

        # BUP: no parameters matter — use defaults
        bup_dir = results_dir / 'BUS'
        bup_metrics = load_metrics(bup_dir, DEFAULT_SWR, DEFAULT_LP, False)
        rows.append((swr, lp, None, 'BUS', bup_metrics))

        # DIF: only swr matters
        dif_dir = results_dir / 'DIF'
        dif_metrics = load_metrics(dif_dir, swr, DEFAULT_LP, False)
        rows.append((swr, lp, None, 'DIF', dif_metrics))

        # IF: only swr matters
        if_dir = results_dir / 'IF'
        if_metrics = load_metrics(if_dir, swr, DEFAULT_LP, False)
        rows.append((swr, lp, None, 'IF', if_metrics))

        # RIF: swr + lp + binary
        rif_dir = results_dir / 'RIF'
        for binary in BINARY_VALUES:
            m = load_metrics(rif_dir, swr, lp, binary)
            rows.append((swr, lp, binary, 'RIF', m))

        # --- remaining lp values: only RIF ---
        for lp in LP_VALUES[1:]:  # 0.25, 0.5, 0.75, 1.0
            for binary in BINARY_VALUES:
                m = load_metrics(rif_dir, swr, lp, binary)
                rows.append((swr, lp, binary, 'RIF', m))

    return rows


# ---------------------------------------------------------------------------
# Write xlsx
# ---------------------------------------------------------------------------
HEADERS = [
    'starting_weight_ratio',
    'length_priority',
    'use_binary_search',
    'Algorithm',
    'relative_weight_linear_deviation',
    'relative_boundary_length',
    'estimator_region_number',
    'average_time_per_vertex',
]

# Styles
HEADER_FILL  = PatternFill('solid', fgColor='4472C4')
HEADER_FONT  = Font(bold=True, color='FFFFFF')
BUP_FILL     = PatternFill('solid', fgColor='E2EFDA')   # light green
DIF_FILL     = PatternFill('solid', fgColor='DDEBF7')   # light blue
IF_FILL      = PatternFill('solid', fgColor='FCE4D6')   # light orange
RIF_FILL     = PatternFill('solid', fgColor='FFF2CC')   # light yellow
NONE_FILL    = PatternFill('solid', fgColor='F2F2F2')   # light grey for missing

THIN = Side(style='thin', color='BFBFBF')
THIN_BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)

ALGO_FILL = {'BUS': BUP_FILL, 'DIF': DIF_FILL, 'IF': IF_FILL, 'RIF': RIF_FILL}

CENTER = Alignment(horizontal='center', vertical='center', wrap_text=True)
LEFT   = Alignment(horizontal='left',   vertical='center', wrap_text=True)


def apply_style(cell, fill=None, font=None, alignment=CENTER, border=THIN_BORDER):
    if fill:
        cell.fill = fill
    if font:
        cell.font = font
    cell.alignment = alignment
    cell.border = border


def write_table(rows):
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = 'Results'

    # --- Header row ---
    for col_idx, header in enumerate(HEADERS, start=1):
        cell = ws.cell(row=1, column=col_idx, value=header)
        apply_style(cell, fill=HEADER_FILL, font=HEADER_FONT)

    # Column widths
    col_widths = [22, 16, 18, 12, 26, 26, 26, 24]
    for i, w in enumerate(col_widths, start=1):
        ws.column_dimensions[get_column_letter(i)].width = w
    ws.row_dimensions[1].height = 30

    # --- Data rows ---
    data_start = 2  # first data row (1-indexed)
    current_row = data_start

    # We need to track merge ranges
    # Group rows by swr, then by (swr, lp)
    # Process in order, tracking start rows for merges

    swr_start = {}   # swr -> first excel row
    lp_start  = {}   # (swr, lp) -> first excel row

    for row_data in rows:
        swr, lp, binary, algorithm, metrics = row_data

        # Track swr group start
        if swr not in swr_start:
            swr_start[swr] = current_row

        # Track (swr, lp) group start
        if (swr, lp) not in lp_start:
            lp_start[(swr, lp)] = current_row

        # Col A: swr
        ws.cell(row=current_row, column=1, value=swr)
        apply_style(ws.cell(row=current_row, column=1))

        # Col B: lp
        ws.cell(row=current_row, column=2, value=lp)
        apply_style(ws.cell(row=current_row, column=2))

        # Col C: binary
        if binary is None:
            ws.cell(row=current_row, column=3, value='')
        else:
            ws.cell(row=current_row, column=3, value=binary)
        apply_style(ws.cell(row=current_row, column=3))

        # Col D: algorithm
        ws.cell(row=current_row, column=4, value=algorithm)
        fill = ALGO_FILL.get(algorithm)
        apply_style(ws.cell(row=current_row, column=4), fill=fill)

        # Cols E-H: metrics
        if metrics:
            vals = [
                fmt(metrics['rel_weight_linear_deviation']),
                fmt(metrics['rel_boundary']),
                fmt(metrics['estimator']),
                fmt(metrics['avg_time_per_vertex'], decimals=8),
            ]
        else:
            vals = ['—', '—', '—', '—']

        for col_offset, val in enumerate(vals):
            cell = ws.cell(row=current_row, column=5 + col_offset, value=val)
            fill = ALGO_FILL.get(algorithm) if metrics else NONE_FILL
            apply_style(cell, fill=fill)

        current_row += 1

    # --- Apply merges ---
    # Merge column A for each swr group
    # Determine end rows
    swr_end = {}
    for i, (swr, lp, binary, algorithm, _) in enumerate(rows):
        excel_row = data_start + i
        swr_end[swr] = excel_row

    for swr, start in swr_start.items():
        end = swr_end[swr]
        if end > start:
            ws.merge_cells(
                start_row=start, start_column=1,
                end_row=end,   end_column=1
            )
            ws.cell(row=start, column=1).alignment = CENTER

    # Merge column B for each (swr, lp) group
    lp_end = {}
    for i, (swr, lp, binary, algorithm, _) in enumerate(rows):
        excel_row = data_start + i
        lp_end[(swr, lp)] = excel_row

    for (swr, lp), start in lp_start.items():
        end = lp_end[(swr, lp)]
        if end > start:
            ws.merge_cells(
                start_row=start, start_column=2,
                end_row=end,   end_column=2
            )
            ws.cell(row=start, column=2).alignment = CENTER

    # Freeze header row
    ws.freeze_panes = 'A2'

    wb.save(output_path)
    print(f"Table saved to: {output_path}")
    print(f"Total data rows: {current_row - data_start}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    print(f"Reading results from: {results_dir}")
    rows = build_rows()

    # Count how many rows have actual data
    filled = sum(1 for _, _, _, _, m in rows if m is not None)
    print(f"Rows with data: {filled} / {len(rows)}")

    write_table(rows)


if __name__ == '__main__':
    main()
