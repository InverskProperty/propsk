import java.sql.*;

public class check_unified3 {
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
            
            // Check by source_system
            String query2 = "SELECT source_system, COUNT(*) as count FROM unified_transactions GROUP BY source_system";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query2)) {
                System.out.println("\nBy source system:");
                while (rs.next()) {
                    System.out.println("  " + rs.getString("source_system") + ": " + rs.getInt("count"));
                }
            }
            
            // Check last rebuild time
            String query3 = "SELECT MAX(rebuilt_at) as last_rebuild FROM unified_transactions";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query3)) {
                if (rs.next()) {
                    Timestamp lastRebuild = rs.getTimestamp("last_rebuild");
                    System.out.println("\nLast rebuild: " + lastRebuild);
                    if (lastRebuild == null) {
                        System.out.println("  [!] NO REBUILD HAS EVER RUN!");
                    }
                }
            }
            
            // Check PayProp data sources
            String query4 = "SELECT payprop_data_source, COUNT(*) as count FROM unified_transactions WHERE source_system = 'PAYPROP' GROUP BY payprop_data_source";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query4)) {
                System.out.println("\nPayProp data sources:");
                boolean hasData = false;
                while (rs.next()) {
                    hasData = true;
                    System.out.println("  " + rs.getString("payprop_data_source") + ": " + rs.getInt("count"));
                }
                if (!hasData) {
                    System.out.println("  [!] NO PAYPROP DATA IN UNIFIED TABLE!");
                }
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
