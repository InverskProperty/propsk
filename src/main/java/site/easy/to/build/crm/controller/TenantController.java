package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.customer.CustomerLoginInfoService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.TenantService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TenantController - Handles both customer-facing tenant dashboard and employee tenant management
 * Provides tenant authentication, tenancy portal, and admin tenant creation/editing
 */
@Controller
@RequestMapping("/tenant")
public class TenantController {

    private final TenantService tenantService;
    private final PropertyService propertyService;
    private final CustomerService customerService;
    private final CustomerLoginInfoService customerLoginInfoService;

    @Autowired
    public TenantController(TenantService tenantService,
                           PropertyService propertyService,
                           CustomerService customerService,
                           CustomerLoginInfoService customerLoginInfoService) {
        this.tenantService = tenantService;
        this.propertyService = propertyService;
        this.customerService = customerService;
        this.customerLoginInfoService = customerLoginInfoService;
    }

    // ===== CUSTOMER-FACING TENANT PORTAL =====

    /**
     * Tenant Dashboard - Main landing page after login
     */
    @GetMapping("/dashboard")
    public String tenantDashboard(Model model, Authentication authentication) {
        System.out.println("=== DEBUG: TenantController.tenantDashboard ===");
        System.out.println("DEBUG: Authentication: " + authentication.getName());
        
        // Simple version without complex database queries
        model.addAttribute("pageTitle", "Tenant Dashboard");
        return "tenant/dashboard";
    }

