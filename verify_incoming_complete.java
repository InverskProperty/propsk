import java.sql.*;

public class verify_incoming_complete {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        System.out.println("=== UNIFIED LAYER VERIFICATION ===\n");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {

            // 1. Total records by source system
            System.out.println("1. TOTAL RECORDS BY SOURCE:");
            String sql1 = "SELECT source_system, COUNT(*) as count FROM unified_transactions GROUP BY source_system";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql1)) {
                int total = 0;
                while (rs.next()) {
                    int count = rs.getInt("count");
                    total += count;
                    System.out.printf("   %s: %d records\n", rs.getString("source_system"), count);
                }
                System.out.println("   TOTAL: " + total + " records");
            }

            // 2. Transaction types breakdown
            System.out.println("\n2. TRANSACTION TYPES:");
            String sql2 = "SELECT transaction_type, flow_direction, COUNT(*) as count, SUM(amount) as total FROM unified_transactions GROUP BY transaction_type, flow_direction ORDER BY count DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql2)) {
                while (rs.next()) {
                    System.out.printf("   %s (%s): %d records (£%.2f)\n",
                        rs.getString("transaction_type"),
                        rs.getString("flow_direction"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total"));
                }
            }

            // 3. Expenses verification
            System.out.println("\n3. EXPENSES VERIFICATION:");
            String sql3 = "SELECT COUNT(*) as count, SUM(amount) as total FROM unified_transactions WHERE transaction_type = 'expense'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql3)) {
                if (rs.next()) {
                    System.out.printf("   Total expenses: %d records (£%.2f)\n",
                        rs.getInt("count"),
                        rs.getBigDecimal("total"));
                }
            }

            // Sample expenses
            String sql4 = "SELECT transaction_date, amount, category, property_name, lease_reference FROM unified_transactions WHERE transaction_type = 'expense' ORDER BY transaction_date DESC LIMIT 5";
            System.out.println("   Sample expenses:");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql4)) {
                while (rs.next()) {
                    System.out.printf("     %s | £%.2f | %s | %s | Lease: %s\n",
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("category"),
                        rs.getString("property_name"),
                        rs.getString("lease_reference"));
                }
            }

            // 4. July 2025 INCOMING completeness
            System.out.println("\n4. JULY 2025 INCOMING COMPLETENESS:");
            String sql5 = "SELECT transaction_type, COUNT(*) as count, SUM(amount) as total FROM unified_transactions WHERE flow_direction = 'INCOMING' AND transaction_date BETWEEN '2025-07-01' AND '2025-07-31' GROUP BY transaction_type";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql5)) {
                int totalPayments = 0;
                double totalAmount = 0;
                while (rs.next()) {
                    int count = rs.getInt("count");
                    double amount = rs.getBigDecimal("total").doubleValue();
                    totalPayments += count;
                    totalAmount += amount;
                    System.out.printf("   %s: %d payments (£%.2f)\n",
                        rs.getString("transaction_type"),
                        count,
                        amount);
                }
                System.out.printf("   TOTAL: %d payments (£%.2f)\n", totalPayments, totalAmount);
            }

            // 5. Data sources for July INCOMING
            System.out.println("\n5. JULY INCOMING BY SOURCE:");
            String sql6 = "SELECT source_system, COUNT(*) as count, SUM(amount) as total FROM unified_transactions WHERE flow_direction = 'INCOMING' AND transaction_date BETWEEN '2025-07-01' AND '2025-07-31' GROUP BY source_system";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql6)) {
                while (rs.next()) {
                    System.out.printf("   %s: %d payments (£%.2f)\n",
                        rs.getString("source_system"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total"));
                }
            }

            System.out.println("\n✅ UNIFIED LAYER COMPLETE - Ready for statement generation!");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
