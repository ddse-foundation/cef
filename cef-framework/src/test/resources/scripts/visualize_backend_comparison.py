#!/usr/bin/env python3
"""
CEF Benchmark Visualization - Backend Comparison Charts

This script generates visualization charts comparing benchmark results
across all graph store backends (inmemory, neo4j, pgsql, pgage).

Requires: matplotlib, pandas, numpy

Usage:
    python visualize_backend_comparison.py [--results-dir benchmark-results] [--output-dir charts]

Author: mrmanna
Since: v0.6
"""

import argparse
import json
import os
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Tuple

try:
    import matplotlib.pyplot as plt
    import matplotlib.patches as mpatches
    import numpy as np
    HAS_MATPLOTLIB = True
except ImportError:
    HAS_MATPLOTLIB = False
    print("Warning: matplotlib not installed. Install with: pip install matplotlib")

try:
    import pandas as pd
    HAS_PANDAS = True
except ImportError:
    HAS_PANDAS = False
    print("Warning: pandas not installed. Install with: pip install pandas")


# Configuration
DEFAULT_RESULTS_DIR = "benchmark-results"
DEFAULT_OUTPUT_DIR = "benchmark-charts"
BACKENDS = ["inmemory", "neo4j", "pgsql", "pgage"]
DATASETS = ["medical", "sap"]

# Color scheme for backends
BACKEND_COLORS = {
    'inmemory': '#4CAF50',  # Green
    'neo4j': '#2196F3',     # Blue
    'pgsql': '#FF9800',     # Orange
    'pgage': '#9C27B0'      # Purple
}

BACKEND_LABELS = {
    'inmemory': 'In-Memory (JGraphT)',
    'neo4j': 'Neo4j',
    'pgsql': 'PostgreSQL SQL',
    'pgage': 'PostgreSQL AGE'
}


@dataclass
class BenchmarkData:
    """Parsed benchmark data for visualization."""
    backend: str
    dataset: str
    scenarios: List[str]
    p50_latencies: List[float]
    p95_latencies: List[float]
    p99_latencies: List[float]
    chunk_improvements: List[float]
    latency_overheads: List[float]


def load_benchmark_data(results_dir: Path) -> List[BenchmarkData]:
    """Load all benchmark data from results directory."""
    data_list = []
    
    for backend in BACKENDS:
        backend_dir = results_dir / backend
        if not backend_dir.exists():
            continue
        
        for dataset in DATASETS:
            pattern = f"{backend}_{dataset}_*.json"
            files = list(backend_dir.glob(pattern))
            if not files:
                continue
            
            # Get most recent file
            most_recent = max(files, key=lambda p: p.stat().st_mtime)
            
            try:
                with open(most_recent, 'r') as f:
                    result = json.load(f)
                
                scenarios = result.get('scenarios', [])
                data_list.append(BenchmarkData(
                    backend=backend,
                    dataset=dataset,
                    scenarios=[s['name'] for s in scenarios],
                    p50_latencies=[s.get('latency', {}).get('p50Ms', 0) for s in scenarios],
                    p95_latencies=[s.get('latency', {}).get('p95Ms', 0) for s in scenarios],
                    p99_latencies=[s.get('latency', {}).get('p99Ms', 0) for s in scenarios],
                    chunk_improvements=[s.get('chunkImprovementPct', 0) for s in scenarios],
                    latency_overheads=[s.get('latencyOverheadPct', 0) for s in scenarios]
                ))
            except Exception as e:
                print(f"Warning: Failed to load {most_recent}: {e}")
    
    return data_list


