import pandas as pd
import sqlite3

conn = sqlite3.connect('skills_data_Zakaria.db')
target_sheets = ['Zakaria,'] #"Ahmed",'Hamza ,H','Hamza,M','Mourad','noura-  ','Zakaria'

excel_file = 'FRE_LD_ICTMF020-HR-Matrix Polyvalence.xlsx'
all_sheets = pd.read_excel(excel_file, sheet_name=target_sheets, header=None)

all_employees = []
all_qualifications = []

for sheet_name, df in all_sheets.items():
    headers = []
    
    # 1. Base Columns (A to E) -> Columns 0 to 4, Row 14 (Index 13)
    for i in range(0, 5):
        val = df.iloc[13, i]
        headers.append(str(val).strip() if pd.notna(val) else f"Base_Col_{i}")
        
    # 2. Main Work Columns (F to T) -> Columns 5 to 19
    # Covers Row 14 (Index 13), Row 15 (Index 14), Row 17 (Index 16)
    last_valid_row13 = "Unknown"
    last_valid_row14 = "Unknown"
    
    for i in range(5, 20):
        cell_13 = df.iloc[13, i]
        cell_14 = df.iloc[14, i]
        
        # Tracker for merged cells on Row 14
        if pd.notna(cell_13) and str(cell_13).strip() not in ["", "nan", "Unknown"]:
            last_valid_row13 = str(cell_13).strip()
            
        # Tracker for merged cells on Row 15
        if pd.notna(cell_14) and str(cell_14).strip() not in ["", "nan", "Unknown"]:
            last_valid_row14 = str(cell_14).strip()
            
        parts = []
        if last_valid_row13 != "Unknown": parts.append(last_valid_row13)
        if last_valid_row14 != "Unknown": parts.append(last_valid_row14)
        
        # Row 17 (Index 16) - Work Post
        cell_16 = df.iloc[16, i]
        if pd.notna(cell_16) and str(cell_16).strip() not in ["", "nan"]:
            parts.append(str(cell_16).strip())
                
        # Row 18 (Index 17) - EXTRA CELL ONLY FOR COLUMNS I (8), J (9), and K (10)
        if i in [8, 9, 10]:
            cell_17 = df.iloc[17, i]
            if pd.notna(cell_17) and str(cell_17).strip() not in ["", "nan"]:
                parts.append(str(cell_17).strip())
                
        # Join all found parts together
        headers.append(" - ".join(parts) if parts else f"Unknown_Col_{i}")
        
    # 3. Special Work Columns (U, V, W) -> Columns 20 to 22, Row 14 (Index 13)
    for i in range(20, 23):
        val = df.iloc[13, i]
        headers.append(str(val).strip() if pd.notna(val) else f"Special_Col_{i}")
        
    # 4. Extract Data Rows (19 to 35 -> Indices 18 to 34)
    # Slice up to column 23 to capture exactly A through W, and row 35 (index 35 is exclusive)
    data_df = df.iloc[18:35, 0:23].copy()
    data_df.columns = headers
    
    # Identify the base columns dynamically to use for melting
    base_columns = headers[0:5] 
    
    # -- TABLE 1: Employees --
    employees_df = data_df[base_columns].dropna(subset=[base_columns[0]])
    employees_df = employees_df.drop_duplicates(subset=[base_columns[0]])
    all_employees.append(employees_df)

    # -- TABLE 2: Qualifications --
    melted_df = data_df.melt(
        id_vars=base_columns,
        var_name='line_of_work', 
        value_name='qualification_level'
    )
    
    qualifications_df = melted_df[[base_columns[0], 'line_of_work', 'qualification_level']].copy()
    qualifications_df.rename(columns={base_columns[0]: 'employee_id'}, inplace=True)
    
    # Clean the data: Drop standard blank cells, empty strings, and 'N/A'
    qualifications_df = qualifications_df.dropna(subset=['qualification_level'])
    qualifications_df = qualifications_df[~qualifications_df['qualification_level'].isin(['N/A', 'nan', ''])]
    
    all_qualifications.append(qualifications_df)

# Combine the data from all the different sheets
final_employees = pd.concat(all_employees, ignore_index=True)
final_qualifications = pd.concat(all_qualifications, ignore_index=True)

# Rename the columns to standard database formatting
final_employees.rename(columns={
    final_employees.columns[0]: 'id',
    final_employees.columns[1]: 'name',
    final_employees.columns[2]: 'employment_date',
    final_employees.columns[3]: 'team_leader',
    final_employees.columns[4]: 'area'
}, inplace=True)

# Format all dates uniformly into YYYY-MM-DD strings
final_employees['employment_date'] = pd.to_datetime(final_employees['employment_date'], dayfirst=True, errors='coerce')
final_employees['employment_date'] = final_employees['employment_date'].dt.strftime('%Y-%m-%d')

# Export to SQLite
final_employees.to_sql('Employees', conn, if_exists='replace', index=False)
final_qualifications.to_sql('Qualifications', conn, if_exists='replace', index=False)

conn.close()

print(f"Migration complete! Extracted data from rows 19-35 across {len(target_sheets)} sheets.")