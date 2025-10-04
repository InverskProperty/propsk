package site.easy.to.build.crm.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.PaymentSource;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.service.paymentsource.PaymentSourceService;
import site.easy.to.build.crm.service.user.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Payment Source Management
 */
@Controller
@RequestMapping("/employee/payment-sources")
public class PaymentSourceController {

    private static final Logger log = LoggerFactory.getLogger(PaymentSourceController.class);

    @Autowired
    private PaymentSourceService paymentSourceService;

    @Autowired
    private UserService userService;

    /**
     * Get all active payment sources
     */
    @GetMapping("/active")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getActivePaymentSources() {
        log.info("📋 [PAYMENT-SOURCES] GET /active called");

        try {
            List<PaymentSource> sources = paymentSourceService.getAllActivePaymentSources();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sources", sources);
            response.put("count", sources.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to fetch active payment sources", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Get all payment sources (active and inactive)
     */
    @GetMapping("/all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAllPaymentSources() {
        log.info("📋 [PAYMENT-SOURCES] GET /all called");

        try {
            List<PaymentSource> sources = paymentSourceService.getAllPaymentSources();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sources", sources);
            response.put("count", sources.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to fetch all payment sources", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Get recently used payment sources
     */
    @GetMapping("/recent")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRecentPaymentSources() {
        log.info("📋 [PAYMENT-SOURCES] GET /recent called");

        try {
            List<PaymentSource> sources = paymentSourceService.getRecentlyUsedSources();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sources", sources);
            response.put("count", sources.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to fetch recent payment sources", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Get payment source by ID
     */
    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPaymentSource(@PathVariable Long id) {
        log.info("📋 [PAYMENT-SOURCES] GET /{} called", id);

        try {
            PaymentSource source = paymentSourceService.getPaymentSourceById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Payment source not found: " + id));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("source", source);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to fetch payment source {}", id, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Create new payment source
     */
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createPaymentSource(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            Authentication authentication) {

        log.info("✨ [PAYMENT-SOURCES] POST /create called - name: {}", name);

        try {
            User currentUser = userService.findByEmail(authentication.getName());
            if (currentUser == null) {
                throw new IllegalStateException("Current user not found");
            }

            PaymentSource source = paymentSourceService.createPaymentSource(
                    name, description, sourceType, currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("source", source);
            response.put("message", "Payment source created successfully");

            log.info("✅ Payment source created: {} (ID: {})", source.getName(), source.getId());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Validation error creating payment source: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            log.error("❌ Failed to create payment source", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to create payment source: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Update payment source
     */
    @PostMapping("/{id}/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updatePaymentSource(
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "sourceType", required = false) String sourceType) {

        log.info("📝 [PAYMENT-SOURCES] POST /{}/update called", id);

        try {
            PaymentSource source = paymentSourceService.updatePaymentSource(id, name, description, sourceType);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("source", source);
            response.put("message", "Payment source updated successfully");

            log.info("✅ Payment source updated: {}", source.getName());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Validation error updating payment source: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            log.error("❌ Failed to update payment source {}", id, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to update payment source: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Deactivate payment source (soft delete)
     */
    @PostMapping("/{id}/deactivate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deactivatePaymentSource(@PathVariable Long id) {
        log.info("🗑️ [PAYMENT-SOURCES] POST /{}/deactivate called", id);

        try {
            paymentSourceService.deactivatePaymentSource(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment source deactivated successfully");

            log.info("✅ Payment source deactivated: {}", id);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to deactivate payment source {}", id, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Reactivate payment source
     */
    @PostMapping("/{id}/reactivate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reactivatePaymentSource(@PathVariable Long id) {
        log.info("♻️ [PAYMENT-SOURCES] POST /{}/reactivate called", id);

        try {
            paymentSourceService.reactivatePaymentSource(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment source reactivated successfully");

            log.info("✅ Payment source reactivated: {}", id);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to reactivate payment source {}", id, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Delete payment source (hard delete - only if no transactions)
     */
    @PostMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deletePaymentSource(@PathVariable Long id) {
        log.warn("⚠️ [PAYMENT-SOURCES] POST /{}/delete called", id);

        try {
            paymentSourceService.deletePaymentSource(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment source deleted successfully");

            log.info("✅ Payment source deleted: {}", id);

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            log.warn("⚠️ Cannot delete payment source: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            log.error("❌ Failed to delete payment source {}", id, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get payment source statistics
     */
    @GetMapping("/{id}/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPaymentSourceStats(@PathVariable Long id) {
        log.info("📊 [PAYMENT-SOURCES] GET /{}/stats called", id);

        try {
            PaymentSourceService.PaymentSourceStats stats = paymentSourceService.getPaymentSourceStats(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("stats", stats);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to fetch payment source stats for {}", id, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
