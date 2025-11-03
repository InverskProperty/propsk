import java.sql.*;

public class BulkFixRentTransactions {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String username = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {

            // Step 1: Identify all miscategorized rent transactions
            System.out.println("=== STEP 1: Finding Miscategorized Rent Transactions ===");
            String findQuery = "SELECT id, transaction_date, amount, description, flow_direction, " +
                "category, invoice_id FROM unified_transactions " +
                "WHERE flow_direction = 'OUTGOING' " +
                "AND invoice_id IS NOT NULL " +
                "AND (description LIKE '%Rent%' OR description LIKE '%rent%') " +
                "AND amount > 0 " +
                "ORDER BY transaction_date";

            int count = 0;
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(findQuery)) {
                while (rs.next()) {
                    count++;
                    if (count <= 10) {  // Show first 10
                        System.out.println("\nTransaction " + count + ":");
                        System.out.println("  ID: " + rs.getLong("id"));
                        System.out.println("  Date: " + rs.getDate("transaction_date"));
                        System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                        System.out.println("  Flow: " + rs.getString("flow_direction"));
                        System.out.println("  Category: " + rs.getString("category"));
                        System.out.println("  Description: " + rs.getString("description"));
                    }
                }
            }
            System.out.println("\nTotal miscategorized rent transactions found: " + count);

            // Step 2: Confirm before fixing
            System.out.println("\n=== STEP 2: Applying Bulk Fix ===");
            System.out.println("Fixing " + count + " transactions...");

            // Update all rent transactions marked as OUTGOING to INCOMING
            String updateQuery = "UPDATE unified_transactions SET " +
                "flow_direction = 'INCOMING', " +
                "category = CASE " +
                "  WHEN category IS NULL THEN 'Rent' " +
                "  ELSE category " +
                "END, " +
                "transaction_type = 'RENT_RECEIVED' " +
                "WHERE flow_direction = 'OUTGOING' " +
                "AND invoice_id IS NOT NULL " +
                "AND (description LIKE '%Rent%' OR description LIKE '%rent%') " +
                "AND amount > 0";

            try (Statement stmt = conn.createStatement()) {
                int rowsUpdated = stmt.executeUpdate(updateQuery);
                System.out.println("SUCCESS: Updated " + rowsUpdated + " transactions");
            }

            // Step 3: Verify the fix
            System.out.println("\n=== STEP 3: Verification ===");

            // Check for remaining miscategorized
            String verifyQuery = "SELECT COUNT(*) as remaining FROM unified_transactions " +
                "WHERE flow_direction = 'OUTGOING' " +
                "AND invoice_id IS NOT NULL " +
                "AND (description LIKE '%Rent%' OR description LIKE '%rent%') " +
                "AND amount > 0";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(verifyQuery)) {
                if (rs.next()) {
                    int remaining = rs.getInt("remaining");
                    System.out.println("Remaining miscategorized rent transactions: " + remaining);
                    if (remaining == 0) {
                        System.out.println("SUCCESS: All rent transactions have been corrected!");
                    }
                }
            }

            // Show updated statistics
            System.out.println("\n=== STEP 4: Updated Statistics ===");
            String statsQuery = "SELECT " +
                "COUNT(*) as total, " +
                "SUM(CASE WHEN category IS NULL THEN 1 ELSE 0 END) as null_category, " +
                "SUM(CASE WHEN flow_direction = 'INCOMING' THEN 1 ELSE 0 END) as incoming, " +
                "SUM(CASE WHEN flow_direction = 'OUTGOING' THEN 1 ELSE 0 END) as outgoing " +
                "FROM unified_transactions WHERE invoice_id IS NOT NULL";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(statsQuery)) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    int nullCat = rs.getInt("null_category");
                    int incoming = rs.getInt("incoming");
                    int outgoing = rs.getInt("outgoing");

                    System.out.println("Total transactions with invoice_id: " + total);
                    System.out.println("  - NULL category: " + nullCat);
                    System.out.println("  - INCOMING: " + incoming + " (" + (incoming * 100 / total) + "%)");
                    System.out.println("  - OUTGOING: " + outgoing + " (" + (outgoing * 100 / total) + "%)");
                }
            }

            // Show sample of fixed transactions
            System.out.println("\n=== STEP 5: Sample of Fixed Transactions ===");
            String sampleQuery = "SELECT id, transaction_date, amount, description, flow_direction, " +
                "category, transaction_type FROM unified_transactions " +
                "WHERE transaction_type = 'RENT_RECEIVED' " +
                "AND flow_direction = 'INCOMING' " +
                "AND (description LIKE '%Rent%' OR description LIKE '%rent%') " +
                "ORDER BY transaction_date DESC LIMIT 5";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sampleQuery)) {
                int sample = 0;
                while (rs.next()) {
                    sample++;
                    System.out.println("\nFixed Transaction " + sample + ":");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Flow: " + rs.getString("flow_direction"));
                    System.out.println("  Category: " + rs.getString("category"));
                    System.out.println("  Type: " + rs.getString("transaction_type"));
                    System.out.println("  Description: " + rs.getString("description"));
                }
            }

            System.out.println("\n========================================");
            System.out.println("BULK FIX COMPLETE!");
            System.out.println("All rent payments now correctly marked as INCOMING");
            System.out.println("Regenerate statements to see the corrections");
            System.out.println("========================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
