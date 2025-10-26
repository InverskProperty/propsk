import java.sql.*;

public class QueryDatabase {
    public static void main(String[] args) {
        String url = "jdbc:mysql://junction.proxy.rlwy.net:32298/railway";
        String user = "root";
        String password = "wjjSoQZGGIzpLTtYKkQvUeAqjqzVVEP";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected to database successfully!\n");

            // Query 1: Check data sources in financial_transactions
            System.out.println("=== FINANCIAL_TRANSACTIONS DATA SOURCES ===");
            String query1 = "SELECT data_source, COUNT(*) as count, " +
                          "MIN(transaction_date) as earliest, MAX(transaction_date) as latest " +
                          "FROM financial_transactions " +
                          "GROUP BY data_source " +
                          "ORDER BY count DESC";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {

                System.out.printf("%-20s %-10s %-25s %-25s%n",
                    "DATA_SOURCE", "COUNT", "EARLIEST", "LATEST");
                System.out.println("-".repeat(85));

                while (rs.next()) {
                    System.out.printf("%-20s %-10d %-25s %-25s%n",
                        rs.getString("data_source"),
                        rs.getInt("count"),
                        rs.getString("earliest"),
                        rs.getString("latest"));
                }
            }

            System.out.println("\n=== HISTORICAL_TRANSACTIONS DATA SOURCES ===");
            String query2 = "SELECT data_source, COUNT(*) as count, " +
                          "MIN(transaction_date) as earliest, MAX(transaction_date) as latest " +
                          "FROM historical_transactions " +
                          "GROUP BY data_source " +
                          "ORDER BY count DESC";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {

                System.out.printf("%-20s %-10s %-25s %-25s%n",
                    "DATA_SOURCE", "COUNT", "EARLIEST", "LATEST");
                System.out.println("-".repeat(85));

                while (rs.next()) {
                    System.out.printf("%-20s %-10d %-25s %-25s%n",
                        rs.getString("data_source"),
                        rs.getInt("count"),
                        rs.getString("earliest"),
                        rs.getString("latest"));
                }
            }

            System.out.println("\n=== OVERLAPPING DATA ANALYSIS ===");
            String query3 = "SELECT " +
                          "'financial_transactions' as table_name, " +
                          "COUNT(*) as total_records, " +
                          "COUNT(CASE WHEN pay_prop_transaction_id IS NOT NULL THEN 1 END) as payprop_records, " +
                          "COUNT(CASE WHEN pay_prop_transaction_id IS NULL THEN 1 END) as non_payprop_records " +
                          "FROM financial_transactions " +
                          "UNION ALL " +
                          "SELECT " +
                          "'historical_transactions', " +
                          "COUNT(*), " +
                          "COUNT(CASE WHEN pay_prop_id IS NOT NULL THEN 1 END), " +
                          "COUNT(CASE WHEN pay_prop_id IS NULL THEN 1 END) " +
                          "FROM historical_transactions";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {

                System.out.printf("%-30s %-15s %-20s %-20s%n",
                    "TABLE_NAME", "TOTAL_RECORDS", "PAYPROP_RECORDS", "NON_PAYPROP_RECORDS");
                System.out.println("-".repeat(90));

                while (rs.next()) {
                    System.out.printf("%-30s %-15d %-20d %-20d%n",
                        rs.getString("table_name"),
                        rs.getInt("total_records"),
                        rs.getInt("payprop_records"),
                        rs.getInt("non_payprop_records"));
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
