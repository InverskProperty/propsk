import java.sql.*;
import java.util.Arrays;
import java.util.List;

public class QueryOrphanedProperties {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        // The 21 orphaned property IDs from the logs
        List<String> orphanedIds = Arrays.asList(
            "2", "5", "6", "7", "17", "18", "20", "21", "25", "27", "28",
            "29", "30", "31", "33", "37", "39", "40", "41", "43", "44"
        );

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected to database successfully!\n");
            System.out.println("=".repeat(100));
            System.out.println("ORPHANED PROPERTIES DATA INVESTIGATION");
            System.out.println("21 Properties that PayProp API cannot find (404 errors)");
            System.out.println("=".repeat(100));

            // Query 1: Check if these property IDs exist in the properties table
            System.out.println("\n=== QUERY 1: DO THESE PROPERTIES EXIST IN LOCAL DATABASE? ===\n");
            String query1 = "SELECT id, payprop_id, property_name, address_line_1, city, postcode, " +
                          "created_at, updated_at " +
                          "FROM properties " +
                          "WHERE payprop_id IN (" + String.join(",", orphanedIds.stream().map(id -> "'" + id + "'").toArray(String[]::new)) + ") " +
                          "ORDER BY CAST(payprop_id AS UNSIGNED)";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-10s %-15s %-40s %-30s %-20s%n",
                    "LOCAL_ID", "PAYPROP_ID", "PROPERTY_NAME", "ADDRESS", "CITY");
                System.out.println("-".repeat(120));

                int count = 0;
                while (rs.next()) {
                    System.out.printf("%-10d %-15s %-40s %-30s %-20s%n",
                        rs.getInt("id"),
                        rs.getString("payprop_id"),
                        truncate(rs.getString("property_name"), 38),
                        truncate(rs.getString("address_line_1"), 28),
                        truncate(rs.getString("city"), 18));
                    count++;
                }
                System.out.println("\nTotal properties found in local database: " + count + " out of 21");
            }

            // Query 2: Find transactions referencing these orphaned property IDs
            System.out.println("\n=== QUERY 2: TRANSACTIONS REFERENCING ORPHANED PROPERTIES ===\n");
            String query2 = "SELECT property_id, property_name, data_source, " +
                          "COUNT(*) as transaction_count, " +
                          "SUM(amount) as total_amount, " +
                          "MIN(transaction_date) as earliest_date, " +
                          "MAX(transaction_date) as latest_date " +
                          "FROM financial_transactions " +
                          "WHERE property_id IN (" + String.join(",", orphanedIds) + ") " +
                          "GROUP BY property_id, property_name, data_source " +
                          "ORDER BY property_id, data_source";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-12s %-40s %-25s %-10s %-15s %-12s %-12s%n",
                    "PROP_ID", "PROPERTY_NAME", "DATA_SOURCE", "TX_COUNT", "TOTAL_AMOUNT", "EARLIEST", "LATEST");
                System.out.println("-".repeat(140));

                while (rs.next()) {
                    System.out.printf("%-12s %-40s %-25s %-10d %-15.2f %-12s %-12s%n",
                        rs.getString("property_id"),
                        truncate(rs.getString("property_name"), 38),
                        truncate(rs.getString("data_source"), 23),
                        rs.getInt("transaction_count"),
                        rs.getDouble("total_amount"),
                        rs.getDate("earliest_date"),
                        rs.getDate("latest_date"));
                }
            }

            // Query 3: Summary by orphaned property ID
            System.out.println("\n=== QUERY 3: SUMMARY BY ORPHANED PROPERTY ID ===\n");
            String query3 = "SELECT property_id, property_name, " +
                          "COUNT(*) as total_transactions, " +
                          "SUM(amount) as total_amount, " +
                          "COUNT(DISTINCT data_source) as data_sources, " +
                          "MIN(transaction_date) as first_transaction, " +
                          "MAX(transaction_date) as last_transaction " +
                          "FROM financial_transactions " +
                          "WHERE property_id IN (" + String.join(",", orphanedIds) + ") " +
                          "GROUP BY property_id, property_name " +
                          "ORDER BY CAST(property_id AS UNSIGNED)";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-12s %-45s %-10s %-15s %-12s %-12s %-12s%n",
                    "PROP_ID", "PROPERTY_NAME", "TX_COUNT", "TOTAL_AMT", "SOURCES", "FIRST_TX", "LAST_TX");
                System.out.println("-".repeat(135));

                while (rs.next()) {
                    System.out.printf("%-12s %-45s %-10d %-15.2f %-12d %-12s %-12s%n",
                        rs.getString("property_id"),
                        truncate(rs.getString("property_name"), 43),
                        rs.getInt("total_transactions"),
                        rs.getDouble("total_amount"),
                        rs.getInt("data_sources"),
                        rs.getDate("first_transaction"),
                        rs.getDate("last_transaction"));
                }
            }

            // Query 4: Check if these properties exist in payprop_export_properties
            System.out.println("\n=== QUERY 4: DO THESE EXIST IN PAYPROP_EXPORT_PROPERTIES TABLE? ===\n");
            String query4 = "SELECT payprop_id, property_name, customer_reference, " +
                          "agent_name, monthly_payment, sync_status " +
                          "FROM payprop_export_properties " +
                          "WHERE payprop_id IN (" + String.join(",", orphanedIds.stream().map(id -> "'" + id + "'").toArray(String[]::new)) + ") " +
                          "ORDER BY CAST(payprop_id AS UNSIGNED)";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-12s %-45s %-20s %-20s %-15s %-15s%n",
                    "PAYPROP_ID", "PROPERTY_NAME", "CUSTOMER_REF", "AGENT", "MONTHLY_PAY", "STATUS");
                System.out.println("-".repeat(135));

                int count = 0;
                while (rs.next()) {
                    System.out.printf("%-12s %-45s %-20s %-20s %-15.2f %-15s%n",
                        rs.getString("payprop_id"),
                        truncate(rs.getString("property_name"), 43),
                        truncate(rs.getString("customer_reference"), 18),
                        truncate(rs.getString("agent_name"), 18),
                        rs.getDouble("monthly_payment"),
                        rs.getString("sync_status"));
                    count++;
                }
                System.out.println("\nProperties found in PayProp export table: " + count + " out of 21");
            }

            // Query 5: Sample transactions for each orphaned property
            System.out.println("\n=== QUERY 5: SAMPLE TRANSACTIONS (First 3 per property) ===\n");
            String query5 = "SELECT property_id, property_name, transaction_date, data_source, " +
                          "amount, tenant_name, description " +
                          "FROM ( " +
                          "  SELECT *, " +
                          "  ROW_NUMBER() OVER (PARTITION BY property_id ORDER BY transaction_date DESC) as rn " +
                          "  FROM financial_transactions " +
                          "  WHERE property_id IN (" + String.join(",", orphanedIds) + ") " +
                          ") ranked " +
                          "WHERE rn <= 3 " +
                          "ORDER BY CAST(property_id AS UNSIGNED), transaction_date DESC";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-10s %-35s %-12s %-20s %-12s %-25s%n",
                    "PROP_ID", "PROPERTY_NAME", "DATE", "SOURCE", "AMOUNT", "TENANT");
                System.out.println("-".repeat(125));

                String lastPropId = "";
                while (rs.next()) {
                    String propId = rs.getString("property_id");
                    if (!propId.equals(lastPropId)) {
                        if (!lastPropId.isEmpty()) {
                            System.out.println();
                        }
                        lastPropId = propId;
                    }

                    System.out.printf("%-10s %-35s %-12s %-20s %-12.2f %-25s%n",
                        propId,
                        truncate(rs.getString("property_name"), 33),
                        rs.getDate("transaction_date"),
                        truncate(rs.getString("data_source"), 18),
                        rs.getDouble("amount"),
                        truncate(rs.getString("tenant_name"), 23));
                }
            }

            // Query 6: Overall orphaned properties statistics
            System.out.println("\n=== QUERY 6: OVERALL ORPHANED PROPERTIES STATISTICS ===\n");
            String query6 = "SELECT " +
                          "COUNT(DISTINCT property_id) as unique_orphaned_properties, " +
                          "COUNT(*) as total_orphaned_transactions, " +
                          "SUM(amount) as total_orphaned_amount, " +
                          "MIN(transaction_date) as earliest_orphaned_tx, " +
                          "MAX(transaction_date) as latest_orphaned_tx, " +
                          "COUNT(DISTINCT data_source) as data_sources_affected " +
                          "FROM financial_transactions " +
                          "WHERE property_id IN (" + String.join(",", orphanedIds) + ")";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {
                if (rs.next()) {
                    System.out.println("Unique Orphaned Properties: " + rs.getInt("unique_orphaned_properties"));
                    System.out.println("Total Orphaned Transactions: " + rs.getInt("total_orphaned_transactions"));
                    System.out.println("Total Amount (Orphaned): £" + String.format("%.2f", rs.getDouble("total_orphaned_amount")));
                    System.out.println("Earliest Transaction: " + rs.getDate("earliest_orphaned_tx"));
                    System.out.println("Latest Transaction: " + rs.getDate("latest_orphaned_tx"));
                    System.out.println("Data Sources Affected: " + rs.getInt("data_sources_affected"));
                }
            }

            System.out.println("\n" + "=".repeat(100));
            System.out.println("INVESTIGATION COMPLETE");
            System.out.println("=".repeat(100));

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String truncate(String str, int maxLength) {
        if (str == null) return "NULL";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 2) + "..";
    }
}
