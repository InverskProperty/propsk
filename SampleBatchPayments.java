import java.sql.*;
import java.math.BigDecimal;

public class SampleBatchPayments {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found.");
        }

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== BATCH_PAYMENT SAMPLES ===\n");

            // Query 1: Overview statistics
            System.out.println("=== 1. BATCH_PAYMENT STATISTICS ===");
            String statsQuery = """
                SELECT
                    COUNT(*) as total_count,
                    SUM(amount) as total_amount,
                    MIN(transaction_date) as earliest_date,
                    MAX(transaction_date) as latest_date,
                    MIN(amount) as min_amount,
                    MAX(amount) as max_amount,
                    AVG(amount) as avg_amount
                FROM financial_transactions
                WHERE data_source = 'BATCH_PAYMENT'
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(statsQuery)) {
                if (rs.next()) {
                    System.out.println("Total records: " + rs.getInt("total_count"));
                    System.out.println("Total amount: £" + rs.getBigDecimal("total_amount"));
                    System.out.println("Date range: " + rs.getDate("earliest_date") + " to " + rs.getDate("latest_date"));
                    System.out.println("Amount range: £" + rs.getBigDecimal("min_amount") + " to £" + rs.getBigDecimal("max_amount"));
                    System.out.println("Average amount: £" + rs.getBigDecimal("avg_amount"));
                }
            }

            // Query 2: Sample records with full details
            System.out.println("\n=== 2. SAMPLE BATCH_PAYMENT RECORDS (First 15) ===");
            String sampleQuery = """
                SELECT
                    id,
                    pay_prop_transaction_id,
                    transaction_date,
                    amount,
                    property_name,
                    tenant_name,
                    description,
                    payprop_beneficiary_type,
                    payprop_global_beneficiary,
                    payprop_batch_id,
                    batch_sequence_number,
                    remittance_date,
                    created_at
                FROM financial_transactions
                WHERE data_source = 'BATCH_PAYMENT'
                ORDER BY transaction_date DESC, id DESC
                LIMIT 15
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sampleQuery)) {

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\n--- Record #" + count + " ---");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  PayProp Transaction ID: " + rs.getString("pay_prop_transaction_id"));
                    System.out.println("  Transaction Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Property: " + rs.getString("property_name"));
                    System.out.println("  Tenant: " + rs.getString("tenant_name"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println("  Beneficiary Type: " + rs.getString("payprop_beneficiary_type"));
                    System.out.println("  Beneficiary: " + rs.getString("payprop_global_beneficiary"));
                    System.out.println("  Batch ID: " + rs.getString("payprop_batch_id"));
                    System.out.println("  Sequence in Batch: " + rs.getInt("batch_sequence_number"));
                    System.out.println("  Remittance Date: " + rs.getDate("remittance_date"));
                }
            }

            // Query 3: Breakdown by beneficiary type
            System.out.println("\n\n=== 3. BATCH_PAYMENT BY BENEFICIARY TYPE ===");
            String beneficiaryQuery = """
                SELECT
                    payprop_beneficiary_type,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM financial_transactions
                WHERE data_source = 'BATCH_PAYMENT'
                GROUP BY payprop_beneficiary_type
                ORDER BY count DESC
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(beneficiaryQuery)) {
                System.out.printf("%-30s %-15s %-20s%n", "BENEFICIARY_TYPE", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(70));
                while (rs.next()) {
                    System.out.printf("%-30s %-15d GBP%-17.2f%n",
                        rs.getString("payprop_beneficiary_type"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Query 4: Group by property
            System.out.println("\n=== 4. BATCH_PAYMENT BY PROPERTY (Top 10) ===");
            String propertyQuery = """
                SELECT
                    property_name,
                    COUNT(*) as payment_count,
                    SUM(amount) as total_amount,
                    MIN(transaction_date) as first_payment,
                    MAX(transaction_date) as last_payment
                FROM financial_transactions
                WHERE data_source = 'BATCH_PAYMENT'
                GROUP BY property_name
                ORDER BY payment_count DESC
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(propertyQuery)) {
                System.out.printf("%-35s %-10s %-15s %-15s%n",
                    "PROPERTY", "COUNT", "TOTAL", "LATEST");
                System.out.println("-".repeat(85));
                while (rs.next()) {
                    System.out.printf("%-35s %-10d GBP%-12.2f %-15s%n",
                        rs.getString("property_name"),
                        rs.getInt("payment_count"),
                        rs.getBigDecimal("total_amount"),
                        rs.getDate("last_payment"));
                }
            }

            // Query 5: Batch groupings
            System.out.println("\n=== 5. BATCH GROUPINGS (Recent 10 batches) ===");
            String batchQuery = """
                SELECT
                    payprop_batch_id,
                    remittance_date,
                    COUNT(*) as payments_in_batch,
                    SUM(amount) as batch_total
                FROM financial_transactions
                WHERE data_source = 'BATCH_PAYMENT'
                  AND payprop_batch_id IS NOT NULL
                GROUP BY payprop_batch_id, remittance_date
                ORDER BY remittance_date DESC
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(batchQuery)) {
                System.out.printf("%-30s %-15s %-15s %-20s%n",
                    "BATCH_ID", "REMIT_DATE", "PAYMENTS", "BATCH_TOTAL");
                System.out.println("-".repeat(85));
                while (rs.next()) {
                    System.out.printf("%-30s %-15s %-15d GBP%-17.2f%n",
                        rs.getString("payprop_batch_id"),
                        rs.getDate("remittance_date"),
                        rs.getInt("payments_in_batch"),
                        rs.getBigDecimal("batch_total"));
                }
            }

            // Query 6: Check invoice linkage
            System.out.println("\n=== 6. BATCH_PAYMENT INVOICE LINKAGE ===");
            String linkageQuery = """
                SELECT
                    COUNT(*) as total,
                    COUNT(invoice_id) as linked_count,
                    COUNT(*) - COUNT(invoice_id) as unlinked_count,
                    ROUND(COUNT(invoice_id) * 100.0 / COUNT(*), 1) as linked_percentage
                FROM financial_transactions
                WHERE data_source = 'BATCH_PAYMENT'
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(linkageQuery)) {
                if (rs.next()) {
                    System.out.println("Total BATCH_PAYMENT records: " + rs.getInt("total"));
                    System.out.println("Linked to invoices: " + rs.getInt("linked_count"));
                    System.out.println("NOT linked to invoices: " + rs.getInt("unlinked_count"));
                    System.out.println("Linkage percentage: " + rs.getDouble("linked_percentage") + "%");
                }
            }

            // Query 7: Sample with invoice details
            System.out.println("\n=== 7. BATCH_PAYMENT WITH INVOICE/LEASE DETAILS (Sample 5) ===");
            String invoiceDetailQuery = """
                SELECT
                    ft.id,
                    ft.transaction_date,
                    ft.amount,
                    ft.property_name,
                    ft.payprop_global_beneficiary as beneficiary,
                    i.lease_reference,
                    i.amount as lease_monthly_amount
                FROM financial_transactions ft
                LEFT JOIN invoices i ON ft.invoice_id = i.id
                WHERE ft.data_source = 'BATCH_PAYMENT'
                  AND ft.invoice_id IS NOT NULL
                ORDER BY ft.transaction_date DESC
                LIMIT 5
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(invoiceDetailQuery)) {

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\n--- Payment #" + count + " ---");
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Property: " + rs.getString("property_name"));
                    System.out.println("  Beneficiary: " + rs.getString("beneficiary"));
                    System.out.println("  Lease: " + rs.getString("lease_reference"));
                    System.out.println("  Monthly Rent: £" + rs.getBigDecimal("lease_monthly_amount"));
                }
            }

            System.out.println("\n=== ANALYSIS COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
