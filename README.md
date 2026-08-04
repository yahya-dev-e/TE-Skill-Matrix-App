📊 TE Connectivity - Skill Matrix System
========================================

A desktop application designed to visualize, filter, and manage employee skill sets, qualifications, and certification logs with dynamic reporting and Excel export capabilities.

✨ Features
----------

*   **🔍 Real-Time Search & Filtering:** Filter employees by ID, Name, Team Leader, Station, or Skill Level instantly across employee records.
    
*   **📂 Interactive Profile & Qualifications:** Select an employee to view detailed qualifications, associated line numbers, areas, and certification records.
    
*   **🌙 Dynamic Dark & Light Themes:** Toggle seamlessly between dark and light modes with custom CSS styling and adaptive top/bottom navigation bars.
    
*   **📊 Excel Data Export:** Export filtered search results and qualification summaries directly to .xlsx spreadsheet format using Apache POI.
    
*   **💾 Database Backend:** Lightweight SQLite database integration for local, fast, and robust data persistence.
    

🛠️ Project Structure
---------------------

*   **bin/**: Compiled Java bytecode (.class files).
    
*   **lib/**: External library dependencies (sqlite-jdbc, poi, commons-io, etc.).
    
*   **master\_skills\_data\_clean2.db**: Local SQLite database storing employees and qualifications.
    
*   **SkillMatrixApp.java**: Main JavaFX user interface and view logic.
    
*   **DatabaseManager.java**: Query execution and database handler.
    
*   **DatabaseMigration.java**: Schema migration utility.
    
*   **styles.css**: Global stylesheet for Light and Dark themes.
    
*   **Dark\_mode\_bg.png / Light\_mode\_bg.png**: Theme background graphics.
    
*   **TE\_Connectivity\_logo.png**: Branding asset.
    
*   **SkillMatrixApp.jar**: Packaged Java archive.
    
*   **SkillMatrix.exe**: Executable wrapper.
    
*   **installer.iss**: Windows installer compilation script.
    
*   **README.md**: Project documentation.
    

🚀 Getting Started
------------------

### Prerequisites

*   **Java Development Kit (JDK):** JDK 17 or higher (with JavaFX included).
    
*   **VS Code / IDE:** Configured with Java Extension Pack (or any preferred Java IDE).
    

💻 Manual Compilation & Execution
---------------------------------

To compile and launch the application directly from the terminal or PowerShell:

### 1\. Compile Java Source Files

javac -d bin -cp "lib/\*" DatabaseManager.java SkillMatrixApp.java

### 2\. Run the Application

java -cp "bin;lib/\*" SkillMatrixApp

📦 Building the Application (.jar & .exe)
-----------------------------------------

### Step 1: Package into JAR

Generate the standalone JAR file containing manifest entry points:jar cfe SkillMatrixApp.jar SkillMatrixApp -C bin .

### Step 2: Wrap with Launch4j (.exe)

1.  Open **Launch4j**.
    
2.  Set **Output file** to SkillMatrix.exe.
    
3.  Set **Jar** to SkillMatrixApp.jar.
    
4.  In the **Classpath** tab:
    
    *   Main class: SkillMatrixApp
        
    *   Classpath: lib/\*
        
5.  Click **Build wrapper**.
    

💿 Creating the Windows Installer
---------------------------------

This project uses **Inno Setup** to build a single installer executable (SkillMatrix\_Setup\_v1.0.exe).

1.  Download and install Inno Setup.
    
2.  Open installer.iss in Inno Setup Compiler.
    
3.  Click **Compile (Ctrl + F9)**.
    
4.  The final installer executable will be generated inside the Output/ directory.
    

🎨 Technology Stack
-------------------

*   **UI Framework:** JavaFX
    
*   **Database:** SQLite JDBC
    
*   **Excel Engine:** Apache POI 5.x
    
*   **Styling:** CSS3 (JavaFX CSS Cascading System)
    
*   **Packaging:** Launch4j & Inno Setup