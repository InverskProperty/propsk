import java.sql.*;

public class CheckExpensesInJuly {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        System.out.println("=== Checking Expenses in July 2025 ===\n");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Check 1: Count all expense records
            String sql1 = "SELECT COUNT(*) as count, SUM(amount) as total FROM unified_transactions WHERE transaction_type = 'expense'";
            try (PreparedStatement stmt = conn.prepareStatement(sql1);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    System.out.println("Total expense records: " + rs.getInt("count"));
                    System.out.println("Total expense amount: £" + rs.getBigDecimal("total"));
                }
            }
            
            System.out.println("\n=== Expenses by Category ===");
            String sql2 = "SELECT category, COUNT(*) as count, SUM(amount) as total FROM unified_transactions WHERE transaction_type = 'expense' GROUP BY category ORDER BY count DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql2);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    System.out.printf("%s: %d records (£%.2f)\n", 
                        rs.getString("category"), 
                        rs.getInt("count"),
                        rs.getBigDecimal("total"));
                }
            }
            
            System.out.println("\n=== Expenses in July 2025 ===");
            String sql3 = "SELECT transaction_date, amount, category, description, invoice_id FROM unified_transactions WHERE transaction_type = 'expense' AND transaction_date BETWEEN '2025-07-01' AND '2025-07-31' ORDER BY transaction_date";
            try (PreparedStatement stmt = conn.prepareStatement(sql3);
                 ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("%s | £%.2f | %s | invoice_id=%s | %s\n",
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("category"),
                        rs.getObject("invoice_id"),
                        rs.getString("description"));
                }
                if (count == 0) {
                    System.out.println("No expenses found in July 2025");
                }
            }
            
            System.out.println("\n=== Check Transaction Types ===");
            String sql4 = "SELECT transaction_type, COUNT(*) as count FROM unified_transactions GROUP BY transaction_type ORDER BY count DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql4);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    System.out.printf("%s: %d records\n", 
                        rs.getString("transaction_type"), 
                        rs.getInt("count"));
                }
            }
            
            System.out.println("\n=== Historical Transactions with Expense Categories (Not Yet Imported) ===");
            String sql5 = "SELECT transaction_date, amount, category, description FROM historical_transactions WHERE category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee') ORDER BY transaction_date LIMIT 10";
            try (PreparedStatement stmt = conn.prepareStatement(sql5);
                 ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("%s | £%.2f | %s | %s\n",
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("category"),
                        rs.getString("description"));
                }
                System.out.println("Total historical expense records waiting to be imported: " + count + "+ (showing first 10)");
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
