package site.easy.to.build.crm.controller.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerLoginInfo;
import site.easy.to.build.crm.service.customer.CustomerLoginInfoService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.util.EmailTokenUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Admin API controller for user management
 * Provides quick password reset functionality
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerLoginInfoService customerLoginInfoService;

    /**
     * Create or reset customer login with password "123"
     * GET /api/admin/reset-password?email=xxx
     */
    @GetMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestParam String email) {
        Map<String, Object> response = new HashMap<>();
        String defaultPassword = "123";

        try {
            log.info("Resetting password for: {}", email);

            // Find customer
            Customer customer = customerService.findByEmail(email);
            if (customer == null) {
                response.put("success", false);
                response.put("message", "Customer not found with email: " + email);
                return ResponseEntity.badRequest().body(response);
            }

            // Check if login exists
            CustomerLoginInfo loginInfo = customerLoginInfoService.findByEmail(email);

            if (loginInfo != null) {
                // Update existing password
                loginInfo.setPassword(EmailTokenUtils.encodePassword(defaultPassword));
                loginInfo.setUpdatedAt(LocalDateTime.now());
                customerLoginInfoService.save(loginInfo);
                log.info("Password reset for existing login: {}", email);
            } else {
                // Create new login
                loginInfo = new CustomerLoginInfo();
                loginInfo.setUsername(email);
                loginInfo.setPassword(EmailTokenUtils.encodePassword(defaultPassword));
                loginInfo.setPasswordSet(true);
                loginInfo.setAccountLocked(false);
                loginInfo.setLoginAttempts(0);
                loginInfo.setCreatedAt(LocalDateTime.now());

                CustomerLoginInfo savedLogin = customerLoginInfoService.save(loginInfo);

                // Link to customer
                customer.setCustomerLoginInfo(savedLogin);
                customerService.save(customer);
                log.info("New login created for: {}", email);
            }

            response.put("success", true);
            response.put("message", "Password set to: " + defaultPassword);
            response.put("email", email);
            response.put("password", defaultPassword);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error resetting password for {}: {}", email, e.getMessage());
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
