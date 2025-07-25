package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.TenantService;
import site.easy.to.build.crm.service.contractor.ContractorService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.PropertyOwnerService;
import site.easy.to.build.crm.service.ticket.TicketService;
import site.easy.to.build.crm.service.email.EmailService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;
import site.easy.to.build.crm.repository.TenantRepository;
import site.easy.to.build.crm.repository.PropertyOwnerRepository;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Employee Controller - Handles all /employee/** routes for dashboard functionality
 * Accessible to users with MANAGER or EMPLOYEE roles
 * 
 * NOTE: Customer-related routes are handled by CustomerController to avoid conflicts
 */
@Controller
@RequestMapping("/employee")
public class EmployeeController {

    private final CustomerService customerService;
    private final TenantService tenantService;
    private final ContractorService contractorService;
    private final PropertyService propertyService;
    private final PropertyOwnerService propertyOwnerService;
    private final TicketService ticketService;
    private final EmailService emailService;
    private final UserService userService;
    private final AuthenticationUtils authenticationUtils;
    
    // Add repositories for Spring Data's findAllById method
    private final TenantRepository tenantRepository;
    private final PropertyOwnerRepository propertyOwnerRepository;

    @Autowired
    public EmployeeController(CustomerService customerService,
                             TenantService tenantService,
                             ContractorService contractorService,
                             PropertyService propertyService,
                             PropertyOwnerService propertyOwnerService,
                             TicketService ticketService,
                             EmailService emailService,
                             UserService userService,
                             AuthenticationUtils authenticationUtils,
                             TenantRepository tenantRepository,
                             PropertyOwnerRepository propertyOwnerRepository) {
        this.customerService = customerService;
        this.tenantService = tenantService;
        this.contractorService = contractorService;
        this.propertyService = propertyService;
        this.propertyOwnerService = propertyOwnerService;
        this.ticketService = ticketService;
        this.emailService = emailService;
        this.userService = userService;
        this.authenticationUtils = authenticationUtils;
        this.tenantRepository = tenantRepository;
        this.propertyOwnerRepository = propertyOwnerRepository;
    }

    // ===== EMPLOYEE DASHBOARD =====
    
    /**
     * Employee Dashboard - Main landing page
     */
    @GetMapping("/dashboard")
    public String employeeDashboard(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(Long.valueOf(userId));
            
            // Use EXISTING method names for basic stats
            model.addAttribute("totalProperties", propertyService.getTotalProperties());
            model.addAttribute("totalTenants", tenantService.getTotalTenants());
            model.addAttribute("totalOwners", propertyOwnerService.getTotalPropertyOwners());
            model.addAttribute("totalContractors", contractorService.getTotalCount());
            model.addAttribute("activeTickets", ticketService.getActiveTicketCount());
            
            // ✅ NEW: Add maintenance statistics for employee dashboard
            try {
                Map<String, Object> maintenanceStats = calculateEmployeeMaintenanceStats(userId, user);
                model.addAttribute("maintenanceStats", maintenanceStats);
            } catch (Exception e) {
                System.err.println("Error calculating employee maintenance statistics: " + e.getMessage());
                model.addAttribute("maintenanceStats", getDefaultMaintenanceStats());
            }
            
            // Add user info for role-based display
            model.addAttribute("user", user);
            model.addAttribute("isManager", AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER"));
            model.addAttribute("pageTitle", "Employee Dashboard");

        } catch (Exception e) {
            System.err.println("Error loading employee dashboard: " + e.getMessage());
            model.addAttribute("error", "Error loading dashboard: " + e.getMessage());
            model.addAttribute("maintenanceStats", getDefaultMaintenanceStats());
        }

        return "employee/dashboard";
    }

