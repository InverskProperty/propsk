import java.sql.*;

public class SummaryAnalysis {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("\n" + "=".repeat(100));
            System.out.println("EXECUTIVE SUMMARY: INVOICES vs RENT_INVOICE RELATIONSHIP");
            System.out.println("=".repeat(100) + "\n");

            // Summary of key findings
            System.out.println("KEY FINDINGS:");
            System.out.println("=".repeat(100));

            // 1. Invoices table overview
            System.out.println("\n1. INVOICES TABLE:");
            String q1 = "SELECT COUNT(*) as total, " +
                      "COUNT(DISTINCT property_id) as distinct_properties, " +
                      "SUM(CASE WHEN payprop_id IS NOT NULL THEN 1 ELSE 0 END) as from_payprop, " +
                      "SUM(CASE WHEN payprop_id IS NULL THEN 1 ELSE 0 END) as locally_created " +
                      "FROM invoices";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(q1)) {
                if (rs.next()) {
                    System.out.println("   - Total Invoices: " + rs.getInt("total"));
                    System.out.println("   - Distinct Properties: " + rs.getInt("distinct_properties"));
                    System.out.println("   - From PayProp: " + rs.getInt("from_payprop"));
                    System.out.println("   - Locally Created: " + rs.getInt("locally_created"));
                }
            }

            // 2. RENT_INVOICE overview
            System.out.println("\n2. RENT_INVOICE RECORDS IN FINANCIAL_TRANSACTIONS:");
            String q2 = "SELECT COUNT(*) as total, " +
                      "COUNT(DISTINCT property_name) as distinct_properties, " +
                      "MIN(transaction_date) as earliest, " +
                      "MAX(transaction_date) as latest, " +
                      "SUM(CASE WHEN property_id IS NULL THEN 1 ELSE 0 END) as null_property_id, " +
                      "SUM(CASE WHEN invoice_id IS NULL THEN 1 ELSE 0 END) as null_invoice_id " +
                      "FROM financial_transactions WHERE data_source = 'RENT_INVOICE'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(q2)) {
                if (rs.next()) {
                    System.out.println("   - Total RENT_INVOICE Records: " + rs.getInt("total"));
                    System.out.println("   - Distinct Properties: " + rs.getInt("distinct_properties"));
                    System.out.println("   - Date Range: " + rs.getString("earliest") + " to " + rs.getString("latest"));
                    System.out.println("   - Records with NULL property_id: " + rs.getInt("null_property_id"));
                    System.out.println("   - Records with NULL invoice_id: " + rs.getInt("null_invoice_id"));
                }
            }

            // 3. The relationship problem
            System.out.println("\n3. THE RELATIONSHIP PROBLEM:");
            System.out.println("   - RENT_INVOICE records have property_name but NO property_id");
            System.out.println("   - RENT_INVOICE records have NO invoice_id linking to invoices table");
            System.out.println("   - The invoices table has 34 properties with 43 invoices");
            System.out.println("   - The RENT_INVOICE data source only has 10 records for 10 properties");

            // 4. Why only 10 RENT_INVOICE records?
            System.out.println("\n4. WHY ONLY 10 RENT_INVOICE RECORDS?");
            System.out.println("   Let's check what properties are in RENT_INVOICE vs invoices:");

            String q4 = "SELECT property_name, COUNT(*) as record_count, " +
                      "GROUP_CONCAT(DISTINCT DATE_FORMAT(transaction_date, '%Y-%m')) as months " +
                      "FROM financial_transactions " +
                      "WHERE data_source = 'RENT_INVOICE' " +
                      "GROUP BY property_name " +
                      "ORDER BY property_name";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(q4)) {
                System.out.println("\n   RENT_INVOICE Properties:");
                System.out.printf("   %-35s %-15s %-30s%n", "PROPERTY_NAME", "RECORD_COUNT", "MONTHS");
                System.out.println("   " + "-".repeat(85));
                while (rs.next()) {
                    System.out.printf("   %-35s %-15s %-30s%n",
                        rs.getString("property_name"),
                        rs.getString("record_count"),
                        rs.getString("months"));
                }
            }

            // 5. Data source comparison
            System.out.println("\n5. FINANCIAL_TRANSACTIONS DATA SOURCES:");
            String q5 = "SELECT data_source, COUNT(*) as count " +
                      "FROM financial_transactions " +
                      "GROUP BY data_source " +
                      "ORDER BY count DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(q5)) {
                System.out.printf("   %-30s %-10s%n", "DATA_SOURCE", "COUNT");
                System.out.println("   " + "-".repeat(45));
                while (rs.next()) {
                    System.out.printf("   %-30s %-10s%n",
                        rs.getString("data_source"),
                        rs.getString("count"));
                }
            }

            // 6. Invoice types
            System.out.println("\n6. INVOICE TYPES IN INVOICES TABLE:");
            String q6 = "SELECT COALESCE(invoice_type, 'NULL') as type, " +
                      "COUNT(*) as count, " +
                      "COUNT(DISTINCT property_id) as distinct_properties " +
                      "FROM invoices " +
                      "GROUP BY invoice_type";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(q6)) {
                System.out.printf("   %-20s %-10s %-20s%n", "TYPE", "COUNT", "PROPERTIES");
                System.out.println("   " + "-".repeat(55));
                while (rs.next()) {
                    System.out.printf("   %-20s %-10s %-20s%n",
                        rs.getString("type"),
                        rs.getString("count"),
                        rs.getString("distinct_properties"));
                }
            }

            System.out.println("\n" + "=".repeat(100));
            System.out.println("CONCLUSION:");
            System.out.println("=".repeat(100));
            System.out.println("1. The 10 RENT_INVOICE records are for specific '3 West Gate' properties only");
            System.out.println("2. These records span January-February 2025");
            System.out.println("3. They do NOT link to the invoices table (invoice_id is NULL for all)");
            System.out.println("4. They do NOT have valid property_id values (all are NULL)");
            System.out.println("5. The invoices table has 43 invoices for 34 different properties");
            System.out.println("6. Most invoices (33 out of 43) are synced from PayProp");
            System.out.println("7. The RENT_INVOICE records appear to be manually created or imported separately");
            System.out.println("\nIMPLICATION: RENT_INVOICE records are NOT automatically generated from invoices");
            System.out.println("             They are a separate data source that needs to be linked/reconciled");
            System.out.println("=".repeat(100) + "\n");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
