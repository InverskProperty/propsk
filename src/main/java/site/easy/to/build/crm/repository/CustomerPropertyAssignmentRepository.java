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
    List<CustomerPropertyAssignment> findByCustomerCustomerId(Integer customerId);
    List<CustomerPropertyAssignment> findByPropertyId(Long propertyId);
    List<CustomerPropertyAssignment> findByAssignmentType(AssignmentType assignmentType);
    List<CustomerPropertyAssignment> findByPropertyIdAndAssignmentType(Long propertyId, AssignmentType assignmentType);
    
    Optional<CustomerPropertyAssignment> findByCustomerCustomerIdAndPropertyIdAndAssignmentType(
        Integer customerId, Long propertyId, AssignmentType assignmentType);
    
    boolean existsByCustomerCustomerIdAndPropertyIdAndAssignmentType(
        Integer customerId, Long propertyId, AssignmentType assignmentType);
    
    void deleteByCustomerCustomerIdAndPropertyIdAndAssignmentType(
        Integer customerId, Long propertyId, AssignmentType assignmentType);

    // ✅ ADDED: Missing methods from paste.txt
    List<CustomerPropertyAssignment> findByCustomerCustomerIdAndAssignmentType(Integer customerId, AssignmentType assignmentType);
    
    // Count assignments by type
    @Query("SELECT COUNT(cpa) FROM CustomerPropertyAssignment cpa WHERE cpa.assignmentType = :assignmentType")
    long countByAssignmentType(@Param("assignmentType") AssignmentType assignmentType);
    
    // Get all unique customer IDs for a property with specific assignment type
    @Query("SELECT DISTINCT cpa.customer.customerId FROM CustomerPropertyAssignment cpa WHERE cpa.property.id = :propertyId AND cpa.assignmentType = :assignmentType")
    List<Integer> findCustomerIdsByPropertyIdAndAssignmentType(@Param("propertyId") Long propertyId, @Param("assignmentType") AssignmentType assignmentType);
    
    // Get all unique property IDs for a customer with specific assignment type
    @Query("SELECT DISTINCT cpa.property.id FROM CustomerPropertyAssignment cpa WHERE cpa.customer.customerId = :customerId AND cpa.assignmentType = :assignmentType")
    List<Long> findPropertyIdsByCustomerIdAndAssignmentType(@Param("customerId") Integer customerId, @Param("assignmentType") AssignmentType assignmentType);
}