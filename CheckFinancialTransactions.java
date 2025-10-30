import java.sql.*;
import java.math.BigDecimal;

public class CheckFinancialTransactions {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== FINANCIAL TRANSACTIONS ANALYSIS ===\n");

            // Query 1: INCOMING_PAYMENT totals
            System.out.println("=== 1. INCOMING PAYMENTS (financial_transactions) ===");
            String query1 = """
                SELECT
                    COUNT(*) as total_count,
                    SUM(amount) as total_amount,
                    COUNT(CASE WHEN invoice_id IS NOT NULL THEN 1 END) as linked,
                    COUNT(CASE WHEN invoice_id IS NULL THEN 1 END) as unlinked
                FROM financial_transactions
                WHERE data_source = 'INCOMING_PAYMENT'
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-15s %-20s %-15s %-15s%n",
                    "TOTAL_COUNT", "TOTAL_AMOUNT", "LINKED", "UNLINKED");
                System.out.println("-".repeat(75));
                if (rs.next()) {
                    System.out.printf("%-15d GBP%-17.2f %-15d %-15d%n",
                        rs.getInt("total_count"),
                        rs.getBigDecimal("total_amount"),
                        rs.getInt("linked"),
                        rs.getInt("unlinked"));
                }
            }

            // Query 2: All data sources with linkage
            System.out.println("\n=== 2. ALL DATA SOURCES (financial_transactions) ===");
            String query2 = """
                SELECT
                    data_source,
                    COUNT(*) as total,
                    COUNT(CASE WHEN invoice_id IS NOT NULL THEN 1 END) as linked,
                    COUNT(CASE WHEN invoice_id IS NULL THEN 1 END) as unlinked,
                    SUM(amount) as total_amount
                FROM financial_transactions
                WHERE data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
                GROUP BY data_source
                ORDER BY total DESC
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-30s %-10s %-10s %-10s %-20s%n",
                    "DATA_SOURCE", "TOTAL", "LINKED", "UNLINKED", "TOTAL_AMOUNT");
                System.out.println("-".repeat(90));
                while (rs.next()) {
                    System.out.printf("%-30s %-10d %-10d %-10d GBP%-17.2f%n",
                        rs.getString("data_source"),
                        rs.getInt("total"),
                        rs.getInt("linked"),
                        rs.getInt("unlinked"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Query 3: Unified transactions totals
            System.out.println("\n=== 3. UNIFIED TRANSACTIONS TOTALS ===");
            String query3 = """
                SELECT
                    COUNT(*) as total_count,
                    SUM(amount) as total_amount
                FROM unified_transactions
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-15s %-20s%n", "TOTAL_COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(40));
                if (rs.next()) {
                    System.out.printf("%-15d GBP%-17.2f%n",
                        rs.getInt("total_count"),
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
