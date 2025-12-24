import java.sql.*;

public class QueryBatch {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";
        String batchId = "HIST-2025-04-1533";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== BATCH DETAILS: " + batchId + " ===\n");

            // Query payment_batches table
            String batchQuery = "SELECT id, batch_id, payment_date, status, created_at FROM payment_batches WHERE batch_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(batchQuery)) {
                ps.setString(1, batchId);
                ResultSet rs = ps.executeQuery();
                System.out.println("PAYMENT BATCH INFO:");
                System.out.println("==================");
                if (rs.next()) {
                    System.out.println("ID: " + rs.getLong("id"));
                    System.out.println("Batch ID: " + rs.getString("batch_id"));
                    System.out.println("Payment Date: " + rs.getDate("payment_date"));
                    System.out.println("Status: " + rs.getString("status"));
                    System.out.println("Created At: " + rs.getTimestamp("created_at"));
                } else {
                    System.out.println("  No batch record found");
                }
            }

            System.out.println("\n");

            // Query unified_allocations for this batch
            String allocQuery = """
                SELECT ua.id, ua.allocation_type, ua.amount, ua.paid_date,
                       ua.beneficiary_id, ua.beneficiary_name, ua.property_id,
                       ua.invoice_id, ua.unified_transaction_id, ua.historical_transaction_id,
                       p.property_name,
                       i.lease_reference
                FROM unified_allocations ua
                LEFT JOIN properties p ON ua.property_id = p.id
                LEFT JOIN invoices i ON ua.invoice_id = i.id
                WHERE ua.payment_batch_id = ?
                ORDER BY ua.allocation_type, ua.amount DESC
                """;

            try (PreparedStatement ps = conn.prepareStatement(allocQuery)) {
                ps.setString(1, batchId);
                ResultSet rs = ps.executeQuery();

                System.out.println("ALLOCATIONS IN THIS BATCH:");
                System.out.println("==========================");
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\nAllocation #" + count);
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Type: " + rs.getString("allocation_type"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Paid Date: " + rs.getDate("paid_date"));
                    System.out.println("  Beneficiary ID: " + rs.getLong("beneficiary_id"));
                    System.out.println("  Beneficiary Name: " + rs.getString("beneficiary_name"));
                    System.out.println("  Property ID: " + rs.getLong("property_id"));
                    System.out.println("  Property Name: " + rs.getString("property_name"));
                    System.out.println("  Lease Reference: " + rs.getString("lease_reference"));
                    System.out.println("  Unified Txn ID: " + rs.getObject("unified_transaction_id"));
                    System.out.println("  Historical Txn ID: " + rs.getObject("historical_transaction_id"));
                }
                System.out.println("\nTotal allocations: " + count);
            }

            System.out.println("\n");

            // Query the source transactions
            String txnQuery = """
                SELECT ut.id, ut.transaction_date, ut.amount, ut.description, 
                       ut.category, ut.flow_direction, ut.payprop_data_source,
                       ut.invoice_id, i.lease_reference, p.property_name
                FROM unified_transactions ut
                LEFT JOIN invoices i ON ut.invoice_id = i.id
                LEFT JOIN properties p ON i.property_id = p.id
                WHERE ut.id IN (
                    SELECT unified_transaction_id FROM unified_allocations 
                    WHERE payment_batch_id = ? AND unified_transaction_id IS NOT NULL
                )
                ORDER BY ut.transaction_date
                """;

            try (PreparedStatement ps = conn.prepareStatement(txnQuery)) {
                ps.setString(1, batchId);
                ResultSet rs = ps.executeQuery();

                System.out.println("SOURCE TRANSACTIONS:");
                System.out.println("====================");
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\nTransaction #" + count);
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println("  Category: " + rs.getString("category"));
                    System.out.println("  Flow: " + rs.getString("flow_direction"));
                    System.out.println("  Data Source: " + rs.getString("payprop_data_source"));
                    System.out.println("  Lease: " + rs.getString("lease_reference"));
                    System.out.println("  Property: " + rs.getString("property_name"));
                }
                if (count == 0) {
                    System.out.println("  No unified transactions found");
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
