package site.easy.to.build.crm.controller;

import jakarta.persistence.EntityManager;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.entity.settings.TicketEmailSettings;
import site.easy.to.build.crm.google.service.acess.GoogleAccessService;
import site.easy.to.build.crm.google.service.gmail.GoogleGmailApiService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.email.EmailService;
import site.easy.to.build.crm.service.property.PropertyService; // ADD THIS IMPORT
import site.easy.to.build.crm.service.settings.TicketEmailSettingsService;
import site.easy.to.build.crm.service.ticket.TicketService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.*;
import site.easy.to.build.crm.service.contractor.ContractorBidService;
import site.easy.to.build.crm.service.payprop.PayPropMaintenanceSyncService;
import site.easy.to.build.crm.service.payprop.PayPropMaintenanceCategoryService;
import site.easy.to.build.crm.repository.PaymentCategoryRepository;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employee/ticket")
public class TicketController {

    private final TicketService ticketService;
    private final AuthenticationUtils authenticationUtils;
    private final UserService userService;
    private final ContractorBidService contractorBidService;
    private final CustomerService customerService;
    private final TicketEmailSettingsService ticketEmailSettingsService;
    private final GoogleGmailApiService googleGmailApiService;
    private final EntityManager entityManager;
    private final EmailService emailService;
    private final PropertyService propertyService;
    private final PayPropMaintenanceSyncService payPropMaintenanceSyncService;
    private final PayPropMaintenanceCategoryService payPropMaintenanceCategoryService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private PaymentCategoryRepository paymentCategoryRepository;

    // Update constructor to include PropertyService
    @Autowired
    public TicketController(TicketService ticketService, AuthenticationUtils authenticationUtils, 
                            UserService userService, CustomerService customerService,
                            TicketEmailSettingsService ticketEmailSettingsService, 
                            GoogleGmailApiService googleGmailApiService, EntityManager entityManager,
                            EmailService emailService, ContractorBidService contractorBidService,
                            PropertyService propertyService,
                            PayPropMaintenanceSyncService payPropMaintenanceSyncService,
                            PayPropMaintenanceCategoryService payPropMaintenanceCategoryService) {
        this.ticketService = ticketService;
        this.authenticationUtils = authenticationUtils;
        this.userService = userService;
        this.customerService = customerService;
        this.ticketEmailSettingsService = ticketEmailSettingsService;
        this.googleGmailApiService = googleGmailApiService;
        this.entityManager = entityManager;
        this.emailService = emailService;
        this.contractorBidService = contractorBidService;
        this.propertyService = propertyService;
        this.payPropMaintenanceSyncService = payPropMaintenanceSyncService;
        this.payPropMaintenanceCategoryService = payPropMaintenanceCategoryService;
    }

    @GetMapping("/show-ticket/{id}")
    public String showTicketDetails(@PathVariable("id") int id, Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }

        Ticket ticket = ticketService.findByTicketId(id);
        if(ticket == null) {
            return "error/not-found";
        }
        User employee = ticket.getEmployee();
        if(!AuthorizationUtil.checkIfUserAuthorized(employee,loggedInUser) && !AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "error/access-denied";
        }

