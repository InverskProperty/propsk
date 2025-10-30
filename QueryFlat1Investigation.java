import java.sql.*;
import java.math.BigDecimal;

public class QueryFlat1Investigation {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found.");
            System.err.println("Trying to connect anyway...");
        }

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== FLAT 1, 2, 3 WEST GATE - COMMISSION SOURCE INVESTIGATION ===\n");

            // Query 1: Financial transactions breakdown
            System.out.println("=== 1. FINANCIAL TRANSACTIONS BY DATA SOURCE ===");
            String query1 = """
                SELECT
                    data_source,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM financial_transactions
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                GROUP BY data_source
                ORDER BY data_source
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-30s %-10s %-15s%n", "DATA_SOURCE", "COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(60));
                while (rs.next()) {
                    System.out.printf("%-30s %-10d GBP%-12.2f%n",
                        rs.getString("data_source"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Query 2: Commission payments detail
            System.out.println("\n=== 2. COMMISSION PAYMENT DETAILS ===");
            String query2 = """
                SELECT
                    id,
                    property_name,
                    transaction_date,
                    amount,
                    pay_prop_transaction_id,
                    description
                FROM financial_transactions
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                  AND data_source = 'COMMISSION_PAYMENT'
                ORDER BY property_name, transaction_date
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-10s %-25s %-15s %-12s %-25s%n",
                    "ID", "PROPERTY", "DATE", "AMOUNT", "PAYPROP_TXN_ID");
                System.out.println("-".repeat(95));
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("%-10d %-25s %-15s GBP%-9.2f %-25s%n",
                        rs.getLong("id"),
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("pay_prop_transaction_id"));
                }
                if (count == 0) {
                    System.out.println("[NO COMMISSION PAYMENTS FOUND]");
                }
            }

            // Query 3: Check payprop_report_icdn
            System.out.println("\n=== 3. PAYPROP ICDN TRANSACTIONS ===");
            String query3 = """
                SELECT
                    property_name,
                    COUNT(*) as icdn_count,
                    SUM(amount) as total_amount
                FROM payprop_report_icdn
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                GROUP BY property_name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-30s %-15s %-15s%n", "PROPERTY", "ICDN_COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(65));
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("%-30s %-15d GBP%-12.2f%n",
                        rs.getString("property_name"),
                        rs.getInt("icdn_count"),
                        rs.getBigDecimal("total_amount"));
                }
                if (count == 0) {
                    System.out.println("[NO ICDN TRANSACTIONS FOUND]");
                }
            }

            // Query 4: Historical transactions
            System.out.println("\n=== 4. HISTORICAL TRANSACTIONS ===");
            String query4 = """
                SELECT
                    COUNT(*) as historical_count,
                    SUM(amount) as total_amount
                FROM historical_transactions
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-20s %-15s%n", "HISTORICAL_COUNT", "TOTAL_AMOUNT");
                System.out.println("-".repeat(40));
                while (rs.next()) {
                    System.out.printf("%-20d GBP%-12.2f%n",
                        rs.getInt("historical_count"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            // Query 5: Check for ICDN transactions with commission
            System.out.println("\n=== 5. ICDN TRANSACTIONS WITH COMMISSION AMOUNTS ===");
            String query5 = """
                SELECT
                    payprop_id,
                    property_name,
                    transaction_date,
                    amount,
                    commission_amount,
                    commission_percentage
                FROM payprop_report_icdn
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                  AND commission_amount IS NOT NULL
                ORDER BY property_name, transaction_date
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-20s %-25s %-15s %-12s %-12s%n",
                    "PAYPROP_ID", "PROPERTY", "DATE", "AMOUNT", "COMM_AMT");
                System.out.println("-".repeat(90));
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("%-20s %-25s %-15s GBP%-9.2f GBP%-9.2f%n",
                        rs.getString("payprop_id"),
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("commission_amount"));
                }
                if (count == 0) {
                    System.out.println("[NO ICDN WITH COMMISSION FOUND]");
                }
            }

            // Query 6: Trace commission back to ICDN
            System.out.println("\n=== 6. COMMISSION TO ICDN LINKAGE ===");
            String query6 = """
                SELECT
                    ft.id as ft_id,
                    ft.property_name,
                    ft.amount as commission_amount,
                    ft.pay_prop_transaction_id as commission_txn_id,
                    icdn.payprop_id as icdn_payprop_id,
                    icdn.amount as icdn_amount,
                    icdn.commission_amount as icdn_commission_amt
                FROM financial_transactions ft
                LEFT JOIN payprop_report_icdn icdn
                  ON ft.pay_prop_transaction_id = CONCAT('COMM_', icdn.payprop_id)
                WHERE ft.property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                  AND ft.data_source = 'COMMISSION_PAYMENT'
                ORDER BY ft.property_name, ft.transaction_date
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {
                System.out.printf("%-10s %-25s %-12s %-25s %-20s%n",
                    "FT_ID", "PROPERTY", "COMM_AMT", "COMM_TXN_ID", "ICDN_PAYPROP_ID");
                System.out.println("-".repeat(100));
                int count = 0;
                while (rs.next()) {
                    count++;
                    String icdnId = rs.getString("icdn_payprop_id");
                    System.out.printf("%-10d %-25s GBP%-9.2f %-25s %-20s%n",
                        rs.getLong("ft_id"),
                        rs.getString("property_name"),
                        rs.getBigDecimal("commission_amount"),
                        rs.getString("commission_txn_id"),
                        icdnId == null ? "[NOT LINKED]" : icdnId);
                }
                if (count == 0) {
                    System.out.println("[NO COMMISSION PAYMENTS FOUND]");
                }
            }

            System.out.println("\n=== INVESTIGATION COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
