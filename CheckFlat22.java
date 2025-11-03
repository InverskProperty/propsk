import java.sql.*;

public class CheckFlat22 {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String username = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {

            // First, get lease details for Flat 22
            System.out.println("=== Flat 22 Lease Details ===");
            String leaseQuery = "SELECT id, lease_reference " +
                "FROM invoices " +
                "WHERE lease_reference LIKE '%F22%'";

            Long invoiceId = null;
            String leaseRef = null;

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(leaseQuery)) {
                while (rs.next()) {
                    invoiceId = rs.getLong("id");
                    leaseRef = rs.getString("lease_reference");
                    System.out.println("\nLease Found:");
                    System.out.println("  Invoice ID: " + invoiceId);
                    System.out.println("  Lease Reference: " + leaseRef);
                }
            }

            if (invoiceId == null) {
                System.out.println("ERROR: Could not find Flat 22 lease!");
                return;
            }

            // Get ALL INCOMING payments for Flat 22
            System.out.println("\n=== ALL INCOMING Payments for Flat 22 ===");
            String paymentsQuery = "SELECT ut.id, ut.transaction_date, ut.amount, ut.description, " +
                "ut.source_system, ut.source_table, ut.source_record_id, " +
                "ut.payprop_data_source, ut.transaction_type, ut.category " +
                "FROM unified_transactions ut " +
                "WHERE ut.invoice_id = ? " +
                "AND ut.flow_direction = 'INCOMING' " +
                "ORDER BY ut.transaction_date, ut.id";

            try (PreparedStatement pstmt = conn.prepareStatement(paymentsQuery)) {
                pstmt.setLong(1, invoiceId);
                ResultSet rs = pstmt.executeQuery();

                int count = 0;
                String lastDate = null;
                int dateCount = 0;
                double totalAmount = 0;

                while (rs.next()) {
                    count++;
                    String currentDate = rs.getDate("transaction_date").toString();
                    double amount = rs.getDouble("amount");
                    totalAmount += amount;

                    if (currentDate.equals(lastDate)) {
                        dateCount++;
                        System.out.println("\n*** WARNING: DUPLICATE DETECTED - Payment " + dateCount + " on same date! ***");
                    } else {
                        dateCount = 1;
                        lastDate = currentDate;
                    }

                    System.out.println("\nPayment " + count + ":");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Source System: " + rs.getString("source_system"));
                    System.out.println("  Source Table: " + rs.getString("source_table"));
                    System.out.println("  Source Record ID: " + rs.getLong("source_record_id"));
                    System.out.println("  PayProp Data Source: " + rs.getString("payprop_data_source"));
                    System.out.println("  Transaction Type: " + rs.getString("transaction_type"));
                    System.out.println("  Category: " + rs.getString("category"));
                    System.out.println("  Description: " + rs.getString("description"));
                }

                System.out.println("\n=== SUMMARY ===");
                System.out.println("Total INCOMING payments: " + count);
                System.out.println("Total amount: £" + String.format("%.2f", totalAmount));
            }

            // Group by date to see duplicates clearly
            System.out.println("\n=== Payments Grouped by Date ===");
            String groupQuery = "SELECT ut.transaction_date, COUNT(*) as payment_count, " +
                "SUM(ut.amount) as total_amount, " +
                "GROUP_CONCAT(ut.source_system ORDER BY ut.id) as sources, " +
                "GROUP_CONCAT(ut.payprop_data_source ORDER BY ut.id) as data_sources, " +
                "GROUP_CONCAT(ut.id ORDER BY ut.id) as transaction_ids " +
                "FROM unified_transactions ut " +
                "WHERE ut.invoice_id = ? " +
                "AND ut.flow_direction = 'INCOMING' " +
                "GROUP BY ut.transaction_date " +
                "ORDER BY ut.transaction_date";

            try (PreparedStatement pstmt = conn.prepareStatement(groupQuery)) {
                pstmt.setLong(1, invoiceId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    int paymentCount = rs.getInt("payment_count");
                    System.out.println("\nDate: " + rs.getDate("transaction_date"));
                    System.out.println("  Payment Count: " + paymentCount);
                    System.out.println("  Total Amount: £" + rs.getBigDecimal("total_amount"));
                    System.out.println("  Sources: " + rs.getString("sources"));
                    System.out.println("  Data Sources: " + rs.getString("data_sources"));
                    System.out.println("  Transaction IDs: " + rs.getString("transaction_ids"));

                    if (paymentCount > 1) {
                        System.out.println("  *** DUPLICATE: Multiple payments on same date! ***");
                    }
                }
            }

            // Check source distribution
            System.out.println("\n=== Source System Distribution ===");
            String sourceQuery = "SELECT ut.source_system, ut.payprop_data_source, COUNT(*) as count, " +
                "SUM(ut.amount) as total_amount, " +
                "MIN(ut.transaction_date) as earliest, MAX(ut.transaction_date) as latest " +
                "FROM unified_transactions ut " +
                "WHERE ut.invoice_id = ? " +
                "AND ut.flow_direction = 'INCOMING' " +
                "GROUP BY ut.source_system, ut.payprop_data_source";

            try (PreparedStatement pstmt = conn.prepareStatement(sourceQuery)) {
                pstmt.setLong(1, invoiceId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    System.out.println("\nSource: " + rs.getString("source_system"));
                    System.out.println("  PayProp Data Source: " + rs.getString("payprop_data_source"));
                    System.out.println("  Count: " + rs.getInt("count"));
                    System.out.println("  Total Amount: £" + rs.getBigDecimal("total_amount"));
                    System.out.println("  Date Range: " + rs.getDate("earliest") + " to " + rs.getDate("latest"));
                }
            }

            // Check for ANY ICDN_ACTUAL that might have been missed
            System.out.println("\n=== Checking for ICDN_ACTUAL Transactions ===");
            String icdnQuery = "SELECT ft.id, ft.transaction_date, ft.amount, ft.description, " +
                "ft.data_source, ft.transaction_type " +
                "FROM financial_transactions ft " +
                "WHERE ft.invoice_id = ? " +
                "AND ft.data_source = 'ICDN_ACTUAL'";

            try (PreparedStatement pstmt = conn.prepareStatement(icdnQuery)) {
                pstmt.setLong(1, invoiceId);
                ResultSet rs = pstmt.executeQuery();

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\nICDN_ACTUAL Transaction " + count + ":");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println("  Transaction Type: " + rs.getString("transaction_type"));
                }

                if (count == 0) {
                    System.out.println("No ICDN_ACTUAL transactions found in financial_transactions (this is good!)");
                } else {
                    System.out.println("\n*** WARNING: Found " + count + " ICDN_ACTUAL invoices that haven't been imported yet ***");
                }
            }

            // Check ALL transactions (including OUTGOING) to see full picture
            System.out.println("\n=== ALL Transactions (INCOMING + OUTGOING) ===");
            String allQuery = "SELECT ut.flow_direction, COUNT(*) as count, " +
                "SUM(ut.amount) as total_amount " +
                "FROM unified_transactions ut " +
                "WHERE ut.invoice_id = ? " +
                "GROUP BY ut.flow_direction";

            try (PreparedStatement pstmt = conn.prepareStatement(allQuery)) {
                pstmt.setLong(1, invoiceId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    System.out.println("\nFlow Direction: " + rs.getString("flow_direction"));
                    System.out.println("  Count: " + rs.getInt("count"));
                    System.out.println("  Total Amount: £" + rs.getBigDecimal("total_amount"));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
