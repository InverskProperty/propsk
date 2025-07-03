// PayPropChangeDetection.java - Intelligent Change Detection for Optimized Sync
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.TenantService;
import site.easy.to.build.crm.service.property.PropertyOwnerService;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PayPropChangeDetection {

    private final CustomerService customerService;
    private final PropertyService propertyService;
    private final TenantService tenantService;
    private final PropertyOwnerService propertyOwnerService;
    private final PayPropSyncService payPropSyncService;

    @Autowired
    public PayPropChangeDetection(CustomerService customerService,
                                 PropertyService propertyService,
                                 TenantService tenantService,
                                 PropertyOwnerService propertyOwnerService,
                                 PayPropSyncService payPropSyncService) {
        this.customerService = customerService;
        this.propertyService = propertyService;
        this.tenantService = tenantService;
        this.propertyOwnerService = propertyOwnerService;
        this.payPropSyncService = payPropSyncService;
    }

    /**
     * Detect all changes since last sync
     */
    public SyncChangeDetection detectChanges() {
        SyncChangeDetection detection = new SyncChangeDetection();
        
        // Detect CRM changes
        detection.setCrmChanges(detectCrmChanges());
        
        // Detect PayProp changes (would require API calls)
        detection.setPayPropChanges(detectPayPropChanges());
        
        return detection;
    }

    /**
     * Detect changes since specific timestamp
     */
    public SyncChangeDetection detectChangesSince(LocalDateTime since) {
        SyncChangeDetection detection = new SyncChangeDetection();
        detection.setSince(since);
        
        // Detect CRM changes since timestamp
        detection.setCrmChanges(detectCrmChangesSince(since));
        
        // Detect PayProp changes since timestamp
        detection.setPayPropChanges(detectPayPropChangesSince(since));
        
        return detection;
    }

    // ===== CRM CHANGE DETECTION =====

    private CrmChanges detectCrmChanges() {
        return detectCrmChangesSince(getLastSyncTimestamp());
    }

    private CrmChanges detectCrmChangesSince(LocalDateTime since) {
        CrmChanges changes = new CrmChanges();
        
        // Detect property changes
        changes.setModifiedProperties(findModifiedProperties(since));
        changes.setNewProperties(findNewProperties(since));
        
        // Detect customer changes
        changes.setModifiedCustomers(findModifiedCustomers(since));
        changes.setNewCustomers(findNewCustomers(since));
        
        // Detect tenant changes (legacy entities)
        changes.setModifiedTenants(findModifiedTenants(since));
        changes.setNewTenants(findNewTenants(since));
        
        // Detect property owner changes (legacy entities)
        changes.setModifiedPropertyOwners(findModifiedPropertyOwners(since));
        changes.setNewPropertyOwners(findNewPropertyOwners(since));
        
        return changes;
    }

    private List<Property> findModifiedProperties(LocalDateTime since) {
        return propertyService.findAll().stream()
            .filter(p -> p.getUpdatedAt() != null && p.getUpdatedAt().isAfter(since))
            .filter(p -> p.getPayPropId() != null) // Only sync already synced properties
            .toList();
    }

    private List<Property> findNewProperties(LocalDateTime since) {
        return propertyService.findAll().stream()
            .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(since))
            .filter(p -> p.getPayPropId() == null) // Only new properties not yet synced
            .filter(p -> p.isReadyForPayPropSync()) // Only ready properties
            .toList();
    }

    private List<Customer> findModifiedCustomers(LocalDateTime since) {
        return customerService.findAll().stream()
            .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(since))
            .filter(c -> c.getPayPropEntityId() != null) // Only sync already synced customers
            .filter(c -> c.isPayPropEntity()) // Only PayProp-related customers
            .toList();
    }

    private List<Customer> findNewCustomers(LocalDateTime since) {
        return customerService.findAll().stream()
            .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(since))
            .filter(c -> c.getPayPropEntityId() == null) // Only new customers not yet synced
            .filter(c -> c.isPayPropEntity()) // Only PayProp-related customers
            .filter(c -> c.isReadyForPayPropSync()) // Only ready customers
            .toList();
    }

    private List<Tenant> findModifiedTenants(LocalDateTime since) {
        return tenantService.findAll().stream()
            .filter(t -> t.getUpdatedAt() != null && t.getUpdatedAt().isAfter(since))
            .filter(t -> t.getPayPropId() != null) // Only sync already synced tenants
            .toList();
    }

    private List<Tenant> findNewTenants(LocalDateTime since) {
        return tenantService.findAll().stream()
            .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isAfter(since))
            .filter(t -> t.getPayPropId() == null) // Only new tenants not yet synced
            .filter(t -> t.isReadyForPayPropSync()) // Only ready tenants
            .toList();
    }

    private List<PropertyOwner> findModifiedPropertyOwners(LocalDateTime since) {
        return propertyOwnerService.findAll().stream()
            .filter(o -> o.getUpdatedAt() != null && o.getUpdatedAt().isAfter(since))
            .filter(o -> o.getPayPropId() != null) // Only sync already synced owners
            .toList();
    }

    private List<PropertyOwner> findNewPropertyOwners(LocalDateTime since) {
        return propertyOwnerService.findAll().stream()
            .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(since))
            .filter(o -> o.getPayPropId() == null) // Only new owners not yet synced
            // Add readiness check when available: .filter(o -> o.isReadyForPayPropSync())
            .toList();
    }

    // ===== PAYPROP CHANGE DETECTION =====

    private PayPropChanges detectPayPropChanges() {
        return detectPayPropChangesSince(getLastSyncTimestamp());
    }

    private PayPropChanges detectPayPropChangesSince(LocalDateTime since) {
        PayPropChanges changes = new PayPropChanges();
        
        try {
            // This would require API calls to PayProp to get modified entities
            // For now, we'll simulate the detection
            
            changes.setModifiedProperties(detectModifiedPayPropProperties(since));
            changes.setModifiedTenants(detectModifiedPayPropTenants(since));
            changes.setModifiedBeneficiaries(detectModifiedPayPropBeneficiaries(since));
            changes.setNewProperties(detectNewPayPropProperties(since));
            changes.setNewTenants(detectNewPayPropTenants(since));
            changes.setNewBeneficiaries(detectNewPayPropBeneficiaries(since));
            
        } catch (Exception e) {
            System.err.println("Failed to detect PayProp changes: " + e.getMessage());
            // Return empty changes on error
        }
        
        return changes;
    }

    private List<Map<String, Object>> detectModifiedPayPropProperties(LocalDateTime since) {
        try {
            // This would call PayProp API with modified_from_time parameter
            // For now, return empty list
            return List.of();
            
            // Real implementation would be:
            // String modifiedFrom = since.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            // return payPropSyncService.getModifiedProperties(modifiedFrom);
            
        } catch (Exception e) {
            System.err.println("Failed to detect modified PayProp properties: " + e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> detectModifiedPayPropTenants(LocalDateTime since) {
        try {
            // This would call PayProp API with modified_from_time parameter
            return List.of();
        } catch (Exception e) {
            System.err.println("Failed to detect modified PayProp tenants: " + e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> detectModifiedPayPropBeneficiaries(LocalDateTime since) {
        try {
            // This would call PayProp API with modified_from_time parameter
            return List.of();
        } catch (Exception e) {
            System.err.println("Failed to detect modified PayProp beneficiaries: " + e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> detectNewPayPropProperties(LocalDateTime since) {
        // Would detect new entities in PayProp that don't exist in CRM
        return List.of();
    }

    private List<Map<String, Object>> detectNewPayPropTenants(LocalDateTime since) {
        // Would detect new entities in PayProp that don't exist in CRM
        return List.of();
    }

    private List<Map<String, Object>> detectNewPayPropBeneficiaries(LocalDateTime since) {
        // Would detect new entities in PayProp that don't exist in CRM
        return List.of();
    }

    // ===== UTILITY METHODS =====

    private LocalDateTime getLastSyncTimestamp() {
        // This would query the database for the last successful sync timestamp
        // For now, return 24 hours ago as default
        return LocalDateTime.now().minusDays(1);
    }

    /**
     * Check if an entity has meaningful changes for sync
     */
    public boolean hasSignificantChanges(Object entity, LocalDateTime since) {
        if (entity instanceof Property) {
            return hasSignificantPropertyChanges((Property) entity, since);
        } else if (entity instanceof Customer) {
            return hasSignificantCustomerChanges((Customer) entity, since);
        } else if (entity instanceof Tenant) {
            return hasSignificantTenantChanges((Tenant) entity, since);
        } else if (entity instanceof PropertyOwner) {
            return hasSignificantPropertyOwnerChanges((PropertyOwner) entity, since);
        }
        return false;
    }

    private boolean hasSignificantPropertyChanges(Property property, LocalDateTime since) {
        if (property.getUpdatedAt() == null || !property.getUpdatedAt().isAfter(since)) {
            return false;
        }
        
        // Check if changes are in PayProp-relevant fields
        // This is a simplified check - in reality you'd track field-level changes
        return property.getPropertyName() != null ||
               property.getMonthlyPayment() != null ||
               property.getEnablePayments() != null ||
               property.getAddressLine1() != null;
    }

    private boolean hasSignificantCustomerChanges(Customer customer, LocalDateTime since) {
        if (customer.getCreatedAt() == null || !customer.getCreatedAt().isAfter(since)) {
            return false;
        }
        
        // Check if changes are in PayProp-relevant fields
        return customer.isPayPropEntity() &&
               (customer.getEmail() != null ||
                customer.getFirstName() != null ||
                customer.getLastName() != null ||
                customer.getBusinessName() != null);
    }

    private boolean hasSignificantTenantChanges(Tenant tenant, LocalDateTime since) {
        if (tenant.getUpdatedAt() == null || !tenant.getUpdatedAt().isAfter(since)) {
            return false;
        }
        
        // Check if changes are in PayProp-relevant fields
        return tenant.getEmailAddress() != null ||
               tenant.getFirstName() != null ||
               tenant.getLastName() != null ||
               tenant.getBusinessName() != null;
    }

    private boolean hasSignificantPropertyOwnerChanges(PropertyOwner owner, LocalDateTime since) {
        if (owner.getUpdatedAt() == null || !owner.getUpdatedAt().isAfter(since)) {
            return false;
        }
        
        // Check if changes are in PayProp-relevant fields
        return owner.getEmailAddress() != null ||
               owner.getFirstName() != null ||
               owner.getLastName() != null ||
               owner.getBusinessName() != null ||
               owner.getPaymentMethod() != null;
    }

    // ===== RESULT CLASSES =====

    public static class SyncChangeDetection {
        private LocalDateTime since;
        private LocalDateTime detectedAt;
        private CrmChanges crmChanges;
        private PayPropChanges payPropChanges;

        public SyncChangeDetection() {
            this.detectedAt = LocalDateTime.now();
            this.crmChanges = new CrmChanges();
            this.payPropChanges = new PayPropChanges();
        }

        public boolean hasNoCrmChanges() {
            return crmChanges.isEmpty();
        }

        public boolean hasNoPayPropChanges() {
            return payPropChanges.isEmpty();
        }

        public boolean hasCrmChanges() {
            return !crmChanges.isEmpty();
        }

        public boolean hasPayPropChanges() {
            return !payPropChanges.isEmpty();
        }

        public boolean hasNoChanges() {
            return hasNoCrmChanges() && hasNoPayPropChanges();
        }

        // Getters and setters
        public LocalDateTime getSince() { return since; }
        public void setSince(LocalDateTime since) { this.since = since; }
        
        public LocalDateTime getDetectedAt() { return detectedAt; }
        public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
        
        public CrmChanges getCrmChanges() { return crmChanges; }
        public void setCrmChanges(CrmChanges crmChanges) { this.crmChanges = crmChanges; }
        
        public PayPropChanges getPayPropChanges() { return payPropChanges; }
        public void setPayPropChanges(PayPropChanges payPropChanges) { this.payPropChanges = payPropChanges; }
    }

    public static class CrmChanges {
        private List<Property> modifiedProperties = new ArrayList<>();
        private List<Property> newProperties = new ArrayList<>();
        private List<Customer> modifiedCustomers = new ArrayList<>();
        private List<Customer> newCustomers = new ArrayList<>();
        private List<Tenant> modifiedTenants = new ArrayList<>();
        private List<Tenant> newTenants = new ArrayList<>();
        private List<PropertyOwner> modifiedPropertyOwners = new ArrayList<>();
        private List<PropertyOwner> newPropertyOwners = new ArrayList<>();

        public boolean isEmpty() {
            return modifiedProperties.isEmpty() && newProperties.isEmpty() &&
                   modifiedCustomers.isEmpty() && newCustomers.isEmpty() &&
                   modifiedTenants.isEmpty() && newTenants.isEmpty() &&
                   modifiedPropertyOwners.isEmpty() && newPropertyOwners.isEmpty();
        }

        public int getTotalChanges() {
            return modifiedProperties.size() + newProperties.size() +
                   modifiedCustomers.size() + newCustomers.size() +
                   modifiedTenants.size() + newTenants.size() +
                   modifiedPropertyOwners.size() + newPropertyOwners.size();
        }

        // Getters and setters
        public List<Property> getModifiedProperties() { return modifiedProperties; }
        public void setModifiedProperties(List<Property> modifiedProperties) { this.modifiedProperties = modifiedProperties; }
        
        public List<Property> getNewProperties() { return newProperties; }
        public void setNewProperties(List<Property> newProperties) { this.newProperties = newProperties; }
        
        public List<Customer> getModifiedCustomers() { return modifiedCustomers; }
        public void setModifiedCustomers(List<Customer> modifiedCustomers) { this.modifiedCustomers = modifiedCustomers; }
        
        public List<Customer> getNewCustomers() { return newCustomers; }
        public void setNewCustomers(List<Customer> newCustomers) { this.newCustomers = newCustomers; }
        
        public List<Tenant> getModifiedTenants() { return modifiedTenants; }
        public void setModifiedTenants(List<Tenant> modifiedTenants) { this.modifiedTenants = modifiedTenants; }
        
        public List<Tenant> getNewTenants() { return newTenants; }
        public void setNewTenants(List<Tenant> newTenants) { this.newTenants = newTenants; }
        
        public List<PropertyOwner> getModifiedPropertyOwners() { return modifiedPropertyOwners; }
        public void setModifiedPropertyOwners(List<PropertyOwner> modifiedPropertyOwners) { this.modifiedPropertyOwners = modifiedPropertyOwners; }
        
        public List<PropertyOwner> getNewPropertyOwners() { return newPropertyOwners; }
        public void setNewPropertyOwners(List<PropertyOwner> newPropertyOwners) { this.newPropertyOwners = newPropertyOwners; }
    }

    public static class PayPropChanges {
        private List<Map<String, Object>> modifiedProperties = new ArrayList<>();
        private List<Map<String, Object>> newProperties = new ArrayList<>();
        private List<Map<String, Object>> modifiedTenants = new ArrayList<>();
        private List<Map<String, Object>> newTenants = new ArrayList<>();
        private List<Map<String, Object>> modifiedBeneficiaries = new ArrayList<>();
        private List<Map<String, Object>> newBeneficiaries = new ArrayList<>();

        public boolean isEmpty() {
            return modifiedProperties.isEmpty() && newProperties.isEmpty() &&
                   modifiedTenants.isEmpty() && newTenants.isEmpty() &&
                   modifiedBeneficiaries.isEmpty() && newBeneficiaries.isEmpty();
        }

        public int getTotalChanges() {
            return modifiedProperties.size() + newProperties.size() +
                   modifiedTenants.size() + newTenants.size() +
                   modifiedBeneficiaries.size() + newBeneficiaries.size();
        }

        // Getters and setters
        public List<Map<String, Object>> getModifiedProperties() { return modifiedProperties; }
        public void setModifiedProperties(List<Map<String, Object>> modifiedProperties) { this.modifiedProperties = modifiedProperties; }
        
        public List<Map<String, Object>> getNewProperties() { return newProperties; }
        public void setNewProperties(List<Map<String, Object>> newProperties) { this.newProperties = newProperties; }
        
        public List<Map<String, Object>> getModifiedTenants() { return modifiedTenants; }
        public void setModifiedTenants(List<Map<String, Object>> modifiedTenants) { this.modifiedTenants = modifiedTenants; }
        
        public List<Map<String, Object>> getNewTenants() { return newTenants; }
        public void setNewTenants(List<Map<String, Object>> newTenants) { this.newTenants = newTenants; }
        
        public List<Map<String, Object>> getModifiedBeneficiaries() { return modifiedBeneficiaries; }
        public void setModifiedBeneficiaries(List<Map<String, Object>> modifiedBeneficiaries) { this.modifiedBeneficiaries = modifiedBeneficiaries; }
        
        public List<Map<String, Object>> getNewBeneficiaries() { return newBeneficiaries; }
        public void setNewBeneficiaries(List<Map<String, Object>> newBeneficiaries) { this.newBeneficiaries = newBeneficiaries; }
    }
}