import java.sql.*;

public class CheckRentDueDate {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Check if payment_day is set for all leases
            System.out.println("=== Payment Day Settings in Invoices ===");
            String sql1 = "SELECT id, lease_reference, payment_day, frequency FROM invoices WHERE invoice_type = 'lease' ORDER BY id LIMIT 20";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql1)) {
                System.out.println("ID | Lease Reference | Payment Day | Frequency");
                System.out.println("---|-----------------|-------------|----------");
                int withPaymentDay = 0;
                int withoutPaymentDay = 0;
                while (rs.next()) {
                    int paymentDay = rs.getInt("payment_day");
                    if (rs.wasNull()) {
                        withoutPaymentDay++;
                    } else {
                        withPaymentDay++;
                    }
                    System.out.printf("%d | %s | %s | %s\n",
                        rs.getInt("id"),
                        rs.getString("lease_reference"),
                        rs.wasNull() ? "NULL" : String.valueOf(paymentDay),
                        rs.getString("frequency"));
                }
                System.out.println("\nWith payment_day: " + withPaymentDay);
                System.out.println("Without payment_day: " + withoutPaymentDay);
            }
            
            // Check total count
            System.out.println("\n=== Summary ===");
            String sql2 = "SELECT COUNT(*) as total, SUM(CASE WHEN payment_day IS NULL THEN 1 ELSE 0 END) as null_count, SUM(CASE WHEN payment_day IS NOT NULL THEN 1 ELSE 0 END) as has_day FROM invoices WHERE invoice_type = 'lease'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql2)) {
                if (rs.next()) {
                    System.out.printf("Total leases: %d\n", rs.getInt("total"));
                    System.out.printf("With payment_day: %d\n", rs.getInt("has_day"));
                    System.out.printf("Without payment_day (NULL): %d\n", rs.getInt("null_count"));
                }
            }
            
            // Check distribution of payment days
            System.out.println("\n=== Payment Day Distribution ===");
            String sql3 = "SELECT payment_day, COUNT(*) as count FROM invoices WHERE invoice_type = 'lease' AND payment_day IS NOT NULL GROUP BY payment_day ORDER BY payment_day";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql3)) {
                while (rs.next()) {
                    System.out.printf("Day %d: %d leases\n",
                        rs.getInt("payment_day"),
                        rs.getInt("count"));
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
