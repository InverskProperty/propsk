import java.sql.*;
import java.math.BigDecimal;

public class CheckTotalFinancials {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== COMPREHENSIVE FINANCIAL TOTALS ANALYSIS ===");
            System.out.println("Expected Total: ~GBP180,000 (Dec 2024 - Jan 2025)\n");

            // Query 1: Historical Transactions Totals
            System.out.println("=== 1. HISTORICAL_TRANSACTIONS TABLE ===");
            String query1 = """
                SELECT
                    COUNT(*) as total_count,
                    SUM(amount) as total_amount,
                    MIN(transaction_date) as earliest_date,
                    MAX(transaction_date) as latest_date,
                    COUNT(CASE WHEN invoice_id IS NOT NULL THEN 1 END) as with_invoice,
                    COUNT(CASE WHEN invoice_id IS NULL THEN 1 END) as without_invoice
                FROM historical_transactions
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-15s %-20s %-15s %-15s %-15s %-15s%n",
                    "TOTAL_COUNT", "TOTAL_AMOUNT", "EARLIEST", "LATEST", "WITH_INVOICE", "NO_INVOICE");
                System.out.println("-".repeat(110));
                if (rs.next()) {
                    System.out.printf("%-15d GBP%-17.2f %-15s %-15s %-15d %-15d%n",
                        rs.getInt("total_count"),
                        rs.getBigDecimal("total_amount"),
                        rs.getDate("earliest_date"),
                        rs.getDate("latest_date"),
                        rs.getInt("with_invoice"),
                        rs.getInt("without_invoice"));
                }
            }

            // Query 2: Historical Dec 2024 - Jan 2025
            System.out.println("\n=== 2. HISTORICAL_TRANSACTIONS (Dec 2024 - Jan 2025) ===");
            String query2 = """
                SELECT
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM historical_transactions
                WHERE transaction_date >= '2024-12-01'
                  AND transaction_date <= '2025-01-31'
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-15s %-20s%n", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(40));
                if (rs.next()) {
                    System.out.printf("%-15d GBP%-17.2f%n",
                        rs.getInt("count"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Query 3: Financial Transactions Totals (PayProp)
            System.out.println("\n=== 3. FINANCIAL_TRANSACTIONS TABLE (All PayProp Data) ===");
            String query3 = """
                SELECT
                    COUNT(*) as total_count,
                    SUM(amount) as total_amount,
                    MIN(transaction_date) as earliest_date,
                    MAX(transaction_date) as latest_date,
                    COUNT(CASE WHEN invoice_id IS NOT NULL THEN 1 END) as with_invoice,
                    COUNT(CASE WHEN invoice_id IS NULL THEN 1 END) as without_invoice
                FROM financial_transactions
                WHERE data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-15s %-20s %-15s %-15s %-15s %-15s%n",
                    "TOTAL_COUNT", "TOTAL_AMOUNT", "EARLIEST", "LATEST", "WITH_INVOICE", "NO_INVOICE");
                System.out.println("-".repeat(110));
                if (rs.next()) {
                    System.out.printf("%-15d GBP%-17.2f %-15s %-15s %-15d %-15d%n",
                        rs.getInt("total_count"),
                        rs.getBigDecimal("total_amount"),
                        rs.getDate("earliest_date"),
                        rs.getDate("latest_date"),
                        rs.getInt("with_invoice"),
                        rs.getInt("without_invoice"));
                }
            }

            // Query 4: Financial Transactions Dec 2024 - Jan 2025
            System.out.println("\n=== 4. FINANCIAL_TRANSACTIONS (Dec 2024 - Jan 2025) ===");
            String query4 = """
                SELECT
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM financial_transactions
                WHERE data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                  AND transaction_date >= '2024-12-01'
                  AND transaction_date <= '2025-01-31'
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-15s %-20s%n", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(40));
                if (rs.next()) {
                    System.out.printf("%-15d GBP%-17.2f%n",
                        rs.getInt("count"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Query 5: Financial Transactions by Data Source
            System.out.println("\n=== 5. FINANCIAL_TRANSACTIONS BREAKDOWN BY DATA_SOURCE ===");
            String query5 = """
                SELECT
                    data_source,
                    COUNT(*) as count,
                    SUM(amount) as total_amount,
                    MIN(transaction_date) as earliest,
                    MAX(transaction_date) as latest
                FROM financial_transactions
                WHERE data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                GROUP BY data_source
                ORDER BY total_amount DESC
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-30s %-10s %-20s %-15s %-15s%n",
                    "DATA_SOURCE", "COUNT", "TOTAL_AMOUNT", "EARLIEST", "LATEST");
                System.out.println("-".repeat(100));
                while (rs.next()) {
                    System.out.printf("%-30s %-10d GBP%-17.2f %-15s %-15s%n",
                        rs.getString("data_source"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total_amount"),
                        rs.getDate("earliest"),
                        rs.getDate("latest"));
                }
            }

            // Query 6: Unified Transactions Totals
            System.out.println("\n=== 6. UNIFIED_TRANSACTIONS TABLE ===");
            String query6 = """
                SELECT
                    COUNT(*) as total_count,
                    SUM(amount) as total_amount,
                    MIN(transaction_date) as earliest_date,
                    MAX(transaction_date) as latest_date
                FROM unified_transactions
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {
                System.out.printf("%-15s %-20s %-15s %-15s%n",
                    "TOTAL_COUNT", "TOTAL_AMOUNT", "EARLIEST", "LATEST");
                System.out.println("-".repeat(75));
                if (rs.next()) {
                    System.out.printf("%-15d GBP%-17.2f %-15s %-15s%n",
                        rs.getInt("total_count"),
                        rs.getBigDecimal("total_amount"),
                        rs.getDate("earliest_date"),
                        rs.getDate("latest_date"));
                }
            }

            // Query 7: Unified by Source System
            System.out.println("\n=== 7. UNIFIED_TRANSACTIONS BY SOURCE_SYSTEM ===");
            String query7 = """
                SELECT
                    source_system,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM unified_transactions
                GROUP BY source_system
                ORDER BY total_amount DESC
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query7)) {
                System.out.printf("%-20s %-15s %-20s%n",
                    "SOURCE_SYSTEM", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(60));
                while (rs.next()) {
                    System.out.printf("%-20s %-15d GBP%-17.2f%n",
                        rs.getString("source_system"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Query 8: Unified Dec 2024 - Jan 2025
            System.out.println("\n=== 8. UNIFIED_TRANSACTIONS (Dec 2024 - Jan 2025) ===");
            String query8 = """
                SELECT
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM unified_transactions
                WHERE transaction_date >= '2024-12-01'
                  AND transaction_date <= '2025-01-31'
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query8)) {
                System.out.printf("%-15s %-20s%n", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(40));
                if (rs.next()) {
                    System.out.printf("%-15d GBP%-17.2f%n",
                        rs.getInt("count"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Query 9: COMBINED TOTALS - What users should see
            System.out.println("\n=== 9. COMBINED TOTALS (What Should Appear in Statements) ===");
            String query9a = """
                SELECT SUM(amount) as historical_total
                FROM historical_transactions
                WHERE invoice_id IS NOT NULL
                """;
            String query9b = """
                SELECT SUM(amount) as payprop_total
                FROM financial_transactions
                WHERE invoice_id IS NOT NULL
                  AND data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                """;

            BigDecimal historicalTotal = BigDecimal.ZERO;
            BigDecimal paypropTotal = BigDecimal.ZERO;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query9a)) {
                if (rs.next()) {
                    historicalTotal = rs.getBigDecimal("historical_total");
                }
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query9b)) {
                if (rs.next()) {
                    paypropTotal = rs.getBigDecimal("payprop_total");
                }
            }

            BigDecimal combinedTotal = historicalTotal.add(paypropTotal);

            System.out.printf("%-30s %-20s%n", "SOURCE", "AMOUNT");
            System.out.println("-".repeat(55));
            System.out.printf("%-30s GBP%-17.2f%n", "Historical (with invoice_id)", historicalTotal);
            System.out.printf("%-30s GBP%-17.2f%n", "PayProp (with invoice_id)", paypropTotal);
            System.out.println("-".repeat(55));
            System.out.printf("%-30s GBP%-17.2f%n", "TOTAL LINKED", combinedTotal);
            System.out.printf("%-30s GBP%-17.2f%n", "EXPECTED", new BigDecimal("180000.00"));
            System.out.printf("%-30s GBP%-17.2f%n", "DIFFERENCE", combinedTotal.subtract(new BigDecimal("180000.00")));

            // Query 10: What's EXCLUDED (unlinked transactions)
            System.out.println("\n=== 10. EXCLUDED FROM STATEMENTS (No invoice_id) ===");
            String query10a = """
                SELECT COUNT(*) as count, SUM(amount) as total
                FROM historical_transactions
                WHERE invoice_id IS NULL
                """;
            String query10b = """
                SELECT COUNT(*) as count, SUM(amount) as total
                FROM financial_transactions
                WHERE invoice_id IS NULL
                  AND data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                """;

            System.out.printf("%-30s %-15s %-20s%n", "SOURCE", "COUNT", "EXCLUDED_AMOUNT");
            System.out.println("-".repeat(70));

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query10a)) {
                if (rs.next()) {
                    System.out.printf("%-30s %-15d GBP%-17.2f%n",
                        "Historical (no invoice_id)",
                        rs.getInt("count"),
                        rs.getBigDecimal("total"));
                }
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query10b)) {
                if (rs.next()) {
                    System.out.printf("%-30s %-15d GBP%-17.2f%n",
                        "PayProp (no invoice_id)",
                        rs.getInt("count"),
                        rs.getBigDecimal("total"));
                }
            }

            // Query 11: Check for NEGATIVE amounts (credits/refunds)
            System.out.println("\n=== 11. TRANSACTION DIRECTION ANALYSIS ===");
            String query11 = """
                SELECT
                    'Historical' as source,
                    COUNT(CASE WHEN amount > 0 THEN 1 END) as positive_count,
                    SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END) as positive_total,
                    COUNT(CASE WHEN amount < 0 THEN 1 END) as negative_count,
                    SUM(CASE WHEN amount < 0 THEN amount ELSE 0 END) as negative_total,
                    SUM(amount) as net_total
                FROM historical_transactions
                WHERE invoice_id IS NOT NULL
                UNION ALL
                SELECT
                    'PayProp' as source,
                    COUNT(CASE WHEN amount > 0 THEN 1 END) as positive_count,
                    SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END) as positive_total,
                    COUNT(CASE WHEN amount < 0 THEN 1 END) as negative_count,
                    SUM(CASE WHEN amount < 0 THEN amount ELSE 0 END) as negative_total,
                    SUM(amount) as net_total
                FROM financial_transactions
                WHERE invoice_id IS NOT NULL
                  AND data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query11)) {
                System.out.printf("%-15s %-15s %-20s %-15s %-20s %-20s%n",
                    "SOURCE", "POSITIVE_CNT", "POSITIVE_TOTAL", "NEGATIVE_CNT", "NEGATIVE_TOTAL", "NET_TOTAL");
                System.out.println("-".repeat(115));
                while (rs.next()) {
                    System.out.printf("%-15s %-15d GBP%-17.2f %-15d GBP%-17.2f GBP%-17.2f%n",
                        rs.getString("source"),
                        rs.getInt("positive_count"),
                        rs.getBigDecimal("positive_total"),
                        rs.getInt("negative_count"),
                        rs.getBigDecimal("negative_total"),
                        rs.getBigDecimal("net_total"));
                }
            }

            System.out.println("\n=== ANALYSIS COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
