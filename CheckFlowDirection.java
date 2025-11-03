import java.sql.*;

public class CheckFlowDirection {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String username = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {

            // Check the flow_direction for Transaction ID 2590 (April 14)
            System.out.println("=== Checking Transaction ID 2590 (April 14 Flat 3 Payment) ===");
            String query = "SELECT id, transaction_date, amount, description, category, flow_direction, " +
                "transaction_type, payprop_data_source FROM unified_transactions WHERE id = 2590";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
                if (rs.next()) {
                    System.out.println("ID: " + rs.getLong("id"));
                    System.out.println("Date: " + rs.getDate("transaction_date"));
                    System.out.println("Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("Description: " + rs.getString("description"));
                    System.out.println("Category: " + rs.getString("category"));
                    System.out.println("Flow Direction: " + rs.getString("flow_direction"));
                    System.out.println("Transaction Type: " + rs.getString("transaction_type"));
                    System.out.println("PayProp Data Source: " + rs.getString("payprop_data_source"));

                    String flowDirection = rs.getString("flow_direction");
                    if (flowDirection == null) {
                        System.out.println("\nPROBLEM: flow_direction is NULL!");
                        System.out.println("This will cause the transaction to be excluded from statement generation.");
                    } else if (!flowDirection.equals("INCOMING")) {
                        System.out.println("\nPROBLEM: flow_direction is '" + flowDirection + "' but should be 'INCOMING' for rent received!");
                    } else {
                        System.out.println("\nflow_direction is correct (INCOMING)");
                    }
                } else {
                    System.out.println("Transaction ID 2590 not found!");
                }
            }

            // Check ALL transactions for Flat 3 to see flow_direction distribution
            System.out.println("\n=== Flow Direction Distribution for ALL Flat 3 Transactions ===");
            String distQuery = "SELECT flow_direction, COUNT(*) as count FROM unified_transactions " +
                "WHERE invoice_id = 3 GROUP BY flow_direction";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(distQuery)) {
                while (rs.next()) {
                    String flowDir = rs.getString("flow_direction");
                    int count = rs.getInt("count");
                    System.out.println(flowDir + ": " + count + " transactions");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
