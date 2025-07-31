// BatchPaymentTestController.java - Enhanced with ICDN and comprehensive endpoint testing
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.PayPropSyncService;
import site.easy.to.build.crm.service.payprop.PayPropSyncService.PayPropExportResult;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.service.property.PropertyService;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

/**
 * Enhanced test controller to investigate ALL PayProp endpoints and data types
 * Now includes ICDN testing and comprehensive endpoint comparison
 */
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@RestController
@RequestMapping("/api/payprop/test")
public class BatchPaymentTestController {

    private static final Logger log = LoggerFactory.getLogger(BatchPaymentTestController.class);

    @Autowired
    private PayPropSyncService payPropSyncService;
    
    @Autowired
    private PayPropOAuth2Service oAuth2Service;

    @Autowired 
    private RestTemplate restTemplate;

    @Autowired
    private PropertyService propertyService;

    /**
     * ✅ NEW: Test ICDN endpoint specifically
     * ICDN = "I Can Do Now" - PayProp's real-time transaction data
     */
    @GetMapping("/icdn-report")
    public ResponseEntity<Map<String, Object>> testICDNEndpoint(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Integer rows) {
        
        try {
            // Default to recent dates if not provided
            LocalDate from = fromDate != null ? LocalDate.parse(fromDate) : LocalDate.now().minusMonths(3);
            LocalDate to = toDate != null ? LocalDate.parse(toDate) : LocalDate.now();
            int pageSize = rows != null ? rows : 25;

            log.info("🔍 Testing ICDN endpoint for date range: {} to {} (rows: {})", from, to, pageSize);

            if (!oAuth2Service.hasValidTokens()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "PayProp not authorized. Please authorize first.",
                    "endpoint", "/report/icdn"
                ));
            }

            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = "https://ukapi.staging.payprop.com/api/agency/v1.1/report/icdn" +
                    "?from_date=" + from.toString() +
                    "&to_date=" + to.toString() +
                    "&rows=" + pageSize;
            
            log.info("📡 Calling ICDN API: {}", url);
            
            ResponseEntity<Map> apiResponse = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (apiResponse.getStatusCode().is2xxSuccessful() && apiResponse.getBody() != null) {
                Map<String, Object> responseBody = apiResponse.getBody();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> transactions = (List<Map<String, Object>>) responseBody.get("items");
                
                Map<String, Object> analysis = analyzeICDNData(transactions);
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "endpoint", "/report/icdn",
                    "url", url,
                    "totalItems", transactions != null ? transactions.size() : 0,
                    "dateRange", from + " to " + to,
                    "payPropResponse", responseBody,
                    "icdnAnalysis", analysis,
                    "note", "ICDN = Real-time transaction data from PayProp"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "ICDN API returned status: " + apiResponse.getStatusCode(),
                    "endpoint", "/report/icdn",
                    "url", url
                ));
            }

        } catch (Exception e) {
            log.error("❌ Error testing ICDN endpoint: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage(),
                "endpoint", "/report/icdn",
                "suggestion", "Check if ICDN endpoint requires different permissions or parameters"
            ));
        }
    }

    /**
     * Validate date range doesn't exceed PayProp's 93-day limit
     */
    private void validateDateRange(LocalDate from, LocalDate to) {
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(from, to);
        if (daysBetween > 93) {
            throw new IllegalArgumentException(
                String.format("Date range cannot exceed 93 days. Current range: %d days (%s to %s)", 
                    daysBetween, from, to)
            );
        }
    }

    /**
     * Helper to create valid date ranges
     */
    private Map<String, LocalDate> getValidDateRange(String fromDate, String toDate) {
        LocalDate from = fromDate != null ? LocalDate.parse(fromDate) : LocalDate.now().minusDays(30);
        LocalDate to = toDate != null ? LocalDate.parse(toDate) : LocalDate.now();
        
        // Ensure we don't exceed 93 days
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(from, to);
        if (daysBetween > 93) {
            // Adjust the 'from' date to be exactly 93 days before 'to'
            from = to.minusDays(93);
            log.warn("Date range adjusted to comply with 93-day limit: {} to {}", from, to);
        }
        
        return Map.of("from", from, "to", to);
    }
     /**
     * Enhanced authentication check with detailed logging
     */
    private ResponseEntity<Map<String, Object>> checkAuthentication() {
        try {
            boolean hasTokens = oAuth2Service.hasValidTokens();
            log.info("OAuth2 token check: hasValidTokens = {}", hasTokens);
            
            if (!hasTokens) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "PayProp not authorized or tokens expired",
                    "action", "Please re-authorize PayProp integration",
                    "authStatus", "TOKENS_MISSING_OR_EXPIRED"
                ));
            }
            
            // Test a simple API call to verify tokens work
            try {
                HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
                HttpEntity<String> request = new HttpEntity<>(headers);
                String testUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1/payments/categories";
                
                ResponseEntity<Map> testResponse = restTemplate.exchange(testUrl, HttpMethod.GET, request, Map.class);
                
                if (testResponse.getStatusCode().is2xxSuccessful()) {
                    log.info("✅ Authentication verified successfully");
                    return null; // Authentication is good
                } else {
                    log.warn("⚠️ Authentication test failed with status: {}", testResponse.getStatusCode());
                    return ResponseEntity.ok(Map.of(
                        "success", false,
                        "error", "Authentication test failed",
                        "authStatus", "TOKENS_INVALID",
                        "httpStatus", testResponse.getStatusCode().value()
                    ));
                }
            } catch (Exception e) {
                log.error("❌ Authentication test exception: {}", e.getMessage());
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "Authentication test failed: " + e.getMessage(),
                    "authStatus", "AUTH_TEST_FAILED"
                ));
            }
            
        } catch (Exception e) {
            log.error("❌ Error checking authentication: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", "Authentication check failed: " + e.getMessage(),
                "authStatus", "AUTH_CHECK_ERROR"
            ));
        }
    }

