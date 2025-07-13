// PayPropPaymentsSyncController.java - Dedicated controller for payments sync
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service;
import site.easy.to.build.crm.service.payprop.PayPropSyncService;
import site.easy.to.build.crm.service.payprop.PayPropSyncOrchestrator;
import site.easy.to.build.crm.service.payprop.SyncResult;
import site.easy.to.build.crm.util.AuthorizationUtil;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.repository.PaymentCategoryRepository;
import site.easy.to.build.crm.repository.PaymentRepository;
import site.easy.to.build.crm.repository.BeneficiaryBalanceRepository;

import java.util.HashMap;
import java.util.Map;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Controller
@RequestMapping("/admin/payprop/payments")
public class PayPropPaymentsSyncController {

    private static final Logger log = LoggerFactory.getLogger(PayPropPaymentsSyncController.class);

    private final PayPropOAuth2Service oAuth2Service;
    private final PayPropSyncService syncService;
    private final PayPropSyncOrchestrator syncOrchestrator;
    private final AuthenticationUtils authenticationUtils;
    private final PaymentCategoryRepository paymentCategoryRepository;
    private final PaymentRepository paymentRepository;
    private final BeneficiaryBalanceRepository beneficiaryBalanceRepository;

    @Autowired
    public PayPropPaymentsSyncController(PayPropOAuth2Service oAuth2Service,
                                        PayPropSyncService syncService,
                                        PayPropSyncOrchestrator syncOrchestrator,
                                        AuthenticationUtils authenticationUtils,
                                        PaymentCategoryRepository paymentCategoryRepository,
                                        PaymentRepository paymentRepository,
                                        BeneficiaryBalanceRepository beneficiaryBalanceRepository) {
        this.oAuth2Service = oAuth2Service;
        this.syncService = syncService;
        this.syncOrchestrator = syncOrchestrator;
        this.authenticationUtils = authenticationUtils;
        this.paymentCategoryRepository = paymentCategoryRepository;
        this.paymentRepository = paymentRepository;
        this.beneficiaryBalanceRepository = beneficiaryBalanceRepository;
    }

    /**
     * Payments sync dashboard
     */
    @GetMapping("/dashboard")
    public String paymentsDashboard(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/access-denied";
        }

        // OAuth2 status
        model.addAttribute("hasTokens", oAuth2Service.hasValidTokens());
        
        // Payment sync statistics
        try {
            long totalCategories = paymentCategoryRepository.count();
            long syncedCategories = paymentCategoryRepository.findByPayPropCategoryIdIsNotNull().size();
            long totalPayments = paymentRepository.count();
            long totalBalances = beneficiaryBalanceRepository.count();
            
            model.addAttribute("totalCategories", totalCategories);
            model.addAttribute("syncedCategories", syncedCategories);
            model.addAttribute("totalPayments", totalPayments);
            model.addAttribute("totalBalances", totalBalances);
            
        } catch (Exception e) {
            log.error("Error loading payments dashboard statistics: {}", e.getMessage());
            model.addAttribute("dashboardError", "Error loading statistics: " + e.getMessage());
            model.addAttribute("totalCategories", 0);
            model.addAttribute("syncedCategories", 0);
            model.addAttribute("totalPayments", 0);
            model.addAttribute("totalBalances", 0);
        }

