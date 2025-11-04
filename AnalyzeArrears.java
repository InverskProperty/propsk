import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class AnalyzeArrears {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "NXRedwMHejnfLPDdzZgnzOWpLreBpUsl";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {

            System.out.println("=".repeat(100));
            System.out.println("ANALYZING RENT ARREARS - Comparing Invoice vs Actual Payments (Last 12 Months)");
            System.out.println("=".repeat(100));
            System.out.println();

            // Date range: last 12 months
            String dateFilter = "DATE_SUB(CURDATE(), INTERVAL 12 MONTH)";

            // STEP 1: Get rent RECEIVED from unified_transactions
            System.out.println("STEP 1: RENT RECEIVED (from unified_transactions)");
            System.out.println("-".repeat(100));

            String rentReceivedQuery =
                "SELECT " +
                "  COUNT(*) as tx_count, " +
                "  SUM(amount) as total_received " +
                "FROM unified_transactions " +
                "WHERE transaction_date >= " + dateFilter + " " +
                "  AND ( " +
                "    transaction_type IN ('incoming_payment', 'rent_received', 'payment', 'invoice', 'tenant_payment') " +
                "    OR category = 'rent' " +
                "  )";

            BigDecimal rentReceived = BigDecimal.ZERO;
            int rentTxCount = 0;

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(rentReceivedQuery)) {
                if (rs.next()) {
                    rentTxCount = rs.getInt("tx_count");
                    rentReceived = rs.getBigDecimal("total_received");
                    System.out.printf("Total Rent Transactions: %d%n", rentTxCount);
                    System.out.printf("Total Rent Received: £%,.2f%n", rentReceived);
                }
            }
            System.out.println();

            // Show breakdown by transaction type
            String breakdownQuery =
                "SELECT " +
                "  transaction_type, " +
                "  COUNT(*) as count, " +
                "  SUM(amount) as total " +
                "FROM unified_transactions " +
                "WHERE transaction_date >= " + dateFilter + " " +
                "  AND ( " +
                "    transaction_type IN ('incoming_payment', 'rent_received', 'payment', 'invoice', 'tenant_payment') " +
                "    OR category = 'rent' " +
                "  ) " +
                "GROUP BY transaction_type " +
                "ORDER BY total DESC";

            System.out.println("Breakdown by Transaction Type:");
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(breakdownQuery)) {
                while (rs.next()) {
                    String type = rs.getString("transaction_type");
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");
                    System.out.printf("  %-30s | Count: %4d | Total: £%,12.2f%n", type, count, total);
                }
            }
            System.out.println();

            // STEP 2: Get rent DUE from invoices
            System.out.println("STEP 2: RENT DUE (from invoice table)");
            System.out.println("-".repeat(100));

            String rentDueQuery =
                "SELECT " +
                "  COUNT(DISTINCT i.id) as invoice_count, " +
                "  COUNT(DISTINCT i.property_id) as property_count, " +
                "  SUM(i.rent_amount) as total_monthly_rent " +
                "FROM invoice i " +
                "WHERE i.tenancy_start_date <= CURDATE() " +
                "  AND (i.tenancy_end_date IS NULL OR i.tenancy_end_date >= " + dateFilter + ")";

            int invoiceCount = 0;
            int propertyCount = 0;
            BigDecimal totalMonthlyRent = BigDecimal.ZERO;

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(rentDueQuery)) {
                if (rs.next()) {
                    invoiceCount = rs.getInt("invoice_count");
                    propertyCount = rs.getInt("property_count");
                    totalMonthlyRent = rs.getBigDecimal("total_monthly_rent");
                    System.out.printf("Active Invoices/Leases: %d%n", invoiceCount);
                    System.out.printf("Properties with Invoices: %d%n", propertyCount);
                    System.out.printf("Total Monthly Rent: £%,.2f%n", totalMonthlyRent);
                }
            }

            // Estimate total due (monthly rent * 12 months)
            BigDecimal estimatedRentDue = totalMonthlyRent.multiply(BigDecimal.valueOf(12));
            System.out.printf("Estimated Rent Due (12 months): £%,.2f%n", estimatedRentDue);
            System.out.println();

            // STEP 3: Calculate ARREARS
            System.out.println("STEP 3: RENT ARREARS CALCULATION");
            System.out.println("-".repeat(100));

            BigDecimal arrears = estimatedRentDue.subtract(rentReceived);

            System.out.printf("Estimated Rent Due:    £%,15.2f%n", estimatedRentDue);
            System.out.printf("Actual Rent Received:  £%,15.2f%n", rentReceived);
            System.out.println("-".repeat(60));
            System.out.printf("ARREARS:               £%,15.2f%n", arrears);
            System.out.println();

            if (arrears.compareTo(BigDecimal.ZERO) < 0) {
                System.out.println("NEGATIVE ARREARS means: More rent received than expected!");
                System.out.printf("Overpayment amount: £%,.2f%n", arrears.abs());
            } else if (arrears.compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("POSITIVE ARREARS means: Rent is owed!");
                System.out.printf("Outstanding amount: £%,.2f%n", arrears);
            } else {
                System.out.println("ZERO ARREARS: All rent paid up!");
            }
            System.out.println();

            // STEP 4: Check for potential issues
            System.out.println("STEP 4: POTENTIAL ISSUES");
            System.out.println("-".repeat(100));

            // Check if there are properties in unified_transactions without invoices
            String orphanQuery =
                "SELECT COUNT(DISTINCT ut.property_id) as properties_without_invoices " +
                "FROM unified_transactions ut " +
                "WHERE ut.transaction_date >= " + dateFilter + " " +
                "  AND ut.property_id IS NOT NULL " +
                "  AND ut.property_id NOT IN (SELECT DISTINCT property_id FROM invoice WHERE property_id IS NOT NULL)";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(orphanQuery)) {
                if (rs.next()) {
                    int orphans = rs.getInt("properties_without_invoices");
                    if (orphans > 0) {
                        System.out.printf("WARNING: %d properties have transactions but NO invoices in the system!%n", orphans);
                        System.out.println("This would cause negative arrears (receiving rent without expected invoices)");
                    } else {
                        System.out.println("OK: All properties with transactions have invoices");
                    }
                }
            }
            System.out.println();

            // Check for deposits that might be counted as rent
            String depositQuery =
                "SELECT COUNT(*) as potential_deposits, SUM(amount) as total " +
                "FROM unified_transactions " +
                "WHERE transaction_date >= " + dateFilter + " " +
                "  AND description LIKE '%deposit%'";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(depositQuery)) {
                if (rs.next()) {
                    int depositCount = rs.getInt("potential_deposits");
                    BigDecimal depositTotal = rs.getBigDecimal("total");
                    if (depositCount > 0) {
                        System.out.printf("WARNING: %d transactions with 'deposit' in description (£%,.2f total)%n",
                            depositCount, depositTotal);
                        System.out.println("These might be incorrectly counted as rent received!");
                    } else {
                        System.out.println("OK: No obvious deposit transactions in rent income");
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
