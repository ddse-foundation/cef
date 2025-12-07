#!/usr/bin/env python3
"""
CEF Benchmark Evaluation - All Backends Comparison

This script evaluates benchmark results from all graph store backends
(inmemory, neo4j, pgsql, pgage) and generates comparative analysis.

Usage:
    python evaluate_all_backends.py [--results-dir benchmark-results]

Author: mrmanna
Since: v0.6
"""

import argparse
import json
import os
import sys
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Tuple
import statistics

# Default configuration
DEFAULT_RESULTS_DIR = "benchmark-results"
BACKENDS = ["inmemory", "neo4j", "pgsql", "pgage"]
DATASETS = ["medical", "sap"]


@dataclass
class LatencyStats:
    """Latency statistics from benchmark results."""
    min_ms: float
    max_ms: float
    mean_ms: float
    p50_ms: float
    p95_ms: float
    p99_ms: float


@dataclass
class ScenarioResult:
    """Result for a single benchmark scenario."""
    name: str
    iterations: int
    baseline_chunks: int
    cef_chunks: int
    chunk_improvement_pct: float
    latency_overhead_ms: float
    latency_overhead_pct: float
    latency: LatencyStats


@dataclass
class BenchmarkResult:
    """Complete benchmark result from a backend."""
    backend: str
    dataset: str
    timestamp: str
    total_duration_ms: float
    data_load_time_ms: float
    graph_build_time_ms: float
    scenarios: List[ScenarioResult]
    avg_chunk_improvement: float
    avg_latency_overhead: float


@dataclass
class BackendComparison:
    """Comparison metrics across backends."""
    backend: str
    dataset: str
    avg_latency_p50: float
    avg_latency_p95: float
    avg_latency_p99: float
    avg_chunk_improvement: float
    avg_latency_overhead_pct: float
    total_duration: float


@dataclass
class EvaluationReport:
    """Complete evaluation report."""
    generated_at: str
    backends_evaluated: List[str]
    datasets_evaluated: List[str]
    comparisons: List[BackendComparison]
    fastest_backend: Dict[str, str]  # dataset -> backend
    best_chunk_improvement: Dict[str, str]  # dataset -> backend
    lowest_overhead: Dict[str, str]  # dataset -> backend


def load_benchmark_result(filepath: Path) -> Optional[BenchmarkResult]:
    """Load a benchmark result JSON file."""
    try:
        with open(filepath, 'r') as f:
            data = json.load(f)
        
        scenarios = []
        for s in data.get('scenarios', []):
            latency_data = s.get('latency', {})
            latency = LatencyStats(
                min_ms=latency_data.get('minMs', 0),
                max_ms=latency_data.get('maxMs', 0),
                mean_ms=latency_data.get('meanMs', 0),
                p50_ms=latency_data.get('p50Ms', 0),
                p95_ms=latency_data.get('p95Ms', 0),
                p99_ms=latency_data.get('p99Ms', 0)
            )
            scenarios.append(ScenarioResult(
                name=s.get('name', ''),
                iterations=s.get('iterations', 0),
                baseline_chunks=s.get('baselineChunks', 0),
                cef_chunks=s.get('cefChunks', 0),
                chunk_improvement_pct=s.get('chunkImprovementPct', 0),
                latency_overhead_ms=s.get('latencyOverheadMs', 0),
                latency_overhead_pct=s.get('latencyOverheadPct', 0),
                latency=latency
            ))
        
        summary = data.get('summary', {})
        return BenchmarkResult(
            backend=data.get('backend', ''),
            dataset=data.get('dataset', ''),
            timestamp=data.get('timestamp', ''),
            total_duration_ms=data.get('totalDurationMs', 0),
            data_load_time_ms=data.get('dataLoadTimeMs', 0),
            graph_build_time_ms=data.get('graphBuildTimeMs', 0),
            scenarios=scenarios,
            avg_chunk_improvement=summary.get('avgChunkImprovement', 0),
            avg_latency_overhead=summary.get('avgLatencyOverhead', 0)
        )
    except Exception as e:
        print(f"Warning: Failed to load {filepath}: {e}")
        return None


def discover_results(results_dir: Path) -> Dict[str, Dict[str, BenchmarkResult]]:
    """
    Discover and load all benchmark results.
    Returns: {backend: {dataset: BenchmarkResult}}
    """
    results: Dict[str, Dict[str, BenchmarkResult]] = {}
    
    for backend in BACKENDS:
        backend_dir = results_dir / backend
        if not backend_dir.exists():
            continue
        
        results[backend] = {}
        for dataset in DATASETS:
            # Look for most recent result file
            pattern = f"{backend}_{dataset}_*.json"
            files = list(backend_dir.glob(pattern))
            if files:
                # Sort by modification time, get most recent
                most_recent = max(files, key=lambda p: p.stat().st_mtime)
                result = load_benchmark_result(most_recent)
                if result:
                    results[backend][dataset] = result
    
    return results


