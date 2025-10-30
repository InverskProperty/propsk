import java.sql.*;

public class CheckExcludedTransactions {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== EXCLUDED TRANSACTIONS ANALYSIS ===\n");

            // Query 1: Historical transactions without invoice_id
            System.out.println("=== 1. HISTORICAL TRANSACTIONS WITHOUT INVOICE_ID ===");
            String query1 = """
                SELECT
                    COUNT(*) as total_excluded,
                    MIN(transaction_date) as earliest_date,
                    MAX(transaction_date) as latest_date,
                    SUM(amount) as total_amount
                FROM historical_transactions
                WHERE invoice_id IS NULL
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-20s %-15s %-15s %-20s%n",
                    "TOTAL_EXCLUDED", "EARLIEST_DATE", "LATEST_DATE", "TOTAL_AMOUNT");
                System.out.println("-".repeat(75));
                while (rs.next()) {
                    System.out.printf("%-20d %-15s %-15s £%-19.2f%n",
                        rs.getInt("total_excluded"),
                        rs.getDate("earliest_date"),
                        rs.getDate("latest_date"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Sample of excluded historical transactions
            System.out.println("\n=== SAMPLE EXCLUDED HISTORICAL (First 10) ===");
            String query1b = """
                SELECT id, transaction_date, amount, description, category, property_id
                FROM historical_transactions
                WHERE invoice_id IS NULL
                ORDER BY transaction_date DESC
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1b)) {
                System.out.printf("%-10s %-15s %-12s %-50s %-20s%n",
                    "ID", "DATE", "AMOUNT", "DESCRIPTION", "CATEGORY");
                System.out.println("-".repeat(115));
                while (rs.next()) {
                    System.out.printf("%-10d %-15s £%-11.2f %-50s %-20s%n",
                        rs.getLong("id"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("description") != null ?
                            (rs.getString("description").length() > 50 ?
                                rs.getString("description").substring(0, 47) + "..." :
                                rs.getString("description")) : "",
                        rs.getString("category"));
                }
            }

            // Query 2: Financial transactions without invoice_id
            System.out.println("\n=== 2. FINANCIAL TRANSACTIONS WITHOUT INVOICE_ID ===");
            String query2 = """
                SELECT
                    data_source,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM financial_transactions
                WHERE invoice_id IS NULL
                  AND data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                GROUP BY data_source
                ORDER BY count DESC
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-30s %-15s %-20s%n",
                    "DATA_SOURCE", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(70));
                while (rs.next()) {
                    System.out.printf("%-30s %-15d £%-19.2f%n",
                        rs.getString("data_source"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Query 3: HISTORICAL_IMPORT and HISTORICAL_CSV (should be excluded)
            System.out.println("\n=== 3. EXCLUDED DUPLICATE SOURCES ===");
            String query3 = """
                SELECT
                    data_source,
                    COUNT(*) as count,
                    COUNT(CASE WHEN invoice_id IS NOT NULL THEN 1 END) as with_invoice_id,
                    COUNT(CASE WHEN invoice_id IS NULL THEN 1 END) as without_invoice_id
                FROM financial_transactions
                WHERE data_source IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                GROUP BY data_source
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-30s %-15s %-20s %-25s%n",
                    "DATA_SOURCE", "TOTAL", "WITH_INVOICE_ID", "WITHOUT_INVOICE_ID");
                System.out.println("-".repeat(95));
                while (rs.next()) {
                    System.out.printf("%-30s %-15d %-20d %-25d%n",
                        rs.getString("data_source"),
                        rs.getInt("count"),
                        rs.getInt("with_invoice_id"),
                        rs.getInt("without_invoice_id"));
                }
            }

            // Query 4: Check if excluded duplicates match historical_transactions
            System.out.println("\n=== 4. DUPLICATE VERIFICATION ===");
            String query4 = """
                SELECT
                    'Records in HISTORICAL_IMPORT' as metric,
                    COUNT(*) as count
                FROM financial_transactions
                WHERE data_source = 'HISTORICAL_IMPORT'
                UNION ALL
                SELECT
                    'Records in historical_transactions' as metric,
                    COUNT(*) as count
                FROM historical_transactions
                UNION ALL
                SELECT
                    'HISTORICAL_IMPORT with invoice_id' as metric,
                    COUNT(*) as count
                FROM financial_transactions
                WHERE data_source = 'HISTORICAL_IMPORT' AND invoice_id IS NOT NULL
                UNION ALL
                SELECT
                    'historical_transactions with invoice_id' as metric,
                    COUNT(*) as count
                FROM historical_transactions
                WHERE invoice_id IS NOT NULL
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-50s %-15s%n", "METRIC", "COUNT");
                System.out.println("-".repeat(70));
                while (rs.next()) {
                    System.out.printf("%-50s %-15d%n",
                        rs.getString("metric"),
                        rs.getInt("count"));
                }
            }

            // Query 5: Transaction type breakdown for excluded financial transactions
            System.out.println("\n=== 5. EXCLUDED FINANCIAL TRANSACTIONS BY TYPE ===");
            String query5 = """
                SELECT
                    transaction_type,
                    data_source,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM financial_transactions
                WHERE invoice_id IS NULL
                  AND data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                GROUP BY transaction_type, data_source
                ORDER BY count DESC
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-30s %-30s %-15s %-20s%n",
                    "TRANSACTION_TYPE", "DATA_SOURCE", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(100));
                while (rs.next()) {
                    System.out.printf("%-30s %-30s %-15d £%-19.2f%n",
                        rs.getString("transaction_type"),
                        rs.getString("data_source"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Query 6: Summary of exclusion reasons
            System.out.println("\n=== 6. EXCLUSION SUMMARY ===");
            String query6a = """
                SELECT COUNT(*) as count FROM historical_transactions WHERE invoice_id IS NULL
                """;
            String query6b = """
                SELECT COUNT(*) as count FROM financial_transactions
                WHERE invoice_id IS NULL AND data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                """;
            String query6c = """
                SELECT COUNT(*) as count FROM financial_transactions
                WHERE data_source IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                """;

            System.out.printf("%-50s %-15s%n", "EXCLUSION_REASON", "COUNT");
            System.out.println("-".repeat(70));

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6a)) {
                if (rs.next()) {
                    System.out.printf("%-50s %-15d%n",
                        "Historical without invoice_id",
                        rs.getInt("count"));
                }
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6b)) {
                if (rs.next()) {
                    System.out.printf("%-50s %-15d%n",
                        "Financial without invoice_id",
                        rs.getInt("count"));
                }
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6c)) {
                if (rs.next()) {
                    System.out.printf("%-50s %-15d%n",
                        "Duplicate sources (HISTORICAL_IMPORT/CSV)",
                        rs.getInt("count"));
                }
            }

            System.out.println("\n=== ANALYSIS COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
