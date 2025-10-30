import java.sql.*;
import java.math.BigDecimal;

public class InvestigateFlat1Data {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== INVESTIGATING FLAT 1, 2, 3 DATA SOURCE ===\n");

            // Query 1: Financial transactions with FULL details for Flats 1, 2, 3
            System.out.println("=== 1. FINANCIAL_TRANSACTIONS FOR FLATS 1, 2, 3 (DETAILED) ===");
            String query1 = """
                SELECT
                    id,
                    pay_prop_transaction_id,
                    data_source,
                    transaction_date,
                    amount,
                    property_name,
                    description,
                    category_name,
                    created_at,
                    updated_at
                FROM financial_transactions
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                ORDER BY property_name, data_source, transaction_date
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-10s %-20s %-25s %-15s %-12s %-25s%n",
                    "ID", "PAYPROP_TXN_ID", "DATA_SOURCE", "DATE", "AMOUNT", "PROPERTY");
                System.out.println("-".repeat(125));
                while (rs.next()) {
                    System.out.printf("%-10d %-20s %-25s %-15s GBP%-9.2f %-25s%n",
                        rs.getLong("id"),
                        rs.getString("pay_prop_transaction_id"),
                        rs.getString("data_source"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("property_name"));
                }
            }

            // Query 2: Check the PayProp source tables
            System.out.println("\n=== 2. CHECK PAYPROP EXPORT TABLES ===");

            // ICDN in payprop_report_financials
            System.out.println("\n--- A. payprop_report_financials (ICDN) ---");
            String query2a = """
                SELECT
                    payprop_id,
                    property_name,
                    transaction_date,
                    amount,
                    transaction_type,
                    invoice_number
                FROM payprop_report_financials
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                ORDER BY property_name, transaction_date
                LIMIT 20
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2a)) {
                System.out.printf("%-20s %-25s %-15s %-12s %-20s %-15s%n",
                    "PAYPROP_ID", "PROPERTY", "DATE", "AMOUNT", "TYPE", "INVOICE_NUM");
                System.out.println("-".repeat(120));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-20s %-25s %-15s GBP%-9.2f %-20s %-15s%n",
                        rs.getString("payprop_id"),
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("transaction_type"),
                        rs.getString("invoice_number"));
                }
                if (!found) {
                    System.out.println("NO DATA FOUND in payprop_report_financials");
                }
            }

            // Commission in payprop_report_financials
            System.out.println("\n--- B. payprop_report_financials (COMMISSION) ---");
            String query2b = """
                SELECT
                    payprop_id,
                    property_name,
                    transaction_date,
                    amount,
                    transaction_type
                FROM payprop_report_financials
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                  AND transaction_type = 'COMMISSION_PAYMENT'
                ORDER BY property_name, transaction_date
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2b)) {
                System.out.printf("%-20s %-25s %-15s %-12s %-20s%n",
                    "PAYPROP_ID", "PROPERTY", "DATE", "AMOUNT", "TYPE");
                System.out.println("-".repeat(95));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-20s %-25s %-15s GBP%-9.2f %-20s%n",
                        rs.getString("payprop_id"),
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("transaction_type"));
                }
                if (!found) {
                    System.out.println("NO COMMISSION DATA FOUND in payprop_report_financials");
                }
            }

            // Query 3: Check invoice instructions
            System.out.println("\n=== 3. CHECK PAYPROP_REPORT_INVOICE_INSTRUCTIONS ===");
            String query3 = """
                SELECT
                    payprop_instruction_id,
                    property_name,
                    transaction_date,
                    amount,
                    description
                FROM payprop_report_invoice_instructions
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                ORDER BY property_name, transaction_date
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-30s %-25s %-15s %-12s%n",
                    "INSTRUCTION_ID", "PROPERTY", "DATE", "AMOUNT");
                System.out.println("-".repeat(90));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-30s %-25s %-15s GBP%-9.2f%n",
                        rs.getString("payprop_instruction_id"),
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"));
                }
                if (!found) {
                    System.out.println("NO DATA FOUND in payprop_report_invoice_instructions");
                }
            }

            // Query 4: Check ALL properties in PayProp export to see pattern
            System.out.println("\n=== 4. ALL PROPERTIES IN PAYPROP_REPORT_FINANCIALS ===");
            String query4 = """
                SELECT
                    property_name,
                    COUNT(*) as transaction_count,
                    MIN(transaction_date) as earliest,
                    MAX(transaction_date) as latest
                FROM payprop_report_financials
                GROUP BY property_name
                ORDER BY transaction_count DESC
                LIMIT 20
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-40s %-15s %-15s %-15s%n",
                    "PROPERTY_NAME", "COUNT", "EARLIEST", "LATEST");
                System.out.println("-".repeat(90));
                while (rs.next()) {
                    System.out.printf("%-40s %-15d %-15s %-15s%n",
                        rs.getString("property_name"),
                        rs.getInt("transaction_count"),
                        rs.getDate("earliest"),
                        rs.getDate("latest"));
                }
            }

            // Query 5: Compare Flat 1,2,3 with Flat 4,5,6
            System.out.println("\n=== 5. COMPARISON: FLATS 1,2,3 vs FLATS 4,5,6 ===");
            String query5 = """
                SELECT
                    CASE
                        WHEN property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                        THEN 'Flats 1-3 (NOT on PayProp)'
                        ELSE 'Flats 4-6 (ON PayProp)'
                    END as group_name,
                    COUNT(*) as total_transactions,
                    COUNT(DISTINCT data_source) as data_sources,
                    SUM(amount) as total_amount
                FROM financial_transactions
                WHERE property_name IN (
                    'Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate',
                    'Flat 4 - 3 West Gate', 'Flat 5 - 3 West Gate', 'Flat 6 - 3 West Gate'
                )
                GROUP BY
                    CASE
                        WHEN property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                        THEN 'Flats 1-3 (NOT on PayProp)'
                        ELSE 'Flats 4-6 (ON PayProp)'
                    END
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-30s %-20s %-15s %-20s%n",
                    "GROUP", "TOTAL_TRANSACTIONS", "DATA_SOURCES", "TOTAL_AMOUNT");
                System.out.println("-".repeat(90));
                while (rs.next()) {
                    System.out.printf("%-30s %-20d %-15d GBP%-17.2f%n",
                        rs.getString("group_name"),
                        rs.getInt("total_transactions"),
                        rs.getInt("data_sources"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Query 6: Check for INCOMING_PAYMENT for Flats 1,2,3
            System.out.println("\n=== 6. INCOMING_PAYMENT CHECK FOR FLATS 1,2,3 ===");
            String query6 = """
                SELECT
                    property_name,
                    COUNT(*) as count
                FROM financial_transactions
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                  AND data_source = 'INCOMING_PAYMENT'
                GROUP BY property_name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {
                System.out.printf("%-30s %-10s%n", "PROPERTY", "COUNT");
                System.out.println("-".repeat(45));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-30s %-10d%n",
                        rs.getString("property_name"),
                        rs.getInt("count"));
                }
                if (!found) {
                    System.out.println("NO INCOMING_PAYMENT transactions for Flats 1,2,3");
                    System.out.println("[GOOD] - This is correct if they're not on PayProp");
                }
            }

            // Query 7: Where is the data REALLY coming from?
            System.out.println("\n=== 7. DATA SOURCE TRACEABILITY ===");
            String query7 = """
                SELECT
                    ft.id,
                    ft.pay_prop_transaction_id,
                    ft.data_source,
                    ft.property_name,
                    ft.amount,
                    ft.transaction_date,
                    ft.created_at,
                    ft.updated_at,
                    DATEDIFF(ft.updated_at, ft.created_at) as days_between_create_update
                FROM financial_transactions ft
                WHERE ft.property_name = 'Flat 1 - 3 West Gate'
                ORDER BY ft.created_at
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query7)) {
                System.out.printf("%-10s %-25s %-25s %-12s %-20s %-20s %-15s%n",
                    "ID", "DATA_SOURCE", "PAYPROP_TXN_ID", "AMOUNT", "CREATED_AT", "UPDATED_AT", "DAYS_DIFF");
                System.out.println("-".repeat(135));
                while (rs.next()) {
                    System.out.printf("%-10d %-25s %-25s GBP%-9.2f %-20s %-20s %-15d%n",
                        rs.getLong("id"),
                        rs.getString("data_source"),
                        rs.getString("pay_prop_transaction_id"),
                        rs.getBigDecimal("amount"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("updated_at"),
                        rs.getInt("days_between_create_update"));
                }
            }

            System.out.println("\n=== ANALYSIS COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