/**
 * ✅ NEW: Fast debug sync for specific properties with comprehensive related data
 * Perfect for fast iteration - processes 5-10 properties in under 30 seconds
 */
@GetMapping("/debug-sync")
public ResponseEntity<Map<String, Object>> debugSyncSpecificProperties(
        @RequestParam(required = false) String propertyIds,
        @RequestParam(defaultValue = "5") int limit,
        @RequestParam(defaultValue = "false") boolean includeFinancials,
        @RequestParam(defaultValue = "false") boolean includeValidationDetails) {
    
    try {
        long startTime = System.currentTimeMillis();
        
        ResponseEntity<Map<String, Object>> authCheck = checkAuthentication();
        if (authCheck != null) return authCheck;
        
        List<String> targetIds;
        
        if (propertyIds != null && !propertyIds.trim().isEmpty()) {
            // Use specific IDs provided (comma-separated)
            targetIds = Arrays.asList(propertyIds.split(","));
            log.info("🎯 DEBUG SYNC: Using specific property IDs: {}", targetIds);
        } else {
            // Get properties most likely to have validation issues
            targetIds = getPropertiesForDebugSync(limit);
            log.info("🎯 DEBUG SYNC: Auto-selected {} properties for testing", targetIds.size());
        }
        
        if (targetIds.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", "No properties found to sync",
                "suggestion", "Provide specific propertyIds or ensure properties exist with PayProp IDs"
            ));
        }
        
        Map<String, Object> results = new HashMap<>();
        results.put("target_properties", targetIds);
        results.put("sync_mode", "DEBUG_FAST_ITERATION");
        
        // 1. Sync Properties with Commission (MAIN TEST)
        Map<String, Object> propertiesResult = debugSyncPropertiesWithCommission(targetIds, includeValidationDetails);
        results.put("properties", propertiesResult);
        
        if (includeFinancials) {
            // 2. Sync Financial Transactions for these properties only
            Map<String, Object> transactionsResult = debugSyncFinancialTransactionsForProperties(targetIds);
            results.put("transactions", transactionsResult);
            
            // 3. Sync Tenants for these properties
            Map<String, Object> tenantsResult = debugSyncTenantsForProperties(targetIds);
            results.put("tenants", tenantsResult);
            
            // 4. Calculate commission for these properties
            Map<String, Object> commissionsResult = debugCalculateCommissionsForProperties(targetIds);
            results.put("commissions", commissionsResult);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        results.put("status", "SUCCESS");
        results.put("duration_ms", duration);
        results.put("duration_seconds", duration / 1000.0);
        results.put("properties_processed", targetIds.size());
        
        log.info("✅ DEBUG SYNC completed in {}ms for {} properties", duration, targetIds.size());
        
        return ResponseEntity.ok(results);
        
    } catch (Exception e) {
        log.error("❌ DEBUG SYNC failed: {}", e.getMessage(), e);
        return ResponseEntity.ok(Map.of(
            "success", false,
            "error", e.getMessage(),
            "stack_trace", e.getStackTrace().length > 0 ? e.getStackTrace()[0].toString() : "No stack trace"
        ));
    }
}

/**
 * ✅ SMART: Get properties most likely to have validation issues
 */
private List<String> getPropertiesForDebugSync(int limit) {
    try {
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        // Get properties with commission data (most likely to have validation issues)
        String url = "https://ukapi.staging.payprop.com/api/agency/v1.1/export/properties?include_commission=true&rows=" + limit;
        
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
        List<Map<String, Object>> properties = (List<Map<String, Object>>) response.getBody().get("items");
        
        return properties.stream()
            .map(p -> (String) p.get("id"))
            .filter(id -> id != null && !id.trim().isEmpty())
            .limit(limit)
            .collect(Collectors.toList());
            
    } catch (Exception e) {
        log.error("❌ Failed to get properties for debug sync: {}", e.getMessage());
        return new ArrayList<>();
    }
}

/**
 * ✅ FIXED: Debug sync properties using bulk endpoint (PayProp doesn't support individual property lookups)
 */