        model.addAttribute("pageTitle", "PayProp Payments Sync Dashboard");
        return "admin/payprop-payments-dashboard";
    }

    /**
     * Sync payment categories only
     */
    @PostMapping("/sync/categories")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncPaymentCategories(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        if (!oAuth2Service.hasValidTokens()) {
            response.put("success", false);
            response.put("message", "PayProp not authorized. Please authorize first.");
            return ResponseEntity.ok(response);
        }

        try {
            Long initiatedBy = authenticationUtils.getCurrentUserId();
            SyncResult result = syncService.syncPaymentCategoriesFromPayProp();
            
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("details", result.getDetails());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error syncing payment categories: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Payment categories sync failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Sync payments only
     */
    @PostMapping("/sync/payments")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncPayments(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        if (!oAuth2Service.hasValidTokens()) {
            response.put("success", false);
            response.put("message", "PayProp not authorized. Please authorize first.");
            return ResponseEntity.ok(response);
        }

        try {
            Long initiatedBy = authenticationUtils.getCurrentUserId();
            SyncResult result = syncService.syncPaymentsToDatabase(initiatedBy);
            
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("details", result.getDetails());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error syncing payments: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Payments sync failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Sync beneficiary balances only
     */
    @PostMapping("/sync/balances")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncBeneficiaryBalances(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        if (!oAuth2Service.hasValidTokens()) {
            response.put("success", false);
            response.put("message", "PayProp not authorized. Please authorize first.");
            return ResponseEntity.ok(response);
        }

        try {
            Long initiatedBy = authenticationUtils.getCurrentUserId();
            SyncResult result = syncService.syncBeneficiaryBalancesToDatabase(initiatedBy);
            
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("details", result.getDetails());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error syncing beneficiary balances: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Beneficiary balances sync failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Sync all payment data (categories + payments + balances)
     */
    @PostMapping("/sync/all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncAllPaymentData(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        if (!oAuth2Service.hasValidTokens()) {
            response.put("success", false);
            response.put("message", "PayProp not authorized. Please authorize first.");
            return ResponseEntity.ok(response);
        }

        try {
            Long initiatedBy = authenticationUtils.getCurrentUserId();
            
            // Step 1: Sync payment categories
            SyncResult categoriesResult = syncService.syncPaymentCategoriesFromPayProp();
            
            // Step 2: Sync payments
            SyncResult paymentsResult = syncService.syncPaymentsToDatabase(initiatedBy);
            
            // Step 3: Sync beneficiary balances
            SyncResult balancesResult = syncService.syncBeneficiaryBalancesToDatabase(initiatedBy);
            
            // Combine results
            boolean overallSuccess = categoriesResult.isSuccess() && 
                                   paymentsResult.isSuccess() && 
                                   balancesResult.isSuccess();
            
            Map<String, Object> details = new HashMap<>();
            details.put("categories", categoriesResult.getDetails());
            details.put("payments", paymentsResult.getDetails());
            details.put("balances", balancesResult.getDetails());
            
            response.put("success", overallSuccess);
            response.put("message", overallSuccess ? 
                "All payment data synced successfully" : 
                "Payment data sync completed with some issues");
            response.put("details", details);
            response.put("categoriesResult", categoriesResult.getMessage());
            response.put("paymentsResult", paymentsResult.getMessage());
            response.put("balancesResult", balancesResult.getMessage());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error syncing all payment data: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Payment data sync failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Get payment sync statistics
     */
    @GetMapping("/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPaymentStats(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            Map<String, Object> stats = new HashMap<>();
            
            // Payment categories stats
            long totalCategories = paymentCategoryRepository.count();
            long syncedCategories = paymentCategoryRepository.findByPayPropCategoryIdIsNotNull().size();
            long activeCategories = paymentCategoryRepository.findByIsActive("Y").size();
            
            stats.put("totalCategories", totalCategories);
            stats.put("syncedCategories", syncedCategories);
            stats.put("activeCategories", activeCategories);
            stats.put("unsyncedCategories", totalCategories - syncedCategories);
            
            // Payment stats
            long totalPayments = paymentRepository.count();
            stats.put("totalPayments", totalPayments);
            
            // Balance stats
            long totalBalances = beneficiaryBalanceRepository.count();
            stats.put("totalBalances", totalBalances);
            
            response.put("success", true);
            response.put("stats", stats);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting payment statistics: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error loading statistics: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Test payment categories API endpoint
     */
    @GetMapping("/test/categories")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testPaymentCategoriesApi(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        if (!oAuth2Service.hasValidTokens()) {
            response.put("success", false);
            response.put("message", "PayProp not authorized. Please authorize first.");
            return ResponseEntity.ok(response);
        }

        try {
            // Just test the API call without saving to database
            PayPropSyncService.PayPropExportResult result = syncService.exportPaymentsFromPayProp(1, 5);
            
            response.put("success", true);
            response.put("message", "Payment categories API test successful");
            response.put("sampleData", result.getItems().size() > 0 ? result.getItems().get(0) : "No data");
            response.put("totalItems", result.getItems().size());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing payment categories API: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "API test failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}