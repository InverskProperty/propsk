import java.sql.*;
import java.math.BigDecimal;

public class CheckPayPropPayments {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found.");
        }

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== CHECKING PAYPROP PAYMENT DATA FOR FLAT 1 ===\n");

            // Check payprop_export_payments
            System.out.println("=== 1. payprop_export_payments ===");
            String query1 = "SELECT * FROM payprop_export_payments WHERE property_payprop_id = 'KAXNvEqAXk'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {

                ResultSetMetaData rsmd = rs.getMetaData();
                int colCount = rsmd.getColumnCount();

                int rowNum = 0;
                while (rs.next()) {
                    rowNum++;
                    System.out.println("Payment Record #" + rowNum + ":");
                    for (int i = 1; i <= colCount; i++) {
                        String colName = rsmd.getColumnName(i);
                        Object value = rs.getObject(i);
                        System.out.println("  " + colName + ": " + value);
                    }
                    System.out.println();
                }
            }

            // Check payprop_export_incoming_payments
            System.out.println("\n=== 2. payprop_export_incoming_payments ===");
            String query2 = "SELECT * FROM payprop_export_incoming_payments WHERE property_name = 'Flat 1 - 3 West Gate' OR property_name LIKE '%KAXNvEqAXk%'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {

                ResultSetMetaData rsmd = rs.getMetaData();
                int colCount = rsmd.getColumnCount();

                int rowNum = 0;
                while (rs.next()) {
                    rowNum++;
                    System.out.println("Incoming Payment #" + rowNum + ":");
                    for (int i = 1; i <= colCount; i++) {
                        String colName = rsmd.getColumnName(i);
                        Object value = rs.getObject(i);
                        System.out.println("  " + colName + ": " + value);
                    }
                    System.out.println();
                }

                if (rowNum == 0) {
                    System.out.println("[NO INCOMING PAYMENTS FOUND]");
                }
            }

            // Check financial_transactions for INCOMING_PAYMENT
            System.out.println("\n=== 3. financial_transactions INCOMING_PAYMENT ===");
            String query3 = """
                SELECT *
                FROM financial_transactions
                WHERE property_id = 'KAXNvEqAXk'
                  AND data_source = 'INCOMING_PAYMENT'
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {

                int rowNum = 0;
                while (rs.next()) {
                    rowNum++;
                    System.out.println("Transaction #" + rowNum + ":");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println();
                }

                if (rowNum == 0) {
                    System.out.println("[NO INCOMING_PAYMENT IN financial_transactions]");
                }
            }

            // Check what IS in financial_transactions for this property
            System.out.println("\n=== 4. ALL financial_transactions for KAXNvEqAXk ===");
            String query4 = """
                SELECT
                    data_source,
                    COUNT(*) as count,
                    SUM(amount) as total
                FROM financial_transactions
                WHERE property_id = 'KAXNvEqAXk'
                GROUP BY data_source
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {

                System.out.printf("%-30s %-10s %-15s%n", "DATA_SOURCE", "COUNT", "TOTAL");
                System.out.println("-".repeat(60));
                while (rs.next()) {
                    System.out.printf("%-30s %-10d GBP%-12.2f%n",
                        rs.getString("data_source"),
                        rs.getInt("count"),
                        rs.getBigDecimal("total"));
                }
            }

            // Check local properties table
            System.out.println("\n=== 5. Local properties table ===");
            String query5 = "SELECT * FROM properties WHERE payprop_id = 'KAXNvEqAXk'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {

                if (rs.next()) {
                    System.out.println("Local Property Record:");
                    ResultSetMetaData rsmd = rs.getMetaData();
                    for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                        String colName = rsmd.getColumnName(i);
                        Object value = rs.getObject(i);
                        if (value != null) {
                            System.out.println("  " + colName + ": " + value);
                        }
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
