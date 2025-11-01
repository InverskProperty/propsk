import java.sql.*;
import java.math.BigDecimal;

public class check_july_incoming {
    public static void main(String[] args) {
        String url = System.getenv("DATABASE_URL");
        String user = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");

        // Fallback to direct values if environment variables not set
        if (url == null || url.isEmpty()) {
            url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
            user = "root";
            password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";
        }

        try (Connection conn = DriverManager.getConnection(url, user, password)) {

            System.out.println("Connected to database successfully!\n");

            // Query 1: Summary of July incoming payments
            System.out.println("=".repeat(120));
            System.out.println("JULY 2025 INCOMING PAYMENTS SUMMARY");
            System.out.println("=".repeat(120));

            String query1 = """
                SELECT
                    COUNT(*) as count,
                    SUM(amount) as total
                FROM unified_transactions
                WHERE transaction_date BETWEEN '2025-07-01' AND '2025-07-31'
                  AND payprop_data_source = 'INCOMING_PAYMENT'
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {

                System.out.printf("%-10s %-15s%n", "COUNT", "TOTAL");
                System.out.println("-".repeat(120));

                if (rs.next()) {
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");
                    System.out.printf("%-10d £%-14.2f%n", count, total);
                }
            }

            // Query 2: Detailed breakdown by lease
            System.out.println("\n" + "=".repeat(120));
            System.out.println("JULY 2025 INCOMING PAYMENTS BY LEASE");
            System.out.println("=".repeat(120));

            String query2 = """
                SELECT
                    invoice_id,
                    lease_reference,
                    property_name,
                    COUNT(*) as payment_count,
                    SUM(amount) as total_received
                FROM unified_transactions
                WHERE transaction_date BETWEEN '2025-07-01' AND '2025-07-31'
                  AND payprop_data_source = 'INCOMING_PAYMENT'
                GROUP BY invoice_id, lease_reference, property_name
                ORDER BY property_name
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {

                System.out.printf("%-10s %-30s %-40s %-10s %-15s%n",
                    "LEASE ID", "LEASE REF", "PROPERTY", "PAYMENTS", "TOTAL");
                System.out.println("-".repeat(120));

                BigDecimal grandTotal = BigDecimal.ZERO;
                int totalLeases = 0;

                while (rs.next()) {
                    Object leaseIdObj = rs.getObject("invoice_id");
                    String leaseId = (leaseIdObj != null) ? leaseIdObj.toString() : "NULL";
                    String leaseRef = rs.getString("lease_reference");
                    if (leaseRef == null) leaseRef = "NULL";
                    String propertyName = rs.getString("property_name");
                    if (propertyName == null) propertyName = "NULL";
                    int paymentCount = rs.getInt("payment_count");
                    BigDecimal total = rs.getBigDecimal("total_received");
                    if (total == null) total = BigDecimal.ZERO;

                    grandTotal = grandTotal.add(total);
                    totalLeases++;

                    // Truncate long strings
                    if (leaseRef.length() > 30) {
                        leaseRef = leaseRef.substring(0, 27) + "...";
                    }
                    if (propertyName.length() > 40) {
                        propertyName = propertyName.substring(0, 37) + "...";
                    }

                    System.out.printf("%-10s %-30s %-40s %-10d £%-14.2f%n",
                        leaseId, leaseRef, propertyName, paymentCount, total);
                }

                System.out.println("-".repeat(120));
                System.out.printf("TOTAL LEASES WITH PAYMENTS: %d%64s £%-14.2f%n",
                    totalLeases, "", grandTotal);
            }

            // Query 3: All individual transactions
            System.out.println("\n" + "=".repeat(120));
            System.out.println("ALL JULY 2025 INCOMING PAYMENT TRANSACTIONS");
            System.out.println("=".repeat(120));

            String query3 = """
                SELECT
                    id,
                    transaction_date,
                    amount,
                    lease_reference,
                    property_name,
                    payprop_transaction_id
                FROM unified_transactions
                WHERE transaction_date BETWEEN '2025-07-01' AND '2025-07-31'
                  AND payprop_data_source = 'INCOMING_PAYMENT'
                ORDER BY property_name, transaction_date
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {

                System.out.printf("%-8s %-12s %-12s %-30s %-40s%n",
                    "ID", "DATE", "AMOUNT", "LEASE REF", "PROPERTY");
                System.out.println("-".repeat(120));

                while (rs.next()) {
                    Long id = rs.getLong("id");
                    Date date = rs.getDate("transaction_date");
                    BigDecimal amount = rs.getBigDecimal("amount");
                    String leaseRef = rs.getString("lease_reference");
                    if (leaseRef == null) leaseRef = "NULL";
                    String propertyName = rs.getString("property_name");
                    if (propertyName == null) propertyName = "NULL";

                    // Truncate long strings
                    if (leaseRef.length() > 30) {
                        leaseRef = leaseRef.substring(0, 27) + "...";
                    }
                    if (propertyName.length() > 40) {
                        propertyName = propertyName.substring(0, 37) + "...";
                    }

                    System.out.printf("%-8d %-12s £%-11.2f %-30s %-40s%n",
                        id, date, amount, leaseRef, propertyName);
                }
            }

            System.out.println("\n" + "=".repeat(120));
            System.out.println("Query completed successfully!");
            System.out.println("=".repeat(120));

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
