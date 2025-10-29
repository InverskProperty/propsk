import java.sql.*;

public class InvoiceSchema {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== INVOICES TABLE STRUCTURE ===");
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "invoices", null);

            System.out.printf("%-30s %-20s %-10s%n", "COLUMN_NAME", "TYPE_NAME", "NULLABLE");
            System.out.println("-".repeat(65));
            while (columns.next()) {
                System.out.printf("%-30s %-20s %-10s%n",
                    columns.getString("COLUMN_NAME"),
                    columns.getString("TYPE_NAME"),
                    columns.getString("IS_NULLABLE"));
            }

            System.out.println("\n=== FINANCIAL_TRANSACTIONS TABLE STRUCTURE ===");
            ResultSet ftColumns = metaData.getColumns(null, null, "financial_transactions", null);

            System.out.printf("%-35s %-20s %-10s%n", "COLUMN_NAME", "TYPE_NAME", "NULLABLE");
            System.out.println("-".repeat(70));
            while (ftColumns.next()) {
                System.out.printf("%-35s %-20s %-10s%n",
                    ftColumns.getString("COLUMN_NAME"),
                    ftColumns.getString("TYPE_NAME"),
                    ftColumns.getString("IS_NULLABLE"));
            }

            // Query sample invoice data
            System.out.println("\n=== SAMPLE INVOICE RECORDS ===");
            String query = "SELECT * FROM invoices LIMIT 5";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                ResultSetMetaData rsmd = rs.getMetaData();
                int columnCount = rsmd.getColumnCount();

                // Print column names
                for (int i = 1; i <= columnCount; i++) {
                    System.out.printf("%-20s ", rsmd.getColumnName(i));
                }
                System.out.println("\n" + "-".repeat(columnCount * 22));

                // Print rows
                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        String value = rs.getString(i);
                        if (value != null && value.length() > 18) {
                            value = value.substring(0, 15) + "...";
                        }
                        System.out.printf("%-20s ", value);
                    }
                    System.out.println();
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
