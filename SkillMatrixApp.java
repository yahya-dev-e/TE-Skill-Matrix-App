import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class SkillMatrixApp extends Application {

    private static final String DB_URL = "jdbc:sqlite:master_skills_data_clean2.db";

    // User Session State
    private String currentUser = null;
    private String userRole = null;

    // App State
    private boolean isDarkMode = false;
    private BorderPane root;
    private Button themeBtn;

    // UI Components
    private TextField searchField;
    private ComboBox<String> searchCategoryCombo;
    private Label idValue, nameValue, dateValue, areaValue, leaderValue, statusLabel, userSessionLabel;

    // Master View (Left)
    private TableView<EmployeeRecord> employeeTable;
    private ObservableList<EmployeeRecord> employeeData;

    // Detail View (Right)
    private TableView<SkillRecord> skillsTable;
    private ObservableList<SkillRecord> skillsData;

    @Override
    public void start(Stage primaryStage) {
        ensureDatabaseSchema(); // Automatically handles schema upgrades (cert_path, AuditLogs)

        // Show Login Screen first
        if (!showLoginDialog()) {
            // User closed the login screen without logging in
            System.exit(0);
            return;
        }

        root = new BorderPane();

        // Initialize Theme
        applyTheme();

        root.setTop(createHeader());

        // --- MASTER-DETAIL SPLIT LAYOUT ---
        SplitPane splitPane = new SplitPane();
        splitPane.setStyle("-fx-background-color: -theme-bg; -fx-padding: 20;");

        VBox masterPanel = createMasterPanel();
        VBox detailPanel = createDetailPanel();

        splitPane.getItems().addAll(masterPanel, detailPanel);
        splitPane.setDividerPositions(0.35); // 35% left, 65% right

        root.setCenter(splitPane);
        root.setBottom(createFooter());

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setTitle("TE Connectivity - Skill Matrix System");
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
        primaryStage.show();

        logAction("SYSTEM", "User logged in into application session");
        executeSearch();
    }

    // ==========================================
    // DATABASE & AUDIT LOG SCHEMA MANAGEMENT
    // ==========================================

    private void ensureDatabaseSchema() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
                Statement stmt = conn.createStatement()) {

            // 1. Ensure Qualifications cert_path column exists
            try {
                stmt.execute("ALTER TABLE Qualifications ADD COLUMN cert_path TEXT");
            } catch (SQLException ignored) {
                // Column already exists
            }

            // 2. Create AuditLogs table for traceability
            String createLogTable = "CREATE TABLE IF NOT EXISTS AuditLogs (" +
                    "log_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "timestamp TEXT NOT NULL, " +
                    "user_id TEXT NOT NULL, " +
                    "action_type TEXT NOT NULL, " +
                    "description TEXT NOT NULL" +
                    ");";
            stmt.execute(createLogTable);

        } catch (SQLException e) {
            showAlert("Database Initialization Error", "Could not verify/update database schema:\n" + e.getMessage());
        }
    }

    private void logAction(String actionType, String description) {
        String sql = "INSERT INTO AuditLogs (timestamp, user_id, action_type, description) VALUES (?, ?, ?, ?)";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try (Connection conn = DriverManager.getConnection(DB_URL);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, timestamp);
            pstmt.setString(2, currentUser != null ? currentUser : "UNKNOWN");
            pstmt.setString(3, actionType);
            pstmt.setString(4, description);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Failed to write to Audit Log: " + e.getMessage());
        }
    }

    // ==========================================
    // LOGIN & AUTHENTICATION DIALOG
    // ==========================================

    private boolean showLoginDialog() {
        Dialog<Boolean> loginDialog = new Dialog<>();
        loginDialog.setTitle("TE Skill Matrix - Authentication");
        loginDialog.setHeaderText("Please log in to continue");

        ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        loginDialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField username = new TextField();
        username.setPromptText("Username (e.g. admin or operator)");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(username, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(password, 1, 1);

        loginDialog.getDialogPane().setContent(grid);

        loginDialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                String u = username.getText().trim();
                String p = password.getText().trim();

                // Simple authentication check (Extendable to query DB user table)
                if (u.equalsIgnoreCase("admin") && p.equals("admin123")) {
                    currentUser = "ADMIN_" + u.toUpperCase();
                    userRole = "ADMIN";
                    return true;
                } else if (!u.isEmpty() && p.equals("user123")) {
                    currentUser = u.toUpperCase();
                    userRole = "OPERATOR";
                    return true;
                } else {
                    return false;
                }
            }
            return false;
        });

        Optional<Boolean> result = loginDialog.showAndWait();
        if (result.isPresent() && result.get()) {
            return true;
        } else if (result.isPresent() && !result.get()) {
            showAlert("Access Denied", "Invalid username or password.");
            return showLoginDialog(); // Retry
        }
        return false;
    }

    // ==========================================
    // THEME MANAGEMENT
    // ==========================================

    private void applyTheme() {
        if (isDarkMode) {
            root.setStyle(getDarkThemeVars()
                    + "-fx-background-color: -theme-bg; -fx-font-family: 'Segoe UI', Arial, sans-serif;");
            if (themeBtn != null)
                themeBtn.setText("☀️ Light");
        } else {
            root.setStyle(getLightThemeVars()
                    + "-fx-background-color: -theme-bg; -fx-font-family: 'Segoe UI', Arial, sans-serif;");
            if (themeBtn != null)
                themeBtn.setText("🌙 Dark");
        }
    }

    private String getDarkThemeVars() {
        return "-theme-bg: #121212; -theme-panel: #1E1E1E; -theme-text: #FFFFFF; " +
                "-theme-muted: #B0B0B0; -theme-border: #333333; -theme-accent: #E4770B; " +
                "-theme-shadow: rgba(0,0,0,0.5); ";
    }

    private String getLightThemeVars() {
        return "-theme-bg: #F0F2F5; -theme-panel: #FFFFFF; -theme-text: #1C1E21; " +
                "-theme-muted: #606770; -theme-border: #DDDFE2; -theme-accent: #E4770B; " +
                "-theme-shadow: rgba(0,0,0,0.1); ";
    }

    private String getActiveThemeVars() {
        return isDarkMode ? getDarkThemeVars() : getLightThemeVars();
    }

    // ==========================================
    // UI BUILDER METHODS
    // ==========================================

    private HBox createHeader() {
        HBox header = new HBox(15);
        header.setPadding(new Insets(15, 30, 15, 30));
        header.setStyle(
                "-fx-background-color: -theme-panel; -fx-effect: dropshadow(gaussian, -theme-shadow, 10, 0, 0, 2);");
        header.setAlignment(Pos.CENTER_LEFT);

        try {
            Image logoImage = new Image("file:TE_Connectivity_logo.png");
            ImageView logoView = new ImageView(logoImage);
            logoView.setFitHeight(45);
            logoView.setPreserveRatio(true);
            header.getChildren().add(logoView);
        } catch (Exception e) {
            Label fallbackLabel = new Label("TE SKILL MATRIX");
            fallbackLabel.setStyle("-fx-text-fill: -theme-accent; -fx-font-size: 22px; -fx-font-weight: bold;");
            header.getChildren().add(fallbackLabel);
        }

        // Active Session User Chip
        userSessionLabel = new Label("👤 " + currentUser + " (" + userRole + ")");
        userSessionLabel.setStyle(
                "-fx-text-fill: -theme-text; -fx-font-weight: bold; -fx-background-color: -theme-border; -fx-padding: 6 12; -fx-background-radius: 15;");

        Button logsBtn = new Button("📜 Logs");
        logsBtn.setStyle(
                "-fx-background-color: -theme-bg; -fx-text-fill: -theme-text; -fx-border-color: -theme-border; -fx-border-radius: 4; -fx-cursor: hand;");
        logsBtn.setOnAction(e -> showAuditLogsDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Theme Toggle Button
        themeBtn = new Button();
        themeBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: -theme-text; -fx-cursor: hand; -fx-font-size: 14px; -fx-border-color: -theme-border; -fx-border-radius: 4;");
        themeBtn.setOnAction(e -> {
            isDarkMode = !isDarkMode;
            applyTheme();
        });
        applyTheme();

        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER);
        searchBox.setStyle("-fx-background-color: -theme-border; -fx-padding: 10; -fx-background-radius: 8;");

        Button addEmpBtn = new Button("+ Employee");
        addEmpBtn.setStyle(
                "-fx-background-color: -theme-bg; -fx-text-fill: -theme-text; -fx-background-radius: 4; -fx-cursor: hand;");
        addEmpBtn.setOnAction(e -> showAddEmployeeDialog());

        Button addSkillBtn = new Button("+ Skill");
        addSkillBtn.setStyle(
                "-fx-background-color: -theme-bg; -fx-text-fill: -theme-text; -fx-background-radius: 4; -fx-cursor: hand;");
        addSkillBtn.setOnAction(e -> showAddSkillDialog());

        searchCategoryCombo = new ComboBox<>();
        searchCategoryCombo.getItems().addAll("All Fields", "Employee ID", "Name", "Area", "Team Leader", "Line Number",
                "Station", "Skill Level");
        searchCategoryCombo.setValue("All Fields");
        searchCategoryCombo.setStyle("-fx-background-color: -theme-bg; -fx-background-radius: 4;");

        searchCategoryCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle("-fx-background-color: -theme-panel; -fx-text-fill: -theme-text;");
            }
        });
        searchCategoryCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle("-fx-text-fill: -theme-text;");
            }
        });

        searchField = new TextField();
        searchField.setPromptText("Search anything...");
        searchField.setPrefWidth(200);
        searchField.setStyle(
                "-fx-control-inner-background: -theme-bg; -fx-text-fill: -theme-text; -fx-prompt-text-fill: -theme-muted; -fx-background-radius: 4; -fx-padding: 6;");

        searchCategoryCombo.setOnAction(e -> {
            String selected = searchCategoryCombo.getValue();
            searchField.setPromptText(
                    selected.equals("All Fields") ? "Search anything..." : "Search by " + selected + "...");
        });

        Button searchBtn = new Button("Search");
        searchBtn.setStyle(
                "-fx-background-color: -theme-accent; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;");

        Button clearBtn = new Button("Clear");
        clearBtn.setStyle(
                "-fx-background-color: -theme-bg; -fx-text-fill: -theme-text; -fx-background-radius: 4; -fx-cursor: hand;");

        searchBox.getChildren().addAll(addEmpBtn, addSkillBtn, searchCategoryCombo, searchField, searchBtn, clearBtn);
        header.getChildren().addAll(userSessionLabel, logsBtn, themeBtn, spacer, searchBox);

        searchBtn.setOnAction(e -> executeSearch());
        searchField.setOnAction(e -> executeSearch());
        clearBtn.setOnAction(e -> {
            searchField.clear();
            searchCategoryCombo.setValue("All Fields");
            executeSearch();
        });

        return header;
    }

    private VBox createMasterPanel() {
        VBox panel = new VBox();
        panel.setStyle(
                "-fx-background-color: -theme-panel; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, -theme-shadow, 10, 0, 0, 3);");

        Label headerLabel = new Label("SEARCH RESULTS");
        headerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        headerLabel.setStyle(
                "-fx-background-color: -theme-border; -fx-padding: 10 15; -fx-background-radius: 8 8 0 0; -fx-border-color: -theme-accent; -fx-border-width: 0 0 2 0; -fx-text-fill: -theme-text;");
        headerLabel.setMaxWidth(Double.MAX_VALUE);

        employeeTable = new TableView<>();
        employeeData = FXCollections.observableArrayList();
        employeeTable.setItems(employeeData);
        employeeTable.setStyle(
                "-fx-base: -theme-panel; -fx-control-inner-background: -theme-panel; -fx-background-color: -theme-panel; -fx-table-cell-border-color: -theme-border; -fx-table-header-border-color: -theme-border; -fx-text-background-color: -theme-text;");
        VBox.setVgrow(employeeTable, Priority.ALWAYS);

        TableColumn<EmployeeRecord, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(90);

        TableColumn<EmployeeRecord, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(150);

        TableColumn<EmployeeRecord, String> areaCol = new TableColumn<>("Area");
        areaCol.setCellValueFactory(new PropertyValueFactory<>("area"));
        areaCol.setPrefWidth(120);

        employeeTable.getColumns().addAll(List.of(idCol, nameCol, areaCol));
        employeeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        employeeTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null)
                loadEmployeeDetails(newSelection);
        });

        panel.getChildren().addAll(headerLabel, employeeTable);
        return panel;
    }

    private VBox createDetailPanel() {
        VBox panel = new VBox(20);
        panel.setPadding(new Insets(0, 0, 0, 10));

        VBox profileBox = new VBox(15);
        profileBox.setStyle(
                "-fx-background-color: -theme-panel; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, -theme-shadow, 10, 0, 0, 3);");
        profileBox.setPadding(new Insets(0, 0, 20, 0));

        Label profileHeader = new Label("EMPLOYEE PROFILE");
        profileHeader.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        profileHeader.setStyle(
                "-fx-background-color: -theme-border; -fx-padding: 10 15; -fx-background-radius: 8 8 0 0; -fx-border-color: -theme-accent; -fx-border-width: 0 0 2 0; -fx-text-fill: -theme-text;");
        profileHeader.setMaxWidth(Double.MAX_VALUE);

        HBox profileInfo = new HBox(20);
        profileInfo.setPadding(new Insets(10, 20, 0, 20));

        StackPane avatarPane = new StackPane();
        Circle avatar = new Circle(35, Color.web("#E4770B"));
        Text avatarText = new Text("ID");
        avatarText.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        avatarText.setFill(Color.WHITE);
        avatarPane.getChildren().addAll(avatar, avatarText);

        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(15);

        idValue = createDataLabel("");
        nameValue = createDataLabel("");
        dateValue = createDataLabel("");
        areaValue = createDataLabel("");
        leaderValue = createDataLabel("");

        grid.addRow(0, createTitleLabel("Employee ID"), idValue, createTitleLabel("Start Date"), dateValue);
        grid.addRow(1, createTitleLabel("Full Name"), nameValue, createTitleLabel("Area"), areaValue);
        grid.addRow(2, createTitleLabel("Team Leader"), leaderValue);

        profileInfo.getChildren().addAll(avatarPane, grid);
        profileBox.getChildren().addAll(profileHeader, profileInfo);

        VBox skillsBox = new VBox();
        skillsBox.setStyle(
                "-fx-background-color: -theme-panel; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, -theme-shadow, 10, 0, 0, 3);");
        VBox.setVgrow(skillsBox, Priority.ALWAYS);

        Label skillsHeader = new Label("QUALIFICATIONS & SKILLS (Double-click to open certificate)");
        skillsHeader.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        skillsHeader.setStyle(
                "-fx-background-color: -theme-border; -fx-padding: 10 15; -fx-background-radius: 8 8 0 0; -fx-border-color: -theme-accent; -fx-border-width: 0 0 2 0; -fx-text-fill: -theme-text;");
        skillsHeader.setMaxWidth(Double.MAX_VALUE);

        skillsTable = new TableView<>();
        skillsData = FXCollections.observableArrayList();
        skillsTable.setItems(skillsData);
        skillsTable.setStyle(
                "-fx-base: -theme-panel; -fx-control-inner-background: -theme-panel; -fx-background-color: -theme-panel; -fx-table-cell-border-color: -theme-border; -fx-table-header-border-color: -theme-border; -fx-text-background-color: -theme-text;");
        VBox.setVgrow(skillsTable, Priority.ALWAYS);

        TableColumn<SkillRecord, String> lineCol = new TableColumn<>("Line Number");
        lineCol.setCellValueFactory(new PropertyValueFactory<>("lineNumber"));
        lineCol.setPrefWidth(120);

        TableColumn<SkillRecord, String> stationCol = new TableColumn<>("Station");
        stationCol.setCellValueFactory(new PropertyValueFactory<>("station"));
        stationCol.setPrefWidth(220);

        TableColumn<SkillRecord, String> levelCol = new TableColumn<>("Skill Level");
        levelCol.setCellValueFactory(new PropertyValueFactory<>("level"));
        levelCol.setPrefWidth(220);
        levelCol.setStyle("-fx-font-weight: bold; -fx-text-fill: -theme-accent;");

        skillsTable.getColumns().addAll(List.of(lineCol, stationCol, levelCol));
        skillsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        skillsTable.setRowFactory(tv -> {
            TableRow<SkillRecord> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    SkillRecord rowData = row.getItem();
                    openCertificationFile(rowData.getCertPath());
                }
            });
            return row;
        });

        skillsBox.getChildren().addAll(skillsHeader, skillsTable);
        panel.getChildren().addAll(profileBox, skillsBox);

        return panel;
    }

    private HBox createFooter() {
        HBox footer = new HBox();
        footer.setStyle(
                "-fx-background-color: -theme-bg; -fx-padding: 5 15; -fx-border-color: -theme-border; -fx-border-width: 1 0 0 0;");
        statusLabel = new Label("Database Connected: " + DB_URL + " | Active User: " + currentUser);
        statusLabel.setFont(Font.font("Arial", 12));
        statusLabel.setStyle("-fx-text-fill: -theme-muted;");
        footer.getChildren().add(statusLabel);
        return footer;
    }

    // ==========================================
    // ACTION HANDLERS
    // ==========================================

    private void openCertificationFile(String path) {
        if (path == null || path.trim().isEmpty() || path.equals("null")) {
            showAlert("No Certificate", "There is no certification file attached to this skill.");
            return;
        }

        try {
            File file = new File(path);
            if (file.exists()) {
                Desktop.getDesktop().open(file);
                logAction("VIEW_CERT", "Opened certification document: " + path);
            } else {
                showAlert("File Not Found", "Could not find the file at:\n" + path);
            }
        } catch (Exception ex) {
            showAlert("Error", "Could not open the file.\n" + ex.getMessage());
        }
    }

    // ==========================================
    // LOG VIEWER DIALOG
    // ==========================================

    private void showAuditLogsDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("System Audit & Traceability Logs");

        TableView<LogRecord> logTable = new TableView<>();
        ObservableList<LogRecord> logData = FXCollections.observableArrayList();

        TableColumn<LogRecord, String> timeCol = new TableColumn<>("Timestamp");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timeCol.setPrefWidth(150);

        TableColumn<LogRecord, String> userCol = new TableColumn<>("User ID");
        userCol.setCellValueFactory(new PropertyValueFactory<>("userId"));
        userCol.setPrefWidth(120);

        TableColumn<LogRecord, String> typeCol = new TableColumn<>("Action Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("actionType"));
        typeCol.setPrefWidth(120);

        TableColumn<LogRecord, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(380);

        logTable.getColumns().addAll(List.of(timeCol, userCol, typeCol, descCol));
        logTable.setItems(logData);

        // Query Logs from DB
        String sql = "SELECT timestamp, user_id, action_type, description FROM AuditLogs ORDER BY log_id DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                logData.add(new LogRecord(
                        rs.getString("timestamp"),
                        rs.getString("user_id"),
                        rs.getString("action_type"),
                        rs.getString("description")));
            }
        } catch (SQLException ex) {
            showAlert("Error Loading Logs", ex.getMessage());
        }

        VBox layout = new VBox(10, new Label("Recent Activity Logs:"), logTable);
        layout.setPadding(new Insets(15));
        layout.setStyle(getActiveThemeVars() + "-fx-background-color: -theme-panel;");

        Scene scene = new Scene(layout, 800, 450);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    // ==========================================
    // DATA ENTRY DIALOGS
    // ==========================================

    private void showAddEmployeeDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Add New Employee");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setVgap(10);
        grid.setHgap(10);

        TextField idInput = createDialogField("e.g., TE99999");
        TextField nameInput = createDialogField("Full Name");
        TextField dateInput = createDialogField("YYYY-MM-DD");
        TextField areaInput = createDialogField("Department/Area");
        TextField leaderInput = createDialogField("Manager Name");

        grid.addRow(0, createTitleLabel("Employee ID:"), idInput);
        grid.addRow(1, createTitleLabel("Full Name:"), nameInput);
        grid.addRow(2, createTitleLabel("Start Date:"), dateInput);
        grid.addRow(3, createTitleLabel("Area:"), areaInput);
        grid.addRow(4, createTitleLabel("Team Leader:"), leaderInput);

        Button saveBtn = new Button("Save Employee");
        saveBtn.setStyle("-fx-background-color: -theme-accent; -fx-text-fill: white; -fx-font-weight: bold;");

        saveBtn.setOnAction(e -> {
            String empId = idInput.getText().trim().toUpperCase();
            String empName = nameInput.getText().trim().toUpperCase();

            if (empId.isEmpty() || empName.isEmpty()) {
                showAlert("Validation Error", "Employee ID and Full Name are required.");
                return;
            }

            String sql = "INSERT INTO Employees (id, name, employment_date, area, team_leader) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, empId);
                pstmt.setString(2, empName);
                pstmt.setString(3, dateInput.getText().trim());
                pstmt.setString(4, areaInput.getText().trim().toUpperCase());
                pstmt.setString(5, leaderInput.getText().trim().toUpperCase());
                pstmt.executeUpdate();

                logAction("ADD_EMPLOYEE", "Added employee ID: " + empId + " (" + empName + ")");
                statusLabel.setText("✅ Successfully added employee: " + empId);
                executeSearch();
                dialog.close();
            } catch (SQLException ex) {
                showAlert("Database Error", "Failed to add employee.\n\n" + ex.getMessage());
            }
        });

        VBox layout = new VBox(20, grid, saveBtn);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(10));
        layout.setStyle(getActiveThemeVars()
                + "-fx-background-color: -theme-panel; -fx-font-family: 'Segoe UI', Arial, sans-serif;");

        Scene scene = new Scene(layout, 400, 350);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void showAddSkillDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Add Qualification");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setVgap(10);
        grid.setHgap(10);

        TextField idInput = createDialogField("Employee ID (e.g., TE12345)");
        if (!idValue.getText().isEmpty())
            idInput.setText(idValue.getText());

        TextField lineInput = createDialogField("e.g., A1959827");
        TextField stationInput = createDialogField("e.g., Assemblage Packaging");

        ComboBox<String> levelCombo = new ComboBox<>();
        levelCombo.getItems().addAll(
                "1(Collaborateur en besoin de suivi)", "2(Collaborateur autonome)",
                "3(Collaborateur en maitrise)", "4(Collaborateur capable de former)");
        levelCombo.setValue("1(Collaborateur en besoin de suivi)");
        levelCombo.setStyle("-fx-background-color: -theme-bg;");

        levelCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle("-fx-background-color: -theme-panel; -fx-text-fill: -theme-text;");
            }
        });
        levelCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle("-fx-text-fill: -theme-text;");
            }
        });

        TextField certPathInput = createDialogField("Certification File Path (Optional)");
        certPathInput.setEditable(false);

        Button browseBtn = new Button("Browse...");
        browseBtn.setStyle("-fx-background-color: -theme-border; -fx-text-fill: -theme-text; -fx-cursor: hand;");
        browseBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Certification Document");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Documents & Images", "*.pdf", "*.png", "*.jpg", "*.jpeg"));
            File selectedFile = fileChooser.showOpenDialog(dialog);
            if (selectedFile != null) {
                certPathInput.setText(selectedFile.getAbsolutePath());
            }
        });
        HBox pathBox = new HBox(5, certPathInput, browseBtn);
        HBox.setHgrow(certPathInput, Priority.ALWAYS);

        grid.addRow(0, createTitleLabel("Employee ID:"), idInput);
        grid.addRow(1, createTitleLabel("Line Number:"), lineInput);
        grid.addRow(2, createTitleLabel("Station:"), stationInput);
        grid.addRow(3, createTitleLabel("Skill Level:"), levelCombo);
        grid.addRow(4, createTitleLabel("Certificate:"), pathBox);

        Button saveBtn = new Button("Save Qualification");
        saveBtn.setStyle("-fx-background-color: -theme-accent; -fx-text-fill: white; -fx-font-weight: bold;");

        saveBtn.setOnAction(e -> {
            String empId = idInput.getText().trim().toUpperCase();
            String station = stationInput.getText().trim();
            String level = levelCombo.getValue();

            if (empId.isEmpty() || station.isEmpty()) {
                showAlert("Validation Error", "Employee ID and Station are required.");
                return;
            }

            String sql = "INSERT INTO Qualifications (employee_id, line_number, station, qualification_level, cert_path) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, empId);
                pstmt.setString(2, lineInput.getText().trim());
                pstmt.setString(3, station);
                pstmt.setString(4, level);
                pstmt.setString(5, certPathInput.getText().trim());
                pstmt.executeUpdate();

                logAction("ADD_SKILL", "Added skill '" + station + "' (" + level + ") for employee: " + empId);
                statusLabel.setText("✅ Successfully added skill for: " + empId);

                if (empId.equals(idValue.getText())) {
                    loadEmployeeDetails(employeeTable.getSelectionModel().getSelectedItem());
                }
                dialog.close();
            } catch (SQLException ex) {
                showAlert("Database Error", "Failed to add qualification.\n\n" + ex.getMessage());
            }
        });

        VBox layout = new VBox(20, grid, saveBtn);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(10));
        layout.setStyle(getActiveThemeVars()
                + "-fx-background-color: -theme-panel; -fx-font-family: 'Segoe UI', Arial, sans-serif;");

        Scene scene = new Scene(layout, 500, 350);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private TextField createDialogField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setStyle(
                "-fx-control-inner-background: -theme-bg; -fx-text-fill: -theme-text; -fx-prompt-text-fill: -theme-muted;");
        return field;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ==========================================
    // HELPER STYLING METHODS
    // ==========================================

    private Label createTitleLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        label.setStyle(
                "-fx-background-color: -theme-border; -fx-padding: 8; -fx-background-radius: 4; -fx-text-fill: -theme-muted;");
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private Label createDataLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Arial", 13));
        label.setStyle(
                "-fx-background-color: -theme-bg; -fx-padding: 8; -fx-background-radius: 4; -fx-border-color: -theme-border; -fx-border-radius: 4; -fx-text-fill: -theme-text;");
        label.setPrefWidth(180);
        return label;
    }

    private void clearProfileFields() {
        idValue.setText("");
        nameValue.setText("");
        dateValue.setText("");
        areaValue.setText("");
        leaderValue.setText("");
        skillsData.clear();
    }

    // ==========================================
    // DATABASE LOGIC
    // ==========================================

    private void executeSearch() {
        String keyword = searchField.getText().trim();
        String searchType = searchCategoryCombo.getValue();

        employeeData.clear();
        clearProfileFields();

        String sqlBase = "SELECT DISTINCT e.id, e.name, e.area, e.employment_date, e.team_leader " +
                "FROM Employees e " +
                "LEFT JOIN Qualifications q ON e.id = q.employee_id ";
        String sqlWhere = "";

        if (keyword.isEmpty()) {
            sqlWhere = "WHERE 1=1";
        } else {
            switch (searchType) {
                case "Employee ID":
                    sqlWhere = "WHERE e.id LIKE ?";
                    break;
                case "Name":
                    sqlWhere = "WHERE e.name LIKE ?";
                    break;
                case "Area":
                    sqlWhere = "WHERE e.area LIKE ?";
                    break;
                case "Team Leader":
                    sqlWhere = "WHERE e.team_leader LIKE ?";
                    break;
                case "Line Number":
                    sqlWhere = "WHERE q.line_number LIKE ?";
                    break;
                case "Station":
                    sqlWhere = "WHERE q.station LIKE ?";
                    break;
                case "Skill Level":
                    sqlWhere = "WHERE q.qualification_level LIKE ?";
                    break;
                case "All Fields":
                default:
                    sqlWhere = "WHERE e.id LIKE ? OR e.name LIKE ? OR e.area LIKE ? " +
                            "OR e.team_leader LIKE ? OR q.line_number LIKE ? " +
                            "OR q.station LIKE ? OR q.qualification_level LIKE ?";
                    break;
            }
        }

        String sql = sqlBase + sqlWhere;

        try (Connection conn = DriverManager.getConnection(DB_URL);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            if (!keyword.isEmpty()) {
                String searchPattern = "%" + keyword + "%";
                if (searchType.equals("All Fields")) {
                    for (int i = 1; i <= 7; i++)
                        pstmt.setString(i, searchPattern);
                } else {
                    pstmt.setString(1, searchPattern);
                }
            }

            ResultSet rs = pstmt.executeQuery();
            int count = 0;
            while (rs.next()) {
                employeeData.add(new EmployeeRecord(
                        rs.getString("id"), rs.getString("name"),
                        rs.getString("area"), rs.getString("employment_date"),
                        rs.getString("team_leader")));
                count++;
            }
            statusLabel.setText("✅ Found " + count + " employees matching criteria | Logged as: " + currentUser);

            if (!employeeData.isEmpty())
                employeeTable.getSelectionModel().selectFirst();

        } catch (SQLException ex) {
            statusLabel.setText("⚠️ Database Error: " + ex.getMessage());
        }
    }

    private void loadEmployeeDetails(EmployeeRecord emp) {
        idValue.setText(emp.getId());
        nameValue.setText(emp.getName() != null ? emp.getName().toUpperCase() : "N/A");
        areaValue.setText(emp.getArea() != null ? emp.getArea().toUpperCase() : "N/A");
        dateValue.setText(emp.getDate() != null ? emp.getDate() : "N/A");
        leaderValue.setText(emp.getLeader() != null ? emp.getLeader().toUpperCase() : "N/A");

        skillsData.clear();

        String skillsQuery = "SELECT line_number, station, qualification_level, cert_path FROM Qualifications WHERE employee_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
                PreparedStatement pstmt = conn.prepareStatement(skillsQuery)) {

            pstmt.setString(1, emp.getId());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                skillsData.add(new SkillRecord(
                        rs.getString("line_number"),
                        rs.getString("station"),
                        rs.getString("qualification_level"),
                        rs.getString("cert_path")));
            }
            statusLabel.setText("✅ Loaded profile for " + emp.getId());
        } catch (SQLException ex) {
            statusLabel.setText("⚠️ Error loading skills: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    // ==========================================
    // DATA MODELS
    // ==========================================

    public static class EmployeeRecord {
        private final SimpleStringProperty id, name, area, date, leader;

        public EmployeeRecord(String id, String name, String area, String date, String leader) {
            this.id = new SimpleStringProperty(id);
            this.name = new SimpleStringProperty(name);
            this.area = new SimpleStringProperty(area);
            this.date = new SimpleStringProperty(date);
            this.leader = new SimpleStringProperty(leader);
        }

        public String getId() {
            return id.get();
        }

        public String getName() {
            return name.get();
        }

        public String getArea() {
            return area.get();
        }

        public String getDate() {
            return date.get();
        }

        public String getLeader() {
            return leader.get();
        }
    }

    public static class SkillRecord {
        private final SimpleStringProperty lineNumber;
        private final SimpleStringProperty station;
        private final SimpleStringProperty level;
        private final SimpleStringProperty certPath;

        public SkillRecord(String lineNumber, String station, String level, String certPath) {
            this.lineNumber = new SimpleStringProperty(lineNumber);
            this.station = new SimpleStringProperty(station);
            this.level = new SimpleStringProperty(level);
            this.certPath = new SimpleStringProperty(certPath);
        }

        public String getLineNumber() {
            return lineNumber.get();
        }

        public String getStation() {
            return station.get();
        }

        public String getLevel() {
            return level.get();
        }

        public String getCertPath() {
            return certPath.get();
        }
    }

    public static class LogRecord {
        private final SimpleStringProperty timestamp;
        private final SimpleStringProperty userId;
        private final SimpleStringProperty actionType;
        private final SimpleStringProperty description;

        public LogRecord(String timestamp, String userId, String actionType, String description) {
            this.timestamp = new SimpleStringProperty(timestamp);
            this.userId = new SimpleStringProperty(userId);
            this.actionType = new SimpleStringProperty(actionType);
            this.description = new SimpleStringProperty(description);
        }

        public String getTimestamp() {
            return timestamp.get();
        }

        public String getUserId() {
            return userId.get();
        }

        public String getActionType() {
            return actionType.get();
        }

        public String getDescription() {
            return description.get();
        }
    }
}