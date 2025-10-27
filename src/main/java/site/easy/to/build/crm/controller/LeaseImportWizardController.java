package site.easy.to.build.crm.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.service.lease.LeaseImportService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerPropertyAssignmentRepository assignmentRepository;

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
    public ResponseEntity<?> searchProperties(@RequestParam(required = false) String q) {
        log.info("🔍 Searching properties for: {}", q);

        try {
            List<Property> allProperties = propertyService.findAll();

            // If no query, return all properties (for browse mode)
            if (q == null || q.trim().isEmpty()) {
                List<PropertyMatch> allMatches = allProperties.stream()
                    .map(property -> new PropertyMatch(property, 100))
                    .sorted((a, b) -> a.propertyName.compareToIgnoreCase(b.propertyName))
                    .collect(Collectors.toList());

                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "matches", allMatches,
                    "total", allProperties.size()
                ));
            }

            // Fuzzy match properties
            List<PropertyMatch> matches = allProperties.stream()
                .map(property -> {
                    int score = fuzzyMatchScore(q.toLowerCase(), property.getPropertyName().toLowerCase());
                    return new PropertyMatch(property, score);
                })
                .filter(match -> match.score > 30) // Only show matches above 30% similarity
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .limit(20) // Show more results
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

            // If no search criteria, return all customers (browse mode)
            if ((name == null || name.trim().isEmpty()) && (email == null || email.trim().isEmpty())) {
                List<CustomerMatch> allMatches = allCustomers.stream()
                    .map(customer -> new CustomerMatch(customer, 100))
                    .sorted((a, b) -> a.name.compareToIgnoreCase(b.name))
                    .collect(Collectors.toList());

                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "matches", allMatches,
                    "total", allCustomers.size()
                ));
            }

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

                if (score > 30) { // Only show decent matches (lowered from 40 to catch partial name matches)
                    matches.add(new CustomerMatch(customer, score));
                }
            }

            // Sort by score descending
            matches.sort((a, b) -> Integer.compare(b.score, a.score));

            return ResponseEntity.ok(Map.of(
                "success", true,
                "matches", matches.stream().limit(20).collect(Collectors.toList())
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
     * Check for duplicate leases before import
     * POST /employee/lease/import-wizard/check-duplicates
     */
    @PostMapping("/check-duplicates")
    @ResponseBody
    public ResponseEntity<?> checkDuplicates(@RequestBody Map<String, Object> request) {
        log.info("🔍 Checking for duplicate leases");

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> mappedLeases = (List<Map<String, Object>>) request.get("leases");

            if (mappedLeases == null || mappedLeases.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "No leases to check"
                ));
            }

            List<Map<String, Object>> duplicateReport = new ArrayList<>();

            for (Map<String, Object> leaseData : mappedLeases) {
                String leaseReference = (String) leaseData.get("leaseReference");
                Long propertyId = ((Number) leaseData.get("propertyId")).longValue();
                Long customerId = ((Number) leaseData.get("customerId")).longValue();
                String startDateStr = (String) leaseData.get("startDate");
                String endDateStr = (String) leaseData.get("endDate");

                Map<String, Object> duplicateInfo = new HashMap<>();
                duplicateInfo.put("leaseReference", leaseReference);
                duplicateInfo.put("isDuplicate", false);
                duplicateInfo.put("duplicateType", null);
                duplicateInfo.put("existingLeaseId", null);
                duplicateInfo.put("existingLeaseDetails", null);
                duplicateInfo.put("recommendation", "CREATE");

                // Check 1: Exact duplicate by lease reference
                if (leaseReference != null && !leaseReference.isEmpty()) {
                    Optional<Invoice> existingByReference = invoiceRepository.findByLeaseReference(leaseReference);
                    if (existingByReference.isPresent()) {
                        Invoice existing = existingByReference.get();
                        duplicateInfo.put("isDuplicate", true);
                        duplicateInfo.put("duplicateType", "EXACT_REFERENCE");
                        duplicateInfo.put("existingLeaseId", existing.getId());
                        duplicateInfo.put("existingLeaseDetails", formatLeaseDetails(existing));
                        duplicateInfo.put("recommendation", "SKIP");
                        duplicateReport.add(duplicateInfo);
                        continue; // Skip other checks if exact match found
                    }
                }

                // Check 2: Logical duplicate (same property, customer, and period)
                Property property = propertyService.findById(propertyId);
                Customer customer = customerService.findByCustomerId(customerId);

                if (property != null && customer != null) {
                    LocalDate startDate = startDateStr != null && !startDateStr.isEmpty()
                        ? LocalDate.parse(startDateStr) : null;
                    LocalDate endDate = endDateStr != null && !endDateStr.isEmpty()
                        ? LocalDate.parse(endDateStr) : null;

                    if (startDate != null) {
                        List<Invoice> matchingLeases = invoiceRepository.findByPropertyAndCustomerAndPeriod(
                            property, customer, startDate, endDate);

                        if (!matchingLeases.isEmpty()) {
                            Invoice existing = matchingLeases.get(0);
                            duplicateInfo.put("isDuplicate", true);
                            duplicateInfo.put("duplicateType", "LOGICAL_DUPLICATE");
                            duplicateInfo.put("existingLeaseId", existing.getId());
                            duplicateInfo.put("existingLeaseDetails", formatLeaseDetails(existing));
                            duplicateInfo.put("recommendation", "SKIP");
                            duplicateReport.add(duplicateInfo);
                            continue;
                        }

                        // Check 3: Overlapping active leases
                        List<Invoice> allLeasesForPair = invoiceRepository
                            .findByPropertyAndCustomerOrderByStartDateDesc(property, customer);

                        for (Invoice existingLease : allLeasesForPair) {
                            if (existingLease.getDeletedAt() != null || !existingLease.getIsActive()) {
                                continue; // Skip deleted or inactive leases
                            }

                            LocalDate existingStart = existingLease.getStartDate();
                            LocalDate existingEnd = existingLease.getEndDate();

                            // Check for overlap
                            boolean hasOverlap = false;
                            if (endDate == null && existingEnd == null) {
                                hasOverlap = true; // Both open-ended
                            } else if (endDate == null) {
                                hasOverlap = startDate.isBefore(existingEnd) || startDate.isEqual(existingEnd);
                            } else if (existingEnd == null) {
                                hasOverlap = endDate.isAfter(existingStart) || endDate.isEqual(existingStart);
                            } else {
                                hasOverlap = !(endDate.isBefore(existingStart) || startDate.isAfter(existingEnd));
                            }

                            if (hasOverlap) {
                                duplicateInfo.put("isDuplicate", true);
                                duplicateInfo.put("duplicateType", "OVERLAPPING");
                                duplicateInfo.put("existingLeaseId", existingLease.getId());
                                duplicateInfo.put("existingLeaseDetails", formatLeaseDetails(existingLease));
                                duplicateInfo.put("recommendation", "REVIEW");
                                duplicateReport.add(duplicateInfo);
                                break; // Found overlap, no need to check more
                            }
                        }
                    }
                }

                // If no duplicates found, still add to report as CREATE
                if (!duplicateInfo.get("isDuplicate").equals(true)) {
                    duplicateReport.add(duplicateInfo);
                }
            }

            long duplicateCount = duplicateReport.stream()
                .filter(info -> (Boolean) info.get("isDuplicate"))
                .count();

            return ResponseEntity.ok(Map.of(
                "success", true,
                "totalLeases", mappedLeases.size(),
                "duplicateCount", duplicateCount,
                "duplicates", duplicateReport
            ));

        } catch (Exception e) {
            log.error("❌ Failed to check duplicates", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Failed to check duplicates: " + e.getMessage()
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
            int replacedCount = 0;
            int skippedCount = 0;
            List<String> errors = new ArrayList<>();

            for (Map<String, Object> leaseData : mappedLeases) {
                try {
                    // Extract and validate data
                    Long propertyId = ((Number) leaseData.get("propertyId")).longValue();
                    Long customerId = ((Number) leaseData.get("customerId")).longValue();
                    String leaseReference = (String) leaseData.get("leaseReference");
                    String startDateStr = (String) leaseData.get("startDate");
                    String endDateStr = (String) leaseData.get("endDate");

                    // Validate and parse rent amount
                    String rentAmountStr = leaseData.get("rentAmount") != null
                        ? leaseData.get("rentAmount").toString().trim()
                        : "";
                    if (rentAmountStr.isEmpty()) {
                        throw new IllegalArgumentException("Rent amount is required");
                    }

                    // Remove currency symbols and commas from amount
                    rentAmountStr = rentAmountStr.replaceAll("[£$€,]", "");

                    if (rentAmountStr.isEmpty()) {
                        throw new IllegalArgumentException("Rent amount is required (empty after removing currency symbols)");
                    }

                    BigDecimal rentAmount = new BigDecimal(rentAmountStr);

                    if (rentAmount.compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("Rent amount cannot be negative, got: " + rentAmount);
                    }

                    // Validate and parse payment day
                    Object paymentDayObj = leaseData.get("paymentDay");
                    if (paymentDayObj == null) {
                        throw new IllegalArgumentException("Payment day is required");
                    }
                    Integer paymentDay = (paymentDayObj instanceof Number)
                        ? ((Number) paymentDayObj).intValue()
                        : Integer.parseInt(paymentDayObj.toString().trim());

                    // Extract action and existingLeaseId (from frontend duplicate handling)
                    String action = leaseData.containsKey("action")
                        ? (String) leaseData.get("action")
                        : "IMPORT";

                    Long existingLeaseId = leaseData.containsKey("existingLeaseId")
                        ? ((Number) leaseData.get("existingLeaseId")).longValue()
                        : null;

                    // Handle SKIP action (though frontend already filters these)
                    if ("SKIP".equals(action)) {
                        log.info("⏭️ Skipping lease: {}", leaseReference);
                        skippedCount++;
                        continue;
                    }

                    // Handle REPLACE action - delete existing lease first
                    if ("REPLACE".equals(action) && existingLeaseId != null) {
                        Optional<Invoice> existingLease = invoiceRepository.findById(existingLeaseId);
                        if (existingLease.isPresent()) {
                            // Soft delete the existing lease
                            Invoice toDelete = existingLease.get();
                            toDelete.setDeletedAt(LocalDateTime.now());
                            toDelete.setIsActive(false);
                            // Change lease_reference to avoid UNIQUE constraint conflict
                            String oldReference = toDelete.getLeaseReference();
                            toDelete.setLeaseReference(oldReference + "_REPLACED_" + System.currentTimeMillis());
                            invoiceRepository.save(toDelete);

                            log.info("🔄 Replaced existing lease ID: {} (old ref: {}) with new lease: {}",
                                    existingLeaseId, oldReference, leaseReference);
                            replacedCount++;
                        } else {
                            log.warn("⚠️ Could not find existing lease ID {} to replace, creating new instead",
                                    existingLeaseId);
                        }
                    }

                    // Create new lease (for both IMPORT and REPLACE actions)
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
            response.put("replacedCount", replacedCount);
            response.put("skippedCount", skippedCount);

            if (!errors.isEmpty()) {
                response.put("errors", errors);
            }

            log.info("✅ Batch import complete - {} success, {} replaced, {} skipped, {} failed",
                    successCount, replacedCount, skippedCount, failedCount);

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

                // Skip rows with missing required fields
                if (row.leaseStartDate.isEmpty()) {
                    log.warn("Skipping row {} - missing lease start date", rowNumber);
                    continue;
                }

                if (row.rentAmount.isEmpty()) {
                    log.warn("Skipping row {} - missing rent amount", rowNumber);
                    continue;
                }

                if (row.paymentDay.isEmpty()) {
                    log.warn("Skipping row {} - missing payment day", rowNumber);
                    continue;
                }

                if (row.leaseReference.isEmpty()) {
                    log.warn("Skipping row {} - missing lease reference", rowNumber);
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
     * Improved fuzzy matching algorithm with position-aware scoring
     * Returns similarity score 0-100
     */
    private int fuzzyMatchScore(String query, String target) {
        // Exact match - highest priority
        if (query.equals(target)) return 100;

        // Case-insensitive exact match
        if (query.equalsIgnoreCase(target)) return 99;

        // Target contains full query
        if (target.contains(query)) return 90;

        // Tokenize by spaces and special characters
        String[] queryWords = query.split("[\\s\\-,]+");
        String[] targetWords = target.split("[\\s\\-,]+");

        if (queryWords.length == 0) return 0;

        int totalScore = 0;
        int matchedWords = 0;

        // Check each query word against target words
        for (int i = 0; i < queryWords.length; i++) {
            String qWord = queryWords[i];
            if (qWord.isEmpty()) continue;

            boolean foundMatch = false;
            int bestWordScore = 0;

            for (int j = 0; j < targetWords.length; j++) {
                String tWord = targetWords[j];
                if (tWord.isEmpty()) continue;

                int wordScore = 0;

                // Exact word match
                if (qWord.equalsIgnoreCase(tWord)) {
                    wordScore = 100;
                    // Bonus for matching at same position (important for unit numbers)
                    if (i == j) {
                        wordScore = 120;
                    }
                }
                // Contains match (but penalize partial matches)
                else if (tWord.toLowerCase().contains(qWord.toLowerCase())) {
                    wordScore = 50;
                }
                else if (qWord.toLowerCase().contains(tWord.toLowerCase())) {
                    wordScore = 40;
                }

                if (wordScore > bestWordScore) {
                    bestWordScore = wordScore;
                }
            }

            if (bestWordScore > 0) {
                matchedWords++;
                totalScore += bestWordScore;

                // Give extra weight to first 2 words (usually the unit identifier)
                if (i < 2) {
                    totalScore += bestWordScore / 2;
                }
            }
        }

        // Calculate final score
        if (matchedWords == 0) return 0;

        // Average score with bonus for matching all words
        int avgScore = totalScore / queryWords.length;
        if (matchedWords == queryWords.length) {
            avgScore = (int)(avgScore * 1.1); // 10% bonus for all words matched
        }

        return Math.min(avgScore, 100);
    }

    /**
     * Create lease directly as Invoice entity
     */
    private void createLeaseDirectly(Long propertyId, Long customerId, String leaseReference,
                                    String startDateStr, String endDateStr, BigDecimal rentAmount,
                                    Integer paymentDay, User createdBy) {

        // Load property and customer
        Property property = propertyService.findById(propertyId);
        if (property == null) {
            throw new IllegalArgumentException("Property not found: " + propertyId);
        }

        Customer customer = customerService.findByCustomerId(customerId);
        if (customer == null) {
            throw new IllegalArgumentException("Customer not found: " + customerId);
        }

        // Parse dates
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = (endDateStr != null && !endDateStr.isEmpty())
            ? LocalDate.parse(endDateStr)
            : null;

        // Create Invoice entity (representing the lease)
        Invoice lease = new Invoice();

        // Set relationships
        lease.setProperty(property);
        lease.setCustomer(customer);
        lease.setCreatedByUser(createdBy);

        // Set financial details
        lease.setAmount(rentAmount);
        lease.setCategoryId("rent"); // Standard rent category
        lease.setCategoryName("Rent");

        // Set frequency and payment details
        lease.setFrequency(Invoice.InvoiceFrequency.monthly); // Monthly
        lease.setPaymentDay(paymentDay);

        // Set date range
        lease.setStartDate(startDate);
        lease.setEndDate(endDate);

        // Set lease reference
        if (leaseReference != null && !leaseReference.isEmpty()) {
            lease.setLeaseReference(leaseReference);
        }

        // Set description
        String description = String.format("Monthly rent for %s - %s",
            property.getPropertyName(),
            customer.getName());
        lease.setDescription(description);

        // Set status flags
        lease.setIsActive(true);
        lease.setIsDebitOrder(false);

        // Set sync status (local only for now)
        lease.setSyncStatus(Invoice.SyncStatus.pending);

        // Set invoice type
        lease.setInvoiceType("lease");

        // Save lease to database
        Invoice savedLease = invoiceRepository.save(lease);

        // Create corresponding tenant assignment
        CustomerPropertyAssignment assignment = new CustomerPropertyAssignment();
        assignment.setCustomer(customer);
        assignment.setProperty(property);
        assignment.setAssignmentType(AssignmentType.TENANT);
        assignment.setStartDate(startDate);
        assignment.setEndDate(endDate);
        assignment.setCreatedAt(LocalDateTime.now());
        assignment.setPaypropInvoiceId(savedLease.getId().toString());
        assignment.setSyncStatus("LOCAL_ONLY");

        assignmentRepository.save(assignment);

        log.info("✅ Created lease: {} (ID: {}) and tenant assignment for property {} and customer {}",
                leaseReference != null ? leaseReference : "Generated",
                savedLease.getId(),
                property.getPropertyName(),
                customer.getName());
    }

    /**
     * Format existing lease details for duplicate report
     */
    private Map<String, String> formatLeaseDetails(Invoice lease) {
        Map<String, String> details = new HashMap<>();

        details.put("leaseReference", lease.getLeaseReference() != null ? lease.getLeaseReference() : "N/A");
        details.put("property", lease.getProperty() != null ? lease.getProperty().getPropertyName() : "N/A");
        details.put("customer", lease.getCustomer() != null ? lease.getCustomer().getName() : "N/A");
        details.put("startDate", lease.getStartDate() != null ? lease.getStartDate().toString() : "N/A");
        details.put("endDate", lease.getEndDate() != null ? lease.getEndDate().toString() : "Open-ended");
        details.put("amount", lease.getAmount() != null ? lease.getAmount().toString() : "N/A");
        details.put("status", lease.getIsActive() ? "Active" : "Inactive");

        return details;
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
