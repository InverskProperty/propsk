import java.sql.*;
import java.math.BigDecimal;

public class AnalyzeUnifiedTransactions {
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
            System.out.println("=== UNIFIED TRANSACTIONS ANALYSIS ===\n");

            // Check if unified_transactions exists and its structure
            System.out.println("=== 1. UNIFIED TRANSACTIONS BREAKDOWN BY SOURCE ===");
            String query1 = """
                SELECT
                    payprop_data_source,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM unified_transactions
                WHERE payprop_data_source IS NOT NULL
                GROUP BY payprop_data_source
                ORDER BY count DESC
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-30s %-15s %-20s%n", "PAYPROP_DATA_SOURCE", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(70));

                BigDecimal totalAmount = BigDecimal.ZERO;
                int totalCount = 0;

                while (rs.next()) {
                    String source = rs.getString("payprop_data_source");
                    int count = rs.getInt("count");
                    BigDecimal amount = rs.getBigDecimal("total_amount");

                    totalCount += count;
                    if (amount != null) {
                        totalAmount = totalAmount.add(amount);
                    }

                    System.out.printf("%-30s %-15d GBP%-17.2f%n", source, count, amount);
                }

                System.out.println("-".repeat(70));
                System.out.printf("%-30s %-15d GBP%-17.2f%n", "TOTAL PAYPROP", totalCount, totalAmount);
            }

            // Check ICDN_ACTUAL specifically
            System.out.println("\n=== 2. ICDN_ACTUAL DETAILS ===");
            String query2 = """
                SELECT
                    COUNT(*) as total_count,
                    SUM(amount) as total_amount,
                    MIN(transaction_date) as earliest_date,
                    MAX(transaction_date) as latest_date
                FROM unified_transactions
                WHERE payprop_data_source = 'ICDN_ACTUAL'
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                if (rs.next()) {
                    System.out.println("ICDN_ACTUAL Statistics:");
                    System.out.println("  Total transactions: " + rs.getInt("total_count"));
                    System.out.println("  Total amount: £" + rs.getBigDecimal("total_amount"));
                    System.out.println("  Date range: " + rs.getDate("earliest_date") + " to " + rs.getDate("latest_date"));
                }
            }

            // Check financial_transactions
            System.out.println("\n=== 3. FINANCIAL TRANSACTIONS BY SOURCE ===");
            String query3 = """
                SELECT
                    data_source,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM financial_transactions
                GROUP BY data_source
                ORDER BY count DESC
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-30s %-15s %-20s%n", "DATA_SOURCE", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(70));

                while (rs.next()) {
                    System.out.printf("%-30s %-15d GBP%-17.2f%n",
                        rs.getString("data_source"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Sample ICDN transactions
            System.out.println("\n=== 4. SAMPLE ICDN_ACTUAL TRANSACTIONS ===");
            String query4 = """
                SELECT
                    transaction_date,
                    property_name,
                    description,
                    amount,
                    transaction_type
                FROM unified_transactions
                WHERE payprop_data_source = 'ICDN_ACTUAL'
                ORDER BY transaction_date DESC
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-15s %-30s %-30s %-12s%n", "DATE", "PROPERTY", "DESCRIPTION", "AMOUNT");
                System.out.println("-".repeat(95));

                int count = 0;
                while (rs.next()) {
                    count++;
                    String desc = rs.getString("description");
                    if (desc != null && desc.length() > 30) {
                        desc = desc.substring(0, 27) + "...";
                    }
                    System.out.printf("%-15s %-30s %-30s GBP%-9.2f%n",
                        rs.getDate("transaction_date"),
                        rs.getString("property_name"),
                        desc,
                        rs.getBigDecimal("amount"));
                }

                if (count == 0) {
                    System.out.println("[NO ICDN_ACTUAL FOUND IN unified_transactions]");
                }
            }

            // Check what SHOULD be in unified (cash flow only)
            System.out.println("\n=== 5. RECOMMENDED DATA SOURCES (Cash Flow Only) ===");
            String query5 = """
                SELECT
                    data_source,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM financial_transactions
                WHERE data_source IN ('INCOMING_PAYMENT', 'BATCH_PAYMENT', 'COMMISSION_PAYMENT')
                GROUP BY data_source
                ORDER BY data_source
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-30s %-15s %-20s%n", "DATA_SOURCE", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(70));

                BigDecimal totalAmount = BigDecimal.ZERO;
                int totalCount = 0;

                while (rs.next()) {
                    int count = rs.getInt("count");
                    BigDecimal amount = rs.getBigDecimal("total_amount");

                    totalCount += count;
                    if (amount != null) {
                        totalAmount = totalAmount.add(amount);
                    }

                    System.out.printf("%-30s %-15d GBP%-17.2f%n",
                        rs.getString("data_source"),
                        count,
                        amount);
                }

                System.out.println("-".repeat(70));
                System.out.printf("%-30s %-15d GBP%-17.2f%n", "TOTAL CASH FLOW", totalCount, totalAmount);
            }

            // Total comparison
            System.out.println("\n=== 6. IMPACT OF REMOVING ICDN_ACTUAL ===");
            String query6a = "SELECT COUNT(*) as count FROM unified_transactions";
            String query6b = "SELECT COUNT(*) as count FROM unified_transactions WHERE payprop_data_source != 'ICDN_ACTUAL' OR payprop_data_source IS NULL";

            try (Statement stmt = conn.createStatement()) {
                ResultSet rs1 = stmt.executeQuery(query6a);
                int currentTotal = 0;
                if (rs1.next()) {
                    currentTotal = rs1.getInt("count");
                }

                ResultSet rs2 = stmt.executeQuery(query6b);
                int afterRemoval = 0;
                if (rs2.next()) {
                    afterRemoval = rs2.getInt("count");
                }

                System.out.println("Current unified_transactions count: " + currentTotal);
                System.out.println("After removing ICDN_ACTUAL: " + afterRemoval);
                System.out.println("Transactions to remove: " + (currentTotal - afterRemoval));
                System.out.println("Percentage reduction: " + String.format("%.1f%%",
                    ((currentTotal - afterRemoval) * 100.0 / currentTotal)));
            }

            System.out.println("\n=== ANALYSIS COMPLETE ===");
            System.out.println("\nRECOMMENDATION:");
            System.out.println("✓ Keep: INCOMING_PAYMENT (tenant receipts)");
            System.out.println("✓ Keep: BATCH_PAYMENT (owner disbursements)");
            System.out.println("✓ Keep: COMMISSION_PAYMENT (commission charges)");
            System.out.println("✗ Remove: ICDN_ACTUAL (invoices - not cash flow)");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
