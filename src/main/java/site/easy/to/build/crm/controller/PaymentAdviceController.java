package site.easy.to.build.crm.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.dto.paymentadvice.PaymentAdviceDTO;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.PaymentBatch;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.service.payment.PaymentAdviceService;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for Payment Advice generation and viewing.
 * Allows owners to view detailed breakdowns of their payments.
 */
@Controller
@RequestMapping("/owner/payment-advice")
public class PaymentAdviceController {

    private static final Logger log = LoggerFactory.getLogger(PaymentAdviceController.class);

    @Autowired
    private PaymentAdviceService paymentAdviceService;

    @Autowired
    private CustomerRepository customerRepository;

    /**
     * List available payment batches for selection.
     * Respects user access: delegated users only see their assigned owner.
     */
    @GetMapping("")
    public String listPaymentBatches(
            @RequestParam(required = false) Long ownerId,
            Authentication authentication,
            Model model) {

        log.info("Listing payment batches, ownerId: {}", ownerId);

        // Get the current logged-in user
        Customer currentUser = getCurrentCustomer(authentication);
        log.info("Current user: {} (type: {})",
            currentUser != null ? currentUser.getName() : "null",
            currentUser != null ? currentUser.getCustomerType() : "null");

        // Determine accessible owners based on user type
        List<Customer> accessibleOwners = getAccessibleOwners(currentUser, authentication);
        model.addAttribute("owners", accessibleOwners);

        // Validate that the requested ownerId is accessible
        Long effectiveOwnerId = ownerId;
        if (ownerId != null) {
            boolean hasAccess = accessibleOwners.stream()
                .anyMatch(o -> o.getCustomerId().equals(ownerId.intValue()));
            if (!hasAccess) {
                log.warn("User {} attempted to access owner {} without permission",
                    currentUser != null ? currentUser.getCustomerId() : "unknown", ownerId);
                effectiveOwnerId = null;
            }
        }

        // If only one owner is accessible and none selected, auto-select it
        if (effectiveOwnerId == null && accessibleOwners.size() == 1) {
            effectiveOwnerId = accessibleOwners.get(0).getCustomerId().longValue();
        }

        model.addAttribute("selectedOwnerId", effectiveOwnerId);

        // Get batches for selected owner
        List<PaymentBatch> batches;
        if (effectiveOwnerId != null) {
            batches = paymentAdviceService.getBatchesForOwner(effectiveOwnerId);
            Customer selectedOwner = customerRepository.findById(effectiveOwnerId).orElse(null);
            model.addAttribute("selectedOwner", selectedOwner);
        } else {
            batches = List.of();
        }
        model.addAttribute("batches", batches);

        // Pass flags for UI
        model.addAttribute("singleOwnerMode", accessibleOwners.size() == 1);

        return "owner/payment-advice/list";
    }

    /**
     * Get the current logged-in customer from authentication.
     */
    private Customer getCurrentCustomer(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        String email = null;
        Object principal = authentication.getPrincipal();

        if (principal instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) principal;
            email = oauth2User.getAttribute("email");
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            email = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        }

        if (email != null) {
            return customerRepository.findByEmail(email);
        }
        return null;
    }

    /**
     * Get list of property owners accessible to the current user.
     * - Admin/Employee: All property owners
     * - Property Owner: Only themselves
     * - Delegated User/Manager: Only their assigned owner
     */
    private List<Customer> getAccessibleOwners(Customer currentUser, Authentication authentication) {
        // Check if user is admin/employee (has ROLE_MANAGER or ROLE_EMPLOYEE)
        boolean isStaff = false;
        if (authentication != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                String auth = authority.getAuthority();
                if ("ROLE_MANAGER".equals(auth) || "ROLE_EMPLOYEE".equals(auth) || "ROLE_ADMIN".equals(auth)) {
                    isStaff = true;
                    break;
                }
            }
        }

        if (isStaff) {
            // Staff can see all property owners
            log.info("Staff user - showing all property owners");
            return customerRepository.findByCustomerType(CustomerType.PROPERTY_OWNER);
        }

        if (currentUser == null) {
            log.warn("No current user found - returning empty list");
            return List.of();
        }

        CustomerType userType = currentUser.getCustomerType();

        if (userType == CustomerType.PROPERTY_OWNER) {
            // Property owner sees only themselves
            log.info("Property owner {} - showing only self", currentUser.getCustomerId());
            return List.of(currentUser);
        }

        if (userType == CustomerType.DELEGATED_USER || userType == CustomerType.MANAGER) {
            // Delegated user/manager sees only their assigned owner
            Customer managesOwner = currentUser.getManagesOwner();
            if (managesOwner != null) {
                log.info("Delegated user {} manages owner {} - showing only that owner",
                    currentUser.getCustomerId(), managesOwner.getCustomerId());
                return List.of(managesOwner);
            } else {
                log.warn("Delegated user {} has no assigned owner", currentUser.getCustomerId());
                return List.of();
            }
        }

        // Default: no access
        log.warn("User type {} has no defined access - returning empty list", userType);
        return List.of();
    }

    /**
     * View a single payment advice for a batch.
     */
    @GetMapping("/{batchId}")
    public String viewPaymentAdvice(
            @PathVariable String batchId,
            Model model) {

        log.info("Viewing payment advice for batch: {}", batchId);

        try {
            PaymentAdviceDTO advice = paymentAdviceService.generatePaymentAdvice(batchId);
            model.addAttribute("advice", advice);
            return "owner/payment-advice/view";
        } catch (Exception e) {
            log.error("Error generating payment advice for batch {}: {}", batchId, e.getMessage());
            model.addAttribute("error", "Could not generate payment advice: " + e.getMessage());
            return "redirect:/owner/payment-advice";
        }
    }

    /**
     * Export payment advice as PDF.
     * For now, redirects to the print view which can be printed to PDF.
     */
    @GetMapping("/{batchId}/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable String batchId) {
        log.info("Exporting PDF for batch: {}", batchId);

        try {
            // For now, return the Excel file as PDF is more complex to generate
            // Users can use browser print to PDF for the view page
            byte[] excelBytes = paymentAdviceService.exportToExcel(batchId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                "PaymentAdvice_" + batchId + ".pdf");

            // Note: For a proper PDF, we'd need to use iText html2pdf or similar
            // For now, return a message suggesting print to PDF
            String message = "PDF export coming soon. Please use the Print button and select 'Save as PDF' in your browser.";
            return ResponseEntity.ok()
                .header("Content-Type", "text/plain")
                .body(message.getBytes());
        } catch (Exception e) {
            log.error("Error exporting PDF for batch {}: {}", batchId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(("Error: " + e.getMessage()).getBytes());
        }
    }

    /**
     * Export payment advice as Excel.
     */
    @GetMapping("/{batchId}/excel")
    public ResponseEntity<byte[]> exportExcel(@PathVariable String batchId) {
        log.info("Exporting Excel for batch: {}", batchId);

        try {
            byte[] excelBytes = paymentAdviceService.exportToExcel(batchId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment",
                "PaymentAdvice_" + batchId + ".xlsx");
            headers.setContentLength(excelBytes.length);

            return ResponseEntity.ok()
                .headers(headers)
                .body(excelBytes);
        } catch (Exception e) {
            log.error("Error exporting Excel for batch {}: {}", batchId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(("Error: " + e.getMessage()).getBytes());
        }
    }
}