def create_latency_comparison_chart(data_list: List[BenchmarkData], dataset: str, output_dir: Path):
    """Create grouped bar chart comparing latencies across backends."""
    if not HAS_MATPLOTLIB:
        return
    
    dataset_data = [d for d in data_list if d.dataset == dataset]
    if not dataset_data:
        print(f"No data for dataset: {dataset}")
        return
    
    # Get scenario names from first backend
    scenarios = dataset_data[0].scenarios if dataset_data else []
    if not scenarios:
        return
    
    fig, ax = plt.subplots(figsize=(14, 8))
    
    x = np.arange(len(scenarios))
    width = 0.2
    multiplier = 0
    
    for data in dataset_data:
        if len(data.p50_latencies) != len(scenarios):
            continue
        
        offset = width * multiplier
        bars = ax.bar(x + offset, data.p50_latencies, width,
                     label=BACKEND_LABELS.get(data.backend, data.backend),
                     color=BACKEND_COLORS.get(data.backend, '#666666'))
        
        # Add value labels on bars
        for bar, val in zip(bars, data.p50_latencies):
            height = bar.get_height()
            ax.annotate(f'{val:.1f}',
                       xy=(bar.get_x() + bar.get_width() / 2, height),
                       xytext=(0, 3),
                       textcoords="offset points",
                       ha='center', va='bottom', fontsize=8)
        
        multiplier += 1
    
    ax.set_ylabel('Latency (ms)', fontsize=12)
    ax.set_xlabel('Benchmark Scenarios', fontsize=12)
    ax.set_title(f'P50 Latency Comparison - {dataset.title()} Dataset', fontsize=14, fontweight='bold')
    ax.set_xticks(x + width * (len(dataset_data) - 1) / 2)
    ax.set_xticklabels(scenarios, rotation=45, ha='right')
    ax.legend(loc='upper right')
    ax.grid(axis='y', alpha=0.3)
    
    plt.tight_layout()
    
    output_path = output_dir / f'latency_comparison_{dataset}.png'
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()
    print(f"Saved: {output_path}")


def create_chunk_improvement_chart(data_list: List[BenchmarkData], dataset: str, output_dir: Path):
    """Create chart showing chunk improvement percentages."""
    if not HAS_MATPLOTLIB:
        return
    
    dataset_data = [d for d in data_list if d.dataset == dataset]
    if not dataset_data:
        return
    
    scenarios = dataset_data[0].scenarios if dataset_data else []
    if not scenarios:
        return
    
    fig, ax = plt.subplots(figsize=(14, 8))
    
    x = np.arange(len(scenarios))
    width = 0.2
    multiplier = 0
    
    for data in dataset_data:
        if len(data.chunk_improvements) != len(scenarios):
            continue
        
        offset = width * multiplier
        bars = ax.bar(x + offset, data.chunk_improvements, width,
                     label=BACKEND_LABELS.get(data.backend, data.backend),
                     color=BACKEND_COLORS.get(data.backend, '#666666'))
        
        multiplier += 1
    
    ax.set_ylabel('Chunk Improvement (%)', fontsize=12)
    ax.set_xlabel('Benchmark Scenarios', fontsize=12)
    ax.set_title(f'Context Chunk Savings - {dataset.title()} Dataset', fontsize=14, fontweight='bold')
    ax.set_xticks(x + width * (len(dataset_data) - 1) / 2)
    ax.set_xticklabels(scenarios, rotation=45, ha='right')
    ax.legend(loc='upper right')
    ax.grid(axis='y', alpha=0.3)
    ax.axhline(y=0, color='black', linestyle='-', linewidth=0.5)
    
    plt.tight_layout()
    
    output_path = output_dir / f'chunk_improvement_{dataset}.png'
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()
    print(f"Saved: {output_path}")


def create_latency_percentile_chart(data_list: List[BenchmarkData], output_dir: Path):
    """Create chart showing P50/P95/P99 latency percentiles per backend."""
    if not HAS_MATPLOTLIB:
        return
    
    fig, axes = plt.subplots(1, 2, figsize=(16, 6))
    
    for ax, dataset in zip(axes, DATASETS):
        dataset_data = [d for d in data_list if d.dataset == dataset]
        if not dataset_data:
            continue
        
        backends = []
        p50_means = []
        p95_means = []
        p99_means = []
        
        for data in dataset_data:
            backends.append(data.backend)
            p50_means.append(np.mean(data.p50_latencies) if data.p50_latencies else 0)
            p95_means.append(np.mean(data.p95_latencies) if data.p95_latencies else 0)
            p99_means.append(np.mean(data.p99_latencies) if data.p99_latencies else 0)
        
        x = np.arange(len(backends))
        width = 0.25
        
        bars1 = ax.bar(x - width, p50_means, width, label='P50', color='#4CAF50')
        bars2 = ax.bar(x, p95_means, width, label='P95', color='#FF9800')
        bars3 = ax.bar(x + width, p99_means, width, label='P99', color='#F44336')
        
        ax.set_ylabel('Average Latency (ms)', fontsize=11)
        ax.set_xlabel('Backend', fontsize=11)
        ax.set_title(f'Latency Percentiles - {dataset.title()} Dataset', fontsize=12, fontweight='bold')
        ax.set_xticks(x)
        ax.set_xticklabels([BACKEND_LABELS.get(b, b) for b in backends], rotation=30, ha='right')
        ax.legend()
        ax.grid(axis='y', alpha=0.3)
    
    plt.tight_layout()
    
    output_path = output_dir / 'latency_percentiles_all.png'
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()
    print(f"Saved: {output_path}")


