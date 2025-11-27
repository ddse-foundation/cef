import csv
import random
import os
from datetime import datetime, timedelta
import uuid

# Configuration
OUTPUT_DIR = "../sap_data"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# --- Helpers ---
def write_csv(filename, headers, rows):
    path = os.path.join(OUTPUT_DIR, filename)
    with open(path, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(headers)
        writer.writerows(rows)
    print(f"Generated {path} with {len(rows)} rows.")

def append_csv(filename, rows):
    path = os.path.join(OUTPUT_DIR, filename)
    with open(path, 'a', newline='') as f:
        writer = csv.writer(f)
        writer.writerows(rows)
    print(f"Appended {len(rows)} rows to {path}.")

# ==========================================
# SCENARIO 1: FINANCIAL GL (Shadow IT)
# ==========================================

# 1. Departments (Custom)
# DEPT_ID, NAME, HAS_OVERRUN (Y/N)
departments = [
    ("ENG", "Engineering", "Y"),  # Has cost overruns
    ("MKT", "Marketing", "N"),
    ("HR", "Human Resources", "N"),
    ("OPS", "Operations", "N")
]
write_csv("DEPARTMENTS.csv", ["DEPT_ID", "NAME", "HAS_OVERRUN"], departments)

# 2. Cost Centers (CSKS) - Now linked to Departments
# KOSTL (ID), KTEXT (Name), ABTEI (Dept)
cost_centers = [
    ("CC-100", "Engineering Core", "ENG"),
    ("CC-101", "DevOps", "ENG"),
    ("CC-102", "Data Science", "ENG"),
    ("CC-200", "Marketing US", "MKT"),
    ("CC-300", "HR Global", "HR")
]
write_csv("CSKS.csv", ["KOSTL", "KTEXT", "ABTEI"], cost_centers)

# 3. Budgets (Custom)
# BUDGET_ID, DEPT_ID, FISCAL_YEAR, APPROVED_VENDORS (comma-separated)
budgets = [
    ("BUD-2025-ENG", "ENG", "2025", "V-1000,V-1001,V-1002,V-1003"),  # AWS, Azure, Salesforce, Staples
    ("BUD-2025-MKT", "MKT", "2025", "V-1002,V-1003"),  # Salesforce, Staples
    ("BUD-2025-HR", "HR", "2025", "V-1003"),  # Staples only
]
write_csv("BUDGETS.csv", ["BUDGET_ID", "DEPT_ID", "FISCAL_YEAR", "APPROVED_VENDORS"], budgets)

# 4. Vendors (LFA1)
# LIFNR (ID), NAME1 (Name), ORT01 (City), LAND1 (Country)
vendors = [
    ("V-1000", "AWS Web Services", "Seattle", "US"),
    ("V-1001", "Microsoft Azure", "Redmond", "US"),
    ("V-1002", "Salesforce", "San Francisco", "US"),
    ("V-1003", "Staples", "Framingham", "US"),
    ("V-9999", "Unknown AI Tool", "George Town", "KY") # The Shadow IT Vendor (NOT in approved budgets)
]
write_csv("LFA1.csv", ["LIFNR", "NAME1", "ORT01", "LAND1"], vendors)

# 5. Projects (Custom) - Projects use materials and track cost status
# PROJECT_ID, NAME, DEPT_ID, STATUS (OVERRUN/OK)
projects = [
    ("PROJ-001", "Cloud Migration Phase 2", "ENG", "OVERRUN"),
    ("PROJ-002", "Holiday Laptop Launch", "ENG", "OVERRUN"),
    ("PROJ-003", "Marketing Campaign Q1", "MKT", "OK"),
    ("PROJ-004", "HR System Upgrade", "HR", "OK")
]
write_csv("PROJECTS.csv", ["PROJECT_ID", "NAME", "DEPT_ID", "STATUS"], projects)

# 6. GL Transactions (BKPF Header + BSEG Segment)
# We simulate 3 months of data.
# "Unknown AI Tool" starts small and grows, spread across departments to hide.

bkpf_rows = [] # BELNR, GJAHR, BLDAT, BKTXT
bseg_rows = [] # BELNR, GJAHR, BUZEI, KOSTL, LIFNR, DMBTR, HKONT (GL Acc)

start_date = datetime(2025, 1, 1)
doc_id_counter = 100000

def add_transaction(date, vendor_id, amount, cost_center, gl_account="600000"):
    global doc_id_counter
    doc_id = str(doc_id_counter)
    year = str(date.year)
    
    # Header
    bkpf_rows.append([doc_id, year, date.strftime("%Y%m%d"), "Vendor Invoice"])
    
    # Segment (Expense Line)
    bseg_rows.append([doc_id, year, "1", cost_center, vendor_id, f"{amount:.2f}", gl_account])
    
    doc_id_counter += 1

# Generate legitimate traffic
for day in range(90):
    current_date = start_date + timedelta(days=day)
    
    # AWS Bill (Monthly)
    if day % 30 == 0:
        add_transaction(current_date, "V-1000", random.uniform(5000, 5500), "CC-101")
    
    # Random Office Supplies
    if random.random() < 0.1:
        add_transaction(current_date, "V-1003", random.uniform(50, 200), random.choice(cost_centers)[0])

# Generate Shadow IT Pattern (Increasing trend, fragmented)
# Month 1: Small tests
add_transaction(start_date + timedelta(days=10), "V-9999", 49.00, "CC-102") # Data Science
add_transaction(start_date + timedelta(days=15), "V-9999", 49.00, "CC-100") # Eng Core

# Month 2: More adoption
add_transaction(start_date + timedelta(days=40), "V-9999", 99.00, "CC-102")
add_transaction(start_date + timedelta(days=42), "V-9999", 99.00, "CC-200") # Marketing joins in
add_transaction(start_date + timedelta(days=45), "V-9999", 150.00, "CC-101")

# Month 3: Explosion (but individual txns still small-ish)
for _ in range(5):
    day_offset = random.randint(60, 85)
    cc = random.choice(cost_centers)[0]
    add_transaction(start_date + timedelta(days=day_offset), "V-9999", random.uniform(200, 400), cc)

write_csv("BKPF.csv", ["BELNR", "GJAHR", "BLDAT", "BKTXT"], bkpf_rows)
write_csv("BSEG.csv", ["BELNR", "GJAHR", "BUZEI", "KOSTL", "LIFNR", "DMBTR", "HKONT"], bseg_rows)


# ==========================================
# SCENARIO 2: SUPPLY CHAIN (Butterfly Effect)
# ==========================================

# 1. Materials (MARA)
# MATNR (ID), MAKTX (Desc), MTART (Type), PROJECT_ID (which project uses this)
materials = [
    ("M-9000", "Holiday Laptop Pro", "FERT", "PROJ-002"), # Finished Good - used in Holiday Laptop project
    ("M-8001", "OLED Screen 15in", "HALB", "PROJ-002"),
    ("M-8002", "Li-Ion Battery 99Wh", "HALB", "PROJ-002"),
    ("M-8003", "Motherboard Main", "HALB", "PROJ-002"),
    ("M-7001", "CPU Ryzen 9", "ROH", "PROJ-002"), # Raw Material
    ("M-7002", "GPU RTX 5000", "ROH", "PROJ-002"),
    ("M-7003", "Capacitor Set", "ROH", "PROJ-002"),
    ("M-6001", "Cloud Server Instance", "FERT", "PROJ-001"), # For Cloud Migration
    ("M-6002", "Network Switch", "HALB", "PROJ-001")
]
write_csv("MARA.csv", ["MATNR", "MAKTX", "MTART", "PROJECT_ID"], materials)

# 2. BOM Link (MAST) - Links Material to BOM ID
# MATNR, STLNR (BOM ID), WERKS (Plant)
mast_rows = [
    ("M-9000", "BOM-100", "1000"), # Laptop BOM
    ("M-8003", "BOM-200", "1000")  # Motherboard BOM
]
write_csv("MAST.csv", ["MATNR", "STLNR", "WERKS"], mast_rows)

# 3. BOM Items (STPO) - Components
# STLNR, IDNRK (Component), MENGE (Qty)
stpo_rows = [
    # Laptop Components
    ("BOM-100", "M-8001", "1"), # Screen
    ("BOM-100", "M-8002", "1"), # Battery
    ("BOM-100", "M-8003", "1"), # Motherboard
    
    # Motherboard Components
    ("BOM-200", "M-7001", "1"), # CPU
    ("BOM-200", "M-7002", "1"), # GPU
    ("BOM-200", "M-7003", "50") # Capacitors
]
write_csv("STPO.csv", ["STLNR", "IDNRK", "MENGE"], stpo_rows)

# 4. Purchasing Info Record (EINA) - Links Material to Vendor
# INFNR, MATNR, LIFNR, APLFZ (Lead Time Days)
# We reuse LFA1 vendors but add new ones for supply chain
sc_vendors = [
    ("V-2001", "Taiwan Semi (TSMC)", "Hsinchu", "TW"),
    ("V-2002", "Samsung Display", "Seoul", "KR"),
    ("V-2003", "LG Chem", "Seoul", "KR")
]
# Append to LFA1 file (simulating shared master data)
append_csv("LFA1.csv", sc_vendors)

eina_rows = [
    ("INF-1", "M-7001", "V-2001", "14"), # CPU from TSMC (Taiwan)
    ("INF-2", "M-7002", "V-2001", "14"), # GPU from TSMC (Taiwan)
    ("INF-3", "M-8001", "V-2002", "10"), # Screen from Samsung
    ("INF-4", "M-8002", "V-2003", "7"),  # Battery from LG
    ("INF-5", "M-7003", "V-2001", "14")  # Capacitors also from TSMC (hidden dependency)
]
write_csv("EINA.csv", ["INFNR", "MATNR", "LIFNR", "APLFZ"], eina_rows)

# 5. Customer Orders (Custom) - Orders for finished products
# ORDER_ID, PRODUCT_ID, CUSTOMER, QUANTITY, ORDER_DATE, STATUS
customer_orders = [
    ("ORD-1001", "M-9000", "BestBuy", "5000", "20251101", "RISK"),  # Holiday Laptop
    ("ORD-1002", "M-9000", "Amazon", "3000", "20251105", "RISK"),
    ("ORD-1003", "M-6001", "Internal IT", "100", "20250201", "OK")
]
write_csv("CUSTOMER_ORDERS.csv", ["ORDER_ID", "PRODUCT_ID", "CUSTOMER", "QUANTITY", "ORDER_DATE", "STATUS"], customer_orders)

# 6. Products (Custom) - Product catalog (subset of Materials that are products)
# PRODUCT_ID, NAME, CATEGORY
products = [
    ("M-9000", "Holiday Laptop Pro", "Electronics"),
    ("M-6001", "Cloud Server Instance", "IT Services")
]
write_csv("PRODUCTS.csv", ["PRODUCT_ID", "NAME", "CATEGORY"], products)

# 7. Vendor Supplies Components (Custom) - Explicit SUPPLIES relationship
# VENDOR_ID, COMPONENT_ID (what they supply)
vendor_supplies = [
    ("V-2001", "M-7001"),  # TSMC supplies CPU
    ("V-2001", "M-7002"),  # TSMC supplies GPU
    ("V-2001", "M-7003"),  # TSMC supplies Capacitors
    ("V-2002", "M-8001"),  # Samsung supplies Screen
    ("V-2003", "M-8002"),  # LG supplies Battery
    ("V-9999", "M-6001"),  # Shadow IT vendor supplies cloud services
]
write_csv("VENDOR_SUPPLIES.csv", ["VENDOR_ID", "COMPONENT_ID"], vendor_supplies)

print("SAP Data Generation Complete.")
