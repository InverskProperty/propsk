package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service;
import site.easy.to.build.crm.service.payprop.PayPropApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/api/payprop/endpoint-test")
public class PayPropEndpointTestController {

    private static final Logger logger = LoggerFactory.getLogger(PayPropEndpointTestController.class);

    @Autowired
    private PayPropOAuth2Service oAuth2Service;

    @Autowired
    private PayPropApiClient apiClient;

    @Autowired
    private RestTemplate restTemplate;

    // Complete list of PayProp endpoints to test
    private static final Map<String, EndpointInfo> ENDPOINTS = new HashMap<>();
    
    static {
        // Core Data Export Endpoints
        ENDPOINTS.put("export-properties", new EndpointInfo("/export/properties", "GET", "?include_commission=true&rows=5", "Property settings and metadata", "read:export:properties"));
        ENDPOINTS.put("export-invoices", new EndpointInfo("/export/invoices", "GET", "?include_categories=true&rows=5", "Invoice instructions (CRITICAL MISSING)", "read:export:invoices"));
        ENDPOINTS.put("export-payments", new EndpointInfo("/export/payments", "GET", "?include_beneficiary_info=true&rows=5", "Payment distribution rules", "read:export:payments"));
        ENDPOINTS.put("export-beneficiaries", new EndpointInfo("/export/beneficiaries", "GET", "?owners=true&rows=5", "Beneficiary information", "read:export:beneficiaries"));
        ENDPOINTS.put("export-tenants", new EndpointInfo("/export/tenants", "GET", "?rows=5", "Tenant information", "read:export:tenants"));
        
        // Report Endpoints
        ENDPOINTS.put("report-all-payments", new EndpointInfo("/report/all-payments", "GET", "?from_date={from_date}&to_date={to_date}&filter_by=reconciliation_date&rows=5", "Actual payment transactions", "read:report:all-payments"));
        ENDPOINTS.put("report-icdn", new EndpointInfo("/report/icdn", "GET", "?from_date={from_date}&to_date={to_date}&rows=5", "ICDN financial transactions", "read:report:icdn"));
        ENDPOINTS.put("report-commission-summary", new EndpointInfo("/reports/commission-summary", "GET", "", "Commission analytics", "read:reports:commission"));
        ENDPOINTS.put("report-payment-variance", new EndpointInfo("/reports/payment-variance", "GET", "", "Payment vs instruction variance", "read:reports:variance"));
        ENDPOINTS.put("report-tenant-arrears", new EndpointInfo("/reports/tenant-arrears", "GET", "", "Overdue payment tracking", "read:reports:arrears"));
        ENDPOINTS.put("report-property-performance", new EndpointInfo("/reports/property-performance", "GET", "", "Property financial performance", "read:reports:performance"));
        ENDPOINTS.put("report-cash-flow", new EndpointInfo("/reports/cash-flow", "GET", "", "Cash flow projections", "read:reports:cashflow"));
        ENDPOINTS.put("report-real-time", new EndpointInfo("/reports/real-time", "GET", "", "Live reporting data", "read:reports:realtime"));
        
        // Category Endpoints
        ENDPOINTS.put("invoices-categories", new EndpointInfo("/invoices/categories", "GET", "", "Invoice category reference data", "read:invoices:categories"));
        ENDPOINTS.put("payments-categories", new EndpointInfo("/payments/categories", "GET", "", "Payment categories", "read:payments:categories"));
        ENDPOINTS.put("categories-invoice", new EndpointInfo("/categories", "GET", "?type=invoice", "Invoice classification", "read:categories"));
        
        // Entity Management Endpoints
        ENDPOINTS.put("entity-adhoc-payment", new EndpointInfo("/entity/adhoc-payment", "POST", "", "One-time payments (CREATE TEST)", "create:entity:adhoc-payment"));
        ENDPOINTS.put("entity-payment", new EndpointInfo("/entity/payment", "GET", "?rows=5", "Recurring payments", "read:entity:payment"));
        ENDPOINTS.put("entity-secondary-payment", new EndpointInfo("/entity/secondary-payment", "GET", "?rows=5", "Split payment portions", "read:entity:secondary-payment"));
        ENDPOINTS.put("entity-adhoc-invoice", new EndpointInfo("/entity/adhoc-invoice", "POST", "", "One-time invoices (CREATE TEST)", "create:entity:adhoc-invoice"));
        ENDPOINTS.put("entity-invoice", new EndpointInfo("/entity/invoice", "GET", "?rows=5", "Recurring invoices", "read:entity:invoice"));
        ENDPOINTS.put("entity-beneficiary", new EndpointInfo("/entity/beneficiary", "GET", "?rows=5", "Beneficiary management", "read:entity:beneficiary"));
        
        // Webhook Endpoints
        ENDPOINTS.put("webhooks-configuration", new EndpointInfo("/webhooks/configuration", "GET", "", "Webhook setup", "read:webhooks"));
        ENDPOINTS.put("notifications-configure", new EndpointInfo("/notifications/configure", "GET", "", "Custom notification setup", "read:notifications"));
        
        // Advanced Payment Endpoints
        ENDPOINTS.put("payments-adhoc", new EndpointInfo("/payments/adhoc", "GET", "?rows=5", "One-time payment processing", "read:payments:adhoc"));
        ENDPOINTS.put("payments-secondary", new EndpointInfo("/payments/secondary", "GET", "?rows=5", "Alternative payment methods", "read:payments:secondary"));
        ENDPOINTS.put("payments-posted", new EndpointInfo("/payments/posted", "GET", "?rows=5", "Posted payments (UK specific)", "read:payments:posted"));
        ENDPOINTS.put("payments-bulk-instructions", new EndpointInfo("/payments/bulk-instructions", "GET", "?rows=5", "Bulk payment setup", "read:payments:bulk"));
        ENDPOINTS.put("posted-payments", new EndpointInfo("/posted-payments", "GET", "?rows=5", "Unreconciled payments", "read:posted:payments"));
        
        // Document Generation Endpoints
        ENDPOINTS.put("documents-agency-invoice", new EndpointInfo("/documents/pdf/agency-invoice", "GET", "?year=2025&month=1", "Generate PDF invoices", "read:documents:invoice"));
        ENDPOINTS.put("documents-owner-statement", new EndpointInfo("/documents/pdf/owner-statement", "GET", "", "Generate owner statements", "read:documents:statement"));
        
        // Additional Endpoints
        ENDPOINTS.put("export-invoice-instructions", new EndpointInfo("/export/invoice-instructions", "GET", "?rows=5", "Detailed instruction history", "read:export:instructions"));
        ENDPOINTS.put("properties-commission", new EndpointInfo("/properties/{id}/commission", "GET", "", "Property commission rates (DYNAMIC)", "read:properties:commission"));
    }