def create_overhead_comparison_chart(data_list: List[BenchmarkData], output_dir: Path):
    """Create chart comparing latency overhead across backends."""
    if not HAS_MATPLOTLIB:
        return
    
    fig, ax = plt.subplots(figsize=(12, 6))
    
    # Group data by dataset
    data_by_dataset = {ds: [] for ds in DATASETS}
    for data in data_list:
        data_by_dataset[data.dataset].append(data)
    
    x = np.arange(len(BACKENDS))
    width = 0.35
    
    colors = {'medical': '#2196F3', 'sap': '#FF9800'}
    
    for i, (dataset, dataset_data) in enumerate(data_by_dataset.items()):
        overheads = []
        for backend in BACKENDS:
            backend_data = next((d for d in dataset_data if d.backend == backend), None)
            if backend_data and backend_data.latency_overheads:
                overheads.append(np.mean(backend_data.latency_overheads))
            else:
                overheads.append(0)
        
        offset = (i - 0.5) * width
        bars = ax.bar(x + offset, overheads, width, label=f'{dataset.title()} Dataset',
                     color=colors.get(dataset, '#666666'))
    
    ax.set_ylabel('Average Latency Overhead (%)', fontsize=12)
    ax.set_xlabel('Backend', fontsize=12)
    ax.set_title('CEF Latency Overhead by Backend', fontsize=14, fontweight='bold')
    ax.set_xticks(x)
    ax.set_xticklabels([BACKEND_LABELS.get(b, b) for b in BACKENDS], rotation=30, ha='right')
    ax.legend()
    ax.grid(axis='y', alpha=0.3)
    ax.axhline(y=0, color='black', linestyle='-', linewidth=0.5)
    
    plt.tight_layout()
    
    output_path = output_dir / 'overhead_comparison.png'
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()
    print(f"Saved: {output_path}")


