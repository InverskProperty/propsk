import java.sql.*;

public class CheckPayPropMasterList {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== INVESTIGATING PAYPROP_EXPORT_PROPERTIES TABLE ===\n");

            // Query 1: Count total properties
            System.out.println("=== 1. TOTAL PROPERTIES IN PAYPROP_EXPORT_PROPERTIES ===");
            String query1 = """
                SELECT
                    COUNT(*) as total,
                    COUNT(CASE WHEN is_archived = 0 THEN 1 END) as active,
                    COUNT(CASE WHEN is_archived = 1 THEN 1 END) as archived
                FROM payprop_export_properties
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-15s %-15s %-15s%n", "TOTAL", "ACTIVE", "ARCHIVED");
                System.out.println("-".repeat(50));
                if (rs.next()) {
                    System.out.printf("%-15d %-15d %-15d%n",
                        rs.getInt("total"),
                        rs.getInt("active"),
                        rs.getInt("archived"));
                }
            }

            // Query 2: Sample properties
            System.out.println("\n=== 2. SAMPLE PROPERTIES (First 20) ===");
            String query2 = """
                SELECT
                    payprop_id,
                    name,
                    is_archived,
                    create_date
                FROM payprop_export_properties
                ORDER BY name
                LIMIT 20
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-20s %-60s %-10s%n",
                    "PAYPROP_ID", "NAME", "ARCHIVED");
                System.out.println("-".repeat(95));
                int count = 0;
                while (rs.next()) {
                    count++;
                    String name = rs.getString("name");
                    if (name != null && name.length() > 60) {
                        name = name.substring(0, 57) + "...";
                    }
                    System.out.printf("%-20s %-60s %-10s%n",
                        rs.getString("payprop_id"),
                        name,
                        rs.getBoolean("is_archived") ? "YES" : "NO");
                }
                if (count == 0) {
                    System.out.println("NO PROPERTIES FOUND!");
                }
            }

            // Query 3: Check where payprop_ids are assigned in local properties table
            System.out.println("\n=== 3. LOCAL PROPERTIES WITH PAYPROP_ID ===");
            String query3 = """
                SELECT
                    COUNT(*) as total,
                    COUNT(CASE WHEN payprop_id IS NOT NULL THEN 1 END) as has_payprop_id,
                    COUNT(CASE WHEN payprop_id IS NULL THEN 1 END) as no_payprop_id
                FROM properties
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-15s %-20s %-20s%n",
                    "TOTAL_LOCAL", "HAS_PAYPROP_ID", "NO_PAYPROP_ID");
                System.out.println("-".repeat(60));
                if (rs.next()) {
                    System.out.printf("%-15d %-20d %-20d%n",
                        rs.getInt("total"),
                        rs.getInt("has_payprop_id"),
                        rs.getInt("no_payprop_id"));
                }
            }

            // Query 4: Check how many local payprop_ids are valid (exist in PayProp)
            System.out.println("\n=== 4. VALIDATION: LOCAL PAYPROP_IDs vs PAYPROP MASTER ===");
            String query4 = """
                SELECT
                    COUNT(DISTINCT p.payprop_id) as local_count,
                    COUNT(DISTINCT prop.payprop_id) as valid_in_payprop,
                    COUNT(DISTINCT p.payprop_id) - COUNT(DISTINCT prop.payprop_id) as invalid
                FROM properties p
                LEFT JOIN payprop_export_properties prop ON p.payprop_id = prop.payprop_id
                WHERE p.payprop_id IS NOT NULL
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-20s %-20s %-20s%n",
                    "LOCAL_PAYPROP_IDS", "VALID_IN_PAYPROP", "INVALID_IDS");
                System.out.println("-".repeat(65));
                if (rs.next()) {
                    System.out.printf("%-20d %-20d %-20d%n",
                        rs.getInt("local_count"),
                        rs.getInt("valid_in_payprop"),
                        rs.getInt("invalid"));
                }
            }

            // Query 5: Check all property names that start with specific patterns
            System.out.println("\n=== 5. PROPERTIES IN PAYPROP BY NAME PATTERN ===");
            String query5 = """
                SELECT
                    SUBSTRING_INDEX(name, ' ', 2) as building_prefix,
                    COUNT(*) as count
                FROM payprop_export_properties
                WHERE name IS NOT NULL
                GROUP BY SUBSTRING_INDEX(name, ' ', 2)
                ORDER BY count DESC
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-40s %-10s%n", "BUILDING_PREFIX", "COUNT");
                System.out.println("-".repeat(55));
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("%-40s %-10d%n",
                        rs.getString("building_prefix"),
                        rs.getInt("count"));
                }
                if (count == 0) {
                    System.out.println("NO PROPERTIES FOUND!");
                }
            }

            // Query 6: Where did the local payprop_id values come from?
            System.out.println("\n=== 6. SAMPLE LOCAL PROPERTIES WITH INVALID PAYPROP_IDs ===");
            String query6 = """
                SELECT
                    p.id,
                    p.property_name,
                    p.payprop_id,
                    p.created_at,
                    p.updated_at
                FROM properties p
                LEFT JOIN payprop_export_properties prop ON p.payprop_id = prop.payprop_id
                WHERE p.payprop_id IS NOT NULL
                  AND prop.payprop_id IS NULL
                ORDER BY p.created_at
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {
                System.out.printf("%-10s %-40s %-20s %-20s%n",
                    "LOCAL_ID", "PROPERTY_NAME", "PAYPROP_ID", "CREATED_AT");
                System.out.println("-".repeat(95));
                while (rs.next()) {
                    System.out.printf("%-10d %-40s %-20s %-20s%n",
                        rs.getLong("id"),
                        rs.getString("property_name"),
                        rs.getString("payprop_id"),
                        rs.getTimestamp("created_at"));
                }
            }

            // Query 7: Check ICDN transactions with property_payprop_id not in master
            System.out.println("\n=== 7. ICDN TRANSACTIONS WITH INVALID PROPERTY_PAYPROP_IDs ===");
            String query7 = """
                SELECT
                    COUNT(DISTINCT icdn.property_payprop_id) as unique_property_ids,
                    COUNT(*) as total_transactions,
                    SUM(icdn.amount) as total_amount
                FROM payprop_report_icdn icdn
                LEFT JOIN payprop_export_properties prop ON icdn.property_payprop_id = prop.payprop_id
                WHERE prop.payprop_id IS NULL
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query7)) {
                System.out.printf("%-25s %-25s %-25s%n",
                    "UNIQUE_PROPERTY_IDS", "TOTAL_TRANSACTIONS", "TOTAL_AMOUNT");
                System.out.println("-".repeat(80));
                if (rs.next()) {
                    System.out.printf("%-25d %-25d GBP%-22.2f%n",
                        rs.getInt("unique_property_ids"),
                        rs.getInt("total_transactions"),
                        rs.getBigDecimal("total_amount"));
                }
            }

            System.out.println("\n=== DIAGNOSIS SUMMARY ===");
            System.out.println("If payprop_export_properties is empty or nearly empty:");
            System.out.println("  -> PayProp property sync failed or never ran");
            System.out.println("  -> All property data is orphaned");
            System.out.println("\nIf many local payprop_ids are invalid:");
            System.out.println("  -> Properties were assigned IDs that don't exist in PayProp");
            System.out.println("  -> Possible manual assignment or data corruption");
            System.out.println("\nIf ICDN has many transactions with invalid property IDs:");
            System.out.println("  -> PayProp API is sending transaction data for properties");
            System.out.println("     that aren't in the property master list");
            System.out.println("  -> Possible API data consistency issue");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
