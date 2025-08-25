package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
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
    private static final int MAX_PAGES = 1000; // Increased from 100 to capture all data
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int RATE_LIMIT_DELAY_MS = 500; // Increased from 250ms to avoid 5 req/sec PayProp limit
    
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
                
                // Check for 404 errors (can be wrapped in RuntimeException)
                boolean is404 = false;
                if (e instanceof HttpClientErrorException) {
                    HttpClientErrorException httpError = (HttpClientErrorException) e;
                    if (httpError.getStatusCode() == HttpStatus.UNAUTHORIZED || 
                        httpError.getStatusCode() == HttpStatus.FORBIDDEN) {
                        log.error("Authentication/Authorization error, stopping pagination");
                        break;
                    } else if (httpError.getStatusCode() == HttpStatus.NOT_FOUND) {
                        is404 = true;
                    }
                } else if (e.getCause() instanceof HttpClientErrorException) {
                    HttpClientErrorException httpError = (HttpClientErrorException) e.getCause();
                    if (httpError.getStatusCode() == HttpStatus.NOT_FOUND) {
                        is404 = true;
                    }
                } else if (e.getMessage() != null && e.getMessage().contains("404 NOT_FOUND")) {
                    is404 = true;
                }
                
                if (is404) {
                    log.debug("404 error on page {}, likely no more pages available, stopping pagination", page);
                    break;
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
     * Fetch historical data from a PayProp report endpoint using chunked date ranges
     * PayProp report endpoints have a 93-day limit, so we chunk the requests
     * 
     * @param baseEndpoint The base API endpoint (e.g., "/report/icdn")
     * @param yearsBack Number of years of historical data to fetch (e.g., 2)
     * @param mapper Function to transform raw API response items to desired type
     * @return List of all items from all historical chunks
     */
    public <T> List<T> fetchHistoricalPages(String baseEndpoint, int yearsBack, Function<Map<String, Object>, T> mapper) {
        List<T> allResults = new ArrayList<>();
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusYears(yearsBack);
        
        log.info("🕐 Starting historical chunked fetch from endpoint: {} (going back {} years)", baseEndpoint, yearsBack);
        
        // Work backwards in 93-day chunks from today
        LocalDateTime currentEnd = endDate;
        int chunkNumber = 0;
        int totalApiCalls = 0;
        
        while (currentEnd.isAfter(startDate)) {
            // Calculate chunk start (93 days before current end, but not before overall start)
            LocalDateTime chunkStart = currentEnd.minusDays(93);
            if (chunkStart.isBefore(startDate)) {
                chunkStart = startDate;
            }
            
            chunkNumber++;
            
            try {
                // Build endpoint with date parameters
                String endpoint = baseEndpoint + 
                    "&from_date=" + chunkStart.toLocalDate().toString() +
                    "&to_date=" + currentEnd.toLocalDate().toString();
                
                log.info("📅 CHUNK {}: Fetching {} to {} ({})", 
                    chunkNumber, chunkStart.toLocalDate(), currentEnd.toLocalDate(), endpoint);
                
                // Fetch all pages for this chunk
                List<T> chunkResults = fetchAllPages(endpoint, mapper);
                allResults.addAll(chunkResults);
                totalApiCalls += (chunkResults.size() / DEFAULT_PAGE_SIZE) + 1; // Estimate API calls
                
                log.info("✅ CHUNK {} complete: {} records added", chunkNumber, chunkResults.size());
                
                // Move to previous chunk (subtract 93 days from current start)
                currentEnd = chunkStart.minusSeconds(1); // Avoid overlap
                
                // Rate limiting between chunks
                if (currentEnd.isAfter(startDate)) {
                    Thread.sleep(RATE_LIMIT_DELAY_MS * 2); // Extra delay between chunks
                }
                
            } catch (InterruptedException e) {
                log.error("Historical chunked fetch interrupted: {}", e.getMessage());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Failed to fetch historical chunk {} ({} to {}): {}", 
                    chunkNumber, chunkStart.toLocalDate(), currentEnd.toLocalDate(), e.getMessage());
                
                // For report endpoints, don't break - just log and continue with next chunk
                currentEnd = chunkStart.minusSeconds(1);
            }
        }
        
        log.info("🎯 Historical chunked fetch complete: {} total records from {} chunks over {} years", 
            allResults.size(), chunkNumber, yearsBack);
        
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
        } catch (Exception e) {
            log.error("Error creating authorized headers or making API call: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch page: " + e.getMessage(), e);
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
        } catch (Exception e) {
            log.error("Error creating authorized headers or making API call: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch with params: " + e.getMessage(), e);
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
        } catch (Exception e) {
            log.error("Error creating authorized headers or making API call: {}", e.getMessage());
            throw new RuntimeException("Failed to execute GET request: " + e.getMessage(), e);
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
        } catch (Exception e) {
            log.error("Error creating authorized headers or downloading binary: {}", e.getMessage());
            throw new RuntimeException("Failed to download binary content: " + e.getMessage(), e);
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
        } catch (Exception e) {
            log.error("Error creating authorized headers or executing DELETE: {}", e.getMessage());
            throw new RuntimeException("Failed to execute DELETE request: " + e.getMessage(), e);
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
        } catch (Exception e) {
            log.error("Error creating authorized headers or executing POST: {}", e.getMessage());
            throw new RuntimeException("Failed to execute POST request: " + e.getMessage(), e);
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
        } catch (Exception e) {
            log.error("Error creating authorized headers or executing PUT: {}", e.getMessage());
            throw new RuntimeException("Failed to execute PUT request: " + e.getMessage(), e);
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
            log.error("PayProp API PATCH error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error creating authorized headers or executing PATCH: {}", e.getMessage());
            throw new RuntimeException("Failed to execute PATCH request: " + e.getMessage(), e);
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
     * Updated to handle maintenance endpoints that use different field names
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(Map<String, Object> responseBody) {
        // Try maintenance tickets field first (per API docs)
        Object tickets = responseBody.get("tickets");
        if (tickets instanceof List) {
            log.debug("✅ Found 'tickets' field with {} items", ((List<?>) tickets).size());
            return (List<Map<String, Object>>) tickets;
        }
        
        // Try maintenance categories field (per API docs)
        Object categories = responseBody.get("categories");
        if (categories instanceof List) {
            log.debug("✅ Found 'categories' field with {} items", ((List<?>) categories).size());
            return (List<Map<String, Object>>) categories;
        }
        
        // Try standard items field (for other endpoints)
        Object items = responseBody.get("items");
        if (items instanceof List) {
            log.debug("✅ Found 'items' field with {} items", ((List<?>) items).size());
            return (List<Map<String, Object>>) items;
        }
        
        // Try data field (alternative structure)
        items = responseBody.get("data");
        if (items instanceof List) {
            log.debug("✅ Found 'data' field with {} items", ((List<?>) items).size());
            return (List<Map<String, Object>>) items;
        }
        
        // If no recognized field found, log available fields for debugging
        log.warn("❌ No recognized data field found. Available fields: {}", responseBody.keySet());
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