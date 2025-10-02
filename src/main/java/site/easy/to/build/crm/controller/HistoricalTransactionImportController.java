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
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
            response.put("errors", result.getErrors());

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
     * Get import format examples
     */
    @GetMapping("/import/examples")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getImportExamples() {
        Map<String, Object> examples = new HashMap<>();

        // CSV Example
        String csvExample = "transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source\n" +
                           "2025-01-22,1125.00,\"Rent Due - January 2025\",invoice,rent,\"Apartment F - Knighton Hayes\",Riaz,,,\"Rent due on 22nd\",OLD_ACCOUNT\n" +
                           "2025-01-22,1125.00,\"Rent Received - January 2025\",payment,rent,\"Apartment F - Knighton Hayes\",Riaz,,,\"Rent received\",OLD_ACCOUNT\n" +
                           "2025-01-22,-112.50,\"Management Fee - 10%\",fee,commission,\"Apartment F - Knighton Hayes\",,,,\"10% of rent\",OLD_ACCOUNT";

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

        examples.put("csv_example", csvExample);
        examples.put("json_example", jsonExample);
        examples.put("csv_headers", List.of(
            "transaction_date (required)", "amount (required)", "description (required)",
            "transaction_type (required)", "category", "property_reference",
            "customer_reference", "bank_reference", "payment_method", "notes", "payment_source"
        ));
        examples.put("transaction_types", List.of(
            "invoice", "payment", "fee", "expense", "maintenance", "adjustment", "deposit", "withdrawal"
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
}