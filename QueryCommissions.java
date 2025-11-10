import java.sql.*;
import java.math.BigDecimal;

public class QueryCommissions {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, user, password);

            System.out.println("=== PROPERTY 1 COMMISSION TRANSACTIONS (Last 12 Months) ===\n");

            String query = """
                SELECT
                    transaction_date,
                    transaction_type,
                    amount,
                    description,
                    category
                FROM unified_transactions
                WHERE property_id = 1
                AND flow_direction = 'OUTGOING'
                AND transaction_date >= DATE_SUB(NOW(), INTERVAL 12 MONTH)
                AND (
                    transaction_type LIKE '%commission%'
                    OR transaction_type LIKE '%agency%'
                    OR transaction_type LIKE '%fee%'
                )
                ORDER BY transaction_date DESC, transaction_type
            """;

            PreparedStatement stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();

            System.out.printf("%-12s %-30s %-10s %-50s %-30s%n",
                "Date", "Type", "Amount", "Description", "Category");
            System.out.println("-".repeat(150));

            BigDecimal total = BigDecimal.ZERO;
            int count = 0;

            while (rs.next()) {
                Date date = rs.getDate("transaction_date");
                String type = rs.getString("transaction_type");
                BigDecimal amount = rs.getBigDecimal("amount");
                String desc = rs.getString("description");
                String category = rs.getString("category");

                if (desc != null && desc.length() > 50) {
                    desc = desc.substring(0, 47) + "...";
                }

                System.out.printf("%-12s %-30s £%-9.2f %-50s %-30s%n",
                    date != null ? date.toString() : "N/A",
                    type != null ? type : "N/A",
                    amount != null ? amount : BigDecimal.ZERO,
                    desc != null ? desc : "N/A",
                    category != null ? category : "N/A"
                );

                if (amount != null) {
                    total = total.add(amount);
                }
                count++;
            }

            System.out.println("-".repeat(150));
            System.out.printf("TOTAL: %d transactions = £%.2f%n", count, total);

            rs.close();

            // Group by transaction type
            System.out.println("\n=== COMMISSION SUMMARY BY TYPE ===\n");

            String summaryQuery = """
                SELECT
                    transaction_type,
                    COUNT(*) as count,
                    SUM(amount) as total_amount,
                    GROUP_CONCAT(DISTINCT category SEPARATOR ', ') as categories
                FROM unified_transactions
                WHERE property_id = 1
                AND flow_direction = 'OUTGOING'
                AND transaction_date >= DATE_SUB(NOW(), INTERVAL 12 MONTH)
                AND (
                    transaction_type LIKE '%commission%'
                    OR transaction_type LIKE '%agency%'
                    OR transaction_type LIKE '%fee%'
                )
                GROUP BY transaction_type
                ORDER BY total_amount DESC
            """;

            stmt = conn.prepareStatement(summaryQuery);
            rs = stmt.executeQuery();

            System.out.printf("%-30s %-10s %-15s %-50s%n", "Transaction Type", "Count", "Total Amount", "Categories");
            System.out.println("-".repeat(110));

            while (rs.next()) {
                String type = rs.getString("transaction_type");
                int typeCount = rs.getInt("count");
                BigDecimal typeTotal = rs.getBigDecimal("total_amount");
                String categories = rs.getString("categories");

                System.out.printf("%-30s %-10d £%-14.2f %-50s%n",
                    type != null ? type : "N/A",
                    typeCount,
                    typeTotal != null ? typeTotal : BigDecimal.ZERO,
                    categories != null ? categories : "N/A");
            }

            rs.close();

            // Check if commission_payment and payment_to_agency have same dates/amounts (potential duplicates)
            System.out.println("\n=== CHECKING FOR POTENTIAL DUPLICATES ===\n");

            String duplicateQuery = """
                SELECT
                    cp.transaction_date,
                    cp.amount as commission_payment_amount,
                    cp.description as commission_payment_desc,
                    pa.amount as payment_to_agency_amount,
                    pa.description as payment_to_agency_desc
                FROM
                    (SELECT transaction_date, amount, description FROM unified_transactions
                     WHERE property_id = 1 AND transaction_type = 'commission_payment'
                     AND transaction_date >= DATE_SUB(NOW(), INTERVAL 12 MONTH)) cp
                JOIN
                    (SELECT transaction_date, amount, description FROM unified_transactions
                     WHERE property_id = 1 AND transaction_type = 'payment_to_agency'
                     AND transaction_date >= DATE_SUB(NOW(), INTERVAL 12 MONTH)) pa
                ON cp.transaction_date = pa.transaction_date
                   AND cp.amount = pa.amount
                ORDER BY cp.transaction_date DESC
            """;

            stmt = conn.prepareStatement(duplicateQuery);
            rs = stmt.executeQuery();

            System.out.printf("%-12s %-15s %-50s %-50s%n",
                "Date", "Amount", "Commission Payment Desc", "Payment to Agency Desc");
            System.out.println("-".repeat(130));

            int duplicates = 0;
            while (rs.next()) {
                Date date = rs.getDate("transaction_date");
                BigDecimal amount = rs.getBigDecimal("commission_payment_amount");
                String cpDesc = rs.getString("commission_payment_desc");
                String paDesc = rs.getString("payment_to_agency_desc");

                if (cpDesc != null && cpDesc.length() > 50) {
                    cpDesc = cpDesc.substring(0, 47) + "...";
                }
                if (paDesc != null && paDesc.length() > 50) {
                    paDesc = paDesc.substring(0, 47) + "...";
                }

                System.out.printf("%-12s £%-14.2f %-50s %-50s%n",
                    date != null ? date.toString() : "N/A",
                    amount != null ? amount : BigDecimal.ZERO,
                    cpDesc != null ? cpDesc : "N/A",
                    paDesc != null ? paDesc : "N/A");

                duplicates++;
            }

            if (duplicates > 0) {
                System.out.println("\nWARNING: FOUND " + duplicates + " POTENTIAL DUPLICATE COMMISSION TRANSACTIONS!");
                System.out.println("Commission and agency payments with matching dates and amounts may be double-counting the same commission.");
            } else {
                System.out.println("\nNo duplicate commissions found (commission_payment and payment_to_agency have different dates or amounts).");
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
