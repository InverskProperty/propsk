import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CheckDataState {
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
            System.out.println("=== DATA STATE REVIEW AFTER IMPORT ===");
            System.out.println("Review Time: " + LocalDateTime.now());
            System.out.println();

            // 1. Check unified_transactions state
            System.out.println("=== 1. UNIFIED_TRANSACTIONS STATUS ===");
            String unifiedQuery = """
                SELECT
                    source_system,
                    payprop_data_source,
                    COUNT(*) as count,
                    SUM(amount) as total,
                    MIN(transaction_date) as earliest,
                    MAX(transaction_date) as latest,
                    MAX(created_at) as last_created
                FROM unified_transactions
                GROUP BY source_system, payprop_data_source
                ORDER BY source_system, payprop_data_source
                """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(unifiedQuery)) {
                System.out.printf("%-15s %-20s %-8s %-15s %-12s %-12s %-20s%n",
                    "SOURCE", "DATA_SOURCE", "COUNT", "TOTAL", "EARLIEST", "LATEST", "LAST_CREATED");
                System.out.println("=".repeat(120));

                int totalCount = 0;
                BigDecimal totalAmount = BigDecimal.ZERO;

                while (rs.next()) {
                    String source = rs.getString("source_system");
                    String dataSource = rs.getString("payprop_data_source");
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");

                    totalCount += count;
                    if (total != null) totalAmount = totalAmount.add(total);

                    System.out.printf("%-15s %-20s %-8d £%-14.2f %-12s %-12s %-20s%n",
                        source,
                        dataSource != null ? dataSource : "N/A",
                        count,
                        total,
                        rs.getDate("earliest"),
                        rs.getDate("latest"),
                        rs.getTimestamp("last_created"));
                }
                System.out.println("=".repeat(120));
                System.out.printf("%-37s %-8d £%-14.2f%n", "TOTAL UNIFIED:", totalCount, totalAmount);
            }

            // 2. Check for ICDN in unified (should be ZERO)
            System.out.println("\n=== 2. ICDN EXCLUSION CHECK (Should be 0) ===");
            String icdnCheck = """
                SELECT COUNT(*) as icdn_count
                FROM unified_transactions
                WHERE payprop_data_source = 'ICDN_ACTUAL'
                """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(icdnCheck)) {
                if (rs.next()) {
                    int icdnCount = rs.getInt("icdn_count");
                    if (icdnCount == 0) {
                        System.out.println("[OK] GOOD: No ICDN records in unified_transactions");
                    } else {
                        System.out.println("[ERROR] PROBLEM: Found " + icdnCount + " ICDN records in unified_transactions!");
                        System.out.println("   -> ICDN should be excluded from unified data");
                    }
                }
            }

            // 3. Check financial_transactions by data_source
            System.out.println("\n=== 3. FINANCIAL_TRANSACTIONS BY DATA_SOURCE ===");
            String ftQuery = """
                SELECT
                    data_source,
                    COUNT(*) as count,
                    SUM(amount) as total,
                    MAX(created_at) as last_created
                FROM financial_transactions
                GROUP BY data_source
                ORDER BY data_source
                """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(ftQuery)) {
                System.out.printf("%-25s %-10s %-20s %-20s%n",
                    "DATA_SOURCE", "COUNT", "TOTAL", "LAST_CREATED");
                System.out.println("=".repeat(80));

                while (rs.next()) {
                    System.out.printf("%-25s %-10d £%-18.2f %-20s%n",
                        rs.getString("data_source"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total"),
                        rs.getTimestamp("last_created"));
                }
            }

            // 4. Check historical_transactions
            System.out.println("\n=== 4. HISTORICAL_TRANSACTIONS STATUS ===");
            String histQuery = """
                SELECT
                    COUNT(*) as count,
                    SUM(amount) as total,
                    MIN(transaction_date) as earliest,
                    MAX(transaction_date) as latest
                FROM historical_transactions
                """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(histQuery)) {
                if (rs.next()) {
                    System.out.println("Total Records: " + rs.getInt("count"));
                    System.out.println("Total Amount: £" + rs.getBigDecimal("total"));
                    System.out.println("Date Range: " + rs.getDate("earliest") + " to " + rs.getDate("latest"));
                }
            }

            // 5. Check rent payments specifically
            System.out.println("\n=== 5. INCOMING RENT PAYMENTS COMPARISON ===");

            // Historical rent
            String histRentQuery = """
                SELECT COUNT(*) as count, SUM(amount) as total
                FROM historical_transactions
                WHERE category = 'rent' AND amount > 0
                """;

            int histCount = 0;
            BigDecimal histTotal = BigDecimal.ZERO;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(histRentQuery)) {
                if (rs.next()) {
                    histCount = rs.getInt("count");
                    histTotal = rs.getBigDecimal("total");
                }
            }

            // PayProp incoming payments
            String ppIncomingQuery = """
                SELECT COUNT(*) as count, SUM(amount) as total
                FROM financial_transactions
                WHERE data_source = 'INCOMING_PAYMENT'
                """;

            int ppCount = 0;
            BigDecimal ppTotal = BigDecimal.ZERO;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(ppIncomingQuery)) {
                if (rs.next()) {
                    ppCount = rs.getInt("count");
                    ppTotal = rs.getBigDecimal("total");
                }
            }

            // Unified incoming (should match sum of above)
            String unifiedIncomingQuery = """
                SELECT COUNT(*) as count, SUM(amount) as total
                FROM unified_transactions
                WHERE (source_system = 'HISTORICAL' AND amount > 0)
                   OR (source_system = 'PAYPROP' AND payprop_data_source = 'INCOMING_PAYMENT')
                """;

            int unifiedCount = 0;
            BigDecimal unifiedTotal = BigDecimal.ZERO;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(unifiedIncomingQuery)) {
                if (rs.next()) {
                    unifiedCount = rs.getInt("count");
                    unifiedTotal = rs.getBigDecimal("total");
                }
            }

            System.out.printf("%-40s %-10s %-20s%n", "SOURCE", "COUNT", "TOTAL");
            System.out.println("=".repeat(75));
            System.out.printf("%-40s %-10d £%-18.2f%n", "Historical (category=rent)", histCount, histTotal);
            System.out.printf("%-40s %-10d £%-18.2f%n", "PayProp (INCOMING_PAYMENT)", ppCount, ppTotal);
            System.out.println("-".repeat(75));
            System.out.printf("%-40s %-10d £%-18.2f%n", "Expected Total", histCount + ppCount, histTotal.add(ppTotal));
            System.out.printf("%-40s %-10d £%-18.2f%n", "Unified Incoming", unifiedCount, unifiedTotal);

            // Check if they match
            if (unifiedCount == histCount + ppCount) {
                System.out.println("\n[OK] COUNT MATCH: Unified has correct number of records");
            } else {
                System.out.println("\n[ERROR] COUNT MISMATCH: Unified has " + unifiedCount +
                    " but expected " + (histCount + ppCount));
            }

            BigDecimal expectedTotal = histTotal.add(ppTotal);
            BigDecimal diff = unifiedTotal.subtract(expectedTotal).abs();
            if (diff.compareTo(new BigDecimal("1.00")) < 0) {
                System.out.println("[OK] AMOUNT MATCH: Unified total matches source tables");
            } else {
                System.out.println("[ERROR] AMOUNT MISMATCH: Difference of GBP " + diff);
            }

            // 6. Check rebuild timestamp
            System.out.println("\n=== 6. RECENT REBUILD ACTIVITY ===");
            String recentQuery = """
                SELECT
                    source_system,
                    MAX(created_at) as most_recent_record
                FROM unified_transactions
                GROUP BY source_system
                ORDER BY most_recent_record DESC
                """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(recentQuery)) {
                System.out.printf("%-20s %-30s%n", "SOURCE", "MOST RECENT RECORD CREATED");
                System.out.println("=".repeat(55));

                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("most_recent_record");
                    System.out.printf("%-20s %-30s%n",
                        rs.getString("source_system"),
                        ts);

                    // Check if very recent (within last 10 minutes)
                    long ageMinutes = (System.currentTimeMillis() - ts.getTime()) / (1000 * 60);
                    if (ageMinutes < 10) {
                        System.out.println("   -> [RECENT] REBUILD (" + ageMinutes + " minutes ago)");
                    }
                }
            }

            // 7. Check for duplicate detection
            System.out.println("\n=== 7. DUPLICATE CHECK ===");
            String dupQuery = """
                SELECT
                    transaction_date,
                    amount,
                    property_name,
                    COUNT(*) as occurrences
                FROM unified_transactions
                WHERE source_system = 'PAYPROP'
                GROUP BY transaction_date, amount, property_name
                HAVING COUNT(*) > 1
                ORDER BY occurrences DESC
                LIMIT 10
                """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(dupQuery)) {
                boolean foundDups = false;

                while (rs.next()) {
                    if (!foundDups) {
                        System.out.println("[WARNING] Found potential duplicates:");
                        System.out.printf("%-15s %-12s %-40s %-10s%n",
                            "DATE", "AMOUNT", "PROPERTY", "COUNT");
                        System.out.println("-".repeat(80));
                        foundDups = true;
                    }

                    System.out.printf("%-15s GBP%-9.2f %-40s %-10d%n",
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("property_name"),
                        rs.getInt("occurrences"));
                }

                if (!foundDups) {
                    System.out.println("[OK] No duplicate transactions detected");
                }
            }

            System.out.println("\n=== DATA STATE REVIEW COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
