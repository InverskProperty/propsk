package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.Ticket;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.service.ticket.TicketService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employee/property")
public class PropertyController {

    private final PropertyService propertyService;
    private final UserService userService;
    private final TicketService ticketService;
    private final AuthenticationUtils authenticationUtils;

    @Autowired
    public PropertyController(PropertyService propertyService,
                            UserService userService, 
                            TicketService ticketService,
                            AuthenticationUtils authenticationUtils) {
        this.propertyService = propertyService;
        this.userService = userService;
        this.ticketService = ticketService;
        this.authenticationUtils = authenticationUtils;
    }

    @GetMapping("/all-properties")
    public String getAllProperties(Model model, Authentication authentication) {
        try {
            System.out.println("=== DEBUG: Starting getAllProperties ===");
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            System.out.println("DEBUG: User ID: " + userId);
            
            if (userId == -1) {
                return "error/not-found";
            }
            
            User user = userService.findById(userId);
            if (user != null && user.isInactiveUser()) {
                return "error/account-inactive";
            }
            
            List<Property> properties;
            try {
                // Role-based property filtering
                if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                    properties = propertyService.findAll();
                } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER")) {
                    properties = propertyService.findByPropertyOwnerId(userId);
                } else {
                    properties = propertyService.getRecentProperties((long) userId, 100);
                }
                
                System.out.println("DEBUG: Found " + (properties != null ? properties.size() : "null") + " properties");
                
                // OPTIMIZED: Batch load maintenance data instead of individual queries
                if (properties != null && !properties.isEmpty()) {
                    loadMaintenanceDataBatch(properties);
                }
                
            } catch (Exception e) {
                System.err.println("DEBUG: Error loading properties: " + e.getMessage());
                e.printStackTrace();
                properties = new ArrayList<>();
            }
            
            // Add comprehensive portfolio statistics
            addComprehensivePortfolioStatistics(model, properties);
            
            // Add global maintenance statistics with batch queries
            try {
                addGlobalMaintenanceStatistics(model);
            } catch (Exception e) {
                System.err.println("Error loading global maintenance statistics: " + e.getMessage());
                model.addAttribute("emergencyMaintenanceCount", 0);
                model.addAttribute("urgentMaintenanceCount", 0);
                model.addAttribute("routineMaintenanceCount", 0);
                model.addAttribute("totalMaintenanceCount", 0);
            }
            
            model.addAttribute("properties", properties != null ? properties : new ArrayList<>());
            model.addAttribute("pageTitle", "All Properties");
            
