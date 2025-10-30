import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class CustomPeriodAnalysis {

    static class Period {
        String name;
        LocalDate startDate;
        LocalDate endDate;

        Period(String name, LocalDate startDate, LocalDate endDate) {
            this.name = name;
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }

    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        // Define custom periods (22nd to 21st)
        Period[] periods = {
            new Period("December", LocalDate.of(2024, 11, 22), LocalDate.of(2024, 12, 21)),
            new Period("January", LocalDate.of(2024, 12, 22), LocalDate.of(2025, 1, 21)),
            new Period("February", LocalDate.of(2025, 1, 22), LocalDate.of(2025, 2, 21)),
            new Period("March", LocalDate.of(2025, 2, 22), LocalDate.of(2025, 3, 21)),
            new Period("April", LocalDate.of(2025, 3, 22), LocalDate.of(2025, 4, 21)),
            new Period("May", LocalDate.of(2025, 4, 22), LocalDate.of(2025, 5, 21)),
            new Period("June", LocalDate.of(2025, 5, 22), LocalDate.of(2025, 6, 21)),
            new Period("July", LocalDate.of(2025, 6, 22), LocalDate.of(2025, 7, 21)),
            new Period("August", LocalDate.of(2025, 7, 22), LocalDate.of(2025, 8, 21)),
            new Period("September", LocalDate.of(2025, 8, 22), LocalDate.of(2025, 9, 21)),
            new Period("October", LocalDate.of(2025, 9, 22), LocalDate.of(2025, 10, 21))
        };

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found.");
        }

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== INCOMING RENT BY CUSTOM PERIODS (22nd to 21st) ===\n");

            System.out.println("Period Definitions:");
            System.out.println("  December: 22/11/2024 - 21/12/2024");
            System.out.println("  January:  22/12/2024 - 21/01/2025");
            System.out.println("  February: 22/01/2025 - 21/02/2025");
            System.out.println("  (and so on...)\n");

            // Summary table header
            System.out.println("=== SUMMARY BY PERIOD ===");
            System.out.printf("%-12s %-20s %-20s %-20s %-20s%n",
                "PERIOD", "OLD ACCOUNT", "PAYPROP ACCOUNT", "SYSTEM TOTAL", "YOUR TOTAL");
            System.out.println("=".repeat(100));

            BigDecimal grandTotalOld = BigDecimal.ZERO;
            BigDecimal grandTotalPayProp = BigDecimal.ZERO;
            BigDecimal grandTotalSystem = BigDecimal.ZERO;

            // Your provided totals
            BigDecimal[] yourOldTotals = {
                new BigDecimal("1125"), new BigDecimal("6350"), new BigDecimal("18960"),
                new BigDecimal("21910"), new BigDecimal("21342.67"), new BigDecimal("17600"),
                new BigDecimal("7904"), new BigDecimal("6020"), new BigDecimal("6350"),
                new BigDecimal("5610")
            };
            BigDecimal[] yourPayPropTotals = {
                new BigDecimal("0"), new BigDecimal("0"), new BigDecimal("0"),
                new BigDecimal("0"), new BigDecimal("0"), new BigDecimal("3690"),
                new BigDecimal("14005"), new BigDecimal("15392"), new BigDecimal("15766"),
                new BigDecimal("16461")
            };

            int periodIndex = 0;

            for (Period period : periods) {
                // Skip December (index 0) as it's before our data range
                if (periodIndex == 0) {
                    periodIndex++;
                    continue;
                }

                // Historical transactions (Old Account)
                String historicalQuery = """
                    SELECT
                        COALESCE(SUM(amount), 0) as total
                    FROM historical_transactions
                    WHERE category = 'rent'
                      AND amount > 0
                      AND transaction_date >= ?
                      AND transaction_date <= ?
                    """;

                BigDecimal historicalTotal = BigDecimal.ZERO;
                try (PreparedStatement pstmt = conn.prepareStatement(historicalQuery)) {
                    pstmt.setDate(1, java.sql.Date.valueOf(period.startDate));
                    pstmt.setDate(2, java.sql.Date.valueOf(period.endDate));
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        historicalTotal = rs.getBigDecimal("total");
                    }
                }

                // PayProp transactions
                String paypropQuery = """
                    SELECT
                        COALESCE(SUM(amount), 0) as total
                    FROM financial_transactions
                    WHERE data_source = 'INCOMING_PAYMENT'
                      AND transaction_date >= ?
                      AND transaction_date <= ?
                    """;

                BigDecimal paypropTotal = BigDecimal.ZERO;
                try (PreparedStatement pstmt = conn.prepareStatement(paypropQuery)) {
                    pstmt.setDate(1, java.sql.Date.valueOf(period.startDate));
                    pstmt.setDate(2, java.sql.Date.valueOf(period.endDate));
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        paypropTotal = rs.getBigDecimal("total");
                    }
                }

                BigDecimal systemTotal = historicalTotal.add(paypropTotal);
                BigDecimal yourTotal = yourOldTotals[periodIndex - 1].add(yourPayPropTotals[periodIndex - 1]);

                grandTotalOld = grandTotalOld.add(historicalTotal);
                grandTotalPayProp = grandTotalPayProp.add(paypropTotal);
                grandTotalSystem = grandTotalSystem.add(systemTotal);

                // Calculate variance
                BigDecimal variance = systemTotal.subtract(yourTotal);
                String varianceStr = variance.compareTo(BigDecimal.ZERO) == 0 ? "✓" :
                    String.format("(Δ £%.2f)", variance);

                System.out.printf("%-12s £%-18.2f £%-18.2f £%-18.2f £%-18.2f %s%n",
                    period.name,
                    historicalTotal,
                    paypropTotal,
                    systemTotal,
                    yourTotal,
                    varianceStr);

                periodIndex++;
            }

            System.out.println("=".repeat(100));
            BigDecimal yourGrandTotal = new BigDecimal("113172").add(new BigDecimal("65314"));
            System.out.printf("%-12s £%-18.2f £%-18.2f £%-18.2f £%-18.2f%n",
                "GRAND TOTAL",
                grandTotalOld,
                grandTotalPayProp,
                grandTotalSystem,
                yourGrandTotal);

            // Detailed breakdown by period
            System.out.println("\n\n=== DETAILED BREAKDOWN BY PERIOD ===\n");

            periodIndex = 0;
            for (Period period : periods) {
                if (periodIndex == 0) {
                    periodIndex++;
                    continue;
                }

                System.out.println("=".repeat(80));
                System.out.println("PERIOD: " + period.name + " (" + period.startDate + " to " + period.endDate + ")");
                System.out.println("=".repeat(80));

                // Historical detail
                String historicalDetailQuery = """
                    SELECT
                        ht.transaction_date,
                        p.property_name,
                        ht.amount,
                        ht.description
                    FROM historical_transactions ht
                    LEFT JOIN properties p ON ht.property_id = p.id
                    WHERE ht.category = 'rent'
                      AND ht.amount > 0
                      AND ht.transaction_date >= ?
                      AND ht.transaction_date <= ?
                    ORDER BY ht.transaction_date
                    """;

                System.out.println("\nOLD ACCOUNT (Historical Transactions):");
                System.out.printf("%-15s %-35s %-12s%n", "DATE", "PROPERTY", "AMOUNT");
                System.out.println("-".repeat(70));

                BigDecimal periodHistTotal = BigDecimal.ZERO;
                int histCount = 0;

                try (PreparedStatement pstmt = conn.prepareStatement(historicalDetailQuery)) {
                    pstmt.setDate(1, java.sql.Date.valueOf(period.startDate));
                    pstmt.setDate(2, java.sql.Date.valueOf(period.endDate));
                    ResultSet rs = pstmt.executeQuery();

                    while (rs.next()) {
                        histCount++;
                        BigDecimal amount = rs.getBigDecimal("amount");
                        periodHistTotal = periodHistTotal.add(amount);

                        System.out.printf("%-15s %-35s £%-10.2f%n",
                            rs.getDate("transaction_date"),
                            rs.getString("property_name"),
                            amount);
                    }
                }

                if (histCount == 0) {
                    System.out.println("[No historical transactions in this period]");
                } else {
                    System.out.println("-".repeat(70));
                    System.out.printf("%-52s £%-10.2f%n", "SUBTOTAL (" + histCount + " transactions):", periodHistTotal);
                }

                // PayProp detail
                String paypropDetailQuery = """
                    SELECT
                        ft.transaction_date,
                        ft.property_name,
                        ft.amount,
                        ft.tenant_name
                    FROM financial_transactions ft
                    WHERE ft.data_source = 'INCOMING_PAYMENT'
                      AND ft.transaction_date >= ?
                      AND ft.transaction_date <= ?
                    ORDER BY ft.transaction_date
                    """;

                System.out.println("\nPAYPROP ACCOUNT (PayProp Incoming Payments):");
                System.out.printf("%-15s %-35s %-12s%n", "DATE", "PROPERTY", "AMOUNT");
                System.out.println("-".repeat(70));

                BigDecimal periodPayPropTotal = BigDecimal.ZERO;
                int paypropCount = 0;

                try (PreparedStatement pstmt = conn.prepareStatement(paypropDetailQuery)) {
                    pstmt.setDate(1, java.sql.Date.valueOf(period.startDate));
                    pstmt.setDate(2, java.sql.Date.valueOf(period.endDate));
                    ResultSet rs = pstmt.executeQuery();

                    while (rs.next()) {
                        paypropCount++;
                        BigDecimal amount = rs.getBigDecimal("amount");
                        periodPayPropTotal = periodPayPropTotal.add(amount);

                        System.out.printf("%-15s %-35s £%-10.2f%n",
                            rs.getDate("transaction_date"),
                            rs.getString("property_name"),
                            amount);
                    }
                }

                if (paypropCount == 0) {
                    System.out.println("[No PayProp transactions in this period]");
                } else {
                    System.out.println("-".repeat(70));
                    System.out.printf("%-52s £%-10.2f%n", "SUBTOTAL (" + paypropCount + " transactions):", periodPayPropTotal);
                }

                BigDecimal periodTotal = periodHistTotal.add(periodPayPropTotal);
                BigDecimal yourPeriodTotal = yourOldTotals[periodIndex - 1].add(yourPayPropTotals[periodIndex - 1]);

                System.out.println("\n" + "=".repeat(80));
                System.out.printf("%-52s £%-10.2f%n", "PERIOD TOTAL (System):", periodTotal);
                System.out.printf("%-52s £%-10.2f%n", "PERIOD TOTAL (Your Records):", yourPeriodTotal);
                System.out.printf("%-52s £%-10.2f%n", "VARIANCE:", periodTotal.subtract(yourPeriodTotal));
                System.out.println();

                periodIndex++;
            }

            System.out.println("\n=== ANALYSIS COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
