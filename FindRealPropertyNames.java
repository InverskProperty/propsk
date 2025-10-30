import java.sql.*;

public class FindRealPropertyNames {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== INVESTIGATING PROPERTY_PAYPROP_ID MISMATCH ===\n");
            System.out.println("Hypothesis: The property_payprop_ids in ICDN belong to OTHER properties");
            System.out.println("PayProp may know 'Flat 1' by a different name\n");

            // Query 1: Find what properties these IDs REALLY belong to
            System.out.println("=== 1. LOOKUP PAYPROP IDs IN MASTER PROPERTY LIST ===");
            String query1 = """
                SELECT
                    payprop_id,
                    name,
                    is_archived,
                    create_date
                FROM payprop_export_properties
                WHERE payprop_id IN ('KAXNvEqAXk', 'KAXNvEqVXk', 'WzJBQ3ERZQ')
                ORDER BY name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-20s %-50s %-10s %-20s%n",
                    "PAYPROP_ID", "REAL_NAME_IN_PAYPROP", "ARCHIVED", "CREATED");
                System.out.println("-".repeat(105));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-20s %-50s %-10s %-20s%n",
                        rs.getString("payprop_id"),
                        rs.getString("name"),
                        rs.getBoolean("is_archived") ? "YES" : "NO",
                        rs.getTimestamp("create_date"));
                }
                if (!found) {
                    System.out.println("NOT FOUND - These IDs don't exist in PayProp!");
                }
            }

            // Query 2: Show ALL 3 West Gate properties in PayProp master list
            System.out.println("\n=== 2. ALL '3 WEST GATE' PROPERTIES IN PAYPROP ===");
            String query2 = """
                SELECT
                    payprop_id,
                    name,
                    is_archived
                FROM payprop_export_properties
                WHERE name LIKE '%3%West%Gate%'
                   OR name LIKE '%West%Gate%3%'
                ORDER BY name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-20s %-60s %-10s%n",
                    "PAYPROP_ID", "NAME", "ARCHIVED");
                System.out.println("-".repeat(95));
                while (rs.next()) {
                    System.out.printf("%-20s %-60s %-10s%n",
                        rs.getString("payprop_id"),
                        rs.getString("name"),
                        rs.getBoolean("is_archived") ? "YES" : "NO");
                }
            }

            // Query 3: Check our local properties table
            System.out.println("\n=== 3. LOCAL PROPERTIES TABLE - FLATS 1-6 AT 3 WEST GATE ===");
            String query3 = """
                SELECT
                    id,
                    property_name,
                    payprop_id
                FROM properties
                WHERE property_name LIKE '%3 West Gate%'
                  AND (property_name LIKE '%Flat 1%'
                    OR property_name LIKE '%Flat 2%'
                    OR property_name LIKE '%Flat 3%'
                    OR property_name LIKE '%Flat 4%'
                    OR property_name LIKE '%Flat 5%'
                    OR property_name LIKE '%Flat 6%')
                ORDER BY property_name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-10s %-40s %-20s%n",
                    "LOCAL_ID", "LOCAL_PROPERTY_NAME", "LOCAL_PAYPROP_ID");
                System.out.println("-".repeat(75));
                while (rs.next()) {
                    System.out.printf("%-10d %-40s %-20s%n",
                        rs.getLong("id"),
                        rs.getString("property_name"),
                        rs.getString("payprop_id"));
                }
            }

            // Query 4: Cross-check - what property names use these payprop_ids in ICDN?
            System.out.println("\n=== 4. ALL PROPERTY NAMES USING THESE PAYPROP IDs IN ICDN ===");
            String query4 = """
                SELECT DISTINCT
                    property_payprop_id,
                    property_name,
                    COUNT(*) as transaction_count
                FROM payprop_report_icdn
                WHERE property_payprop_id IN ('KAXNvEqAXk', 'KAXNvEqVXk', 'WzJBQ3ERZQ')
                GROUP BY property_payprop_id, property_name
                ORDER BY property_payprop_id, property_name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-20s %-40s %-15s%n",
                    "PROPERTY_PAYPROP_ID", "PROPERTY_NAME_IN_ICDN", "TXN_COUNT");
                System.out.println("-".repeat(80));
                while (rs.next()) {
                    System.out.printf("%-20s %-40s %-15d%n",
                        rs.getString("property_payprop_id"),
                        rs.getString("property_name"),
                        rs.getInt("transaction_count"));
                }
            }

            // Query 5: Check if property names in ICDN match PayProp master names
            System.out.println("\n=== 5. PROPERTY NAME CONSISTENCY CHECK ===");
            String query5 = """
                SELECT
                    icdn.property_payprop_id,
                    icdn.property_name as icdn_name,
                    prop.name as master_name,
                    CASE
                        WHEN prop.name IS NULL THEN '[ID NOT IN MASTER]'
                        WHEN icdn.property_name = prop.name THEN '[MATCH]'
                        ELSE '[NAME MISMATCH]'
                    END as status
                FROM (
                    SELECT DISTINCT property_payprop_id, property_name
                    FROM payprop_report_icdn
                    WHERE property_name LIKE '%3 West Gate%'
                      AND (property_name LIKE '%Flat 1%'
                        OR property_name LIKE '%Flat 2%'
                        OR property_name LIKE '%Flat 3%'
                        OR property_name LIKE '%Flat 4%'
                        OR property_name LIKE '%Flat 5%'
                        OR property_name LIKE '%Flat 6%')
                ) icdn
                LEFT JOIN payprop_export_properties prop ON icdn.property_payprop_id = prop.payprop_id
                ORDER BY icdn.property_name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-20s %-35s %-35s %-20s%n",
                    "PAYPROP_ID", "ICDN_NAME", "MASTER_NAME", "STATUS");
                System.out.println("-".repeat(115));
                while (rs.next()) {
                    String masterName = rs.getString("master_name");
                    System.out.printf("%-20s %-35s %-35s %-20s%n",
                        rs.getString("property_payprop_id"),
                        rs.getString("icdn_name"),
                        masterName == null ? "" : masterName,
                        rs.getString("status"));
                }
            }

            // Query 6: Check the properties table payprop_id assignment
            System.out.println("\n=== 6. PAYPROP_ID ASSIGNMENT IN LOCAL PROPERTIES TABLE ===");
            String query6 = """
                SELECT
                    p.id,
                    p.property_name,
                    p.payprop_id as local_payprop_id,
                    prop.name as payprop_master_name
                FROM properties p
                LEFT JOIN payprop_export_properties prop ON p.payprop_id = prop.payprop_id
                WHERE p.property_name LIKE '%3 West Gate%'
                  AND (p.property_name LIKE '%Flat 1%'
                    OR p.property_name LIKE '%Flat 2%'
                    OR p.property_name LIKE '%Flat 3%'
                    OR p.property_name LIKE '%Flat 4%'
                    OR p.property_name LIKE '%Flat 5%'
                    OR p.property_name LIKE '%Flat 6%')
                ORDER BY p.property_name
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {
                System.out.printf("%-10s %-35s %-20s %-40s%n",
                    "LOCAL_ID", "LOCAL_NAME", "PAYPROP_ID", "PAYPROP_MASTER_NAME");
                System.out.println("-".repeat(110));
                while (rs.next()) {
                    String payPropId = rs.getString("local_payprop_id");
                    String masterName = rs.getString("payprop_master_name");
                    System.out.printf("%-10d %-35s %-20s %-40s%n",
                        rs.getLong("id"),
                        rs.getString("property_name"),
                        payPropId == null ? "[NULL]" : payPropId,
                        masterName == null ? "[NOT IN PAYPROP]" : masterName);
                }
            }

            System.out.println("\n=== DIAGNOSIS ===");
            System.out.println("If property_payprop_ids resolve to OTHER property names:");
            System.out.println("  -> PayProp API is returning correct data but with different property names");
            System.out.println("  -> Property name mismatch between local DB and PayProp");
            System.out.println("\nIf property_payprop_ids do NOT exist in master list:");
            System.out.println("  -> ICDN data contains invalid/phantom property IDs");
            System.out.println("  -> Possible data corruption or API issue");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