            System.out.println("DEBUG: Model attributes set, returning template");
            return "property/all-properties";
            
        } catch (Exception e) {
            System.err.println("DEBUG: Critical exception in getAllProperties: " + e.getMessage());
            e.printStackTrace();
            
            setDefaultModelAttributes(model);
            model.addAttribute("properties", new ArrayList<>());
            model.addAttribute("pageTitle", "All Properties");
            
            return "property/all-properties";
        }
    }

    /**
     * OPTIMIZED: Load maintenance data for all properties in batch instead of individual queries
     */
    private void loadMaintenanceDataBatch(List<Property> properties) {
        try {
            System.out.println("DEBUG: Loading maintenance data for " + properties.size() + " properties");
            
            // Get all property IDs
            List<Long> propertyIds = properties.stream()
                .map(Property::getId)
                .collect(Collectors.toList());
            
            // Batch load all tickets by type (much more efficient than individual queries)
            Map<String, List<Ticket>> ticketsByType = new HashMap<>();
            
            try {
                ticketsByType.put("emergency", ticketService.findByType("emergency"));
                ticketsByType.put("urgent", ticketService.findByType("urgent"));
                ticketsByType.put("routine", ticketService.findByType("routine"));
                ticketsByType.put("maintenance", ticketService.findByType("maintenance"));
            } catch (Exception e) {
                System.err.println("Error loading tickets by type: " + e.getMessage());
                // Initialize empty lists to prevent null pointer exceptions
                ticketsByType.put("emergency", new ArrayList<>());
                ticketsByType.put("urgent", new ArrayList<>());
                ticketsByType.put("routine", new ArrayList<>());
                ticketsByType.put("maintenance", new ArrayList<>());
            }
            
            // Create lookup maps for efficient property-ticket matching
            Map<Long, Integer> emergencyCounts = createPropertyTicketCountMap(ticketsByType.get("emergency"));
            Map<Long, Integer> urgentCounts = createPropertyTicketCountMap(ticketsByType.get("urgent"));
            Map<Long, Integer> routineCounts = createPropertyTicketCountMap(ticketsByType.get("routine"));
            Map<Long, Integer> maintenanceCounts = createPropertyTicketCountMap(ticketsByType.get("maintenance"));
            
            // Assign counts to properties
            for (Property property : properties) {
                Long propertyId = property.getId();
                
                property.setEmergencyMaintenanceCount(emergencyCounts.getOrDefault(propertyId, 0));
                property.setUrgentMaintenanceCount(urgentCounts.getOrDefault(propertyId, 0));
                property.setRoutineMaintenanceCount(
                    routineCounts.getOrDefault(propertyId, 0) + maintenanceCounts.getOrDefault(propertyId, 0)
                );
            }
            
            System.out.println("DEBUG: Maintenance data loaded successfully");
            
        } catch (Exception e) {
            System.err.println("Error in batch maintenance loading: " + e.getMessage());
            e.printStackTrace();
            
            // Set safe defaults for all properties
            for (Property property : properties) {
                property.setEmergencyMaintenanceCount(0);
                property.setUrgentMaintenanceCount(0);
                property.setRoutineMaintenanceCount(0);
            }
        }
    }

    /**
     * Create a map of property ID to ticket count from a list of tickets
     */
    private Map<Long, Integer> createPropertyTicketCountMap(List<Ticket> tickets) {
        Map<Long, Integer> countMap = new HashMap<>();
        
        if (tickets != null) {
            for (Ticket ticket : tickets) {
                try {
                    // Try to get property ID from ticket - this depends on your Ticket entity structure
                    Long propertyId = getPropertyIdFromTicket(ticket);
                    if (propertyId != null) {
                        countMap.put(propertyId, countMap.getOrDefault(propertyId, 0) + 1);
                    }
                } catch (Exception e) {
                    // Skip tickets that can't be mapped to properties
                    System.err.println("Error mapping ticket to property: " + e.getMessage());
                }
            }
        }
        
        return countMap;
    }

    /**
     * Extract property ID from ticket - adapt this to your Ticket entity structure
     */
    private Long getPropertyIdFromTicket(Ticket ticket) {
        try {
            // Method 1: If ticket has direct property ID field
            // return ticket.getPropertyId();
            
            // Method 2: If ticket uses PayProp property ID, convert to internal ID
            String payPropPropertyId = ticket.getPayPropPropertyId();
            if (payPropPropertyId != null && !payPropPropertyId.trim().isEmpty()) {
                // Look up property by PayProp ID
                Property property = propertyService.findByPayPropId(payPropPropertyId).orElse(null);
                return property != null ? property.getId() : null;
            }
            
            // Method 3: If ticket has customer relationship, find property through customer
            // This is a fallback - you may need to adapt based on your data model
            
            return null;
        } catch (Exception e) {
            System.err.println("Error extracting property ID from ticket: " + e.getMessage());
            return null;
        }
    }

    /**
     * Add global maintenance statistics with efficient queries
     */
    private void addGlobalMaintenanceStatistics(Model model) {
        try {
            List<Ticket> emergencyTickets = ticketService.findByType("emergency");
            List<Ticket> urgentTickets = ticketService.findByType("urgent");
            List<Ticket> routineTickets = ticketService.findByType("routine");
            List<Ticket> maintenanceTickets = ticketService.findByType("maintenance");
            
            model.addAttribute("emergencyMaintenanceCount", emergencyTickets.size());
            model.addAttribute("urgentMaintenanceCount", urgentTickets.size());
            model.addAttribute("routineMaintenanceCount", routineTickets.size() + maintenanceTickets.size());
            model.addAttribute("totalMaintenanceCount", 
                emergencyTickets.size() + urgentTickets.size() + routineTickets.size() + maintenanceTickets.size());
                
        } catch (Exception e) {
            System.err.println("Error loading global maintenance statistics: " + e.getMessage());
            model.addAttribute("emergencyMaintenanceCount", 0);
            model.addAttribute("urgentMaintenanceCount", 0);
            model.addAttribute("routineMaintenanceCount", 0);
            model.addAttribute("totalMaintenanceCount", 0);
        }
    }

    // AJAX endpoints for maintenance statistics
    @GetMapping("/maintenance-summary")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMaintenanceSummary(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            if (userId == -1) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Map<String, Object> response = new HashMap<>();
            
            try {
                List<Ticket> emergencyTickets = ticketService.findByType("emergency");
                List<Ticket> urgentTickets = ticketService.findByType("urgent");
                List<Ticket> routineTickets = ticketService.findByType("routine");
                List<Ticket> maintenanceTickets = ticketService.findByType("maintenance");
                
                int totalActiveIssues = emergencyTickets.size() + urgentTickets.size() + 
                                      routineTickets.size() + maintenanceTickets.size();
                
                response.put("totalActiveIssues", totalActiveIssues);
                response.put("emergencyCount", emergencyTickets.size());
                response.put("urgentCount", urgentTickets.size());
                response.put("routineCount", routineTickets.size() + maintenanceTickets.size());
                
            } catch (Exception e) {
                System.err.println("Error loading maintenance summary: " + e.getMessage());
                response.put("totalActiveIssues", 0);
                response.put("emergencyCount", 0);
                response.put("urgentCount", 0);
                response.put("routineCount", 0);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/api/maintenance-stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMaintenanceStats(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            if (userId == -1) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Map<String, Object> response = new HashMap<>();
            
            try {
                List<Ticket> emergencyTickets = ticketService.findByType("emergency");
                List<Ticket> urgentTickets = ticketService.findByType("urgent");
                List<Ticket> routineTickets = ticketService.findByType("routine");
                
                response.put("emergencyTickets", emergencyTickets.size());
                response.put("urgentTickets", urgentTickets.size());
                response.put("routineTickets", routineTickets.size());
                
            } catch (Exception e) {
                response.put("emergencyTickets", 0);
                response.put("urgentTickets", 0);
                response.put("routineTickets", 0);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Portfolio statistics calculation
    private void addComprehensivePortfolioStatistics(Model model, List<Property> properties) {
        try {
            int totalProperties = properties != null ? properties.size() : 0;
            
            // Use efficient single-pass calculation
            int occupied = 0;
            int vacant = 0;
            int synced = 0;
            BigDecimal totalRentPotential = BigDecimal.ZERO;
            
            if (properties != null) {
                for (Property property : properties) {
                    // Calculate occupancy
                    if (property.isOccupied() != null && property.isOccupied()) {
                        occupied++;
                    } else {
                        vacant++;
                    }
                    
                    // Calculate synced properties
                    if (property.getPayPropId() != null) {
                        synced++;
                    }
                    
                    // Calculate rent potential
                    if (property.getMonthlyPayment() != null) {
                        totalRentPotential = totalRentPotential.add(property.getMonthlyPayment());
                    }
                }
            }
            
            int readyForSync = totalProperties - synced;

            // Add ALL required model attributes for templates
            model.addAttribute("totalProperties", totalProperties);
            model.addAttribute("occupiedCount", occupied);
            model.addAttribute("vacantCount", vacant);
            model.addAttribute("syncedCount", synced);
            model.addAttribute("syncedProperties", synced);
            model.addAttribute("readyForSync", readyForSync);
            model.addAttribute("totalRentPotential", totalRentPotential);
            model.addAttribute("occupiedProperties", occupied);
            model.addAttribute("vacantProperties", vacant);
            
        } catch (Exception e) {
            System.err.println("Error calculating portfolio statistics: " + e.getMessage());
            setDefaultModelAttributes(model);
        }
    }

    // Helper method to set default model attributes when errors occur
    private void setDefaultModelAttributes(Model model) {
        model.addAttribute("totalProperties", 0);
        model.addAttribute("occupiedCount", 0);
        model.addAttribute("vacantCount", 0);
        model.addAttribute("syncedCount", 0);
        model.addAttribute("syncedProperties", 0);
        model.addAttribute("readyForSync", 0);
        model.addAttribute("totalRentPotential", BigDecimal.ZERO);
        model.addAttribute("occupiedProperties", 0);
        model.addAttribute("vacantProperties", 0);
        model.addAttribute("emergencyMaintenanceCount", 0);
        model.addAttribute("urgentMaintenanceCount", 0);
        model.addAttribute("routineMaintenanceCount", 0);
        model.addAttribute("totalMaintenanceCount", 0);
    }
}