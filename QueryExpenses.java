import java.sql.*;
import java.math.BigDecimal;

public class QueryExpenses {
    public static void main(String[] args) {
        String url = "jdbc:mysql://autorack.proxy.rlwy.net:45349/railway";
        String user = "root";
        String password = "bvitbJhYIVGfVIOBlQHWvZFewEFgEwEh";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, user, password);

            System.out.println("=== PROPERTY 1 EXPENSES (Last 12 Months) ===\n");

            String query = """
                SELECT
                    transaction_date,
                    transaction_type,
                    category_name,
                    beneficiary_type,
                    flow_direction,
                    amount,
                    description,
                    source_system
                FROM unified_transactions
                WHERE property_id = 1
                AND flow_direction = 'OUTGOING'
                AND transaction_date >= DATE_SUB(NOW(), INTERVAL 12 MONTH)
                AND (
                    transaction_type LIKE '%expense%'
                    OR transaction_type LIKE '%repair%'
                    OR transaction_type LIKE '%maintenance%'
                    OR transaction_type LIKE '%utility%'
                    OR transaction_type LIKE '%tax%'
                    OR transaction_type LIKE '%insurance%'
                    OR transaction_type = 'payment_to_beneficiary'
                )
                AND transaction_type NOT LIKE '%commission%'
                AND transaction_type NOT LIKE '%agency%'
                AND transaction_type NOT LIKE '%fee%'
                ORDER BY transaction_date DESC
            """;

            PreparedStatement stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();

            System.out.printf("%-12s %-25s %-20s %-15s %-10s %-50s%n",
                "Date", "Type", "Category", "Beneficiary", "Amount", "Description");
            System.out.println("-".repeat(140));

            BigDecimal total = BigDecimal.ZERO;
            int count = 0;

            while (rs.next()) {
                Date date = rs.getDate("transaction_date");
                String type = rs.getString("transaction_type");
                String category = rs.getString("category_name");
                String beneficiary = rs.getString("beneficiary_type");
                BigDecimal amount = rs.getBigDecimal("amount");
                String desc = rs.getString("description");

                if (desc != null && desc.length() > 50) {
                    desc = desc.substring(0, 47) + "...";
                }

                System.out.printf("%-12s %-25s %-20s %-15s £%-9.2f %-50s%n",
                    date != null ? date.toString() : "N/A",
                    type != null ? type : "N/A",
                    category != null ? category : "N/A",
                    beneficiary != null ? beneficiary : "N/A",
                    amount != null ? amount : BigDecimal.ZERO,
                    desc != null ? desc : "N/A"
                );

                if (amount != null) {
                    total = total.add(amount);
                }
                count++;
            }

            System.out.println("-".repeat(140));
            System.out.printf("TOTAL: %d transactions = £%.2f%n", count, total);

            rs.close();

            // Summary by type
            System.out.println("\n=== SUMMARY BY TRANSACTION TYPE ===\n");

            String summaryQuery = """
                SELECT
                    transaction_type,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM unified_transactions
                WHERE property_id = 1
                AND flow_direction = 'OUTGOING'
                AND transaction_date >= DATE_SUB(NOW(), INTERVAL 12 MONTH)
                AND (
                    transaction_type LIKE '%expense%'
                    OR transaction_type LIKE '%repair%'
                    OR transaction_type LIKE '%maintenance%'
                    OR transaction_type LIKE '%utility%'
                    OR transaction_type LIKE '%tax%'
                    OR transaction_type LIKE '%insurance%'
                    OR transaction_type = 'payment_to_beneficiary'
                )
                AND transaction_type NOT LIKE '%commission%'
                AND transaction_type NOT LIKE '%agency%'
                AND transaction_type NOT LIKE '%fee%'
                GROUP BY transaction_type
                ORDER BY total_amount DESC
            """;

            stmt = conn.prepareStatement(summaryQuery);
            rs = stmt.executeQuery();

            System.out.printf("%-30s %-10s %-15s%n", "Transaction Type", "Count", "Total Amount");
            System.out.println("-".repeat(60));

            while (rs.next()) {
                String type = rs.getString("transaction_type");
                int typeCount = rs.getInt("count");
                BigDecimal typeTotal = rs.getBigDecimal("total_amount");

                System.out.printf("%-30s %-10d £%-14.2f%n",
                    type != null ? type : "N/A",
                    typeCount,
                    typeTotal != null ? typeTotal : BigDecimal.ZERO);
            }

            rs.close();
            stmt.close();
            conn.close();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
