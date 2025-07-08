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
    
    // Use customer.customerId instead of customer.id
    List<CustomerPropertyAssignment> findByCustomerCustomerId(Integer customerId);
    List<CustomerPropertyAssignment> findByPropertyId(Long propertyId);
    List<CustomerPropertyAssignment> findByAssignmentType(AssignmentType assignmentType);
    
    Optional<CustomerPropertyAssignment> findByCustomerCustomerIdAndPropertyIdAndAssignmentType(
        Integer customerId, Long propertyId, AssignmentType assignmentType);
    
    boolean existsByCustomerCustomerIdAndPropertyIdAndAssignmentType(
        Integer customerId, Long propertyId, AssignmentType assignmentType);
    
    void deleteByCustomerCustomerIdAndPropertyIdAndAssignmentType(
        Integer customerId, Long propertyId, AssignmentType assignmentType);
}