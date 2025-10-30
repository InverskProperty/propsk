import java.sql.*;
import java.math.BigDecimal;

public class CheckBodenProperty {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== BODEN PROPERTY ANALYSIS ===\n");

            // Query 1: Find all properties with "Boden" in name
            System.out.println("=== 1. PROPERTIES WITH 'BODEN' IN NAME ===");
            String query1 = """
                SELECT
                    id,
                    property_name,
                    payprop_id
                FROM properties
                WHERE property_name LIKE '%Boden%'
                ORDER BY property_name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-10s %-40s %-20s%n",
                    "ID", "PROPERTY_NAME", "PAYPROP_ID");
                System.out.println("-".repeat(75));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-10d %-40s %-20s%n",
                        rs.getLong("id"),
                        rs.getString("property_name"),
                        rs.getString("payprop_id"));
                }
                if (!found) {
                    System.out.println("No properties found with 'Boden' in name");
                }
            }

            // Query 2: Financial transactions for Boden properties
            System.out.println("\n=== 2. FINANCIAL_TRANSACTIONS FOR BODEN PROPERTIES ===");
            String query2 = """
                SELECT
                    ft.data_source,
                    ft.property_name,
                    COUNT(*) as transaction_count,
                    SUM(ft.amount) as total_amount,
                    COUNT(CASE WHEN ft.invoice_id IS NOT NULL THEN 1 END) as linked_count,
                    MIN(ft.transaction_date) as earliest,
                    MAX(ft.transaction_date) as latest
                FROM financial_transactions ft
                WHERE ft.property_name LIKE '%Boden%'
                  AND ft.data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                GROUP BY ft.data_source, ft.property_name
                ORDER BY ft.property_name, ft.data_source
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-25s %-40s %-10s %-20s %-10s %-15s %-15s%n",
                    "DATA_SOURCE", "PROPERTY_NAME", "COUNT", "TOTAL_AMOUNT", "LINKED", "EARLIEST", "LATEST");
                System.out.println("-".repeat(145));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-25s %-40s %-10d GBP%-17.2f %-10d %-15s %-15s%n",
                        rs.getString("data_source"),
                        rs.getString("property_name"),
                        rs.getInt("transaction_count"),
                        rs.getBigDecimal("total_amount"),
                        rs.getInt("linked_count"),
                        rs.getDate("earliest"),
                        rs.getDate("latest"));
                }
                if (!found) {
                    System.out.println("No financial transactions found for Boden properties");
                }
            }

            // Query 3: Historical transactions for Boden properties (skip - check unified instead)
            System.out.println("\n=== 3. HISTORICAL_TRANSACTIONS FOR BODEN PROPERTIES ===");
            System.out.println("(Skipping - will check in unified_transactions)");

            // Query 4: Unified transactions for Boden properties
            System.out.println("\n=== 4. UNIFIED_TRANSACTIONS FOR BODEN PROPERTIES ===");
            String query4 = """
                SELECT
                    ut.source_system,
                    ut.property_name,
                    COUNT(*) as transaction_count,
                    SUM(ut.amount) as total_amount,
                    MIN(ut.transaction_date) as earliest,
                    MAX(ut.transaction_date) as latest
                FROM unified_transactions ut
                WHERE ut.property_name LIKE '%Boden%'
                GROUP BY ut.source_system, ut.property_name
                ORDER BY ut.property_name, ut.source_system
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-20s %-40s %-10s %-20s %-15s %-15s%n",
                    "SOURCE_SYSTEM", "PROPERTY_NAME", "COUNT", "TOTAL_AMOUNT", "EARLIEST", "LATEST");
                System.out.println("-".repeat(125));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-20s %-40s %-10d GBP%-17.2f %-15s %-15s%n",
                        rs.getString("source_system"),
                        rs.getString("property_name"),
                        rs.getInt("transaction_count"),
                        rs.getBigDecimal("total_amount"),
                        rs.getDate("earliest"),
                        rs.getDate("latest"));
                }
                if (!found) {
                    System.out.println("No unified transactions found for Boden properties");
                }
            }

            // Query 5: Sample transactions for "Flat 1 Boden"
            System.out.println("\n=== 5. SAMPLE TRANSACTIONS FOR 'FLAT 1 BODEN' (First 10) ===");
            String query5 = """
                SELECT
                    ut.id,
                    ut.transaction_date,
                    ut.amount,
                    ut.description,
                    ut.source_system,
                    ut.lease_reference
                FROM unified_transactions ut
                WHERE ut.property_name LIKE '%Flat 1%Boden%'
                   OR ut.property_name LIKE '%Boden%Flat 1%'
                ORDER BY ut.transaction_date DESC
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-10s %-15s %-15s %-20s %-50s%n",
                    "ID", "DATE", "AMOUNT", "SOURCE", "DESCRIPTION");
                System.out.println("-".repeat(120));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    String description = rs.getString("description");
                    if (description != null && description.length() > 50) {
                        description = description.substring(0, 47) + "...";
                    }
                    System.out.printf("%-10d %-15s GBP%-12.2f %-20s %-50s%n",
                        rs.getLong("id"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("source_system"),
                        description);
                }
                if (!found) {
                    System.out.println("No transactions found for 'Flat 1 Boden'");
                }
            }

            // Query 6: Check if property exists in properties table
            System.out.println("\n=== 6. SPECIFIC CHECK FOR 'FLAT 1 BODEN' PROPERTY ===");
            String query6 = """
                SELECT
                    id,
                    property_name,
                    payprop_id,
                    created_at
                FROM properties
                WHERE property_name LIKE '%Flat 1%'
                  AND property_name LIKE '%Boden%'
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {
                System.out.printf("%-10s %-50s %-20s %-20s%n",
                    "ID", "PROPERTY_NAME", "PAYPROP_ID", "CREATED_AT");
                System.out.println("-".repeat(105));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-10d %-50s %-20s %-20s%n",
                        rs.getLong("id"),
                        rs.getString("property_name"),
                        rs.getString("payprop_id"),
                        rs.getTimestamp("created_at"));
                }
                if (!found) {
                    System.out.println("Property 'Flat 1 Boden' NOT FOUND in properties table");
                }
            }

            // Query 7: All Boden transactions breakdown by source
            System.out.println("\n=== 7. SUMMARY: BODEN TRANSACTIONS IN PAYPROP DATA ===");
            String query7 = """
                SELECT
                    COUNT(*) as payprop_transactions,
                    SUM(amount) as payprop_total
                FROM financial_transactions
                WHERE property_name LIKE '%Boden%'
                  AND data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                  AND invoice_id IS NOT NULL
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query7)) {
                System.out.printf("%-25s %-20s%n", "PAYPROP_TRANSACTIONS", "PAYPROP_TOTAL");
                System.out.println("-".repeat(50));
                if (rs.next()) {
                    int count = rs.getInt("payprop_transactions");
                    BigDecimal total = rs.getBigDecimal("payprop_total");
                    System.out.printf("%-25d GBP%-17.2f%n", count, total);

                    if (count > 0) {
                        System.out.println("\n[YES] Boden property IS included in PayProp data");
                    } else {
                        System.out.println("\n[NO] Boden property NOT found in PayProp data");
                    }
                }
            }

            System.out.println("\n=== ANALYSIS COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
