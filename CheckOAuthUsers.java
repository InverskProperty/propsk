import java.sql.*;

public class CheckOAuthUsers {
    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(
            "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway",
            "root", "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW")) {

            System.out.println("=== USERS ===");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, username, email FROM users")) {
                while (rs.next()) {
                    System.out.printf("User ID: %d, Username: %s, Email: %s%n",
                        rs.getInt("id"), rs.getString("username"), rs.getString("email"));
                }
            }

            System.out.println("\n=== OAUTH USERS TABLE STRUCTURE ===");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM oauth_users")) {
                while (rs.next()) {
                    System.out.printf("Column: %s, Type: %s%n",
                        rs.getString("Field"), rs.getString("Type"));
                }
            }

            System.out.println("\n=== OAUTH USERS ===");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM oauth_users LIMIT 5")) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                while (rs.next()) {
                    System.out.println("OAuth User Record:");
                    for (int i = 1; i <= colCount; i++) {
                        System.out.printf("  %s: %s%n", meta.getColumnName(i), rs.getObject(i));
                    }
                    System.out.println();
                }
            }

            System.out.println("\n=== PAYPROP OAUTH TOKENS ===");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM payprop_oauth_tokens")) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.println("PayProp Token Record:");
                    for (int i = 1; i <= colCount; i++) {
                        Object val = rs.getObject(i);
                        if (meta.getColumnName(i).contains("token") && val != null) {
                            String valStr = val.toString();
                            System.out.printf("  %s: %s...%s (len=%d)%n",
                                meta.getColumnName(i),
                                valStr.substring(0, Math.min(20, valStr.length())),
                                valStr.length() > 20 ? valStr.substring(valStr.length() - 10) : "",
                                valStr.length());
                        } else {
                            System.out.printf("  %s: %s%n", meta.getColumnName(i), val);
                        }
                    }
                    System.out.println();
                }
                if (!found) {
                    System.out.println("[NO PAYPROP TOKENS FOUND - This is why sync fails!]");
                }
            } catch (Exception e) {
                System.out.println("[payprop_oauth_tokens table doesn't exist: " + e.getMessage() + "]");
            }
        }
    }
}
