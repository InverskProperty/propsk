package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.BlockRepository;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.repository.EmailTemplateRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.service.correspondence.CorrespondenceService;
import site.easy.to.build.crm.service.customer.CustomerLoginInfoService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.document.DocumentTemplateService;
import site.easy.to.build.crm.service.email.EmailService;
import site.easy.to.build.crm.service.payprop.LocalToPayPropSyncService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;
import site.easy.to.build.crm.util.EmailTokenUtils;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final BlockRepository blockRepository;
    private final PropertyRepository propertyRepository;
    private final DocumentTemplateService documentTemplateService;
    private final EmailTemplateRepository emailTemplateRepository;

    @Autowired(required = false)
    private LocalToPayPropSyncService localToPayPropSyncService;


    @Autowired
    public CustomerController(CustomerService customerService, UserService userService,
                            PropertyService propertyService,
                            CustomerLoginInfoService customerLoginInfoService,
                            AuthenticationUtils authenticationUtils,
                            EmailService emailService,
                            CustomerPropertyAssignmentRepository customerPropertyAssignmentRepository,
                            BlockRepository blockRepository,
                            PropertyRepository propertyRepository,
                            DocumentTemplateService documentTemplateService,
                            EmailTemplateRepository emailTemplateRepository) {
        this.customerService = customerService;
        this.userService = userService;
        this.propertyService = propertyService;
        this.customerLoginInfoService = customerLoginInfoService;
        this.authenticationUtils = authenticationUtils;
        this.emailService = emailService;
        this.customerPropertyAssignmentRepository = customerPropertyAssignmentRepository;
        this.blockRepository = blockRepository;
        this.propertyRepository = propertyRepository;
        this.documentTemplateService = documentTemplateService;
        this.emailTemplateRepository = emailTemplateRepository;
    }

    // ===== MAIN CUSTOMER DASHBOARD =====
    
    @GetMapping("/dashboard")
    public String customerDashboard(Model model, Authentication authentication) {
        try {
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
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

    // ===== DEBUG ENDPOINTS =====
    
    @GetMapping("/debug/owners")
    @ResponseBody
    public Map<String, Object> debugOwners() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            System.out.println("🔍 DEBUG: Testing assignment service for owners...");
            
            // Test assignment service directly
            List<CustomerPropertyAssignment> ownerAssignments = customerPropertyAssignmentRepository.findByAssignmentType(AssignmentType.OWNER);
            System.out.println("🔍 DEBUG: Found " + ownerAssignments.size() + " owner assignments from repository");
            
            List<Customer> customers = ownerAssignments.stream()
                .map(CustomerPropertyAssignment::getCustomer)
                .filter(customer -> customer != null)
                .distinct()
                .collect(Collectors.toList());
            
            System.out.println("🔍 DEBUG: Mapped to " + customers.size() + " unique customers");
            
            // Test service method
            List<Customer> serviceResult = customerService.findPropertyOwners();
            System.out.println("🔍 DEBUG: Service returned " + serviceResult.size() + " customers");
            
            result.put("repository_assignments", ownerAssignments.size());
            result.put("unique_customers_from_repo", customers.size());
            result.put("service_result_size", serviceResult.size());
            result.put("first_few_customers", customers.stream().limit(3).map(c -> 
                Map.of("id", c.getCustomerId(), "name", c.getName(), "email", c.getEmail())
            ).collect(Collectors.toList()));
            
        } catch (Exception e) {
            System.out.println("🔍 DEBUG ERROR: " + e.getMessage());
            e.printStackTrace();
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @GetMapping("/debug/tenants") 
    @ResponseBody
    public Map<String, Object> debugTenants() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            System.out.println("🔍 DEBUG: Testing assignment service for tenants...");
            
            // Test assignment service directly
            List<CustomerPropertyAssignment> tenantAssignments = customerPropertyAssignmentRepository.findByAssignmentType(AssignmentType.TENANT);
            System.out.println("🔍 DEBUG: Found " + tenantAssignments.size() + " tenant assignments from repository");
            
            List<Customer> customers = tenantAssignments.stream()
                .map(CustomerPropertyAssignment::getCustomer)
                .filter(customer -> customer != null)
                .distinct()
                .collect(Collectors.toList());
            
            System.out.println("🔍 DEBUG: Mapped to " + customers.size() + " unique customers");
            
            // Test service method
            List<Customer> serviceResult = customerService.findTenants();
            System.out.println("🔍 DEBUG: Service returned " + serviceResult.size() + " customers");
            
            result.put("repository_assignments", tenantAssignments.size());
            result.put("unique_customers_from_repo", customers.size());
            result.put("service_result_size", serviceResult.size());
            result.put("first_few_customers", customers.stream().limit(3).map(c -> 
                Map.of("id", c.getCustomerId(), "name", c.getName(), "email", c.getEmail())
            ).collect(Collectors.toList()));
            
        } catch (Exception e) {
            System.out.println("🔍 DEBUG ERROR: " + e.getMessage());
            e.printStackTrace();
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    // ===== PROPERTY OWNERS SECTION =====
    
    @GetMapping("/property-owners")
    public String listPropertyOwners(@RequestParam(value = "search", required = false) String search,
                                @RequestParam(value = "propertyId", required = false) Long propertyId,
                                Model model, Authentication authentication) {
        try {
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
            User user = userService.findById(userId);
            
            // UNIFIED PAYPROP WINNER LOGIC: Get ALL property owners (including pending sync like "ABC TEST")
            List<Customer> propertyOwners = customerService.findPropertyOwners();
            System.out.println("🔍 Property Owners Controller - Retrieved " + propertyOwners.size() + " total property owners");
            
            // Filter by user's customers if not manager or super admin
            if (!user.getRoles().stream().anyMatch(r -> r.getName().contains("MANAGER") || r.getName().contains("SUPER_ADMIN"))) {
                List<Customer> userCustomers = customerService.findByUserId(userId);
                propertyOwners = propertyOwners.stream()
                    .filter(userCustomers::contains)
                    .collect(Collectors.toList());
            }

            // Filter by property ID ONLY if provided (specific property view)
            if (propertyId != null) {
                System.out.println("🔍 Filtering property owners for property ID: " + propertyId);
                // Get customer IDs assigned as OWNER to this property from junction table
                List<CustomerPropertyAssignment> propertyAssignments = 
                    customerPropertyAssignmentRepository.findByPropertyId(propertyId);
                
                List<Long> ownerCustomerIds = propertyAssignments.stream()
                    .filter(assignment -> assignment.getAssignmentType() == AssignmentType.OWNER)
                    .map(assignment -> assignment.getCustomer().getCustomerId())
                    .collect(Collectors.toList());
                
                // Filter property owners based on junction table assignments
                propertyOwners = propertyOwners.stream()
                    .filter(owner -> ownerCustomerIds.contains(owner.getCustomerId()))
                    .collect(Collectors.toList());
                    
                System.out.println("🔍 After property filter: " + propertyOwners.size() + " owners for property " + propertyId);
                    
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
            } else {
                // NO PROPERTY FILTER: Show ALL property owners (including "ABC TEST")
                System.out.println("🔍 No property filter - showing ALL " + propertyOwners.size() + " property owners");
                model.addAttribute("pageTitle", "All Property Owners");
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

    // In CustomerController.java - createCustomer method
    @PostMapping("/create-customer")
    public String createCustomer(@ModelAttribute Customer customer,
                            @RequestParam(value = "customerTypeSelection", required = false) String customerTypeSelection,
                            @RequestParam(value = "isTenant", required = false) Boolean isTenant,
                            @RequestParam(value = "isPropertyOwner", required = false) Boolean isPropertyOwner,
                            @RequestParam(value = "isContractor", required = false) Boolean isContractor,
                            @RequestParam(value = "entityType", required = false) String entityType,
                            @RequestParam(value = "syncToPayProp", defaultValue = "false") Boolean syncToPayProp,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        try {
            // FIXED: Get the correct user ID based on authentication type
            Integer userId = null;
            User user = null;
            
            if (authentication.getPrincipal() instanceof OAuth2User) {
                // OAuth authentication - get the OAuth user
                OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
                if (oAuthUser != null) {
                    // Use the OAuth user's user_id field, not the linked User entity's ID
                    userId = oAuthUser.getUserId();
                    System.out.println("OAuth user - using user_id: " + userId);
                    
                    // Try to find the actual User entity
                    if (userId != null) {
                        user = userService.findById(userId.longValue());
                    }
                }
            } else {
                // Regular authentication
                Long regularUserId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
                user = userService.findById(regularUserId);
                userId = user != null ? user.getId() : null;
            }
            
            // If we couldn't find a user, create a placeholder or handle the error
            if (user == null && userId != null) {
                // Option 1: Try to find/create a user for this OAuth account
                // Option 2: Allow null user (for OAuth-only customers)
                System.out.println("Warning: No User entity found for ID " + userId);
                
                // For now, we'll allow the customer to be created without a User link
                // This is better than linking all customers to user 54
            }
            
            // Set basic properties
            if (user != null) {
                customer.setUser(user);
            }
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
            
            // PayProp sync if requested
            String successMessage = null;
            String customerTypeDisplay = getCustomerTypeDisplay(savedCustomer);
            
            if (syncToPayProp && localToPayPropSyncService != null) {
                try {
                    String syncResult = null;
                    
                    if (savedCustomer.getCustomerType() == CustomerType.PROPERTY_OWNER) {
                        System.out.println("🔄 Syncing customer as PayProp beneficiary...");
                        syncResult = localToPayPropSyncService.syncCustomerAsBeneficiaryToPayProp(savedCustomer);
                    } else if (savedCustomer.getCustomerType() == CustomerType.TENANT) {
                        System.out.println("🔄 Syncing customer as PayProp tenant...");
                        syncResult = localToPayPropSyncService.syncCustomerAsTenantToPayProp(savedCustomer);
                    }
                    
                    if (syncResult != null) {
                        System.out.println("✅ Customer synced to PayProp: " + syncResult);
                        successMessage = customerTypeDisplay + " " + savedCustomer.getName() + 
                                       " created and synced to PayProp successfully! (PayProp ID: " + syncResult + ")";
                    } else {
                        successMessage = customerTypeDisplay + " " + savedCustomer.getName() + 
                                       " created successfully. PayProp sync only available for Property Owners and Tenants.";
                    }
                    
                } catch (Exception syncError) {
                    System.out.println("❌ PayProp sync failed: " + syncError.getMessage());
                    successMessage = customerTypeDisplay + " " + savedCustomer.getName() + 
                                   " created successfully, but PayProp sync failed: " + syncError.getMessage();
                }
            } else {
                successMessage = customerTypeDisplay + " " + savedCustomer.getName() + " created successfully!";
            }
            
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
            
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
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
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

    /**
     * Quick-create property owner (AJAX endpoint for property creation modal)
     * POST /employee/customer/quick-create-owner
     */
    @PostMapping("/quick-create-owner")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> quickCreateOwner(@RequestBody Map<String, String> customerData,
                                                                Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Validate required fields
            String firstName = customerData.get("firstName");
            String lastName = customerData.get("lastName");
            String email = customerData.get("email");

            if (firstName == null || firstName.trim().isEmpty() ||
                lastName == null || lastName.trim().isEmpty() ||
                email == null || email.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "First name, last name, and email are required");
                return ResponseEntity.badRequest().body(response);
            }

            // Check if customer with this email already exists
            Customer existing = customerService.findByEmail(email.trim());
            if (existing != null) {
                response.put("success", false);
                response.put("message", "A customer with this email already exists");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }

            // Get user ID
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
            User user = userService.findById(userId);

            // Create new customer
            Customer customer = new Customer();
            customer.setUser(user);
            customer.setFirstName(firstName.trim());
            customer.setLastName(lastName.trim());
            customer.setName((firstName.trim() + " " + lastName.trim()).trim());
            customer.setEmail(email.trim());

            // Optional fields
            if (customerData.get("phone") != null && !customerData.get("phone").trim().isEmpty()) {
                customer.setPhone(customerData.get("phone").trim());
            }

            // Build address from components
            StringBuilder addressBuilder = new StringBuilder();
            if (customerData.get("addressLine1") != null && !customerData.get("addressLine1").trim().isEmpty()) {
                addressBuilder.append(customerData.get("addressLine1").trim());
            }
            if (addressBuilder.length() > 0) {
                customer.setAddress(addressBuilder.toString());
            }

            if (customerData.get("city") != null && !customerData.get("city").trim().isEmpty()) {
                customer.setCity(customerData.get("city").trim());
            }

            if (customerData.get("postcode") != null && !customerData.get("postcode").trim().isEmpty()) {
                customer.setPostcode(customerData.get("postcode").trim());
            }

            // Set as property owner
            customer.setIsPropertyOwner(true);
            customer.setIsTenant(false);
            customer.setIsContractor(false);
            customer.setCustomerType(CustomerType.PROPERTY_OWNER);
            customer.setEntityType("property_owner");
            customer.setDescription("Active");
            customer.setCreatedAt(LocalDateTime.now());

            // Save customer
            Customer savedCustomer = customerService.save(customer);

            response.put("success", true);
            response.put("customerId", savedCustomer.getCustomerId());
            response.put("fullName", savedCustomer.getName());
            response.put("email", savedCustomer.getEmail());

            System.out.println("✅ Quick-created property owner: " + savedCustomer.getName() + " (ID: " + savedCustomer.getCustomerId() + ")");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ Error in quick-create-owner: " + e.getMessage());
            e.printStackTrace();

            response.put("success", false);
            response.put("message", "Server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ===== EMAIL PROPERTY OWNERS =====
    @GetMapping("/email-property-owners")
    public String emailPropertyOwnersForm(Model model, Authentication authentication) {
        try {
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
            User user = userService.findById(userId);

            List<Customer> propertyOwners = customerService.findPropertyOwners();

            // Get all properties for the dropdown
            List<Property> properties = propertyService.findAll();

            // Get all property owners for the owner filter dropdown
            List<Customer> allPropertyOwners = customerService.findPropertyOwners();

            // Get all blocks for the dropdown
            List<Block> blocks = blockRepository.findAll();

            model.addAttribute("customers", propertyOwners);
            model.addAttribute("properties", properties);
            model.addAttribute("propertyOwners", allPropertyOwners);
            model.addAttribute("blocks", blocks);
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

    /**
     * Process email form for property owners
     */
    @PostMapping("/email-property-owners")
    public String sendEmailToPropertyOwners(@RequestParam("subject") String subject,
                                        @RequestParam("message") String message,
                                        @RequestParam(value = "selectedIds", required = false) List<Integer> selectedIds,
                                        @RequestParam(value = "emailAll", defaultValue = "false") boolean emailAll,
                                        @RequestParam(value = "propertyId", required = false) Long propertyId,
                                        @RequestParam(value = "blockId", required = false) Long blockId,
                                        @RequestParam(value = "ownerId", required = false) Integer ownerId,
                                        @RequestParam(value = "includePropertyInfo", defaultValue = "false") boolean includePropertyInfo,
                                        Authentication authentication,
                                        RedirectAttributes redirectAttributes) {
        
        if (!emailService.isGmailApiAvailable(authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Gmail API access required. Please ensure you're logged in with Google and have granted Gmail permissions.");
            return "redirect:/employee/customer/email-property-owners";
        }
        
        try {
            List<Customer> propertyOwners;
            String filterDescription = "";
            
            if (emailAll || selectedIds == null || selectedIds.isEmpty()) {
                // Start with all property owners
                propertyOwners = customerService.findPropertyOwners();

                // Apply block filter if specified
                if (blockId != null) {
                    List<Property> blockProperties = propertyRepository.findByBlockId(blockId);
                    List<Long> propertyIds = blockProperties.stream()
                        .map(Property::getId)
                        .collect(Collectors.toList());

                    if (propertyIds.isEmpty()) {
                        redirectAttributes.addFlashAttribute("warningMessage",
                            "No properties found in the selected block.");
                        return "redirect:/employee/customer/property-owners";
                    }

                    List<Long> ownerCustomerIds = new ArrayList<>();
                    for (Long propId : propertyIds) {
                        List<CustomerPropertyAssignment> assignments =
                            customerPropertyAssignmentRepository.findByPropertyId(propId);

                        ownerCustomerIds.addAll(assignments.stream()
                            .filter(assignment -> assignment.getAssignmentType() == AssignmentType.OWNER)
                            .map(assignment -> assignment.getCustomer().getCustomerId())
                            .collect(Collectors.toList()));
                    }

                    propertyOwners = propertyOwners.stream()
                        .filter(owner -> ownerCustomerIds.contains(owner.getCustomerId()))
                        .distinct()
                        .collect(Collectors.toList());

                    try {
                        Block block = blockRepository.findById(blockId).orElse(null);
                        if (block != null) {
                            filterDescription += "for block: " + block.getName();
                        } else {
                            filterDescription += "for block ID: " + blockId;
                        }
                    } catch (Exception e) {
                        filterDescription += "for block ID: " + blockId;
                    }
                }
                // Apply property filter if specified
                else if (propertyId != null) {
                    List<CustomerPropertyAssignment> propertyAssignments =
                        customerPropertyAssignmentRepository.findByPropertyId(propertyId);

                    List<Long> ownerCustomerIds = propertyAssignments.stream()
                        .filter(assignment -> assignment.getAssignmentType() == AssignmentType.OWNER)
                        .map(assignment -> assignment.getCustomer().getCustomerId())
                        .collect(Collectors.toList());

                    propertyOwners = propertyOwners.stream()
                        .filter(owner -> ownerCustomerIds.contains(owner.getCustomerId()))
                        .collect(Collectors.toList());

                    try {
                        Property property = propertyService.findById(propertyId);
                        if (property != null) {
                            filterDescription += "for property: " + property.getPropertyName();
                        }
                    } catch (Exception e) {
                        filterDescription += "for property ID: " + propertyId;
                    }
                }
                
                // Apply owner filter if specified
                if (ownerId != null) {
                    propertyOwners = propertyOwners.stream()
                        .filter(owner -> owner.getCustomerId().equals(ownerId.longValue()))
                        .collect(Collectors.toList());
                    
                    if (!filterDescription.isEmpty()) {
                        filterDescription += " and ";
                    }
                    filterDescription += "specific owner";
                }
                
            } else {
                // Email only selected property owners
                propertyOwners = selectedIds.stream()
                    .map(id -> customerService.findByCustomerId(id.longValue()))
                    .filter(customer -> customer != null && Boolean.TRUE.equals(customer.getIsPropertyOwner()))
                    .collect(Collectors.toList());
                filterDescription = "selected owners";
            }
            
            // Filter out customers with invalid email addresses
            List<Customer> validCustomers = propertyOwners.stream()
                .filter(customer -> emailService.isValidEmail(customer.getEmail()))
                .collect(Collectors.toList());
                
            if (validCustomers.isEmpty()) {
                redirectAttributes.addFlashAttribute("warningMessage", 
                    "No property owners " + filterDescription + " with valid email addresses found.");
                return "redirect:/employee/customer/property-owners";
            }
            
            // Enhance message with property info if requested
            // Enhance message with property info if requested
            String finalMessage = message;
            if (includePropertyInfo && propertyId != null) {
                try {
                    Property property = propertyService.findById(propertyId);
                    if (property != null) {
                        finalMessage += "\n\n--- Property Information ---\n";
                        finalMessage += "Property: " + property.getPropertyName() + "\n";
                        finalMessage += "Address: " + property.getFullAddress() + "\n";
                        if (property.getCity() != null) {
                            finalMessage += "City: " + property.getCity() + "\n";
                        }
                    }
                } catch (Exception e) {
                    // Continue without property info if error
                }
            }
            
            int emailsSent = emailService.sendBulkEmail(validCustomers, subject, finalMessage, authentication);
            
            if (emailsSent > 0) {
                String successMessage = String.format("Successfully sent email to %d out of %d property owners", 
                    emailsSent, validCustomers.size());
                if (!filterDescription.isEmpty()) {
                    successMessage += " " + filterDescription;
                }
                redirectAttributes.addFlashAttribute("successMessage", successMessage + ".");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Failed to send emails. Please check your Gmail API access and try again.");
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error sending bulk email: " + e.getMessage());
        }
        
        return "redirect:/employee/customer/property-owners";
    }

    // ===== TENANTS SECTION =====

    @GetMapping("/tenants")
    public String listTenants(@RequestParam(value = "search", required = false) String search,
                            @RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "propertyId", required = false) Long propertyId,
                            Model model, Authentication authentication) {
        try {
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
            User user = userService.findById(userId);
            
            List<Customer> tenants = customerService.findTenants();
            
            // Filter by user's customers if not manager or super admin
            if (!user.getRoles().stream().anyMatch(r -> r.getName().contains("MANAGER") || r.getName().contains("SUPER_ADMIN"))) {
                List<Customer> userCustomers = customerService.findByUserId(userId);
                tenants = tenants.stream()
                    .filter(userCustomers::contains)
                    .collect(Collectors.toList());
            }

            // Filter by property ID if provided
            if (propertyId != null) {
                // Get customer IDs assigned as TENANT to this property from junction table
                // ONLY show active tenants (end_date is NULL or in the future)
                List<CustomerPropertyAssignment> propertyAssignments =
                    customerPropertyAssignmentRepository.findByPropertyId(propertyId);

                java.time.LocalDate today = java.time.LocalDate.now();

                List<Long> tenantCustomerIds = propertyAssignments.stream()
                    .filter(assignment -> assignment.getAssignmentType() == AssignmentType.TENANT)
                    .filter(assignment -> assignment.getEndDate() == null || assignment.getEndDate().isAfter(today))
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
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
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
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
            User user = userService.findById(userId);

            List<Customer> tenants = customerService.findTenants();
            List<Block> blocks = blockRepository.findAll();
            List<EmailTemplate> emailTemplates = emailTemplateRepository.findAll();
            List<DocumentTemplate> documentTemplates = documentTemplateService.findAllActive();
            List<String> mergeFields = documentTemplateService.getAvailableMergeFields();

            model.addAttribute("tenants", tenants);
            model.addAttribute("blocks", blocks);
            model.addAttribute("emailTemplates", emailTemplates);
            model.addAttribute("documentTemplates", documentTemplates);
            model.addAttribute("mergeFields", mergeFields);
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "Email Tenants");

            return "employee/customer/email-tenants";

        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error loading email form: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Process email form for tenants
     */
    @PostMapping("/email-tenants")
    public String sendEmailToTenants(@RequestParam("subject") String subject,
                                    @RequestParam("message") String message,
                                    @RequestParam(value = "selectedIds", required = false) List<Integer> selectedIds,
                                    @RequestParam(value = "emailAll", defaultValue = "false") boolean emailAll,
                                    @RequestParam(value = "activeOnly", defaultValue = "false") boolean activeOnly,
                                    @RequestParam(value = "propertyId", required = false) Long propertyId,
                                    @RequestParam(value = "blockId", required = false) Long blockId,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        
        if (!emailService.isGmailApiAvailable(authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Gmail API access required. Please ensure you're logged in with Google and have granted Gmail permissions.");
            return "redirect:/employee/customer/email-tenants";
        }
        
        try {
            List<Customer> tenants;

            if (emailAll || selectedIds == null || selectedIds.isEmpty()) {
                // Get tenants based on filters
                if (blockId != null) {
                    // Email tenants for all properties in a specific block
                    List<Property> blockProperties = propertyRepository.findByBlockId(blockId);
                    List<Long> propertyIds = blockProperties.stream()
                        .map(Property::getId)
                        .collect(Collectors.toList());

                    if (propertyIds.isEmpty()) {
                        redirectAttributes.addFlashAttribute("warningMessage",
                            "No properties found in the selected block.");
                        return "redirect:/employee/customer/tenants";
                    }

                    java.time.LocalDate today = java.time.LocalDate.now();
                    List<Long> tenantCustomerIds = new ArrayList<>();

                    for (Long propId : propertyIds) {
                        List<CustomerPropertyAssignment> assignments =
                            customerPropertyAssignmentRepository.findByPropertyId(propId);

                        tenantCustomerIds.addAll(assignments.stream()
                            .filter(assignment -> assignment.getAssignmentType() == AssignmentType.TENANT)
                            .filter(assignment -> assignment.getEndDate() == null || assignment.getEndDate().isAfter(today))
                            .map(assignment -> assignment.getCustomer().getCustomerId())
                            .collect(Collectors.toList()));
                    }

                    tenants = customerService.findTenants().stream()
                        .filter(tenant -> tenantCustomerIds.contains(tenant.getCustomerId()))
                        .distinct()
                        .collect(Collectors.toList());

                } else if (propertyId != null) {
                    // Email tenants for specific property using junction table
                    // ONLY include active tenants (end_date is NULL or in the future)
                    List<CustomerPropertyAssignment> propertyAssignments =
                        customerPropertyAssignmentRepository.findByPropertyId(propertyId);

                    java.time.LocalDate today = java.time.LocalDate.now();

                    List<Long> tenantCustomerIds = propertyAssignments.stream()
                        .filter(assignment -> assignment.getAssignmentType() == AssignmentType.TENANT)
                        .filter(assignment -> assignment.getEndDate() == null || assignment.getEndDate().isAfter(today))
                        .map(assignment -> assignment.getCustomer().getCustomerId())
                        .collect(Collectors.toList());

                    tenants = customerService.findTenants().stream()
                        .filter(tenant -> tenantCustomerIds.contains(tenant.getCustomerId()))
                        .collect(Collectors.toList());

                } else if (activeOnly) {
                    // Email only active tenants (those with active property assignments)
                    // ONLY include tenants where end_date is NULL or in the future
                    List<CustomerPropertyAssignment> activeAssignments = customerPropertyAssignmentRepository
                        .findByAssignmentType(AssignmentType.TENANT);

                    java.time.LocalDate today = java.time.LocalDate.now();

                    List<Long> activeTenantIds = activeAssignments.stream()
                        .filter(assignment -> assignment.getEndDate() == null || assignment.getEndDate().isAfter(today))
                        .map(assignment -> assignment.getCustomer().getCustomerId())
                        .distinct()
                        .collect(Collectors.toList());

                    tenants = customerService.findTenants().stream()
                        .filter(tenant -> activeTenantIds.contains(tenant.getCustomerId()))
                        .collect(Collectors.toList());
                } else {
                    // Email all tenants
                    tenants = customerService.findTenants();
                }
            } else {
                // Email only selected tenants
                tenants = selectedIds.stream()
                    .map(id -> customerService.findByCustomerId(id.longValue()))
                    .filter(customer -> customer != null && Boolean.TRUE.equals(customer.getIsTenant()))
                    .collect(Collectors.toList());
            }
            
            // Filter out customers with invalid email addresses
            List<Customer> validCustomers = tenants.stream()
                .filter(customer -> emailService.isValidEmail(customer.getEmail()))
                .collect(Collectors.toList());
                
            if (validCustomers.isEmpty()) {
                String filterDesc = blockId != null ? "in this block" :
                                   propertyId != null ? "for this property" :
                                   activeOnly ? "with active tenancies" : "";
                redirectAttributes.addFlashAttribute("warningMessage",
                    "No tenants " + filterDesc + " with valid email addresses found.");
                return "redirect:/employee/customer/tenants" + (propertyId != null ? "?propertyId=" + propertyId : "");
            }

            int emailsSent = emailService.sendBulkEmail(validCustomers, subject, message, authentication);

            if (emailsSent > 0) {
                String filterDesc = blockId != null ? " in block ID " + blockId :
                                   propertyId != null ? " for property ID " + propertyId :
                                   activeOnly ? " with active tenancies" : "";
                redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Successfully sent email to %d out of %d tenants%s.",
                        emailsSent, validCustomers.size(), filterDesc));
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Failed to send emails. Please check your Gmail API access and try again.");
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error sending bulk email: " + e.getMessage());
        }
        
        String returnUrl = "redirect:/employee/customer/tenants";
        if (propertyId != null) {
            returnUrl += "?propertyId=" + propertyId;
        }
        return returnUrl;
    }

    // ===== CONTRACTORS SECTION =====
    
    @GetMapping("/contractors")
    public String listContractors(@RequestParam(value = "search", required = false) String search,
                                @RequestParam(value = "status", required = false) String status,
                                Model model, Authentication authentication) {
        try {
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
            User user = userService.findById(userId);
            
            List<Customer> contractors = customerService.findContractors();
            
            // Filter by user's customers if not manager or super admin
            if (!user.getRoles().stream().anyMatch(r -> r.getName().contains("MANAGER") || r.getName().contains("SUPER_ADMIN"))) {
                List<Customer> userCustomers = customerService.findByUserId(userId);
                contractors = contractors.stream()
                    .filter(userCustomers::contains)
                    .collect(Collectors.toList());
            }
            
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
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
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

    // ===== EMAIL CONTRACTORS =====

    @GetMapping("/email-contractors")
    public String emailContractorsForm(Model model, Authentication authentication) {
        try {
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
            User user = userService.findById(userId);
            
            List<Customer> contractors = customerService.findContractors();
            
            model.addAttribute("customers", contractors);
            model.addAttribute("customerType", "Contractors");
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "Email Contractors");
            model.addAttribute("backUrl", "/employee/customer/contractors");
            
            return "customer/email-form";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading email form: " + e.getMessage());
            return "error/500";
        }
    }

    @PostMapping("/email-contractors")
    public String sendEmailToContractors(@RequestParam("subject") String subject,
                                    @RequestParam("message") String message,
                                    @RequestParam(value = "selectedIds", required = false) List<Integer> selectedIds,
                                    @RequestParam(value = "emailAll", defaultValue = "false") boolean emailAll,
                                    @RequestParam(value = "preferredOnly", defaultValue = "false") boolean preferredOnly,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        
        if (!emailService.isGmailApiAvailable(authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Gmail API access required. Please ensure you're logged in with Google and have granted Gmail permissions.");
            return "redirect:/employee/customer/email-contractors";
        }
        
        try {
            List<Customer> contractors;
            String filterDescription = "";
            
            if (emailAll || selectedIds == null || selectedIds.isEmpty()) {
                contractors = customerService.findContractors();
                
                // Apply preferred filter if specified
                if (preferredOnly) {
                    contractors = contractors.stream()
                        .filter(contractor -> contractor.getDescription() != null && 
                            contractor.getDescription().toLowerCase().contains("preferred"))
                        .collect(Collectors.toList());
                    filterDescription = "preferred contractors";
                } else {
                    filterDescription = "all contractors";
                }
            } else {
                // Email only selected contractors
                contractors = selectedIds.stream()
                    .map(id -> customerService.findByCustomerId(id.longValue()))
                    .filter(customer -> customer != null && Boolean.TRUE.equals(customer.getIsContractor()))
                    .collect(Collectors.toList());
                filterDescription = "selected contractors";
            }
            
            // Filter out customers with invalid email addresses
            List<Customer> validCustomers = contractors.stream()
                .filter(customer -> emailService.isValidEmail(customer.getEmail()))
                .collect(Collectors.toList());
                
            if (validCustomers.isEmpty()) {
                redirectAttributes.addFlashAttribute("warningMessage", 
                    "No " + filterDescription + " with valid email addresses found.");
                return "redirect:/employee/customer/contractors";
            }
            
            int emailsSent = emailService.sendBulkEmail(validCustomers, subject, message, authentication);
            
            if (emailsSent > 0) {
                redirectAttributes.addFlashAttribute("successMessage", 
                    String.format("Successfully sent email to %d out of %d %s.", 
                        emailsSent, validCustomers.size(), filterDescription));
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Failed to send emails. Please check your Gmail API access and try again.");
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error sending bulk email: " + e.getMessage());
        }
        
        return "redirect:/employee/customer/contractors";
    }

    // ===== CUSTOMER DETAILS & MANAGEMENT =====
    
    @GetMapping("/{id:[0-9]+}")  // FIXED: Only match numeric IDs
    public String showCustomerDetail(@PathVariable("id") Long id, Model model, Authentication authentication) {
        try {
            Customer customer = customerService.findByCustomerId(id);
            if (customer == null) {
                return "error/not-found";
            }

            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
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

                        // Filter for tenant assignments
                        List<CustomerPropertyAssignment> tenantAssignments = assignments.stream()
                            .filter(assignment -> assignment.getAssignmentType() == AssignmentType.TENANT)
                            .collect(Collectors.toList());

                        // Add tenant assignments with dates to model
                        model.addAttribute("tenantAssignments", tenantAssignments);

                        // Also add just the properties for backward compatibility
                        List<Property> assignedProperties = tenantAssignments.stream()
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
    public String editCustomerForm(@PathVariable("id") Long id, Model model, Authentication authentication) {
        try {
            Customer customer = customerService.findByCustomerId(id);
            if (customer == null) {
                return "error/not-found";
            }

            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
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

            // Add list of property owners for delegated user/manager assignment
            List<Customer> propertyOwners = customerService.findPropertyOwners();
            System.out.println("🔍 [Edit Customer] Found " + propertyOwners.size() + " property owners for dropdown");
            model.addAttribute("propertyOwners", propertyOwners);

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
    public String updateCustomer(@PathVariable("id") Long id,
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
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
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

            // Get list of property owners for delegated user/manager assignment
            List<Customer> propertyOwners = customerService.findPropertyOwners();
            System.out.println("🔍 [Create Customer] Found " + propertyOwners.size() + " property owners for dropdown");
            if (propertyOwners.size() > 0) {
                System.out.println("🔍 [Create Customer] First property owner: " + propertyOwners.get(0).getName() + " (" + propertyOwners.get(0).getEmail() + ")");
            }
            model.addAttribute("propertyOwners", propertyOwners);

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
                            // Login Credentials
                            @RequestParam(value = "temporaryPassword", required = false) String temporaryPassword,
                            @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
                            // Delegated User / Manager Assignment
                            @RequestParam(value = "managesOwnerId", required = false) Long managesOwnerId,
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
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
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
            } else if ("DELEGATED_USER".equals(finalCustomerType)) {
                customer.setIsPropertyOwner(false);
                customer.setIsTenant(false);
                customer.setIsContractor(false);
                customer.setCustomerType(CustomerType.DELEGATED_USER);
                customer.setEntityType("delegated_user");
                System.out.println("🔍 Setting as DELEGATED_USER");
            } else if ("CONTRACTOR".equals(finalCustomerType)) {
                customer.setIsContractor(true);
                customer.setIsTenant(false);
                customer.setIsPropertyOwner(false);
                customer.setCustomerType(CustomerType.CONTRACTOR);
                customer.setEntityType("contractor");
                System.out.println("🔍 Setting as CONTRACTOR");
            } else if ("MANAGER".equals(finalCustomerType)) {
                customer.setIsContractor(false);
                customer.setIsTenant(false);
                customer.setIsPropertyOwner(false);
                customer.setCustomerType(CustomerType.MANAGER);
                customer.setEntityType("manager");
                System.out.println("🔍 Setting as MANAGER");
            } else if ("ADMIN".equals(finalCustomerType)) {
                customer.setIsContractor(false);
                customer.setIsTenant(false);
                customer.setIsPropertyOwner(false);
                customer.setCustomerType(CustomerType.ADMIN);
                customer.setEntityType("admin");
                System.out.println("🔍 Setting as ADMIN");
            } else {
                // Default to tenant if no type specified
                customer.setIsTenant(true);
                customer.setCustomerType(CustomerType.TENANT);
                customer.setEntityType("tenant");
                System.out.println("🔍 Defaulting to TENANT");
            }

            // ===== SET DELEGATED USER / MANAGER OWNER ASSIGNMENT =====
            if (("DELEGATED_USER".equals(finalCustomerType) || "MANAGER".equals(finalCustomerType)) && managesOwnerId != null) {
                try {
                    Customer propertyOwner = customerService.findByCustomerId(managesOwnerId);
                    if (propertyOwner != null) {
                        customer.setManagesOwner(propertyOwner);
                        System.out.println("🔍 Assigned to property owner: " + propertyOwner.getName() + " (ID: " + managesOwnerId + ")");
                    } else {
                        System.err.println("❌ Property owner not found with ID: " + managesOwnerId);
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error assigning property owner: " + e.getMessage());
                }
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

            // ===== CREATE LOGIN CREDENTIALS IF PROVIDED =====
            if (temporaryPassword != null && !temporaryPassword.trim().isEmpty()) {
                System.out.println("🔍 Temporary password provided, creating login credentials...");

                // Validate password match
                if (confirmPassword == null || !temporaryPassword.equals(confirmPassword)) {
                    System.err.println("❌ Password and confirmation do not match");
                    redirectAttributes.addFlashAttribute("errorMessage",
                        "Customer created but login NOT created: passwords do not match");
                } else if (temporaryPassword.length() < 8) {
                    System.err.println("❌ Password too short (minimum 8 characters)");
                    redirectAttributes.addFlashAttribute("errorMessage",
                        "Customer created but login NOT created: password must be at least 8 characters");
                } else {
                    try {
                        // Check if login already exists
                        CustomerLoginInfo existingLogin = customerLoginInfoService.findByEmail(email);
                        if (existingLogin != null) {
                            System.out.println("⚠️ Login already exists for email: " + email);
                            redirectAttributes.addFlashAttribute("warningMessage",
                                "Customer created but login already exists for this email");
                        } else {
                            // Create new login info
                            CustomerLoginInfo loginInfo = new CustomerLoginInfo();
                            loginInfo.setUsername(email);
                            loginInfo.setPassword(EmailTokenUtils.encodePassword(temporaryPassword)); // BCrypt hash
                            loginInfo.setPasswordSet(true);
                            loginInfo.setAccountLocked(false);
                            loginInfo.setLoginAttempts(0);
                            loginInfo.setCreatedAt(LocalDateTime.now());

                            // Save login info
                            CustomerLoginInfo savedLoginInfo = customerLoginInfoService.save(loginInfo);
                            System.out.println("✅ Login credentials created with ID: " + savedLoginInfo.getId());

                            // Link customer to login info
                            savedCustomer.setCustomerLoginInfo(savedLoginInfo);
                            customerService.save(savedCustomer);
                            System.out.println("✅ Customer linked to login credentials");

                            redirectAttributes.addFlashAttribute("successMessage",
                                "Customer and login credentials created successfully! User can now log in with email and the temporary password.");
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error creating login: " + e.getMessage());
                        e.printStackTrace();
                        redirectAttributes.addFlashAttribute("warningMessage",
                            "Customer created but failed to create login: " + e.getMessage());
                    }
                }
            } else if ("DELEGATED_USER".equals(finalCustomerType) || "MANAGER".equals(finalCustomerType)) {
                // Auto-create login with default password for delegated users and managers
                System.out.println("🔍 Creating login with default password for " + finalCustomerType);

                try {
                    CustomerLoginInfo existingLogin = customerLoginInfoService.findByEmail(email);
                    if (existingLogin != null) {
                        System.out.println("⚠️ Login already exists for email: " + email);
                    } else {
                        // Create login with default password "123"
                        String defaultPassword = "123";
                        CustomerLoginInfo loginInfo = new CustomerLoginInfo();
                        loginInfo.setUsername(email);
                        loginInfo.setPassword(EmailTokenUtils.encodePassword(defaultPassword));
                        loginInfo.setPasswordSet(true);
                        loginInfo.setAccountLocked(false);
                        loginInfo.setLoginAttempts(0);
                        loginInfo.setCreatedAt(LocalDateTime.now());

                        CustomerLoginInfo savedLoginInfo = customerLoginInfoService.save(loginInfo);
                        System.out.println("✅ Login created with default password for: " + email);

                        savedCustomer.setCustomerLoginInfo(savedLoginInfo);
                        customerService.save(savedCustomer);

                        redirectAttributes.addFlashAttribute("infoMessage",
                            "Login created with temporary password: " + defaultPassword + " - Please change after first login.");
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error creating default login: " + e.getMessage());
                }
            } else {
                System.out.println("🔍 No temporary password provided, skipping login creation");
            }

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

    /**
     * Update customer role and delegated owner assignment
     */
    @PostMapping("/{customerId}/update-role")
    @ResponseBody
    public ResponseEntity<?> updateCustomerRole(
            @PathVariable Long customerId,
            @RequestParam String customerType,
            @RequestParam(required = false) Long managesOwnerId,
            Authentication authentication) {

        try {
            System.out.println("🔍 [Update Role] Customer ID: " + customerId);
            System.out.println("🔍 [Update Role] New customer type: " + customerType);
            System.out.println("🔍 [Update Role] Manages owner ID: " + managesOwnerId);

            // Find customer
            Customer customer = customerService.findByCustomerId(customerId);
            if (customer == null) {
                System.err.println("❌ [Update Role] Customer not found: " + customerId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Customer not found"));
            }

            // Parse and validate customer type
            CustomerType newCustomerType;
            try {
                newCustomerType = CustomerType.valueOf(customerType);
            } catch (IllegalArgumentException e) {
                System.err.println("❌ [Update Role] Invalid customer type: " + customerType);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid customer type: " + customerType));
            }

            // Validate delegated user/manager assignment
            if ((newCustomerType == CustomerType.DELEGATED_USER || newCustomerType == CustomerType.MANAGER)) {
                if (managesOwnerId == null) {
                    System.err.println("❌ [Update Role] Delegated user/manager requires property owner assignment");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Delegated users and managers must be assigned to a property owner"));
                }

                // Verify the property owner exists
                Customer propertyOwner = customerService.findByCustomerId(managesOwnerId);
                if (propertyOwner == null) {
                    System.err.println("❌ [Update Role] Property owner not found: " + managesOwnerId);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Property owner not found"));
                }

                if (propertyOwner.getCustomerType() != CustomerType.PROPERTY_OWNER) {
                    System.err.println("❌ [Update Role] Assigned customer is not a property owner: " + managesOwnerId);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Selected customer is not a property owner"));
                }

                customer.setManagesOwner(propertyOwner);
                System.out.println("✅ [Update Role] Assigned to property owner: " + propertyOwner.getName());
            } else {
                // Clear manages owner for non-delegated types
                if (customer.getManagesOwner() != null) {
                    System.out.println("🔍 [Update Role] Clearing manages owner assignment");
                    customer.setManagesOwner(null);
                }
            }

            // Update customer type
            customer.setCustomerType(newCustomerType);

            // Update entity type and legacy boolean flags for backward compatibility
            switch (newCustomerType) {
                case PROPERTY_OWNER:
                    customer.setEntityType("property_owner");
                    customer.setIsPropertyOwner(true);
                    customer.setIsTenant(false);
                    customer.setIsContractor(false);
                    break;
                case TENANT:
                    customer.setEntityType("tenant");
                    customer.setIsPropertyOwner(false);
                    customer.setIsTenant(true);
                    customer.setIsContractor(false);
                    break;
                case CONTRACTOR:
                    customer.setEntityType("contractor");
                    customer.setIsPropertyOwner(false);
                    customer.setIsTenant(false);
                    customer.setIsContractor(true);
                    break;
                case DELEGATED_USER:
                    customer.setEntityType("delegated_user");
                    customer.setIsPropertyOwner(true); // Delegated users have owner-like permissions
                    customer.setIsTenant(false);
                    customer.setIsContractor(false);
                    break;
                case MANAGER:
                    customer.setEntityType("manager");
                    customer.setIsPropertyOwner(true); // Managers have owner-like permissions
                    customer.setIsTenant(false);
                    customer.setIsContractor(false);
                    break;
                case ADMIN:
                    customer.setEntityType("admin");
                    customer.setIsPropertyOwner(false);
                    customer.setIsTenant(false);
                    customer.setIsContractor(false);
                    break;
                case REGULAR_CUSTOMER:
                default:
                    customer.setEntityType("customer");
                    customer.setIsPropertyOwner(false);
                    customer.setIsTenant(false);
                    customer.setIsContractor(false);
                    break;
            }

            // Save changes
            Customer updatedCustomer = customerService.save(customer);
            System.out.println("✅ [Update Role] Customer role updated successfully: " + updatedCustomer.getName());

            // Auto-create login for DELEGATED_USER and MANAGER if not exists
            String loginMessage = null;
            if ((newCustomerType == CustomerType.DELEGATED_USER || newCustomerType == CustomerType.MANAGER)
                && updatedCustomer.getCustomerLoginInfo() == null
                && updatedCustomer.getEmail() != null) {

                CustomerLoginInfo existingLogin = customerLoginInfoService.findByEmail(updatedCustomer.getEmail());
                if (existingLogin == null) {
                    try {
                        String defaultPassword = "123";
                        CustomerLoginInfo loginInfo = new CustomerLoginInfo();
                        loginInfo.setUsername(updatedCustomer.getEmail());
                        loginInfo.setPassword(EmailTokenUtils.encodePassword(defaultPassword));
                        loginInfo.setPasswordSet(true);
                        loginInfo.setAccountLocked(false);
                        loginInfo.setLoginAttempts(0);
                        loginInfo.setCreatedAt(LocalDateTime.now());

                        CustomerLoginInfo savedLoginInfo = customerLoginInfoService.save(loginInfo);
                        updatedCustomer.setCustomerLoginInfo(savedLoginInfo);
                        customerService.save(updatedCustomer);

                        loginMessage = "Login created with temporary password: " + defaultPassword;
                        System.out.println("✅ [Update Role] Login created for: " + updatedCustomer.getEmail());
                    } catch (Exception e) {
                        System.err.println("❌ [Update Role] Failed to create login: " + e.getMessage());
                    }
                }
            }

            return ResponseEntity.ok(Map.of(
                "message", "Customer role updated successfully" + (loginMessage != null ? ". " + loginMessage : ""),
                "customerId", updatedCustomer.getCustomerId(),
                "customerType", updatedCustomer.getCustomerType().name(),
                "managesOwnerId", updatedCustomer.getManagesOwnerId() != null ? updatedCustomer.getManagesOwnerId() : null
            ));

        } catch (Exception e) {
            System.err.println("❌ [Update Role] Error updating customer role: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Error updating customer role: " + e.getMessage()));
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
    public String createCustomerLogin(@PathVariable("id") Long id,
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
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
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

    /**
     * Get all customers as JSON (API endpoint for AJAX)
     * GET /employee/customer/api/all
     */
    @GetMapping("/api/all")
    @ResponseBody
    public ResponseEntity<List<Customer>> getAllCustomersApi(Authentication authentication) {
        try {
            // Check if user has manager or employee role
            if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            List<Customer> customers = customerService.findAll();
            return ResponseEntity.ok(customers);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/manager/all-customers/email")
    public String emailAllCustomersForm(Model model, Authentication authentication) {
        try {
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
            User user = userService.findById(userId);

            // Check if user is manager
            if(!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                return "error/access-denied";
            }

            List<Customer> allCustomers = customerService.findAll();
            List<Block> blocks = blockRepository.findAll();
            List<Property> properties = propertyService.findAll();

            model.addAttribute("customers", allCustomers);
            model.addAttribute("blocks", blocks);
            model.addAttribute("properties", properties);
            model.addAttribute("customerType", "All Customers");
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "Email All Customers");
            model.addAttribute("backUrl", "/employee/customer/manager/all-customers");

            return "customer/email-form";

        } catch (Exception e) {
            model.addAttribute("error", "Error loading email form: " + e.getMessage());
            return "error/500";
        }
    }

    @PostMapping("/manager/all-customers/email")
    public String sendEmailToAllCustomers(@RequestParam("subject") String subject,
                                        @RequestParam("message") String message,
                                        @RequestParam(value = "selectedIds", required = false) List<Integer> selectedIds,
                                        @RequestParam(value = "emailAll", defaultValue = "false") boolean emailAll,
                                        Authentication authentication,
                                        RedirectAttributes redirectAttributes) {

        if (!emailService.isGmailApiAvailable(authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Gmail API access required. Please ensure you're logged in with Google and have granted Gmail permissions.");
            return "redirect:/employee/customer/manager/all-customers/email";
        }

        try {
            List<Customer> customers;

            if (emailAll || selectedIds == null || selectedIds.isEmpty()) {
                customers = customerService.findAll();
            } else {
                customers = selectedIds.stream()
                    .map(id -> customerService.findByCustomerId(id.longValue()))
                    .filter(customer -> customer != null)
                    .collect(Collectors.toList());
            }

            // Filter out customers with invalid email addresses
            List<Customer> validCustomers = customers.stream()
                .filter(customer -> emailService.isValidEmail(customer.getEmail()))
                .collect(Collectors.toList());

            if (validCustomers.isEmpty()) {
                redirectAttributes.addFlashAttribute("warningMessage",
                    "No customers with valid email addresses found.");
                return "redirect:/employee/customer/manager/all-customers";
            }

            int emailsSent = emailService.sendBulkEmail(validCustomers, subject, message, authentication);

            if (emailsSent > 0) {
                redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Successfully sent email to %d out of %d customers.", emailsSent, validCustomers.size()));
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to send emails. Please check your Gmail API access and try again.");
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Error sending bulk email: " + e.getMessage());
        }

        return "redirect:/employee/customer/manager/all-customers";
    }

    @GetMapping("/manager/all-customers")
    public String showAllCustomersManager(@RequestParam(value = "search", required = false) String search,
                                        @RequestParam(value = "type", required = false) String typeFilter,
                                        Model model, Authentication authentication) {
        try {
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
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
                final String searchLower = search.toLowerCase();
                customers = customers.stream()
                    .filter(c -> (c.getName() != null && c.getName().toLowerCase().contains(searchLower)) ||
                                (c.getEmail() != null && c.getEmail().toLowerCase().contains(searchLower)) ||
                                (c.getCity() != null && c.getCity().toLowerCase().contains(searchLower)))
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

    /**
     * Enhanced bulk email with advanced filtering
     */
    @PostMapping("/send-bulk-email")
    public String sendAdvancedBulkEmail(@RequestParam("customerType") String customerType,
                                       @RequestParam("subject") String subject,
                                       @RequestParam("message") String message,
                                       @RequestParam(value = "propertyId", required = false) Long propertyId,
                                       @RequestParam(value = "activeOnly", defaultValue = "false") boolean activeOnly,
                                       @RequestParam(value = "city", required = false) String city,
                                       Authentication authentication,
                                       RedirectAttributes redirectAttributes) {
        
        if (!emailService.isGmailApiAvailable(authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Gmail API access required for bulk email functionality");
            return "redirect:/employee/customer/dashboard";
        }
        
        try {
            List<Customer> customers = new ArrayList<>();
            String filterDescription = "";
            
            // Get customers based on type and filters
            switch (customerType.toLowerCase()) {
                case "property-owners":
                    customers = customerService.findPropertyOwners();
                    filterDescription = "property owners";
                    
                    // Filter by property if specified
                    if (propertyId != null) {
                        List<CustomerPropertyAssignment> propertyAssignments = 
                            customerPropertyAssignmentRepository.findByPropertyId(propertyId);
                        
                        List<Long> ownerCustomerIds = propertyAssignments.stream()
                            .filter(assignment -> assignment.getAssignmentType() == AssignmentType.OWNER)
                            .map(assignment -> assignment.getCustomer().getCustomerId())
                            .collect(Collectors.toList());
                        
                        customers = customers.stream()
                            .filter(owner -> ownerCustomerIds.contains(owner.getCustomerId()))
                            .collect(Collectors.toList());
                            
                        filterDescription += " for property ID " + propertyId;
                    }
                    break;
                    
                case "tenants":
                    if (activeOnly) {
                        // Get only tenants with active property assignments
                        List<CustomerPropertyAssignment> activeAssignments = customerPropertyAssignmentRepository
                            .findByAssignmentType(AssignmentType.TENANT);
                        
                        List<Long> activeTenantIds = activeAssignments.stream()
                            .map(assignment -> assignment.getCustomer().getCustomerId())
                            .distinct()
                            .collect(Collectors.toList());
                            
                        customers = customerService.findTenants().stream()
                            .filter(tenant -> activeTenantIds.contains(tenant.getCustomerId()))
                            .collect(Collectors.toList());
                            
                        filterDescription = "active tenants";
                    } else {
                        customers = customerService.findTenants();
                        filterDescription = "tenants";
                    }
                    
                    // Filter by property if specified
                    if (propertyId != null) {
                        List<CustomerPropertyAssignment> propertyAssignments = 
                            customerPropertyAssignmentRepository.findByPropertyId(propertyId);
                        
                        List<Long> tenantCustomerIds = propertyAssignments.stream()
                            .filter(assignment -> assignment.getAssignmentType() == AssignmentType.TENANT)
                            .map(assignment -> assignment.getCustomer().getCustomerId())
                            .collect(Collectors.toList());
                        
                        customers = customers.stream()
                            .filter(tenant -> tenantCustomerIds.contains(tenant.getCustomerId()))
                            .collect(Collectors.toList());
                            
                        if (!filterDescription.contains("property")) {
                            filterDescription += " for property ID " + propertyId;
                        }
                    }
                    break;
                    
                case "contractors":
                    customers = customerService.findContractors();
                    filterDescription = "contractors";
                    break;
                    
                default:
                    customers = customerService.findAll();
                    filterDescription = "all customers";
                    break;
            }
            
            // Apply city filter if specified
            if (city != null && !city.trim().isEmpty()) {
                customers = customers.stream()
                    .filter(c -> c.getCity() != null && c.getCity().toLowerCase().contains(city.toLowerCase()))
                    .collect(Collectors.toList());
                filterDescription += " in " + city;
            }
            
            // Filter out customers with invalid email addresses
            List<Customer> validCustomers = customers.stream()
                .filter(customer -> emailService.isValidEmail(customer.getEmail()))
                .collect(Collectors.toList());
                
            if (validCustomers.isEmpty()) {
                redirectAttributes.addFlashAttribute("warningMessage", 
                    "No " + filterDescription + " with valid email addresses found.");
                return "redirect:/employee/customer/dashboard";
            }
            
            int emailsSent = emailService.sendBulkEmail(validCustomers, subject, message, authentication);
            
            if (emailsSent > 0) {
                redirectAttributes.addFlashAttribute("successMessage", 
                    String.format("Successfully sent bulk email to %d out of %d %s.", 
                        emailsSent, validCustomers.size(), filterDescription));
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Failed to send emails. Please check your Gmail API access and try again.");
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error sending bulk email: " + e.getMessage());
        }
        
        return "redirect:/employee/customer/dashboard";
    }

    /**
     * Advanced email form with filtering options
     */
    @GetMapping("/bulk-email-form")
    public String showBulkEmailForm(@RequestParam(value = "type", required = false) String customerType,
                                   @RequestParam(value = "propertyId", required = false) Long propertyId,
                                   Model model, Authentication authentication) {
        try {
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
            User user = userService.findById(userId);
            
            if (!emailService.isGmailApiAvailable(authentication)) {
                model.addAttribute("gmailError", "Gmail API access required. Please log in with Google and grant Gmail permissions.");
            }
            
            // Get all properties for property filtering dropdown
            List<Property> properties = propertyService.findAll();
            
            // Get customer counts for display
            int propertyOwnersCount = customerService.findPropertyOwners().size();
            int tenantsCount = customerService.findTenants().size();
            int contractorsCount = customerService.findContractors().size();
            
            // Get active tenant count (tenants with property assignments)
            List<CustomerPropertyAssignment> activeAssignments = customerPropertyAssignmentRepository
                .findByAssignmentType(AssignmentType.TENANT);
            int activeTenantsCount = (int) activeAssignments.stream()
                .map(assignment -> assignment.getCustomer().getCustomerId())
                .distinct()
                .count();
            
            model.addAttribute("properties", properties);
            model.addAttribute("propertyOwnersCount", propertyOwnersCount);
            model.addAttribute("tenantsCount", tenantsCount);
            model.addAttribute("activeTenantsCount", activeTenantsCount);
            model.addAttribute("contractorsCount", contractorsCount);
            model.addAttribute("selectedCustomerType", customerType);
            model.addAttribute("selectedPropertyId", propertyId);
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "Send Bulk Email");
            model.addAttribute("backUrl", "/employee/customer/dashboard");
            
            return "customer/bulk-email-form";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading bulk email form: " + e.getMessage());
            return "error/500";
        }
    }
    
    @PostMapping("/{id:[0-9]+}/send-email")
    public String sendIndividualEmail(@PathVariable("id") Long id,
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

    // ===== PASSWORD MANAGEMENT FOR CUSTOMERS =====

    /**
     * Admin reset password to default (123)
     * ONLY for admin/manager users
     */
    @PostMapping("/{id:[0-9]+}/reset-password")
    @ResponseBody
    public ResponseEntity<?> resetPasswordToDefault(@PathVariable("id") Long id,
                                                     Authentication authentication) {
        System.out.println("🔄 RESET PASSWORD REQUEST - Customer ID: " + id);

        try {
            // Log authentication details
            if (authentication == null) {
                System.err.println("❌ RESET PASSWORD: No authentication found!");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }

            System.out.println("🔍 User: " + authentication.getName());
            System.out.println("🔍 Authorities: " + authentication.getAuthorities());

            // Check if user has admin/manager role
            boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN") ||
                                auth.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                                auth.getAuthority().equals("ROLE_MANAGER") ||
                                auth.getAuthority().equals("OIDC_USER"));

            if (!isAdmin) {
                System.err.println("❌ RESET PASSWORD: User lacks admin privileges");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
            }

            Customer customer = customerService.findByCustomerId(id);
            if (customer == null) {
                System.err.println("❌ RESET PASSWORD: Customer not found - ID: " + id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Customer not found"));
            }

            System.out.println("✅ Found customer: " + customer.getEmail());

            // Get customer login info
            CustomerLoginInfo loginInfo = customerLoginInfoService.findByEmail(customer.getEmail());

            if (loginInfo == null) {
                System.err.println("❌ RESET PASSWORD: No login info found for email: " + customer.getEmail());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Customer does not have login credentials. Please create login first."));
            }

            System.out.println("✅ Found login info - ID: " + loginInfo.getId());

            // Reset password to "123"
            String defaultPassword = "123";
            String hashedPassword = EmailTokenUtils.encodePassword(defaultPassword);
            System.out.println("✅ Generated BCrypt hash: " + hashedPassword.substring(0, 20) + "...");

            loginInfo.setPassword(hashedPassword);
            loginInfo.setPasswordSet(true);
            loginInfo.setToken(null); // Clear any existing token
            loginInfo.setTokenExpiresAt(null);
            loginInfo.setAccountLocked(false); // Unlock account if it was locked
            loginInfo.setLoginAttempts(0); // Reset failed login attempts

            CustomerLoginInfo saved = customerLoginInfoService.save(loginInfo);
            System.out.println("✅ Password reset saved for customer: " + customer.getEmail());
            System.out.println("✅ Saved login info ID: " + saved.getId());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Password reset to '123' for " + customer.getEmail(),
                "email", customer.getEmail()
            ));

        } catch (Exception e) {
            System.err.println("❌ EXCEPTION in reset password: " + e.getClass().getName());
            System.err.println("❌ Exception message: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error resetting password: " + e.getMessage()));
        }
    }

    /**
     * Send password setup email for existing customer (useful for owners who haven't set up login yet)
     */
    @PostMapping("/{id:[0-9]+}/send-password-setup")
    public String sendPasswordSetupEmail(@PathVariable("id") Long id,
                                        Authentication authentication,
                                        RedirectAttributes redirectAttributes) {
        try {
            Customer customer = customerService.findByCustomerId(id);
            if (customer == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Customer not found");
                return "redirect:/employee/customer/dashboard";
            }

            // Check if customer already has login info
            CustomerLoginInfo existingLoginInfo = customerLoginInfoService.findByEmail(customer.getEmail());

            if (existingLoginInfo != null && existingLoginInfo.isPasswordSet()) {
                redirectAttributes.addFlashAttribute("warningMessage",
                    "Customer already has password set. Use password reset instead.");
                return "redirect:/employee/customer/" + id;
            }

            // Generate or update token
            CustomerLoginInfo loginInfo;
            if (existingLoginInfo != null) {
                loginInfo = existingLoginInfo;
            } else {
                loginInfo = new CustomerLoginInfo();
                loginInfo.setUsername(customer.getEmail());
                loginInfo.setCustomer(customer);
                loginInfo.setCreatedAt(LocalDateTime.now());
            }

            // Generate new token
            String token = EmailTokenUtils.generateToken();
            loginInfo.setToken(token);
            loginInfo.setTokenExpiresAt(EmailTokenUtils.createExpirationTime(72)); // 72 hours
            loginInfo.setPasswordSet(false);

            customerLoginInfoService.save(loginInfo);

            // Determine customer type
            String customerType = "customer";
            if (Boolean.TRUE.equals(customer.getIsPropertyOwner())) {
                customerType = "property owner";
            } else if (Boolean.TRUE.equals(customer.getIsTenant())) {
                customerType = "tenant";
            } else if (Boolean.TRUE.equals(customer.getIsContractor())) {
                customerType = "contractor";
            }

            // Send password setup email
            if (emailService.isGmailApiAvailable(authentication)) {
                boolean emailSent = sendPasswordSetupEmailToCustomer(customer, token, customerType, authentication);

                if (emailSent) {
                    redirectAttributes.addFlashAttribute("successMessage",
                        "Password setup email sent to " + customer.getEmail() + "!");
                } else {
                    redirectAttributes.addFlashAttribute("warningMessage",
                        "Password setup link created but email could not be sent. Token: " + token);
                }
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                    "Password setup link created. Please manually send this link to the customer: " +
                    "/set-password?token=" + token);
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Error sending password setup email: " + e.getMessage());
        }

        return "redirect:/employee/customer/" + id;
    }

    /**
     * Send password setup email to customer
     */
    private boolean sendPasswordSetupEmailToCustomer(Customer customer, String token,
                                                     String customerType, Authentication authentication) {
        try {
            String subject = "Set Up Your Password - " + capitalizeFirst(customerType) + " Portal Access";
            String portalInstructions = getPortalInstructions(customerType);
            String baseUrl = "https://spoutproperty-hub.onrender.com"; // Your production URL

            String message = String.format(
                "Dear %s,\n\n" +
                "Welcome to Spout Property Hub! Your %s account is ready.\n\n" +
                "Please click the link below to set up your password and access your portal:\n\n" +
                "%s/set-password?token=%s\n\n" +
                "This link will expire in 72 hours.\n\n" +
                "%s\n\n" +
                "Once you've set your password, you can log in at:\n" +
                "%s/customer-login\n\n" +
                "If you did not expect this email or have any questions, please contact us.\n\n" +
                "Best regards,\n" +
                "The Spout Property Hub Team",
                customer.getName() != null ? customer.getName() : "Customer",
                customerType,
                baseUrl,
                token,
                portalInstructions,
                baseUrl
            );

            return emailService.sendEmailToCustomer(customer, subject, message, authentication);

        } catch (Exception e) {
            System.err.println("Error sending password setup email: " + e.getMessage());
            return false;
        }
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