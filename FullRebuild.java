import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FullRebuild {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        System.out.println("=== Full Rebuild: Historical + PayProp ===\n");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {

            String batchId = "FULL-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

            // Step 1: Clear unified_transactions
            System.out.println("Step 1: Clearing unified_transactions...");
            try (Statement stmt = conn.createStatement()) {
                int deleted = stmt.executeUpdate("DELETE FROM unified_transactions");
                System.out.println("  Deleted " + deleted + " records\n");
            }

            // Step 2: Insert from historical_transactions
            System.out.println("Step 2: Inserting from historical_transactions...");
            String sql1 = "INSERT INTO unified_transactions (source_system, source_table, source_record_id, transaction_date, amount, description, category, invoice_id, property_id, customer_id, lease_reference, lease_start_date, lease_end_date, rent_amount_at_transaction, property_name, transaction_type, flow_direction, rebuilt_at, rebuild_batch_id) SELECT 'HISTORICAL', 'historical_transactions', ht.id, ht.transaction_date, CASE WHEN ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee') OR ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%' THEN ABS(ht.amount) ELSE ht.amount END, ht.description, ht.category, COALESCE(ht.invoice_id, active_lease.id), ht.property_id, ht.customer_id, COALESCE(i.lease_reference, active_lease.lease_reference), ht.lease_start_date, ht.lease_end_date, ht.rent_amount_at_transaction, p.property_name, CASE WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' THEN 'rent_received' WHEN ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee') OR ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%' THEN 'expense' WHEN ht.category = 'owner_payment' THEN 'payment_to_beneficiary' ELSE 'other' END, CASE WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' THEN 'INCOMING' WHEN ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee') OR ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%' THEN 'OUTGOING' WHEN ht.category = 'owner_payment' THEN 'OUTGOING' ELSE 'OUTGOING' END, NOW(), ? FROM historical_transactions ht LEFT JOIN properties p ON ht.property_id = p.id LEFT JOIN invoices i ON ht.invoice_id = i.id LEFT JOIN invoices active_lease ON ht.property_id = active_lease.property_id AND ht.transaction_date >= active_lease.start_date AND (active_lease.end_date IS NULL OR ht.transaction_date <= active_lease.end_date) AND ht.invoice_id IS NULL WHERE ht.invoice_id IS NOT NULL OR active_lease.id IS NOT NULL";

            try (PreparedStatement stmt = conn.prepareStatement(sql1)) {
                stmt.setString(1, batchId);
                int inserted = stmt.executeUpdate();
                System.out.println("  Inserted " + inserted + " historical records\n");
            }

            // Step 3: Insert from financial_transactions
            System.out.println("Step 3: Inserting from financial_transactions (PayProp)...");
            String sql2 = "INSERT INTO unified_transactions (source_system, source_table, source_record_id, transaction_date, amount, description, category, invoice_id, property_id, customer_id, lease_reference, property_name, payprop_transaction_id, payprop_data_source, transaction_type, flow_direction, rebuilt_at, rebuild_batch_id) SELECT 'PAYPROP', 'financial_transactions', ft.id, ft.transaction_date, ft.amount, ft.description, ft.category_name, ft.invoice_id, CASE WHEN ft.data_source = 'INCOMING_PAYMENT' THEN CAST(ft.property_id AS UNSIGNED) ELSE p.id END, CASE WHEN ft.data_source = 'INCOMING_PAYMENT' THEN CAST(ft.tenant_id AS UNSIGNED) ELSE NULL END, i.lease_reference, ft.property_name, ft.pay_prop_transaction_id, ft.data_source, ft.transaction_type, CASE WHEN ft.data_source = 'INCOMING_PAYMENT' THEN 'INCOMING' WHEN ft.data_source = 'BATCH_PAYMENT' OR ft.data_source = 'COMMISSION_PAYMENT' THEN 'OUTGOING' ELSE 'OUTGOING' END, NOW(), ? FROM financial_transactions ft LEFT JOIN invoices i ON ft.invoice_id = i.id LEFT JOIN properties p ON i.property_id = p.id WHERE ft.invoice_id IS NOT NULL";

            try (PreparedStatement stmt = conn.prepareStatement(sql2)) {
                stmt.setString(1, batchId);
                int inserted = stmt.executeUpdate();
                System.out.println("  Inserted " + inserted + " PayProp records\n");
            }

            // Step 4: Verify totals
            System.out.println("Step 4: Verification:");
            String sql3 = "SELECT transaction_type, COUNT(*) as count, SUM(amount) as total FROM unified_transactions GROUP BY transaction_type ORDER BY count DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql3)) {
                while (rs.next()) {
                    System.out.printf("  %s: %d records (£%.2f)\n",
                        rs.getString("transaction_type"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total"));
                }
            }

            // Step 5: Check July rent
            System.out.println("\n=== July 2025 Rent Received ===");
            String sql4 = "SELECT source_system, COUNT(*) as count, SUM(amount) as total FROM unified_transactions WHERE transaction_type = 'rent_received' AND transaction_date BETWEEN '2025-07-01' AND '2025-07-31' GROUP BY source_system";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql4)) {
                while (rs.next()) {
                    System.out.printf("  %s: %d payments (£%.2f)\n",
                        rs.getString("source_system"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total"));
                }
            }

            System.out.println("\n✅ Full rebuild complete!");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
