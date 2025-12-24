import java.sql.*;

public class QueryOwner {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            // Check who owns property 69 (Boden House Block)
            String query = """
                SELECT p.id, p.property_name, p.customer_id, c.name as owner_name
                FROM properties p
                LEFT JOIN customers c ON p.customer_id = c.customer_id
                WHERE p.id = 69
                """;
            
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ResultSet rs = ps.executeQuery();
                System.out.println("PROPERTY 69 (Boden House Block) OWNER:");
                System.out.println("======================================");
                if (rs.next()) {
                    System.out.println("Property ID: " + rs.getLong("id"));
                    System.out.println("Property Name: " + rs.getString("property_name"));
                    System.out.println("Customer ID (Owner): " + rs.getString("customer_id"));
                    System.out.println("Owner Name: " + rs.getString("owner_name"));
                }
            }

            System.out.println("\n");

            // Check customer 68 (Udayan Bhardwaj from OWNER allocations)
            String query2 = "SELECT customer_id, name FROM customers WHERE customer_id = '68'";
            try (PreparedStatement ps = conn.prepareStatement(query2)) {
                ResultSet rs = ps.executeQuery();
                System.out.println("CUSTOMER 68 (from OWNER allocations):");
                System.out.println("=====================================");
                if (rs.next()) {
                    System.out.println("Customer ID: " + rs.getString("customer_id"));
                    System.out.println("Name: " + rs.getString("name"));
                } else {
                    System.out.println("Not found - trying numeric lookup...");
                }
            }

            // Find customer by numeric ID in beneficiary_id
            String query2b = """
                SELECT c.customer_id, c.name 
                FROM customers c 
                WHERE c.customer_id = (
                    SELECT MIN(customer_id) FROM customers WHERE customer_id LIKE '68%'
                )
                """;
            
            // Actually let's just look at who beneficiary_id 68 is in allocations
            String query3 = """
                SELECT DISTINCT beneficiary_id, beneficiary_name 
                FROM unified_allocations 
                WHERE beneficiary_id = 68
                LIMIT 1
                """;
            try (PreparedStatement ps = conn.prepareStatement(query3)) {
                ResultSet rs = ps.executeQuery();
                System.out.println("\nBENEFICIARY 68 IN ALLOCATIONS:");
                System.out.println("==============================");
                if (rs.next()) {
                    System.out.println("Beneficiary ID: " + rs.getLong("beneficiary_id"));
                    System.out.println("Beneficiary Name: " + rs.getString("beneficiary_name"));
                }
            }

            // Check all properties owned by Achal/Udayan
            String query4 = """
                SELECT p.id, p.property_name, p.customer_id, c.name as owner_name
                FROM properties p
                LEFT JOIN customers c ON p.customer_id = c.customer_id
                WHERE c.name LIKE '%Achal%' OR c.name LIKE '%Udayan%'
                ORDER BY p.id
                """;
            try (PreparedStatement ps = conn.prepareStatement(query4)) {
                ResultSet rs = ps.executeQuery();
                System.out.println("\nPROPERTIES OWNED BY ACHAL/UDAYAN:");
                System.out.println("==================================");
                while (rs.next()) {
                    System.out.println("ID " + rs.getLong("id") + ": " + rs.getString("property_name") + 
                                     " (Owner: " + rs.getString("owner_name") + ")");
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
