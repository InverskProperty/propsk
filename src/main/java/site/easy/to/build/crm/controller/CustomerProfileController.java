package site.easy.to.build.crm.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.google.model.gmail.Attachment;
import site.easy.to.build.crm.service.contract.ContractService;
import site.easy.to.build.crm.service.customer.CustomerLoginInfoService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.file.FileService;
import site.easy.to.build.crm.service.lead.LeadService;
import site.easy.to.build.crm.service.ticket.TicketService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.service.financial.UnifiedFinancialDataService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequestMapping("/customer")
public class CustomerProfileController {
    private final CustomerService customerService;
    private final AuthenticationUtils authenticationUtils;
    private final CustomerLoginInfoService customerLoginInfoService;
    private final UserService userService;
    private final TicketService ticketService;
    private final ContractService contractService;
    private final LeadService leadService;
    private final FileService fileService;
    private final UnifiedFinancialDataService unifiedFinancialDataService;
    private final PropertyService propertyService;

    public CustomerProfileController(CustomerService customerService, AuthenticationUtils authenticationUtils, CustomerLoginInfoService customerLoginInfoService, UserService userService, TicketService ticketService, ContractService contractService, LeadService leadService, FileService fileService, UnifiedFinancialDataService unifiedFinancialDataService, PropertyService propertyService) {
        this.customerService = customerService;
        this.authenticationUtils = authenticationUtils;
        this.customerLoginInfoService = customerLoginInfoService;
        this.userService = userService;
        this.ticketService = ticketService;
        this.contractService = contractService;
        this.leadService = leadService;
        this.fileService = fileService;
        this.unifiedFinancialDataService = unifiedFinancialDataService;
        this.propertyService = propertyService;
    }

    @GetMapping("/profile")
    public String showProfileInfo(Model model, Authentication authentication) {
        Customer customer;
        try {
            int customerId = authenticationUtils.getLoggedInUserId(authentication);
            CustomerLoginInfo customerLoginInfo = customerLoginInfoService.findById(customerId);
            customer = customerService.findByEmail(customerLoginInfo.getEmail());
        } catch (RuntimeException e) {
            return "error/not-found";
        }
        model.addAttribute("customer", customer);
        model.addAttribute("settingsTab", false);
        return "customer-profile";
    }

    @PostMapping("/profile/update")
    @Transactional
    public String updateUser(@ModelAttribute("customer") @Validated(Customer.CustomerUpdateValidationGroupInclusion.class) Customer customer,
                             BindingResult bindingResult, Authentication authentication, @RequestParam("userId") int userId, Model model) {

        if(bindingResult.hasErrors()) {
            int customerId = authenticationUtils.getLoggedInUserId(authentication);
            CustomerLoginInfo customerLoginInfo = customerLoginInfoService.findById(customerId);
            Customer currentCustomer = customerService.findByEmail(customerLoginInfo.getEmail());
            customer.setCustomerId(currentCustomer.getCustomerId());
            customer.setUser(currentCustomer.getUser());
            customer.setCustomerLoginInfo(customerLoginInfo);

            model.addAttribute("settingsTab", true);
            return "customer-profile";
        }

        int customerId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(Long.valueOf(userId));

        CustomerLoginInfo customerLoginInfo = customerLoginInfoService.findById(customerId);
        if(user == null || !Objects.equals(customerLoginInfo.getCustomer().getUser().getId(), user.getId())) {
            return "error/400";
        }

        customer.setCustomerLoginInfo(customerLoginInfo);
        customer.setEmail(customerLoginInfo.getEmail());
        customer.setUser(user);
        customerService.save(customer);
        return "redirect:/customer/profile";
    }

    @GetMapping("/my-tickets")
    public String showMyTickets(Model model, Authentication authentication){
        int customerId = authenticationUtils.getLoggedInUserId(authentication);
        CustomerLoginInfo customerLoginInfo = customerLoginInfoService.findById(customerId);
        Customer customer = customerService.findByEmail(customerLoginInfo.getEmail());
        List<Ticket> tickets = ticketService.findCustomerTickets(customer.getCustomerId().intValue());

        model.addAttribute("tickets", tickets);
        return "customer-info/my-tickets";
    }

    @GetMapping("/my-leads")
    public String showMyLeads(Model model, Authentication authentication){
        int customerId = authenticationUtils.getLoggedInUserId(authentication);
        CustomerLoginInfo customerLoginInfo = customerLoginInfoService.findById(customerId);
        Customer customer = customerService.findByEmail(customerLoginInfo.getEmail());
        List<Lead> leads = leadService.getCustomerLeads(customer.getCustomerId().intValue());

        model.addAttribute("leads", leads);
        return "customer-info/my-leads";
    }

