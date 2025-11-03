import java.sql.*;

public class InvestigateFlat3 {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String username = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {

            // Check 1: Find the invoice for Flat 3
            System.out.println("=== 1. Invoice for LEASE-BH-F3-2025 ===");
            String invoiceQuery = "SELECT id, lease_reference, description, start_date, end_date FROM invoices WHERE lease_reference = 'LEASE-BH-F3-2025'";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(invoiceQuery)) {
                if (rs.next()) {
                    Long invoiceId = rs.getLong("id");
                    System.out.println("Invoice ID: " + invoiceId);
                    System.out.println("Lease Reference: " + rs.getString("lease_reference"));
                    System.out.println("Description: " + rs.getString("description"));
                    System.out.println("Start Date: " + rs.getDate("start_date"));
                    System.out.println("End Date: " + rs.getDate("end_date"));

                    // Check 2: Find ALL transactions linked to this invoice
                    System.out.println("\n=== 2. ALL Transactions for Invoice " + invoiceId + " ===");
                    String txnQuery = "SELECT id, transaction_date, amount, description, category FROM unified_transactions WHERE invoice_id = " + invoiceId + " ORDER BY transaction_date";
                    try (Statement stmt2 = conn.createStatement(); ResultSet rs2 = stmt2.executeQuery(txnQuery)) {
                        int count = 0;
                        while (rs2.next()) {
                            count++;
                            System.out.println("\nTransaction " + count + ":");
                            System.out.println("  ID: " + rs2.getLong("id"));
                            System.out.println("  Date: " + rs2.getDate("transaction_date"));
                            System.out.println("  Amount: £" + rs2.getBigDecimal("amount"));
                            System.out.println("  Category: " + rs2.getString("category"));
                            System.out.println("  Description: " + rs2.getString("description"));
                        }
                        if (count == 0) {
                            System.out.println("NO transactions found for this invoice!");
                        } else {
                            System.out.println("\nTotal: " + count + " transactions");
                        }
                    }
                } else {
                    System.out.println("NO invoice found with lease_reference = 'LEASE-BH-F3-2025'");
                }
            }

            // Check 3: Search for any payments around April 14, 2025
            System.out.println("\n=== 3. Any Payments Near April 14, 2025 ===");
            String nearbyQuery = "SELECT ut.id, ut.transaction_date, ut.amount, ut.description, ut.invoice_id, i.lease_reference " +
                "FROM unified_transactions ut " +
                "LEFT JOIN invoices i ON ut.invoice_id = i.id " +
                "WHERE ut.transaction_date BETWEEN '2025-04-10' AND '2025-04-20' " +
                "AND ut.category = 'INCOMING_PAYMENT' " +
                "ORDER BY ut.transaction_date";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(nearbyQuery)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\nPayment " + count + ":");
                    System.out.println("  Transaction ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Lease Ref: " + rs.getString("lease_reference"));
                    System.out.println("  Description: " + rs.getString("description"));
                }
                if (count == 0) {
                    System.out.println("NO payments found between April 10-20, 2025");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
