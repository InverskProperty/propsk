import java.sql.*;

public class RemoveDuplicateInvoices {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String username = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {

            // First, check how many ICDN_ACTUAL transactions exist
            System.out.println("=== Checking for ICDN_ACTUAL Transactions in unified_transactions ===");
            String checkQuery = "SELECT COUNT(*) as count, " +
                "SUM(CASE WHEN flow_direction = 'INCOMING' THEN 1 ELSE 0 END) as incoming_count, " +
                "SUM(amount) as total_amount " +
                "FROM unified_transactions " +
                "WHERE payprop_data_source = 'ICDN_ACTUAL'";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(checkQuery)) {
                if (rs.next()) {
                    System.out.println("\nFound ICDN_ACTUAL transactions:");
                    System.out.println("  Total Count: " + rs.getInt("count"));
                    System.out.println("  INCOMING Count: " + rs.getInt("incoming_count"));
                    System.out.println("  Total Amount: £" + rs.getBigDecimal("total_amount"));
                }
            }

            // Show which properties are affected
            System.out.println("\n=== Properties Affected by ICDN_ACTUAL Duplicates ===");
            String propertiesQuery = "SELECT i.lease_reference, COUNT(*) as count, " +
                "SUM(ut.amount) as total_amount, " +
                "MIN(ut.transaction_date) as earliest, MAX(ut.transaction_date) as latest " +
                "FROM unified_transactions ut " +
                "LEFT JOIN invoices i ON ut.invoice_id = i.id " +
                "WHERE ut.payprop_data_source = 'ICDN_ACTUAL' " +
                "GROUP BY i.lease_reference " +
                "ORDER BY count DESC";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(propertiesQuery)) {
                while (rs.next()) {
                    System.out.println("\nLease: " + rs.getString("lease_reference"));
                    System.out.println("  Duplicate Count: " + rs.getInt("count"));
                    System.out.println("  Duplicate Amount: £" + rs.getBigDecimal("total_amount"));
                    System.out.println("  Date Range: " + rs.getDate("earliest") + " to " + rs.getDate("latest"));
                }
            }

            // Show specific Flat 5 duplicates before deletion
            System.out.println("\n=== Flat 5 ICDN_ACTUAL Transactions (TO BE DELETED) ===");
            String flat5Query = "SELECT ut.id, ut.transaction_date, ut.amount, ut.description " +
                "FROM unified_transactions ut " +
                "LEFT JOIN invoices i ON ut.invoice_id = i.id " +
                "WHERE i.lease_reference = 'LEASE-BH-F5-2025' " +
                "AND ut.payprop_data_source = 'ICDN_ACTUAL' " +
                "ORDER BY ut.transaction_date";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(flat5Query)) {
                while (rs.next()) {
                    System.out.println("\nTransaction ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Description: " + rs.getString("description"));
                }
            }

            // NOW DELETE THE DUPLICATES
            System.out.println("\n=== DELETING ALL ICDN_ACTUAL TRANSACTIONS ===");
            String deleteQuery = "DELETE FROM unified_transactions WHERE payprop_data_source = 'ICDN_ACTUAL'";

            try (Statement stmt = conn.createStatement()) {
                int deletedCount = stmt.executeUpdate(deleteQuery);
                System.out.println("\nSUCCESS: Deleted " + deletedCount + " ICDN_ACTUAL duplicate transactions!");
            }

            // Verify deletion
            System.out.println("\n=== Verifying Deletion ===");
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(checkQuery)) {
                if (rs.next()) {
                    System.out.println("Remaining ICDN_ACTUAL transactions: " + rs.getInt("count"));
                    if (rs.getInt("count") == 0) {
                        System.out.println("SUCCESS: All duplicates removed!");
                    }
                }
            }

            // Verify Flat 5 now has correct payments
            System.out.println("\n=== Verifying Flat 5 After Deletion ===");
            String verifyQuery = "SELECT ut.transaction_date, ut.amount, ut.description, " +
                "ut.payprop_data_source " +
                "FROM unified_transactions ut " +
                "LEFT JOIN invoices i ON ut.invoice_id = i.id " +
                "WHERE i.lease_reference = 'LEASE-BH-F5-2025' " +
                "AND ut.flow_direction = 'INCOMING' " +
                "ORDER BY ut.transaction_date";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(verifyQuery)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\nPayment " + count + ":");
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Source: " + rs.getString("payprop_data_source"));
                    System.out.println("  Description: " + rs.getString("description"));
                }
                System.out.println("\nTotal INCOMING payments: " + count);
                System.out.println("Expected: 6 payments (Feb, Mar, Apr, May, Jun, Jul)");

                if (count == 6) {
                    System.out.println("\nSUCCESS: Flat 5 now has exactly 6 payments - duplicates removed!");
                } else {
                    System.out.println("\nWARNING: Expected 6 payments, found " + count);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
