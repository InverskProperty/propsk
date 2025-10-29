import java.sql.*;

public class DetailedAnalysis {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("\n" + "=".repeat(100));
            System.out.println("DETAILED ANALYSIS: INVOICES vs RENT_INVOICE RELATIONSHIP");
            System.out.println("=".repeat(100) + "\n");

            // Query 1: Sample invoices with property info
            System.out.println("=== SAMPLE INVOICES (First 10) ===");
            String query1 = "SELECT i.id, i.property_id, i.lease_reference, i.amount, " +
                          "i.start_date, i.payprop_id, i.description " +
                          "FROM invoices i LIMIT 10";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-5s %-12s %-20s %-12s %-15s %-15s %-30s%n",
                    "ID", "PROP_ID", "LEASE_REF", "AMOUNT", "START_DATE", "PAYPROP_ID", "DESCRIPTION");
                System.out.println("-".repeat(120));
                while (rs.next()) {
                    String desc = rs.getString("description");
                    if (desc != null && desc.length() > 28) {
                        desc = desc.substring(0, 25) + "...";
                    }
                    System.out.printf("%-5s %-12s %-20s %-12s %-15s %-15s %-30s%n",
                        rs.getString("id"),
                        rs.getString("property_id"),
                        rs.getString("lease_reference"),
                        rs.getString("amount"),
                        rs.getString("start_date"),
                        rs.getString("payprop_id"),
                        desc);
                }
            }

            // Query 2: RENT_INVOICE records detailed
            System.out.println("\n=== ALL RENT_INVOICE RECORDS ===");
            String query2 = "SELECT id, property_id, property_name, transaction_date, amount, " +
                          "invoice_id, pay_prop_transaction_id, description " +
                          "FROM financial_transactions WHERE data_source = 'RENT_INVOICE'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-5s %-12s %-30s %-15s %-12s %-12s %-20s %-30s%n",
                    "ID", "PROP_ID", "PROPERTY_NAME", "TXN_DATE", "AMOUNT", "INV_ID", "PAYPROP_TXN_ID", "DESCRIPTION");
                System.out.println("-".repeat(145));
                while (rs.next()) {
                    String propName = rs.getString("property_name");
                    if (propName != null && propName.length() > 28) {
                        propName = propName.substring(0, 25) + "...";
                    }
                    String desc = rs.getString("description");
                    if (desc != null && desc.length() > 28) {
                        desc = desc.substring(0, 25) + "...";
                    }
                    System.out.printf("%-5s %-12s %-30s %-15s %-12s %-12s %-20s %-30s%n",
                        rs.getString("id"),
                        rs.getString("property_id"),
                        propName,
                        rs.getString("transaction_date"),
                        rs.getString("amount"),
                        rs.getString("invoice_id"),
                        rs.getString("pay_prop_transaction_id"),
                        desc);
                }
            }

            // Query 3: Try to match RENT_INVOICE to invoices by property_id
            System.out.println("\n=== MATCHING RENT_INVOICE TO INVOICES BY PROPERTY_ID ===");
            String query3 = "SELECT " +
                          "ft.id as ft_id, " +
                          "ft.property_id as ft_property_id, " +
                          "ft.property_name, " +
                          "COUNT(i.id) as matching_invoices, " +
                          "GROUP_CONCAT(i.id) as invoice_ids " +
                          "FROM financial_transactions ft " +
                          "LEFT JOIN invoices i ON CAST(ft.property_id AS UNSIGNED) = i.property_id " +
                          "WHERE ft.data_source = 'RENT_INVOICE' " +
                          "GROUP BY ft.id, ft.property_id, ft.property_name";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-8s %-15s %-35s %-20s %-30s%n",
                    "FT_ID", "FT_PROP_ID", "PROPERTY_NAME", "MATCHING_INVOICES", "INVOICE_IDS");
                System.out.println("-".repeat(115));
                while (rs.next()) {
                    String propName = rs.getString("property_name");
                    if (propName != null && propName.length() > 33) {
                        propName = propName.substring(0, 30) + "...";
                    }
                    String invIds = rs.getString("invoice_ids");
                    if (invIds != null && invIds.length() > 28) {
                        invIds = invIds.substring(0, 25) + "...";
                    }
                    System.out.printf("%-8s %-15s %-35s %-20s %-30s%n",
                        rs.getString("ft_id"),
                        rs.getString("ft_property_id"),
                        propName,
                        rs.getString("matching_invoices"),
                        invIds);
                }
            }

            // Query 4: Count of properties
            System.out.println("\n=== PROPERTY COUNT COMPARISON ===");
            String query4 = "SELECT " +
                          "'Total Properties in Invoices' as metric, COUNT(DISTINCT property_id) as count " +
                          "FROM invoices " +
                          "UNION ALL " +
                          "SELECT 'Total Properties in RENT_INVOICE', COUNT(DISTINCT CAST(property_id AS UNSIGNED)) " +
                          "FROM financial_transactions WHERE data_source = 'RENT_INVOICE' " +
                          "UNION ALL " +
                          "SELECT 'Properties with property_id=0 in RENT_INVOICE', " +
                          "COUNT(DISTINCT property_name) " +
                          "FROM financial_transactions WHERE data_source = 'RENT_INVOICE' AND property_id = '0'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-50s %-10s%n", "METRIC", "COUNT");
                System.out.println("-".repeat(65));
                while (rs.next()) {
                    System.out.printf("%-50s %-10s%n",
                        rs.getString("metric"),
                        rs.getString("count"));
                }
            }

            // Query 5: Check data sources in financial_transactions
            System.out.println("\n=== DATA SOURCES IN FINANCIAL_TRANSACTIONS ===");
            String query5 = "SELECT data_source, COUNT(*) as count FROM financial_transactions " +
                          "GROUP BY data_source ORDER BY count DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-30s %-10s%n", "DATA_SOURCE", "COUNT");
                System.out.println("-".repeat(45));
                while (rs.next()) {
                    System.out.printf("%-30s %-10s%n",
                        rs.getString("data_source"),
                        rs.getString("count"));
                }
            }

            // Query 6: Check invoice types
            System.out.println("\n=== INVOICE TYPES BREAKDOWN ===");
            String query6 = "SELECT " +
                          "COALESCE(invoice_type, 'NULL') as invoice_type, " +
                          "COUNT(*) as count, " +
                          "COUNT(DISTINCT property_id) as distinct_properties " +
                          "FROM invoices GROUP BY invoice_type";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {
                System.out.printf("%-20s %-10s %-20s%n", "INVOICE_TYPE", "COUNT", "DISTINCT_PROPERTIES");
                System.out.println("-".repeat(55));
                while (rs.next()) {
                    System.out.printf("%-20s %-10s %-20s%n",
                        rs.getString("invoice_type"),
                        rs.getString("count"),
                        rs.getString("distinct_properties"));
                }
            }

            System.out.println("\n" + "=".repeat(100));
            System.out.println("ANALYSIS COMPLETE");
            System.out.println("=".repeat(100) + "\n");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
