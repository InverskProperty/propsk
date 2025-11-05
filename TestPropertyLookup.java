import java.sql.*;

public class TestPropertyLookup {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        String[] testIds = {
            "oRZQ8ldM1m",  // Flat 26 - FAILED in lease creation
            "08JL4wzmJR",  // Flat 6 - FAILED in lease creation
            "5AJ5KVr91M",  // Flat 16 - FAILED in lease creation
            "EyJ6K7RxXj",  // Apartment 40 - FAILED in lease creation
            "d71eApoB15"   // Flat 28 - FAILED in lease creation
        };

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== TESTING PROPERTY LOOKUPS (Simulating JPA Query) ===\n");

            // This simulates what JPA's findByPayPropId would do
            String jpaSql = "SELECT id, property_name, payprop_id FROM properties WHERE payprop_id = ?";
            PreparedStatement stmt = conn.prepareStatement(jpaSql);

            int found = 0;
            int notFound = 0;

            for (String testId : testIds) {
                stmt.setString(1, testId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    System.out.println(String.format("✓ FOUND: %s -> ID=%d, Name=%s",
                        testId, rs.getLong("id"), rs.getString("property_name")));
                    found++;
                } else {
                    System.out.println(String.format("✗ NOT FOUND: %s", testId));
                    notFound++;
                }
                rs.close();
            }

            stmt.close();

            System.out.println("\n=== RESULTS ===");
            System.out.println(String.format("Found: %d / %d", found, testIds.length));
            System.out.println(String.format("Not Found: %d / %d", notFound, testIds.length));

            if (found == testIds.length) {
                System.out.println("\n✓✓✓ ALL PROPERTIES FOUND - JPA should work correctly!");
            } else {
                System.out.println("\n✗✗✗ PROPERTIES MISSING - There's a data issue!");
            }

            // Now let's check if there's a case sensitivity issue
            System.out.println("\n=== CASE SENSITIVITY TEST ===");
            String caseTest = "SELECT payprop_id, BINARY payprop_id as binary_id FROM properties WHERE id = 1";
            Statement st = conn.createStatement();
            ResultSet caseRs = st.executeQuery(caseTest);
            if (caseRs.next()) {
                String id1 = caseRs.getString("payprop_id");
                String id2 = caseRs.getString("binary_id");
                System.out.println(String.format("Property ID 1: payprop_id='%s', binary='%s'", id1, id2));
            }
            caseRs.close();
            st.close();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
