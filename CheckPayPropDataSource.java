import java.sql.*;

public class CheckPayPropDataSource {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String username = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {

            // Check the PayProp financial_transactions for Flat 5 June/July
            System.out.println("=== PayProp Financial Transactions for Flat 5 (June-July) ===");
            String query = "SELECT ft.id, ft.transaction_date, ft.amount, ft.description, " +
                "ft.category_name, ft.data_source, ft.transaction_type, ft.pay_prop_transaction_id " +
                "FROM financial_transactions ft " +
                "WHERE ft.id IN (15152, 15484, 15118, 15500) " +
                "ORDER BY ft.transaction_date";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    System.out.println("\n" + (rs.getDate("transaction_date").toString().contains("Jun") ? "JUNE" : "JULY") + " Transaction:");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Category: " + rs.getString("category_name"));
                    System.out.println("  Data Source: " + rs.getString("data_source"));
                    System.out.println("  Transaction Type: " + rs.getString("transaction_type"));
                    System.out.println("  PayProp TX ID: " + rs.getString("pay_prop_transaction_id"));
                    System.out.println("  Description: " + rs.getString("description"));
                }
            }

            // Get all PayProp data_source values for Flat 5
            System.out.println("\n=== Data Source Distribution for Flat 5 PayProp Transactions ===");
            String distQuery = "SELECT ft.data_source, ft.transaction_type, COUNT(*) as count, " +
                "GROUP_CONCAT(DISTINCT ft.category_name) as categories " +
                "FROM financial_transactions ft " +
                "WHERE ft.invoice_id = 5 " +
                "AND ft.data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV', 'ICDN_ACTUAL') " +
                "GROUP BY ft.data_source, ft.transaction_type " +
                "ORDER BY ft.data_source";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(distQuery)) {
                while (rs.next()) {
                    System.out.println("\nData Source: " + rs.getString("data_source"));
                    System.out.println("  Transaction Type: " + rs.getString("transaction_type"));
                    System.out.println("  Count: " + rs.getInt("count"));
                    System.out.println("  Categories: " + rs.getString("categories"));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
