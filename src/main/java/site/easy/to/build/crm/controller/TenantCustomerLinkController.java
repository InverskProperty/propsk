package site.easy.to.build.crm.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.service.synchronization.TenantCustomerLinkService;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for manually managing tenant-customer synchronization.
 *
 * Provides endpoints to:
 * - Check linking status
 * - Manually trigger linking
 * - Monitor synchronization health
 */
@Controller
@RequestMapping("/api/tenant-customer-sync")
public class TenantCustomerLinkController {

    private static final Logger log = LoggerFactory.getLogger(TenantCustomerLinkController.class);

    @Autowired
    private TenantCustomerLinkService linkService;

    /**
     * Check current linking status without making changes
     */
    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStatus() {
        log.info("📊 Checking tenant-customer linking status");

        try {
            Map<String, Integer> status = linkService.checkLinkingStatus();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("status", status);
            response.put("message", String.format("%d tenants unlinked (%d can be linked, %d no match)",
                status.get("unlinked"),
                status.get("unlinkedWithMatchAvailable"),
                status.get("unlinkedWithNoMatch")));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Status check failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Manually trigger linking of all unlinked tenants
     */
    @PostMapping("/link-all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> linkAll() {
        log.info("🔗 Manually triggering tenant-customer linking");

        try {
            TenantCustomerLinkService.LinkResult result = linkService.linkAllTenantsToCustomers();

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("summary", result.getSummary());
            response.put("details", Map.of(
                "totalChecked", result.getTotalTenantsChecked(),
                "newlyLinked", result.getNewlyLinked(),
                "alreadyLinked", result.getAlreadyLinked(),
                "noMatch", result.getNoMatchingCustomer(),
                "errors", result.getErrors(),
                "durationMs", result.getDurationMs()
            ));

            if (result.isSuccess()) {
                log.info("✅ Linking completed successfully: {}", result.getSummary());
                return ResponseEntity.ok(response);
            } else {
                log.warn("⚠️ Linking completed with errors: {}", result.getSummary());
                return ResponseEntity.status(207).body(response); // 207 Multi-Status
            }

        } catch (Exception e) {
            log.error("❌ Linking failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get comprehensive info about the linking system
     */
    @GetMapping("/info")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("service", "Tenant-Customer Link Service");
        info.put("purpose", "Maintains bidirectional synchronization between tenants and customers tables");
        info.put("linkingLogic", "Matches tenant.payprop_id with customer.payprop_entity_id");
        info.put("automaticExecution", "Runs during full PayProp sync (Step 7.5)");
        info.put("manualTrigger", "/api/tenant-customer-sync/link-all");
        info.put("statusCheck", "/api/tenant-customer-sync/status");

        return ResponseEntity.ok(info);
    }
}
