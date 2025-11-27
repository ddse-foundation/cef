import json
import random
import uuid
from datetime import datetime, timedelta

# Configuration
NUM_PATIENTS = 150
NUM_DOCTORS = 15

# Behavioral & Demographic Data (No PII)
AGE_GROUPS = ["18-29", "30-44", "45-59", "60-74", "75+"]
GENDERS = ["Male", "Female", "Non-Binary"]
SMOKING_STATUS = ["Never Smoker", "Former Smoker", "Current Smoker ( < 1 pack/day)", "Current Smoker ( > 1 pack/day)"]
ALCOHOL_CONSUMPTION = ["None", "Occasional", "Moderate", "Heavy"]
ACTIVITY_LEVEL = ["Sedentary", "Lightly Active", "Moderately Active", "Very Active"]
DIET_ADHERENCE = ["Poor", "Fair", "Good", "Excellent"]
STRESS_LEVELS = ["Low", "Moderate", "High", "Severe"]

CONDITIONS = [
    {"name": "Type 2 Diabetes Mellitus", "icd10": "E11.9", "symptoms": ["polydipsia", "polyuria", "fatigue", "blurred vision"], "contraindications": ["Prednisone", "Gatifloxacin"], "risk_factors": ["Obesity", "Sedentary"]},
    {"name": "Essential Hypertension", "icd10": "I10", "symptoms": ["headache", "shortness of breath", "nosebleeds"], "contraindications": ["Ibuprofen", "Naproxen", "Pseudoephedrine"], "risk_factors": ["High Sodium Diet", "Stress"]},
    {"name": "Bronchial Asthma", "icd10": "J45.909", "symptoms": ["wheezing", "coughing", "chest tightness"], "contraindications": ["Propranolol", "Atenolol", "Aspirin"], "risk_factors": ["Smoking", "Allergens"]},
    {"name": "Congestive Heart Failure", "icd10": "I50.9", "symptoms": ["edema", "fatigue", "dyspnea"], "contraindications": ["Metformin", "Pioglitazone", "Ibuprofen"], "risk_factors": ["Hypertension", "CAD"]},
    {"name": "Rheumatoid Arthritis", "icd10": "M06.9", "symptoms": ["joint pain", "stiffness", "swelling"], "contraindications": ["Live Vaccines"], "risk_factors": ["Smoking", "Family History"]}
]

MEDICATIONS = [
    {"name": "Metformin", "class": "Biguanide", "treats": "Type 2 Diabetes Mellitus", "dosage": ["500mg", "850mg", "1000mg"], "side_effects": ["nausea", "diarrhea"]},
    {"name": "Lisinopril", "class": "ACE Inhibitor", "treats": "Essential Hypertension", "dosage": ["10mg", "20mg", "40mg"], "side_effects": ["dizziness", "cough"]},
    {"name": "Albuterol", "class": "Bronchodilator", "treats": "Bronchial Asthma", "dosage": ["90mcg inhaler"], "side_effects": ["tremors", "palpitations"]},
    {"name": "Prednisone", "class": "Corticosteroid", "treats": "Inflammation", "dosage": ["5mg", "10mg", "20mg"], "side_effects": ["weight gain", "insomnia", "hyperglycemia"]}, # Contraindicated for Diabetes
    {"name": "Ibuprofen", "class": "NSAID", "treats": "Pain", "dosage": ["200mg", "400mg", "600mg"], "side_effects": ["stomach pain", "heartburn"]}, # Contraindicated for Hypertension/Heart Failure
    {"name": "Propranolol", "class": "Beta Blocker", "treats": "Hypertension", "dosage": ["40mg", "80mg"], "side_effects": ["fatigue", "bradycardia"]}, # Contraindicated for Asthma
    {"name": "Atorvastatin", "class": "Statin", "treats": "Hyperlipidemia", "dosage": ["10mg", "20mg", "40mg"], "side_effects": ["muscle pain", "liver enzyme elevation"]}
]

def random_date(start_year=2015, end_year=2024):
    start = datetime(start_year, 1, 1)
    end = datetime(end_year, 12, 31)
    return start + timedelta(days=random.randint(0, (end - start).days))

