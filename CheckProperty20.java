import java.sql.*;

public class CheckProperty20 {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        System.out.println("=== Checking Property 20 (July Expenses) ===\n");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Find leases for property 20
            System.out.println("Leases for property 20:");
            String sql1 = """
                SELECT i.id, i.lease_reference, i.start_date, i.end_date, i.is_active,
                       p.property_name, c.name as tenant_name
                FROM invoices i
                LEFT JOIN properties p ON i.property_id = p.id
                LEFT JOIN customers c ON i.customer_id = c.customer_id
                WHERE i.property_id = 20
                ORDER BY i.start_date DESC
            """;
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql1)) {
                while (rs.next()) {
                    System.out.printf("\nLease ID: %d | %s | %s to %s | Active: %s\n",
                        rs.getInt("id"),
                        rs.getString("lease_reference"),
                        rs.getDate("start_date"),
                        rs.getObject("end_date"),
                        rs.getBoolean("is_active"));
                    System.out.printf("Property: %s | Tenant: %s\n",
                        rs.getString("property_name"),
                        rs.getString("tenant_name"));
                }
            }
            
            // Show July expenses for property 20
            System.out.println("\n=== July 2025 Expenses for Property 20 ===");
            String sql2 = """
                SELECT transaction_date, amount, category, description, invoice_id, property_id
                FROM historical_transactions
                WHERE property_id = 20
                  AND transaction_date BETWEEN '2025-07-01' AND '2025-07-31'
                  AND category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
                ORDER BY transaction_date
            """;
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql2)) {
                while (rs.next()) {
                    System.out.printf("%s | £%.2f | %s | invoice_id=%s | %s\n",
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("category"),
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
