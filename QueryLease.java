import java.sql.*;

public class QueryLease {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";
        String leaseRef = "LEASE-KH-F-2024";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== LEASE DETAILS: " + leaseRef + " ===\n");

            // Query invoices table with joins
            String invoiceQuery = "SELECT i.id, i.lease_reference, " +
                                 "c.name as tenant_name, p.property_name, " +
                                 "CONCAT_WS(', ', p.address_line_1, p.city, p.postcode) as full_address, " +
                                 "i.amount, i.start_date, i.end_date, " +
                                 "i.frequency, i.payment_day, i.description, " +
                                 "i.is_active, i.category_name, i.created_at " +
                                 "FROM invoices i " +
                                 "LEFT JOIN customers c ON i.customer_id = c.customer_id " +
                                 "LEFT JOIN properties p ON i.property_id = p.id " +
                                 "WHERE i.lease_reference = ? " +
                                 "LIMIT 1";

            try (PreparedStatement ps = conn.prepareStatement(invoiceQuery)) {
                ps.setString(1, leaseRef);
                ResultSet rs = ps.executeQuery();

                System.out.println("INVOICE/LEASE DETAILS:");
                System.out.println("======================");
                if (rs.next()) {
                    System.out.println("Invoice ID: " + rs.getLong("id"));
                    System.out.println("Lease Reference: " + rs.getString("lease_reference"));
                    System.out.println("Tenant: " + rs.getString("tenant_name"));
                    System.out.println("Property Name: " + rs.getString("property_name"));
                    System.out.println("Property Address: " + rs.getString("full_address"));
                    System.out.println("Rent Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("Frequency: " + rs.getString("frequency"));
                    System.out.println("Payment Day: " + rs.getInt("payment_day"));
                    System.out.println("Lease Period: " + rs.getDate("start_date") + " to " + rs.getDate("end_date"));
                    System.out.println("Active: " + rs.getBoolean("is_active"));
                    System.out.println("Category: " + rs.getString("category_name"));
                    System.out.println("Description: " + rs.getString("description"));
                    System.out.println("Created: " + rs.getTimestamp("created_at"));
                } else {
                    System.out.println("  No invoice record found for " + leaseRef);
                }
            }

            System.out.println("\n");

            // Query unified_transactions
            String txnQuery = "SELECT id, transaction_date, amount, description, category, " +
                            "payprop_data_source, transaction_type, flow_direction " +
                            "FROM unified_transactions WHERE lease_reference = ? " +
                            "ORDER BY transaction_date DESC LIMIT 10";

            try (PreparedStatement ps = conn.prepareStatement(txnQuery)) {
                ps.setString(1, leaseRef);
                ResultSet rs = ps.executeQuery();

                System.out.println("UNIFIED TRANSACTION RECORDS:");
                System.out.println("===========================");
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("Transaction ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println("  Category: " + rs.getString("category"));
                    System.out.println("  Data Source: " + rs.getString("payprop_data_source"));
                    System.out.println("  Type: " + rs.getString("transaction_type"));
                    System.out.println("  Flow: " + rs.getString("flow_direction"));
                    System.out.println();
                }
                if (count == 0) {
                    System.out.println("  No transaction records found");
                } else {
                    System.out.println("Total transactions shown: " + count + " (showing first 10)");
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
