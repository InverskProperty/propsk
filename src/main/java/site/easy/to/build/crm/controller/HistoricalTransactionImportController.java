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
            // Get properties and customers for reference
            List<Property> properties = propertyService.findAll();
            List<Customer> customers = customerService.findAll();
            
            model.addAttribute("properties", properties);
            model.addAttribute("customers", customers);
            model.addAttribute("pageTitle", "Historical Transaction Import");
            
            return "transaction/import";
            
        } catch (Exception e) {
            log.error("Error loading import page: {}", e.getMessage());
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
     * Handle CSV file upload
     */
    @PostMapping("/import/csv")
    public String importCsvFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "batchDescription", defaultValue = "") String batchDescription,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {
        
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
     * Get import format examples
     */
    @GetMapping("/import/examples")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getImportExamples() {
        Map<String, Object> examples = new HashMap<>();
        
        // CSV Example
        String csvExample = "transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference\n" +
                           "2023-01-15,-1200.00,\"Rent payment - Main St\",payment,rent,\"123 Main St\",\"john@email.com\",TXN123456\n" +
                           "2023-02-01,50.00,\"Interest payment\",deposit,interest,,,INT789\n" +
                           "2023-02-15,-150.00,\"Maintenance repair\",payment,maintenance,\"123 Main St\",,REP456";
        
        // JSON Example
        String jsonExample = "{\n" +
                            "  \"source_description\": \"Historical bank data 2023\",\n" +
                            "  \"transactions\": [\n" +
                            "    {\n" +
                            "      \"transaction_date\": \"2023-01-15\",\n" +
                            "      \"amount\": -1200.00,\n" +
                            "      \"description\": \"Rent payment - Main St\",\n" +
                            "      \"transaction_type\": \"payment\",\n" +
                            "      \"category\": \"rent\",\n" +
                            "      \"property_reference\": \"123 Main St\",\n" +
                            "      \"customer_reference\": \"john@email.com\",\n" +
                            "      \"bank_reference\": \"TXN123456\"\n" +
                            "    },\n" +
                            "    {\n" +
                            "      \"transaction_date\": \"2023-02-01\",\n" +
                            "      \"amount\": 50.00,\n" +
                            "      \"description\": \"Interest payment\",\n" +
                            "      \"transaction_type\": \"deposit\",\n" +
                            "      \"category\": \"interest\",\n" +
                            "      \"bank_reference\": \"INT789\"\n" +
                            "    }\n" +
                            "  ]\n" +
                            "}";
        
        examples.put("csv_example", csvExample);
        examples.put("json_example", jsonExample);
        examples.put("csv_headers", List.of(
            "transaction_date (required)", "amount (required)", "description (required)", 
            "transaction_type (required)", "category", "subcategory", "property_reference", 
            "customer_reference", "bank_reference", "payment_method", "counterparty_name", 
            "source_reference", "notes"
        ));
        examples.put("transaction_types", List.of(
            "payment", "deposit", "fee", "commission", "maintenance", "utility", "insurance", "tax"
        ));
        examples.put("date_formats", List.of(
            "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "yyyy/MM/dd"
        ));
        
        return ResponseEntity.ok(examples);
    }
}