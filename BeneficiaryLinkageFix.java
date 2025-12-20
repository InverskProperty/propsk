import java.sql.*;
import java.util.*;

/**
 * Fix script to link PayProp beneficiaries to customer records.
 *
 * This script:
 * 1. Finds PayProp beneficiaries not linked to any customer
 * 2. Attempts to match them by name to existing customers
 * 3. Updates customer.payprop_entity_id with the PayProp beneficiary ID
 * 4. Updates unified_allocations.beneficiary_id for PayProp allocations
 * 5. Updates payment_batches.beneficiary_id for PayProp batches
 *
 * Run with: java BeneficiaryLinkageFix.java
 */
public class BeneficiaryLinkageFix {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/crecrm?useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Redwan@123";

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("PAYPROP BENEFICIARY LINKAGE FIX");
        System.out.println("=".repeat(80));
        System.out.println();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);

            try {
                // Step 1: Link customers by exact name match
                System.out.println("STEP 1: Link customers by exact name match");
                System.out.println("-".repeat(50));
                int exactMatches = linkByExactName(conn);
                System.out.printf("Linked %d customers by exact name match%n%n", exactMatches);

                // Step 2: Link customers by normalized name match
                System.out.println("STEP 2: Link customers by normalized name match");
                System.out.println("-".repeat(50));
                int normalizedMatches = linkByNormalizedName(conn);
                System.out.printf("Linked %d customers by normalized name match%n%n", normalizedMatches);

                // Step 3: Update unified_allocations.beneficiary_id
                System.out.println("STEP 3: Update unified_allocations.beneficiary_id");
                System.out.println("-".repeat(50));
                int allocationsUpdated = updateUnifiedAllocations(conn);
                System.out.printf("Updated %d allocations with beneficiary_id%n%n", allocationsUpdated);

                // Step 4: Update payment_batches.beneficiary_id
                System.out.println("STEP 4: Update payment_batches.beneficiary_id");
                System.out.println("-".repeat(50));
                int batchesUpdated = updatePaymentBatches(conn);
                System.out.printf("Updated %d payment batches with beneficiary_id%n%n", batchesUpdated);

                // Step 5: Show remaining unlinked beneficiaries
                System.out.println("STEP 5: Remaining unlinked beneficiaries");
                System.out.println("-".repeat(50));
                showRemainingUnlinked(conn);

                // Commit changes
                System.out.println("\nCommitting changes...");
                conn.commit();
                System.out.println("SUCCESS! All changes committed.");

                // Summary
                System.out.println("\n" + "=".repeat(50));
                System.out.println("SUMMARY");
                System.out.println("=".repeat(50));
                System.out.printf("Customers linked:           %d%n", exactMatches + normalizedMatches);
                System.out.printf("Allocations updated:        %d%n", allocationsUpdated);
                System.out.printf("Payment batches updated:    %d%n", batchesUpdated);

            } catch (Exception e) {
                System.err.println("Error occurred, rolling back...");
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Link customers to PayProp beneficiaries by exact name match
     */
    private static int linkByExactName(Connection conn) throws SQLException {
        // Find unlinked beneficiaries that have exact name match to a customer
        String updateSql = """
            UPDATE customers c
            JOIN (
                SELECT DISTINCT
                    prap.beneficiary_payprop_id,
                    prap.beneficiary_name,
                    (
                        SELECT customer_id
                        FROM customers
                        WHERE LOWER(TRIM(name)) = LOWER(TRIM(prap.beneficiary_name))
                          AND (payprop_entity_id IS NULL OR payprop_entity_id = '')
                        LIMIT 1
                    ) as matched_customer_id
                FROM payprop_report_all_payments prap
                LEFT JOIN customers c_existing ON prap.beneficiary_payprop_id COLLATE utf8mb4_unicode_ci = c_existing.payprop_entity_id COLLATE utf8mb4_unicode_ci
                WHERE prap.beneficiary_type = 'beneficiary'
                  AND prap.beneficiary_payprop_id IS NOT NULL
                  AND c_existing.customer_id IS NULL
            ) matched ON c.customer_id = matched.matched_customer_id
            SET c.payprop_entity_id = matched.beneficiary_payprop_id,
                c.payprop_synced = 1,
                c.payprop_last_sync = NOW()
            WHERE matched.matched_customer_id IS NOT NULL
            """;

        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            int updated = stmt.executeUpdate();

            // Log what was updated
            if (updated > 0) {
                String logSql = """
                    SELECT c.customer_id, c.name, c.payprop_entity_id
                    FROM customers c
                    WHERE c.payprop_synced = 1
                      AND c.payprop_last_sync >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)
                    LIMIT 10
                    """;
                try (PreparedStatement logStmt = conn.prepareStatement(logSql);
                     ResultSet rs = logStmt.executeQuery()) {
                    System.out.println("Sample linked customers:");
                    while (rs.next()) {
                        System.out.printf("  - Customer %d: %s -> PayProp ID: %s%n",
                            rs.getInt("customer_id"),
                            rs.getString("name"),
                            rs.getString("payprop_entity_id"));
                    }
                }
            }

            return updated;
        }
    }

    /**
     * Link customers by normalized name match (handles slight variations)
     */
    private static int linkByNormalizedName(Connection conn) throws SQLException {
        // Find unlinked beneficiaries and try to match by normalized name
        String findSql = """
            SELECT DISTINCT
                prap.beneficiary_payprop_id,
                prap.beneficiary_name
            FROM payprop_report_all_payments prap
            LEFT JOIN customers c ON prap.beneficiary_payprop_id COLLATE utf8mb4_unicode_ci = c.payprop_entity_id COLLATE utf8mb4_unicode_ci
            WHERE prap.beneficiary_type = 'beneficiary'
              AND prap.beneficiary_payprop_id IS NOT NULL
              AND c.customer_id IS NULL
            """;

        List<String[]> unlinkedBeneficiaries = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(findSql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                unlinkedBeneficiaries.add(new String[]{
                    rs.getString("beneficiary_payprop_id"),
                    rs.getString("beneficiary_name")
                });
            }
        }

        int linked = 0;
        String updateSql = "UPDATE customers SET payprop_entity_id = ?, payprop_synced = 1, payprop_last_sync = NOW() WHERE customer_id = ?";

        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            for (String[] beneficiary : unlinkedBeneficiaries) {
                String paypropId = beneficiary[0];
                String paypropName = beneficiary[1];

                if (paypropName == null) continue;

                // Normalize name for matching
                String normalizedName = normalizeName(paypropName);

                // Try to find a customer with similar name
                String matchSql = """
                    SELECT customer_id, name
                    FROM customers
                    WHERE (payprop_entity_id IS NULL OR payprop_entity_id = '')
                      AND customer_type = 'PROPERTY_OWNER'
                    """;

                try (PreparedStatement matchStmt = conn.prepareStatement(matchSql);
                     ResultSet rs = matchStmt.executeQuery()) {
                    while (rs.next()) {
                        String customerName = rs.getString("name");
                        if (customerName != null && namesMatch(normalizedName, normalizeName(customerName))) {
                            updateStmt.setString(1, paypropId);
                            updateStmt.setInt(2, rs.getInt("customer_id"));
                            updateStmt.executeUpdate();
                            linked++;
                            System.out.printf("  Linked: '%s' -> Customer '%s' (ID: %d)%n",
                                paypropName, customerName, rs.getInt("customer_id"));
                            break;
                        }
                    }
                }
            }
        }

        return linked;
    }

    /**
     * Normalize a name for fuzzy matching
     */
    private static String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase()
            .replaceAll("[^a-z0-9]", "")  // Remove non-alphanumeric
            .trim();
    }

    /**
     * Check if two normalized names match
     */
    private static boolean namesMatch(String name1, String name2) {
        if (name1.isEmpty() || name2.isEmpty()) return false;
        // Exact match or one contains the other (for partial matches)
        return name1.equals(name2) ||
               (name1.length() > 5 && name2.contains(name1)) ||
               (name2.length() > 5 && name1.contains(name2));
    }

    /**
     * Update unified_allocations.beneficiary_id based on linked customers
     */
    private static int updateUnifiedAllocations(Connection conn) throws SQLException {
        // Method 1: Update via payprop_report_all_payments beneficiary link
        String updateViaPrapSql = """
            UPDATE unified_allocations ua
            JOIN payprop_report_all_payments prap ON ua.payprop_payment_id COLLATE utf8mb4_unicode_ci = prap.payprop_id COLLATE utf8mb4_unicode_ci
            JOIN customers c ON prap.beneficiary_payprop_id COLLATE utf8mb4_unicode_ci = c.payprop_entity_id COLLATE utf8mb4_unicode_ci
            SET ua.beneficiary_id = c.customer_id,
                ua.beneficiary_name = c.name,
                ua.updated_at = NOW()
            WHERE ua.beneficiary_id IS NULL
              AND ua.source = 'PAYPROP'
            """;

        int updated1 = 0;
        try (PreparedStatement stmt = conn.prepareStatement(updateViaPrapSql)) {
            updated1 = stmt.executeUpdate();
            System.out.printf("  Updated %d allocations via payprop_report_all_payments link%n", updated1);
        }

        // Method 2: Update via property owner
        String updateViaPropertySql = """
            UPDATE unified_allocations ua
            JOIN properties p ON ua.property_id = p.id
            JOIN customers c ON p.property_owner_id = c.customer_id
            SET ua.beneficiary_id = c.customer_id,
                ua.beneficiary_name = c.name,
                ua.updated_at = NOW()
            WHERE ua.beneficiary_id IS NULL
              AND ua.allocation_type = 'OWNER'
            """;

        int updated2 = 0;
        try (PreparedStatement stmt = conn.prepareStatement(updateViaPropertySql)) {
            updated2 = stmt.executeUpdate();
            System.out.printf("  Updated %d allocations via property owner link%n", updated2);
        }

        // Method 3: Update via payment_batch beneficiary
        String updateViaBatchSql = """
            UPDATE unified_allocations ua
            JOIN payment_batches pb ON ua.payment_batch_id COLLATE utf8mb4_unicode_ci = pb.batch_id COLLATE utf8mb4_unicode_ci
            SET ua.beneficiary_id = pb.beneficiary_id,
                ua.updated_at = NOW()
            WHERE ua.beneficiary_id IS NULL
              AND pb.beneficiary_id IS NOT NULL
            """;

        int updated3 = 0;
        try (PreparedStatement stmt = conn.prepareStatement(updateViaBatchSql)) {
            updated3 = stmt.executeUpdate();
            System.out.printf("  Updated %d allocations via payment_batch link%n", updated3);
        }

        return updated1 + updated2 + updated3;
    }

    /**
     * Update payment_batches.beneficiary_id based on linked customers
     */
    private static int updatePaymentBatches(Connection conn) throws SQLException {
        // Update payment_batches where we can find the beneficiary via payprop_report_all_payments
        String updateSql = """
            UPDATE payment_batches pb
            JOIN (
                SELECT DISTINCT
                    prap.payment_batch_id,
                    c.customer_id
                FROM payprop_report_all_payments prap
                JOIN customers c ON prap.beneficiary_payprop_id COLLATE utf8mb4_unicode_ci = c.payprop_entity_id COLLATE utf8mb4_unicode_ci
                WHERE prap.beneficiary_type = 'beneficiary'
                  AND prap.payment_batch_id IS NOT NULL
            ) matched ON pb.batch_id COLLATE utf8mb4_unicode_ci = matched.payment_batch_id COLLATE utf8mb4_unicode_ci
            SET pb.beneficiary_id = matched.customer_id,
                pb.updated_at = NOW()
            WHERE pb.beneficiary_id IS NULL
            """;

        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            return stmt.executeUpdate();
        }
    }

    /**
     * Show remaining unlinked beneficiaries
     */
    private static void showRemainingUnlinked(Connection conn) throws SQLException {
        String sql = """
            SELECT DISTINCT
                prap.beneficiary_payprop_id,
                prap.beneficiary_name,
                COUNT(*) as payment_count
            FROM payprop_report_all_payments prap
            LEFT JOIN customers c ON prap.beneficiary_payprop_id COLLATE utf8mb4_unicode_ci = c.payprop_entity_id COLLATE utf8mb4_unicode_ci
            WHERE prap.beneficiary_type = 'beneficiary'
              AND prap.beneficiary_payprop_id IS NOT NULL
              AND c.customer_id IS NULL
            GROUP BY prap.beneficiary_payprop_id, prap.beneficiary_name
            ORDER BY payment_count DESC
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            int count = 0;
            while (rs.next()) {
                count++;
                if (count <= 10) {
                    System.out.printf("  - PayProp ID: %s, Name: %s, Payments: %d%n",
                        rs.getString("beneficiary_payprop_id"),
                        rs.getString("beneficiary_name"),
                        rs.getInt("payment_count"));
                }
            }
            if (count > 10) {
                System.out.printf("  ... and %d more%n", count - 10);
            }
            if (count == 0) {
                System.out.println("  All beneficiaries are now linked!");
            } else {
                System.out.printf("\nTotal unlinked beneficiaries: %d%n", count);
                System.out.println("These may need manual review or customer creation.");
            }
        }

        // Show allocations still without beneficiary_id
        String allocSql = """
            SELECT COUNT(*) as count
            FROM unified_allocations
            WHERE beneficiary_id IS NULL
            """;

        try (PreparedStatement stmt = conn.prepareStatement(allocSql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int remaining = rs.getInt("count");
                if (remaining > 0) {
                    System.out.printf("\nAllocations still without beneficiary_id: %d%n", remaining);
                    System.out.println("Run UnifiedTransactionRebuildService.rebuildAll() to regenerate.");
                }
            }
        }
    }
}
