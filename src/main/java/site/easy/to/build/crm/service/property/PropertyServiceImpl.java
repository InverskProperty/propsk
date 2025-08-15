package site.easy.to.build.crm.service.property;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.entity.Customer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
public class PropertyServiceImpl implements PropertyService {

    private final PropertyRepository propertyRepository;
    private final CustomerPropertyAssignmentRepository assignmentRepository;

    @Autowired
    public PropertyServiceImpl(PropertyRepository propertyRepository,
                            CustomerPropertyAssignmentRepository assignmentRepository) {
        this.propertyRepository = propertyRepository;
        this.assignmentRepository = assignmentRepository;
    }

    // ✅ Core CRUD operations
    @Override
    public Property findById(Long id) {
        return propertyRepository.findById(id).orElse(null);
    }

    @Override
    public List<Property> findAll() {
        return propertyRepository.findAll();
    }

    @Override
    public Property save(Property property) {
        return propertyRepository.save(property);
    }

    @Override
    public Property getPropertyById(Long propertyId) {
        return propertyRepository.findById(propertyId).orElse(null);
    }

    @Override
    public void delete(Property property) {
        propertyRepository.delete(property);
    }

    @Override
    public Customer getCurrentTenant(Long propertyId) {
        try {
            // Use existing repository method to find tenant assignments
            List<CustomerPropertyAssignment> tenantAssignments = 
                assignmentRepository.findByPropertyIdAndAssignmentType(propertyId, AssignmentType.TENANT);
            
            // Filter for active tenants (no end date or end date in future)
            return tenantAssignments.stream()
                .filter(assignment -> assignment.getEndDate() == null || 
                                    assignment.getEndDate().isAfter(LocalDate.now()))
                .filter(assignment -> assignment.getStartDate() == null || 
                                    assignment.getStartDate().isBefore(LocalDate.now()) ||
                                    assignment.getStartDate().equals(LocalDate.now()))
                .map(CustomerPropertyAssignment::getCustomer)
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            System.err.println("Error finding tenant for property " + propertyId + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void deleteById(Long id) {
        propertyRepository.deleteById(id);
    }

    // ✅ PayProp integration methods
    @Override
    public Optional<Property> findByPayPropId(String payPropId) {
        return propertyRepository.findByPayPropId(payPropId);
    }

    @Override
    public Optional<Property> findByCustomerId(String customerId) {
        return propertyRepository.findByCustomerId(customerId);
    }

    @Override
    public List<Property> findByPropertyOwnerId(Long propertyOwnerId) {
        try {
            System.out.println("🔍 [PropertyService] Looking for properties owned by customer ID: " + propertyOwnerId);
            
            // Query the junction table
            List<CustomerPropertyAssignment> assignments = 
                assignmentRepository.findByCustomerCustomerIdAndAssignmentType(
                    propertyOwnerId, AssignmentType.OWNER);
            
            System.out.println("📊 [PropertyService] Found " + assignments.size() + " assignments in junction table");
            
            if (!assignments.isEmpty()) {
                // Debug each assignment
                for (CustomerPropertyAssignment assignment : assignments) {
                    Property prop = assignment.getProperty();
                    System.out.println("  - Assignment ID " + assignment.getId() + 
                                    ": Property " + (prop != null ? prop.getId() + " - " + prop.getPropertyName() : "NULL"));
                }
                
                List<Property> properties = assignments.stream()
                    .map(assignment -> {
                        Property p = assignment.getProperty();
                        if (p == null) {
                            System.out.println("    ⚠️ NULL property in assignment!");
                        }
                        return p;
                    })
                    .filter(property -> {
                        if (property == null) {
                            System.out.println("    ❌ Filtering out NULL property");
                            return false;
                        }
                        boolean archived = "Y".equals(property.getIsArchived());
                        if (archived) {
                            System.out.println("    ❌ Filtering out archived property: " + property.getPropertyName());
                        }
                        return !archived;
                    })
                    .distinct()
                    .collect(Collectors.toList());
                
                System.out.println("✅ [PropertyService] Returning " + properties.size() + " properties after filtering");
                return properties;
            }
            
            System.out.println("❌ [PropertyService] No assignments found for customer " + propertyOwnerId);
            return new ArrayList<>();
            
        } catch (Exception e) {
            System.err.println("💥 [PropertyService] Error: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // ✅ Junction table-based property-owner relationships
    @Override
    public List<Property> getPropertiesByOwner(Long ownerId) {
        try {
            var assignments = assignmentRepository.findByCustomerCustomerIdAndAssignmentType(
                ownerId, AssignmentType.OWNER);
            
            return assignments.stream()
                .map(assignment -> assignment.getProperty())
                .distinct()
                .toList();
        } catch (Exception e) {
            // Fallback to legacy method if junction table not available
            return propertyRepository.findByPropertyOwnerId(ownerId);
        }
    }

    // Add this to PropertyServiceImpl.java
    @Override
    public List<Property> findPropertiesWithNoPortfolioAssignments() {
        return propertyRepository.findPropertiesWithNoPortfolioAssignments();
    }

    @Override
    public Property getPropertyByTenant(Long tenantId) {
        try {
            var assignments = assignmentRepository.findByCustomerCustomerIdAndAssignmentType(
                tenantId, AssignmentType.TENANT);
            
            return assignments.stream()
                .map(assignment -> assignment.getProperty())
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ✅ Portfolio and Block methods - UPDATED for junction table approach
    @Override
    public List<Property> findByPortfolioId(Long portfolioId) {
        // ❌ DISABLED: Direct portfolio queries - now handled by PropertyPortfolioAssignment table
        // return propertyRepository.findByPortfolioId(portfolioId);
        throw new UnsupportedOperationException("Use PropertyPortfolioAssignmentRepository.findPropertiesForPortfolio() instead");
    }

    @Override
    public List<Property> findByBlockId(Long blockId) {
        return propertyRepository.findByBlockId(blockId);
    }

    @Override
    public List<Property> findActivePropertiesByPortfolio(Long portfolioId) {
        return propertyRepository.findActivePropertiesByPortfolio(portfolioId);
    }

    @Override
    public List<Property> findActivePropertiesByBlock(Long blockId) {
        return propertyRepository.findActivePropertiesByBlock(blockId);
    }

    @Override
    public List<Property> findUnassignedProperties() {
        return propertyRepository.findUnassignedProperties();
    }

    @Override
    public void assignPropertyToPortfolio(Long propertyId, Long portfolioId, Long assignedBy) {
        Property property = findById(propertyId);
        if (property != null) {
            property.setPortfolioAssignmentDate(LocalDateTime.now());
            save(property);
        }
    }

    @Override
    public void removePropertyFromPortfolio(Long propertyId, Long removedBy) {
        // ❌ DISABLED: Direct portfolio assignment - now handled by PropertyPortfolioAssignment table
        // Property property = findById(propertyId);
        // if (property != null) {
        //     property.setPortfolio(null);
        //     property.setPortfolioAssignmentDate(null);
        //     save(property);
        // }
        
        // ✅ TODO: This method should be deprecated and replaced with PortfolioAssignmentService.removeAssignment()
        throw new UnsupportedOperationException("Use PortfolioAssignmentService.removeAssignment() instead");
    }

    @Override
    public void assignPropertyToBlock(Long propertyId, Long blockId, Long assignedBy) {
        Property property = findById(propertyId);
        if (property != null) {
            property.setBlockAssignmentDate(LocalDateTime.now());
            save(property);
        }
    }

    @Override
    public void removePropertyFromBlock(Long propertyId, Long removedBy) {
        Property property = findById(propertyId);
        if (property != null) {
            property.setBlock(null);
            property.setBlockAssignmentDate(null);
            save(property);
        }
    }

    @Override
    public long countPropertiesByPortfolio(Long portfolioId) {
        // ❌ DISABLED: Direct portfolio count - now handled by PropertyPortfolioAssignment table
        // return propertyRepository.countByPortfolioId(portfolioId);
        throw new UnsupportedOperationException("Use PropertyPortfolioAssignmentRepository.countPropertiesForPortfolio() instead");
    }

    @Override
    public long countPropertiesByBlock(Long blockId) {
        return propertyRepository.countByBlockId(blockId);
    }

    // ✅ Property characteristics
    @Override
    public List<Property> findByPropertyType(String propertyType) {
        return propertyRepository.findByPropertyType(propertyType);
    }

    @Override
    public List<Property> findByCity(String city) {
        return propertyRepository.findByCity(city);
    }

    @Override
    public List<Property> findByPostalCode(String postalCode) {
        return propertyRepository.findByPostcode(postalCode);
    }

    @Override
    public List<Property> findPropertiesByBedrooms(Integer bedrooms) {
        return propertyRepository.findByBedrooms(bedrooms);
    }

    @Override
    public List<Property> findPropertiesByFurnished(String furnished) {
        return propertyRepository.findByFurnished(furnished);
    }

    // ✅ Search methods
    @Override
    public List<Property> searchByPropertyName(String propertyName) {
        return propertyRepository.findByPropertyNameContainingIgnoreCase(propertyName);
    }

    @Override
    public List<Property> searchByAddress(String address) {
        return propertyRepository.findByFullAddressContaining(address);
    }

    @Override
    public List<Property> getRecentProperties(Long userId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return propertyRepository.findByCreatedByOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public List<Property> searchProperties(String propertyName, String city, String postalCode,
                                          Boolean isArchived, String propertyType, Integer bedrooms, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        String archivedString = null;
        if (isArchived != null) {
            archivedString = isArchived ? "Y" : "N";
        }
        return propertyRepository.searchProperties(propertyName, city, postalCode, archivedString, propertyType, bedrooms, pageable);
    }

    // ✅ Date-based queries
    @Override
    public List<Property> findPropertiesWithUpcomingExpiry(LocalDate date) {
        return propertyRepository.findPropertiesWithUpcomingExpiry(date);
    }

    // ✅ User-based queries
    @Override
    public long countByCreatedBy(Long userId) {
        return propertyRepository.countByCreatedBy(userId);
    }

    @Override
    public long getTotalProperties() {
        return propertyRepository.count();
    }

    // ✅ Status methods
    @Override
    public List<Property> findActiveProperties() {
        return propertyRepository.findByIsArchivedOrderByCreatedAtDesc("N");
    }

    @Override
    public List<Property> findPropertiesWithPaymentsEnabled() {
        return propertyRepository.findByEnablePayments("Y");
    }

    // 🔧 FIXED: Junction table-based occupancy detection (WORKING - Returns 252 occupied properties)
    @Override
    public List<Property> findOccupiedProperties() {
        try {
            List<Property> occupied = propertyRepository.findOccupiedProperties();
            System.out.println("DEBUG: Found " + occupied.size() + " occupied properties using junction table");
            return occupied;
        } catch (Exception e) {
            System.err.println("Error finding occupied properties using junction table: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // 🔧 FIXED: Junction table-based vacancy detection (WORKING - Returns 11 vacant properties)
    @Override
    public List<Property> findVacantProperties() {
        try {
            List<Property> vacant = propertyRepository.findVacantProperties();
            System.out.println("DEBUG: Found " + vacant.size() + " vacant properties using junction table");
            return vacant;
        } catch (Exception e) {
            System.err.println("Error finding vacant properties using junction table: " + e.getMessage());
            return propertyRepository.findByIsArchivedOrderByCreatedAtDesc("N");
        }
    }

    // 🔧 FIXED: Junction table-based availability check
    @Override
    public boolean isPropertyAvailableForTenant(Long propertyId) {
        try {
            return propertyRepository.hasNoActiveTenantsById(propertyId);
        } catch (Exception e) {
            System.err.println("Error checking property availability: " + e.getMessage());
            return true;
        }
    }

    // ✅ Archive methods
    @Override
    public void archiveProperty(Long propertyId) {
        Property property = findById(propertyId);
        if (property != null) {
            property.setIsArchived("Y");
            save(property);
        }
    }

    @Override
    public void unarchiveProperty(Long propertyId) {
        Property property = findById(propertyId);
        if (property != null) {
            property.setIsArchived("N");
            save(property);
        }
    }

    @Override
    public long countArchivedProperties() {
        return propertyRepository.countByIsArchived("Y");
    }

    @Override
    public long countActiveProperties() {
        return propertyRepository.countByIsArchived("N");
    }

    // ✅ PayProp sync methods
    @Override
    public List<Property> findPropertiesNeedingSync() {
        return propertyRepository.findByPayPropIdIsNullAndIsArchivedFalse();
    }

    @Override
    public void markPropertyAsSynced(Long propertyId, String payPropId) {
        Property property = findById(propertyId);
        if (property != null) {
            property.setPayPropId(payPropId);
            save(property);
        }
    }

    @Override
    public List<Property> findPropertiesByPayPropSyncStatus(boolean synced) {
        return synced ? propertyRepository.findByPayPropIdIsNotNull() : propertyRepository.findByPayPropIdIsNull();
    }

    @Override
    public List<Property> findArchivedProperties() {
        return propertyRepository.findByIsArchivedOrderByCreatedAtDesc("Y");
    }

    // ✅ PayProp validation methods
    @Override
    public List<Property> findPropertiesReadyForSync() {
        return propertyRepository.findPropertiesReadyForSync();
    }

    @Override
    public boolean isPropertyReadyForPayPropSync(Long propertyId) {
        Property property = findById(propertyId);
        return property != null && property.isReadyForPayPropSync();
    }

    @Override
    public List<Property> findPropertiesWithMissingPayPropFields() {
        return propertyRepository.findPropertiesWithMissingPayPropFields();
    }

    @Override
    public List<Property> findByPayPropIdIsNull() {
        return propertyRepository.findByPayPropIdIsNull();
    }

    // ✅ Debug method to verify assignments
    public void debugPropertyAssignments() {
        try {
            List<Object[]> results = propertyRepository.findPropertyAssignmentCounts();
            System.out.println("=== PROPERTY ASSIGNMENT DEBUG ===");
            System.out.println("Property ID | Property Name | Active Tenants | Active Owners");
            
            int occupiedCount = 0;
            int vacantCount = 0;
            
            for (Object[] row : results) {
                int activeTenants = ((Number) row[2]).intValue();
                if (activeTenants > 0) {
                    occupiedCount++;
                } else {
                    vacantCount++;
                }
                System.out.printf("%s | %s | %s | %s%n", row[0], row[1], row[2], row[3]);
            }
            
            System.out.println("=== SUMMARY ===");
            System.out.println("Occupied Properties: " + occupiedCount);
            System.out.println("Vacant Properties: " + vacantCount);
            System.out.println("=== END DEBUG ===");
        } catch (Exception e) {
            System.err.println("Debug query failed: " + e.getMessage());
        }
    }
}