        model.addAttribute("ticket",ticket);
        return "ticket/show-ticket";
    }

    // ===== FIXED: Manager Ticket Views =====
    
    @GetMapping("/manager/all-tickets")
    public String showAllTicketsManager(@RequestParam(value = "type", required = false) String typeFilter,
                                      @RequestParam(value = "status", required = false) String statusFilter,
                                      Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }
        
        // Check if user is manager
        if(!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "error/access-denied";
        }
        
        List<Ticket> tickets = ticketService.findAll();
        
        // Apply filters if provided
        if (typeFilter != null && !typeFilter.trim().isEmpty()) {
            tickets = tickets.stream()
                .filter(ticket -> typeFilter.equalsIgnoreCase(ticket.getType()))
                .collect(Collectors.toList());
        }
        
        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            tickets = tickets.stream()
                .filter(ticket -> statusFilter.equalsIgnoreCase(ticket.getStatus()))
                .collect(Collectors.toList());
        }
        
        model.addAttribute("tickets", tickets);
        model.addAttribute("typeFilter", typeFilter);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("pageTitle", "All Tickets - Manager View");
        return "employee/ticket/manager/all-tickets";
    }

    // ===== LEGACY ROUTE (Keep for backward compatibility) =====
    @GetMapping("/manager/view-all-tickets")
    public String showAllTickets(Model model, Authentication authentication) {
        // Redirect to the new consistent route
        return "redirect:/employee/ticket/manager/all-tickets";
    }

    // ===== ENHANCED BID MANAGEMENT SYSTEM =====
    
    @GetMapping("/pending-bids")
    public String showPendingBids(@RequestParam(value = "ticketId", required = false) Integer ticketId,
                                 @RequestParam(value = "status", required = false) String statusFilter,
                                 Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }
        
        // For now, we'll show tickets that need contractor assignment
        List<Ticket> ticketsNeedingBids;
        
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            // Managers see all tickets
            ticketsNeedingBids = ticketService.findAll();
        } else {
            // Employees see only their tickets
            ticketsNeedingBids = ticketService.findEmployeeTickets(userId);
        }
        
        // Filter for tickets that could use contractor bids (maintenance type, open status)
        ticketsNeedingBids = ticketsNeedingBids.stream()
            .filter(ticket -> "maintenance".equalsIgnoreCase(ticket.getType()) || 
                            "emergency".equalsIgnoreCase(ticket.getType()))
            .filter(ticket -> "open".equalsIgnoreCase(ticket.getStatus()) || 
                            "in-progress".equalsIgnoreCase(ticket.getStatus()))
            .collect(Collectors.toList());
        
        // Apply filters if provided
        if (ticketId != null) {
            ticketsNeedingBids = ticketsNeedingBids.stream()
                .filter(ticket -> ticket.getTicketId() == ticketId)
                .collect(Collectors.toList());
        }
        
        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            ticketsNeedingBids = ticketsNeedingBids.stream()
                .filter(ticket -> statusFilter.equalsIgnoreCase(ticket.getStatus()))
                .collect(Collectors.toList());
        }
        
        // Get contractors for bid invitations
        List<Customer> contractors = customerService.findContractors();
        
        model.addAttribute("tickets", ticketsNeedingBids);
        model.addAttribute("contractors", contractors);
        model.addAttribute("ticketIdFilter", ticketId);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("pageTitle", "Pending Contractor Bids");
        model.addAttribute("user", loggedInUser);
        
        return "employee/ticket/pending-bids";
    }
    
    @GetMapping("/property/{propertyId}/maintenance-history")
    public String showPropertyMaintenanceHistory(@PathVariable Long propertyId, Model model, Authentication authentication) {
        try {
            System.out.println("=== DEBUG: Property Maintenance History ===");
            System.out.println("Property ID: " + propertyId);
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));
            if(loggedInUser.isInactiveUser()) {
                return "error/account-inactive";
            }

            // Get all tickets first
            List<Ticket> allTickets = ticketService.findAll();
            System.out.println("Total tickets in system: " + allTickets.size());
            
            // Filter maintenance and emergency tickets
            List<Ticket> maintenanceTickets = allTickets.stream()
                .filter(ticket -> {
                    String type = ticket.getType();
                    return "maintenance".equalsIgnoreCase(type) || "emergency".equalsIgnoreCase(type);
                })
                .collect(Collectors.toList());
            
            System.out.println("Maintenance/Emergency tickets: " + maintenanceTickets.size());
            
            // Method 1: Link via PayProp Property IDs (if populated)
            // Get property PayProp IDs for comparison
            Property property = propertyService.findById(propertyId);
            if (property == null) {
                System.out.println("❌ Property not found: " + propertyId);
                model.addAttribute("tickets", new ArrayList<>());
                model.addAttribute("propertyId", propertyId);
                model.addAttribute("errorMessage", "Property not found");
                return "employee/ticket/maintenance-history";
            }
            
            Set<String> propertyPayPropIds = new HashSet<>();
            if (property.getPayPropId() != null) {
                propertyPayPropIds.add(property.getPayPropId());
            }
            if (property.getPayPropPropertyId() != null) {
                propertyPayPropIds.add(property.getPayPropPropertyId());
            }
            if (property.getCustomerId() != null) {
                propertyPayPropIds.add(property.getCustomerId());
            }
            
            System.out.println("Property PayProp IDs to match: " + propertyPayPropIds);
            
            // Method 2: Use customer_property_assignments table
            // Get all customers assigned to this property
            String assignmentQuery = """
                SELECT DISTINCT c.customer_id 
                FROM customers c 
                JOIN customer_property_assignments cpa ON c.customer_id = cpa.customer_id 
                WHERE cpa.property_id = ?
                """;
                
            @SuppressWarnings("unchecked")
            List<Number> rawCustomerIds = entityManager.createNativeQuery(assignmentQuery)
                .setParameter(1, propertyId)
                .getResultList();
                
            // FIXED: Convert Number results to Long (handles both Integer and Long from database)
            List<Long> assignedCustomerIds = rawCustomerIds.stream()
                .map(Number::longValue)
                .collect(Collectors.toList());
                
            System.out.println("Customers assigned to property " + propertyId + ": " + assignedCustomerIds);
            
            // Filter tickets by multiple linking methods
            List<Ticket> propertyTickets = maintenanceTickets.stream()
                .filter(ticket -> {
                    // Method 1: Direct PayProp property ID match
                    if (ticket.getPayPropPropertyId() != null && 
                        propertyPayPropIds.contains(ticket.getPayPropPropertyId())) {
                        System.out.println("✅ Ticket #" + ticket.getTicketId() + 
                            " MATCHES via PayProp Property ID: " + ticket.getPayPropPropertyId());
                        return true;
                    }
                    
                    // Method 2: Customer assignment match - FIXED: Use Long comparison
                    if (ticket.getCustomer() != null && 
                        assignedCustomerIds.contains(ticket.getCustomer().getCustomerId())) {
                        System.out.println("✅ Ticket #" + ticket.getTicketId() + 
                            " MATCHES via Customer Assignment: Customer ID " + ticket.getCustomer().getCustomerId());
                        return true;
                    }
                    
                    // Method 3: Legacy assigned_property_id match (fallback)
                    if (ticket.getCustomer() != null && 
                        ticket.getCustomer().getAssignedPropertyId() != null &&
                        ticket.getCustomer().getAssignedPropertyId().equals(propertyId)) {
                        System.out.println("✅ Ticket #" + ticket.getTicketId() + 
                            " MATCHES via Legacy Assignment: Property ID " + propertyId);
                        return true;
                    }
                    
                    return false;
                })
                .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt()))
                .collect(Collectors.toList());

            System.out.println("Final filtered tickets for property " + propertyId + ": " + propertyTickets.size());
            
            // Debug: Show which tickets were found and why
            propertyTickets.forEach(ticket -> {
                System.out.println("📋 Ticket #" + ticket.getTicketId() + ": " + ticket.getSubject());
                System.out.println("   - Type: " + ticket.getType());
                System.out.println("   - Status: " + ticket.getStatus());
                System.out.println("   - Created: " + ticket.getCreatedAt());
                System.out.println("   - PayProp Property ID: " + ticket.getPayPropPropertyId());
                if (ticket.getCustomer() != null) {
                    System.out.println("   - Customer: " + ticket.getCustomer().getName() + 
                        " (ID: " + ticket.getCustomer().getCustomerId() + ")");
                }
            });
            
            model.addAttribute("tickets", propertyTickets);
            model.addAttribute("propertyId", propertyId);
            model.addAttribute("property", property);
            
            return "employee/ticket/maintenance-history";
            
        } catch (Exception e) {
            System.err.println("ERROR in showPropertyMaintenanceHistory: " + e.getMessage());
            e.printStackTrace();
            
            model.addAttribute("tickets", new ArrayList<>());
            model.addAttribute("propertyId", propertyId);
            model.addAttribute("errorMessage", "Error loading maintenance history: " + e.getMessage());
            return "employee/ticket/maintenance-history";
        }
    }

    @GetMapping("/contractor-bids")
    public String showContractorBids(@RequestParam(value = "ticketId", required = false) Integer ticketId,
                                   Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }
        
        // For now, this will show a placeholder view until bid entities are created
        List<Ticket> tickets;
        
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            tickets = ticketService.findAll();
        } else {
            tickets = ticketService.findEmployeeTickets(userId);
        }
        
        // Filter for maintenance/emergency tickets
        tickets = tickets.stream()
            .filter(ticket -> "maintenance".equalsIgnoreCase(ticket.getType()) || 
                            "emergency".equalsIgnoreCase(ticket.getType()))
            .collect(Collectors.toList());
        
        if (ticketId != null) {
            tickets = tickets.stream()
                .filter(ticket -> ticket.getTicketId() == ticketId)
                .collect(Collectors.toList());
        }
        
        model.addAttribute("tickets", tickets);
        model.addAttribute("ticketIdFilter", ticketId);
        model.addAttribute("pageTitle", "Contractor Bids");
        model.addAttribute("user", loggedInUser);
        
        return "employee/ticket/contractor-bids";
    }
    
    // FIXED: Replace the existing invite contractor bid method with this complete implementation
    @PostMapping("/invite-contractor-bid")
    public String inviteContractorBid(@RequestParam("ticketId") int ticketId,
                                    @RequestParam("contractorIds") List<Long> contractorIds, // FIXED: Integer → Long
                                    @RequestParam(value = "message", required = false) String message,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));
            
            Ticket ticket = ticketService.findByTicketId(ticketId);
            if (ticket == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Ticket not found");
                return "redirect:/employee/ticket/pending-bids";
            }
            
            // Check authorization
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && 
                ticket.getEmployee().getId() != userId) {
                redirectAttributes.addFlashAttribute("errorMessage", "Not authorized to invite bids for this ticket");
                return "redirect:/employee/ticket/pending-bids";
            }
            
            // FIXED: Get contractors and validate they're actually contractors - use Long IDs
            List<Customer> contractors = contractorIds.stream()
                .map(id -> customerService.findByCustomerId(id)) // FIXED: Now correctly passes Long
                .filter(Objects::nonNull)
                .filter(customer -> Boolean.TRUE.equals(customer.getIsContractor()))
                .collect(Collectors.toList());
            
            if (contractors.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "No valid contractors found");
                return "redirect:/employee/ticket/pending-bids";
            }
            
            // Create bid invitations using the bid service
            List<ContractorBid> invitations = contractorBidService.createBidInvitations(
                ticket, contractors, (long) userId);
            
            // Update ticket status
            ticket.setStatus("bidding");
            ticket.setDescription(ticket.getDescription() + 
                String.format("\n\n[Bid Invitations Sent: %s to %d contractors by %s]", 
                    LocalDateTime.now(), contractors.size(), loggedInUser.getName()));
            ticketService.save(ticket);
            
            // Send email invitations to contractors
            int emailsSent = 0;
            for (Customer contractor : contractors) {
                if (emailService.sendContractorBidInvitation(contractor, ticket, authentication)) {
                    emailsSent++;
                }
            }
            
            // Send alert to property owner
            Customer propertyOwner = findPropertyOwnerForTicket(ticket);
            if (propertyOwner != null) {
                emailService.sendNotificationEmail(propertyOwner, 
                    "Contractors Invited - " + ticket.getSubject(),
                    String.format("We have invited %d contractors to bid on your maintenance request. " +
                                "We will notify you once bids are received and reviewed.", contractors.size()),
                    authentication);
            }
            
            redirectAttributes.addFlashAttribute("successMessage", 
                String.format("Created %d bid invitations for ticket #%d (%d emails sent successfully)", 
                            invitations.size(), ticketId, emailsSent));
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error sending bid invitations: " + e.getMessage());
        }
        
        return "redirect:/employee/ticket/pending-bids";
    }

    @PostMapping("/{ticketId}/select-contractor")
    public String selectContractor(@PathVariable int ticketId,
                                  @RequestParam("contractorId") Long contractorId,
                                  @RequestParam("approvedAmount") BigDecimal approvedAmount,
                                  @RequestParam(value = "notes", required = false) String notes,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));
            
            Ticket ticket = ticketService.findByTicketId(ticketId);
            Customer contractor = customerService.findByCustomerId(contractorId);
            
            if (ticket == null || contractor == null || !Boolean.TRUE.equals(contractor.getIsContractor())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Invalid ticket or contractor");
                return "redirect:/employee/ticket/" + ticketId + "/bids";
            }
            
            // Check authorization
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && 
                ticket.getEmployee().getId() != userId) {
                redirectAttributes.addFlashAttribute("errorMessage", "Not authorized to select contractor");
                return "redirect:/employee/ticket/" + ticketId + "/bids";
            }
            
            // Update ticket with selected contractor
            ticket.setSelectedContractorId(contractorId);
            ticket.setApprovedAmount(approvedAmount);
            ticket.setStatus("contractor-selected");
            
            if (notes != null && !notes.trim().isEmpty()) {
                ticket.setDescription(ticket.getDescription() + 
                    String.format("\n\n[Contractor Selected: %s - Amount: £%.2f - Notes: %s - By: %s on %s]",
                                 contractor.getName(), approvedAmount, notes, loggedInUser.getName(), LocalDateTime.now()));
            }
            
            ticketService.save(ticket);
            
            // Send notifications
            // Notify selected contractor
            emailService.sendJobAwardNotification(contractor, ticket, authentication);
            
            // Notify property owner
            Customer propertyOwner = findPropertyOwnerForTicket(ticket);
            if (propertyOwner != null) {
                emailService.sendNotificationEmail(propertyOwner,
                    "Contractor Selected - " + ticket.getSubject(),
                    String.format("We have selected %s to complete your maintenance request. " +
                                "Approved amount: £%.2f. Work will begin shortly.",
                                contractor.getName(), approvedAmount),
                    authentication);
            }
            
            redirectAttributes.addFlashAttribute("successMessage", 
                String.format("Contractor %s selected for £%.2f", contractor.getName(), approvedAmount));
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error selecting contractor: " + e.getMessage());
        }
        
        return "redirect:/employee/ticket/" + ticketId;
    }

    // ===== WORK MANAGEMENT METHODS =====

    @PostMapping("/{ticketId}/start-work")
    public String startWork(@PathVariable int ticketId,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        try {
            Ticket ticket = ticketService.findByTicketId(ticketId);
            if (ticket == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Ticket not found");
                return "redirect:/employee/ticket/assigned-tickets";
            }
            
            if (!"contractor-selected".equals(ticket.getStatus())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Cannot start work - contractor not selected");
                return "redirect:/employee/ticket/" + ticketId;
            }
            
            ticket.setStatus("work-in-progress");
            ticket.setWorkStartedAt(LocalDateTime.now());
            ticketService.save(ticket);
            
            // Notify stakeholders
            Customer propertyOwner = findPropertyOwnerForTicket(ticket);
            if (propertyOwner != null) {
                emailService.sendNotificationEmail(propertyOwner,
                    "Work Started - " + ticket.getSubject(),
                    "The contractor has started work on your maintenance request. " +
                    "We will notify you upon completion.",
                    authentication);
            }
            
            redirectAttributes.addFlashAttribute("successMessage", "Work has been started");
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error starting work: " + e.getMessage());
        }
        
        return "redirect:/employee/ticket/" + ticketId;
    }

    // Add method to show actual bids for a ticket
    @GetMapping("/{ticketId}/bids")
    public String viewTicketBids(@PathVariable int ticketId, Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }

        Ticket ticket = ticketService.findByTicketId(ticketId);
        if(ticket == null) {
            return "error/not-found";
        }

        // Check authorization
        if(!AuthorizationUtil.checkIfUserAuthorized(ticket.getEmployee(), loggedInUser) && 
        !AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "error/access-denied";
        }

        // Get actual bids for this ticket
        List<ContractorBid> bids = contractorBidService.findBidsForTicket(ticketId);
        List<ContractorBid> submittedBids = contractorBidService.findSubmittedBidsForTicket(ticketId);
        Optional<ContractorBid> acceptedBid = contractorBidService.findAcceptedBidForTicket(ticketId);
        
        // Get available contractors for additional invitations
        List<Customer> availableContractors = customerService.findContractors().stream()
            .filter(contractor -> bids.stream()
                .noneMatch(bid -> bid.getContractor().getCustomerId().equals(contractor.getCustomerId())))
            .collect(Collectors.toList());
        
        model.addAttribute("ticket", ticket);
        model.addAttribute("allBids", bids);
        model.addAttribute("submittedBids", submittedBids);
        model.addAttribute("acceptedBid", acceptedBid.orElse(null));
        model.addAttribute("availableContractors", availableContractors);
        model.addAttribute("user", loggedInUser);
        model.addAttribute("canManageBids", 
            AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") || 
            ticket.getEmployee().getId() == userId);
        
        return "ticket/view-bids";
    }


    @PostMapping("/{ticketId}/complete-work")
    public String completeWork(@PathVariable int ticketId,
                              @RequestParam("actualCost") BigDecimal actualCost,
                              @RequestParam("actualHours") Integer actualHours,
                              @RequestParam(value = "completionNotes", required = false) String completionNotes,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            Ticket ticket = ticketService.findByTicketId(ticketId);
            if (ticket == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Ticket not found");
                return "redirect:/employee/ticket/assigned-tickets";
            }
            
            if (!"work-in-progress".equals(ticket.getStatus())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Cannot complete work - work not in progress");
                return "redirect:/employee/ticket/" + ticketId;
            }
            
            ticket.setStatus("work-completed");
            ticket.setWorkCompletedAt(LocalDateTime.now());
            ticket.setActualCost(actualCost);
            ticket.setActualHours(actualHours);
            
            if (completionNotes != null && !completionNotes.trim().isEmpty()) {
                ticket.setDescription(ticket.getDescription() + 
                    String.format("\n\n[Work Completed: %s - Cost: £%.2f - Hours: %d - Notes: %s]",
                                 LocalDateTime.now(), actualCost, actualHours, completionNotes));
            }
            
            ticketService.save(ticket);
            
            // Send completion notifications
            // Notify property owner
            Customer propertyOwner = findPropertyOwnerForTicket(ticket);
            if (propertyOwner != null) {
                emailService.sendWorkCompletionNotification(propertyOwner, ticket, authentication);
            }
            
            // Notify tenant if different
            Customer tenant = findTenantForTicket(ticket);
            if (tenant != null && !tenant.equals(propertyOwner)) {
                emailService.sendWorkCompletionNotification(tenant, ticket, authentication);
            }
            
            redirectAttributes.addFlashAttribute("successMessage", 
                String.format("Work completed - Cost: £%.2f, Hours: %d", actualCost, actualHours));
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error completing work: " + e.getMessage());
        }
        
        return "redirect:/employee/ticket/" + ticketId;
    }

    // ===== PAYMENT PROCESSING =====

    @PostMapping("/{ticketId}/process-payment")
    public String processPayment(@PathVariable int ticketId,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            Ticket ticket = ticketService.findByTicketId(ticketId);
            if (ticket == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Ticket not found");
                return "redirect:/employee/ticket/assigned-tickets";
            }
            
            if (!"work-completed".equals(ticket.getStatus()) || ticket.getApprovedAmount() == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Cannot process payment - work not completed or amount not approved");
                return "redirect:/employee/ticket/" + ticketId;
            }
            
            // TODO: Integrate with PayProp payment processing
            // For now, just update status
            ticket.setStatus("payment-pending");
            ticket.setPaymentReference("PAY-" + System.currentTimeMillis());
            ticketService.save(ticket);
            
            // Send payment notifications
            Customer contractor = customerService.findByCustomerId(ticket.getSelectedContractorId());
            if (contractor != null) {
                emailService.sendPaymentNotification(contractor, ticket, 
                    "£" + ticket.getApprovedAmount().toString(), authentication);
            }
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Payment processing initiated - Reference: " + ticket.getPaymentReference());
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error processing payment: " + e.getMessage());
        }
        
        return "redirect:/employee/ticket/" + ticketId;
    }

    // ===== EXISTING TICKET MANAGEMENT =====

    @GetMapping("/created-tickets")
    public String showCreatedTicket(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        List<Ticket> tickets = ticketService.findManagerTickets(userId);
        model.addAttribute("tickets",tickets);
        return "ticket/my-tickets";
    }

    @GetMapping("/assigned-tickets")
    public String showEmployeeTicket(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        List<Ticket> tickets = ticketService.findEmployeeTickets(userId);
        model.addAttribute("tickets",tickets);
        return "ticket/my-tickets";
    }
    
    @GetMapping("/create-ticket")
    public String showTicketCreationForm(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(Long.valueOf(userId));
        if(user.isInactiveUser()) {
            return "error/account-inactive";
        }
        List<User> employees = new ArrayList<>();
        List<Customer> customers;

        if(AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            employees = userService.findAll();
            customers = customerService.findAll();
        } else {
            employees.add(user);
            customers = customerService.findByUserId(user.getId().longValue());
        }

        model.addAttribute("employees",employees);
        model.addAttribute("customers",customers);
        model.addAttribute("ticket", new Ticket());
        
        // Add maintenance categories if service is available
        try {
            if (payPropMaintenanceCategoryService != null) {
                model.addAttribute("maintenanceCategories", payPropMaintenanceCategoryService.getAllMaintenanceCategories());
            }
        } catch (Exception e) {
            // Log error but continue - form will work without categories
            System.err.println("Warning: Could not load maintenance categories: " + e.getMessage());
        }
        
        return "ticket/create-ticket";
    }

    @PostMapping("/create-ticket")
    public String createTicket(@ModelAttribute("ticket") @Validated Ticket ticket, BindingResult bindingResult, 
                               @RequestParam("customerId") Long customerId,
                               @RequestParam Map<String, String> formParams, Model model,
                               @RequestParam("employeeId") int employeeId, Authentication authentication) {

        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User manager = userService.findById(Long.valueOf(userId));
        if(manager == null) {
            return "error/500";
        }
        if(manager.isInactiveUser()) {
            return "error/account-inactive";
        }
        if(bindingResult.hasErrors()) {
            List<User> employees = new ArrayList<>();
            List<Customer> customers;

            if(AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                employees = userService.findAll();
                customers = customerService.findAll();
            } else {
                employees.add(manager);
                customers = customerService.findByUserId(manager.getId().longValue());
            }

            model.addAttribute("employees",employees);
            model.addAttribute("customers",customers);
            
            // Add maintenance categories if service is available
            try {
                if (payPropMaintenanceCategoryService != null) {
                    model.addAttribute("maintenanceCategories", payPropMaintenanceCategoryService.getAllMaintenanceCategories());
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not load maintenance categories: " + e.getMessage());
            }
            
            return "ticket/create-ticket";
        }

        User employee = userService.findById(Long.valueOf(employeeId));
        Customer customer = customerService.findByCustomerId(customerId);

        if(employee == null || customer == null) {
            return "error/500";
        }
        if(AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
            if(userId != employeeId || (customer.getUser() != null && customer.getUser().getId() != userId)) {
                return "error/500";
            }
        }

        ticket.setCustomer(customer);
        ticket.setManager(manager);
        ticket.setEmployee(employee);
        ticket.setCreatedAt(LocalDateTime.now());

        // ENHANCEMENT: Pre-populate PayProp fields if this is a maintenance ticket and PayProp is enabled
        if (ticket.getType() != null && ticket.getType().toLowerCase().contains("maintenance")) {
            try {
                populatePayPropFieldsForNewTicket(ticket);
            } catch (Exception e) {
                System.err.println("Warning: Could not populate PayProp fields for new ticket: " + e.getMessage());
                // Continue with ticket creation even if PayProp field population fails
            }
        }

        ticketService.save(ticket);

        // Sync ticket to PayProp if it's a maintenance ticket
        try {
            if (payPropMaintenanceSyncService != null && 
                (ticket.getType() != null && ticket.getType().toLowerCase().contains("maintenance"))) {
                // Use the export method which handles individual tickets
                payPropMaintenanceSyncService.exportMaintenanceTicketsToPayProp();
            }
        } catch (Exception e) {
            // Log error but don't fail ticket creation if PayProp sync fails
            System.err.println("Warning: Failed to sync ticket to PayProp: " + e.getMessage());
        }

        return "redirect:/employee/ticket/assigned-tickets";
    }

    @GetMapping("/update-ticket/{id}")
    public String showTicketUpdatingForm(Model model, @PathVariable("id") int id, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }

        Ticket ticket = ticketService.findByTicketId(id);
        if(ticket == null) {
            return "error/not-found";
        }

        User employee = ticket.getEmployee();
        if(!AuthorizationUtil.checkIfUserAuthorized(employee,loggedInUser) && !AuthorizationUtil.hasRole(authentication,"ROLE_MANAGER")) {
            return "error/access-denied";
        }

        List<User> employees = new ArrayList<>();
        List<Customer> customers = new ArrayList<>();

        if(AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            employees = userService.findAll();
            customers = customerService.findAll();
        } else {
            employees.add(loggedInUser);
            //In case Employee's manager assign lead for the employee with a customer that's not created by this employee
            //As a result of that the employee mustn't change the customer
            if(!Objects.equals(employee.getId(), ticket.getManager().getId())) {
                customers.add(ticket.getCustomer());
            } else {
                customers = customerService.findByUserId(loggedInUser.getId().longValue());
            }
        }

        model.addAttribute("employees",employees);
        model.addAttribute("customers",customers);
        model.addAttribute("ticket", ticket);
        return "ticket/update-ticket";
    }

    @PostMapping("/update-ticket")
    public String updateTicket(@ModelAttribute("ticket") @Validated Ticket ticket, BindingResult bindingResult,
                               @RequestParam("customerId") Long customerId, @RequestParam("employeeId") int employeeId,
                               Authentication authentication, Model model) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }

        Ticket previousTicket = ticketService.findByTicketId(ticket.getTicketId());
        if(previousTicket == null) {
            return "error/not-found";
        }
        Ticket originalTicket = new Ticket();
        BeanUtils.copyProperties(previousTicket, originalTicket);

        User manager = originalTicket.getManager();
        User employee = userService.findById(Long.valueOf(employeeId));
        Customer customer = customerService.findByCustomerId(customerId);

        if(manager == null || employee ==null || customer == null) {
            return "error/500";
        }

        if(bindingResult.hasErrors()) {
            ticket.setEmployee(employee);
            ticket.setManager(manager);
            ticket.setCustomer(customer);

            List<User> employees = new ArrayList<>();
            List<Customer> customers = new ArrayList<>();

            if(AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                employees = userService.findAll();
                customers = customerService.findAll();
            } else {
                employees.add(loggedInUser);
                //In case Employee's manager assign lead for the employee with a customer that's not created by this employee
                //As a result of that the employee mustn't change the customer
                if(!Objects.equals(employee.getId(), ticket.getManager().getId())) {
                    customers.add(ticket.getCustomer());
                } else {
                    customers = customerService.findByUserId(loggedInUser.getId().longValue());
                }
            }

            model.addAttribute("employees",employees);
            model.addAttribute("customers",customers);
            return "ticket/update-ticket";
        }
        if(manager.getId() == employeeId) {
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && (customer.getUser() != null && customer.getUser().getId() != userId)) {
                return "error/500";
            }
        } else {
            // FIXED: Use Long comparison for customerId
            if(!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && !originalTicket.getCustomer().getCustomerId().equals(customerId)) {
                return "error/500";
            }
        }

        if(AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE") && employee.getId() != userId) {
            return "error/500";
        }

        ticket.setCustomer(customer);
        ticket.setManager(manager);
        ticket.setEmployee(employee);
        Ticket currentTicket = ticketService.save(ticket);

        List<String> properties = DatabaseUtil.getColumnNames(entityManager, Ticket.class);
        Map<String, Pair<String,String>> changes = LogEntityChanges.trackChanges(originalTicket,currentTicket,properties);
        boolean isGoogleUser = !(authentication instanceof UsernamePasswordAuthenticationToken);

        if(isGoogleUser && googleGmailApiService != null) {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            if(oAuthUser.getGrantedScopes().contains(GoogleAccessService.SCOPE_GMAIL)) {
                processEmailSettingsChanges(changes, userId, oAuthUser, customer);
            }
        }

        return "redirect:/employee/ticket/assigned-tickets";
    }

    @PostMapping("/delete-ticket/{id}")
    public String deleteTicket(@PathVariable("id") int id, Authentication authentication){
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }

        Ticket ticket = ticketService.findByTicketId(id);

        User employee = ticket.getEmployee();
        if(!AuthorizationUtil.checkIfUserAuthorized(employee,loggedInUser)) {
            return "error/access-denied";
        }

        ticketService.delete(ticket);
        return "redirect:/employee/ticket/assigned-tickets";
    }

    // ===== HELPER METHODS =====

    private Customer findPropertyOwnerForTicket(Ticket ticket) {
        // Look for property owner based on PayProp property ID or customer relationship
        if (ticket.getPayPropPropertyId() != null) {
            // TODO: Use property mapping to find owner when property service is available
            // For now, implement basic logic
            List<Customer> propertyOwners = customerService.findAll().stream()
                .filter(customer -> Boolean.TRUE.equals(customer.getIsPropertyOwner()))
                .collect(Collectors.toList());
            
            // Return first property owner for now - you should implement proper property mapping
            return propertyOwners.isEmpty() ? null : propertyOwners.get(0);
        }
        
        // Fallback: if customer is property owner
        if (ticket.getCustomer() != null && Boolean.TRUE.equals(ticket.getCustomer().getIsPropertyOwner())) {
            return ticket.getCustomer();
        }
        
        return null;
    }

    private Customer findTenantForTicket(Ticket ticket) {
        // Similar logic for finding tenant
        if (ticket.getPayPropTenantId() != null) {
            // TODO: Implement proper tenant lookup when available
            List<Customer> tenants = customerService.findAll().stream()
                .filter(customer -> Boolean.TRUE.equals(customer.getIsTenant()))
                .collect(Collectors.toList());
            
            return tenants.isEmpty() ? null : tenants.get(0);
        }
        
        if (ticket.getCustomer() != null && Boolean.TRUE.equals(ticket.getCustomer().getIsTenant())) {
            return ticket.getCustomer();
        }
        
        return null;
    }

    private void processEmailSettingsChanges(Map<String, Pair<String, String>> changes, int userId, OAuthUser oAuthUser,
                                             Customer customer) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        for (Map.Entry<String, Pair<String, String>> entry : changes.entrySet()) {
            String property = entry.getKey();
            String propertyName = StringUtils.replaceCharToCamelCase(property, '_');
            propertyName = StringUtils.replaceCharToCamelCase(propertyName, ' ');

            String prevState = entry.getValue().getFirst();
            String nextState = entry.getValue().getSecond();

            TicketEmailSettings ticketEmailSettings = ticketEmailSettingsService.findByUserId(userId);

            CustomerLoginInfo customerLoginInfo = customer.getCustomerLoginInfo();
            TicketEmailSettings customerTicketEmailSettings = ticketEmailSettingsService.findByCustomerId(customerLoginInfo.getId());

            if (ticketEmailSettings != null) {
                String getterMethodName = "get" + StringUtils.capitalizeFirstLetter(propertyName);
                Method getterMethod = TicketEmailSettings.class.getMethod(getterMethodName);
                Boolean propertyValue = (Boolean) getterMethod.invoke(ticketEmailSettings);

                Boolean isCustomerLikeToGetNotified = true;
                if(customerTicketEmailSettings != null) {
                    isCustomerLikeToGetNotified = (Boolean) getterMethod.invoke(customerTicketEmailSettings);
                }

                if (isCustomerLikeToGetNotified != null && propertyValue != null && propertyValue && isCustomerLikeToGetNotified) {
                    String emailTemplateGetterMethodName = "get" + StringUtils.capitalizeFirstLetter(propertyName) + "EmailTemplate";
                    Method emailTemplateGetterMethod = TicketEmailSettings.class.getMethod(emailTemplateGetterMethodName);
                    EmailTemplate emailTemplate = (EmailTemplate) emailTemplateGetterMethod.invoke(ticketEmailSettings);
                    String body = emailTemplate.getContent();

                    property = property.replace(' ', '_');
                    String regex = "\\{\\{(.*?)\\}\\}";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(body);

                    while (matcher.find()) {
                        String placeholder = matcher.group(1);
                        if (placeholder.contains("previous") && placeholder.contains(property)) {
                            body = body.replace("{{" + placeholder + "}}", prevState);
                        } else if (placeholder.contains("next") && placeholder.contains(property)) {
                            body = body.replace("{{" + placeholder + "}}", nextState);
                        }
                    }

                    try {
                        googleGmailApiService.sendEmail(oAuthUser, customer.getEmail(), emailTemplate.getName(), body);
                    } catch (IOException | GeneralSecurityException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    /**
     * Pre-populate PayProp fields for newly created maintenance tickets to improve export success rate.
     * Uses the same logic as the improved export methods to resolve PayProp IDs proactively.
     */
    private void populatePayPropFieldsForNewTicket(Ticket ticket) {
        if (ticket.getCustomer() == null || jdbcTemplate == null) {
            return; // Cannot populate without customer or database access
        }

        Customer customer = ticket.getCustomer();
        System.out.println("🔧 Pre-populating PayProp fields for new ticket with customer " + customer.getCustomerId());

        // 1. Try to find PayProp property ID using customer_property_assignments
        try {
            String propertyQuery = "SELECT p.payprop_id FROM customer_property_assignments cpa " +
                                  "JOIN properties p ON cpa.property_id = p.id " +
                                  "WHERE cpa.customer_id = ? AND p.payprop_id IS NOT NULL " +
                                  "ORDER BY cpa.is_primary DESC, cpa.created_at DESC LIMIT 1";
                                  
            List<String> propertyIds = jdbcTemplate.queryForList(propertyQuery, String.class, customer.getCustomerId());
            if (!propertyIds.isEmpty()) {
                String payPropPropertyId = propertyIds.get(0);
                ticket.setPayPropPropertyId(payPropPropertyId);
                System.out.println("✅ Set PayProp property ID: " + payPropPropertyId);
            } else {
                System.out.println("⚠️ No PayProp property ID found for customer " + customer.getCustomerId());
            }
        } catch (Exception e) {
            System.err.println("Error finding PayProp property ID: " + e.getMessage());
        }

        // 2. Try to find PayProp tenant ID
        try {
            if (Boolean.TRUE.equals(customer.getIsTenant()) && customer.getPayPropEntityId() != null) {
                ticket.setPayPropTenantId(customer.getPayPropEntityId());
                System.out.println("✅ Set PayProp tenant ID from customer: " + customer.getPayPropEntityId());
            } else {
                // Check via assignments
                String tenantQuery = "SELECT c.payprop_entity_id FROM customer_property_assignments cpa " +
                                   "JOIN customers c ON cpa.customer_id = c.customer_id " +
                                   "WHERE cpa.customer_id = ? AND cpa.assignment_type = 'TENANT' " +
                                   "AND c.payprop_entity_id IS NOT NULL LIMIT 1";
                                   
                List<String> tenantIds = jdbcTemplate.queryForList(tenantQuery, String.class, customer.getCustomerId());
                if (!tenantIds.isEmpty()) {
                    String payPropTenantId = tenantIds.get(0);
                    ticket.setPayPropTenantId(payPropTenantId);
                    System.out.println("✅ Set PayProp tenant ID via assignment: " + payPropTenantId);
                } else {
                    System.out.println("⚠️ No PayProp tenant ID found for customer " + customer.getCustomerId());
                }
            }
        } catch (Exception e) {
            System.err.println("Error finding PayProp tenant ID: " + e.getMessage());
        }

        // 3. Try to find PayProp category ID from maintenance_category field
        try {
            if (ticket.getMaintenanceCategory() != null && paymentCategoryRepository != null) {
                PaymentCategory category = paymentCategoryRepository.findByCategoryName(ticket.getMaintenanceCategory());
                if (category != null && category.getPayPropCategoryId() != null) {
                    ticket.setPayPropCategoryId(category.getPayPropCategoryId());
                    System.out.println("✅ Set PayProp category ID: " + category.getPayPropCategoryId());
                } else {
                    System.out.println("⚠️ No PayProp category ID found for maintenance category: " + ticket.getMaintenanceCategory());
                }
            }
        } catch (Exception e) {
            System.err.println("Error finding PayProp category ID: " + e.getMessage());
        }

        // Set sync status to false initially (will be set to true after successful export)
        ticket.setPayPropSynced(false);
        
        System.out.println("🔧 PayProp field population completed for ticket");
    }
}