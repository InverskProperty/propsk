// PayPropSyncService.java - Enhanced with Payment Sync Capabilities
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.TenantService;
import site.easy.to.build.crm.service.property.PropertyOwnerService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.payprop.SyncResult;
import site.easy.to.build.crm.service.payprop.SyncResultType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropSyncService {

    private static final Logger log = LoggerFactory.getLogger(PayPropSyncService.class);

    // ===== EXISTING SERVICES =====
    private final PropertyService propertyService;
    private final TenantService tenantService;
    private final PropertyOwnerService propertyOwnerService;
    private final CustomerService customerService;
    private final RestTemplate restTemplate;
    private final PayPropOAuth2Service oAuth2Service;
    
    // ===== NEW PAYMENT REPOSITORIES =====
    private final PaymentRepository paymentRepository;
    private final PaymentCategoryRepository paymentCategoryRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final BeneficiaryBalanceRepository beneficiaryBalanceRepository;
    
    @Value("${payprop.api.base-url:https://ukapi.staging.payprop.com/api/agency/v1.1}")
    private String payPropApiBase;
    
    @Autowired
    public PayPropSyncService(PropertyService propertyService, 
                             TenantService tenantService,
                             PropertyOwnerService propertyOwnerService,
                             CustomerService customerService,
                             RestTemplate restTemplate,
                             PayPropOAuth2Service oAuth2Service,
                             PaymentRepository paymentRepository,
                             PaymentCategoryRepository paymentCategoryRepository,
                             BeneficiaryRepository beneficiaryRepository,
                             BeneficiaryBalanceRepository beneficiaryBalanceRepository) {
        this.propertyService = propertyService;
        this.tenantService = tenantService;
        this.propertyOwnerService = propertyOwnerService;
        this.customerService = customerService;
        this.restTemplate = restTemplate;
        this.oAuth2Service = oAuth2Service;
        this.paymentRepository = paymentRepository;
        this.paymentCategoryRepository = paymentCategoryRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.beneficiaryBalanceRepository = beneficiaryBalanceRepository;
    }
    
    // ===== NEW PAYMENT SYNC METHODS =====
    
    /**
     * ✅ ENHANCEMENT 1: Sync payment categories to your database
     * Ensures you have all category mappings for payment classification
     */
    public SyncResult syncPaymentCategoriesFromPayProp() {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                payPropApiBase + "/payments/categories", 
                HttpMethod.GET, 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                // Handle different possible response structures
                List<Map<String, Object>> categories = null;
                
                // Try 'data' field first (most common in PayProp API)
                if (responseBody.containsKey("data")) {
                    Object dataObj = responseBody.get("data");
                    if (dataObj instanceof List) {
                        categories = (List<Map<String, Object>>) dataObj;
                    }
                }
                
                // Try 'categories' field as backup
                if (categories == null && responseBody.containsKey("categories")) {
                    Object categoriesObj = responseBody.get("categories");
                    if (categoriesObj instanceof List) {
                        categories = (List<Map<String, Object>>) categoriesObj;
                    }
                }
                
                // Try 'items' field as another backup
                if (categories == null && responseBody.containsKey("items")) {
                    Object itemsObj = responseBody.get("items");
                    if (itemsObj instanceof List) {
                        categories = (List<Map<String, Object>>) itemsObj;
                    }
                }
                
                // If still null, log the actual response structure
                if (categories == null) {
                    log.error("❌ Unexpected payment categories response structure: {}", responseBody);
                    return SyncResult.failure("Payment categories response structure not recognized. Expected 'data', 'categories', or 'items' field with list of categories.");
                }
                
                int created = 0;
                int updated = 0;
                
                for (Map<String, Object> categoryData : categories) {
                    String payPropCategoryId = (String) categoryData.get("id");
                    
                    if (payPropCategoryId == null) {
                        log.warn("⚠️ Category missing ID field: {}", categoryData);
                        continue;
                    }
                    
                    // Check if category exists in your payment_categories table
                    PaymentCategory existing = paymentCategoryRepository.findByPayPropCategoryId(payPropCategoryId);
                    
                    if (existing != null) {
                        // Update existing
                        existing.setCategoryName((String) categoryData.get("name"));
                        existing.setCategoryType((String) categoryData.get("type"));
                        existing.setDescription((String) categoryData.get("description"));
                        existing.setUpdatedAt(LocalDateTime.now());
                        paymentCategoryRepository.save(existing);
                        updated++;
                        log.info("✅ Updated payment category: {} ({})", existing.getCategoryName(), payPropCategoryId);
                    } else {
                        // Create new
                        PaymentCategory newCategory = new PaymentCategory();
                        newCategory.setPayPropCategoryId(payPropCategoryId);
                        newCategory.setCategoryName((String) categoryData.get("name"));
                        newCategory.setCategoryType((String) categoryData.get("type"));
                        newCategory.setDescription((String) categoryData.get("description"));
                        newCategory.setCreatedAt(LocalDateTime.now());
                        newCategory.setUpdatedAt(LocalDateTime.now());
                        paymentCategoryRepository.save(newCategory);
                        created++;
                        log.info("✅ Created payment category: {} ({})", newCategory.getCategoryName(), payPropCategoryId);
                    }
                }
                
                log.info("✅ Payment categories sync completed: {} created, {} updated", created, updated);
                return SyncResult.success("Payment categories synced", 
                    Map.of("created", created, "updated", updated, "total", categories.size()));
            }
            
            log.error("❌ Failed to get payment categories from PayProp: {}", response.getStatusCode());
            return SyncResult.failure("Failed to get payment categories from PayProp: " + response.getStatusCode());
            
        } catch (HttpClientErrorException e) {
            log.error("❌ PayProp API error for payment categories: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return SyncResult.failure("PayProp API error: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("❌ Payment categories sync failed: {}", e.getMessage(), e);
            return SyncResult.failure("Payment categories sync failed: " + e.getMessage());
        }
    }

    /**
     * ✅ ENHANCEMENT 2: Sync payments with complete data to your database
     * Extends your existing exportPaymentsFromPayProp with database storage
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncPaymentsToDatabase(Long initiatedBy) {
        try {
            int page = 1;
            int totalProcessed = 0;
            int totalCreated = 0;
            int totalUpdated = 0;
            int totalErrors = 0;
            
            while (true) {
                PayPropExportResult exportResult = exportPaymentsFromPayProp(page, 25);
                
                if (exportResult.getItems().isEmpty()) {
                    break;
                }

                for (Map<String, Object> paymentData : exportResult.getItems()) {
                    try {
                        boolean isNew = createOrUpdatePayment(paymentData, initiatedBy);
                        if (isNew) totalCreated++; else totalUpdated++;
                        totalProcessed++;
                    } catch (Exception e) {
                        totalErrors++;
                        log.error("Failed to sync payment {}: {}", paymentData.get("id"), e.getMessage());
                    }
                }
                page++;
            }

            Map<String, Object> details = Map.of(
                "processed", totalProcessed,
                "created", totalCreated, 
                "updated", totalUpdated,
                "errors", totalErrors
            );

            return totalErrors == 0 ? 
                SyncResult.success("Payments synced successfully", details) : 
                SyncResult.partial("Payments synced with some errors", details);
                
        } catch (Exception e) {
            return SyncResult.failure("Payments sync failed: " + e.getMessage());
        }
    }

    /**
     * ✅ ENHANCEMENT 3: Create or update payment in your database
     */
    private boolean createOrUpdatePayment(Map<String, Object> paymentData, Long initiatedBy) {
        String payPropPaymentId = (String) paymentData.get("id");
        
        // Check if payment exists in your payments table
        Payment existingPayment = paymentRepository.findByPayPropPaymentId(payPropPaymentId);
        
        if (existingPayment != null) {
            updatePaymentFromPayPropData(existingPayment, paymentData);
            paymentRepository.save(existingPayment);
            return false; // Updated
        } else {
            Payment newPayment = createPaymentFromPayPropData(paymentData);
            paymentRepository.save(newPayment);
            return true; // Created
        }
    }

    /**
     * ✅ ENHANCEMENT 4: Map PayProp payment data to your Payment entity
     */
    private Payment createPaymentFromPayPropData(Map<String, Object> data) {
        Payment payment = new Payment();
        
        payment.setPayPropPaymentId((String) data.get("id"));
        
        // Amount and dates
        Object amount = data.get("amount");
        if (amount instanceof Number) {
            payment.setAmount(new BigDecimal(amount.toString()));
        }
        
        payment.setPaymentDate(parsePayPropDate((String) data.get("payment_date")));
        payment.setReconciliationDate(parsePayPropDate((String) data.get("reconciliation_date")));
        payment.setRemittanceDate(parsePayPropDate((String) data.get("remittance_date")));
        
        // Categories and references
        payment.setCategoryId((String) data.get("category_id"));
        payment.setDescription((String) data.get("description"));
        payment.setReference((String) data.get("reference"));
        payment.setStatus((String) data.get("status"));
        
        // Relationships
        payment.setParentPaymentId((String) data.get("parent_payment_id"));
        payment.setBatchId((String) data.get("batch_id"));
        
        // Financial details
        Object taxAmount = data.get("tax_amount");
        if (taxAmount instanceof Number) {
            payment.setTaxAmount(new BigDecimal(taxAmount.toString()));
        }
        
        Object commissionAmount = data.get("commission_amount");
        if (commissionAmount instanceof Number) {
            payment.setCommissionAmount(new BigDecimal(commissionAmount.toString()));
        }
        
        // Property and beneficiary relationships
        Map<String, Object> property = (Map<String, Object>) data.get("property");
        if (property != null) {
            String propertyPayPropId = (String) property.get("id");
            // Find your property by PayProp ID and set the relationship
            Optional<Property> propertyOpt = propertyService.findByPayPropId(propertyPayPropId);
            if (propertyOpt.isPresent()) {
                payment.setPropertyId(propertyOpt.get().getId());
            }
        }
        
        // AFTER (correct):
        Map<String, Object> beneficiaryInfo = (Map<String, Object>) data.get("beneficiary_info");
        if (beneficiaryInfo != null) {
            String beneficiaryPayPropId = (String) beneficiaryInfo.get("id");
            Customer customer = customerService.findByPayPropEntityId(beneficiaryPayPropId);
            if (customer != null) {
                payment.setBeneficiaryId(customer.getCustomerId().longValue());
            }
        }
        
        // Tenant relationship (for rent payments)
        if (payment.getCategoryId() != null && payment.getCategoryId().toLowerCase().contains("rent")) {
            // Try to find tenant by property
            if (payment.getPropertyId() != null) {
                // Look for tenant assigned to this property
                Customer tenant = customerService.findTenantByPropertyId(payment.getPropertyId());
                if (tenant != null) {
                    payment.setTenantId(tenant.getCustomerId().longValue());
                }
            }
        }
        
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        
        return payment;
    }

    /**
     * ✅ ENHANCEMENT 5: Update existing payment with PayProp data
     */
    private void updatePaymentFromPayPropData(Payment payment, Map<String, Object> data) {
        // Update amount and dates
        Object amount = data.get("amount");
        if (amount instanceof Number) {
            payment.setAmount(new BigDecimal(amount.toString()));
        }
        
        payment.setPaymentDate(parsePayPropDate((String) data.get("payment_date")));
        payment.setReconciliationDate(parsePayPropDate((String) data.get("reconciliation_date")));
        payment.setRemittanceDate(parsePayPropDate((String) data.get("remittance_date")));
        
        // Update status and references
        payment.setStatus((String) data.get("status"));
        payment.setDescription((String) data.get("description"));
        payment.setReference((String) data.get("reference"));
        
        // Update financial details
        Object taxAmount = data.get("tax_amount");
        if (taxAmount instanceof Number) {
            payment.setTaxAmount(new BigDecimal(taxAmount.toString()));
        }
        
        Object commissionAmount = data.get("commission_amount");
        if (commissionAmount instanceof Number) {
            payment.setCommissionAmount(new BigDecimal(commissionAmount.toString()));
        }
        
        payment.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * ✅ ENHANCEMENT 6: Sync beneficiary balances to your database
     */
    public SyncResult syncBeneficiaryBalancesToDatabase(Long initiatedBy) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                payPropApiBase + "/report/beneficiary/balances", 
                HttpMethod.GET, 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> balances = (List<Map<String, Object>>) response.getBody().get("data");
                
                int processed = 0;
                
                // Check if balances is null or empty
                if (balances == null || balances.isEmpty()) {
                    return SyncResult.success("No beneficiary balances found to sync", 
                        Map.of("processed", 0));
                }
                
                for (Map<String, Object> balanceData : balances) {
                    try {
                        createOrUpdateBeneficiaryBalance(balanceData);
                        processed++;
                    } catch (Exception e) {
                        log.error("Failed to sync beneficiary balance: {}", e.getMessage());
                    }
                }
                
                return SyncResult.success("Beneficiary balances synced", 
                    Map.of("processed", processed));
            }
            
            return SyncResult.failure("Failed to get beneficiary balances from PayProp");
            
        } catch (Exception e) {
            return SyncResult.failure("Beneficiary balances sync failed: " + e.getMessage());
        }
    }

    /**
     * ✅ CORRECT: Export actual payment transactions from PayProp
     * This gets real payments with amounts and dates
     */
    public PayPropExportResult exportActualPaymentsFromPayProp(int page, int rows) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            LocalDate fromDate = LocalDate.now().minusMonths(3);
            LocalDate toDate = LocalDate.now();
            
            // ✅ USE THE CORRECT ENDPOINT
            String url = payPropApiBase + "/report/all-payments" +
                        "?from_date=" + fromDate +
                        "&to_date=" + toDate +
                        "&filter_by=reconciliation_date" +
                        "&include_beneficiary_info=true" +
                        "&page=" + page + 
                        "&rows=" + Math.min(rows, 25);
            
            log.info("📥 Exporting ACTUAL PAYMENTS from PayProp - Page {} (from {})", page, fromDate);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                PayPropExportResult result = new PayPropExportResult();
                result.setItems((List<Map<String, Object>>) responseBody.get("items"));
                result.setPagination((Map<String, Object>) responseBody.get("pagination"));
                
                log.info("✅ Exported {} ACTUAL PAYMENTS from PayProp", result.getItems().size());
                return result;
            }
            
            throw new RuntimeException("Failed to export actual payments from PayProp");
            
        } catch (Exception e) {
            log.error("❌ Actual payments export failed: {}", e.getMessage());
            throw new RuntimeException("Actual payments export failed", e);
        }
    }

    /**
     * ✅ COMPREHENSIVE: Get all payments report for a property (your "goldmine" endpoint)
     */
    public PayPropExportResult exportAllPaymentsReportFromPayProp(String propertyId, LocalDate fromDate, LocalDate toDate) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + "/report/all-payments" +
                        "?property_id=" + propertyId +
                        "&from_date=" + fromDate.toString() +
                        "&to_date=" + toDate.toString() +
                        "&filter_by=reconciliation_date" +
                        "&include_beneficiary_info=true";
            
            log.info("📊 Getting comprehensive payment report for property {} ({} to {})", 
                    propertyId, fromDate, toDate);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                PayPropExportResult result = new PayPropExportResult();
                result.setItems((List<Map<String, Object>>) responseBody.get("items"));
                result.setPagination((Map<String, Object>) responseBody.get("pagination"));
                
                log.info("✅ Got {} payments from comprehensive report for property {}", 
                        result.getItems().size(), propertyId);
                
                return result;
            }
            
            throw new RuntimeException("Failed to get all-payments report from PayProp");
            
        } catch (Exception e) {
            log.error("❌ All-payments report failed for property {}: {}", propertyId, e.getMessage());
            throw new RuntimeException("All-payments report failed", e);
        }
    }

    /**
     * ✅ UPDATED: Create payment from actual PayProp payment transaction data
     */
    private Payment createPaymentFromActualPayPropData(Map<String, Object> data) {
        Payment payment = new Payment();
        
        payment.setPayPropPaymentId((String) data.get("payment_id")); // Note: might be "id" or "payment_id"
        
        // ✅ CORRECT: Actual payment amounts and dates
        Object amount = data.get("amount");
        if (amount instanceof Number) {
            payment.setAmount(new BigDecimal(amount.toString()));
        }
        
        // Try different date field names from actual PayProp responses
        payment.setPaymentDate(parsePayPropDate((String) data.get("payment_date")));
        payment.setReconciliationDate(parsePayPropDate((String) data.get("reconciliation_date")));
        payment.setRemittanceDate(parsePayPropDate((String) data.get("remittance_date")));
        
        // Payment details
        payment.setDescription((String) data.get("description"));
        payment.setReference((String) data.get("reference"));
        payment.setStatus((String) data.get("status"));
        payment.setCategoryId((String) data.get("category_id"));
        
        // Additional fields that might be in actual payment data
        Object taxAmount = data.get("tax_amount");
        if (taxAmount instanceof Number) {
            payment.setTaxAmount(new BigDecimal(taxAmount.toString()));
        }
        
        Object commissionAmount = data.get("commission_amount");
        if (commissionAmount instanceof Number) {
            payment.setCommissionAmount(new BigDecimal(commissionAmount.toString()));
        }
        
        // ✅ CORRECT: Property relationship
        String propertyId = (String) data.get("property_id");
        if (propertyId != null) {
            Optional<Property> propertyOpt = propertyService.findByPayPropId(propertyId);
            if (propertyOpt.isPresent()) {
                payment.setPropertyId(propertyOpt.get().getId());
            }
        }
        
        // ✅ CORRECT: Beneficiary relationship
        Object beneficiaryInfo = data.get("beneficiary_info");
        if (beneficiaryInfo instanceof Map) {
            Map<String, Object> beneficiary = (Map<String, Object>) beneficiaryInfo;
            String beneficiaryPayPropId = (String) beneficiary.get("id");
            if (beneficiaryPayPropId != null) {
                Customer customer = customerService.findByPayPropEntityId(beneficiaryPayPropId);
                if (customer != null) {
                    payment.setBeneficiaryId(customer.getCustomerId().longValue());
                }
            }
        }
        
        // ✅ CORRECT: Tenant relationship (if this is a rent payment)
        String tenantId = (String) data.get("tenant_id");
        if (tenantId != null && payment.getPropertyId() != null) {
            // Find tenant by PayProp ID and link to payment
            Customer tenant = customerService.findByPayPropEntityId(tenantId);
            if (tenant != null) {
                payment.setTenantId(tenant.getCustomerId().longValue());
            }
        }
        
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        
        return payment;
    }

    /**
     * ✅ UPDATED: Sync actual payments to database
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncActualPaymentsToDatabase(Long initiatedBy) {
        try {
            int page = 1;
            int totalProcessed = 0;
            int totalCreated = 0;
            int totalUpdated = 0;
            int totalErrors = 0;
            
            while (true) {
                PayPropExportResult exportResult = exportActualPaymentsFromPayProp(page, 25);
                
                if (exportResult.getItems().isEmpty()) {
                    break;
                }

                for (Map<String, Object> paymentData : exportResult.getItems()) {
                    try {
                        boolean isNew = createOrUpdateActualPayment(paymentData, initiatedBy);
                        if (isNew) totalCreated++; else totalUpdated++;
                        totalProcessed++;
                    } catch (Exception e) {
                        totalErrors++;
                        log.error("Failed to sync actual payment {}: {}", paymentData.get("id"), e.getMessage());
                    }
                }
                page++;
            }

            Map<String, Object> details = Map.of(
                "processed", totalProcessed,
                "created", totalCreated, 
                "updated", totalUpdated,
                "errors", totalErrors
            );

            return totalErrors == 0 ? 
                SyncResult.success("Actual payments synced successfully", details) : 
                SyncResult.partial("Actual payments synced with some errors", details);
                
        } catch (Exception e) {
            return SyncResult.failure("Actual payments sync failed: " + e.getMessage());
        }
    }

    /**
     * ✅ HELPER: Create or update actual payment
     */
    private boolean createOrUpdateActualPayment(Map<String, Object> paymentData, Long initiatedBy) {
        String payPropPaymentId = (String) paymentData.get("payment_id");
        if (payPropPaymentId == null) {
            payPropPaymentId = (String) paymentData.get("id"); // Fallback
        }
        
        if (payPropPaymentId == null) {
            throw new RuntimeException("Payment missing ID field");
        }
        
        // Check if payment exists
        Payment existingPayment = paymentRepository.findByPayPropPaymentId(payPropPaymentId);
        
        if (existingPayment != null) {
            updatePaymentFromActualPayPropData(existingPayment, paymentData);
            paymentRepository.save(existingPayment);
            return false; // Updated
        } else {
            Payment newPayment = createPaymentFromActualPayPropData(paymentData);
            paymentRepository.save(newPayment);
            return true; // Created
        }
    }

    /**
     * ✅ HELPER: Update existing payment with actual PayProp data
     */
    private void updatePaymentFromActualPayPropData(Payment payment, Map<String, Object> data) {
        // Update amount and dates
        Object amount = data.get("amount");
        if (amount instanceof Number) {
            payment.setAmount(new BigDecimal(amount.toString()));
        }
        
        payment.setPaymentDate(parsePayPropDate((String) data.get("payment_date")));
        payment.setReconciliationDate(parsePayPropDate((String) data.get("reconciliation_date")));
        payment.setRemittanceDate(parsePayPropDate((String) data.get("remittance_date")));
        
        // Update status and references
        payment.setStatus((String) data.get("status"));
        payment.setDescription((String) data.get("description"));
        payment.setReference((String) data.get("reference"));
        
        payment.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * ✅ ENHANCEMENT 7: Create or update beneficiary balance
     */
    private void createOrUpdateBeneficiaryBalance(Map<String, Object> balanceData) {
        String beneficiaryPayPropId = (String) balanceData.get("beneficiary_id");
        String propertyPayPropId = (String) balanceData.get("property_id");
        
        // Find your beneficiary and property
        Beneficiary beneficiary = beneficiaryRepository.findByPayPropBeneficiaryId(beneficiaryPayPropId);
        Optional<Property> propertyOpt = propertyService.findByPayPropId(propertyPayPropId);
        
        if (beneficiary != null && propertyOpt.isPresent()) {
            Property property = propertyOpt.get();
            
            // Check if balance record exists
            BeneficiaryBalance existingBalance = beneficiaryBalanceRepository
                .findByBeneficiaryIdAndPropertyId(beneficiary.getId(), property.getId());
            
            Object balanceAmount = balanceData.get("balance");
            BigDecimal balance = balanceAmount instanceof Number ? 
                new BigDecimal(balanceAmount.toString()) : BigDecimal.ZERO;
            
            if (existingBalance != null) {
                existingBalance.setBalanceAmount(balance);
                existingBalance.setBalanceDate(LocalDate.now());
                existingBalance.setLastUpdated(LocalDateTime.now());
                beneficiaryBalanceRepository.save(existingBalance);
            } else {
                BeneficiaryBalance newBalance = new BeneficiaryBalance();
                newBalance.setBeneficiaryId(beneficiary.getId());
                newBalance.setPropertyId(property.getId());
                newBalance.setBalanceAmount(balance);
                newBalance.setBalanceDate(LocalDate.now());
                newBalance.setLastUpdated(LocalDateTime.now());
                beneficiaryBalanceRepository.save(newBalance);
            }
        }
    }

    /**
     * ✅ UTILITY: Parse PayProp date strings
     */
    private LocalDate parsePayPropDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateString);
        } catch (Exception e) {
            log.warn("Could not parse PayProp date: {}", dateString);
            return null;
        }
    }
    
    // ===== EXISTING PROPERTY SYNC METHODS (UNCHANGED) =====
    
    public String syncPropertyToPayProp(Long propertyId) {
        Property property = propertyService.findById(propertyId);
        if (property == null) {
            throw new IllegalArgumentException("Property not found: " + propertyId);
        }
        
        try {
            // Convert to PayProp format
            PayPropPropertyDTO dto = convertPropertyToPayPropFormat(property);
            
            // Make OAuth2 authenticated API call
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<PayPropPropertyDTO> request = new HttpEntity<>(dto, headers);
            
            System.out.println("🏠 Syncing property to PayProp: " + property.getPropertyName());
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                payPropApiBase + "/entity/property", 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String payPropId = (String) response.getBody().get("id");
                
                // FIXED: Update property with PayProp ID using duplicate key handling
                property.setPayPropId(payPropId);
                try {
                    propertyService.save(property);
                    System.out.println("✅ Property synced successfully! PayProp ID: " + payPropId);
                } catch (DataIntegrityViolationException e) {
                    log.warn("Property with PayProp ID {} already exists when saving sync result, skipping save", payPropId);
                    System.out.println("⚠️ Property synced to PayProp but already exists locally with PayProp ID: " + payPropId);
                }
                
                return payPropId;
            }
            
            throw new RuntimeException("Failed to create property in PayProp");
            
        } catch (HttpClientErrorException e) {
            System.err.println("❌ PayProp API error: " + e.getResponseBodyAsString());
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            System.err.println("❌ Property sync failed: " + e.getMessage());
            throw new RuntimeException("Property sync failed", e);
        }
    }

    /**
    * NEW: Export payments for a specific property to find owners
    */
    public PayPropExportResult exportPaymentsByProperty(String propertyId) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + "/export/payments?property_id=" + propertyId + 
                        "&include_beneficiary_info=true&page=1&rows=100";
            
            log.info("📥 Exporting payments for property: {}", propertyId);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                PayPropExportResult result = new PayPropExportResult();
                result.setItems((List<Map<String, Object>>) responseBody.get("items"));
                result.setPagination((Map<String, Object>) responseBody.get("pagination"));
                
                log.info("✅ Found {} payments for property {}", result.getItems().size(), propertyId);
                return result;
            }
            
            throw new RuntimeException("Failed to export payments for property");
            
        } catch (Exception e) {
            log.error("❌ Property payment export failed: {}", e.getMessage());
            throw new RuntimeException("Property payment export failed", e);
        }
    }
    
    public void updatePropertyInPayProp(Long propertyId) {
        Property property = propertyService.findById(propertyId);
        if (property == null || property.getPayPropId() == null) {
            throw new IllegalArgumentException("Property not synced with PayProp");
        }
        
        try {
            PayPropPropertyDTO dto = convertPropertyToPayPropFormat(property);
            
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<PayPropPropertyDTO> request = new HttpEntity<>(dto, headers);
            
            restTemplate.put(
                payPropApiBase + "/entity/property/" + property.getPayPropId(), 
                request
            );
            
            // FIXED: Add duplicate key handling for update save
            try {
                propertyService.save(property);
                System.out.println("✅ Property updated in PayProp: " + property.getPayPropId());
            } catch (DataIntegrityViolationException e) {
                log.warn("Property with PayProp ID {} already exists when saving update, skipping save", property.getPayPropId());
                System.out.println("✅ Property updated in PayProp (local save skipped due to duplicate): " + property.getPayPropId());
            }
            
        } catch (HttpClientErrorException e) {
            System.err.println("❌ Failed to update property in PayProp: " + e.getResponseBodyAsString());
            throw new RuntimeException("Failed to update property in PayProp: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("Property update failed", e);
        }
    }

    /**
     * ✅ NEW: Enhanced export with complete rent and occupancy data
     */
    public PayPropExportResult exportPropertiesFromPayPropEnhanced(int page, int rows) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            // Enhanced export includes settings, tenancies, commission, contract amounts, balances, and processing info

            String url = payPropApiBase + "/export/properties" +
            "?include_settings=true" +
            "&include_active_tenancies=true" +
            "&include_commission=true" +
            "&include_contract_amount=true" +
            "&include_balance=true" +
            "&include_last_processing_info=true" +
            "&page=" + page + "&rows=" + Math.min(rows, 25);
            
            System.out.println("📥 Enhanced export from PayProp - Page " + page);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                PayPropExportResult result = new PayPropExportResult();
                result.setItems((List<Map<String, Object>>) responseBody.get("items"));
                result.setPagination((Map<String, Object>) responseBody.get("pagination"));
                
                System.out.println("✅ Enhanced export: " + result.getItems().size() + " properties with full data (settings, tenancies, commission, balances)");
                return result;
            }
            
            throw new RuntimeException("Failed to export enhanced properties from PayProp");
            
        } catch (Exception e) {
            System.err.println("❌ Enhanced property export failed: " + e.getMessage());
            // Fallback to regular export
            return exportPropertiesFromPayProp(page, rows);
        }
    }

    /**
     * ✅ NEW: Get comprehensive property statistics with rent and occupancy data
     */
    public Map<String, Object> getPropertyStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // Get all properties with enhanced data
            int totalProperties = 0;
            int propertiesWithRent = 0;
            int occupiedProperties = 0;
            BigDecimal totalMonthlyRent = BigDecimal.ZERO;
            
            int page = 1;
            while (true) {
                PayPropExportResult result = exportPropertiesFromPayPropEnhanced(page, 25);
                if (result.getItems().isEmpty()) break;
                
                for (Map<String, Object> property : result.getItems()) {
                    totalProperties++;
                    
                    // Check for rent data
                    Map<String, Object> settings = (Map<String, Object>) property.get("settings");
                    if (settings != null && settings.get("monthly_payment") != null) {
                        propertiesWithRent++;
                        Object monthlyPayment = settings.get("monthly_payment");
                        if (monthlyPayment instanceof Number) {
                            totalMonthlyRent = totalMonthlyRent.add(new BigDecimal(monthlyPayment.toString()));
                        }
                    }
                    
                    // Check occupancy
                    List<Map<String, Object>> activeTenancies = (List<Map<String, Object>>) property.get("active_tenancies");
                    if (activeTenancies != null && !activeTenancies.isEmpty()) {
                        occupiedProperties++;
                    }
                }
                page++;
            }
            
            stats.put("totalProperties", totalProperties);
            stats.put("propertiesWithRent", propertiesWithRent);
            stats.put("occupiedProperties", occupiedProperties);
            stats.put("vacantProperties", totalProperties - occupiedProperties);
            stats.put("totalMonthlyRent", totalMonthlyRent);
            stats.put("averageRent", propertiesWithRent > 0 ? totalMonthlyRent.divide(new BigDecimal(propertiesWithRent), 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO);
            stats.put("occupancyRate", totalProperties > 0 ? (occupiedProperties * 100.0 / totalProperties) : 0);
            stats.put("rentDataQuality", propertiesWithRent * 100.0 / totalProperties);
            
            return stats;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get property statistics: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ NEW: Get complete property data with all settings and tenancy info
     */
    public Map<String, Object> getCompletePropertyData(String propertyId) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + "/entity/property/" + propertyId + "?include_settings=true&include_active_tenants=true";
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> propertyData = response.getBody();
                
                // Add occupancy detection
                List<Map<String, Object>> activeTenants = (List<Map<String, Object>>) propertyData.get("active_tenants");
                propertyData.put("is_occupied", activeTenants != null && !activeTenants.isEmpty());
                
                return propertyData;
            }
            
            throw new RuntimeException("Failed to get complete property data");
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get complete property data: " + e.getMessage(), e);
        }
    }
    
    // ===== TENANT SYNC METHODS - TEMPORARILY DISABLED =====
    
    public String syncTenantToPayProp(Long tenantId) {
        // TEMPORARILY DISABLED: Due to PayProp permission restrictions
        log.warn("Tenant sync to PayProp is temporarily disabled due to insufficient permissions");
        throw new UnsupportedOperationException("Tenant sync to PayProp is temporarily disabled (read-only mode). " +
            "PayProp API returned 'Denied (create:entity:tenant)' - insufficient permissions to create tenants.");
    }
    
    public void updateTenantInPayProp(Long tenantId) {
        // TEMPORARILY DISABLED: Due to PayProp permission restrictions
        log.warn("Tenant update in PayProp is temporarily disabled due to insufficient permissions");
        throw new UnsupportedOperationException("Tenant update in PayProp is temporarily disabled (read-only mode). " +
            "PayProp API permissions do not allow tenant modifications.");
    }
    
    // ===== BENEFICIARY SYNC METHODS =====
    
    public String syncBeneficiaryToPayProp(Long propertyOwnerId) {
        PropertyOwner owner = propertyOwnerService.findById(propertyOwnerId);
        if (owner == null) {
            throw new IllegalArgumentException("Property owner not found");
        }
        
        // FIXED: Use your actual validation method
        if (!isValidForPayPropSync(owner)) {
            throw new IllegalArgumentException("Beneficiary not ready for sync - missing required fields");
        }
        
        try {
            PayPropBeneficiaryDTO dto = convertBeneficiaryToPayPropFormat(owner);
            
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<PayPropBeneficiaryDTO> request = new HttpEntity<>(dto, headers);
            
            System.out.println("🏦 Syncing beneficiary to PayProp: " + owner.getFirstName() + " " + owner.getLastName());
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                payPropApiBase + "/entity/beneficiary", 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String payPropId = (String) response.getBody().get("id");
                
                // FIXED: Update property owner with PayProp ID using duplicate key handling
                owner.setPayPropId(payPropId);
                try {
                    propertyOwnerService.save(owner);
                    System.out.println("✅ Beneficiary synced successfully! PayProp ID: " + payPropId);
                } catch (DataIntegrityViolationException e) {
                    log.warn("PropertyOwner with PayProp ID {} already exists when saving sync result, skipping save", payPropId);
                    System.out.println("⚠️ Beneficiary synced to PayProp but already exists locally with PayProp ID: " + payPropId);
                }
                
                return payPropId;
            }
            
            throw new RuntimeException("Failed to create beneficiary in PayProp");
            
        } catch (HttpClientErrorException e) {
            System.err.println("❌ PayProp API error: " + e.getResponseBodyAsString());
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            System.err.println("❌ Beneficiary sync failed: " + e.getMessage());
            throw new RuntimeException("Beneficiary sync failed", e);
        }
    }
    
    // ===== EXPORT METHODS (Bulk Data Retrieval) =====
    
    /**
     * Export properties from PayProp (handles hashed IDs)
     */
    public PayPropExportResult exportPropertiesFromPayProp(int page, int rows) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + "/export/properties?page=" + page + "&rows=" + Math.min(rows, 25);
            
            System.out.println("📥 Exporting properties from PayProp - Page " + page);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                PayPropExportResult result = new PayPropExportResult();
                result.setItems((List<Map<String, Object>>) responseBody.get("items"));
                result.setPagination((Map<String, Object>) responseBody.get("pagination"));
                
                System.out.println("✅ Exported " + result.getItems().size() + " properties from PayProp");
                
                return result;
            }
            
            throw new RuntimeException("Failed to export properties from PayProp");
            
        } catch (HttpClientErrorException e) {
            System.err.println("❌ PayProp export error: " + e.getResponseBodyAsString());
            throw new RuntimeException("PayProp export error: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            System.err.println("❌ Property export failed: " + e.getMessage());
            throw new RuntimeException("Property export failed", e);
        }
    }
    
    /**
     * Export tenants from PayProp (handles hashed IDs)
     */
    public PayPropExportResult exportTenantsFromPayProp(int page, int rows) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + "/export/tenants?page=" + page + "&rows=" + Math.min(rows, 25);
            
            System.out.println("📥 Exporting tenants from PayProp - Page " + page);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                PayPropExportResult result = new PayPropExportResult();
                result.setItems((List<Map<String, Object>>) responseBody.get("items"));
                result.setPagination((Map<String, Object>) responseBody.get("pagination"));
                
                System.out.println("✅ Exported " + result.getItems().size() + " tenants from PayProp");
                
                return result;
            }
            
            throw new RuntimeException("Failed to export tenants from PayProp");
            
        } catch (Exception e) {
            System.err.println("❌ Tenant export failed: " + e.getMessage());
            throw new RuntimeException("Tenant export failed", e);
        }
    }
    
    /**
     * Export beneficiaries from PayProp (handles hashed IDs)
     */
    public PayPropExportResult exportBeneficiariesFromPayProp(int page, int rows) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + "/export/beneficiaries?page=" + page + "&rows=" + Math.min(rows, 25);
            
            System.out.println("📥 Exporting beneficiaries from PayProp - Page " + page);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                PayPropExportResult result = new PayPropExportResult();
                result.setItems((List<Map<String, Object>>) responseBody.get("items"));
                result.setPagination((Map<String, Object>) responseBody.get("pagination"));
                
                System.out.println("✅ Exported " + result.getItems().size() + " beneficiaries from PayProp");
                
                return result;
            }
            
            throw new RuntimeException("Failed to export beneficiaries from PayProp");
            
        } catch (Exception e) {
            System.err.println("❌ Beneficiary export failed: " + e.getMessage());
            throw new RuntimeException("Beneficiary export failed", e);
        }
    }

    /**
     * NEW: Export invoices from PayProp for relationship validation
     */
    public PayPropExportResult exportInvoicesFromPayProp(int page, int rows) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + "/export/invoices?page=" + page + "&rows=" + Math.min(rows, 25);
            
            System.out.println("📥 Exporting invoices from PayProp - Page " + page);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                PayPropExportResult result = new PayPropExportResult();
                result.setItems((List<Map<String, Object>>) responseBody.get("items"));
                result.setPagination((Map<String, Object>) responseBody.get("pagination"));
                
                System.out.println("✅ Exported " + result.getItems().size() + " invoices from PayProp");
                
                return result;
            }
            
            throw new RuntimeException("Failed to export invoices from PayProp");
            
        } catch (Exception e) {
            System.err.println("❌ Invoice export failed: " + e.getMessage());
            throw new RuntimeException("Invoice export failed", e);
        }
    }

    /**
     * NEW: Export payments from PayProp for relationship validation
     */
    public PayPropExportResult exportPaymentsFromPayProp(int page, int rows) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + "/export/payments?include_beneficiary_info=true&page=" + page + "&rows=" + Math.min(rows, 25);
            
            System.out.println("📥 Exporting payments from PayProp - Page " + page);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                PayPropExportResult result = new PayPropExportResult();
                result.setItems((List<Map<String, Object>>) responseBody.get("items"));
                result.setPagination((Map<String, Object>) responseBody.get("pagination"));
                
                System.out.println("✅ Exported " + result.getItems().size() + " payments from PayProp");
                
                return result;
            }
            
            throw new RuntimeException("Failed to export payments from PayProp");
            
        } catch (Exception e) {
            System.err.println("❌ Payment export failed: " + e.getMessage());
            throw new RuntimeException("Payment export failed", e);
        }
    }

    /**
     * Export tenants for a specific property from PayProp
     */
    public PayPropExportResult exportTenantsByProperty(String propertyId) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + "/export/tenants?property_id=" + propertyId;
            
            System.out.println("📥 Exporting tenants for property: " + propertyId);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                PayPropExportResult result = new PayPropExportResult();
                result.setItems((List<Map<String, Object>>) responseBody.get("items"));
                result.setPagination((Map<String, Object>) responseBody.get("pagination"));
                
                System.out.println("✅ Found " + result.getItems().size() + " tenants for property " + propertyId);
                
                return result;
            }
            
            throw new RuntimeException("Failed to export tenants for property");
            
        } catch (Exception e) {
            System.err.println("❌ Tenant export for property failed: " + e.getMessage());
            throw new RuntimeException("Tenant export failed", e);
        }
    }

    // ===== FILE AND DOCUMENT METHODS (UNCHANGED) =====

    /**
     * Get PayProp attachments for a customer entity
     */
    public List<PayPropAttachment> getPayPropAttachments(String entityType, String entityId) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + "/attachments/" + entityType + "/" + entityId;
            
            log.info("📎 Getting PayProp attachments for {} entity: {}", entityType, entityId);
            
            ResponseEntity<PayPropAttachmentResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, request, PayPropAttachmentResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<PayPropAttachment> attachments = response.getBody().getData();
                log.info("✅ Found {} attachments for {} {}", attachments.size(), entityType, entityId);
                return attachments;
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("❌ Failed to get PayProp attachments: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get PayProp attachments for a customer entity with pagination
     */
    public PayPropAttachmentResponse getPayPropAttachmentsWithPagination(String entityType, String entityId, int page, int rows) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + "/attachments/" + entityType + "/" + entityId + 
                        "?page=" + page + "&rows=" + rows;
            
            log.info("📎 Getting PayProp attachments for {} entity: {} (page {}, rows {})", 
                entityType, entityId, page, rows);
            
            ResponseEntity<PayPropAttachmentResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, request, PayPropAttachmentResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                PayPropAttachmentResponse result = response.getBody();
                log.info("✅ Found {} attachments for {} {} (page {} of {})", 
                    result.getData().size(), entityType, entityId, page, 
                    result.getTotal() > 0 ? (result.getTotal() / rows) + 1 : 1);
                return result;
            }
            
            return new PayPropAttachmentResponse();
            
        } catch (Exception e) {
            log.error("❌ Failed to get PayProp attachments: {}", e.getMessage());
            return new PayPropAttachmentResponse();
        }
    }

    /**
     * Download PayProp attachment by external ID
     */
    public byte[] downloadPayPropAttachment(String externalId) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + "/attachments/" + externalId;
            
            log.info("📥 Downloading PayProp attachment: {}", externalId);
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                url, HttpMethod.GET, request, byte[].class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("✅ Downloaded PayProp attachment: {} ({} bytes)", externalId, 
                    response.getBody() != null ? response.getBody().length : 0);
                return response.getBody();
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("❌ Failed to download PayProp attachment {}: {}", externalId, e.getMessage());
            return null;
        }
    }

    /**
     * Generate PayProp owner statement PDF
     */
    public byte[] generateOwnerStatementPDF(String propertyId, String beneficiaryId, 
                                           String fromDate, String toDate) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + "/documents/pdf/owner-statement?" +
                        "property_id=" + propertyId + 
                        "&beneficiary_id=" + beneficiaryId;
            
            if (fromDate != null) url += "&from_date=" + fromDate;
            if (toDate != null) url += "&to_date=" + toDate;
            
            log.info("📊 Generating PayProp owner statement for property {} beneficiary {}", 
                propertyId, beneficiaryId);
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                url, HttpMethod.GET, request, byte[].class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("✅ Generated owner statement PDF ({} bytes)", 
                    response.getBody() != null ? response.getBody().length : 0);
                return response.getBody();
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("❌ Failed to generate owner statement: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Generate PayProp agency invoice PDF
     */
    public byte[] generateAgencyInvoicePDF(int year, int month) {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String url = payPropApiBase + "/documents/pdf/agency-invoice?" +
                        "year=" + year + "&month=" + month;
            
            log.info("📊 Generating PayProp agency invoice for {}/{}", year, month);
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                url, HttpMethod.GET, request, byte[].class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("✅ Generated agency invoice PDF ({} bytes)", 
                    response.getBody() != null ? response.getBody().length : 0);
                return response.getBody();
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("❌ Failed to generate agency invoice: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Remove tag from property using PayProp API
     * Uses the PayProp endpoint: DELETE /tags/entities/property/{property_id}/{tag_id}
     */
    public void removeTagFromProperty(String payPropPropertyId, String tagId) throws Exception {
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                payPropApiBase + "/tags/entities/property/" + payPropPropertyId + "/" + tagId,
                HttpMethod.DELETE,
                request,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Successfully removed PayProp tag {} from property {}", tagId, payPropPropertyId);
            } else {
                throw new RuntimeException("Unexpected response status: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            log.error("Failed to remove tag {} from property {}: {}", tagId, payPropPropertyId, e.getResponseBodyAsString());
            throw new RuntimeException("Failed to remove tag from property: " + e.getResponseBodyAsString(), e);
        }
    }
    
    // ===== CONVERSION METHODS (UNCHANGED BUT NEEDED FOR COMPILATION) =====
    
    private PayPropPropertyDTO convertPropertyToPayPropFormat(Property property) {
        PayPropPropertyDTO dto = new PayPropPropertyDTO();
        
        // Basic fields
        dto.setName(property.getPropertyName());
        
        // FIXED: Ensure customer_id is valid (non-null, non-empty, alphanumeric with dash/underscore)
        String customerId = property.getCustomerId();
        if (customerId == null || customerId.trim().isEmpty()) {
            customerId = "CRM_" + property.getId(); // Generate valid customer_id
        }
        // Sanitize customer_id to match PayProp pattern ^[a-zA-Z0-9_-]+$
        customerId = customerId.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (customerId.length() == 0) {
            customerId = "CRM_" + property.getId();
        }
        dto.setCustomer_id(customerId);
        
        dto.setCustomer_reference(property.getCustomerReference());
        dto.setAgent_name(property.getAgentName());
        dto.setNotes(property.getComment());
        
        // Convert address to nested structure
        PayPropAddressDTO address = new PayPropAddressDTO();
        address.setAddress_line_1(property.getAddressLine1());
        address.setAddress_line_2(property.getAddressLine2());
        address.setAddress_line_3(property.getAddressLine3());
        address.setCity(property.getCity());
        address.setPostal_code(property.getPostcode());
        address.setCountry_code(property.getCountryCode());
        
        // FIXED: Handle state field - provide default if null/empty to avoid PayProp validation error
        String state = property.getState();
        if (state == null || state.trim().isEmpty()) {
            state = "N/A"; // Minimum 1 character required by PayProp
        }
        address.setState(state);
        dto.setAddress(address);
        
        // Convert settings to nested structure
        PayPropSettingsDTO settings = new PayPropSettingsDTO();
        
        // FIXED: Handle your actual boolean field conversion safely
        try {
            Boolean enablePayments = convertYNToBoolean(property.getEnablePayments()); // varchar(1) - needs conversion
            if (enablePayments != null) {
                settings.setEnable_payments(enablePayments);
            }
        } catch (Exception e) {
            log.warn("Could not convert enable_payments: {}", e.getMessage());
            settings.setEnable_payments(false); // Safe default
        }

        try {
            Boolean holdOwnerFunds = convertYNToBoolean(property.getHoldOwnerFunds()); // varchar(1) - needs conversion
            if (holdOwnerFunds != null) {
                settings.setHold_owner_funds(holdOwnerFunds);
            }
        } catch (Exception e) {
            log.warn("Could not convert hold_owner_funds: {}", e.getMessage());
            settings.setHold_owner_funds(false); // Safe default
        }

        // FIXED: verify_payments is already Boolean in database - no conversion needed
        Boolean verifyPayments = convertYNToBoolean(property.getVerifyPayments());
        settings.setVerify_payments(verifyPayments != null ? verifyPayments : false);

        settings.setMonthly_payment(property.getMonthlyPayment());
        settings.setMinimum_balance(property.getPropertyAccountMinimumBalance());
        
        // FIXED: Convert LocalDate to String to avoid array serialization issues
        // Convert LocalDate to String to avoid array serialization issues
        if (property.getListedFrom() != null) {
            settings.setListing_from(property.getListedFrom().toString()); // Convert to ISO string
        }
        if (property.getListedUntil() != null) {
            settings.setListing_to(property.getListedUntil().toString()); // Convert to ISO string
        }
        
        dto.setSettings(settings);
        
        return dto;
    }
    
    private PayPropTenantDTO convertTenantToPayPropFormat(Tenant tenant) {
        PayPropTenantDTO dto = new PayPropTenantDTO();
        
        // FIXED: Handle your actual enum values
        if (tenant.getAccountType() != null) {
            dto.setAccount_type(tenant.getAccountType().toString().toLowerCase());
        } else {
            dto.setAccount_type("individual"); // Default
        }
        
        if ("individual".equals(dto.getAccount_type())) {
            dto.setFirst_name(tenant.getFirstName());
            dto.setLast_name(tenant.getLastName());
        } else {
            dto.setBusiness_name(tenant.getBusinessName());
        }
        
        // Contact information
        dto.setEmail_address(tenant.getEmailAddress());
        dto.setMobile_number(formatMobileForPayProp(tenant.getMobileNumber()));
        dto.setPhone(tenant.getPhoneNumber());
        dto.setFax(tenant.getFaxNumber());
        dto.setCustomer_id(tenant.getPayPropCustomerId());
        dto.setCustomer_reference(tenant.getCustomerReference());
        dto.setComment(tenant.getComment());
        dto.setDate_of_birth(tenant.getDateOfBirth());
        dto.setId_number(tenant.getIdNumber());
        dto.setVat_number(tenant.getVatNumber());
        
        // FIXED: Simple assignment - DTO expects Boolean, convertYNToBoolean returns Boolean
        dto.setNotify_email(convertYNToBoolean(tenant.getNotifyEmail()));
        dto.setNotify_sms(convertYNToBoolean(tenant.getNotifyText()));
        
        // Address
        PayPropAddressDTO address = new PayPropAddressDTO();
        address.setAddress_line_1(tenant.getAddressLine1());
        address.setAddress_line_2(tenant.getAddressLine2());
        address.setAddress_line_3(tenant.getAddressLine3());
        address.setCity(tenant.getCity());
        address.setPostal_code(tenant.getPostcode());
        address.setCountry_code(tenant.getCountry());
        dto.setAddress(address);
        
        // FIXED: Bank account handling for your bit(1) field
        Boolean hasBankAccount = convertBitToBoolean(tenant.getHasBankAccount());
        if (Boolean.TRUE.equals(hasBankAccount)) {
            PayPropBankAccountDTO bankAccount = new PayPropBankAccountDTO();
            bankAccount.setAccount_name(tenant.getAccountName());
            bankAccount.setAccount_number(tenant.getAccountNumber());
            bankAccount.setBranch_code(tenant.getSortCode());
            bankAccount.setBank_name(tenant.getBankName());
            bankAccount.setBranch_name(tenant.getBranchName());
            dto.setBank_account(bankAccount);
            dto.setHas_bank_account(true);
        }
        
        return dto;
    }
    
    private PayPropBeneficiaryDTO convertBeneficiaryToPayPropFormat(PropertyOwner owner) {
        PayPropBeneficiaryDTO dto = new PayPropBeneficiaryDTO();
        
        // FIXED: Handle your actual enum values with case conversion
        if (owner.getAccountType() != null) {
            dto.setAccount_type(owner.getAccountType().toString().toLowerCase());
        } else {
            dto.setAccount_type("individual"); // Default
        }
        
        // FIXED: Handle payment method enum case conversion
        if (owner.getPaymentMethod() != null) {
            dto.setPayment_method(owner.getPaymentMethod().toString().toLowerCase());
        } else {
            dto.setPayment_method("local"); // Default
        }
        
        if ("individual".equals(dto.getAccount_type())) {
            dto.setFirst_name(owner.getFirstName());
            dto.setLast_name(owner.getLastName());
        } else {
            dto.setBusiness_name(owner.getBusinessName());
        }
        
        // Contact information
        dto.setEmail_address(owner.getEmailAddress());
        dto.setMobile(formatMobileForPayProp(owner.getMobile()));
        dto.setPhone(owner.getPhone());
        dto.setFax(owner.getFax());
        dto.setCustomer_id(owner.getPayPropCustomerId());
        dto.setCustomer_reference(owner.getCustomerReference());
        dto.setComment(owner.getComment());
        dto.setId_number(owner.getIdNumber());
        dto.setVat_number(owner.getVatNumber());
        
        // Communication preferences
        PayPropCommunicationDTO communication = new PayPropCommunicationDTO();
        PayPropEmailDTO email = new PayPropEmailDTO();
        
        // FIXED: Simple assignment - DTO expects Boolean, owner methods return Boolean
        email.setEnabled(owner.getEmailEnabled());
        email.setPayment_advice(owner.getPaymentAdviceEnabled());
        
        communication.setEmail(email);
        dto.setCommunication_preferences(communication);
        
        // Address (required for international payments and cheque)
        if ("international".equals(dto.getPayment_method()) || "cheque".equals(dto.getPayment_method())) {
            PayPropAddressDTO address = new PayPropAddressDTO();
            address.setAddress_line_1(owner.getAddressLine1());
            address.setAddress_line_2(owner.getAddressLine2());
            address.setAddress_line_3(owner.getAddressLine3());
            address.setCity(owner.getCity());
            address.setState(owner.getState());
            address.setPostal_code(owner.getPostalCode());
            address.setCountry_code(owner.getCountry());
            dto.setAddress(address);
        }
        
        // Bank account
        PayPropBankAccountDTO bankAccount = new PayPropBankAccountDTO();
        bankAccount.setAccount_name(owner.getBankAccountName());
        
        if ("local".equals(dto.getPayment_method())) {
            bankAccount.setAccount_number(owner.getBankAccountNumber());
            bankAccount.setBranch_code(owner.getBranchCode());
            bankAccount.setBank_name(owner.getBankName());
            bankAccount.setBranch_name(owner.getBranchName());
        } else if ("international".equals(dto.getPayment_method())) {
            if (owner.getIban() != null && !owner.getIban().isEmpty()) {
                bankAccount.setIban(owner.getIban());
            } else {
                bankAccount.setAccount_number(owner.getInternationalAccountNumber());
            }
            bankAccount.setSwift_code(owner.getSwiftCode());
            bankAccount.setCountry_code(owner.getBankCountryCode());
            bankAccount.setBank_name(owner.getBankName());
        }
        
        dto.setBank_account(bankAccount);
        
        return dto;
    }
    
    // ===== VALIDATION METHODS (UNCHANGED) =====
    
    private boolean isValidForPayPropSync(Tenant tenant) {
        // Check account type specific requirements
        if (tenant.getAccountType() != null) {
            String accountType = tenant.getAccountType().toString().toLowerCase();
            if ("individual".equals(accountType)) {
                if (tenant.getFirstName() == null || tenant.getFirstName().trim().isEmpty() ||
                    tenant.getLastName() == null || tenant.getLastName().trim().isEmpty()) {
                    log.warn("Tenant {} missing required first/last name for individual account", tenant.getId());
                    return false;
                }
            } else if ("business".equals(accountType)) {
                if (tenant.getBusinessName() == null || tenant.getBusinessName().trim().isEmpty()) {
                    log.warn("Tenant {} missing required business name for business account", tenant.getId());
                    return false;
                }
            }
        } else {
            log.warn("Tenant {} missing account type", tenant.getId());
            return false;
        }
        
        // Check required email address
        if (tenant.getEmailAddress() == null || tenant.getEmailAddress().trim().isEmpty()) {
            log.warn("Tenant {} missing required email address", tenant.getId());
            return false;
        }
        
        // Validate email format
        if (!isValidEmail(tenant.getEmailAddress())) {
            log.warn("Tenant {} has invalid email format: {}", tenant.getId(), tenant.getEmailAddress());
            return false;
        }
        
        // FIXED: Handle bank account validation for your bit(1) field
        Boolean hasBankAccount = convertBitToBoolean(tenant.getHasBankAccount());
        if (Boolean.TRUE.equals(hasBankAccount)) {
            if (tenant.getAccountName() == null || tenant.getAccountName().trim().isEmpty()) {
                log.warn("Tenant {} has bank account but missing account name", tenant.getId());
                return false;
            }
            if (tenant.getAccountNumber() == null || tenant.getAccountNumber().trim().isEmpty()) {
                log.warn("Tenant {} has bank account but missing account number", tenant.getId());
                return false;
            }
            if (tenant.getSortCode() == null || tenant.getSortCode().trim().isEmpty()) {
                log.warn("Tenant {} has bank account but missing sort code", tenant.getId());
                return false;
            }
        }
        
        return true;
    }
    
    private boolean isValidForPayPropSync(PropertyOwner owner) {
        // Check account type specific requirements
        if (owner.getAccountType() != null) {
            String accountType = owner.getAccountType().toString().toLowerCase();
            if ("individual".equals(accountType)) {
                if (owner.getFirstName() == null || owner.getFirstName().trim().isEmpty() ||
                    owner.getLastName() == null || owner.getLastName().trim().isEmpty()) {
                    log.warn("PropertyOwner {} missing required first/last name for individual account", owner.getId());
                    return false;
                }
            } else if ("business".equals(accountType)) {
                if (owner.getBusinessName() == null || owner.getBusinessName().trim().isEmpty()) {
                    log.warn("PropertyOwner {} missing required business name for business account", owner.getId());
                    return false;
                }
            }
        } else {
            log.warn("PropertyOwner {} missing account type", owner.getId());
            return false;
        }
        
        // Check required email address
        if (owner.getEmailAddress() == null || owner.getEmailAddress().trim().isEmpty()) {
            log.warn("PropertyOwner {} missing required email address", owner.getId());
            return false;
        }
        
        // Validate email format
        if (!isValidEmail(owner.getEmailAddress())) {
            log.warn("PropertyOwner {} has invalid email format: {}", owner.getId(), owner.getEmailAddress());
            return false;
        }
        
        // Validate payment method specific requirements
        if (owner.getPaymentMethod() != null) {
            String paymentMethod = owner.getPaymentMethod().toString().toLowerCase();
            
            if ("international".equals(paymentMethod)) {
                // Address is required for international
                if (owner.getAddressLine1() == null || owner.getAddressLine1().trim().isEmpty()) {
                    log.warn("PropertyOwner {} missing address line 1 for international payment", owner.getId());
                    return false;
                }
                if (owner.getCity() == null || owner.getCity().trim().isEmpty()) {
                    log.warn("PropertyOwner {} missing city for international payment", owner.getId());
                    return false;
                }
                if (owner.getState() == null || owner.getState().trim().isEmpty()) {
                    log.warn("PropertyOwner {} missing state for international payment", owner.getId());
                    return false;
                }
                if (owner.getPostalCode() == null || owner.getPostalCode().trim().isEmpty()) {
                    log.warn("PropertyOwner {} missing postal code for international payment", owner.getId());
                    return false;
                }
                
                // Either IBAN or account number + SWIFT required
                boolean hasIban = owner.getIban() != null && !owner.getIban().trim().isEmpty();
                boolean hasAccountAndSwift = owner.getInternationalAccountNumber() != null && 
                                           owner.getSwiftCode() != null;
                
                if (!hasIban && !hasAccountAndSwift) {
                    log.warn("PropertyOwner {} missing IBAN or account number+SWIFT for international payment", owner.getId());
                    return false;
                }
            } else if ("local".equals(paymentMethod)) {
                if (owner.getBankAccountName() == null || owner.getBankAccountName().trim().isEmpty()) {
                    log.warn("PropertyOwner {} missing bank account name for local payment", owner.getId());
                    return false;
                }
                if (owner.getBankAccountNumber() == null || owner.getBankAccountNumber().trim().isEmpty()) {
                    log.warn("PropertyOwner {} missing bank account number for local payment", owner.getId());
                    return false;
                }
                if (owner.getBranchCode() == null || owner.getBranchCode().trim().isEmpty()) {
                    log.warn("PropertyOwner {} missing branch code for local payment", owner.getId());
                    return false;
                }
            }
        } else {
            log.warn("PropertyOwner {} missing payment method", owner.getId());
            return false;
        }
        
        return true;
    }
    
    /**
     * Simple email validation
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        // Basic email validation - contains @ and has text before and after
        return email.contains("@") && 
               email.indexOf("@") > 0 && 
               email.indexOf("@") < email.length() - 1 &&
               !email.startsWith("@") &&
               !email.endsWith("@");
    }
    
    // ===== UTILITY METHODS (UPDATED FOR YOUR DATABASE) =====
    
    /**
     * Convert Y/N/1/0 values to boolean - OPTIMIZED for your specific data patterns
     * Based on analysis: Properties have "Y" (88.85%) and "1" (11.15%) values
     */
    private Boolean convertYNToBoolean(Object value) {
        if (value == null) return null;
        
        // If already Boolean, return as-is
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        
        // If String, handle your actual data patterns: "Y", "N", "1", "0"
        if (value instanceof String) {
            String trimmed = ((String) value).trim().toUpperCase();
            return "Y".equals(trimmed) || "YES".equals(trimmed) || "TRUE".equals(trimmed) || "1".equals(trimmed);
        }
        
        // If Number, treat 0 as false, anything else as true
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        
        // Default: try string conversion for edge cases
        String stringValue = value.toString().trim().toUpperCase();
        return "Y".equals(stringValue) || "YES".equals(stringValue) || "TRUE".equals(stringValue) || "1".equals(stringValue);
    }
    
    /**
     * Convert ENUM values to boolean - for customer notify_email/notify_sms fields
     * Based on analysis: All customer notify_email = "Y", notify_sms = "N"
     */
    private Boolean convertEnumToBoolean(Object value) {
        if (value == null) return null;
        
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        
        String stringValue = value.toString().trim().toUpperCase();
        return "Y".equals(stringValue) || "YES".equals(stringValue) || "TRUE".equals(stringValue);
    }
    
    /**
     * Convert bit(1) to boolean - your database uses bit(1) for has_bank_account
     */
    private Boolean convertBitToBoolean(Object bitValue) {
        if (bitValue == null) return null;
        if (bitValue instanceof Boolean) return (Boolean) bitValue;
        if (bitValue instanceof Number) return ((Number) bitValue).intValue() != 0;
        if (bitValue instanceof String) return "1".equals(bitValue) || "true".equalsIgnoreCase((String) bitValue);
        return false;
    }
    
    private String formatMobileForPayProp(String mobile) {
        if (mobile == null || mobile.trim().isEmpty()) {
            return null;
        }
        
        // Remove any non-digit characters
        mobile = mobile.replaceAll("[^\\d]", "");
        
        // Add UK country code if not present
        if (!mobile.startsWith("44") && mobile.startsWith("0")) {
            mobile = "44" + mobile.substring(1);
        } else if (!mobile.startsWith("44") && !mobile.startsWith("0")) {
            mobile = "44" + mobile;
        }
        
        return mobile;
    }
    
    // ===== UNIFIED CUSTOMER SYNC METHODS =====
    // Based on analysis: Your system uses a unified 'customer' table for all entities
    
    /**
     * Sync customer as tenant to PayProp
     * Based on analysis: 31 tenants, all notify_email="Y", notify_text="N", has_bank_account=NULL
     */
    public String syncCustomerAsTenantToPayProp(Long customerId) {
        // Note: This method should work with your Customer entity that has is_tenant=1
        throw new UnsupportedOperationException("Customer-based tenant sync not yet implemented. " +
            "Your database uses unified customer table - this needs Customer entity integration.");
    }
    
    /**
     * Sync customer as property owner to PayProp
     * Based on analysis: 2 customers with is_property_owner=1, property_owners table is empty
     */
    public String syncCustomerAsPropertyOwnerToPayProp(Long customerId) {
        // Note: This method should work with your Customer entity that has is_property_owner=1
        throw new UnsupportedOperationException("Customer-based property owner sync not yet implemented. " +
            "Your database uses unified customer table - this needs Customer entity integration.");
    }
    
    // ===== BULK SYNC METHODS WITH DUPLICATE KEY HANDLING AND SEPARATE TRANSACTIONS =====
    
    /**
     * UPDATED: Sync based on your actual data
     * 296 total properties, 295 already have PayProp IDs, 1 remaining
     */
    public void syncAllReadyProperties() {
        if (!oAuth2Service.hasValidTokens()) {
            throw new IllegalStateException("No valid OAuth2 tokens. Please authorize PayProp first.");
        }
        
        // UPDATED: Based on analysis - use 'properties' table
        List<Property> readyProperties = propertyService.findAll().stream()
            .filter(p -> p.getPayPropId() == null) // Not yet synced (analysis shows 1 remaining)
            .toList();
            
        System.out.println("📋 Found " + readyProperties.size() + " properties ready for sync (Analysis: 1 expected)");
        
        int successCount = 0;
        int errorCount = 0;
        int duplicateCount = 0;
        
        for (Property property : readyProperties) {
            try {
                // Each property sync in its own transaction
                String payPropId = syncPropertyToPayPropInSeparateTransaction(property.getId());
                System.out.println("✅ Successfully synced property " + property.getId() + " -> " + payPropId);
                successCount++;
            } catch (DataIntegrityViolationException e) {
                log.warn("Property {} already has PayProp ID during bulk sync, skipping", property.getId());
                duplicateCount++;
            } catch (Exception e) {
                System.err.println("❌ Failed to sync property " + property.getId() + ": " + e.getMessage());
                errorCount++;
            }
        }
        
        System.out.println("🏁 Property sync completed. Success: " + successCount + 
                          ", Errors: " + errorCount + ", Duplicates: " + duplicateCount);
    }
    
    /**
     * Sync property in separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String syncPropertyToPayPropInSeparateTransaction(Long propertyId) {
        return syncPropertyToPayProp(propertyId);
    }
    
    /**
     * UPDATED: Sync based on your actual data
     * 31 total tenants, 0 have PayProp IDs, all pending sync
     */
    public void syncAllReadyTenants() {
        if (!oAuth2Service.hasValidTokens()) {
            throw new IllegalStateException("No valid OAuth2 tokens. Please authorize PayProp first.");
        }
        
        // UPDATED: Based on analysis - use 'tenants' table
        List<Tenant> readyTenants = tenantService.findAll().stream()
            .filter(t -> t.getPayPropId() == null) // Not yet synced (analysis shows 31 need sync)
            .toList();
            
        System.out.println("📋 Found " + readyTenants.size() + " tenants ready for sync (Analysis: 31 expected)");
        System.out.println("⚠️ NOTE: Tenant sync currently disabled due to PayProp permission restrictions");
        
        // Currently disabled - see syncTenantToPayProp method
        System.out.println("🏁 Tenant sync skipped - insufficient PayProp permissions");
    }

    
    /**
     * Sync tenant in separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String syncTenantToPayPropInSeparateTransaction(Long tenantId) {
        return syncTenantToPayProp(tenantId);
    }
    
    /**
     * UPDATED: Sync based on your actual data  
     * 0 total property_owners (table empty), but 2 customers with is_property_owner=1
     */
    public void syncAllReadyBeneficiaries() {
        if (!oAuth2Service.hasValidTokens()) {
            throw new IllegalStateException("No valid OAuth2 tokens. Please authorize PayProp first.");
        }
        
        // UPDATED: Based on analysis - property_owners table is empty (0 records)
        // But customer table has 2 records with is_property_owner=1
        List<PropertyOwner> readyOwners = propertyOwnerService.findAll().stream()
            .filter(o -> o.getPayPropId() == null) // Not yet synced
            .toList();
            
        System.out.println("📋 Found " + readyOwners.size() + " beneficiaries ready for sync");
        System.out.println("ℹ️ NOTE: Analysis shows property_owners table is empty (0 records)");
        System.out.println("ℹ️ Your system uses unified customer table with is_property_owner flag");
        System.out.println("ℹ️ Consider implementing syncCustomerAsPropertyOwnerToPayProp() instead");
        
        if (readyOwners.isEmpty()) {
            System.out.println("🏁 No beneficiaries to sync - property_owners table is empty");
            return;
        }
        
        // Rest of sync logic...
        int successCount = 0;
        int errorCount = 0;
        int duplicateCount = 0;
        
        for (PropertyOwner owner : readyOwners) {
            try {
                String payPropId = syncBeneficiaryToPayPropInSeparateTransaction(owner.getId());
                System.out.println("✅ Successfully synced beneficiary " + owner.getId() + " -> " + payPropId);
                successCount++;
            } catch (DataIntegrityViolationException e) {
                log.warn("PropertyOwner {} already has PayProp ID during bulk sync, skipping", owner.getId());
                duplicateCount++;
            } catch (Exception e) {
                System.err.println("❌ Failed to sync beneficiary " + owner.getId() + ": " + e.getMessage());
                errorCount++;
            }
        }
        
        System.out.println("🏁 Beneficiary sync completed. Success: " + successCount + 
                          ", Errors: " + errorCount + ", Duplicates: " + duplicateCount);
    }
    
    /**
     * Sync beneficiary in separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String syncBeneficiaryToPayPropInSeparateTransaction(Long propertyOwnerId) {
        return syncBeneficiaryToPayProp(propertyOwnerId);
    }
    
    public void checkSyncStatus() {
        System.out.println("=== PayProp OAuth2 Sync Status ===");
        
        // Check tokens only ONCE
        boolean hasValidTokens = oAuth2Service.hasValidTokens();
        System.out.println("OAuth2 Status: " + (hasValidTokens ? "✅ Authorized" : "❌ Not Authorized"));
        
        if (hasValidTokens) {
            try {
                PayPropOAuth2Service.PayPropTokens tokens = oAuth2Service.getCurrentTokens();
                System.out.println("Token Expires: " + tokens.getExpiresAt());
                System.out.println("Scopes: " + tokens.getScopes());
            } catch (Exception e) {
                System.err.println("⚠️ Error getting token details: " + e.getMessage());
            }
        }
        
        try {
            // UPDATED: Based on actual database analysis
            long totalProperties = propertyService.findAll().size();
            List<Property> needsSync = propertyService.findAll().stream()
                .filter(p -> p.getPayPropId() == null)
                .toList();
            List<Property> synced = propertyService.findAll().stream()
                .filter(p -> p.getPayPropId() != null)
                .toList();
            
            long totalTenants = tenantService.findAll().size();
            List<Tenant> tenantsNeedingSync = tenantService.findAll().stream()
                .filter(t -> t.getPayPropId() == null)
                .toList();
            List<Tenant> tenantsSynced = tenantService.findAll().stream()
                .filter(t -> t.getPayPropId() != null)
                .toList();
            
            long totalPropertyOwners = propertyOwnerService.findAll().size();
            
            System.out.println();
            System.out.println("PROPERTIES (from analysis: 296 total, 295 synced):");
            System.out.println("  Total: " + totalProperties);
            System.out.println("  Needs Sync: " + needsSync.size());
            System.out.println("  Already Synced: " + synced.size());
            System.out.println();
            System.out.println("TENANTS (from analysis: 31 total, 0 synced):");
            System.out.println("  Total: " + totalTenants);
            System.out.println("  Needs Sync: " + tenantsNeedingSync.size());
            System.out.println("  Already Synced: " + tenantsSynced.size());
            System.out.println();
            System.out.println("PROPERTY OWNERS (from analysis: 0 records in property_owners table):");
            System.out.println("  Total: " + totalPropertyOwners);
            System.out.println("  Note: Your system uses unified customer table (38 records, 2 are property owners)");
            
        } catch (Exception e) {
            System.err.println("⚠️ Error checking entity status: " + e.getMessage());
            System.out.println("Unable to check entity status - database error occurred");
        }
    }

    // ===== RESULT AND DATA CLASSES =====

    public static class PayPropExportResult {
        private List<Map<String, Object>> items;
        private Map<String, Object> pagination;
        
        public PayPropExportResult() {
            this.items = new ArrayList<>();
        }
        
        public List<Map<String, Object>> getItems() { 
            return items; 
        }
        
        public void setItems(List<Map<String, Object>> items) { 
            this.items = items; 
        }
        
        public Map<String, Object> getPagination() { 
            return pagination; 
        }
        
        public void setPagination(Map<String, Object> pagination) { 
            this.pagination = pagination; 
        }
    }

    public static class PayPropAttachmentResponse {
        private List<PayPropAttachment> data;
        private int total;
        private int page;
        private int rows;
        
        public PayPropAttachmentResponse() {
            this.data = new ArrayList<>();
        }
        
        // Getters and setters
        public List<PayPropAttachment> getData() { return data != null ? data : new ArrayList<>(); }
        public void setData(List<PayPropAttachment> data) { this.data = data; }
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getRows() { return rows; }
        public void setRows(int rows) { this.rows = rows; }
    }

    public static class PayPropAttachment {
        private String externalId;
        private String fileName;
        private String fileType;
        private String entityType;
        private String entityId;
        private String uploadedAt;
        
        // Getters and setters
        public String getExternalId() { return externalId; }
        public void setExternalId(String externalId) { this.externalId = externalId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }
        public String getEntityId() { return entityId; }
        public void setEntityId(String entityId) { this.entityId = entityId; }
        public String getUploadedAt() { return uploadedAt; }
        public void setUploadedAt(String uploadedAt) { this.uploadedAt = uploadedAt; }
    }

    // ===== ATTACHMENT PERMISSION HANDLING =====

    /**
     * Log missing permissions once per sync session
     */
    private static boolean attachmentPermissionWarningLogged = false;


    /**
     * Check if we have attachment permissions
     */
    public boolean hasAttachmentPermissions() {
        try {
            if (!oAuth2Service.hasValidTokens()) {
                return false;
            }
            
            PayPropOAuth2Service.PayPropTokens tokens = oAuth2Service.getCurrentTokens();
            String scopes = tokens.getScopes();
            
            // Check if scopes contain attachment permissions
            return scopes != null && (
                scopes.contains("read:attachment:list") || 
                scopes.contains("read:attachment") ||
                scopes.contains("all") // Some APIs grant 'all' scope
            );
        } catch (Exception e) {
            log.error("Error checking attachment permissions: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Log missing permissions once per sync session
     */
    public void logAttachmentPermissionWarningOnce() {
        if (!attachmentPermissionWarningLogged && !hasAttachmentPermissions()) {
            log.warn("⚠️ PERMISSION WARNING: Your PayProp API credentials lack attachment permissions. " +
                    "Attachment sync will be skipped. To enable attachment sync, request the following " +
                    "scopes from PayProp: 'read:attachment:list', 'read:attachment:download'. " +
                    "This warning will only appear once per session.");
            attachmentPermissionWarningLogged = true;
        }
    }

}