def compute_comparison(result: BenchmarkResult) -> BackendComparison:
    """Compute comparison metrics from a benchmark result."""
    if not result.scenarios:
        return BackendComparison(
            backend=result.backend,
            dataset=result.dataset,
            avg_latency_p50=0,
            avg_latency_p95=0,
            avg_latency_p99=0,
            avg_chunk_improvement=0,
            avg_latency_overhead_pct=0,
            total_duration=result.total_duration_ms
        )
    
    p50_values = [s.latency.p50_ms for s in result.scenarios]
    p95_values = [s.latency.p95_ms for s in result.scenarios]
    p99_values = [s.latency.p99_ms for s in result.scenarios]
    
    return BackendComparison(
        backend=result.backend,
        dataset=result.dataset,
        avg_latency_p50=statistics.mean(p50_values),
        avg_latency_p95=statistics.mean(p95_values),
        avg_latency_p99=statistics.mean(p99_values),
        avg_chunk_improvement=result.avg_chunk_improvement,
        avg_latency_overhead_pct=result.avg_latency_overhead,
        total_duration=result.total_duration_ms
    )


def generate_evaluation_report(results: Dict[str, Dict[str, BenchmarkResult]]) -> EvaluationReport:
    """Generate evaluation report from all benchmark results."""
    comparisons: List[BackendComparison] = []
    
    # Compute comparisons for all backend/dataset combinations
    for backend, datasets in results.items():
        for dataset, result in datasets.items():
            comparison = compute_comparison(result)
            comparisons.append(comparison)
    
    # Find winners for each metric per dataset
    fastest_backend: Dict[str, str] = {}
    best_chunk_improvement: Dict[str, str] = {}
    lowest_overhead: Dict[str, str] = {}
    
    for dataset in DATASETS:
        dataset_comparisons = [c for c in comparisons if c.dataset == dataset]
        if dataset_comparisons:
            # Fastest = lowest P50 latency
            fastest = min(dataset_comparisons, key=lambda c: c.avg_latency_p50)
            fastest_backend[dataset] = fastest.backend
            
            # Best chunk improvement = highest improvement percentage
            best_improvement = max(dataset_comparisons, key=lambda c: c.avg_chunk_improvement)
            best_chunk_improvement[dataset] = best_improvement.backend
            
            # Lowest overhead = lowest latency overhead percentage
            lowest = min(dataset_comparisons, key=lambda c: c.avg_latency_overhead_pct)
            lowest_overhead[dataset] = lowest.backend
    
    return EvaluationReport(
        generated_at=datetime.now().isoformat(),
        backends_evaluated=list(results.keys()),
        datasets_evaluated=DATASETS,
        comparisons=comparisons,
        fastest_backend=fastest_backend,
        best_chunk_improvement=best_chunk_improvement,
        lowest_overhead=lowest_overhead
    )


def print_report(report: EvaluationReport):
    """Print evaluation report to console."""
    print("\n" + "=" * 80)
    print("CEF BENCHMARK EVALUATION REPORT")
    print("=" * 80)
    print(f"Generated: {report.generated_at}")
    print(f"Backends: {', '.join(report.backends_evaluated)}")
    print(f"Datasets: {', '.join(report.datasets_evaluated)}")
    print()
    
    # Print comparison table
    print("-" * 80)
    print(f"{'Backend':<12} {'Dataset':<10} {'P50 (ms)':<12} {'P95 (ms)':<12} {'Chunk Imp%':<12} {'Overhead%':<12}")
    print("-" * 80)
    
    for c in report.comparisons:
        print(f"{c.backend:<12} {c.dataset:<10} {c.avg_latency_p50:<12.2f} {c.avg_latency_p95:<12.2f} "
              f"{c.avg_chunk_improvement:<12.1f} {c.avg_latency_overhead_pct:<12.1f}")
    
    print("-" * 80)
    print()
    
    # Print winners
    print("WINNERS BY DATASET:")
    print("-" * 40)
    for dataset in report.datasets_evaluated:
        print(f"\n{dataset.upper()} Dataset:")
        if dataset in report.fastest_backend:
            print(f"  🏆 Fastest (P50):       {report.fastest_backend[dataset]}")
        if dataset in report.best_chunk_improvement:
            print(f"  🏆 Best Chunk Savings:  {report.best_chunk_improvement[dataset]}")
        if dataset in report.lowest_overhead:
            print(f"  🏆 Lowest Overhead:     {report.lowest_overhead[dataset]}")
    
    print("\n" + "=" * 80)


