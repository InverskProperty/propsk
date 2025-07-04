package site.easy.to.build.crm.service.property;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.PropertyRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PropertyServiceImpl implements PropertyService {

    private final PropertyRepository propertyRepository;

    @Autowired
    public PropertyServiceImpl(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    // Core CRUD operations
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
    public void delete(Property property) {
        propertyRepository.delete(property);
    }

    @Override
    public void deleteById(Long id) {
        propertyRepository.deleteById(id);
    }

    // PayProp integration methods
    @Override
    public Optional<Property> findByPayPropId(String payPropId) {
        return propertyRepository.findByPayPropId(payPropId);
    }

    @Override
    public Optional<Property> findByCustomerId(String customerId) {
        return propertyRepository.findByCustomerId(customerId);
    }

    @Override
    public List<Property> findByPropertyOwnerId(Integer propertyOwnerId) {
        return propertyRepository.findByPropertyOwnerId(propertyOwnerId);
    }

    @Override
    public List<Property> findByPortfolioId(Long portfolioId) {
        return propertyRepository.findByPortfolioId(portfolioId);
    }

    // FIXED: Removed @Override annotation since this method is not in the interface
    public long getTotalCount() {
        return propertyRepository.count();
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
            // You'll need to inject PortfolioRepository to get the portfolio
            // Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
            // property.setPortfolio(portfolio);
            property.setPortfolioAssignmentDate(LocalDateTime.now());
            save(property);
        }
    }

    @Override
    public void removePropertyFromPortfolio(Long propertyId, Long removedBy) {
        Property property = findById(propertyId);
        if (property != null) {
            property.setPortfolio(null);
            property.setPortfolioAssignmentDate(null);
            save(property);
        }
    }

    @Override
    public void assignPropertyToBlock(Long propertyId, Long blockId, Long assignedBy) {
        Property property = findById(propertyId);
        if (property != null) {
            // You'll need to inject BlockRepository to get the block
            // Block block = blockRepository.findById(blockId).orElse(null);
            // property.setBlock(block);
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
        return propertyRepository.countByPortfolioId(portfolioId);
    }

    @Override
    public long countPropertiesByBlock(Long blockId) {
        return propertyRepository.countByBlockId(blockId);
    }

    // Property characteristics
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

    // Search methods
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
        // Convert Boolean to String for database query
        String archivedString = null;
        if (isArchived != null) {
            archivedString = isArchived ? "Y" : "N";
        }
        return propertyRepository.searchProperties(propertyName, city, postalCode, archivedString, propertyType, bedrooms, pageable);
    }

    // Date-based queries
    @Override
    public List<Property> findPropertiesWithUpcomingExpiry(LocalDate date) {
        return propertyRepository.findPropertiesWithUpcomingExpiry(date);
    }

    // User-based queries
    @Override
    public long countByCreatedBy(Long userId) {
        return propertyRepository.countByCreatedBy(userId);
    }

    @Override
    public long getTotalProperties() {
        return propertyRepository.count();
    }

    // PayProp compatible status methods - FIXED BOOLEAN TO STRING CONVERSIONS
    @Override
    public List<Property> findActiveProperties() {
        // Fixed: Pass "N" string instead of false boolean
        return propertyRepository.findByIsArchivedOrderByCreatedAtDesc("N");
    }

    @Override
    public List<Property> findPropertiesWithPaymentsEnabled() {
        // Fixed: Pass "Y" string instead of true boolean
        return propertyRepository.findByEnablePayments("Y");
    }

    @Override
    public List<Property> findVacantProperties() {
        return propertyRepository.findVacantProperties();
    }

    @Override
    public List<Property> findOccupiedProperties() {
        return propertyRepository.findOccupiedProperties();
    }

    @Override
    public boolean isPropertyAvailableForTenant(Long propertyId) {
        return propertyRepository.hasNoActiveTenants(propertyId);
    }

    @Override
    public void archiveProperty(Long propertyId) {
        Property property = findById(propertyId);
        if (property != null) {
            // Fixed: Use proper string setter instead of trying to pass boolean
            property.setIsArchived("Y");
            save(property);
        }
    }

    @Override
    public void unarchiveProperty(Long propertyId) {
        Property property = findById(propertyId);
        if (property != null) {
            // Fixed: Use proper string setter instead of trying to pass boolean
            property.setIsArchived("N");
            save(property);
        }
    }

    @Override
    public long countArchivedProperties() {
        // Fixed: Pass "Y" string instead of true boolean
        return propertyRepository.countByIsArchived("Y");
    }

    @Override
    public long countActiveProperties() {
        // Fixed: Pass "N" string instead of false boolean
        return propertyRepository.countByIsArchived("N");
    }

    // PayProp sync methods
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
        // Fixed: Pass "Y" string instead of true boolean
        return propertyRepository.findByIsArchivedOrderByCreatedAtDesc("Y");
    }

    // PayProp validation methods
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
}