import java.sql.*;

public class QueryDatabase2 {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected to database successfully!\n");

            // Query 7: Check invoices table structure and sample data
            System.out.println("=== QUERY 7: SAMPLE INVOICE RECORDS ===");
            String query7 = "SELECT id, property_id, property_name, invoice_date, amount, payprop_id " +
                          "FROM invoices LIMIT 10";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query7)) {
                System.out.printf("%-5s %-12s %-35s %-15s %-12s %-12s%n",
                    "ID", "PROPERTY_ID", "PROPERTY_NAME", "INVOICE_DATE", "AMOUNT", "PAYPROP_ID");
                System.out.println("-".repeat(110));
                while (rs.next()) {
                    System.out.printf("%-5s %-12s %-35s %-15s %-12s %-12s%n",
                        rs.getString("id"),
                        rs.getString("property_id"),
                        rs.getString("property_name"),
                        rs.getString("invoice_date"),
                        rs.getString("amount"),
                        rs.getString("payprop_id"));
                }
            }

            // Query 8: Check RENT_INVOICE records with more details
            System.out.println("\n=== QUERY 8: RENT_INVOICE DETAILS ===");
            String query8 = "SELECT id, property_id, property_name, transaction_date, amount, " +
                          "invoice_id, pay_prop_transaction_id " +
                          "FROM financial_transactions WHERE data_source = 'RENT_INVOICE'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query8)) {
                System.out.printf("%-5s %-12s %-35s %-15s %-12s %-12s %-20s%n",
                    "ID", "PROPERTY_ID", "PROPERTY_NAME", "TXN_DATE", "AMOUNT", "INVOICE_ID", "PAYPROP_TXN_ID");
                System.out.println("-".repeat(125));
                while (rs.next()) {
                    System.out.printf("%-5s %-12s %-35s %-15s %-12s %-12s %-20s%n",
                        rs.getString("id"),
                        rs.getString("property_id"),
                        rs.getString("property_name"),
                        rs.getString("transaction_date"),
                        rs.getString("amount"),
                        rs.getString("invoice_id"),
                        rs.getString("pay_prop_transaction_id"));
                }
            }

            // Query 9: Count properties in invoices vs RENT_INVOICE
            System.out.println("\n=== QUERY 9: PROPERTY DISTRIBUTION ===");
            String query9 = "SELECT " +
                          "'Invoices' as source, COUNT(DISTINCT property_id) as property_count, " +
                          "COUNT(*) as record_count FROM invoices " +
                          "UNION ALL " +
                          "SELECT 'RENT_INVOICE', COUNT(DISTINCT property_id), COUNT(*) " +
                          "FROM financial_transactions WHERE data_source = 'RENT_INVOICE'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query9)) {
                System.out.printf("%-20s %-20s %-20s%n", "SOURCE", "PROPERTY_COUNT", "RECORD_COUNT");
                System.out.println("-".repeat(65));
                while (rs.next()) {
                    System.out.printf("%-20s %-20s %-20s%n",
                        rs.getString("source"),
                        rs.getString("property_count"),
                        rs.getString("record_count"));
                }
            }

            // Query 10: Check why property_id is 0 for RENT_INVOICE
            System.out.println("\n=== QUERY 10: RENT_INVOICE PROPERTY_ID ANALYSIS ===");
            String query10 = "SELECT " +
                          "COUNT(*) as total, " +
                          "SUM(CASE WHEN property_id = 0 THEN 1 ELSE 0 END) as zero_property_id, " +
                          "SUM(CASE WHEN property_id IS NULL THEN 1 ELSE 0 END) as null_property_id, " +
                          "SUM(CASE WHEN property_id > 0 THEN 1 ELSE 0 END) as valid_property_id " +
                          "FROM financial_transactions WHERE data_source = 'RENT_INVOICE'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query10)) {
                System.out.printf("%-10s %-20s %-20s %-20s%n",
                    "TOTAL", "ZERO_PROPERTY_ID", "NULL_PROPERTY_ID", "VALID_PROPERTY_ID");
                System.out.println("-".repeat(75));
                while (rs.next()) {
                    System.out.printf("%-10s %-20s %-20s %-20s%n",
                        rs.getString("total"),
                        rs.getString("zero_property_id"),
                        rs.getString("null_property_id"),
                        rs.getString("valid_property_id"));
                }
            }

            // Query 11: Check if there are invoices matching the RENT_INVOICE properties by name
            System.out.println("\n=== QUERY 11: MATCHING BY PROPERTY NAME ===");
            String query11 = "SELECT " +
                          "ft.property_name as rent_invoice_property, " +
                          "COUNT(DISTINCT i.id) as matching_invoice_count " +
                          "FROM financial_transactions ft " +
                          "LEFT JOIN invoices i ON ft.property_name = i.property_name " +
                          "WHERE ft.data_source = 'RENT_INVOICE' " +
                          "GROUP BY ft.property_name";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query11)) {
                System.out.printf("%-50s %-25s%n", "RENT_INVOICE_PROPERTY", "MATCHING_INVOICE_COUNT");
                System.out.println("-".repeat(80));
                while (rs.next()) {
                    System.out.printf("%-50s %-25s%n",
                        rs.getString("rent_invoice_property"),
                        rs.getString("matching_invoice_count"));
                }
            }

            // Query 12: Check invoices table for "West Gate" properties
            System.out.println("\n=== QUERY 12: WEST GATE INVOICES ===");
            String query12 = "SELECT COUNT(*) as west_gate_invoice_count, " +
                          "COUNT(DISTINCT property_name) as distinct_properties " +
                          "FROM invoices WHERE property_name LIKE '%West Gate%'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query12)) {
                System.out.printf("%-30s %-25s%n", "WEST_GATE_INVOICE_COUNT", "DISTINCT_PROPERTIES");
                System.out.println("-".repeat(60));
                while (rs.next()) {
                    System.out.printf("%-30s %-25s%n",
                        rs.getString("west_gate_invoice_count"),
                        rs.getString("distinct_properties"));
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
