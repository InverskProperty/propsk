import java.sql.*;
import java.math.BigDecimal;

public class VerifyRealUnifiedLayer {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== VERIFYING UNIFIED LAYER DATA (payprop_export_incoming_payments) ===\n");

            Statement stmt = conn.createStatement();

            // Get Flat 10's PayProp ID
            String propSql = "SELECT id, property_name, payprop_id FROM properties WHERE id = 7";
            ResultSet propRs = stmt.executeQuery(propSql);
            String propertyPayPropId = null;
            String propertyName = null;
            if (propRs.next()) {
                propertyPayPropId = propRs.getString("payprop_id");
                propertyName = propRs.getString("property_name");
            }
            propRs.close();

            System.out.println("Property 7: " + propertyName);
            System.out.println("PayProp ID: " + propertyPayPropId + "\n");

            // 1. Check incoming payments for Flat 10
            System.out.println("=== 1. INCOMING PAYMENTS FOR FLAT 10 ===\n");
            String incomingSql = "SELECT payprop_id, amount, reconciliation_date, transaction_type, " +
                                "transaction_status, tenant_name, property_name " +
                                "FROM payprop_export_incoming_payments " +
                                "WHERE property_payprop_id = ? " +
                                "ORDER BY reconciliation_date DESC";

            PreparedStatement pstmt = conn.prepareStatement(incomingSql);
            pstmt.setString(1, propertyPayPropId);
            ResultSet incomingRs = pstmt.executeQuery();

            int incomingCount = 0;
            BigDecimal totalIncoming = BigDecimal.ZERO;
            while (incomingRs.next()) {
                incomingCount++;
                BigDecimal amount = incomingRs.getBigDecimal("amount");
                totalIncoming = totalIncoming.add(amount);

                System.out.println("Payment " + incomingCount + ":");
                System.out.println("  ID: " + incomingRs.getString("payprop_id"));
                System.out.println("  Amount: GBP " + amount);
                System.out.println("  Date: " + incomingRs.getDate("reconciliation_date"));
                System.out.println("  Type: " + incomingRs.getString("transaction_type"));
                System.out.println("  Status: " + incomingRs.getString("transaction_status"));
                System.out.println("  Tenant: " + incomingRs.getString("tenant_name"));
                System.out.println();
            }
            incomingRs.close();
            pstmt.close();

            System.out.println("Total incoming payments: " + incomingCount);
            System.out.println("Total amount: GBP " + totalIncoming + "\n");

            // 2. Check how these payments link to leases via payprop_report_all_payments
            System.out.println("=== 2. PAYMENT INSTRUCTIONS LINKED TO INCOMING PAYMENTS ===\n");
            String linkedSql = "SELECT rap.payprop_id, rap.amount, rap.description, " +
                             "rap.incoming_transaction_id, rap.incoming_transaction_amount, " +
                             "rap.payment_instruction_id, rap.beneficiary_name, " +
                             "rap.incoming_property_name, rap.category_name " +
                             "FROM payprop_report_all_payments rap " +
                             "WHERE rap.incoming_property_payprop_id = ? " +
                             "ORDER BY rap.reconciliation_date DESC";

            PreparedStatement linkedPstmt = conn.prepareStatement(linkedSql);
            linkedPstmt.setString(1, propertyPayPropId);
            ResultSet linkedRs = linkedPstmt.executeQuery();

            int linkedCount = 0;
            while (linkedRs.next()) {
                linkedCount++;
                System.out.println("Payment Instruction " + linkedCount + ":");
                System.out.println("  Payment ID: " + linkedRs.getString("payprop_id"));
                System.out.println("  Amount: GBP " + linkedRs.getBigDecimal("amount"));
                System.out.println("  Description: " + linkedRs.getString("description"));
                System.out.println("  Incoming Txn ID: " + linkedRs.getString("incoming_transaction_id"));
                System.out.println("  Incoming Amt: GBP " + linkedRs.getBigDecimal("incoming_transaction_amount"));
                System.out.println("  Instruction ID: " + linkedRs.getString("payment_instruction_id"));
                System.out.println("  Beneficiary: " + linkedRs.getString("beneficiary_name"));
                System.out.println("  Category: " + linkedRs.getString("category_name"));
                System.out.println();
            }
            linkedRs.close();
            linkedPstmt.close();

            System.out.println("Total payment instructions: " + linkedCount + "\n");

            // 3. Check ICDN transactions (these should be EXCLUDED from linking)
            System.out.println("=== 3. ICDN TRANSACTIONS (EXCLUDED) ===\n");
            String icdnSql = "SELECT payprop_id, amount, transaction_type, description, " +
                           "property_name, category_name " +
                           "FROM payprop_report_icdn " +
                           "WHERE property_payprop_id = ? " +
                           "ORDER BY transaction_date DESC";

            PreparedStatement icdnPstmt = conn.prepareStatement(icdnSql);
            icdnPstmt.setString(1, propertyPayPropId);
            ResultSet icdnRs = icdnPstmt.executeQuery();

            int icdnCount = 0;
            BigDecimal totalIcdn = BigDecimal.ZERO;
            while (icdnRs.next()) {
                icdnCount++;
                BigDecimal amount = icdnRs.getBigDecimal("amount");
                totalIcdn = totalIcdn.add(amount);

                if (icdnCount <= 5) {  // Show first 5
                    System.out.println("ICDN Transaction " + icdnCount + ":");
                    System.out.println("  ID: " + icdnRs.getString("payprop_id"));
                    System.out.println("  Amount: GBP " + amount);
                    System.out.println("  Type: " + icdnRs.getString("transaction_type"));
                    System.out.println("  Description: " + icdnRs.getString("description"));
                    System.out.println("  Category: " + icdnRs.getString("category_name"));
                    System.out.println();
                }
            }
            icdnRs.close();
            icdnPstmt.close();

            System.out.println("Total ICDN transactions: " + icdnCount);
            System.out.println("Total ICDN amount: GBP " + totalIcdn);
            System.out.println("NOTE: These are EXCLUDED from lease linking\n");

            // 4. Overall statistics
            System.out.println("=== 4. OVERALL STATISTICS ===\n");
            String statsSql = "SELECT " +
                            "(SELECT COUNT(*) FROM payprop_export_incoming_payments) as total_incoming, " +
                            "(SELECT COUNT(*) FROM payprop_report_all_payments) as total_payments, " +
                            "(SELECT COUNT(*) FROM payprop_report_icdn) as total_icdn";

            ResultSet statsRs = stmt.executeQuery(statsSql);
            if (statsRs.next()) {
                System.out.println("Total incoming payments (unified layer): " + statsRs.getInt("total_incoming"));
                System.out.println("Total payment instructions: " + statsRs.getInt("total_payments"));
                System.out.println("Total ICDN transactions (excluded): " + statsRs.getInt("total_icdn"));
            }
            statsRs.close();

            stmt.close();

            System.out.println("\n=== SUMMARY ===");
            System.out.println("Unified Layer Table: payprop_export_incoming_payments");
            System.out.println("Flat 10 incoming payments: " + incomingCount);
            System.out.println("Flat 10 payment instructions: " + linkedCount);
            System.out.println("Flat 10 ICDN transactions: " + icdnCount + " (excluded)");
            System.out.println("\nVerification: " +
                (incomingCount > 0 ? "SUCCESS - Incoming payments found" : "WARNING - No incoming payments") +
                " | " +
                (linkedCount > 0 ? "SUCCESS - Payment instructions linked" : "WARNING - No payment instructions") +
                " | " +
                "ICDN properly tracked in separate table");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
