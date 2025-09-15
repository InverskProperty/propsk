package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.assignment.CustomerPropertyAssignmentService;

import java.util.*;

/**
 * Service to fix the broken PayProp relationship mappings
 * Corrects the many-to-many assignment errors and establishes proper property-specific relationships
 */
@Service
public class PayPropRelationshipFixService {

    private static final Logger log = LoggerFactory.getLogger(PayPropRelationshipFixService.class);

    @Autowired
    private CustomerService customerService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private CustomerPropertyAssignmentService assignmentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Fix the broken relationship mappings
     */
    @Transactional
    public RelationshipFixResult fixPayPropRelationships() {
        log.info("🔧 Starting PayProp relationship fix...");

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

            // Step 3: Create tenant-property relationships from PayProp tenants
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
            SELECT DISTINCT
                p.beneficiary,
                p.property_name,
                p.property_payprop_id,
                props.id as property_db_id
            FROM payprop_export_payments p
            JOIN properties props ON props.payprop_id = p.property_payprop_id
            WHERE p.category = 'Owner'
            AND p.beneficiary IS NOT NULL
            ORDER BY p.beneficiary, p.property_name
            """;

        Map<String, List<Long>> ownerToProperties = new HashMap<>();

        jdbcTemplate.query(sql, rs -> {
            String beneficiary = rs.getString("beneficiary");
            Long propertyId = rs.getLong("property_db_id");

            ownerToProperties.computeIfAbsent(beneficiary, k -> new ArrayList<>()).add(propertyId);
        });

        log.info("📊 Found {} unique owners with property relationships", ownerToProperties.size());

        int assignmentsCreated = 0;

        for (Map.Entry<String, List<Long>> entry : ownerToProperties.entrySet()) {
            String beneficiaryName = entry.getKey();
            List<Long> propertyIds = entry.getValue();

            log.info("👤 Processing owner: {} ({} properties)", beneficiaryName, propertyIds.size());

            // Find customer by name (PayProp sync creates customers with beneficiary names)
            Customer owner = findCustomerByBeneficiaryName(beneficiaryName);

            if (owner == null) {
                log.warn("⚠️ Owner not found for beneficiary: {}", beneficiaryName);
                continue;
            }

            // Ensure customer is marked as property owner
            if (!owner.getIsPropertyOwner()) {
                owner.setIsPropertyOwner(true);
                owner.setCustomerType(CustomerType.PROPERTY_OWNER);
                customerService.save(owner);
                log.info("✅ Updated customer {} as property owner", owner.getName());
            }

            // Create assignments for each property
            for (Long propertyId : propertyIds) {
                try {
                    Property property = propertyService.findById(propertyId);
                    if (property != null) {
                        assignmentService.createAssignment(owner, property, AssignmentType.OWNER);
                        assignmentsCreated++;
                        log.debug("✅ Assigned {} to {}", owner.getName(), property.getPropertyName());
                    }
                } catch (IllegalStateException e) {
                    log.debug("ℹ️ Assignment already exists: {}", e.getMessage());
                } catch (Exception e) {
                    log.error("❌ Failed to create assignment: {}", e.getMessage());
                }
            }
        }

        log.info("✅ Created {} correct owner assignments", assignmentsCreated);
        return assignmentsCreated;
    }

    /**
     * Create tenant relationships from PayProp tenant data
     */
    private int createTenantRelationships() {
        log.info("🔍 Analyzing PayProp tenant data...");

        // Check if payprop_export_tenants has data
        String countSql = "SELECT COUNT(*) FROM payprop_export_tenants";
        Integer tenantCount = jdbcTemplate.queryForObject(countSql, Integer.class);

        if (tenantCount == null || tenantCount == 0) {
            log.warn("⚠️ No tenant data found in payprop_export_tenants - checking payments for tenant info");
            return createTenantRelationshipsFromPayments();
        }

        String sql = """
            SELECT DISTINCT
                t.payprop_id as tenant_payprop_id,
                t.name as tenant_name,
                p.id as property_db_id,
                p.property_name
            FROM payprop_export_tenants t
            JOIN properties p ON p.payprop_id = t.property_payprop_id
            WHERE t.payprop_id IS NOT NULL
            ORDER BY t.name, p.property_name
            """;

        Map<String, Long> tenantToProperty = new HashMap<>();

        jdbcTemplate.query(sql, rs -> {
            String tenantPayPropId = rs.getString("tenant_payprop_id");
            Long propertyId = rs.getLong("property_db_id");
            tenantToProperty.put(tenantPayPropId, propertyId);
        });

        log.info("📊 Found {} tenant-property relationships", tenantToProperty.size());

        int assignmentsCreated = 0;

        for (Map.Entry<String, Long> entry : tenantToProperty.entrySet()) {
            String tenantPayPropId = entry.getKey();
            Long propertyId = entry.getValue();

            // Find customer by PayProp entity ID
            Customer tenant = customerService.findByPayPropEntityId(tenantPayPropId);

            if (tenant == null) {
                log.warn("⚠️ Tenant not found for PayProp ID: {}", tenantPayPropId);
                continue;
            }

            // Ensure customer is marked as tenant
            if (!tenant.getIsTenant()) {
                tenant.setIsTenant(true);
                tenant.setCustomerType(CustomerType.TENANT);
                customerService.save(tenant);
                log.info("✅ Updated customer {} as tenant", tenant.getName());
            }

            try {
                Property property = propertyService.findById(propertyId);
                if (property != null) {
                    assignmentService.createAssignment(tenant, property, AssignmentType.TENANT);
                    assignmentsCreated++;
                    log.debug("✅ Assigned tenant {} to {}", tenant.getName(), property.getPropertyName());
                }
            } catch (IllegalStateException e) {
                log.debug("ℹ️ Tenant assignment already exists: {}", e.getMessage());
            } catch (Exception e) {
                log.error("❌ Failed to create tenant assignment: {}", e.getMessage());
            }
        }

        log.info("✅ Created {} tenant assignments", assignmentsCreated);
        return assignmentsCreated;
    }

    /**
     * Fallback: Create tenant relationships from payment data if tenant export is empty
     */
    private int createTenantRelationshipsFromPayments() {
        log.info("🔍 Creating tenant relationships from payment data...");

        String sql = """
            SELECT DISTINCT
                p.tenant_payprop_id,
                p.tenant_name,
                p.property_payprop_id,
                props.id as property_db_id
            FROM payprop_export_payments p
            JOIN properties props ON props.payprop_id = p.property_payprop_id
            WHERE p.tenant_payprop_id IS NOT NULL
            AND p.tenant_payprop_id != ''
            ORDER BY p.tenant_name, p.property_name
            """;

        Map<String, Long> tenantToProperty = new HashMap<>();

        jdbcTemplate.query(sql, rs -> {
            String tenantPayPropId = rs.getString("tenant_payprop_id");
            Long propertyId = rs.getLong("property_db_id");
            tenantToProperty.put(tenantPayPropId, propertyId);
        });

        log.info("📊 Found {} tenant-property relationships from payments", tenantToProperty.size());

        int assignmentsCreated = 0;

        for (Map.Entry<String, Long> entry : tenantToProperty.entrySet()) {
            String tenantPayPropId = entry.getKey();
            Long propertyId = entry.getValue();

            Customer tenant = customerService.findByPayPropEntityId(tenantPayPropId);

            if (tenant == null) {
                log.warn("⚠️ Tenant not found for PayProp ID: {}", tenantPayPropId);
                continue;
            }

            try {
                Property property = propertyService.findById(propertyId);
                if (property != null) {
                    assignmentService.createAssignment(tenant, property, AssignmentType.TENANT);
                    assignmentsCreated++;
                    log.debug("✅ Assigned tenant {} to {}", tenant.getName(), property.getPropertyName());
                }
            } catch (Exception e) {
                log.error("❌ Failed to create tenant assignment: {}", e.getMessage());
            }
        }

        return assignmentsCreated;
    }

    /**
     * Find customer by beneficiary name from PayProp data
     */
    private Customer findCustomerByBeneficiaryName(String beneficiaryName) {
        // PayProp beneficiary names often have [B] suffix
        String cleanName = beneficiaryName.replaceAll("\\s*\\[B\\]\\s*$", "").trim();

        // Try exact match first
        List<Customer> customers = customerService.findByNameContainingIgnoreCase(cleanName);

        if (!customers.isEmpty()) {
            return customers.get(0);
        }

        // Try partial match
        String[] nameParts = cleanName.split("\\s+");
        for (String part : nameParts) {
            if (part.length() > 2) {
                customers = customerService.findByNameContainingIgnoreCase(part);
                if (!customers.isEmpty()) {
                    log.info("📝 Matched beneficiary '{}' to customer '{}' via partial match",
                        beneficiaryName, customers.get(0).getName());
                    return customers.get(0);
                }
            }
        }

        return null;
    }

    /**
     * Validate the fixed relationships
     */
    private ValidationSummary validateRelationships() {
        String ownerCountSql = "SELECT COUNT(*) FROM customer_property_assignments WHERE assignment_type = 'OWNER'";
        String tenantCountSql = "SELECT COUNT(*) FROM customer_property_assignments WHERE assignment_type = 'TENANT'";
        String uniqueOwnersSql = "SELECT COUNT(DISTINCT customer_id) FROM customer_property_assignments WHERE assignment_type = 'OWNER'";
        String uniqueTenantsSql = "SELECT COUNT(DISTINCT customer_id) FROM customer_property_assignments WHERE assignment_type = 'TENANT'";
        String propertiesWithOwnersSql = "SELECT COUNT(DISTINCT property_id) FROM customer_property_assignments WHERE assignment_type = 'OWNER'";
        String propertiesWithTenantsSql = "SELECT COUNT(DISTINCT property_id) FROM customer_property_assignments WHERE assignment_type = 'TENANT'";

        ValidationSummary summary = new ValidationSummary();
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

    // Result classes
    public static class RelationshipFixResult {
        private int deletedAssignments;
        private int ownerAssignments;
        private int tenantAssignments;
        private ValidationSummary validation;
        private String error;

        // Getters and setters
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

        // Getters and setters
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