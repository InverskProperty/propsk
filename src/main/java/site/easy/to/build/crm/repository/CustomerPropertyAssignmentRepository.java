package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.entity.AssignmentType;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerPropertyAssignmentRepository extends JpaRepository<CustomerPropertyAssignment, Long> {
    
    // ===== EXISTING METHODS =====
    List<CustomerPropertyAssignment> findByCustomerCustomerId(Long customerId);
    List<CustomerPropertyAssignment> findByPropertyId(Long propertyId);
    List<CustomerPropertyAssignment> findByAssignmentType(AssignmentType assignmentType);
    List<CustomerPropertyAssignment> findByPropertyIdAndAssignmentType(Long propertyId, AssignmentType assignmentType);
    
    Optional<CustomerPropertyAssignment> findByCustomerCustomerIdAndPropertyIdAndAssignmentType(Long customerId, Long propertyId, AssignmentType assignmentType);
    
    boolean existsByCustomerCustomerIdAndPropertyIdAndAssignmentType(Long customerId, Long propertyId, AssignmentType assignmentType);
    
    void deleteByCustomerCustomerIdAndPropertyIdAndAssignmentType(Long customerId, Long propertyId, AssignmentType assignmentType);

    // ✅ ADDED: Missing methods from paste.txt
    List<CustomerPropertyAssignment> findByCustomerCustomerIdAndAssignmentType(Long customerId, AssignmentType assignmentType);
    
    // Count assignments by type
    @Query("SELECT COUNT(cpa) FROM CustomerPropertyAssignment cpa WHERE cpa.assignmentType = :assignmentType")
    long countByAssignmentType(@Param("assignmentType") AssignmentType assignmentType);
    
    // Get all unique customer IDs for a property with specific assignment type
    @Query("SELECT DISTINCT cpa.customer.customerId FROM CustomerPropertyAssignment cpa WHERE cpa.property.id = :propertyId AND cpa.assignmentType = :assignmentType")
    List<Long> findCustomerIdsByPropertyIdAndAssignmentType(@Param("propertyId") Long propertyId, @Param("assignmentType") AssignmentType assignmentType);
    
    // Get all unique property IDs for a customer with specific assignment type
    @Query("SELECT DISTINCT cpa.property.id FROM CustomerPropertyAssignment cpa WHERE cpa.customer.customerId = :customerId AND cpa.assignmentType = :assignmentType")
    List<Long> findPropertyIdsByCustomerIdAndAssignmentType(@Param("customerId") Long customerId, @Param("assignmentType") AssignmentType assignmentType);

    // Find active tenants for a property (end_date IS NULL or > today)
    @Query("SELECT cpa FROM CustomerPropertyAssignment cpa WHERE cpa.property.id = :propertyId " +
           "AND cpa.assignmentType = :assignmentType " +
           "AND (cpa.endDate IS NULL OR cpa.endDate > :currentDate) " +
           "ORDER BY cpa.startDate DESC")
    List<CustomerPropertyAssignment> findActiveAssignmentsByPropertyAndType(@Param("propertyId") Long propertyId,
                                                                           @Param("assignmentType") AssignmentType assignmentType,
                                                                           @Param("currentDate") java.time.LocalDate currentDate);

    // Find assignments for multiple properties by assignment type
    @Query("SELECT cpa FROM CustomerPropertyAssignment cpa WHERE cpa.property.id IN :propertyIds AND cpa.assignmentType = :assignmentType")
    List<CustomerPropertyAssignment> findByPropertyIdInAndAssignmentType(@Param("propertyIds") List<Long> propertyIds,
                                                                         @Param("assignmentType") AssignmentType assignmentType);

    // ✅ NEW: Get properties for an owner ordered by block display_order
    // Properties in blocks are ordered by display_order, standalone properties are ordered alphabetically
    // Note: MySQL doesn't support NULLS LAST, so we use "IS NULL" trick (NULL=1, non-NULL=0, sorts NULLs last)
    // FIXED: Use GROUP BY instead of DISTINCT with MIN() aggregates to make ORDER BY columns compatible
    @Query("SELECT cpa.property FROM CustomerPropertyAssignment cpa " +
           "LEFT JOIN PropertyBlockAssignment pba ON cpa.property.id = pba.property.id AND pba.isActive = true " +
           "WHERE cpa.customer.customerId = :customerId AND cpa.assignmentType = :assignmentType " +
           "GROUP BY cpa.property.id " +
           "ORDER BY MIN(CASE WHEN pba.displayOrder IS NULL THEN 1 ELSE 0 END), " +
                    "MIN(pba.displayOrder), " +
                    "MIN(cpa.property.propertyName)")
    List<site.easy.to.build.crm.entity.Property> findPropertiesByCustomerIdAndAssignmentTypeOrdered(
            @Param("customerId") Long customerId,
            @Param("assignmentType") AssignmentType assignmentType);

    // ✅ NEW: Find assignments with eagerly fetched customer and property (for Thymeleaf rendering)
    @Query("SELECT cpa FROM CustomerPropertyAssignment cpa " +
           "JOIN FETCH cpa.customer " +
           "JOIN FETCH cpa.property " +
           "WHERE cpa.property.id = :propertyId AND cpa.assignmentType = :assignmentType")
    List<CustomerPropertyAssignment> findByPropertyIdAndAssignmentTypeWithDetails(
            @Param("propertyId") Long propertyId,
            @Param("assignmentType") AssignmentType assignmentType);

    // ✅ NEW: Find assignments for multiple properties with eagerly fetched details
    @Query("SELECT cpa FROM CustomerPropertyAssignment cpa " +
           "JOIN FETCH cpa.customer " +
           "JOIN FETCH cpa.property " +
           "WHERE cpa.property.id IN :propertyIds AND cpa.assignmentType = :assignmentType")
    List<CustomerPropertyAssignment> findByPropertyIdsAndAssignmentTypeWithDetails(
            @Param("propertyIds") List<Long> propertyIds,
            @Param("assignmentType") AssignmentType assignmentType);
}