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
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class SkillMatrixApp extends Application {

    private final DatabaseManager dbManager = new DatabaseManager();

    // Session & Exit Counter State
    private String currentUser = null;
    private String userRole = null;
    private boolean isDarkMode = false;
    private int exitCounter = 0;

    // UI Structure
    private BorderPane root;
    private Button themeBtn;
    private TextField searchField;
    private ComboBox<String> searchCategoryCombo;
    private Label idValue, nameValue, dateValue, areaValue, leaderValue, statusLabel, userSessionLabel;
    private Text avatarText;

    // Tables
    private TableView<EmployeeRecord> employeeTable;
    private ObservableList<EmployeeRecord> employeeData;
    private TableView<SkillRecord> skillsTable;
    private ObservableList<SkillRecord> skillsData;

    @Override
    public void start(Stage primaryStage) {
        if (!showLoginDialog()) {
            System.exit(0);
            return;
        }

        root = new BorderPane();
        root.getStyleClass().add("root-container");

        SplitPane splitPane = new SplitPane();
        splitPane.setStyle("-fx-background-color: transparent; -fx-padding: 15;");

        VBox masterPanel = createMasterPanel();
        VBox detailPanel = createDetailPanel();

        splitPane.getItems().addAll(masterPanel, detailPanel);
        splitPane.setDividerPositions(0.35);

        root.setTop(createHeader());
        root.setCenter(splitPane);
        root.setBottom(createFooter());

        Scene scene = new Scene(root, 1200, 800);
        try {
            var cssUrl = getClass().getResource("styles.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            } else {
                File fallbackCss = new File("styles.css");
                if (fallbackCss.exists()) {
                    scene.getStylesheets().add(fallbackCss.toURI().toURL().toExternalForm());
                }
            }
        } catch (Exception e) {
            System.err.println("[CSS Warning] Stylesheet could not be loaded: " + e.getMessage());
        }

        applyTheme();

        primaryStage.setTitle("TE Connectivity - Skill Matrix System");
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
        primaryStage.show();

        dbManager.logAction(currentUser, "SYSTEM", "User logged into session");
        executeSearch();
    }

    // ==========================================
    // USER AUTHENTICATION MODAL & EXIT COUNTER
    // ==========================================

    private boolean showLoginDialog() {
        while (true) {
            Stage loginStage = new Stage();
            loginStage.initModality(Modality.APPLICATION_MODAL);
            loginStage.setTitle("TE Skill Matrix - Authentication");
            loginStage.setResizable(false);

            final boolean[] success = { false };
            final boolean[] failedAttempt = { false };

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(12);
            grid.setPadding(new Insets(20));

            Label headerLabel = new Label("Please sign in with your credentials");
            headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            TextField username = new TextField();
            username.setPromptText("Username");

            PasswordField password = new PasswordField();
            password.setPromptText("Password");

            grid.add(headerLabel, 0, 0, 2, 1);
            grid.add(new Label("Username:"), 0, 1);
            grid.add(username, 1, 1);
            grid.add(new Label("Password:"), 0, 2);
            grid.add(password, 1, 2);

            Button loginBtn = new Button("Login");
            loginBtn.setDefaultButton(true);

            Button cancelBtn = new Button("Cancel");
            cancelBtn.setCancelButton(true);

            HBox buttonBox = new HBox(10, loginBtn, cancelBtn);
            buttonBox.setAlignment(Pos.CENTER_RIGHT);

            loginBtn.setOnAction(e -> {
                try {
                    DatabaseManager.UserSession session = dbManager.authenticate(username.getText().trim(),
                            password.getText().trim());
                    if (session != null) {
                        currentUser = session.getUsername();
                        userRole = session.getRole();
                        success[0] = true;
                    } else {
                        failedAttempt[0] = true;
                    }
                } catch (SQLException ex) {
                    System.err.println("[Auth Error] " + ex.getMessage());
                    failedAttempt[0] = true;
                }
                loginStage.close();
            });

            cancelBtn.setOnAction(e -> loginStage.close());

            VBox layout = new VBox(15, grid, buttonBox);
            layout.setPadding(new Insets(15));
            layout.setStyle("-fx-background-color: -fx-control-inner-background;");

            Scene scene = new Scene(layout, 360, 200);
            loginStage.setScene(scene);

            loginStage.showAndWait();

            // 1. Granted access
            if (success[0]) {
                return true;
            }

            // 2. Parity increments
            if (failedAttempt[0]) {
                exitCounter += 2; // Failed credential attempt
            } else {
                exitCounter += 1; // User clicked Cancel or closed window ('X')
            }

            // 3. Parity Check: If ODD -> Terminate program instantly
            evaluateExitCounter();

            // 4. Parity Check: If EVEN -> Prompt error and display login again
            showFriendlyError("Access Denied", "Invalid username or password. Please verify your credentials.");
        }
    }

    private void evaluateExitCounter() {
        if (exitCounter % 2 != 0) {
            System.exit(0);
        }
    }

    // ==========================================
    // THEMING
    // ==========================================

    private void applyTheme() {
        root.getStyleClass().removeAll("theme-dark", "theme-light");
        root.getStyleClass().add(isDarkMode ? "theme-dark" : "theme-light");

        String bgImage = isDarkMode ? "Dark_mode_bg.png" : "Light_mode_bg.png";
        root.setStyle("-fx-background-image: url('file:" + bgImage + "');");

        if (themeBtn != null) {
            themeBtn.setText(isDarkMode ? "☀️ Light" : "🌙 Dark");
        }

        if (employeeTable != null && skillsTable != null) {
            employeeTable.getStyleClass().removeAll("table-dark", "table-light");
            skillsTable.getStyleClass().removeAll("table-dark", "table-light");

            String tableClass = isDarkMode ? "table-dark" : "table-light";
            employeeTable.getStyleClass().add(tableClass);
            skillsTable.getStyleClass().add(tableClass);
        }
    }

    // ==========================================
    // UI BUILDER METHODS
    // ==========================================

    private HBox createHeader() {
        HBox header = new HBox(15);
        header.setPadding(new Insets(12, 20, 12, 20));
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);

        try {
            Image logoImage = new Image("file:TE_Connectivity_logo.png");
            ImageView logoView = new ImageView(logoImage);
            logoView.setFitHeight(40);
            logoView.setPreserveRatio(true);
            header.getChildren().add(logoView);
        } catch (Exception e) {
            Label fallbackLabel = new Label("TE SKILL MATRIX");
            fallbackLabel.getStyleClass().add("panel-header-label");
            header.getChildren().add(fallbackLabel);
        }

        userSessionLabel = new Label("👤 " + currentUser + " (" + userRole + ")");
        userSessionLabel.getStyleClass().add("user-badge");

        Button logsBtn = new Button("📜 Logs");
        logsBtn.getStyleClass().add("btn-secondary");
        logsBtn.setOnAction(e -> showAuditLogsDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        themeBtn = new Button();
        themeBtn.getStyleClass().add("btn-secondary");
        themeBtn.setOnAction(e -> {
            isDarkMode = !isDarkMode;
            applyTheme();
        });

        HBox searchBox = new HBox(8);
        searchBox.setAlignment(Pos.CENTER);
        searchBox.getStyleClass().add("search-box-container");

        if ("ADMIN".equals(userRole)) {
            Button addEmpBtn = new Button("+ Employee");
            addEmpBtn.getStyleClass().add("btn-secondary");
            addEmpBtn.setOnAction(e -> showAddEmployeeDialog());

            Button removeEmpBtn = new Button("- Employee");
            removeEmpBtn.getStyleClass().add("btn-danger");
            removeEmpBtn.setOnAction(e -> handleRemoveSelectedEmployee());

            Button addSkillBtn = new Button("+ Skill");
            addSkillBtn.getStyleClass().add("btn-secondary");
            addSkillBtn.setOnAction(e -> showAddSkillDialog());

            searchBox.getChildren().addAll(addEmpBtn, removeEmpBtn, addSkillBtn);
        }

        searchCategoryCombo = new ComboBox<>();
        searchCategoryCombo.getItems().addAll("All Fields", "Employee ID", "Name", "Area", "Team Leader", "Line Number",
                "Station", "Skill Level");
        searchCategoryCombo.setValue("All Fields");
        searchCategoryCombo.getStyleClass().add("btn-secondary");

        searchField = new TextField();
        searchField.setPromptText("Search anything...");
        searchField.setPrefWidth(160);
        searchField.getStyleClass().add("input-field");

        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("btn-primary");

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().add("btn-secondary");

        searchBox.getChildren().addAll(searchCategoryCombo, searchField, searchBtn, clearBtn);
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
        panel.getStyleClass().add("card-panel");

        Label headerLabel = new Label("SEARCH RESULTS");
        headerLabel.getStyleClass().add("panel-header-label");
        headerLabel.setMaxWidth(Double.MAX_VALUE);

        employeeTable = new TableView<>();
        employeeData = FXCollections.observableArrayList();
        employeeTable.setItems(employeeData);
        VBox.setVgrow(employeeTable, Priority.ALWAYS);

        TableColumn<EmployeeRecord, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<EmployeeRecord, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<EmployeeRecord, String> areaCol = new TableColumn<>("Area");
        areaCol.setCellValueFactory(new PropertyValueFactory<>("area"));

        employeeTable.getColumns().addAll(List.of(idCol, nameCol, areaCol));
        employeeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        employeeTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null)
                loadEmployeeDetails(newSelection);
        });

        if ("ADMIN".equals(userRole)) {
            ContextMenu cm = new ContextMenu();
            MenuItem removeItem = new MenuItem("🗑️ Remove Selected Employee");
            removeItem.setOnAction(e -> handleRemoveSelectedEmployee());
            cm.getItems().add(removeItem);
            employeeTable.setContextMenu(cm);
        }

        panel.getChildren().addAll(headerLabel, employeeTable);
        return panel;
    }

    private VBox createDetailPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(0, 0, 0, 5));

        VBox profileBox = new VBox(10);
        profileBox.getStyleClass().add("card-panel");
        profileBox.setPadding(new Insets(0, 0, 15, 0));

        HBox profileHeaderBox = new HBox();
        profileHeaderBox.setAlignment(Pos.CENTER_LEFT);
        profileHeaderBox.getStyleClass().add("panel-header-label");

        Label profileHeader = new Label("EMPLOYEE PROFILE");
        profileHeader.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Region profileSpacer = new Region();
        HBox.setHgrow(profileSpacer, Priority.ALWAYS);

        profileHeaderBox.getChildren().addAll(profileHeader, profileSpacer);

        if ("ADMIN".equals(userRole)) {
            Button removeProfileBtn = new Button("🗑️ Remove");
            removeProfileBtn.getStyleClass().add("btn-danger");
            removeProfileBtn.setOnAction(e -> handleRemoveSelectedEmployee());
            profileHeaderBox.getChildren().add(removeProfileBtn);
        }

        HBox profileInfo = new HBox(20);
        profileInfo.setPadding(new Insets(10, 20, 0, 20));

        StackPane avatarPane = new StackPane();
        Circle avatar = new Circle(32, Color.web("#E4770B"));
        avatarText = new Text("ID");
        avatarText.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        avatarText.setFill(Color.WHITE);
        avatarPane.getChildren().addAll(avatar, avatarText);

        GridPane grid = new GridPane();
        grid.setVgap(8);
        grid.setHgap(12);

        idValue = createDataLabel("");
        nameValue = createDataLabel("");
        dateValue = createDataLabel("");
        areaValue = createDataLabel("");
        leaderValue = createDataLabel("");

        grid.addRow(0, createTitleLabel("Employee ID"), idValue, createTitleLabel("Start Date"), dateValue);
        grid.addRow(1, createTitleLabel("Full Name"), nameValue, createTitleLabel("Area"), areaValue);
        grid.addRow(2, createTitleLabel("Team Leader"), leaderValue);

        profileInfo.getChildren().addAll(avatarPane, grid);
        profileBox.getChildren().addAll(profileHeaderBox, profileInfo);

        VBox skillsBox = new VBox();
        skillsBox.getStyleClass().add("card-panel");
        VBox.setVgrow(skillsBox, Priority.ALWAYS);

        Label skillsHeader = new Label("QUALIFICATIONS & SKILLS (Double-click to open certificate)");
        skillsHeader.getStyleClass().add("panel-header-label");
        skillsHeader.setMaxWidth(Double.MAX_VALUE);

        skillsTable = new TableView<>();
        skillsData = FXCollections.observableArrayList();
        skillsTable.setItems(skillsData);
        VBox.setVgrow(skillsTable, Priority.ALWAYS);

        TableColumn<SkillRecord, String> lineCol = new TableColumn<>("Line Number");
        lineCol.setCellValueFactory(new PropertyValueFactory<>("lineNumber"));

        TableColumn<SkillRecord, String> stationCol = new TableColumn<>("Station");
        stationCol.setCellValueFactory(new PropertyValueFactory<>("station"));

        TableColumn<SkillRecord, String> levelCol = new TableColumn<>("Skill Level");
        levelCol.setCellValueFactory(new PropertyValueFactory<>("level"));

        skillsTable.getColumns().addAll(List.of(lineCol, stationCol, levelCol));
        skillsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        skillsTable.setRowFactory(tv -> {
            TableRow<SkillRecord> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty()) && row.getItem() != null) {
                    openCertificationFile(row.getItem().getCertPath());
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
        footer.getStyleClass().add("header-bar");
        footer.setPadding(new Insets(5, 15, 5, 15));

        statusLabel = new Label("Database Connected | Active User: " + currentUser);
        statusLabel.setFont(Font.font("Arial", 12));
        footer.getChildren().add(statusLabel);
        return footer;
    }

    // ==========================================
    // LOGIC & EVENT HANDLERS
    // ==========================================

    private void executeSearch() {
        String keyword = searchField != null ? searchField.getText().trim() : "";
        String searchType = searchCategoryCombo != null ? searchCategoryCombo.getValue() : "All Fields";

        if (employeeData != null)
            employeeData.clear();
        clearProfileFields();

        try {
            List<EmployeeRecord> results = dbManager.searchEmployees(keyword, searchType);
            employeeData.addAll(results);

            if (statusLabel != null) {
                statusLabel
                        .setText("✅ Found " + results.size() + " employees matching criteria | Active: " + currentUser);
            }
            if (employeeTable != null && !employeeData.isEmpty()) {
                employeeTable.getSelectionModel().selectFirst();
            }
        } catch (SQLException e) {
            System.err.println("[Search Failed] " + e.getMessage());
            e.printStackTrace();
            showFriendlyError("Search Error", "Unable to retrieve employee records. Please try refining your query.");
        }
    }

    private void loadEmployeeDetails(EmployeeRecord emp) {
        if (emp == null)
            return;

        idValue.setText(emp.getId());
        nameValue.setText(emp.getName() != null ? emp.getName().toUpperCase() : "N/A");
        areaValue.setText(emp.getArea() != null ? emp.getArea().toUpperCase() : "N/A");
        dateValue.setText(emp.getDate() != null ? emp.getDate() : "N/A");
        leaderValue.setText(emp.getLeader() != null ? emp.getLeader().toUpperCase() : "N/A");

        if (emp.getId().length() >= 2) {
            avatarText.setText(emp.getId().substring(0, 2).toUpperCase());
        } else {
            avatarText.setText("ID");
        }

        skillsData.clear();
        try {
            skillsData.addAll(dbManager.fetchEmployeeSkills(emp.getId()));
            if (statusLabel != null)
                statusLabel.setText("✅ Loaded profile for " + emp.getId());
        } catch (SQLException e) {
            System.err.println("[Skill Fetch Error] " + e.getMessage());
            e.printStackTrace();
            showFriendlyError("Data Fetch Error", "Could not load skills profile for the selected employee.");
        }
    }

    private void handleRemoveSelectedEmployee() {
        if (!"ADMIN".equals(userRole)) {
            showFriendlyError("Access Denied", "Only administrative accounts can remove employee profiles.");
            return;
        }

        EmployeeRecord selected = employeeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showFriendlyError("Selection Required", "Please select an employee profile to remove.");
            return;
        }

        Dialog<String> passwordDialog = new Dialog<>();
        passwordDialog.setTitle("Security Check - Delete Employee");
        passwordDialog.setHeaderText("⚠️ CAUTION: Deleting " + selected.getName() + " (" + selected.getId() + ")");

        ButtonType confirmButtonType = new ButtonType("Confirm Delete", ButtonBar.ButtonData.OK_DONE);
        passwordDialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        Label warningLabel = new Label(
                "This action is permanent and removes all associated qualifications.\nEnter Admin Password:");
        warningLabel.setWrapText(true);

        PasswordField passwordInput = new PasswordField();
        passwordInput.setPromptText("Admin Password");

        grid.add(warningLabel, 0, 0, 2, 1);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordInput, 1, 1);

        passwordDialog.getDialogPane().setContent(grid);
        passwordDialog.setResultConverter(
                dialogButton -> dialogButton == confirmButtonType ? passwordInput.getText().trim() : null);

        Optional<String> result = passwordDialog.showAndWait();
        if (result.isPresent()) {
            try {
                if (dbManager.verifyAdminPassword(result.get())) {
                    boolean success = dbManager.deleteEmployeeTransaction(selected.getId());
                    if (success) {
                        dbManager.logAction(currentUser, "DELETE_EMPLOYEE",
                                "Removed employee: " + selected.getId() + " (" + selected.getName() + ")");
                        if (statusLabel != null)
                            statusLabel.setText("✅ Removed employee: " + selected.getId());
                        clearProfileFields();
                        executeSearch();
                    } else {
                        showFriendlyError("Delete Failed", "The employee record could not be found in the system.");
                    }
                } else {
                    showFriendlyError("Security Check Failed", "Incorrect password entered. Operation cancelled.");
                }
            } catch (SQLException e) {
                System.err.println("[Delete Error] " + e.getMessage());
                e.printStackTrace();
                showFriendlyError("Database Error", "An error occurred while removing the employee record.");
            }
        }
    }

    private void showAddEmployeeDialog() {
        if (!"ADMIN".equals(userRole)) {
            showFriendlyError("Access Denied", "Only administrative accounts can register employees.");
            return;
        }

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
        saveBtn.getStyleClass().add("btn-primary");

        saveBtn.setOnAction(e -> {
            String empId = idInput.getText().trim().toUpperCase();
            String empName = nameInput.getText().trim().toUpperCase();

            if (empId.isEmpty() || empName.isEmpty()) {
                showFriendlyError("Validation Error", "Employee ID and Full Name are required fields.");
                return;
            }

            try {
                dbManager.addEmployee(empId, empName, dateInput.getText().trim(),
                        areaInput.getText().trim().toUpperCase(), leaderInput.getText().trim().toUpperCase());
                dbManager.logAction(currentUser, "ADD_EMPLOYEE", "Added employee ID: " + empId + " (" + empName + ")");
                statusLabel.setText("✅ Added employee: " + empId);
                executeSearch();
                dialog.close();
            } catch (SQLException ex) {
                System.err.println("[Add Employee Error] " + ex.getMessage());
                ex.printStackTrace();
                showFriendlyError("Registration Failed", "Could not save new employee. The ID may already exist.");
            }
        });

        VBox layout = new VBox(20, grid, saveBtn);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(10));
        layout.getStyleClass().addAll("root-container", isDarkMode ? "theme-dark" : "theme-light");

        Scene scene = new Scene(layout, 400, 350);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void showAddSkillDialog() {
        if (!"ADMIN".equals(userRole)) {
            showFriendlyError("Access Denied", "Only administrative accounts can add qualifications.");
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Add Qualification");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setVgap(10);
        grid.setHgap(10);

        TextField idInput = createDialogField("Employee ID (e.g., TE12345)");
        if (idValue != null && !idValue.getText().isEmpty())
            idInput.setText(idValue.getText());

        TextField lineInput = createDialogField("e.g., A1959827");
        TextField stationInput = createDialogField("e.g., Assemblage Packaging");

        ComboBox<String> levelCombo = new ComboBox<>();
        levelCombo.getItems().addAll(
                "1(Collaborateur en besoin de suivi)", "2(Collaborateur autonome)",
                "3(Collaborateur en maitrise)", "4(Collaborateur capable de former)");
        levelCombo.setValue("1(Collaborateur en besoin de suivi)");

        TextField certPathInput = createDialogField("Certification File Path (Optional)");
        certPathInput.setEditable(false);

        Button browseBtn = new Button("Browse...");
        browseBtn.getStyleClass().add("btn-secondary");
        browseBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Certification Document");
            fileChooser.getExtensionFilters()
                    .addAll(new FileChooser.ExtensionFilter("Documents & Images", "*.pdf", "*.png", "*.jpg", "*.jpeg"));
            File selectedFile = fileChooser.showOpenDialog(dialog);
            if (selectedFile != null)
                certPathInput.setText(selectedFile.getAbsolutePath());
        });

        HBox pathBox = new HBox(5, certPathInput, browseBtn);
        HBox.setHgrow(certPathInput, Priority.ALWAYS);

        grid.addRow(0, createTitleLabel("Employee ID:"), idInput);
        grid.addRow(1, createTitleLabel("Line Number:"), lineInput);
        grid.addRow(2, createTitleLabel("Station:"), stationInput);
        grid.addRow(3, createTitleLabel("Skill Level:"), levelCombo);
        grid.addRow(4, createTitleLabel("Certificate:"), pathBox);

        Button saveBtn = new Button("Save Qualification");
        saveBtn.getStyleClass().add("btn-primary");

        saveBtn.setOnAction(e -> {
            String empId = idInput.getText().trim().toUpperCase();
            String station = stationInput.getText().trim();
            String level = levelCombo.getValue();

            if (empId.isEmpty() || station.isEmpty()) {
                showFriendlyError("Validation Error", "Employee ID and Station are required.");
                return;
            }

            try {
                dbManager.addSkill(empId, lineInput.getText().trim(), station, level, certPathInput.getText().trim());
                dbManager.logAction(currentUser, "ADD_SKILL", "Added skill '" + station + "' for: " + empId);
                statusLabel.setText("✅ Added skill for: " + empId);

                if (idValue != null && empId.equals(idValue.getText())) {
                    loadEmployeeDetails(employeeTable.getSelectionModel().getSelectedItem());
                }
                dialog.close();
            } catch (SQLException ex) {
                System.err.println("[Add Skill Error] " + ex.getMessage());
                ex.printStackTrace();
                showFriendlyError("Save Failed", "Could not save skill record. Please check the details entered.");
            }
        });

        VBox layout = new VBox(20, grid, saveBtn);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(10));
        layout.getStyleClass().addAll("root-container", isDarkMode ? "theme-dark" : "theme-light");

        Scene scene = new Scene(layout, 500, 350);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void openCertificationFile(String path) {
        if (path == null || path.trim().isEmpty() || path.equalsIgnoreCase("null")) {
            showFriendlyError("No Certificate", "There is no certification document linked to this qualification.");
            return;
        }

        try {
            File file = new File(path);
            if (file.exists()) {
                Desktop.getDesktop().open(file);
                dbManager.logAction(currentUser, "VIEW_CERT", "Opened certificate: " + path);
            } else {
                showFriendlyError("File Not Found",
                        "The requested document file could not be located at the target path.");
            }
        } catch (Exception ex) {
            System.err.println("[File Open Error] " + ex.getMessage());
            ex.printStackTrace();
            showFriendlyError("Launch Error", "Failed to open the attachment file via the operating system.");
        }
    }

    private void showAuditLogsDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("System Audit & Traceability Logs");

        TableView<LogRecord> logTable = new TableView<>();
        ObservableList<LogRecord> logData = FXCollections.observableArrayList();

        TableColumn<LogRecord, String> timeCol = new TableColumn<>("Timestamp");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));

        TableColumn<LogRecord, String> userCol = new TableColumn<>("User ID");
        userCol.setCellValueFactory(new PropertyValueFactory<>("userId"));

        TableColumn<LogRecord, String> typeCol = new TableColumn<>("Action Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("actionType"));

        TableColumn<LogRecord, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));

        logTable.getColumns().addAll(List.of(timeCol, userCol, typeCol, descCol));
        logTable.setItems(logData);

        try {
            logData.addAll(dbManager.fetchAuditLogs());
        } catch (SQLException ex) {
            System.err.println("[Log Load Error] " + ex.getMessage());
            ex.printStackTrace();
            showFriendlyError("Log Fetch Failed", "Unable to load system audit history.");
        }

        VBox layout = new VBox(10, new Label("Recent Activity Logs:"), logTable);
        layout.setPadding(new Insets(15));
        layout.getStyleClass().addAll("root-container", isDarkMode ? "theme-dark" : "theme-light");

        Scene scene = new Scene(layout, 800, 450);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    // ==========================================
    // HELPERS & ALERTS
    // ==========================================

    private void showFriendlyError(String title, String userFriendlyMessage) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(userFriendlyMessage);
        alert.showAndWait();
    }

    private TextField createDialogField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("input-field");
        return field;
    }

    private Label createTitleLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("title-label");
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private Label createDataLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("data-label");
        label.setPrefWidth(180);
        return label;
    }

    private void clearProfileFields() {
        if (idValue != null)
            idValue.setText("");
        if (nameValue != null)
            nameValue.setText("");
        if (dateValue != null)
            dateValue.setText("");
        if (areaValue != null)
            areaValue.setText("");
        if (leaderValue != null)
            leaderValue.setText("");
        if (avatarText != null)
            avatarText.setText("ID");
        if (skillsData != null)
            skillsData.clear();
    }

    public static void main(String[] args) {
        launch(args);
    }

    // ==========================================
    // DATA MODEL BEANS
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
        private final SimpleStringProperty lineNumber, station, level, certPath;

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
        private final SimpleStringProperty timestamp, userId, actionType, description;

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