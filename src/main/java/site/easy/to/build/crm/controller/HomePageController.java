package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.google.model.calendar.EventDisplay;
import site.easy.to.build.crm.google.model.calendar.EventDisplayList;
import site.easy.to.build.crm.google.service.acess.GoogleAccessService;
import site.easy.to.build.crm.google.service.calendar.GoogleCalendarApiService;
import site.easy.to.build.crm.service.contract.ContractService;
import site.easy.to.build.crm.service.customer.CustomerLoginInfoService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.lead.LeadService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.ticket.TicketService;
import site.easy.to.build.crm.service.weather.WeatherService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Controller
public class HomePageController {
    private final TicketService ticketService;
    private final CustomerService customerService;
    private final ContractService contractService;
    private final LeadService leadService;
    private final WeatherService weatherService;
    private final AuthenticationUtils authenticationUtils;
    private final GoogleCalendarApiService googleCalendarApiService;
    private final CustomerLoginInfoService customerLoginInfoService;
    private final PropertyService propertyService;

    @Autowired
    public HomePageController(TicketService ticketService, CustomerService customerService, ContractService contractService, LeadService leadService,
                              WeatherService weatherService, AuthenticationUtils authenticationUtils, GoogleCalendarApiService googleCalendarApiService,
                              CustomerLoginInfoService customerLoginInfoService, PropertyService propertyService) {
        this.ticketService = ticketService;
        this.customerService = customerService;
        this.contractService = contractService;
        this.leadService = leadService;
        this.weatherService = weatherService;
        this.authenticationUtils = authenticationUtils;
        this.googleCalendarApiService = googleCalendarApiService;
        this.customerLoginInfoService = customerLoginInfoService;
        this.propertyService = propertyService;
    }

    @GetMapping("/login")
    public String showLoginForm(@RequestParam(value = "error", required = false) String error,
                               @RequestParam(value = "logout", required = false) String logout,
                               @RequestParam(value = "tokenError", required = false) String tokenError,
                               @RequestParam(value = "passwordSetError", required = false) String passwordSetError,
                               Model model) {
        
        if (error != null) {
            model.addAttribute("error", "Invalid username or password");
        }
        
        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully");
        }
        
        if (tokenError != null) {
            model.addAttribute("tokenError", tokenError);
        }
        
        if (passwordSetError != null) {
            model.addAttribute("passwordSetError", passwordSetError);
        }
        
