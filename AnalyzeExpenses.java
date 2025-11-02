import java.sql.*;
import java.math.BigDecimal;

public class AnalyzeExpenses {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=======================================================");
            System.out.println("EXPENSE HANDLING ANALYSIS");
            System.out.println("=======================================================\n");

            // Analysis 1: Check all transaction_type values
            System.out.println("ANALYSIS 1: All transaction_type values in unified_transactions");
            System.out.println("----------------------------------------------------------------");

            String typeQuery =
                "SELECT transaction_type, flow_direction, COUNT(*) as count, SUM(amount) as total " +
                "FROM unified_transactions " +
                "GROUP BY transaction_type, flow_direction " +
                "ORDER BY flow_direction, transaction_type";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(typeQuery)) {

                System.out.println(String.format("%-30s | %-12s | %-8s | %s",
                    "Transaction Type", "Flow", "Count", "Total Amount"));
                System.out.println("-".repeat(80));

                while (rs.next()) {
                    String type = rs.getString("transaction_type");
                    String flow = rs.getString("flow_direction");
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");

                    System.out.println(String.format("%-30s | %-12s | %-8d | £%.2f",
                        type != null ? type : "NULL",
                        flow != null ? flow : "NULL",
                        count,
                        total != null ? total : BigDecimal.ZERO));
                }
            }

            System.out.println("\n");

            // Analysis 2: Check categories that might be expenses
            System.out.println("ANALYSIS 2: Category breakdown in unified_transactions");
            System.out.println("-------------------------------------------------------");

            String categoryQuery =
                "SELECT category, flow_direction, COUNT(*) as count, SUM(amount) as total " +
                "FROM unified_transactions " +
                "WHERE category IS NOT NULL " +
                "GROUP BY category, flow_direction " +
                "ORDER BY flow_direction, category";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(categoryQuery)) {

                System.out.println(String.format("%-30s | %-12s | %-8s | %s",
                    "Category", "Flow", "Count", "Total Amount"));
                System.out.println("-".repeat(80));

                while (rs.next()) {
                    String category = rs.getString("category");
                    String flow = rs.getString("flow_direction");
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");

                    System.out.println(String.format("%-30s | %-12s | %-8d | £%.2f",
                        category, flow, count, total));
                }
            }

            System.out.println("\n");

            // Analysis 3: Check payprop_data_source for expense-related entries
            System.out.println("ANALYSIS 3: PayProp data sources");
            System.out.println("---------------------------------");

            String sourceQuery =
                "SELECT payprop_data_source, flow_direction, COUNT(*) as count, SUM(amount) as total " +
                "FROM unified_transactions " +
                "WHERE payprop_data_source IS NOT NULL " +
                "GROUP BY payprop_data_source, flow_direction " +
                "ORDER BY flow_direction, payprop_data_source";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sourceQuery)) {

                System.out.println(String.format("%-30s | %-12s | %-8s | %s",
                    "PayProp Data Source", "Flow", "Count", "Total Amount"));
                System.out.println("-".repeat(80));

                while (rs.next()) {
                    String source = rs.getString("payprop_data_source");
                    String flow = rs.getString("flow_direction");
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");

                    System.out.println(String.format("%-30s | %-12s | %-8d | £%.2f",
                        source, flow, count, total));
                }
            }

            System.out.println("\n");

            // Analysis 4: Sample expense transactions
            System.out.println("ANALYSIS 4: Sample expense-like transactions (first 10)");
            System.out.println("---------------------------------------------------------");

            String sampleQuery =
                "SELECT id, transaction_date, amount, description, category, " +
                "transaction_type, flow_direction, payprop_data_source " +
                "FROM unified_transactions " +
                "WHERE (category LIKE '%expense%' OR category LIKE '%Expense%' " +
                "   OR transaction_type LIKE '%expense%' " +
                "   OR description LIKE '%expense%' OR description LIKE '%Expense%') " +
                "ORDER BY transaction_date DESC " +
                "LIMIT 10";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sampleQuery)) {

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\nExpense Transaction #" + count + ":");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println("  Category: " + rs.getString("category"));
                    System.out.println("  Type: " + rs.getString("transaction_type"));
                    System.out.println("  Flow: " + rs.getString("flow_direction"));
                    System.out.println("  PayProp Source: " + rs.getString("payprop_data_source"));
                }

                if (count == 0) {
                    System.out.println("  No explicit expense transactions found");
                }
            }

            System.out.println("\n");

            // Analysis 5: Check historical_transactions table
            System.out.println("ANALYSIS 5: Historical transactions table analysis");
            System.out.println("---------------------------------------------------");

            String historicalQuery =
                "SELECT category, COUNT(*) as count, SUM(amount) as total " +
                "FROM historical_transactions " +
                "GROUP BY category " +
                "ORDER BY category";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(historicalQuery)) {

                System.out.println(String.format("%-40s | %-8s | %s",
                    "Historical Category", "Count", "Total Amount"));
                System.out.println("-".repeat(80));

                while (rs.next()) {
                    String category = rs.getString("category");
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");

                    System.out.println(String.format("%-40s | %-8d | £%.2f",
                        category != null ? category : "NULL", count,
                        total != null ? total : BigDecimal.ZERO));
                }
            }

            System.out.println("\n");

            // Analysis 6: Check financial_transactions (PayProp) table
            System.out.println("ANALYSIS 6: Financial transactions (PayProp) table analysis");
            System.out.println("-----------------------------------------------------------");

            String financialQuery =
                "SELECT transaction_type, COUNT(*) as count, SUM(amount) as total " +
                "FROM financial_transactions " +
                "GROUP BY transaction_type " +
                "ORDER BY transaction_type";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(financialQuery)) {

                System.out.println(String.format("%-40s | %-8s | %s",
                    "PayProp Transaction Type", "Count", "Total Amount"));
                System.out.println("-".repeat(80));

                while (rs.next()) {
                    String type = rs.getString("transaction_type");
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");

                    System.out.println(String.format("%-40s | %-8d | £%.2f",
                        type != null ? type : "NULL", count,
                        total != null ? total : BigDecimal.ZERO));
                }
            }

            System.out.println("\n=======================================================");
            System.out.println("ANALYSIS COMPLETE");
            System.out.println("=======================================================");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
