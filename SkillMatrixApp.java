import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Apache POI Imports
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class SkillMatrixApp extends Application {

    private final DatabaseManager dbManager = new DatabaseManager();

    // Session State
    private boolean isDarkMode = true;
    private String currentUser = "admin";

    // Active qualification filter context
    private String activeLineFilter = null;
    private String activeStationFilter = null;
    private String activeLevelFilter = null;

    // View Navigation Container
    private StackPane rootStack;

    // View Screens
    private VBox loginView;
    private BorderPane dashboardView;
    private BorderPane mainMatrixView;

    // Header & Controls
    private Button themeBtn;
    private TextField searchField;
    private ComboBox<String> searchCategoryCombo;
    private Label idValue, nameValue, dateValue, areaValue, leaderValue, statusLabel;
    private Text avatarText;

    // Search Result Count Label
    private Label resultsHeaderLabel;

    // Tables
    private TableView<EmployeeRecord> employeeTable;
    private ObservableList<EmployeeRecord> employeeData;
    private TableView<SkillRecord> skillsTable;
    private ObservableList<SkillRecord> skillsData;

    @Override
    public void start(Stage primaryStage) {
        rootStack = new StackPane();
        rootStack.getStyleClass().add("root-container");

        // Build Login View Screen initially
        buildLoginView();

        Scene scene = new Scene(rootStack, 1280, 800);
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

        rootStack.getChildren().add(loginView);
        dbManager.logAction("SYSTEM", "START", "Application launched on login screen.");
    }

    // ==========================================
    // 🔒 1. LOGIN SCREEN
    // ==========================================

    private void buildLoginView() {
        loginView = new VBox(20);
        loginView.setAlignment(Pos.CENTER);
        loginView.setMaxSize(400, 480);
        loginView.getStyleClass().add("card-panel");
        loginView.setPadding(new Insets(35));

        ImageView logoView = new ImageView();
        try {
            Image logoImage = new Image("file:TE_Connectivity_logo.png");
            logoView.setImage(logoImage);
            logoView.setFitWidth(180);
            logoView.setPreserveRatio(true);
        } catch (Exception ignored) {
        }

        Label titleLabel = new Label("Skill Matrix System");
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        TextField usernameInput = new TextField();
        usernameInput.setPromptText("Username");
        usernameInput.getStyleClass().add("input-field");

        PasswordField passwordInput = new PasswordField();
        passwordInput.setPromptText("Password");
        passwordInput.getStyleClass().add("input-field");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #D9381E; -fx-font-weight: bold;");

        Button loginBtn = new Button("Sign In");
        loginBtn.getStyleClass().add("btn-primary");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setStyle("-fx-font-size: 15px; -fx-padding: 10;");

        loginBtn.setOnAction(e -> {
            String user = usernameInput.getText().trim();
            String pass = passwordInput.getText().trim();

            if (dbManager.validateUser(user, pass)) {
                currentUser = user.isEmpty() ? "admin" : user;
                dbManager.logAction(currentUser, "LOGIN", "Successful login.");
                showDashboardScreen();
            } else {
                errorLabel.setText("Invalid credentials (try admin / admin123)");
            }
        });

        loginView.getChildren().addAll(logoView, titleLabel, usernameInput, passwordInput, loginBtn, errorLabel);
    }

    // ==========================================
    // 📊 2. DASHBOARD ANALYTICS SCREEN
    // ==========================================

    private void showDashboardScreen() {
        dashboardView = new BorderPane();

        // Top Header Bar
        HBox topHeader = new HBox(15);
        topHeader.getStyleClass().add("header-bar");
        topHeader.setPadding(new Insets(12, 25, 12, 25));
        topHeader.setAlignment(Pos.CENTER_LEFT);

        try {
            Image logoImage = new Image("file:TE_Connectivity_logo.png");
            ImageView logoView = new ImageView(logoImage);
            logoView.setFitHeight(38);
            logoView.setPreserveRatio(true);
            topHeader.getChildren().add(logoView);
        } catch (Exception e) {
            Label brand = new Label("TE CONNECTIVITY");
            brand.getStyleClass().add("panel-header-label");
            topHeader.getChildren().add(brand);
        }

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button dashThemeBtn = new Button(isDarkMode ? "☀️ Light" : "🌙 Dark");
        dashThemeBtn.getStyleClass().add("btn-secondary");
        dashThemeBtn.setOnAction(e -> {
            isDarkMode = !isDarkMode;
            applyTheme();
            dashThemeBtn.setText(isDarkMode ? "☀️ Light" : "🌙 Dark");
        });

        Button logoutBtn = new Button("Logout");
        logoutBtn.getStyleClass().add("btn-danger");
        logoutBtn.setOnAction(e -> {
            dbManager.logAction(currentUser, "LOGOUT", "Logged out from application.");
            rootStack.getChildren().clear();
            rootStack.getChildren().add(loginView);
        });

        topHeader.getChildren().addAll(headerSpacer, dashThemeBtn, logoutBtn);
        dashboardView.setTop(topHeader);

        // Center Content Container
        VBox contentBox = new VBox(25);
        contentBox.setPadding(new Insets(25, 40, 25, 40));
        contentBox.setAlignment(Pos.TOP_LEFT);

        // Fixed Greeting
        Label greetingLabel = new Label("Hello, " + currentUser);
        greetingLabel.getStyleClass().add("dashboard-greeting");

        // Cards Grid (Side-by-side)
        HBox cardsBox = new HBox(35);
        cardsBox.setAlignment(Pos.CENTER);

        // CARD 1: Total Employees (% Women / % Men Count Pie Chart)
        VBox empCard = new VBox(15);
        empCard.getStyleClass().add("card-panel");
        empCard.setPadding(new Insets(25));
        empCard.setAlignment(Pos.CENTER);
        HBox.setHgrow(empCard, Priority.ALWAYS);

        int totalEmp = dbManager.getTotalEmployeesCount();
        Label empTitle = new Label("Total Employees");
        empTitle.getStyleClass().add("metric-card-title");

        Label empCount = new Label(String.valueOf(totalEmp));
        empCount.setStyle("-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: -theme-accent;");

        PieChart genderChart = new PieChart();
        Map<String, Integer> genderMap = dbManager.getGenderDistribution();

        // Custom formatting: "145 Women" / "192 Men"
        genderMap.forEach((gender, count) -> {
            String sliceLabel = count + " " + gender;
            genderChart.getData().add(new PieChart.Data(sliceLabel, count));
        });
        genderChart.setPrefSize(300, 220);
        genderChart.setLegendVisible(true);

        empCard.getChildren().addAll(empTitle, empCount, genderChart);

        // CARD 2: Flexibility Rate Chart
        VBox flexCard = new VBox(15);
        flexCard.getStyleClass().add("card-panel");
        flexCard.setPadding(new Insets(25));
        flexCard.setAlignment(Pos.CENTER);
        HBox.setHgrow(flexCard, Priority.ALWAYS);

        double flexRate = dbManager.getFlexibilityRate();
        Label flexTitle = new Label("Flexibility Rate");
        flexTitle.getStyleClass().add("metric-card-title");

        Label flexPercentage = new Label(String.format("%.1f%%", flexRate));
        flexPercentage.setStyle("-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: -theme-accent;");

        PieChart flexChart = new PieChart();
        flexChart.getData().add(new PieChart.Data("Flexible", flexRate));
        flexChart.getData().add(new PieChart.Data("Single Skill", 100.0 - flexRate));
        flexChart.setPrefSize(300, 220);
        flexChart.setLegendVisible(true);

        flexCard.getChildren().addAll(flexTitle, flexPercentage, flexChart);

        cardsBox.getChildren().addAll(empCard, flexCard);

        // Centered Navigation Button
        HBox btnContainer = new HBox();
        btnContainer.setAlignment(Pos.CENTER);
        Button matrixBtn = new Button("Skill Matrix ➔");
        matrixBtn.getStyleClass().add("btn-primary");
        matrixBtn.setStyle("-fx-font-size: 18px; -fx-padding: 12 45;");
        matrixBtn.setOnAction(e -> showMainMatrixScreen());
        btnContainer.getChildren().add(matrixBtn);

        contentBox.getChildren().addAll(greetingLabel, cardsBox, btnContainer);
        dashboardView.setCenter(contentBox);

        rootStack.getChildren().clear();
        rootStack.getChildren().add(dashboardView);
    }

    // ==========================================
    // 📂 3. MAIN SKILL MATRIX SCREEN
    // ==========================================

    private void showMainMatrixScreen() {
        if (mainMatrixView == null) {
            mainMatrixView = new BorderPane();

            SplitPane splitPane = new SplitPane();
            splitPane.setStyle("-fx-background-color: transparent; -fx-padding: 15;");

            VBox masterPanel = createMasterPanel();
            VBox detailPanel = createDetailPanel();

            splitPane.getItems().addAll(masterPanel, detailPanel);
            splitPane.setDividerPositions(0.35);

            mainMatrixView.setTop(createHeader());
            mainMatrixView.setCenter(splitPane);
            mainMatrixView.setBottom(createFooter());

            executeSearch();
        } else {
            if (themeBtn != null) {
                themeBtn.setText(isDarkMode ? "☀️ Light" : "🌙 Dark");
            }
        }

        rootStack.getChildren().clear();
        rootStack.getChildren().add(mainMatrixView);
    }

    // ==========================================
    // SECURITY PASSWORD PROMPT
    // ==========================================

    private boolean promptAdminPassword(String actionDescription) {
        Dialog<String> passwordDialog = new Dialog<>();
        passwordDialog.setTitle("Admin Security Check");
        passwordDialog.setHeaderText("🔒 Admin Password Required\nAction: " + actionDescription);

        ButtonType confirmButtonType = new ButtonType("Authorize", ButtonBar.ButtonData.OK_DONE);
        passwordDialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        PasswordField passwordInput = new PasswordField();
        passwordInput.setPromptText("Admin Password");

        grid.add(new Label("Password:"), 0, 0);
        grid.add(passwordInput, 1, 0);

        passwordDialog.getDialogPane().setContent(grid);
        passwordDialog.setResultConverter(
                dialogButton -> dialogButton == confirmButtonType ? passwordInput.getText().trim() : null);

        Optional<String> result = passwordDialog.showAndWait();
        if (result.isPresent()) {
            if (dbManager.verifyAdminPassword(result.get())) {
                return true;
            } else {
                showFriendlyError("Access Denied", "Incorrect password entered. Action cancelled.");
            }
        }
        return false;
    }

    // ==========================================
    // THEMING
    // ==========================================

    private void applyTheme() {
        rootStack.getStyleClass().removeAll("theme-dark", "theme-light");
        rootStack.getStyleClass().add(isDarkMode ? "theme-dark" : "theme-light");

        String bgImage = isDarkMode ? "Dark_mode_bg.png" : "Light_mode_bg.png";
        rootStack.setStyle("-fx-background-image: url('file:" + bgImage + "');");

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
        HBox header = new HBox(12);
        header.setPadding(new Insets(10, 15, 10, 15));
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);

        Button backToDashBtn = new Button("⬅ Dashboard");
        backToDashBtn.getStyleClass().add("btn-secondary");
        backToDashBtn.setOnAction(e -> showDashboardScreen());

        try {
            Image logoImage = new Image("file:TE_Connectivity_logo.png");
            ImageView logoView = new ImageView(logoImage);
            logoView.setFitHeight(36);
            logoView.setPreserveRatio(true);
            header.getChildren().addAll(backToDashBtn, logoView);
        } catch (Exception e) {
            Label fallbackLabel = new Label("TE SKILL MATRIX");
            fallbackLabel.getStyleClass().add("panel-header-label");
            header.getChildren().addAll(backToDashBtn, fallbackLabel);
        }

        Button logsBtn = new Button("📜 Logs");
        logsBtn.getStyleClass().add("btn-secondary");
        logsBtn.setOnAction(e -> showAuditLogsDialog());

        themeBtn = new Button(isDarkMode ? "☀️ Light" : "🌙 Dark");
        themeBtn.getStyleClass().add("btn-secondary");
        themeBtn.setOnAction(e -> {
            isDarkMode = !isDarkMode;
            applyTheme();
            themeBtn.setText(isDarkMode ? "☀️ Light" : "🌙 Dark");
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox searchBox = new HBox(6);
        searchBox.setAlignment(Pos.CENTER);

        Button addEmpBtn = new Button("+ Employee");
        addEmpBtn.getStyleClass().add("btn-secondary");
        addEmpBtn.setOnAction(e -> showAddEmployeeDialog());

        Button removeEmpBtn = new Button("- Employee");
        removeEmpBtn.getStyleClass().add("btn-danger");
        removeEmpBtn.setOnAction(e -> handleRemoveSelectedEmployee());

        Button addSkillBtn = new Button("+ Skill");
        addSkillBtn.getStyleClass().add("btn-secondary");
        addSkillBtn.setOnAction(e -> showAddSkillDialog());

        searchCategoryCombo = new ComboBox<>();
        searchCategoryCombo.getItems().addAll("All Fields", "Employee ID", "Name", "Area", "Team Leader", "Line Number",
                "Station", "Skill Level");
        searchCategoryCombo.setValue("All Fields");
        searchCategoryCombo.getStyleClass().add("btn-secondary");

        searchField = new TextField();
        searchField.setPromptText("Search anything...");
        searchField.setPrefWidth(150);
        searchField.getStyleClass().add("input-field");

        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("btn-primary");

        Button multiFilterBtn = new Button("🔍 Advanced Filters");
        multiFilterBtn.getStyleClass().add("btn-secondary");
        multiFilterBtn.setOnAction(e -> showAdvancedFilterDialog());

        Button exportBtn = new Button("📊 Export Excel");
        exportBtn.getStyleClass().add("btn-secondary");
        exportBtn.setOnAction(e -> exportFilteredResultsToExcel());

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().add("btn-secondary");

        searchBox.getChildren().addAll(addEmpBtn, removeEmpBtn, addSkillBtn, searchCategoryCombo, searchField,
                searchBtn, multiFilterBtn, exportBtn, clearBtn);
        header.getChildren().addAll(logsBtn, themeBtn, spacer, searchBox);

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

        HBox headerContainer = new HBox();
        headerContainer.setAlignment(Pos.CENTER_LEFT);
        headerContainer.getStyleClass().add("panel-header-label");

        Label titleLabel = new Label("SEARCH RESULTS");
        titleLabel.setFont(javafx.scene.text.Font.font("Arial", FontWeight.BOLD, 14));
        titleLabel.getStyleClass().add("panel-header-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        resultsHeaderLabel = new Label("Total Employees: 0");
        resultsHeaderLabel.setFont(javafx.scene.text.Font.font("Arial", FontWeight.BOLD, 12));
        resultsHeaderLabel.getStyleClass().add("panel-header-text");
        resultsHeaderLabel.setStyle("-fx-padding: 0 10 0 0;");

        headerContainer.getChildren().addAll(titleLabel, spacer, resultsHeaderLabel);

        employeeTable = new TableView<>();
        employeeData = FXCollections.observableArrayList();
        employeeTable.setItems(employeeData);
        VBox.setVgrow(employeeTable, Priority.ALWAYS);

        TableColumn<EmployeeRecord, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<EmployeeRecord, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<EmployeeRecord, String> leaderCol = new TableColumn<>("Team Leader");
        leaderCol.setCellValueFactory(new PropertyValueFactory<>("leader"));

        employeeTable.getColumns().addAll(List.of(idCol, nameCol, leaderCol));
        employeeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        employeeTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null)
                loadEmployeeDetails(newSelection);
        });

        ContextMenu cm = new ContextMenu();
        MenuItem removeItem = new MenuItem("🗑️ Remove Selected Employee");
        removeItem.setOnAction(e -> handleRemoveSelectedEmployee());
        cm.getItems().add(removeItem);
        employeeTable.setContextMenu(cm);

        panel.getChildren().addAll(headerContainer, employeeTable);
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
        profileHeader.setFont(javafx.scene.text.Font.font("Arial", FontWeight.BOLD, 14));
        profileHeader.getStyleClass().add("panel-header-text");

        Region profileSpacer = new Region();
        HBox.setHgrow(profileSpacer, Priority.ALWAYS);

        Button removeProfileBtn = new Button("🗑️ Remove");
        removeProfileBtn.getStyleClass().add("btn-danger");
        removeProfileBtn.setOnAction(e -> handleRemoveSelectedEmployee());

        profileHeaderBox.getChildren().addAll(profileHeader, profileSpacer, removeProfileBtn);

        HBox profileInfo = new HBox(20);
        profileInfo.setPadding(new Insets(10, 20, 0, 20));

        StackPane avatarPane = new StackPane();
        Circle avatar = new Circle(32, Color.web("#E4770B"));
        avatarText = new Text("TE");
        avatarText.setFont(javafx.scene.text.Font.font("Arial", FontWeight.BOLD, 16));
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
        skillsHeader.getStyleClass().add("panel-header-text");
        skillsHeader.setMaxWidth(Double.MAX_VALUE);

        skillsTable = new TableView<>();
        skillsData = FXCollections.observableArrayList();
        skillsTable.setItems(skillsData);
        VBox.setVgrow(skillsTable, Priority.ALWAYS);

        TableColumn<SkillRecord, String> areaCol = new TableColumn<>("Area");
        areaCol.setCellValueFactory(new PropertyValueFactory<>("area"));

        TableColumn<SkillRecord, String> lineCol = new TableColumn<>("Line Number");
        lineCol.setCellValueFactory(new PropertyValueFactory<>("lineNumber"));

        TableColumn<SkillRecord, String> stationCol = new TableColumn<>("Station");
        stationCol.setCellValueFactory(new PropertyValueFactory<>("station"));

        TableColumn<SkillRecord, String> levelCol = new TableColumn<>("Skill Level");
        levelCol.setCellValueFactory(new PropertyValueFactory<>("level"));

        skillsTable.getColumns().addAll(List.of(areaCol, lineCol, stationCol, levelCol));
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

        statusLabel = new Label("Database Connected");
        statusLabel.setFont(javafx.scene.text.Font.font("Arial", 12));
        statusLabel.getStyleClass().add("status-label");

        footer.getChildren().add(statusLabel);
        return footer;
    }

    // ==========================================
    // EXPORT TO EXCEL FEATURE
    // ==========================================

    private void exportFilteredResultsToExcel() {
        if (employeeData == null || employeeData.isEmpty()) {
            showFriendlyError("Export Unavailable",
                    "No employee records found to export. Please execute a search first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Skill Matrix Export");
        fileChooser.setInitialFileName("Skill_Matrix_Filtered_Export.xlsx");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"));

        File file = fileChooser.showSaveDialog(rootStack.getScene().getWindow());
        if (file == null) {
            return;
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font poiHeaderFont = workbook.createFont();
            poiHeaderFont.setBold(true);
            headerStyle.setFont(poiHeaderFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet sheetEmp = workbook.createSheet("Filtered Employees");

            Row headerRow = sheetEmp.createRow(0);
            String[] empHeaders = { "Employee ID", "Full Name", "Start Date", "Area", "Team Leader" };
            for (int i = 0; i < empHeaders.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(empHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (EmployeeRecord emp : employeeData) {
                Row row = sheetEmp.createRow(rowNum++);
                row.createCell(0).setCellValue(emp.getId());
                row.createCell(1).setCellValue(emp.getName());
                row.createCell(2).setCellValue(emp.getDate() != null ? emp.getDate() : "");
                row.createCell(3).setCellValue(emp.getArea() != null ? emp.getArea() : "");
                row.createCell(4).setCellValue(emp.getLeader() != null ? emp.getLeader() : "");
            }

            for (int i = 0; i < empHeaders.length; i++) {
                sheetEmp.autoSizeColumn(i);
            }

            Sheet sheetSkills = workbook.createSheet("Detailed Qualifications");
            Row skillHeaderRow = sheetSkills.createRow(0);
            String[] skillHeaders = { "Employee ID", "Full Name", "Area", "Line Number", "Station", "Skill Level" };
            for (int i = 0; i < skillHeaders.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = skillHeaderRow.createCell(i);
                cell.setCellValue(skillHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            int skillRowNum = 1;
            for (EmployeeRecord emp : employeeData) {
                List<SkillRecord> skills = dbManager.fetchEmployeeSkills(
                        emp.getId(), activeLineFilter, activeStationFilter, activeLevelFilter);

                for (SkillRecord skill : skills) {
                    Row row = sheetSkills.createRow(skillRowNum++);
                    row.createCell(0).setCellValue(emp.getId());
                    row.createCell(1).setCellValue(emp.getName());
                    row.createCell(2).setCellValue(skill.getArea());
                    row.createCell(3).setCellValue(skill.getLineNumber());
                    row.createCell(4).setCellValue(skill.getStation());
                    row.createCell(5).setCellValue(skill.getLevel());
                }
            }

            for (int i = 0; i < skillHeaders.length; i++) {
                sheetSkills.autoSizeColumn(i);
            }

            try (FileOutputStream fileOut = new FileOutputStream(file)) {
                workbook.write(fileOut);
            }

            dbManager.logAction(currentUser, "EXPORT_EXCEL",
                    "Exported " + employeeData.size() + " records to Excel: " + file.getName());
            if (statusLabel != null) {
                statusLabel.setText("✅ Export successful: " + file.getName());
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export Complete");
            alert.setHeaderText(null);
            alert.setContentText("Excel file generated successfully!\nPath: " + file.getAbsolutePath());
            alert.showAndWait();

        } catch (Exception ex) {
            System.err.println("[Export Error] " + ex.getMessage());
            ex.printStackTrace();
            showFriendlyError("Export Failed", "Could not create Excel file: " + ex.getMessage());
        }
    }

    // ==========================================
    // LOGIC & EVENT HANDLERS
    // ==========================================

    private void executeSearch() {
        String keyword = searchField != null ? searchField.getText().trim() : "";
        String searchType = searchCategoryCombo != null ? searchCategoryCombo.getValue() : "All Fields";

        activeLineFilter = null;
        activeStationFilter = null;
        activeLevelFilter = null;

        if (!keyword.isEmpty()) {
            if ("Line Number".equals(searchType))
                activeLineFilter = keyword;
            else if ("Station".equals(searchType))
                activeStationFilter = keyword;
            else if ("Skill Level".equals(searchType))
                activeLevelFilter = keyword;
        }

        if (employeeData != null)
            employeeData.clear();
        clearProfileFields();

        try {
            List<EmployeeRecord> results = dbManager.searchEmployees(keyword, searchType);
            if (employeeData != null)
                employeeData.addAll(results);

            if (resultsHeaderLabel != null) {
                resultsHeaderLabel.setText("Total Employees: " + results.size());
            }

            if (statusLabel != null) {
                statusLabel.setText("✅ Found " + results.size() + " employees matching criteria");
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

    private void showAdvancedFilterDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Advanced Multi-Filter Search");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setVgap(12);
        grid.setHgap(10);

        TextField idInput = createDialogField("e.g. TE307569");
        TextField nameInput = createDialogField("e.g. HALIMA");
        TextField leaderInput = createDialogField("e.g. HAMZA");
        TextField areaInput = createDialogField("e.g. Area 1");
        TextField lineInput = createDialogField("e.g. A1014280");
        TextField stationInput = createDialogField("e.g. Assemblage");
        TextField levelInput = createDialogField("e.g. 4");

        grid.addRow(0, createTitleLabel("Employee ID:"), idInput);
        grid.addRow(1, createTitleLabel("Full Name:"), nameInput);
        grid.addRow(2, createTitleLabel("Team Leader:"), leaderInput);
        grid.addRow(3, createTitleLabel("Area:"), areaInput);
        grid.addRow(4, createTitleLabel("Line Number:"), lineInput);
        grid.addRow(5, createTitleLabel("Station:"), stationInput);
        grid.addRow(6, createTitleLabel("Skill Level:"), levelInput);

        Button applyBtn = new Button("Apply Combined Filters");
        applyBtn.getStyleClass().add("btn-primary");

        applyBtn.setOnAction(e -> {
            if (employeeData != null)
                employeeData.clear();
            clearProfileFields();

            activeLineFilter = lineInput.getText().trim();
            activeStationFilter = stationInput.getText().trim();
            activeLevelFilter = levelInput.getText().trim();

            try {
                List<EmployeeRecord> results = dbManager.searchEmployeesMultiFilter(
                        idInput.getText(),
                        nameInput.getText(),
                        leaderInput.getText(),
                        areaInput.getText(),
                        lineInput.getText(),
                        stationInput.getText(),
                        levelInput.getText());

                employeeData.addAll(results);

                if (resultsHeaderLabel != null) {
                    resultsHeaderLabel.setText("Total Employees: " + results.size());
                }

                if (statusLabel != null) {
                    statusLabel.setText("✅ Filter applied | Found " + results.size() + " matching employees");
                }

                if (employeeTable != null && !employeeData.isEmpty()) {
                    employeeTable.getSelectionModel().selectFirst();
                }

                dialog.close();
            } catch (SQLException ex) {
                System.err.println("[Multi-Filter Error] " + ex.getMessage());
                ex.printStackTrace();
                showFriendlyError("Filter Error", "Could not execute advanced query.");
            }
        });

        VBox layout = new VBox(15, grid, applyBtn);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(10));
        layout.getStyleClass().addAll("root-container", isDarkMode ? "theme-dark" : "theme-light");

        Scene scene = new Scene(layout, 450, 420);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void loadEmployeeDetails(EmployeeRecord emp) {
        if (emp == null)
            return;

        idValue.setText(emp.getId());
        nameValue.setText(emp.getName() != null ? emp.getName().toUpperCase() : "N/A");
        areaValue.setText(emp.getArea() != null ? emp.getArea().toUpperCase() : "N/A");
        dateValue.setText(emp.getDate() != null ? emp.getDate() : "N/A");
        leaderValue.setText(emp.getLeader() != null ? emp.getLeader().toUpperCase() : "N/A");

        avatarText.setText("TE");

        skillsData.clear();
        try {
            skillsData.addAll(dbManager.fetchEmployeeSkills(
                    emp.getId(),
                    activeLineFilter,
                    activeStationFilter,
                    activeLevelFilter));

            if (statusLabel != null)
                statusLabel.setText("Loaded profile for " + emp.getId());
        } catch (SQLException e) {
            System.err.println("[Skill Fetch Error] " + e.getMessage());
            e.printStackTrace();
            showFriendlyError("Data Fetch Error", "Could not load skills profile for the selected employee.");
        }
    }

    private void handleRemoveSelectedEmployee() {
        EmployeeRecord selected = employeeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showFriendlyError("Selection Required", "Please select an employee profile to remove.");
            return;
        }

        if (!promptAdminPassword("Remove employee " + selected.getName() + " (" + selected.getId() + ")")) {
            return;
        }

        try {
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
        } catch (SQLException e) {
            System.err.println("[Delete Error] " + e.getMessage());
            e.printStackTrace();
            showFriendlyError("Database Error", "An error occurred while removing the employee record.");
        }
    }

    private void showAddEmployeeDialog() {
        if (!promptAdminPassword("Add new employee")) {
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

        ComboBox<String> genderCombo = new ComboBox<>();
        genderCombo.getItems().addAll("Women", "Men");
        genderCombo.setValue("Women");
        genderCombo.getStyleClass().add("btn-secondary");

        grid.addRow(0, createTitleLabel("Employee ID:"), idInput);
        grid.addRow(1, createTitleLabel("Full Name:"), nameInput);
        grid.addRow(2, createTitleLabel("Start Date:"), dateInput);
        grid.addRow(3, createTitleLabel("Area:"), areaInput);
        grid.addRow(4, createTitleLabel("Team Leader:"), leaderInput);
        grid.addRow(5, createTitleLabel("Gender:"), genderCombo);

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
                        areaInput.getText().trim().toUpperCase(), leaderInput.getText().trim().toUpperCase(),
                        genderCombo.getValue());
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

        Scene scene = new Scene(layout, 400, 390);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void showAddSkillDialog() {
        if (!promptAdminPassword("Add new qualification/skill")) {
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

        TextField areaInput = createDialogField("e.g., Area 1");
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
        grid.addRow(1, createTitleLabel("Area:"), areaInput);
        grid.addRow(2, createTitleLabel("Line Number:"), lineInput);
        grid.addRow(3, createTitleLabel("Station:"), stationInput);
        grid.addRow(4, createTitleLabel("Skill Level:"), levelCombo);
        grid.addRow(5, createTitleLabel("Certificate:"), pathBox);

        Button saveBtn = new Button("Save Qualification");
        saveBtn.getStyleClass().add("btn-primary");

        saveBtn.setOnAction(e -> {
            String empId = idInput.getText().trim().toUpperCase();
            String station = stationInput.getText().trim();
            String area = areaInput.getText().trim();

            if (empId.isEmpty() || station.isEmpty()) {
                showFriendlyError("Validation Error", "Employee ID and Station are required.");
                return;
            }

            try {
                dbManager.addSkillWithArea(empId, area, lineInput.getText().trim(), station, levelCombo.getValue(),
                        certPathInput.getText().trim());
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

        Scene scene = new Scene(layout, 500, 380);
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
            avatarText.setText("TE");
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
        private final SimpleStringProperty area, lineNumber, station, level, certPath;

        public SkillRecord(String area, String lineNumber, String station, String level, String certPath) {
            this.area = new SimpleStringProperty(area != null ? area : "N/A");
            this.lineNumber = new SimpleStringProperty(lineNumber);
            this.station = new SimpleStringProperty(station);
            this.level = new SimpleStringProperty(level);
            this.certPath = new SimpleStringProperty(certPath);
        }

        public String getArea() {
            return area.get();
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