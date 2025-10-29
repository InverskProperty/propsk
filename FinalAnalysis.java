import java.sql.*;

public class FinalAnalysis {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("\n" + "=".repeat(100));
            System.out.println("FINAL ANALYSIS: WHY ONLY 10 RENT_INVOICE RECORDS?");
            System.out.println("=".repeat(100) + "\n");

            // Query 1: Check if we can match by property name
            System.out.println("=== MATCHING RENT_INVOICE TO INVOICES BY DESCRIPTION/PROPERTY NAME ===");
            String query1 =
                "SELECT " +
                "  ft.id as ft_id, " +
                "  ft.property_name, " +
                "  ft.amount as ft_amount, " +
                "  ft.transaction_date, " +
                "  i.id as invoice_id, " +
                "  i.property_id, " +
                "  i.amount as invoice_amount, " +
                "  i.lease_reference, " +
                "  i.description " +
                "FROM financial_transactions ft " +
                "LEFT JOIN invoices i ON i.description LIKE CONCAT('%', SUBSTRING_INDEX(ft.property_name, ' - ', -1), '%') " +
                "WHERE ft.data_source = 'RENT_INVOICE' " +
                "ORDER BY ft.id";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-6s %-30s %-10s %-15s %-8s %-8s %-12s %-18s%n",
                    "FT_ID", "PROPERTY_NAME", "FT_AMT", "TXN_DATE", "INV_ID", "PROP_ID", "INV_AMT", "LEASE_REF");
                System.out.println("-".repeat(120));
                while (rs.next()) {
                    String propName = rs.getString("property_name");
                    if (propName != null && propName.length() > 28) {
                        propName = propName.substring(0, 25) + "...";
                    }
                    System.out.printf("%-6s %-30s %-10s %-15s %-8s %-8s %-12s %-18s%n",
                        rs.getString("ft_id"),
                        propName,
                        rs.getString("ft_amount"),
                        rs.getString("transaction_date"),
                        rs.getString("invoice_id"),
                        rs.getString("property_id"),
                        rs.getString("invoice_amount"),
                        rs.getString("lease_reference"));
                }
            }

            // Query 2: Properties in invoices table
            System.out.println("\n=== ALL WEST GATE PROPERTIES IN INVOICES TABLE ===");
            String query2 = "SELECT DISTINCT " +
                          "i.property_id, " +
                          "i.lease_reference, " +
                          "i.description " +
                          "FROM invoices i " +
                          "WHERE i.description LIKE '%West Gate%' " +
                          "ORDER BY i.property_id";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-12s %-20s %-60s%n", "PROPERTY_ID", "LEASE_REF", "DESCRIPTION");
                System.out.println("-".repeat(95));
                while (rs.next()) {
                    String desc = rs.getString("description");
                    if (desc != null && desc.length() > 58) {
                        desc = desc.substring(0, 55) + "...";
                    }
                    System.out.printf("%-12s %-20s %-60s%n",
                        rs.getString("property_id"),
                        rs.getString("lease_reference"),
                        desc);
                }
            }

            // Query 3: Check when RENT_INVOICE records were created
            System.out.println("\n=== RENT_INVOICE CREATION TIMELINE ===");
            String query3 = "SELECT " +
                          "DATE(created_at) as creation_date, " +
                          "COUNT(*) as count, " +
                          "MIN(transaction_date) as earliest_txn, " +
                          "MAX(transaction_date) as latest_txn " +
                          "FROM financial_transactions " +
                          "WHERE data_source = 'RENT_INVOICE' " +
                          "GROUP BY DATE(created_at)";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-15s %-10s %-15s %-15s%n", "CREATION_DATE", "COUNT", "EARLIEST_TXN", "LATEST_TXN");
                System.out.println("-".repeat(60));
                while (rs.next()) {
                    System.out.printf("%-15s %-10s %-15s %-15s%n",
                        rs.getString("creation_date"),
                        rs.getString("count"),
                        rs.getString("earliest_txn"),
                        rs.getString("latest_txn"));
                }
            }

            // Query 4: Check all 3 West Gate properties
            System.out.println("\n=== ALL '3 WEST GATE' PROPERTIES IN SYSTEM ===");
            String query4 =
                "SELECT 'invoices' as source, COUNT(*) as count FROM invoices " +
                "WHERE description LIKE '%3 West Gate%' " +
                "UNION ALL " +
                "SELECT 'financial_transactions', COUNT(*) FROM financial_transactions " +
                "WHERE property_name LIKE '%3 West Gate%' " +
                "UNION ALL " +
                "SELECT 'properties table', COUNT(*) FROM properties " +
                "WHERE (property_name LIKE '%3 West Gate%' OR property_address LIKE '%3 West Gate%')";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-30s %-10s%n", "SOURCE", "COUNT");
                System.out.println("-".repeat(45));
                while (rs.next()) {
                    System.out.printf("%-30s %-10s%n",
                        rs.getString("source"),
                        rs.getString("count"));
                }
            }

            // Query 5: Invoice category breakdown
            System.out.println("\n=== INVOICE CATEGORY BREAKDOWN ===");
            String query5 = "SELECT " +
                          "category_name, " +
                          "COUNT(*) as count, " +
                          "COUNT(DISTINCT property_id) as distinct_properties, " +
                          "SUM(amount) as total_amount " +
                          "FROM invoices " +
                          "GROUP BY category_name " +
                          "ORDER BY count DESC";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-20s %-10s %-20s %-15s%n", "CATEGORY", "COUNT", "DISTINCT_PROPERTIES", "TOTAL_AMOUNT");
                System.out.println("-".repeat(70));
                while (rs.next()) {
                    System.out.printf("%-20s %-10s %-20s %-15s%n",
                        rs.getString("category_name"),
                        rs.getString("count"),
                        rs.getString("distinct_properties"),
                        rs.getString("total_amount"));
                }
            }

            // Query 6: Check if property_id is properly set in financial_transactions
            System.out.println("\n=== PROPERTY_ID STATUS IN FINANCIAL_TRANSACTIONS ===");
            String query6 = "SELECT " +
                          "data_source, " +
                          "COUNT(*) as total, " +
                          "SUM(CASE WHEN property_id IS NULL THEN 1 ELSE 0 END) as null_count, " +
                          "SUM(CASE WHEN property_id = '0' THEN 1 ELSE 0 END) as zero_count, " +
                          "SUM(CASE WHEN property_id IS NOT NULL AND property_id != '0' THEN 1 ELSE 0 END) as valid_count " +
                          "FROM financial_transactions " +
                          "WHERE data_source IN ('RENT_INVOICE', 'HISTORICAL_IMPORT', 'INCOMING_PAYMENT') " +
                          "GROUP BY data_source";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {
                System.out.printf("%-25s %-10s %-12s %-12s %-12s%n", "DATA_SOURCE", "TOTAL", "NULL_COUNT", "ZERO_COUNT", "VALID_COUNT");
                System.out.println("-".repeat(75));
                while (rs.next()) {
                    System.out.printf("%-25s %-10s %-12s %-12s %-12s%n",
                        rs.getString("data_source"),
                        rs.getString("total"),
                        rs.getString("null_count"),
                        rs.getString("zero_count"),
                        rs.getString("valid_count"));
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
