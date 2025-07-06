// PayPropAdminController.java - Admin tools for PayProp testing and management with Duplicate Key Handling
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service;
import site.easy.to.build.crm.service.payprop.PayPropSyncService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.TenantService;
import site.easy.to.build.crm.service.property.PropertyOwnerService;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.Tenant;
import site.easy.to.build.crm.entity.PropertyOwner;
import site.easy.to.build.crm.util.AuthorizationUtil;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Controller
@RequestMapping("/admin/payprop")
public class PayPropAdminController {

    private static final Logger log = LoggerFactory.getLogger(PayPropAdminController.class);

    private final PayPropOAuth2Service oAuth2Service;
    private final PayPropSyncService syncService;
    private final PropertyService propertyService;
    private final TenantService tenantService;
    private final PropertyOwnerService propertyOwnerService;

    @Autowired
    public PayPropAdminController(PayPropOAuth2Service oAuth2Service,
                                 PayPropSyncService syncService,
                                 PropertyService propertyService,
                                 TenantService tenantService,
                                 PropertyOwnerService propertyOwnerService) {
        this.oAuth2Service = oAuth2Service;
        this.syncService = syncService;
        this.propertyService = propertyService;
        this.tenantService = tenantService;
        this.propertyOwnerService = propertyOwnerService;
    }

    /**
     * Admin dashboard for PayProp integration
     */
    @GetMapping("/dashboard")
    public String adminDashboard(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/access-denied";
        }

        // OAuth2 status
        model.addAttribute("hasTokens", oAuth2Service.hasValidTokens());
        model.addAttribute("tokens", oAuth2Service.getCurrentTokens());

        // Sync statistics with error handling
        try {
            List<Property> propertiesNeedingSync = propertyService.findPropertiesNeedingSync();
            List<Property> propertiesSynced = propertyService.findPropertiesByPayPropSyncStatus(true);
            List<Property> propertiesReadyForSync = propertyService.findPropertiesReadyForSync();

            List<Tenant> tenantsNeedingSync = tenantService.findByPayPropIdIsNull();
            List<Tenant> tenantsSynced = tenantService.findByPayPropIdIsNotNull();
            List<Tenant> tenantsReadyForSync = tenantService.findTenantsReadyForPayPropSync();

            List<PropertyOwner> ownersNeedingSync = propertyOwnerService.findByPayPropIdIsNull();
            List<PropertyOwner> ownersSynced = propertyOwnerService.findByPayPropIdIsNotNull();
            List<PropertyOwner> ownersReadyForSync = propertyOwnerService.findPropertyOwnersReadyForSync();

            model.addAttribute("propertiesNeedingSync", propertiesNeedingSync.size());
            model.addAttribute("propertiesSynced", propertiesSynced.size());
            model.addAttribute("propertiesReadyForSync", propertiesReadyForSync.size());

            model.addAttribute("tenantsNeedingSync", tenantsNeedingSync.size());
            model.addAttribute("tenantsSynced", tenantsSynced.size());
            model.addAttribute("tenantsReadyForSync", tenantsReadyForSync.size());

            model.addAttribute("ownersNeedingSync", ownersNeedingSync.size());
            model.addAttribute("ownersSynced", ownersSynced.size());
            model.addAttribute("ownersReadyForSync", ownersReadyForSync.size());
        } catch (Exception e) {
            log.error("Error loading dashboard statistics: {}", e.getMessage());
            model.addAttribute("dashboardError", "Error loading sync statistics: " + e.getMessage());
            
            // Set default values to prevent template errors
            model.addAttribute("propertiesNeedingSync", 0);
            model.addAttribute("propertiesSynced", 0);
            model.addAttribute("propertiesReadyForSync", 0);
            model.addAttribute("tenantsNeedingSync", 0);
            model.addAttribute("tenantsSynced", 0);
            model.addAttribute("tenantsReadyForSync", 0);
            model.addAttribute("ownersNeedingSync", 0);
            model.addAttribute("ownersSynced", 0);
            model.addAttribute("ownersReadyForSync", 0);
        }

        model.addAttribute("pageTitle", "PayProp Admin Dashboard");

