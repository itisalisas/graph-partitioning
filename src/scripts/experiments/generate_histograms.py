#!/usr/bin/env python3
"""
Generate histograms from a results_vN experiment directory.

For each value of starting_weight_ratio, produces 4 histograms:
  - relative_weight_linear_deviation
  - relative_boundary_length
  - estimator_region_number
  - average_time_per_vertex

Algorithms are shown in order: IF, BUP, DIF, RIF_{lp}_{bs} / RIF_{lp}
where lp = length_priority, bs = with_binary_search / no_binary_search.

Usage:
  python generate_histograms.py <results_dir> [--output-dir <dir>]

  <results_dir>  path to results_vN directory
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

try:
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    import matplotlib.patches as mpatches
    import numpy as np
except ImportError:
    print("matplotlib and numpy are required: pip install matplotlib numpy")
    sys.exit(1)

# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------
parser = argparse.ArgumentParser(description='Generate histograms from results_vN directory')
parser.add_argument('results_dir', type=str,
                    help='Path to results_vN directory (e.g. src/main/output/results_v1)')
parser.add_argument('--output-dir', type=str, default=None,
                    help='Output directory for PNG files (default: <results_dir>/histograms)')
args = parser.parse_args()

results_dir = Path(args.results_dir)
if not results_dir.exists():
    print(f"Error: directory not found: {results_dir}")
    sys.exit(1)

output_dir = Path(args.output_dir) if args.output_dir else results_dir / 'histograms'
output_dir.mkdir(parents=True, exist_ok=True)

# ---------------------------------------------------------------------------
# Parameter definitions (must match run_all_experiments.py)
# ---------------------------------------------------------------------------
SWR_VALUES    = ['0.1', '0.2', '0.3', '0.4']
LP_VALUES     = ['0.0', '0.25', '0.5', '0.75', '1.0']
BINARY_VALUES = [True, False]

DEFAULT_SWR = '0.25'
DEFAULT_LP  = '0.5'

METRICS = [
    ('rel_weight_linear_deviation', 'relative_weight_linear_deviation'),
    ('rel_boundary',                'relative_boundary_length'),
    ('estimator',                   'estimator_region_number'),
    ('avg_time_per_vertex',         'average_time_per_vertex'),
]

METRIC_LABELS = {
    'relative_weight_linear_deviation': r'$\frac{\Delta_w \cdot k}{w_{total}}$',
    'relative_boundary_length':         r'$\frac{L_{cut}}{\sqrt{k}\,L}$',
    'estimator_region_number':          r'$\frac{N}{N_{opt}}$',
    'average_time_per_vertex':          r'$\frac{T}{|V|}$ (сек)',
}

# ---------------------------------------------------------------------------
# Data loading (same logic as generate_results_table.py)
# ---------------------------------------------------------------------------
def mean_present(values):
    present = [v for v in values if v is not None]
    if not present:
        return None
    return sum(present) / len(present)


def load_metrics(algo_dir: Path, swr: str, lp: str, binary: bool):
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
                    if total_weight and total_weight > 0 else None
                ),
                'rel_boundary': (
                    d.get('totalBoundaryLength', 0) / boundary_denominator / (k ** 0.5)
                    if boundary_denominator > 0 else None
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

    return {
        'rel_weight_linear_deviation': mean_present(r['rel_weight_linear_deviation'] for r in records),
        'rel_boundary':                mean_present(r['rel_boundary'] for r in records),
        'estimator':                   mean_present(r['estimator'] for r in records),
        'avg_time_per_vertex':         mean_present(r['avg_time_per_vertex'] for r in records),
        'n': len(records),
    }


# ---------------------------------------------------------------------------
# Build bar entries for a given swr
# Each entry: (label, metrics_dict_or_None, color)
# Order: IF, BUP, DIF, RIF_{lp}_{bs} / RIF_{lp}
# ---------------------------------------------------------------------------
# Color palette
COLOR_IF  = '#F4A460'   # sandy brown / orange
COLOR_BUP = '#90EE90'   # light green
COLOR_DIF = '#F48FB1'   # pink (Material Pink-200) — clearly distinct from blue RIF family

# RIF colors: all in the indigo/blue family so they look visually grouped.
# Each lp gets a lighter shade (binary=True) and a darker shade (binary=False)
# of the same hue, progressing from lightest (lp=0.0) to darkest (lp=1.0).
RIF_BASE_COLORS = {
    '0.0':  ('#C5CAE9', '#9FA8DA'),   # indigo-100 / indigo-200
    '0.25': ('#9FA8DA', '#7986CB'),   # indigo-200 / indigo-300
    '0.5':  ('#7986CB', '#5C6BC0'),   # indigo-300 / indigo-400 (default lp)
    '0.75': ('#5C6BC0', '#3949AB'),   # indigo-400 / indigo-600
    '1.0':  ('#3949AB', '#283593'),   # indigo-600 / indigo-800
}


def build_bar_entries(swr: str):
    entries = []

    # IF
    if_dir = results_dir / 'IF'
    if_m = load_metrics(if_dir, swr, DEFAULT_LP, False)
    entries.append(('IF', if_m, COLOR_IF))

    # BUP (no swr/lp/binary dependency — use defaults)
    bup_dir = results_dir / 'BUS'
    bup_m = load_metrics(bup_dir, DEFAULT_SWR, DEFAULT_LP, False)
    entries.append(('BUS', bup_m, COLOR_BUP))

    # DIF
    dif_dir = results_dir / 'DIF'
    dif_m = load_metrics(dif_dir, swr, DEFAULT_LP, False)
    entries.append(('DIF', dif_m, COLOR_DIF))

    # RIF: all lp x binary combinations
    rif_dir = results_dir / 'RIF'
    for lp in LP_VALUES:
        color_bs, color_no_bs = RIF_BASE_COLORS[lp]
        for binary in BINARY_VALUES:
            m = load_metrics(rif_dir, swr, lp, binary)
            bs_suffix = 'bin=T' if binary else 'bin=F'
            label = f'RIF_{lp}_{bs_suffix}'
            color = color_bs if binary else color_no_bs
            entries.append((label, m, color))

    return entries


# ---------------------------------------------------------------------------
# Plot histograms for one swr value
# ---------------------------------------------------------------------------
def plot_swr(swr: str):
    entries = build_bar_entries(swr)

    labels  = [e[0] for e in entries]
    metrics_list = [e[1] for e in entries]
    colors  = [e[2] for e in entries]

    n_bars = len(labels)
    x = np.arange(n_bars)

    fig, axes = plt.subplots(2, 2, figsize=(max(24, n_bars * 1.1), 16))
    fig.suptitle(f'$b = {swr}$',
                 fontsize=24, fontweight='bold', y=1.01)

    metric_keys_display = [
        ('rel_weight_linear_deviation', 'relative_weight_linear_deviation'),
        ('rel_boundary',                'relative_boundary_length'),
        ('estimator',                   'estimator_region_number'),
        ('avg_time_per_vertex',         'average_time_per_vertex'),
    ]

    for ax, (internal_key, display_key) in zip(axes.flat, metric_keys_display):
        values = [
            (m[internal_key] if m and m.get(internal_key) is not None else 0.0)
            for m in metrics_list
        ]
        missing = [m is None or m.get(internal_key) is None for m in metrics_list]

        bars = ax.bar(x, values, color=colors, edgecolor='grey', linewidth=0.5, zorder=3)

        # Hatch missing bars
        for bar, is_missing in zip(bars, missing):
            if is_missing:
                bar.set_hatch('//')
                bar.set_alpha(0.4)

        ax.set_title(METRIC_LABELS[display_key], fontsize=22, pad=12)
        ax.set_xticks(x)
        ax.set_xticklabels(labels, rotation=45, ha='right', fontsize=13)
        ax.yaxis.grid(True, linestyle='--', alpha=0.6, zorder=0)
        ax.tick_params(axis='y', labelsize=13)
        ax.set_axisbelow(True)

        # Value labels on bars
        for bar, val, is_missing in zip(bars, values, missing):
            if not is_missing and val > 0:
                ax.text(
                    bar.get_x() + bar.get_width() / 2,
                    bar.get_height() * 1.01,
                    f'{val:.4f}' if val < 10 else f'{val:.2f}',
                    ha='center', va='bottom', fontsize=12, rotation=0
                )

        # Separator lines between algorithm groups
        # Groups: IF(1), BUP(1), DIF(1), then RIF blocks of 2 per lp
        group_boundaries = [0.5, 1.5, 2.5]  # after IF, BUP, DIF
        for lp_idx in range(len(LP_VALUES) - 1):
            group_boundaries.append(2.5 + (lp_idx + 1) * 2)
        for xb in group_boundaries:
            ax.axvline(x=xb, color='black', linewidth=0.8, linestyle=':', alpha=0.5)

    # Legend for RIF lp groups
    legend_patches = [
        mpatches.Patch(color=COLOR_IF,  label='IF'),
        mpatches.Patch(color=COLOR_BUP, label='BUS'),
        mpatches.Patch(color=COLOR_DIF, label='DIF'),
    ]
    for lp in LP_VALUES:
        c_bs, c_no = RIF_BASE_COLORS[lp]
        legend_patches.append(mpatches.Patch(color=c_bs,  label=f'RIF lp={lp} bin=T'))
        legend_patches.append(mpatches.Patch(color=c_no,  label=f'RIF lp={lp} bin=F'))

    fig.legend(
        handles=legend_patches,
        loc='lower center',
        ncol=4,
        fontsize=14,
        bbox_to_anchor=(0.5, -0.02),
        frameon=True,
    )

    plt.tight_layout()
    plt.subplots_adjust(bottom=0.2, hspace=0.45)
    out_file = output_dir / f'histograms_swr_{swr.replace(".", "_")}.png'
    plt.savefig(out_file, dpi=150, bbox_inches='tight')
    plt.close(fig)
    print(f"Saved: {out_file}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    print(f"Reading results from: {results_dir}")
    print(f"Output directory:     {output_dir}")
    for swr in SWR_VALUES:
        plot_swr(swr)
    print("Done.")


if __name__ == '__main__':
    main()
