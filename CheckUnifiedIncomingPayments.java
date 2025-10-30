import java.sql.*;
import java.math.BigDecimal;

public class CheckUnifiedIncomingPayments {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== UNIFIED TRANSACTIONS - INCOMING PAYMENTS ANALYSIS ===\n");

            // Query 1: Total incoming payments in unified_transactions
            System.out.println("=== 1. TOTAL INCOMING PAYMENTS (Since Dawn of Time) ===");
            String query1 = """
                SELECT
                    COUNT(*) as total_count,
                    SUM(amount) as total_amount,
                    MIN(transaction_date) as earliest_date,
                    MAX(transaction_date) as latest_date
                FROM unified_transactions
                WHERE source_system = 'PAYPROP'
                  AND payprop_data_source = 'INCOMING_PAYMENT'
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-15s %-20s %-15s %-15s%n",
                    "TOTAL_COUNT", "TOTAL_AMOUNT", "EARLIEST_DATE", "LATEST_DATE");
                System.out.println("-".repeat(75));
                if (rs.next()) {
                    System.out.printf("%-15d GBP%-17.2f %-15s %-15s%n",
                        rs.getInt("total_count"),
                        rs.getBigDecimal("total_amount"),
                        rs.getDate("earliest_date"),
                        rs.getDate("latest_date"));
                }
            }

            // Query 2: Incoming payments WITH invoice linkage
            System.out.println("\n=== 2. INCOMING PAYMENTS WITH INVOICE LINKAGE ===");
            String query2 = """
                SELECT
                    COUNT(*) as linked_count,
                    SUM(amount) as linked_amount
                FROM unified_transactions
                WHERE source_system = 'PAYPROP'
                  AND payprop_data_source = 'INCOMING_PAYMENT'
                  AND invoice_id IS NOT NULL
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-15s %-20s%n", "LINKED_COUNT", "LINKED_AMOUNT");
                System.out.println("-".repeat(40));
                if (rs.next()) {
                    System.out.printf("%-15d GBP%-17.2f%n",
                        rs.getInt("linked_count"),
                        rs.getBigDecimal("linked_amount"));
                }
            }

            // Query 3: Incoming payments WITHOUT invoice linkage
            System.out.println("\n=== 3. INCOMING PAYMENTS WITHOUT INVOICE LINKAGE ===");
            String query3 = """
                SELECT
                    COUNT(*) as unlinked_count,
                    SUM(amount) as unlinked_amount
                FROM unified_transactions
                WHERE source_system = 'PAYPROP'
                  AND payprop_data_source = 'INCOMING_PAYMENT'
                  AND invoice_id IS NULL
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-15s %-20s%n", "UNLINKED_COUNT", "UNLINKED_AMOUNT");
                System.out.println("-".repeat(40));
                if (rs.next()) {
                    System.out.printf("%-15d GBP%-17.2f%n",
                        rs.getInt("unlinked_count"),
                        rs.getBigDecimal("unlinked_amount"));
                }
            }

            // Query 4: Sample unlinked incoming payments
            System.out.println("\n=== 4. SAMPLE UNLINKED INCOMING PAYMENTS (First 10) ===");
            String query4 = """
                SELECT
                    id,
                    transaction_date,
                    amount,
                    property_name,
                    tenant_name,
                    payprop_transaction_id
                FROM unified_transactions
                WHERE source_system = 'PAYPROP'
                  AND payprop_data_source = 'INCOMING_PAYMENT'
                  AND invoice_id IS NULL
                ORDER BY transaction_date DESC
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-10s %-15s %-12s %-30s %-30s%n",
                    "ID", "DATE", "AMOUNT", "PROPERTY", "TENANT");
                System.out.println("-".repeat(110));
                while (rs.next()) {
                    String propertyName = rs.getString("property_name");
                    String tenantName = rs.getString("tenant_name");
                    System.out.printf("%-10d %-15s GBP%-9.2f %-30s %-30s%n",
                        rs.getLong("id"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        propertyName != null ? (propertyName.length() > 30 ? propertyName.substring(0, 27) + "..." : propertyName) : "NULL",
                        tenantName != null ? (tenantName.length() > 30 ? tenantName.substring(0, 27) + "..." : tenantName) : "NULL");
                }
            }

            // Query 5: All transaction types from PayProp in unified
            System.out.println("\n=== 5. ALL PAYPROP TRANSACTION TYPES IN UNIFIED ===");
            String query5 = """
                SELECT
                    payprop_data_source,
                    COUNT(*) as count,
                    COUNT(CASE WHEN invoice_id IS NOT NULL THEN 1 END) as linked,
                    COUNT(CASE WHEN invoice_id IS NULL THEN 1 END) as unlinked,
                    SUM(amount) as total_amount
                FROM unified_transactions
                WHERE source_system = 'PAYPROP'
                GROUP BY payprop_data_source
                ORDER BY count DESC
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-30s %-10s %-10s %-10s %-20s%n",
                    "DATA_SOURCE", "TOTAL", "LINKED", "UNLINKED", "TOTAL_AMOUNT");
                System.out.println("-".repeat(90));
                while (rs.next()) {
                    System.out.printf("%-30s %-10d %-10d %-10d GBP%-17.2f%n",
                        rs.getString("payprop_data_source"),
                        rs.getInt("count"),
                        rs.getInt("linked"),
                        rs.getInt("unlinked"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            System.out.println("\n=== ANALYSIS COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
