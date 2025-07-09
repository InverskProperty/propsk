package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.service.customer.CustomerLoginInfoService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.email.EmailService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;
import site.easy.to.build.crm.util.EmailTokenUtils;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employee/customer")
public class CustomerController {

    private final CustomerService customerService;
    private final UserService userService;
    private final PropertyService propertyService;
    private final CustomerLoginInfoService customerLoginInfoService;
    private final AuthenticationUtils authenticationUtils;
    private final EmailService emailService;
    private final CustomerPropertyAssignmentRepository customerPropertyAssignmentRepository;


    @Autowired
    public CustomerController(CustomerService customerService, UserService userService, 
                            PropertyService propertyService,
                            CustomerLoginInfoService customerLoginInfoService,
                            AuthenticationUtils authenticationUtils, 
                            EmailService emailService,
                            CustomerPropertyAssignmentRepository customerPropertyAssignmentRepository) {
        this.customerService = customerService;
        this.userService = userService;
        this.propertyService = propertyService;
        this.customerLoginInfoService = customerLoginInfoService;
        this.authenticationUtils = authenticationUtils;
        this.emailService = emailService;
        this.customerPropertyAssignmentRepository = customerPropertyAssignmentRepository;
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
                                @RequestParam(value = "propertyId", required = false) Long propertyId,
                                Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            List<Customer> propertyOwners = customerService.findPropertyOwners();

                    // Filter by property ID if provided
                    if (propertyId != null) {
                        // Get customer IDs assigned as OWNER to this property from junction table
                        List<CustomerPropertyAssignment> propertyAssignments = 
                            customerPropertyAssignmentRepository.findByPropertyId(propertyId);
                        
                        List<Integer> ownerCustomerIds = propertyAssignments.stream()
                            .filter(assignment -> assignment.getAssignmentType() == AssignmentType.OWNER)
                            .map(assignment -> assignment.getCustomer().getCustomerId())
                            .collect(Collectors.toList());
                        
                        // Filter property owners based on junction table assignments
                        propertyOwners = propertyOwners.stream()
                            .filter(owner -> ownerCustomerIds.contains(owner.getCustomerId()))
                            .collect(Collectors.toList());
                            
                        // Add property info to model for display
                        try {
                            Property property = propertyService.findById(propertyId);
                            if (property != null) {
                                model.addAttribute("filterProperty", property);
                                model.addAttribute("pageTitle", "Property Owners for " + property.getPropertyName());
                                model.addAttribute("backUrl", "/employee/property/" + propertyId);
                            }
                        } catch (Exception e) {
                            // Property not found, continue with general listing
                        }
                    }
            
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
            model.addAttribute("propertyIdFilter", propertyId);
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
        // Redirect to unified form with property owner type pre-selected
        return "redirect:/employee/customer/create-customer?type=PROPERTY_OWNER";
    }