def save_report(report: EvaluationReport, output_path: Path):
    """Save evaluation report to JSON."""
    report_dict = {
        'generated_at': report.generated_at,
        'backends_evaluated': report.backends_evaluated,
        'datasets_evaluated': report.datasets_evaluated,
        'comparisons': [
            {
                'backend': c.backend,
                'dataset': c.dataset,
                'avg_latency_p50': c.avg_latency_p50,
                'avg_latency_p95': c.avg_latency_p95,
                'avg_latency_p99': c.avg_latency_p99,
                'avg_chunk_improvement': c.avg_chunk_improvement,
                'avg_latency_overhead_pct': c.avg_latency_overhead_pct,
                'total_duration': c.total_duration
            }
            for c in report.comparisons
        ],
        'winners': {
            'fastest_backend': report.fastest_backend,
            'best_chunk_improvement': report.best_chunk_improvement,
            'lowest_overhead': report.lowest_overhead
        }
    }
    
    with open(output_path, 'w') as f:
        json.dump(report_dict, f, indent=2)
    
    print(f"Report saved to: {output_path}")


def generate_markdown_report(report: EvaluationReport, output_path: Path):
    """Generate Markdown report for documentation."""
    lines = [
        "# CEF Benchmark Evaluation Report",
        "",
        f"**Generated:** {report.generated_at}",
        "",
        "## Executive Summary",
        "",
        "This report compares CEF (Context Engineering Framework) performance across multiple graph store backends.",
        "",
        "### Backends Evaluated",
        "",
    ]
    
    for backend in report.backends_evaluated:
        backend_names = {
            'inmemory': 'In-Memory (JGraphT)',
            'neo4j': 'Neo4j',
            'pgsql': 'PostgreSQL (SQL)',
            'pgage': 'PostgreSQL (Apache AGE)'
        }
        lines.append(f"- **{backend}**: {backend_names.get(backend, backend)}")
    
    lines.extend([
        "",
        "## Performance Comparison",
        "",
        "### Latency Metrics",
        "",
        "| Backend | Dataset | P50 (ms) | P95 (ms) | P99 (ms) | Chunk Improvement | Latency Overhead |",
        "|---------|---------|----------|----------|----------|-------------------|------------------|",
    ])
    
    for c in report.comparisons:
        lines.append(
            f"| {c.backend} | {c.dataset} | {c.avg_latency_p50:.2f} | {c.avg_latency_p95:.2f} | "
            f"{c.avg_latency_p99:.2f} | {c.avg_chunk_improvement:.1f}% | {c.avg_latency_overhead_pct:.1f}% |"
        )
    
    lines.extend([
        "",
        "## Winners by Dataset",
        "",
    ])
    
    for dataset in report.datasets_evaluated:
        lines.append(f"### {dataset.title()} Dataset")
        lines.append("")
        if dataset in report.fastest_backend:
            lines.append(f"- **Fastest (P50 Latency):** {report.fastest_backend[dataset]}")
        if dataset in report.best_chunk_improvement:
            lines.append(f"- **Best Chunk Savings:** {report.best_chunk_improvement[dataset]}")
        if dataset in report.lowest_overhead:
            lines.append(f"- **Lowest Overhead:** {report.lowest_overhead[dataset]}")
        lines.append("")
    
    lines.extend([
        "## Recommendations",
        "",
        "Based on the benchmark results:",
        "",
        "1. **Development/Testing:** Use `inmemory` backend for fastest iteration",
        "2. **Production with Simple Graphs:** Consider `pgsql` for SQL-native environments",
        "3. **Production with Complex Graphs:** Use `neo4j` or `pgage` for graph traversal",
        "4. **Hybrid Workloads:** `pgage` provides both SQL and Cypher query capabilities",
        "",
        "---",
        "",
        f"*Report generated by CEF Benchmark Evaluation v0.6*",
    ])
    
    with open(output_path, 'w') as f:
        f.write('\n'.join(lines))
    
    print(f"Markdown report saved to: {output_path}")


def main():
    parser = argparse.ArgumentParser(
        description='Evaluate CEF benchmark results across all backends'
    )
    parser.add_argument(
        '--results-dir',
        type=str,
        default=DEFAULT_RESULTS_DIR,
        help='Directory containing benchmark results'
    )
    parser.add_argument(
        '--output',
        type=str,
        default='evaluation_report.json',
        help='Output file for evaluation report'
    )
    parser.add_argument(
        '--markdown',
        type=str,
        default='BENCHMARK_EVALUATION.md',
        help='Output file for Markdown report'
    )
    
    args = parser.parse_args()
    
    results_dir = Path(args.results_dir)
    if not results_dir.exists():
        print(f"Error: Results directory not found: {results_dir}")
        sys.exit(1)
    
    print(f"Loading benchmark results from: {results_dir}")
    
    # Discover and load results
    results = discover_results(results_dir)
    
    if not results:
        print("No benchmark results found!")
        sys.exit(1)
    
    print(f"Found results for {len(results)} backends")
    
    # Generate report
    report = generate_evaluation_report(results)
    
    # Print to console
    print_report(report)
    
    # Save reports
    save_report(report, Path(args.output))
    generate_markdown_report(report, Path(args.markdown))


if __name__ == '__main__':
    main()
