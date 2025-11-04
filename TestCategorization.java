import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TestCategorization {
    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("MySQL driver not found, trying without explicit load...");
        }

        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "NXRedwMHejnfLPDdzZgnzOWpLreBpUsl";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {

            System.out.println("=".repeat(100));
            System.out.println("APPLYING CATEGORIZATION RULES TO unified_transactions (LAST 12 MONTHS)");
            System.out.println("=".repeat(100));

            String query =
                "SELECT " +
                "  transaction_type, " +
                "  category, " +
                "  beneficiary_type, " +
                "  COUNT(*) as count, " +
                "  SUM(amount) as total_amount, " +
                "  -- Rent Income Rule " +
                "  SUM(CASE WHEN transaction_type IN ('invoice', 'payment', 'incoming_payment', 'rent_received', 'tenant_payment') " +
                "            OR category = 'rent' " +
                "       THEN amount ELSE 0 END) as rent_income, " +
                "  -- Expense Rule (contractor payments) " +
                "  SUM(CASE WHEN transaction_type IN ('expense', 'maintenance', 'payment_to_contractor') " +
                "            OR beneficiary_type = 'contractor' " +
                "       THEN ABS(amount) ELSE 0 END) as expenses, " +
                "  -- Commission Rule (agency payments) " +
                "  SUM(CASE WHEN transaction_type IN ('fee', 'commission_payment', 'payment_to_agency') " +
                "            OR beneficiary_type = 'agency' " +
                "            OR category IN ('management_fee', 'commission') " +
                "       THEN ABS(amount) ELSE 0 END) as commissions, " +
                "  -- Owner Payment Rule " +
                "  SUM(CASE WHEN beneficiary_type = 'beneficiary' " +
                "       THEN ABS(amount) ELSE 0 END) as owner_payments " +
                "FROM unified_transactions " +
                "WHERE transaction_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH) " +
                "GROUP BY transaction_type, category, beneficiary_type " +
                "ORDER BY count DESC";

            BigDecimal totalRentIncome = BigDecimal.ZERO;
            BigDecimal totalExpenses = BigDecimal.ZERO;
            BigDecimal totalCommissions = BigDecimal.ZERO;
            BigDecimal totalOwnerPayments = BigDecimal.ZERO;

            System.out.println();
            System.out.printf("%-30s | %-20s | %-15s | %5s | %12s | %12s | %12s | %12s | %12s%n",
                "Transaction Type", "Category", "Benefic Type", "Count", "Total Amt", "Rent", "Expenses", "Commission", "Owner Pay");
            System.out.println("-".repeat(180));

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    String txType = rs.getString("transaction_type");
                    String category = rs.getString("category");
                    String benefType = rs.getString("beneficiary_type");
                    int count = rs.getInt("count");
                    BigDecimal totalAmt = rs.getBigDecimal("total_amount");
                    BigDecimal rent = rs.getBigDecimal("rent_income");
                    BigDecimal expense = rs.getBigDecimal("expenses");
                    BigDecimal commission = rs.getBigDecimal("commissions");
                    BigDecimal ownerPay = rs.getBigDecimal("owner_payments");

                    totalRentIncome = totalRentIncome.add(rent != null ? rent : BigDecimal.ZERO);
                    totalExpenses = totalExpenses.add(expense != null ? expense : BigDecimal.ZERO);
                    totalCommissions = totalCommissions.add(commission != null ? commission : BigDecimal.ZERO);
                    totalOwnerPayments = totalOwnerPayments.add(ownerPay != null ? ownerPay : BigDecimal.ZERO);

                    System.out.printf("%-30s | %-20s | %-15s | %5d | £%,10.2f | £%,10.2f | £%,10.2f | £%,10.2f | £%,10.2f%n",
                        truncate(txType, 30),
                        truncate(category, 20),
                        truncate(benefType, 15),
                        count,
                        totalAmt != null ? totalAmt : BigDecimal.ZERO,
                        rent != null ? rent : BigDecimal.ZERO,
                        expense != null ? expense : BigDecimal.ZERO,
                        commission != null ? commission : BigDecimal.ZERO,
                        ownerPay != null ? ownerPay : BigDecimal.ZERO);
                }
            }

            System.out.println("=".repeat(180));
            System.out.println();
            System.out.println("TOTALS (LAST 12 MONTHS):");
            System.out.println("-".repeat(80));
            System.out.printf("Total Rent Income:    £%,15.2f%n", totalRentIncome);
            System.out.printf("Total Expenses:       £%,15.2f%n", totalExpenses);
            System.out.printf("Total Commissions:    £%,15.2f%n", totalCommissions);
            System.out.printf("Total Owner Payments: £%,15.2f%n", totalOwnerPayments);
            System.out.println("-".repeat(80));

            BigDecimal netToOwner = totalRentIncome.subtract(totalExpenses).subtract(totalCommissions);
            System.out.printf("NET TO OWNER:         £%,15.2f%n", netToOwner);
            System.out.println();
            System.out.println("Formula: Rent Income - Expenses - Commissions = Net to Owner");
            System.out.printf("         £%,.2f - £%,.2f - £%,.2f = £%,.2f%n",
                totalRentIncome, totalExpenses, totalCommissions, netToOwner);

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "NULL";
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}
