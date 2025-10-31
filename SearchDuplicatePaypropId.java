import java.sql.*;

public class SearchDuplicatePaypropId {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "LbUYFYPmrLsrLYeZCRzqBFMXzcnQOPIj";
        String searchValue = "d71ebxD9Z5";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== SEARCHING FOR PAYPROP_ID: " + searchValue + " ===");
            System.out.println();

            // 1. Check invoices table
            System.out.println("=== INVOICES TABLE ===");
            String invoiceQuery = "SELECT id, payprop_id, tenant_id, property_id, " +
                "amount, issue_date, created_at, updated_at " +
                "FROM invoices " +
                "WHERE payprop_id = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(invoiceQuery)) {
                pstmt.setString(1, searchValue);
                ResultSet rs = pstmt.executeQuery();

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("INVOICE #" + count + ":");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  PayProp ID: " + rs.getString("payprop_id"));
                    System.out.println("  Tenant ID: " + rs.getLong("tenant_id"));
                    System.out.println("  Property ID: " + rs.getLong("property_id"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Issue Date: " + rs.getDate("issue_date"));
                    System.out.println("  Created: " + rs.getTimestamp("created_at"));
                    System.out.println("  Updated: " + rs.getTimestamp("updated_at"));
                    System.out.println();
                }

                if (count == 0) {
                    System.out.println("No records found in invoices table");
                } else {
                    System.out.println("TOTAL INVOICES WITH THIS PAYPROP_ID: " + count);
                    if (count > 1) {
                        System.out.println("ERROR: DUPLICATE FOUND - This explains the constraint violation!");
                    }
                }
                System.out.println();
            }

            // 2. Check financial_transactions for related data
            System.out.println("=== FINANCIAL_TRANSACTIONS (ICDN_ACTUAL) ===");
            String financialQuery = "SELECT id, payprop_id, data_source, amount, " +
                "transaction_date, created_at " +
                "FROM financial_transactions " +
                "WHERE data_source = 'ICDN_ACTUAL' AND payprop_id = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(financialQuery)) {
                pstmt.setString(1, searchValue);
                ResultSet rs = pstmt.executeQuery();

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("FINANCIAL TRANSACTION #" + count + ":");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  PayProp ID: " + rs.getString("payprop_id"));
                    System.out.println("  Data Source: " + rs.getString("data_source"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Transaction Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Created: " + rs.getTimestamp("created_at"));
                    System.out.println();
                }

                if (count == 0) {
                    System.out.println("No ICDN_ACTUAL records found in financial_transactions");
                } else {
                    System.out.println("TOTAL ICDN TRANSACTIONS: " + count);
                }
                System.out.println();
            }

            // 3. Check unified_transactions
            System.out.println("=== UNIFIED_TRANSACTIONS ===");
            String unifiedQuery = "SELECT id, payprop_id, payprop_data_source, amount, " +
                "transaction_date, created_at " +
                "FROM unified_transactions " +
                "WHERE payprop_id = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(unifiedQuery)) {
                pstmt.setString(1, searchValue);
                ResultSet rs = pstmt.executeQuery();

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("UNIFIED TRANSACTION #" + count + ":");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  PayProp ID: " + rs.getString("payprop_id"));
                    System.out.println("  Data Source: " + rs.getString("payprop_data_source"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Transaction Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Created: " + rs.getTimestamp("created_at"));
                    System.out.println();
                }

                if (count == 0) {
                    System.out.println("No records found in unified_transactions");
                } else {
                    System.out.println("TOTAL UNIFIED TRANSACTIONS: " + count);
                }
                System.out.println();
            }

            // 4. Check invoice_instructions from sync logs
            System.out.println("=== PAYMENT INSTRUCTIONS (if table exists) ===");
            String instructionQuery = "SELECT id, payprop_id, amount, instruction_date, " +
                "created_at, updated_at " +
                "FROM payment_instructions " +
                "WHERE payprop_id = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(instructionQuery)) {
                pstmt.setString(1, searchValue);
                ResultSet rs = pstmt.executeQuery();

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("PAYMENT INSTRUCTION #" + count + ":");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  PayProp ID: " + rs.getString("payprop_id"));
                    System.out.println("  Amount: £" + rs.getBigDecimal("amount"));
                    System.out.println("  Instruction Date: " + rs.getDate("instruction_date"));
                    System.out.println("  Created: " + rs.getTimestamp("created_at"));
                    System.out.println("  Updated: " + rs.getTimestamp("updated_at"));
                    System.out.println();
                }

                if (count == 0) {
                    System.out.println("No records found in payment_instructions");
                } else {
                    System.out.println("TOTAL PAYMENT INSTRUCTIONS: " + count);
                }
            } catch (SQLException e) {
                System.out.println("Table payment_instructions doesn't exist or query failed");
            }
            System.out.println();

            // 5. Summary
            System.out.println("=== SUMMARY ===");
            System.out.println();
            System.out.println("The error 'Duplicate entry for key invoices.payprop_id' occurs when:");
            System.out.println("1. Multiple invoice records try to use the same payprop_id value");
            System.out.println("2. The invoices table has a UNIQUE constraint on payprop_id column");
            System.out.println();
            System.out.println("From sync logs, these instruction IDs tried to use payprop_id '" + searchValue + "':");
            System.out.println("  - PzZy6370Jd");
            System.out.println("  - EyJ6BBLrJj");
            System.out.println("  - BRXEW4v7ZO");
            System.out.println();
            System.out.println("Resolution options:");
            System.out.println("1. DELETE duplicate invoices with this payprop_id (keep only one)");
            System.out.println("2. SET payprop_id = NULL for duplicates (allows multiple NULL values)");
            System.out.println("3. MODIFY invoice enrichment logic to handle duplicates gracefully");
            System.out.println();

        } catch (SQLException e) {
            System.out.println("Database error:");
            e.printStackTrace();
        }
    }
}
