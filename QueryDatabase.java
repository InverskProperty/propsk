import java.sql.*;

public class QueryDatabase {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected to database successfully!\n");

            // Query 1: Count invoices in invoices table
            System.out.println("=== QUERY 1: TOTAL INVOICES ===");
            String query1 = "SELECT 'invoices table - total' as metric, COUNT(*) as count FROM invoices";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-30s %-10s%n", "METRIC", "COUNT");
                System.out.println("-".repeat(45));
                while (rs.next()) {
                    System.out.printf("%-30s %-10d%n",
                        rs.getString("metric"),
                        rs.getInt("count"));
                }
            }

            // Query 2: Count invoices by PayProp sync status
            System.out.println("\n=== QUERY 2: INVOICES BY SOURCE ===");
            String query2 = "SELECT 'invoices table - by source' as metric, " +
                          "CASE WHEN payprop_id IS NOT NULL THEN 'From PayProp' ELSE 'Locally Created' END as source, " +
                          "COUNT(*) as count FROM invoices GROUP BY source";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-30s %-20s %-10s%n", "METRIC", "SOURCE", "COUNT");
                System.out.println("-".repeat(65));
                while (rs.next()) {
                    System.out.printf("%-30s %-20s %-10d%n",
                        rs.getString("metric"),
                        rs.getString("source"),
                        rs.getInt("count"));
                }
            }

            // Query 3: Compare with RENT_INVOICE in financial_transactions
            System.out.println("\n=== QUERY 3: RENT_INVOICE IN FINANCIAL_TRANSACTIONS ===");
            String query3 = "SELECT 'financial_transactions RENT_INVOICE' as metric, COUNT(*) as count " +
                          "FROM financial_transactions WHERE data_source = 'RENT_INVOICE'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-40s %-10s%n", "METRIC", "COUNT");
                System.out.println("-".repeat(55));
                while (rs.next()) {
                    System.out.printf("%-40s %-10d%n",
                        rs.getString("metric"),
                        rs.getInt("count"));
                }
            }

            // Query 4: Check which properties have RENT_INVOICE
            System.out.println("\n=== QUERY 4: PROPERTIES WITH RENT_INVOICE ===");
            String query4 = "SELECT DISTINCT property_name, property_id FROM financial_transactions " +
                          "WHERE data_source = 'RENT_INVOICE' ORDER BY property_name";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-50s %-10s%n", "PROPERTY_NAME", "PROPERTY_ID");
                System.out.println("-".repeat(65));
                while (rs.next()) {
                    System.out.printf("%-50s %-10d%n",
                        rs.getString("property_name"),
                        rs.getInt("property_id"));
                }
            }

            // Query 5: Check total properties with invoices in invoices table
            System.out.println("\n=== QUERY 5: DISTINCT PROPERTIES WITH INVOICES ===");
            String query5 = "SELECT COUNT(DISTINCT property_id) as properties_with_invoices FROM invoices";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-35s%n", "PROPERTIES_WITH_INVOICES");
                System.out.println("-".repeat(40));
                while (rs.next()) {
                    System.out.printf("%-35d%n",
                        rs.getInt("properties_with_invoices"));
                }
            }

            // Query 6: Check if RENT_INVOICE records link to invoices table
            System.out.println("\n=== QUERY 6: RENT_INVOICE WITH INVOICE_ID ===");
            String query6 = "SELECT 'RENT_INVOICE with invoice_id' as check_type, COUNT(*) as count " +
                          "FROM financial_transactions WHERE data_source = 'RENT_INVOICE' AND invoice_id IS NOT NULL";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {
                System.out.printf("%-35s %-10s%n", "CHECK_TYPE", "COUNT");
                System.out.println("-".repeat(50));
                while (rs.next()) {
                    System.out.printf("%-35s %-10d%n",
                        rs.getString("check_type"),
                        rs.getInt("count"));
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
