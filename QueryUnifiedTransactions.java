import java.sql.*;
import java.math.BigDecimal;

public class QueryUnifiedTransactions {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "NXRedwMHejnfLPDdzZgnzOWpLreBpUsl";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== INCOMING PAYMENTS - LAST 12 MONTHS ===\n");

            String query = "SELECT transaction_type, COUNT(*) as count, SUM(amount) as total " +
                          "FROM unified_transactions " +
                          "WHERE transaction_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH) " +
                          "  AND transaction_type IN ('incoming_payment', 'tenant_payment', 'payment', 'invoice') " +
                          "GROUP BY transaction_type " +
                          "ORDER BY total DESC";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                BigDecimal grandTotal = BigDecimal.ZERO;

                while (rs.next()) {
                    String type = rs.getString("transaction_type");
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");

                    System.out.println(String.format("%-20s | Count: %3d | Total: £%,.2f",
                        type, count, total));

                    if (total != null) {
                        grandTotal = grandTotal.add(total);
                    }
                }

                System.out.println("\n" + "=".repeat(60));
                System.out.println(String.format("GRAND TOTAL: £%,.2f", grandTotal));
            }

        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
