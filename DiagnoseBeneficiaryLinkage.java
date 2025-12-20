import java.sql.*;
import java.util.*;

/**
 * Diagnostic script to analyze PayProp beneficiary linkage issues.
 *
 * This checks:
 * 1. How many customers have payprop_entity_id populated
 * 2. How many beneficiaries in payprop_report_all_payments exist
 * 3. How many can be linked (matched)
 * 4. Which beneficiaries are missing customer records
 * 5. unified_allocations beneficiary_id null analysis
 *
 * Run with: java DiagnoseBeneficiaryLinkage.java
 */
public class DiagnoseBeneficiaryLinkage {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/crecrm?useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Redwan@123";

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("PAYPROP BENEFICIARY LINKAGE DIAGNOSTIC");
        System.out.println("=".repeat(80));
        System.out.println();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

            // 1. Customer payprop_entity_id analysis
            System.out.println("1. CUSTOMER PAYPROP_ENTITY_ID ANALYSIS");
            System.out.println("-".repeat(50));
            analyzeCustomerPayPropIds(conn);

            // 2. PayProp beneficiaries analysis
            System.out.println("\n2. PAYPROP_REPORT_ALL_PAYMENTS BENEFICIARIES");
            System.out.println("-".repeat(50));
            analyzePayPropBeneficiaries(conn);

            // 3. Linkage analysis
            System.out.println("\n3. BENEFICIARY LINKAGE ANALYSIS");
            System.out.println("-".repeat(50));
            analyzeLinkage(conn);

            // 4. Missing beneficiaries (in PayProp but not in customers)
            System.out.println("\n4. MISSING BENEFICIARIES (PayProp -> Customer)");
            System.out.println("-".repeat(50));
            findMissingBeneficiaries(conn);

            // 5. unified_allocations beneficiary_id analysis
            System.out.println("\n5. UNIFIED_ALLOCATIONS BENEFICIARY_ID ANALYSIS");
            System.out.println("-".repeat(50));
            analyzeUnifiedAllocations(conn);

            // 6. Payment batches beneficiary analysis
            System.out.println("\n6. PAYMENT_BATCHES BENEFICIARY ANALYSIS");
            System.out.println("-".repeat(50));
            analyzePaymentBatches(conn);

            // 7. Property owner linkage
            System.out.println("\n7. PROPERTY OWNER LINKAGE");
            System.out.println("-".repeat(50));
            analyzePropertyOwners(conn);

