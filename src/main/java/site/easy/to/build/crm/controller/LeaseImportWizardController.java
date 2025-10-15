package site.easy.to.build.crm.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.service.lease.LeaseImportService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Interactive Lease Import Wizard
 *
 * Provides a step-by-step interface for importing leases from CSV:
 * 1. Parse CSV (flexible format - supports name and/or email)
 * 2. Map properties (autocomplete + fuzzy matching)
 * 3. Map customers (recommendations + create new)
 * 4. Review all mappings
 * 5. Execute batch import
 */
@Controller
@RequestMapping("/employee/lease/import-wizard")
public class LeaseImportWizardController {

    private static final Logger log = LoggerFactory.getLogger(LeaseImportWizardController.class);

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private LeaseImportService leaseImportService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationUtils authenticationUtils;

    /**
     * Show the import wizard page
     */
    @GetMapping
    public String showImportWizard(Model model, Authentication authentication) {
        log.info("📋 Lease Import Wizard - Loading page");
        return "employee/lease/import-wizard";
    }

    /**
     * Parse CSV and return structured lease rows
     * POST /employee/lease/import-wizard/parse-csv
     */
    @PostMapping("/parse-csv")
    @ResponseBody
    public ResponseEntity<?> parseCSV(@RequestBody Map<String, String> request) {
        log.info("🔄 Parsing CSV for lease import");

        try {
            String csvContent = request.get("csvContent");
            if (csvContent == null || csvContent.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "CSV content is empty"
                ));
            }

            List<ParsedLeaseRow> parsedRows = parseCsvContent(csvContent);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalRows", parsedRows.size());
            response.put("leases", parsedRows);

