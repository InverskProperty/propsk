import java.sql.*;
import java.math.BigDecimal;

public class AnalyzeTransactionTypes {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, user, password);

            System.out.println("=== TRANSACTION TYPES IN UNIFIED_TRANSACTIONS ===\n");

            // Query 1: Transaction types in unified_transactions
            String query1 = """
                SELECT
                    transaction_type,
                    flow_direction,
                    source_system,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM unified_transactions
                GROUP BY transaction_type, flow_direction, source_system
                ORDER BY source_system, flow_direction, count DESC
            """;

            PreparedStatement stmt = conn.prepareStatement(query1);
            ResultSet rs = stmt.executeQuery();

            System.out.printf("%-30s %-12s %-15s %-10s %-15s%n",
                "Transaction Type", "Flow", "Source", "Count", "Total Amount");
            System.out.println("-".repeat(100));

            while (rs.next()) {
                String txType = rs.getString("transaction_type");
                String flow = rs.getString("flow_direction");
                String source = rs.getString("source_system");
                int count = rs.getInt("count");
                BigDecimal total = rs.getBigDecimal("total_amount");

                System.out.printf("%-30s %-12s %-15s %-10d £%-14.2f%n",
                    txType != null ? txType : "NULL",
                    flow != null ? flow : "NULL",
                    source != null ? source : "NULL",
                    count,
                    total != null ? total : BigDecimal.ZERO);
            }

            rs.close();

            // Query 2: Transaction types in financial_transactions (PayProp source)
            System.out.println("\n\n=== TRANSACTION TYPES IN FINANCIAL_TRANSACTIONS (PayProp Source) ===\n");

            String query2 = """
                SELECT
                    transaction_type,
                    data_source,
                    COUNT(*) as count,
                    SUM(amount) as total_amount
                FROM financial_transactions
                GROUP BY transaction_type, data_source
                ORDER BY data_source, count DESC
            """;

            stmt = conn.prepareStatement(query2);
            rs = stmt.executeQuery();

            System.out.printf("%-35s %-25s %-10s %-15s%n",
                "Transaction Type", "Data Source", "Count", "Total Amount");
            System.out.println("-".repeat(100));

            while (rs.next()) {
                String txType = rs.getString("transaction_type");
                String dataSource = rs.getString("data_source");
                int count = rs.getInt("count");
                BigDecimal total = rs.getBigDecimal("total_amount");

                System.out.printf("%-35s %-25s %-10d £%-14.2f%n",
                    txType != null ? txType : "NULL",
                    dataSource != null ? dataSource : "NULL",
                    count,
                    total != null ? total : BigDecimal.ZERO);
            }

            rs.close();

            // Query 3: Sample transactions to understand the pattern
            System.out.println("\n\n=== SAMPLE PAYPROP TRANSACTIONS (Property 1) ===\n");

            String query3 = """
                SELECT
                    transaction_date,
                    transaction_type,
                    data_source,
                    amount,
                    description,
                    category_name
                FROM financial_transactions
                WHERE property_id = '1'
                ORDER BY transaction_date DESC
                LIMIT 20
            """;

            stmt = conn.prepareStatement(query3);
            rs = stmt.executeQuery();

            System.out.printf("%-12s %-30s %-25s %-10s %-50s%n",
                "Date", "Type", "Data Source", "Amount", "Description");
            System.out.println("-".repeat(150));

            while (rs.next()) {
                Date date = rs.getDate("transaction_date");
                String txType = rs.getString("transaction_type");
                String dataSource = rs.getString("data_source");
                BigDecimal amount = rs.getBigDecimal("amount");
                String desc = rs.getString("description");

                if (desc != null && desc.length() > 50) {
                    desc = desc.substring(0, 47) + "...";
                }

                System.out.printf("%-12s %-30s %-25s £%-9.2f %-50s%n",
                    date != null ? date.toString() : "N/A",
                    txType != null ? txType : "N/A",
                    dataSource != null ? dataSource : "N/A",
                    amount != null ? amount : BigDecimal.ZERO,
                    desc != null ? desc : "N/A");
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
