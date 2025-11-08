import java.sql.*;

public class QueryMySQL {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java QueryMySQL \"<SQL_QUERY>\"");
            return;
        }

        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String username = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, username, password);
            Statement stmt = conn.createStatement();

            String query = args[0];
            String queryUpper = query.trim().toUpperCase();

            if (queryUpper.startsWith("SELECT") || queryUpper.startsWith("SHOW") || queryUpper.startsWith("DESCRIBE") || queryUpper.startsWith("DESC")) {
                ResultSet rs = stmt.executeQuery(query);
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // Print column headers
                for (int i = 1; i <= columnCount; i++) {
                    System.out.print(metaData.getColumnName(i));
                    if (i < columnCount) System.out.print(" | ");
                }
                System.out.println();
                System.out.println("=".repeat(80));

                // Print rows
                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        System.out.print(rs.getString(i));
                        if (i < columnCount) System.out.print(" | ");
                    }
                    System.out.println();
                }
                rs.close();
            } else {
                int rowsAffected = stmt.executeUpdate(query);
                System.out.println("Rows affected: " + rowsAffected);
            }

            stmt.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
