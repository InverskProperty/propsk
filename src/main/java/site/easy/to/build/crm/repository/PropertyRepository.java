package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Property;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {
    
    // ✅ PayProp integration methods
    Optional<Property> findByPayPropId(String payPropId);
    Optional<Property> findByCustomerId(String customerId);
    List<Property> findByPropertyOwnerId(Long propertyOwnerId);
    
    // ✅ Archive status methods (String-based)
    List<Property> findByIsArchivedOrderByCreatedAtDesc(String isArchived);
    List<Property> findByEnablePayments(String enablePayments);
    long countByIsArchived(String isArchived);

    // ✅ Portfolio and Block relationship queries
    List<Property> findByPortfolioId(Long portfolioId);
    List<Property> findByBlockId(Long blockId);
    List<Property> findByPortfolioIdAndIsArchived(Long portfolioId, String isArchived);
    List<Property> findByBlockIdAndIsArchived(Long blockId, String isArchived);
    
    // ✅ Property characteristics (PayProp compatible)
    List<Property> findByPropertyType(String propertyType);
    List<Property> findByCity(String city);
    List<Property> findByPostcode(String postcode);
    List<Property> findByBedrooms(Integer bedrooms);
    List<Property> findByFurnished(String furnished);
    List<Property> findByTagsContaining(String tag);
    
    // ✅ Search by property name (case insensitive)
    List<Property> findByPropertyNameContainingIgnoreCase(String propertyName);
    
    // ✅ Find by created by user with pagination
    List<Property> findByCreatedByOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    // ✅ Count methods
    long countByCreatedBy(Long userId);
    
    // ✅ Advanced search query
    @Query("SELECT p FROM Property p WHERE " +
           "(:propertyName IS NULL OR LOWER(p.propertyName) LIKE LOWER(CONCAT('%', :propertyName, '%'))) AND " +
           "(:city IS NULL OR LOWER(p.city) LIKE LOWER(CONCAT('%', :city, '%'))) AND " +
           "(:postalCode IS NULL OR LOWER(p.postcode) LIKE LOWER(CONCAT('%', :postalCode, '%'))) AND " +
           "(:isArchived IS NULL OR p.isArchived = :isArchived) AND " +
           "(:propertyType IS NULL OR p.propertyType = :propertyType) AND " +
           "(:bedrooms IS NULL OR p.bedrooms = :bedrooms)")
    List<Property> searchProperties(@Param("propertyName") String propertyName,
                                   @Param("city") String city,
                                   @Param("postalCode") String postalCode,
                                   @Param("isArchived") String isArchived,
                                   @Param("propertyType") String propertyType,
                                   @Param("bedrooms") Integer bedrooms,
                                   Pageable pageable);
    
    // ✅ Date-based queries
    @Query("SELECT p FROM Property p WHERE p.listedUntil IS NOT NULL AND p.listedUntil <= :date")
    List<Property> findPropertiesWithUpcomingExpiry(@Param("date") LocalDate date);
    
    // ✅ Address search - PayProp compatible fields
    @Query("SELECT p FROM Property p WHERE " +
           "LOWER(CONCAT(COALESCE(p.addressLine1, ''), ' ', COALESCE(p.addressLine2, ''), ' ', " +
           "COALESCE(p.addressLine3, ''), ' ', COALESCE(p.city, ''), ' ', COALESCE(p.postcode, ''))) " +
           "LIKE LOWER(CONCAT('%', :address, '%'))")
    List<Property> findByFullAddressContaining(@Param("address") String address);

    // ✅ Portfolio assignment queries
    @Query("SELECT p FROM Property p WHERE p.portfolio IS NULL AND p.isArchived = 'N'")
    List<Property> findUnassignedProperties();

    @Query("SELECT p FROM Property p WHERE p.portfolio.id = :portfolioId AND p.isArchived = 'N'")
    List<Property> findActivePropertiesByPortfolio(@Param("portfolioId") Long portfolioId);

    @Query("SELECT p FROM Property p WHERE p.block.id = :blockId AND p.isArchived = 'N'")
    List<Property> findActivePropertiesByBlock(@Param("blockId") Long blockId);

    // ✅ Count properties by portfolio/block
    @Query("SELECT COUNT(p) FROM Property p WHERE p.portfolio.id = :portfolioId")
    long countByPortfolioId(@Param("portfolioId") Long portfolioId);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.block.id = :blockId")
    long countByBlockId(@Param("blockId") Long blockId);

    // ✅ Portfolio analytics support
    @Query("SELECT p FROM Property p LEFT JOIN FETCH p.portfolio WHERE p.id IN :propertyIds")
    List<Property> findByIdInWithPortfolio(@Param("propertyIds") List<Long> propertyIds);
    
    // 🔧 FIXED: Junction table-based occupancy detection (WORKING - 252 occupied properties)
    @Query(value = "SELECT DISTINCT p.* FROM properties p " +
                   "WHERE p.is_archived = 'N' " +
                   "AND EXISTS ( " +
                   "    SELECT 1 FROM customer_property_assignments cpa " +
                   "    WHERE cpa.property_id = p.id " +
                   "    AND cpa.assignment_type = 'TENANT' " +
                   "    AND (cpa.end_date IS NULL OR cpa.end_date > CURDATE()) " +
                   "    AND (cpa.start_date IS NULL OR cpa.start_date <= CURDATE()) " +
                   ")", nativeQuery = true)
    List<Property> findOccupiedProperties();
    
    // 🔧 FIXED: Junction table-based vacancy detection (WORKING - 11 vacant properties)
    @Query(value = "SELECT DISTINCT p.* FROM properties p " +
                   "WHERE p.is_archived = 'N' " +
                   "AND NOT EXISTS ( " +
                   "    SELECT 1 FROM customer_property_assignments cpa " +
                   "    WHERE cpa.property_id = p.id " +
                   "    AND cpa.assignment_type = 'TENANT' " +
                   "    AND (cpa.end_date IS NULL OR cpa.end_date > CURDATE()) " +
                   "    AND (cpa.start_date IS NULL OR cpa.start_date <= CURDATE()) " +
                   ")", nativeQuery = true)
    List<Property> findVacantProperties();
    
    // 🔧 FIXED: Check individual property for active tenants using junction table
    @Query(value = "SELECT CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END " +
                   "FROM customer_property_assignments cpa " +
                   "WHERE cpa.property_id = ?1 " +
                   "AND cpa.assignment_type = 'TENANT' " +
                   "AND (cpa.end_date IS NULL OR cpa.end_date > CURDATE()) " +
                   "AND (cpa.start_date IS NULL OR cpa.start_date <= CURDATE())", 
           nativeQuery = true)
    boolean hasNoActiveTenantsById(@Param("propertyId") Long propertyId);
    
    // ✅ Debug query to show assignment counts with dates
    @Query(value = "SELECT " +
                   "p.id, " +
                   "p.property_name, " +
                   "COUNT(CASE WHEN cpa.assignment_type = 'TENANT' " +
                   "           AND (cpa.end_date IS NULL OR cpa.end_date > CURDATE()) " +
                   "           AND (cpa.start_date IS NULL OR cpa.start_date <= CURDATE()) " +
                   "           THEN 1 END) as active_tenants, " +
                   "COUNT(CASE WHEN cpa.assignment_type = 'OWNER' " +
                   "           AND (cpa.end_date IS NULL OR cpa.end_date > CURDATE()) " +
                   "           AND (cpa.start_date IS NULL OR cpa.start_date <= CURDATE()) " +
                   "           THEN 1 END) as active_owners " +
                   "FROM properties p " +
                   "LEFT JOIN customer_property_assignments cpa ON p.id = cpa.property_id " +
                   "WHERE p.is_archived = 'N' " +
                   "GROUP BY p.id, p.property_name " +
                   "ORDER BY active_tenants DESC, p.property_name " +
                   "LIMIT 30", nativeQuery = true)
    List<Object[]> findPropertyAssignmentCounts();
    
    // ✅ PayProp sync queries
    @Query("SELECT p FROM Property p WHERE p.payPropId IS NULL AND p.isArchived = 'N'")
    List<Property> findByPayPropIdIsNullAndIsArchivedFalse();
    
    List<Property> findByPayPropIdIsNotNull();
    List<Property> findByPayPropIdIsNull();
    
    // ✅ PayProp validation queries
    @Query("SELECT p FROM Property p WHERE p.isArchived = 'N' AND " +
           "p.propertyName IS NOT NULL AND p.customerId IS NOT NULL AND " +
           "p.monthlyPayment IS NOT NULL AND p.monthlyPayment > 0")
    List<Property> findPropertiesReadyForSync();
    
    @Query("SELECT p FROM Property p WHERE p.isArchived = 'N' AND " +
           "(p.propertyName IS NULL OR p.customerId IS NULL OR " +
           "p.monthlyPayment IS NULL OR p.monthlyPayment <= 0)")
    List<Property> findPropertiesWithMissingPayPropFields();
    
    // ✅ Find by agent (PayProp portfolio subdivision)
    List<Property> findByAgentName(String agentName);
    
    // ✅ Find by customer reference (PayProp identifier)
    Optional<Property> findByCustomerReference(String customerReference);
}