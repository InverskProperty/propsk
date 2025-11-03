import java.sql.*;

public class FixAprilTransaction {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String username = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {

            // Show current state
            System.out.println("=== BEFORE FIX ===");
            String beforeQuery = "SELECT id, transaction_date, amount, description, category, " +
                "flow_direction, transaction_type FROM unified_transactions WHERE id = 2590";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(beforeQuery)) {
                if (rs.next()) {
                    System.out.println("ID: " + rs.getLong("id"));
                    System.out.println("Date: " + rs.getDate("transaction_date"));
                    System.out.println("Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("Category: " + rs.getString("category"));
                    System.out.println("Flow Direction: " + rs.getString("flow_direction"));
                    System.out.println("Transaction Type: " + rs.getString("transaction_type"));
                }
            }

            // Fix the transaction
            System.out.println("\n=== APPLYING FIX ===");
            String updateQuery = "UPDATE unified_transactions SET " +
                "flow_direction = 'INCOMING', " +
                "category = 'Rent', " +
                "transaction_type = 'RENT_RECEIVED' " +
                "WHERE id = 2590";

            try (Statement stmt = conn.createStatement()) {
                int rowsUpdated = stmt.executeUpdate(updateQuery);
                System.out.println("Updated " + rowsUpdated + " row(s)");
            }

            // Show fixed state
            System.out.println("\n=== AFTER FIX ===");
            String afterQuery = "SELECT id, transaction_date, amount, description, category, " +
                "flow_direction, transaction_type FROM unified_transactions WHERE id = 2590";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(afterQuery)) {
                if (rs.next()) {
                    System.out.println("ID: " + rs.getLong("id"));
                    System.out.println("Date: " + rs.getDate("transaction_date"));
                    System.out.println("Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("Category: " + rs.getString("category"));
                    System.out.println("Flow Direction: " + rs.getString("flow_direction"));
                    System.out.println("Transaction Type: " + rs.getString("transaction_type"));
                }
            }

            System.out.println("\nSUCCESS: Transaction 2590 has been fixed!");
            System.out.println("The April 14 payment should now appear in the RENT_RECEIVED sheet.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
