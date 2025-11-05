import java.sql.*;
import java.math.BigDecimal;

public class VerifyUnifiedLayer {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== VERIFYING UNIFIED LAYER DATA ===\n");
            
            // 1. Check payprop_incoming_payments table structure
            System.out.println("1. INCOMING PAYMENTS TABLE COLUMNS:");
            String colSql = "SHOW COLUMNS FROM payprop_incoming_payments";
            Statement stmt = conn.createStatement();
            ResultSet colRs = stmt.executeQuery(colSql);
            while (colRs.next()) {
                System.out.println("  " + colRs.getString("Field"));
            }
            colRs.close();
            System.out.println();

            // 2. Check incoming payments for Flat 10 - first query by property_payprop_id
            System.out.println("2. INCOMING PAYMENTS FOR FLAT 10:\n");

            // First get the property's PayProp ID
            String propSql = "SELECT payprop_id FROM properties WHERE id = 7";
            ResultSet propRs = stmt.executeQuery(propSql);
            String propertyPayPropId = null;
            if (propRs.next()) {
                propertyPayPropId = propRs.getString("payprop_id");
            }
            propRs.close();
            System.out.println("  Property 7 PayProp ID: " + propertyPayPropId + "\n");

            String incomingSql = "SELECT id, incoming_transaction_id, amount, reconciliation_date, " +
                                "transaction_type, transaction_status, tenant_name, property_name " +
                                "FROM payprop_incoming_payments " +
                                "WHERE property_payprop_id = ? " +
                                "ORDER BY reconciliation_date DESC LIMIT 10";

            PreparedStatement pstmt = conn.prepareStatement(incomingSql);
            pstmt.setString(1, propertyPayPropId);
            ResultSet incomingRs = pstmt.executeQuery();
            int incomingCount = 0;
            while (incomingRs.next()) {
                incomingCount++;
                System.out.println("Payment " + incomingCount + ":");
                System.out.println("  ID: " + incomingRs.getInt("id"));
                System.out.println("  Transaction ID: " + incomingRs.getString("incoming_transaction_id"));
                System.out.println("  Amount: GBP " + incomingRs.getBigDecimal("amount"));
                System.out.println("  Date: " + incomingRs.getDate("reconciliation_date"));
                System.out.println("  Type: " + incomingRs.getString("transaction_type"));
                System.out.println("  Status: " + incomingRs.getString("transaction_status"));
                System.out.println("  Tenant: " + incomingRs.getString("tenant_name"));
                System.out.println("  Property: " + incomingRs.getString("property_name"));
                System.out.println();
            }
            incomingRs.close();
            pstmt.close();

            if (incomingCount == 0) {
                System.out.println("  No incoming payments found for Flat 10.\n");
            } else {
                System.out.println("  Total: " + incomingCount + " incoming payments\n");
            }

            // 3. Check transaction types - likely ICDN is in transaction_type
            System.out.println("3. TRANSACTION TYPE BREAKDOWN:\n");
            String typeSql = "SELECT transaction_type, COUNT(*) as count, SUM(amount) as total " +
                           "FROM payprop_incoming_payments " +
                           "GROUP BY transaction_type";

            ResultSet typeRs = stmt.executeQuery(typeSql);
            System.out.println("  Transaction Types Found:");
            while (typeRs.next()) {
                String txType = typeRs.getString("transaction_type");
                int count = typeRs.getInt("count");
                BigDecimal total = typeRs.getBigDecimal("total");

                System.out.println("    " + (txType != null ? txType : "NULL") + ":");
                System.out.println("      Count: " + count);
                System.out.println("      Total Amount: GBP " + total);
            }
            typeRs.close();
            System.out.println();

            // 4. Check historical sync status
            System.out.println("4. HISTORICAL SYNC STATUS:\n");
            String historicalSql = "SELECT synced_to_historical, COUNT(*) as count " +
                                 "FROM payprop_incoming_payments " +
                                 "GROUP BY synced_to_historical";

            ResultSet historicalRs = stmt.executeQuery(historicalSql);
            while (historicalRs.next()) {
                boolean synced = historicalRs.getBoolean("synced_to_historical");
                int count = historicalRs.getInt("count");
                System.out.println("  " + (synced ? "Synced to historical" : "Not synced") + ": " + count);
            }
            historicalRs.close();
            System.out.println();

            // 5. Check for Flat 10 specifically with transaction details
            System.out.println("5. DETAILED FLAT 10 TRANSACTION STATUS:\n");
            String detailSql = "SELECT incoming_transaction_id, amount, transaction_type, " +
                             "transaction_status, synced_to_historical, historical_transaction_id " +
                             "FROM payprop_incoming_payments " +
                             "WHERE property_payprop_id = ? " +
                             "ORDER BY reconciliation_date DESC LIMIT 5";

            PreparedStatement detailPstmt = conn.prepareStatement(detailSql);
            detailPstmt.setString(1, propertyPayPropId);
            ResultSet detailRs = detailPstmt.executeQuery();
            int detailCount = 0;
            while (detailRs.next()) {
                detailCount++;
                System.out.println("  Transaction " + detailCount + ":");
                System.out.println("    ID: " + detailRs.getString("incoming_transaction_id"));
                System.out.println("    Amount: GBP " + detailRs.getBigDecimal("amount"));
                System.out.println("    Type: " + detailRs.getString("transaction_type"));
                System.out.println("    Status: " + detailRs.getString("transaction_status"));
                System.out.println("    Synced to Historical: " + detailRs.getBoolean("synced_to_historical"));
                System.out.println("    Historical ID: " + detailRs.getString("historical_transaction_id"));
                System.out.println();
            }
            detailRs.close();
            detailPstmt.close();

            stmt.close();
            
            System.out.println("\n=== VERIFICATION COMPLETE ===");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
