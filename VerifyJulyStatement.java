import java.sql.*;
import java.time.LocalDate;
import java.math.BigDecimal;

public class VerifyJulyStatement {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        LocalDate julyStart = LocalDate.of(2025, 7, 1);
        LocalDate julyEnd = LocalDate.of(2025, 7, 31);

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=======================================================");
            System.out.println("JULY 2025 STATEMENT VERIFICATION");
            System.out.println("=======================================================\n");

            // Test 1: RENT_RECEIVED data (should be INCOMING only)
            System.out.println("TEST 1: RENT_RECEIVED Sheet Data (INCOMING only)");
            System.out.println("--------------------------------------------------");

            String rentQuery =
                "SELECT " +
                "  i.id as invoice_id, " +
                "  i.lease_reference, " +
                "  p.property_name, " +
                "  DATE('2025-07-01') as month_start, " +
                "  DATE('2025-07-31') as month_end, " +
                "  SUM(ut.amount) as total_received, " +
                "  COUNT(ut.id) as payment_count " +
                "FROM invoices i " +
                "LEFT JOIN properties p ON i.property_id = p.id " +
                "LEFT JOIN unified_transactions ut ON " +
                "  ut.invoice_id = i.id " +
                "  AND ut.transaction_date BETWEEN ? AND ? " +
                "  AND ut.flow_direction = 'INCOMING' " +
                "WHERE i.is_active = 1 " +
                "GROUP BY i.id, i.lease_reference, p.property_name " +
                "HAVING total_received > 0 " +
                "ORDER BY p.property_name " +
                "LIMIT 5";

            try (PreparedStatement ps = conn.prepareStatement(rentQuery)) {
                ps.setDate(1, Date.valueOf(julyStart));
                ps.setDate(2, Date.valueOf(julyEnd));
                ResultSet rs = ps.executeQuery();

                int rowCount = 0;
                BigDecimal grandTotal = BigDecimal.ZERO;

                while (rs.next()) {
                    rowCount++;
                    String leaseRef = rs.getString("lease_reference");
                    String property = rs.getString("property_name");
                    BigDecimal total = rs.getBigDecimal("total_received");
                    int paymentCount = rs.getInt("payment_count");

                    System.out.println(String.format("%-25s | %-40s | £%-10.2f | %d payment(s)",
                        leaseRef, property, total, paymentCount));

                    grandTotal = grandTotal.add(total);
                }

                System.out.println("\nTotal properties with rent in July: " + rowCount);
                System.out.println("Total rent received (INCOMING only): £" + String.format("%.2f", grandTotal));
            }

            System.out.println("\n");

            // Test 2: Payment breakdown for a specific lease
            System.out.println("TEST 2: Payment Breakdown for LEASE-KH-F-2024");
            System.out.println("----------------------------------------------");

            String paymentQuery =
                "SELECT " +
                "  transaction_date, " +
                "  amount, " +
                "  description, " +
                "  flow_direction " +
                "FROM unified_transactions " +
                "WHERE lease_reference = 'LEASE-KH-F-2024' " +
                "  AND transaction_date BETWEEN ? AND ? " +
                "  AND flow_direction = 'INCOMING' " +
                "ORDER BY transaction_date";

            try (PreparedStatement ps = conn.prepareStatement(paymentQuery)) {
                ps.setDate(1, Date.valueOf(julyStart));
                ps.setDate(2, Date.valueOf(julyEnd));
                ResultSet rs = ps.executeQuery();

                int count = 0;
                BigDecimal total = BigDecimal.ZERO;

                while (rs.next()) {
                    count++;
                    Date date = rs.getDate("transaction_date");
                    BigDecimal amount = rs.getBigDecimal("amount");
                    String desc = rs.getString("description");
                    String flow = rs.getString("flow_direction");

                    System.out.println(String.format("Payment %d: %s | £%-10.2f | %s | %s",
                        count, date, amount, flow, desc));

                    total = total.add(amount);
                }

                if (count == 0) {
                    System.out.println("  No INCOMING payments for this lease in July 2025");
                } else {
                    System.out.println("\nTotal INCOMING for this lease: £" + String.format("%.2f", total));
                }
            }

            System.out.println("\n");

            // Test 3: Check for any OUTGOING that might be incorrectly included
            System.out.println("TEST 3: Verify No OUTGOING in RENT_RECEIVED Query");
            System.out.println("--------------------------------------------------");

            String outgoingCheck =
                "SELECT COUNT(*) as outgoing_count, SUM(amount) as outgoing_total " +
                "FROM unified_transactions " +
                "WHERE transaction_date BETWEEN ? AND ? " +
                "  AND flow_direction = 'OUTGOING'";

            try (PreparedStatement ps = conn.prepareStatement(outgoingCheck)) {
                ps.setDate(1, Date.valueOf(julyStart));
                ps.setDate(2, Date.valueOf(julyEnd));
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int count = rs.getInt("outgoing_count");
                    BigDecimal total = rs.getBigDecimal("outgoing_total");

                    System.out.println("OUTGOING transactions in July: " + count);
                    System.out.println("OUTGOING total: £" + (total != null ? String.format("%.2f", total) : "0.00"));
                    System.out.println("\n✓ These should NOT appear in RENT_RECEIVED sheet");
                }
            }

            System.out.println("\n=======================================================");
            System.out.println("VERIFICATION COMPLETE");
            System.out.println("=======================================================");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
