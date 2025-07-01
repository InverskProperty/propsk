package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.TenantService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EmployeeTenantController - Employee-facing tenant management
 * Handles CRUD operations for tenants from the employee perspective
 * Follows PayProp best practices: Always use BUSINESS account type
 */
@Controller
@RequestMapping("/admin/tenants")
public class EmployeeTenantController {

    private final TenantService tenantService;
    private final PropertyService propertyService;
    private final CustomerService customerService;

    @Autowired
    public EmployeeTenantController(TenantService tenantService, 
                                   PropertyService propertyService,
                                   CustomerService customerService) {
        this.tenantService = tenantService;
        this.propertyService = propertyService;
        this.customerService = customerService;
    }

    /**
     * Create Tenant Form - GET /admin/tenants/create
     */
    @GetMapping("/create")
    public String showCreateTenantForm(Model model, Authentication authentication) {
        try {
            // Create new Tenant entity with PayProp best practice defaults
            Tenant tenant = new Tenant();
            tenant.setAccountType(AccountType.BUSINESS); // PayProp recommendation
            tenant.setCountry("UK");
            tenant.setNotifyEmail("Y");
            tenant.setNotifyText("Y");
            
            // Get all properties for the dropdown
            List<Property> properties = propertyService.findAll();
            
            // Add all required model attributes that the template expects
            model.addAttribute("tenant", tenant);
            model.addAttribute("properties", properties);
            model.addAttribute("selectedProperty", null);
            model.addAttribute("pageTitle", "Create New Tenant");
            model.addAttribute("isEdit", false);
            
            return "employee/tenant/create-tenant";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading create tenant form: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Create Tenant - POST /admin/tenants/create
     * Implements PayProp best practices for tenant creation
     */
    @PostMapping("/create")
    public String createTenant(@ModelAttribute Tenant tenant, 
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            // PayProp best practice: Always use BUSINESS account type
            tenant.setAccountType(AccountType.BUSINESS);
            
            // Handle name population intelligently for PayProp compatibility
            if (tenant.getFirstName() != null && tenant.getLastName() != null 
                && !tenant.getFirstName().trim().isEmpty() && !tenant.getLastName().trim().isEmpty()) {
                
                // Standard case: Individual names provided - populate business name
                if (tenant.getBusinessName() == null || tenant.getBusinessName().trim().isEmpty()) {
                    tenant.setBusinessName(tenant.getFirstName().trim() + " " + tenant.getLastName().trim());
                }
                
            } else if (tenant.getBusinessName() != null && !tenant.getBusinessName().trim().isEmpty()) {
                
                // Business name provided, try to extract individual names for compatibility
                String businessName = tenant.getBusinessName().trim();
                
                // Simple heuristic: if it doesn't contain "and", "And", "&", treat as single person
                if (!businessName.toLowerCase().contains(" and ") && !businessName.contains(" & ")) {
                    String[] nameParts = businessName.split("\\s+", 2);
                    if (nameParts.length >= 2) {
                        // Standard "First Last" format
                        tenant.setFirstName(nameParts[0]);
                        tenant.setLastName(nameParts[1]);
                    } else {
                        // Single name - use as first name
                        tenant.setFirstName(businessName);
                        tenant.setLastName(""); // Empty but not null to avoid validation issues
                    }
                } else {
                    // Multiple people (e.g., "Mr A Blag and Mrs B Blag") - use generic values
                    tenant.setFirstName("Tenant");
                    tenant.setLastName("Multiple");
                }
            }
            
            // Validation: Ensure we have the minimum required fields for both PayProp and internal use
            if (tenant.getBusinessName() == null || tenant.getBusinessName().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Business name is required. Please provide tenant name(s).");
                return "redirect:/admin/tenants/create";
            }
            
            // Ensure individual names exist for internal compatibility
            if (tenant.getFirstName() == null || tenant.getFirstName().trim().isEmpty()) {
                tenant.setFirstName("Tenant"); // Default value
            }
            if (tenant.getLastName() == null || tenant.getLastName().trim().isEmpty()) {
                tenant.setLastName("Name"); // Default value
            }
            
            // Clean up mobile number to avoid validation issues
            if (tenant.getMobileNumber() != null && tenant.getMobileNumber().trim().isEmpty()) {
                tenant.setMobileNumber(null);
            }
            
            // Validate UK mobile number format if provided
            if (tenant.getMobileNumber() != null && !tenant.getMobileNumber().matches("^(0[1-9]\\d{8,9}|\\+44[1-9]\\d{8,9})$")) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Invalid mobile number format. Use UK format (e.g., 07123456789).");
                return "redirect:/admin/tenants/create";
            }
            
            // Set audit fields
            tenant.setCreatedAt(LocalDateTime.now());
            tenant.setUpdatedAt(LocalDateTime.now());
            
            // Generate PayProp customer ID if not set
            if (tenant.getPayPropCustomerId() == null || tenant.getPayPropCustomerId().isEmpty()) {
                tenant.setPayPropCustomerId("TENANT_" + System.currentTimeMillis());
            }
            
            // Set default status values
            if (tenant.getStatus() == null || tenant.getStatus().isEmpty()) {
                tenant.setStatus("Active");
            }
            
            if (tenant.getTenancyStatus() == null || tenant.getTenancyStatus().isEmpty()) {
                tenant.setTenancyStatus("active");
            }
            
            // Save the tenant
            Tenant savedTenant = tenantService.save(tenant);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Tenant " + savedTenant.getFullName() + " created successfully! (PayProp Business Account)");
            
            // Redirect to tenant list
            return "redirect:/employee/customer/tenants";
            
        } catch (Exception e) {
            String errorMessage = "Error creating tenant: " + e.getMessage();
            
            // Handle specific validation errors
            if (e.getMessage().contains("mobile_number")) {
                errorMessage = "Invalid mobile number format. Please use UK format (e.g., 07123456789).";
            } else if (e.getMessage().contains("business_name")) {
                errorMessage = "Business name is required and must be 2-50 characters.";
            }
            
            redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
            return "redirect:/admin/tenants/create";
        }
    }

