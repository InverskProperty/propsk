import java.sql.*;

public class CheckPayPropExpenses {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Check if PayProp has expenses
            System.out.println("=== PayProp Expenses Check ===");
            String sql = "SELECT transaction_type, COUNT(*) as count FROM financial_transactions WHERE transaction_type = 'expense' GROUP BY transaction_type";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (!rs.next()) {
                    System.out.println("No expenses found in financial_transactions");
                } else {
                    do {
                        System.out.printf("%s: %d records\n",
                            rs.getString("transaction_type"),
                            rs.getInt("count"));
                    } while (rs.next());
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
