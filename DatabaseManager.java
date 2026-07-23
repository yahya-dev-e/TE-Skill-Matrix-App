import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:master_skills_data_clean2.db";

    public DatabaseManager() {
        initSchema();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    // ==========================================
    // SCHEMA INITIALIZATION & SEEDING
    // ==========================================

    private void initSchema() {
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            // 1. Qualifications Table Patch
            try {
                stmt.execute("ALTER TABLE Qualifications ADD COLUMN cert_path TEXT");
            } catch (SQLException ignored) {
                // Column already exists
            }

            // 2. Audit Logs Table
            stmt.execute("CREATE TABLE IF NOT EXISTS AuditLogs (" +
                    "log_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "timestamp TEXT NOT NULL, " +
                    "user_id TEXT NOT NULL, " +
                    "action_type TEXT NOT NULL, " +
                    "description TEXT NOT NULL" +
                    ");");

            // 3. Hashed Credentials Users Table
            stmt.execute("CREATE TABLE IF NOT EXISTS Users (" +
                    "username TEXT PRIMARY KEY, " +
                    "password_hash TEXT NOT NULL, " +
                    "role TEXT NOT NULL" +
                    ");");

            seedDefaultUsers(conn);

        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Schema initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void seedDefaultUsers(Connection conn) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM Users";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) == 0) {
                String insertSql = "INSERT INTO Users (username, password_hash, role) VALUES (?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                    // Seed Admin (admin / admin123)
                    pstmt.setString(1, "ADMIN");
                    pstmt.setString(2, hashPassword("admin123"));
                    pstmt.setString(3, "ADMIN");
                    pstmt.executeUpdate();

                    // Seed Operator (operator / user123)
                    pstmt.setString(1, "OPERATOR");
                    pstmt.setString(2, hashPassword("user123"));
                    pstmt.setString(3, "OPERATOR");
                    pstmt.executeUpdate();
                }
            }
        }
    }

    // ==========================================
    // SECURITY & AUTHENTICATION
    // ==========================================

    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    public UserSession authenticate(String username, String password) throws SQLException {
        String sql = "SELECT role FROM Users WHERE UPPER(username) = ? AND password_hash = ?";
        String hashed = hashPassword(password);

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username.toUpperCase().trim());
            pstmt.setString(2, hashed);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String role = rs.getString("role");
                    String sessionName = role.equals("ADMIN") ? "ADMIN_" + username.toUpperCase()
                            : username.toUpperCase();
                    return new UserSession(sessionName, role);
                }
            }
        }
        return null;
    }

    public boolean verifyAdminPassword(String password) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Users WHERE role = 'ADMIN' AND password_hash = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hashPassword(password));
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // ==========================================
    // AUDIT LOGGING DAO
    // ==========================================

    public void logAction(String userId, String actionType, String description) {
        String sql = "INSERT INTO AuditLogs (timestamp, user_id, action_type, description) VALUES (?, ?, ?, ?)";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, timestamp);
            pstmt.setString(2, userId != null ? userId : "UNKNOWN");
            pstmt.setString(3, actionType);
            pstmt.setString(4, description);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Audit log insert failed: " + e.getMessage());
        }
    }

    public List<SkillMatrixApp.LogRecord> fetchAuditLogs() throws SQLException {
        List<SkillMatrixApp.LogRecord> logs = new ArrayList<>();
        String sql = "SELECT timestamp, user_id, action_type, description FROM AuditLogs ORDER BY log_id DESC";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                logs.add(new SkillMatrixApp.LogRecord(
                        rs.getString("timestamp"),
                        rs.getString("user_id"),
                        rs.getString("action_type"),
                        rs.getString("description")));
            }
        }
        return logs;
    }

    // ==========================================
    // EMPLOYEE & SKILL OPERATIONS
    // ==========================================

    public List<SkillMatrixApp.EmployeeRecord> searchEmployees(String keyword, String searchType) throws SQLException {
        List<SkillMatrixApp.EmployeeRecord> results = new ArrayList<>();
        String sqlBase = "SELECT DISTINCT e.id, e.name, e.area, e.employment_date, e.team_leader " +
                "FROM Employees e LEFT JOIN Qualifications q ON e.id = q.employee_id ";
        String sqlWhere;

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
                default:
                    sqlWhere = "WHERE e.id LIKE ? OR e.name LIKE ? OR e.area LIKE ? " +
                            "OR e.team_leader LIKE ? OR q.line_number LIKE ? " +
                            "OR q.station LIKE ? OR q.qualification_level LIKE ?";
                    break;
            }
        }

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sqlBase + sqlWhere)) {

            if (!keyword.isEmpty()) {
                String pattern = "%" + keyword + "%";
                if ("All Fields".equals(searchType) || searchType == null) {
                    for (int i = 1; i <= 7; i++)
                        pstmt.setString(i, pattern);
                } else {
                    pstmt.setString(1, pattern);
                }
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new SkillMatrixApp.EmployeeRecord(
                            rs.getString("id"), rs.getString("name"),
                            rs.getString("area"), rs.getString("employment_date"),
                            rs.getString("team_leader")));
                }
            }
        }
        return results;
    }

    public List<SkillMatrixApp.SkillRecord> fetchEmployeeSkills(String empId) throws SQLException {
        List<SkillMatrixApp.SkillRecord> skills = new ArrayList<>();
        String sql = "SELECT line_number, station, qualification_level, cert_path FROM Qualifications WHERE employee_id = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, empId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    skills.add(new SkillMatrixApp.SkillRecord(
                            rs.getString("line_number"),
                            rs.getString("station"),
                            rs.getString("qualification_level"),
                            rs.getString("cert_path")));
                }
            }
        }
        return skills;
    }

    public void addEmployee(String id, String name, String date, String area, String leader) throws SQLException {
        String sql = "INSERT INTO Employees (id, name, employment_date, area, team_leader) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, name);
            pstmt.setString(3, date);
            pstmt.setString(4, area);
            pstmt.setString(5, leader);
            pstmt.executeUpdate();
        }
    }

    public void addSkill(String empId, String line, String station, String level, String certPath) throws SQLException {
        String sql = "INSERT INTO Qualifications (employee_id, line_number, station, qualification_level, cert_path) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, empId);
            pstmt.setString(2, line);
            pstmt.setString(3, station);
            pstmt.setString(4, level);
            pstmt.setString(5, certPath);
            pstmt.executeUpdate();
        }
    }

    public boolean deleteEmployeeTransaction(String empId) throws SQLException {
        String deleteQuals = "DELETE FROM Qualifications WHERE employee_id = ?";
        String deleteEmp = "DELETE FROM Employees WHERE id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pQual = conn.prepareStatement(deleteQuals);
                    PreparedStatement pEmp = conn.prepareStatement(deleteEmp)) {

                pQual.setString(1, empId);
                pQual.executeUpdate();

                pEmp.setString(1, empId);
                int affected = pEmp.executeUpdate();

                conn.commit();
                return affected > 0;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // Helper Record Session DTO
    public static class UserSession {
        private final String username;
        private final String role;

        public UserSession(String username, String role) {
            this.username = username;
            this.role = role;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }
    }
}