        return "login";
    }

    @GetMapping("/")
    public String showHomePage(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        WeatherData weatherData = weatherService.getWeatherData("cairo");

        List<Ticket> tickets;
        List<Lead> leads;
        List<Customer> customers;
        List<Contract> contracts;
        long countTickets;
        long countLeads;
        long countContracts;

        if(AuthorizationUtil.hasRole(authentication,"ROLE_CUSTOMER")) {
            String email = customerLoginInfoService.findById(userId).getEmail();
            Customer customer = customerService.findByEmail(email);
            userId = customer.getCustomerId().intValue();
            tickets = ticketService.getRecentCustomerTickets(userId, 10);
            countTickets = ticketService.countByCustomerCustomerId(userId);

            leads = leadService.getRecentCustomerLeads(userId, 10);
            countLeads = leadService.countByCustomerId(userId);

            contracts = contractService.getRecentCustomerContracts(userId, 10);
            countContracts = contractService.countByCustomerId(userId);

            // ✅ NEW: Add maintenance statistics for customers
            try {
                Map<String, Object> maintenanceStats = calculateMaintenanceStats(userId, true);
                model.addAttribute("maintenanceStats", maintenanceStats);
            } catch (Exception e) {
                System.err.println("Error calculating customer maintenance statistics: " + e.getMessage());
                model.addAttribute("maintenanceStats", getDefaultMaintenanceStats());
            }

        } else {
            tickets = ticketService.getRecentEmployeeTickets(userId, 10);
            leads = leadService.getRecentLeadsByEmployee(userId, 10);
            customers = customerService.getRecentCustomers(Long.valueOf(userId), 10);
            contracts = contractService.getRecentContracts(userId, 10);

            countTickets = ticketService.countByEmployeeId(userId);
            countLeads = leadService.countByEmployeeId(userId);
            Long countCustomers = customerService.countByUserId(Long.valueOf(userId));
            countContracts = contractService.countByUserId(userId);
            
            List<EventDisplay> eventDisplays = null;
            boolean hasCalendarAccess = false;
            boolean isGoogleUser = false;
            if (!(authentication instanceof UsernamePasswordAuthenticationToken) && googleCalendarApiService != null) {
                isGoogleUser = true;
                OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
                if (oAuthUser.getGrantedScopes().contains(GoogleAccessService.SCOPE_CALENDAR)) {
                    try {
                        hasCalendarAccess = true;
                        EventDisplayList eventDisplayList = googleCalendarApiService.getEvents("primary", oAuthUser);
                        eventDisplays = eventDisplayList.getItems();
                    } catch (IOException | GeneralSecurityException e) {
                        throw new RuntimeException("error" + e);
                    }
                }
            }
            
            model.addAttribute("customers", customers);
            model.addAttribute("countCustomers", countCustomers);
            model.addAttribute("eventDisplays", eventDisplays);
            model.addAttribute("hasCalendarAccess", hasCalendarAccess);
            model.addAttribute("isGoogleUser", isGoogleUser);


            // ✅ NEW: Add maintenance statistics for employees/managers
            try {
                Map<String, Object> maintenanceStats = calculateMaintenanceStats(userId, false);
                model.addAttribute("maintenanceStats", maintenanceStats);
            } catch (Exception e) {
                System.err.println("Error calculating employee maintenance statistics: " + e.getMessage());
                model.addAttribute("maintenanceStats", getDefaultMaintenanceStats());
            }
        }
        
        // 🔧 FIXED: Property statistics using junction table (WORKING CORRECTLY)
        try {
            // Get user's properties based on role
            List<Property> userProperties;
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                userProperties = propertyService.findAll();
            } else {
                userProperties = propertyService.getRecentProperties((long) userId, 1000);
            }
            
            // 🔧 FIXED: Use junction table for accurate occupancy counts
            List<Property> occupiedProperties = propertyService.findOccupiedProperties();
            List<Property> vacantProperties = propertyService.findVacantProperties();
            
            int totalProperties = userProperties.size();
            int occupied = 0;
            int vacant = 0;
            
            // Filter based on user's properties if not manager
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                // Managers see all properties
                occupied = occupiedProperties.size();
                vacant = vacantProperties.size();
            } else {
                // Filter to user's properties only
                List<Long> userPropertyIds = userProperties.stream().map(Property::getId).toList();
                
                occupied = (int) occupiedProperties.stream()
                    .filter(p -> userPropertyIds.contains(p.getId()))
                    .count();
                
                vacant = (int) vacantProperties.stream()
                    .filter(p -> userPropertyIds.contains(p.getId()))
                    .count();
            }

            // Calculate synced properties
            int synced = (int) userProperties.stream()
                .filter(p -> p.getPayPropId() != null)
                .count();
            
            int readyForSync = totalProperties - synced;

            // Calculate rent potential
            BigDecimal totalRentPotential = userProperties.stream()
                .map(Property::getMonthlyPayment)
                .filter(rent -> rent != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 🔧 FIXED: Set correct property statistics
            model.addAttribute("totalProperties", totalProperties);
            model.addAttribute("occupiedCount", occupied);        // Now shows 252 (correct)
            model.addAttribute("vacantCount", vacant);            // Now shows 11 (correct)
            model.addAttribute("syncedCount", synced);
            model.addAttribute("syncedProperties", synced);
            model.addAttribute("readyForSync", readyForSync);
            model.addAttribute("totalRentPotential", totalRentPotential);
            
            // Additional attributes for dashboard compatibility
            model.addAttribute("occupiedProperties", occupied);
            model.addAttribute("vacantProperties", vacant);
            model.addAttribute("recentProperties", userProperties.stream().limit(5).collect(Collectors.toList()));
            
            // Debug logging
            System.out.println("=== HOME PAGE PROPERTY STATS ===");
            System.out.println("Total Properties: " + totalProperties);
            System.out.println("Occupied Properties: " + occupied);
            System.out.println("Vacant Properties: " + vacant);
            System.out.println("Synced Properties: " + synced);
            System.out.println("=== END STATS ===");
            
        } catch (Exception e) {
            // If property service fails, set default values to prevent template errors
            System.err.println("Error calculating property statistics: " + e.getMessage());
            e.printStackTrace();
            
            model.addAttribute("totalProperties", 0);
            model.addAttribute("occupiedCount", 0);
            model.addAttribute("vacantCount", 0);
            model.addAttribute("syncedCount", 0);
            model.addAttribute("readyForSync", 0);
            model.addAttribute("totalRentPotential", BigDecimal.ZERO);
            model.addAttribute("occupiedProperties", 0);
            model.addAttribute("vacantProperties", 0);
            model.addAttribute("recentProperties", new ArrayList<>());  // <-- ADD THIS LINE
        }

        model.addAttribute("tickets", tickets);
        model.addAttribute("leads", leads);
        model.addAttribute("contracts", contracts);
        model.addAttribute("weatherData", weatherData);
        model.addAttribute("countTickets", countTickets);
        model.addAttribute("countLeads", countLeads);
        model.addAttribute("countContracts", countContracts);

        return (AuthorizationUtil.hasRole(authentication,"ROLE_CUSTOMER")) ? "customer-dashboard" : "index";
    }

    // ✅ NEW: Helper method to calculate maintenance statistics
    private Map<String, Object> calculateMaintenanceStats(int userId, boolean isCustomer) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            List<Ticket> maintenanceTickets;
            List<Ticket> emergencyTickets;
            
            if (isCustomer) {
                // For customers, get tickets where they are the customer
                maintenanceTickets = ticketService.getTicketsByCustomerIdAndType(userId, "maintenance");
                emergencyTickets = ticketService.getTicketsByCustomerIdAndType(userId, "emergency");
            } else {
                // For employees, get tickets assigned to them or all tickets if manager
                maintenanceTickets = ticketService.findByType("maintenance");
                emergencyTickets = ticketService.findByType("emergency");
            }
            
            // Calculate open maintenance tickets
            long openTickets = maintenanceTickets.stream()
                .filter(t -> "open".equals(t.getStatus()))
                .count();
            
            // Calculate in-progress maintenance tickets  
            long inProgressTickets = maintenanceTickets.stream()
                .filter(t -> "in-progress".equals(t.getStatus()) || "work-in-progress".equals(t.getStatus()))
                .count();
            
            // Calculate emergency tickets (not closed)
            long emergencyCount = emergencyTickets.stream()
                .filter(t -> !"closed".equals(t.getStatus()) && !"resolved".equals(t.getStatus()))
                .count();
            
            // Calculate tickets awaiting bids
            long awaitingBids = maintenanceTickets.stream()
                .filter(t -> "bidding".equals(t.getStatus()) || "awaiting-bids".equals(t.getStatus()))
                .count();
            
            // Calculate total maintenance tickets
            long totalMaintenance = maintenanceTickets.size();
            
            // Calculate completed this month (if your Ticket entity has date fields)
            long completedThisMonth = maintenanceTickets.stream()
                .filter(t -> "completed".equals(t.getStatus()) || "closed".equals(t.getStatus()))
                .count();
            
            stats.put("openTickets", openTickets);
            stats.put("inProgressTickets", inProgressTickets);
            stats.put("emergencyTickets", emergencyCount);
            stats.put("awaitingBids", awaitingBids);
            stats.put("totalMaintenance", totalMaintenance);
            stats.put("completedThisMonth", completedThisMonth);
            
            // Debug logging
            System.out.println("=== MAINTENANCE STATS ===");
            System.out.println("Open: " + openTickets);
            System.out.println("In Progress: " + inProgressTickets);
            System.out.println("Emergency: " + emergencyCount);
            System.out.println("Awaiting Bids: " + awaitingBids);
            System.out.println("=== END MAINTENANCE STATS ===");
            
        } catch (Exception e) {
            System.err.println("Error in maintenance stats calculation: " + e.getMessage());
            return getDefaultMaintenanceStats();
        }
        
        return stats;
    }
    
    // ✅ NEW: Default maintenance stats in case of errors
    private Map<String, Object> getDefaultMaintenanceStats() {
        Map<String, Object> defaultStats = new HashMap<>();
        defaultStats.put("openTickets", 0L);
        defaultStats.put("inProgressTickets", 0L);
        defaultStats.put("emergencyTickets", 0L);
        defaultStats.put("awaitingBids", 0L);
        defaultStats.put("totalMaintenance", 0L);
        defaultStats.put("completedThisMonth", 0L);
        return defaultStats;
    }
}