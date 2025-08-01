package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.customer.CustomerService;

import java.time.LocalDateTime;
import java.util.*;

/**
 * PayProp Entity Resolution Service
 * Handles resolution of orphaned entity references in the database.
 * Finds entities referenced in transactions but missing from main tables,
 * then fetches and creates them from PayProp.
 */
@Service
public class PayPropEntityResolutionService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropEntityResolutionService.class);
    
    @Autowired
    private PayPropSyncService apiClient;
    
    @Autowired
    private PayPropApiClient payPropApiClient;
    
    @Autowired
    private PropertyService propertyService;
    
    @Autowired
    private CustomerService customerService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * Scheduled task to resolve orphaned entities daily at 2 AM
     * Can also be called manually via API endpoint
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void resolveAllOrphanedEntities() {
        log.info("🔍 Starting scheduled orphaned entity resolution...");
        
        try {
            OrphanedEntityReport report = new OrphanedEntityReport();
            
            // Resolve orphaned properties
            report.setPropertyResolution(resolveOrphanedProperties());
            
            // Resolve orphaned tenants
            report.setTenantResolution(resolveOrphanedTenants());
            
            // Resolve orphaned beneficiaries
            report.setBeneficiaryResolution(resolveOrphanedBeneficiaries());
            
            // Log summary
            logResolutionSummary(report);
            
        } catch (Exception e) {
            log.error("❌ Failed to complete orphaned entity resolution: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Resolve orphaned property references
     * Finds property IDs in financial_transactions that don't exist in properties table
     */
    @Transactional
    public ResolutionResult resolveOrphanedProperties() {
        log.info("🏠 Resolving orphaned properties...");
        
        ResolutionResult result = new ResolutionResult("Properties");
        
        try {
            // Find orphaned property IDs
            List<String> orphanedIds = findOrphanedPropertyIds();
            result.setOrphanedCount(orphanedIds.size());
            
            if (orphanedIds.isEmpty()) {
                log.info("✅ No orphaned properties found");
                return result;
            }
            
            log.info("Found {} orphaned property references", orphanedIds.size());
            
            // First, try to fetch all properties in bulk to find matches
            Map<String, Map<String, Object>> allProperties = fetchAllPropertiesAsMap();
            
            for (String propertyId : orphanedIds) {
                try {
                    boolean resolved = resolveProperty(propertyId, allProperties);
                    if (resolved) {
                        result.incrementResolved();
                    } else {
                        result.incrementFailed();
                        result.addFailedId(propertyId);
                    }
                } catch (Exception e) {
                    log.error("Failed to resolve property {}: {}", propertyId, e.getMessage());
                    result.incrementFailed();
                    result.addFailedId(propertyId);
                }
            }
            
        } catch (Exception e) {
            log.error("Error during property resolution: {}", e.getMessage(), e);
            result.setError(e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Resolve orphaned tenant references
     * Finds tenant IDs in financial_transactions that don't exist in customers table
     */
    @Transactional
    public ResolutionResult resolveOrphanedTenants() {
        log.info("👥 Resolving orphaned tenants...");
        
        ResolutionResult result = new ResolutionResult("Tenants");
        
        try {
            // Find orphaned tenant IDs
            List<String> orphanedIds = findOrphanedTenantIds();
            result.setOrphanedCount(orphanedIds.size());
            
            if (orphanedIds.isEmpty()) {
                log.info("✅ No orphaned tenants found");
                return result;
            }
            
            log.info("Found {} orphaned tenant references", orphanedIds.size());
            
            // Fetch all tenants to find matches
            Map<String, Map<String, Object>> allTenants = fetchAllTenantsAsMap();
            
            for (String tenantId : orphanedIds) {
                try {
                    boolean resolved = resolveTenant(tenantId, allTenants);
                    if (resolved) {
                        result.incrementResolved();
                    } else {
                        result.incrementFailed();
                        result.addFailedId(tenantId);
                    }
                } catch (Exception e) {
                    log.error("Failed to resolve tenant {}: {}", tenantId, e.getMessage());
                    result.incrementFailed();
                    result.addFailedId(tenantId);
                }
            }
            
        } catch (Exception e) {
            log.error("Error during tenant resolution: {}", e.getMessage(), e);
            result.setError(e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Resolve orphaned beneficiary references
     * Finds beneficiary IDs in payments that don't exist in customers table
     */
    @Transactional
    public ResolutionResult resolveOrphanedBeneficiaries() {
        log.info("💰 Resolving orphaned beneficiaries...");
        
        ResolutionResult result = new ResolutionResult("Beneficiaries");
        
        try {
            // Find orphaned beneficiary IDs
            List<String> orphanedIds = findOrphanedBeneficiaryIds();
            result.setOrphanedCount(orphanedIds.size());
            
            if (orphanedIds.isEmpty()) {
                log.info("✅ No orphaned beneficiaries found");
                return result;
            }
            
            log.info("Found {} orphaned beneficiary references", orphanedIds.size());
            
            // Fetch all beneficiaries to find matches
            Map<String, Map<String, Object>> allBeneficiaries = fetchAllBeneficiariesAsMap();
            
            for (String beneficiaryId : orphanedIds) {
                try {
                    boolean resolved = resolveBeneficiary(beneficiaryId, allBeneficiaries);
                    if (resolved) {
                        result.incrementResolved();
                    } else {
                        result.incrementFailed();
                        result.addFailedId(beneficiaryId);
                    }
                } catch (Exception e) {
                    log.error("Failed to resolve beneficiary {}: {}", beneficiaryId, e.getMessage());
                    result.incrementFailed();
                    result.addFailedId(beneficiaryId);
                }
            }
            
        } catch (Exception e) {
            log.error("Error during beneficiary resolution: {}", e.getMessage(), e);
            result.setError(e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Find orphaned property IDs using SQL query
     */
    private List<String> findOrphanedPropertyIds() {
        String sql = """
            SELECT DISTINCT ft.property_id 
            FROM financial_transactions ft 
            LEFT JOIN properties p ON p.payprop_id = ft.property_id 
            WHERE ft.property_id IS NOT NULL 
            AND p.id IS NULL
            ORDER BY ft.property_id
        """;
        
        return jdbcTemplate.queryForList(sql, String.class);
    }
    
    /**
     * Find orphaned tenant IDs using SQL query
     */
    private List<String> findOrphanedTenantIds() {
        String sql = """
            SELECT DISTINCT ft.tenant_id 
            FROM financial_transactions ft 
            LEFT JOIN customers c ON c.payprop_entity_id = ft.tenant_id 
            WHERE ft.tenant_id IS NOT NULL 
            AND c.customer_id IS NULL
            ORDER BY ft.tenant_id
        """;
        
        return jdbcTemplate.queryForList(sql, String.class);
    }
    
    /**
     * Find orphaned beneficiary IDs from payments table
     */
    private List<String> findOrphanedBeneficiaryIds() {
        String sql = """
            SELECT DISTINCT p.beneficiary_id 
            FROM payments p 
            LEFT JOIN customers c ON c.customer_id = p.beneficiary_id 
            WHERE p.beneficiary_id IS NOT NULL 
            AND c.customer_id IS NULL
            ORDER BY p.beneficiary_id
        """;
        
        return jdbcTemplate.queryForList(sql, String.class);
    }
    
    /**
     * Fetch all properties from PayProp as a map for efficient lookup
     */
    private Map<String, Map<String, Object>> fetchAllPropertiesAsMap() {
        log.info("Fetching all properties from PayProp for matching...");
        
        Map<String, Map<String, Object>> propertyMap = new HashMap<>();
        
        List<Map<String, Object>> allProperties = payPropApiClient.fetchAllPages(
            "/export/properties", 
            Function.identity()
        );
        
        for (Map<String, Object> property : allProperties) {
            String id = (String) property.get("id");
            if (id != null) {
                propertyMap.put(id, property);
            }
        }
        
        log.info("Loaded {} properties from PayProp", propertyMap.size());
        return propertyMap;
    }
    
    /**
     * Fetch all tenants from PayProp as a map
     */
    private Map<String, Map<String, Object>> fetchAllTenantsAsMap() {
        log.info("Fetching all tenants from PayProp for matching...");
        
        Map<String, Map<String, Object>> tenantMap = new HashMap<>();
        
        List<Map<String, Object>> allTenants = payPropApiClient.fetchAllPages(
            "/export/tenants", 
            Function.identity()
        );
        
        for (Map<String, Object> tenant : allTenants) {
            String id = (String) tenant.get("id");
            if (id != null) {
                tenantMap.put(id, tenant);
            }
        }
        
        log.info("Loaded {} tenants from PayProp", tenantMap.size());
        return tenantMap;
    }
    
    /**
     * Fetch all beneficiaries from PayProp as a map
     */
    private Map<String, Map<String, Object>> fetchAllBeneficiariesAsMap() {
        log.info("Fetching all beneficiaries from PayProp for matching...");
        
        Map<String, Map<String, Object>> beneficiaryMap = new HashMap<>();
        
        List<Map<String, Object>> allBeneficiaries = payPropApiClient.fetchAllPages(
            "/export/beneficiaries", 
            Function.identity()
        );
        
        for (Map<String, Object> beneficiary : allBeneficiaries) {
            String id = (String) beneficiary.get("id");
            if (id != null) {
                beneficiaryMap.put(id, beneficiary);
            }
        }
        
        log.info("Loaded {} beneficiaries from PayProp", beneficiaryMap.size());
        return beneficiaryMap;
    }
    
    /**
     * Resolve a single property
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private boolean resolveProperty(String propertyId, Map<String, Map<String, Object>> allProperties) {
        try {
            // Check if property exists in PayProp data
            Map<String, Object> propertyData = allProperties.get(propertyId);
            
            if (propertyData == null) {
                // Try to fetch individually
                log.debug("Property {} not found in bulk data, fetching individually...", propertyId);
                propertyData = apiClient.getCompletePropertyData(propertyId);
            }
            
            if (propertyData == null) {
                log.warn("Property {} not found in PayProp", propertyId);
                return false;
            }
            
            // Create property in local database
            Property property = createPropertyFromPayPropData(propertyData);
            propertyService.save(property);
            
            log.info("✅ Resolved property: {} -> {}", propertyId, property.getPropertyName());
            return true;
            
        } catch (Exception e) {
            log.error("Failed to resolve property {}: {}", propertyId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Resolve a single tenant
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private boolean resolveTenant(String tenantId, Map<String, Map<String, Object>> allTenants) {
        try {
            Map<String, Object> tenantData = allTenants.get(tenantId);
            
            if (tenantData == null) {
                log.warn("Tenant {} not found in PayProp", tenantId);
                return false;
            }
            
            // Create customer as tenant in local database
            Customer customer = createCustomerFromTenantData(tenantData);
            customerService.save(customer);
            
            log.info("✅ Resolved tenant: {} -> {}", tenantId, 
                customer.getFirstName() + " " + customer.getLastName());
            return true;
            
        } catch (Exception e) {
            log.error("Failed to resolve tenant {}: {}", tenantId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Resolve a single beneficiary
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private boolean resolveBeneficiary(String beneficiaryId, Map<String, Map<String, Object>> allBeneficiaries) {
        try {
            Map<String, Object> beneficiaryData = allBeneficiaries.get(beneficiaryId);
            
            if (beneficiaryData == null) {
                log.warn("Beneficiary {} not found in PayProp", beneficiaryId);
                return false;
            }
            
            // Create customer as beneficiary in local database
            Customer customer = createCustomerFromBeneficiaryData(beneficiaryData);
            customerService.save(customer);
            
            log.info("✅ Resolved beneficiary: {} -> {}", beneficiaryId, 
                customer.getFirstName() + " " + customer.getLastName());
            return true;
            
        } catch (Exception e) {
            log.error("Failed to resolve beneficiary {}: {}", beneficiaryId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Create Property entity from PayProp data
     */
    private Property createPropertyFromPayPropData(Map<String, Object> data) {
        Property property = new Property();
        
        property.setPayPropId((String) data.get("id"));
        property.setPropertyName((String) data.get("name"));
        property.setCustomerId((String) data.get("customer_id"));
        property.setCustomerReference((String) data.get("customer_reference"));
        property.setAgentName((String) data.get("agent_name"));
        property.setComment((String) data.get("notes"));
        
        // Address
        Map<String, Object> address = (Map<String, Object>) data.get("address");
        if (address != null) {
            property.setAddressLine1((String) address.get("address_line_1"));
            property.setAddressLine2((String) address.get("address_line_2"));
            property.setAddressLine3((String) address.get("address_line_3"));
            property.setCity((String) address.get("city"));
            property.setPostcode((String) address.get("postal_code"));
            property.setCountryCode((String) address.get("country_code"));
            property.setState((String) address.get("state"));
        }
        
        // Settings
        Map<String, Object> settings = (Map<String, Object>) data.get("settings");
        if (settings != null) {
            Object monthlyPayment = settings.get("monthly_payment");
            if (monthlyPayment != null) {
                property.setMonthlyPayment(Double.parseDouble(monthlyPayment.toString()));
            }
        }
        
        property.setCreatedAt(LocalDateTime.now());
        property.setUpdatedAt(LocalDateTime.now());
        
        return property;
    }
    
    /**
     * Create Customer entity from PayProp tenant data
     */
    private Customer createCustomerFromTenantData(Map<String, Object> data) {
        Customer customer = new Customer();
        
        customer.setPayPropEntityId((String) data.get("id"));
        customer.setIsTenant(true);
        
        String accountType = (String) data.get("account_type");
        if ("individual".equalsIgnoreCase(accountType)) {
            customer.setFirstName((String) data.get("first_name"));
            customer.setLastName((String) data.get("last_name"));
        } else {
            customer.setBusinessName((String) data.get("business_name"));
            customer.setIsCompany(true);
        }
        
        customer.setEmail((String) data.get("email_address"));
        customer.setPhone((String) data.get("phone"));
        customer.setCustomerReference((String) data.get("customer_reference"));
        
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());
        
        return customer;
    }
    
    /**
     * Create Customer entity from PayProp beneficiary data
     */
    private Customer createCustomerFromBeneficiaryData(Map<String, Object> data) {
        Customer customer = new Customer();
        
        customer.setPayPropEntityId((String) data.get("id"));
        customer.setIsPropertyOwner(true);
        
        String accountType = (String) data.get("account_type");
        if ("individual".equalsIgnoreCase(accountType)) {
            customer.setFirstName((String) data.get("first_name"));
            customer.setLastName((String) data.get("last_name"));
        } else {
            customer.setBusinessName((String) data.get("business_name"));
            customer.setIsCompany(true);
        }
        
        customer.setEmail((String) data.get("email_address"));
        customer.setPhone((String) data.get("phone"));
        customer.setCustomerReference((String) data.get("customer_reference"));
        
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());
        
        return customer;
    }
    
    /**
     * Log resolution summary
     */
    private void logResolutionSummary(OrphanedEntityReport report) {
        log.info("🏁 === ORPHANED ENTITY RESOLUTION SUMMARY ===");
        
        logEntitySummary(report.getPropertyResolution());
        logEntitySummary(report.getTenantResolution());
        logEntitySummary(report.getBeneficiaryResolution());
        
        int totalOrphaned = report.getTotalOrphaned();
        int totalResolved = report.getTotalResolved();
        int totalFailed = report.getTotalFailed();
        
        log.info("📊 TOTAL: {} orphaned, {} resolved, {} failed", 
            totalOrphaned, totalResolved, totalFailed);
        
        if (totalFailed > 0) {
            log.warn("⚠️ Failed to resolve {} entities. Manual intervention may be required.", totalFailed);
        }
        
        if (totalResolved > 0) {
            log.info("✅ Successfully resolved {} orphaned entities!", totalResolved);
        }
    }
    
    /**
     * Log individual entity type summary
     */
    private void logEntitySummary(ResolutionResult result) {
        if (result.getOrphanedCount() > 0) {
            log.info("{}: {} orphaned, {} resolved, {} failed", 
                result.getEntityType(), 
                result.getOrphanedCount(), 
                result.getResolvedCount(), 
                result.getFailedCount()
            );
            
            if (!result.getFailedIds().isEmpty()) {
                log.debug("Failed {} IDs: {}", result.getEntityType(), result.getFailedIds());
            }
        }
    }
    
    /**
     * Resolution result for a single entity type
     */
    public static class ResolutionResult {
        private String entityType;
        private int orphanedCount = 0;
        private int resolvedCount = 0;
        private int failedCount = 0;
        private List<String> failedIds = new ArrayList<>();
        private String error;
        
        public ResolutionResult(String entityType) {
            this.entityType = entityType;
        }
        
        public void incrementResolved() {
            resolvedCount++;
        }
        
        public void incrementFailed() {
            failedCount++;
        }
        
        public void addFailedId(String id) {
            failedIds.add(id);
        }
        
        // Getters and setters
        public String getEntityType() { return entityType; }
        public int getOrphanedCount() { return orphanedCount; }
        public void setOrphanedCount(int count) { this.orphanedCount = count; }
        public int getResolvedCount() { return resolvedCount; }
        public int getFailedCount() { return failedCount; }
        public List<String> getFailedIds() { return failedIds; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
    
    /**
     * Complete orphaned entity report
     */
    public static class OrphanedEntityReport {
        private ResolutionResult propertyResolution;
        private ResolutionResult tenantResolution;
        private ResolutionResult beneficiaryResolution;
        private LocalDateTime timestamp = LocalDateTime.now();
        
        public int getTotalOrphaned() {
            return (propertyResolution != null ? propertyResolution.getOrphanedCount() : 0) +
                   (tenantResolution != null ? tenantResolution.getOrphanedCount() : 0) +
                   (beneficiaryResolution != null ? beneficiaryResolution.getOrphanedCount() : 0);
        }
        
        public int getTotalResolved() {
            return (propertyResolution != null ? propertyResolution.getResolvedCount() : 0) +
                   (tenantResolution != null ? tenantResolution.getResolvedCount() : 0) +
                   (beneficiaryResolution != null ? beneficiaryResolution.getResolvedCount() : 0);
        }
        
        public int getTotalFailed() {
            return (propertyResolution != null ? propertyResolution.getFailedCount() : 0) +
                   (tenantResolution != null ? tenantResolution.getFailedCount() : 0) +
                   (beneficiaryResolution != null ? beneficiaryResolution.getFailedCount() : 0);
        }
        
        // Getters and setters
        public ResolutionResult getPropertyResolution() { return propertyResolution; }
        public void setPropertyResolution(ResolutionResult result) { this.propertyResolution = result; }
        public ResolutionResult getTenantResolution() { return tenantResolution; }
        public void setTenantResolution(ResolutionResult result) { this.tenantResolution = result; }
        public ResolutionResult getBeneficiaryResolution() { return beneficiaryResolution; }
        public void setBeneficiaryResolution(ResolutionResult result) { this.beneficiaryResolution = result; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}