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
import site.easy.to.build.crm.service.email.EmailService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;
import site.easy.to.build.crm.util.EmailTokenUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employee/customer")
public class CustomerController {

    private final CustomerService customerService;
    private final UserService userService;
    private final CustomerLoginInfoService customerLoginInfoService;
    private final AuthenticationUtils authenticationUtils;
    private final EmailService emailService;

    @Autowired
    public CustomerController(CustomerService customerService, UserService userService, 
                              CustomerLoginInfoService customerLoginInfoService,
                              AuthenticationUtils authenticationUtils, 
                              EmailService emailService) {
        this.customerService = customerService;
        this.userService = userService;
        this.customerLoginInfoService = customerLoginInfoService;
        this.authenticationUtils = authenticationUtils;
        this.emailService = emailService;
    }

    // ===== MAIN CUSTOMER DASHBOARD =====
    
    @GetMapping("/dashboard")
    public String customerDashboard(Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            // Get statistics for all customer types using the WORKING repository methods
            List<Customer> propertyOwners = customerService.findPropertyOwners();
            List<Customer> tenants = customerService.findTenants();
            List<Customer> contractors = customerService.findContractors();
            
            // Filter by user's customers if not manager
            if (!user.getRoles().stream().anyMatch(r -> r.getName().contains("MANAGER"))) {
                List<Customer> userCustomers = customerService.findByUserId(userId);
                propertyOwners = propertyOwners.stream()
                    .filter(userCustomers::contains)
                    .collect(Collectors.toList());
                tenants = tenants.stream()
                    .filter(userCustomers::contains)
                    .collect(Collectors.toList());
                contractors = contractors.stream()
                    .filter(userCustomers::contains)
                    .collect(Collectors.toList());
            }
            
            // Add counts to model
            model.addAttribute("totalCustomers", propertyOwners.size() + tenants.size() + contractors.size());
            model.addAttribute("propertyOwnersCount", propertyOwners.size());
            model.addAttribute("tenantsCount", tenants.size());
            model.addAttribute("contractorsCount", contractors.size());
            
            // Recent activity
            model.addAttribute("recentPropertyOwners", propertyOwners.stream().limit(3).collect(Collectors.toList()));
            model.addAttribute("recentTenants", tenants.stream().limit(3).collect(Collectors.toList()));
            model.addAttribute("recentContractors", contractors.stream().limit(3).collect(Collectors.toList()));
            
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "Customer Management Dashboard");
            return "customer-dashboard";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading customer dashboard: " + e.getMessage());
            return "error/500";
        }
    }

    // ===== PROPERTY OWNERS SECTION =====
    
    @GetMapping("/property-owners")
    public String listPropertyOwners(@RequestParam(value = "search", required = false) String search,
                                   Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            List<Customer> propertyOwners = customerService.findPropertyOwners();
            
            // Apply search filter if provided
            if (search != null && !search.trim().isEmpty()) {
                propertyOwners = propertyOwners.stream()
                    .filter(c -> c.getName().toLowerCase().contains(search.toLowerCase()) ||
                                c.getEmail().toLowerCase().contains(search.toLowerCase()) ||
                                (c.getCity() != null && c.getCity().toLowerCase().contains(search.toLowerCase())))
                    .collect(Collectors.toList());
            }
            
            model.addAttribute("customers", propertyOwners);
            model.addAttribute("customerType", "Property Owner");
            model.addAttribute("pageTitle", "Property Owners");
            model.addAttribute("filterType", "property-owners");
            model.addAttribute("searchTerm", search);
            model.addAttribute("user", user);
            model.addAttribute("createUrl", "/employee/customer/create-property-owner");
            return "customer/customer-list";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading property owners: " + e.getMessage());
            return "error/500";
        }
    }

    @GetMapping("/create-property-owner")
    public String showCreatePropertyOwnerForm(Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            Customer customer = new Customer();
            customer.setIsPropertyOwner(true);
            customer.setCustomerType(CustomerType.PROPERTY_OWNER);
            
            model.addAttribute("customer", customer);
            model.addAttribute("customerType", "Property Owner");
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "Create Property Owner");
            model.addAttribute("submitUrl", "/employee/customer/create-property-owner");
            model.addAttribute("cancelUrl", "/employee/customer/property-owners");
            
            // Add missing attributes that the template expects
            model.addAttribute("isGoogleUser", false);
            model.addAttribute("hasGoogleGmailAccess", false);
            model.addAttribute("isEdit", false);
            
            return "customer/create-customer";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading create property owner form: " + e.getMessage());
            return "error/500";
        }
    }

    @PostMapping("/create-property-owner")
    public String createPropertyOwner(@ModelAttribute Customer customer, 
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            customer.setUser(user);
            customer.setIsPropertyOwner(true);
            customer.setCustomerType(CustomerType.PROPERTY_OWNER);
            customer.setCreatedAt(LocalDateTime.now());
            
            Customer savedCustomer = customerService.save(customer);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Property owner " + savedCustomer.getName() + " created successfully!");
            
            return "redirect:/employee/customer/property-owners";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error creating property owner: " + e.getMessage());
            return "redirect:/employee/customer/create-property-owner";
        }
    }

    // ===== EMAIL PROPERTY OWNERS =====
    
    @GetMapping("/email-property-owners")
    public String emailPropertyOwnersForm(Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            List<Customer> propertyOwners = customerService.findPropertyOwners();
            
            model.addAttribute("customers", propertyOwners);
            model.addAttribute("customerType", "Property Owners");
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "Email Property Owners");
            model.addAttribute("backUrl", "/employee/customer/property-owners");
            
            return "customer/email-form";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading email form: " + e.getMessage());
            return "error/500";
        }
    }

    // ===== TENANTS SECTION =====
    

    // Replace the existing listTenants() method in CustomerController.java (around lines 170-207)

    @GetMapping("/tenants")
    public String listTenants(@RequestParam(value = "search", required = false) String search,
                            @RequestParam(value = "status", required = false) String status,
                            Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            List<Customer> tenants = customerService.findTenants();
            
            // Apply search filter if provided
            if (search != null && !search.trim().isEmpty()) {
                tenants = tenants.stream()
                    .filter(c -> c.getName().toLowerCase().contains(search.toLowerCase()) ||
                                c.getEmail().toLowerCase().contains(search.toLowerCase()) ||
                                (c.getCity() != null && c.getCity().toLowerCase().contains(search.toLowerCase())))
                    .collect(Collectors.toList());
            }
            
            // Apply status filter if provided
            if (status != null && !status.trim().isEmpty()) {
                tenants = tenants.stream()
                    .filter(c -> status.equalsIgnoreCase("active") ? 
                        (c.getDescription() != null && c.getDescription().contains("Active")) : 
                        (c.getDescription() == null || !c.getDescription().contains("Active")))
                    .collect(Collectors.toList());
            }
            
            model.addAttribute("customers", tenants);
            model.addAttribute("customerType", "Tenant");
            model.addAttribute("pageTitle", "Tenants");
            model.addAttribute("filterType", "tenants");
            model.addAttribute("searchTerm", search);
            model.addAttribute("statusFilter", status);
            model.addAttribute("user", user);
            model.addAttribute("createUrl", "/employee/tenant/create-tenant"); // FIXED: Use tenant controller
            
            return "customer/customer-list";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading tenants: " + e.getMessage());
            return "error/500";
        }
    }

    // In CustomerController.java - Update the showCreateTenantForm method
    @GetMapping("/create-tenant")
    public String showCreateTenantForm(Model model, Authentication authentication) {
        // Redirect to the new employee tenant controller
        return "redirect:/employee/tenant/create-tenant";
    }

    @PostMapping("/create-tenant")
    public String createTenant(@ModelAttribute Customer customer, 
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        // This method can be removed or kept for legacy compatibility
        // Redirect to the proper TenantController method
        return "redirect:/employee/tenant/create-tenant";
    }

    // ===== EMAIL TENANTS =====
    
    @GetMapping("/email-tenants")
    public String emailTenantsForm(Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            List<Customer> tenants = customerService.findTenants();
            
            model.addAttribute("customers", tenants);
            model.addAttribute("customerType", "Tenants");
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "Email Tenants");
            model.addAttribute("backUrl", "/employee/customer/tenants");
            
            return "customer/email-form";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading email form: " + e.getMessage());
            return "error/500";
        }
    }

    // ===== CONTRACTORS SECTION =====
    
    @GetMapping("/contractors")
    public String listContractors(@RequestParam(value = "search", required = false) String search,
                                @RequestParam(value = "status", required = false) String status,
                                Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            List<Customer> contractors = customerService.findContractors();
            
            // Apply search filter if provided
            if (search != null && !search.trim().isEmpty()) {
                contractors = contractors.stream()
                    .filter(c -> c.getName().toLowerCase().contains(search.toLowerCase()) ||
                                c.getEmail().toLowerCase().contains(search.toLowerCase()) ||
                                (c.getCity() != null && c.getCity().toLowerCase().contains(search.toLowerCase())))
                    .collect(Collectors.toList());
            }
            
            // Apply status filter if provided
            if (status != null && !status.trim().isEmpty()) {
                contractors = contractors.stream()
                    .filter(c -> status.equalsIgnoreCase("preferred") ? 
                        (c.getDescription() != null && c.getDescription().contains("Preferred")) : 
                        (c.getDescription() == null || !c.getDescription().contains("Preferred")))
                    .collect(Collectors.toList());
            }
            
            model.addAttribute("customers", contractors);
            model.addAttribute("customerType", "Contractor");
            model.addAttribute("pageTitle", "Contractors");
            model.addAttribute("filterType", "contractors");
            model.addAttribute("searchTerm", search);
            model.addAttribute("statusFilter", status);
            model.addAttribute("user", user);
            model.addAttribute("createUrl", "/employee/customer/create-contractor");
            return "customer/customer-list";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading contractors: " + e.getMessage());
            return "error/500";
        }
    }

    @GetMapping("/create-contractor")
    public String showCreateContractorForm(Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            Customer customer = new Customer();
            customer.setIsContractor(true);
            customer.setCustomerType(CustomerType.CONTRACTOR);
            
            model.addAttribute("customer", customer);
            model.addAttribute("customerType", "Contractor");
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "Create Contractor");
            model.addAttribute("submitUrl", "/employee/customer/create-contractor");
            model.addAttribute("cancelUrl", "/employee/customer/contractors");
            
            // Add missing attributes that the template expects
            model.addAttribute("isGoogleUser", false);
            model.addAttribute("hasGoogleGmailAccess", false);
            model.addAttribute("isEdit", false);
            
            return "customer/create-customer";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading create contractor form: " + e.getMessage());
            return "error/500";
        }
    }

    @PostMapping("/create-contractor")
    public String createContractor(@ModelAttribute Customer customer, 
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            customer.setUser(user);
            customer.setIsContractor(true);
            customer.setCustomerType(CustomerType.CONTRACTOR);
            customer.setCreatedAt(LocalDateTime.now());
            
            Customer savedCustomer = customerService.save(customer);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Contractor " + savedCustomer.getName() + " created successfully!");
            
            return "redirect:/employee/customer/contractors";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error creating contractor: " + e.getMessage());
            return "redirect:/employee/customer/create-contractor";
        }
    }

    // ===== CUSTOMER DETAILS & MANAGEMENT =====
    
    @GetMapping("/{id:[0-9]+}")  // FIXED: Only match numeric IDs
    public String showCustomerDetail(@PathVariable("id") int id, Model model, Authentication authentication) {
        try {
            Customer customer = customerService.findByCustomerId(id);
            if (customer == null) {
                return "error/not-found";
            }

            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(userId);
            
            // Check if user can access this customer
            if (!customer.getUser().getId().equals(loggedInUser.getId()) && 
                !loggedInUser.getRoles().stream().anyMatch(r -> r.getName().contains("MANAGER"))) {
                return "redirect:/access-denied";
            }

            // Determine customer type for proper display
            String customerTypeDisplay = "Customer";
            String backUrl = "/employee/customer/dashboard";
            
            if (Boolean.TRUE.equals(customer.getIsPropertyOwner())) {
                customerTypeDisplay = "Property Owner";
                backUrl = "/employee/customer/property-owners";
            } else if (Boolean.TRUE.equals(customer.getIsTenant())) {
                customerTypeDisplay = "Tenant";
                backUrl = "/employee/customer/tenants";
            } else if (Boolean.TRUE.equals(customer.getIsContractor())) {
                customerTypeDisplay = "Contractor";
                backUrl = "/employee/customer/contractors";
            }

            model.addAttribute("customer", customer);
            model.addAttribute("customerTypeDisplay", customerTypeDisplay);
            model.addAttribute("backUrl", backUrl);
            model.addAttribute("user", loggedInUser);
            model.addAttribute("pageTitle", customerTypeDisplay + " Details");
            return "customer/customer-details";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading customer details: " + e.getMessage());
            return "error/500";
        }
    }

    // ===== EDIT CUSTOMER =====
    
    @GetMapping("/{id:[0-9]+}/edit")
    public String editCustomerForm(@PathVariable("id") int id, Model model, Authentication authentication) {
        try {
            Customer customer = customerService.findByCustomerId(id);
            if (customer == null) {
                return "error/not-found";
            }

            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(userId);
            
            // Check if user can access this customer
            if (customer.getUser() != null && 
                !customer.getUser().getId().equals(loggedInUser.getId()) && 
                !loggedInUser.getRoles().stream().anyMatch(r -> r.getName().contains("MANAGER"))) {
                return "redirect:/access-denied";
            }

            // Determine customer type for proper display
            String customerTypeDisplay = "Customer";
            String backUrl = "/employee/customer/dashboard";
            
            if (Boolean.TRUE.equals(customer.getIsPropertyOwner())) {
                customerTypeDisplay = "Property Owner";
                backUrl = "/employee/customer/property-owners";
            } else if (Boolean.TRUE.equals(customer.getIsTenant())) {
                customerTypeDisplay = "Tenant";
                backUrl = "/employee/customer/tenants";
            } else if (Boolean.TRUE.equals(customer.getIsContractor())) {
                customerTypeDisplay = "Contractor";
                backUrl = "/employee/customer/contractors";
            }

            model.addAttribute("customer", customer);
            model.addAttribute("customerTypeDisplay", customerTypeDisplay);
            model.addAttribute("customerType", customerTypeDisplay);
            model.addAttribute("backUrl", backUrl);
            model.addAttribute("user", loggedInUser);
            model.addAttribute("pageTitle", "Edit " + customerTypeDisplay);
            model.addAttribute("submitUrl", "/employee/customer/" + id + "/edit");
            model.addAttribute("cancelUrl", backUrl);
            model.addAttribute("isEdit", true);
            
            // Add missing attributes that the template expects
            model.addAttribute("isGoogleUser", false);
            model.addAttribute("hasGoogleGmailAccess", false);
            
            return "customer/create-customer";  // Reuse the create form for editing
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading customer for edit: " + e.getMessage());
            return "error/500";
        }
    }

    @PostMapping("/{id:[0-9]+}/edit")
    public String updateCustomer(@PathVariable("id") int id,
                               @ModelAttribute Customer customer,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            Customer existingCustomer = customerService.findByCustomerId(id);
            if (existingCustomer == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Customer not found");
                return "redirect:/employee/customer/dashboard";
            }
            
            // Update the customer
            customer.setCustomerId(id);
            customer.setUser(existingCustomer.getUser()); // Keep original user relationship
            customer.setCreatedAt(existingCustomer.getCreatedAt()); // Keep creation date
            
            // Preserve customer type flags
            customer.setIsPropertyOwner(existingCustomer.getIsPropertyOwner());
            customer.setIsTenant(existingCustomer.getIsTenant());
            customer.setIsContractor(existingCustomer.getIsContractor());
            customer.setCustomerType(existingCustomer.getCustomerType());
            
            Customer savedCustomer = customerService.save(customer);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Customer " + savedCustomer.getName() + " updated successfully!");
            
            // Redirect based on customer type
            if (Boolean.TRUE.equals(savedCustomer.getIsPropertyOwner())) {
                return "redirect:/employee/customer/property-owners";
            } else if (Boolean.TRUE.equals(savedCustomer.getIsTenant())) {
                return "redirect:/employee/customer/tenants";
            } else if (Boolean.TRUE.equals(savedCustomer.getIsContractor())) {
                return "redirect:/employee/customer/contractors";
            } else {
                return "redirect:/employee/customer/dashboard";
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error updating customer: " + e.getMessage());
            return "redirect:/employee/customer/" + id + "/edit";
        }
    }

    // REPLACE the methods I gave you earlier with these REDIRECT versions:

    @GetMapping("/my-customers")
    public String showMyCustomers(Authentication authentication) {
        // Redirect to existing dashboard until template is created
        return "redirect:/employee/customer/dashboard";
    }

    @GetMapping("/create-customer")
    public String showCreateCustomerForm(Authentication authentication) {
        // Redirect to create property owner as fallback
        return "redirect:/employee/customer/create-property-owner";
    }

    @GetMapping("/by-type")
    public String showCustomersByType(@RequestParam(value = "type", required = false) String type,
                                    Authentication authentication) {
        // Redirect based on type
        if ("property-owners".equals(type)) {
            return "redirect:/employee/customer/property-owners";
        } else if ("tenants".equals(type)) {
            return "redirect:/employee/customer/tenants";
        } else if ("contractors".equals(type)) {
            return "redirect:/employee/customer/contractors";
        } else {
            return "redirect:/employee/customer/dashboard";
        }
    }

    // ===== LOGIN MANAGEMENT FOR CUSTOMERS =====
    
    @PostMapping("/{id:[0-9]+}/create-login")
    public String createCustomerLogin(@PathVariable("id") int id,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        try {
            Customer customer = customerService.findByCustomerId(id);
            if (customer == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Customer not found");
                return "redirect:/employee/customer/dashboard";
            }
            
            // Check if customer already has login info
            if (customer.getCustomerLoginInfo() != null) {
                redirectAttributes.addFlashAttribute("warningMessage", 
                    "Customer already has login credentials");
                return "redirect:/employee/customer/" + id;
            }
            
            // Generate temporary password
            String tempPassword = EmailTokenUtils.generateRandomPassword();
            
            // Create customer login info
            CustomerLoginInfo loginInfo = new CustomerLoginInfo();
            loginInfo.setUsername(customer.getEmail());
            loginInfo.setPassword(EmailTokenUtils.encodePassword(tempPassword));
            loginInfo.setCustomer(customer);
            loginInfo.setCreatedAt(LocalDateTime.now());
            loginInfo.setToken(EmailTokenUtils.generateEmailToken());
            loginInfo.setTokenExpiresAt(EmailTokenUtils.createExpirationTime(24)); // 24 hours
            
            customerLoginInfoService.save(loginInfo);
            
            // Send type-specific welcome email
            String customerType = "customer";
            if (Boolean.TRUE.equals(customer.getIsPropertyOwner())) {
                customerType = "property owner";
            } else if (Boolean.TRUE.equals(customer.getIsTenant())) {
                customerType = "tenant";
            } else if (Boolean.TRUE.equals(customer.getIsContractor())) {
                customerType = "contractor";
            }
            
            if (emailService.isGmailApiAvailable(authentication)) {
                boolean emailSent = sendCustomerWelcomeEmail(customer, tempPassword, customerType, authentication);
                
                if (emailSent) {
                    redirectAttributes.addFlashAttribute("successMessage", 
                        capitalizeFirst(customerType) + " login created and welcome email sent!");
                } else {
                    redirectAttributes.addFlashAttribute("warningMessage", 
                        capitalizeFirst(customerType) + " login created but email could not be sent. Temporary password: " + tempPassword);
                }
            } else {
                redirectAttributes.addFlashAttribute("successMessage", 
                    capitalizeFirst(customerType) + " login created. Temporary password: " + tempPassword);
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error creating customer login: " + e.getMessage());
        }
        
        return "redirect:/employee/customer/" + id;
    }

    // ===== SEARCH FUNCTIONALITY =====
    
    @GetMapping("/search")
    public String searchCustomers(@RequestParam("keyword") String keyword,
                                 @RequestParam(value = "type", required = false) String type,
                                 Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            List<Customer> customers;
            
            if (type != null && !type.isEmpty()) {
                switch (type.toLowerCase()) {
                    case "property-owners":
                        customers = customerService.findPropertyOwners();
                        break;
                    case "tenants":
                        customers = customerService.findTenants();
                        break;
                    case "contractors":
                        customers = customerService.findContractors();
                        break;
                    default:
                        customers = customerService.findByKeyword(keyword);
                        break;
                }
                
                // Apply keyword filter to the specific type
                if (keyword != null && !keyword.trim().isEmpty()) {
                    customers = customers.stream()
                        .filter(c -> c.getName().toLowerCase().contains(keyword.toLowerCase()) ||
                                    c.getEmail().toLowerCase().contains(keyword.toLowerCase()) ||
                                    (c.getCity() != null && c.getCity().toLowerCase().contains(keyword.toLowerCase())))
                        .collect(Collectors.toList());
                }
            } else {
                customers = customerService.findByKeyword(keyword);
            }
            
            model.addAttribute("customers", customers);
            model.addAttribute("keyword", keyword);
            model.addAttribute("typeFilter", type);
            model.addAttribute("pageTitle", "Search Results");
            model.addAttribute("user", user);
            return "customer/search-results";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error searching customers: " + e.getMessage());
            return "error/500";
        }
    }

    // ===== MANAGER CUSTOMER VIEWS =====

    @GetMapping("/manager/all-customers")
    public String showAllCustomersManager(@RequestParam(value = "search", required = false) String search,
                                        @RequestParam(value = "type", required = false) String typeFilter,
                                        Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            // Check if user is manager
            if(!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                return "error/access-denied";
            }
            
            List<Customer> customers = customerService.findAll();
            
            // Apply type filter if provided
            if (typeFilter != null && !typeFilter.trim().isEmpty()) {
                switch (typeFilter.toLowerCase()) {
                    case "property-owners":
                        customers = customers.stream()
                            .filter(c -> Boolean.TRUE.equals(c.getIsPropertyOwner()))
                            .collect(Collectors.toList());
                        break;
                    case "tenants":
                        customers = customers.stream()
                            .filter(c -> Boolean.TRUE.equals(c.getIsTenant()))
                            .collect(Collectors.toList());
                        break;
                    case "contractors":
                        customers = customers.stream()
                            .filter(c -> Boolean.TRUE.equals(c.getIsContractor()))
                            .collect(Collectors.toList());
                        break;
                }
            }
            
            // Apply search filter if provided
            if (search != null && !search.trim().isEmpty()) {
                customers = customers.stream()
                    .filter(c -> c.getName().toLowerCase().contains(search.toLowerCase()) ||
                                c.getEmail().toLowerCase().contains(search.toLowerCase()) ||
                                (c.getCity() != null && c.getCity().toLowerCase().contains(search.toLowerCase())))
                    .collect(Collectors.toList());
            }
            
            model.addAttribute("customers", customers);
            model.addAttribute("customerType", "All Customers");
            model.addAttribute("pageTitle", "All Customers - Manager View");
            model.addAttribute("searchTerm", search);
            model.addAttribute("typeFilter", typeFilter);
            model.addAttribute("user", user);
            model.addAttribute("isManagerView", true);
            
            return "customer/manager-all-customers";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading customers: " + e.getMessage());
            return "error/500";
        }
    }

    // ===== BULK EMAIL FUNCTIONALITY =====
    
    @PostMapping("/bulk-email")
    public String sendBulkEmail(@RequestParam("customerType") String customerType,
                               @RequestParam("subject") String subject,
                               @RequestParam("message") String message,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        
        if (!emailService.isGmailApiAvailable(authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Gmail API access required for bulk email functionality");
            return "redirect:/employee/customer/dashboard";
        }
        
        try {
            List<Customer> customers;
            
            // Filter customers by type using the working repository methods
            switch (customerType.toLowerCase()) {
                case "property-owners":
                    customers = customerService.findPropertyOwners();
                    break;
                case "tenants":
                    customers = customerService.findTenants();
                    break;
                case "contractors":
                    customers = customerService.findContractors();
                    break;
                default:
                    customers = customerService.findAll();
                    break;
            }
            
            int emailsSent = 0;
            for (Customer customer : customers) {
                try {
                    boolean sent = emailService.sendEmailToCustomer(customer, subject, message, authentication);
                    if (sent) {
                        emailsSent++;
                    }
                } catch (Exception e) {
                    System.err.println("Failed to send email to " + customer.getEmail() + ": " + e.getMessage());
                }
            }
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Bulk email sent successfully to " + emailsSent + " out of " + customers.size() + " customers");
                
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error sending bulk email: " + e.getMessage());
        }
        
        return "redirect:/employee/customer/dashboard";
    }
    
    @PostMapping("/{id:[0-9]+}/send-email")
    public String sendIndividualEmail(@PathVariable("id") int id,
                                     @RequestParam("subject") String subject,
                                     @RequestParam("message") String message,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        
        Customer customer = customerService.findByCustomerId(id);
        if (customer == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Customer not found");
            return "redirect:/employee/customer/dashboard";
        }
        
        if (!emailService.isGmailApiAvailable(authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Gmail API access required for email functionality");
            return "redirect:/employee/customer/" + id;
        }
        
        try {
            boolean success = emailService.sendEmailToCustomer(customer, subject, message, authentication);
            
            if (success) {
                redirectAttributes.addFlashAttribute("successMessage", 
                    "Email sent successfully to " + customer.getEmail());
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Failed to send email to " + customer.getEmail());
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error sending email: " + e.getMessage());
        }
        
        return "redirect:/employee/customer/" + id;
    }

    // ===== UTILITY METHODS =====
    
    /**
     * Helper method for sending customer type-specific welcome emails
     */
    private boolean sendCustomerWelcomeEmail(Customer customer, String tempPassword, 
                                           String customerType, Authentication authentication) {
        try {
            String subject = "Welcome to PropertyManager - " + capitalizeFirst(customerType) + " Portal Access";
            String portalInstructions = getPortalInstructions(customerType);
            
            String message = String.format(
                "Dear %s,\n\n" +
                "Welcome to PropertyManager! Your %s account has been created successfully.\n\n" +
                "Your login credentials:\n" +
                "Email: %s\n" +
                "Temporary Password: %s\n\n" +
                "%s\n\n" +
                "Please log in and change your password as soon as possible.\n" +
                "Login URL: %s/customer-login\n\n" +
                "Best regards,\n" +
                "The PropertyManager Team",
                customer.getName() != null ? customer.getName() : "Customer",
                customerType,
                customer.getEmail(),
                tempPassword,
                portalInstructions,
                "http://localhost:8081" // Replace with your actual base URL
            );
            
            return emailService.sendEmailToCustomer(customer, subject, message, authentication);
            
        } catch (Exception e) {
            System.err.println("Error sending customer welcome email: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get portal-specific instructions for welcome emails
     */
    private String getPortalInstructions(String customerType) {
        switch (customerType.toLowerCase()) {
            case "property owner":
                return "You can access the Property Owner portal to manage your properties, view reports, and communicate with tenants.";
            case "tenant":
                return "You can access the Tenant portal to submit maintenance requests, view your lease details, and make payments.";
            case "contractor":
                return "You can access the Contractor portal to view job assignments, update work status, and submit invoices.";
            default:
                return "You can access your customer portal to view your account details and interact with our services.";
        }
    }
    
    /**
     * Capitalize first letter of a string
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}