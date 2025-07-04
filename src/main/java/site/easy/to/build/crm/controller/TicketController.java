package site.easy.to.build.crm.controller;

import jakarta.persistence.EntityManager;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
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
import site.easy.to.build.crm.service.settings.TicketEmailSettingsService;
import site.easy.to.build.crm.service.ticket.TicketService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
    private final CustomerService customerService;
    private final TicketEmailSettingsService ticketEmailSettingsService;
    private final GoogleGmailApiService googleGmailApiService;
    private final EntityManager entityManager;

    @Autowired
    public TicketController(TicketService ticketService, AuthenticationUtils authenticationUtils, UserService userService, CustomerService customerService,
                            TicketEmailSettingsService ticketEmailSettingsService, GoogleGmailApiService googleGmailApiService, EntityManager entityManager) {
        this.ticketService = ticketService;
        this.authenticationUtils = authenticationUtils;
        this.userService = userService;
        this.customerService = customerService;
        this.ticketEmailSettingsService = ticketEmailSettingsService;
        this.googleGmailApiService = googleGmailApiService;
        this.entityManager = entityManager;
    }

    @GetMapping("/show-ticket/{id}")
    public String showTicketDetails(@PathVariable("id") int id, Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(userId);
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
        User loggedInUser = userService.findById(userId);
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

    // ===== BID MANAGEMENT SYSTEM =====
    
    @GetMapping("/pending-bids")
    public String showPendingBids(@RequestParam(value = "ticketId", required = false) Integer ticketId,
                                 @RequestParam(value = "status", required = false) String statusFilter,
                                 Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(userId);
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
    
    @GetMapping("/contractor-bids")
    public String showContractorBids(@RequestParam(value = "ticketId", required = false) Integer ticketId,
                                   Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(userId);
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
    
    @PostMapping("/invite-contractor-bid")
    public String inviteContractorBid(@RequestParam("ticketId") int ticketId,
                                     @RequestParam("contractorIds") List<Integer> contractorIds,
                                     @RequestParam(value = "message", required = false) String message,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(userId);
            
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
            
            // For now, just update ticket status to indicate bids are being sought
            if ("open".equalsIgnoreCase(ticket.getStatus())) {
                ticket.setStatus("bidding");
                ticket.setDescription(ticket.getDescription() + "\n\n[Bid Invitations Sent: " + 
                                    LocalDateTime.now() + " to " + contractorIds.size() + " contractors]");
                ticketService.save(ticket);
            }
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Bid invitations sent to " + contractorIds.size() + " contractors for ticket #" + ticketId);
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error sending bid invitations: " + e.getMessage());
        }
        
        return "redirect:/employee/ticket/pending-bids";
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
        User user = userService.findById(userId);
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
            customers = customerService.findByUserId(user.getId());
        }

        model.addAttribute("employees",employees);
        model.addAttribute("customers",customers);
        model.addAttribute("ticket", new Ticket());
        return "ticket/create-ticket";
    }

    @PostMapping("/create-ticket")
    public String createTicket(@ModelAttribute("ticket") @Validated Ticket ticket, BindingResult bindingResult, @RequestParam("customerId") int customerId,
                               @RequestParam Map<String, String> formParams, Model model,
                               @RequestParam("employeeId") int employeeId, Authentication authentication) {

        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User manager = userService.findById(userId);
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
                customers = customerService.findByUserId(manager.getId());
            }

            model.addAttribute("employees",employees);
            model.addAttribute("customers",customers);
            return "ticket/create-ticket";
        }

        User employee = userService.findById(employeeId);
        Customer customer = customerService.findByCustomerId(customerId);

        if(employee == null || customer == null) {
            return "error/500";
        }
        if(AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
            if(userId != employeeId || customer.getUser().getId() != userId) {
                return "error/500";
            }
        }

        ticket.setCustomer(customer);
        ticket.setManager(manager);
        ticket.setEmployee(employee);
        ticket.setCreatedAt(LocalDateTime.now());

        ticketService.save(ticket);

        return "redirect:/employee/ticket/assigned-tickets";
    }

    @GetMapping("/update-ticket/{id}")
    public String showTicketUpdatingForm(Model model, @PathVariable("id") int id, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(userId);
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
                customers = customerService.findByUserId(loggedInUser.getId());
            }
        }

        model.addAttribute("employees",employees);
        model.addAttribute("customers",customers);
        model.addAttribute("ticket", ticket);
        return "ticket/update-ticket";
    }

    @PostMapping("/update-ticket")
    public String updateTicket(@ModelAttribute("ticket") @Validated Ticket ticket, BindingResult bindingResult,
                               @RequestParam("customerId") int customerId, @RequestParam("employeeId") int employeeId,
                               Authentication authentication, Model model) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(userId);
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
        User employee = userService.findById(employeeId);
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
                    customers = customerService.findByUserId(loggedInUser.getId());
                }
            }

            model.addAttribute("employees",employees);
            model.addAttribute("customers",customers);
            return "ticket/update-ticket";
        }
        if(manager.getId() == employeeId) {
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && customer.getUser().getId() != userId) {
                return "error/500";
            }
        } else {
            if(!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && originalTicket.getCustomer().getCustomerId() != customerId) {
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
        User loggedInUser = userService.findById(userId);
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
}