            log.info("✅ Parsed {} lease rows from CSV", parsedRows.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to parse CSV", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Failed to parse CSV: " + e.getMessage()
            ));
        }
    }

    /**
     * Search for properties with fuzzy matching
     * GET /employee/lease/import-wizard/search-properties?q=flat+1
     */
    @GetMapping("/search-properties")
    @ResponseBody
    public ResponseEntity<?> searchProperties(@RequestParam String q) {
        log.info("🔍 Searching properties for: {}", q);

        try {
            List<Property> allProperties = propertyService.findAll();

            // Fuzzy match properties
            List<PropertyMatch> matches = allProperties.stream()
                .map(property -> {
                    int score = fuzzyMatchScore(q.toLowerCase(), property.getPropertyName().toLowerCase());
                    return new PropertyMatch(property, score);
                })
                .filter(match -> match.score > 30) // Only show matches above 30% similarity
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .limit(10)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "matches", matches
            ));

        } catch (Exception e) {
            log.error("❌ Failed to search properties", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Search for customers with recommendations
     * GET /employee/lease/import-wizard/search-customers?name=John&email=john@
     */
    @GetMapping("/search-customers")
    @ResponseBody
    public ResponseEntity<?> searchCustomers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email) {

        log.info("🔍 Searching customers - name: {}, email: {}", name, email);

        try {
            List<Customer> allCustomers = customerService.findAll();

            List<CustomerMatch> matches = new ArrayList<>();

            for (Customer customer : allCustomers) {
                int score = 0;

                // Email match (highest priority)
                if (email != null && !email.isEmpty() && customer.getEmail() != null) {
                    if (customer.getEmail().equalsIgnoreCase(email)) {
                        score = 100; // Perfect match
                    } else if (customer.getEmail().toLowerCase().contains(email.toLowerCase())) {
                        score = 80; // Partial match
                    }
                }

                // Name match
                if (name != null && !name.isEmpty() && customer.getName() != null) {
                    int nameScore = fuzzyMatchScore(name.toLowerCase(), customer.getName().toLowerCase());
                    score = Math.max(score, nameScore);
                }

                if (score > 40) { // Only show decent matches
                    matches.add(new CustomerMatch(customer, score));
                }
            }

            // Sort by score descending
            matches.sort((a, b) -> Integer.compare(b.score, a.score));

            return ResponseEntity.ok(Map.of(
                "success", true,
                "matches", matches.stream().limit(10).collect(Collectors.toList())
            ));

        } catch (Exception e) {
            log.error("❌ Failed to search customers", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Create a new customer during import
     * POST /employee/lease/import-wizard/create-customer
     */
    @PostMapping("/create-customer")
    @ResponseBody
    public ResponseEntity<?> createCustomer(@RequestBody Map<String, String> request) {
        log.info("➕ Creating new customer during lease import");

        try {
            String name = request.get("name");
            String email = request.get("email");
            String phone = request.get("phone");
            String customerTypeStr = request.getOrDefault("customerType", "TENANT");

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Customer name is required"
                ));
            }

            Customer customer = new Customer();
            customer.setName(name.trim());

            if (email != null && !email.trim().isEmpty()) {
                customer.setEmail(email.trim());
            }

            if (phone != null && !phone.trim().isEmpty()) {
                customer.setPhone(phone.trim());
            }

            customer.setCustomerType(CustomerType.valueOf(customerTypeStr));

            Customer savedCustomer = customerService.save(customer);

            log.info("✅ Created new customer: {} (ID: {})", savedCustomer.getName(), savedCustomer.getCustomerId());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "customer", Map.of(
                    "customerId", savedCustomer.getCustomerId(),
                    "name", savedCustomer.getName(),
                    "email", savedCustomer.getEmail() != null ? savedCustomer.getEmail() : "",
                    "customerType", savedCustomer.getCustomerType().toString()
                )
            ));

        } catch (Exception e) {
            log.error("❌ Failed to create customer", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Failed to create customer: " + e.getMessage()
            ));
        }
    }

    /**
     * Execute the batch import with all mappings
     * POST /employee/lease/import-wizard/execute-import
     */
    @PostMapping("/execute-import")
    @ResponseBody
    public ResponseEntity<?> executeImport(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        log.info("🚀 Executing batch lease import");

        try {
            // Get current user
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            if (userId <= 0) {
                return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "error", "User not authenticated"
                ));
            }

            User currentUser = userService.findById(Long.valueOf(userId));
            if (currentUser == null) {
                return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "error", "User not found"
                ));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> mappedLeases = (List<Map<String, Object>>) request.get("leases");

            if (mappedLeases == null || mappedLeases.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "No leases to import"
                ));
            }

            int successCount = 0;
            int failedCount = 0;
            List<String> errors = new ArrayList<>();

            for (Map<String, Object> leaseData : mappedLeases) {
                try {
                    // Extract and validate data
                    Long propertyId = ((Number) leaseData.get("propertyId")).longValue();
                    Long customerId = ((Number) leaseData.get("customerId")).longValue();
                    String leaseReference = (String) leaseData.get("leaseReference");
                    String startDateStr = (String) leaseData.get("startDate");
                    String endDateStr = (String) leaseData.get("endDate");
                    BigDecimal rentAmount = new BigDecimal(leaseData.get("rentAmount").toString());
                    Integer paymentDay = ((Number) leaseData.get("paymentDay")).intValue();

                    // TODO: Integrate with LeaseImportService once LeaseRow is made public
                    // For now, create lease directly
                    createLeaseDirectly(propertyId, customerId, leaseReference, startDateStr,
                                      endDateStr, rentAmount, paymentDay, currentUser);

                    successCount++;

                } catch (Exception e) {
                    failedCount++;
                    errors.add("Failed to import lease " + leaseData.get("leaseReference") + ": " + e.getMessage());
                    log.error("❌ Failed to import lease", e);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", failedCount == 0);
            response.put("totalLeases", mappedLeases.size());
            response.put("successfulImports", successCount);
            response.put("failedImports", failedCount);

            if (!errors.isEmpty()) {
                response.put("errors", errors);
            }

            log.info("✅ Batch import complete - {} success, {} failed", successCount, failedCount);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Batch import failed", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Batch import failed: " + e.getMessage()
            ));
        }
    }

    // ==================== Helper Methods ====================

    private List<ParsedLeaseRow> parseCsvContent(String csvContent) throws Exception {
        List<ParsedLeaseRow> rows = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new StringReader(csvContent));

        String headerLine = reader.readLine();
        if (headerLine == null) {
            throw new IllegalArgumentException("CSV is empty");
        }

        String[] headers = headerLine.split(",");
        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            headerMap.put(headers[i].trim().toLowerCase(), i);
        }

        String line;
        int rowNumber = 1;
        while ((line = reader.readLine()) != null) {
            rowNumber++;
            String[] values = line.split(",", -1); // -1 to keep empty trailing fields

            try {
                ParsedLeaseRow row = new ParsedLeaseRow();
                row.rowNumber = rowNumber;
                row.rawPropertyReference = getValueOrEmpty(values, headerMap, "property_reference");
                row.rawCustomerName = getValueOrEmpty(values, headerMap, "customer_name");
                row.rawCustomerEmail = getValueOrEmpty(values, headerMap, "customer_email");
                row.leaseStartDate = getValueOrEmpty(values, headerMap, "lease_start_date");
                row.leaseEndDate = getValueOrEmpty(values, headerMap, "lease_end_date");
                row.rentAmount = getValueOrEmpty(values, headerMap, "rent_amount");
                row.paymentDay = getValueOrEmpty(values, headerMap, "payment_day");
                row.leaseReference = getValueOrEmpty(values, headerMap, "lease_reference");

                // Skip rows with no customer name or email
                if (row.rawCustomerName.isEmpty() && row.rawCustomerEmail.isEmpty()) {
                    log.debug("Skipping row {} - no customer information", rowNumber);
                    continue;
                }

                // Skip rows with no property
                if (row.rawPropertyReference.isEmpty()) {
                    log.debug("Skipping row {} - no property reference", rowNumber);
                    continue;
                }

                rows.add(row);

            } catch (Exception e) {
                log.warn("Failed to parse row {}: {}", rowNumber, e.getMessage());
            }
        }

        return rows;
    }

    private String getValueOrEmpty(String[] values, Map<String, Integer> headerMap, String columnName) {
        Integer index = headerMap.get(columnName);
        if (index == null || index >= values.length) {
            return "";
        }
        return values[index].trim();
    }

    /**
     * Simple fuzzy matching algorithm (Levenshtein-inspired)
     * Returns similarity score 0-100
     */
    private int fuzzyMatchScore(String query, String target) {
        if (query.equals(target)) return 100;
        if (target.contains(query)) return 85;

        // Tokenize and check word overlap
        String[] queryWords = query.split("\\s+");
        String[] targetWords = target.split("\\s+");

        int matches = 0;
        for (String qWord : queryWords) {
            for (String tWord : targetWords) {
                if (tWord.contains(qWord) || qWord.contains(tWord)) {
                    matches++;
                    break;
                }
            }
        }

        if (queryWords.length == 0) return 0;
        return (matches * 100) / queryWords.length;
    }

    private void createLeaseDirectly(Long propertyId, Long customerId, String leaseReference,
                                    String startDateStr, String endDateStr, BigDecimal rentAmount,
                                    Integer paymentDay, User createdBy) {
        // This will be implemented using the existing Invoice entity
        // For now, placeholder - will integrate with LeaseImportService
        log.info("Creating lease: {} for property {} and customer {}", leaseReference, propertyId, customerId);
    }

    // ==================== DTOs ====================

    public static class ParsedLeaseRow {
        public int rowNumber;
        public String rawPropertyReference;
        public String rawCustomerName;
        public String rawCustomerEmail;
        public String leaseStartDate;
        public String leaseEndDate;
        public String rentAmount;
        public String paymentDay;
        public String leaseReference;

        // Getters for JSON serialization
        public int getRowNumber() { return rowNumber; }
        public String getRawPropertyReference() { return rawPropertyReference; }
        public String getRawCustomerName() { return rawCustomerName; }
        public String getRawCustomerEmail() { return rawCustomerEmail; }
        public String getLeaseStartDate() { return leaseStartDate; }
        public String getLeaseEndDate() { return leaseEndDate; }
        public String getRentAmount() { return rentAmount; }
        public String getPaymentDay() { return paymentDay; }
        public String getLeaseReference() { return leaseReference; }
    }

    public static class PropertyMatch {
        public Long propertyId;
        public String propertyName;
        public String propertyType;
        public int score;

        public PropertyMatch(Property property, int score) {
            this.propertyId = property.getId();
            this.propertyName = property.getPropertyName();
            this.propertyType = property.getPropertyType();
            this.score = score;
        }

        public Long getPropertyId() { return propertyId; }
        public String getPropertyName() { return propertyName; }
        public String getPropertyType() { return propertyType; }
        public int getScore() { return score; }
    }

    public static class CustomerMatch {
        public Long customerId;
        public String name;
        public String email;
        public String customerType;
        public int score;

        public CustomerMatch(Customer customer, int score) {
            this.customerId = customer.getCustomerId();
            this.name = customer.getName();
            this.email = customer.getEmail();
            this.customerType = customer.getCustomerType() != null ? customer.getCustomerType().toString() : "TENANT";
            this.score = score;
        }

        public Long getCustomerId() { return customerId; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getCustomerType() { return customerType; }
        public int getScore() { return score; }
    }
}
