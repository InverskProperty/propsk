import java.sql.*;

public class CheckExpenseAmounts {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Check source data (historical_transactions)
            System.out.println("=== Source Data (historical_transactions) ===");
            String sql1 = "SELECT amount, category, description FROM historical_transactions WHERE category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee') LIMIT 5";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql1)) {
                while (rs.next()) {
                    System.out.printf("  £%.2f | %s | %s\n",
                        rs.getBigDecimal("amount"),
                        rs.getString("category"),
                        rs.getString("description"));
                }
            }
            
            // Check unified data
            System.out.println("\n=== Current Unified Data (expenses) ===");
            String sql2 = "SELECT amount, category, description FROM unified_transactions WHERE transaction_type = 'expense' LIMIT 5";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql2)) {
                while (rs.next()) {
                    System.out.printf("  £%.2f | %s | %s\n",
                        rs.getBigDecimal("amount"),
                        rs.getString("category"),
                        rs.getString("description"));
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
