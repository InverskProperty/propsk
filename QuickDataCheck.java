import java.sql.*;
import java.math.BigDecimal;

public class QuickDataCheck {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found.");
        }

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== QUICK DATA STATE CHECK ===\n");

            // 1. Unified transactions summary (simple query without created_at)
            System.out.println("1. UNIFIED_TRANSACTIONS SUMMARY:");
            String unifiedQuery = """
                SELECT
                    source_system,
                    payprop_data_source,
                    COUNT(*) as count,
                    SUM(amount) as total
                FROM unified_transactions
                GROUP BY source_system, payprop_data_source
                ORDER BY source_system, payprop_data_source
                """;

            int totalUnified = 0;
            BigDecimal totalUnifiedAmount = BigDecimal.ZERO;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(unifiedQuery)) {
                System.out.printf("%-15s %-25s %-10s %-15s%n",
                    "SOURCE", "DATA_SOURCE", "COUNT", "TOTAL");
                System.out.println("=".repeat(70));

                while (rs.next()) {
                    String source = rs.getString("source_system");
                    String dataSource = rs.getString("payprop_data_source");
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");

                    totalUnified += count;
                    if (total != null) totalUnifiedAmount = totalUnifiedAmount.add(total);

                    System.out.printf("%-15s %-25s %-10d GBP%-12.2f%n",
                        source,
                        dataSource != null ? dataSource : "N/A",
                        count,
                        total);
                }
                System.out.println("=".repeat(70));
                System.out.printf("%-42s %-10d GBP%-12.2f%n", "TOTAL UNIFIED:", totalUnified, totalUnifiedAmount);
            }

            // 2. ICDN check
            System.out.println("\n2. ICDN EXCLUSION CHECK:");
            String icdnCheck = "SELECT COUNT(*) as cnt FROM unified_transactions WHERE payprop_data_source = 'ICDN_ACTUAL'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(icdnCheck)) {
                if (rs.next()) {
                    int icdnCount = rs.getInt("cnt");
                    if (icdnCount == 0) {
                        System.out.println("[OK] No ICDN records in unified (correct)");
                    } else {
                        System.out.println("[ERROR] Found " + icdnCount + " ICDN records (should be excluded!)");
                    }
                }
            }

            // 3. Source tables
            System.out.println("\n3. SOURCE TABLES:");

            // Financial transactions
            String ftQuery = "SELECT data_source, COUNT(*) as cnt, SUM(amount) as total FROM financial_transactions GROUP BY data_source";
            System.out.println("\nFinancial Transactions:");
            System.out.printf("  %-25s %-10s %-15s%n", "DATA_SOURCE", "COUNT", "TOTAL");
            System.out.println("  " + "-".repeat(55));
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(ftQuery)) {
                while (rs.next()) {
                    System.out.printf("  %-25s %-10d GBP%-12.2f%n",
                        rs.getString("data_source"),
                        rs.getInt("cnt"),
                        rs.getBigDecimal("total"));
                }
            }

            // Historical transactions
            String histQuery = "SELECT COUNT(*) as cnt, SUM(amount) as total FROM historical_transactions";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(histQuery)) {
                if (rs.next()) {
                    System.out.println("\nHistorical Transactions:");
                    System.out.println("  Total: " + rs.getInt("cnt") + " records, GBP " + rs.getBigDecimal("total"));
                }
            }

            // Historical rent only
            String histRentQuery = "SELECT COUNT(*) as cnt, SUM(amount) as total FROM historical_transactions WHERE category = 'rent' AND amount > 0";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(histRentQuery)) {
                if (rs.next()) {
                    System.out.println("  Rent only: " + rs.getInt("cnt") + " records, GBP " + rs.getBigDecimal("total"));
                }
            }

            // 4. Incoming payments comparison
            System.out.println("\n4. INCOMING PAYMENTS VERIFICATION:");

            int histRentCount = 0;
            BigDecimal histRentTotal = BigDecimal.ZERO;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(histRentQuery)) {
                if (rs.next()) {
                    histRentCount = rs.getInt("cnt");
                    histRentTotal = rs.getBigDecimal("total");
                }
            }

            String ppIncomingQuery = "SELECT COUNT(*) as cnt, SUM(amount) as total FROM financial_transactions WHERE data_source = 'INCOMING_PAYMENT'";
            int ppCount = 0;
            BigDecimal ppTotal = BigDecimal.ZERO;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(ppIncomingQuery)) {
                if (rs.next()) {
                    ppCount = rs.getInt("cnt");
                    ppTotal = rs.getBigDecimal("total");
                }
            }

            String unifiedIncomingQuery = """
                SELECT COUNT(*) as cnt, SUM(amount) as total
                FROM unified_transactions
                WHERE (source_system = 'HISTORICAL' AND amount > 0)
                   OR (source_system = 'PAYPROP' AND payprop_data_source = 'INCOMING_PAYMENT')
                """;
            int unifiedIncCount = 0;
            BigDecimal unifiedIncTotal = BigDecimal.ZERO;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(unifiedIncomingQuery)) {
                if (rs.next()) {
                    unifiedIncCount = rs.getInt("cnt");
                    unifiedIncTotal = rs.getBigDecimal("total");
                }
            }

            System.out.printf("  %-40s %10d  GBP%12.2f%n", "Historical Rent:", histRentCount, histRentTotal);
            System.out.printf("  %-40s %10d  GBP%12.2f%n", "PayProp Incoming:", ppCount, ppTotal);
            System.out.println("  " + "-".repeat(70));
            System.out.printf("  %-40s %10d  GBP%12.2f%n", "Expected in Unified:", histRentCount + ppCount, histRentTotal.add(ppTotal));
            System.out.printf("  %-40s %10d  GBP%12.2f%n", "Actual in Unified:", unifiedIncCount, unifiedIncTotal);

            boolean countMatch = (unifiedIncCount == histRentCount + ppCount);
            BigDecimal expectedTotal = histRentTotal.add(ppTotal);
            BigDecimal diff = unifiedIncTotal.subtract(expectedTotal).abs();
            boolean amountMatch = diff.compareTo(new BigDecimal("1.00")) < 0;

            System.out.println();
            System.out.println("  Count Match: " + (countMatch ? "[OK]" : "[ERROR] Mismatch: " + (unifiedIncCount - histRentCount - ppCount)));
            System.out.println("  Amount Match: " + (amountMatch ? "[OK]" : "[ERROR] Diff: GBP " + diff));

            // 5. Check if rebuild happened recently
            System.out.println("\n5. REBUILD STATUS:");
            String maxIdQuery = "SELECT MAX(id) as max_id, COUNT(*) as total FROM unified_transactions";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(maxIdQuery)) {
                if (rs.next()) {
                    System.out.println("  Total records: " + rs.getInt("total"));
                    System.out.println("  Max ID: " + rs.getLong("max_id"));
                }
            }

            System.out.println("\n=== QUICK CHECK COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
