import java.sql.*;
import java.math.BigDecimal;

public class CheckWestGateProperty {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== WEST GATE PROPERTY ANALYSIS ===\n");

            // Query 1: Find all properties with "West Gate" or "Westgate" in name
            System.out.println("=== 1. PROPERTIES WITH 'WEST GATE' OR 'WESTGATE' IN NAME ===");
            String query1 = """
                SELECT
                    id,
                    property_name,
                    payprop_id,
                    created_at
                FROM properties
                WHERE property_name LIKE '%West Gate%'
                   OR property_name LIKE '%Westgate%'
                   OR property_name LIKE '%West%Gate%'
                ORDER BY property_name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
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
                    System.out.println("No properties found with 'West Gate' in name");
                }
            }

            // Query 2: Financial transactions for West Gate properties
            System.out.println("\n=== 2. FINANCIAL_TRANSACTIONS FOR WEST GATE PROPERTIES ===");
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
                WHERE (ft.property_name LIKE '%West Gate%'
                   OR ft.property_name LIKE '%Westgate%'
                   OR ft.property_name LIKE '%West%Gate%')
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
                BigDecimal totalAmount = BigDecimal.ZERO;
                int totalCount = 0;
                while (rs.next()) {
                    found = true;
                    BigDecimal amount = rs.getBigDecimal("total_amount");
                    int count = rs.getInt("transaction_count");
                    totalAmount = totalAmount.add(amount);
                    totalCount += count;
                    System.out.printf("%-25s %-40s %-10d GBP%-17.2f %-10d %-15s %-15s%n",
                        rs.getString("data_source"),
                        rs.getString("property_name"),
                        count,
                        amount,
                        rs.getInt("linked_count"),
                        rs.getDate("earliest"),
                        rs.getDate("latest"));
                }
                if (!found) {
                    System.out.println("No financial transactions found for West Gate properties");
                } else {
                    System.out.println("-".repeat(145));
                    System.out.printf("%-25s %-40s %-10d GBP%-17.2f%n",
                        "TOTAL", "", totalCount, totalAmount);
                }
            }

            // Query 3: Unified transactions for West Gate properties
            System.out.println("\n=== 3. UNIFIED_TRANSACTIONS FOR WEST GATE PROPERTIES ===");
            String query3 = """
                SELECT
                    ut.source_system,
                    ut.property_name,
                    COUNT(*) as transaction_count,
                    SUM(ut.amount) as total_amount,
                    MIN(ut.transaction_date) as earliest,
                    MAX(ut.transaction_date) as latest
                FROM unified_transactions ut
                WHERE ut.property_name LIKE '%West Gate%'
                   OR ut.property_name LIKE '%Westgate%'
                   OR ut.property_name LIKE '%West%Gate%'
                GROUP BY ut.source_system, ut.property_name
                ORDER BY ut.property_name, ut.source_system
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-20s %-40s %-10s %-20s %-15s %-15s%n",
                    "SOURCE_SYSTEM", "PROPERTY_NAME", "COUNT", "TOTAL_AMOUNT", "EARLIEST", "LATEST");
                System.out.println("-".repeat(125));
                boolean found = false;
                BigDecimal totalAmount = BigDecimal.ZERO;
                int totalCount = 0;
                while (rs.next()) {
                    found = true;
                    BigDecimal amount = rs.getBigDecimal("total_amount");
                    int count = rs.getInt("transaction_count");
                    totalAmount = totalAmount.add(amount);
                    totalCount += count;
                    System.out.printf("%-20s %-40s %-10d GBP%-17.2f %-15s %-15s%n",
                        rs.getString("source_system"),
                        rs.getString("property_name"),
                        count,
                        amount,
                        rs.getDate("earliest"),
                        rs.getDate("latest"));
                }
                if (!found) {
                    System.out.println("No unified transactions found for West Gate properties");
                } else {
                    System.out.println("-".repeat(125));
                    System.out.printf("%-20s %-40s %-10d GBP%-17.2f%n",
                        "TOTAL", "", totalCount, totalAmount);
                }
            }

            // Query 4: Sample transactions for "Flat 1" West Gate
            System.out.println("\n=== 4. SAMPLE TRANSACTIONS FOR 'FLAT 1' WEST GATE (First 10) ===");
            String query4 = """
                SELECT
                    ut.id,
                    ut.transaction_date,
                    ut.amount,
                    ut.property_name,
                    ut.description,
                    ut.source_system,
                    ut.lease_reference
                FROM unified_transactions ut
                WHERE (ut.property_name LIKE '%Flat 1%West Gate%'
                   OR ut.property_name LIKE '%Flat 1%Westgate%'
                   OR ut.property_name LIKE '%West Gate%Flat 1%'
                   OR ut.property_name LIKE '%Westgate%Flat 1%')
                ORDER BY ut.transaction_date DESC
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-10s %-15s %-15s %-40s %-20s%n",
                    "ID", "DATE", "AMOUNT", "PROPERTY_NAME", "SOURCE");
                System.out.println("-".repeat(110));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-10d %-15s GBP%-12.2f %-40s %-20s%n",
                        rs.getLong("id"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("property_name"),
                        rs.getString("source_system"));
                }
                if (!found) {
                    System.out.println("No transactions found for 'Flat 1' West Gate");
                }
            }

            // Query 5: Check for "3 West Gate" specifically
            System.out.println("\n=== 5. SPECIFIC CHECK FOR '3 WEST GATE' PROPERTY ===");
            String query5 = """
                SELECT
                    id,
                    property_name,
                    payprop_id,
                    created_at
                FROM properties
                WHERE property_name LIKE '%3%West%Gate%'
                   OR property_name LIKE '%West%Gate%3%'
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
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
                    System.out.println("Property '3 West Gate' NOT FOUND in properties table");
                }
            }

            // Query 6: Transactions for "3 West Gate"
            System.out.println("\n=== 6. TRANSACTIONS FOR '3 WEST GATE' ===");
            String query6 = """
                SELECT
                    ut.source_system,
                    COUNT(*) as count,
                    SUM(ut.amount) as total_amount
                FROM unified_transactions ut
                WHERE ut.property_name LIKE '%3%West%Gate%'
                   OR ut.property_name LIKE '%West%Gate%3%'
                GROUP BY ut.source_system
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {
                System.out.printf("%-20s %-15s %-20s%n",
                    "SOURCE_SYSTEM", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(60));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-20s %-15d GBP%-17.2f%n",
                        rs.getString("source_system"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total_amount"));
                }
                if (!found) {
                    System.out.println("No transactions found for '3 West Gate'");
                }
            }

            // Query 7: Summary - Is West Gate in PayProp data?
            System.out.println("\n=== 7. SUMMARY: WEST GATE IN PAYPROP DATA ===");
            String query7 = """
                SELECT
                    COUNT(*) as payprop_transactions,
                    SUM(amount) as payprop_total
                FROM financial_transactions
                WHERE (property_name LIKE '%West Gate%'
                   OR property_name LIKE '%Westgate%')
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
                        System.out.println("\n[YES] West Gate property IS included in PayProp data");
                        System.out.printf("Contributes: GBP%.2f out of GBP188,935.82 total (%.2f%%)%n",
                            total, (total.doubleValue() / 188935.82) * 100);
                    } else {
                        System.out.println("\n[NO] West Gate property NOT found in PayProp data");
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
