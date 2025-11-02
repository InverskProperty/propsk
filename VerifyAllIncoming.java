import java.sql.*;

public class VerifyAllIncoming {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            System.out.println("=== All July 2025 INCOMING Transactions ===\n");
            
            String sql = "SELECT invoice_id, transaction_type, transaction_date, amount, description, flow_direction FROM unified_transactions WHERE flow_direction = 'INCOMING' AND transaction_date BETWEEN '2025-07-01' AND '2025-07-31' ORDER BY transaction_date, invoice_id LIMIT 25";
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                int count = 0;
                double total = 0;
                while (rs.next()) {
                    count++;
                    double amt = rs.getBigDecimal("amount").doubleValue();
                    total += amt;
                    System.out.printf("%d. %s | lease=%d | %s | £%.2f | %s\n",
                        count,
                        rs.getDate("transaction_date"),
                        rs.getInt("invoice_id"),
                        rs.getString("transaction_type"),
                        amt,
                        rs.getString("description").substring(0, Math.min(50, rs.getString("description").length())));
                }
                System.out.println("\nTotal shown: £" + String.format("%.2f", total));
                System.out.println("Records shown: " + count + " (of 25 limit)");
            }
            
            // Get total count
            String sql2 = "SELECT transaction_type, COUNT(*) as count, SUM(amount) as total FROM unified_transactions WHERE flow_direction = 'INCOMING' AND transaction_date BETWEEN '2025-07-01' AND '2025-07-31' GROUP BY transaction_type";
            System.out.println("\n=== July 2025 INCOMING Summary ===");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql2)) {
                double grandTotal = 0;
                int grandCount = 0;
                while (rs.next()) {
                    int cnt = rs.getInt("count");
                    double tot = rs.getBigDecimal("total").doubleValue();
                    grandCount += cnt;
                    grandTotal += tot;
                    System.out.printf("  %s: %d payments (£%.2f)\n",
                        rs.getString("transaction_type"),
                        cnt,
                        tot);
                }
                System.out.println("\nGrand Total: " + grandCount + " payments (£" + String.format("%.2f", grandTotal) + ")");
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
