package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.TenantService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EmployeeTenantController - Employee-facing tenant management
 * Handles CRUD operations for tenants from the employee perspective
 */
@Controller
@RequestMapping("/employee/tenant")
public class EmployeeTenantController {

    private final TenantService tenantService;
    private final PropertyService propertyService;

    @Autowired
    public EmployeeTenantController(TenantService tenantService, PropertyService propertyService) {
        this.tenantService = tenantService;
        this.propertyService = propertyService;
    }

    /**
     * Create Tenant Form - GET /employee/tenant/create-tenant
     */
    @GetMapping("/create-tenant")
    public String showCreateTenantForm(Model model, Authentication authentication) {
        try {
            // Create new Tenant entity
            Tenant tenant = new Tenant();
            tenant.setAccountType(AccountType.INDIVIDUAL); // Default
            tenant.setCountry("UK"); // Default
            tenant.setNotifyEmail("Y"); // Default
            tenant.setNotifyText("Y"); // Default
            
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
     * Create Tenant - POST /employee/tenant/create-tenant
     */
    @PostMapping("/create-tenant")
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
            
            // Save the tenant
            Tenant savedTenant = tenantService.save(tenant);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Tenant " + savedTenant.getFullName() + " created successfully!");
            
            // Redirect to tenant list
            return "redirect:/employee/customer/tenants";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error creating tenant: " + e.getMessage());
            return "redirect:/employee/tenant/create-tenant";
        }
    }

    /**
     * Edit Tenant Form - GET /employee/tenant/{id}/edit
     */
    @GetMapping("/{id}/edit")
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
     * Delete Tenant - POST /employee/tenant/{id}/delete
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