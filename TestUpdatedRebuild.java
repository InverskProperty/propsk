import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TestUpdatedRebuild {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        System.out.println("=== Testing Updated Rebuild with Auto-Linked Property Expenses ===\n");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Step 1: Clear unified_transactions
            System.out.println("Step 1: Clearing unified_transactions...");
            String deleteSql = "DELETE FROM unified_transactions";
            try (Statement stmt = conn.createStatement()) {
                int deleted = stmt.executeUpdate(deleteSql);
                System.out.println("  Deleted " + deleted + " existing records\n");
            }
            
            // Step 2: Insert with updated SQL (auto-link property expenses to active leases)
            System.out.println("Step 2: Inserting with auto-linked property expenses...");
            String batchId = "MANUAL-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            
            String insertSql = """
                INSERT INTO unified_transactions (
                    source_system, source_table, source_record_id,
                    transaction_date, amount, description, category,
                    invoice_id, property_id, customer_id,
                    lease_reference, lease_start_date, lease_end_date, rent_amount_at_transaction,
                    property_name,
                    transaction_type, flow_direction,
                    rebuilt_at, rebuild_batch_id
                )
                SELECT
                    'HISTORICAL' as source_system,
                    'historical_transactions' as source_table,
                    ht.id as source_record_id,
                    ht.transaction_date,
                    ht.amount,
                    ht.description,
                    ht.category,
                    COALESCE(ht.invoice_id, active_lease.id) as invoice_id,
                    ht.property_id,
                    ht.customer_id,
                    COALESCE(i.lease_reference, active_lease.lease_reference) as lease_reference,
                    ht.lease_start_date,
                    ht.lease_end_date,
                    ht.rent_amount_at_transaction,
                    p.property_name,
                    CASE
                        WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' THEN 'rent_received'
                        WHEN ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
                            OR ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%' THEN 'expense'
                        WHEN ht.category = 'owner_payment' THEN 'payment_to_beneficiary'
                        ELSE 'other'
                    END as transaction_type,
                    CASE
                        WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' THEN 'INCOMING'
                        WHEN ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
                            OR ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%' THEN 'OUTGOING'
                        WHEN ht.category = 'owner_payment' THEN 'OUTGOING'
                        ELSE 'OUTGOING'
                    END as flow_direction,
                    NOW() as rebuilt_at,
                    ? as rebuild_batch_id
                FROM historical_transactions ht
                LEFT JOIN properties p ON ht.property_id = p.id
                LEFT JOIN invoices i ON ht.invoice_id = i.id
                LEFT JOIN invoices active_lease ON ht.property_id = active_lease.property_id
                    AND ht.transaction_date >= active_lease.start_date
                    AND (active_lease.end_date IS NULL OR ht.transaction_date <= active_lease.end_date)
                    AND ht.invoice_id IS NULL
                WHERE ht.invoice_id IS NOT NULL OR active_lease.id IS NOT NULL
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, batchId);
                int inserted = stmt.executeUpdate();
                System.out.println("  Inserted " + inserted + " records\n");
            }
            
            // Step 3: Verify transaction types
            System.out.println("Step 3: Transaction type breakdown:");
            String sql1 = "SELECT transaction_type, COUNT(*) as count, SUM(amount) as total FROM unified_transactions GROUP BY transaction_type ORDER BY count DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql1)) {
                while (rs.next()) {
                    System.out.printf("  %s: %d records (£%.2f)\n",
                        rs.getString("transaction_type"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total"));
                }
            }
            
            // Step 4: Check July 2025 expenses
            System.out.println("\n=== July 2025 Expenses (Linked to Leases) ===");
            String sql2 = """
                SELECT ut.transaction_date, ut.amount, ut.category, ut.description, 
                       ut.invoice_id, ut.lease_reference, p.property_name
                FROM unified_transactions ut
                LEFT JOIN properties p ON ut.property_id = p.id
                WHERE ut.transaction_type = 'expense' 
                  AND ut.transaction_date BETWEEN '2025-07-01' AND '2025-07-31'
                ORDER BY ut.transaction_date
            """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql2)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("%s | £%.2f | %s | %s | lease=%s (%s) | %s\n",
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("category"),
                        rs.getString("property_name"),
                        rs.getInt("invoice_id"),
                        rs.getString("lease_reference"),
                        rs.getString("description"));
                }
                System.out.println("\nTotal July 2025 expenses: " + count);
            }
            
            System.out.println("\n✅ Updated rebuild complete!");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
