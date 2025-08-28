package site.easy.to.build.crm.service.property;

import site.easy.to.build.crm.entity.Property;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import site.easy.to.build.crm.entity.Customer;

public interface PropertyService {

    // Core CRUD operations
    Property findById(Long id);
    List<Property> findAll();
    Property save(Property property);
    void delete(Property property);
    void deleteById(Long id);
    Property getPropertyById(Long propertyId);
    Customer getCurrentTenant(Long propertyId);
    
    // PayProp-specific methods  
    default Property findByPayPropIdString(String payPropId) { return null; }

    // PayProp integration methods
    Optional<Property> findByPayPropId(String payPropId);
    Optional<Property> findByCustomerId(String customerId);
    List<Property> findByPropertyOwnerId(Long propertyOwnerId);

    // FIXED: Added missing methods that were causing compilation errors
    List<Property> getPropertiesByOwner(Long ownerId);
    Property getPropertyByTenant(Long tenantId);
    
    // Property characteristics (PayProp compatible)
    List<Property> findByPropertyType(String propertyType);
    List<Property> findByCity(String city);
    List<Property> findByPostalCode(String postalCode); // Updated field name
    List<Property> findPropertiesByBedrooms(Integer bedrooms);
    List<Property> findPropertiesByFurnished(String furnished);

    // Portfolio and Block relationships
    List<Property> findActivePropertiesByPortfolio(Long portfolioId);
    List<Property> findByBlockId(Long blockId);
    List<Property> findActivePropertiesByBlock(Long blockId);
    List<Property> findUnassignedProperties();
    
    // Search methods (PayProp compatible)
    List<Property> searchByPropertyName(String propertyName);
    List<Property> searchByAddress(String address);
    List<Property> getRecentProperties(Long userId, int limit);
    
    // Updated search method - PayProp compatible
    List<Property> searchProperties(String propertyName, String city, String postalCode, 
                                   Boolean isArchived, String propertyType, Integer bedrooms, int limit);
    
    // Date-based queries
    List<Property> findPropertiesWithUpcomingExpiry(LocalDate date);
    
    // User-based queries
    long countByCreatedBy(Long userId);
    
    // Keep existing method name (don't add duplicate)
    long getTotalProperties();

    // PayProp compatible status methods (replace old status-based logic)
    List<Property> findActiveProperties(); // Uses isArchived = false
    List<Property> findPropertiesWithPaymentsEnabled(); // Uses enablePayments field
    List<Property> findVacantProperties(); // Properties without active tenants
    List<Property> findOccupiedProperties(); // Properties with active tenants
    
    // Status-based availability
    boolean isPropertyAvailableForTenant(Long propertyId);
    
    // PayProp-based occupancy check
    boolean isPropertyOccupied(String payPropId);
    
    // Archive logic
    void archiveProperty(Long propertyId);
    void unarchiveProperty(Long propertyId);
    
    // Count methods
    long countArchivedProperties();
    long countActiveProperties();

    // Block assignment (portfolio assignment now handled by PropertyPortfolioAssignmentService)
    void assignPropertyToBlock(Long propertyId, Long blockId, Long assignedBy);
    void removePropertyFromBlock(Long propertyId, Long removedBy);

    // Analytics support
    long countPropertiesByBlock(Long blockId);
    
    // PayProp sync methods
    List<Property> findPropertiesNeedingSync();
    void markPropertyAsSynced(Long propertyId, String payPropId);
    List<Property> findPropertiesByPayPropSyncStatus(boolean synced);
    List<Property> findArchivedProperties();
    
    // PayProp validation methods
    List<Property> findPropertiesReadyForSync();
    boolean isPropertyReadyForPayPropSync(Long propertyId);
    List<Property> findPropertiesWithMissingPayPropFields();

    List<Property> findByPayPropIdIsNull();

    List<Property> findPropertiesWithNoPortfolioAssignments();
}