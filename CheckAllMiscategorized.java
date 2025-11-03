import java.sql.*;

public class CheckAllMiscategorized {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String username = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {

            // Check for transactions with null category
            System.out.println("=== Transactions with NULL Category ===");
            String nullCatQuery = "SELECT id, transaction_date, amount, description, flow_direction, " +
                "invoice_id, source_system FROM unified_transactions " +
                "WHERE category IS NULL AND invoice_id IS NOT NULL " +
                "ORDER BY transaction_date LIMIT 20";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(nullCatQuery)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\nTransaction " + count + ":");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Flow Direction: " + rs.getString("flow_direction"));
                    System.out.println("  Invoice ID: " + rs.getLong("invoice_id"));
                    System.out.println("  Source: " + rs.getString("source_system"));
                    System.out.println("  Description: " + rs.getString("description"));
                }
                System.out.println("\nTotal with NULL category: " + count);
            }

            // Check for rent-like transactions marked as OUTGOING
            System.out.println("\n=== Rent-like Descriptions with OUTGOING Flow ===");
            String outgoingRentQuery = "SELECT id, transaction_date, amount, description, category, " +
                "flow_direction, invoice_id FROM unified_transactions " +
                "WHERE flow_direction = 'OUTGOING' " +
                "AND invoice_id IS NOT NULL " +
                "AND (description LIKE '%Rent%' OR description LIKE '%rent%') " +
                "AND amount > 0 " +
                "ORDER BY transaction_date LIMIT 20";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(outgoingRentQuery)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\nSuspicious Transaction " + count + ":");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Category: " + rs.getString("category"));
                    System.out.println("  Flow Direction: " + rs.getString("flow_direction"));
                    System.out.println("  Invoice ID: " + rs.getLong("invoice_id"));
                    System.out.println("  Description: " + rs.getString("description"));
                }
                System.out.println("\nTotal suspicious: " + count);
            }

            // Summary statistics
            System.out.println("\n=== Summary Statistics ===");
            String statsQuery = "SELECT " +
                "COUNT(*) as total, " +
                "SUM(CASE WHEN category IS NULL THEN 1 ELSE 0 END) as null_category, " +
                "SUM(CASE WHEN flow_direction IS NULL THEN 1 ELSE 0 END) as null_flow, " +
                "SUM(CASE WHEN flow_direction = 'INCOMING' THEN 1 ELSE 0 END) as incoming, " +
                "SUM(CASE WHEN flow_direction = 'OUTGOING' THEN 1 ELSE 0 END) as outgoing " +
                "FROM unified_transactions WHERE invoice_id IS NOT NULL";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(statsQuery)) {
                if (rs.next()) {
                    System.out.println("Total transactions with invoice_id: " + rs.getInt("total"));
                    System.out.println("  - NULL category: " + rs.getInt("null_category"));
                    System.out.println("  - NULL flow_direction: " + rs.getInt("null_flow"));
                    System.out.println("  - INCOMING: " + rs.getInt("incoming"));
                    System.out.println("  - OUTGOING: " + rs.getInt("outgoing"));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