            // 8. Recommendations
            System.out.println("\n8. RECOMMENDATIONS");
            System.out.println("-".repeat(50));
            generateRecommendations(conn);

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void analyzeCustomerPayPropIds(Connection conn) throws SQLException {
        String sql = """
            SELECT
                COUNT(*) as total_customers,
                SUM(CASE WHEN payprop_entity_id IS NOT NULL AND payprop_entity_id != '' THEN 1 ELSE 0 END) as with_payprop_id,
                SUM(CASE WHEN payprop_entity_id IS NULL OR payprop_entity_id = '' THEN 1 ELSE 0 END) as without_payprop_id,
                SUM(CASE WHEN customer_type = 'PROPERTY_OWNER' THEN 1 ELSE 0 END) as property_owners,
                SUM(CASE WHEN customer_type = 'PROPERTY_OWNER' AND (payprop_entity_id IS NULL OR payprop_entity_id = '') THEN 1 ELSE 0 END) as owners_without_payprop_id
            FROM customers
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                System.out.printf("Total customers:                    %d%n", rs.getInt("total_customers"));
                System.out.printf("With payprop_entity_id:             %d%n", rs.getInt("with_payprop_id"));
                System.out.printf("Without payprop_entity_id:          %d%n", rs.getInt("without_payprop_id"));
                System.out.printf("Property owners:                    %d%n", rs.getInt("property_owners"));
                System.out.printf("Owners WITHOUT payprop_entity_id:   %d (PROBLEM!)%n", rs.getInt("owners_without_payprop_id"));
            }
        }

        // List property owners without payprop_entity_id
        String ownersSql = """
            SELECT customer_id, name, email, data_source
            FROM customers
            WHERE customer_type = 'PROPERTY_OWNER'
              AND (payprop_entity_id IS NULL OR payprop_entity_id = '')
            LIMIT 10
            """;

        try (PreparedStatement stmt = conn.prepareStatement(ownersSql);
             ResultSet rs = stmt.executeQuery()) {
            boolean hasResults = false;
            while (rs.next()) {
                if (!hasResults) {
                    System.out.println("\nProperty owners missing payprop_entity_id:");
                    hasResults = true;
                }
                System.out.printf("  - ID: %d, Name: %s, Email: %s, Source: %s%n",
                    rs.getInt("customer_id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("data_source"));
            }
        }
    }

    private static void analyzePayPropBeneficiaries(Connection conn) throws SQLException {
        String sql = """
            SELECT
                COUNT(DISTINCT beneficiary_payprop_id) as unique_beneficiaries,
                COUNT(DISTINCT CASE WHEN beneficiary_type = 'beneficiary' THEN beneficiary_payprop_id END) as owner_beneficiaries,
                COUNT(DISTINCT CASE WHEN beneficiary_type = 'agency' THEN beneficiary_payprop_id END) as agency_beneficiaries,
                COUNT(DISTINCT CASE WHEN beneficiary_type = 'contractor' THEN beneficiary_payprop_id END) as contractor_beneficiaries
            FROM payprop_report_all_payments
            WHERE beneficiary_payprop_id IS NOT NULL
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                System.out.printf("Total unique beneficiaries:         %d%n", rs.getInt("unique_beneficiaries"));
                System.out.printf("Owner beneficiaries (type=beneficiary): %d%n", rs.getInt("owner_beneficiaries"));
                System.out.printf("Agency beneficiaries:               %d%n", rs.getInt("agency_beneficiaries"));
                System.out.printf("Contractor beneficiaries:           %d%n", rs.getInt("contractor_beneficiaries"));
            }
        }

        // Sample beneficiaries
        String sampleSql = """
            SELECT DISTINCT beneficiary_payprop_id, beneficiary_name, beneficiary_type
            FROM payprop_report_all_payments
            WHERE beneficiary_type = 'beneficiary'
              AND beneficiary_payprop_id IS NOT NULL
            LIMIT 10
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sampleSql);
             ResultSet rs = stmt.executeQuery()) {
            System.out.println("\nSample owner beneficiaries from PayProp:");
            while (rs.next()) {
                System.out.printf("  - PayProp ID: %s, Name: %s%n",
                    rs.getString("beneficiary_payprop_id"),
                    rs.getString("beneficiary_name"));
            }
        }
    }

    private static void analyzeLinkage(Connection conn) throws SQLException {
        // How many PayProp beneficiaries can be linked to customers
        String sql = """
            SELECT
                COUNT(DISTINCT prap.beneficiary_payprop_id) as total_payprop_beneficiaries,
                COUNT(DISTINCT CASE WHEN c.customer_id IS NOT NULL THEN prap.beneficiary_payprop_id END) as linked_to_customer,
                COUNT(DISTINCT CASE WHEN c.customer_id IS NULL THEN prap.beneficiary_payprop_id END) as not_linked
            FROM (
                SELECT DISTINCT beneficiary_payprop_id
                FROM payprop_report_all_payments
                WHERE beneficiary_type = 'beneficiary'
                  AND beneficiary_payprop_id IS NOT NULL
            ) prap
            LEFT JOIN customers c ON prap.beneficiary_payprop_id COLLATE utf8mb4_unicode_ci = c.payprop_entity_id COLLATE utf8mb4_unicode_ci
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int total = rs.getInt("total_payprop_beneficiaries");
                int linked = rs.getInt("linked_to_customer");
                int notLinked = rs.getInt("not_linked");
                double linkRate = total > 0 ? (linked * 100.0 / total) : 0;

                System.out.printf("PayProp beneficiaries (owners):     %d%n", total);
                System.out.printf("Successfully linked to customers:   %d (%.1f%%)%n", linked, linkRate);
                System.out.printf("NOT linked (PROBLEM!):              %d (%.1f%%)%n", notLinked, 100 - linkRate);
            }
        }
    }

    private static void findMissingBeneficiaries(Connection conn) throws SQLException {
        String sql = """
            SELECT DISTINCT
                prap.beneficiary_payprop_id,
                prap.beneficiary_name,
                COUNT(*) as payment_count,
                SUM(ABS(prap.amount)) as total_amount
            FROM payprop_report_all_payments prap
            LEFT JOIN customers c ON prap.beneficiary_payprop_id COLLATE utf8mb4_unicode_ci = c.payprop_entity_id COLLATE utf8mb4_unicode_ci
            WHERE prap.beneficiary_type = 'beneficiary'
              AND prap.beneficiary_payprop_id IS NOT NULL
              AND c.customer_id IS NULL
            GROUP BY prap.beneficiary_payprop_id, prap.beneficiary_name
            ORDER BY total_amount DESC
            LIMIT 15
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            System.out.println("Beneficiaries in PayProp NOT linked to any customer:");
            System.out.println("(These need payprop_entity_id set in customers table)");
            System.out.println();
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.printf("  %d. PayProp ID: %s%n", count, rs.getString("beneficiary_payprop_id"));
                System.out.printf("     Name: %s%n", rs.getString("beneficiary_name"));
                System.out.printf("     Payments: %d, Total: £%.2f%n",
                    rs.getInt("payment_count"),
                    rs.getBigDecimal("total_amount"));
                System.out.println();
            }
            if (count == 0) {
                System.out.println("  None found - all beneficiaries are linked!");
            }
        }

        // Check if these beneficiaries match customer names
        System.out.println("Potential matches by name:");
        String matchSql = """
            SELECT DISTINCT
                prap.beneficiary_payprop_id as payprop_id,
                prap.beneficiary_name as payprop_name,
                c.customer_id,
                c.name as customer_name,
                c.payprop_entity_id as existing_payprop_id
            FROM payprop_report_all_payments prap
            LEFT JOIN customers c_linked ON prap.beneficiary_payprop_id COLLATE utf8mb4_unicode_ci = c_linked.payprop_entity_id COLLATE utf8mb4_unicode_ci
            JOIN customers c ON (
                LOWER(TRIM(prap.beneficiary_name)) = LOWER(TRIM(c.name))
                OR LOWER(TRIM(prap.beneficiary_name)) LIKE CONCAT('%', LOWER(TRIM(c.name)), '%')
            )
            WHERE prap.beneficiary_type = 'beneficiary'
              AND prap.beneficiary_payprop_id IS NOT NULL
              AND c_linked.customer_id IS NULL
            LIMIT 10
            """;

        try (PreparedStatement stmt = conn.prepareStatement(matchSql);
             ResultSet rs = stmt.executeQuery()) {
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.printf("  - PayProp '%s' (ID: %s) -> Customer '%s' (ID: %d, existing PayProp ID: %s)%n",
                    rs.getString("payprop_name"),
                    rs.getString("payprop_id"),
                    rs.getString("customer_name"),
                    rs.getInt("customer_id"),
                    rs.getString("existing_payprop_id"));
            }
            if (count == 0) {
                System.out.println("  No name-based matches found");
            }
        }
    }

    private static void analyzeUnifiedAllocations(Connection conn) throws SQLException {
        String sql = """
            SELECT
                COUNT(*) as total_allocations,
                SUM(CASE WHEN beneficiary_id IS NOT NULL THEN 1 ELSE 0 END) as with_beneficiary_id,
                SUM(CASE WHEN beneficiary_id IS NULL THEN 1 ELSE 0 END) as without_beneficiary_id,
                SUM(CASE WHEN source = 'PAYPROP' THEN 1 ELSE 0 END) as payprop_allocations,
                SUM(CASE WHEN source = 'PAYPROP' AND beneficiary_id IS NULL THEN 1 ELSE 0 END) as payprop_without_beneficiary,
                SUM(CASE WHEN source = 'MANUAL' THEN 1 ELSE 0 END) as manual_allocations,
                SUM(CASE WHEN source = 'MANUAL' AND beneficiary_id IS NULL THEN 1 ELSE 0 END) as manual_without_beneficiary
            FROM unified_allocations
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int total = rs.getInt("total_allocations");
                int withId = rs.getInt("with_beneficiary_id");
                int withoutId = rs.getInt("without_beneficiary_id");

                System.out.printf("Total allocations:                  %d%n", total);
                System.out.printf("With beneficiary_id:                %d (%.1f%%)%n", withId, total > 0 ? withId * 100.0 / total : 0);
                System.out.printf("WITHOUT beneficiary_id (PROBLEM!):  %d (%.1f%%)%n", withoutId, total > 0 ? withoutId * 100.0 / total : 0);
                System.out.println();
                System.out.printf("PayProp allocations:                %d%n", rs.getInt("payprop_allocations"));
                System.out.printf("PayProp WITHOUT beneficiary_id:     %d%n", rs.getInt("payprop_without_beneficiary"));
                System.out.printf("Manual allocations:                 %d%n", rs.getInt("manual_allocations"));
                System.out.printf("Manual WITHOUT beneficiary_id:      %d%n", rs.getInt("manual_without_beneficiary"));
            }
        }

        // Sample allocations without beneficiary_id
        String sampleSql = """
            SELECT id, property_name, beneficiary_name, amount, payment_batch_id, source
            FROM unified_allocations
            WHERE beneficiary_id IS NULL
            ORDER BY created_at DESC
            LIMIT 10
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sampleSql);
             ResultSet rs = stmt.executeQuery()) {
            System.out.println("\nSample allocations without beneficiary_id:");
            while (rs.next()) {
                System.out.printf("  - ID: %d, Property: %s, Name: %s, Amount: %.2f, Batch: %s, Source: %s%n",
                    rs.getLong("id"),
                    rs.getString("property_name"),
                    rs.getString("beneficiary_name"),
                    rs.getBigDecimal("amount"),
                    rs.getString("payment_batch_id"),
                    rs.getString("source"));
            }
        }
    }

    private static void analyzePaymentBatches(Connection conn) throws SQLException {
        String sql = """
            SELECT
                COUNT(*) as total_batches,
                SUM(CASE WHEN beneficiary_id IS NOT NULL THEN 1 ELSE 0 END) as with_beneficiary_id,
                SUM(CASE WHEN beneficiary_id IS NULL THEN 1 ELSE 0 END) as without_beneficiary_id,
                SUM(CASE WHEN source = 'PAYPROP' THEN 1 ELSE 0 END) as payprop_batches,
                SUM(CASE WHEN source = 'PAYPROP' AND beneficiary_id IS NULL THEN 1 ELSE 0 END) as payprop_without_beneficiary
            FROM payment_batches
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                System.out.printf("Total payment batches:              %d%n", rs.getInt("total_batches"));
                System.out.printf("With beneficiary_id:                %d%n", rs.getInt("with_beneficiary_id"));
                System.out.printf("WITHOUT beneficiary_id:             %d%n", rs.getInt("without_beneficiary_id"));
                System.out.printf("PayProp batches:                    %d%n", rs.getInt("payprop_batches"));
                System.out.printf("PayProp WITHOUT beneficiary_id:     %d%n", rs.getInt("payprop_without_beneficiary"));
            }
        }
    }

    private static void analyzePropertyOwners(Connection conn) throws SQLException {
        String sql = """
            SELECT
                COUNT(*) as total_properties,
                SUM(CASE WHEN property_owner_id IS NOT NULL THEN 1 ELSE 0 END) as with_owner,
                SUM(CASE WHEN property_owner_id IS NULL THEN 1 ELSE 0 END) as without_owner
            FROM properties
            WHERE is_block_property = 0 OR is_block_property IS NULL
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                System.out.printf("Total properties (non-block):       %d%n", rs.getInt("total_properties"));
                System.out.printf("With property_owner_id:             %d%n", rs.getInt("with_owner"));
                System.out.printf("WITHOUT property_owner_id:          %d%n", rs.getInt("without_owner"));
            }
        }

        // Check if property owners have payprop_entity_id
        String ownerLinkSql = """
            SELECT
                COUNT(DISTINCT p.id) as properties_with_owner,
                COUNT(DISTINCT CASE WHEN c.payprop_entity_id IS NOT NULL THEN p.id END) as owner_has_payprop_id,
                COUNT(DISTINCT CASE WHEN c.payprop_entity_id IS NULL THEN p.id END) as owner_missing_payprop_id
            FROM properties p
            JOIN customers c ON p.property_owner_id = c.customer_id
            """;

        try (PreparedStatement stmt = conn.prepareStatement(ownerLinkSql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                System.out.println("\nProperty owner payprop_entity_id status:");
                System.out.printf("Properties with owner:              %d%n", rs.getInt("properties_with_owner"));
                System.out.printf("Owner has payprop_entity_id:        %d%n", rs.getInt("owner_has_payprop_id"));
                System.out.printf("Owner MISSING payprop_entity_id:    %d%n", rs.getInt("owner_missing_payprop_id"));
            }
        }
    }

    private static void generateRecommendations(Connection conn) throws SQLException {
        System.out.println("Based on the analysis above, here are the recommended fixes:");
        System.out.println();

        // Check if we need to link beneficiaries
        String checkSql = """
            SELECT COUNT(DISTINCT prap.beneficiary_payprop_id) as unlinked_count
            FROM payprop_report_all_payments prap
            LEFT JOIN customers c ON prap.beneficiary_payprop_id COLLATE utf8mb4_unicode_ci = c.payprop_entity_id COLLATE utf8mb4_unicode_ci
            WHERE prap.beneficiary_type = 'beneficiary'
              AND prap.beneficiary_payprop_id IS NOT NULL
              AND c.customer_id IS NULL
            """;

        try (PreparedStatement stmt = conn.prepareStatement(checkSql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next() && rs.getInt("unlinked_count") > 0) {
                System.out.println("1. UPDATE customers to set payprop_entity_id from PayProp beneficiaries:");
                System.out.println("   Run: BeneficiaryLinkageFix.java");
                System.out.println();
            }
        }

        // Check unified_allocations
        String allocSql = """
            SELECT COUNT(*) as null_count
            FROM unified_allocations
            WHERE beneficiary_id IS NULL
            """;

        try (PreparedStatement stmt = conn.prepareStatement(allocSql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next() && rs.getInt("null_count") > 0) {
                System.out.println("2. After fixing customer links, run UnifiedTransactionRebuildService.rebuildAll()");
                System.out.println("   to regenerate unified_allocations with correct beneficiary_id values.");
                System.out.println();
            }
        }

        System.out.println("3. For future PayProp syncs, ensure beneficiary data is synced BEFORE payments.");
        System.out.println("   The sync order should be: Beneficiaries -> Properties -> Payments");
    }
}
