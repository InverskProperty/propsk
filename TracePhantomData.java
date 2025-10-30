import java.sql.*;

public class TracePhantomData {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== TRACING PHANTOM DATA FOR FLATS 1, 2, 3 AT 3 WEST GATE ===\n");
            System.out.println("USER STATES: These properties have NEVER been on PayProp\n");

            // Query 1: Check if these properties exist in payprop_export_properties
            System.out.println("=== 1. CHECK payprop_export_properties (PayProp Master Property List) ===");
            String query1 = """
                SELECT
                    payprop_id,
                    name,
                    create_date,
                    modify_date,
                    is_archived,
                    sync_status
                FROM payprop_export_properties
                WHERE name LIKE '%Flat 1%3 West Gate%'
                   OR name LIKE '%Flat 2%3 West Gate%'
                   OR name LIKE '%Flat 3%3 West Gate%'
                ORDER BY name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-20s %-40s %-20s %-10s%n",
                    "PAYPROP_ID", "NAME", "CREATE_DATE", "ARCHIVED");
                System.out.println("-".repeat(95));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-20s %-40s %-20s %-10s%n",
                        rs.getString("payprop_id"),
                        rs.getString("name"),
                        rs.getTimestamp("create_date"),
                        rs.getBoolean("is_archived") ? "YES" : "NO");
                }
                if (!found) {
                    System.out.println("[GOOD] NOT FOUND in PayProp properties - confirms NOT on PayProp");
                } else {
                    System.out.println("[BAD] FOUND in PayProp properties - contradicts user statement!");
                }
            }

            // Query 2: Sample ICDN transactions for Flats 1, 2, 3
            System.out.println("\n=== 2. SAMPLE ICDN TRANSACTIONS FOR FLATS 1, 2, 3 ===");
            String query2 = """
                SELECT
                    payprop_id,
                    transaction_type,
                    property_name,
                    property_payprop_id,
                    transaction_date,
                    amount,
                    description,
                    reference,
                    imported_at
                FROM payprop_report_icdn
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                ORDER BY property_name, transaction_date
                LIMIT 20
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-20s %-25s %-20s %-15s %-12s%n",
                    "PAYPROP_ID", "PROPERTY", "PROPERTY_PAYPROP_ID", "DATE", "AMOUNT");
                System.out.println("-".repeat(100));
                while (rs.next()) {
                    System.out.printf("%-20s %-25s %-20s %-15s GBP%-9.2f%n",
                        rs.getString("payprop_id"),
                        rs.getString("property_name"),
                        rs.getString("property_payprop_id"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"));
                }
            }

            // Query 3: Check the property_payprop_id values
            System.out.println("\n=== 3. DISTINCT PROPERTY PAYPROP IDs FOR FLATS 1, 2, 3 ===");
            String query3 = """
                SELECT DISTINCT
                    property_name,
                    property_payprop_id
                FROM payprop_report_icdn
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                ORDER BY property_name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-30s %-20s%n", "PROPERTY_NAME", "PROPERTY_PAYPROP_ID");
                System.out.println("-".repeat(55));
                while (rs.next()) {
                    System.out.printf("%-30s %-20s%n",
                        rs.getString("property_name"),
                        rs.getString("property_payprop_id"));
                }
            }

            // Query 4: Cross-reference property_payprop_id with payprop_export_properties
            System.out.println("\n=== 4. LOOKUP PROPERTY_PAYPROP_IDs IN MASTER PROPERTY LIST ===");
            String query4 = """
                SELECT DISTINCT
                    icdn.property_payprop_id,
                    icdn.property_name as icdn_name,
                    prop.name as master_name,
                    prop.is_archived
                FROM payprop_report_icdn icdn
                LEFT JOIN payprop_export_properties prop ON icdn.property_payprop_id = prop.payprop_id
                WHERE icdn.property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                ORDER BY icdn.property_name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-20s %-30s %-30s %-10s%n",
                    "PROPERTY_PAYPROP_ID", "ICDN_NAME", "MASTER_NAME", "ARCHIVED");
                System.out.println("-".repeat(95));
                while (rs.next()) {
                    String masterName = rs.getString("master_name");
                    System.out.printf("%-20s %-30s %-30s %-10s%n",
                        rs.getString("property_payprop_id"),
                        rs.getString("icdn_name"),
                        masterName == null ? "[NOT IN MASTER]" : masterName,
                        rs.getBoolean("is_archived") ? "YES" : "NO");
                }
            }

            // Query 5: Compare with Flats 4, 5, 6 (which ARE on PayProp)
            System.out.println("\n=== 5. COMPARISON: FLATS 1-3 vs FLATS 4-6 ===");
            String query5 = """
                SELECT
                    CASE
                        WHEN property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                        THEN 'Flats 1-3 (NOT on PayProp per user)'
                        ELSE 'Flats 4-6 (ON PayProp)'
                    END as group_name,
                    property_name,
                    property_payprop_id,
                    COUNT(*) as transaction_count,
                    MIN(transaction_date) as earliest,
                    MAX(transaction_date) as latest,
                    MIN(imported_at) as first_import,
                    MAX(imported_at) as last_import
                FROM payprop_report_icdn
                WHERE property_name IN (
                    'Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate',
                    'Flat 4 - 3 West Gate', 'Flat 5 - 3 West Gate', 'Flat 6 - 3 West Gate'
                )
                GROUP BY
                    CASE
                        WHEN property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                        THEN 'Flats 1-3 (NOT on PayProp per user)'
                        ELSE 'Flats 4-6 (ON PayProp)'
                    END,
                    property_name,
                    property_payprop_id
                ORDER BY group_name, property_name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-40s %-30s %-20s %-8s %-12s %-20s%n",
                    "GROUP", "PROPERTY", "PAYPROP_ID", "COUNT", "EARLIEST", "FIRST_IMPORT");
                System.out.println("-".repeat(140));
                while (rs.next()) {
                    System.out.printf("%-40s %-30s %-20s %-8d %-12s %-20s%n",
                        rs.getString("group_name"),
                        rs.getString("property_name"),
                        rs.getString("property_payprop_id"),
                        rs.getInt("transaction_count"),
                        rs.getDate("earliest"),
                        rs.getTimestamp("first_import"));
                }
            }

            // Query 6: Check invoice instructions for these properties
            System.out.println("\n=== 6. INVOICE INSTRUCTIONS FOR FLATS 1, 2, 3 ===");
            String query6 = """
                SELECT
                    payprop_id,
                    property_name,
                    amount,
                    description,
                    frequency,
                    from_date,
                    to_date,
                    is_active
                FROM payprop_export_invoice_instructions
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                ORDER BY property_name
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {
                System.out.printf("%-30s %-25s %-12s %-15s %-10s%n",
                    "PAYPROP_ID", "PROPERTY", "AMOUNT", "FROM_DATE", "ACTIVE");
                System.out.println("-".repeat(100));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-30s %-25s GBP%-9.2f %-15s %-10s%n",
                        rs.getString("payprop_id"),
                        rs.getString("property_name"),
                        rs.getBigDecimal("amount"),
                        rs.getDate("from_date"),
                        rs.getBoolean("is_active") ? "YES" : "NO");
                }
                if (!found) {
                    System.out.println("[OK] No invoice instructions for Flats 1, 2, 3");
                }
            }

            // Query 7: Check for these properties in all_payments report
            System.out.println("\n=== 7. ALL_PAYMENTS REPORT FOR FLATS 1, 2, 3 ===");
            String query7 = """
                SELECT
                    payprop_id,
                    incoming_property_name,
                    incoming_transaction_type,
                    amount,
                    description,
                    due_date
                FROM payprop_report_all_payments
                WHERE incoming_property_name LIKE '%Flat 1%3 West Gate%'
                   OR incoming_property_name LIKE '%Flat 2%3 West Gate%'
                   OR incoming_property_name LIKE '%Flat 3%3 West Gate%'
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query7)) {
                System.out.printf("%-20s %-30s %-20s %-12s%n",
                    "PAYPROP_ID", "PROPERTY", "TXN_TYPE", "AMOUNT");
                System.out.println("-".repeat(90));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-20s %-30s %-20s GBP%-9.2f%n",
                        rs.getString("payprop_id"),
                        rs.getString("incoming_property_name"),
                        rs.getString("incoming_transaction_type"),
                        rs.getBigDecimal("amount"));
                }
                if (!found) {
                    System.out.println("[OK] No all_payments entries for Flats 1, 2, 3");
                }
            }

            System.out.println("\n=== ANALYSIS COMPLETE ===");
            System.out.println("\nKEY FINDINGS:");
            System.out.println("- If Flats 1-3 appear in payprop_export_properties: PayProp HAS these properties");
            System.out.println("- If property_payprop_id resolves to a different property: Name mismatch issue");
            System.out.println("- If imported_at dates are recent: Data appeared during recent sync");
            System.out.println("- Compare transaction patterns between Flats 1-3 vs 4-6 for anomalies");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
