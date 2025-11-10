import java.sql.*;
import java.math.BigDecimal;

public class QueryBeneficiaryTypes {
    public static void main(String[] args) {
        String url = "jdbc:mysql://autorack.proxy.rlwy.net:45349/railway";
        String user = "root";
        String password = "bvitbJhYIVGfVIOBlQHWvZFewEFgEwEh";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, user, password);

            System.out.println("=== PROPERTY 1 OUTGOING TRANSACTIONS WITH BENEFICIARY TYPES ===\n");

            String query = """
                SELECT
                    id,
                    transaction_date,
                    transaction_type,
                    amount,
                    payprop_beneficiary_type,
                    description
                FROM financial_transactions
                WHERE property_id = 1
                AND transaction_date >= DATE_SUB(NOW(), INTERVAL 12 MONTH)
                AND flow_direction = 'OUTGOING'
                ORDER BY transaction_date DESC
            """;

            PreparedStatement stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();

            System.out.printf("%-8s %-12s %-30s %-10s %-20s %-50s%n",
                "ID", "Date", "Type", "Amount", "Beneficiary Type", "Description");
            System.out.println("-".repeat(160));

            BigDecimal total = BigDecimal.ZERO;
            int count = 0;

            while (rs.next()) {
                long id = rs.getLong("id");
                Date date = rs.getDate("transaction_date");
                String type = rs.getString("transaction_type");
                BigDecimal amount = rs.getBigDecimal("amount");
                String beneficiaryType = rs.getString("payprop_beneficiary_type");
                String desc = rs.getString("description");

                if (desc != null && desc.length() > 50) {
                    desc = desc.substring(0, 47) + "...";
                }

                System.out.printf("%-8d %-12s %-30s £%-9.2f %-20s %-50s%n",
                    id,
                    date != null ? date.toString() : "N/A",
                    type != null ? type : "N/A",
                    amount != null ? amount : BigDecimal.ZERO,
                    beneficiaryType != null ? beneficiaryType : "NULL",
                    desc != null ? desc : "N/A"
                );

                if (amount != null) {
                    total = total.add(amount);
                }
                count++;
            }

            System.out.println("-".repeat(160));
            System.out.printf("TOTAL: %d transactions = £%.2f%n", count, total);

            rs.close();

            // Summary by beneficiary type
            System.out.println("\n=== SUMMARY BY BENEFICIARY TYPE ===\n");

            String summaryQuery = """
                SELECT
                    payprop_beneficiary_type,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM financial_transactions
                WHERE property_id = 1
                AND transaction_date >= DATE_SUB(NOW(), INTERVAL 12 MONTH)
                AND flow_direction = 'OUTGOING'
                GROUP BY payprop_beneficiary_type
                ORDER BY total_amount DESC
            """;

            stmt = conn.prepareStatement(summaryQuery);
            rs = stmt.executeQuery();

            System.out.printf("%-25s %-10s %-15s%n", "Beneficiary Type", "Count", "Total Amount");
            System.out.println("-".repeat(60));

            while (rs.next()) {
                String beneficiaryType = rs.getString("payprop_beneficiary_type");
                int typeCount = rs.getInt("count");
                BigDecimal typeTotal = rs.getBigDecimal("total_amount");

                System.out.printf("%-25s %-10d £%-14.2f%n",
                    beneficiaryType != null ? beneficiaryType : "NULL",
                    typeCount,
                    typeTotal != null ? typeTotal : BigDecimal.ZERO);
            }

            rs.close();

            // Show specifically payment_to_beneficiary transactions
            System.out.println("\n=== PAYMENT_TO_BENEFICIARY TRANSACTIONS ONLY ===\n");

            String beneficiaryQuery = """
                SELECT
                    id,
                    transaction_date,
                    amount,
                    payprop_beneficiary_type,
                    description
                FROM financial_transactions
                WHERE property_id = 1
                AND transaction_date >= DATE_SUB(NOW(), INTERVAL 12 MONTH)
                AND flow_direction = 'OUTGOING'
                AND transaction_type = 'payment_to_beneficiary'
                ORDER BY transaction_date DESC
            """;

            stmt = conn.prepareStatement(beneficiaryQuery);
            rs = stmt.executeQuery();

            System.out.printf("%-8s %-12s %-10s %-20s %-60s%n",
                "ID", "Date", "Amount", "Beneficiary Type", "Description");
            System.out.println("-".repeat(140));

            BigDecimal beneficiaryTotal = BigDecimal.ZERO;
            int beneficiaryCount = 0;

            while (rs.next()) {
                long id = rs.getLong("id");
                Date date = rs.getDate("transaction_date");
                BigDecimal amount = rs.getBigDecimal("amount");
                String beneficiaryType = rs.getString("payprop_beneficiary_type");
                String desc = rs.getString("description");

                if (desc != null && desc.length() > 60) {
                    desc = desc.substring(0, 57) + "...";
                }

                System.out.printf("%-8d %-12s £%-9.2f %-20s %-60s%n",
                    id,
                    date != null ? date.toString() : "N/A",
                    amount != null ? amount : BigDecimal.ZERO,
                    beneficiaryType != null ? beneficiaryType : "NULL",
                    desc != null ? desc : "N/A"
                );

                if (amount != null) {
                    beneficiaryTotal = beneficiaryTotal.add(amount);
                }
                beneficiaryCount++;
            }

            System.out.println("-".repeat(140));
            System.out.printf("PAYMENT_TO_BENEFICIARY TOTAL: %d transactions = £%.2f%n", beneficiaryCount, beneficiaryTotal);

            rs.close();
            stmt.close();
            conn.close();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
