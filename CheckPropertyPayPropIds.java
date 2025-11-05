import java.sql.*;

public class CheckPropertyPayPropIds {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        String[] testIds = {
            "oRZQ8ldM1m",  // Flat 26 - Louis Scotting - FAILED in logs
            "08JL4wzmJR",  // Flat 6 - Shelley Lees - FAILED in logs
            "5AJ5KVr91M",  // Flat 16 - FAILED in logs
            "7QZGPmabJ9",  // Flat 17 - FAILED in logs
            "EyJ6K7RxXj",  // Apartment 40 - FAILED in logs
            "d71eApoB15",  // Flat 28 - EXISTS in DB
            "d71eApon15",  // Flat 28 variant - EXISTS in DB
            "8EJAnY8VXj"   // Flat 10 - FAILED in logs
        };

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("✅ Connected to database\n");
            System.out.println("=== TESTING PROPERTY LOOKUPS ===\n");

            String sql = "SELECT id, property_name, payprop_id, CHAR_LENGTH(payprop_id) as len, HEX(payprop_id) as hex FROM properties WHERE payprop_id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);

            for (String testId : testIds) {
                stmt.setString(1, testId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    System.out.println(String.format("✅ FOUND: %-15s -> Property #%-3d: %-30s (len=%d, hex=%s)",
                        testId, rs.getInt("id"), rs.getString("property_name"), rs.getInt("len"), rs.getString("hex")));
                } else {
                    System.out.println(String.format("NOT FOUND: %s", testId));
                }
                rs.close();
            }

            stmt.close();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
