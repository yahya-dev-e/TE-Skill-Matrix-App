# TE Connectivity - Skill Matrix Application 📊

A desktop application developed with **JavaFX** and backed by an **SQLite** relational database. This application enables team leaders and plant operators to search, inspect, update, and trace manufacturing qualifications and skills across production lines.

---

## 🌟 Key Features

* **Master-Detail View:** Easily browse and filter employee profiles on the left while dynamically inspecting qualifications, lines, and certification documents on the right.
* **Audit & Traceability Logging:** Every critical action (login, record creation, certificate inspection) is automatically saved with timestamps to an `AuditLogs` SQLite table for full compliance tracking.
* **Authentication & User Sessions:** Integrated role-aware login modal (`ADMIN` vs. `OPERATOR`) to trace system actions back to specific active users.
* **Certificate Document Viewer:** Double-click any qualification entry to instantly launch attached PDF/image certifications via the system's default desktop viewer.
* **Adaptive Light/Dark Theme:** Real-time theme toggling designed with modern styling variables.

---

## 🛠️ Project Structure

```text
TE-Skill-Matrix-App/
├── SkillMatrixApp.java         # Main JavaFX Application & UI
├── DatabaseMigration.java      # Schema migration helper utilities
├── TE_Connectivity_logo.png    # Header branding asset
├── build.xml                   # Build configuration
└── .gitignore                  # Git tracking rules (excludes DB and local binaries)
```

---

## 🚀 Getting Started

### Prerequisites

1. **Java Development Kit (JDK 17+ or 21+):**
   * Recommended: Azul Zulu JDK with JavaFX (bundled with JavaFX support out of the box).
2. **SQLite JDBC Driver:**
   * Download `sqlite-jdbc-3.x.x.jar` (e.g., SQLite JDBC Releases).

---

## ⚙️ IDE Setup Instructions (VS Code / Antigravity)

1. **Clone the Repository:**
   ```bash
   git clone [https://github.com/your-username/TE-Skill-Matrix-App.git](https://github.com/your-username/TE-Skill-Matrix-App.git)
   cd TE-Skill-Matrix-App
   ```

2. **Add SQLite Driver to Classpath:**
   * Open the project in your IDE.
   * Locate the **Java Projects** tab in the sidebar.
   * Scroll down to **Referenced Libraries** and click **`+`**.
   * Select your local `sqlite-jdbc-*.jar` file to add it to the build path.

3. **Run the Application:**
   * Open `SkillMatrixApp.java`.
   * Click **Run** or use `F5`.

---

## 🔐 Default Login Credentials

Upon launch, enter one of the built-in session credentials:

| Role | Username | Password |
| :--- | :--- | :--- |
| **Administrator** | `admin` | `admin123` |
| **Operator** | `operator` (or any username) | `user123` |

---

## 🗄️ Database Auto-Initialization

* The SQLite database file (`master_skills_data_clean2.db`) is excluded from Git tracking for data privacy and security.
* On first launch, `ensureDatabaseSchema()` will **automatically create** the local database file and generate the required `Qualifications` and `AuditLogs` schema tables.