def create_summary_dashboard(data_list: List[BenchmarkData], output_dir: Path):
    """Create a summary dashboard with multiple metrics."""
    if not HAS_MATPLOTLIB:
        return
    
    fig, axes = plt.subplots(2, 2, figsize=(16, 12))
    
    # Top-left: Average P50 latency per backend
    ax = axes[0, 0]
    backends = []
    p50_avgs = []
    colors = []
    
    for backend in BACKENDS:
        backend_data = [d for d in data_list if d.backend == backend]
        if backend_data:
            all_p50 = []
            for d in backend_data:
                all_p50.extend(d.p50_latencies)
            backends.append(BACKEND_LABELS.get(backend, backend))
            p50_avgs.append(np.mean(all_p50) if all_p50 else 0)
            colors.append(BACKEND_COLORS.get(backend, '#666666'))
    
    ax.barh(backends, p50_avgs, color=colors)
    ax.set_xlabel('Average P50 Latency (ms)', fontsize=11)
    ax.set_title('Average Query Latency by Backend', fontsize=12, fontweight='bold')
    ax.grid(axis='x', alpha=0.3)
    
    # Top-right: Average chunk improvement per backend
    ax = axes[0, 1]
    backends = []
    improvements = []
    colors = []
    
    for backend in BACKENDS:
        backend_data = [d for d in data_list if d.backend == backend]
        if backend_data:
            all_improvements = []
            for d in backend_data:
                all_improvements.extend(d.chunk_improvements)
            backends.append(BACKEND_LABELS.get(backend, backend))
            improvements.append(np.mean(all_improvements) if all_improvements else 0)
            colors.append(BACKEND_COLORS.get(backend, '#666666'))
    
    ax.barh(backends, improvements, color=colors)
    ax.set_xlabel('Average Chunk Improvement (%)', fontsize=11)
    ax.set_title('Context Optimization Effectiveness', fontsize=12, fontweight='bold')
    ax.grid(axis='x', alpha=0.3)
    ax.axvline(x=0, color='black', linestyle='-', linewidth=0.5)
    
    # Bottom-left: Latency overhead comparison
    ax = axes[1, 0]
    backends = []
    overheads = []
    colors = []
    
    for backend in BACKENDS:
        backend_data = [d for d in data_list if d.backend == backend]
        if backend_data:
            all_overheads = []
            for d in backend_data:
                all_overheads.extend(d.latency_overheads)
            backends.append(BACKEND_LABELS.get(backend, backend))
            overheads.append(np.mean(all_overheads) if all_overheads else 0)
            colors.append(BACKEND_COLORS.get(backend, '#666666'))
    
    ax.barh(backends, overheads, color=colors)
    ax.set_xlabel('Average Latency Overhead (%)', fontsize=11)
    ax.set_title('CEF Processing Overhead', fontsize=12, fontweight='bold')
    ax.grid(axis='x', alpha=0.3)
    ax.axvline(x=0, color='black', linestyle='-', linewidth=0.5)
    
    # Bottom-right: Scenario count per backend
    ax = axes[1, 1]
    backend_scenario_counts = {}
    for data in data_list:
        key = BACKEND_LABELS.get(data.backend, data.backend)
        if key not in backend_scenario_counts:
            backend_scenario_counts[key] = 0
        backend_scenario_counts[key] += len(data.scenarios)
    
    if backend_scenario_counts:
        ax.pie(list(backend_scenario_counts.values()),
               labels=list(backend_scenario_counts.keys()),
               autopct='%1.1f%%',
               colors=[BACKEND_COLORS.get(b, '#666666') for b in BACKENDS if BACKEND_LABELS.get(b, b) in backend_scenario_counts])
        ax.set_title('Scenarios Executed by Backend', fontsize=12, fontweight='bold')
    
    plt.suptitle('CEF Benchmark Summary Dashboard', fontsize=16, fontweight='bold', y=1.02)
    plt.tight_layout()
    
    output_path = output_dir / 'benchmark_dashboard.png'
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()
    print(f"Saved: {output_path}")


def main():
    parser = argparse.ArgumentParser(
        description='Generate visualization charts for CEF benchmark results'
    )
    parser.add_argument(
        '--results-dir',
        type=str,
        default=DEFAULT_RESULTS_DIR,
        help='Directory containing benchmark results'
    )
    parser.add_argument(
        '--output-dir',
        type=str,
        default=DEFAULT_OUTPUT_DIR,
        help='Output directory for charts'
    )
    
    args = parser.parse_args()
    
    if not HAS_MATPLOTLIB:
        print("Error: matplotlib is required for visualization")
        print("Install with: pip install matplotlib")
        sys.exit(1)
    
    results_dir = Path(args.results_dir)
    output_dir = Path(args.output_dir)
    
    if not results_dir.exists():
        print(f"Error: Results directory not found: {results_dir}")
        sys.exit(1)
    
    # Create output directory
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"Loading benchmark data from: {results_dir}")
    print(f"Saving charts to: {output_dir}")
    
    # Load data
    data_list = load_benchmark_data(results_dir)
    
    if not data_list:
        print("No benchmark data found!")
        sys.exit(1)
    
    print(f"Found {len(data_list)} benchmark result sets")
    
    # Generate charts
    print("\nGenerating charts...")
    
    for dataset in DATASETS:
        create_latency_comparison_chart(data_list, dataset, output_dir)
        create_chunk_improvement_chart(data_list, dataset, output_dir)
    
    create_latency_percentile_chart(data_list, output_dir)
    create_overhead_comparison_chart(data_list, output_dir)
    create_summary_dashboard(data_list, output_dir)
    
    print("\nVisualization complete!")
    print(f"Charts saved to: {output_dir}")


if __name__ == '__main__':
    main()
