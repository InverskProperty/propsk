import java.sql.*;

public class QueryFlat10Transactions {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== CHECKING PayProp INVOICES FOR FLAT 10 ===\n");
            
            // First find the property's PayProp ID
            String propSql = "SELECT payprop_id FROM properties WHERE id = 7";
            Statement stmt = conn.createStatement();
            ResultSet propRs = stmt.executeQuery(propSql);
            String propertyPayPropId = null;
            if (propRs.next()) {
                propertyPayPropId = propRs.getString("payprop_id");
                System.out.println("Property 7 PayProp ID: " + propertyPayPropId + "\n");
            }
            propRs.close();
            
            if (propertyPayPropId == null) {
                System.out.println("Property 7 has no PayProp ID!");
                return;
            }

            // Query PayProp invoices for this property
            String sql = "SELECT payprop_id, invoice_type, gross_amount, vat_amount, " +
                        "from_date, to_date, description, reference, sync_status, " +
                        "tenant_display_name " +
                        "FROM payprop_export_invoices " +
                        "WHERE property_payprop_id = ? " +
                        "ORDER BY from_date DESC LIMIT 10";
            
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, propertyPayPropId);
            ResultSet rs = pstmt.executeQuery();
            
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.println("Invoice " + count + ":");
                System.out.println("  PayProp ID: " + rs.getString("payprop_id"));
                System.out.println("  Type: " + rs.getString("invoice_type"));
                System.out.println("  Gross Amount: GBP " + rs.getBigDecimal("gross_amount"));
                System.out.println("  VAT: GBP " + rs.getBigDecimal("vat_amount"));
                System.out.println("  Period: " + rs.getDate("from_date") + " to " + rs.getDate("to_date"));
                System.out.println("  Tenant: " + rs.getString("tenant_display_name"));
                System.out.println("  Status: " + rs.getString("sync_status"));
                System.out.println("  Description: " + rs.getString("description"));
                System.out.println();
            }
            rs.close();
            pstmt.close();
            
            if (count == 0) {
                System.out.println("NO PayProp invoices found for property " + propertyPayPropId);
            } else {
                System.out.println("SUCCESS: Total PayProp invoices found: " + count);
            }

            stmt.close();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
