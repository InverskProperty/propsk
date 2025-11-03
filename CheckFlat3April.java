import java.sql.*;

public class CheckFlat3April {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String username = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        String query = "SELECT " +
            "ut.id, ut.transaction_date, ut.amount, ut.description, ut.category, " +
            "ut.invoice_id, i.lease_reference " +
            "FROM unified_transactions ut " +
            "LEFT JOIN invoices i ON ut.invoice_id = i.id " +
            "WHERE i.lease_reference = 'LEASE-BH-F3-2025' " +
            "AND ut.transaction_date >= '2025-04-01' " +
            "AND ut.transaction_date <= '2025-04-30' " +
            "AND ut.category = 'INCOMING_PAYMENT' " +
            "ORDER BY ut.transaction_date";

        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            System.out.println("=== Flat 3 April 2025 Payments ===");
            boolean found = false;

            while (rs.next()) {
                found = true;
                System.out.println("ID: " + rs.getLong("id"));
                System.out.println("Date: " + rs.getDate("transaction_date"));
                System.out.println("Amount: £" + rs.getBigDecimal("amount"));
                System.out.println("Description: " + rs.getString("description"));
                System.out.println("Invoice ID: " + rs.getLong("invoice_id"));
                System.out.println("---");
            }

            if (!found) {
                System.out.println("NO payments found for Flat 3 in April 2025!");
                System.out.println("This confirms it's a DATA ISSUE - the transaction is missing from unified_transactions");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
