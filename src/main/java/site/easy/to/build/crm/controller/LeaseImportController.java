package site.easy.to.build.crm.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.service.lease.LeaseImportService;
import site.easy.to.build.crm.service.lease.LeaseImportService.ImportResult;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Lease Import Controller - Handles bulk import of lease agreements
 *
 * Provides endpoints for importing leases (Invoice entities) in bulk from CSV.
 * This is Phase 1 of the two-phase import workflow:
 *   Phase 1: Import leases (this controller)
 *   Phase 2: Import transactions (HistoricalTransactionImportController)
 *
 * CSV Format:
 * property_reference,customer_reference,lease_start_date,lease_end_date,rent_amount,payment_day,lease_reference
 */
@Controller
@RequestMapping("/employee/lease")
public class LeaseImportController {

    private static final Logger log = LoggerFactory.getLogger(LeaseImportController.class);

    @Autowired
    private LeaseImportService leaseImportService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationUtils authenticationUtils;

    /**
     * Show lease import page
     */
    @GetMapping("/import")
    public String showImportPage(Model model, Authentication authentication) {
        try {
            log.info("🔍 Loading lease import page for user: {}",
                    authentication != null ? authentication.getName() : "unknown");

            model.addAttribute("pageTitle", "Lease Import");

            log.info("✅ Lease import page loaded successfully");
            return "lease/import";

        } catch (Exception e) {
            log.error("❌ Error loading lease import page: {}", e.getMessage(), e);
            model.addAttribute("error", "Error loading import page: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Handle CSV file upload
     */
    @PostMapping("/import/csv")
    public String importCsvFile(
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {

        log.info("🚀 CSV LEASE IMPORT ENDPOINT REACHED");

        try {
            log.info("📊 Lease CSV Import: {} (size: {} bytes)", file.getOriginalFilename(), file.getBytes().length);

            // Validate file
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please select a CSV file to upload");
                return "redirect:/employee/lease/import";
            }

            if (!file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
                redirectAttributes.addFlashAttribute("error", "Please upload a CSV file (.csv extension)");
                return "redirect:/employee/lease/import";
            }

            // Get current user
            User currentUser = getCurrentUser(authentication);
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "User not found");
                return "redirect:/employee/lease/import";
            }

            // Import file
            ImportResult result = leaseImportService.importFromCsvFile(file, currentUser);

            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("success",
                        String.format("Lease import successful! %s", result.getSummary()));
                log.info("✅ Lease CSV Import completed: {}", result.getSummary());
            } else {
                String errorMsg = String.format("Lease import completed with errors: %s", result.getSummary());
                if (!result.getErrors().isEmpty()) {
                    errorMsg += "\nFirst 3 errors: " + String.join("; ",
                            result.getErrors().subList(0, Math.min(3, result.getErrors().size())));
                }
                redirectAttributes.addFlashAttribute("warning", errorMsg);
                log.warn("⚠️ Lease CSV Import completed with errors: {}", result.getSummary());
            }

        } catch (Exception e) {
            log.error("❌ Lease CSV import failed: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Lease import failed: " + e.getMessage());
        }

        return "redirect:/employee/lease/import";
    }

    /**
     * Handle CSV string import (for paste functionality)
     */
    @PostMapping("/import/csv-string")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> importCsvString(
            @RequestParam("csvData") String csvData,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("📋 Lease CSV String Import: {} characters", csvData.length());

            // Validate input
            if (csvData == null || csvData.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "CSV data is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Get current user
            User currentUser = getCurrentUser(authentication);
            if (currentUser == null) {
                response.put("success", false);
                response.put("error", "User not found");
                return ResponseEntity.status(401).body(response);
            }

            // Import data
            ImportResult result = leaseImportService.importFromCsvString(csvData, currentUser);

            response.put("success", result.isSuccess());
            response.put("summary", result.getSummary());
            response.put("totalRows", result.getTotalRows());
            response.put("successfulImports", result.getSuccessfulImports());
            response.put("failedImports", result.getFailedImports());
            response.put("skippedRows", result.getSkippedRows());
            response.put("errors", result.getErrors());
            response.put("skipped", result.getSkipped());
            response.put("durationSeconds", result.getDurationSeconds());

            if (result.isSuccess()) {
                log.info("✅ Lease CSV String Import completed: {}", result.getSummary());
            } else {
                log.warn("⚠️ Lease CSV String Import completed with errors: {}", result.getSummary());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Lease CSV string import failed: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Import failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get import format examples
     */
    @GetMapping("/import/examples")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getImportExamples() {
        Map<String, Object> examples = new HashMap<>();

        // CSV Example
        String csvExample = "property_reference,customer_reference,lease_start_date,lease_end_date,rent_amount,payment_day,lease_reference\n" +
                           "\"FLAT 1 - 3 WEST GATE\",\"MS O SMOLIARENKO\",2024-04-27,,795.00,27,LEASE-FLAT1-2024\n" +
                           "\"FLAT 18 - 3 WEST GATE\",\"PREVIOUS TENANT\",2023-03-01,2024-06-30,740.00,28,LEASE-FLAT18-2023\n" +
                           "\"FLAT 18 - 3 WEST GATE\",\"MARIE DINKO\",2024-07-01,,740.00,28,LEASE-FLAT18-2024";

        examples.put("csv_example", csvExample);
        examples.put("csv_headers", java.util.List.of(
            "property_reference (required)", "customer_reference (required)",
            "lease_start_date (required)", "lease_end_date (optional - leave blank for ongoing)",
            "rent_amount (required)", "payment_day (required)", "lease_reference (required - unique)"
        ));
        examples.put("date_formats", java.util.List.of(
            "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "yyyy/MM/dd"
        ));
        examples.put("notes", java.util.List.of(
            "lease_reference must be unique (e.g., LEASE-FLAT1-2024)",
            "leave lease_end_date empty for ongoing/current leases",
            "payment_day should be between 1 and 31",
            "Import leases BEFORE importing historical transactions"
        ));
        examples.put("workflow", java.util.Map.of(
            "step1", "Import leases using this endpoint",
            "step2", "Then import transactions at /employee/transaction/import with lease_reference column"
        ));

        return ResponseEntity.ok(examples);
    }

    /**
     * Get current user from authentication
     */
    private User getCurrentUser(Authentication authentication) {
        if (authentication == null) return null;

        try {
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
            return userService.findById(userId);
        } catch (Exception e) {
            log.error("Failed to get current user: {}", e.getMessage());
            return null;
        }
    }
}