def generate_clinical_note(patient_code, condition, medication, doctor_code, behavioral, vitals, adherence):
    symptoms = ", ".join(random.sample(condition["symptoms"], k=min(2, len(condition["symptoms"]))))
    
    note = f"""
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** {patient_code}
**Provider ID:** {doctor_code}
**Date:** {datetime.now().strftime('%Y-%m-%d')}

**SUBJECTIVE:**
Patient presents for follow-up of {condition['name']}. Reports {symptoms}. 
Behavioral Assessment:
- Smoking: {behavioral['smoking']}
- Alcohol: {behavioral['alcohol']}
- Activity: {behavioral['activity']}
- Stress: {behavioral['stress']}
- Diet Adherence: {behavioral['diet']}

Patient reports medication adherence is {adherence}.

**OBJECTIVE:**
Vitals: BP {vitals['bp']}, HR {vitals['hr']}, BMI {vitals['bmi']}, HbA1c {vitals['hba1c']}%.
Lab Trends: {random.choice(['Stable', 'Worsening', 'Improving'])} since last visit ({random_date(2023, 2023).strftime('%Y-%m-%d')}).

**ASSESSMENT:**
1. {condition['name']} ({condition['icd10']}) - {random.choice(['Stable', 'Uncontrolled', 'Improving'])}.
2. Risk factors discussed: {", ".join(condition['risk_factors'])}.

**PLAN:**
1. Continue {medication['name']} {random.choice(medication['dosage'])} PO daily.
2. Lifestyle modification counseling provided regarding {behavioral['smoking']} and {behavioral['diet']} diet.
3. Follow up in 3 months.
"""
    return note.strip()