    /**
     * Tenant Property Details - View current property details
     */
    @GetMapping("/property")
    public String viewProperty(Model model, Authentication authentication) {
        try {
            Customer customer = getAuthenticatedTenant(authentication);
            if (customer == null) {
                return "redirect:/customer-login?error=not_found";
            }

            Tenant tenantEntity = tenantService.findByEmailAddress(customer.getEmail()).orElse(null);
            Property property = null;
            
            if (tenantEntity != null) {
                property = tenantEntity.getProperty();
            }

            model.addAttribute("customer", customer);
            model.addAttribute("tenant", tenantEntity);
            model.addAttribute("property", property);
            model.addAttribute("pageTitle", "My Property");

            return "tenant/property-details";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading property details: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Tenant Profile - View and edit profile
     */
    @GetMapping("/profile")
    public String viewProfile(Model model, Authentication authentication) {
        try {
            Customer customer = getAuthenticatedTenant(authentication);
            if (customer == null) {
                return "redirect:/customer-login?error=not_found";
            }

            Tenant tenantEntity = tenantService.findByEmailAddress(customer.getEmail()).orElse(null);

            model.addAttribute("customer", customer);
            model.addAttribute("tenant", tenantEntity);
            model.addAttribute("pageTitle", "My Profile");

            return "tenant/profile";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading profile: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Maintenance Requests - Submit and view maintenance requests
     */
    @GetMapping("/maintenance")
    public String maintenanceRequests(Model model, Authentication authentication) {
        try {
            Customer customer = getAuthenticatedTenant(authentication);
            if (customer == null) {
                return "redirect:/customer-login?error=not_found";
            }

            // TODO: Implement maintenance request service
            // List<MaintenanceRequest> requests = maintenanceService.findByTenantEmail(customer.getEmail());

            model.addAttribute("customer", customer);
            model.addAttribute("pageTitle", "Maintenance Requests");
            // model.addAttribute("maintenanceRequests", requests);

            return "tenant/maintenance";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading maintenance requests: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Rental Payments - View payment history and make payments
     */
    @GetMapping("/payments")
    public String rentalPayments(Model model, Authentication authentication) {
        try {
            Customer customer = getAuthenticatedTenant(authentication);
            if (customer == null) {
                return "redirect:/customer-login?error=not_found";
            }

            Tenant tenantEntity = tenantService.findByEmailAddress(customer.getEmail()).orElse(null);
            Property property = null;
            
            if (tenantEntity != null) {
                property = tenantEntity.getProperty();
            }

            // TODO: Implement payment service
            // List<Payment> payments = paymentService.findByTenantEmail(customer.getEmail());

            model.addAttribute("customer", customer);
            model.addAttribute("tenant", tenantEntity);
            model.addAttribute("property", property);
            model.addAttribute("pageTitle", "Rental Payments");
            // model.addAttribute("payments", payments);

            return "tenant/payments";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading payment information: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Documents - View lease agreements and other documents
     */
    @GetMapping("/documents")
    public String viewDocuments(Model model, Authentication authentication) {
        try {
            Customer customer = getAuthenticatedTenant(authentication);
            if (customer == null) {
                return "redirect:/customer-login?error=not_found";
            }

            // TODO: Implement document service
            // List<Document> documents = documentService.findByTenantEmail(customer.getEmail());

            model.addAttribute("customer", customer);
            model.addAttribute("pageTitle", "My Documents");
            // model.addAttribute("documents", documents);

            return "tenant/documents";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading documents: " + e.getMessage());
            return "error/500";
        }
    }

    // ===== EMPLOYEE TENANT MANAGEMENT =====

    /**
     * Create Tenant Form - GET /employee/tenant/create-tenant
     * This method provides the proper Tenant entity and properties list for the template
     */
    @GetMapping("/employee/tenant/create-tenant")
    public String showCreateTenantForm(Model model, Authentication authentication) {
        try {
            // Create new Tenant entity (not Customer)
            Tenant tenant = new Tenant();
            tenant.setAccountType(AccountType.INDIVIDUAL); // Default
            tenant.setCountry("UK"); // Default
            tenant.setNotifyEmail("Y"); // Default
            tenant.setNotifyText("Y"); // Default
            
            // Get all properties for the dropdown - this was missing!
            List<Property> properties = propertyService.findAll();
            
            // Add all required model attributes that the template expects
            model.addAttribute("tenant", tenant);
            model.addAttribute("properties", properties);
            model.addAttribute("selectedProperty", null);
            model.addAttribute("pageTitle", "Create New Tenant");
            model.addAttribute("isEdit", false);
            
            return "employee/tenant/create-tenant"; // Correct template path
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading create tenant form: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Create Tenant - POST /employee/tenant/create-tenant
     * This method handles the form submission with proper Tenant entity
     */
    @PostMapping("/employee/tenant/create-tenant")
    public String createTenant(@ModelAttribute Tenant tenant, 
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            // Set audit fields
            tenant.setCreatedAt(LocalDateTime.now());
            tenant.setUpdatedAt(LocalDateTime.now());
            
            // Generate PayProp customer ID if not set
            if (tenant.getPayPropCustomerId() == null || tenant.getPayPropCustomerId().isEmpty()) {
                tenant.setPayPropCustomerId("TENANT_" + System.currentTimeMillis());
            }
            
            // Set default status
            if (tenant.getStatus() == null || tenant.getStatus().isEmpty()) {
                tenant.setStatus("Active");
            }
            
            // Set default tenancy status
            if (tenant.getTenancyStatus() == null || tenant.getTenancyStatus().isEmpty()) {
                tenant.setTenancyStatus("active");
            }
            
            // Save the tenant using TenantService (not CustomerService)
            Tenant savedTenant = tenantService.save(tenant);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Tenant " + savedTenant.getFullName() + " created successfully!");
            
            // Redirect to tenant list (in CustomerController)
            return "redirect:/employee/customer/tenants";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error creating tenant: " + e.getMessage());
            return "redirect:/employee/tenant/create-tenant";
        }
    }

    /**
     * Edit Tenant Form - GET /employee/tenant/{id}/edit
     * Allow editing existing tenants
     */
    @GetMapping("/employee/tenant/{id}/edit")
    public String editTenantForm(@PathVariable Long id, Model model, Authentication authentication) {
        try {
            Tenant tenant = tenantService.findById(id);
            if (tenant == null) {
                return "error/not-found";
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
     * Update Tenant - POST /employee/tenant/{id}/edit
     * Handle tenant updates
     */
    @PostMapping("/employee/tenant/{id}/edit")
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
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Tenant " + savedTenant.getFullName() + " updated successfully!");
            
            return "redirect:/employee/customer/tenants";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error updating tenant: " + e.getMessage());
            return "redirect:/employee/tenant/" + id + "/edit";
        }
    }

    /**
     * View Tenant Details - GET /employee/tenant/{id}
     * Display detailed tenant information
     */
    @GetMapping("/employee/tenant/{id}")
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
     * Delete Tenant - POST /employee/tenant/{id}/delete
     * Soft delete or archive a tenant
     */
    @PostMapping("/employee/tenant/{id}/delete")
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

    // ===== HELPER METHODS =====

    /**
     * Get the authenticated tenant customer from the customer portal
     */
    private Customer getAuthenticatedTenant(Authentication authentication) {
        try {
            String email = authentication.getName();
            CustomerLoginInfo loginInfo = customerLoginInfoService.findByEmail(email);
            
            if (loginInfo == null) {
                return null;
            }
            
            Customer customer = loginInfo.getCustomer();
            if (customer == null) {
                return null;
            }
            
            // Verify this is actually a tenant
            if (!Boolean.TRUE.equals(customer.getIsTenant()) && 
                (customer.getCustomerType() == null || !customer.getCustomerType().toString().equals("TENANT"))) {
                return null;
            }
            
            return customer;
            
        } catch (Exception e) {
            return null;
        }
    }
}