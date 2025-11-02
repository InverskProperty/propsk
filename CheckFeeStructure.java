import java.sql.*;

public class CheckFeeStructure {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Check all transaction types that might be fees
            System.out.println("=== Fee-Related Transaction Types in Unified ===");
            String sql1 = "SELECT transaction_type, COUNT(*) as count, SUM(amount) as total FROM unified_transactions WHERE transaction_type LIKE '%fee%' OR transaction_type LIKE '%commission%' OR transaction_type LIKE '%management%' OR transaction_type LIKE '%service%' GROUP BY transaction_type ORDER BY count DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql1)) {
                while (rs.next()) {
                    System.out.printf("  %s: %d records (£%.2f)\n",
                        rs.getString("transaction_type"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total"));
                }
            }
            
            // Check categories in historical_transactions
            System.out.println("\n=== Fee Categories in Historical Transactions ===");
            String sql2 = "SELECT category, COUNT(*) as count, SUM(amount) as total FROM historical_transactions WHERE category LIKE '%fee%' OR category LIKE '%commission%' OR category LIKE '%management%' OR category LIKE '%service%' GROUP BY category ORDER BY count DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql2)) {
                while (rs.next()) {
                    System.out.printf("  %s: %d records (£%.2f)\n",
                        rs.getString("category"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total"));
                }
            }
            
            // Check categories in financial_transactions (PayProp)
            System.out.println("\n=== Fee Categories in PayProp Financial Transactions ===");
            String sql3 = "SELECT category_name, transaction_type, COUNT(*) as count, SUM(amount) as total FROM financial_transactions WHERE category_name LIKE '%fee%' OR category_name LIKE '%commission%' OR category_name LIKE '%management%' OR category_name LIKE '%service%' OR transaction_type LIKE '%fee%' OR transaction_type LIKE '%commission%' OR transaction_type LIKE '%management%' GROUP BY category_name, transaction_type ORDER BY count DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql3)) {
                while (rs.next()) {
                    System.out.printf("  %s (%s): %d records (£%.2f)\n",
                        rs.getString("category_name"),
                        rs.getString("transaction_type"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total"));
                }
            }
            
            // Check sample July data
            System.out.println("\n=== Sample July 2025 Fee Records ===");
            String sql4 = "SELECT transaction_date, amount, transaction_type, category, description FROM unified_transactions WHERE (transaction_type LIKE '%fee%' OR transaction_type LIKE '%commission%' OR transaction_type LIKE '%management%') AND transaction_date BETWEEN '2025-07-01' AND '2025-07-31' LIMIT 5";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql4)) {
                while (rs.next()) {
                    System.out.printf("  %s | £%.2f | %s | %s | %s\n",
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("transaction_type"),
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
