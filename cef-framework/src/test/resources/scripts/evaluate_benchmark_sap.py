#!/usr/bin/env python3
import matplotlib.pyplot as plt
import numpy as np

# Data from SAP_BENCHMARK_REPORT.md
scenarios = [
    'Scenario 1:\nCross-Project\nResource\nAllocation',
    'Scenario 2:\nCost Center\nContagion\nAnalysis'
]

vector_chunks = [5, 5]
km_chunks = [8, 8]
vector_latency = [51, 18]
km_latency = [56, 29]

# Calculate improvements
chunk_improvement = [(km - vec) / vec * 100 for vec, km in zip(vector_chunks, km_chunks)]
avg_chunk_improvement = sum(chunk_improvement) / len(chunk_improvement)

# Create figure with 2 subplots
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 6))

# Plot 1: Chunks Retrieved Comparison
x = np.arange(len(scenarios))
width = 0.35

bars1 = ax1.bar(x - width/2, vector_chunks, width, label='Vector-Only (Naive RAG)', 
                color='#ff6b6b', alpha=0.8)
bars2 = ax1.bar(x + width/2, km_chunks, width, label='Knowledge Model (Graph RAG)', 
                color='#4ecdc4', alpha=0.8)

# Add value labels on bars
for bars in [bars1, bars2]:
    for bar in bars:
        height = bar.get_height()
        ax1.text(bar.get_x() + bar.get_width()/2., height,
                f'{int(height)}',
                ha='center', va='bottom', fontweight='bold', fontsize=10)

# Add improvement percentages
for i, (vec, km) in enumerate(zip(vector_chunks, km_chunks)):
    improvement = ((km - vec) / vec) * 100
    ax1.text(i, max(vec, km) + 0.5, f'+{improvement:.0f}%', 
            ha='center', va='bottom', color='green', fontweight='bold', fontsize=11)

ax1.set_xlabel('SAP ERP Scenarios', fontsize=12, fontweight='bold')
ax1.set_ylabel('Number of Chunks Retrieved', fontsize=12, fontweight='bold')
ax1.set_title('SAP: Knowledge Model Discovers Organizational Structure\n(Department→CostCenter Hierarchies vs Vector Search)', 
             fontsize=13, fontweight='bold', pad=20)
ax1.set_xticks(x)
ax1.set_xticklabels(scenarios, fontsize=9)
ax1.legend(loc='upper left', fontsize=11)
ax1.grid(axis='y', linestyle='--', alpha=0.3)
ax1.set_ylim(0, max(km_chunks) + 2)

# Plot 2: Latency Comparison
bars3 = ax2.bar(x - width/2, vector_latency, width, label='Vector-Only', 
                color='#ff6b6b', alpha=0.8)
bars4 = ax2.bar(x + width/2, km_latency, width, label='Knowledge Model', 
                color='#4ecdc4', alpha=0.8)

# Add value labels
for bars in [bars3, bars4]:
    for bar in bars:
        height = bar.get_height()
        ax2.text(bar.get_x() + bar.get_width()/2., height,
                f'{int(height)}ms',
                ha='center', va='bottom', fontweight='bold', fontsize=9)

ax2.set_xlabel('SAP ERP Scenarios', fontsize=12, fontweight='bold')
ax2.set_ylabel('Latency (milliseconds)', fontsize=12, fontweight='bold')
ax2.set_title('SAP Latency: Acceptable Overhead for Structural Discovery\n(Graph Traversal Cost vs Pure Vector)', 
             fontsize=13, fontweight='bold', pad=20)
ax2.set_xticks(x)
ax2.set_xticklabels(scenarios, fontsize=9)
ax2.legend(loc='upper left', fontsize=11)
ax2.grid(axis='y', linestyle='--', alpha=0.3)
ax2.set_ylim(0, max(km_latency) + 10)

plt.tight_layout()
plt.savefig('results/sap_benchmark_comparison.png', dpi=300, bbox_inches='tight')
print("✓ SAP visualization saved to results/sap_benchmark_comparison.png")

# Print summary
print("\n" + "="*80)
print("SAP BENCHMARK SUMMARY: Knowledge Model vs Vector-Only RAG")
print("="*80)
print(f"\nAverage Chunk Improvement: +{avg_chunk_improvement:.1f}%")
print(f"  - Scenario 1 (Resource Allocation): +{chunk_improvement[0]:.0f}% more chunks (5 → 8)")
print(f"  - Scenario 2 (Contagion Analysis): +{chunk_improvement[1]:.0f}% more chunks (5 → 8)")

avg_vector_latency = sum(vector_latency) / len(vector_latency)
avg_km_latency = sum(km_latency) / len(km_latency)
latency_overhead = ((avg_km_latency - avg_vector_latency) / avg_vector_latency) * 100

print(f"\nAverage Latency:")
print(f"  - Vector-Only: {avg_vector_latency:.1f}ms")
print(f"  - Knowledge Model: {avg_km_latency:.1f}ms")
print(f"  - Overhead: +{latency_overhead:.1f}% (acceptable for {avg_chunk_improvement:.0f}% more content)")

print("\n" + "="*80)
print("KEY FINDINGS (SAP ERP Domain):")
print("="*80)
print("✓ Knowledge Model retrieves 60% MORE organizational structure data on average")
print("✓ Graph patterns discover CostCenter→Department hierarchies missed by vector search")
print("✓ Vector search finds departments, Graph RAG finds funding structure relationships")
print("✓ Both scenarios show consistent 60% improvement in structural pattern discovery")
print("✓ Latency overhead acceptable (~23%) for discovering hidden organizational relationships")
print("\n" + "="*80)
print("DOMAIN INSIGHT:")
print("="*80)
print("Graph RAG wins when discovering STRUCTURAL ORGANIZATIONAL PATTERNS:")
print("  • Department→CostCenter hierarchies (not semantically rich in embeddings)")
print("  • Funding networks (Project→Department→CostCenter chains)")
print("  • Cross-department risk exposure via shared budget structures")
print("\nGraph RAG equal to vector for SEMANTICALLY EXPLICIT relationships:")
print("  • Supply chain descriptions already mention 'TSMC supplies CPU'")
print("  • Vendor-component relationships captured in chunk text directly")
print("="*80 + "\n")
