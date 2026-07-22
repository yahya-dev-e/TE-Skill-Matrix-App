import pandas as pd
import sqlite3

# 1. List the exact names of your 6 database files
db_files = [
    'skills_data_ahmed.db', 
    'skills_data_Hamza.db', 
    'skills_data_mourad.db', 
    'skills_data_noura.db', 
    'skills_data_Zakaria.db', 
    'skills_data_HamzaM.db'
]

all_employees = []
all_qualifications = []

# 2. Loop through each database and extract its tables
for db in db_files:
    # Connect to the current database in the loop
    conn = sqlite3.connect(db)
    
    # Read the two tables into pandas dataframes
    employees_df = pd.read_sql('SELECT * FROM Employees', conn)
    qualifications_df = pd.read_sql('SELECT * FROM Qualifications', conn)
    
    # Add them to our master lists
    all_employees.append(employees_df)
    all_qualifications.append(qualifications_df)
    
    # Close the connection to this database
    conn.close()

# 3. Combine all the extracted data together
master_employees = pd.concat(all_employees, ignore_index=True)
master_qualifications = pd.concat(all_qualifications, ignore_index=True)

# Optional but recommended: Drop duplicate rows just in case 
# the same person was accidentally in two different databases
master_employees = master_employees.drop_duplicates(subset=['id'])
master_qualifications = master_qualifications.drop_duplicates()

# 4. Create and connect to the new master database
master_conn = sqlite3.connect('master_skills_data.db')

# Export the fully combined tables
master_employees.to_sql('Employees', master_conn, if_exists='replace', index=False)
master_qualifications.to_sql('Qualifications', master_conn, if_exists='replace', index=False)

master_conn.close()

print(f"Successfully merged {len(db_files)} databases into 'master_skills_data.db'!")