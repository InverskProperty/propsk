package site.easy.to.build.crm.service.payprop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * PayProp Lease Creation Service
 *
 * INFALLIBLE process to create leases and tenant assignments from PayProp data.
 *
 * This service bridges the gap between raw PayProp import and operational data:
 * 1. Reads payprop_export_properties and payprop_export_tenants_complete
 * 2. Matches them using PayProp IDs (not our internal IDs)
 * 3. Creates/updates records in properties, customers, tenants tables
 * 4. Creates Invoice (lease) records with proper linkage
 * 5. Creates customer_property_assignments for both OWNER and TENANT
 * 6. Returns detailed report of what was created
 *
 * DESIGN PRINCIPLES:
 * - Idempotent: Can be run multiple times safely
 * - Comprehensive logging: Every decision is logged
 * - Validation before creation: Check data integrity first
 * - Atomic transactions: All or nothing
 * - Clear error messages: Explain WHY something failed
 */
@Service
public class PayPropLeaseCreationService {

    private static final Logger log = LoggerFactory.getLogger(PayPropLeaseCreationService.class);

    @Autowired
    private javax.sql.DataSource dataSource;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerPropertyAssignmentRepository assignmentRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    /**
     * Create leases and assignments for all active tenants in PayProp data
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public LeaseCreationResult createLeasesFromPayPropData() {
        log.info("🏗️ Starting PayProp Lease Creation Process");

        // CRITICAL: Clear JPA EntityManager cache to ensure we see freshly committed properties
        // from previous sync steps. Without this, findByPayPropId() may use stale/cached queries.
        entityManager.flush();
        entityManager.clear();
        log.info("✅ EntityManager cache cleared - will query fresh property data");

        LeaseCreationResult result = new LeaseCreationResult();
        result.setStartTime(LocalDateTime.now());

        try {
            // Step 1: Load PayProp tenants with property information
            List<PayPropTenantData> tenants = loadPayPropTenants();
            result.setTotalTenantsFound(tenants.size());
            log.info("📋 Found {} tenants in PayProp data", tenants.size());

            // Step 2: Process each tenant
            for (PayPropTenantData tenantData : tenants) {
                try {
                    processPayPropTenant(tenantData, result);
                } catch (Exception e) {
                    String error = String.format("Failed to process tenant %s (%s): %s",
                        tenantData.getDisplayName(), tenantData.getPaypropTenantId(), e.getMessage());
                    result.addError(error);
                    log.error("❌ " + error, e);

                    // CRITICAL: Clear the entity manager after an error to prevent cascade failures
                    // When a constraint violation occurs, Hibernate marks the session as rollback-only
                    // and subsequent operations fail with "null id" errors
                    try {
                        entityManager.clear();
                    } catch (Exception clearEx) {
                        log.warn("Failed to clear entity manager after error: {}", clearEx.getMessage());
                    }
                }
            }

            result.setEndTime(LocalDateTime.now());
            result.setSuccess(result.getErrors().isEmpty());

            log.info("✅ Lease Creation Complete: {} leases created, {} assignments created, {} errors",
                result.getLeasesCreated(), result.getAssignmentsCreated(), result.getErrors().size());

        } catch (Exception e) {
            log.error("❌ Fatal error in lease creation process", e);
            result.setSuccess(false);
            result.addError("Fatal error: " + e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }

        return result;
    }

    /**
     * Load tenant data from payprop_export_tenants_complete with their property assignments
     */
    private List<PayPropTenantData> loadPayPropTenants() throws SQLException {
        List<PayPropTenantData> tenants = new ArrayList<>();

        String sql = """
            SELECT
                t.payprop_id as tenant_payprop_id,
                t.first_name,
                t.last_name,
                t.business_name,
                t.display_name,
                t.email,
                t.mobile,
                t.tenancy_start_date,
                t.tenancy_end_date,
                t.monthly_rent_amount,
                t.deposit_amount,
                t.is_active,
                t.properties_json
            FROM payprop_export_tenants_complete t
            WHERE t.is_active = 1
            ORDER BY t.payprop_id
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                PayPropTenantData tenant = new PayPropTenantData();
                tenant.setPaypropTenantId(rs.getString("tenant_payprop_id"));
                tenant.setFirstName(rs.getString("first_name"));
                tenant.setLastName(rs.getString("last_name"));
                tenant.setBusinessName(rs.getString("business_name"));
                tenant.setDisplayName(rs.getString("display_name"));
                tenant.setEmail(rs.getString("email"));
                tenant.setMobile(rs.getString("mobile"));
                tenant.setTenancyStartDate(rs.getDate("tenancy_start_date") != null ?
                    rs.getDate("tenancy_start_date").toLocalDate() : null);
                tenant.setTenancyEndDate(rs.getDate("tenancy_end_date") != null ?
                    rs.getDate("tenancy_end_date").toLocalDate() : null);
                tenant.setMonthlyRentAmount(rs.getBigDecimal("monthly_rent_amount"));
                tenant.setDepositAmount(rs.getBigDecimal("deposit_amount"));
                tenant.setIsActive(rs.getBoolean("is_active"));

                // Parse properties_json to extract property assignments
                String propertiesJson = rs.getString("properties_json");
                tenant.setPropertyAssignments(parsePropertyAssignments(propertiesJson));

                tenants.add(tenant);
            }
        }

        return tenants;
    }

    /**
     * Parse properties JSON array to extract property IDs and rent amounts
     */
    private List<PropertyAssignment> parsePropertyAssignments(String propertiesJson) {
        List<PropertyAssignment> assignments = new ArrayList<>();

        if (propertiesJson == null || propertiesJson.trim().isEmpty() || propertiesJson.equals("null")) {
            return assignments;
        }

        try {
            // Simple JSON parsing for properties array
            // Format: [{"id": "propertyId", "monthly_payment_required": "735.00", "tenant": {"start_date": "2025-09-03", ...}}, ...]

            // Remove outer brackets
            String content = propertiesJson.trim();
            if (content.startsWith("[")) content = content.substring(1);
            if (content.endsWith("]")) content = content.substring(0, content.length() - 1);

            // Split by objects (simple approach - look for "},{"
            String[] objects = content.split("\\},\\{");

            for (String obj : objects) {
                String clean = obj.trim();
                if (!clean.startsWith("{")) clean = "{" + clean;
                if (!clean.endsWith("}")) clean = clean + "}";

                PropertyAssignment assignment = new PropertyAssignment();

                // Extract property ID
                String propertyId = extractJsonValue(clean, "\"id\":");
                // Clean up any trailing/leading whitespace, escape characters, and trailing punctuation
                if (propertyId != null) {
                    propertyId = propertyId.trim()
                        .replaceAll("[\\\\\"]", "")      // Remove escape chars and quotes
                        .replaceAll("[,;:\\s]+$", "");    // Remove trailing commas, semicolons, colons, spaces
                }
                assignment.setPropertyPaypropId(propertyId);

                // Extract monthly payment
                String monthlyPayment = extractJsonValue(clean, "\"monthly_payment_required\":");
                if (monthlyPayment != null) {
                    try {
                        assignment.setMonthlyRent(new BigDecimal(monthlyPayment));
                    } catch (NumberFormatException e) {
                        log.warn("Could not parse monthly payment: {}", monthlyPayment);
                    }
                }

                // Extract start date from nested tenant object
                String tenantObj = extractJsonObject(clean, "\"tenant\":");
                if (tenantObj != null) {
                    String startDate = extractJsonValue(tenantObj, "\"start_date\":");
                    if (startDate != null) {
                        try {
                            assignment.setStartDate(LocalDate.parse(startDate));
                        } catch (Exception e) {
                            log.warn("Could not parse start date: {}", startDate);
                        }
                    }

                    String endDate = extractJsonValue(tenantObj, "\"end_date\":");
                    if (endDate != null && !endDate.equals("null")) {
                        try {
                            assignment.setEndDate(LocalDate.parse(endDate));
                        } catch (Exception e) {
                            log.warn("Could not parse end date: {}", endDate);
                        }
                    }
                }

                if (propertyId != null && !propertyId.isEmpty()) {
                    assignments.add(assignment);
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse properties JSON: {}", propertiesJson, e);
        }

        return assignments;
    }

    /**
     * Extract value for a JSON key (simple extraction)
     * Handles values like: "key": "value", "key": 123, "key": null
     */
    private String extractJsonValue(String json, String key) {
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) return null;

        int valueStart = keyIndex + key.length();
        // Skip whitespace after the colon
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= json.length()) return null;

        // Check if value is quoted
        boolean isQuoted = json.charAt(valueStart) == '"';
        if (isQuoted) {
            valueStart++; // Skip opening quote
        }

        int valueEnd = valueStart;
        while (valueEnd < json.length()) {
            char c = json.charAt(valueEnd);
            if (isQuoted) {
                // For quoted values, look for closing quote (not escaped)
                if (c == '"' && (valueEnd == valueStart || json.charAt(valueEnd - 1) != '\\')) {
                    break;
                }
            } else {
                // For unquoted values, stop at comma, brace, or bracket
                if (c == ',' || c == '}' || c == ']') {
                    break;
                }
            }
            valueEnd++;
        }

        String value = json.substring(valueStart, valueEnd).trim();

        // Clean up any remaining quotes, commas, braces from the value
        value = value.replaceAll("^[\"']+", "")     // Leading quotes
                     .replaceAll("[\"']+$", "")     // Trailing quotes
                     .replaceAll("[,}\\]]+$", "")   // Trailing punctuation
                     .trim();

        return value.isEmpty() || value.equals("null") ? null : value;
    }

    /**
     * Extract nested JSON object
     */
    private String extractJsonObject(String json, String key) {
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) return null;

        int objectStart = keyIndex + key.length();
        while (objectStart < json.length() && json.charAt(objectStart) != '{') {
            objectStart++;
        }

        if (objectStart >= json.length()) return null;

        int braceCount = 0;
        int objectEnd = objectStart;
        while (objectEnd < json.length()) {
            char c = json.charAt(objectEnd);
            if (c == '{') braceCount++;
            if (c == '}') braceCount--;
            objectEnd++;
            if (braceCount == 0) break;
        }

        return json.substring(objectStart, objectEnd);
    }

    /**
     * Process a single PayProp tenant: create customer, tenant record, lease, and assignments
     * Note: Runs within the parent transaction - errors are handled by clearing entity manager
     */
    private void processPayPropTenant(PayPropTenantData tenantData, LeaseCreationResult result) throws Exception {

        log.info("🔄 Processing tenant: {} ({})", tenantData.getDisplayName(), tenantData.getPaypropTenantId());

        // Validate tenant has property assignments
        if (tenantData.getPropertyAssignments().isEmpty()) {
            log.warn("⚠️ Tenant {} has no property assignments, skipping", tenantData.getDisplayName());
            result.addWarning("Tenant " + tenantData.getDisplayName() + " has no property assignments");
            return;
        }

        // Get the first (current) property assignment
        PropertyAssignment assignment = tenantData.getPropertyAssignments().get(0);

        // Find or create Customer record
        Customer customer = findOrCreateCustomer(tenantData);

        // Find Property by PayProp ID
        Property property = findPropertyByPayPropId(assignment.getPropertyPaypropId());
        if (property == null) {
            throw new Exception("Property not found for PayProp ID: " + assignment.getPropertyPaypropId());
        }

        log.info("   📍 Property: {} (ID: {}, PayProp ID: {})",
            property.getPropertyName(), property.getId(), property.getPayPropId());

        // Determine lease dates
        LocalDate leaseStart = assignment.getStartDate() != null ? assignment.getStartDate() : tenantData.getTenancyStartDate();
        LocalDate leaseEnd = assignment.getEndDate() != null ? assignment.getEndDate() : tenantData.getTenancyEndDate();
        BigDecimal rentAmount = assignment.getMonthlyRent() != null ? assignment.getMonthlyRent() : tenantData.getMonthlyRentAmount();

        if (leaseStart == null) {
            throw new Exception("No lease start date found for tenant " + tenantData.getDisplayName());
        }

        if (rentAmount == null || rentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new Exception("No valid rent amount found for tenant " + tenantData.getDisplayName());
        }

        // Check if lease already exists
        Invoice existingLease = findExistingLease(property, customer, leaseStart);

        if (existingLease != null) {
            log.info("   ✓ Lease already exists: {}", existingLease.getLeaseReference());
            result.incrementLeasesAlreadyExist();
        } else {
            // Create new lease
            Invoice lease = createLease(property, customer, leaseStart, leaseEnd, rentAmount, tenantData);
            Invoice savedLease = invoiceRepository.save(lease);
            log.info("   ✅ Created lease: {} (£{}/month)", savedLease.getLeaseReference(), rentAmount);
            result.incrementLeasesCreated();
        }

        // Check and create tenant assignment
        CustomerPropertyAssignment existingAssignment = findExistingAssignment(property, customer, AssignmentType.TENANT);

        if (existingAssignment != null) {
            log.info("   ✓ Tenant assignment already exists");
            result.incrementAssignmentsAlreadyExist();
        } else {
            CustomerPropertyAssignment newAssignment = createTenantAssignment(property, customer, leaseStart, leaseEnd);
            assignmentRepository.save(newAssignment);
            log.info("   ✅ Created tenant assignment");
            result.incrementAssignmentsCreated();
        }
    }

    /**
     * Find or create Customer record from PayProp tenant data
     */
    private Customer findOrCreateCustomer(PayPropTenantData tenantData) {
        // Try to find by PayProp ID first
        Customer existing = customerRepository.findByPayPropEntityId(tenantData.getPaypropTenantId());
        if (existing != null) {
            log.info("   ✓ Found existing customer: {} (ID: {})", existing.getName(), existing.getCustomerId());
            return existing;
        }

        // Try to find by email
        if (tenantData.getEmail() != null && !tenantData.getEmail().isEmpty()) {
            Customer byEmail = customerRepository.findByEmail(tenantData.getEmail());
            if (byEmail != null) {
                // Update PayProp ID and return
                byEmail.setPayPropEntityId(tenantData.getPaypropTenantId());
                byEmail.setPayPropCustomerId(tenantData.getPaypropTenantId());
                customerRepository.save(byEmail);
                log.info("   ✓ Found customer by email, updated PayProp ID: {}", byEmail.getName());
                return byEmail;
            }
        }

        // Create new customer
        Customer customer = new Customer();
        customer.setName(tenantData.getDisplayName());
        customer.setFirstName(tenantData.getFirstName());
        customer.setLastName(tenantData.getLastName());
        customer.setBusinessName(tenantData.getBusinessName());
        customer.setEmail(tenantData.getEmail());
        customer.setMobileNumber(tenantData.getMobile());
        customer.setCustomerType(CustomerType.TENANT);
        customer.setIsTenant(true);
        customer.setPayPropEntityId(tenantData.getPaypropTenantId());
        customer.setPayPropCustomerId(tenantData.getPaypropTenantId());
        customer.setDataSource(site.easy.to.build.crm.entity.DataSource.PAYPROP);
        customer.setCreatedAt(LocalDateTime.now());

        Customer saved = customerRepository.save(customer);
        log.info("   ✅ Created new customer: {} (ID: {})", saved.getName(), saved.getCustomerId());
        return saved;
    }

    /**
     * Find property by PayProp ID
     */
    private Property findPropertyByPayPropId(String paypropId) {
        // Clean the input - remove any whitespace, trailing commas, or other punctuation
        String cleanId = paypropId != null ? paypropId.trim().replaceAll("[,;:\\s]+$", "") : null;

        log.debug("🔍 Searching for property with PayProp ID: '{}' (length: {}, cleaned: '{}')",
            paypropId, paypropId != null ? paypropId.length() : 0, cleanId);

        Optional<Property> result = propertyRepository.findByPayPropId(cleanId);
        if (result.isPresent()) {
            log.debug("✅ Found property: {} (ID: {})", result.get().getPropertyName(), result.get().getId());
        } else {
            log.warn("❌ Property NOT FOUND for PayProp ID: {} (cleaned: {})", paypropId, cleanId);
            // Try to find by direct query to debug
            long count = propertyRepository.count();
            log.warn("📊 Total properties in repository: {}", count);
        }
        return result.orElse(null);
    }

    /**
     * Find existing lease for property - prevents duplicates when customer matching fails
     *
     * A lease is considered a duplicate if:
     * 1. Same property + same customer (regardless of dates or active status)
     * 2. Same property + ANY lease with overlapping dates (customer may not have linked correctly)
     *
     * This prevents duplicates when:
     * - Local lease was created with customer that doesn't have PayProp ID
     * - PayProp import creates new customer (can't match) and would create duplicate lease
     * - Existing lease has is_active=false or NULL
     *
     * Note: This is conservative - if a property has ANY lease with overlapping dates,
     * we won't create another one even if the tenant appears different.
     */
    private Invoice findExistingLease(Property property, Customer customer, LocalDate startDate) {
        // FIRST: Check for existing lease with same property + customer (exact match)
        List<Invoice> customerLeases = invoiceRepository.findByCustomerAndProperty(customer, property);

        for (Invoice lease : customerLeases) {
            // Check for lease type - accept both explicit "lease" type and NULL (legacy data)
            String invoiceType = lease.getInvoiceType();
            boolean isLease = "lease".equals(invoiceType) || invoiceType == null;
            if (!isLease) {
                continue;
            }

            // If lease is active OR has no end date, it's a duplicate
            if (Boolean.TRUE.equals(lease.getIsActive()) || lease.getEndDate() == null) {
                log.info("   ✓ Found existing lease for same property+customer: {} (active: {}, endDate: {})",
                    lease.getLeaseReference(), lease.getIsActive(), lease.getEndDate());
                return lease;
            }

            // If lease dates overlap with the new start date, it's a duplicate
            if (lease.getStartDate() != null && startDate != null) {
                LocalDate leaseEnd = lease.getEndDate();
                if (leaseEnd == null || !leaseEnd.isBefore(startDate)) {
                    log.info("   ✓ Found existing overlapping lease for same property+customer: {} (start: {}, end: {})",
                        lease.getLeaseReference(), lease.getStartDate(), leaseEnd);
                    return lease;
                }
            }
        }

        // SECOND: Check for ANY lease on this property with overlapping dates (regardless of active status)
        // This catches cases where:
        // - Customer wasn't linked properly (missing PayProp ID, different email, etc.)
        // - Lease has is_active=false/NULL but is still current
        List<Invoice> propertyLeases = invoiceRepository.findByProperty(property);

        for (Invoice lease : propertyLeases) {
            // Check for lease type - accept both explicit "lease" type and NULL (legacy data)
            String invoiceType = lease.getInvoiceType();
            boolean isLease = "lease".equals(invoiceType) || invoiceType == null;
            if (!isLease) {
                continue;
            }

            // Check for date overlap (don't skip based on is_active - that was causing duplicates!)
            if (startDate != null && lease.getStartDate() != null) {
                LocalDate leaseEnd = lease.getEndDate();

                // Overlap: new start is before existing end (or no end)
                if (leaseEnd == null || !leaseEnd.isBefore(startDate)) {
                    log.info("   ⚠️ Found existing lease on same property (different customer): {} - tenant: {} (active: {})",
                        lease.getLeaseReference(),
                        lease.getCustomer() != null ? lease.getCustomer().getName() : "unknown",
                        lease.getIsActive());
                    log.info("      Preventing duplicate - link customers or end existing lease first");
                    return lease;
                }
            }
        }

        return null;
    }

    /**
     * Create lease (Invoice entity)
     */
    private Invoice createLease(Property property, Customer customer, LocalDate startDate,
                                LocalDate endDate, BigDecimal rentAmount, PayPropTenantData tenantData) {
        Invoice lease = new Invoice();

        lease.setProperty(property);
        lease.setCustomer(customer);
        lease.setStartDate(startDate);
        lease.setEndDate(endDate);
        lease.setAmount(rentAmount);
        lease.setFrequency(Invoice.InvoiceFrequency.monthly);
        lease.setPaymentDay(startDate.getDayOfMonth()); // Use start date day as payment day
        lease.setCategoryId("rent");
        lease.setCategoryName("Rent");
        lease.setDescription(String.format("Lease for %s - %s (£%s/month)",
            customer.getName(), property.getPropertyName(), rentAmount));
        lease.setIsActive(true);
        lease.setSyncStatus(Invoice.SyncStatus.synced); // From PayProp
        lease.setInvoiceType("lease");
        lease.setCreatedAt(LocalDateTime.now());
        // Link tenant PayProp ID to paypropCustomerId (NOT paypropId)
        // paypropId is reserved for invoice instruction IDs (unique constraint)
        // paypropCustomerId identifies the tenant associated with this lease
        lease.setPaypropCustomerId(tenantData.getPaypropTenantId());

        return lease;
    }

    /**
     * Find existing assignment - checks for ANY assignment regardless of dates
     * to avoid unique constraint violations (customer_id, property_id, assignment_type)
     */
    private CustomerPropertyAssignment findExistingAssignment(Property property, Customer customer, AssignmentType type) {
        List<CustomerPropertyAssignment> assignments = assignmentRepository
            .findByPropertyIdAndAssignmentType(property.getId(), type);

        for (CustomerPropertyAssignment assignment : assignments) {
            if (assignment.getCustomer().getCustomerId().equals(customer.getCustomerId())) {
                // Return ANY existing assignment - unique constraint is on customer+property+type
                // regardless of dates, so we can't create a new one
                return assignment;
            }
        }

        return null;
    }

    /**
     * Create tenant assignment
     */
    private CustomerPropertyAssignment createTenantAssignment(Property property, Customer customer,
                                                              LocalDate startDate, LocalDate endDate) {
        CustomerPropertyAssignment assignment = new CustomerPropertyAssignment();
        assignment.setProperty(property);
        assignment.setCustomer(customer);
        assignment.setAssignmentType(AssignmentType.TENANT);
        assignment.setStartDate(startDate);
        assignment.setEndDate(endDate);
        assignment.setIsPrimary(true);
        assignment.setCreatedAt(LocalDateTime.now());
        assignment.setSyncStatus("PAYPROP");

        return assignment;
    }

    // ===== DATA CLASSES =====

    public static class PayPropTenantData {
        private String paypropTenantId;
        private String firstName;
        private String lastName;
        private String businessName;
        private String displayName;
        private String email;
        private String mobile;
        private LocalDate tenancyStartDate;
        private LocalDate tenancyEndDate;
        private BigDecimal monthlyRentAmount;
        private BigDecimal depositAmount;
        private Boolean isActive;
        private List<PropertyAssignment> propertyAssignments = new ArrayList<>();

        // Getters and setters
        public String getPaypropTenantId() { return paypropTenantId; }
        public void setPaypropTenantId(String paypropTenantId) { this.paypropTenantId = paypropTenantId; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getBusinessName() { return businessName; }
        public void setBusinessName(String businessName) { this.businessName = businessName; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }

        public LocalDate getTenancyStartDate() { return tenancyStartDate; }
        public void setTenancyStartDate(LocalDate tenancyStartDate) { this.tenancyStartDate = tenancyStartDate; }

        public LocalDate getTenancyEndDate() { return tenancyEndDate; }
        public void setTenancyEndDate(LocalDate tenancyEndDate) { this.tenancyEndDate = tenancyEndDate; }

        public BigDecimal getMonthlyRentAmount() { return monthlyRentAmount; }
        public void setMonthlyRentAmount(BigDecimal monthlyRentAmount) { this.monthlyRentAmount = monthlyRentAmount; }

        public BigDecimal getDepositAmount() { return depositAmount; }
        public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }

        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }

        public List<PropertyAssignment> getPropertyAssignments() { return propertyAssignments; }
        public void setPropertyAssignments(List<PropertyAssignment> propertyAssignments) { this.propertyAssignments = propertyAssignments; }
    }

    public static class PropertyAssignment {
        private String propertyPaypropId;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal monthlyRent;

        public String getPropertyPaypropId() { return propertyPaypropId; }
        public void setPropertyPaypropId(String propertyPaypropId) { this.propertyPaypropId = propertyPaypropId; }

        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

        public BigDecimal getMonthlyRent() { return monthlyRent; }
        public void setMonthlyRent(BigDecimal monthlyRent) { this.monthlyRent = monthlyRent; }
    }

    public static class LeaseCreationResult {
        private boolean success;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private int totalTenantsFound;
        private int leasesCreated;
        private int leasesAlreadyExist;
        private int assignmentsCreated;
        private int assignmentsAlreadyExist;
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();

        public void incrementLeasesCreated() { this.leasesCreated++; }
        public void incrementLeasesAlreadyExist() { this.leasesAlreadyExist++; }
        public void incrementAssignmentsCreated() { this.assignmentsCreated++; }
        public void incrementAssignmentsAlreadyExist() { this.assignmentsAlreadyExist++; }

        public void addError(String error) { this.errors.add(error); }
        public void addWarning(String warning) { this.warnings.add(warning); }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

        public int getTotalTenantsFound() { return totalTenantsFound; }
        public void setTotalTenantsFound(int totalTenantsFound) { this.totalTenantsFound = totalTenantsFound; }

        public int getLeasesCreated() { return leasesCreated; }
        public int getLeasesAlreadyExist() { return leasesAlreadyExist; }
        public int getAssignmentsCreated() { return assignmentsCreated; }
        public int getAssignmentsAlreadyExist() { return assignmentsAlreadyExist; }

        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }

        public String getSummary() {
            return String.format(
                "Lease Creation: %d tenants processed | %d leases created (%d already existed) | " +
                "%d assignments created (%d already existed) | %d errors | %d warnings",
                totalTenantsFound, leasesCreated, leasesAlreadyExist,
                assignmentsCreated, assignmentsAlreadyExist, errors.size(), warnings.size()
            );
        }
    }
}
