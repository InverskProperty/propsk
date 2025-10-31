import java.sql.*;

public class check_schema {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== UNIFIED_TRANSACTIONS SCHEMA ===\n");
            
            String query = "DESCRIBE unified_transactions";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    System.out.println(rs.getString("Field") + " - " + rs.getString("Type"));
                }
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
