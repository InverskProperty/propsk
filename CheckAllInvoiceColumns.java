import java.sql.*;

public class CheckAllInvoiceColumns {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Get all column names from invoices table
            System.out.println("=== All Invoices Table Columns ===");
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "invoices", "%");
            
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String columnType = columns.getString("TYPE_NAME");
                System.out.printf("  %s (%s)\n", columnName, columnType);
            }
            
            // Check sample invoice data
            System.out.println("\n=== Sample Invoice Data ===");
            String sql = "SELECT * FROM invoices LIMIT 3";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData rsMetaData = rs.getMetaData();
                int columnCount = rsMetaData.getColumnCount();
                
                // Print column headers
                for (int i = 1; i <= columnCount; i++) {
                    System.out.print(rsMetaData.getColumnName(i) + "\t");
                }
                System.out.println();
                
                // Print data
                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        System.out.print(rs.getString(i) + "\t");
                    }
                    System.out.println();
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
