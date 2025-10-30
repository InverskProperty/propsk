import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class InvestigateDifferences {

    static class Period {
        String name;
        LocalDate startDate;
        LocalDate endDate;
        BigDecimal yourOld;
        BigDecimal yourPayProp;
        BigDecimal yourTotal;

        Period(String name, LocalDate startDate, LocalDate endDate, String old, String payprop) {
            this.name = name;
            this.startDate = startDate;
            this.endDate = endDate;
            this.yourOld = new BigDecimal(old);
            this.yourPayProp = new BigDecimal(payprop);
            this.yourTotal = this.yourOld.add(this.yourPayProp);
        }
    }

    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        // Your updated numbers
        Period[] periods = {
            new Period("January", LocalDate.of(2024, 12, 22), LocalDate.of(2025, 1, 21), "0", "0"),
            new Period("February", LocalDate.of(2025, 1, 22), LocalDate.of(2025, 2, 21), "7475", "0"),
            new Period("March", LocalDate.of(2025, 2, 22), LocalDate.of(2025, 3, 21), "20085", "0"),
            new Period("April", LocalDate.of(2025, 3, 22), LocalDate.of(2025, 4, 21), "20470", "0"),
            new Period("May", LocalDate.of(2025, 4, 22), LocalDate.of(2025, 5, 21), "22800", "0"),
            new Period("June", LocalDate.of(2025, 5, 22), LocalDate.of(2025, 6, 21), "16475", "3690"),
            new Period("July", LocalDate.of(2025, 6, 22), LocalDate.of(2025, 7, 21), "7460", "15130"),
            new Period("August", LocalDate.of(2025, 7, 22), LocalDate.of(2025, 8, 21), "6458", "15384"),
            new Period("September", LocalDate.of(2025, 8, 22), LocalDate.of(2025, 9, 21), "5225", "14271"),
            new Period("October", LocalDate.of(2025, 9, 22), LocalDate.of(2025, 10, 21), "6735", "19516")
        };

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found.");
        }

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== VARIANCE INVESTIGATION: SYSTEM vs YOUR RECORDS ===\n");
            System.out.println("Your Totals:");
            System.out.println("  Old Account (Historical): £113,183");
            System.out.println("  PayProp Account: £67,991");
            System.out.println("  Combined Total: £181,174\n");

            // Summary comparison
            System.out.println("=== PERIOD COMPARISON ===");
            System.out.printf("%-12s %-15s %-15s %-15s %-15s %-15s%n",
                "PERIOD", "YOUR TOTAL", "SYS TOTAL", "VARIANCE", "SYS OLD", "SYS PAYPROP");
            System.out.println("=".repeat(95));

            BigDecimal grandTotalYours = BigDecimal.ZERO;
            BigDecimal grandTotalSystem = BigDecimal.ZERO;
            BigDecimal grandTotalSystemOld = BigDecimal.ZERO;
            BigDecimal grandTotalSystemPayProp = BigDecimal.ZERO;

            for (Period period : periods) {
                // Historical transactions
                String historicalQuery = """
                    SELECT COALESCE(SUM(amount), 0) as total
                    FROM historical_transactions
                    WHERE category = 'rent'
                      AND amount > 0
                      AND transaction_date >= ?
                      AND transaction_date <= ?
                    """;

                BigDecimal systemOld = BigDecimal.ZERO;
                try (PreparedStatement pstmt = conn.prepareStatement(historicalQuery)) {
                    pstmt.setDate(1, java.sql.Date.valueOf(period.startDate));
                    pstmt.setDate(2, java.sql.Date.valueOf(period.endDate));
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        systemOld = rs.getBigDecimal("total");
                    }
                }

                // PayProp transactions
                String paypropQuery = """
                    SELECT COALESCE(SUM(amount), 0) as total
                    FROM financial_transactions
                    WHERE data_source = 'INCOMING_PAYMENT'
                      AND transaction_date >= ?
                      AND transaction_date <= ?
                    """;

                BigDecimal systemPayProp = BigDecimal.ZERO;
                try (PreparedStatement pstmt = conn.prepareStatement(paypropQuery)) {
                    pstmt.setDate(1, java.sql.Date.valueOf(period.startDate));
                    pstmt.setDate(2, java.sql.Date.valueOf(period.endDate));
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        systemPayProp = rs.getBigDecimal("total");
                    }
                }

                BigDecimal systemTotal = systemOld.add(systemPayProp);
                BigDecimal variance = systemTotal.subtract(period.yourTotal);

                grandTotalYours = grandTotalYours.add(period.yourTotal);
                grandTotalSystem = grandTotalSystem.add(systemTotal);
                grandTotalSystemOld = grandTotalSystemOld.add(systemOld);
                grandTotalSystemPayProp = grandTotalSystemPayProp.add(systemPayProp);

                String flag = variance.abs().compareTo(new BigDecimal("10")) > 0 ? " [!]" : "";

                System.out.printf("%-12s £%-14.2f £%-14.2f £%-14.2f £%-14.2f £%-14.2f%s%n",
                    period.name,
                    period.yourTotal,
                    systemTotal,
                    variance,
                    systemOld,
                    systemPayProp,
                    flag);
            }

            System.out.println("=".repeat(95));
            System.out.printf("%-12s £%-14.2f £%-14.2f £%-14.2f £%-14.2f £%-14.2f%n",
                "GRAND TOTAL",
                grandTotalYours,
                grandTotalSystem,
                grandTotalSystem.subtract(grandTotalYours),
                grandTotalSystemOld,
                grandTotalSystemPayProp);

            // Detailed investigation of major variances
            System.out.println("\n\n=== INVESTIGATING MAJOR VARIANCES ===\n");

            // Check periods with >£500 variance
            for (Period period : periods) {
                String historicalQuery = """
                    SELECT COALESCE(SUM(amount), 0) as total FROM historical_transactions
                    WHERE category = 'rent' AND amount > 0
                      AND transaction_date >= ? AND transaction_date <= ?
                    """;
                BigDecimal systemOld = BigDecimal.ZERO;
                try (PreparedStatement pstmt = conn.prepareStatement(historicalQuery)) {
                    pstmt.setDate(1, java.sql.Date.valueOf(period.startDate));
                    pstmt.setDate(2, java.sql.Date.valueOf(period.endDate));
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) systemOld = rs.getBigDecimal("total");
                }

                String paypropQuery = """
                    SELECT COALESCE(SUM(amount), 0) as total FROM financial_transactions
                    WHERE data_source = 'INCOMING_PAYMENT'
                      AND transaction_date >= ? AND transaction_date <= ?
                    """;
                BigDecimal systemPayProp = BigDecimal.ZERO;
                try (PreparedStatement pstmt = conn.prepareStatement(paypropQuery)) {
                    pstmt.setDate(1, java.sql.Date.valueOf(period.startDate));
                    pstmt.setDate(2, java.sql.Date.valueOf(period.endDate));
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) systemPayProp = rs.getBigDecimal("total");
                }

                BigDecimal systemTotal = systemOld.add(systemPayProp);
                BigDecimal variance = systemTotal.subtract(period.yourTotal);

                if (variance.abs().compareTo(new BigDecimal("500")) > 0) {
                    System.out.println("=".repeat(90));
                    System.out.println("PERIOD: " + period.name + " - Variance: £" + variance);
                    System.out.println("=".repeat(90));
                    System.out.println("Your Records: Old £" + period.yourOld + " + PayProp £" + period.yourPayProp + " = £" + period.yourTotal);
                    System.out.println("System:       Old £" + systemOld + " + PayProp £" + systemPayProp + " = £" + systemTotal);
                    System.out.println();

                    // Show differences in detail
                    if (!systemOld.equals(period.yourOld) && systemOld.compareTo(BigDecimal.ZERO) > 0) {
                        System.out.println("OLD ACCOUNT VARIANCE: £" + systemOld.subtract(period.yourOld));

                        String detailQuery = """
                            SELECT
                                ht.transaction_date,
                                p.property_name,
                                ht.amount
                            FROM historical_transactions ht
                            LEFT JOIN properties p ON ht.property_id = p.id
                            WHERE ht.category = 'rent' AND ht.amount > 0
                              AND ht.transaction_date >= ? AND ht.transaction_date <= ?
                            ORDER BY ht.amount DESC
                            """;

                        System.out.println("\nHistorical Transactions in System:");
                        System.out.printf("  %-15s %-40s %-12s%n", "DATE", "PROPERTY", "AMOUNT");
                        System.out.println("  " + "-".repeat(75));

                        try (PreparedStatement pstmt = conn.prepareStatement(detailQuery)) {
                            pstmt.setDate(1, java.sql.Date.valueOf(period.startDate));
                            pstmt.setDate(2, java.sql.Date.valueOf(period.endDate));
                            ResultSet rs = pstmt.executeQuery();

                            while (rs.next()) {
                                System.out.printf("  %-15s %-40s £%-10.2f%n",
                                    rs.getDate("transaction_date"),
                                    rs.getString("property_name"),
                                    rs.getBigDecimal("amount"));
                            }
                        }
                    }

                    if (!systemPayProp.equals(period.yourPayProp) && systemPayProp.compareTo(BigDecimal.ZERO) > 0) {
                        System.out.println("\nPAYPROP ACCOUNT VARIANCE: £" + systemPayProp.subtract(period.yourPayProp));

                        String detailQuery = """
                            SELECT
                                ft.transaction_date,
                                ft.property_name,
                                ft.amount
                            FROM financial_transactions ft
                            WHERE ft.data_source = 'INCOMING_PAYMENT'
                              AND ft.transaction_date >= ? AND ft.transaction_date <= ?
                            ORDER BY ft.amount DESC
                            """;

                        System.out.println("\nPayProp Transactions in System:");
                        System.out.printf("  %-15s %-40s %-12s%n", "DATE", "PROPERTY", "AMOUNT");
                        System.out.println("  " + "-".repeat(75));

                        try (PreparedStatement pstmt = conn.prepareStatement(detailQuery)) {
                            pstmt.setDate(1, java.sql.Date.valueOf(period.startDate));
                            pstmt.setDate(2, java.sql.Date.valueOf(period.endDate));
                            ResultSet rs = pstmt.executeQuery();

                            while (rs.next()) {
                                System.out.printf("  %-15s %-40s £%-10.2f%n",
                                    rs.getDate("transaction_date"),
                                    rs.getString("property_name"),
                                    rs.getBigDecimal("amount"));
                            }
                        }
                    }

                    System.out.println();
                }
            }

            // Check for specific anomalies
            System.out.println("\n=== CHECKING FOR ANOMALIES ===\n");

            // 1. Check for unusually large payments
            System.out.println("1. UNUSUALLY LARGE PAYMENTS (>£2000):");
            String largePaymentsQuery = """
                SELECT
                    'Historical' as source,
                    ht.transaction_date,
                    p.property_name,
                    ht.amount
                FROM historical_transactions ht
                LEFT JOIN properties p ON ht.property_id = p.id
                WHERE ht.category = 'rent'
                  AND ht.amount > 2000
                  AND ht.transaction_date >= '2024-12-22'
                  AND ht.transaction_date <= '2025-10-21'
                UNION ALL
                SELECT
                    'PayProp' as source,
                    ft.transaction_date,
                    ft.property_name,
                    ft.amount
                FROM financial_transactions ft
                WHERE ft.data_source = 'INCOMING_PAYMENT'
                  AND ft.amount > 2000
                  AND ft.transaction_date >= '2024-12-22'
                  AND ft.transaction_date <= '2025-10-21'
                ORDER BY amount DESC
                """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(largePaymentsQuery)) {
                System.out.printf("  %-15s %-15s %-40s %-12s%n", "SOURCE", "DATE", "PROPERTY", "AMOUNT");
                System.out.println("  " + "-".repeat(85));

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("  %-15s %-15s %-40s £%-10.2f%n",
                        rs.getString("source"),
                        rs.getDate("transaction_date"),
                        rs.getString("property_name"),
                        rs.getBigDecimal("amount"));
                }
                if (count == 0) {
                    System.out.println("  [No unusually large payments found]");
                }
            }

            // 2. Check for duplicate payments
            System.out.println("\n2. POTENTIAL DUPLICATE PAYMENTS (same property, same amount, close dates):");
            String duplicatesQuery = """
                SELECT
                    property_name,
                    amount,
                    COUNT(*) as occurrences,
                    GROUP_CONCAT(transaction_date ORDER BY transaction_date) as dates
                FROM (
                    SELECT
                        p.property_name,
                        ht.amount,
                        ht.transaction_date
                    FROM historical_transactions ht
                    LEFT JOIN properties p ON ht.property_id = p.id
                    WHERE ht.category = 'rent'
                      AND ht.transaction_date >= '2024-12-22'
                      AND ht.transaction_date <= '2025-10-21'
                    UNION ALL
                    SELECT
                        ft.property_name,
                        ft.amount,
                        ft.transaction_date
                    FROM financial_transactions ft
                    WHERE ft.data_source = 'INCOMING_PAYMENT'
                      AND ft.transaction_date >= '2024-12-22'
                      AND ft.transaction_date <= '2025-10-21'
                ) combined
                GROUP BY property_name, amount
                HAVING COUNT(*) > 10
                ORDER BY occurrences DESC
                LIMIT 10
                """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(duplicatesQuery)) {
                System.out.printf("  %-40s %-12s %-10s%n", "PROPERTY", "AMOUNT", "COUNT");
                System.out.println("  " + "-".repeat(70));

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("  %-40s £%-10.2f %-10d%n",
                        rs.getString("property_name"),
                        rs.getBigDecimal("amount"),
                        rs.getInt("occurrences"));
                }
                if (count == 0) {
                    System.out.println("  [No suspicious duplicates found]");
                }
            }

            System.out.println("\n=== INVESTIGATION COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