    // ✅ NEW: Employee Work Management Dashboard
    @GetMapping("/work-dashboard")
    public String employeeWorkDashboard(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(Long.valueOf(userId));
            
            // Get employee's assigned tickets
            List<Ticket> assignedTickets = ticketService.getTicketsByEmployeeId(userId);
            List<Ticket> recentTickets = ticketService.getRecentEmployeeTickets(userId, 10);
            
            // ✅ Add detailed maintenance statistics for work dashboard
            Map<String, Object> workStats = calculateEmployeeWorkStats(userId);
            model.addAttribute("workStats", workStats);
            
            // Add ticket data
            model.addAttribute("assignedTickets", assignedTickets);
            model.addAttribute("recentTickets", recentTickets);
            model.addAttribute("totalAssignedTickets", assignedTickets.size());
            
            // Filter tickets by status for quick counts
            long myOpenTickets = assignedTickets.stream()
                .filter(t -> "open".equals(t.getStatus()))
                .count();
            long myInProgressTickets = assignedTickets.stream()
                .filter(t -> "in-progress".equals(t.getStatus()) || "work-in-progress".equals(t.getStatus()))
                .count();
            long myCompletedTickets = assignedTickets.stream()
                .filter(t -> "completed".equals(t.getStatus()) || "closed".equals(t.getStatus()))
                .count();
            
            model.addAttribute("myOpenTickets", myOpenTickets);
            model.addAttribute("myInProgressTickets", myInProgressTickets);
            model.addAttribute("myCompletedTickets", myCompletedTickets);
            
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "My Work Dashboard");

        } catch (Exception e) {
            System.err.println("Error loading employee work dashboard: " + e.getMessage());
            model.addAttribute("error", "Error loading work dashboard: " + e.getMessage());
            model.addAttribute("workStats", getDefaultMaintenanceStats());
        }

        return "employee/work-dashboard";
    }

    // ✅ NEW: Employee Performance Dashboard
    @GetMapping("/performance")
    public String employeePerformance(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(Long.valueOf(userId));
            
            // Calculate performance metrics
            Map<String, Object> performanceStats = calculateEmployeePerformanceStats(userId);
            model.addAttribute("performanceStats", performanceStats);
            
            // Get recent activity
            List<Ticket> recentCompletedTickets = ticketService.getRecentEmployeeTickets(userId, 20)
                .stream()
                .filter(t -> "completed".equals(t.getStatus()) || "closed".equals(t.getStatus()))
                .limit(10)
                .toList();
                
            model.addAttribute("recentCompletedTickets", recentCompletedTickets);
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "My Performance");

        } catch (Exception e) {
            System.err.println("Error loading employee performance: " + e.getMessage());
            model.addAttribute("error", "Error loading performance data: " + e.getMessage());
            model.addAttribute("performanceStats", getDefaultMaintenanceStats());
        }

        return "employee/performance";
    }

    // ===== AJAX/API ENDPOINTS =====

    /**
     * Get tenant data for AJAX requests (Tenant entities)
     */
    @GetMapping("/api/tenants")
    @ResponseBody
    public ResponseEntity<List<Tenant>> getTenantsApi(@RequestParam(required = false) String status,
                                                     Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Tenant> tenants;
        if ("active".equals(status)) {
            tenants = tenantService.findByStatus("active");
        } else {
            tenants = tenantService.findAll();
        }

        return ResponseEntity.ok(tenants);
    }

    /**
     * Get property owners data for AJAX requests (PropertyOwner entities)
     */
    @GetMapping("/api/property-owners")
    @ResponseBody
    public ResponseEntity<List<PropertyOwner>> getPropertyOwnersApi(Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(propertyOwnerService.findAll());
    }

    /**
     * Get contractors data for AJAX requests (Contractor entities)
     */
    @GetMapping("/api/contractors")
    @ResponseBody
    public ResponseEntity<List<Contractor>> getContractorsApi(@RequestParam(required = false) String type,
                                                             Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Contractor> contractors;
        if ("preferred".equals(type)) {
            contractors = contractorService.findPreferredContractors();
        } else if ("emergency".equals(type)) {
            contractors = contractorService.findEmergencyContractors();
        } else {
            contractors = contractorService.findAll();
        }

        return ResponseEntity.ok(contractors);
    }

    // ✅ NEW: Get maintenance statistics via API
    @GetMapping("/api/maintenance-stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMaintenanceStatsApi(Authentication authentication) {
        if (!AuthorizationUtil.hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_EMPLOYEE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(Long.valueOf(userId));
            
            Map<String, Object> stats = calculateEmployeeMaintenanceStats(userId, user);
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            return ResponseEntity.ok(getDefaultMaintenanceStats());
        }
    }

    // ✅ NEW: Helper method to calculate employee maintenance statistics
    private Map<String, Object> calculateEmployeeMaintenanceStats(int userId, User user) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            boolean isManager = user.getRoles().stream()
                .anyMatch(role -> role.getName().contains("MANAGER"));
            
            List<Ticket> maintenanceTickets;
            List<Ticket> emergencyTickets;
            
            if (isManager) {
                // Managers see all tickets
                maintenanceTickets = ticketService.findByType("maintenance");
                emergencyTickets = ticketService.findByType("emergency");
            } else {
                // Employees see only their assigned tickets
                maintenanceTickets = ticketService.getTicketsByEmployeeIdAndType(userId, "maintenance");
                emergencyTickets = ticketService.getTicketsByEmployeeIdAndType(userId, "emergency");
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
            
            // Calculate completed tickets
            long completedTickets = maintenanceTickets.stream()
                .filter(t -> "completed".equals(t.getStatus()) || "closed".equals(t.getStatus()))
                .count();
            
            // Calculate pending assignment (for managers)
            long pendingAssignment = 0;
            if (isManager) {
                pendingAssignment = maintenanceTickets.stream()
                    .filter(t -> t.getEmployeeId() == null || t.getEmployeeId() == 0)
                    .filter(t -> !"closed".equals(t.getStatus()) && !"completed".equals(t.getStatus()))
                    .count();
            }
            
            stats.put("openTickets", openTickets);
            stats.put("inProgressTickets", inProgressTickets);
            stats.put("emergencyTickets", emergencyCount);
            stats.put("awaitingBids", awaitingBids);
            stats.put("totalMaintenance", totalMaintenance);
            stats.put("completedTickets", completedTickets);
            stats.put("pendingAssignment", pendingAssignment);
            stats.put("isManager", isManager);
            stats.put("userId", userId);
            
            // Debug logging
            System.out.println("=== EMPLOYEE MAINTENANCE STATS ===");
            System.out.println("User ID: " + userId + " (Manager: " + isManager + ")");
            System.out.println("Open: " + openTickets);
            System.out.println("In Progress: " + inProgressTickets);
            System.out.println("Emergency: " + emergencyCount);
            System.out.println("Awaiting Bids: " + awaitingBids);
            System.out.println("Total: " + totalMaintenance);
            System.out.println("=== END EMPLOYEE STATS ===");
            
        } catch (Exception e) {
            System.err.println("Error in employee maintenance stats calculation: " + e.getMessage());
            return getDefaultMaintenanceStats();
        }
        
        return stats;
    }
    
    // ✅ NEW: Helper method to calculate employee work statistics
    private Map<String, Object> calculateEmployeeWorkStats(int userId) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            List<Ticket> userTickets = ticketService.getTicketsByEmployeeId(userId);
            
            // Today's work
            long todayTickets = userTickets.stream()
                .filter(t -> t.getCreatedAt() != null)
                // Add date filtering logic here if needed
                .count();
            
            // This week's work
            long weekTickets = userTickets.stream()
                .filter(t -> t.getCreatedAt() != null)
                // Add date filtering logic here if needed
                .count();
            
            // Overdue tickets (you can enhance this with actual due date logic)
            long overdueTickets = userTickets.stream()
                .filter(t -> "open".equals(t.getStatus()) || "in-progress".equals(t.getStatus()))
                .count(); // Simplified - add actual overdue logic
            
            // High priority tickets
            long highPriorityTickets = userTickets.stream()
                .filter(t -> "high".equals(t.getPriority()) || "urgent".equals(t.getPriority()) || "critical".equals(t.getPriority()))
                .filter(t -> !"closed".equals(t.getStatus()) && !"completed".equals(t.getStatus()))
                .count();
            
            stats.put("todayTickets", todayTickets);
            stats.put("weekTickets", weekTickets);
            stats.put("overdueTickets", overdueTickets);
            stats.put("highPriorityTickets", highPriorityTickets);
            stats.put("totalAssigned", userTickets.size());
            
        } catch (Exception e) {
            System.err.println("Error in employee work stats calculation: " + e.getMessage());
            return getDefaultMaintenanceStats();
        }
        
        return stats;
    }
    
    // ✅ NEW: Helper method to calculate employee performance statistics
    private Map<String, Object> calculateEmployeePerformanceStats(int userId) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            List<Ticket> userTickets = ticketService.getTicketsByEmployeeId(userId);
            
            // Completion rate
            long totalTickets = userTickets.size();
            long completedTickets = userTickets.stream()
                .filter(t -> "completed".equals(t.getStatus()) || "closed".equals(t.getStatus()))
                .count();
            
            double completionRate = totalTickets > 0 ? (double) completedTickets / totalTickets * 100 : 0;
            
            // Average resolution time (simplified - you can enhance with actual date calculations)
            double avgResolutionDays = 3.2; // Placeholder - calculate from actual data
            
            // Customer satisfaction (placeholder - integrate with actual rating system)
            double customerSatisfaction = 4.2; // Placeholder
            
            // Response time (placeholder)
            double avgResponseHours = 2.1; // Placeholder
            
            stats.put("totalTickets", totalTickets);
            stats.put("completedTickets", completedTickets);
            stats.put("completionRate", Math.round(completionRate * 100.0) / 100.0);
            stats.put("avgResolutionDays", avgResolutionDays);
            stats.put("customerSatisfaction", customerSatisfaction);
            stats.put("avgResponseHours", avgResponseHours);
            
        } catch (Exception e) {
            System.err.println("Error in employee performance stats calculation: " + e.getMessage());
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
        defaultStats.put("completedTickets", 0L);
        defaultStats.put("pendingAssignment", 0L);
        defaultStats.put("isManager", false);
        defaultStats.put("userId", 0);
        defaultStats.put("todayTickets", 0L);
        defaultStats.put("weekTickets", 0L);
        defaultStats.put("overdueTickets", 0L);
        defaultStats.put("highPriorityTickets", 0L);
        defaultStats.put("totalAssigned", 0L);
        defaultStats.put("completionRate", 0.0);
        defaultStats.put("avgResolutionDays", 0.0);
        defaultStats.put("customerSatisfaction", 0.0);
        defaultStats.put("avgResponseHours", 0.0);
        return defaultStats;
    }

    // ===== ERROR HANDLING =====

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, Model model) {
        model.addAttribute("errorMessage", "An error occurred: " + e.getMessage());
        return "error/500";
    }
}