    @GetMapping("/my-contracts")
    public String showMyContracts(Model model, Authentication authentication){
        int customerId = authenticationUtils.getLoggedInUserId(authentication);
        CustomerLoginInfo customerLoginInfo = customerLoginInfoService.findById(customerId);
        Customer customer = customerService.findByEmail(customerLoginInfo.getEmail());
        List<Contract> contracts = contractService.getCustomerContracts(customer.getCustomerId().intValue());

        model.addAttribute("contracts", contracts);
        return "customer-info/my-contracts";
    }

    @GetMapping("/ticket/{id}")
    public String showTicketDetails(@PathVariable("id") int id, Model model) {
        Ticket ticket = ticketService.findByTicketId(id);
        if (ticket == null) {
            return "error/not-found";
        }
        model.addAttribute("ticket",ticket);
        return "customer-info/ticket-detail";
    }

    @GetMapping("/lead/{id}")
    public String showLeadDetails(@PathVariable("id") int id, Model model) {
        Lead lead = leadService.findByLeadId(id);

        if (lead == null) {
            return "error/not-found";
        }

        List<File> files = lead.getFiles();
        List<Attachment> attachments = new ArrayList<>();
        for (File file : files) {
            String base64Data = Base64.getEncoder().encodeToString(file.getFileData());
            Attachment attachment = new Attachment(file.getFileName(), base64Data, file.getFileType());
            attachments.add(attachment);
        }
        model.addAttribute("lead",lead);
        model.addAttribute("attachments",attachments);
        return "customer-info/lead-detail";
    }
    @GetMapping("/contract/{id}")
    public String showContractDetails(@PathVariable("id") int id, Model model) {
        Contract contract = contractService.findByContractId(id);

        if (contract == null) {
            return "error/not-found";
        }

        List<File> files = contract.getFiles();
        List<Attachment> attachments = new ArrayList<>();
        for (File file : files) {
            String base64Data = Base64.getEncoder().encodeToString(file.getFileData());
            Attachment attachment = new Attachment(file.getFileName(), base64Data, file.getFileType());
            attachments.add(attachment);
        }
        model.addAttribute("contract",contract);
        model.addAttribute("attachments",attachments);
        return "customer-info/contract-detail";
    }

    /**
     * Get financial summary for customer's properties
     * GET /customer/my-properties/financial
     */
    @GetMapping("/my-properties/financial")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMyPropertiesFinancial(Authentication authentication) {
        try {
            int customerId = authenticationUtils.getLoggedInUserId(authentication);
            CustomerLoginInfo customerLoginInfo = customerLoginInfoService.findById(customerId);
            Customer customer = customerService.findByEmail(customerLoginInfo.getEmail());

            if (customer == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Get all properties for this customer (owner or delegated)
            List<Property> properties = propertyService.findPropertiesByCustomerAssignments(customer.getCustomerId());

            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> propertyFinancials = new ArrayList<>();

            for (Property property : properties) {
                Map<String, Object> propertySummary = unifiedFinancialDataService.getPropertyFinancialSummary(property);
                propertySummary.put("propertyId", property.getId());
                propertySummary.put("propertyName", property.getPropertyName());
                propertySummary.put("propertyType", property.getPropertyType());
                propertySummary.put("postcode", property.getPostcode());
                propertyFinancials.add(propertySummary);
            }

            response.put("success", true);
            response.put("properties", propertyFinancials);
            response.put("totalProperties", properties.size());
            response.put("customerName", customer.getFullName());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error loading financial data");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get financial summary for a specific property (customer must own it)
     * GET /customer/property/{id}/financial
     */
    @GetMapping("/property/{id}/financial")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPropertyFinancial(@PathVariable("id") Long propertyId, Authentication authentication) {
        try {
            int customerId = authenticationUtils.getLoggedInUserId(authentication);
            CustomerLoginInfo customerLoginInfo = customerLoginInfoService.findById(customerId);
            Customer customer = customerService.findByEmail(customerLoginInfo.getEmail());

            if (customer == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Get all properties for this customer to verify access
            List<Property> customerProperties = propertyService.findPropertiesByCustomerAssignments(customer.getCustomerId());
            Property property = customerProperties.stream()
                .filter(p -> p.getId().equals(propertyId))
                .findFirst()
                .orElse(null);

            if (property == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You do not have access to this property"));
            }

            // Get unified financial summary
            Map<String, Object> financialSummary = unifiedFinancialDataService.getPropertyFinancialSummary(property);
            financialSummary.put("propertyId", property.getId());
            financialSummary.put("propertyName", property.getPropertyName());
            financialSummary.put("propertyType", property.getPropertyType());
            financialSummary.put("fullAddress", String.join(", ",
                property.getAddressLine1() != null ? property.getAddressLine1() : "",
                property.getCity() != null ? property.getCity() : "",
                property.getPostcode() != null ? property.getPostcode() : ""
            ));

            return ResponseEntity.ok(financialSummary);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error loading property financial data");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
