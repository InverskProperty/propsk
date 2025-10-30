import java.sql.*;
import java.math.BigDecimal;

public class TraceCommissionSource {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== TRACING COMMISSION PAYMENT SOURCE FOR FLATS 1, 2, 3 ===\n");

            // Query 1: Show ALL commission payments for Flats 1, 2, 3
            System.out.println("=== 1. ALL COMMISSION PAYMENTS FOR FLATS 1, 2, 3 ===");
            String query1 = """
                SELECT
                    id,
                    property_name,
                    transaction_date,
                    amount,
                    data_source,
                    pay_prop_transaction_id,
                    description,
                    created_at
                FROM financial_transactions
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                  AND data_source = 'COMMISSION_PAYMENT'
                ORDER BY property_name, transaction_date
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query1)) {
                System.out.printf("%-10s %-25s %-15s %-12s %-25s%n",
                    "ID", "PROPERTY", "DATE", "AMOUNT", "PAYPROP_TXN_ID");
                System.out.println("-".repeat(95));
                while (rs.next()) {
                    System.out.printf("%-10d %-25s %-15s GBP%-9.2f %-25s%n",
                        rs.getLong("id"),
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        rs.getString("pay_prop_transaction_id"));
                }
            }

            // Query 2: Check if these pay_prop_transaction_ids exist in payprop_report_icdn
            System.out.println("\n=== 2. LOOKUP COMMISSION IN PAYPROP_REPORT_ICDN ===");
            String query2 = """
                SELECT
                    ft.id as ft_id,
                    ft.property_name,
                    ft.amount as ft_amount,
                    ft.pay_prop_transaction_id,
                    icdn.payprop_id,
                    icdn.transaction_type,
                    icdn.amount as icdn_amount,
                    icdn.commission_amount,
                    icdn.commission_percentage,
                    icdn.description
                FROM financial_transactions ft
                LEFT JOIN payprop_report_icdn icdn
                  ON ft.pay_prop_transaction_id = CONCAT('COMM_', icdn.payprop_id)
                WHERE ft.property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                  AND ft.data_source = 'COMMISSION_PAYMENT'
                ORDER BY ft.property_name, ft.transaction_date
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                System.out.printf("%-25s %-12s %-20s %-20s %-12s %-8s%n",
                    "PROPERTY", "FT_AMOUNT", "PAYPROP_TXN_ID", "ICDN_PAYPROP_ID", "ICDN_AMT", "COMM_%%");
                System.out.println("-".repeat(110));
                while (rs.next()) {
                    String icdnId = rs.getString("payprop_id");
                    System.out.printf("%-25s GBP%-9.2f %-20s %-20s GBP%-9.2f %-8s%n",
                        rs.getString("property_name"),
                        rs.getBigDecimal("ft_amount"),
                        rs.getString("pay_prop_transaction_id"),
                        icdnId == null ? "[NOT FOUND]" : icdnId,
                        rs.getBigDecimal("icdn_amount"),
                        rs.getBigDecimal("commission_percentage"));
                }
            }

            // Query 3: Check the ICDN transaction details for these properties
            System.out.println("\n=== 3. ICDN TRANSACTIONS FOR FLATS 1, 2, 3 (WITH COMMISSION) ===");
            String query3 = """
                SELECT
                    payprop_id,
                    property_name,
                    transaction_date,
                    transaction_type,
                    amount,
                    commission_amount,
                    commission_percentage,
                    description
                FROM payprop_report_icdn
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                ORDER BY property_name, transaction_date
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                System.out.printf("%-20s %-25s %-15s %-20s %-12s %-12s %-8s%n",
                    "PAYPROP_ID", "PROPERTY", "DATE", "TYPE", "AMOUNT", "COMM_AMT", "COMM_%%");
                System.out.println("-".repeat(125));
                while (rs.next()) {
                    System.out.printf("%-20s %-25s %-15s %-20s GBP%-9.2f GBP%-9.2f %-8s%n",
                        rs.getString("payprop_id"),
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getString("transaction_type"),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("commission_amount"),
                        rs.getBigDecimal("commission_percentage"));
                }
            }

            // Query 4: Check historical transactions for incoming payments
            System.out.println("\n=== 4. HISTORICAL INCOMING PAYMENTS FOR FLATS 1, 2, 3 ===");
            String query4 = """
                SELECT
                    id,
                    property_name,
                    transaction_date,
                    amount,
                    description
                FROM historical_transactions
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                  AND description LIKE '%rent%'
                ORDER BY property_name, transaction_date
                LIMIT 20
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-10s %-25s %-15s %-12s %-40s%n",
                    "ID", "PROPERTY", "DATE", "AMOUNT", "DESCRIPTION");
                System.out.println("-".repeat(110));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    String desc = rs.getString("description");
                    if (desc != null && desc.length() > 40) {
                        desc = desc.substring(0, 37) + "...";
                    }
                    System.out.printf("%-10d %-25s %-15s GBP%-9.2f %-40s%n",
                        rs.getLong("id"),
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"),
                        desc);
                }
                if (!found) {
                    System.out.println("[NONE FOUND]");
                }
            }

            // Query 5: Cross-reference: For each commission payment, find related ICDN_ACTUAL
            System.out.println("\n=== 5. COMMISSION vs ICDN_ACTUAL PAIRING ===");
            String query5 = """
                SELECT
                    comm.id as comm_id,
                    comm.property_name,
                    comm.transaction_date as comm_date,
                    comm.amount as comm_amount,
                    comm.pay_prop_transaction_id as comm_payprop_id,
                    icdn_ft.id as icdn_ft_id,
                    icdn_ft.amount as icdn_ft_amount,
                    icdn_ft.pay_prop_transaction_id as icdn_payprop_id
                FROM financial_transactions comm
                LEFT JOIN financial_transactions icdn_ft ON
                    comm.property_name = icdn_ft.property_name
                    AND comm.transaction_date = icdn_ft.transaction_date
                    AND icdn_ft.data_source = 'ICDN_ACTUAL'
                    AND comm.pay_prop_transaction_id = CONCAT('COMM_', icdn_ft.pay_prop_transaction_id)
                WHERE comm.property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                  AND comm.data_source = 'COMMISSION_PAYMENT'
                ORDER BY comm.property_name, comm.transaction_date
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query5)) {
                System.out.printf("%-25s %-15s %-12s %-25s %-12s%n",
                    "PROPERTY", "DATE", "COMM_AMT", "COMM_PAYPROP_ID", "ICDN_AMT");
                System.out.println("-".repeat(100));
                while (rs.next()) {
                    BigDecimal icdnAmount = rs.getBigDecimal("icdn_ft_amount");
                    System.out.printf("%-25s %-15s GBP%-9.2f %-25s GBP%-9.2f%n",
                        rs.getString("property_name"),
                        rs.getDate("comm_date"),
                        rs.getBigDecimal("comm_amount"),
                        rs.getString("comm_payprop_id"),
                        icdnAmount);
                }
            }

            // Query 6: Calculate if commission = 15% of ICDN amount
            System.out.println("\n=== 6. COMMISSION CALCULATION VERIFICATION ===");
            String query6 = """
                SELECT
                    comm.property_name,
                    comm.transaction_date,
                    icdn_ft.amount as icdn_amount,
                    comm.amount as commission_amount,
                    ROUND(icdn_ft.amount * 0.15, 2) as calculated_15_percent,
                    CASE
                        WHEN comm.amount = ROUND(icdn_ft.amount * 0.15, 2) THEN '[MATCH - 15%%]'
                        ELSE '[NO MATCH]'
                    END as verification
                FROM financial_transactions comm
                INNER JOIN financial_transactions icdn_ft ON
                    comm.property_name = icdn_ft.property_name
                    AND comm.transaction_date = icdn_ft.transaction_date
                    AND icdn_ft.data_source = 'ICDN_ACTUAL'
                    AND comm.pay_prop_transaction_id = CONCAT('COMM_', icdn_ft.pay_prop_transaction_id)
                WHERE comm.property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                  AND comm.data_source = 'COMMISSION_PAYMENT'
                ORDER BY comm.property_name, comm.transaction_date
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query6)) {
                System.out.printf("%-25s %-15s %-12s %-12s %-15s %-15s%n",
                    "PROPERTY", "DATE", "ICDN_AMT", "COMM_AMT", "CALC_15%%", "VERIFICATION");
                System.out.println("-".repeat(110));
                while (rs.next()) {
                    System.out.printf("%-25s %-15s GBP%-9.2f GBP%-9.2f GBP%-12.2f %-15s%n",
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("icdn_amount"),
                        rs.getBigDecimal("commission_amount"),
                        rs.getBigDecimal("calculated_15_percent"),
                        rs.getString("verification"));
                }
            }

            // Query 7: Check if there are actual incoming payments in financial_transactions
            System.out.println("\n=== 7. INCOMING_PAYMENT IN FINANCIAL_TRANSACTIONS FOR FLATS 1, 2, 3 ===");
            String query7 = """
                SELECT
                    id,
                    property_name,
                    transaction_date,
                    amount,
                    data_source
                FROM financial_transactions
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                  AND data_source = 'INCOMING_PAYMENT'
                ORDER BY property_name, transaction_date
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query7)) {
                System.out.printf("%-10s %-25s %-15s %-12s%n",
                    "ID", "PROPERTY", "DATE", "AMOUNT");
                System.out.println("-".repeat(70));
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-10d %-25s %-15s GBP%-9.2f%n",
                        rs.getLong("id"),
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getBigDecimal("amount"));
                }
                if (!found) {
                    System.out.println("[NONE FOUND] - No INCOMING_PAYMENT for these properties");
                }
            }

            System.out.println("\n=== SUMMARY ===");
            System.out.println("Commission Source Analysis:");
            System.out.println("1. If pay_prop_transaction_id starts with 'COMM_': Generated from PayProp ICDN");
            System.out.println("2. If commission = 15%% of ICDN amount: Calculated from invoiced amount");
            System.out.println("3. If no INCOMING_PAYMENT exists: Commission NOT based on actual receipts");
            System.out.println("4. ICDN_ACTUAL = Invoice amounts, NOT actual payments received");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
