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
    
    List<CustomerPropertyAssignment> findByCustomerId(Integer customerId);
    List<CustomerPropertyAssignment> findByPropertyId(Long propertyId);
    List<CustomerPropertyAssignment> findByAssignmentType(AssignmentType assignmentType);
    
    Optional<CustomerPropertyAssignment> findByCustomerIdAndPropertyIdAndAssignmentType(
        Integer customerId, Long propertyId, AssignmentType assignmentType);
    
    boolean existsByCustomerIdAndPropertyIdAndAssignmentType(
        Integer customerId, Long propertyId, AssignmentType assignmentType);
    
    void deleteByCustomerIdAndPropertyIdAndAssignmentType(
        Integer customerId, Long propertyId, AssignmentType assignmentType);
}