        return "admin/payprop-dashboard";
    }

    /**
     * Sync single property to PayProp
     */
    @PostMapping("/sync/property/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncProperty(@PathVariable Long id, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            if (!oAuth2Service.hasValidTokens()) {
                response.put("success", false);
                response.put("message", "PayProp not authorized. Please authorize first.");
                return ResponseEntity.ok(response);
            }

            String payPropId = syncService.syncPropertyToPayProp(id);

            response.put("success", true);
            response.put("message", "Property synced successfully");
            response.put("payPropId", payPropId);

            return ResponseEntity.ok(response);

        } catch (DataIntegrityViolationException e) {
            log.warn("Property {} already exists with PayProp ID during admin sync", id);
            response.put("success", true);
            response.put("message", "Property was already synced to PayProp (duplicate detected)");
            response.put("warning", "Duplicate PayProp ID detected - entity may already be synced");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error syncing property {} to PayProp: {}", id, e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Sync single tenant to PayProp
     */
    @PostMapping("/sync/tenant/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncTenant(@PathVariable Long id, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            if (!oAuth2Service.hasValidTokens()) {
                response.put("success", false);
                response.put("message", "PayProp not authorized. Please authorize first.");
                return ResponseEntity.ok(response);
            }

            String payPropId = syncService.syncTenantToPayProp(id);

            response.put("success", true);
            response.put("message", "Tenant synced successfully");
            response.put("payPropId", payPropId);

            return ResponseEntity.ok(response);

        } catch (DataIntegrityViolationException e) {
            log.warn("Tenant {} already exists with PayProp ID during admin sync", id);
            response.put("success", true);
            response.put("message", "Tenant was already synced to PayProp (duplicate detected)");
            response.put("warning", "Duplicate PayProp ID detected - entity may already be synced");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error syncing tenant {} to PayProp: {}", id, e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Sync single beneficiary to PayProp
     */
    @PostMapping("/sync/beneficiary/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncBeneficiary(@PathVariable Long id, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            if (!oAuth2Service.hasValidTokens()) {
                response.put("success", false);
                response.put("message", "PayProp not authorized. Please authorize first.");
                return ResponseEntity.ok(response);
            }

            String payPropId = syncService.syncBeneficiaryToPayProp(id);

            response.put("success", true);
            response.put("message", "Beneficiary synced successfully");
            response.put("payPropId", payPropId);

            return ResponseEntity.ok(response);

        } catch (DataIntegrityViolationException e) {
            log.warn("Beneficiary {} already exists with PayProp ID during admin sync", id);
            response.put("success", true);
            response.put("message", "Beneficiary was already synced to PayProp (duplicate detected)");
            response.put("warning", "Duplicate PayProp ID detected - entity may already be synced");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error syncing beneficiary {} to PayProp: {}", id, e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Bulk sync all ready properties
     */
    @PostMapping("/sync/properties/bulk")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncAllProperties(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            if (!oAuth2Service.hasValidTokens()) {
                response.put("success", false);
                response.put("message", "PayProp not authorized. Please authorize first.");
                return ResponseEntity.ok(response);
            }

            syncService.syncAllReadyProperties();

            response.put("success", true);
            response.put("message", "Bulk property sync initiated successfully");

            return ResponseEntity.ok(response);

        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate key detected during bulk property sync: {}", e.getMessage());
            response.put("success", true);
            response.put("message", "Bulk property sync completed with some duplicates handled gracefully");
            response.put("warning", "Some properties were already synced (duplicates detected)");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during bulk property sync: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Bulk sync all ready tenants
     */
    @PostMapping("/sync/tenants/bulk")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncAllTenants(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            if (!oAuth2Service.hasValidTokens()) {
                response.put("success", false);
                response.put("message", "PayProp not authorized. Please authorize first.");
                return ResponseEntity.ok(response);
            }

            syncService.syncAllReadyTenants();

            response.put("success", true);
            response.put("message", "Bulk tenant sync initiated successfully");

            return ResponseEntity.ok(response);

        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate key detected during bulk tenant sync: {}", e.getMessage());
            response.put("success", true);
            response.put("message", "Bulk tenant sync completed with some duplicates handled gracefully");
            response.put("warning", "Some tenants were already synced (duplicates detected)");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during bulk tenant sync: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Bulk sync all ready beneficiaries
     */
    @PostMapping("/sync/beneficiaries/bulk")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncAllBeneficiaries(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            if (!oAuth2Service.hasValidTokens()) {
                response.put("success", false);
                response.put("message", "PayProp not authorized. Please authorize first.");
                return ResponseEntity.ok(response);
            }

            syncService.syncAllReadyBeneficiaries();

            response.put("success", true);
            response.put("message", "Bulk beneficiary sync initiated successfully");

            return ResponseEntity.ok(response);

        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate key detected during bulk beneficiary sync: {}", e.getMessage());
            response.put("success", true);
            response.put("message", "Bulk beneficiary sync completed with some duplicates handled gracefully");
            response.put("warning", "Some beneficiaries were already synced (duplicates detected)");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during bulk beneficiary sync: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Export data from PayProp for testing
     */
    @PostMapping("/export/properties")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> exportProperties(@RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "10") int rows,
                                                               Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            if (!oAuth2Service.hasValidTokens()) {
                response.put("success", false);
                response.put("message", "PayProp not authorized. Please authorize first.");
                return ResponseEntity.ok(response);
            }

            PayPropSyncService.PayPropExportResult result = syncService.exportPropertiesFromPayProp(page, rows);

            response.put("success", true);
            response.put("message", "Properties exported successfully");
            response.put("data", result.getItems());
            response.put("pagination", result.getPagination());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error exporting properties from PayProp: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Export tenants from PayProp for testing
     */
    @PostMapping("/export/tenants")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> exportTenants(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "10") int rows,
                                                            Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            if (!oAuth2Service.hasValidTokens()) {
                response.put("success", false);
                response.put("message", "PayProp not authorized. Please authorize first.");
                return ResponseEntity.ok(response);
            }

            PayPropSyncService.PayPropExportResult result = syncService.exportTenantsFromPayProp(page, rows);

            response.put("success", true);
            response.put("message", "Tenants exported successfully");
            response.put("data", result.getItems());
            response.put("pagination", result.getPagination());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error exporting tenants from PayProp: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/test-basic-api")
    @ResponseBody
    public ResponseEntity<String> testBasicApi() {
        try {
            if (!oAuth2Service.hasValidTokens()) {
                return ResponseEntity.ok("❌ No OAuth tokens - authorize first");
            }
            
            StringBuilder results = new StringBuilder();
            results.append("🔑 OAuth tokens: ✅ Valid\n\n");
            
            // Test 1: Try to get properties
            try {
                PayPropSyncService.PayPropExportResult properties = syncService.exportPropertiesFromPayProp(1, 5);
                results.append("📊 GET Properties: ✅ SUCCESS - Found ").append(properties.getItems().size()).append(" properties\n");
            } catch (Exception e) {
                results.append("📊 GET Properties: ❌ FAILED - ").append(e.getMessage()).append("\n");
            }
            
            // Test 2: Try to get tenants  
            try {
                PayPropSyncService.PayPropExportResult tenants = syncService.exportTenantsFromPayProp(1, 5);
                results.append("👥 GET Tenants: ✅ SUCCESS - Found ").append(tenants.getItems().size()).append(" tenants\n");
            } catch (Exception e) {
                results.append("👥 GET Tenants: ❌ FAILED - ").append(e.getMessage()).append("\n");
            }
            
            // Test 3: Try basic sync status
            try {
                syncService.checkSyncStatus();
                results.append("🔍 Check Status: ✅ SUCCESS\n");
            } catch (Exception e) {
                results.append("🔍 Check Status: ❌ FAILED - ").append(e.getMessage()).append("\n");
            }
            
            return ResponseEntity.ok(results.toString());
            
        } catch (Exception e) {
            log.error("Error during basic API test: {}", e.getMessage());
            return ResponseEntity.ok("💥 GENERAL ERROR: " + e.getMessage());
        }
    }

    /**
     * Get sync status summary
     */
    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSyncStatus(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            // OAuth2 status
            response.put("oauth2Authorized", oAuth2Service.hasValidTokens());
            if (oAuth2Service.hasValidTokens()) {
                PayPropOAuth2Service.PayPropTokens tokens = oAuth2Service.getCurrentTokens();
                response.put("tokenExpires", tokens.getExpiresAt());
                response.put("tokenExpiringSoon", tokens.isExpiringSoon());
            }

            // Sync statistics with error handling
            Map<String, Object> properties = new HashMap<>();
            Map<String, Object> tenants = new HashMap<>();
            Map<String, Object> beneficiaries = new HashMap<>();

            try {
                properties.put("total", propertyService.getTotalProperties());
                properties.put("needsSync", propertyService.findPropertiesNeedingSync().size());
                properties.put("synced", propertyService.findPropertiesByPayPropSyncStatus(true).size());
                properties.put("readyForSync", propertyService.findPropertiesReadyForSync().size());
            } catch (Exception e) {
                log.error("Error getting property statistics: {}", e.getMessage());
                properties.put("error", "Unable to load property statistics");
                properties.put("total", 0);
                properties.put("needsSync", 0);
                properties.put("synced", 0);
                properties.put("readyForSync", 0);
            }

            try {
                tenants.put("total", tenantService.getTotalTenants());
                tenants.put("needsSync", tenantService.findByPayPropIdIsNull().size());
                tenants.put("synced", tenantService.findByPayPropIdIsNotNull().size());
                tenants.put("readyForSync", tenantService.findTenantsReadyForPayPropSync().size());
            } catch (Exception e) {
                log.error("Error getting tenant statistics: {}", e.getMessage());
                tenants.put("error", "Unable to load tenant statistics");
                tenants.put("total", 0);
                tenants.put("needsSync", 0);
                tenants.put("synced", 0);
                tenants.put("readyForSync", 0);
            }

            try {
                beneficiaries.put("total", propertyOwnerService.getTotalPropertyOwners());
                beneficiaries.put("needsSync", propertyOwnerService.findByPayPropIdIsNull().size());
                beneficiaries.put("synced", propertyOwnerService.findByPayPropIdIsNotNull().size());
                beneficiaries.put("readyForSync", propertyOwnerService.findPropertyOwnersReadyForSync().size());
            } catch (Exception e) {
                log.error("Error getting beneficiary statistics: {}", e.getMessage());
                beneficiaries.put("error", "Unable to load beneficiary statistics");
                beneficiaries.put("total", 0);
                beneficiaries.put("needsSync", 0);
                beneficiaries.put("synced", 0);
                beneficiaries.put("readyForSync", 0);
            }

            response.put("properties", properties);
            response.put("tenants", tenants);
            response.put("beneficiaries", beneficiaries);
            response.put("success", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting sync status: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error loading sync status: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Check sync status (console output)
     */
    @PostMapping("/check-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkStatus(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            syncService.checkSyncStatus();

            response.put("success", true);
            response.put("message", "Sync status printed to console");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error checking sync status: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}