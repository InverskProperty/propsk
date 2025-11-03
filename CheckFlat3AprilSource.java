import java.sql.*;

public class CheckFlat3AprilSource {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String username = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {

            // Check the source of Transaction ID 2590 in unified_transactions
            System.out.println("=== Checking Source of Transaction 2590 (April 14 Payment) ===");
            String unifiedQuery = "SELECT id, transaction_date, amount, description, category, " +
                "source_system, source_table, source_record_id, payprop_data_source " +
                "FROM unified_transactions WHERE id = 2590";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(unifiedQuery)) {
                if (rs.next()) {
                    System.out.println("\nUNIFIED_TRANSACTIONS Record:");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println("  Category: " + rs.getString("category"));
                    System.out.println("  Source System: " + rs.getString("source_system"));
                    System.out.println("  Source Table: " + rs.getString("source_table"));
                    System.out.println("  Source Record ID: " + rs.getLong("source_record_id"));
                    System.out.println("  PayProp Data Source: " + rs.getString("payprop_data_source"));

                    String sourceSystem = rs.getString("source_system");
                    String sourceTable = rs.getString("source_table");
                    Long sourceRecordId = rs.getLong("source_record_id");

                    if ("HISTORICAL".equals(sourceSystem)) {
                        System.out.println("\n=== THIS IS HISTORICAL DATA (CSV Import) ===");

                        // Look up the original record
                        String histQuery = "SELECT id, transaction_date, amount, description, category, " +
                            "source FROM historical_transactions WHERE id = " + sourceRecordId;

                        try (Statement stmt2 = conn.createStatement(); ResultSet rs2 = stmt2.executeQuery(histQuery)) {
                            if (rs2.next()) {
                                System.out.println("\nOriginal HISTORICAL_TRANSACTIONS Record:");
                                System.out.println("  ID: " + rs2.getLong("id"));
                                System.out.println("  Date: " + rs2.getDate("transaction_date"));
                                System.out.println("  Amount: £" + rs2.getBigDecimal("amount"));
                                System.out.println("  Description: " + rs2.getString("description"));
                                System.out.println("  Category: " + rs2.getString("category"));
                                System.out.println("  Source: " + rs2.getString("source"));
                            }
                        }

                    } else if ("PAYPROP".equals(sourceSystem)) {
                        System.out.println("\n=== THIS IS PAYPROP DATA ===");

                        // Look up the original record
                        String paypropQuery = "SELECT id, transaction_date, amount, description, category_name, " +
                            "data_source, transaction_type FROM financial_transactions WHERE id = " + sourceRecordId;

                        try (Statement stmt2 = conn.createStatement(); ResultSet rs2 = stmt2.executeQuery(paypropQuery)) {
                            if (rs2.next()) {
                                System.out.println("\nOriginal FINANCIAL_TRANSACTIONS Record:");
                                System.out.println("  ID: " + rs2.getLong("id"));
                                System.out.println("  Date: " + rs2.getDate("transaction_date"));
                                System.out.println("  Amount: £" + rs2.getBigDecimal("amount"));
                                System.out.println("  Description: " + rs2.getString("description"));
                                System.out.println("  Category Name: " + rs2.getString("category_name"));
                                System.out.println("  Data Source: " + rs2.getString("data_source"));
                                System.out.println("  Transaction Type: " + rs2.getString("transaction_type"));
                            }
                        }
                    }
                }
            }

            // Check ALL Flat 3 payments to see source distribution
            System.out.println("\n=== ALL Flat 3 Payments by Source ===");
            String allQuery = "SELECT source_system, COUNT(*) as count, " +
                "MIN(transaction_date) as earliest, MAX(transaction_date) as latest " +
                "FROM unified_transactions ut " +
                "LEFT JOIN invoices i ON ut.invoice_id = i.id " +
                "WHERE i.lease_reference = 'LEASE-BH-F3-2025' " +
                "AND ut.flow_direction = 'INCOMING' " +
                "GROUP BY source_system";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(allQuery)) {
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
