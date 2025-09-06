package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.service.payprop.LocalToPayPropSyncService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller for Customer-Property Assignment Management
 * Enables linking customers to properties as owners, tenants, or managers
 */
@Controller
@RequestMapping("/employee/assignment")
public class CustomerAssignmentController {

    @Autowired
    private CustomerService customerService;
    
    @Autowired
    private PropertyService propertyService;
    
    
    @Autowired
    private CustomerPropertyAssignmentRepository assignmentRepository;
    
    @Autowired
    private LocalToPayPropSyncService localToPayPropSyncService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private AuthenticationUtils authenticationUtils;

    /**
     * Show assignment management page
     */
    @GetMapping("")
    public String showAssignments(Model model, Authentication authentication) {
        try {
            // Get all existing assignments
            List<CustomerPropertyAssignment> assignments = assignmentRepository.findAll();
            
            // Get customers and properties for dropdowns
            List<Customer> allCustomers = customerService.findAll();
            List<Property> allProperties = propertyService.findAll();
            
            // Filter customers by type for easier selection
            List<Customer> propertyOwners = allCustomers.stream()
                .filter(c -> c.getCustomerType().toString().equals("PROPERTY_OWNER"))
                .collect(Collectors.toList());
                
            List<Customer> tenants = allCustomers.stream()
                .filter(c -> c.getCustomerType().toString().equals("TENANT"))
                .collect(Collectors.toList());
                
            List<Customer> contractors = allCustomers.stream()
                .filter(c -> c.getCustomerType().toString().equals("CONTRACTOR"))
                .collect(Collectors.toList());
            
            model.addAttribute("assignments", assignments);
            model.addAttribute("properties", allProperties);
            model.addAttribute("propertyOwners", propertyOwners);
            model.addAttribute("tenants", tenants);
            model.addAttribute("contractors", contractors);
            model.addAttribute("assignmentTypes", AssignmentType.values());
            
            return "assignment/assignment-management";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading assignments: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Create new customer-property assignment
     */
    @PostMapping("/create")
    public String createAssignment(
            @RequestParam("customerId") Long customerId,
            @RequestParam("propertyId") Long propertyId,
            @RequestParam("assignmentType") String assignmentTypeStr,
            @RequestParam(value = "ownershipPercentage", required = false) BigDecimal ownershipPercentage,
            @RequestParam(value = "syncToPayProp", defaultValue = "false") Boolean syncToPayProp,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {
        
        try {
            System.out.println("🔗 Creating assignment: Customer " + customerId + " → Property " + propertyId + " as " + assignmentTypeStr);
            
            // Get entities
            Customer customer = customerService.findByCustomerId(customerId);
            Property property = propertyService.findById(propertyId);
            AssignmentType assignmentType = AssignmentType.valueOf(assignmentTypeStr);
            
            if (customer == null || property == null) {
                redirectAttributes.addFlashAttribute("error", "Customer or Property not found");
                return "redirect:/employee/assignment";
            }
            
            // Check if assignment already exists
            Optional<CustomerPropertyAssignment> existingOpt = assignmentRepository
                .findByCustomerCustomerIdAndPropertyIdAndAssignmentType(customerId, propertyId, assignmentType);
            
            if (existingOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("error", 
                    customer.getName() + " is already assigned to " + property.getPropertyName() + " as " + assignmentType);
                return "redirect:/employee/assignment";
            }
            
            // Create assignment
            CustomerPropertyAssignment assignment = new CustomerPropertyAssignment();
            assignment.setCustomer(customer);
            assignment.setProperty(property);
            assignment.setAssignmentType(assignmentType);
            assignment.setCreatedAt(LocalDateTime.now());
            
            // Set ownership percentage for owners
            if (assignmentType == AssignmentType.OWNER && ownershipPercentage != null) {
                assignment.setOwnershipPercentage(ownershipPercentage);
            }
            
            // Save assignment
            assignmentRepository.save(assignment);
            
            System.out.println("✅ Assignment created successfully: " + assignment.getId());
            
            // PayProp sync based on assignment type
            if (syncToPayProp) {
                try {
                    String syncResult = null;
                    
                    if (assignmentType == AssignmentType.OWNER) {
                        // Sync customer as PayProp beneficiary
                        System.out.println("🔄 Syncing customer as PayProp beneficiary...");
                        syncResult = localToPayPropSyncService.syncCustomerAsBeneficiaryToPayProp(customer);
                        System.out.println("✅ Customer synced as PayProp beneficiary: " + syncResult);
                        
                    } else if (assignmentType == AssignmentType.TENANT) {
                        // Sync customer as PayProp tenant
                        System.out.println("🔄 Syncing customer as PayProp tenant...");
                        syncResult = localToPayPropSyncService.syncCustomerAsTenantToPayProp(customer);
                        System.out.println("✅ Customer synced as PayProp tenant: " + syncResult);
                    }
                    
                    if (syncResult != null) {
                        redirectAttributes.addFlashAttribute("success", 
                            "Assignment created and customer synced to PayProp successfully! PayProp ID: " + syncResult);
                    } else {
                        redirectAttributes.addFlashAttribute("warning", 
                            "Assignment created. PayProp sync only available for OWNER and TENANT assignments.");
                    }
                    
                } catch (Exception syncError) {
                    System.out.println("❌ PayProp sync failed: " + syncError.getMessage());
                    redirectAttributes.addFlashAttribute("warning", 
                        "Assignment created locally, but PayProp sync failed: " + syncError.getMessage());
                }
            } else {
                redirectAttributes.addFlashAttribute("success", 
                    "Successfully assigned " + customer.getName() + " to " + property.getPropertyName() + " as " + assignmentType);
            }
            
        } catch (Exception e) {
            System.out.println("❌ Assignment creation failed: " + e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error creating assignment: " + e.getMessage());
        }
        
        return "redirect:/employee/assignment";
    }

    /**
     * Delete assignment
     */
    @PostMapping("/delete/{id}")
    public String deleteAssignment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            CustomerPropertyAssignment assignment = assignmentRepository.findById(id).orElse(null);
            if (assignment != null) {
                String description = assignment.getCustomer().getName() + " → " + 
                                   assignment.getProperty().getPropertyName() + " (" + assignment.getAssignmentType() + ")";
                
                assignmentRepository.deleteById(id);
                redirectAttributes.addFlashAttribute("success", "Deleted assignment: " + description);
            } else {
                redirectAttributes.addFlashAttribute("error", "Assignment not found");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting assignment: " + e.getMessage());
        }
        
        return "redirect:/employee/assignment";
    }

    /**
     * Get customers by type for AJAX dropdown
     */
    @GetMapping("/customers/{type}")
    @ResponseBody
    public List<Customer> getCustomersByType(@PathVariable String type) {
        try {
            switch (type.toUpperCase()) {
                case "PROPERTY_OWNER":
                    return customerService.findPropertyOwners();
                case "TENANT":
                    return customerService.findTenants();
                case "CONTRACTOR":
                    return customerService.findContractors();
                default:
                    return customerService.findAll();
            }
        } catch (Exception e) {
            System.out.println("Error fetching customers by type: " + e.getMessage());
            return List.of();
        }
    }
}