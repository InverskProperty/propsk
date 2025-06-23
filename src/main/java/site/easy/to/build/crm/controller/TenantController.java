package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerLoginInfo;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.Tenant;
import site.easy.to.build.crm.service.customer.CustomerLoginInfoService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.TenantService;

import java.util.List;

/**
 * TenantController - Customer-facing dashboard for tenants
 * Handles tenant authentication and their tenancy portal view
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

    // ===== HELPER METHODS =====

    /**
     * Get the authenticated tenant customer
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