import java.sql.*;

public class ShowPayPropTables {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== PAYPROP TABLES IN DATABASE ===\n");

            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(null, null, "payprop%", new String[]{"TABLE"});

            System.out.printf("%-50s%n", "TABLE_NAME");
            System.out.println("-".repeat(50));

            while (tables.next()) {
                System.out.printf("%-50s%n", tables.getString("TABLE_NAME"));
            }

            // Now let's check one of those tables for Flat 1
            System.out.println("\n=== CHECKING payprop_export_financials for Flat 1 ===");
            String query = """
                SELECT *
                FROM payprop_export_financials
                WHERE property_name = 'Flat 1 - 3 West Gate'
                LIMIT 5
                """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                ResultSetMetaData rsMetaData = rs.getMetaData();
                int columnCount = rsMetaData.getColumnCount();

                // Print column names
                for (int i = 1; i <= columnCount; i++) {
                    System.out.printf("%-20s ", rsMetaData.getColumnName(i));
                }
                System.out.println();
                System.out.println("-".repeat(columnCount * 21));

                // Print data
                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        String value = rs.getString(i);
                        if (value != null && value.length() > 20) {
                            value = value.substring(0, 17) + "...";
                        }
                        System.out.printf("%-20s ", value);
                    }
                    System.out.println();
                }
            } catch (SQLException e) {
                System.out.println("Error or table doesn't exist: " + e.getMessage());
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
