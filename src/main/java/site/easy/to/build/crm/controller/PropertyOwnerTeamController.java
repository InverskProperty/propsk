package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerLoginInfo;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.service.customer.CustomerLoginInfoService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/property-owner/team")
public class PropertyOwnerTeamController {

    private final CustomerService customerService;
    private final CustomerLoginInfoService customerLoginInfoService;
    private final AuthenticationUtils authenticationUtils;

    @Autowired
    public PropertyOwnerTeamController(CustomerService customerService,
                                      CustomerLoginInfoService customerLoginInfoService,
                                      AuthenticationUtils authenticationUtils) {
        this.customerService = customerService;
        this.customerLoginInfoService = customerLoginInfoService;
        this.authenticationUtils = authenticationUtils;
    }

    /**
     * Show team management page
     */
    @GetMapping
    public String showTeamManagementPage(Model model, Authentication authentication) {
        System.out.println("🔍 [Team] Loading team management page");

        try {
            // Get current property owner
            String email = authentication.getName();
            Customer propertyOwner = customerService.findByEmail(email);
            if (propertyOwner == null) {
                System.err.println("❌ [Team] Could not find property owner customer");
                model.addAttribute("errorMessage", "Could not load your account information");
                return "property-owner/team";
            }

            System.out.println("🔍 [Team] Property owner: " + propertyOwner.getName() + " (ID: " + propertyOwner.getCustomerId() + ")");

            // Get all delegated users and managers for this property owner
            List<Customer> teamMembers = customerService.findAll().stream()
                .filter(c -> c.getManagesOwner() != null &&
                           c.getManagesOwner().getCustomerId().equals(propertyOwner.getCustomerId()) &&
                           (c.getCustomerType() == CustomerType.DELEGATED_USER ||
                            c.getCustomerType() == CustomerType.MANAGER))
                .collect(Collectors.toList());

            System.out.println("✅ [Team] Found " + teamMembers.size() + " team members");

            model.addAttribute("customer", propertyOwner);
            model.addAttribute("teamMembers", teamMembers);
            model.addAttribute("home", "/");

            return "property-owner/team";

        } catch (Exception e) {
            System.err.println("❌ [Team] Error loading team page: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("errorMessage", "Error loading team: " + e.getMessage());
            return "property-owner/team";
        }
    }

    /**
     * Invite a new team member
     */
    @PostMapping("/invite")
    public String inviteTeamMember(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam(required = false) String phone,
            @RequestParam String customerType,
            @RequestParam String temporaryPassword,
            @RequestParam String confirmPassword,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        System.out.println("🔍 [Team] Inviting team member: " + email);

        try {
            // Get current property owner
            String ownerEmail = authentication.getName();
            Customer propertyOwner = customerService.findByEmail(ownerEmail);
            if (propertyOwner == null) {
                System.err.println("❌ [Team] Could not find property owner");
                redirectAttributes.addFlashAttribute("errorMessage", "Could not verify your account");
                return "redirect:/property-owner/team";
            }

            // Validate passwords match
            if (!temporaryPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Passwords do not match");
                return "redirect:/property-owner/team";
            }

            if (temporaryPassword.length() < 8) {
                redirectAttributes.addFlashAttribute("errorMessage", "Password must be at least 8 characters");
                return "redirect:/property-owner/team";
            }

            // Validate customer type
            if (!"DELEGATED_USER".equals(customerType) && !"MANAGER".equals(customerType)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Invalid role selected");
                return "redirect:/property-owner/team";
            }

            // Check if customer already exists
            Customer existingCustomer = customerService.findByEmail(email);
            if (existingCustomer != null) {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "A customer with this email already exists");
                return "redirect:/property-owner/team";
            }

            // Check if login already exists
            CustomerLoginInfo existingLogin = customerLoginInfoService.findByEmail(email);
            if (existingLogin != null) {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "An account with this email already exists");
                return "redirect:/property-owner/team";
            }

            // Create new customer
            Customer newMember = new Customer();
            newMember.setName(name);
            newMember.setEmail(email);
            newMember.setPhone(phone);
            newMember.setCustomerType(CustomerType.valueOf(customerType));
            newMember.setEntityType(customerType.toLowerCase());
            newMember.setManagesOwner(propertyOwner);
            newMember.setCreatedAt(LocalDateTime.now());

            // Set legacy boolean flags for backward compatibility
            if ("DELEGATED_USER".equals(customerType) || "MANAGER".equals(customerType)) {
                newMember.setIsPropertyOwner(true); // Delegated users have owner-like permissions
                newMember.setIsTenant(false);
                newMember.setIsContractor(false);
            }

            // Save customer first
            Customer savedMember = customerService.save(newMember);
            System.out.println("✅ [Team] Created customer: " + savedMember.getName() + " (ID: " + savedMember.getCustomerId() + ")");

            // Create login credentials
            CustomerLoginInfo loginInfo = new CustomerLoginInfo();
            loginInfo.setUsername(email);
            loginInfo.setPassword(temporaryPassword); // Service will hash it
            loginInfo.setPasswordSet(true);
            loginInfo.setAccountLocked(false);
            loginInfo.setLoginAttempts(0);
            loginInfo.setCreatedAt(LocalDateTime.now());

            CustomerLoginInfo savedLoginInfo = customerLoginInfoService.save(loginInfo);
            System.out.println("✅ [Team] Created login for: " + email);

            // Link customer to login info
            savedMember.setCustomerLoginInfo(savedLoginInfo);
            customerService.save(savedMember);

            System.out.println("✅ [Team] Successfully invited team member: " + name);
            redirectAttributes.addFlashAttribute("successMessage",
                "Team member invited successfully! They can now log in with their email and password.");

            return "redirect:/property-owner/team";

        } catch (Exception e) {
            System.err.println("❌ [Team] Error inviting team member: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage",
                "Error inviting team member: " + e.getMessage());
            return "redirect:/property-owner/team";
        }
    }

    /**
     * Remove a team member
     */
    @PostMapping("/remove/{memberId}")
    @ResponseBody
    public ResponseEntity<?> removeTeamMember(
            @PathVariable Long memberId,
            Authentication authentication) {

        System.out.println("🔍 [Team] Removing team member ID: " + memberId);

        try {
            // Get current property owner
            String ownerEmail = authentication.getName();
            Customer propertyOwner = customerService.findByEmail(ownerEmail);
            if (propertyOwner == null) {
                System.err.println("❌ [Team] Could not find property owner");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Could not verify your account"));
            }

            // Get team member
            Customer teamMember = customerService.findByCustomerId(memberId);
            if (teamMember == null) {
                System.err.println("❌ [Team] Team member not found: " + memberId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Team member not found"));
            }

            // Verify this team member belongs to the property owner
            if (teamMember.getManagesOwner() == null ||
                !teamMember.getManagesOwner().getCustomerId().equals(propertyOwner.getCustomerId())) {
                System.err.println("❌ [Team] Team member does not belong to this property owner");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You do not have permission to remove this team member"));
            }

            // Remove the delegation (clear manages_owner_id)
            teamMember.setManagesOwner(null);
            teamMember.setCustomerType(CustomerType.REGULAR_CUSTOMER);
            teamMember.setEntityType("customer");
            teamMember.setIsPropertyOwner(false);
            customerService.save(teamMember);

            System.out.println("✅ [Team] Successfully removed team member: " + teamMember.getName());

            return ResponseEntity.ok(Map.of(
                "message", "Team member removed successfully",
                "memberId", memberId
            ));

        } catch (Exception e) {
            System.err.println("❌ [Team] Error removing team member: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Error removing team member: " + e.getMessage()));
        }
    }
}
