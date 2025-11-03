import java.sql.*;

public class CheckJune17Payments {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String username = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {

            // Check all unified_transactions for June 17 with amount 740
            System.out.println("=== ALL Transactions on June 17, 2025 for £740 ===");
            String unifiedQuery = "SELECT ut.id, ut.transaction_date, ut.amount, ut.description, " +
                "ut.flow_direction, ut.source_system, ut.source_table, ut.source_record_id, " +
                "ut.payprop_data_source, ut.payprop_transaction_id, ut.category, " +
                "i.lease_reference " +
                "FROM unified_transactions ut " +
                "LEFT JOIN invoices i ON ut.invoice_id = i.id " +
                "WHERE ut.transaction_date = '2025-06-17' " +
                "AND ut.amount = 740 " +
                "ORDER BY i.lease_reference, ut.id";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(unifiedQuery)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\nTransaction " + count + ":");
                    System.out.println("  Unified TX ID: " + rs.getLong("id"));
                    System.out.println("  Lease: " + rs.getString("lease_reference"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Flow Direction: " + rs.getString("flow_direction"));
                    System.out.println("  Source System: " + rs.getString("source_system"));
                    System.out.println("  Source Table: " + rs.getString("source_table"));
                    System.out.println("  Source Record ID: " + rs.getLong("source_record_id"));
                    System.out.println("  PayProp Data Source: " + rs.getString("payprop_data_source"));
                    System.out.println("  PayProp TX ID: " + rs.getString("payprop_transaction_id"));
                    System.out.println("  Category: " + rs.getString("category"));
                    System.out.println("  Description: " + rs.getString("description"));
                }
                System.out.println("\n=== SUMMARY ===");
                System.out.println("Total transactions on June 17 for £740: " + count);
            }

            // Check financial_transactions (PayProp source) for June 17
            System.out.println("\n=== PayProp financial_transactions for June 17, 2025 (£740) ===");
            String paypropQuery = "SELECT ft.id, ft.transaction_date, ft.amount, ft.description, " +
                "ft.category_name, ft.data_source, ft.transaction_type, ft.pay_prop_transaction_id, " +
                "ft.invoice_id, i.lease_reference " +
                "FROM financial_transactions ft " +
                "LEFT JOIN invoices i ON ft.invoice_id = i.id " +
                "WHERE ft.transaction_date = '2025-06-17' " +
                "AND ft.amount = 740 " +
                "ORDER BY i.lease_reference";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(paypropQuery)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\nPayProp Transaction " + count + ":");
                    System.out.println("  FT ID: " + rs.getLong("id"));
                    System.out.println("  Lease: " + rs.getString("lease_reference"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Category: " + rs.getString("category_name"));
                    System.out.println("  Data Source: " + rs.getString("data_source"));
                    System.out.println("  Transaction Type: " + rs.getString("transaction_type"));
                    System.out.println("  PayProp TX ID: " + rs.getString("pay_prop_transaction_id"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println("  Invoice ID: " + rs.getLong("invoice_id"));
                }
                System.out.println("\nTotal PayProp transactions on June 17 for £740: " + count);
            }

            // Now check what Flat 5 actually has in unified_transactions for June
            System.out.println("\n=== Flat 5 June Payments (ALL dates) ===");
            String flat5Query = "SELECT ut.id, ut.transaction_date, ut.amount, ut.description, " +
                "ut.flow_direction, ut.payprop_data_source, ut.payprop_transaction_id " +
                "FROM unified_transactions ut " +
                "LEFT JOIN invoices i ON ut.invoice_id = i.id " +
                "WHERE i.lease_reference = 'LEASE-BH-F5-2025' " +
                "AND ut.transaction_date >= '2025-06-01' " +
                "AND ut.transaction_date <= '2025-06-30' " +
                "AND ut.flow_direction = 'INCOMING' " +
                "ORDER BY ut.transaction_date";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(flat5Query)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\nFlat 5 June Payment " + count + ":");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  PayProp Data Source: " + rs.getString("payprop_data_source"));
                    System.out.println("  PayProp TX ID: " + rs.getString("payprop_transaction_id"));
                    System.out.println("  Description: " + rs.getString("description"));
                }
                System.out.println("\nTotal Flat 5 June INCOMING payments: " + count);
            }

            // Check if there's a June 23 payment for Flat 5 in financial_transactions
            System.out.println("\n=== PayProp Data for Flat 5 June Payments ===");
            String flat5PaypropQuery = "SELECT ft.id, ft.transaction_date, ft.amount, ft.description, " +
                "ft.data_source, ft.transaction_type, ft.pay_prop_transaction_id " +
                "FROM financial_transactions ft " +
                "LEFT JOIN invoices i ON ft.invoice_id = i.id " +
                "WHERE i.lease_reference = 'LEASE-BH-F5-2025' " +
                "AND ft.transaction_date >= '2025-06-01' " +
                "AND ft.transaction_date <= '2025-06-30' " +
                "ORDER BY ft.transaction_date, ft.data_source";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(flat5PaypropQuery)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\nPayProp Record " + count + ":");
                    System.out.println("  FT ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Data Source: " + rs.getString("data_source"));
                    System.out.println("  Transaction Type: " + rs.getString("transaction_type"));
                    System.out.println("  PayProp TX ID: " + rs.getString("pay_prop_transaction_id"));
                    System.out.println("  Description: " + rs.getString("description"));
                }
                System.out.println("\nTotal PayProp records for Flat 5 in June: " + count);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
