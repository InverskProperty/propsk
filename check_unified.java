import java.sql.*;

public class check_unified {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== UNIFIED_TRANSACTIONS TABLE STATUS ===\n");
            
            // Check total count
            String query1 = "SELECT COUNT(*) as total FROM unified_transactions";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query1)) {
                if (rs.next()) {
                    System.out.println("Total records: " + rs.getInt("total"));
                }
            }
            
            // Check most recent records
            String query2 = "SELECT MAX(created_at) as latest_created, MAX(updated_at) as latest_updated FROM unified_transactions";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query2)) {
                if (rs.next()) {
                    System.out.println("Latest created_at: " + rs.getTimestamp("latest_created"));
                    System.out.println("Latest updated_at: " + rs.getTimestamp("latest_updated"));
                }
            }
            
            // Check records created in last hour
            String query3 = "SELECT COUNT(*) as recent_count FROM unified_transactions WHERE created_at > DATE_SUB(NOW(), INTERVAL 1 HOUR)";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query3)) {
                if (rs.next()) {
                    System.out.println("Records created in last hour: " + rs.getInt("recent_count"));
                }
            }
            
            // Check data sources
            String query4 = "SELECT data_source, COUNT(*) as count FROM unified_transactions GROUP BY data_source ORDER BY count DESC";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query4)) {
                System.out.println("\nBy data source:");
                while (rs.next()) {
                    System.out.println("  " + rs.getString("data_source") + ": " + rs.getInt("count"));
                }
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
