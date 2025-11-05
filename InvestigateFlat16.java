import java.sql.*;

public class InvestigateFlat16 {
    public static void main(String[] args) {
        String url = "jdbc:mysql://junction.proxy.rlwy.net:28459/railway";
        String user = "root";
        String password = "tPQVPqAXiBJsyAjHyXZRanYAjJDJpVNL";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("================================================================================");
            System.out.println("FLAT 16 INVESTIGATION");
            System.out.println("================================================================================\n");

            // Find Flat 16
            String propertyQuery = "SELECT id, property_name, payprop_id, monthly_payment " +
                                  "FROM properties " +
                                  "WHERE property_name LIKE '%Flat 16%' OR payprop_id = '5AJ5KVr91M'";

            Long propertyId = null;
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(propertyQuery)) {
                while (rs.next()) {
                    propertyId = rs.getLong("id");
                    System.out.println("Property ID: " + propertyId);
                    System.out.println("Name: " + rs.getString("property_name"));
                    System.out.println("PayProp ID: " + rs.getString("payprop_id"));
                    System.out.println("Monthly Payment: £" + rs.getBigDecimal("monthly_payment"));
                    System.out.println();
                }
            }

            if (propertyId == null) {
                System.out.println("ERROR: Property not found!");
                return;
            }

            // Check unified transactions
            System.out.println("================================================================================");
            System.out.println("UNIFIED TRANSACTIONS (Last 12 months)");
            System.out.println("================================================================================\n");

            String txQuery = "SELECT transaction_date, flow_direction, transaction_type, amount, " +
                           "payprop_data_source, description " +
                           "FROM unified_transactions " +
                           "WHERE property_id = ? " +
                           "AND transaction_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH) " +
                           "ORDER BY transaction_date DESC";

            try (PreparedStatement pstmt = conn.prepareStatement(txQuery)) {
                pstmt.setLong(1, propertyId);
                ResultSet rs = pstmt.executeQuery();

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println(rs.getDate("transaction_date") + " | " +
                                     rs.getString("flow_direction") + " | " +
                                     rs.getString("transaction_type") + " | £" +
                                     rs.getBigDecimal("amount") + " | " +
                                     rs.getString("payprop_data_source") + " | " +
                                     (rs.getString("description") != null ? rs.getString("description").substring(0, Math.min(40, rs.getString("description").length())) : "N/A"));
                }
                System.out.println("\nTotal transactions: " + count + "\n");
            }

            // Summary by type
            System.out.println("TRANSACTION TYPE SUMMARY:");
            System.out.println("================================================================================\n");

            String summaryQuery = "SELECT flow_direction, transaction_type, COUNT(*) as count, SUM(amount) as total " +
                                "FROM unified_transactions " +
                                "WHERE property_id = ? " +
                                "AND transaction_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH) " +
                                "GROUP BY flow_direction, transaction_type " +
                                "ORDER BY flow_direction, transaction_type";

            try (PreparedStatement pstmt = conn.prepareStatement(summaryQuery)) {
                pstmt.setLong(1, propertyId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    System.out.println(rs.getString("flow_direction") + " | " +
                                     rs.getString("transaction_type") + " | " +
                                     "Count: " + rs.getInt("count") + " | " +
                                     "Total: £" + rs.getBigDecimal("total"));
                }
                System.out.println();
            }

            // Check PayProp invoices for occupancy
            System.out.println("================================================================================");
            System.out.println("PAYPROP RENT INSTRUCTIONS (Occupancy Check)");
            System.out.println("================================================================================\n");

            String payPropQuery = "SELECT pei.invoice_type, pei.sync_status, pei.amount, pei.description " +
                                "FROM payprop_export_invoices pei " +
                                "INNER JOIN payprop_export_properties pep ON pei.property_payprop_id = pep.payprop_id " +
                                "WHERE pep.payprop_id = '5AJ5KVr91M'";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(payPropQuery)) {
                int rentCount = 0;
                while (rs.next()) {
                    System.out.println("Type: " + rs.getString("invoice_type") + " | " +
                                     "Status: " + rs.getString("sync_status") + " | £" +
                                     rs.getBigDecimal("amount"));
                    if ("Rent".equals(rs.getString("invoice_type")) && "active".equals(rs.getString("sync_status"))) {
                        rentCount++;
                    }
                }
                System.out.println("\nActive Rent Instructions: " + rentCount);
                System.out.println("Occupancy Status: " + (rentCount > 0 ? "OCCUPIED" : "VACANT") + "\n");
            }

            // Check tenant assignments
            System.out.println("================================================================================");
            System.out.println("TENANT ASSIGNMENTS");
            System.out.println("================================================================================\n");

            String tenantQuery = "SELECT cpa.customer_id, c.first_name, c.last_name, c.email, " +
                               "cpa.start_date, cpa.end_date " +
                               "FROM customer_property_assignments cpa " +
                               "INNER JOIN customers c ON cpa.customer_id = c.customer_id " +
                               "WHERE cpa.property_id = ? AND cpa.assignment_type = 'TENANT' " +
                               "ORDER BY cpa.start_date DESC";

            try (PreparedStatement pstmt = conn.prepareStatement(tenantQuery)) {
                pstmt.setLong(1, propertyId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    String status = (rs.getDate("end_date") == null || rs.getDate("end_date").after(new java.util.Date())) ? "ACTIVE" : "ENDED";
                    System.out.println("Customer ID: " + rs.getLong("customer_id"));
                    System.out.println("Name: " + rs.getString("first_name") + " " + rs.getString("last_name"));
                    System.out.println("Email: " + rs.getString("email"));
                    System.out.println("Start: " + rs.getDate("start_date") + " | End: " + rs.getDate("end_date") + " | Status: " + status);
                    System.out.println();
                }
            }

            // Check for commission/fees in financial_transactions
            System.out.println("================================================================================");
            System.out.println("COMMISSION/FEES IN FINANCIAL_TRANSACTIONS");
            System.out.println("================================================================================\n");

            String commissionQuery = "SELECT date, type, amount, data_source, description " +
                                   "FROM financial_transactions " +
                                   "WHERE property_id = ? " +
                                   "AND date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH) " +
                                   "AND (type LIKE '%commission%' OR type LIKE '%fee%' OR type LIKE '%agency%' OR type LIKE '%Commission%') " +
                                   "ORDER BY date DESC";

            try (PreparedStatement pstmt = conn.prepareStatement(commissionQuery)) {
                pstmt.setLong(1, propertyId);
                ResultSet rs = pstmt.executeQuery();

                int commCount = 0;
                while (rs.next()) {
                    commCount++;
                    System.out.println(rs.getDate("date") + " | " +
                                     rs.getString("type") + " | £" +
                                     rs.getBigDecimal("amount") + " | " +
                                     rs.getString("data_source"));
                }
                System.out.println("\nTotal commission/fee records: " + commCount + "\n");
            }

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
