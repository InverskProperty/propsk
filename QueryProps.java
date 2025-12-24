import java.sql.*;

public class QueryProps {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            // Check properties with customer_id = 68
            String query = """
                SELECT p.id, p.property_name, p.customer_id
                FROM properties p
                WHERE p.customer_id = '68'
                ORDER BY p.id
                """;
            
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ResultSet rs = ps.executeQuery();
                System.out.println("PROPERTIES WITH customer_id = '68':");
                System.out.println("====================================");
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("ID " + rs.getLong("id") + ": " + rs.getString("property_name"));
                }
                System.out.println("Total: " + count);
            }

            System.out.println("\n");

            // Check property 69's lease and who the invoice customer is
            String query2 = """
                SELECT i.id, i.lease_reference, i.customer_id, c.name as customer_name,
                       i.property_id, p.property_name
                FROM invoices i
                LEFT JOIN customers c ON i.customer_id = c.customer_id
                LEFT JOIN properties p ON i.property_id = p.id
                WHERE i.lease_reference = 'BODEN-BLOCK-2025'
                """;
            
            try (PreparedStatement ps = conn.prepareStatement(query2)) {
                ResultSet rs = ps.executeQuery();
                System.out.println("BODEN-BLOCK-2025 LEASE DETAILS:");
                System.out.println("================================");
                if (rs.next()) {
                    System.out.println("Invoice ID: " + rs.getLong("id"));
                    System.out.println("Lease Reference: " + rs.getString("lease_reference"));
                    System.out.println("Customer ID: " + rs.getString("customer_id"));
                    System.out.println("Customer Name: " + rs.getString("customer_name"));
                    System.out.println("Property ID: " + rs.getLong("property_id"));
                    System.out.println("Property Name: " + rs.getString("property_name"));
                }
            }

            System.out.println("\n");

            // Check what customer runs statements that include the block
            String query3 = """
                SELECT DISTINCT c.customer_id, c.name
                FROM customers c
                JOIN invoices i ON i.customer_id = c.customer_id
                WHERE i.property_id = 69
                """;
            
            try (PreparedStatement ps = conn.prepareStatement(query3)) {
                ResultSet rs = ps.executeQuery();
                System.out.println("CUSTOMERS WITH LEASES ON PROPERTY 69:");
                System.out.println("=====================================");
                while (rs.next()) {
                    System.out.println("Customer ID: " + rs.getString("customer_id") + " - " + rs.getString("name"));
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
