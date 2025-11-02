import java.sql.*;

public class CheckInvoiceStructure {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Get column names from invoices table
            System.out.println("=== Invoices Table Structure (Fee-Related Fields) ===");
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "invoices", "%");
            
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String columnType = columns.getString("TYPE_NAME");
                if (columnName.toLowerCase().contains("fee") || 
                    columnName.toLowerCase().contains("commission") || 
                    columnName.toLowerCase().contains("management") ||
                    columnName.toLowerCase().contains("service") ||
                    columnName.toLowerCase().contains("charge") ||
                    columnName.toLowerCase().contains("rent") ||
                    columnName.toLowerCase().contains("amount")) {
                    System.out.printf("  %s (%s)\n", columnName, columnType);
                }
            }
            
            // Check sample invoice data
            System.out.println("\n=== Sample Invoice Data ===");
            String sql = "SELECT id, lease_reference, rent_amount, management_fee, service_charge FROM invoices LIMIT 5";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                System.out.println("ID | Lease Ref | Rent | Mgmt Fee | Service Charge");
                System.out.println("---|-----------|------|----------|----------------");
                while (rs.next()) {
                    System.out.printf("%d | %s | £%.2f | £%.2f | £%.2f\n",
                        rs.getInt("id"),
                        rs.getString("lease_reference"),
                        rs.getBigDecimal("rent_amount"),
                        rs.getBigDecimal("management_fee"),
                        rs.getBigDecimal("service_charge"));
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
