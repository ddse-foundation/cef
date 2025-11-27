import json
import re
import matplotlib.pyplot as plt
import pandas as pd
from typing import Set, List, Dict

# 1. Load Data and Build Graph
def load_graph(json_path):
    with open(json_path, 'r') as f:
        data = json.load(f)
    
    nodes = {n['id']: n for n in data['nodes']}
    edges = data['edges']
    
    # Helper maps
    patient_id_map = {n['properties'].get('patient_id'): n['id'] for n in nodes.values() if n['label'] == 'Patient'}
    reverse_patient_id_map = {v: k for k, v in patient_id_map.items()}
    
    # Adjacency list
    adj = {n: [] for n in nodes}
    for e in edges:
        adj[e['sourceNodeId']].append((e['targetNodeId'], e['relationType']))
        # We might need reverse edges for some traversals
    
    return nodes, edges, adj, patient_id_map, reverse_patient_id_map

# 2. Ground Truth Logic
def get_ground_truth(nodes, edges, adj, patient_id_map, reverse_patient_id_map):
    gt = {}
    
    # Scenario 1: Patient Zero (Same doctor as PT-10001)
    # PT-10001 -> TREATED_BY -> DOC -> TREATED_BY (reverse) -> Patients
    pt_10001_uuid = patient_id_map['PT-10001']
    doctors = [target for target, rel in adj[pt_10001_uuid] if rel == 'TREATED_BY']
    
    # Find all patients treated by these doctors
    # Since edges are directed Patient -> Doctor, we need to scan edges or build reverse index
    doc_patients = set()
    for e in edges:
        if e['relationType'] == 'TREATED_BY' and e['targetNodeId'] in doctors:
            if e['sourceNodeId'] != pt_10001_uuid: # Exclude self? Usually yes.
                doc_patients.add(reverse_patient_id_map.get(e['sourceNodeId']))
    
    gt['Patient Zero'] = doc_patients
    
    # Scenario 2: Contraindications
    # Find (M)-[:CONTRAINDICATED_FOR]->(C)
    contraindications = []
    for e in edges:
        if e['relationType'] == 'CONTRAINDICATED_FOR':
            contraindications.append((e['sourceNodeId'], e['targetNodeId'])) # Med, Condition
            
    contra_patients = set()
    for pid, uuid in patient_id_map.items():
        # Get patient's meds and conditions
        p_meds = set()
        p_conds = set()
        for target, rel in adj[uuid]:
            if rel == 'PRESCRIBED_MEDICATION':
                p_meds.add(target)
            elif rel == 'HAS_CONDITION':
                p_conds.add(target)
        
        # Check for conflict
        for med, cond in contraindications:
            if med in p_meds and cond in p_conds:
                contra_patients.add(pid)
                
    gt['Contraindications'] = contra_patients

    # Scenario 3: Smokers with Asthma
    # Asthma Node ID
    asthma_id = next(n['id'] for n in nodes.values() if n['properties'].get('name') == 'Bronchial Asthma')
    
    smoker_asthma_patients = set()
    for pid, uuid in patient_id_map.items():
        node = nodes[uuid]
        smoking_status = node['properties'].get('smoking_status', '')
        is_smoker = "Smoker" in smoking_status and "Never" not in smoking_status and "Former" not in smoking_status
        # Or strictly "Current Smoker"
        is_current_smoker = "Current Smoker" in smoking_status
        
        has_asthma = False
        for target, rel in adj[uuid]:
            if rel == 'HAS_CONDITION' and target == asthma_id:
                has_asthma = True
                break
        
        if is_current_smoker and has_asthma:
            smoker_asthma_patients.add(pid)
            
    gt['Smokers with Asthma'] = smoker_asthma_patients

    # Scenario 4: Intersection (Rheumatoid Arthritis + Albuterol)
    # RA Node ID: e5581355-937b-40db-ab3f-034cd9e0ebfa
    # Albuterol Node ID: 27380cec-996c-4233-b14d-c06d72a9392a
    ra_id = "e5581355-937b-40db-ab3f-034cd9e0ebfa"
    albuterol_id = "27380cec-996c-4233-b14d-c06d72a9392a"
    
    intersection_patients = set()
    for pid, uuid in patient_id_map.items():
        has_ra = False
        has_albuterol = False
        for target, rel in adj[uuid]:
            if rel == 'HAS_CONDITION' and target == ra_id:
                has_ra = True
            if rel == 'PRESCRIBED_MEDICATION' and target == albuterol_id:
                has_albuterol = True
        
        if has_ra and has_albuterol:
            intersection_patients.add(pid)
            
    gt['Intersection'] = intersection_patients

    # Scenario 5: Root Cause Analysis (Prednisone + Type 2 Diabetes)
    # Prednisone ID: Find by name
    # Diabetes ID: Find by name
    prednisone_id = next(n['id'] for n in nodes.values() if n['properties'].get('name') == 'Prednisone')
    diabetes_id = next(n['id'] for n in nodes.values() if n['properties'].get('name') == 'Type 2 Diabetes Mellitus')
    
    rca_patients = set()
    for pid, uuid in patient_id_map.items():
        has_prednisone = False
        has_diabetes = False
        for target, rel in adj[uuid]:
            if rel == 'PRESCRIBED_MEDICATION' and target == prednisone_id:
                has_prednisone = True
            if rel == 'HAS_CONDITION' and target == diabetes_id:
                has_diabetes = True
        
        if has_prednisone and has_diabetes:
            rca_patients.add(pid)
            
    gt['Root Cause Analysis'] = rca_patients

    # Scenario 4 (Report 1): Indirect Contraindication Risk (CHF)
    # Find (M)-[:CONTRAINDICATED_FOR]->(CHF)
    # CHF ID: 9a468e15-4c0f-4ce4-9f3e-b8ff5d6e928b (from json or lookup)
    chf_id = next(n['id'] for n in nodes.values() if n['properties'].get('name') == 'Congestive Heart Failure')
    
    contra_meds_chf = set()
    for e in edges:
        if e['relationType'] == 'CONTRAINDICATED_FOR' and e['targetNodeId'] == chf_id:
            contra_meds_chf.add(e['sourceNodeId'])
            
    indirect_contra_patients = set()
    for pid, uuid in patient_id_map.items():
        # Check if patient takes any of these meds
        patient_meds = [target for target, rel in adj[uuid] if rel == 'PRESCRIBED_MEDICATION']
        if any(m in contra_meds_chf for m in patient_meds):
            indirect_contra_patients.add(pid)
            
    gt['Indirect Contraindication Risk'] = indirect_contra_patients
    
    # Scenario 4 (Report 2): Shared Doctor Risk (RA)
    # Find Doctors treating RA patients
    ra_id = "e5581355-937b-40db-ab3f-034cd9e0ebfa" # From previous code
    
    ra_doctors = set()
    # Find patients with RA
    ra_patients = set()
    for pid, uuid in patient_id_map.items():
        conditions = [target for target, rel in adj[uuid] if rel == 'HAS_CONDITION']
        if ra_id in conditions:
            ra_patients.add(uuid)
            
    # Find doctors of these patients
    for p_uuid in ra_patients:
        docs = [target for target, rel in adj[p_uuid] if rel == 'TREATED_BY']
        ra_doctors.update(docs)
        
    # Find ALL patients of these doctors
    shared_doctor_patients = set()
    for doc_id in ra_doctors:
        for e in edges:
            if e['relationType'] == 'TREATED_BY' and e['targetNodeId'] == doc_id:
                pat_uuid = e['sourceNodeId']
                if pat_uuid in reverse_patient_id_map:
                    shared_doctor_patients.add(reverse_patient_id_map[pat_uuid])
                    
    gt['Shared Doctor Risk'] = shared_doctor_patients
    
    return gt

