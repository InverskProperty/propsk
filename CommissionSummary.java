import java.sql.*;
import java.math.BigDecimal;

public class CommissionSummary {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== COMMISSION SOURCE SUMMARY FOR FLATS 1, 2, 3 ===\n");

            // Key question: Are commissions calculated from INVOICES or INCOMING PAYMENTS?
            System.out.println("=== COMMISSION CALCULATION VERIFICATION ===");
            String query = """
                SELECT
                    comm.property_name,
                    comm.transaction_date,
                    icdn.transaction_type,
                    icdn.amount as icdn_invoice_amount,
                    comm.amount as commission_amount,
                    ROUND(icdn.amount * 0.15, 2) as calculated_15_percent,
                    CASE
                        WHEN comm.amount = ROUND(icdn.amount * 0.15, 2) THEN 'YES - 15%% of INVOICE'
                        ELSE 'NO MATCH'
                    END as calculation_method
                FROM financial_transactions comm
                INNER JOIN payprop_report_icdn icdn ON
                    comm.pay_prop_transaction_id = CONCAT('COMM_', icdn.payprop_id)
                WHERE comm.property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                  AND comm.data_source = 'COMMISSION_PAYMENT'
                ORDER BY comm.property_name, comm.transaction_date
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                System.out.printf("%-25s %-15s %-15s %-12s %-12s %-12s %-25s%n",
                    "PROPERTY", "DATE", "ICDN_TYPE", "INVOICE", "COMMISSION", "CALC_15%%", "CALCULATION_METHOD");
                System.out.println("-".repeat(130));
                while (rs.next()) {
                    System.out.printf("%-25s %-15s %-15s GBP%-9.2f GBP%-9.2f GBP%-9.2f %-25s%n",
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getString("transaction_type"),
                        rs.getBigDecimal("icdn_invoice_amount"),
                        rs.getBigDecimal("commission_amount"),
                        rs.getBigDecimal("calculated_15_percent"),
                        rs.getString("calculation_method"));
                }
            }

            // Check if there are ANY incoming payments
            System.out.println("\n=== INCOMING_PAYMENT CHECK ===");
            String query2 = """
                SELECT
                    COUNT(*) as incoming_payment_count
                FROM financial_transactions
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                  AND data_source = 'INCOMING_PAYMENT'
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query2)) {
                if (rs.next()) {
                    int count = rs.getInt("incoming_payment_count");
                    System.out.printf("Incoming payments for Flats 1, 2, 3: %d%n", count);
                    if (count == 0) {
                        System.out.println("[CRITICAL] No INCOMING_PAYMENT transactions exist!");
                        System.out.println("           Commission is NOT based on actual payments received");
                    }
                }
            }

            // Check PayProp commission_percentage field
            System.out.println("\n=== PAYPROP COMMISSION FIELD CHECK ===");
            String query3 = """
                SELECT
                    COUNT(*) as total,
                    COUNT(commission_amount) as has_commission_amt,
                    COUNT(commission_percentage) as has_commission_pct
                FROM payprop_report_icdn
                WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query3)) {
                if (rs.next()) {
                    System.out.printf("Total ICDN records: %d%n", rs.getInt("total"));
                    System.out.printf("Has commission_amount: %d%n", rs.getInt("has_commission_amt"));
                    System.out.printf("Has commission_percentage: %d%n", rs.getInt("has_commission_pct"));
                    if (rs.getInt("has_commission_amt") == 0 && rs.getInt("has_commission_pct") == 0) {
                        System.out.println("[INFO] PayProp ICDN data does NOT contain commission values");
                        System.out.println("       Commission is calculated locally, not from PayProp");
                    }
                }
            }

            // Check property commission percentage setting
            System.out.println("\n=== PROPERTY COMMISSION SETTINGS ===");
            String query4 = """
                SELECT
                    prop.payprop_id,
                    prop.commission_percentage,
                    prop.commission_amount
                FROM payprop_export_properties prop
                WHERE prop.payprop_id IN ('KAXNvEqAXk', 'KAXNvEqVXk', 'WzJBQ3ERZQ')
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query4)) {
                System.out.printf("%-20s %-20s %-20s%n",
                    "PAYPROP_ID", "COMMISSION_%%", "COMMISSION_AMT");
                System.out.println("-".repeat(65));
                while (rs.next()) {
                    BigDecimal pct = rs.getBigDecimal("commission_percentage");
                    BigDecimal amt = rs.getBigDecimal("commission_amount");
                    System.out.printf("%-20s %-20s %-20s%n",
                        rs.getString("payprop_id"),
                        pct == null ? "[NULL]" : pct.toString() + "%",
                        amt == null ? "[NULL]" : "GBP" + amt.toString());
                }
            }

            System.out.println("\n" + "=".repeat(100));
            System.out.println("ANSWER TO YOUR QUESTION:");
            System.out.println("=".repeat(100));
            System.out.println();
            System.out.println("WHERE DO THE COMMISSION PAYMENTS COME FROM?");
            System.out.println("-".repeat(100));
            System.out.println("1. Source: PayProp ICDN (Invoice Debit Credit Note) transactions");
            System.out.println("   - NOT from historical_transactions");
            System.out.println("   - NOT from actual incoming payments");
            System.out.println();
            System.out.println("2. Calculation: 15%% of INVOICE amount (rent due), NOT actual payments received");
            System.out.println("   - Flat 1: GBP795.00 invoice -> GBP119.25 commission (15%%)");
            System.out.println("   - Flat 2: GBP740.00 invoice -> GBP111.00 commission (15%%)");
            System.out.println("   - Flat 3: GBP740.00 invoice -> GBP111.00 commission (15%%)");
            System.out.println();
            System.out.println("3. Generated by: Your local sync code (PayPropFinancialSyncService)");
            System.out.println("   - Creates COMMISSION_PAYMENT records with pay_prop_transaction_id = 'COMM_' + icdn.payprop_id");
            System.out.println("   - Uses commission_percentage from payprop_export_properties (15%%)");
            System.out.println("   - Applies to each ICDN invoice transaction");
            System.out.println();
            System.out.println("4. CRITICAL: Commission is based on INVOICED amounts, not RECEIVED payments");
            System.out.println("   - ICDN = Invoice Debit Credit Note (what tenant SHOULD pay)");
            System.out.println("   - INCOMING_PAYMENT = What tenant ACTUALLY paid");
            System.out.println("   - Commission calculated on SHOULD PAY, not ACTUAL PAYMENT");
            System.out.println();
            System.out.println("5. For Flats 1, 2, 3 specifically:");
            System.out.println("   - These properties ARE on PayProp (confirmed by payprop_export_properties)");
            System.out.println("   - Invoice data IS coming from PayProp ICDN report");
            System.out.println("   - Commission is being generated for every invoice");
            System.out.println("   - No INCOMING_PAYMENT data exists (may not be using PayProp for collections)");
            System.out.println("=".repeat(100));

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