    @PostMapping("/create-customer")
    public String createCustomer(@ModelAttribute Customer customer,
                            @RequestParam(value = "customerTypeSelection", required = false) String customerTypeSelection,
                            @RequestParam(value = "isTenant", required = false) Boolean isTenant,
                            @RequestParam(value = "isPropertyOwner", required = false) Boolean isPropertyOwner,
                            @RequestParam(value = "isContractor", required = false) Boolean isContractor,
                            @RequestParam(value = "entityType", required = false) String entityType,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            // Set basic properties
            customer.setUser(user);
            customer.setCreatedAt(LocalDateTime.now());
            customer.setDescription("Active");
            
            // Determine customer type from form selection or hidden fields
            String finalCustomerType = customerTypeSelection;
            if (finalCustomerType == null && customer.getCustomerType() != null) {
                finalCustomerType = customer.getCustomerType().toString();
            }
            
            // Set customer type properties
            if ("TENANT".equals(finalCustomerType) || Boolean.TRUE.equals(isTenant)) {
                customer.setIsTenant(true);
                customer.setIsPropertyOwner(false);
                customer.setIsContractor(false);
                customer.setCustomerType(CustomerType.TENANT);
                customer.setEntityType("tenant");
            } else if ("PROPERTY_OWNER".equals(finalCustomerType) || Boolean.TRUE.equals(isPropertyOwner)) {
                customer.setIsPropertyOwner(true);
                customer.setIsTenant(false);
                customer.setIsContractor(false);
                customer.setCustomerType(CustomerType.PROPERTY_OWNER);
                customer.setEntityType("property_owner");
            } else if ("CONTRACTOR".equals(finalCustomerType) || Boolean.TRUE.equals(isContractor)) {
                customer.setIsContractor(true);
                customer.setIsTenant(false);
                customer.setIsPropertyOwner(false);
                customer.setCustomerType(CustomerType.CONTRACTOR);
                customer.setEntityType("contractor");
            } else {
                // Default to tenant if no type specified (backward compatibility)
                customer.setIsTenant(true);
                customer.setCustomerType(CustomerType.TENANT);
                customer.setEntityType("tenant");
            }
            
            Customer savedCustomer = customerService.save(customer);
            
            // Determine success message and redirect based on customer type
            String customerTypeDisplay = getCustomerTypeDisplay(savedCustomer);
            redirectAttributes.addFlashAttribute("successMessage", 
                customerTypeDisplay + " " + savedCustomer.getName() + " created successfully!");
            
            // Redirect to appropriate list
            return "redirect:" + getRedirectUrl(savedCustomer);
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error creating customer: " + e.getMessage());
            return "redirect:/employee/customer/create-customer";
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

    @GetMapping("/tenants")
    public String listTenants(@RequestParam(value = "search", required = false) String search,
                            @RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "propertyId", required = false) Long propertyId,
                            Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            List<Customer> tenants = customerService.findTenants();

            // Filter by property ID if provided
            if (propertyId != null) {
                // Get customer IDs assigned as TENANT to this property from junction table
                List<CustomerPropertyAssignment> propertyAssignments = 
                    customerPropertyAssignmentRepository.findByPropertyId(propertyId);
                
                List<Integer> tenantCustomerIds = propertyAssignments.stream()
                    .filter(assignment -> assignment.getAssignmentType() == AssignmentType.TENANT)
                    .map(assignment -> assignment.getCustomer().getCustomerId())
                    .collect(Collectors.toList());
                
                // Filter tenants based on junction table assignments
                tenants = tenants.stream()
                    .filter(tenant -> tenantCustomerIds.contains(tenant.getCustomerId()))
                    .collect(Collectors.toList());
                    
                // Add property info to model for display
                try {
                    Property property = propertyService.findById(propertyId);
                    if (property != null) {
                        model.addAttribute("filterProperty", property);
                        model.addAttribute("pageTitle", "Tenants for " + property.getPropertyName());
                        model.addAttribute("backUrl", "/employee/property/" + propertyId);
                    }
                } catch (Exception e) {
                    // Property not found, continue with general listing
                }
            }
            
            // Apply search filter if provided
            if (search != null && !search.trim().isEmpty()) {
                tenants = tenants.stream()
                    .filter(c -> c.getName().toLowerCase().contains(search.toLowerCase()) ||
                                (c.getEmail() != null && c.getEmail().toLowerCase().contains(search.toLowerCase())) ||
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
            model.addAttribute("propertyIdFilter", propertyId);
            model.addAttribute("user", user);
            model.addAttribute("createUrl", "/employee/customer/create-tenant");
            
            return "customer/customer-list";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading tenants: " + e.getMessage());
            return "error/500";
        }
    }

    @GetMapping("/create-tenant")
    public String showCreateTenantForm(Model model, Authentication authentication) {
        // Redirect to unified form with tenant type pre-selected
        return "redirect:/employee/customer/create-customer?type=TENANT";
    }

    @PostMapping("/create-tenant")
    public String createTenant(@ModelAttribute Customer customer, 
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            customer.setUser(user);
            customer.setIsTenant(true);
            customer.setCustomerType(CustomerType.TENANT);
            customer.setEntityType("tenant");
            customer.setCreatedAt(LocalDateTime.now());
            customer.setDescription("Active");
            
            Customer savedCustomer = customerService.save(customer);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Tenant " + savedCustomer.getName() + " created successfully!");
            
            return "redirect:/employee/customer/tenants";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error creating tenant: " + e.getMessage());
            return "redirect:/employee/customer/create-tenant";
        }
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
        // Redirect to unified form with contractor type pre-selected
        return "redirect:/employee/customer/create-customer?type=CONTRACTOR";
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
            
            // Check if user can access this customer (handle NULL user relationship)
            if (customer.getUser() != null && 
                !customer.getUser().getId().equals(loggedInUser.getId()) && 
                !loggedInUser.getRoles().stream().anyMatch(r -> r.getName().contains("MANAGER"))) {
                return "redirect:/access-denied";
            }
            // If customer.getUser() is NULL, allow managers/employees to view (PayProp imported customers)
            else if (customer.getUser() == null && 
                    !loggedInUser.getRoles().stream().anyMatch(r -> r.getName().contains("MANAGER") || r.getName().contains("EMPLOYEE"))) {
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
                
                // Add property information for tenants using junction table
                if (Boolean.TRUE.equals(customer.getIsTenant())) {
                    try {
                        List<CustomerPropertyAssignment> assignments = 
                            customerPropertyAssignmentRepository.findByCustomerCustomerId(customer.getCustomerId());
                        
                        List<Property> assignedProperties = assignments.stream()
                            .filter(assignment -> assignment.getAssignmentType() == AssignmentType.TENANT)
                            .map(assignment -> assignment.getProperty())
                            .collect(Collectors.toList());
                            
                        if (!assignedProperties.isEmpty()) {
                            model.addAttribute("assignedProperty", assignedProperties.get(0)); // Primary property
                            model.addAttribute("allAssignedProperties", assignedProperties); // All properties if multiple
                        }
                    } catch (Exception e) {
                        // Handle property loading error gracefully
                        System.err.println("Error loading properties for customer " + id + ": " + e.getMessage());
                    }
                }
            } else if (Boolean.TRUE.equals(customer.getIsContractor())) {
                customerTypeDisplay = "Contractor";
                backUrl = "/employee/customer/contractors";
            }

            // Add property information for property owners using junction table
            if (Boolean.TRUE.equals(customer.getIsPropertyOwner())) {
                try {
                    List<CustomerPropertyAssignment> assignments = 
                        customerPropertyAssignmentRepository.findByCustomerCustomerId(customer.getCustomerId());
                    
                    List<Property> ownedProperties = assignments.stream()
                        .filter(assignment -> assignment.getAssignmentType() == AssignmentType.OWNER)
                        .map(assignment -> assignment.getProperty())
                        .collect(Collectors.toList());
                        
                    model.addAttribute("allAssignedProperties", ownedProperties);
                    model.addAttribute("propertyCount", ownedProperties.size());
                } catch (Exception e) {
                    System.err.println("Error loading properties for owner " + id + ": " + e.getMessage());
                }
            }

            model.addAttribute("customer", customer);
            model.addAttribute("customerTypeDisplay", customerTypeDisplay);
            model.addAttribute("backUrl", backUrl);
            model.addAttribute("user", loggedInUser);
            model.addAttribute("pageTitle", customerTypeDisplay + " Details");
            
            // PayProp sync status
            model.addAttribute("payPropReady", customer.isPayPropEntity());
            model.addAttribute("payPropSynced", customer.getPayPropSynced());
            model.addAttribute("needsSync", customer.needsPayPropSync());
            
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

    // ===== ENHANCED CUSTOMER CREATION FORM =====

    @GetMapping("/my-customers")
    public String showMyCustomers(Authentication authentication) {
        // Redirect to existing dashboard until template is created
        return "redirect:/employee/customer/dashboard";
    }

    @GetMapping("/create-customer")
    public String showCreateCustomerForm(@RequestParam(value = "type", required = false) String customerType,
                                    Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            Customer customer = new Customer();
            String displayType = "Customer";
            String cancelUrl = "/employee/customer/dashboard";
            
            // Pre-configure based on customer type parameter
            if ("TENANT".equals(customerType)) {
                customer.setIsTenant(true);
                customer.setCustomerType(CustomerType.TENANT);
                customer.setEntityType("tenant");
                displayType = "Tenant";
                cancelUrl = "/employee/customer/tenants";
            } else if ("PROPERTY_OWNER".equals(customerType)) {
                customer.setIsPropertyOwner(true);
                customer.setCustomerType(CustomerType.PROPERTY_OWNER);
                customer.setEntityType("property_owner");
                displayType = "Property Owner";
                cancelUrl = "/employee/customer/property-owners";
            } else if ("CONTRACTOR".equals(customerType)) {
                customer.setIsContractor(true);
                customer.setCustomerType(CustomerType.CONTRACTOR);
                customer.setEntityType("contractor");
                displayType = "Contractor";
                cancelUrl = "/employee/customer/contractors";
            }
            
            model.addAttribute("customer", customer);
            model.addAttribute("customerType", displayType);
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "Create " + displayType);
            model.addAttribute("cancelUrl", cancelUrl);
            
            // Template compatibility
            model.addAttribute("isGoogleUser", false);
            model.addAttribute("hasGoogleGmailAccess", false);
            model.addAttribute("isEdit", false);
            
            return "customer/create-customer";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading create customer form: " + e.getMessage());
            return "error/500";
        }
    }

    // ===== ENHANCED ADD CUSTOMER METHOD =====
    
    @PostMapping("/add-customer")
    public String addCustomer(@ModelAttribute("customer") Customer customer,
                            @RequestParam(value = "customerTypeSelection", required = false) String customerTypeSelection,
                            @RequestParam(value = "accountType", required = false) String accountType,
                            // PayProp Individual Fields
                            @RequestParam(value = "firstName", required = false) String firstName,
                            @RequestParam(value = "lastName", required = false) String lastName,
                            @RequestParam(value = "dateOfBirth", required = false) String dateOfBirth,
                            @RequestParam(value = "idNumber", required = false) String idNumber,
                            // PayProp Business Fields
                            @RequestParam(value = "businessName", required = false) String businessName,
                            @RequestParam(value = "registrationNumber", required = false) String registrationNumber,
                            // Contact Fields
                            @RequestParam(value = "email", required = false) String email,
                            @RequestParam(value = "mobileNumber", required = false) String mobileNumber,
                            // Address Fields
                            @RequestParam(value = "addressLine1", required = false) String addressLine1,
                            @RequestParam(value = "addressLine2", required = false) String addressLine2,
                            @RequestParam(value = "city", required = false) String city,
                            @RequestParam(value = "county", required = false) String county,
                            @RequestParam(value = "postcode", required = false) String postcode,
                            @RequestParam(value = "country", required = false) String country,
                            // Property Assignment (Tenants)
                            @RequestParam(value = "propertyId", required = false) Long propertyId,
                            // PayProp Tenant Settings
                            @RequestParam(value = "invoiceLeadDays", required = false) Integer invoiceLeadDays,
                            @RequestParam(value = "customerReference", required = false) String customerReference,
                            @RequestParam(value = "notifyEmail", required = false) Boolean notifyEmail,
                            @RequestParam(value = "notifySms", required = false) Boolean notifySms,
                            // PayProp Payment Method (Property Owners)
                            @RequestParam(value = "paymentMethod", required = false) String paymentMethod,
                            @RequestParam(value = "bankAccountName", required = false) String bankAccountName,
                            @RequestParam(value = "bankAccountNumber", required = false) String bankAccountNumber,
                            @RequestParam(value = "bankSortCode", required = false) String bankSortCode,
                            @RequestParam(value = "bankName", required = false) String bankName,
                            @RequestParam(value = "bankIban", required = false) String bankIban,
                            @RequestParam(value = "bankSwiftCode", required = false) String bankSwiftCode,
                            // Notes
                            @RequestParam(value = "notes", required = false) String notes,
                            HttpServletRequest request,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        
        System.out.println("=== DEBUG: Enhanced addCustomer method called ===");
        System.out.println("🔍 Customer type selection: " + customerTypeSelection);
        System.out.println("🔍 Account type: " + accountType);
        System.out.println("🔍 First name: " + firstName);
        System.out.println("🔍 Last name: " + lastName);
        System.out.println("🔍 Business name: " + businessName);
        System.out.println("🔍 Email: " + email);
        System.out.println("🔍 Property ID: " + propertyId);
        
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            
            // Set basic properties
            customer.setUser(user);
            customer.setCreatedAt(LocalDateTime.now());
            
            // ===== DETERMINE CUSTOMER TYPE =====
            String finalCustomerType = customerTypeSelection;
            if (finalCustomerType == null && customer.getCustomerType() != null) {
                finalCustomerType = customer.getCustomerType().toString();
            }
            
            System.out.println("🔍 Final customer type: " + finalCustomerType);
            
            // Set customer type properties
            if ("TENANT".equals(finalCustomerType)) {
                customer.setIsTenant(true);
                customer.setIsPropertyOwner(false);
                customer.setIsContractor(false);
                customer.setCustomerType(CustomerType.TENANT);
                customer.setEntityType("tenant");
                System.out.println("🔍 Setting as TENANT");
            } else if ("PROPERTY_OWNER".equals(finalCustomerType)) {
                customer.setIsPropertyOwner(true);
                customer.setIsTenant(false);
                customer.setIsContractor(false);
                customer.setCustomerType(CustomerType.PROPERTY_OWNER);
                customer.setEntityType("property_owner");
                System.out.println("🔍 Setting as PROPERTY_OWNER");
            } else if ("CONTRACTOR".equals(finalCustomerType)) {
                customer.setIsContractor(true);
                customer.setIsTenant(false);
                customer.setIsPropertyOwner(false);
                customer.setCustomerType(CustomerType.CONTRACTOR);
                customer.setEntityType("contractor");
                System.out.println("🔍 Setting as CONTRACTOR");
            } else {
                // Default to tenant if no type specified
                customer.setIsTenant(true);
                customer.setCustomerType(CustomerType.TENANT);
                customer.setEntityType("tenant");
                System.out.println("🔍 Defaulting to TENANT");
            }

            // ===== SET CORE FIELDS =====

            // ===== SET ACCOUNT TYPE =====
            if (accountType != null) {
                if ("individual".equals(accountType)) {
                    customer.setAccountType(AccountType.individual);
                } else if ("business".equals(accountType)) {
                    customer.setAccountType(AccountType.business);
                } else {
                    customer.setAccountType(AccountType.individual); // Default fallback
                }
            }
            
            // Name handling - build full name from individual fields or use business name
            if ("business".equals(accountType) && businessName != null && !businessName.trim().isEmpty()) {
                customer.setName(businessName.trim());
            } else if (firstName != null && lastName != null) {
                customer.setName((firstName.trim() + " " + lastName.trim()).trim());
            } else if (customer.getName() == null || customer.getName().trim().isEmpty()) {
                // Use email prefix as fallback name
                if (email != null && email.contains("@")) {
                    customer.setName(email.substring(0, email.indexOf("@")));
                } else {
                    customer.setName("New Customer");
                }
            }

            // Contact information
            if (email != null && !email.trim().isEmpty()) {
                customer.setEmail(email.trim());
            }
            if (mobileNumber != null && !mobileNumber.trim().isEmpty()) {
                customer.setPhone(mobileNumber.trim());
            }

            // Address information - Use the existing address field in Customer entity
            StringBuilder addressBuilder = new StringBuilder();
            if (addressLine1 != null && !addressLine1.trim().isEmpty()) {
                addressBuilder.append(addressLine1.trim());
            }
            if (addressLine2 != null && !addressLine2.trim().isEmpty()) {
                if (addressBuilder.length() > 0) addressBuilder.append(", ");
                addressBuilder.append(addressLine2.trim());
            }
            customer.setAddress(addressBuilder.toString());
            
            if (city != null && !city.trim().isEmpty()) {
                customer.setCity(city.trim());
            }
            if (county != null && !county.trim().isEmpty()) {
                customer.setState(county.trim()); // Using state field for county
            }
            if (country != null && !country.trim().isEmpty()) {
                customer.setCountry(country.trim());
            } else {
                customer.setCountry("United Kingdom"); // Default
            }

            // ===== PROPERTY ASSIGNMENT FOR TENANTS =====
            if ("TENANT".equals(finalCustomerType) && propertyId != null) {
                Property property = propertyService.findById(propertyId);
                if (property != null) {
                    customer.setEntityId(propertyId);
                    customer.setEntityType("property");
                    System.out.println("🔍 Assigned tenant to property ID: " + propertyId);
                }
            }

            // ===== PAYPROP INTEGRATION SETUP =====
            
            // Generate PayProp customer ID
            if (customerReference != null && !customerReference.trim().isEmpty()) {
                customer.setPayPropCustomerId(customerReference.trim());
            } else {
                // Auto-generate customer reference
                String prefix = "TENANT".equals(finalCustomerType) ? "TN" : 
                               "PROPERTY_OWNER".equals(finalCustomerType) ? "PO" : "CO";
                customer.setPayPropCustomerId(prefix + "_" + System.currentTimeMillis());
            }

            // PayProp sync status
            customer.setPayPropSynced(false); // Will be synced later
            
            // Set description with PayProp account type info
            StringBuilder descBuilder = new StringBuilder("Active");
            if (accountType != null) {
                descBuilder.append(" - ").append("business".equals(accountType) ? "Business" : "Individual").append(" Account");
            }
            if (paymentMethod != null && "PROPERTY_OWNER".equals(finalCustomerType)) {
                descBuilder.append(" - ").append(paymentMethod.toUpperCase()).append(" Payment");
            }
            customer.setDescription(descBuilder.toString());

            // Additional notes
            if (notes != null && !notes.trim().isEmpty()) {
                customer.setDescription(customer.getDescription() + "\n\nNotes: " + notes.trim());
            }

            System.out.println("🔍 About to save customer...");
            Customer savedCustomer = customerService.save(customer);
            System.out.println("🔍 Customer saved successfully with ID: " + savedCustomer.getCustomerId());
            
            // ===== POST-CREATION ACTIONS =====
            
            // Store additional PayProp data in description or future custom fields
            if ("individual".equals(accountType)) {
                // Store individual-specific data
                String individualData = String.format("PayProp Individual: %s %s", 
                    firstName != null ? firstName : "", 
                    lastName != null ? lastName : "");
                if (dateOfBirth != null && !dateOfBirth.trim().isEmpty()) {
                    individualData += " | DOB: " + dateOfBirth;
                }
                if (idNumber != null && !idNumber.trim().isEmpty()) {
                    individualData += " | ID: " + idNumber;
                }
                
                // Append to description
                savedCustomer.setDescription(savedCustomer.getDescription() + "\n" + individualData);
                customerService.save(savedCustomer);
            }

            // Success message and redirect
            String customerTypeDisplay = getCustomerTypeDisplay(savedCustomer);
            redirectAttributes.addFlashAttribute("successMessage", 
                customerTypeDisplay + " " + savedCustomer.getName() + " created successfully! Ready for PayProp sync.");
            
            // Determine redirect URL
            String redirectUrl = getRedirectUrl(savedCustomer);
            System.out.println("🔍 Redirecting to: " + redirectUrl);
            
            return "redirect:" + redirectUrl;
            
        } catch (Exception e) {
            System.err.println("❌ ERROR in addCustomer: " + e.getMessage());
            e.printStackTrace();
            
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error creating customer: " + e.getMessage());
            return "redirect:/employee/customer/create-customer";
        }
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

    private String getCustomerTypeDisplay(Customer customer) {
        if (Boolean.TRUE.equals(customer.getIsTenant())) {
            return "Tenant";
        } else if (Boolean.TRUE.equals(customer.getIsPropertyOwner())) {
            return "Property Owner";
        } else if (Boolean.TRUE.equals(customer.getIsContractor())) {
            return "Contractor";
        }
        return "Customer";
    }

    private String getRedirectUrl(Customer customer) {
        if (Boolean.TRUE.equals(customer.getIsTenant())) {
            return "/employee/customer/tenants";
        } else if (Boolean.TRUE.equals(customer.getIsPropertyOwner())) {
            return "/employee/customer/property-owners";
        } else if (Boolean.TRUE.equals(customer.getIsContractor())) {
            return "/employee/customer/contractors";
        }
        return "/employee/customer/dashboard";
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