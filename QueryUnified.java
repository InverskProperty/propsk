import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class QueryUnified {
    public static void main(String[] args) {
        try {
            // Load driver
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("MySQL driver not found, trying without explicit load...");
        }
        
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "NXRedwMHejnfLPDdzZgnzOWpLreBpUsl";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            
            // QUERY 1: Last 12 months - Incoming payment types
            System.out.println("=".repeat(80));
            System.out.println("UNIFIED_TRANSACTIONS - INCOMING PAYMENTS (LAST 12 MONTHS)");
            System.out.println("=".repeat(80));
            
            String query1 = 
                "SELECT transaction_type, COUNT(*) as count, SUM(amount) as total " +
                "FROM unified_transactions " +
                "WHERE transaction_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH) " +
                "  AND (transaction_type IN ('incoming_payment', 'tenant_payment', 'payment', 'invoice') " +
                "       OR transaction_type LIKE '%payment%' OR transaction_type LIKE '%invoice%') " +
                "GROUP BY transaction_type " +
                "ORDER BY total DESC";
            
            BigDecimal grandTotal12 = BigDecimal.ZERO;
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query1)) {
                while (rs.next()) {
                    String type = rs.getString("transaction_type");
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");
                    
                    System.out.printf("%-30s | Count: %4d | Total: £%,12.2f%n", type, count, total);
                    if (total != null && total.compareTo(BigDecimal.ZERO) > 0) {
                        grandTotal12 = grandTotal12.add(total);
                    }
                }
            }
            System.out.println("-".repeat(80));
            System.out.printf("GRAND TOTAL (12 MONTHS): £%,12.2f%n", grandTotal12);
            System.out.println();
            
            // QUERY 2: Last 24 months - Incoming payment types
            System.out.println("=".repeat(80));
            System.out.println("UNIFIED_TRANSACTIONS - INCOMING PAYMENTS (LAST 24 MONTHS)");
            System.out.println("=".repeat(80));
            
            String query2 = 
                "SELECT transaction_type, COUNT(*) as count, SUM(amount) as total " +
                "FROM unified_transactions " +
                "WHERE transaction_date >= DATE_SUB(CURDATE(), INTERVAL 24 MONTH) " +
                "  AND (transaction_type IN ('incoming_payment', 'tenant_payment', 'payment', 'invoice') " +
                "       OR transaction_type LIKE '%payment%' OR transaction_type LIKE '%invoice%') " +
                "GROUP BY transaction_type " +
                "ORDER BY total DESC";
            
            BigDecimal grandTotal24 = BigDecimal.ZERO;
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query2)) {
                while (rs.next()) {
                    String type = rs.getString("transaction_type");
                    int count = rs.getInt("count");
                    BigDecimal total = rs.getBigDecimal("total");
                    
                    System.out.printf("%-30s | Count: %4d | Total: £%,12.2f%n", type, count, total);
                    if (total != null && total.compareTo(BigDecimal.ZERO) > 0) {
                        grandTotal24 = grandTotal24.add(total);
                    }
                }
            }
            System.out.println("-".repeat(80));
            System.out.printf("GRAND TOTAL (24 MONTHS): £%,12.2f%n", grandTotal24);
            System.out.println();
            
            // QUERY 3: All transaction types in the table
            System.out.println("=".repeat(80));
            System.out.println("ALL TRANSACTION TYPES IN unified_transactions");
            System.out.println("=".repeat(80));
            
            String query3 = 
                "SELECT transaction_type, COUNT(*) as count, " +
                "MIN(transaction_date) as earliest, MAX(transaction_date) as latest " +
                "FROM unified_transactions " +
                "GROUP BY transaction_type " +
                "ORDER BY count DESC";
            
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query3)) {
                while (rs.next()) {
                    String type = rs.getString("transaction_type");
                    int count = rs.getInt("count");
                    Date earliest = rs.getDate("earliest");
                    Date latest = rs.getDate("latest");
                    
                    System.out.printf("%-30s | Count: %4d | From: %s To: %s%n", 
                        type, count, earliest, latest);
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
