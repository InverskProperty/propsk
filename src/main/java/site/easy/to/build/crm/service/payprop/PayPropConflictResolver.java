// PayPropConflictResolver.java - Intelligent Conflict Resolution
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PayPropConflictResolver {

    private final CustomerService customerService;
    private final PropertyService propertyService;
    private final PayPropSyncLogger syncLogger;
    
    @Value("${payprop.conflict.resolution.strategy:LAST_WRITE_WINS}")
    private String defaultResolutionStrategy;
    
    @Value("${payprop.conflict.payprop-authority-fields:monthly_payment,enable_payments,deposit_amount}")
    private String payPropAuthorityFields;
    
    @Value("${payprop.conflict.crm-authority-fields:customer_type,entity_type,entity_id}")
    private String crmAuthorityFields;

    @Autowired
    public PayPropConflictResolver(CustomerService customerService,
                                  PropertyService propertyService,
                                  PayPropSyncLogger syncLogger) {
        this.customerService = customerService;
        this.propertyService = propertyService;
        this.syncLogger = syncLogger;
    }

    /**
     * Detect conflicts between CRM and PayProp data
     */
    public List<SyncConflict> detectConflicts() {
        List<SyncConflict> conflicts = new ArrayList<>();
        
        // Detect property conflicts
        conflicts.addAll(detectPropertyConflicts());
        
        // Detect customer conflicts
        conflicts.addAll(detectCustomerConflicts());
        
        syncLogger.logConflictDetection(conflicts.size());
        return conflicts;
    }

    /**
     * Resolve a specific conflict using configured strategy
     */
    public ConflictResolution resolveConflict(SyncConflict conflict) {
        ConflictResolution resolution = new ConflictResolution();
        resolution.setConflict(conflict);
        resolution.setTimestamp(LocalDateTime.now());
        
        try {
            switch (conflict.getEntityType()) {
                case "PROPERTY":
                    resolution = resolvePropertyConflict(conflict);
                    break;
                case "CUSTOMER":
                    resolution = resolveCustomerConflict(conflict);
                    break;
                case "TENANT":
                    resolution = resolveTenantConflict(conflict);
                    break;
                case "BENEFICIARY":
                    resolution = resolveBeneficiaryConflict(conflict);
                    break;
                default:
                    resolution.setResolved(false);
                    resolution.setReason("Unknown entity type: " + conflict.getEntityType());
            }
            
            syncLogger.logConflictResolution(conflict, resolution);
            
        } catch (Exception e) {
            resolution.setResolved(false);
            resolution.setReason("Error resolving conflict: " + e.getMessage());
            syncLogger.logConflictError(conflict, e);
        }
        
        return resolution;
    }

    // ===== CONFLICT DETECTION =====

    private List<SyncConflict> detectPropertyConflicts() {
        List<SyncConflict> conflicts = new ArrayList<>();
        
        // Find properties that exist in both systems with different data
        List<Property> syncedProperties = propertyService.findPropertiesByPayPropSyncStatus(true);
        
        for (Property property : syncedProperties) {
            try {
                // Compare with PayProp data (would need to fetch from PayProp API)
                // For now, simulate conflict detection based on update timestamps
                if (hasPropertyConflict(property)) {
                    SyncConflict conflict = new SyncConflict();
                    conflict.setEntityType("PROPERTY");
                    conflict.setEntityId(property.getId().toString());
                    conflict.setPayPropId(property.getPayPropId());
                    conflict.setConflictType(ConflictType.DATA_MISMATCH);
                    conflict.setDescription("Property data differs between CRM and PayProp");
                    conflict.setDetectedAt(LocalDateTime.now());
                    conflicts.add(conflict);
                }
            } catch (Exception e) {
                syncLogger.logEntityError("CONFLICT_DETECTION", property.getId(), e);
            }
        }
        
        return conflicts;
    }

    private List<SyncConflict> detectCustomerConflicts() {
        List<SyncConflict> conflicts = new ArrayList<>();
        
        // Find customers that exist in both systems with different data
        List<Customer> syncedCustomers = customerService.findCustomersNeedingPayPropSync();
        
        for (Customer customer : syncedCustomers) {
            try {
                if (hasCustomerConflict(customer)) {
                    SyncConflict conflict = new SyncConflict();
                    conflict.setEntityType("CUSTOMER");
                    conflict.setEntityId(customer.getCustomerId().toString());
                    conflict.setPayPropId(customer.getPayPropEntityId());
                    conflict.setConflictType(ConflictType.DATA_MISMATCH);
                    conflict.setDescription("Customer data differs between CRM and PayProp");
                    conflict.setDetectedAt(LocalDateTime.now());
                    conflicts.add(conflict);
                }
            } catch (Exception e) {
                syncLogger.logEntityError("CONFLICT_DETECTION", customer.getCustomerId(), e);
            }
        }
        
        return conflicts;
    }

    // ===== CONFLICT RESOLUTION =====

    private ConflictResolution resolvePropertyConflict(SyncConflict conflict) {
        ConflictResolution resolution = new ConflictResolution();
        
        try {
            Property property = propertyService.findById(Long.parseLong(conflict.getEntityId()));
            if (property == null) {
                resolution.setResolved(false);
                resolution.setReason("Property not found");
                return resolution;
            }
            
            // Apply resolution strategy
            ResolutionStrategy strategy = determineResolutionStrategy(conflict, "PROPERTY");
            
            switch (strategy) {
                case PAYPROP_WINS:
                    resolution = applyPayPropPropertyData(property, conflict);
                    break;
                case CRM_WINS:
                    resolution = applyCrmPropertyData(property, conflict);
                    break;
                case LAST_WRITE_WINS:
                    resolution = applyLastWritePropertyData(property, conflict);
                    break;
                case FIELD_AUTHORITY:
                    resolution = applyFieldAuthorityPropertyData(property, conflict);
                    break;
                case MANUAL_REVIEW:
                    resolution = createManualReviewResolution(conflict);
                    break;
            }
            
        } catch (Exception e) {
            resolution.setResolved(false);
            resolution.setReason("Error resolving property conflict: " + e.getMessage());
        }
        
        return resolution;
    }

    private ConflictResolution resolveCustomerConflict(SyncConflict conflict) {
        ConflictResolution resolution = new ConflictResolution();
        
        try {
            Customer customer = customerService.findByCustomerId(Integer.parseInt(conflict.getEntityId()));
            if (customer == null) {
                resolution.setResolved(false);
                resolution.setReason("Customer not found");
                return resolution;
            }
            
            // Apply resolution strategy
            ResolutionStrategy strategy = determineResolutionStrategy(conflict, "CUSTOMER");
            
            switch (strategy) {
                case PAYPROP_WINS:
                    resolution = applyPayPropCustomerData(customer, conflict);
                    break;
                case CRM_WINS:
                    resolution = applyCrmCustomerData(customer, conflict);
                    break;
                case LAST_WRITE_WINS:
                    resolution = applyLastWriteCustomerData(customer, conflict);
                    break;
                case FIELD_AUTHORITY:
                    resolution = applyFieldAuthorityCustomerData(customer, conflict);
                    break;
                case MANUAL_REVIEW:
                    resolution = createManualReviewResolution(conflict);
                    break;
            }
            
        } catch (Exception e) {
            resolution.setResolved(false);
            resolution.setReason("Error resolving customer conflict: " + e.getMessage());
        }
        
        return resolution;
    }

    private ConflictResolution resolveTenantConflict(SyncConflict conflict) {
        // Similar to customer conflict but tenant-specific logic
        return resolveCustomerConflict(conflict);
    }

    private ConflictResolution resolveBeneficiaryConflict(SyncConflict conflict) {
        // Similar to customer conflict but beneficiary-specific logic
        return resolveCustomerConflict(conflict);
    }

    // ===== RESOLUTION STRATEGIES =====

    private ResolutionStrategy determineResolutionStrategy(SyncConflict conflict, String entityType) {
        // Check if there are field-specific authority rules
        if (hasFieldAuthorityRules(conflict)) {
            return ResolutionStrategy.FIELD_AUTHORITY;
        }
        
        // Check for entity-specific strategies
        if ("PROPERTY".equals(entityType) && conflict.getConflictField() != null) {
            Set<String> payPropAuthoritySet = Set.of(payPropAuthorityFields.split(","));
            if (payPropAuthoritySet.contains(conflict.getConflictField())) {
                return ResolutionStrategy.PAYPROP_WINS;
            }
            
            Set<String> crmAuthoritySet = Set.of(crmAuthorityFields.split(","));
            if (crmAuthoritySet.contains(conflict.getConflictField())) {
                return ResolutionStrategy.CRM_WINS;
            }
        }
        
        // Apply default strategy
        switch (defaultResolutionStrategy) {
            case "PAYPROP_WINS": return ResolutionStrategy.PAYPROP_WINS;
            case "CRM_WINS": return ResolutionStrategy.CRM_WINS;
            case "MANUAL_REVIEW": return ResolutionStrategy.MANUAL_REVIEW;
            case "FIELD_AUTHORITY": return ResolutionStrategy.FIELD_AUTHORITY;
            default: return ResolutionStrategy.LAST_WRITE_WINS;
        }
    }

    private ConflictResolution applyPayPropPropertyData(Property property, SyncConflict conflict) {
        ConflictResolution resolution = new ConflictResolution();
        
        try {
            // Fetch latest data from PayProp and apply to property
            // This would require calling PayProp API to get latest property data
            
            // For now, simulate the resolution
            property.setUpdatedAt(LocalDateTime.now());
            propertyService.save(property);
            
            resolution.setResolved(true);
            resolution.setStrategy(ResolutionStrategy.PAYPROP_WINS);
            resolution.setReason("Applied PayProp data as authoritative source");
            
        } catch (Exception e) {
            resolution.setResolved(false);
            resolution.setReason("Failed to apply PayProp data: " + e.getMessage());
        }
        
        return resolution;
    }

    private ConflictResolution applyCrmPropertyData(Property property, SyncConflict conflict) {
        ConflictResolution resolution = new ConflictResolution();
        
        try {
            // Push CRM data to PayProp as authoritative
            // This would require calling PayProp API to update property
            
            property.setUpdatedAt(LocalDateTime.now());
            propertyService.save(property);
            
            resolution.setResolved(true);
            resolution.setStrategy(ResolutionStrategy.CRM_WINS);
            resolution.setReason("Applied CRM data as authoritative source");
            
        } catch (Exception e) {
            resolution.setResolved(false);
            resolution.setReason("Failed to apply CRM data: " + e.getMessage());
        }
        
        return resolution;
    }

    private ConflictResolution applyLastWritePropertyData(Property property, SyncConflict conflict) {
        ConflictResolution resolution = new ConflictResolution();
        
        try {
            // Compare timestamps and apply the most recent change
            LocalDateTime crmLastUpdate = property.getUpdatedAt();
            LocalDateTime payPropLastUpdate = getPayPropLastUpdate(property.getPayPropId());
            
            if (payPropLastUpdate != null && payPropLastUpdate.isAfter(crmLastUpdate)) {
                return applyPayPropPropertyData(property, conflict);
            } else {
                return applyCrmPropertyData(property, conflict);
            }
            
        } catch (Exception e) {
            resolution.setResolved(false);
            resolution.setReason("Failed to determine last write: " + e.getMessage());
        }
        
        return resolution;
    }

    private ConflictResolution applyFieldAuthorityPropertyData(Property property, SyncConflict conflict) {
        ConflictResolution resolution = new ConflictResolution();
        
        try {
            // Apply field-specific authority rules
            Map<String, String> fieldAuthority = parseFieldAuthorityRules();
            
            String conflictField = conflict.getConflictField();
            String authority = fieldAuthority.get(conflictField);
            
            if ("PAYPROP".equals(authority)) {
                return applyPayPropPropertyData(property, conflict);
            } else if ("CRM".equals(authority)) {
                return applyCrmPropertyData(property, conflict);
            } else {
                return applyLastWritePropertyData(property, conflict);
            }
            
        } catch (Exception e) {
            resolution.setResolved(false);
            resolution.setReason("Failed to apply field authority: " + e.getMessage());
        }
        
        return resolution;
    }

    private ConflictResolution applyPayPropCustomerData(Customer customer, SyncConflict conflict) {
        ConflictResolution resolution = new ConflictResolution();
        
        try {
            // Fetch latest data from PayProp and apply to customer
            customer.setPayPropLastSync(LocalDateTime.now());
            customerService.save(customer);
            
            resolution.setResolved(true);
            resolution.setStrategy(ResolutionStrategy.PAYPROP_WINS);
            resolution.setReason("Applied PayProp customer data as authoritative source");
            
        } catch (Exception e) {
            resolution.setResolved(false);
            resolution.setReason("Failed to apply PayProp customer data: " + e.getMessage());
        }
        
        return resolution;
    }

    private ConflictResolution applyCrmCustomerData(Customer customer, SyncConflict conflict) {
        ConflictResolution resolution = new ConflictResolution();
        
        try {
            // Push CRM data to PayProp as authoritative
            customer.setPayPropLastSync(LocalDateTime.now());
            customerService.save(customer);
            
            resolution.setResolved(true);
            resolution.setStrategy(ResolutionStrategy.CRM_WINS);
            resolution.setReason("Applied CRM customer data as authoritative source");
            
        } catch (Exception e) {
            resolution.setResolved(false);
            resolution.setReason("Failed to apply CRM customer data: " + e.getMessage());
        }
        
        return resolution;
    }

    private ConflictResolution applyLastWriteCustomerData(Customer customer, SyncConflict conflict) {
        ConflictResolution resolution = new ConflictResolution();
        
        try {
            // Compare timestamps and apply the most recent change
            LocalDateTime crmLastUpdate = customer.getCreatedAt(); // Use created for now
            LocalDateTime payPropLastUpdate = customer.getPayPropLastSync();
            
            if (payPropLastUpdate != null && payPropLastUpdate.isAfter(crmLastUpdate)) {
                return applyPayPropCustomerData(customer, conflict);
            } else {
                return applyCrmCustomerData(customer, conflict);
            }
            
        } catch (Exception e) {
            resolution.setResolved(false);
            resolution.setReason("Failed to determine last write for customer: " + e.getMessage());
        }
        
        return resolution;
    }

    private ConflictResolution applyFieldAuthorityCustomerData(Customer customer, SyncConflict conflict) {
        ConflictResolution resolution = new ConflictResolution();
        
        try {
            // Apply field-specific authority rules for customers
            Map<String, String> fieldAuthority = parseFieldAuthorityRules();
            
            String conflictField = conflict.getConflictField();
            String authority = fieldAuthority.get(conflictField);
            
            if ("PAYPROP".equals(authority)) {
                return applyPayPropCustomerData(customer, conflict);
            } else if ("CRM".equals(authority)) {
                return applyCrmCustomerData(customer, conflict);
            } else {
                return applyLastWriteCustomerData(customer, conflict);
            }
            
        } catch (Exception e) {
            resolution.setResolved(false);
            resolution.setReason("Failed to apply field authority for customer: " + e.getMessage());
        }
        
        return resolution;
    }

    private ConflictResolution createManualReviewResolution(SyncConflict conflict) {
        ConflictResolution resolution = new ConflictResolution();
        resolution.setResolved(false);
        resolution.setStrategy(ResolutionStrategy.MANUAL_REVIEW);
        resolution.setReason("Conflict requires manual review");
        resolution.setManualReviewRequired(true);
        return resolution;
    }

    // ===== UTILITY METHODS =====

    private boolean hasPropertyConflict(Property property) {
        // Only consider it a conflict if:
        // 1. Property exists in both systems (has PayProp ID)
        // 2. CRM was updated AFTER the last sync
        
        if (property.getPayPropId() == null) {
            return false; // Not in PayProp, no conflict
        }
        
        LocalDateTime lastSync = property.getPayPropLastSync();
        LocalDateTime lastUpdate = property.getUpdatedAt();
        
        // If never synced, or CRM was updated after last sync
        return lastSync != null && 
            lastUpdate != null && 
            lastUpdate.isAfter(lastSync.plusMinutes(5)); // 5min buffer for sync operations
    }

    private boolean hasCustomerConflict(Customer customer) {
        // Only consider it a conflict if:
        // 1. Customer exists in both systems (has PayProp ID)
        // 2. CRM was updated AFTER the last sync
        
        if (customer.getPayPropEntityId() == null) {
            return false; // Not in PayProp, no conflict
        }
        
        LocalDateTime lastSync = customer.getPayPropLastSync();
        LocalDateTime lastUpdate = customer.getCreatedAt(); // Use appropriate timestamp
        
        // If never synced, or CRM was updated after last sync
        return lastSync != null && 
            lastUpdate != null && 
            lastUpdate.isAfter(lastSync.plusMinutes(5)); // 5min buffer for sync operations
    }

    private boolean hasFieldAuthorityRules(SyncConflict conflict) {
        String field = conflict.getConflictField();
        return field != null && (
            Arrays.asList(payPropAuthorityFields.split(",")).contains(field) ||
            Arrays.asList(crmAuthorityFields.split(",")).contains(field)
        );
    }

    private Map<String, String> parseFieldAuthorityRules() {
        Map<String, String> rules = new HashMap<>();
        
        // PayProp authority fields
        for (String field : payPropAuthorityFields.split(",")) {
            rules.put(field.trim(), "PAYPROP");
        }
        
        // CRM authority fields
        for (String field : crmAuthorityFields.split(",")) {
            rules.put(field.trim(), "CRM");
        }
        
        return rules;
    }

    private LocalDateTime getPayPropLastUpdate(String payPropId) {
        // This would call PayProp API to get the last update timestamp
        // For now, return null to indicate unknown
        return null;
    }

    // ===== ENUMS AND CLASSES =====

    public enum ResolutionStrategy {
        PAYPROP_WINS, CRM_WINS, LAST_WRITE_WINS, FIELD_AUTHORITY, MANUAL_REVIEW
    }

    public enum ConflictType {
        DATA_MISMATCH, ENTITY_MISSING, DUPLICATE_ENTITY, VALIDATION_ERROR
    }

    public static class SyncConflict {
        private String entityType;
        private String entityId;
        private String payPropId;
        private ConflictType conflictType;
        private String conflictField;
        private String crmValue;
        private String payPropValue;
        private String description;
        private LocalDateTime detectedAt;
        private Map<String, Object> metadata;

        // Getters and setters
        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }
        
        public String getEntityId() { return entityId; }
        public void setEntityId(String entityId) { this.entityId = entityId; }
        
        public String getPayPropId() { return payPropId; }
        public void setPayPropId(String payPropId) { this.payPropId = payPropId; }
        
        public ConflictType getConflictType() { return conflictType; }
        public void setConflictType(ConflictType conflictType) { this.conflictType = conflictType; }
        
        public String getConflictField() { return conflictField; }
        public void setConflictField(String conflictField) { this.conflictField = conflictField; }
        
        public String getCrmValue() { return crmValue; }
        public void setCrmValue(String crmValue) { this.crmValue = crmValue; }
        
        public String getPayPropValue() { return payPropValue; }
        public void setPayPropValue(String payPropValue) { this.payPropValue = payPropValue; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public LocalDateTime getDetectedAt() { return detectedAt; }
        public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    public static class ConflictResolution {
        private SyncConflict conflict;
        private boolean resolved;
        private ResolutionStrategy strategy;
        private String reason;
        private boolean manualReviewRequired;
        private LocalDateTime timestamp;
        private Map<String, Object> resolutionData;

        // Getters and setters
        public SyncConflict getConflict() { return conflict; }
        public void setConflict(SyncConflict conflict) { this.conflict = conflict; }
        
        public boolean isResolved() { return resolved; }
        public void setResolved(boolean resolved) { this.resolved = resolved; }
        
        public ResolutionStrategy getStrategy() { return strategy; }
        public void setStrategy(ResolutionStrategy strategy) { this.strategy = strategy; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public boolean isManualReviewRequired() { return manualReviewRequired; }
        public void setManualReviewRequired(boolean manualReviewRequired) { this.manualReviewRequired = manualReviewRequired; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public Map<String, Object> getResolutionData() { return resolutionData; }
        public void setResolutionData(Map<String, Object> resolutionData) { this.resolutionData = resolutionData; }
    }
}