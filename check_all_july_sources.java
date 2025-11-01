import java.sql.*;
import java.math.BigDecimal;

public class check_all_july_sources {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {

            System.out.println("=".repeat(120));
            System.out.println("CHECKING ALL DATA SOURCES FOR JULY 2025 RENT RECEIVED");
            System.out.println("=".repeat(120));

            // Query 1: unified_transactions
            String query1 = """
                SELECT
                    'unified_transactions' as source,
                    COUNT(*) as count,
                    SUM(amount) as total
                FROM unified_transactions
                WHERE transaction_date BETWEEN '2025-07-01' AND '2025-07-31'
                  AND payprop_data_source = 'INCOMING_PAYMENT'
            """;

            // Query 2: financial_transactions INCOMING_PAYMENT
            String query2 = """
                SELECT
                    'financial_transactions (INCOMING)' as source,
                    COUNT(*) as count,
                    SUM(amount) as total
                FROM financial_transactions
                WHERE transaction_date BETWEEN '2025-07-01' AND '2025-07-31'
                  AND data_source = 'INCOMING_PAYMENT'
            """;

            // Query 3: historical_transactions rent
            String query3 = """
                SELECT
                    'historical_transactions (Rent)' as source,
                    COUNT(*) as count,
                    SUM(amount) as total
                FROM historical_transactions
                WHERE transaction_date BETWEEN '2025-07-01' AND '2025-07-31'
                  AND (category LIKE '%Rent%' OR category LIKE '%rent%')
            """;

            // Query 4: ALL financial_transactions for July (all data sources)
            String query4 = """
                SELECT
                    data_source,
                    COUNT(*) as count,
                    SUM(amount) as total
                FROM financial_transactions
                WHERE transaction_date BETWEEN '2025-07-01' AND '2025-07-31'
                GROUP BY data_source
                ORDER BY data_source
            """;

            System.out.printf("\n%-50s %-10s %-15s%n", "SOURCE", "COUNT", "TOTAL");
            System.out.println("-".repeat(120));

            // Run queries 1-3
            for (String query : new String[]{query1, query2, query3}) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {
                    if (rs.next()) {
                        String source = rs.getString("source");
                        int count = rs.getInt("count");
                        BigDecimal total = rs.getBigDecimal("total");
                        System.out.printf("%-50s %-10d £%-14.2f%n", source, count, total != null ? total : BigDecimal.ZERO);
                    }
                }
            }

            System.out.println("\n" + "=".repeat(120));
            System.out.println("FINANCIAL_TRANSACTIONS BREAKDOWN BY DATA_SOURCE (JULY 2025)");
            System.out.println("=".repeat(120));

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {

                System.out.printf("%-50s %-10s %-15s%n", "DATA SOURCE", "COUNT", "TOTAL");
                System.out.println("-".repeat(120));

                while (rs.next()) {
                    String dataSource = rs.getString("data_source");
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");
                    System.out.printf("%-50s %-10d £%-14.2f%n", dataSource, count, total != null ? total : BigDecimal.ZERO);
                }
            }

            // Query 5: Check if there are INCOMING_PAYMENT records NOT in unified
            System.out.println("\n" + "=".repeat(120));
            System.out.println("INCOMING_PAYMENT RECORDS MISSING FROM UNIFIED_TRANSACTIONS");
            System.out.println("=".repeat(120));

            String query5 = """
                SELECT
                    ft.id,
                    ft.transaction_date,
                    ft.amount,
                    ft.property_name,
                    ft.invoice_id
                FROM financial_transactions ft
                LEFT JOIN unified_transactions ut ON (
                    ut.source_system = 'PAYPROP'
                    AND ut.source_table = 'financial_transactions'
                    AND ut.source_record_id = ft.id
                )
                WHERE ft.transaction_date BETWEEN '2025-07-01' AND '2025-07-31'
                  AND ft.data_source = 'INCOMING_PAYMENT'
                  AND ut.id IS NULL
                ORDER BY ft.property_name
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {

                System.out.printf("%-10s %-12s %-12s %-40s %-15s%n",
                    "FT_ID", "DATE", "AMOUNT", "PROPERTY", "INVOICE_ID");
                System.out.println("-".repeat(120));

                int missingCount = 0;
                BigDecimal missingTotal = BigDecimal.ZERO;

                while (rs.next()) {
                    Long id = rs.getLong("id");
                    Date date = rs.getDate("transaction_date");
                    BigDecimal amount = rs.getBigDecimal("amount");
                    String propertyName = rs.getString("property_name");
                    if (propertyName == null) propertyName = "NULL";
                    if (propertyName.length() > 40) propertyName = propertyName.substring(0, 37) + "...";
                    Object invoiceId = rs.getObject("invoice_id");
                    String invoiceIdStr = (invoiceId != null) ? invoiceId.toString() : "NULL";

                    System.out.printf("%-10d %-12s £%-11.2f %-40s %-15s%n",
                        id, date, amount, propertyName, invoiceIdStr);

                    missingCount++;
                    missingTotal = missingTotal.add(amount != null ? amount : BigDecimal.ZERO);
                }

                if (missingCount == 0) {
                    System.out.println("No missing records found - all INCOMING_PAYMENT records are in unified_transactions");
                } else {
                    System.out.println("-".repeat(120));
                    System.out.printf("MISSING FROM UNIFIED: %d records totaling £%.2f%n", missingCount, missingTotal);
                }
            }

            System.out.println("\n" + "=".repeat(120));
            System.out.println("Query completed successfully!");
            System.out.println("=".repeat(120));

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
