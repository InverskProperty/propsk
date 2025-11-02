import java.sql.*;

public class CheckOtherTransactions {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("========================================");
            System.out.println("CHECKING 'other' TRANSACTIONS");
            System.out.println("========================================\n");

            String query =
                "SELECT id, source_system, transaction_date, amount, description, " +
                "category, transaction_type, flow_direction " +
                "FROM unified_transactions " +
                "WHERE transaction_type = 'other' " +
                "ORDER BY transaction_date";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("Transaction #" + count + ":");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Source: " + rs.getString("source_system"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println("  Category: " + rs.getString("category"));
                    System.out.println("  Type: " + rs.getString("transaction_type"));
                    System.out.println("  Flow: " + rs.getString("flow_direction"));
                    System.out.println();
                }

                System.out.println("Total 'other' transactions: " + count);
            }

            System.out.println("\n========================================");
            System.out.println("CHECKING HISTORICAL EXPENSE CATEGORIES");
            System.out.println("========================================\n");

            String histQuery =
                "SELECT id, transaction_date, amount, description, category, property_id " +
                "FROM historical_transactions " +
                "WHERE category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management') " +
                "  AND category NOT LIKE '%rent%' " +
                "ORDER BY category, transaction_date";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(histQuery)) {

                System.out.println("Historical expense transactions:");
                System.out.println();

                int count = 0;
                String lastCategory = "";

                while (rs.next()) {
                    String category = rs.getString("category");
                    if (!category.equals(lastCategory)) {
                        System.out.println("\n" + category.toUpperCase() + ":");
                        System.out.println("------------------");
                        lastCategory = category;
                    }

                    count++;
                    System.out.println(String.format("  %s | £%-8.2f | %s",
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("description")));
                }

                System.out.println("\n\nTotal historical expense records: " + count);
            }

            System.out.println("\n========================================");
            System.out.println("CHECKING IF EXPENSES IN UNIFIED_TRANSACTIONS");
            System.out.println("========================================\n");

            String checkQuery =
                "SELECT COUNT(*) as count " +
                "FROM unified_transactions ut " +
                "WHERE ut.source_system = 'HISTORICAL' " +
                "  AND ut.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management')";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(checkQuery)) {

                if (rs.next()) {
                    int count = rs.getInt("count");
                    System.out.println("Expense categories found in unified_transactions: " + count);

                    if (count == 0) {
                        System.out.println("\n⚠️  WARNING: Historical expenses are NOT being imported into unified_transactions!");
                        System.out.println("    Problem: The category matching logic only looks for categories containing 'expense'");
                        System.out.println("    but historical expenses use specific categories like 'cleaning', 'furnishings', etc.");
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
