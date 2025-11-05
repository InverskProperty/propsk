import java.sql.*;

public class QueryFlat10Payments {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== CHECKING PAYMENTS FOR FLAT 10 LEASES ===\n");
            
            // Query payprop_export_payments linked to property
            String sql = "SELECT pep.payprop_id, pep.payment_amount, pep.payment_date, " +
                        "pep.linked_invoice_id, i.lease_reference " +
                        "FROM payprop_export_payments pep " +
                        "LEFT JOIN invoices i ON pep.linked_invoice_id = i.id " +
                        "WHERE i.property_id = 7 " +
                        "ORDER BY pep.payment_date DESC LIMIT 10";
            
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.println("Payment " + count + ":");
                System.out.println("  PayProp Payment ID: " + rs.getString("payprop_id"));
                System.out.println("  Amount: GBP " + rs.getBigDecimal("payment_amount"));
                System.out.println("  Date: " + rs.getDate("payment_date"));
                System.out.println("  Linked to Invoice ID: " + rs.getInt("linked_invoice_id"));
                System.out.println("  Lease Reference: " + rs.getString("lease_reference"));
                System.out.println();
            }
            rs.close();
            
            if (count == 0) {
                System.out.println("NO payments linked to Flat 10 leases found in payprop_export_payments.\n");
            } else {
                System.out.println("SUCCESS: Found " + count + " payments linked to Flat 10 leases\n");
            }

            // Also check batch_payments table
            System.out.println("=== CHECKING BATCH PAYMENTS ===\n");
            String batchSql = "SELECT bp.id, bp.payment_id, bp.amount, bp.payment_date, " +
                            "bp.invoice_id, i.lease_reference " +
                            "FROM batch_payments bp " +
                            "LEFT JOIN invoices i ON bp.invoice_id = i.id " +
                            "WHERE i.property_id = 7 " +
                            "ORDER BY bp.payment_date DESC LIMIT 10";
            
            ResultSet batchRs = stmt.executeQuery(batchSql);
            
            int batchCount = 0;
            while (batchRs.next()) {
                batchCount++;
                System.out.println("Batch Payment " + batchCount + ":");
                System.out.println("  ID: " + batchRs.getInt("id"));
                System.out.println("  Payment ID: " + batchRs.getString("payment_id"));
                System.out.println("  Amount: GBP " + batchRs.getBigDecimal("amount"));
                System.out.println("  Date: " + batchRs.getDate("payment_date"));
                System.out.println("  Linked to Invoice ID: " + batchRs.getInt("invoice_id"));
                System.out.println("  Lease Reference: " + batchRs.getString("lease_reference"));
                System.out.println();
            }
            batchRs.close();
            
            if (batchCount == 0) {
                System.out.println("NO batch payments linked to Flat 10 leases.");
            } else {
                System.out.println("SUCCESS: Found " + batchCount + " batch payments");
            }

            stmt.close();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
