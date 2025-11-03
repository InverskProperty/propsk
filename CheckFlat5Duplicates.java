import java.sql.*;

public class CheckFlat5Duplicates {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String username = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {

            // Check ALL Flat 5 INCOMING payments with details
            System.out.println("=== ALL Flat 5 INCOMING Payments ===");
            String query = "SELECT ut.id, ut.transaction_date, ut.amount, ut.description, " +
                "ut.source_system, ut.source_table, ut.source_record_id, " +
                "ut.flow_direction, ut.category, i.lease_reference " +
                "FROM unified_transactions ut " +
                "LEFT JOIN invoices i ON ut.invoice_id = i.id " +
                "WHERE i.lease_reference = 'LEASE-BH-F5-2025' " +
                "AND ut.flow_direction = 'INCOMING' " +
                "ORDER BY ut.transaction_date, ut.id";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
                int count = 0;
                String lastDate = null;
                int dateCount = 0;

                while (rs.next()) {
                    count++;
                    String currentDate = rs.getDate("transaction_date").toString();

                    if (currentDate.equals(lastDate)) {
                        dateCount++;
                        System.out.println("\n⚠️ DUPLICATE DETECTED - Payment " + dateCount + " on same date!");
                    } else {
                        dateCount = 1;
                        lastDate = currentDate;
                    }

                    System.out.println("\nPayment " + count + ":");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Source: " + rs.getString("source_system"));
                    System.out.println("  Source Table: " + rs.getString("source_table"));
                    System.out.println("  Source Record ID: " + rs.getLong("source_record_id"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println("  Category: " + rs.getString("category"));
                }
                System.out.println("\nTotal INCOMING payments: " + count);
            }

            // Group by date to see duplicates clearly
            System.out.println("\n=== Payments Grouped by Date ===");
            String groupQuery = "SELECT ut.transaction_date, COUNT(*) as payment_count, " +
                "SUM(ut.amount) as total_amount, " +
                "GROUP_CONCAT(ut.source_system ORDER BY ut.id) as sources, " +
                "GROUP_CONCAT(ut.id ORDER BY ut.id) as transaction_ids " +
                "FROM unified_transactions ut " +
                "LEFT JOIN invoices i ON ut.invoice_id = i.id " +
                "WHERE i.lease_reference = 'LEASE-BH-F5-2025' " +
                "AND ut.flow_direction = 'INCOMING' " +
                "GROUP BY ut.transaction_date " +
                "ORDER BY ut.transaction_date";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(groupQuery)) {
                while (rs.next()) {
                    int paymentCount = rs.getInt("payment_count");
                    System.out.println("\nDate: " + rs.getDate("transaction_date"));
                    System.out.println("  Payment Count: " + paymentCount);
                    System.out.println("  Total Amount: £" + rs.getBigDecimal("total_amount"));
                    System.out.println("  Sources: " + rs.getString("sources"));
                    System.out.println("  Transaction IDs: " + rs.getString("transaction_ids"));

                    if (paymentCount > 1) {
                        System.out.println("  ⚠️ DUPLICATE: Multiple payments on same date!");
                    }
                }
            }

            // Check source distribution
            System.out.println("\n=== Source System Distribution ===");
            String sourceQuery = "SELECT ut.source_system, COUNT(*) as count, " +
                "MIN(ut.transaction_date) as earliest, MAX(ut.transaction_date) as latest " +
                "FROM unified_transactions ut " +
                "LEFT JOIN invoices i ON ut.invoice_id = i.id " +
                "WHERE i.lease_reference = 'LEASE-BH-F5-2025' " +
                "AND ut.flow_direction = 'INCOMING' " +
                "GROUP BY ut.source_system";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sourceQuery)) {
                while (rs.next()) {
                    System.out.println("\nSource: " + rs.getString("source_system"));
                    System.out.println("  Count: " + rs.getInt("count"));
                    System.out.println("  Date Range: " + rs.getDate("earliest") + " to " + rs.getDate("latest"));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
