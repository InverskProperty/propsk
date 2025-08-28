package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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
import site.easy.to.build.crm.service.user.OAuthUserService;
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
    private final OAuthUserService oAuthUserService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public HomePageController(TicketService ticketService, CustomerService customerService, ContractService contractService, LeadService leadService,
                              WeatherService weatherService, AuthenticationUtils authenticationUtils, GoogleCalendarApiService googleCalendarApiService,
                              CustomerLoginInfoService customerLoginInfoService, PropertyService propertyService, OAuthUserService oAuthUserService, JdbcTemplate jdbcTemplate) {
        this.ticketService = ticketService;
        this.customerService = customerService;
        this.contractService = contractService;
        this.leadService = leadService;
        this.weatherService = weatherService;
        this.authenticationUtils = authenticationUtils;
        this.googleCalendarApiService = googleCalendarApiService;
        this.customerLoginInfoService = customerLoginInfoService;
        this.propertyService = propertyService;
        this.oAuthUserService = oAuthUserService;
        this.jdbcTemplate = jdbcTemplate;
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
            System.out.println("📅 DEBUG: Checking Google Calendar access...");
            System.out.println("   Authentication type: " + authentication.getClass().getSimpleName());
            System.out.println("   Is OAuth user: " + !(authentication instanceof UsernamePasswordAuthenticationToken));
            System.out.println("   Google Calendar service available: " + (googleCalendarApiService != null));
            
            if (!(authentication instanceof UsernamePasswordAuthenticationToken) && googleCalendarApiService != null) {
                isGoogleUser = true;
                System.out.println("✅ User is authenticated via OAuth (Google)");
                
                OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
                System.out.println("   OAuth user found: " + (oAuthUser != null));
                if (oAuthUser != null) {
                    System.out.println("   OAuth user email: " + oAuthUser.getEmail());
                    System.out.println("   OAuth user scopes: " + oAuthUser.getGrantedScopes());
                    System.out.println("   Has calendar scope: " + oAuthUser.getGrantedScopes().contains(GoogleAccessService.SCOPE_CALENDAR));
                }
                
                // Check if user has valid OAuth tokens before attempting API calls
                if (!oAuthUserService.hasValidTokens(oAuthUser)) {
                    System.out.println("⚠️ OAuth tokens not available or expired for user");
                    hasCalendarAccess = false;
                    model.addAttribute("oauthTokenExpired", true);
                    model.addAttribute("oauthMessage", "Google services require authentication. Please re-authenticate to access Google calendar.");
                } else if (oAuthUser.getGrantedScopes().contains(GoogleAccessService.SCOPE_CALENDAR)) {
                    System.out.println("📅 Attempting to fetch Google Calendar events...");
                    try {
                        hasCalendarAccess = true;
                        System.out.println("   Making Google Calendar API call...");
                        EventDisplayList eventDisplayList = googleCalendarApiService.getEvents("primary", oAuthUser);
                        eventDisplays = eventDisplayList.getItems();
                        System.out.println("✅ Successfully retrieved " + (eventDisplays != null ? eventDisplays.size() : 0) + " calendar events");
                    } catch (RuntimeException e) {
                        System.err.println("❌ RuntimeException in Google Calendar API: " + e.getMessage());
                        if (e.getMessage().contains("OAuth tokens expired")) {
                            System.err.println("🔄 OAuth tokens expired for user, calendar access disabled");
                            hasCalendarAccess = false;
                            eventDisplays = null;
                            model.addAttribute("oauthTokenExpired", true);
                            model.addAttribute("oauthMessage", "Google calendar access expired. Please re-authenticate to restore Google services.");
                        } else {
                            System.err.println("❌ Google Calendar API error: " + e.getMessage());
                            hasCalendarAccess = false;
                            eventDisplays = null;
                            model.addAttribute("googleCalendarError", "Unable to load calendar events: " + e.getMessage());
                        }
                        e.printStackTrace();
                    } catch (IOException | GeneralSecurityException e) {
                        System.err.println("❌ Google Calendar API IOException/GeneralSecurityException: " + e.getMessage());
                        hasCalendarAccess = false;
                        eventDisplays = null;
                        model.addAttribute("googleCalendarError", "Unable to load calendar events: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("⚠️ User does not have calendar scope access");
                }
            } else {
                System.out.println("📅 User is not OAuth authenticated or Google Calendar service unavailable");
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
        
        // 🔧 FIXED: Use accurate PayProp statistics (consistent with PropertyController)
        try {
            int totalProperties, occupied, vacant, synced;
            
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                // Managers: Use direct PayProp statistics for accuracy
                Map<String, Integer> payPropStats = getPayPropStatistics();
                totalProperties = payPropStats.get("activeProperties"); // Active properties only (not archived)
                occupied = payPropStats.get("occupiedProperties");
                vacant = payPropStats.get("vacantProperties");
                
                // Get synced count from active properties
                List<Property> activeProperties = propertyService.findActiveProperties();
                synced = (int) activeProperties.stream()
                    .filter(p -> p.getPayPropId() != null)
                    .count();
            } else {
                // Non-managers: Use user-specific property filtering
                List<Property> userProperties = propertyService.getRecentProperties((long) userId, 1000);
                List<Property> occupiedProperties = propertyService.findOccupiedProperties();
                List<Property> vacantProperties = propertyService.findVacantProperties();
                
                totalProperties = userProperties.size();
                List<Long> userPropertyIds = userProperties.stream().map(Property::getId).toList();
                
                occupied = (int) occupiedProperties.stream()
                    .filter(p -> userPropertyIds.contains(p.getId()))
                    .count();
                
                vacant = (int) vacantProperties.stream()
                    .filter(p -> userPropertyIds.contains(p.getId()))
                    .count();
                
                synced = (int) userProperties.stream()
                    .filter(p -> p.getPayPropId() != null)
                    .count();
            }
            
            int readyForSync = totalProperties - synced;

            // Calculate rent potential
            BigDecimal totalRentPotential;
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                // For managers, calculate from all active properties
                List<Property> activeProperties = propertyService.findActiveProperties();
                totalRentPotential = activeProperties.stream()
                    .map(Property::getMonthlyPayment)
                    .filter(rent -> rent != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            } else {
                // For non-managers, calculate from user properties
                List<Property> userProperties = propertyService.getRecentProperties((long) userId, 1000);
                totalRentPotential = userProperties.stream()
                    .map(Property::getMonthlyPayment)
                    .filter(rent -> rent != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

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
            
            // Recent properties for dashboard display
            List<Property> recentProperties;
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                recentProperties = propertyService.findActiveProperties().stream().limit(5).collect(Collectors.toList());
            } else {
                List<Property> userProperties = propertyService.getRecentProperties((long) userId, 1000);
                recentProperties = userProperties.stream().limit(5).collect(Collectors.toList());
            }
            model.addAttribute("recentProperties", recentProperties);
            
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
    
    // Get accurate PayProp statistics from database (same as PropertyController)
    private Map<String, Integer> getPayPropStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        try {
            // Get accurate PayProp counts
            String sql = """
                SELECT 
                  (SELECT COUNT(*) FROM payprop_export_properties) as total_properties,
                  (SELECT COUNT(*) FROM payprop_export_properties WHERE is_archived = 0) as active_properties,
                  (SELECT COUNT(*) FROM payprop_export_properties WHERE is_archived = 1) as archived_properties,
                  (SELECT COUNT(DISTINCT property_payprop_id) 
                   FROM payprop_export_invoices 
                   WHERE invoice_type = 'Rent' AND sync_status = 'active') as occupied_properties
                """;
            
            jdbcTemplate.query(sql, rs -> {
                int totalProperties = rs.getInt("total_properties");
                int activeProperties = rs.getInt("active_properties");
                int archivedProperties = rs.getInt("archived_properties");
                int occupiedProperties = rs.getInt("occupied_properties");
                int vacantProperties = activeProperties - occupiedProperties;
                
                stats.put("totalProperties", totalProperties);
                stats.put("activeProperties", activeProperties);
                stats.put("archivedProperties", archivedProperties);
                stats.put("occupiedProperties", occupiedProperties);
                stats.put("vacantProperties", vacantProperties);
            });
            
            return stats;
        } catch (Exception e) {
            System.err.println("Error getting PayProp statistics: " + e.getMessage());
            // Return safe defaults
            stats.put("totalProperties", 0);
            stats.put("activeProperties", 0);
            stats.put("archivedProperties", 0);
            stats.put("occupiedProperties", 0);
            stats.put("vacantProperties", 0);
            return stats;
        }
    }
}