# 3. Parse Reports
def parse_reports(report_files):
    results = {}
    
    # Regex to find patient IDs in the report tables/blocks
    # Pattern: | ... | ... | **Patient ID:** PT-XXXXX ...
    # Or just extracting PT-XXXXX from the raw text blocks
    
    for filepath, scenarios in report_files.items():
        with open(filepath, 'r') as f:
            content = f.read()
            
        # Split by scenario headers
        # This is a bit brittle, assuming standard markdown structure
        sections = re.split(r'## \d+\. ', content)
        
        for section in sections[1:]: # Skip preamble
            lines = section.split('\n')
            title = lines[0].strip()
            
            # Identify scenario key
            key = None
            if "Patient Zero" in title: key = "Patient Zero"
            elif "Contraindicated" in title: key = "Contraindications"
            elif "Smokers" in title: key = "Smokers with Asthma"
            elif "Intersection" in title: key = "Intersection"
            elif "Root Cause" in title: key = "Root Cause Analysis"
            
            if not key: continue
            
            # Extract Vector results
            vector_match = re.search(r'\*\*Vector-Only \(Naive RAG\):\*\*\n```(.*?)```', section, re.DOTALL)
            km_match = re.search(r'\*\*Knowledge Model \(Graph RAG\):\*\*\n```(.*?)```', section, re.DOTALL)
            
            def extract_ids(text_block):
                if not text_block: return set()
                return set(re.findall(r'PT-\d{5}', text_block))
            
            results[key] = {
                'Vector': extract_ids(vector_match.group(1) if vector_match else ""),
                'KM': extract_ids(km_match.group(1) if km_match else "")
            }
            
    return results

