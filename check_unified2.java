import java.sql.*;

public class check_unified2 {
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
            
            // Check most recent transaction dates
            String query2 = "SELECT MAX(transaction_date) as latest_tx_date FROM unified_transactions";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query2)) {
                if (rs.next()) {
                    System.out.println("Latest transaction_date: " + rs.getDate("latest_tx_date"));
                }
            }
            
            // Check records from today
            String query3 = "SELECT COUNT(*) as today_count FROM unified_transactions WHERE DATE(transaction_date) = CURDATE()";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query3)) {
                if (rs.next()) {
                    System.out.println("Records with today's transaction_date: " + rs.getInt("today_count"));
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
            
            // Check recent imports - check if there are any PayProp records
            String query5 = "SELECT data_source, COUNT(*) as count FROM unified_transactions WHERE data_source LIKE 'PAYPROP%' GROUP BY data_source";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query5)) {
                System.out.println("\nPayProp data sources:");
                int total = 0;
                while (rs.next()) {
                    int count = rs.getInt("count");
                    System.out.println("  " + rs.getString("data_source") + ": " + count);
                    total += count;
                }
                if (total == 0) {
                    System.out.println("  [!] NO PAYPROP DATA FOUND!");
                }
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
