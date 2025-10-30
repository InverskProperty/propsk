import java.sql.*;

public class InvestigatePropertySync {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== INVESTIGATING PAYPROP_EXPORT_PROPERTIES SYNC STATUS ===\n");

            // Query 1: Check what fields are populated
            System.out.println("=== 1. SAMPLE PROPERTY RECORD (ALL FIELDS) ===");
            String query1 = """
                SELECT *
                FROM payprop_export_properties
                LIMIT 1
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                if (rs.next()) {
                    System.out.printf("%-35s %-50s%n", "COLUMN_NAME", "VALUE");
                    System.out.println("-".repeat(90));
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        String value = rs.getString(i);
                        if (value != null && value.length() > 50) {
                            value = value.substring(0, 47) + "...";
                        }
                        System.out.printf("%-35s %-50s%n",
                            columnName,
                            value == null ? "[NULL]" : value);
                    }
                }
            }

            // Query 2: Check field population statistics
            System.out.println("\n=== 2. FIELD POPULATION STATISTICS ===");
            String query2 = """
                SELECT
                    COUNT(*) as total,
                    COUNT(payprop_id) as has_payprop_id,
                    COUNT(name) as has_name,
                    COUNT(description) as has_description,
                    COUNT(address_first_line) as has_address,
                    COUNT(commission_percentage) as has_commission_pct,
                    COUNT(imported_at) as has_imported_at,
                    COUNT(last_modified_at) as has_last_modified,
                    MIN(imported_at) as earliest_import,
                    MAX(imported_at) as latest_import
                FROM payprop_export_properties
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                if (rs.next()) {
                    System.out.printf("%-25s %d%n", "Total records:", rs.getInt("total"));
                    System.out.printf("%-25s %d%n", "Has payprop_id:", rs.getInt("has_payprop_id"));
                    System.out.printf("%-25s %d%n", "Has name:", rs.getInt("has_name"));
                    System.out.printf("%-25s %d%n", "Has description:", rs.getInt("has_description"));
                    System.out.printf("%-25s %d%n", "Has address:", rs.getInt("has_address"));
                    System.out.printf("%-25s %d%n", "Has commission %%:", rs.getInt("has_commission_pct"));
                    System.out.printf("%-25s %d%n", "Has imported_at:", rs.getInt("has_imported_at"));
                    System.out.printf("%-25s %d%n", "Has last_modified:", rs.getInt("has_last_modified"));
                    System.out.printf("%-25s %s%n", "Earliest import:", rs.getTimestamp("earliest_import"));
                    System.out.printf("%-25s %s%n", "Latest import:", rs.getTimestamp("latest_import"));
                }
            }

            // Query 3: Check if names exist in ICDN for the same property IDs
            System.out.println("\n=== 3. PROPERTY NAMES FROM ICDN vs MASTER LIST ===");
            String query3 = """
                SELECT
                    prop.payprop_id,
                    prop.name as master_name,
                    icdn.property_name as icdn_name,
                    COUNT(DISTINCT icdn.payprop_id) as icdn_transaction_count
                FROM payprop_export_properties prop
                LEFT JOIN payprop_report_icdn icdn ON prop.payprop_id = icdn.property_payprop_id
                GROUP BY prop.payprop_id, prop.name, icdn.property_name
                ORDER BY icdn_transaction_count DESC
                LIMIT 15
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-20s %-35s %-35s %-10s%n",
                    "PAYPROP_ID", "MASTER_NAME", "ICDN_NAME", "TXN_COUNT");
                System.out.println("-".repeat(105));
                while (rs.next()) {
                    String masterName = rs.getString("master_name");
                    String icdnName = rs.getString("icdn_name");
                    System.out.printf("%-20s %-35s %-35s %-10d%n",
                        rs.getString("payprop_id"),
                        masterName == null ? "[NULL]" : masterName,
                        icdnName == null ? "[NO ICDN DATA]" : icdnName,
                        rs.getInt("icdn_transaction_count"));
                }
            }

            // Query 4: How were local property names assigned?
            System.out.println("\n=== 4. LOCAL PROPERTY NAME ASSIGNMENT ===");
            String query4 = """
                SELECT
                    p.id,
                    p.property_name as local_name,
                    p.payprop_id,
                    prop.name as payprop_name,
                    icdn.property_name as icdn_name
                FROM properties p
                LEFT JOIN payprop_export_properties prop ON p.payprop_id = prop.payprop_id
                LEFT JOIN (
                    SELECT DISTINCT property_payprop_id, property_name
                    FROM payprop_report_icdn
                ) icdn ON p.payprop_id = icdn.property_payprop_id
                WHERE p.property_name LIKE '%3 West Gate%'
                  AND (p.property_name LIKE '%Flat 1 %'
                    OR p.property_name LIKE '%Flat 2 %'
                    OR p.property_name LIKE '%Flat 3 %'
                    OR p.property_name LIKE '%Flat 4 %')
                ORDER BY p.property_name
                LIMIT 10
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-35s %-20s %-25s %-25s%n",
                    "LOCAL_NAME", "PAYPROP_ID", "PAYPROP_NAME", "ICDN_NAME");
                System.out.println("-".repeat(110));
                while (rs.next()) {
                    String paypropName = rs.getString("payprop_name");
                    String icdnName = rs.getString("icdn_name");
                    System.out.printf("%-35s %-20s %-25s %-25s%n",
                        rs.getString("local_name"),
                        rs.getString("payprop_id"),
                        paypropName == null ? "[NULL]" : paypropName,
                        icdnName == null ? "[NO ICDN]" : icdnName);
                }
            }

            // Query 5: Check when properties were created locally
            System.out.println("\n=== 5. PROPERTY CREATION TIMELINE ===");
            String query5 = """
                SELECT
                    DATE(created_at) as creation_date,
                    COUNT(*) as properties_created
                FROM properties
                GROUP BY DATE(created_at)
                ORDER BY creation_date
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-20s %-20s%n", "CREATION_DATE", "PROPERTIES_CREATED");
                System.out.println("-".repeat(45));
                while (rs.next()) {
                    System.out.printf("%-20s %-20d%n",
                        rs.getDate("creation_date"),
                        rs.getInt("properties_created"));
                }
            }

            System.out.println("\n=== DIAGNOSIS ===");
            System.out.println("If payprop_export_properties has payprop_ids but NULL names:");
            System.out.println("  -> Property sync only saved IDs, not full property data");
            System.out.println("  -> PayProp API may have returned IDs without property details");
            System.out.println("  -> Sync code may have bug in property name extraction");
            System.out.println("\nIf ICDN has correct names but master list doesn't:");
            System.out.println("  -> ICDN sync works correctly");
            System.out.println("  -> Property sync is broken or incomplete");
            System.out.println("  -> Should use ICDN names to populate master list");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