# 4. Calculate Metrics
def calculate_metrics(gt, results):
    metrics = []
    
    for scenario, data in results.items():
        if scenario not in gt: continue
        
        true_set = gt[scenario]
        
        for method in ['Vector', 'KM']:
            retrieved = data[method]
            
            # Precision: |Retrieved ∩ True| / |Retrieved|
            # Recall: |Retrieved ∩ True| / |True|
            
            intersection = len(retrieved.intersection(true_set))
            precision = intersection / len(retrieved) if retrieved else 0
            recall = intersection / len(true_set) if true_set else 0
            f1 = 2 * (precision * recall) / (precision + recall) if (precision + recall) > 0 else 0
            
            metrics.append({
                'Scenario': scenario,
                'Method': method,
                'Precision': precision,
                'Recall': recall,
                'F1 Score': f1,
                'Retrieved Count': len(retrieved),
                'True Count': len(true_set)
            })
            
    return pd.DataFrame(metrics)

# Main Execution
if __name__ == "__main__":
    json_path = '../medical_benchmark_data.json'
    report_files = {
        '../../../BENCHMARK_REPORT.md': [
            'Multi-Hop Contraindication Discovery',
            'High-Risk Behavioral Pattern', 
            'Cascading Side Effect Risk',
            'Transitive Exposure Risk'
        ],
        '../../../BENCHMARK_REPORT_2.md': [
            'Network Contagion',
            'Polypharmacy Risk Pattern',
            'Provider Network Cascade',
            'Bidirectional Risk Network'
        ]
    }
    
    print("Loading Knowledge Graph...")
    nodes, edges, adj, pid_map, r_pid_map = load_graph(json_path)
    
    print("Generating Ground Truth...")
    gt = get_ground_truth(nodes, edges, adj, pid_map, r_pid_map)
    
    print("Parsing Benchmark Reports...")
    results = parse_reports(report_files)
    
    print("Calculating Metrics...")
    df = calculate_metrics(gt, results)
    
    print("\nEvaluation Results:")
    print(df.to_string(index=False))
    
    # Visualization
    scenarios = df['Scenario'].unique()
    metrics_to_plot = ['Precision', 'Recall', 'F1 Score']
    
    fig, axes = plt.subplots(1, 3, figsize=(18, 6))
    
    for i, metric in enumerate(metrics_to_plot):
        ax = axes[i]
        pivot = df.pivot(index='Scenario', columns='Method', values=metric)
        pivot.plot(kind='bar', ax=ax, color=['#ff9999', '#66b3ff'])
        ax.set_title(f'{metric} Comparison')
        ax.set_ylim(0, 1.1)
        ax.grid(axis='y', linestyle='--', alpha=0.7)
        
    plt.tight_layout()
    plt.savefig('results/benchmark_quality_evaluation.png')
    print("\nVisualization saved to results/benchmark_quality_evaluation.png")
