import java.sql.*;

public class CheckMigration {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        try (Connection conn = DriverManager.getConnection(url, "root", "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW")) {
            String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'unified_transactions' AND COLUMN_NAME IN ('transaction_type', 'flow_direction')";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                System.out.println("Checking for new columns in unified_transactions:");
                boolean found = false;
                while (rs.next()) {
                    System.out.println("  - " + rs.getString("COLUMN_NAME") + " ✓");
                    found = true;
                }
                if (!found) {
                    System.out.println("  Columns NOT found - migration needs to run");
                    System.out.println("\nRunning migration manually...");
                    
                    String migrate1 = "ALTER TABLE unified_transactions ADD COLUMN transaction_type VARCHAR(50) NULL";
                    String migrate2 = "ALTER TABLE unified_transactions ADD COLUMN flow_direction ENUM('INCOMING', 'OUTGOING') NULL";
                    
                    try (Statement migrateStmt = conn.createStatement()) {
                        migrateStmt.execute(migrate1);
                        System.out.println("  Added transaction_type column ✓");
                        migrateStmt.execute(migrate2);
                        System.out.println("  Added flow_direction column ✓");
                        System.out.println("\nMigration completed successfully!");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
