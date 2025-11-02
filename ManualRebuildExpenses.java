import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ManualRebuildExpenses {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        System.out.println("=== Manual Rebuild: Importing Expenses from Historical Transactions ===\n");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Step 1: Clear unified_transactions
            System.out.println("Step 1: Clearing unified_transactions table...");
            String deleteSql = "DELETE FROM unified_transactions";
            try (Statement stmt = conn.createStatement()) {
                int deleted = stmt.executeUpdate(deleteSql);
                System.out.println("  Deleted " + deleted + " existing records\n");
            }
            
            // Step 2: Insert from historical_transactions with NEW expense logic
            System.out.println("Step 2: Inserting from historical_transactions (with expense support)...");
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
                    ht.invoice_id,
                    ht.property_id,
                    ht.customer_id,
                    i.lease_reference,
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
                WHERE ht.invoice_id IS NOT NULL
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, batchId);
                int inserted = stmt.executeUpdate();
                System.out.println("  Inserted " + inserted + " records from historical_transactions\n");
            }
            
            // Step 3: Verify expense import
            System.out.println("Step 3: Verifying expense import...");
            String verifySql = "SELECT transaction_type, COUNT(*) as count, SUM(amount) as total FROM unified_transactions GROUP BY transaction_type ORDER BY count DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(verifySql)) {
                System.out.println("\nTransaction Types:");
                while (rs.next()) {
                    System.out.printf("  %s: %d records (£%.2f)\n",
                        rs.getString("transaction_type"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total"));
                }
            }
            
            // Step 4: Check July 2025 expenses
            System.out.println("\n=== Expenses in July 2025 ===");
            String julySql = "SELECT transaction_date, amount, category, description, invoice_id FROM unified_transactions WHERE transaction_type = 'expense' AND transaction_date BETWEEN '2025-07-01' AND '2025-07-31' ORDER BY transaction_date";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(julySql)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("%s | £%.2f | %s | invoice_id=%s | %s\n",
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("category"),
                        rs.getObject("invoice_id"),
                        rs.getString("description"));
                }
                if (count == 0) {
                    System.out.println("No expenses found in July 2025");
                } else {
                    System.out.println("\nTotal July 2025 expenses: " + count);
                }
            }
            
            System.out.println("\n✅ Rebuild complete! Batch ID: " + batchId);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
