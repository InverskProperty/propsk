package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerLoginInfo;
import site.easy.to.build.crm.entity.Contractor;
import site.easy.to.build.crm.service.customer.CustomerLoginInfoService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.contractor.ContractorService;

import java.math.BigDecimal;
import java.util.List;

/**
 * ContractorController - Customer-facing dashboard for contractors
 * Handles contractor authentication and their job management portal
 */
@Controller
@RequestMapping("/contractor")
public class ContractorController {

    private final ContractorService contractorService;
    private final CustomerService customerService;
    private final CustomerLoginInfoService customerLoginInfoService;

    @Autowired
    public ContractorController(ContractorService contractorService,
                               CustomerService customerService,
                               CustomerLoginInfoService customerLoginInfoService) {
        this.contractorService = contractorService;
        this.customerService = customerService;
        this.customerLoginInfoService = customerLoginInfoService;
    }

    /**
     * Contractor Dashboard - Main landing page after login
     */
    @GetMapping("/dashboard")
    public String contractorDashboard(Model model, Authentication authentication) {
        System.out.println("=== DEBUG: ContractorController.contractorDashboard ===");
        System.out.println("DEBUG: Authentication: " + authentication.getName());
        
        model.addAttribute("pageTitle", "Contractor Dashboard");
        return "contractor/dashboard";
    }

    /**
     * Contractor Jobs - View assigned jobs and their status
     */
    @GetMapping("/jobs")
    public String viewJobs(@RequestParam(value = "status", required = false) String status,
                          Model model, Authentication authentication) {
        try {
            Customer customer = getAuthenticatedContractor(authentication);
            if (customer == null) {
                return "redirect:/customer-login?error=not_found";
            }

            // TODO: Implement job service
            // List<Job> jobs = jobService.findByContractorEmail(customer.getEmail());
            
            // if (status != null && !status.isEmpty()) {
            //     jobs = jobs.stream()
            //         .filter(job -> status.equalsIgnoreCase(job.getStatus()))
            //         .collect(Collectors.toList());
            // }

            model.addAttribute("customer", customer);
            model.addAttribute("statusFilter", status);
            model.addAttribute("pageTitle", "My Jobs");
            // model.addAttribute("jobs", jobs);

            return "contractor/jobs";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading jobs: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Contractor Profile - View and edit profile
     */
    @GetMapping("/profile")
    public String viewProfile(Model model, Authentication authentication) {
        try {
            Customer customer = getAuthenticatedContractor(authentication);
            if (customer == null) {
                return "redirect:/customer-login?error=not_found";
            }

            Contractor contractorEntity = contractorService.findByEmailAddress(customer.getEmail())
                .orElse(null);

            model.addAttribute("customer", customer);
            model.addAttribute("contractor", contractorEntity);
            model.addAttribute("pageTitle", "My Profile");

            return "contractor/profile";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading profile: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Contractor Invoices - View and manage invoices
     */
    @GetMapping("/invoices")
    public String viewInvoices(Model model, Authentication authentication) {
        try {
            Customer customer = getAuthenticatedContractor(authentication);
            if (customer == null) {
                return "redirect:/customer-login?error=not_found";
            }

            // TODO: Implement invoice service
            // List<Invoice> invoices = invoiceService.findByContractorEmail(customer.getEmail());

            model.addAttribute("customer", customer);
            model.addAttribute("pageTitle", "My Invoices");
            // model.addAttribute("invoices", invoices);

            return "contractor/invoices";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading invoices: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Contractor Payments - View payment history
     */
    @GetMapping("/payments")
    public String viewPayments(Model model, Authentication authentication) {
        try {
            Customer customer = getAuthenticatedContractor(authentication);
            if (customer == null) {
                return "redirect:/customer-login?error=not_found";
            }

            // TODO: Implement payment service
            // List<Payment> payments = paymentService.findByContractorEmail(customer.getEmail());

            model.addAttribute("customer", customer);
            model.addAttribute("pageTitle", "Payment History");
            // model.addAttribute("payments", payments);

            return "contractor/payments";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading payment history: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Contractor Availability - Manage availability and schedule
     */
    @GetMapping("/availability")
    public String manageAvailability(Model model, Authentication authentication) {
        try {
            Customer customer = getAuthenticatedContractor(authentication);
            if (customer == null) {
                return "redirect:/customer-login?error=not_found";
            }

            Contractor contractorEntity = contractorService.findByEmailAddress(customer.getEmail())
                .orElse(null);

            model.addAttribute("customer", customer);
            model.addAttribute("contractor", contractorEntity);
            model.addAttribute("pageTitle", "Availability Management");

            return "contractor/availability";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading availability: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Contractor Certifications - View and manage certifications
     */
    @GetMapping("/certifications")
    public String viewCertifications(Model model, Authentication authentication) {
        try {
            Customer customer = getAuthenticatedContractor(authentication);
            if (customer == null) {
                return "redirect:/customer-login?error=not_found";
            }

            Contractor contractorEntity = contractorService.findByEmailAddress(customer.getEmail())
                .orElse(null);

            model.addAttribute("customer", customer);
            model.addAttribute("contractor", contractorEntity);
            model.addAttribute("pageTitle", "My Certifications");

            // Add certification status checks
            if (contractorEntity != null) {
                model.addAttribute("isGasSafeCertified", contractorEntity.isGasSafeCertified());
                model.addAttribute("isNiceicCertified", contractorEntity.isNiceicCertified());
                model.addAttribute("isInsuranceValid", contractorEntity.isInsuranceValid());
            }

            return "contractor/certifications";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading certifications: " + e.getMessage());
            return "error/500";
        }
    }

    // ===== HELPER METHODS =====

    /**
     * Get the authenticated contractor customer
     */
    private Customer getAuthenticatedContractor(Authentication authentication) {
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
            
            // Verify this is actually a contractor
            if (!Boolean.TRUE.equals(customer.getIsContractor()) && 
                (customer.getCustomerType() == null || !customer.getCustomerType().toString().equals("CONTRACTOR"))) {
                return null;
            }
            
            return customer;
            
        } catch (Exception e) {
            return null;
        }
    }
}