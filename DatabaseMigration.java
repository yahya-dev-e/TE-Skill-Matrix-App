import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseMigration {
    private static final String DB_URL = "jdbc:sqlite:master_skills_data_clean2.db";

    public static void main(String[] args) {
        List<Record> newRecords = new ArrayList<>();

        System.out.println("Step 1: Reading and parsing existing database...");

        try (Connection conn = DriverManager.getConnection(DB_URL);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt
                        .executeQuery("SELECT employee_id, line_of_work, qualification_level FROM Qualifications")) {

            while (rs.next()) {
                String empId = rs.getString("employee_id");
                String lineOfWork = rs.getString("line_of_work");
                String level = rs.getString("qualification_level");

                if (lineOfWork == null)
                    continue;

                String station = lineOfWork;
                String linesPart = "N/A";

                if (lineOfWork.contains(" - ")) {
                    String[] parts = lineOfWork.split(" - ");

                    if (parts.length == 2) {
                        boolean leftHasFamily = parts[0].toLowerCase().contains("production family");
                        boolean rightHasDigits = parts[1].matches(".*\\d.*");
                        boolean leftHasDigits = parts[0].matches(".*\\d.*");

                        if (leftHasFamily || (rightHasDigits && !leftHasDigits)) {
                            station = parts[0].trim();
                            linesPart = parts[1].trim();
                        } else {
                            linesPart = parts[0].trim();
                            station = parts[1].trim();
                        }
                    } else if (parts.length >= 3) {
                        linesPart = parts[1].trim();
                        StringBuilder stationBuilder = new StringBuilder();
                        stationBuilder.append(parts[0].trim());
                        for (int i = 2; i < parts.length; i++) {
                            stationBuilder.append(" - ").append(parts[i].trim());
                        }
                        station = stationBuilder.toString();
                    }
                }

                if (linesPart.equals("N/A")) {
                    newRecords.add(new Record(empId, "N/A", station, level));
                } else {
                    // Protect specific multi-word line numbers and A/B labels from being split
                    linesPart = linesPart.replace("tool 701", "tool_701")
                            .replace("APIN HSG", "APIN_HSG")
                            .replace("A/B", "A_B");

                    // Split multiple line numbers by spaces or slashes
                    String[] lines = linesPart.split("[\\s/]+");
                    for (String line : lines) {
                        // Restore the protected strings
                        String cleanLine = line.replace("tool_701", "tool 701")
                                .replace("APIN_HSG", "APIN HSG")
                                .replace("A_B", "A/B").trim();

                        // Ignore stray "o" typos from the raw database
                        if (cleanLine.equalsIgnoreCase("o"))
                            continue;

                        if (!cleanLine.isEmpty()) {
                            newRecords.add(new Record(empId, cleanLine, station, level));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error reading database: " + e.getMessage());
            System.err.println("⚠️ ERROR: You must restore your ORIGINAL database file before running this!");
            return;
        }

        System.out.println("Step 2: Rebuilding the Qualifications table...");

        try (Connection conn = DriverManager.getConnection(DB_URL);
                Statement stmt = conn.createStatement()) {

            stmt.execute("DROP TABLE IF EXISTS Qualifications");
            stmt.execute("CREATE TABLE Qualifications (" +
                    "employee_id TEXT, " +
                    "line_number TEXT, " +
                    "station TEXT, " +
                    "qualification_level TEXT)");

            String insertSql = "INSERT INTO Qualifications (employee_id, line_number, station, qualification_level) VALUES (?, ?, ?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                conn.setAutoCommit(false);
                for (Record r : newRecords) {
                    pstmt.setString(1, r.empId);
                    pstmt.setString(2, r.lineNumber);
                    pstmt.setString(3, r.station);
                    pstmt.setString(4, r.level);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit();
            }

            System.out.println(
                    "✅ Database successfully migrated! " + newRecords.size() + " new individual records created.");

        } catch (SQLException e) {
            System.err.println("Error writing to database: " + e.getMessage());
        }
    }

    static class Record {
        String empId, lineNumber, station, level;

        Record(String empId, String lineNumber, String station, String level) {
            this.empId = empId;
            this.lineNumber = lineNumber;
            this.station = station;
            this.level = level;
        }
    }
}