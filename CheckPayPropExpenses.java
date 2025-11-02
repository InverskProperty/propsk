import java.sql.*;

public class CheckPayPropExpenses {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=======================================================");
            System.out.println("PAYPROP EXPENSE ANALYSIS");
            System.out.println("=======================================================\n");

            // Check financial_transactions for credit_note and debit_note
            System.out.println("ANALYSIS 1: credit_note and debit_note in financial_transactions");
            System.out.println("------------------------------------------------------------------");

            String ftQuery =
                "SELECT transaction_type, data_source, COUNT(*) as count, SUM(amount) as total " +
                "FROM financial_transactions " +
                "WHERE transaction_type IN ('credit_note', 'debit_note') " +
                "GROUP BY transaction_type, data_source";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(ftQuery)) {

                System.out.println(String.format("%-20s | %-20s | %-8s | %s",
                    "Type", "Data Source", "Count", "Total"));
                System.out.println("-".repeat(70));

                while (rs.next()) {
                    System.out.println(String.format("%-20s | %-20s | %-8d | £%.2f",
                        rs.getString("transaction_type"),
                        rs.getString("data_source"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total")));
                }
            }

            System.out.println("\n");

            // Check if these are in unified_transactions
            System.out.println("ANALYSIS 2: credit_note and debit_note in unified_transactions");
            System.out.println("---------------------------------------------------------------");

            String utQuery =
                "SELECT transaction_type, payprop_data_source, flow_direction, COUNT(*) as count, SUM(amount) as total " +
                "FROM unified_transactions " +
                "WHERE transaction_type IN ('credit_note', 'debit_note') " +
                "GROUP BY transaction_type, payprop_data_source, flow_direction";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(utQuery)) {

                int totalCount = 0;
                while (rs.next()) {
                    totalCount++;
                    System.out.println(String.format("%-20s | %-20s | %-12s | %d records | £%.2f",
                        rs.getString("transaction_type"),
                        rs.getString("payprop_data_source"),
                        rs.getString("flow_direction"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total")));
                }

                if (totalCount == 0) {
                    System.out.println("  No credit_note or debit_note found in unified_transactions");
                    System.out.println("  These are in financial_transactions but NOT being imported!");
                }
            }

            System.out.println("\n");

            // Sample credit_note and debit_note transactions
            System.out.println("ANALYSIS 3: Sample credit_note/debit_note transactions (first 5)");
            System.out.println("------------------------------------------------------------------");

            String sampleQuery =
                "SELECT transaction_type, transaction_date, amount, description, category_name, data_source " +
                "FROM financial_transactions " +
                "WHERE transaction_type IN ('credit_note', 'debit_note') " +
                "ORDER BY transaction_date DESC " +
                "LIMIT 5";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sampleQuery)) {

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\nSample #" + count + ":");
                    System.out.println("  Type: " + rs.getString("transaction_type"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println("  Category: " + rs.getString("category_name"));
                    System.out.println("  Data Source: " + rs.getString("data_source"));
                }
            }

            System.out.println("\n");

            // Check the WHERE clause in financial_transactions import
            System.out.println("ANALYSIS 4: Check financial_transactions import filtering");
            System.out.println("-----------------------------------------------------------");

            String filterQuery =
                "SELECT " +
                "  COUNT(*) as total_count, " +
                "  SUM(CASE WHEN invoice_id IS NOT NULL THEN 1 ELSE 0 END) as with_invoice, " +
                "  SUM(CASE WHEN invoice_id IS NULL THEN 1 ELSE 0 END) as without_invoice, " +
                "  SUM(CASE WHEN data_source = 'INCOMING_PAYMENT' THEN 1 ELSE 0 END) as incoming_payment, " +
                "  SUM(CASE WHEN transaction_type IN ('credit_note', 'debit_note') THEN 1 ELSE 0 END) as notes " +
                "FROM financial_transactions " +
                "WHERE data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV', 'ICDN_ACTUAL')";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(filterQuery)) {

                if (rs.next()) {
                    System.out.println("Total financial_transactions (excluding historical): " + rs.getInt("total_count"));
                    System.out.println("  With invoice_id: " + rs.getInt("with_invoice"));
                    System.out.println("  Without invoice_id: " + rs.getInt("without_invoice"));
                    System.out.println("  INCOMING_PAYMENT: " + rs.getInt("incoming_payment"));
                    System.out.println("  credit_note/debit_note: " + rs.getInt("notes"));
                    System.out.println();
                    System.out.println("Import rule: (invoice_id IS NOT NULL OR data_source = 'INCOMING_PAYMENT')");
                    System.out.println("This means credit_note/debit_note WITHOUT invoice_id are EXCLUDED!");
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
