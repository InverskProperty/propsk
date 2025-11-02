import java.sql.*;

public class CheckHistoricalExpenseInvoices {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        System.out.println("=== Checking Historical Expense Records ===\n");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Check expense records WITH invoice_id
            String sql1 = """
                SELECT COUNT(*) as count, SUM(amount) as total
                FROM historical_transactions
                WHERE category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
                  AND invoice_id IS NOT NULL
            """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql1)) {
                if (rs.next()) {
                    System.out.println("Expense records WITH invoice_id: " + rs.getInt("count"));
                    System.out.println("Total amount: £" + rs.getBigDecimal("total"));
                }
            }
            
            // Check expense records WITHOUT invoice_id
            String sql2 = """
                SELECT COUNT(*) as count, SUM(amount) as total
                FROM historical_transactions
                WHERE category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
                  AND invoice_id IS NULL
            """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql2)) {
                if (rs.next()) {
                    System.out.println("\nExpense records WITHOUT invoice_id: " + rs.getInt("count"));
                    System.out.println("Total amount: £" + rs.getBigDecimal("total"));
                }
            }
            
            // Show sample expense records without invoice_id
            System.out.println("\n=== Sample Expense Records WITHOUT invoice_id ===");
            String sql3 = """
                SELECT transaction_date, amount, category, description, property_id, invoice_id
                FROM historical_transactions
                WHERE category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
                ORDER BY transaction_date
                LIMIT 10
            """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql3)) {
                while (rs.next()) {
                    System.out.printf("%s | £%.2f | %s | property_id=%s | invoice_id=%s | %s\n",
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("category"),
                        rs.getObject("property_id"),
                        rs.getObject("invoice_id"),
                        rs.getString("description"));
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
