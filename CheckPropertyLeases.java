import java.sql.*;

public class CheckPropertyLeases {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        System.out.println("=== Checking Leases for Properties with Expenses ===\n");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Get properties with expenses
            String sql1 = """
                SELECT DISTINCT property_id
                FROM historical_transactions
                WHERE category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
                  AND property_id IS NOT NULL
                ORDER BY property_id
            """;
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql1)) {
                
                System.out.println("Properties with expenses:");
                while (rs.next()) {
                    int propertyId = rs.getInt("property_id");
                    System.out.println("\n--- Property ID: " + propertyId + " ---");
                    
                    // Find active leases for this property
                    String sql2 = """
                        SELECT i.id, i.lease_reference, i.start_date, i.end_date, i.is_active,
                               p.property_name, c.name as tenant_name
                        FROM invoices i
                        LEFT JOIN properties p ON i.property_id = p.id
                        LEFT JOIN customers c ON i.customer_id = c.customer_id
                        WHERE i.property_id = ?
                        ORDER BY i.start_date DESC
                    """;
                    
                    try (PreparedStatement stmt2 = conn.prepareStatement(sql2)) {
                        stmt2.setInt(1, propertyId);
                        try (ResultSet rs2 = stmt2.executeQuery()) {
                            boolean found = false;
                            while (rs2.nextObject("id"),
                                rs2.getString("lease_reference"),
                                rs2.getDate("start_date"),
                                rs2.getDate("end_date"),
                                rs2.getBoolean("is_active"),
                                rs2.getString("property_name"),
                                rs2.getString("tenant_name"));
                                found = true;
                            }
                            if (!found) {
                                System.out.println("  No leases found for property " + propertyId);
                            }
                        }
                    }
                    
                    // Show expenses for this property
                    String sql3 = """
                        SELECT transaction_date, amount, category, description
                        FROM historical_transactions
                        WHERE property_id = ?
                          AND category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
                        ORDER BY transaction_date
                    """;
                    
                    try (PreparedStatement stmt3 = conn.prepareStatement(sql3)) {
                        stmt3.setInt(1, propertyId);
                        try (ResultSet rs3 = stmt3.executeQuery()) {
                            System.out.println("\n  Expenses:");
                            while (rs3.next()) {
                                System.out.printf("    %s | £%.2f | %s | %s\n",
                                    rs3.getDate("transaction_date"),
                                    rs3.getBigDecimal("amount"),
                                    rs3.getString("category"),
                                    rs3.getString("description"));
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
