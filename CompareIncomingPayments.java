import java.sql.*;
import java.math.BigDecimal;

public class CompareIncomingPayments {
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
            System.out.println("=== INCOMING PAYMENTS COMPARISON ===\n");

            // Query 1: financial_transactions INCOMING_PAYMENT
            System.out.println("=== 1. FINANCIAL_TRANSACTIONS (PayProp) ===");
            String financialQuery = """
                SELECT
                    COUNT(*) as total_count,
                    SUM(amount) as total_amount,
                    MIN(transaction_date) as earliest_date,
                    MAX(transaction_date) as latest_date,
                    AVG(amount) as avg_amount
                FROM financial_transactions
                WHERE data_source = 'INCOMING_PAYMENT'
                """;

            BigDecimal financialTotal = BigDecimal.ZERO;
            int financialCount = 0;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(financialQuery)) {
                if (rs.next()) {
                    financialCount = rs.getInt("total_count");
                    financialTotal = rs.getBigDecimal("total_amount");

                    System.out.println("Data Source: INCOMING_PAYMENT");
                    System.out.println("Total records: " + financialCount);
                    System.out.println("Total amount: £" + financialTotal);
                    System.out.println("Date range: " + rs.getDate("earliest_date") + " to " + rs.getDate("latest_date"));
                    System.out.println("Average amount: £" + rs.getBigDecimal("avg_amount"));
                }
            }

            // Sample records
            System.out.println("\nSample INCOMING_PAYMENT records:");
            String sampleFinancial = """
                SELECT
                    transaction_date,
                    property_name,
                    tenant_name,
                    amount,
                    description
                FROM financial_transactions
                WHERE data_source = 'INCOMING_PAYMENT'
                ORDER BY transaction_date DESC
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sampleFinancial)) {
                System.out.printf("%-15s %-30s %-12s%n", "DATE", "PROPERTY", "AMOUNT");
                System.out.println("-".repeat(65));
                while (rs.next()) {
                    System.out.printf("%-15s %-30s GBP%-9.2f%n",
                        rs.getDate("transaction_date"),
                        rs.getString("property_name"),
                        rs.getBigDecimal("amount"));
                }
            }

            // Query 2: Check transaction_type values in historical_transactions
            System.out.println("\n\n=== 2. HISTORICAL_TRANSACTIONS (Transaction Types) ===");
            String typeQuery = """
                SELECT
                    transaction_type,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM historical_transactions
                GROUP BY transaction_type
                ORDER BY count DESC
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(typeQuery)) {
                System.out.printf("%-30s %-15s %-20s%n", "TRANSACTION_TYPE", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(70));
                while (rs.next()) {
                    System.out.printf("%-30s %-15d GBP%-17.2f%n",
                        rs.getString("transaction_type"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Query 3: historical_transactions incoming (assuming type = 'INCOMING' or 'INCOMING_PAYMENT')
            System.out.println("\n=== 3. HISTORICAL_TRANSACTIONS INCOMING PAYMENTS ===");
            String historicalQuery = """
                SELECT
                    COUNT(*) as total_count,
                    SUM(amount) as total_amount,
                    MIN(transaction_date) as earliest_date,
                    MAX(transaction_date) as latest_date,
                    AVG(amount) as avg_amount
                FROM historical_transactions
                WHERE transaction_type IN ('INCOMING', 'INCOMING_PAYMENT', 'PAYMENT_IN', 'RECEIPT')
                """;

            BigDecimal historicalTotal = BigDecimal.ZERO;
            int historicalCount = 0;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(historicalQuery)) {
                if (rs.next()) {
                    historicalCount = rs.getInt("total_count");
                    historicalTotal = rs.getBigDecimal("total_amount") != null ? rs.getBigDecimal("total_amount") : BigDecimal.ZERO;

                    System.out.println("Transaction Types: INCOMING/INCOMING_PAYMENT/PAYMENT_IN/RECEIPT");
                    System.out.println("Total records: " + historicalCount);
                    System.out.println("Total amount: £" + historicalTotal);
                    if (rs.getDate("earliest_date") != null) {
                        System.out.println("Date range: " + rs.getDate("earliest_date") + " to " + rs.getDate("latest_date"));
                        System.out.println("Average amount: £" + rs.getBigDecimal("avg_amount"));
                    }
                }
            }

            // Sample historical incoming
            System.out.println("\nSample historical INCOMING records:");
            String sampleHistorical = """
                SELECT
                    ht.transaction_date,
                    p.property_name,
                    ht.amount,
                    ht.description,
                    ht.transaction_type
                FROM historical_transactions ht
                LEFT JOIN properties p ON ht.property_id = p.id
                WHERE ht.transaction_type IN ('INCOMING', 'INCOMING_PAYMENT', 'PAYMENT_IN', 'RECEIPT')
                ORDER BY ht.transaction_date DESC
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sampleHistorical)) {
                System.out.printf("%-15s %-30s %-20s %-12s%n", "DATE", "PROPERTY", "TYPE", "AMOUNT");
                System.out.println("-".repeat(85));
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("%-15s %-30s %-20s GBP%-9.2f%n",
                        rs.getDate("transaction_date"),
                        rs.getString("property_name"),
                        rs.getString("transaction_type"),
                        rs.getBigDecimal("amount"));
                }
                if (count == 0) {
                    System.out.println("[NO INCOMING RECORDS FOUND IN HISTORICAL]");
                }
            }

            // Query 4: Check category field for incoming payments
            System.out.println("\n=== 4. HISTORICAL_TRANSACTIONS BY CATEGORY ===");
            String categoryQuery = """
                SELECT
                    category,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM historical_transactions
                WHERE category IS NOT NULL
                GROUP BY category
                ORDER BY count DESC
                LIMIT 15
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(categoryQuery)) {
                System.out.printf("%-40s %-15s %-20s%n", "CATEGORY", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(80));
                while (rs.next()) {
                    System.out.printf("%-40s %-15d GBP%-17.2f%n",
                        rs.getString("category"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Query 5: Summary comparison
            System.out.println("\n\n=== 5. GRAND SUMMARY ===");
            System.out.println("-".repeat(80));
            System.out.printf("%-50s %-15s %-20s%n", "SOURCE", "COUNT", "TOTAL AMOUNT");
            System.out.println("-".repeat(80));
            System.out.printf("%-50s %-15d GBP%-17.2f%n",
                "financial_transactions (INCOMING_PAYMENT)",
                financialCount,
                financialTotal);
            System.out.printf("%-50s %-15d GBP%-17.2f%n",
                "historical_transactions (INCOMING types)",
                historicalCount,
                historicalTotal);
            System.out.println("-".repeat(80));

            BigDecimal combinedTotal = financialTotal.add(historicalTotal);
            int combinedCount = financialCount + historicalCount;

            System.out.printf("%-50s %-15d GBP%-17.2f%n",
                "COMBINED TOTAL",
                combinedCount,
                combinedTotal);
            System.out.println("-".repeat(80));

            // Query 6: Check unified_transactions current state
            System.out.println("\n=== 6. CURRENT STATE OF unified_transactions ===");
            String unifiedQuery = """
                SELECT
                    source_system,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM unified_transactions
                GROUP BY source_system
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(unifiedQuery)) {
                System.out.printf("%-30s %-15s %-20s%n", "SOURCE_SYSTEM", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(70));
                while (rs.next()) {
                    System.out.printf("%-30s %-15d GBP%-17.2f%n",
                        rs.getString("source_system"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Check incoming in unified
            System.out.println("\n=== 7. INCOMING PAYMENTS IN unified_transactions ===");
            String unifiedIncomingQuery = """
                SELECT
                    source_system,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM unified_transactions
                WHERE payprop_data_source = 'INCOMING_PAYMENT'
                   OR category LIKE '%incoming%'
                   OR category LIKE '%receipt%'
                   OR category LIKE '%payment%'
                GROUP BY source_system
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(unifiedIncomingQuery)) {
                System.out.printf("%-30s %-15s %-20s%n", "SOURCE_SYSTEM", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(70));
                int totalCount = 0;
                BigDecimal totalAmount = BigDecimal.ZERO;

                while (rs.next()) {
                    int count = rs.getInt("count");
                    BigDecimal amount = rs.getBigDecimal("total_amount");
                    totalCount += count;
                    totalAmount = totalAmount.add(amount);

                    System.out.printf("%-30s %-15d GBP%-17.2f%n",
                        rs.getString("source_system"),
                        count,
                        amount);
                }

                if (totalCount > 0) {
                    System.out.println("-".repeat(70));
                    System.out.printf("%-30s %-15d GBP%-17.2f%n", "TOTAL IN UNIFIED", totalCount, totalAmount);
                }
            }

            System.out.println("\n=== ANALYSIS COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
