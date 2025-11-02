import java.sql.*;

public class CheckPayPropPaymentExpenses {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=======================================================");
            System.out.println("PAYPROP PAYMENT EXPENSE ANALYSIS");
            System.out.println("=======================================================\n");

            // Check BATCH_PAYMENT categories
            System.out.println("ANALYSIS 1: BATCH_PAYMENT categories in financial_transactions");
            System.out.println("---------------------------------------------------------------");

            String batchQuery =
                "SELECT category_name, COUNT(*) as count, SUM(amount) as total " +
                "FROM financial_transactions " +
                "WHERE data_source = 'BATCH_PAYMENT' " +
                "  AND transaction_type = 'payment_to_beneficiary' " +
                "GROUP BY category_name " +
                "ORDER BY count DESC";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(batchQuery)) {

                System.out.println(String.format("%-30s | %-8s | %s", "Category", "Count", "Total"));
                System.out.println("-".repeat(60));

                while (rs.next()) {
                    System.out.println(String.format("%-30s | %-8d | £%.2f",
                        rs.getString("category_name"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total")));
                }
            }

            System.out.println("\n");

            // Check payment_to_agency categories
            System.out.println("ANALYSIS 2: payment_to_agency categories in financial_transactions");
            System.out.println("-------------------------------------------------------------------");

            String agencyQuery =
                "SELECT category_name, COUNT(*) as count, SUM(amount) as total " +
                "FROM financial_transactions " +
                "WHERE transaction_type = 'payment_to_agency' " +
                "GROUP BY category_name " +
                "ORDER BY count DESC";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(agencyQuery)) {

                System.out.println(String.format("%-30s | %-8s | %s", "Category", "Count", "Total"));
                System.out.println("-".repeat(60));

                while (rs.next()) {
                    System.out.println(String.format("%-30s | %-8d | £%.2f",
                        rs.getString("category_name"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total")));
                }
            }

            System.out.println("\n");

            // Sample BATCH_PAYMENT transactions to see if any are expenses
            System.out.println("ANALYSIS 3: Sample BATCH_PAYMENT transactions (first 10)");
            System.out.println("----------------------------------------------------------");

            String sampleQuery =
                "SELECT transaction_date, amount, description, category_name " +
                "FROM financial_transactions " +
                "WHERE data_source = 'BATCH_PAYMENT' " +
                "  AND transaction_type = 'payment_to_beneficiary' " +
                "ORDER BY transaction_date DESC " +
                "LIMIT 10";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sampleQuery)) {

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\nTransaction #" + count + ":");
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println("  Category: " + rs.getString("category_name"));
                }
            }

            System.out.println("\n");

            // Check what's already in unified_transactions from BATCH_PAYMENT
            System.out.println("ANALYSIS 4: BATCH_PAYMENT in unified_transactions");
            System.out.println("--------------------------------------------------");

            String unifiedQuery =
                "SELECT transaction_type, category, COUNT(*) as count, SUM(amount) as total " +
                "FROM unified_transactions " +
                "WHERE payprop_data_source = 'BATCH_PAYMENT' " +
                "GROUP BY transaction_type, category " +
                "ORDER BY transaction_type, category";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(unifiedQuery)) {

                System.out.println(String.format("%-30s | %-20s | %-8s | %s",
                    "Transaction Type", "Category", "Count", "Total"));
                System.out.println("-".repeat(80));

                while (rs.next()) {
                    System.out.println(String.format("%-30s | %-20s | %-8d | £%.2f",
                        rs.getString("transaction_type"),
                        rs.getString("category") != null ? rs.getString("category") : "NULL",
                        rs.getInt("count"),
                        rs.getBigDecimal("total")));
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
