import java.sql.*;

public class QueryFlat10Lease {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== PROPERTY 7 (FLAT 10) DETAILS ===\n");

            // Get property details
            String propSql = "SELECT id, property_name, payprop_id FROM properties WHERE id = 7";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(propSql);
            
            if (rs.next()) {
                System.out.println("Property ID: " + rs.getInt("id"));
                System.out.println("Property Name: " + rs.getString("property_name"));
                System.out.println("PayProp ID: " + rs.getString("payprop_id"));
            }
            rs.close();

            System.out.println("\n=== LEASES FOR PROPERTY 7 ===\n");

            // Get lease details
            String leaseSql = "SELECT id, lease_reference, start_date, end_date, amount, customer_id " +
                            "FROM invoices WHERE property_id = 7 AND lease_reference IS NOT NULL " +
                            "ORDER BY start_date DESC";
            ResultSet leaseRs = stmt.executeQuery(leaseSql);
            
            int count = 0;
            while (leaseRs.next()) {
                count++;
                System.out.println("Lease " + count + ":");
                System.out.println("  ID: " + leaseRs.getInt("id"));
                System.out.println("  Reference: " + leaseRs.getString("lease_reference"));
                System.out.println("  Start Date: " + leaseRs.getDate("start_date"));
                System.out.println("  End Date: " + leaseRs.getDate("end_date"));
                System.out.println("  Amount: £" + leaseRs.getBigDecimal("amount"));
                System.out.println("  Customer ID: " + leaseRs.getInt("customer_id"));
                System.out.println();
            }
            leaseRs.close();
            
            System.out.println("Total leases found: " + count);

            stmt.close();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
