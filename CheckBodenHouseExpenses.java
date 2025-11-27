import java.sql.*;

public class CheckBodenHouseExpenses {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=======================================================");
            System.out.println("BODEN HOUSE BLOCK EXPENSE INVESTIGATION");
            System.out.println("=======================================================\n");

            // Search for Boden in payprop_export_properties
            System.out.println("STEP 1: Search 'boden' in payprop_export_properties");
            System.out.println("----------------------------------------------------");

            String bodenQuery = "SELECT * FROM payprop_export_properties WHERE property_name LIKE '%boden%'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(bodenQuery)) {
                ResultSetMetaData meta = rs.getMetaData();
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\n--- Boden Property #" + count + " ---");
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String colName = meta.getColumnName(i);
                        String value = rs.getString(i);
                        if (value != null && !value.isEmpty()) {
                            System.out.println("  " + colName + ": " + value);
                        }
                    }
                }
                if (count == 0) {
                    System.out.println("No properties found with 'boden'");
                }
            }

            System.out.println("\n");

            // Search for Boden in payprop_export_invoices
            System.out.println("STEP 2: Search 'boden' in payprop_export_invoices");
            System.out.println("-------------------------------------------------");

            String bodenInvQuery = "SELECT * FROM payprop_export_invoices WHERE property_name LIKE '%boden%'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(bodenInvQuery)) {
                ResultSetMetaData meta = rs.getMetaData();
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\n--- Boden Invoice #" + count + " ---");
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String colName = meta.getColumnName(i);
                        String value = rs.getString(i);
                        if (value != null && !value.isEmpty()) {
                            System.out.println("  " + colName + ": " + value);
                        }
                    }
                }
                if (count == 0) {
                    System.out.println("No invoices found with 'boden'");
                }
            }

            System.out.println("\n");

            // Search for Boden in payprop_export_payments (distribution rules)
            System.out.println("STEP 3: Search 'boden' in payprop_export_payments (distribution rules)");
            System.out.println("----------------------------------------------------------------------");

            String bodenPaymentsQuery = "SELECT * FROM payprop_export_payments WHERE property_name LIKE '%boden%' OR beneficiary LIKE '%boden%'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(bodenPaymentsQuery)) {
                ResultSetMetaData meta = rs.getMetaData();
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\n--- Boden Distribution Rule #" + count + " ---");
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String colName = meta.getColumnName(i);
                        String value = rs.getString(i);
                        if (value != null && !value.isEmpty()) {
                            System.out.println("  " + colName + ": " + value);
                        }
                    }
                }
                if (count == 0) {
                    System.out.println("No distribution rules found with 'boden'");
                }
            }

            System.out.println("\n");

            // Search for Boden in payprop_export_incoming_payments
            System.out.println("STEP 4: Search 'boden' in payprop_export_incoming_payments");
            System.out.println("----------------------------------------------------------");

            String bodenIncQuery = "SELECT * FROM payprop_export_incoming_payments WHERE property_name LIKE '%boden%'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(bodenIncQuery)) {
                ResultSetMetaData meta = rs.getMetaData();
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\n--- Boden Incoming Payment #" + count + " ---");
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String colName = meta.getColumnName(i);
                        String value = rs.getString(i);
                        if (value != null && !value.isEmpty()) {
                            System.out.println("  " + colName + ": " + value);
                        }
                    }
                }
                if (count == 0) {
                    System.out.println("No incoming payments found with 'boden'");
                }
            }

            System.out.println("\n");

            // Search for Boden in beneficiaries
            System.out.println("STEP 5: Search 'boden' in payprop_export_beneficiaries_complete");
            System.out.println("---------------------------------------------------------------");

            String bodenBenefQuery = "SELECT * FROM payprop_export_beneficiaries_complete WHERE display_name LIKE '%boden%' OR business_name LIKE '%boden%'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(bodenBenefQuery)) {
                ResultSetMetaData meta = rs.getMetaData();
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\n--- Boden Beneficiary #" + count + " ---");
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String colName = meta.getColumnName(i);
                        String value = rs.getString(i);
                        if (value != null && !value.isEmpty()) {
                            System.out.println("  " + colName + ": " + value);
                        }
                    }
                }
                if (count == 0) {
                    System.out.println("No beneficiaries found with 'boden'");
                }
            }

            System.out.println("\n");

            // Look at financial_transactions for Boden
            System.out.println("STEP 6: Search 'boden' in financial_transactions");
            System.out.println("-------------------------------------------------");

            String bodenFTQuery = "SELECT id, transaction_date, property_name, description, amount, category_name, transaction_type, data_source " +
                                  "FROM financial_transactions WHERE property_name LIKE '%boden%' ORDER BY transaction_date DESC LIMIT 20";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(bodenFTQuery)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\n--- Boden Financial Transaction #" + count + " ---");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Property: " + rs.getString("property_name"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println("  Amount: " + rs.getBigDecimal("amount"));
                    System.out.println("  Category: " + rs.getString("category_name"));
                    System.out.println("  Type: " + rs.getString("transaction_type"));
                    System.out.println("  Data Source: " + rs.getString("data_source"));
                }
                if (count == 0) {
                    System.out.println("No financial transactions found with 'boden'");
                }
            }

            System.out.println("\n");

            // Check unified_transactions for Boden
            System.out.println("STEP 7: Search 'boden' in unified_transactions");
            System.out.println("-----------------------------------------------");

            String bodenUTQuery = "SELECT id, transaction_date, property_name, description, amount, category, transaction_type, payprop_data_source " +
                                  "FROM unified_transactions WHERE property_name LIKE '%boden%' ORDER BY transaction_date DESC LIMIT 20";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(bodenUTQuery)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("\n--- Boden Unified Transaction #" + count + " ---");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Date: " + rs.getDate("transaction_date"));
                    System.out.println("  Property: " + rs.getString("property_name"));
                    System.out.println("  Description: " + rs.getString("description"));
                    System.out.println("  Amount: " + rs.getBigDecimal("amount"));
                    System.out.println("  Category: " + rs.getString("category"));
                    System.out.println("  Type: " + rs.getString("transaction_type"));
                    System.out.println("  Data Source: " + rs.getString("payprop_data_source"));
                }
                if (count == 0) {
                    System.out.println("No unified transactions found with 'boden'");
                }
            }

            System.out.println("\n");

            // Check batch_payment_items or similar table for actual payment details
            System.out.println("STEP 8: Check for batch_payment_items or similar");
            System.out.println("-------------------------------------------------");

            String tablesQuery = "SHOW TABLES LIKE '%batch%'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(tablesQuery)) {
                System.out.println("Tables containing 'batch':");
                while (rs.next()) {
                    System.out.println("  - " + rs.getString(1));
                }
            }

            System.out.println("\n");

            // Check all payprop_export tables record counts
            System.out.println("STEP 9: PayProp Export Table Record Counts");
            System.out.println("-------------------------------------------");

            String[] tables = {
                "payprop_export_beneficiaries",
                "payprop_export_beneficiaries_complete",
                "payprop_export_incoming_payments",
                "payprop_export_invoice_instructions",
                "payprop_export_invoices",
                "payprop_export_payments",
                "payprop_export_properties",
                "payprop_export_tenants",
                "payprop_export_tenants_complete"
            };

            for (String table : tables) {
                String countQuery = "SELECT COUNT(*) FROM " + table;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(countQuery)) {
                    if (rs.next()) {
                        System.out.printf("  %s: %d records\n", table, rs.getInt(1));
                    }
                }
            }

            System.out.println("\n");

            // Check for expense-related entries in payprop_export tables
            System.out.println("STEP 10: Categories in payprop_export_payments (distribution rules)");
            System.out.println("-------------------------------------------------------------------");

            String catQuery = "SELECT category, COUNT(*) as count FROM payprop_export_payments GROUP BY category ORDER BY count DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(catQuery)) {
                while (rs.next()) {
                    System.out.printf("  %s: %d records\n", rs.getString("category"), rs.getInt("count"));
                }
            }

            System.out.println("\n");

            // Check invoice types
            System.out.println("STEP 11: Invoice types in payprop_export_invoices");
            System.out.println("-------------------------------------------------");

            String invTypeQuery = "SELECT invoice_type, COUNT(*) as count FROM payprop_export_invoices GROUP BY invoice_type ORDER BY count DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(invTypeQuery)) {
                while (rs.next()) {
                    System.out.printf("  %s: %d records\n", rs.getString("invoice_type"), rs.getInt("count"));
                }
            }

            System.out.println("\n=======================================================");
            System.out.println("INVESTIGATION COMPLETE");
            System.out.println("=======================================================");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
