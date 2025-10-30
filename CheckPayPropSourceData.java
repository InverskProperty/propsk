import java.sql.*;

public class CheckPayPropSourceData {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== CHECKING PAYPROP SOURCE TABLES FOR FLAT 1-3 ===\n");

            // Query 1: payprop_report_icdn
            System.out.println("=== 1. payprop_report_icdn (ICDN transactions) ===");
            String query1 = """
                SELECT *
                FROM payprop_report_icdn
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                LIMIT 5
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {

                ResultSetMetaData metaData = rs.getMetaData();
                int count = metaData.getColumnCount();

                System.out.printf("%-20s %-25s %-15s %-12s %-20s%n",
                    "PAYPROP_ID", "PROPERTY", "DATE", "AMOUNT", "INVOICE_NUM");
                System.out.println("-".repeat(95));

                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-20s %-25s %-15s GBP%-9.2f %-20s%n",
                        rs.getString("payprop_id"),
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("invoice_number"));
                }
                if (!found) {
                    System.out.println("NO DATA FOUND");
                }
            } catch (SQLException e) {
                System.out.println("Error: " + e.getMessage());
            }

            // Query 2: payprop_export_invoice_instructions
            System.out.println("\n=== 2. payprop_export_invoice_instructions ===");
            String query2 = """
                SELECT
                    payprop_instruction_id,
                    property_name,
                    transaction_date,
                    amount,
                    description
                FROM payprop_export_invoice_instructions
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {

                System.out.printf("%-30s %-25s %-15s %-12s%n",
                    "INSTRUCTION_ID", "PROPERTY", "DATE", "AMOUNT");
                System.out.println("-".repeat(90));

                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-30s %-25s %-15s GBP%-9.2f%n",
                        rs.getString("payprop_instruction_id"),
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"));
                }
                if (!found) {
                    System.out.println("NO DATA FOUND");
                }
            } catch (SQLException e) {
                System.out.println("Error: " + e.getMessage());
            }

            // Query 3: payprop_report_all_payments
            System.out.println("\n=== 3. payprop_report_all_payments (Commission source?) ===");
            String query3 = """
                SELECT *
                FROM payprop_report_all_payments
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                LIMIT 5
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // Show first few columns
                System.out.printf("%-20s %-25s %-15s %-12s %-20s%n",
                    "PAYPROP_ID", "PROPERTY", "DATE", "AMOUNT", "TYPE");
                System.out.println("-".repeat(95));

                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-20s %-25s %-15s GBP%-9.2f %-20s%n",
                        rs.getString("payprop_id"),
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("transaction_type"));
                }
                if (!found) {
                    System.out.println("NO DATA FOUND");
                }
            } catch (SQLException e) {
                System.out.println("Error: " + e.getMessage());
            }

            // Query 4: Check if Flat 4,5,6 (which ARE on PayProp) are in these tables
            System.out.println("\n=== 4. COMPARISON: Flat 4,5,6 in payprop_report_icdn ===");
            String query4 = """
                SELECT
                    property_name,
                    COUNT(*) as count
                FROM payprop_report_icdn
                WHERE property_name IN (
                    'Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate',
                    'Flat 4 - 3 West Gate', 'Flat 5 - 3 West Gate', 'Flat 6 - 3 West Gate'
                )
                GROUP BY property_name
                ORDER BY property_name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {

                System.out.printf("%-30s %-10s%n", "PROPERTY", "COUNT");
                System.out.println("-".repeat(45));

                while (rs.next()) {
                    System.out.printf("%-30s %-10d%n",
                        rs.getString("property_name"),
                        rs.getInt("count"));
                }
            } catch (SQLException e) {
                System.out.println("Error: " + e.getMessage());
            }

            // Query 5: Check payprop_export_properties table
            System.out.println("\n=== 5. payprop_export_properties (Property list in PayProp) ===");
            String query5 = """
                SELECT
                    payprop_id,
                    property_name
                FROM payprop_export_properties
                WHERE property_name IN (
                    'Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate',
                    'Flat 4 - 3 West Gate', 'Flat 5 - 3 West Gate', 'Flat 6 - 3 West Gate'
                )
                ORDER BY property_name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {

                System.out.printf("%-30s %-20s%n", "PROPERTY", "PAYPROP_ID");
                System.out.println("-".repeat(55));

                while (rs.next()) {
                    System.out.printf("%-30s %-20s%n",
                        rs.getString("property_name"),
                        rs.getString("payprop_id"));
                }
            } catch (SQLException e) {
                System.out.println("Error: " + e.getMessage());
            }

            // Query 6: Track the PayProp ID from financial_transactions back to source
            System.out.println("\n=== 6. TRACE PayProp ID 'yJ66DlrkJj' (Flat 1 transaction) ===");
            String query6 = """
                SELECT
                    'payprop_report_icdn' as source_table,
                    payprop_id,
                    property_name,
                    transaction_date,
                    amount
                FROM payprop_report_icdn
                WHERE payprop_id = 'yJ66DlrkJj'
                UNION ALL
                SELECT
                    'payprop_export_invoice_instructions' as source_table,
                    payprop_instruction_id as payprop_id,
                    property_name,
                    transaction_date,
                    amount
                FROM payprop_export_invoice_instructions
                WHERE payprop_instruction_id = 'yJ66DlrkJj'
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {

                System.out.printf("%-40s %-20s %-25s %-15s %-12s%n",
                    "SOURCE_TABLE", "PAYPROP_ID", "PROPERTY", "DATE", "AMOUNT");
                System.out.println("-".repeat(120));

                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-40s %-20s %-25s %-15s GBP%-9.2f%n",
                        rs.getString("source_table"),
                        rs.getString("payprop_id"),
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"));
                }
                if (!found) {
                    System.out.println("NOT FOUND - This PayProp ID doesn't exist in source tables!");
                }
            } catch (SQLException e) {
                System.out.println("Error: " + e.getMessage());
            }

            System.out.println("\n=== ANALYSIS COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
