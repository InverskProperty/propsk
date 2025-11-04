import java.sql.*;

public class QueryDelegation {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "NXRedwMHejnfLPDdzZgnzOWpLreBpUsl";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=".repeat(120));
            System.out.println("INVESTIGATING DELEGATED USER ACCESS");
            System.out.println("=".repeat(120));
            System.out.println();

            // STEP 1: Get customer IDs for both users
            System.out.println("STEP 1: Finding Customer IDs");
            System.out.println("-".repeat(120));

            String customerQuery =
                "SELECT customer_id, email, first_name, last_name " +
                "FROM customer " +
                "WHERE email IN ('achal@sunflaguk.com', 'uday@sunflaguk.com')";

            Long achalId = null;
            Long udayId = null;

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(customerQuery)) {
                while (rs.next()) {
                    Long id = rs.getLong("customer_id");
                    String email = rs.getString("email");
                    String name = rs.getString("first_name") + " " + rs.getString("last_name");
                    System.out.printf("Customer: %-30s | ID: %d | Name: %s%n", email, id, name);

                    if (email.equals("achal@sunflaguk.com")) {
                        achalId = id;
                    } else if (email.equals("uday@sunflaguk.com")) {
                        udayId = id;
                    }
                }
            }
            System.out.println();

            if (achalId == null || udayId == null) {
                System.out.println("ERROR: Could not find one or both users!");
                return;
            }

            // STEP 2: Check all assignments for achal@sunflaguk.com
            System.out.println("STEP 2: All Property Assignments for achal@sunflaguk.com (ID: " + achalId + ")");
            System.out.println("-".repeat(120));

            String achalAssignmentsQuery =
                "SELECT " +
                "  cpa.assignment_type, " +
                "  COUNT(*) as count, " +
                "  GROUP_CONCAT(DISTINCT p.property_name SEPARATOR ', ') as properties " +
                "FROM customer_property_assignment cpa " +
                "LEFT JOIN property p ON cpa.property_id = p.id " +
                "WHERE cpa.customer_id = " + achalId + " " +
                "GROUP BY cpa.assignment_type";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(achalAssignmentsQuery)) {
                while (rs.next()) {
                    String type = rs.getString("assignment_type");
                    int count = rs.getInt("count");
                    String props = rs.getString("properties");
                    System.out.printf("Assignment Type: %-20s | Count: %3d%n", type, count);
                    if (props != null && props.length() < 200) {
                        System.out.printf("  Properties: %s%n", props);
                    }
                }
            }
            System.out.println();

            // STEP 3: Check properties owned by uday@sunflaguk.com
            System.out.println("STEP 3: Properties Owned by uday@sunflaguk.com (ID: " + udayId + ")");
            System.out.println("-".repeat(120));

            String udayPropertiesQuery =
                "SELECT COUNT(*) as property_count " +
                "FROM property " +
                "WHERE owner_customer_id = " + udayId;

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(udayPropertiesQuery)) {
                if (rs.next()) {
                    int count = rs.getInt("property_count");
                    System.out.printf("Uday owns %d properties%n", count);
                }
            }
            System.out.println();

            // STEP 4: Check if achal has assignments to uday's properties
            System.out.println("STEP 4: Achal's Assignments to Uday's Properties");
            System.out.println("-".repeat(120));

            String crossAssignmentQuery =
                "SELECT " +
                "  cpa.assignment_type, " +
                "  COUNT(*) as count, " +
                "  GROUP_CONCAT(p.property_name SEPARATOR ', ') as properties " +
                "FROM customer_property_assignment cpa " +
                "JOIN property p ON cpa.property_id = p.id " +
                "WHERE cpa.customer_id = " + achalId + " " +
                "  AND p.owner_customer_id = " + udayId + " " +
                "GROUP BY cpa.assignment_type";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(crossAssignmentQuery)) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    String type = rs.getString("assignment_type");
                    int count = rs.getInt("count");
                    String props = rs.getString("properties");
                    System.out.printf("Assignment Type: %-20s | Count: %3d%n", type, count);
                    if (props != null && props.length() < 300) {
                        System.out.printf("  Sample Properties: %s%n", props);
                    }
                }
                if (!found) {
                    System.out.println("WARNING: NO ASSIGNMENTS FOUND - Achal has no assignments to any of Uday's properties!");
                }
            }
            System.out.println();

            // STEP 5: Check delegation table
            System.out.println("STEP 5: Checking for Delegation Records");
            System.out.println("-".repeat(120));

            // Check if there's a delegation table
            String checkTableQuery =
                "SELECT COUNT(*) as count " +
                "FROM information_schema.tables " +
                "WHERE table_schema = 'railway' " +
                "  AND table_name LIKE '%deleg%'";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(checkTableQuery)) {
                if (rs.next() && rs.getInt("count") > 0) {
                    System.out.println("Found delegation-related tables:");

                    String listTablesQuery =
                        "SELECT table_name " +
                        "FROM information_schema.tables " +
                        "WHERE table_schema = 'railway' " +
                        "  AND table_name LIKE '%deleg%'";

                    try (Statement stmt2 = conn.createStatement(); ResultSet rs2 = stmt2.executeQuery(listTablesQuery)) {
                        while (rs2.next()) {
                            String tableName = rs2.getString("table_name");
                            System.out.println("  - " + tableName);

                            // Try to query it
                            String queryTable = "SELECT * FROM " + tableName + " WHERE delegator_id = " + udayId + " OR delegate_id = " + achalId + " LIMIT 5";
                            try (Statement stmt3 = conn.createStatement(); ResultSet rs3 = stmt3.executeQuery(queryTable)) {
                                System.out.println("    Records:");
                                ResultSetMetaData meta = rs3.getMetaData();
                                while (rs3.next()) {
                                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                                        System.out.printf("      %s: %s ", meta.getColumnName(i), rs3.getString(i));
                                    }
                                    System.out.println();
                                }
                            } catch (Exception e) {
                                System.out.println("    (Could not query: " + e.getMessage() + ")");
                            }
                        }
                    }
                } else {
                    System.out.println("No delegation-related tables found");
                }
            }
            System.out.println();

            // STEP 6: Summary and recommendations
            System.out.println("STEP 6: ANALYSIS & RECOMMENDATIONS");
            System.out.println("-".repeat(120));
            System.out.println("Current filter in PropertyServiceImpl.findPropertiesByCustomerAssignments():");
            System.out.println("  - Only includes: OWNER, MANAGER");
            System.out.println();
            System.out.println("To fix delegated user access, we need to:");
            System.out.println("  1. Identify what assignment_type is used for delegation (from STEP 2)");
            System.out.println("  2. Add that type to the filter in PropertyServiceImpl");
            System.out.println("  3. OR implement a separate delegation mechanism");
            System.out.println("=".repeat(120));

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
