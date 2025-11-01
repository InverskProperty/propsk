import java.sql.*;

public class verify_incoming_complete {
    public static void main(String[] args) {
        String url = System.getenv("DATABASE_URL");
        String user = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // Check INCOMING_PAYMENT completeness
            System.out.println("=== INCOMING_PAYMENT COMPLETENESS CHECK ===\n");
            
            String sql = """
                SELECT
                    COUNT(*) as total,
                    SUM(CASE WHEN invoice_id IS NULL THEN 1 ELSE 0 END) as null_invoice,
                    SUM(CASE WHEN property_id IS NULL THEN 1 ELSE 0 END) as null_property,
                    SUM(CASE WHEN customer_id IS NULL THEN 1 ELSE 0 END) as null_customer,
                    SUM(CASE WHEN invoice_id IS NOT NULL THEN 1 ELSE 0 END) as has_invoice,
                    SUM(CASE WHEN property_id IS NOT NULL THEN 1 ELSE 0 END) as has_property,
                    SUM(CASE WHEN customer_id IS NOT NULL THEN 1 ELSE 0 END) as has_customer
                FROM unified_transactions
                WHERE payprop_data_source = 'INCOMING_PAYMENT'
            """;
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    System.out.println("Total INCOMING_PAYMENT: " + rs.getInt("total"));
                    System.out.println("\nNULL counts:");
                    System.out.println("  invoice_id: " + rs.getInt("null_invoice"));
                    System.out.println("  property_id: " + rs.getInt("null_property"));
                    System.out.println("  customer_id: " + rs.getInt("null_customer"));
                    System.out.println("\nPopulated counts:");
                    System.out.println("  invoice_id: " + rs.getInt("has_invoice"));
                    System.out.println("  property_id: " + rs.getInt("has_property"));
                    System.out.println("  customer_id: " + rs.getInt("has_customer"));
                }
            }
            
            // Sample records
            System.out.println("\n=== SAMPLE INCOMING_PAYMENT RECORDS ===\n");
            
            String sampleSql = """
                SELECT
                    invoice_id, property_id, customer_id,
                    property_name, amount, transaction_date
                FROM unified_transactions
                WHERE payprop_data_source = 'INCOMING_PAYMENT'
                  AND property_name LIKE '%Flat%'
                ORDER BY property_name
                LIMIT 5
            """;
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sampleSql)) {
                while (rs.next()) {
                    System.out.printf("invoice_id=%s, property_id=%s, customer_id=%s, property=\"%s\", amount=£%.2f%n",
                        rs.getString("invoice_id"),
                        rs.getString("property_id"),
                        rs.getString("customer_id"),
                        rs.getString("property_name"),
                        rs.getBigDecimal("amount"));
                }
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
