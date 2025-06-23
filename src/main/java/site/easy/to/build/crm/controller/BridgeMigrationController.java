package site.easy.to.build.crm.controller;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.service.customer.CustomerEntityBridgeService;
import site.easy.to.build.crm.service.customer.BridgeMigrationResult;
import site.easy.to.build.crm.util.AuthorizationUtil;

import java.util.Map;

/**
 * Controller for managing Customer-Entity bridge migrations
 * Only accessible to MANAGER role
 */
@Controller
@RequestMapping("/employee/bridge")
public class BridgeMigrationController {

    private final CustomerEntityBridgeService bridgeService;

    @Autowired
    public BridgeMigrationController(CustomerEntityBridgeService bridgeService) {
        this.bridgeService = bridgeService;
    }

    /**
     * Show migration status dashboard
     */
    @GetMapping("/status")
    public String showMigrationStatus(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/access-denied";
        }

        // Get counts of orphaned entities
        int orphanedOwners = bridgeService.findOrphanedPropertyOwners().size();
        int orphanedTenants = bridgeService.findOrphanedTenants().size();
        int orphanedContractors = bridgeService.findOrphanedContractors().size();
        int customersWithoutLogin = bridgeService.findCustomersWithoutLogin().size();

        model.addAttribute("orphanedOwners", orphanedOwners);
        model.addAttribute("orphanedTenants", orphanedTenants);
        model.addAttribute("orphanedContractors", orphanedContractors);
        model.addAttribute("customersWithoutLogin", customersWithoutLogin);
        model.addAttribute("totalOrphaned", orphanedOwners + orphanedTenants + orphanedContractors);

        return "bridge/migration-status";
    }

    /**
     * Execute migration of orphaned entities
     */
    @PostMapping("/migrate-orphaned")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> migrateOrphanedEntities(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "message", "Access denied - Manager role required"
            ));
        }

        try {
            BridgeMigrationResult result = bridgeService.migrateOrphanedEntities();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Migration completed successfully",
                "successCount", result.getSuccessCount(),
                "errorCount", result.getErrorCount(),
                "details", result.getSummary()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Migration failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Create login credentials for migrated customers
     */
    @PostMapping("/create-logins")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createLoginCredentials(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "message", "Access denied - Manager role required"
            ));
        }

        try {
            int beforeCount = bridgeService.findCustomersWithoutLogin().size();
            bridgeService.createLoginCredentialsForMigratedCustomers();
            int afterCount = bridgeService.findCustomersWithoutLogin().size();
            int created = beforeCount - afterCount;
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Login credentials created successfully",
                "loginCredentialsCreated", created
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Failed to create login credentials: " + e.getMessage()
            ));
        }
    }

    // Add this to your BridgeMigrationController.java

    @GetMapping("/migrate")
    public String showMigrationPage(Model model) {
        model.addAttribute("pageTitle", "Entity Migration");
        return "admin/migration";
    }

    @PostMapping("/migrate")
    public String runMigration(RedirectAttributes redirectAttributes) {
        try {
            BridgeMigrationResult result = bridgeService.migrateOrphanedEntities();
            
            redirectAttributes.addFlashAttribute("success", 
                "Migration completed: " + result.getSuccessCount() + " successes, " + 
                result.getErrorCount() + " errors");
            redirectAttributes.addFlashAttribute("migrationLog", result.getSummary());
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Migration failed: " + e.getMessage());
        }
        
        return "redirect:/admin/migration";
    }

    /**
     * Get orphaned entities for preview
     */
    @GetMapping("/orphaned-preview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getOrphanedEntitiesPreview(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        return ResponseEntity.ok(Map.of(
            "propertyOwners", bridgeService.findOrphanedPropertyOwners().stream()
                .limit(5)
                .map(owner -> Map.of(
                    "id", owner.getId(),
                    "name", owner.getFirstName() + " " + owner.getLastName(),
                    "email", owner.getEmailAddress(),
                    "businessName", owner.getBusinessName() != null ? owner.getBusinessName() : ""
                )).toList(),
            "tenants", bridgeService.findOrphanedTenants().stream()
                .limit(5)
                .map(tenant -> Map.of(
                    "id", tenant.getId(),
                    "name", tenant.getFirstName() + " " + tenant.getLastName(),
                    "email", tenant.getEmailAddress(),
                    "businessName", tenant.getBusinessName() != null ? tenant.getBusinessName() : ""
                )).toList(),
            "contractors", bridgeService.findOrphanedContractors().stream()
                .limit(5)
                .map(contractor -> Map.of(
                    "id", contractor.getId(),
                    "name", contractor.getContactPerson(),
                    "email", contractor.getEmailAddress(),
                    "companyName", contractor.getCompanyName()
                )).toList()
        ));
    }
}