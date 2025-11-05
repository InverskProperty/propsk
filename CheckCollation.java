import java.sql.*;

public class CheckCollation {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== CHECKING COLUMN COLLATION ===\n");

            String sql = "SELECT COLUMN_NAME, COLUMN_TYPE, CHARACTER_SET_NAME, COLLATION_NAME " +
                        "FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = 'railway' AND TABLE_NAME = 'properties' AND COLUMN_NAME = 'payprop_id'";

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            if (rs.next()) {
                System.out.println("Column: " + rs.getString("COLUMN_NAME"));
                System.out.println("Type: " + rs.getString("COLUMN_TYPE"));
                System.out.println("Character Set: " + rs.getString("CHARACTER_SET_NAME"));
                System.out.println("Collation: " + rs.getString("COLLATION_NAME"));
            }

            rs.close();
            stmt.close();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