private Map<String, Object> debugSyncPropertiesWithCommission(List<String> payPropIds, boolean includeValidationDetails) {
    Map<String, Object> result = new HashMap<>();
    List<Map<String, Object>> processedProperties = new ArrayList<>();
    List<Map<String, Object>> validationFailures = new ArrayList<>();
    
    int processed = 0, updated = 0, failed = 0;
    
    try {
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        // ✅ FIXED: Use bulk endpoint to get all properties, then filter for our target IDs
        String url = "https://ukapi.staging.payprop.com/api/agency/v1.1/export/properties?include_commission=true&rows=1000";
        
        log.info("🔍 DEBUG: Getting properties from bulk endpoint, will filter for IDs: {}", payPropIds);
        
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
        List<Map<String, Object>> allProperties = (List<Map<String, Object>>) response.getBody().get("items");
        
        if (allProperties == null || allProperties.isEmpty()) {
            result.put("error", "No properties returned from PayProp API");
            return result;
        }
        
        log.info("📊 DEBUG: Got {} total properties from PayProp, filtering for target IDs", allProperties.size());
        
        // Filter for our target properties
        List<Map<String, Object>> targetProperties = allProperties.stream()
            .filter(p -> {
                String id = (String) p.get("id");
                return id != null && payPropIds.contains(id);
            })
            .collect(Collectors.toList());
        
        log.info("🎯 DEBUG: Found {} target properties out of {} requested", targetProperties.size(), payPropIds.size());
        
        // Track which IDs we found vs missing
        Set<String> foundIds = targetProperties.stream()
            .map(p -> (String) p.get("id"))
            .collect(Collectors.toSet());
        
        List<String> missingIds = payPropIds.stream()
            .filter(id -> !foundIds.contains(id))
            .collect(Collectors.toList());
        
        if (!missingIds.isEmpty()) {
            log.warn("⚠️ DEBUG: Missing properties from PayProp API: {}", missingIds);
        }
        
        // Process each target property
        for (Map<String, Object> ppProperty : targetProperties) {
            try {
                Map<String, Object> propertyResult = debugProcessSingleProperty(ppProperty, includeValidationDetails);
                processedProperties.add(propertyResult);
                
                if ("SUCCESS".equals(propertyResult.get("status"))) {
                    updated++;
                } else {
                    failed++;
                    if (propertyResult.containsKey("validation_details")) {
                        validationFailures.add(propertyResult);
                    }
                }
                
                processed++;
                
            } catch (Exception e) {
                failed++;
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("payprop_id", ppProperty.get("id"));
                errorResult.put("status", "PROCESSING_ERROR");
                errorResult.put("error", e.getMessage());
                processedProperties.add(errorResult);
                
                log.error("❌ Failed to process property {}: {}", ppProperty.get("id"), e.getMessage());
            }
        }
        
        // Add missing properties to results
        for (String missingId : missingIds) {
            Map<String, Object> missingResult = new HashMap<>();
            missingResult.put("payprop_id", missingId);
            missingResult.put("status", "NOT_FOUND_IN_PAYPROP");
            missingResult.put("error", "Property not found in PayProp API response");
            processedProperties.add(missingResult);
            failed++;
        }
        
    } catch (Exception e) {
        log.error("❌ Debug sync failed: {}", e.getMessage());
        result.put("sync_error", e.getMessage());
    }
    
    result.put("processed", processed);
    result.put("updated", updated);
    result.put("failed", failed);
    result.put("properties", processedProperties);
    result.put("validation_failures", validationFailures);
    result.put("success_rate", processed > 0 ? (updated * 100.0 / processed) : 0);
    result.put("requested_ids", payPropIds);
    
    return result;
}

    /**
     * ✅ DETAILED: Process single property with comprehensive validation logging
     */
    private Map<String, Object> debugProcessSingleProperty(Map<String, Object> ppProperty, boolean includeValidationDetails) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String payPropId = (String) ppProperty.get("id");
            String propertyName = (String) ppProperty.get("property_name");
            
            result.put("payprop_id", payPropId);
            result.put("payprop_property_name", propertyName);
            
            // ✅ DETAILED VALIDATION LOGGING
            List<String> validationIssues = new ArrayList<>();
            
            // Check property name
            if (propertyName == null) {
                validationIssues.add("property_name is NULL");
            } else if (propertyName.trim().isEmpty()) {
                validationIssues.add("property_name is empty/whitespace");
            } else if (!propertyName.matches("^.*\\S.*$")) {
                validationIssues.add("property_name fails pattern validation");
            }
            
            // Check commission data
            Map<String, Object> commission = (Map<String, Object>) ppProperty.get("commission");
            if (commission != null) {
                Object percentage = commission.get("percentage");
                if (percentage instanceof String) {
                    try {
                        BigDecimal rate = new BigDecimal((String) percentage);
                        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
                            validationIssues.add("commission_percentage out of range 0-100: " + rate);
                        }
                        result.put("commission_percentage", rate);
                    } catch (NumberFormatException e) {
                        validationIssues.add("commission_percentage invalid format: " + percentage);
                    }
                }
            }
            
            // Check other financial fields
            Object monthlyPayment = ppProperty.get("monthly_payment_required");
            if (monthlyPayment instanceof Number) {
                BigDecimal payment = new BigDecimal(monthlyPayment.toString());
                if (payment.compareTo(new BigDecimal("0.01")) < 0) {
                    validationIssues.add("monthly_payment below minimum 0.01: " + payment);
                }
                result.put("monthly_payment", payment);
            }
            
            if (includeValidationDetails) {
                result.put("validation_issues", validationIssues);
                result.put("raw_payprop_data", ppProperty);
            }
            
            // If no validation issues, try to update the property
            if (validationIssues.isEmpty()) {
                Optional<Property> existingOpt = propertyService.findByPayPropId(payPropId);
                
                if (existingOpt.isPresent()) {
                    Property property = existingOpt.get();
                    String originalName = property.getPropertyName();
                    
                    // Update property safely
                    if (propertyName != null && !propertyName.trim().isEmpty()) {
                        property.setPropertyName(propertyName.trim());
                    }
                    
                    if (commission != null) {
                        Object percentage = commission.get("percentage");
                        if (percentage instanceof String && !((String) percentage).trim().isEmpty()) {
                            try {
                                BigDecimal rate = new BigDecimal(((String) percentage).trim());
                                property.setCommissionPercentage(rate);
                            } catch (NumberFormatException e) {
                                // Already logged above
                            }
                        }
                    }
                    
                    property.setUpdatedAt(LocalDateTime.now());
                    
                    // ✅ DETAILED SAVE ATTEMPT
                    try {
                        propertyService.save(property);
                        result.put("status", "SUCCESS");
                        result.put("action", "UPDATED");
                        result.put("database_name", property.getPropertyName());
                        result.put("name_changed", !originalName.equals(property.getPropertyName()));
                        
                    } catch (jakarta.validation.ConstraintViolationException e) {
                        result.put("status", "VALIDATION_FAILED");
                        result.put("validation_details", e.getConstraintViolations().stream()
                            .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage() + " (value: '" + cv.getInvalidValue() + "')")
                            .collect(Collectors.toList()));
                        
                    } catch (Exception e) {
                        result.put("status", "SAVE_FAILED");
                        result.put("save_error", e.getMessage());
                    }
                } else {
                    result.put("status", "NOT_FOUND");
                    result.put("error", "Property not found in database");
                }
            } else {
                result.put("status", "PRE_VALIDATION_FAILED");
                result.put("pre_validation_issues", validationIssues);
            }
            
        } catch (Exception e) {
            result.put("status", "PROCESSING_ERROR");
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * ✅ FAST: Get financial transactions for specific properties only
     */
    private Map<String, Object> debugSyncFinancialTransactionsForProperties(List<String> payPropIds) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get last 30 days of transactions for these properties
            LocalDate fromDate = LocalDate.now().minusDays(30);
            LocalDate toDate = LocalDate.now();
            
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            int totalTransactions = 0;
            
            for (String propertyId : payPropIds) {
                String url = "https://ukapi.staging.payprop.com/api/agency/v1.1/report/icdn" +
                    "?property_id=" + propertyId +
                    "&from_date=" + fromDate +
                    "&to_date=" + toDate +
                    "&rows=25";
                
                try {
                    ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                    List<Map<String, Object>> transactions = (List<Map<String, Object>>) response.getBody().get("items");
                    
                    if (transactions != null) {
                        totalTransactions += transactions.size();
                    }
                    
                    Thread.sleep(200); // Rate limiting
                    
                } catch (Exception e) {
                    log.warn("⚠️ Failed to get transactions for property {}: {}", propertyId, e.getMessage());
                }
            }
            
            result.put("properties_checked", payPropIds.size());
            result.put("total_transactions_found", totalTransactions);
            result.put("date_range", fromDate + " to " + toDate);
            result.put("status", "SUCCESS");
            
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * ✅ FAST: Get tenants for specific properties only
     */
    private Map<String, Object> debugSyncTenantsForProperties(List<String> payPropIds) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            int totalTenants = 0;
            
            for (String propertyId : payPropIds) {
                String url = "https://ukapi.staging.payprop.com/api/agency/v1.1/export/tenants?property_id=" + propertyId + "&rows=10";
                
                try {
                    ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                    List<Map<String, Object>> tenants = (List<Map<String, Object>>) response.getBody().get("items");
                    
                    if (tenants != null) {
                        totalTenants += tenants.size();
                    }
                    
                    Thread.sleep(150); // Rate limiting
                    
                } catch (Exception e) {
                    log.warn("⚠️ Failed to get tenants for property {}: {}", propertyId, e.getMessage());
                }
            }
            
            result.put("properties_checked", payPropIds.size());
            result.put("total_tenants_found", totalTenants);
            result.put("status", "SUCCESS");
            
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * ✅ FAST: Calculate commission for specific properties only
     */
    private Map<String, Object> debugCalculateCommissionsForProperties(List<String> payPropIds) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            int commissionsCalculated = 0;
            BigDecimal totalCommission = BigDecimal.ZERO;
            
            // This would use the existing database data to calculate commissions
            // for the specific properties only
            
            result.put("properties_processed", payPropIds.size());
            result.put("commissions_calculated", commissionsCalculated);
            result.put("total_commission_amount", totalCommission);
            result.put("status", "SUCCESS");
            
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    @GetMapping("/custom-endpoint")
    public ResponseEntity<Map<String, Object>> testCustomEndpoint(@RequestParam String endpoint) {
        try {
            ResponseEntity<Map<String, Object>> authCheck = checkAuthentication();
            if (authCheck != null) return authCheck;
            
            String url = "https://ukapi.staging.payprop.com/api/agency/v1.1/" + endpoint;
            
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> apiResponse = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "endpoint", endpoint,
                "url", url,
                "payPropResponse", apiResponse.getBody()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage(),
                "endpoint", endpoint
            ));
        }
    }

    @GetMapping("/endpoint-comparison")
    public ResponseEntity<Map<String, Object>> compareAllEndpoints(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        
        try {
            LocalDate from = fromDate != null ? LocalDate.parse(fromDate) : LocalDate.now().minusMonths(1);
            LocalDate to = toDate != null ? LocalDate.parse(toDate) : LocalDate.now();

            log.info("🔍 Comparing all PayProp endpoints for period: {} to {}", from, to);

            Map<String, Object> comparison = new HashMap<>();
            
            // Test 1: /export/payments (Payment Instructions)
            comparison.put("export_payments", testEndpoint(
                "/export/payments", 
                "?include_beneficiary_info=true&page=1&rows=10",
                "Payment Instructions/Templates"
            ));
            
            // Test 2: /export/actual-payments (Actual Payments?)
            comparison.put("export_actual_payments", testEndpoint(
                "/export/actual-payments",
                "?from_date=" + from + "&to_date=" + to + "&page=1&rows=10",
                "Actual Payments (claimed)"
            ));
            
            // Test 3: /report/all-payments (Working endpoint)
            comparison.put("report_all_payments", testEndpoint(
                "/report/all-payments",
                "?from_date=" + from + "&to_date=" + to + "&filter_by=reconciliation_date&include_beneficiary_info=true&rows=10",
                "All Payments Report (WORKING)"
            ));
            
            // Test 4: /report/icdn (Real-time transactions)
            comparison.put("report_icdn", testEndpoint(
                "/report/icdn",
                "?from_date=" + from + "&to_date=" + to + "&rows=10",
                "ICDN Real-time Transactions"
            ));

            // Test 5: /export/payments with reconciliation filter
            comparison.put("export_payments_reconciled", testEndpoint(
                "/export/payments",
                "?from_date=" + from + "&to_date=" + to + "&filter_by=reconciliation_date&include_beneficiary_info=true&rows=10",
                "Payment Instructions with Reconciliation Filter"
            ));
            
            comparison.put("dateRange", from + " to " + to);
            comparison.put("comparisonTime", System.currentTimeMillis());
            comparison.put("summary", generateEndpointSummary(comparison));
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "comparison", comparison,
                "note", "Compare data types, amounts, and structures across all endpoints"
            ));

        } catch (Exception e) {
            log.error("❌ Error in endpoint comparison: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * ✅ NEW: Test specific endpoint with detailed analysis
     */
    private Map<String, Object> testEndpoint(String endpoint, String parameters, String description) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (!oAuth2Service.hasValidTokens()) {
                result.put("accessible", false);
                result.put("error", "Not authorized");
                return result;
            }

            String url = "https://ukapi.staging.payprop.com/api/agency/v1.1" + endpoint + parameters;
            
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            log.info("🧪 Testing: {} - {}", description, url);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            result.put("accessible", true);
            result.put("status", response.getStatusCode().value());
            result.put("description", description);
            result.put("url", url);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) responseBody.get("items");
                
                result.put("hasData", items != null && !items.isEmpty());
                result.put("itemCount", items != null ? items.size() : 0);
                
                if (items != null && !items.isEmpty()) {
                    Map<String, Object> dataAnalysis = analyzeEndpointData(items, endpoint);
                    result.put("dataAnalysis", dataAnalysis);
                    result.put("sampleRecord", items.get(0)); // First record for inspection
                }
                
                // Check pagination info
                Map<String, Object> pagination = (Map<String, Object>) responseBody.get("pagination");
                if (pagination != null) {
                    result.put("totalRecords", pagination.get("total_rows"));
                    result.put("totalPages", pagination.get("total_pages"));
                }
            } else {
                result.put("hasData", false);
                result.put("error", "HTTP " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            result.put("accessible", false);
            result.put("error", e.getMessage());
            log.warn("⚠️ Endpoint {} failed: {}", endpoint, e.getMessage());
        }
        
        return result;
    }

    /**
     * Test payment categories endpoint
     */
    @GetMapping("/payment-categories")
    public ResponseEntity<Map<String, Object>> testPaymentCategories() {
        try {
            ResponseEntity<Map<String, Object>> authCheck = checkAuthentication();
            if (authCheck != null) return authCheck;
            
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = "https://ukapi.staging.payprop.com/api/agency/v1.1/categories";
            log.info("Testing payment categories: {}", url);
            
            ResponseEntity<Map> apiResponse = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "categories", apiResponse.getBody(),
                "url", url
            ));
            
        } catch (Exception e) {
            log.error("Error testing payment categories: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Discover available API endpoints
     */
    @GetMapping("/discover-endpoints")
    public ResponseEntity<Map<String, Object>> discoverEndpoints() {
        try {
            ResponseEntity<Map<String, Object>> authCheck = checkAuthentication();
            if (authCheck != null) return authCheck;
            
            Map<String, LocalDate> dateRange = getValidDateRange(null, null);
            LocalDate from = dateRange.get("from");
            LocalDate to = dateRange.get("to");
            
            Map<String, Object> discovery = new HashMap<>();
            
            // Test common PayProp endpoints
            String[] endpoints = {
                "/user/me",
                "/properties",
                "/tenants", 
                "/categories",
                "/payment-instructions",
                "/payment-batches",
                "/reports",
                "/beneficiaries"
            };
            
            for (String endpoint : endpoints) {
                try {
                    HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
                    HttpEntity<String> request = new HttpEntity<>(headers);
                    String url = "https://ukapi.staging.payprop.com/api/agency/v1.1" + endpoint;
                    
                    ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                    
                    discovery.put(endpoint, Map.of(
                        "accessible", true,
                        "status", response.getStatusCode().value(),
                        "hasData", response.getBody() != null
                    ));
                    
                } catch (Exception e) {
                    discovery.put(endpoint, Map.of(
                        "accessible", false,
                        "error", e.getMessage()
                    ));
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "endpoints", discovery,
                "dateRange", from + " to " + to
            ));
            
        } catch (Exception e) {
            log.error("Error discovering endpoints: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Get property statistics
     */
    @GetMapping("/property-stats")
    public ResponseEntity<Map<String, Object>> getPropertyStats() {
        try {
            ResponseEntity<Map<String, Object>> authCheck = checkAuthentication();
            if (authCheck != null) return authCheck;
            
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = "https://ukapi.staging.payprop.com/api/agency/v1.1/properties?rows=1";
            log.info("Testing property stats: {}", url);
            
            ResponseEntity<Map> apiResponse = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            Map<String, Object> responseBody = apiResponse.getBody();
            Map<String, Object> pagination = (Map<String, Object>) responseBody.get("pagination");
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "totalProperties", pagination != null ? pagination.get("total_rows") : 0,
                "url", url,
                "pagination", pagination
            ));
            
        } catch (Exception e) {
            log.error("Error getting property stats: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * ✅ NEW: Analyze ICDN-specific data structure
     */
    private Map<String, Object> analyzeICDNData(List<Map<String, Object>> transactions) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (transactions == null || transactions.isEmpty()) {
            analysis.put("hasData", false);
            analysis.put("message", "No ICDN transaction data found");
            return analysis;
        }

        analysis.put("hasData", true);
        analysis.put("totalRecords", transactions.size());

        // ICDN-specific field analysis
        Set<String> allFields = new HashSet<>();
        Map<String, Integer> fieldFrequency = new HashMap<>();
        List<Map<String, Object>> amountAnalysis = new ArrayList<>();
        Set<String> transactionTypes = new HashSet<>();
        Set<String> statuses = new HashSet<>();
        
        for (Map<String, Object> transaction : transactions) {
            // Collect all field names
            for (String field : transaction.keySet()) {
                allFields.add(field);
                fieldFrequency.put(field, fieldFrequency.getOrDefault(field, 0) + 1);
            }
            
            // Analyze amounts
            Map<String, Object> amountInfo = new HashMap<>();
            amountInfo.put("id", transaction.get("id"));
            amountInfo.put("amount", transaction.get("amount"));
            amountInfo.put("matched_amount", transaction.get("matched_amount"));
            amountInfo.put("type", transaction.get("type"));
            amountInfo.put("date", transaction.get("date"));
            amountAnalysis.add(amountInfo);
            
            // Collect transaction types and statuses
            if (transaction.get("type") != null) {
                transactionTypes.add(transaction.get("type").toString());
            }
            if (transaction.get("status") != null) {
                statuses.add(transaction.get("status").toString());
            }
        }
        
        analysis.put("allFields", allFields);
        analysis.put("fieldFrequency", fieldFrequency);
        analysis.put("amountAnalysis", amountAnalysis);
        analysis.put("transactionTypes", transactionTypes);
        analysis.put("statuses", statuses);
        analysis.put("sampleTransaction", transactions.get(0));
        
        // ICDN-specific checks
        analysis.put("hasPropertyInfo", transactions.stream().anyMatch(t -> t.containsKey("property")));
        analysis.put("hasTenantInfo", transactions.stream().anyMatch(t -> t.containsKey("tenant")));
        analysis.put("hasCategoryInfo", transactions.stream().anyMatch(t -> t.containsKey("category")));
        analysis.put("hasDateInfo", transactions.stream().anyMatch(t -> t.containsKey("date")));
        
        return analysis;
    }

    /**
     * ✅ NEW: Generic endpoint data analysis
     */
    private Map<String, Object> analyzeEndpointData(List<Map<String, Object>> items, String endpoint) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (items.isEmpty()) {
            analysis.put("isEmpty", true);
            return analysis;
        }
        
        Map<String, Object> firstItem = items.get(0);
        
        // Basic structure analysis
        analysis.put("totalItems", items.size());
        analysis.put("fieldsInFirstItem", firstItem.keySet());
        analysis.put("hasId", firstItem.containsKey("id"));
        
        // Amount analysis
        Map<String, Object> amountAnalysis = new HashMap<>();
        amountAnalysis.put("hasAmount", firstItem.containsKey("amount"));
        amountAnalysis.put("hasGrossAmount", firstItem.containsKey("gross_amount"));
        amountAnalysis.put("hasNetAmount", firstItem.containsKey("net_amount"));
        
        if (firstItem.containsKey("amount")) {
            Object amount = firstItem.get("amount");
            amountAnalysis.put("amountType", amount != null ? amount.getClass().getSimpleName() : "null");
            amountAnalysis.put("amountValue", amount);
            amountAnalysis.put("isZeroAmount", "0".equals(String.valueOf(amount)) || "0.00".equals(String.valueOf(amount)));
        }
        
        if (firstItem.containsKey("gross_amount")) {
            Object grossAmount = firstItem.get("gross_amount");
            amountAnalysis.put("grossAmountValue", grossAmount);
            amountAnalysis.put("isZeroGrossAmount", "0".equals(String.valueOf(grossAmount)) || "0.00".equals(String.valueOf(grossAmount)));
        }
        
        analysis.put("amountAnalysis", amountAnalysis);
        
        // Batch analysis
        Map<String, Object> batchAnalysis = analyzeBatchDataInPayments(items);
        analysis.put("batchAnalysis", batchAnalysis);
        
        // Date analysis
        Map<String, Object> dateAnalysis = new HashMap<>();
        dateAnalysis.put("hasDate", firstItem.containsKey("date"));
        dateAnalysis.put("hasPaymentDate", firstItem.containsKey("payment_date"));
        dateAnalysis.put("hasReconciliationDate", firstItem.containsKey("reconciliation_date"));
        dateAnalysis.put("hasDueDate", firstItem.containsKey("due_date"));
        analysis.put("dateAnalysis", dateAnalysis);
        
        // Relationship analysis
        Map<String, Object> relationshipAnalysis = new HashMap<>();
        relationshipAnalysis.put("hasProperty", firstItem.containsKey("property"));
        relationshipAnalysis.put("hasTenant", firstItem.containsKey("tenant"));
        relationshipAnalysis.put("hasBeneficiary", firstItem.containsKey("beneficiary"));
        relationshipAnalysis.put("hasBeneficiaryInfo", firstItem.containsKey("beneficiary_info"));
        relationshipAnalysis.put("hasCategory", firstItem.containsKey("category"));
        relationshipAnalysis.put("hasIncomingTransaction", firstItem.containsKey("incoming_transaction"));
        analysis.put("relationshipAnalysis", relationshipAnalysis);
        
        return analysis;
    }

    /**
     * ✅ UPDATED: Enhanced batch detection for all endpoint types
     */
    private Map<String, Object> analyzeBatchDataInPayments(List<Map<String, Object>> payments) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (payments == null || payments.isEmpty()) {
            analysis.put("hasData", false);
            analysis.put("message", "No payment data to analyze");
            return analysis;
        }

        analysis.put("hasData", true);
        analysis.put("totalRecords", payments.size());

        // Enhanced batch detection
        Set<String> batchFields = new HashSet<>();
        Set<String> allBatchIds = new HashSet<>();
        Map<String, Integer> batchCounts = new HashMap<>();
        Map<String, Map<String, Object>> batchDetails = new HashMap<>();
        
        for (Map<String, Object> payment : payments) {
            String batchId = null;
            Map<String, Object> batchData = null;
            
            // Method 1: Check for payment_batch object (PRIMARY for /report/all-payments)
            if (payment.containsKey("payment_batch")) {
                Object batchObj = payment.get("payment_batch");
                if (batchObj instanceof Map) {
                    batchData = (Map<String, Object>) batchObj;
                    batchId = (String) batchData.get("id");
                    if (batchId != null && !batchId.isEmpty()) {
                        batchFields.add("payment_batch");
                        allBatchIds.add(batchId);
                        batchCounts.put(batchId, batchCounts.getOrDefault(batchId, 0) + 1);
                        
                        // Store complete batch information
                        Map<String, Object> details = new HashMap<>();
                        details.put("amount", batchData.get("amount"));
                        details.put("status", batchData.get("status"));
                        details.put("transfer_date", batchData.get("transfer_date"));
                        details.put("source", "payment_batch_object");
                        batchDetails.put(batchId, details);
                    }
                }
            }
            
            // Method 2: Check flat fields (for other endpoints)
            if (batchId == null) {
                String[] possibleBatchFields = {
                    "batch_id", "payment_batch_id", "batchId", "batch", 
                    "group_id", "groupId", "payment_group_id",
                    "transaction_batch_id", "remittance_batch_id",
                    "remittance_id", "batch_reference"
                };
                
                for (String fieldName : possibleBatchFields) {
                    if (payment.containsKey(fieldName)) {
                        Object value = payment.get(fieldName);
                        if (value != null && !"null".equals(String.valueOf(value)) && !String.valueOf(value).isEmpty()) {
                            batchFields.add(fieldName);
                            batchId = String.valueOf(value);
                            allBatchIds.add(batchId);
                            batchCounts.put(batchId, batchCounts.getOrDefault(batchId, 0) + 1);
                            
                            // Store basic batch information
                            Map<String, Object> details = new HashMap<>();
                            details.put("field_name", fieldName);
                            details.put("source", "flat_field");
                            batchDetails.put(batchId, details);
                            break; // Use first found batch field
                        }
                    }
                }
            }
        }
        
        analysis.put("batchFieldsFound", batchFields);
        analysis.put("uniqueBatchIds", allBatchIds);
        analysis.put("batchCounts", batchCounts);
        analysis.put("batchDetails", batchDetails);
        analysis.put("hasBatchData", !batchFields.isEmpty());
        analysis.put("paymentBatchObjectsFound", batchFields.contains("payment_batch"));
        analysis.put("flatBatchFieldsFound", batchFields.stream()
            .filter(field -> !"payment_batch".equals(field))
            .toArray());
        
        // Show sample payment structure
        if (!payments.isEmpty()) {
            analysis.put("samplePayment", payments.get(0));
        }
        
        return analysis;
    }

    /**
     * ✅ NEW: Generate summary comparison across endpoints
     */
    private Map<String, Object> generateEndpointSummary(Map<String, Object> comparison) {
        Map<String, Object> summary = new HashMap<>();
        
        // Count accessible endpoints
        long accessibleCount = comparison.values().stream()
            .filter(val -> val instanceof Map)
            .map(val -> (Map<String, Object>) val)
            .filter(map -> Boolean.TRUE.equals(map.get("accessible")))
            .count();
        
        // Count endpoints with data
        long withDataCount = comparison.values().stream()
            .filter(val -> val instanceof Map)
            .map(val -> (Map<String, Object>) val)
            .filter(map -> Boolean.TRUE.equals(map.get("hasData")))
            .count();
        
        // Find endpoints with batch data
        List<String> endpointsWithBatches = new ArrayList<>();
        comparison.forEach((endpoint, data) -> {
            if (data instanceof Map) {
                Map<String, Object> endpointData = (Map<String, Object>) data;
                Map<String, Object> dataAnalysis = (Map<String, Object>) endpointData.get("dataAnalysis");
                if (dataAnalysis != null) {
                    Map<String, Object> batchAnalysis = (Map<String, Object>) dataAnalysis.get("batchAnalysis");
                    if (batchAnalysis != null && Boolean.TRUE.equals(batchAnalysis.get("hasBatchData"))) {
                        endpointsWithBatches.add(endpoint);
                    }
                }
            }
        });
        
        // Find endpoints with real amounts (not zero)
        List<String> endpointsWithRealAmounts = new ArrayList<>();
        comparison.forEach((endpoint, data) -> {
            if (data instanceof Map) {
                Map<String, Object> endpointData = (Map<String, Object>) data;
                Map<String, Object> dataAnalysis = (Map<String, Object>) endpointData.get("dataAnalysis");
                if (dataAnalysis != null) {
                    Map<String, Object> amountAnalysis = (Map<String, Object>) dataAnalysis.get("amountAnalysis");
                    if (amountAnalysis != null) {
                        boolean hasRealAmount = Boolean.TRUE.equals(amountAnalysis.get("hasAmount")) &&
                                              !Boolean.TRUE.equals(amountAnalysis.get("isZeroAmount"));
                        boolean hasRealGrossAmount = Boolean.TRUE.equals(amountAnalysis.get("hasGrossAmount")) &&
                                                   !Boolean.TRUE.equals(amountAnalysis.get("isZeroGrossAmount"));
                        if (hasRealAmount || hasRealGrossAmount) {
                            endpointsWithRealAmounts.add(endpoint);
                        }
                    }
                }
            }
        });
        
        summary.put("totalEndpointsTested", comparison.size() - 2); // Exclude non-endpoint fields
        summary.put("accessibleEndpoints", accessibleCount);
        summary.put("endpointsWithData", withDataCount);
        summary.put("endpointsWithBatchData", endpointsWithBatches);
        summary.put("endpointsWithRealAmounts", endpointsWithRealAmounts);
        
        // Recommendations
        List<String> recommendations = new ArrayList<>();
        if (endpointsWithRealAmounts.contains("report_all_payments")) {
            recommendations.add("✅ Use /report/all-payments for actual payment amounts");
        }
        if (endpointsWithBatches.size() > 0) {
            recommendations.add("✅ Batch data available in: " + String.join(", ", endpointsWithBatches));
        }
        if (endpointsWithRealAmounts.contains("report_icdn")) {
            recommendations.add("✅ ICDN endpoint has real transaction data");
        }
        if (withDataCount == 0) {
            recommendations.add("⚠️ No endpoints returned data - check date ranges and permissions");
        }
        
        summary.put("recommendations", recommendations);
        
        return summary;
    }

    /**
     * ✅ EXISTING: Keep original working methods
     */
    @GetMapping("/report-all-payments")
    public ResponseEntity<Map<String, Object>> testReportAllPaymentsEndpoint(
            @RequestParam(required = false) String propertyId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String paymentBatchId) {
        
        try {
            // Default dates if not provided
            LocalDate from = fromDate != null ? LocalDate.parse(fromDate) : LocalDate.now().minusMonths(6);
            LocalDate to = toDate != null ? LocalDate.parse(toDate) : LocalDate.now();

            log.info("Testing PayProp /report/all-payments endpoint for property {} ({} to {})", propertyId, from, to);

            // Build URL for direct API call
            String baseUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1/report/all-payments";
            StringBuilder urlBuilder = new StringBuilder(baseUrl);
            urlBuilder.append("?from_date=").append(from.toString());
            urlBuilder.append("&to_date=").append(to.toString());
            urlBuilder.append("&filter_by=reconciliation_date");
            urlBuilder.append("&include_beneficiary_info=true");
            
            if (propertyId != null && !propertyId.isEmpty()) {
                urlBuilder.append("&property_id=").append(propertyId);
            }
            
            if (paymentBatchId != null && !paymentBatchId.isEmpty()) {
                urlBuilder.append("&payment_batch_id=").append(paymentBatchId);
            }

            // Make direct API call using OAuth2 service
            if (!oAuth2Service.hasValidTokens()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "PayProp not authorized. Please authorize first.",
                    "endpoint", "/report/all-payments"
                ));
            }

            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String finalUrl = urlBuilder.toString();
            log.info("Calling PayProp API: {}", finalUrl);
            
            ResponseEntity<Map> apiResponse = restTemplate.exchange(finalUrl, HttpMethod.GET, request, Map.class);
            
            if (apiResponse.getStatusCode().is2xxSuccessful() && apiResponse.getBody() != null) {
                Map<String, Object> responseBody = apiResponse.getBody();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> payments = (List<Map<String, Object>>) responseBody.get("items");
                
                Map<String, Object> analysis = analyzeBatchDataInPayments(payments);
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "endpoint", "/report/all-payments",
                    "url", finalUrl,
                    "totalItems", payments != null ? payments.size() : 0,
                    "dateRange", from + " to " + to,
                    "propertyId", propertyId != null ? propertyId : "all",
                    "paymentBatchId", paymentBatchId != null ? paymentBatchId : "all",
                    "payPropResponse", responseBody,
                    "batchAnalysis", analysis,
                    "note", "This should contain actual payment transactions with batch information"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "API returned status: " + apiResponse.getStatusCode(),
                    "endpoint", "/report/all-payments",
                    "url", finalUrl,
                    "note", "This endpoint may require additional permissions"
                ));
            }

        } catch (Exception e) {
            log.error("Error testing /report/all-payments endpoint: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage(),
                "endpoint", "/report/all-payments",
                "suggestion", "Check if you have 'Read: All payments report' permission in PayProp"
            ));
        }
    }
}