import java.sql.*;
import java.math.BigDecimal;

public class check_all_unified_july {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {

            System.out.println("=".repeat(120));
            System.out.println("ALL UNIFIED_TRANSACTIONS FOR JULY 2025 (ALL TYPES)");
            System.out.println("=".repeat(120));

            String query1 = """
                SELECT
                    payprop_data_source,
                    category,
                    COUNT(*) as count,
                    SUM(amount) as total
                FROM unified_transactions
                WHERE transaction_date BETWEEN '2025-07-01' AND '2025-07-31'
                GROUP BY payprop_data_source, category
                ORDER BY payprop_data_source, category
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {

                System.out.printf("%-30s %-30s %-10s %-15s%n",
                    "DATA SOURCE", "CATEGORY", "COUNT", "TOTAL");
                System.out.println("-".repeat(120));

                BigDecimal grandTotal = BigDecimal.ZERO;
                while (rs.next()) {
                    String dataSource = rs.getString("payprop_data_source");
                    if (dataSource == null) dataSource = "NULL (HISTORICAL)";
                    String category = rs.getString("category");
                    if (category == null) category = "NULL";
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");
                    if (total == null) total = BigDecimal.ZERO;

                    grandTotal = grandTotal.add(total);

                    System.out.printf("%-30s %-30s %-10d £%-14.2f%n",
                        dataSource, category, count, total);
                }

                System.out.println("-".repeat(120));
                System.out.printf("GRAND TOTAL:%91s £%-14.2f%n", "", grandTotal);
            }

            // Query 2: Just INCOMING_PAYMENT summary
            System.out.println("\n" + "=".repeat(120));
            System.out.println("JUST INCOMING_PAYMENT SUMMARY");
            System.out.println("=".repeat(120));

            String query2 = """
                SELECT
                    COUNT(*) as count,
                    SUM(amount) as total
                FROM unified_transactions
                WHERE transaction_date BETWEEN '2025-07-01' AND '2025-07-31'
                  AND payprop_data_source = 'INCOMING_PAYMENT'
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {

                if (rs.next()) {
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");
                    System.out.printf("INCOMING_PAYMENT: %d payments totaling £%.2f%n", count, total);
                }
            }

            // Query 3: What's included in statements (invoice_id IS NOT NULL)?
            System.out.println("\n" + "=".repeat(120));
            System.out.println("TRANSACTIONS WITH invoice_id IS NOT NULL (WHAT STATEMENTS USE)");
            System.out.println("=".repeat(120));

            String query3 = """
                SELECT
                    payprop_data_source,
                    COUNT(*) as count,
                    SUM(amount) as total
                FROM unified_transactions
                WHERE transaction_date BETWEEN '2025-07-01' AND '2025-07-31'
                  AND invoice_id IS NOT NULL
                GROUP BY payprop_data_source
                ORDER BY payprop_data_source
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {

                System.out.printf("%-30s %-10s %-15s%n",
                    "DATA SOURCE", "COUNT", "TOTAL");
                System.out.println("-".repeat(120));

                BigDecimal statementTotal = BigDecimal.ZERO;
                while (rs.next()) {
                    String dataSource = rs.getString("payprop_data_source");
                    if (dataSource == null) dataSource = "NULL (HISTORICAL)";
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");
                    if (total == null) total = BigDecimal.ZERO;

                    statementTotal = statementTotal.add(total);

                    System.out.printf("%-30s %-10d £%-14.2f%n",
                        dataSource, count, total);
                }

                System.out.println("-".repeat(120));
                System.out.printf("STATEMENT TOTAL (invoice_id NOT NULL):%62s £%-14.2f%n", "", statementTotal);
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
