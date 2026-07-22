import pandas as pd
import sqlite3
import os

source_db = 'master_skills_data.db'
output_db = 'master_skills_data_clean2.db'

if not os.path.exists(source_db):
    print(f"❌ ERROR: '{source_db}' not found.")
else:
    # 1. Connect and extract the current data
    conn = sqlite3.connect(source_db)
    master_employees = pd.read_sql('SELECT * FROM Employees', conn)
    master_qualifications = pd.read_sql('SELECT * FROM Qualifications', conn)
    conn.close()

    if 'team_leaders' in master_employees.columns:
        master_employees.rename(columns={'team_leaders': 'team_leader'}, inplace=True)

    # ==========================================
    # --- TEXT CLEANING & FORMATTING BLOCK ---
    # ==========================================
    
    print("🧹 Cleaning text formatting and applying uppercase...")
    
    # Clean ID, Name, Area, and Team Leader
    columns_to_clean = ['id', 'name', 'area', 'team_leader']
    for col in columns_to_clean:
        if col in master_employees.columns:
            master_employees[col] = master_employees[col].astype(str)
            master_employees[col] = master_employees[col].str.replace('\n', ' ', regex=False)
            master_employees[col] = master_employees[col].str.replace(',', '', regex=False)
            master_employees[col] = master_employees[col].str.replace(r'\s+', ' ', regex=True)
            master_employees[col] = master_employees[col].str.strip()

    # Apply UPPERCASE to ID, Name, and Team Leader
    cols_to_upper = ['id', 'name', 'team_leader']
    for col in cols_to_upper:
        if col in master_employees.columns:
            master_employees[col] = master_employees[col].str.upper()

    # Clean the Qualifications table (line_of_work)
    master_qualifications['line_of_work'] = master_qualifications['line_of_work'].astype(str)
    master_qualifications['line_of_work'] = master_qualifications['line_of_work'].str.replace('\n', ' ', regex=False)
    master_qualifications['line_of_work'] = master_qualifications['line_of_work'].str.replace(',', '', regex=False)
    master_qualifications['line_of_work'] = master_qualifications['line_of_work'].str.replace(r'\s+', ' ', regex=True)
    master_qualifications['line_of_work'] = master_qualifications['line_of_work'].str.strip()

    # Clean and UPPERCASE the 'employee_id' in Qualifications so it matches perfectly
    master_qualifications['employee_id'] = master_qualifications['employee_id'].astype(str)
    master_qualifications['employee_id'] = master_qualifications['employee_id'].str.replace('\n', ' ', regex=False)
    master_qualifications['employee_id'] = master_qualifications['employee_id'].str.replace(r'\s+', ' ', regex=True)
    master_qualifications['employee_id'] = master_qualifications['employee_id'].str.strip().str.upper()

    # ==========================================
    # --- FILTER N/A QUALIFICATIONS ---
    # ==========================================
    
    print("✂️ Filtering out N/A qualifications...")
    master_qualifications['qualification_level'] = master_qualifications['qualification_level'].astype(str).str.strip()
    master_qualifications = master_qualifications[
        (master_qualifications['qualification_level'] != 'N/A') & 
        (master_qualifications['qualification_level'] != 'nan') &
        (master_qualifications['qualification_level'] != 'None') &
        (master_qualifications['qualification_level'] != '')
    ]

    # ==========================================
    # --- DUPLICATE MERGING LOGIC ---
    # ==========================================
    
    print("👯 Merging duplicate IDs...")

    def combine_text(series):
        unique_vals = [str(val).strip() for val in series.dropna().unique() if str(val).strip() not in ('', 'None', 'nan')]
        if len(unique_vals) > 1:
            return f"{' - '.join(unique_vals)}"
        elif len(unique_vals) == 1:
            return unique_vals[0]
        else:
            return None

    # Group strictly by the newly cleaned and uppercase 'id'
    master_employees = master_employees.groupby(
        'id', 
        dropna=False, 
        as_index=False
    ).agg({
        'name': 'first',             
        'employment_date': 'first',  
        'area': combine_text,        
        'team_leader': combine_text  
    })

    master_qualifications = master_qualifications.drop_duplicates()

    # ==========================================
    # --- EXPORT TO NEW DATABASE ---
    # ==========================================

    new_conn = sqlite3.connect(output_db)
    master_employees.to_sql('Employees', new_conn, if_exists='replace', index=False)
    master_qualifications.to_sql('Qualifications', new_conn, if_exists='replace', index=False)
    new_conn.close()

    print(f"\n🎉 Done! New database saved as '{output_db}'")
    print(f"Total Unique Employees: {len(master_employees)}")
    print(f"Total Valid Qualifications: {len(master_qualifications)}")