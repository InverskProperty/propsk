import java.sql.*;
import java.util.*;

public class InvestigateTableStructure {
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
            System.out.println("=== INVESTIGATING TABLE STRUCTURE ===\n");

            // Question 1: What tables feed unified_transactions?
            System.out.println("=== 1. TABLES FEEDING UNIFIED_TRANSACTIONS ===");
            System.out.println("\nA. historical_transactions:");
            describeTable(conn, "historical_transactions");

            System.out.println("\nB. financial_transactions:");
            describeTable(conn, "financial_transactions");

            // Question 2: Is there an "invoices" table?
            System.out.println("\n=== 2. INVOICES TABLE ===");
            describeTable(conn, "invoices");

            System.out.println("\nSample invoices:");
            String invoiceSample = """
                SELECT
                    id,
                    lease_reference,
                    payprop_id,
                    customer_id,
                    property_id,
                    amount,
                    start_date,
                    end_date,
                    invoice_type,
                    frequency
                FROM invoices
                LIMIT 5
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(invoiceSample)) {

                System.out.printf("%-10s %-20s %-20s %-12s %-15s%n",
                    "ID", "LEASE_REF", "PAYPROP_ID", "AMOUNT", "FREQUENCY");
                System.out.println("-".repeat(90));

                while (rs.next()) {
                    System.out.printf("%-10d %-20s %-20s GBP%-9.2f %-15s%n",
                        rs.getLong("id"),
                        rs.getString("lease_reference"),
                        rs.getString("payprop_id"),
                        rs.getBigDecimal("amount"),
                        rs.getString("frequency"));
                }
            }

            // Question 3: PayProp tables - what are they?
            System.out.println("\n=== 3. PAYPROP TABLES OVERVIEW ===");
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables("railway", null, "payprop%", new String[]{"TABLE"});

            List<String> paypropTables = new ArrayList<>();
            while (tables.next()) {
                paypropTables.add(tables.getString("TABLE_NAME"));
            }
            Collections.sort(paypropTables);

            System.out.println("\nAll PayProp tables found:");
            for (String table : paypropTables) {
                String countQuery = "SELECT COUNT(*) as count FROM " + table;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(countQuery)) {
                    if (rs.next()) {
                        System.out.printf("  %-50s : %d rows%n", table, rs.getInt("count"));
                    }
                }
            }

            // Question 4: What is payprop_report_icdn exactly?
            System.out.println("\n=== 4. PAYPROP_REPORT_ICDN DETAILS ===");
            describeTable(conn, "payprop_report_icdn");

            System.out.println("\nSample ICDN records:");
            String icdnSample = """
                SELECT
                    payprop_id,
                    transaction_type,
                    property_name,
                    amount,
                    commission_amount,
                    description
                FROM payprop_report_icdn
                LIMIT 5
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(icdnSample)) {

                System.out.printf("%-20s %-20s %-30s %-12s%n",
                    "PAYPROP_ID", "TYPE", "PROPERTY", "AMOUNT");
                System.out.println("-".repeat(90));

                while (rs.next()) {
                    System.out.printf("%-20s %-20s %-30s GBP%-9.2f%n",
                        rs.getString("payprop_id"),
                        rs.getString("transaction_type"),
                        rs.getString("property_name"),
                        rs.getBigDecimal("amount"));
                }
            }

            // Question 5: Payment instructions
            System.out.println("\n=== 5. PAYMENT INSTRUCTIONS ===");
            describeTable(conn, "payprop_export_payments");

            System.out.println("\nSample payment instructions:");
            String paymentSample = """
                SELECT
                    payprop_id,
                    description,
                    category,
                    beneficiary,
                    gross_percentage,
                    gross_amount,
                    frequency,
                    property_name
                FROM payprop_export_payments
                LIMIT 5
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(paymentSample)) {

                System.out.printf("%-20s %-30s %-20s %-20s%n",
                    "PAYPROP_ID", "DESCRIPTION", "CATEGORY", "BENEFICIARY");
                System.out.println("-".repeat(95));

                while (rs.next()) {
                    String desc = rs.getString("description");
                    if (desc != null && desc.length() > 30) {
                        desc = desc.substring(0, 27) + "...";
                    }
                    System.out.printf("%-20s %-30s %-20s %-20s%n",
                        rs.getString("payprop_id"),
                        desc,
                        rs.getString("category"),
                        rs.getString("beneficiary"));
                }
            }

            // Question 6: What's in payprop_export_invoices?
            System.out.println("\n=== 6. PAYPROP_EXPORT_INVOICES (Instructions) ===");
            describeTable(conn, "payprop_export_invoices");

            System.out.println("\nSample invoice instructions:");
            String invoiceInstrSample = """
                SELECT
                    payprop_id,
                    payprop_invoice_id,
                    property_name,
                    description,
                    amount,
                    frequency,
                    is_active_instruction
                FROM payprop_export_invoices
                LIMIT 5
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(invoiceInstrSample)) {

                System.out.printf("%-20s %-20s %-30s %-12s %-10s%n",
                    "INSTRUCTION_ID", "INVOICE_ID", "PROPERTY", "AMOUNT", "ACTIVE");
                System.out.println("-".repeat(100));

                while (rs.next()) {
                    System.out.printf("%-20s %-20s %-30s GBP%-9.2f %-10s%n",
                        rs.getString("payprop_id"),
                        rs.getString("payprop_invoice_id"),
                        rs.getString("property_name"),
                        rs.getBigDecimal("amount"),
                        rs.getBoolean("is_active_instruction") ? "YES" : "NO");
                }
            }

            // Summary: Relationship between tables
            System.out.println("\n=== 7. TABLE RELATIONSHIPS SUMMARY ===");
            System.out.println("\nLOCAL TABLES (Your Database):");
            System.out.println("  invoices              - Lease agreements with rent amounts");
            System.out.println("  properties            - Property master list");
            System.out.println("  customers             - Tenants and owners");
            System.out.println("  historical_transactions - Pre-PayProp transaction history");
            System.out.println("  financial_transactions  - PayProp-synced transactions");
            System.out.println("  unified_transactions    - Materialized view (rebuilt from above)");

            System.out.println("\nPAYPROP EXPORT TABLES (From PayProp API):");
            System.out.println("  payprop_export_properties        - PayProp property master list");
            System.out.println("  payprop_export_invoices          - Invoice INSTRUCTIONS (setup)");
            System.out.println("  payprop_export_payments          - Payment INSTRUCTIONS (rules)");
            System.out.println("  payprop_export_incoming_payments - Actual tenant payments received");
            System.out.println("  payprop_report_icdn              - Invoice/Credit/Debit ACTUALS");
            System.out.println("  payprop_report_financials        - All financial transactions");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void describeTable(Connection conn, String tableName) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns("railway", null, tableName, "%");

            System.out.printf("  %-30s %-20s %-10s%n", "COLUMN", "TYPE", "NULLABLE");
            System.out.println("  " + "-".repeat(65));

            while (columns.next()) {
                System.out.printf("  %-30s %-20s %-10s%n",
                    columns.getString("COLUMN_NAME"),
                    columns.getString("TYPE_NAME"),
                    columns.getString("IS_NULLABLE"));
            }
        } catch (SQLException e) {
            System.out.println("  Error describing table: " + e.getMessage());
        }
    }
}
