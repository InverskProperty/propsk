package site.easy.to.build.crm.service.assignment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerPropertyAssignmentService {
    
    private final CustomerPropertyAssignmentRepository assignmentRepository;
    
    @Autowired
    public CustomerPropertyAssignmentService(CustomerPropertyAssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }
    
    public CustomerPropertyAssignment createAssignment(Customer customer, Property property, AssignmentType type) {
        return createAssignment(customer, property, type, null, true);
    }
    
    public CustomerPropertyAssignment createAssignment(Customer customer, Property property, 
                                                     AssignmentType type, BigDecimal percentage, Boolean isPrimary) {
        if (assignmentRepository.existsByCustomerIdAndPropertyIdAndAssignmentType(
                customer.getCustomerId(), property.getId(), type)) {
            throw new IllegalStateException("Assignment already exists");
        }
        
        CustomerPropertyAssignment assignment = new CustomerPropertyAssignment(customer, property, type);
        if (percentage != null) {
            assignment.setOwnershipPercentage(percentage);
        }
        assignment.setIsPrimary(isPrimary);
        
        return assignmentRepository.save(assignment);
    }
    
    public List<Property> getPropertiesForCustomer(Integer customerId, AssignmentType type) {
        return assignmentRepository.findByCustomerId(customerId).stream()
            .filter(assignment -> type == null || assignment.getAssignmentType() == type)
            .map(CustomerPropertyAssignment::getProperty)
            .collect(Collectors.toList());
    }
    
    public List<Customer> getCustomersForProperty(Long propertyId, AssignmentType type) {
        return assignmentRepository.findByPropertyId(propertyId).stream()
            .filter(assignment -> type == null || assignment.getAssignmentType() == type)
            .map(CustomerPropertyAssignment::getCustomer)
            .collect(Collectors.toList());
    }
    
    public void removeAssignment(Integer customerId, Long propertyId, AssignmentType type) {
        assignmentRepository.deleteByCustomerIdAndPropertyIdAndAssignmentType(customerId, propertyId, type);
    }
}