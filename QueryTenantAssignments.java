import java.sql.*;

public class QueryTenantAssignments {
    public static void main(String[] args) {
        String url = "jdbc:mysql://autorack.proxy.rlwy.net:52566/railway";
        String user = "root";
        String password = "jJqMIDKDELJVeZMSmGcOxnJzrrHfWnRD";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, user, password);

            // Query for tenant assignments with details
            String sql = "SELECT " +
                "cpa.id, " +
                "cpa.customer_id, " +
                "c.name as customer_name, " +
                "cpa.property_id, " +
                "p.name as property_name, " +
                "cpa.assignment_type, " +
                "cpa.start_date, " +
                "cpa.end_date, " +
                "cpa.sync_status, " +
                "cpa.created_at " +
                "FROM customer_property_assignments cpa " +
                "LEFT JOIN customers c ON cpa.customer_id = c.customer_id " +
                "LEFT JOIN properties p ON cpa.property_id = p.id " +
                "WHERE cpa.assignment_type = 'TENANT' " +
                "ORDER BY cpa.created_at DESC " +
                "LIMIT 20";

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            System.out.println("=== TENANT ASSIGNMENTS (Latest 20) ===\n");
            System.out.printf("%-5s %-8s %-25s %-8s %-30s %-12s %-12s %-15s %-20s%n",
                "ID", "Cust ID", "Customer Name", "Prop ID", "Property Name",
                "Start Date", "End Date", "Sync Status", "Created At");
            System.out.println("=".repeat(160));

            int count = 0;
            while (rs.next()) {
                count++;
                System.out.printf("%-5d %-8d %-25s %-8s %-30s %-12s %-12s %-15s %-20s%n",
                    rs.getInt("id"),
                    rs.getInt("customer_id"),
                    truncate(rs.getString("customer_name"), 25),
                    rs.getString("property_id") != null ? rs.getString("property_id") : "NULL",
                    truncate(rs.getString("property_name"), 30),
                    rs.getString("start_date") != null ? rs.getString("start_date") : "NULL",
                    rs.getString("end_date") != null ? rs.getString("end_date") : "NULL",
                    rs.getString("sync_status") != null ? rs.getString("sync_status") : "NULL",
                    rs.getString("created_at")
                );
            }

            System.out.println("\nTotal tenant assignments: " + count);

            // Now check for Anna Stoliarchuk specifically
            System.out.println("\n=== ANNA STOLIARCHUK STATUS ===\n");

            String annaSql = "SELECT " +
                "c.customer_id, c.name, c.email, c.payprop_entity_id, " +
                "cpa.id as assignment_id, cpa.property_id, p.name as property_name, " +
                "cpa.start_date, cpa.end_date, cpa.created_at as assignment_created, " +
                "i.id as lease_id, i.amount, i.start_date as lease_start, i.end_date as lease_end " +
                "FROM customers c " +
                "LEFT JOIN customer_property_assignments cpa ON c.customer_id = cpa.customer_id " +
                "LEFT JOIN properties p ON cpa.property_id = p.id " +
                "LEFT JOIN invoices i ON i.customer_id = c.customer_id AND i.invoice_type = 'lease' " +
                "WHERE c.name LIKE '%Anna%Stoliarchuk%'";

            ResultSet annaRs = stmt.executeQuery(annaSql);

            if (annaRs.next()) {
                System.out.println("Customer ID: " + annaRs.getInt("customer_id"));
                System.out.println("Name: " + annaRs.getString("name"));
                System.out.println("Email: " + annaRs.getString("email"));
                System.out.println("PayProp Entity ID: " + annaRs.getString("payprop_entity_id"));
                System.out.println("\nAssignment:");
                if (annaRs.getString("assignment_id") != null) {
                    System.out.println("  Assignment ID: " + annaRs.getInt("assignment_id"));
                    System.out.println("  Property ID: " + annaRs.getInt("property_id"));
                    System.out.println("  Property Name: " + annaRs.getString("property_name"));
                    System.out.println("  Start Date: " + annaRs.getString("start_date"));
                    System.out.println("  End Date: " + annaRs.getString("end_date"));
                    System.out.println("  Created: " + annaRs.getString("assignment_created"));
                } else {
                    System.out.println("  [X] NO ASSIGNMENT FOUND");
                }

                System.out.println("\nLease:");
                if (annaRs.getString("lease_id") != null) {
                    System.out.println("  Lease ID: " + annaRs.getInt("lease_id"));
                    System.out.println("  Amount: " + annaRs.getBigDecimal("amount"));
                    System.out.println("  Start Date: " + annaRs.getString("lease_start"));
                    System.out.println("  End Date: " + annaRs.getString("lease_end"));
                } else {
                    System.out.println("  [X] NO LEASE FOUND");
                }
            } else {
                System.out.println("[X] Anna Stoliarchuk NOT FOUND in database");
            }

            rs.close();
            annaRs.close();
            stmt.close();
            conn.close();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String truncate(String str, int length) {
        if (str == null) return "NULL";
        return str.length() > length ? str.substring(0, length - 3) + "..." : str;
    }
}
