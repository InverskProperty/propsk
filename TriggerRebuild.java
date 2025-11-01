import java.sql.*;
import java.math.BigDecimal;

public class TriggerRebuild {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        try (Connection conn = DriverManager.getConnection(url, "root", "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW")) {
            
            System.out.println("🔄 Starting unified_transactions rebuild...\n");
            
            // Step 1: Truncate
            System.out.println("Step 1: Truncating unified_transactions...");
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("TRUNCATE TABLE unified_transactions");
            }
            System.out.println("  ✓ Table truncated\n");
            
            // Step 2: Insert from historical_transactions
            System.out.println("Step 2: Inserting from historical_transactions...");
            String historicalSql = """
                INSERT INTO unified_transactions (
                    source_system, source_table, source_record_id,
                    transaction_date, amount, description, category,
                    invoice_id, property_id, customer_id,
                    lease_reference, lease_start_date, lease_end_date,
                    rent_amount_at_transaction, property_name,
                    transaction_type, flow_direction,
                    rebuilt_at, rebuild_batch_id
                )
                SELECT
                    'HISTORICAL', 'historical_transactions', ht.id,
                    ht.transaction_date, ht.amount, ht.description, ht.category,
                    ht.invoice_id, ht.property_id, ht.customer_id,
                    i.lease_reference, ht.lease_start_date, ht.lease_end_date,
                    ht.rent_amount_at_transaction, p.property_name,
                    CASE
                        WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' THEN 'rent_received'
                        WHEN ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%' THEN 'expense'
                        ELSE 'other'
                    END,
                    CASE
                        WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' THEN 'INCOMING'
                        ELSE 'OUTGOING'
                    END,
                    NOW(), 'MANUAL-REBUILD'
                FROM historical_transactions ht
                LEFT JOIN properties p ON ht.property_id = p.id
                LEFT JOIN invoices i ON ht.invoice_id = i.id
                WHERE ht.invoice_id IS NOT NULL
            """;
            
            int historicalCount;
            try (Statement stmt = conn.createStatement()) {
                historicalCount = stmt.executeUpdate(historicalSql);
            }
            System.out.println("  ✓ Inserted " + historicalCount + " historical records\n");
            
            // Step 3: Insert from financial_transactions
            System.out.println("Step 3: Inserting from financial_transactions...");
            String paypropSql = """
                INSERT INTO unified_transactions (
                    source_system, source_table, source_record_id,
                    transaction_date, amount, description, category,
                    invoice_id, property_id, customer_id,
                    lease_reference, property_name,
                    payprop_transaction_id, payprop_data_source,
                    transaction_type, flow_direction,
                    rebuilt_at, rebuild_batch_id
                )
                SELECT
                    'PAYPROP', 'financial_transactions', ft.id,
                    ft.transaction_date, ft.amount, ft.description, ft.category_name,
                    ft.invoice_id,
                    CASE
                        WHEN ft.data_source = 'INCOMING_PAYMENT' THEN CAST(ft.property_id AS UNSIGNED)
                        ELSE p.id
                    END,
                    CASE
                        WHEN ft.data_source = 'INCOMING_PAYMENT' THEN CAST(ft.tenant_id AS UNSIGNED)
                        ELSE NULL
                    END,
                    i.lease_reference, ft.property_name,
                    ft.pay_prop_transaction_id, ft.data_source,
                    ft.transaction_type,
                    CASE
                        WHEN ft.data_source = 'INCOMING_PAYMENT' THEN 'INCOMING'
                        WHEN ft.data_source = 'BATCH_PAYMENT' OR ft.data_source = 'COMMISSION_PAYMENT' THEN 'OUTGOING'
                        ELSE 'OUTGOING'
                    END,
                    NOW(), 'MANUAL-REBUILD'
                FROM financial_transactions ft
                LEFT JOIN properties p ON ft.property_id = p.payprop_id
                LEFT JOIN invoices i ON ft.invoice_id = i.id
                WHERE (ft.invoice_id IS NOT NULL OR ft.data_source = 'INCOMING_PAYMENT')
                  AND ft.data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV', 'ICDN_ACTUAL')
            """;
            
            int paypropCount;
            try (Statement stmt = conn.createStatement()) {
                paypropCount = stmt.executeUpdate(paypropSql);
            }
            System.out.println("  ✓ Inserted " + paypropCount + " PayProp records\n");
            
            // Step 4: Verify
            System.out.println("Step 4: Verifying rebuild...");
            String verifySql = """
                SELECT 
                    source_system,
                    flow_direction,
                    COUNT(*) as count,
                    SUM(amount) as total
                FROM unified_transactions
                GROUP BY source_system, flow_direction
                ORDER BY source_system, flow_direction
            """;
            
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(verifySql)) {
                System.out.println("\n" + "=".repeat(80));
                System.out.printf("%-15s %-15s %-10s %-15s%n", "SOURCE", "FLOW", "COUNT", "TOTAL");
                System.out.println("-".repeat(80));
                
                while (rs.next()) {
                    String source = rs.getString("source_system");
                    String flow = rs.getString("flow_direction");
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");
                    
                    System.out.printf("%-15s %-15s %-10d £%-14.2f%n",
                        source, flow, count, total != null ? total : BigDecimal.ZERO);
                }
                System.out.println("=".repeat(80));
            }
            
            System.out.println("\n🎉 Rebuild completed successfully!");
            System.out.println("   Total records: " + (historicalCount + paypropCount));
            
        } catch (SQLException e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
