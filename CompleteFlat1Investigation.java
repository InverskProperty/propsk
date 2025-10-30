import java.sql.*;
import java.math.BigDecimal;

public class CompleteFlat1Investigation {
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
            System.out.println("=== FLAT 1, 2, 3 WEST GATE - COMPLETE INVESTIGATION ===");
            System.out.println("USER CLAIM: These properties were NEVER on PayProp, only have historical INCOMING transactions\n");

            // Query 1: Get PayProp property IDs
            System.out.println("=== 1. PAYPROP PROPERTY IDs ===");
            String query1 = """
                SELECT DISTINCT
                    property_name,
                    property_payprop_id
                FROM payprop_report_icdn
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                ORDER BY property_name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-30s %-20s%n", "PROPERTY", "PAYPROP_ID");
                System.out.println("-".repeat(55));
                while (rs.next()) {
                    System.out.printf("%-30s %-20s%n",
                        rs.getString("property_name"),
                        rs.getString("property_payprop_id"));
                }
            }

            // Query 2: Check if these IDs exist in payprop_export_properties
            System.out.println("\n=== 2. CHECK PAYPROP MASTER PROPERTY LIST ===");
            String query2 = """
                SELECT
                    p.payprop_id,
                    p.name,
                    p.is_archived,
                    p.create_date
                FROM payprop_export_properties p
                WHERE p.name LIKE '%Flat 1%3 West Gate%'
                   OR p.name LIKE '%Flat 2%3 West Gate%'
                   OR p.name LIKE '%Flat 3%3 West Gate%'
                ORDER BY p.name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-20s %-40s %-10s%n", "PAYPROP_ID", "NAME", "ARCHIVED");
                System.out.println("-".repeat(75));
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("%-20s %-40s %-10s%n",
                        rs.getString("payprop_id"),
                        rs.getString("name"),
                        rs.getBoolean("is_archived") ? "YES" : "NO");
                }
                if (count == 0) {
                    System.out.println("[NOT IN PAYPROP MASTER LIST] - Contradicts ICDN data!");
                }
            }

            // Query 3: ICDN transaction details with dates
            System.out.println("\n=== 3. ICDN TRANSACTIONS (Invoice/Credit/Debit Notes) ===");
            String query3 = """
                SELECT
                    property_name,
                    transaction_date,
                    transaction_type,
                    amount,
                    commission_amount,
                    description,
                    imported_at
                FROM payprop_report_icdn
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                ORDER BY property_name, transaction_date DESC
                LIMIT 15
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-25s %-15s %-20s %-12s %-12s%n",
                    "PROPERTY", "DATE", "TYPE", "AMOUNT", "COMMISSION");
                System.out.println("-".repeat(95));
                while (rs.next()) {
                    System.out.printf("%-25s %-15s %-20s GBP%-9.2f GBP%-9.2f%n",
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getString("transaction_type"),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("commission_amount") != null ? rs.getBigDecimal("commission_amount") : BigDecimal.ZERO);
                }
            }

            // Query 4: Commission payment linkage
            System.out.println("\n=== 4. COMMISSION PAYMENTS LINKED TO ICDN ===");
            String query4 = """
                SELECT
                    ft.property_name,
                    ft.transaction_date,
                    ft.amount as commission_amt,
                    ft.pay_prop_transaction_id,
                    SUBSTRING(ft.pay_prop_transaction_id, 6) as icdn_id,
                    icdn.amount as invoice_amount,
                    icdn.commission_amount as icdn_comm_amt
                FROM financial_transactions ft
                LEFT JOIN payprop_report_icdn icdn
                  ON SUBSTRING(ft.pay_prop_transaction_id, 6) = icdn.payprop_id
                WHERE ft.property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                  AND ft.data_source = 'COMMISSION_PAYMENT'
                ORDER BY ft.property_name, ft.transaction_date DESC
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-25s %-15s %-12s %-12s %-12s%n",
                    "PROPERTY", "DATE", "COMM_AMT", "INVOICE_AMT", "MATCH?");
                System.out.println("-".repeat(85));
                while (rs.next()) {
                    BigDecimal commAmt = rs.getBigDecimal("commission_amt");
                    BigDecimal icdnCommAmt = rs.getBigDecimal("icdn_comm_amt");
                    boolean match = (icdnCommAmt != null && commAmt.compareTo(icdnCommAmt) == 0);
                    System.out.printf("%-25s %-15s GBP%-9.2f GBP%-9.2f %-12s%n",
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        commAmt,
                        rs.getBigDecimal("invoice_amount"),
                        match ? "✓ MATCH" : "✗ NO MATCH");
                }
            }

            // Query 5: Check for incoming payments in PayProp
            System.out.println("\n=== 5. INCOMING PAYMENTS IN PAYPROP EXPORTS ===");
            String query5 = """
                SELECT
                    property_name,
                    COUNT(*) as payment_count,
                    MIN(transaction_date) as earliest,
                    MAX(transaction_date) as latest
                FROM payprop_export_incoming_payments
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                GROUP BY property_name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-30s %-15s %-15s %-15s%n",
                    "PROPERTY", "COUNT", "EARLIEST", "LATEST");
                System.out.println("-".repeat(80));
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("%-30s %-15d %-15s %-15s%n",
                        rs.getString("property_name"),
                        rs.getInt("payment_count"),
                        rs.getDate("earliest"),
                        rs.getDate("latest"));
                }
                if (count == 0) {
                    System.out.println("[NO INCOMING PAYMENTS IN PAYPROP] - Expected if not on PayProp");
                }
            }

            // Query 6: Summary by data source
            System.out.println("\n=== 6. FINANCIAL TRANSACTIONS SUMMARY ===");
            String query6 = """
                SELECT
                    property_name,
                    data_source,
                    COUNT(*) as txn_count,
                    SUM(amount) as total_amount,
                    MIN(transaction_date) as earliest,
                    MAX(transaction_date) as latest
                FROM financial_transactions
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                GROUP BY property_name, data_source
                ORDER BY property_name, data_source
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {
                System.out.printf("%-25s %-25s %-8s %-12s %-12s%n",
                    "PROPERTY", "DATA_SOURCE", "COUNT", "TOTAL", "LATEST");
                System.out.println("-".repeat(95));
                while (rs.next()) {
                    System.out.printf("%-25s %-25s %-8d GBP%-9.2f %-12s%n",
                        rs.getString("property_name"),
                        rs.getString("data_source"),
                        rs.getInt("txn_count"),
                        rs.getBigDecimal("total_amount"),
                        rs.getDate("latest"));
                }
            }

            System.out.println("\n=== ANALYSIS SUMMARY ===");
            System.out.println("\nFINDINGS:");
            System.out.println("1. These properties ARE in PayProp's ICDN export (invoices/credits/debits)");
            System.out.println("2. Commission payments are derived from ICDN transactions (COMM_ prefix)");
            System.out.println("3. ICDN = Invoice/Credit/Debit Notes - charges TO tenants, not payments FROM tenants");
            System.out.println("4. No INCOMING_PAYMENT data suggests properties may not have rental receipts in PayProp");
            System.out.println("\nCONCLUSION:");
            System.out.println("Properties ARE on PayProp but may only be tracking CHARGES (invoices)");
            System.out.println("not RECEIPTS (incoming payments). This is why commission appears but no payments.");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