    @PostMapping("/test-single")
    @ResponseBody
    public Map<String, Object> testSingleEndpoint(@RequestParam String endpointKey) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            EndpointInfo info = ENDPOINTS.get(endpointKey);
            if (info == null) {
                result.put("success", false);
                result.put("error", "Unknown endpoint key: " + endpointKey);
                return result;
            }
            
            result.put("endpoint", info.path);
            result.put("description", info.description);
            result.put("expected_permission", info.permission);
            
            return testEndpoint(info);
            
        } catch (Exception e) {
            logger.error("Error testing endpoint {}: {}", endpointKey, e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @PostMapping("/test-all")
    @ResponseBody
    public Map<String, Object> testAllEndpoints() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> endpointResults = new HashMap<>();
        
        int successCount = 0;
        int errorCount = 0;
        int unauthorizedCount = 0;
        int notFoundCount = 0;
        
        try {
            for (Map.Entry<String, EndpointInfo> entry : ENDPOINTS.entrySet()) {
                String key = entry.getKey();
                EndpointInfo info = entry.getValue();
                
                logger.info("Testing endpoint: {} - {}", key, info.path);
                
                Map<String, Object> endpointResult = testEndpoint(info);
                endpointResults.put(key, endpointResult);
                
                // Count results
                if (Boolean.TRUE.equals(endpointResult.get("success"))) {
                    successCount++;
                } else {
                    String error = (String) endpointResult.get("error");
                    if (error != null) {
                        if (error.contains("401") || error.contains("403")) {
                            unauthorizedCount++;
                        } else if (error.contains("404")) {
                            notFoundCount++;
                        } else {
                            errorCount++;
                        }
                    } else {
                        errorCount++;
                    }
                }
                
                // Rate limiting delay
                Thread.sleep(250);
            }
            
            result.put("success", true);
            result.put("total_endpoints", ENDPOINTS.size());
            result.put("successful", successCount);
            result.put("errors", errorCount);
            result.put("unauthorized", unauthorizedCount);
            result.put("not_found", notFoundCount);
            result.put("results", endpointResults);
            
        } catch (Exception e) {
            logger.error("Error during comprehensive test: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    @PostMapping("/test-critical-missing")
    @ResponseBody
    public Map<String, Object> testCriticalMissingEndpoints() {
        Map<String, Object> result = new HashMap<>();
        
        // Test the critical missing endpoints identified in documentation
        String[] criticalEndpoints = {
            "export-invoices",           // CRITICAL: Invoice instructions  
            "export-payments",           // CRITICAL: Payment distribution
            "invoices-categories",       // CRITICAL: Category reference
            "payments-adhoc",            // HIGH VALUE: One-time payments
            "webhooks-configuration",   // HIGH VALUE: Real-time updates
            "report-commission-summary", // HIGH VALUE: Commission analytics
            "report-payment-variance"    // HIGH VALUE: Variance detection
        };
        
        Map<String, Object> criticalResults = new HashMap<>();
        
        try {
            for (String endpointKey : criticalEndpoints) {
                EndpointInfo info = ENDPOINTS.get(endpointKey);
                if (info != null) {
                    Map<String, Object> endpointResult = testEndpoint(info);
                    criticalResults.put(endpointKey, endpointResult);
                    Thread.sleep(250); // Rate limiting
                }
            }
            
            result.put("success", true);
            result.put("critical_endpoints_tested", criticalEndpoints.length);
            result.put("results", criticalResults);
            
        } catch (Exception e) {
            logger.error("Error testing critical endpoints: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    @GetMapping("/list-endpoints")
    @ResponseBody
    public Map<String, Object> listAllEndpoints() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Map<String, Map<String, String>> endpointList = new HashMap<>();
            
            for (Map.Entry<String, EndpointInfo> entry : ENDPOINTS.entrySet()) {
                String key = entry.getKey();
                EndpointInfo info = entry.getValue();
                
                Map<String, String> endpointDetails = new HashMap<>();
                endpointDetails.put("path", info.path);
                endpointDetails.put("method", info.method);
                endpointDetails.put("parameters", info.parameters);
                endpointDetails.put("description", info.description);
                endpointDetails.put("permission", info.permission);
                
                endpointList.put(key, endpointDetails);
            }
            
            result.put("success", true);
            result.put("total_endpoints", ENDPOINTS.size());
            result.put("endpoints", endpointList);
            
        } catch (Exception e) {
            logger.error("Error listing endpoints: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    private Map<String, Object> testEndpoint(EndpointInfo info) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get authentication token
            String accessToken = oAuth2Service.getValidAccessToken();
            if (accessToken == null) {
                result.put("success", false);
                result.put("error", "No valid access token available");
                result.put("suggestion", "Check OAuth2 authentication");
                return result;
            }
            
            // Prepare URL
            String baseUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1";
            String fullPath = info.path;
            String parameters = info.parameters;
            
            // Replace dynamic parameters
            if (parameters.contains("{from_date}") || parameters.contains("{to_date}")) {
                LocalDate toDate = LocalDate.now();
                LocalDate fromDate = toDate.minusDays(30); // Last 30 days
                
                parameters = parameters.replace("{from_date}", fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                parameters = parameters.replace("{to_date}", toDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
            
            String fullUrl = baseUrl + fullPath + parameters;
            
            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Content-Type", "application/json");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // Make the API call
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response;
            
            if ("GET".equalsIgnoreCase(info.method)) {
                response = restTemplate.exchange(fullUrl, HttpMethod.GET, entity, String.class);
            } else if ("POST".equalsIgnoreCase(info.method)) {
                // For POST endpoints, we'll just test accessibility, not create data
                result.put("success", false);
                result.put("error", "POST endpoint - requires payload for creation (skipping for safety)");
                result.put("status", "SKIPPED_POST");
                result.put("note", "This endpoint exists but requires data payload to test properly");
                return result;
            } else {
                result.put("success", false);
                result.put("error", "Unsupported method: " + info.method);
                return result;
            }
            
            long endTime = System.currentTimeMillis();
            
            // Analyze response
            result.put("success", true);
            result.put("status_code", response.getStatusCodeValue());
            result.put("response_time_ms", endTime - startTime);
            result.put("has_data", response.getBody() != null && !response.getBody().trim().isEmpty());
            result.put("content_type", response.getHeaders().getContentType());
            
            // Try to count records if it's a JSON response with data
            if (response.getBody() != null) {
                String body = response.getBody();
                result.put("response_length", body.length());
                
                // Simple data detection
                if (body.contains("\"data\"") && body.contains("[")) {
                    result.put("has_data_array", true);
                } else if (body.contains("{") && body.length() > 50) {
                    result.put("has_data_object", true);
                }
                
                // Sample response (first 500 chars)
                if (body.length() > 500) {
                    result.put("sample_response", body.substring(0, 500) + "...");
                } else {
                    result.put("sample_response", body);
                }
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            
            // Categorize common errors
            if (e.getMessage().contains("401")) {
                result.put("error_type", "UNAUTHORIZED");
                result.put("suggestion", "Check API permissions for: " + info.permission);
            } else if (e.getMessage().contains("403")) {
                result.put("error_type", "FORBIDDEN");
                result.put("suggestion", "Permission denied for: " + info.permission);
            } else if (e.getMessage().contains("404")) {
                result.put("error_type", "NOT_FOUND");
                result.put("suggestion", "Endpoint may not exist in this PayProp version");
            } else if (e.getMessage().contains("500")) {
                result.put("error_type", "SERVER_ERROR");
                result.put("suggestion", "PayProp server error - try again later");
            } else {
                result.put("error_type", "UNKNOWN");
                result.put("suggestion", "Check network connectivity and authentication");
            }
        }
        
        return result;
    }

    // Helper class for endpoint information
    private static class EndpointInfo {
        final String path;
        final String method;
        final String parameters;
        final String description;
        final String permission;
        
        EndpointInfo(String path, String method, String parameters, String description, String permission) {
            this.path = path;
            this.method = method;
            this.parameters = parameters;
            this.description = description;
            this.permission = permission;
        }
    }
}