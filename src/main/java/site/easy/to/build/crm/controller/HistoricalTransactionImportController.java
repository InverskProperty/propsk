package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.service.transaction.HistoricalTransactionImportService;
import site.easy.to.build.crm.service.transaction.HistoricalTransactionImportService.ImportResult;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.AccountType;
import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * Controller for Historical Transaction Import
 * Handles CSV and JSON file uploads for importing historical payment/transaction data
 */
@Controller
@RequestMapping("/employee/transaction")
public class HistoricalTransactionImportController {
    
    private static final Logger log = LoggerFactory.getLogger(HistoricalTransactionImportController.class);
    
    @Autowired
    private HistoricalTransactionImportService importService;
    
    @Autowired
    private PropertyService propertyService;
    
    @Autowired
    private CustomerService customerService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationUtils authenticationUtils;

    /**
     * Show historical transaction import page
     */
    @GetMapping("/import")
    public String showImportPage(Model model, Authentication authentication) {
        try {
            log.info("🔍 Loading import page for user: {}",
                authentication != null ? authentication.getName() : "unknown");

            // Skip database queries for now to avoid timeouts
            // Just show the import form without data counts
            model.addAttribute("propertyCount", "N/A");
            model.addAttribute("customerCount", "N/A");
            model.addAttribute("pageTitle", "Historical Transaction Import");

            log.info("✅ Import page loaded successfully (fast mode)");
            return "transaction/import";

        } catch (Exception e) {
            log.error("❌ Error loading import page: {}", e.getMessage(), e);
            model.addAttribute("error", "Error loading import page: " + e.getMessage());
            return "error/500";
        }
    }
    
    /**
     * Debug endpoint to test security
     */
    @GetMapping("/debug-auth")
    @ResponseBody
    public String debugAuth(Authentication authentication) {
        log.info("🔍 DEBUG-AUTH endpoint reached!");
        StringBuilder debug = new StringBuilder();
        debug.append("Authentication: ").append(authentication != null ? "Present" : "Null").append("\n");
        if (authentication != null) {
            debug.append("Name: ").append(authentication.getName()).append("\n");
            debug.append("Authorities: ").append(authentication.getAuthorities()).append("\n");
            debug.append("Is Authenticated: ").append(authentication.isAuthenticated()).append("\n");
        }
        return debug.toString();
    }

    /**
     * Test POST endpoint to debug security
     */
    @PostMapping("/debug-post")
    @ResponseBody
    public String debugPost(Authentication authentication) {
        log.info("🔍 DEBUG-POST endpoint reached!");
        StringBuilder debug = new StringBuilder();
        debug.append("POST Authentication: ").append(authentication != null ? "Present" : "Null").append("\n");
        if (authentication != null) {
            debug.append("Name: ").append(authentication.getName()).append("\n");
            debug.append("Authorities: ").append(authentication.getAuthorities()).append("\n");
            debug.append("Is Authenticated: ").append(authentication.isAuthenticated()).append("\n");
        }
        return debug.toString();
    }

    /**
     * Simple test page for debugging forms
     */
    @GetMapping("/test-form")
    public String testForm() {
        return "transaction/test-form";
    }

