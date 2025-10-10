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
}