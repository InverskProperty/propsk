import org.springframework.http.MediaType;package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * PayProp API Client Utility
 * Provides common patterns for API communication with PayProp, including:
 * - Pagination handling
 * - Rate limiting
 * - Error handling
 * - Response mapping
 */
@Component
public class PayPropApiClient {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropApiClient.class);
    
    // Constants for API behavior
    private static final int MAX_PAGES = 100;
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int RATE_LIMIT_DELAY_MS = 250;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private PayPropOAuth2Service oAuth2Service;
    
    @Value("${payprop.api.base-url:https://ukapi.staging.payprop.com/api/agency/v1.1}")
    private String payPropApiBase;
    
    /**
     * Fetch all pages of data from a PayProp endpoint with automatic pagination
     * 
     * @param endpoint The API endpoint (e.g., "/export/properties")
     * @param mapper Function to transform raw API response items to desired type
     * @return List of all items from all pages
     */
    public <T> List<T> fetchAllPages(String endpoint, Function<Map<String, Object>, T> mapper) {
        List<T> allResults = new ArrayList<>();
        int page = 1;
        int totalApiCalls = 0;
        
        log.info("🔄 Starting paginated fetch from endpoint: {}", endpoint);
        
        while (page <= MAX_PAGES) {
            try {
                // Rate limiting - wait between API calls (except for first call)
                if (page > 1) {
                    Thread.sleep(RATE_LIMIT_DELAY_MS);
                }
                
                // Fetch single page
                PayPropPageResult result = fetchSinglePage(endpoint, page, DEFAULT_PAGE_SIZE);
                totalApiCalls++;
                
                // Check if we got any items
                if (result.getItems().isEmpty()) {
                    log.debug("No items found on page {}, ending pagination", page);
                    break;
                }
                
                // Process each item with error handling
                int successCount = 0;
                int errorCount = 0;
                
                for (Map<String, Object> item : result.getItems()) {
                    try {
                        T mappedItem = mapper.apply(item);
                        if (mappedItem != null) {
                            allResults.add(mappedItem);
                            successCount++;
                        }
                    } catch (Exception e) {
                        errorCount++;
                        log.error("Failed to map item on page {}: {}", page, e.getMessage());
                        log.debug("Failed item: {}", item);
                    }
                }
                
                log.info("Page {} processed: {} items (✅ {} mapped, ❌ {} errors)", 
                    page, result.getItems().size(), successCount, errorCount);
                
                // Check if there's a next page
                if (!hasNextPage(result)) {
                    log.debug("No more pages available, ending pagination");
                    break;
                }
                
                page++;
                
            } catch (InterruptedException e) {
                log.error("Rate limiting interrupted: {}", e.getMessage());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Failed to fetch page {} from {}: {}", page, endpoint, e.getMessage());
                // Decide whether to continue or break based on error type
                if (e instanceof HttpClientErrorException) {
                    HttpClientErrorException httpError = (HttpClientErrorException) e;
                    if (httpError.getStatusCode() == HttpStatus.UNAUTHORIZED || 
                        httpError.getStatusCode() == HttpStatus.FORBIDDEN) {
                        log.error("Authentication/Authorization error, stopping pagination");
                        break;
                    }
                }
                // For other errors, log and continue to next page
                page++;
            }
        }
        
        if (page > MAX_PAGES) {
            log.warn("⚠️ Reached maximum page limit of {}", MAX_PAGES);
        }
        
        log.info("✅ Pagination complete: {} total items fetched in {} API calls", 
            allResults.size(), totalApiCalls);
        
        return allResults;
    }
    
    /**
     * Fetch a single page of data from a PayProp endpoint
     * 
     * @param endpoint The API endpoint
     * @param page Page number (1-based)
     * @param rows Number of rows per page (max 25 for PayProp)
     * @return Page result with items and pagination info
     */
    public PayPropPageResult fetchSinglePage(String endpoint, int page, int rows) {
        // Ensure rows doesn't exceed PayProp's maximum
        rows = Math.min(rows, DEFAULT_PAGE_SIZE);
        
        // Build URL with pagination parameters
        String url = buildPaginatedUrl(endpoint, page, rows);
        
        try {
            // Create authorized request
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            log.debug("Fetching page {} from: {}", page, url);
            
            // Make API call
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parsePageResponse(response.getBody());
            } else {
                throw new RuntimeException("Unexpected response status: " + response.getStatusCode());
            }
            
        } catch (HttpClientErrorException e) {
            log.error("PayProp API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        }
    }
    
    /**
     * Fetch data with custom parameters
     */
    public PayPropPageResult fetchWithParams(String endpoint, Map<String, String> params) {
        // Build URL with custom parameters
        StringBuilder url = new StringBuilder(payPropApiBase + endpoint);
        if (params != null && !params.isEmpty()) {
            url.append("?");
            params.forEach((key, value) -> 
                url.append(key).append("=").append(value).append("&")
            );
            // Remove trailing &
            url.setLength(url.length() - 1);
        }
        
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url.toString(), 
                HttpMethod.GET, 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parsePageResponse(response.getBody());
            } else {
                throw new RuntimeException("Unexpected response status: " + response.getStatusCode());
            }
            
        } catch (HttpClientErrorException e) {
            log.error("PayProp API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        }
    }
    
    /**
     * Execute a simple GET request to PayProp API
     */
    public Map<String, Object> get(String endpoint) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + endpoint;
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException("Unexpected response status: " + response.getStatusCode());
            }
            
        } catch (HttpClientErrorException e) {
            log.error("PayProp API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        }
    }
    
    /**
     * Download binary content (PDFs, attachments, etc.)
     */
    public byte[] downloadBinary(String endpoint) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + endpoint;
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                request, 
                byte[].class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                throw new RuntimeException("Failed to download binary content: " + response.getStatusCode());
            }
            
        } catch (HttpClientErrorException e) {
            log.error("PayProp API error downloading binary: {} - {}", e.getStatusCode(), e.getMessage());
            throw new RuntimeException("Failed to download binary content", e);
        }
    }
    
    /**
     * Build paginated URL with proper parameter handling
     */
    private String buildPaginatedUrl(String endpoint, int page, int rows) {
        StringBuilder url = new StringBuilder(payPropApiBase + endpoint);
        
        // Check if endpoint already has parameters
        if (endpoint.contains("?")) {
            url.append("&");
        } else {
            url.append("?");
        }
        
        url.append("page=").append(page);
        url.append("&rows=").append(rows);
        
        return url.toString();
    }
    
    /**
     * Parse API response into standardized page result
     */
    private PayPropPageResult parsePageResponse(Map<String, Object> responseBody) {
        PayPropPageResult result = new PayPropPageResult();
        
        // Extract items (handle different response structures)
        List<Map<String, Object>> items = extractItems(responseBody);
        result.setItems(items);
        
        // Extract pagination info
        Map<String, Object> pagination = extractPagination(responseBody);
        result.setPagination(pagination);
        
        return result;
    }
    
    /**
     * Extract items from various PayProp response structures
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(Map<String, Object> responseBody) {
        // Try different common field names used by PayProp
        Object items = responseBody.get("items");
        if (items instanceof List) {
            return (List<Map<String, Object>>) items;
        }
        
        items = responseBody.get("data");
        if (items instanceof List) {
            return (List<Map<String, Object>>) items;
        }
        
        items = responseBody.get("results");
        if (items instanceof List) {
            return (List<Map<String, Object>>) items;
        }
        
        // If no standard field found, log warning and return empty list
        log.warn("No standard items field found in response. Available fields: {}", responseBody.keySet());
        return new ArrayList<>();
    }
    
    /**
     * Extract pagination info from response
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPagination(Map<String, Object> responseBody) {
        Object pagination = responseBody.get("pagination");
        if (pagination instanceof Map) {
            return (Map<String, Object>) pagination;
        }
        
        // If no pagination object, create one from available fields
        Map<String, Object> paginationInfo = new HashMap<>();
        
        // Try to extract common pagination fields
        if (responseBody.containsKey("page")) {
            paginationInfo.put("page", responseBody.get("page"));
        }
        if (responseBody.containsKey("total_pages")) {
            paginationInfo.put("total_pages", responseBody.get("total_pages"));
        }
        if (responseBody.containsKey("total")) {
            paginationInfo.put("total", responseBody.get("total"));
        }
        if (responseBody.containsKey("per_page")) {
            paginationInfo.put("per_page", responseBody.get("per_page"));
        }
        
        return paginationInfo;
    }
    
    /**
     * Check if there's a next page based on pagination info
     */
    private boolean hasNextPage(PayPropPageResult result) {
        Map<String, Object> pagination = result.getPagination();
        
        if (pagination == null || pagination.isEmpty()) {
            // No pagination info, check if we got a full page
            return result.getItems().size() >= DEFAULT_PAGE_SIZE;
        }
        
        // Check if current page < total pages
        Integer currentPage = getIntegerValue(pagination.get("page"));
        Integer totalPages = getIntegerValue(pagination.get("total_pages"));
        
        if (currentPage != null && totalPages != null) {
            return currentPage < totalPages;
        }
        
        // Fallback: if we got a full page, assume there might be more
        return result.getItems().size() >= DEFAULT_PAGE_SIZE;
    }
    
    /**
     * Safely convert object to Integer
     */
    private Integer getIntegerValue(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Execute a DELETE request to PayProp API
     */
    public Map<String, Object> delete(String endpoint) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + endpoint;
            
            log.debug("Executing DELETE request to: {}", url);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.DELETE, 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK || 
                response.getStatusCode() == HttpStatus.NO_CONTENT) {
                return response.getBody() != null ? response.getBody() : new HashMap<>();
            } else {
                throw new RuntimeException("Unexpected response status: " + response.getStatusCode());
            }
            
        } catch (HttpClientErrorException e) {
            log.error("PayProp API DELETE error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * Execute a POST request to PayProp API
     */
    public <T> Map<String, Object> post(String endpoint, T body) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<T> request = new HttpEntity<>(body, headers);
            
            String url = payPropApiBase + endpoint;
            
            log.debug("Executing POST request to: {}", url);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK || 
                response.getStatusCode() == HttpStatus.CREATED) {
                return response.getBody() != null ? response.getBody() : new HashMap<>();
            } else {
                throw new RuntimeException("Unexpected response status: " + response.getStatusCode());
            }
            
        } catch (HttpClientErrorException e) {
            log.error("PayProp API POST error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * Execute a PUT request to PayProp API
     */
    public <T> Map<String, Object> put(String endpoint, T body) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<T> request = new HttpEntity<>(body, headers);
            
            String url = payPropApiBase + endpoint;
            
            log.debug("Executing PUT request to: {}", url);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.PUT, 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK || 
                response.getStatusCode() == HttpStatus.NO_CONTENT) {
                return response.getBody() != null ? response.getBody() : new HashMap<>();
            } else {
                throw new RuntimeException("Unexpected response status: " + response.getStatusCode());
            }
            
        } catch (HttpClientErrorException e) {
            log.error("PayProp API PUT error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * Execute a PATCH request to PayProp API
     */
    public <T> Map<String, Object> patch(String endpoint, T body) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<T> request = new HttpEntity<>(body, headers);
            
            String url = payPropApiBase + endpoint;
            
            log.debug("Executing PATCH request to: {}", url);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.PATCH, 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK || 
                response.getStatusCode() == HttpStatus.NO_CONTENT) {
                return response.getBody() != null ? response.getBody() : new HashMap<>();
            } else {
                throw new RuntimeException("Unexpected response status: " + response.getStatusCode());
            }
            
        } catch (HttpClientErrorException e) {
            log.error("PayProp API PATCH error: {} - {}", e.getResponseBodyAsString());
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        }
    }
    
    /**
     * Result class for paginated responses
     */
    public static class PayPropPageResult {
        private List<Map<String, Object>> items = new ArrayList<>();
        private Map<String, Object> pagination = new HashMap<>();
        
        public List<Map<String, Object>> getItems() {
            return items;
        }
        
        public void setItems(List<Map<String, Object>> items) {
            this.items = items != null ? items : new ArrayList<>();
        }
        
        public Map<String, Object> getPagination() {
            return pagination;
        }
        
        public void setPagination(Map<String, Object> pagination) {
            this.pagination = pagination != null ? pagination : new HashMap<>();
        }
        
        public boolean isEmpty() {
            return items.isEmpty();
        }
        
        public int size() {
            return items.size();
        }
    }
}