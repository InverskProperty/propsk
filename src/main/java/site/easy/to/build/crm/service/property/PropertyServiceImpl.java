package site.easy.to.build.crm.service.property;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.DataSource;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.entity.Customer;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    private final JdbcTemplate jdbcTemplate;
    
    @Value("${crm.data.source:LEGACY}")
    private String dataSource;

    @Autowired
    public PropertyServiceImpl(PropertyRepository propertyRepository,
                            CustomerPropertyAssignmentRepository assignmentRepository,
                            JdbcTemplate jdbcTemplate) {
        this.propertyRepository = propertyRepository;
        this.assignmentRepository = assignmentRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ✅ Core CRUD operations
    @Override
    public Property findById(Long id) {
        // FIXED: Always use database lookup for findById() - database IDs are the primary identifiers
        // PayProp data integration should be handled separately, not interfere with basic CRUD operations
        return propertyRepository.findById(id).orElse(null);
    }
    
    // Override method to find by PayProp ID directly
    @Override
    public Property findByPayPropIdString(String payPropId) {
        // FIXED: Always return the real database Property entity, not a fake constructed one
        System.out.println("🔍 Looking up property by PayProp ID: " + payPropId);
        
        Property property = propertyRepository.findByPayPropId(payPropId).orElse(null);
        
        if (property != null) {
            System.out.println("✅ Found real database property: ID=" + property.getId() + ", Name=" + property.getPropertyName());
        } else {
            System.out.println("❌ No database property found for PayProp ID: " + payPropId);
        }
        
        return property;
    }

    @Override
    public List<Property> findAll() {
        // FIXED: Always use database properties for findAll() - ensures consistent IDs across the system
        // PayProp data should enhance, not replace, the core property data
        return propertyRepository.findAll();
    }
    
    private List<Property> findAllFromPayProp() {
        String sql = """
            SELECT 
                pep.payprop_id,
                -- Create property name from address if name is null
                COALESCE(
                    NULLIF(TRIM(pep.name), ''),
                    CONCAT(pep.address_first_line, 
                           CASE WHEN pep.address_city IS NOT NULL THEN CONCAT(', ', pep.address_city) ELSE '' END)
                ) as property_name,
                pep.address_first_line,
                pep.address_second_line,
                pep.address_third_line,
                pep.address_city,
                pep.address_state,
                pep.address_country_code,
                pep.address_postal_code,
                pep.settings_monthly_payment,
                pep.commission_percentage,
                pep.commission_amount,
                pep.is_archived,
                pep.sync_status,
                pep.imported_at,
                
                -- Current tenant information
                tenant.tenant_name,
                tenant.tenant_email,
                tenant.tenancy_start_date,
                tenant.tenancy_end_date,
                tenant.monthly_rent_amount as tenant_rent,
                
                -- Recent rent from ICDN invoices (last 3 months)
                COALESCE(rent_recent.recent_rent, tenant.monthly_rent_amount, pep.settings_monthly_payment, 0) as monthly_payment,
                
                -- Account balance from properties table
                COALESCE(pep.balance_amount, 0) as account_balance
                
            FROM payprop_export_properties pep
            
            -- Current active tenant
            LEFT JOIN (
                SELECT 
                    current_property_id,
                    CONCAT(first_name, ' ', last_name) as tenant_name,
                    email as tenant_email,
                    tenancy_start_date,
                    tenancy_end_date,
                    monthly_rent_amount
                FROM payprop_export_tenants_complete 
                WHERE tenant_status = 'active'
            ) tenant ON pep.payprop_id = tenant.current_property_id
            
            -- Recent rent calculation (last 3 months)
            LEFT JOIN (
                SELECT 
                    property_payprop_id, 
                    AVG(amount) as recent_rent
                FROM payprop_report_icdn 
                WHERE category_name = 'Rent' 
                AND transaction_date >= DATE_SUB(NOW(), INTERVAL 3 MONTH)
                GROUP BY property_payprop_id
            ) rent_recent ON pep.payprop_id = rent_recent.property_payprop_id
            
            ORDER BY pep.name
            """;
        
        try {
            return jdbcTemplate.query(sql, new PayPropPropertyRowMapper());
        } catch (Exception e) {
            System.err.println("Error querying PayProp properties, falling back to legacy data: " + e.getMessage());
            return propertyRepository.findAll();
        }
    }
    
    private static class PayPropPropertyRowMapper implements RowMapper<Property> {
        @Override
        public Property mapRow(ResultSet rs, int rowNum) throws SQLException {
            Property property = new Property();
            
            // Basic property info
            property.setPayPropId(rs.getString("payprop_id"));
            property.setPropertyName(rs.getString("property_name"));
            property.setAddressLine1(rs.getString("address_first_line"));
            property.setAddressLine2(rs.getString("address_second_line"));
            property.setAddressLine3(rs.getString("address_third_line"));
            property.setCity(rs.getString("address_city"));
            property.setState(rs.getString("address_state"));
            property.setCountryCode(rs.getString("address_country_code"));
            property.setPostcode(rs.getString("address_postal_code"));
            
            // Financial information
            BigDecimal monthlyPayment = rs.getBigDecimal("monthly_payment");
            if (monthlyPayment != null) {
                property.setMonthlyPayment(monthlyPayment);
            }
            
            BigDecimal accountBalance = rs.getBigDecimal("account_balance");
            if (accountBalance != null) {
                property.setAccountBalance(accountBalance);
            }
            
            BigDecimal commissionAmount = rs.getBigDecimal("commission_amount");
            if (commissionAmount != null) {
                property.setCommissionAmount(commissionAmount);
            }
            
            BigDecimal commissionPercentage = rs.getBigDecimal("commission_percentage");
            if (commissionPercentage != null) {
                property.setCommissionPercentage(commissionPercentage);
            }
            
            // Status flags
            boolean isArchived = rs.getBoolean("is_archived");
            property.setIsArchived(isArchived ? "Y" : "N");
            
            // Timestamps
            if (rs.getTimestamp("imported_at") != null) {
                property.setCreatedAt(rs.getTimestamp("imported_at").toLocalDateTime());
                property.setUpdatedAt(rs.getTimestamp("imported_at").toLocalDateTime());
            }
            
            // Generate a fake ID for compatibility (using hash of PayProp ID)
            property.setId((long) rs.getString("payprop_id").hashCode());
            
            return property;
        }
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
                .filter(property -> property != null)
                .distinct()
                .toList();
        } catch (Exception e) {
            System.err.println("❌ Failed to get properties for owner " + ownerId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<Property> findPropertiesOwnedByCustomer(Long customerId) {
        try {
            System.out.println("🔍 [PropertyService] Finding properties owned by Customer ID: " + customerId + " (using assignments)");
            
            // Use the assignment repository to find properties owned by this customer
            List<CustomerPropertyAssignment> assignments = 
                assignmentRepository.findByCustomerCustomerIdAndAssignmentType(
                    customerId, AssignmentType.OWNER);
            
            System.out.println("📊 [PropertyService] Found " + assignments.size() + " ownership assignments");
            
            // Extract the properties from the assignments
            List<Property> properties = assignments.stream()
                .map(assignment -> assignment.getProperty())
                .filter(property -> property != null) // Safety check
                .distinct()
                .collect(Collectors.toList());
                
            System.out.println("✅ [PropertyService] Returning " + properties.size() + " unique properties for customer " + customerId);
            return properties;
            
        } catch (Exception e) {
            System.err.println("❌ [PropertyService] Error finding properties for customer " + customerId + ": " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
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


    @Override
    public List<Property> findByBlockId(Long blockId) {
        return propertyRepository.findByBlockId(blockId);
    }

    @Override
    @Deprecated // Use PortfolioService.getPropertiesForPortfolio() instead
    public List<Property> findActivePropertiesByPortfolio(Long portfolioId) {
        // This method is deprecated - portfolio relationships now use junction table
        // Use PortfolioService.getPropertiesForPortfolio() instead
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
        if ("PAYPROP".equals(dataSource)) {
            try {
                return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payprop_export_properties", 
                    Long.class);
            } catch (Exception e) {
                System.err.println("Error counting PayProp properties: " + e.getMessage());
                return propertyRepository.count();
            }
        }
        return propertyRepository.count();
    }

    // ✅ Status methods
    @Override
    public List<Property> findActiveProperties() {
        if ("PAYPROP".equals(dataSource)) {
            try {
                // Get PayProp synced properties
                List<Property> payPropProperties = findAllFromPayProp().stream()
                    .filter(p -> !"Y".equals(p.getIsArchived()))
                    .collect(Collectors.toList());
                
                System.out.println("DEBUG: Found " + payPropProperties.size() + " PayProp properties");
                
                // Get local CRM properties that haven't been synced to PayProp yet
                List<Property> localOnlyProperties = propertyRepository.findByPayPropIdIsNullAndIsArchivedFalse();
                
                System.out.println("DEBUG: Found " + localOnlyProperties.size() + " local-only properties");
                
                // Combine both lists - PayProp properties first, then local properties
                List<Property> allProperties = new ArrayList<>(payPropProperties);
                allProperties.addAll(localOnlyProperties);
                
                System.out.println("DEBUG: Total active properties: " + allProperties.size());
                return allProperties;
                
            } catch (Exception e) {
                System.err.println("ERROR: Failed to get PayProp properties, falling back to local only: " + e.getMessage());
                return propertyRepository.findByIsArchivedOrderByCreatedAtDesc("N");
            }
        }
        return propertyRepository.findByIsArchivedOrderByCreatedAtDesc("N");
    }

    @Override
    public List<Property> findPropertiesWithPaymentsEnabled() {
        return propertyRepository.findByEnablePayments("Y");
    }

    // 🔧 UPDATED: PayProp-based occupancy detection using active rent instructions
    @Override
    public List<Property> findOccupiedProperties() {
        try {
            if ("PAYPROP".equals(dataSource)) {
                // Use PayProp data: properties with active rent instructions
                String sql = """
                    SELECT DISTINCT p.* FROM properties p
                    INNER JOIN payprop_export_properties pep ON p.payprop_id = pep.payprop_id
                    WHERE pep.is_archived = 0
                    AND EXISTS (
                        SELECT 1 FROM payprop_export_invoices pei 
                        WHERE pei.property_payprop_id = pep.payprop_id 
                        AND pei.invoice_type = 'Rent'
                        AND pei.sync_status = 'active'
                    )
                    ORDER BY p.created_at DESC
                    """;
                
                List<Property> occupied = jdbcTemplate.query(sql, (rs, rowNum) -> {
                    Property property = new Property();
                    property.setId(rs.getLong("id"));
                    property.setPropertyName(rs.getString("property_name"));
                    property.setAddressLine1(rs.getString("address_line_1"));
                    property.setCity(rs.getString("city"));
                    property.setPostcode(rs.getString("postcode"));
                    property.setPayPropId(rs.getString("payprop_id"));
                    property.setMonthlyPayment(rs.getBigDecimal("monthly_payment"));
                    property.setIsArchived(rs.getString("is_archived"));
                    return property;
                });
                
                System.out.println("DEBUG: Found " + occupied.size() + " occupied properties using PayProp rent instructions");
                return occupied;
            } else {
                // Hybrid approach: PayProp properties + Legacy properties
                List<Property> payPropOccupied = propertyRepository.findOccupiedProperties();
                List<Property> legacyOccupied = propertyRepository.findLegacyOccupiedProperties();
                
                List<Property> allOccupied = new ArrayList<>(payPropOccupied);
                allOccupied.addAll(legacyOccupied);
                
                System.out.println("DEBUG: Found " + payPropOccupied.size() + " PayProp occupied + " + 
                                 legacyOccupied.size() + " legacy occupied = " + allOccupied.size() + " total occupied properties");
                return allOccupied;
            }
        } catch (Exception e) {
            System.err.println("Error finding occupied properties: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // 🔧 UPDATED: PayProp-based vacancy detection using absence of active rent instructions
    @Override
    public List<Property> findVacantProperties() {
        try {
            if ("PAYPROP".equals(dataSource)) {
                // Use PayProp data: active properties without rent instructions
                String sql = """
                    SELECT DISTINCT p.* FROM properties p
                    INNER JOIN payprop_export_properties pep ON p.payprop_id = pep.payprop_id
                    WHERE pep.is_archived = 0
                    AND NOT EXISTS (
                        SELECT 1 FROM payprop_export_invoices pei 
                        WHERE pei.property_payprop_id = pep.payprop_id 
                        AND pei.invoice_type = 'Rent'
                        AND pei.sync_status = 'active'
                    )
                    ORDER BY p.created_at DESC
                    """;
                
                List<Property> vacant = jdbcTemplate.query(sql, (rs, rowNum) -> {
                    Property property = new Property();
                    property.setId(rs.getLong("id"));
                    property.setPropertyName(rs.getString("property_name"));
                    property.setAddressLine1(rs.getString("address_line_1"));
                    property.setCity(rs.getString("city"));
                    property.setPostcode(rs.getString("postcode"));
                    property.setPayPropId(rs.getString("payprop_id"));
                    property.setMonthlyPayment(rs.getBigDecimal("monthly_payment"));
                    property.setIsArchived(rs.getString("is_archived"));
                    return property;
                });
                
                System.out.println("DEBUG: Found " + vacant.size() + " vacant properties using PayProp rent instructions");
                return vacant;
            } else {
                // Hybrid approach: PayProp properties + Legacy properties
                List<Property> payPropVacant = propertyRepository.findVacantProperties();
                List<Property> legacyVacant = propertyRepository.findLegacyVacantProperties();
                
                List<Property> allVacant = new ArrayList<>(payPropVacant);
                allVacant.addAll(legacyVacant);
                
                System.out.println("DEBUG: Found " + payPropVacant.size() + " PayProp vacant + " + 
                                 legacyVacant.size() + " legacy vacant = " + allVacant.size() + " total vacant properties");
                return allVacant;
            }
        } catch (Exception e) {
            System.err.println("Error finding vacant properties: " + e.getMessage());
            e.printStackTrace();
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
    
    // ✅ PayProp-based occupancy check - ACCURATE
    @Override
    public boolean isPropertyOccupied(String payPropId) {
        if (payPropId == null || payPropId.trim().isEmpty()) {
            return false;
        }
        
        try {
            if ("PAYPROP".equals(dataSource)) {
                // Use PayProp export_invoices for accurate occupancy status
                String sql = """
                    SELECT COUNT(*) > 0 
                    FROM payprop_export_invoices pei 
                    WHERE pei.property_payprop_id = ?
                    AND pei.invoice_type = 'Rent'
                    AND pei.sync_status = 'active'
                    """;
                
                return jdbcTemplate.queryForObject(sql, Boolean.class, payPropId);
            } else {
                // Fallback to status-based logic for non-PayProp properties
                Optional<Property> property = findByPayPropId(payPropId);
                return property.map(p -> {
                    String status = p.getStatus();
                    if (status != null) {
                        return "occupied".equalsIgnoreCase(status) || 
                               "rented".equalsIgnoreCase(status) || 
                               "let".equalsIgnoreCase(status);
                    }
                    // Additional fallback: if property is active and has monthly payment, likely occupied
                    return p.isActive() && p.getMonthlyPayment() != null && 
                           p.getMonthlyPayment().compareTo(java.math.BigDecimal.ZERO) > 0;
                }).orElse(false);
            }
        } catch (Exception e) {
            System.err.println("Error checking PayProp occupancy for property " + payPropId + ": " + e.getMessage());
            return false;
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