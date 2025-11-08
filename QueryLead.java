import java.sql.*;

public class QueryLead {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://dpg-ct3onsbqf0us73e8gvv0-a.oregon-postgres.render.com:5432/crecrm";
        String user = "root";
        String password = "PJIrXxdIXLKfC6Qa1jyvNt8mlacVIx76";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            // Query lead data
            String query = "SELECT lead_id, name, status, phone, email, lead_type, " +
                         "budget_min, budget_max, desired_move_in_date, number_of_occupants, " +
                         "has_pets, has_guarantor, employment_status, lead_source, " +
                         "created_at, converted_at, customer_id, employee_id, manager_id, " +
                         "property_id, letting_instruction_id " +
                         "FROM lead WHERE lead_id = 56";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                if (rs.next()) {
                    System.out.println("Lead ID: " + rs.getInt("lead_id"));
                    System.out.println("Name: " + rs.getString("name"));
                    System.out.println("Status: " + rs.getString("status"));
                    System.out.println("Phone: " + rs.getString("phone"));
                    System.out.println("Email: " + rs.getString("email"));
                    System.out.println("Lead Type: " + rs.getString("lead_type"));
                    System.out.println("Budget Min: " + rs.getBigDecimal("budget_min"));
                    System.out.println("Budget Max: " + rs.getBigDecimal("budget_max"));
                    System.out.println("Desired Move-in Date: " + rs.getDate("desired_move_in_date"));
                    System.out.println("Number of Occupants: " + rs.getObject("number_of_occupants"));
                    System.out.println("Has Pets: " + rs.getObject("has_pets"));
                    System.out.println("Has Guarantor: " + rs.getObject("has_guarantor"));
                    System.out.println("Employment Status: " + rs.getString("employment_status"));
                    System.out.println("Lead Source: " + rs.getString("lead_source"));
                    System.out.println("Created At: " + rs.getTimestamp("created_at"));
                    System.out.println("Converted At: " + rs.getTimestamp("converted_at"));
                    System.out.println("Customer ID: " + rs.getObject("customer_id"));
                    System.out.println("Employee ID: " + rs.getObject("employee_id"));
                    System.out.println("Manager ID: " + rs.getObject("manager_id"));
                    System.out.println("Property ID: " + rs.getObject("property_id"));
                    System.out.println("Letting Instruction ID: " + rs.getObject("letting_instruction_id"));
                } else {
                    System.out.println("No lead found with ID 56");
                }
            }

            // Check counts
            String[] countQueries = {
                "SELECT COUNT(*) FROM lead_action WHERE lead_id = 56",
                "SELECT COUNT(*) FROM property_viewing WHERE lead_id = 56",
                "SELECT COUNT(*) FROM attachment WHERE entity_type = 'LEAD' AND entity_id = 56"
            };

            String[] labels = {
                "\nLead Actions Count",
                "Property Viewings Count",
                "Attachments Count"
            };

            for (int i = 0; i < countQueries.length; i++) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(countQueries[i])) {
                    if (rs.next()) {
                        System.out.println(labels[i] + ": " + rs.getInt(1));
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
