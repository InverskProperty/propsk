import java.sql.*;

public class CheckJulyRent {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        System.out.println("=== July 2025 Rent Received Summary ===\n");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Get July rent by lease
            String sql = """
                SELECT ut.invoice_id, i.lease_reference, p.property_name,
                       COUNT(*) as payment_count, SUM(ut.amount) as total_received
                FROM unified_transactions ut
                LEFT JOIN invoices i ON ut.invoice_id = i.id
                LEFT JOIN properties p ON ut.property_id = p.id
                WHERE ut.transaction_type = 'rent_received'
                  AND ut.transaction_date BETWEEN '2025-07-01' AND '2025-07-31'
                GROUP BY ut.invoice_id, i.lease_reference, p.property_name
                ORDER BY ut.invoice_id
                LIMIT 20
            """;
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                System.out.println("Lease ID | Lease Reference | Property | Payments | Total");
                System.out.println("---------|-----------------|----------|----------|--------");
                double grandTotal = 0;
                while (rs.next()) {
                    int leaseId = rs.getInt("invoice_id");
                    String leaseRef = rs.getString("lease_reference");
                    String property = rs.getString("property_name");
                    int count = rs.getInt("payment_count");
                    double total = rs.getBigDecimal("total_received").doubleValue();
                    grandTotal += total;
                    
                    System.out.printf("%-8d | %-15s | %-20s | %d | £%.2f\n",
                        leaseId, leaseRef, 
                        property != null ? (property.length() > 20 ? property.substring(0, 17) + "..." : property) : "null",
                        count, total);
                }
                System.out.println("\nGrand Total: £" + String.format("%.2f", grandTotal));
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
