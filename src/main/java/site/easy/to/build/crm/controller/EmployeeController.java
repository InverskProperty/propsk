package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.TenantService;
import site.easy.to.build.crm.service.contractor.ContractorService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.PropertyOwnerService;
import site.easy.to.build.crm.service.ticket.TicketService;
import site.easy.to.build.crm.service.email.EmailService;
import site.easy.to.build.crm.util.AuthorizationUtil;
import site.easy.to.build.crm.repository.TenantRepository;
import site.easy.to.build.crm.repository.PropertyOwnerRepository;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Employee Controller - Handles all /employee/** routes for dashboard functionality
 * Accessible to users with MANAGER or EMPLOYEE roles
 */
@Controller
@RequestMapping("/employee")
public class EmployeeController {

    private final CustomerService customerService;
    private final TenantService tenantService;
    private final ContractorService contractorService;
    private final PropertyService propertyService;
    private final PropertyOwnerService propertyOwnerService;
    private final TicketService ticketService;
    private final EmailService emailService;
    
    // Add repositories for Spring Data's findAllById method
    private final TenantRepository tenantRepository;
    private final PropertyOwnerRepository propertyOwnerRepository;

    @Autowired
    public EmployeeController(CustomerService customerService,
                             TenantService tenantService,
                             ContractorService contractorService,
                             PropertyService propertyService,
                             PropertyOwnerService propertyOwnerService,
                             TicketService ticketService,
                             EmailService emailService,
                             TenantRepository tenantRepository,
                             PropertyOwnerRepository propertyOwnerRepository) {
        this.customerService = customerService;
        this.tenantService = tenantService;
        this.contractorService = contractorService;
        this.propertyService = propertyService;
        this.propertyOwnerService = propertyOwnerService;
        this.ticketService = ticketService;
        this.emailService = emailService;
        this.tenantRepository = tenantRepository;
        this.propertyOwnerRepository = propertyOwnerRepository;
    }

    // ===== EMPLOYEE DASHBOARD =====
    
    /**
     * Employee Dashboard - Main landing page
     */
    @GetMapping("/dashboard")
    public String employeeDashboard(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        // Use EXISTING method names
        model.addAttribute("totalProperties", propertyService.getTotalProperties());
        model.addAttribute("totalTenants", tenantService.getTotalTenants());
        model.addAttribute("totalOwners", propertyOwnerService.getTotalPropertyOwners());
        model.addAttribute("totalContractors", contractorService.getTotalCount());
        model.addAttribute("activeTickets", ticketService.getActiveTicketCount());

        return "employee/dashboard";
    }

    // ===== TENANT MANAGEMENT =====

    /**
     * Show tenant creation form
     */
    @GetMapping("/tenant/create-tenant")
    public String showCreateTenantForm(@RequestParam(required = false) Long propertyId, 
                                      Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        model.addAttribute("tenant", new Tenant());
        model.addAttribute("properties", propertyService.findAll());
        
        if (propertyId != null) {
            Property property = propertyService.findById(propertyId);
            model.addAttribute("selectedProperty", property);
        }

        return "employee/tenant/create-tenant";
    }

    /**
     * Process tenant creation
     */
    @PostMapping("/tenant/create-tenant")
    public String createTenant(@Valid @ModelAttribute Tenant tenant,
                              BindingResult result,
                              RedirectAttributes redirectAttributes,
                              Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        if (result.hasErrors()) {
            return "employee/tenant/create-tenant";
        }

        try {
            tenantService.save(tenant);
            redirectAttributes.addFlashAttribute("successMessage", "Tenant created successfully");
            return "redirect:/employee/customer/tenants";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error creating tenant: " + e.getMessage());
            return "redirect:/employee/tenant/create-tenant";
        }
    }

    // ===== CUSTOMER MANAGEMENT =====

    /**
     * List all tenants
     */
    @GetMapping("/customer/tenants")
    public String listTenants(@RequestParam(required = false) String status,
                             Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        List<Tenant> tenants;
        if ("active".equals(status)) {
            tenants = tenantService.findByStatus("active");
        } else {
            tenants = tenantService.findAll();
        }

        model.addAttribute("tenants", tenants);
        model.addAttribute("statusFilter", status);
        return "employee/customer/tenants";
    }

    /**
     * List property owners
     */
    @GetMapping("/customer/property-owners")
    public String listPropertyOwners(@RequestParam(required = false) String owner,
                                   Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        List<PropertyOwner> owners;
        if (owner != null && !owner.isEmpty()) {
            owners = propertyOwnerService.findByNameContaining(owner);
        } else {
            owners = propertyOwnerService.findAll();
        }

        model.addAttribute("propertyOwners", owners);
        model.addAttribute("ownerFilter", owner);
        return "employee/customer/property-owners";
    }

    /**
     * List contractors
     */
    @GetMapping("/customer/contractors")
    public String listContractors(@RequestParam(required = false) String preferred,
                                 @RequestParam(required = false) String emergency,
                                 Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        List<Contractor> contractors;
        
        if ("true".equals(preferred)) {
            contractors = contractorService.findPreferredContractors();
        } else if ("true".equals(emergency)) {
            contractors = contractorService.findEmergencyContractors();
        } else {
            contractors = contractorService.findAll();
        }

        model.addAttribute("contractors", contractors);
        model.addAttribute("preferredFilter", preferred);
        model.addAttribute("emergencyFilter", emergency);
        return "employee/customer/contractors";
    }

