import java.sql.*;

public class QueryBlockExpenses {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            // Find allocations for Boden Block in batch HIST-2025-10-1650 with Jul 22 dates
            String query = """
                SELECT ua.id, ua.allocation_type, ua.amount, ua.paid_date,
                       ut.transaction_date, ut.category, ut.description,
                       p.property_name
                FROM unified_allocations ua
                LEFT JOIN unified_transactions ut ON ua.unified_transaction_id = ut.id
                LEFT JOIN properties p ON ua.property_id = p.id
                WHERE ua.payment_batch_id = 'HIST-2025-10-1650'
                  AND p.property_name LIKE '%Boden%'
                  AND ut.transaction_date = '2025-07-22'
                ORDER BY ua.allocation_type, ua.amount DESC
                """;

            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ResultSet rs = ps.executeQuery();
                System.out.println("BODEN BLOCK ALLOCATIONS IN HIST-2025-10-1650 (Jul 22):");
                System.out.println("======================================================");
                while (rs.next()) {
                    System.out.println("\nAllocation ID: " + rs.getLong("id"));
                    System.out.println("  Type: " + rs.getString("allocation_type"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Paid Date: " + rs.getDate("paid_date"));
                    System.out.println("  Txn Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Category: " + rs.getString("category"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println("  Property: " + rs.getString("property_name"));
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
