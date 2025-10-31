import java.sql.*;

public class CheckUnifiedState {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "LbUYFYPmrLsrLYeZCRzqBFMXzcnQOPIj";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== UNIFIED TRANSACTIONS STATE CHECK ===");
            System.out.println();

            // Check unified_transactions by data source
            String unifiedCheck = "SELECT " +
                "COALESCE(source_system, 'NULL') as source, " +
                "COALESCE(payprop_data_source, 'N/A') as data_source, " +
                "COUNT(*) as count, " +
                "CONCAT('£', FORMAT(SUM(amount), 2)) as total " +
                "FROM unified_transactions " +
                "GROUP BY source_system, payprop_data_source " +
                "ORDER BY source_system, payprop_data_source";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(unifiedCheck)) {
                System.out.println("UNIFIED_TRANSACTIONS BREAKDOWN:");
                System.out.println("SOURCE      DATA_SOURCE         COUNT    TOTAL");
                System.out.println("--------------------------------------------------");
                while (rs.next()) {
                    System.out.printf("%-11s %-18s %5d    %s%n",
                        rs.getString("source"),
                        rs.getString("data_source"),
                        rs.getInt("count"),
                        rs.getString("total"));
                }
                System.out.println();
            }

            // Check for ICDN records
            String icdnCheck = "SELECT COUNT(*) as count, " +
                "CONCAT('£', FORMAT(SUM(amount), 2)) as total " +
                "FROM unified_transactions " +
                "WHERE payprop_data_source = 'ICDN_ACTUAL'";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(icdnCheck)) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    String total = rs.getString("total");
                    System.out.println("ICDN EXCLUSION CHECK:");
                    if (count > 0) {
                        System.out.println("L PROBLEM: " + count + " ICDN records still in unified (should be 0)");
                        System.out.println("   Total: " + total);
                    } else {
                        System.out.println(" GOOD: No ICDN records in unified (correct)");
                    }
                    System.out.println();
                }
            }

            // Check incoming payments in unified
            String incomingUnified = "SELECT COUNT(*) as count, " +
                "CONCAT('£', FORMAT(SUM(amount), 2)) as total " +
                "FROM unified_transactions " +
                "WHERE payprop_data_source = 'INCOMING_PAYMENT'";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(incomingUnified)) {
                if (rs.next()) {
                    System.out.println("INCOMING PAYMENTS IN UNIFIED:");
                    System.out.println("Count: " + rs.getInt("count"));
                    System.out.println("Total: " + rs.getString("total"));
                    System.out.println();
                }
            }

            // Check source tables
            String financialCheck = "SELECT data_source, COUNT(*) as count, " +
                "CONCAT('£', FORMAT(SUM(amount), 2)) as total " +
                "FROM financial_transactions " +
                "WHERE data_source IN ('INCOMING_PAYMENT', 'ICDN_ACTUAL', 'BATCH_PAYMENT', 'COMMISSION_PAYMENT') " +
                "GROUP BY data_source";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(financialCheck)) {
                System.out.println("FINANCIAL_TRANSACTIONS SOURCE DATA:");
                System.out.println("DATA_SOURCE         COUNT    TOTAL");
                System.out.println("--------------------------------------------------");
                while (rs.next()) {
                    System.out.printf("%-18s %5d    %s%n",
                        rs.getString("data_source"),
                        rs.getInt("count"),
                        rs.getString("total"));
                }
                System.out.println();
            }

            // Check historical
            String historicalCheck = "SELECT COUNT(*) as count, " +
                "CONCAT('£', FORMAT(SUM(amount), 2)) as total " +
                "FROM historical_transactions " +
                "WHERE category = 'rent' AND amount > 0";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(historicalCheck)) {
                if (rs.next()) {
                    System.out.println("HISTORICAL_TRANSACTIONS (RENT):");
                    System.out.println("Count: " + rs.getInt("count"));
                    System.out.println("Total: " + rs.getString("total"));
                    System.out.println();
                }
            }

            // Check last update times
            String lastUpdateCheck = "SELECT " +
                "MAX(created_at) as last_unified_update, " +
                "(SELECT MAX(created_at) FROM financial_transactions) as last_financial_update " +
                "FROM unified_transactions";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(lastUpdateCheck)) {
                if (rs.next()) {
                    System.out.println("LAST UPDATE TIMES:");
                    System.out.println("Unified: " + rs.getTimestamp("last_unified_update"));
                    System.out.println("Financial: " + rs.getTimestamp("last_financial_update"));
                    System.out.println();
                }
            }

            System.out.println("=== SUMMARY ===");
            System.out.println();
            System.out.println("Expected if unified is up-to-date:");
            System.out.println("  - ICDN_ACTUAL: 0 records in unified (excluded)");
            System.out.println("  - INCOMING_PAYMENT: 106 records in unified");
            System.out.println("  - BATCH_PAYMENT: ~152 records in unified");
            System.out.println("  - COMMISSION_PAYMENT: ~118 records in unified");
            System.out.println();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
