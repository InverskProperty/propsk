import java.sql.*;

public class CheckRentReceived {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        System.out.println("=== Checking Rent Received Transactions ===\n");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Check for duplicate rent transactions
            System.out.println("1. Checking for duplicates in unified_transactions:");
            String sql1 = """
                SELECT source_record_id, COUNT(*) as count, transaction_date, amount, description
                FROM unified_transactions
                WHERE transaction_type = 'rent_received'
                GROUP BY source_record_id, transaction_date, amount, description
                HAVING count > 1
                LIMIT 10
            """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql1)) {
                int dupeCount = 0;
                while (rs.next()) {
                    dupeCount++;
                    System.out.printf("  DUPLICATE: source_record_id=%d, count=%d, date=%s, amount=£%.2f, desc=%s\n",
                        rs.getInt("source_record_id"),
                        rs.getInt("count"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("description"));
                }
                if (dupeCount == 0) {
                    System.out.println("  No duplicates found");
                } else {
                    System.out.println("\n  WARNING: Found " + dupeCount + " duplicate rent records!");
                }
            }
            
            // Check total rent_received count
            System.out.println("\n2. Rent received transaction count:");
            String sql2 = "SELECT COUNT(*) as count, SUM(amount) as total FROM unified_transactions WHERE transaction_type = 'rent_received'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql2)) {
                if (rs.next()) {
                    System.out.println("  Total records: " + rs.getInt("count"));
                    System.out.println("  Total amount: £" + rs.getBigDecimal("total"));
                }
            }
            
            // Check historical_transactions rent count
            System.out.println("\n3. Historical transactions rent count (source):");
            String sql3 = "SELECT COUNT(*) as count, SUM(amount) as total FROM historical_transactions WHERE category LIKE '%rent%'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql3)) {
                if (rs.next()) {
                    System.out.println("  Total records: " + rs.getInt("count"));
                    System.out.println("  Total amount: £" + rs.getBigDecimal("total"));
                }
            }
            
            // Sample July 2025 rent transactions
            System.out.println("\n4. Sample July 2025 rent_received:");
            String sql4 = """
                SELECT invoice_id, transaction_date, amount, description, lease_reference
                FROM unified_transactions
                WHERE transaction_type = 'rent_received'
                  AND transaction_date BETWEEN '2025-07-01' AND '2025-07-31'
                ORDER BY transaction_date, invoice_id
                LIMIT 10
            """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql4)) {
                while (rs.next()) {
                    System.out.printf("  %s | lease_id=%d (%s) | £%.2f | %s\n",
                        rs.getDate("transaction_date"),
                        rs.getInt("invoice_id"),
                        rs.getString("lease_reference"),
                        rs.getBigDecimal("amount"),
                        rs.getString("description"));
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