def generate_data():
    nodes = []
    edges = []
    chunks = []

    # 1. Create Reference Nodes (Conditions, Medications)
    condition_map = {}
    for cond in CONDITIONS:
        node_id = str(uuid.uuid4())
        nodes.append({
            "id": node_id,
            "label": "Condition",
            "properties": {
                "name": cond["name"],
                "icd10": cond["icd10"],
                "risk_factors": ", ".join(cond["risk_factors"])
            }
        })
        condition_map[cond["name"]] = node_id

    med_map = {}
    for med in MEDICATIONS:
        node_id = str(uuid.uuid4())
        nodes.append({
            "id": node_id,
            "label": "Medication",
            "properties": {
                "name": med["name"],
                "drug_class": med["class"],
                "side_effects": ", ".join(med["side_effects"])
            }
        })
        med_map[med["name"]] = node_id

    # 1a. Create chunks for Conditions
    for cond in CONDITIONS:
        cond_id = condition_map[cond["name"]]
        chunk_text = f"""**CONDITION PROFILE**
Name: {cond['name']}
ICD-10 Code: {cond['icd10']}
Common Symptoms: {', '.join(cond['symptoms'])}
Risk Factors: {', '.join(cond['risk_factors'])}
Contraindicated Medications: {', '.join(cond['contraindications'])}
"""
        chunks.append({
            "id": str(uuid.uuid4()),
            "content": chunk_text.strip(),
            "linkedNodeId": cond_id,
            "metadata": {
                "type": "condition_profile",
                "icd10": cond['icd10']
            }
        })

    # 1b. Create chunks for Medications
    for med in MEDICATIONS:
        med_id = med_map[med["name"]]
        chunk_text = f"""**MEDICATION PROFILE**
Name: {med['name']}
Drug Class: {med['class']}
Indication: {med['treats']}
Available Dosages: {', '.join(med['dosage'])}
Common Side Effects: {', '.join(med['side_effects'])}
"""
        chunks.append({
            "id": str(uuid.uuid4()),
            "content": chunk_text.strip(),
            "linkedNodeId": med_id,
            "metadata": {
                "type": "medication_profile",
                "drug_class": med['class']
            }
        })

    # 2. Create Interaction Edges (Knowledge Model Rules)
    for cond in CONDITIONS:
        cond_id = condition_map[cond["name"]]
        for contra_med in cond["contraindications"]:
            if contra_med in med_map:
                med_id = med_map[contra_med]
                edges.append({
                    "id": str(uuid.uuid4()),
                    "sourceNodeId": med_id,
                    "targetNodeId": cond_id,
                    "relationType": "CONTRAINDICATED_FOR",
                    "properties": {
                        "severity": "High", 
                        "reason": "Adverse Reaction Risk"
                    }
                })

    # 3. Create Doctors (Anonymized)
    doctor_ids = []
    doctors = []
    for i in range(NUM_DOCTORS):
        doc_id = str(uuid.uuid4())
        doc_code = f"DOC-{100+i}"
        specialty = random.choice(["Internal Medicine", "Family Practice", "Cardiology", "Endocrinology"])
        nodes.append({
            "id": doc_id,
            "label": "Doctor",
            "properties": {
                "provider_id": doc_code,
                "specialty": specialty,
                "years_experience": random.randint(5, 35)
            }
        })
        doctor_ids.append(doc_id)
        doctors.append({"id": doc_id, "code": doc_code})
        
        # Create chunk for Doctor
        doc_chunk_text = f"""**PROVIDER PROFILE**
Provider ID: {doc_code}
Specialty: {specialty}
Years of Experience: {random.randint(5, 35)}
Board Certified: Yes
"""
        chunks.append({
            "id": str(uuid.uuid4()),
            "content": doc_chunk_text.strip(),
            "linkedNodeId": doc_id,
            "metadata": {
                "type": "provider_profile",
                "specialty": specialty
            }
        })

    # 4. Create Patients and Clinical Data (Anonymized & Behavioral)
    for i in range(NUM_PATIENTS):
        patient_id = str(uuid.uuid4())
        patient_code = f"PT-{10000+i}"
        
        behavioral = {
            "smoking": random.choice(SMOKING_STATUS),
            "alcohol": random.choice(ALCOHOL_CONSUMPTION),
            "activity": random.choice(ACTIVITY_LEVEL),
            "diet": random.choice(DIET_ADHERENCE),
            "stress": random.choice(STRESS_LEVELS)
        }

        nodes.append({
            "id": patient_id,
            "label": "Patient",
            "properties": {
                "patient_id": patient_code,
                "age_group": random.choice(AGE_GROUPS),
                "gender": random.choice(GENDERS),
                "smoking_status": behavioral["smoking"],
                "alcohol_consumption": behavioral["alcohol"],
                "activity_level": behavioral["activity"],
                "stress_level": behavioral["stress"]
            }
        })

        # Assign Condition
        cond = random.choice(CONDITIONS)
        cond_id = condition_map[cond["name"]]
        diagnosis_date = random_date(2015, 2023)
        edges.append({
            "id": str(uuid.uuid4()),
            "sourceNodeId": patient_id,
            "targetNodeId": cond_id,
            "relationType": "HAS_CONDITION",
            "properties": {
                "diagnosed_date": diagnosis_date.strftime("%Y-%m-%d"),
                "status": "Active",
                "severity": random.choice(["Mild", "Moderate", "Severe"])
            }
        })

        # Assign Medication (Logic for Contraindications)
        med_data = None
        # 15% chance of dangerous prescription (Contraindicated)
        if random.random() < 0.15:
            bad_med_name = random.choice(cond["contraindications"])
            if bad_med_name in med_map:
                med_data = next(m for m in MEDICATIONS if m["name"] == bad_med_name)
        
        if not med_data:
            safe_meds = [m for m in MEDICATIONS if m["name"] not in cond["contraindications"]]
            if safe_meds:
                med_data = random.choice(safe_meds)

        if med_data:
            med_id = med_map[med_data["name"]]
            adherence = random.choice(["Good", "Poor", "Variable"])
            edges.append({
                "id": str(uuid.uuid4()),
                "sourceNodeId": patient_id,
                "targetNodeId": med_id,
                "relationType": "PRESCRIBED_MEDICATION",
                "properties": {
                    "start_date": (diagnosis_date + timedelta(days=random.randint(0, 30))).strftime("%Y-%m-%d"),
                    "adherence": adherence,
                    "dosage": random.choice(med_data["dosage"])
                }
            })

            # Assign Doctor
            doc = random.choice(doctors)
            edges.append({
                "id": str(uuid.uuid4()),
                "sourceNodeId": patient_id,
                "targetNodeId": doc["id"],
                "relationType": "TREATED_BY",
                "properties": {
                    "last_visit": datetime.now().strftime("%Y-%m-%d"),
                    "visit_frequency": random.choice(["Monthly", "Quarterly", "Annually"])
                }
            })

            # Generate Clinical Note (Chunk)
            vitals = {
                "bp": f"{random.randint(110, 160)}/{random.randint(70, 100)}",
                "hr": random.randint(60, 100),
                "bmi": round(random.uniform(18.5, 35.0), 1),
                "hba1c": round(random.uniform(5.0, 12.0), 1)
            }
            
            note_text = generate_clinical_note(patient_code, cond, med_data, doc["code"], behavioral, vitals, adherence)
            
            chunks.append({
                "id": str(uuid.uuid4()),
                "content": note_text,
                "linkedNodeId": patient_id,
                "metadata": {
                    "type": "clinical_note",
                    "date": datetime.now().strftime("%Y-%m-%d"),
                    "patient_id": patient_code
                }
            })

    output = {
        "nodes": nodes,
        "edges": edges,
        "chunks": chunks
    }

    output_path = "../medical_benchmark_data.json"
    with open(output_path, "w") as f:
        json.dump(output, f, indent=2)
    
    print(f"Generated {len(nodes)} nodes, {len(edges)} edges, {len(chunks)} chunks to {output_path}")

if __name__ == "__main__":
    generate_data()