    /**
     * Edit Tenant Form - GET /admin/tenants/{id}/edit
     */
    @GetMapping("/{id}/edit")
    public String editTenantForm(@PathVariable Long id, Model model, Authentication authentication) {
        try {
            Tenant tenant = tenantService.findById(id);
            if (tenant == null) {
                return "error/not-found";
            }
            
            // Ensure business account type for PayProp compatibility
            if (tenant.getAccountType() != AccountType.BUSINESS) {
                tenant.setAccountType(AccountType.BUSINESS);
                // Populate business name if missing
                if (tenant.getBusinessName() == null || tenant.getBusinessName().trim().isEmpty()) {
                    if (tenant.getFirstName() != null && tenant.getLastName() != null) {
                        tenant.setBusinessName(tenant.getFirstName() + " " + tenant.getLastName());
                    }
                }
            }
            
            // Get all properties for the dropdown
            List<Property> properties = propertyService.findAll();
            
            model.addAttribute("tenant", tenant);
            model.addAttribute("properties", properties);
            model.addAttribute("selectedProperty", tenant.getProperty());
            model.addAttribute("pageTitle", "Edit Tenant");
            model.addAttribute("isEdit", true);
            
            return "employee/tenant/create-tenant"; // Reuse the same template
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading tenant for edit: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Update Tenant - POST /admin/tenants/{id}/edit
     */
    @PostMapping("/{id}/edit")
    public String updateTenant(@PathVariable Long id,
                              @ModelAttribute Tenant tenant,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            Tenant existingTenant = tenantService.findById(id);
            if (existingTenant == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Tenant not found");
                return "redirect:/employee/customer/tenants";
            }
            
            // Apply the same PayProp business account logic as create
            tenant.setAccountType(AccountType.BUSINESS);
            
            // Handle name population for updates
            if (tenant.getFirstName() != null && tenant.getLastName() != null 
                && !tenant.getFirstName().trim().isEmpty() && !tenant.getLastName().trim().isEmpty()) {
                
                if (tenant.getBusinessName() == null || tenant.getBusinessName().trim().isEmpty()) {
                    tenant.setBusinessName(tenant.getFirstName().trim() + " " + tenant.getLastName().trim());
                }
            }
            
            // Clean up mobile number
            if (tenant.getMobileNumber() != null && tenant.getMobileNumber().trim().isEmpty()) {
                tenant.setMobileNumber(null);
            }
            
            // Preserve the ID and audit fields
            tenant.setId(id);
            tenant.setCreatedAt(existingTenant.getCreatedAt());
            tenant.setUpdatedAt(LocalDateTime.now());
            tenant.setCreatedBy(existingTenant.getCreatedBy());
            
            // Preserve PayProp sync data
            tenant.setPayPropId(existingTenant.getPayPropId());
            if (tenant.getPayPropCustomerId() == null) {
                tenant.setPayPropCustomerId(existingTenant.getPayPropCustomerId());
            }
            
            Tenant savedTenant = tenantService.save(tenant);
            
            // Update corresponding Customer record if it exists
            try {
                List<Customer> customers = customerService.findAll().stream()
                    .filter(c -> c.getEntityType() != null && c.getEntityType().equals("tenant") 
                             && c.getEntityId() != null && c.getEntityId().equals(savedTenant.getId()))
                    .collect(Collectors.toList());
                
                if (!customers.isEmpty()) {
                    Customer customer = customers.get(0);
                    customer.setName(savedTenant.getFullName());
                    customer.setEmail(savedTenant.getEmailAddress());
                    customer.setPhone(savedTenant.getMobileNumber());
                    customer.setCity(savedTenant.getCity());
                    customer.setCountry(savedTenant.getCountry());
                    customer.setAddress(savedTenant.getAddressLine1());
                    customer.setState(savedTenant.getCounty());
                    customerService.save(customer);
                }
            } catch (Exception e) {
                // Log but don't fail the main update
                System.err.println("Warning: Could not update corresponding customer record: " + e.getMessage());
            }
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Tenant " + savedTenant.getFullName() + " updated successfully!");
            
            return "redirect:/employee/customer/tenants";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error updating tenant: " + e.getMessage());
            return "redirect:/admin/tenants/" + id + "/edit";
        }
    }

    /**
     * View Tenant Details - GET /admin/tenants/{id}
     */
    @GetMapping("/{id}")
    public String viewTenantDetails(@PathVariable Long id, Model model, Authentication authentication) {
        try {
            Tenant tenant = tenantService.findById(id);
            if (tenant == null) {
                return "error/not-found";
            }
            
            model.addAttribute("tenant", tenant);
            model.addAttribute("property", tenant.getProperty());
            model.addAttribute("pageTitle", "Tenant Details");
            model.addAttribute("backUrl", "/employee/customer/tenants");
            
            return "employee/tenant/tenant-details";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading tenant details: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Delete Tenant - POST /admin/tenants/{id}/delete
     * Soft delete - mark as inactive instead of hard delete
     */
    @PostMapping("/{id}/delete")
    public String deleteTenant(@PathVariable Long id,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            Tenant tenant = tenantService.findById(id);
            if (tenant == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Tenant not found");
                return "redirect:/employee/customer/tenants";
            }
            
            // Soft delete - mark as inactive instead of hard delete
            tenant.setStatus("Inactive");
            tenant.setTenancyStatus("Ended");
            tenant.setUpdatedAt(LocalDateTime.now());
            tenantService.save(tenant);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Tenant " + tenant.getFullName() + " has been marked as inactive.");
            
            return "redirect:/employee/customer/tenants";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error deleting tenant: " + e.getMessage());
            return "redirect:/employee/customer/tenants";
        }
    }
}