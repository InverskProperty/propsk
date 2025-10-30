import java.sql.*;
import java.math.BigDecimal;

public class CheckUnifiedTransactions {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== UNIFIED TRANSACTIONS COMPLETENESS & DUPLICATION AUDIT ===\n");

            // Query 1: Check for duplicates by source
            System.out.println("=== 1. DUPLICATE CHECK BY SOURCE ===");
            String query1 = """
                SELECT
                    source_system,
                    source_table,
                    COUNT(*) as total_records,
                    COUNT(DISTINCT source_record_id) as unique_source_ids,
                    COUNT(*) - COUNT(DISTINCT source_record_id) as duplicate_count
                FROM unified_transactions
                GROUP BY source_system, source_table
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-15s %-30s %-15s %-20s %-15s%n",
                    "SOURCE_SYSTEM", "SOURCE_TABLE", "TOTAL_RECORDS", "UNIQUE_SOURCE_IDS", "DUPLICATES");
                System.out.println("-".repeat(100));
                while (rs.next()) {
                    System.out.printf("%-15s %-30s %-15d %-20d %-15d%n",
                        rs.getString("source_system"),
                        rs.getString("source_table"),
                        rs.getInt("total_records"),
                        rs.getInt("unique_source_ids"),
                        rs.getInt("duplicate_count"));
                }
            }

            // Query 2: Find actual duplicate records
            System.out.println("\n=== 2. ACTUAL DUPLICATE RECORDS ===");
            String query2 = """
                SELECT
                    source_system,
                    source_table,
                    source_record_id,
                    COUNT(*) as count
                FROM unified_transactions
                GROUP BY source_system, source_table, source_record_id
                HAVING count > 1
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-15s %-30s %-20s %-10s%n",
                    "SOURCE_SYSTEM", "SOURCE_TABLE", "SOURCE_RECORD_ID", "COUNT");
                System.out.println("-".repeat(80));
                boolean foundDuplicates = false;
                while (rs.next()) {
                    foundDuplicates = true;
                    System.out.printf("%-15s %-30s %-20d %-10d%n",
                        rs.getString("source_system"),
                        rs.getString("source_table"),
                        rs.getLong("source_record_id"),
                        rs.getInt("count"));
                }
                if (!foundDuplicates) {
                    System.out.println("[OK] NO DUPLICATES FOUND");
                }
            }

            // Query 3: Completeness check - compare source tables
            System.out.println("\n=== 3. COMPLETENESS CHECK - SOURCE VS UNIFIED ===");

            // Historical transactions
            String query3a = """
                SELECT
                    'historical_transactions' as source,
                    COUNT(*) as total,
                    COUNT(CASE WHEN invoice_id IS NOT NULL THEN 1 END) as with_invoice_id,
                    COUNT(CASE WHEN invoice_id IS NULL THEN 1 END) as without_invoice_id
                FROM historical_transactions
                """;

            // Financial transactions (excluding historical duplicates)
            String query3b = """
                SELECT
                    'financial_transactions' as source,
                    COUNT(*) as total,
                    COUNT(CASE WHEN invoice_id IS NOT NULL THEN 1 END) as with_invoice_id,
                    COUNT(CASE WHEN invoice_id IS NULL THEN 1 END) as without_invoice_id
                FROM financial_transactions
                WHERE data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                """;

            // Unified transactions
            String query3c = """
                SELECT
                    'unified_transactions' as source,
                    COUNT(*) as total,
                    COUNT(CASE WHEN invoice_id IS NOT NULL THEN 1 END) as with_invoice_id,
                    COUNT(CASE WHEN invoice_id IS NULL THEN 1 END) as without_invoice_id
                FROM unified_transactions
                """;

            System.out.printf("%-30s %-10s %-20s %-25s%n",
                "SOURCE", "TOTAL", "WITH_INVOICE_ID", "WITHOUT_INVOICE_ID");
            System.out.println("-".repeat(90));

            for (String query : new String[]{query3a, query3b, query3c}) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {
                    while (rs.next()) {
                        System.out.printf("%-30s %-10d %-20d %-25d%n",
                            rs.getString("source"),
                            rs.getInt("total"),
                            rs.getInt("with_invoice_id"),
                            rs.getInt("without_invoice_id"));
                    }
                }
            }

            // Query 4: Check missing transactions
            System.out.println("\n=== 4. MISSING TRANSACTIONS CHECK ===");

            // Historical transactions not in unified
            String query4a = """
                SELECT COUNT(*) as missing_historical
                FROM historical_transactions ht
                WHERE ht.invoice_id IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM unified_transactions ut
                    WHERE ut.source_system = 'HISTORICAL'
                      AND ut.source_table = 'historical_transactions'
                      AND ut.source_record_id = ht.id
                  )
                """;

            // Financial transactions not in unified
            String query4b = """
                SELECT COUNT(*) as missing_financial
                FROM financial_transactions ft
                WHERE ft.invoice_id IS NOT NULL
                  AND ft.data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                  AND NOT EXISTS (
                    SELECT 1 FROM unified_transactions ut
                    WHERE ut.source_system = 'PAYPROP'
                      AND ut.source_table = 'financial_transactions'
                      AND ut.source_record_id = ft.id
                  )
                """;

            System.out.printf("%-30s %-10s%n", "CHECK", "COUNT");
            System.out.println("-".repeat(45));

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4a)) {
                if (rs.next()) {
                    int missing = rs.getInt("missing_historical");
                    System.out.printf("%-30s %-10d %s%n",
                        "Missing Historical Txns",
                        missing,
                        missing == 0 ? "[OK]" : "[ERROR]");
                }
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4b)) {
                if (rs.next()) {
                    int missing = rs.getInt("missing_financial");
                    System.out.printf("%-30s %-10d %s%n",
                        "Missing Financial Txns",
                        missing,
                        missing == 0 ? "[OK]" : "[ERROR]");
                }
            }

            // Query 5: Date range and amount totals
            System.out.println("\n=== 5. DATE RANGE & AMOUNT TOTALS ===");
            String query5 = """
                SELECT
                    source_system,
                    COUNT(*) as record_count,
                    MIN(transaction_date) as earliest_date,
                    MAX(transaction_date) as latest_date,
                    SUM(amount) as total_amount
                FROM unified_transactions
                GROUP BY source_system
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-15s %-15s %-15s %-15s %-20s%n",
                    "SOURCE_SYSTEM", "RECORD_COUNT", "EARLIEST_DATE", "LATEST_DATE", "TOTAL_AMOUNT");
                System.out.println("-".repeat(90));
                while (rs.next()) {
                    System.out.printf("%-15s %-15d %-15s %-15s £%-19.2f%n",
                        rs.getString("source_system"),
                        rs.getInt("record_count"),
                        rs.getDate("earliest_date"),
                        rs.getDate("latest_date"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Query 6: Rebuild metadata
            System.out.println("\n=== 6. REBUILD METADATA ===");
            String query6 = """
                SELECT
                    MIN(rebuilt_at) as first_build,
                    MAX(rebuilt_at) as last_build,
                    COUNT(DISTINCT rebuild_batch_id) as batch_count
                FROM unified_transactions
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {
                System.out.printf("%-30s %-30s %-15s%n",
                    "FIRST_BUILD", "LAST_BUILD", "BATCH_COUNT");
                System.out.println("-".repeat(80));
                while (rs.next()) {
                    System.out.printf("%-30s %-30s %-15d%n",
                        rs.getTimestamp("first_build"),
                        rs.getTimestamp("last_build"),
                        rs.getInt("batch_count"));
                }
            }

            // Query 7: Invoice linkage quality
            System.out.println("\n=== 7. INVOICE LINKAGE QUALITY ===");
            String query7 = """
                SELECT
                    source_system,
                    COUNT(*) as total,
                    COUNT(CASE WHEN invoice_id IS NOT NULL THEN 1 END) as linked_to_invoice,
                    COUNT(CASE WHEN lease_reference IS NOT NULL THEN 1 END) as has_lease_ref,
                    COUNT(CASE WHEN property_id IS NOT NULL THEN 1 END) as has_property
                FROM unified_transactions
                GROUP BY source_system
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query7)) {
                System.out.printf("%-15s %-10s %-20s %-20s %-15s%n",
                    "SOURCE_SYSTEM", "TOTAL", "LINKED_TO_INVOICE", "HAS_LEASE_REF", "HAS_PROPERTY");
                System.out.println("-".repeat(90));
                while (rs.next()) {
                    System.out.printf("%-15s %-10d %-20d %-20d %-15d%n",
                        rs.getString("source_system"),
                        rs.getInt("total"),
                        rs.getInt("linked_to_invoice"),
                        rs.getInt("has_lease_ref"),
                        rs.getInt("has_property"));
                }
            }

            System.out.println("\n=== AUDIT COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