    /**
     * Show tenant creation form (alternative route)
     */
    @GetMapping("/customer/create-tenant")
    public String showCreateTenantFormAlt(Model model, Authentication authentication) {
        return showCreateTenantForm(null, model, authentication);
    }

    /**
     * Show contractor creation form
     */
    @GetMapping("/customer/create-contractor")
    public String showCreateContractorForm(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        model.addAttribute("contractor", new Contractor());
        return "employee/customer/create-contractor";
    }

    /**
     * Process contractor creation
     */
    @PostMapping("/customer/create-contractor")
    public String createContractor(@Valid @ModelAttribute Contractor contractor,
                                  BindingResult result,
                                  RedirectAttributes redirectAttributes,
                                  Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        if (result.hasErrors()) {
            return "employee/customer/create-contractor";
        }

        try {
            contractorService.save(contractor);
            redirectAttributes.addFlashAttribute("successMessage", "Contractor created successfully");
            return "redirect:/employee/customer/contractors";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error creating contractor: " + e.getMessage());
            return "redirect:/employee/customer/create-contractor";
        }
    }

    // ===== EMAIL MANAGEMENT =====

    /**
     * Email property owners page
     */
    @GetMapping("/customer/email-property-owners")
    public String emailPropertyOwnersForm(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        model.addAttribute("propertyOwners", propertyOwnerService.findAll());
        return "employee/customer/email-property-owners";
    }

    /**
     * Send email to property owners
     */
    @PostMapping("/customer/email-property-owners")
    public String sendEmailToPropertyOwners(@RequestParam String subject,
                                          @RequestParam String message,
                                          @RequestParam(required = false) List<Long> ownerIds,
                                          RedirectAttributes redirectAttributes,
                                          Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        try {
            emailService.sendBulkEmailToPropertyOwners(subject, message, ownerIds);
            redirectAttributes.addFlashAttribute("successMessage", "Emails sent successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error sending emails: " + e.getMessage());
        }

        return "redirect:/employee/customer/email-property-owners";
    }

    /**
     * Email tenants page
     */
    @GetMapping("/customer/email-tenants")
    public String emailTenantsForm(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        model.addAttribute("tenants", tenantService.findAll());
        return "employee/customer/email-tenants";
    }

    /**
     * Send email to tenants
     */
    @PostMapping("/customer/email-tenants")
    public String sendEmailToTenants(@RequestParam String subject,
                                   @RequestParam String message,
                                   @RequestParam(required = false) List<Long> tenantIds,
                                   RedirectAttributes redirectAttributes,
                                   Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        try {
            emailService.sendBulkEmailToTenants(subject, message, tenantIds);
            redirectAttributes.addFlashAttribute("successMessage", "Emails sent successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error sending emails: " + e.getMessage());
        }

        return "redirect:/employee/customer/email-tenants";
    }

    // ===== TICKET MANAGEMENT =====

    /**
     * Show all tickets for managers
     */
    @GetMapping("/ticket/manager/all-tickets")
    public String showAllTickets(@RequestParam(required = false) String type,
                                Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/access-denied";
        }

        List<Ticket> tickets;
        
        if ("maintenance".equals(type)) {
            tickets = ticketService.findByType("maintenance");
        } else {
            tickets = ticketService.findAll();
        }

        model.addAttribute("tickets", tickets);
        model.addAttribute("typeFilter", type);
        return "employee/ticket/manager/all-tickets";
    }

    /**
     * Show contractor bids
     */
    @GetMapping("/ticket/contractor-bids")
    public String showContractorBids(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        // This would depend on your ticket/bid system implementation
        model.addAttribute("bids", ticketService.findAllBids());
        return "employee/ticket/contractor-bids";
    }

    // ===== AJAX/API ENDPOINTS =====

    /**
     * Get tenant data for AJAX requests
     */
    @GetMapping("/api/tenants")
    @ResponseBody
    public ResponseEntity<List<Tenant>> getTenantsApi(@RequestParam(required = false) String status,
                                                     Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Tenant> tenants;
        if ("active".equals(status)) {
            tenants = tenantService.findByStatus("active");
        } else {
            tenants = tenantService.findAll();
        }

        return ResponseEntity.ok(tenants);
    }

    /**
     * Get property owners data for AJAX requests
     */
    @GetMapping("/api/property-owners")
    @ResponseBody
    public ResponseEntity<List<PropertyOwner>> getPropertyOwnersApi(Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(propertyOwnerService.findAll());
    }

    /**
     * Get contractors data for AJAX requests
     */
    @GetMapping("/api/contractors")
    @ResponseBody
    public ResponseEntity<List<Contractor>> getContractorsApi(@RequestParam(required = false) String type,
                                                             Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Contractor> contractors;
        if ("preferred".equals(type)) {
            contractors = contractorService.findPreferredContractors();
        } else if ("emergency".equals(type)) {
            contractors = contractorService.findEmergencyContractors();
        } else {
            contractors = contractorService.findAll();
        }

        return ResponseEntity.ok(contractors);
    }

    // ===== ERROR HANDLING =====

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, Model model) {
        model.addAttribute("errorMessage", "An error occurred: " + e.getMessage());
        return "error/500";
    }
}