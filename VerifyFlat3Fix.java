import java.sql.*;

public class VerifyFlat3Fix {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String username = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {

            // Verify Flat 3 April payment
            System.out.println("=== Verifying Flat 3 April 2025 Payment ===");
            String query = "SELECT ut.id, ut.transaction_date, ut.amount, ut.description, " +
                "ut.category, ut.flow_direction, ut.transaction_type, i.lease_reference " +
                "FROM unified_transactions ut " +
                "LEFT JOIN invoices i ON ut.invoice_id = i.id " +
                "WHERE i.lease_reference = 'LEASE-BH-F3-2025' " +
                "AND ut.transaction_date >= '2025-04-01' " +
                "AND ut.transaction_date <= '2025-04-30' " +
                "AND ut.flow_direction = 'INCOMING' " +
                "ORDER BY ut.transaction_date";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.println("\nFOUND - April Payment:");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Category: " + rs.getString("category"));
                    System.out.println("  Flow Direction: " + rs.getString("flow_direction"));
                    System.out.println("  Transaction Type: " + rs.getString("transaction_type"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println("  Lease: " + rs.getString("lease_reference"));
                }

                if (found) {
                    System.out.println("\nSUCCESS: Flat 3 April payment is now correctly categorized!");
                    System.out.println("This payment will now appear in the RENT_RECEIVED sheet.");
                } else {
                    System.out.println("\nERROR: Flat 3 April payment still not found with INCOMING flow!");
                }
            }

            // Show all Flat 3 rent received for the full lease period
            System.out.println("\n=== All Flat 3 Rent Received (Full History) ===");
            String allQuery = "SELECT ut.id, ut.transaction_date, ut.amount, ut.flow_direction " +
                "FROM unified_transactions ut " +
                "LEFT JOIN invoices i ON ut.invoice_id = i.id " +
                "WHERE i.lease_reference = 'LEASE-BH-F3-2025' " +
                "AND ut.flow_direction = 'INCOMING' " +
                "ORDER BY ut.transaction_date";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(allQuery)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\nPayment " + count + ":");
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Flow: " + rs.getString("flow_direction"));
                }
                System.out.println("\nTotal INCOMING payments: " + count);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
