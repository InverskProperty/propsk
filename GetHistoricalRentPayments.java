import java.sql.*;
import java.math.BigDecimal;

public class GetHistoricalRentPayments {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found.");
        }

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== HISTORICAL INCOMING RENT PAYMENTS ===\n");

            // Query: Historical rent payments (category = 'rent' with positive amounts)
            System.out.println("=== HISTORICAL: Rent Category (Incoming Payments) ===");
            String rentQuery = """
                SELECT
                    COUNT(*) as total_count,
                    SUM(amount) as total_amount,
                    MIN(transaction_date) as earliest_date,
                    MAX(transaction_date) as latest_date,
                    AVG(amount) as avg_amount,
                    MIN(amount) as min_amount,
                    MAX(amount) as max_amount
                FROM historical_transactions
                WHERE category = 'rent'
                  AND amount > 0
                """;

            BigDecimal historicalRentTotal = BigDecimal.ZERO;
            int historicalRentCount = 0;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(rentQuery)) {
                if (rs.next()) {
                    historicalRentCount = rs.getInt("total_count");
                    historicalRentTotal = rs.getBigDecimal("total_amount");

                    System.out.println("Category: 'rent' (positive amounts = incoming)");
                    System.out.println("Total records: " + historicalRentCount);
                    System.out.println("Total amount: £" + historicalRentTotal);
                    System.out.println("Date range: " + rs.getDate("earliest_date") + " to " + rs.getDate("latest_date"));
                    System.out.println("Average: £" + rs.getBigDecimal("avg_amount"));
                    System.out.println("Range: £" + rs.getBigDecimal("min_amount") + " to £" + rs.getBigDecimal("max_amount"));
                }
            }

            // Sample historical rent payments
            System.out.println("\nSample HISTORICAL rent payments:");
            String sampleQuery = """
                SELECT
                    ht.transaction_date,
                    p.property_name,
                    ht.amount,
                    ht.description,
                    ht.transaction_type
                FROM historical_transactions ht
                LEFT JOIN properties p ON ht.property_id = p.id
                WHERE ht.category = 'rent'
                  AND ht.amount > 0
                ORDER BY ht.transaction_date DESC
                LIMIT 15
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sampleQuery)) {
                System.out.printf("%-15s %-35s %-12s %-15s%n", "DATE", "PROPERTY", "AMOUNT", "TYPE");
                System.out.println("-".repeat(85));
                while (rs.next()) {
                    System.out.printf("%-15s %-35s GBP%-9.2f %-15s%n",
                        rs.getDate("transaction_date"),
                        rs.getString("property_name"),
                        rs.getBigDecimal("amount"),
                        rs.getString("transaction_type"));
                }
            }

            // Check if there are negative rent amounts (shouldn't be for incoming)
            System.out.println("\n=== Check for Negative Rent (Credit Notes?) ===");
            String negativeQuery = """
                SELECT
                    COUNT(*) as count,
                    SUM(amount) as total
                FROM historical_transactions
                WHERE category = 'rent'
                  AND amount < 0
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(negativeQuery)) {
                if (rs.next()) {
                    int negCount = rs.getInt("count");
                    if (negCount > 0) {
                        System.out.println("Negative rent records: " + negCount);
                        System.out.println("Total negative: £" + rs.getBigDecimal("total"));
                    } else {
                        System.out.println("No negative rent records (good - all incoming)");
                    }
                }
            }

            // GRAND TOTAL COMPARISON
            System.out.println("\n\n=== GRAND TOTAL: ALL INCOMING RENT PAYMENTS ===");
            System.out.println("=".repeat(80));

            // Get PayProp incoming
            String paypropQuery = """
                SELECT
                    COUNT(*) as count,
                    SUM(amount) as total
                FROM financial_transactions
                WHERE data_source = 'INCOMING_PAYMENT'
                """;

            BigDecimal paypropTotal = BigDecimal.ZERO;
            int paypropCount = 0;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(paypropQuery)) {
                if (rs.next()) {
                    paypropCount = rs.getInt("count");
                    paypropTotal = rs.getBigDecimal("total");
                }
            }

            System.out.printf("%-50s %-15s %-20s%n", "SOURCE", "COUNT", "TOTAL AMOUNT");
            System.out.println("-".repeat(90));
            System.out.printf("%-50s %-15d GBP%-17.2f%n",
                "PayProp (financial_transactions INCOMING_PAYMENT)",
                paypropCount,
                paypropTotal);
            System.out.printf("%-50s %-15d GBP%-17.2f%n",
                "Historical (historical_transactions category=rent)",
                historicalRentCount,
                historicalRentTotal);
            System.out.println("-".repeat(90));

            BigDecimal grandTotal = paypropTotal.add(historicalRentTotal);
            int grandCount = paypropCount + historicalRentCount;

            System.out.printf("%-50s %-15d GBP%-17.2f%n",
                "GRAND TOTAL (All Incoming Rent Payments)",
                grandCount,
                grandTotal);
            System.out.println("=".repeat(90));

            // Properties breakdown
            System.out.println("\n=== PROPERTIES WITH HISTORICAL RENT DATA ===");
            String propQuery = """
                SELECT
                    p.property_name,
                    COUNT(*) as payment_count,
                    SUM(ht.amount) as total_rent,
                    MIN(ht.transaction_date) as first_payment,
                    MAX(ht.transaction_date) as last_payment
                FROM historical_transactions ht
                LEFT JOIN properties p ON ht.property_id = p.id
                WHERE ht.category = 'rent'
                  AND ht.amount > 0
                GROUP BY p.property_name
                ORDER BY total_rent DESC
                LIMIT 20
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(propQuery)) {
                System.out.printf("%-40s %-10s %-15s %-15s%n",
                    "PROPERTY", "COUNT", "TOTAL", "LATEST");
                System.out.println("-".repeat(90));
                while (rs.next()) {
                    System.out.printf("%-40s %-10d GBP%-12.2f %-15s%n",
                        rs.getString("property_name"),
                        rs.getInt("payment_count"),
                        rs.getBigDecimal("total_rent"),
                        rs.getDate("last_payment"));
                }
            }

            // Check what's currently in unified
            System.out.println("\n=== WHAT'S IN unified_transactions FROM HISTORICAL ===");
            String unifiedHistQuery = """
                SELECT
                    COUNT(*) as count,
                    SUM(amount) as total,
                    MIN(transaction_date) as earliest,
                    MAX(transaction_date) as latest
                FROM unified_transactions
                WHERE source_system = 'HISTORICAL'
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(unifiedHistQuery)) {
                if (rs.next()) {
                    System.out.println("Current HISTORICAL records in unified_transactions:");
                    System.out.println("  Count: " + rs.getInt("count"));
                    System.out.println("  Total: £" + rs.getBigDecimal("total"));
                    System.out.println("  Date range: " + rs.getDate("earliest") + " to " + rs.getDate("latest"));
                }
            }

            System.out.println("\n=== ANALYSIS COMPLETE ===");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
