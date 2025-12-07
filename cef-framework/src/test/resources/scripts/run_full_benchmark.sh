#!/bin/bash
# ==============================================================================
# CEF Full Benchmark Suite Runner
# ==============================================================================
# This script runs all benchmark tests across all graph store backends and
# generates evaluation reports and visualization charts.
#
# Usage:
#   ./run_full_benchmark.sh [OPTIONS]
#
# Options:
#   --backends    Comma-separated list of backends (default: all)
#   --datasets    Comma-separated list of datasets (default: all)
#   --skip-tests  Skip running Java tests (use existing results)
#   --skip-charts Skip generating visualization charts
#   --help        Show this help message
#
# Author: mrmanna
# Since: v0.6
# ==============================================================================

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
RESULTS_DIR="$PROJECT_ROOT/benchmark-results"
CHARTS_DIR="$PROJECT_ROOT/benchmark-charts"
PYTHON_SCRIPTS="$SCRIPT_DIR"

# Default options
BACKENDS="inmemory,neo4j,pgsql,pgage"
DATASETS="medical,sap"
SKIP_TESTS=false
SKIP_CHARTS=false

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
print_header() {
    echo -e "${BLUE}======================================================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}======================================================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

show_help() {
    head -30 "$0" | tail -20
    exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --backends)
            BACKENDS="$2"
            shift 2
            ;;
        --datasets)
            DATASETS="$2"
            shift 2
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --skip-charts)
            SKIP_CHARTS=true
            shift
            ;;
        --help)
            show_help
            ;;
        *)
            print_error "Unknown option: $1"
            show_help
            ;;
    esac
done

# ==============================================================================
# Main Script
# ==============================================================================

print_header "CEF Full Benchmark Suite"
echo "Started at: $(date)"
echo "Project root: $PROJECT_ROOT"
echo "Backends: $BACKENDS"
echo "Datasets: $DATASETS"
echo ""

# Create results directory
mkdir -p "$RESULTS_DIR"
for backend in ${BACKENDS//,/ }; do
    mkdir -p "$RESULTS_DIR/$backend"
done

# ==============================================================================
# Step 1: Run Java Benchmark Tests
# ==============================================================================

if [ "$SKIP_TESTS" = false ]; then
    print_header "Step 1: Running Java Benchmark Tests"
    
    cd "$PROJECT_ROOT"
    
    # Convert comma-separated backends to test filter pattern
    IFS=',' read -ra BACKEND_ARRAY <<< "$BACKENDS"
    
    for backend in "${BACKEND_ARRAY[@]}"; do
        echo ""
        echo -e "${YELLOW}Running $backend benchmarks...${NC}"
        
        case $backend in
            inmemory)
                TEST_CLASS="org.ddse.ml.cef.benchmark.runner.InMemoryBenchmarkIT"
                ;;
            neo4j)
                TEST_CLASS="org.ddse.ml.cef.benchmark.runner.Neo4jBenchmarkIT"
                ;;
            pgsql)
                TEST_CLASS="org.ddse.ml.cef.benchmark.runner.PgSqlBenchmarkIT"
                ;;
            pgage)
                TEST_CLASS="org.ddse.ml.cef.benchmark.runner.PgAgeBenchmarkIT"
                ;;
            *)
                print_warning "Unknown backend: $backend"
                continue
                ;;
        esac
        
        if mvn test -Dtest="$TEST_CLASS" -DfailIfNoTests=false -q; then
            print_success "$backend benchmarks completed"
        else
            print_warning "$backend benchmarks failed or skipped"
        fi
    done
else
    print_warning "Skipping Java tests (--skip-tests specified)"
fi

# ==============================================================================
# Step 2: Generate Evaluation Report
# ==============================================================================

print_header "Step 2: Generating Evaluation Report"

cd "$PROJECT_ROOT"

# Check if Python is available
if command -v python3 &> /dev/null; then
    PYTHON_CMD="python3"
elif command -v python &> /dev/null; then
    PYTHON_CMD="python"
else
    print_error "Python not found. Skipping evaluation report."
    PYTHON_CMD=""
fi

if [ -n "$PYTHON_CMD" ]; then
    echo "Using Python: $PYTHON_CMD"
    
    if [ -f "$PYTHON_SCRIPTS/evaluate_all_backends.py" ]; then
        $PYTHON_CMD "$PYTHON_SCRIPTS/evaluate_all_backends.py" \
            --results-dir "$RESULTS_DIR" \
            --output "$RESULTS_DIR/evaluation_report.json" \
            --markdown "$RESULTS_DIR/BENCHMARK_EVALUATION.md"
        
        if [ -f "$RESULTS_DIR/evaluation_report.json" ]; then
            print_success "Evaluation report generated"
        fi
    else
        print_warning "Evaluation script not found: $PYTHON_SCRIPTS/evaluate_all_backends.py"
    fi
fi

# ==============================================================================
# Step 3: Generate Visualization Charts
# ==============================================================================

if [ "$SKIP_CHARTS" = false ]; then
    print_header "Step 3: Generating Visualization Charts"
    
    if [ -n "$PYTHON_CMD" ]; then
        # Check for matplotlib
        if $PYTHON_CMD -c "import matplotlib" 2>/dev/null; then
            if [ -f "$PYTHON_SCRIPTS/visualize_backend_comparison.py" ]; then
                $PYTHON_CMD "$PYTHON_SCRIPTS/visualize_backend_comparison.py" \
                    --results-dir "$RESULTS_DIR" \
                    --output-dir "$CHARTS_DIR"
                
                if [ -d "$CHARTS_DIR" ] && [ "$(ls -A $CHARTS_DIR)" ]; then
                    print_success "Visualization charts generated"
                fi
            else
                print_warning "Visualization script not found"
            fi
        else
            print_warning "matplotlib not installed. Install with: pip install matplotlib"
        fi
    fi
else
    print_warning "Skipping charts (--skip-charts specified)"
fi

# ==============================================================================
# Summary
# ==============================================================================

print_header "Benchmark Suite Complete"

echo ""
echo "Results Summary:"
echo "----------------"

# Count result files
TOTAL_RESULTS=0
for backend in ${BACKENDS//,/ }; do
    COUNT=$(find "$RESULTS_DIR/$backend" -name "*.json" 2>/dev/null | wc -l)
    if [ "$COUNT" -gt 0 ]; then
        echo "  $backend: $COUNT result files"
        TOTAL_RESULTS=$((TOTAL_RESULTS + COUNT))
    fi
done

echo ""
echo "Total result files: $TOTAL_RESULTS"

if [ -f "$RESULTS_DIR/evaluation_report.json" ]; then
    echo ""
    echo "Reports generated:"
    echo "  - $RESULTS_DIR/evaluation_report.json"
    echo "  - $RESULTS_DIR/BENCHMARK_EVALUATION.md"
fi

if [ -d "$CHARTS_DIR" ] && [ "$(ls -A $CHARTS_DIR 2>/dev/null)" ]; then
    CHART_COUNT=$(find "$CHARTS_DIR" -name "*.png" | wc -l)
    echo ""
    echo "Charts generated: $CHART_COUNT"
    echo "  Location: $CHARTS_DIR/"
fi

echo ""
echo "Completed at: $(date)"
print_success "Done!"
