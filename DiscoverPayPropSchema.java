import java.sql.*;

public class DiscoverPayPropSchema {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== PAYPROP TABLE SCHEMA DISCOVERY ===\n");

            String[] tables = {
                "payprop_report_icdn",
                "payprop_export_invoice_instructions",
                "payprop_export_properties",
                "payprop_report_all_payments"
            };

            for (String tableName : tables) {
                System.out.println("\n=== TABLE: " + tableName + " ===");

                try {
                    DatabaseMetaData metaData = conn.getMetaData();
                    ResultSet columns = metaData.getColumns(null, null, tableName, null);

                    System.out.printf("%-30s %-20s %-10s%n", "COLUMN_NAME", "DATA_TYPE", "NULLABLE");
                    System.out.println("-".repeat(65));

                    boolean found = false;
                    while (columns.next()) {
                        found = true;
                        System.out.printf("%-30s %-20s %-10s%n",
                            columns.getString("COLUMN_NAME"),
                            columns.getString("TYPE_NAME"),
                            columns.getString("IS_NULLABLE"));
                    }

                    if (!found) {
                        System.out.println("Table not found or no columns");
                    }
                } catch (SQLException e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.println("\n=== SCHEMA DISCOVERY COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
