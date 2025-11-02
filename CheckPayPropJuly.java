import java.sql.*;

public class CheckPayPropJuly {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Check transaction types in unified_transactions
            System.out.println("=== Unified Transactions Transaction Types ===");
            String sql1 = "SELECT transaction_type, flow_direction, COUNT(*) as count FROM unified_transactions GROUP BY transaction_type, flow_direction ORDER BY count DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql1)) {
                while (rs.next()) {
                    System.out.printf("%s (%s): %d records\n",
                        rs.getString("transaction_type"),
                        rs.getString("flow_direction"),
                        rs.getInt("count"));
                }
            }
            
            // Check incoming_payment records
            System.out.println("\n=== Incoming Payment Records ===");
            String sql2 = "SELECT COUNT(*) as count, SUM(amount) as total FROM unified_transactions WHERE transaction_type = 'incoming_payment'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql2)) {
                if (rs.next()) {
                    System.out.println("Total incoming_payment: " + rs.getInt("count") + " records (£" + rs.getBigDecimal("total") + ")");
                }
            }
            
            // Check July incoming_payment
            System.out.println("\n=== July 2025 Incoming Payments (sample) ===");
            String sql3 = "SELECT transaction_date, amount, description, property_name, invoice_id FROM unified_transactions WHERE transaction_type = 'incoming_payment' AND transaction_date BETWEEN '2025-07-01' AND '2025-07-31' LIMIT 10";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql3)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("%s | £%.2f | invoice_id=%s | %s | %s\n",
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getObject("invoice_id"),
                        rs.getString("property_name"),
                        rs.getString("description"));
                }
                if (count == 0) {
                    System.out.println("No incoming_payment in July 2025");
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
