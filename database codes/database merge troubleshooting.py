import pandas as pd
import sqlite3
import os

db_files = [
    'skills_data_ahmed.db', 
    'skills_data_Hamza.db', 
    'skills_data_HamzaM.db', 
    'skills_data_mourad.db', 
    'skills_data_noura.db', 
    'skills_data_Zakaria.db'
]

all_employees = []
all_qualifications = []

for db in db_files:
    if not os.path.exists(db):
        print(f"❌ ERROR: The file '{db}' was not found in this folder. Check the spelling.")
        continue 

    conn = sqlite3.connect(db)
    
    try:
        employees_df = pd.read_sql('SELECT * FROM Employees', conn)
        qualifications_df = pd.read_sql('SELECT * FROM Qualifications', conn)
        
        all_employees.append(employees_df)
        all_qualifications.append(qualifications_df)
        print(f"✅ Successfully read data from {db}")
        
    except pd.errors.DatabaseError:
        print(f"❌ ERROR: The file '{db}' exists, but it does not have the 'Employees' or 'Qualifications' table.")
    finally:
        conn.close()

if len(all_employees) > 0:
    master_employees = pd.concat(all_employees, ignore_index=True)
    master_qualifications = pd.concat(all_qualifications, ignore_index=True)

    def combine_text(series):
        # Extract unique values and ignore empty ones
        unique_vals = [str(val).strip() for val in series.dropna().unique() if str(val).strip() not in ('', 'None', 'nan')]
        
        if len(unique_vals) > 1:
            # If there are duplicates, join them with ' - ' and wrap in brackets
            return f"{' - '.join(unique_vals)}"
        elif len(unique_vals) == 1:
            # If it's just one value, return it normally
            return unique_vals[0]
        else:
            return None

    # Group strictly by the 'id' column
    master_employees = master_employees.groupby(
        'id', 
        dropna=False, 
        as_index=False
    ).agg({
        'name': 'first',             # Keeps the first spelling of the name it sees
        'employment_date': 'first',  # Keeps the first employment date it sees
        'area': combine_text,        # Squashes the areas together
        'team_leader': combine_text  # Squashes the team leaders together
    })

    # Keep the qualifications clean of duplicates
    master_qualifications = master_qualifications.drop_duplicates()

    master_conn = sqlite3.connect('master_skills_data.db')

    # Export to SQLite
    master_employees.to_sql('Employees', master_conn, if_exists='replace', index=False)
    master_qualifications.to_sql('Qualifications', master_conn, if_exists='replace', index=False)

    master_conn.close()

    print(f"\n🎉 Successfully merged databases! Duplicates were merged using strictly the ID.")
else:
    print("\n⚠️ No data was merged.")