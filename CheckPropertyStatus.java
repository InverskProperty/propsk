import java.sql.*;

public class CheckPropertyStatus {
    public static void main(String[] args) {
        String url = "jdbc:mysql://autorack.proxy.rlwy.net:45349/railway";
        String user = "root";
        String password = "bvitbJhYIVGfVIOBlQHWvZFewEFgEwEh";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, user, password);

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                "SELECT status, COUNT(*) as count FROM properties GROUP BY status ORDER BY count DESC"
            );

            System.out.println("=== STATUS VALUES IN PROPERTIES TABLE ===\n");
            System.out.printf("%-20s %s\n", "Status", "Count");
            System.out.println("-".repeat(40));

            while (rs.next()) {
                String status = rs.getString("status");
                int count = rs.getInt("count");
                System.out.printf("%-20s %5d\n", status == null ? "NULL" : status, count);
            }

            rs.close();
            stmt.close();
            conn.close();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
