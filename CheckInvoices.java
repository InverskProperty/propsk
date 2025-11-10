import java.sql.*;
import java.math.BigDecimal;

public class CheckInvoices {
    public static void main(String[] args) {
        String url = "jdbc:mysql://autorack.proxy.rlwy.net:45349/railway";
        String user = "root";
        String password = "bvitbJhYIVGfVIOBlQHWvZFewEFgEwEh";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, user, password);

            // Count total properties
            Statement stmt1 = conn.createStatement();
            ResultSet rs1 = stmt1.executeQuery("SELECT COUNT(*) as total FROM properties");
            rs1.next();
            int totalProperties = rs1.getInt("total");

            // Count properties with invoices
            Statement stmt2 = conn.createStatement();
            ResultSet rs2 = stmt2.executeQuery(
                "SELECT COUNT(DISTINCT property_id) as with_invoices FROM invoices"
            );
            rs2.next();
            int propertiesWithInvoices = rs2.getInt("with_invoices");

            // Get some sample invoices
            Statement stmt3 = conn.createStatement();
            ResultSet rs3 = stmt3.executeQuery(
                "SELECT i.id, p.property_name, i.amount, i.payment_day, i.start_date, i.end_date, i.frequency " +
                "FROM invoices i " +
                "JOIN properties p ON i.property_id = p.id " +
                "ORDER BY i.id DESC LIMIT 10"
            );

            System.out.println("=== INVOICE/LEASE COVERAGE ===\n");
            System.out.println("Total Properties: " + totalProperties);
            System.out.println("Properties with Invoices/Leases: " + propertiesWithInvoices);
            System.out.println("Properties WITHOUT Invoices/Leases: " + (totalProperties - propertiesWithInvoices));
            System.out.println("\nCoverage: " + String.format("%.1f%%", (propertiesWithInvoices * 100.0 / totalProperties)));

            System.out.println("\n=== SAMPLE INVOICES/LEASES ===\n");
            System.out.printf("%-5s %-30s %-10s %-12s %-12s %-12s %-10s\n",
                "ID", "Property", "Amount", "Pay Day", "Start Date", "End Date", "Frequency");
            System.out.println("-".repeat(100));

            while (rs3.next()) {
                long id = rs3.getLong("id");
                String propName = rs3.getString("property_name");
                if (propName != null && propName.length() > 30) propName = propName.substring(0, 27) + "...";
                BigDecimal amount = rs3.getBigDecimal("amount");
                Integer payDay = (Integer) rs3.getObject("payment_day");
                Date startDate = rs3.getDate("start_date");
                Date endDate = rs3.getDate("end_date");
                String frequency = rs3.getString("frequency");

                System.out.printf("%-5d %-30s £%-9.2f %-12s %-12s %-12s %-10s\n",
                    id,
                    propName != null ? propName : "N/A",
                    amount != null ? amount : BigDecimal.ZERO,
                    payDay != null ? ("Day " + payDay) : "N/A",
                    startDate != null ? startDate.toString() : "N/A",
                    endDate != null ? endDate.toString() : "Ongoing",
                    frequency != null ? frequency : "N/A");
            }

            // Check if all leases have required fields
            System.out.println("\n=== DATA COMPLETENESS CHECK ===\n");
            Statement stmt4 = conn.createStatement();
            ResultSet rs4 = stmt4.executeQuery(
                "SELECT " +
                "SUM(CASE WHEN amount IS NULL THEN 1 ELSE 0 END) as missing_amount, " +
                "SUM(CASE WHEN start_date IS NULL THEN 1 ELSE 0 END) as missing_start_date, " +
                "SUM(CASE WHEN payment_day IS NULL THEN 1 ELSE 0 END) as missing_payment_day " +
                "FROM invoices"
            );
            rs4.next();
            int missingAmount = rs4.getInt("missing_amount");
            int missingStartDate = rs4.getInt("missing_start_date");
            int missingPaymentDay = rs4.getInt("missing_payment_day");

            System.out.println("Invoices missing amount: " + missingAmount);
            System.out.println("Invoices missing start_date: " + missingStartDate);
            System.out.println("Invoices missing payment_day: " + missingPaymentDay);

            conn.close();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
