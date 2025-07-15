package site.easy.to.build.crm.service.assignment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
        if (assignmentRepository.existsByCustomerCustomerIdAndPropertyIdAndAssignmentType(
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
        try {
            return assignmentRepository.findByCustomerCustomerId(customerId).stream()
                .filter(assignment -> type == null || assignment.getAssignmentType() == type)
                .map(assignment -> {
                    try {
                        return assignment.getProperty();
                    } catch (Exception e) {
                        System.err.println("Skipping orphaned property assignment: " + e.getMessage());
                        return null;
                    }
                })
                .filter(property -> property != null)
                .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Error getting properties for customer " + customerId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public List<Customer> getCustomersForProperty(Long propertyId, AssignmentType type) {
        List<CustomerPropertyAssignment> assignments = assignmentRepository.findByPropertyId(propertyId);
        
        return assignments.stream()
            .filter(assignment -> type == null || assignment.getAssignmentType() == type)
            .map(assignment -> {
                try {
                    // Safely get the customer, handling cases where the customer might not exist
                    Customer customer = assignment.getCustomer();
                    return customer;
                } catch (Exception e) {
                    // Log the error and skip this assignment
                    System.err.println("Error loading customer for assignment ID " + assignment.getId() + 
                        ": " + e.getMessage());
                    return null;
                }
            })
            .filter(customer -> customer != null) // Remove null customers
            .collect(Collectors.toList());
    }
    
    public void removeAssignment(Integer customerId, Long propertyId, AssignmentType type) {
        assignmentRepository.deleteByCustomerCustomerIdAndPropertyIdAndAssignmentType(customerId, propertyId, type);
    }
    
    public CustomerPropertyAssignment updateAssignment(Integer customerId, Long propertyId, 
                                                     AssignmentType type, BigDecimal percentage) {
        CustomerPropertyAssignment assignment = assignmentRepository
            .findByCustomerCustomerIdAndPropertyIdAndAssignmentType(customerId, propertyId, type)
            .orElseThrow(() -> new RuntimeException("Assignment not found"));
        
        assignment.setOwnershipPercentage(percentage);
        assignment.setUpdatedAt(LocalDateTime.now());
        
        return assignmentRepository.save(assignment);
    }
    
    public List<CustomerPropertyAssignment> getAllAssignments() {
        return assignmentRepository.findAll();
    }
    
    public List<CustomerPropertyAssignment> getAssignmentsByType(AssignmentType type) {
        return assignmentRepository.findByAssignmentType(type);
    }
    
    public long countPropertiesForCustomer(Integer customerId) {
        return assignmentRepository.findByCustomerCustomerId(customerId).size();
    }
    
    public long countCustomersForProperty(Long propertyId) {
        return assignmentRepository.findByPropertyId(propertyId).size();
    }
    
    public boolean hasAssignment(Integer customerId, Long propertyId, AssignmentType type) {
        return assignmentRepository.existsByCustomerCustomerIdAndPropertyIdAndAssignmentType(customerId, propertyId, type);
    }
    
    public void clearAllAssignments() {
        assignmentRepository.deleteAll();
    }
}