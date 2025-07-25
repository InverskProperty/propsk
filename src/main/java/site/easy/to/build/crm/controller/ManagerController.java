package site.easy.to.build.crm.controller;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.google.service.gmail.GoogleGmailApiService;
import site.easy.to.build.crm.service.role.RoleService;
import site.easy.to.build.crm.service.ticket.TicketService;
import site.easy.to.build.crm.service.user.UserProfileService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.EmailTokenUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class ManagerController {
    private final AuthenticationUtils authenticationUtils;
    private final UserProfileService userProfileService;
    private final UserService userService;
    private final Environment environment;
    private final GoogleGmailApiService googleGmailApiService;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    
    // ✅ NEW: Add TicketService for maintenance statistics
    private final TicketService ticketService;

    @Autowired
    public ManagerController(AuthenticationUtils authenticationUtils, UserProfileService userProfileService, UserService userService,
                             Environment environment, GoogleGmailApiService googleGmailApiService, RoleService roleService, 
                             PasswordEncoder passwordEncoder, TicketService ticketService) {
        this.authenticationUtils = authenticationUtils;
        this.userProfileService = userProfileService;
        this.userService = userService;
        this.environment = environment;
        this.googleGmailApiService = googleGmailApiService;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.ticketService = ticketService;
    }

    // ✅ NEW: Manager Dashboard Method
    @GetMapping("/manager/dashboard")
    public String showManagerDashboard(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }

        // ✅ Add maintenance statistics for manager dashboard
        try {
            Map<String, Object> maintenanceStats = calculateManagerMaintenanceStats();
            model.addAttribute("maintenanceStats", maintenanceStats);
        } catch (Exception e) {
            System.err.println("Error calculating manager maintenance statistics: " + e.getMessage());
            model.addAttribute("maintenanceStats", getDefaultMaintenanceStats());
        }

        // Add other manager-specific data here
        List<User> allUsers = userService.findAll();
        model.addAttribute("totalUsers", allUsers.size());
        model.addAttribute("activeUsers", allUsers.stream().filter(u -> !u.isInactiveUser()).count());

        return "manager/dashboard";
    }

    @GetMapping("/manager/all-users")
    public String showAllUsers(Model model, Authentication authentication) {
        List<User> profiles = userService.findAll();
        int currentUserId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(currentUserId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }
        profiles.removeIf(profile -> profile.getId() == currentUserId);

        // ✅ Add maintenance statistics to all-users view
        try {
            Map<String, Object> maintenanceStats = calculateManagerMaintenanceStats();
            model.addAttribute("maintenanceStats", maintenanceStats);
        } catch (Exception e) {
            System.err.println("Error calculating maintenance statistics for all-users view: " + e.getMessage());
            model.addAttribute("maintenanceStats", getDefaultMaintenanceStats());
        }

        model.addAttribute("profiles",profiles);
        return "manager/all-users";
    }

    @GetMapping("/manager/show-user/{id}")
    public String showUserInfo(@PathVariable("id") int id, Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }
        User user = userService.findById(Long.valueOf(id));
        if(user == null) {
            return "error/not-found";
        }
        UserProfile profile = user.getUserProfile();

        // ✅ Add user-specific maintenance statistics
        try {
            Map<String, Object> userMaintenanceStats = calculateUserMaintenanceStats(id);
            model.addAttribute("userMaintenanceStats", userMaintenanceStats);
        } catch (Exception e) {
            System.err.println("Error calculating user maintenance statistics: " + e.getMessage());
            model.addAttribute("userMaintenanceStats", getDefaultMaintenanceStats());
        }

        model.addAttribute("user", user);
        model.addAttribute("profile", profile);
        return "manager/show-user";
    }

    @GetMapping("/manager/register-user")
    public String showRegistrationForm(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }
        boolean gmailAccess = false;
        boolean isGoogleUser = !(authentication instanceof UsernamePasswordAuthenticationToken);
        if (isGoogleUser) {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            gmailAccess = authenticationUtils.checkIfAppHasAccess("https://www.googleapis.com/auth/gmail.modify", oAuthUser);
        }
        List<Role> roles = roleService.getAllRoles();
        model.addAttribute("gmailAccess",gmailAccess);
        model.addAttribute("isGoogleUser",isGoogleUser);
        model.addAttribute("roles",roles);
        model.addAttribute("user",new User());
        return "manager/register-user";
    }

    @PostMapping("/manager/register-user")
    public String registerUser(@ModelAttribute("user") @Validated(User.ValidationGroupInclusion.class) User user, BindingResult bindingResult,
                               @RequestParam("role") int roleId, Model model, Authentication authentication){
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }
        if(bindingResult.hasErrors()){
            boolean gmailAccess = false;
            boolean isGoogleUser = !(authentication instanceof UsernamePasswordAuthenticationToken);
            if (isGoogleUser) {
                OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
                gmailAccess = authenticationUtils.checkIfAppHasAccess("https://www.googleapis.com/auth/gmail.modify", oAuthUser);
            }
            List<Role> roles = roleService.getAllRoles();
            model.addAttribute("roles",roles);
            model.addAttribute("gmailAccess",gmailAccess);
            model.addAttribute("isGoogleUser",isGoogleUser);
            return "manager/register-user";
        }

        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);

        Optional<Role> role = roleService.findById(roleId);
        if(role.isEmpty()) {
            return "error/400";
        }

        role.ifPresent(value -> user.setRoles(List.of(value)));

        String token = EmailTokenUtils.generateToken();
        user.setToken(token);

        String baseUrl = environment.getProperty("app.base-url") + "set-employee-password?token=" + token;
        String name = user.getEmail().split("@")[0];
        if(googleGmailApiService != null) {
            EmailTokenUtils.sendRegistrationEmail(user.getEmail(), name, baseUrl, oAuthUser, googleGmailApiService);
        }
        user.setUsername(name);
        user.setPasswordSet(false);
        user.setCreatedAt(LocalDateTime.now());
        User createdUser = userService.save(user);
        UserProfile userProfile = new UserProfile();
        userProfile.setStatus(user.getStatus());
        userProfile.setFirstName(name);
        userProfile.setUser(createdUser);
        userProfileService.save(userProfile);
        return "redirect:/manager/all-users";
    }

    @GetMapping("/manager/update-user/{id}")
    public String showUserUpdatingForm(@PathVariable("id") int id, Model model, HttpSession session, Authentication authentication) {
        User user = userService.findById(Long.valueOf(id));
        int managerId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(managerId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }
        String sessionName = managerId + "manager-update-user";
        session.setAttribute(sessionName,id);
        List<Role> roles = roleService.getAllRoles();
        model.addAttribute("roles",roles);
        model.addAttribute("user", user);
        return "manager/update-user";
    }

    @PostMapping("/manager/update-user")
    public String updateUserInfo(@ModelAttribute("user") @Validated(User.ManagerUpdateValidationGroupInclusion.class) User user, BindingResult bindingResult,
                                 @RequestParam("role") int roleId, HttpSession session, Authentication authentication, Model model) {

        int managerId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(managerId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }
        String sessionName = managerId + "manager-update-user";

        if(session.getAttribute(sessionName) == null) {
            return "error/not-found";
        }

        int employeeId = (int) session.getAttribute(sessionName);

        if(bindingResult.hasErrors()) {
            List<Role> roles = roleService.getAllRoles();
            User employee = userService.findById(Long.valueOf(employeeId));
            model.addAttribute("roles",roles);
            model.addAttribute("user", employee);
            return "manager/update-user";
        }

        session.removeAttribute(sessionName);

        User employee = userService.findById(Long.valueOf(employeeId));
        employee.setStatus(user.getStatus());
        employee.setUpdatedAt(LocalDateTime.now());
        Optional<Role> role = roleService.findById(roleId);

        if(role.isEmpty()) {
            return "error/400";
        }

        List<Role> roles = new ArrayList<>();
        roles.add(role.get());
        employee.setRoles(roles);
        userService.save(employee);
        return "redirect:/manager/all-users";
    }

    @GetMapping("/set-employee-password")
    public String showPasswordForm(Model model, @RequestParam("token") @Nullable String token, RedirectAttributes redirectAttributes) {
        if(token == null) {
            redirectAttributes.addFlashAttribute("tokenError", "Incorrect token. Please check with your manager");
            return "redirect:/login";
        }
        User user = userService.findByToken(token);
        if(user == null) {
            redirectAttributes.addFlashAttribute("tokenError", "Incorrect token. Please check with your manager");
            return "redirect:/login";
        }
        if(user.isPasswordSet()) {
            redirectAttributes.addFlashAttribute("passwordSetError", "Password has already been set.");
            return "redirect:/login";
        }

        model.addAttribute("user", user);
        model.addAttribute("username", user.getUsername());
        return "set-employee-password";
    }

    @PostMapping("/set-employee-password")
    public String setPassword(@ModelAttribute("user") @Validated(User.SetEmployeePasswordValidation.class) User user, BindingResult bindingResult, Model model,
                              @RequestParam("token") @Nullable String token, RedirectAttributes redirectAttributes){
        if(token ==null){
            redirectAttributes.addFlashAttribute("tokenError", "Incorrect token. Please check with your manager.");
            return "redirect:/login";
        }
        User currUser = userService.findByToken(token);
        if(currUser == null) {
            redirectAttributes.addFlashAttribute("tokenError", "Incorrect token. Please check with your manager.");
            return "redirect:/login";
        }

        if(bindingResult.hasErrors()) {
            user.setUsername(currUser.getUsername());
            user.setEmail(currUser.getEmail());
            user.setToken(currUser.getToken());
            user.setStatus(currUser.getStatus());
            user.setCreatedAt(currUser.getCreatedAt());
            user.setPasswordSet(false);
            user.setUserProfile(currUser.getUserProfile());
            user.setRoles(currUser.getRoles());
            model.addAttribute("username",currUser.getUsername());
            return "set-employee-password";
        }
        if(!currUser.isPasswordSet()) {
            String hashPassword = passwordEncoder.encode(user.getPassword());
            currUser.setPassword(hashPassword);
            currUser.setPasswordSet(true);
            userService.save(currUser);
        }
        return "redirect:/login";
    }

    // ✅ NEW: Helper method to calculate manager-level maintenance statistics
    private Map<String, Object> calculateManagerMaintenanceStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Managers see all maintenance tickets across the system
            List<Ticket> maintenanceTickets = ticketService.findByType("maintenance");
            List<Ticket> emergencyTickets = ticketService.findByType("emergency");
            
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
            
            // Calculate overdue tickets (if your Ticket entity has due date)
            long overdueTickets = maintenanceTickets.stream()
                .filter(t -> !"closed".equals(t.getStatus()) && !"completed".equals(t.getStatus()))
                .count(); // You can add date logic here if needed
            
            stats.put("openTickets", openTickets);
            stats.put("inProgressTickets", inProgressTickets);
            stats.put("emergencyTickets", emergencyCount);
            stats.put("awaitingBids", awaitingBids);
            stats.put("totalMaintenance", totalMaintenance);
            stats.put("completedTickets", completedTickets);
            stats.put("overdueTickets", overdueTickets);
            
            // Debug logging
            System.out.println("=== MANAGER MAINTENANCE STATS ===");
            System.out.println("Open: " + openTickets);
            System.out.println("In Progress: " + inProgressTickets);
            System.out.println("Emergency: " + emergencyCount);
            System.out.println("Awaiting Bids: " + awaitingBids);
            System.out.println("Total: " + totalMaintenance);
            System.out.println("=== END MANAGER STATS ===");
            
        } catch (Exception e) {
            System.err.println("Error in manager maintenance stats calculation: " + e.getMessage());
            return getDefaultMaintenanceStats();
        }
        
        return stats;
    }
    
    // ✅ NEW: Helper method to calculate user-specific maintenance statistics
    private Map<String, Object> calculateUserMaintenanceStats(int userId) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Get tickets assigned to specific user
            List<Ticket> userMaintenanceTickets = ticketService.getTicketsByEmployeeIdAndType(userId, "maintenance");
            List<Ticket> userEmergencyTickets = ticketService.getTicketsByEmployeeIdAndType(userId, "emergency");
            
            // Calculate statistics for this specific user
            long openTickets = userMaintenanceTickets.stream()
                .filter(t -> "open".equals(t.getStatus()))
                .count();
            
            long inProgressTickets = userMaintenanceTickets.stream()
                .filter(t -> "in-progress".equals(t.getStatus()) || "work-in-progress".equals(t.getStatus()))
                .count();
            
            long emergencyCount = userEmergencyTickets.stream()
                .filter(t -> !"closed".equals(t.getStatus()) && !"resolved".equals(t.getStatus()))
                .count();
            
            long completedTickets = userMaintenanceTickets.stream()
                .filter(t -> "completed".equals(t.getStatus()) || "closed".equals(t.getStatus()))
                .count();
            
            stats.put("openTickets", openTickets);
            stats.put("inProgressTickets", inProgressTickets);
            stats.put("emergencyTickets", emergencyCount);
            stats.put("completedTickets", completedTickets);
            stats.put("totalAssigned", userMaintenanceTickets.size());
            
        } catch (Exception e) {
            System.err.println("Error in user maintenance stats calculation: " + e.getMessage());
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
        defaultStats.put("overdueTickets", 0L);
        defaultStats.put("totalAssigned", 0L);
        return defaultStats;
    }
}