    /**
     * Handle CSV file upload
     */
    @PostMapping("/import/csv")
    public String importCsvFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "batchDescription", defaultValue = "") String batchDescription,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {

        log.info("🚀 CSV IMPORT ENDPOINT REACHED! This confirms security is working!");

        try {
            log.info("📊 CSV Import: {} (size: {} bytes)", file.getOriginalFilename(), file.getSize());
            log.info("🔐 Authentication: {} - Authorities: {}",
                    authentication != null ? authentication.getName() : "null",
                    authentication != null ? authentication.getAuthorities() : "null");
            
            // Validate file
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please select a CSV file to upload");
                return "redirect:/employee/transaction/import";
            }
            
            if (!file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
                redirectAttributes.addFlashAttribute("error", "Please upload a CSV file (.csv extension)");
                return "redirect:/employee/transaction/import";
            }
            
            // Import file
            ImportResult result = importService.importFromCsvFile(file, batchDescription);
            
            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("success", 
                    String.format("CSV import successful! %s", result.getSummary()));
                log.info("✅ CSV Import completed: {}", result.getSummary());
            } else {
                String errorMsg = String.format("CSV import completed with errors: %s", result.getSummary());
                if (!result.getErrors().isEmpty()) {
                    errorMsg += "\nFirst 3 errors: " + String.join("; ", 
                        result.getErrors().subList(0, Math.min(3, result.getErrors().size())));
                }
                redirectAttributes.addFlashAttribute("warning", errorMsg);
                log.warn("⚠️ CSV Import completed with errors: {}", result.getSummary());
            }
            
        } catch (Exception e) {
            log.error("❌ CSV import failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "CSV import failed: " + e.getMessage());
        }
        
        return "redirect:/employee/transaction/import";
    }
    
    /**
     * Handle JSON file upload
     */
    @PostMapping("/import/json")
    public String importJsonFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "batchDescription", defaultValue = "") String batchDescription,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {
        
        try {
            log.info("📋 JSON Import: {} (size: {} bytes)", file.getOriginalFilename(), file.getSize());
            
            // Validate file
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please select a JSON file to upload");
                return "redirect:/employee/transaction/import";
            }
            
            if (!file.getOriginalFilename().toLowerCase().endsWith(".json")) {
                redirectAttributes.addFlashAttribute("error", "Please upload a JSON file (.json extension)");
                return "redirect:/employee/transaction/import";
            }
            
            // Import file
            ImportResult result = importService.importFromJsonFile(file, batchDescription);
            
            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("success", 
                    String.format("JSON import successful! %s", result.getSummary()));
                log.info("✅ JSON Import completed: {}", result.getSummary());
            } else {
                String errorMsg = String.format("JSON import completed with errors: %s", result.getSummary());
                if (!result.getErrors().isEmpty()) {
                    errorMsg += "\nFirst 3 errors: " + String.join("; ", 
                        result.getErrors().subList(0, Math.min(3, result.getErrors().size())));
                }
                redirectAttributes.addFlashAttribute("warning", errorMsg);
                log.warn("⚠️ JSON Import completed with errors: {}", result.getSummary());
            }
            
        } catch (Exception e) {
            log.error("❌ JSON import failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "JSON import failed: " + e.getMessage());
        }
        
        return "redirect:/employee/transaction/import";
    }
    
    /**
     * Handle JSON string import (for paste functionality)
     */
    @PostMapping("/import/json-string")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> importJsonString(
            @RequestParam("jsonData") String jsonData,
            @RequestParam(value = "batchDescription", defaultValue = "") String batchDescription,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("📋 JSON String Import: {} characters", jsonData.length());

            // Validate input
            if (jsonData == null || jsonData.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "JSON data is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Import data
            ImportResult result = importService.importFromJsonString(jsonData, batchDescription);

            response.put("success", result.isSuccess());
            response.put("batchId", result.getBatchId());
            response.put("summary", result.getSummary());
            response.put("totalProcessed", result.getTotalProcessed());
            response.put("successfulImports", result.getSuccessfulImports());
            response.put("failedImports", result.getFailedImports());
            response.put("errors", result.getErrors());

            if (result.isSuccess()) {
                log.info("✅ JSON String Import completed: {}", result.getSummary());
            } else {
                log.warn("⚠️ JSON String Import completed with errors: {}", result.getSummary());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ JSON string import failed: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Import failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Handle CSV string import (for paste functionality)
     * Supports optional batchId parameter to group multiple paste operations into one batch
     */
    @PostMapping("/import/csv-string")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> importCsvString(
            @RequestParam("csvData") String csvData,
            @RequestParam(value = "batchDescription", defaultValue = "") String batchDescription,
            @RequestParam(value = "batchId", required = false) String batchId,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("📋 CSV String Import: {} characters, batch: {}", csvData.length(),
                    batchId != null ? batchId : "new batch");

            // Validate input
            if (csvData == null || csvData.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "CSV data is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Import data with optional batch ID
            ImportResult result = importService.importFromCsvString(csvData, batchDescription, batchId);

            response.put("success", result.isSuccess());
            response.put("batchId", result.getBatchId());
            response.put("summary", result.getSummary());
            response.put("totalProcessed", result.getTotalProcessed());
            response.put("successfulImports", result.getSuccessfulImports());
            response.put("failedImports", result.getFailedImports());
            response.put("skippedDuplicates", result.getSkippedDuplicates());
            response.put("skippedDuplicatesInPaste", result.getSkippedDuplicatesInPaste());
            response.put("skippedDuplicatesInBatch", result.getSkippedDuplicatesInBatch());
            response.put("skippedDuplicatesInDatabase", result.getSkippedDuplicatesInDatabase());
            response.put("errors", result.getErrors());
            response.put("skippedTransactions", result.getSkippedTransactions());

            if (result.isSuccess()) {
                log.info("✅ CSV String Import completed: {} (batch: {})", result.getSummary(), result.getBatchId());
            } else {
                log.warn("⚠️ CSV String Import completed with errors: {} (batch: {})", result.getSummary(), result.getBatchId());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ CSV string import failed: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Import failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Validate CSV data before import (no database operations)
     * Returns validation errors without attempting to save
     */
    @PostMapping("/import/validate-csv")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateCsvString(
            @RequestParam("csvData") String csvData,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("🔍 Validating CSV data: {} characters", csvData.length());

            // Validate input
            if (csvData == null || csvData.trim().isEmpty()) {
                response.put("valid", false);
                response.put("errors", List.of("CSV data is required"));
                return ResponseEntity.badRequest().body(response);
            }

            // Perform validation without database operations
            ImportResult validationResult = importService.validateCsvString(csvData);

            response.put("valid", validationResult.isSuccess());
            response.put("totalRows", validationResult.getTotalProcessed());
            response.put("validRows", validationResult.getSuccessfulImports());
            response.put("invalidRows", validationResult.getFailedImports());
            response.put("errors", validationResult.getErrors());

            if (validationResult.isSuccess()) {
                log.info("✅ CSV validation passed: {} rows valid", validationResult.getSuccessfulImports());
            } else {
                log.warn("⚠️ CSV validation failed: {} errors found", validationResult.getFailedImports());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ CSV validation failed: {}", e.getMessage());
            response.put("valid", false);
            response.put("errors", List.of("Validation failed: " + e.getMessage()));
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get recent import batches
     */
    @GetMapping("/import/batches")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getRecentBatches() {
        try {
            log.info("📋 Fetching recent import batches");

            // Get batch summaries from repository
            org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 10);

            List<Object[]> batchSummaries = importService.getRecentBatchSummaries(pageable);

            List<Map<String, Object>> batches = new ArrayList<>();
            for (Object[] summary : batchSummaries) {
                Map<String, Object> batch = new HashMap<>();
                batch.put("batchId", summary[0]);
                batch.put("count", summary[1]);
                batch.put("earliestDate", summary[2]);
                batch.put("latestDate", summary[3]);
                batch.put("importedAt", summary[4]);
                batches.add(batch);
            }

            log.info("✅ Retrieved {} batches", batches.size());
            return ResponseEntity.ok(batches);

        } catch (Exception e) {
            log.error("❌ Failed to get recent batches: {}", e.getMessage());
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
    }

    /**
     * Delete import batch
     */
    @PostMapping("/import/delete-batch")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteBatch(
            @RequestParam("batchId") String batchId,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("🗑️ Delete batch request: {}", batchId);

            // Get count before deletion
            long count = importService.countTransactionsInBatch(batchId);

            if (count == 0) {
                response.put("success", false);
                response.put("error", "Batch not found or already empty");
                return ResponseEntity.badRequest().body(response);
            }

            // Delete the batch
            importService.deleteBatch(batchId);

            response.put("success", true);
            response.put("message", String.format("Successfully deleted %d transactions from batch %s", count, batchId));
            response.put("deletedCount", count);

            log.info("✅ Deleted batch {}: {} transactions", batchId, count);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to delete batch {}: {}", batchId, e.getMessage());
            response.put("success", false);
            response.put("error", "Failed to delete batch: " + e.getMessage());
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

        // BEGINNER: Minimal template (2 required fields only)
        String csvExampleMinimal = "transaction_date,amount\n" +
                                  "2025-05-06,795.00\n" +
                                  "2025-05-15,740.00\n" +
                                  "2025-05-20,-150.00\n";

        // INTERMEDIATE: Recommended template (7 most useful fields)
        String csvExampleRecommended = "transaction_date,amount,description,category,property_reference,customer_reference,lease_reference\n" +
                                      "2025-05-06,795.00,Rent - May 2025,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO,LEASE-BH-F1-2025\n" +
                                      "2025-05-06,-119.25,Management fee,commission,FLAT 1 - 3 WEST GATE,,\n" +
                                      "2025-05-15,740.00,Rent - May 2025,rent,FLAT 2 - 3 WEST GATE,MR M K J AL BAYAT,LEASE-BH-F2-2025\n";

        // ADVANCED: Full template with all available fields
        String csvExampleFull = "transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,incoming_transaction_amount,payment_method,notes,payprop_property_id,payprop_tenant_id\n" +
                               "2025-01-22,1125.00,\"Rent - January 2025\",payment,rent,\"Apartment F - Knighton Hayes\",Riaz,\"LEASE-APT-F-2025\",1125.00,\"Bank Transfer\",\"Rent due on 22nd\",PPR_12345,PPT_67890\n" +
                               "2025-01-25,-956.25,\"Payment to Owner\",payment,owner_payment,,\"John Smith\",,,,\"Bank Transfer\",\"Monthly payment\",,\n" +
                               "2025-01-20,-150.00,\"Plumbing Repair\",expense,maintenance,\"Apartment F - Knighton Hayes\",\"ABC Plumbing\",\"LEASE-APT-F-2025\",,,,,\n";

        // Backward compatibility - use recommended as default
        String csvExample = csvExampleRecommended;

        // JSON Example
        String jsonExample = "{\n" +
                            "  \"source_description\": \"Historical bank data 2025\",\n" +
                            "  \"transactions\": [\n" +
                            "    {\n" +
                            "      \"transaction_date\": \"2025-01-22\",\n" +
                            "      \"amount\": 1125.00,\n" +
                            "      \"description\": \"Rent Due - January 2025\",\n" +
                            "      \"transaction_type\": \"invoice\",\n" +
                            "      \"category\": \"rent\",\n" +
                            "      \"property_reference\": \"Apartment F - Knighton Hayes\",\n" +
                            "      \"customer_reference\": \"Riaz\",\n" +
                            "      \"payment_source\": \"OLD_ACCOUNT\"\n" +
                            "    },\n" +
                            "    {\n" +
                            "      \"transaction_date\": \"2025-01-22\",\n" +
                            "      \"amount\": 1125.00,\n" +
                            "      \"description\": \"Rent Received - January 2025\",\n" +
                            "      \"transaction_type\": \"payment\",\n" +
                            "      \"category\": \"rent\",\n" +
                            "      \"property_reference\": \"Apartment F - Knighton Hayes\",\n" +
                            "      \"customer_reference\": \"Riaz\",\n" +
                            "      \"payment_source\": \"OLD_ACCOUNT\"\n" +
                            "    }\n" +
                            "  ]\n" +
                            "}";

        // Tiered examples for different user levels
        examples.put("csv_example_minimal", csvExampleMinimal);
        examples.put("csv_example_recommended", csvExampleRecommended);
        examples.put("csv_example_full", csvExampleFull);

        // Backward compatibility
        examples.put("csv_example", csvExample);

        examples.put("json_example", jsonExample);

        // Field documentation - ALWAYS include ALL fields in CSV header, leave blank where no data
        examples.put("csv_fields_required", List.of(
            "transaction_date - Date of transaction (REQUIRED)",
            "amount - Transaction amount in decimal format (REQUIRED)"
        ));
        examples.put("csv_fields_recommended", List.of(
            "lease_reference - HIGHLY RECOMMENDED! Links to specific lease (e.g., LEASE-BH-F1-2025)",
            "category - rent/maintenance/commission (helps with auto-matching)",
            "property_reference - Property name/address (fuzzy matched)",
            "customer_reference - Customer name/email (fuzzy matched)"
        ));
        examples.put("csv_fields_optional", List.of(
            "description - Transaction description (auto-generated if blank)",
            "transaction_type - payment/invoice/expense/fee/maintenance/adjustment/deposit/withdrawal (inferred if blank)",
            "subcategory - Additional categorization",
            "beneficiary_type - beneficiary/beneficiary_payment/contractor (for balance tracking)",
            "incoming_transaction_amount - Original payment amount before splits (triggers auto-split logic)",
            "bank_reference - Bank transaction reference",
            "payment_method - Bank Transfer/Cash/Cheque etc.",
            "counterparty_name - Who sent/received payment (used in auto-description)",
            "notes - Additional notes",
            "payment_source - OLD_ACCOUNT/PAYPROP/BOTH"
        ));
        examples.put("csv_fields_payprop", List.of(
            "payprop_property_id - PayProp property ID for enrichment (e.g., PPR_12345)",
            "payprop_tenant_id - PayProp tenant ID for enrichment (e.g., PPT_67890)",
            "payprop_beneficiary_id - PayProp beneficiary ID for enrichment",
            "payprop_transaction_id - PayProp transaction ID for deduplication"
        ));
        examples.put("transaction_types", List.of(
            "invoice", "payment", "fee", "expense", "maintenance", "adjustment", "deposit", "withdrawal"
        ));
        examples.put("beneficiary_types", List.of(
            "beneficiary (owner allocation - increases balance)",
            "beneficiary_payment (payment to owner - decreases balance)",
            "contractor (payment to contractor)"
        ));
        examples.put("payment_sources", List.of(
            "OLD_ACCOUNT", "PAYPROP", "BOTH"
        ));
        examples.put("date_formats", List.of(
            "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "yyyy/MM/dd"
        ));
        examples.put("endpoints", Map.of(
            "csv_file_upload", "/employee/transaction/import/csv",
            "csv_paste", "/employee/transaction/import/csv-string",
            "json_file_upload", "/employee/transaction/import/json",
            "json_paste", "/employee/transaction/import/json-string"
        ));

        return ResponseEntity.ok(examples);
    }

    /**
     * Download simple CSV template
     */
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        // Simple 7-column template (recommended fields)
        String template = "transaction_date,amount,description,category,property_reference,customer_reference,lease_reference\n" +
                         "2025-05-06,795.00,Rent - May 2025,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO,LEASE-BH-F1-2025\n" +
                         "2025-05-06,-119.25,Management fee,commission,FLAT 1 - 3 WEST GATE,,\n" +
                         "2025-05-15,740.00,Rent - May 2025,rent,FLAT 2 - 3 WEST GATE,MR M K J AL BAYAT,LEASE-BH-F2-2025\n";

        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=transaction_import_template.csv")
            .header("Content-Type", "text/csv")
            .body(template.getBytes());
    }

    // ===== HUMAN VERIFICATION WORKFLOW =====

    /**
     * Stage 1: Validate CSV and create review queue
     * Returns detailed analysis without importing
     */
    @PostMapping("/import/review-validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateForReview(
            @RequestParam("csvData") String csvData,
            @RequestParam(value = "batchId", required = false) String batchId,
            @RequestParam(value = "paymentSourceId", required = false) Long paymentSourceId,
            Authentication authentication) {

        log.info("═══════════════════════════════════════════════════════════");
        log.info("🚀 [REVIEW-ENDPOINT] POST /import/review-validate called");
        log.info("User: {}", authentication != null ? authentication.getName() : "anonymous");
        log.info("CSV data length: {} characters", csvData != null ? csvData.length() : 0);
        log.info("Batch ID: {}", batchId);
        log.info("Payment Source ID: {}", paymentSourceId);
        log.info("═══════════════════════════════════════════════════════════");

        Map<String, Object> response = new HashMap<>();

        try {

            // Validate input
            if (csvData == null || csvData.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "CSV data is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Generate batch ID if not provided
            if (batchId == null || batchId.trim().isEmpty()) {
                batchId = "HIST_CSV_" + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            }

            // Validate and create review queue
            log.info("📞 [REVIEW-ENDPOINT] Calling service.validateForReview()...");
            HistoricalTransactionImportService.ReviewQueue queue = importService.validateForReview(csvData, batchId, paymentSourceId);
            log.info("📞 [REVIEW-ENDPOINT] Service returned queue with {} reviews", queue.getReviews().size());

            // Build response
            response.put("success", true);
            response.put("batchId", queue.getBatchId());
            response.put("totalRows", queue.getTotalRows());
            response.put("perfectMatches", queue.getPerfectMatches());
            response.put("needsReview", queue.getNeedsReview());
            response.put("hasIssues", queue.getHasIssues());
            response.put("reviews", queue.getReviews());

            log.info("✅ [REVIEW-ENDPOINT] Review validation complete: {} total, {} perfect, {} needs review, {} issues",
                queue.getTotalRows(), queue.getPerfectMatches(), queue.getNeedsReview(), queue.getHasIssues());
            log.info("📤 [REVIEW-ENDPOINT] Returning response with {} reviews", queue.getReviews().size());
            log.info("═══════════════════════════════════════════════════════════");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ [REVIEW-ENDPOINT] Review validation failed: {}", e.getMessage(), e);
            log.error("Stack trace:", e);
            log.info("═══════════════════════════════════════════════════════════");
            response.put("success", false);
            response.put("error", "Validation failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Stage 2: Show review interface
     * Displays the review page with validated data
     */
    @GetMapping("/import/review")
    public String showReviewPage(
            @RequestParam(value = "batchId", required = false) String batchId,
            Model model,
            Authentication authentication) {

        try {
            log.info("🔍 Loading review page for batchId: {}", batchId);

            model.addAttribute("batchId", batchId);
            model.addAttribute("pageTitle", "Review Import Data");

            return "transaction/import-review";

        } catch (Exception e) {
            log.error("❌ Error loading review page: {}", e.getMessage(), e);
            model.addAttribute("error", "Error loading review page: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Stage 3: Process confirmed import with user selections
     * Imports only after human verification
     */
    @PostMapping("/import/review-confirm")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> confirmReviewedImport(
            @RequestBody Map<String, Object> reviewData,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("🔍 Processing confirmed import with review data");

            // Extract review data
            String batchId = (String) reviewData.get("batchId");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> reviews = (List<Map<String, Object>>) reviewData.get("reviews");

            if (batchId == null || reviews == null || reviews.isEmpty()) {
                response.put("success", false);
                response.put("error", "Invalid review data");
                return ResponseEntity.badRequest().body(response);
            }

            log.info("📋 Confirmed import for batch {}: {} transactions", batchId, reviews.size());

            // Convert Map list to TransactionReview list
            List<HistoricalTransactionImportService.TransactionReview> reviewList = convertToReviewList(reviews);

            // Process confirmed import
            HistoricalTransactionImportService.ImportResult result = importService.processConfirmedImport(batchId, reviewList);

            if (result.isSuccess()) {
                response.put("success", true);
                response.put("message", String.format("Successfully imported %d transactions", result.getSuccessfulImports()));
                response.put("batchId", batchId);
                response.put("imported", result.getSuccessfulImports());
                response.put("skipped", result.getSkippedDuplicates());
                response.put("errors", result.getErrors());

                log.info("✅ Confirmed import successful: {} imported, {} skipped",
                    result.getSuccessfulImports(), result.getSkippedDuplicates());
            } else {
                response.put("success", false);
                response.put("error", "Import completed with errors");
                response.put("imported", result.getSuccessfulImports());
                response.put("errors", result.getErrors());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Confirmed import failed: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Import failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Create new property from review interface
     */
    @PostMapping("/import/create-property")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createProperty(
            @RequestBody Map<String, Object> propertyData,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("🏠 Creating new property from review interface");

            String propertyName = (String) propertyData.get("propertyName");
            if (propertyName == null || propertyName.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Property name is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Create new property
            Property property = new Property();
            property.setPropertyName(propertyName);
            property.setAddressLine1((String) propertyData.get("addressLine1"));
            property.setPostcode((String) propertyData.get("postcode"));
            property.setIsArchived("N");
            property.setCreatedAt(java.time.LocalDateTime.now());

            Property saved = propertyService.save(property);

            response.put("success", true);
            response.put("propertyId", saved.getId());
            response.put("propertyName", saved.getPropertyName());

            log.info("✅ Created property: {} (ID: {})", saved.getPropertyName(), saved.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to create property: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Failed to create property: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Create new customer from review interface
     */
    @PostMapping("/import/create-customer")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createCustomer(
            @RequestBody Map<String, Object> customerData,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("👤 Creating new customer from review interface");

            String accountType = (String) customerData.get("accountType");
            String customerType = (String) customerData.get("customerType");
            String firstName = (String) customerData.get("firstName");
            String lastName = (String) customerData.get("lastName");
            String businessName = (String) customerData.get("businessName");
            String email = (String) customerData.get("email");
            String phone = (String) customerData.get("phone");
            String country = (String) customerData.get("country");

            // Validation based on account type
            if ("business".equals(accountType)) {
                if (businessName == null || businessName.trim().isEmpty()) {
                    response.put("success", false);
                    response.put("error", "Business name is required for business accounts");
                    return ResponseEntity.badRequest().body(response);
                }
            } else {
                // Individual account
                if (firstName == null || firstName.trim().isEmpty()) {
                    response.put("success", false);
                    response.put("error", "First name is required for individual accounts");
                    return ResponseEntity.badRequest().body(response);
                }
            }

            // Validate required fields
            if (email == null || email.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Email is required");
                return ResponseEntity.badRequest().body(response);
            }
            if (phone == null || phone.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Phone is required");
                return ResponseEntity.badRequest().body(response);
            }
            if (country == null || country.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Country is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Get current user - required for customer creation
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
            User user = userService.findById(userId);

            if (user == null) {
                response.put("success", false);
                response.put("error", "Unable to identify current user");
                return ResponseEntity.status(401).body(response);
            }

            // Create new customer
            Customer customer = new Customer();

            // Set user relationship - REQUIRED
            customer.setUser(user);

            // Set account type
            if ("business".equals(accountType)) {
                customer.setAccountType(AccountType.business);
                customer.setBusinessName(businessName);
                customer.setName(businessName); // Use business name as the display name
            } else {
                customer.setAccountType(AccountType.individual);
                customer.setFirstName(firstName);
                customer.setLastName(lastName != null ? lastName : "");
                customer.setName(firstName + " " + (lastName != null ? lastName : ""));
            }

            // Set customer type flags
            if ("property_owner".equals(customerType)) {
                customer.setIsPropertyOwner(true);
            } else if ("tenant".equals(customerType)) {
                customer.setIsTenant(true);
            } else if ("contractor".equals(customerType)) {
                customer.setIsContractor(true);
            }

            // Set contact information
            customer.setEmail(email);
            customer.setPhone(phone);
            customer.setMobileNumber(phone);
            customer.setCountry(country);

            // Set optional address fields
            customer.setAddressLine1((String) customerData.get("address"));
            customer.setCity((String) customerData.get("city"));
            customer.setPostcode((String) customerData.get("postcode"));

            customer.setCreatedAt(java.time.LocalDateTime.now());

            Customer saved = customerService.save(customer);

            String fullName = "business".equals(accountType) ?
                saved.getBusinessName() :
                (saved.getFirstName() + " " + saved.getLastName());

            response.put("success", true);
            response.put("customerId", saved.getCustomerId());
            response.put("fullName", fullName);

            log.info("✅ Created {} customer: {} (ID: {})", accountType, fullName, saved.getCustomerId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to create customer: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Failed to create customer: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Convert Map list from JSON to TransactionReview list
     */
    private List<HistoricalTransactionImportService.TransactionReview> convertToReviewList(List<Map<String, Object>> maps) {
        List<HistoricalTransactionImportService.TransactionReview> reviews = new ArrayList<>();

        for (Map<String, Object> map : maps) {
            int lineNumber = ((Number) map.get("lineNumber")).intValue();
            String csvLine = (String) map.get("csvLine");

            HistoricalTransactionImportService.TransactionReview review =
                new HistoricalTransactionImportService.TransactionReview(lineNumber, csvLine);

            // Set status
            String statusStr = (String) map.get("status");
            if (statusStr != null) {
                review.setStatus(HistoricalTransactionImportService.ReviewStatus.valueOf(statusStr));
            }

            // Set user selections
            if (map.get("selectedPropertyId") != null) {
                review.setSelectedPropertyId(((Number) map.get("selectedPropertyId")).longValue());
            }

            if (map.get("selectedCustomerId") != null) {
                review.setSelectedCustomerId(((Number) map.get("selectedCustomerId")).longValue());
            }

            if (map.get("selectedPaymentSourceId") != null) {
                review.setSelectedPaymentSourceId(((Number) map.get("selectedPaymentSourceId")).longValue());
            }

            if (map.get("skipDuplicate") != null) {
                review.setSkipDuplicate((Boolean) map.get("skipDuplicate"));
            }

            if (map.get("userNote") != null) {
                review.setUserNote((String) map.get("userNote"));
            }

            // Set error message
            if (map.get("errorMessage") != null) {
                review.setErrorMessage((String) map.get("errorMessage"));
            }

            // Set parsed data
            @SuppressWarnings("unchecked")
            Map<String, Object> parsedData = (Map<String, Object>) map.get("parsedData");
            if (parsedData != null) {
                // Convert parsed data types
                if (parsedData.get("parsedDate") != null) {
                    Object dateObj = parsedData.get("parsedDate");
                    if (dateObj instanceof String) {
                        parsedData.put("parsedDate", java.time.LocalDate.parse((String) dateObj));
                    } else if (dateObj instanceof List) {
                        // Handle array format [year, month, day]
                        @SuppressWarnings("unchecked")
                        List<Integer> dateArr = (List<Integer>) dateObj;
                        parsedData.put("parsedDate", java.time.LocalDate.of(dateArr.get(0), dateArr.get(1), dateArr.get(2)));
                    }
                }

                if (parsedData.get("parsedAmount") != null) {
                    Object amountObj = parsedData.get("parsedAmount");
                    if (amountObj instanceof String) {
                        parsedData.put("parsedAmount", new java.math.BigDecimal((String) amountObj));
                    } else if (amountObj instanceof Number) {
                        parsedData.put("parsedAmount", new java.math.BigDecimal(amountObj.toString()));
                    }
                }

                if (parsedData.get("parsedType") != null) {
                    Object typeObj = parsedData.get("parsedType");
                    if (typeObj instanceof String) {
                        parsedData.put("parsedType", HistoricalTransaction.TransactionType.valueOf((String) typeObj));
                    }
                }

                review.getParsedData().putAll(parsedData);
            }

            // Handle user overrides from review UI (transaction type and description)
            @SuppressWarnings("unchecked")
            Map<String, Object> overrides = (Map<String, Object>) map.get("overrides");
            if (overrides != null) {
                // Override transaction type if user changed it
                if (overrides.get("transactionType") != null) {
                    String typeStr = (String) overrides.get("transactionType");
                    HistoricalTransaction.TransactionType overriddenType =
                        HistoricalTransaction.TransactionType.valueOf(typeStr);
                    review.getParsedData().put("parsedType", overriddenType);
                    log.debug("✏️ User override: transaction type = {}", typeStr);
                }

                // Override description if user changed it
                if (overrides.get("description") != null) {
                    String desc = (String) overrides.get("description");
                    review.getParsedData().put("description", desc);
                    log.debug("✏️ User override: description = {}", desc);
                }
            }

            // Set duplicate info if present
            @SuppressWarnings("unchecked")
            Map<String, Object> duplicateInfo = (Map<String, Object>) map.get("duplicateInfo");
            if (duplicateInfo != null) {
                // Create a dummy transaction for DuplicateInfo
                HistoricalTransaction dummyTxn = new HistoricalTransaction();
                if (duplicateInfo.get("transactionDate") != null) {
                    Object dateObj = duplicateInfo.get("transactionDate");
                    if (dateObj instanceof String) {
                        dummyTxn.setTransactionDate(java.time.LocalDate.parse((String) dateObj));
                    } else if (dateObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Integer> dateArr = (List<Integer>) dateObj;
                        dummyTxn.setTransactionDate(java.time.LocalDate.of(dateArr.get(0), dateArr.get(1), dateArr.get(2)));
                    }
                }
                if (duplicateInfo.get("amount") != null) {
                    Object amountObj = duplicateInfo.get("amount");
                    if (amountObj instanceof Number) {
                        dummyTxn.setAmount(new java.math.BigDecimal(amountObj.toString()));
                    }
                }
                if (duplicateInfo.get("description") != null) {
                    dummyTxn.setDescription((String) duplicateInfo.get("description"));
                }
                if (duplicateInfo.get("batchId") != null) {
                    dummyTxn.setImportBatchId((String) duplicateInfo.get("batchId"));
                }

                review.setDuplicateInfo(new HistoricalTransactionImportService.DuplicateInfo(dummyTxn));
            }

            reviews.add(review);
        }

        return reviews;
    }
}