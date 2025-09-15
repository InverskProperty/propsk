package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * Simplified service to fix the broken PayProp relationship mappings
 * Uses direct SQL to correct the many-to-many assignment errors
 */
@Service
public class PayPropRelationshipFixServiceSimple {

    private static final Logger log = LoggerFactory.getLogger(PayPropRelationshipFixServiceSimple.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Fix the broken relationship mappings using direct SQL
     */
    @Transactional
    public RelationshipFixResult fixPayPropRelationships() {
        log.info("🔧 Starting PayProp relationship fix (simple version)...");

        RelationshipFixResult result = new RelationshipFixResult();

        try {
            // Step 1: Clear all incorrect assignments
            log.info("🗑️ Step 1: Clearing incorrect assignments...");
            int deletedAssignments = clearIncorrectAssignments();
            result.setDeletedAssignments(deletedAssignments);

            // Step 2: Create correct owner-property relationships from PayProp payments
            log.info("🔗 Step 2: Creating correct owner relationships...");
            int ownerAssignments = createCorrectOwnerRelationships();
            result.setOwnerAssignments(ownerAssignments);

            // Step 3: Create tenant-property relationships (if possible)
            log.info("🏠 Step 3: Creating tenant relationships...");
            int tenantAssignments = createTenantRelationships();
            result.setTenantAssignments(tenantAssignments);

            // Step 4: Validate results
            log.info("✅ Step 4: Validating relationships...");
            ValidationSummary validation = validateRelationships();
            result.setValidation(validation);

            log.info("🎯 Relationship fix completed: {} deleted, {} owners, {} tenants",
                deletedAssignments, ownerAssignments, tenantAssignments);

            return result;

        } catch (Exception e) {
            log.error("❌ Relationship fix failed: {}", e.getMessage(), e);
            result.setError(e.getMessage());
            return result;
        }
    }

    /**
     * Clear all existing assignments (they're all wrong anyway)
     */
    private int clearIncorrectAssignments() {
        String sql = "DELETE FROM customer_property_assignments";
        int deleted = jdbcTemplate.update(sql);
        log.info("🗑️ Deleted {} incorrect assignments", deleted);
        return deleted;
    }

    /**
     * Create correct owner relationships based on PayProp payment data
     */
    private int createCorrectOwnerRelationships() {
        log.info("🔍 Analyzing PayProp payment data for owner relationships...");

        String sql = """
            INSERT IGNORE INTO customer_property_assignments
            (customer_id, property_id, assignment_type, ownership_percentage, is_primary, created_at, updated_at)
            SELECT DISTINCT
                c.customer_id,
                p.id as property_id,
                'OWNER' as assignment_type,
                100.00 as ownership_percentage,
                1 as is_primary,
                NOW() as created_at,
                NOW() as updated_at
            FROM payprop_export_payments pay
            JOIN properties p ON p.payprop_id = pay.property_payprop_id
            JOIN customers c ON (
                c.name LIKE CONCAT('%', TRIM(REPLACE(pay.beneficiary, '[B]', '')), '%')
                OR TRIM(REPLACE(pay.beneficiary, '[B]', '')) LIKE CONCAT('%', c.name, '%')
            )
            WHERE pay.category = 'Owner'
            AND pay.beneficiary IS NOT NULL
            AND pay.beneficiary != ''
            AND c.is_property_owner = 1
            """;

        int created = jdbcTemplate.update(sql);
        log.info("✅ Created {} correct owner assignments via SQL", created);

        // Fallback: Manual mapping for unmatched owners
        if (created == 0) {
            log.info("🔄 Trying fallback owner mapping...");
            created = createOwnerAssignmentsFallback();
        }

        return created;
    }

    /**
     * Fallback method for owner assignments if automatic matching fails
     */
    private int createOwnerAssignmentsFallback() {
        // Get all property owners and properties
        String getOwnersSql = "SELECT customer_id, name FROM customers WHERE is_property_owner = 1";
        String getPropertiesSql = "SELECT id, property_name, payprop_id FROM properties WHERE payprop_id IS NOT NULL";

        List<Map<String, Object>> owners = jdbcTemplate.queryForList(getOwnersSql);
        List<Map<String, Object>> properties = jdbcTemplate.queryForList(getPropertiesSql);

        log.info("📊 Found {} owners and {} properties for fallback mapping", owners.size(), properties.size());

        int created = 0;

        // Simple strategy: Distribute properties among owners based on PayProp payment patterns
        String paymentCountSql = """
            SELECT
                pay.beneficiary,
                COUNT(DISTINCT pay.property_payprop_id) as property_count
            FROM payprop_export_payments pay
            WHERE pay.category = 'Owner'
            AND pay.beneficiary IS NOT NULL
            GROUP BY pay.beneficiary
            ORDER BY property_count DESC
            """;

        List<Map<String, Object>> paymentCounts = jdbcTemplate.queryForList(paymentCountSql);

        for (Map<String, Object> paymentCount : paymentCounts) {
            String beneficiary = (String) paymentCount.get("beneficiary");
            Long propertyCount = ((Number) paymentCount.get("property_count")).longValue();

            log.info("🔍 Processing beneficiary: {} ({} properties)", beneficiary, propertyCount);

            // Find matching customer
            Long customerId = findCustomerByBeneficiaryName(beneficiary);
            if (customerId == null) {
                log.warn("⚠️ No customer found for beneficiary: {}", beneficiary);
                continue;
            }

            // Get properties for this beneficiary
            String propertiesForBeneficiarySQL = """
                SELECT DISTINCT p.id
                FROM payprop_export_payments pay
                JOIN properties p ON p.payprop_id = pay.property_payprop_id
                WHERE pay.category = 'Owner'
                AND pay.beneficiary = ?
                """;

            List<Long> propertyIds = jdbcTemplate.queryForList(
                propertiesForBeneficiarySQL,
                new Object[]{beneficiary},
                Long.class
            );

            // Create assignments
            for (Long propertyId : propertyIds) {
                String insertSql = """
                    INSERT IGNORE INTO customer_property_assignments
                    (customer_id, property_id, assignment_type, ownership_percentage, is_primary, created_at, updated_at)
                    VALUES (?, ?, 'OWNER', 100.00, 1, NOW(), NOW())
                    """;

                int inserted = jdbcTemplate.update(insertSql, customerId, propertyId);
                if (inserted > 0) {
                    created++;
                    log.debug("✅ Assigned property {} to customer {}", propertyId, customerId);
                }
            }
        }

        return created;
    }

    /**
     * Find customer by beneficiary name
     */
    private Long findCustomerByBeneficiaryName(String beneficiaryName) {
        String cleanName = beneficiaryName.replaceAll("\\s*\\[B\\]\\s*$", "").trim();

        // Try exact match
        String exactSql = "SELECT customer_id FROM customers WHERE name = ? AND is_property_owner = 1 LIMIT 1";
        List<Long> exactMatches = jdbcTemplate.queryForList(exactSql, new Object[]{cleanName}, Long.class);
        if (!exactMatches.isEmpty()) {
            return exactMatches.get(0);
        }

        // Try partial match
        String partialSql = "SELECT customer_id FROM customers WHERE name LIKE ? AND is_property_owner = 1 LIMIT 1";
        List<Long> partialMatches = jdbcTemplate.queryForList(partialSql, new Object[]{"%" + cleanName + "%"}, Long.class);
        if (!partialMatches.isEmpty()) {
            log.info("📝 Matched beneficiary '{}' to customer ID {} via partial match", beneficiaryName, partialMatches.get(0));
            return partialMatches.get(0);
        }

        // Try reverse partial match
        String[] nameParts = cleanName.split("\\s+");
        for (String part : nameParts) {
            if (part.length() > 2) {
                String reversePartialSql = "SELECT customer_id FROM customers WHERE name LIKE ? AND is_property_owner = 1 LIMIT 1";
                List<Long> reverseMatches = jdbcTemplate.queryForList(reversePartialSql, new Object[]{"%" + part + "%"}, Long.class);
                if (!reverseMatches.isEmpty()) {
                    log.info("📝 Matched beneficiary '{}' to customer ID {} via name part '{}'", beneficiaryName, reverseMatches.get(0), part);
                    return reverseMatches.get(0);
                }
            }
        }

        return null;
    }

    /**
     * Create tenant relationships from PayProp data
     */
    private int createTenantRelationships() {
        log.info("🔍 Analyzing PayProp tenant data...");

        // Try to create tenant assignments from payment data with tenant_payprop_id
        String sql = """
            INSERT IGNORE INTO customer_property_assignments
            (customer_id, property_id, assignment_type, ownership_percentage, is_primary, created_at, updated_at)
            SELECT DISTINCT
                c.customer_id,
                p.id as property_id,
                'TENANT' as assignment_type,
                NULL as ownership_percentage,
                1 as is_primary,
                NOW() as created_at,
                NOW() as updated_at
            FROM payprop_export_payments pay
            JOIN properties p ON p.payprop_id = pay.property_payprop_id
            JOIN customers c ON c.payprop_entity_id = pay.tenant_payprop_id
            WHERE pay.tenant_payprop_id IS NOT NULL
            AND pay.tenant_payprop_id != ''
            AND c.is_tenant = 1
            """;

        int created = jdbcTemplate.update(sql);
        log.info("✅ Created {} tenant assignments from payment data", created);

        // If no tenant assignments from payments, try from tenant export table
        if (created == 0) {
            String tenantExportSql = """
                INSERT IGNORE INTO customer_property_assignments
                (customer_id, property_id, assignment_type, ownership_percentage, is_primary, created_at, updated_at)
                SELECT DISTINCT
                    c.customer_id,
                    p.id as property_id,
                    'TENANT' as assignment_type,
                    NULL as ownership_percentage,
                    1 as is_primary,
                    NOW() as created_at,
                    NOW() as updated_at
                FROM payprop_export_tenants t
                JOIN properties p ON p.payprop_id = t.property_payprop_id
                JOIN customers c ON c.payprop_entity_id = t.payprop_id
                WHERE t.payprop_id IS NOT NULL
                AND c.is_tenant = 1
                """;

            created = jdbcTemplate.update(tenantExportSql);
            log.info("✅ Created {} tenant assignments from tenant export table", created);
        }

        return created;
    }

    /**
     * Validate the fixed relationships
     */
    private ValidationSummary validateRelationships() {
        ValidationSummary summary = new ValidationSummary();

        String ownerCountSql = "SELECT COUNT(*) FROM customer_property_assignments WHERE assignment_type = 'OWNER'";
        String tenantCountSql = "SELECT COUNT(*) FROM customer_property_assignments WHERE assignment_type = 'TENANT'";
        String uniqueOwnersSql = "SELECT COUNT(DISTINCT customer_id) FROM customer_property_assignments WHERE assignment_type = 'OWNER'";
        String uniqueTenantsSql = "SELECT COUNT(DISTINCT customer_id) FROM customer_property_assignments WHERE assignment_type = 'TENANT'";
        String propertiesWithOwnersSql = "SELECT COUNT(DISTINCT property_id) FROM customer_property_assignments WHERE assignment_type = 'OWNER'";
        String propertiesWithTenantsSql = "SELECT COUNT(DISTINCT property_id) FROM customer_property_assignments WHERE assignment_type = 'TENANT'";

        summary.setOwnerAssignments(jdbcTemplate.queryForObject(ownerCountSql, Integer.class));
        summary.setTenantAssignments(jdbcTemplate.queryForObject(tenantCountSql, Integer.class));
        summary.setUniqueOwners(jdbcTemplate.queryForObject(uniqueOwnersSql, Integer.class));
        summary.setUniqueTenants(jdbcTemplate.queryForObject(uniqueTenantsSql, Integer.class));
        summary.setPropertiesWithOwners(jdbcTemplate.queryForObject(propertiesWithOwnersSql, Integer.class));
        summary.setPropertiesWithTenants(jdbcTemplate.queryForObject(propertiesWithTenantsSql, Integer.class));

        log.info("📊 Validation Summary:");
        log.info("   Owner assignments: {}", summary.getOwnerAssignments());
        log.info("   Tenant assignments: {}", summary.getTenantAssignments());
        log.info("   Unique owners: {}", summary.getUniqueOwners());
        log.info("   Unique tenants: {}", summary.getUniqueTenants());
        log.info("   Properties with owners: {}", summary.getPropertiesWithOwners());
        log.info("   Properties with tenants: {}", summary.getPropertiesWithTenants());

        return summary;
    }

    // Result classes (same as complex version)
    public static class RelationshipFixResult {
        private int deletedAssignments;
        private int ownerAssignments;
        private int tenantAssignments;
        private ValidationSummary validation;
        private String error;

        public int getDeletedAssignments() { return deletedAssignments; }
        public void setDeletedAssignments(int deletedAssignments) { this.deletedAssignments = deletedAssignments; }

        public int getOwnerAssignments() { return ownerAssignments; }
        public void setOwnerAssignments(int ownerAssignments) { this.ownerAssignments = ownerAssignments; }

        public int getTenantAssignments() { return tenantAssignments; }
        public void setTenantAssignments(int tenantAssignments) { this.tenantAssignments = tenantAssignments; }

        public ValidationSummary getValidation() { return validation; }
        public void setValidation(ValidationSummary validation) { this.validation = validation; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public boolean isSuccess() { return error == null; }
    }

    public static class ValidationSummary {
        private int ownerAssignments;
        private int tenantAssignments;
        private int uniqueOwners;
        private int uniqueTenants;
        private int propertiesWithOwners;
        private int propertiesWithTenants;

        public int getOwnerAssignments() { return ownerAssignments; }
        public void setOwnerAssignments(int ownerAssignments) { this.ownerAssignments = ownerAssignments; }

        public int getTenantAssignments() { return tenantAssignments; }
        public void setTenantAssignments(int tenantAssignments) { this.tenantAssignments = tenantAssignments; }

        public int getUniqueOwners() { return uniqueOwners; }
        public void setUniqueOwners(int uniqueOwners) { this.uniqueOwners = uniqueOwners; }

        public int getUniqueTenants() { return uniqueTenants; }
        public void setUniqueTenants(int uniqueTenants) { this.uniqueTenants = uniqueTenants; }

        public int getPropertiesWithOwners() { return propertiesWithOwners; }
        public void setPropertiesWithOwners(int propertiesWithOwners) { this.propertiesWithOwners = propertiesWithOwners; }

        public int getPropertiesWithTenants() { return propertiesWithTenants; }
        public void setPropertiesWithTenants(int propertiesWithTenants) { this.propertiesWithTenants = propertiesWithTenants; }
    }
}