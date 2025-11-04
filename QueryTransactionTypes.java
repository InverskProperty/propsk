import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class QueryTransactionTypes {
    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("MySQL driver not found, trying without explicit load...");
        }

        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "NXRedwMHejnfLPDdzZgnzOWpLreBpUsl";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {

            // Get all unique transaction types
            System.out.println("=" + "=".repeat(80));
            System.out.println("ALL TRANSACTION TYPES IN unified_transactions");
            System.out.println("=" + "=".repeat(80));

            String query1 =
                "SELECT DISTINCT transaction_type, COUNT(*) as count, " +
                "SUM(amount) as total_amount, " +
                "MIN(transaction_date) as earliest, " +
                "MAX(transaction_date) as latest " +
                "FROM unified_transactions " +
                "GROUP BY transaction_type " +
                "ORDER BY count DESC";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query1)) {
                while (rs.next()) {
                    String type = rs.getString("transaction_type");
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total_amount");
                    Date earliest = rs.getDate("earliest");
                    Date latest = rs.getDate("latest");

                    System.out.printf("%-35s | Count: %4d | Total: £%,12.2f | %s to %s%n",
                        type, count, total, earliest, latest);
                }
            }

            System.out.println("\n" + "=" + "=".repeat(80));
            System.out.println("LOOKING FOR COMMISSION/FEE TRANSACTIONS");
            System.out.println("=" + "=".repeat(80));

            String query2 =
                "SELECT transaction_type, category, description, amount, transaction_date " +
                "FROM unified_transactions " +
                "WHERE transaction_type LIKE '%commission%' " +
                "   OR transaction_type LIKE '%fee%' " +
                "   OR transaction_type LIKE '%agency%' " +
                "   OR category LIKE '%commission%' " +
                "   OR category LIKE '%fee%' " +
                "   OR category LIKE '%agency%' " +
                "   OR description LIKE '%commission%' " +
                "   OR description LIKE '%agency fee%' " +
                "LIMIT 20";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query2)) {
                int found = 0;
                while (rs.next()) {
                    found++;
                    String type = rs.getString("transaction_type");
                    String category = rs.getString("category");
                    String description = rs.getString("description");
                    BigDecimal amount = rs.getBigDecimal("amount");
                    Date date = rs.getDate("transaction_date");

                    System.out.printf("Type: %-30s | Cat: %-20s | £%,8.2f | %s%n",
                        type, category, amount, date);
                    System.out.printf("  Desc: %s%n", description);
                    System.out.println();
                }

                if (found == 0) {
                    System.out.println("NO COMMISSION/FEE TRANSACTIONS FOUND!");
                }
            }

            // Look for payment_to_agency or payment_to_beneficiary which might be commissions
            System.out.println("\n" + "=" + "=".repeat(80));
            System.out.println("PAYMENT_TO_AGENCY TRANSACTIONS (Likely Commissions)");
            System.out.println("=" + "=".repeat(80));

            String query3 =
                "SELECT COUNT(*) as count, SUM(ABS(amount)) as total " +
                "FROM unified_transactions " +
                "WHERE transaction_type = 'payment_to_agency'";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query3)) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");
                    System.out.printf("Count: %d | Total: £%,12.2f%n", count, total != null ? total : BigDecimal.ZERO);
                }
            }

            // Show sample payment_to_agency transactions
            String query4 =
                "SELECT transaction_type, description, amount, transaction_date " +
                "FROM unified_transactions " +
                "WHERE transaction_type = 'payment_to_agency' " +
                "LIMIT 10";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query4)) {
                while (rs.next()) {
                    String type = rs.getString("transaction_type");
                    String description = rs.getString("description");
                    BigDecimal amount = rs.getBigDecimal("amount");
                    Date date = rs.getDate("transaction_date");

                    System.out.printf("  £%,8.2f | %s | %s%n", amount, date, description);
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
