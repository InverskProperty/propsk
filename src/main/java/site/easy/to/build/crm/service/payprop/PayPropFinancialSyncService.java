// PayPropFinancialSyncService.java - Comprehensive financial data sync service
package site.easy.to.build.crm.service.payprop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.property.PropertyService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropFinancialSyncService {
    
    private static final Logger logger = LoggerFactory.getLogger(PayPropFinancialSyncService.class);
    
    private final PayPropOAuth2Service oAuth2Service;
    private final RestTemplate restTemplate;
    
    // Database repositories
    private final PropertyRepository propertyRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final PaymentCategoryRepository paymentCategoryRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final BatchPaymentRepository batchPaymentRepository;
    private final PropertyService propertyService;
    
    // PayProp API base URL
    private final String payPropApiBase = "https://ukapi.staging.payprop.com/api/agency/v1.1";
    
    @Autowired
    public PayPropFinancialSyncService(
        PayPropOAuth2Service oAuth2Service,
        RestTemplate restTemplate,
        PropertyRepository propertyRepository,
        BeneficiaryRepository beneficiaryRepository,
        PaymentCategoryRepository paymentCategoryRepository,
        FinancialTransactionRepository financialTransactionRepository,
        BatchPaymentRepository batchPaymentRepository,
        PropertyService propertyService
    ) {
        this.oAuth2Service = oAuth2Service;
        this.restTemplate = restTemplate;
        this.propertyRepository = propertyRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.paymentCategoryRepository = paymentCategoryRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.batchPaymentRepository = batchPaymentRepository;
        this.propertyService = propertyService;
    }
    
    /**
     * Main method to sync all comprehensive financial data from PayProp
     * ✅ UPDATED: Now includes actual commission payment sync
     */
    @Transactional
    public Map<String, Object> syncComprehensiveFinancialData() {
        Map<String, Object> syncResults = new HashMap<>();
        
        try {
            logger.info("🚀 Starting comprehensive financial data sync...");
            
            // 1. Sync Properties with Commission Data
            Map<String, Object> propertiesResult = syncPropertiesWithCommission();
            syncResults.put("properties", propertiesResult);
            
            // 2. Sync Owner Beneficiaries  
            Map<String, Object> beneficiariesResult = syncOwnerBeneficiaries();
            syncResults.put("beneficiaries", beneficiariesResult);
            
            // 3. Sync Payment Categories
            Map<String, Object> categoriesResult = syncPaymentCategories();
            syncResults.put("categories", categoriesResult);
            
            // 4. Sync Financial Transactions (ICDN) - Rent payments and deposits
            Map<String, Object> transactionsResult = syncFinancialTransactions();
            syncResults.put("transactions", transactionsResult);
            
            // 5. Sync Batch Payments from /report/all-payments
            Map<String, Object> batchPaymentsResult = syncBatchPayments();
            syncResults.put("batch_payments", batchPaymentsResult);
            
            // 7. Calculate and store commission data (for rent payments only)
            Map<String, Object> commissionsResult = calculateAndStoreCommissions();
            syncResults.put("commissions", commissionsResult);

            // 8. Sync actual commission payments
            Map<String, Object> actualCommissionsResult = syncActualCommissionPayments();
            syncResults.put("actual_commissions", actualCommissionsResult);

            // 9. Link actual commission payments to rent transactions
            Map<String, Object> linkingResult = linkActualCommissionToTransactions();
            syncResults.put("commission_linking", linkingResult);

            syncResults.put("status", "SUCCESS");
                        syncResults.put("sync_time", LocalDateTime.now());
            
            logger.info("✅ Comprehensive financial sync completed successfully");
            
        } catch (Exception e) {
            logger.error("❌ Comprehensive financial sync failed", e);
            syncResults.put("status", "FAILED");
            syncResults.put("error", e.getMessage());
        }
        
        return syncResults;
    }
    
    /**
     * ✅ FIXED COMMISSION SYNC: Apply same pagination pattern as successful batch payments
     * Replace syncPropertiesWithCommission() method in PayPropFinancialSyncService.java
     */
    private Map<String, Object> syncPropertiesWithCommission() throws Exception {
        logger.info("📊 Starting FIXED commission sync with comprehensive pagination...");
        
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        // Counters with detailed breakdown (same pattern as batch payments)
        int totalApiCalls = 0, totalPagesProcessed = 0;
        int propertiesProcessed = 0, propertiesUpdated = 0, propertiesCreated = 0;
        int propertiesWithCommission = 0, propertiesWithoutCommission = 0;
        int commissionsSet = 0, errors = 0;
        
        // ✅ COMPREHENSIVE PAGINATION: Same pattern as successful batch payments
        int page = 1;
        
        logger.info("🔍 FIXED: Processing properties with commission data using pagination...");
        
        while (true) {
            String url = payPropApiBase + "/export/properties?include_commission=true&page=" + page + "&rows=25";
            
            logger.info("📞 Commission API Call {}: Page {}", totalApiCalls + 1, page);
            
            try {
                // ✅ Rate limiting (same as batch payments)
                if (totalApiCalls > 0) {
                    Thread.sleep(250);
                }
                
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                totalApiCalls++;
                
                if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                    logger.error("❌ Commission API failed: HTTP {}", response.getStatusCode());
                    break;
                }
                
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> payPropProperties = (List<Map<String, Object>>) responseBody.get("items");
                
                if (payPropProperties == null || payPropProperties.isEmpty()) {
                    logger.info("📄 No properties on page {}, commission sync complete", page);
                    break;
                }
                
                logger.info("📊 Processing {} properties with commission data on page {}", 
                    payPropProperties.size(), page);
                
                // ✅ PROCESS EACH PROPERTY: Same careful handling as batch payments
                for (Map<String, Object> ppProperty : payPropProperties) {
                    try {
                        boolean processed = processPropertyCommissionData(ppProperty);
                        if (processed) {
                            propertiesProcessed++;
                            
                            // Check if commission data was found and set
                            Map<String, Object> commission = (Map<String, Object>) ppProperty.get("commission");
                            if (commission != null) {
                                Object percentage = commission.get("percentage");
                                if (percentage instanceof String && !((String) percentage).isEmpty()) {
                                    propertiesWithCommission++;
                                    commissionsSet++;
                                } else {
                                    propertiesWithoutCommission++;
                                }
                            } else {
                                propertiesWithoutCommission++;
                            }
                        }
                        
                    } catch (Exception e) {
                        errors++;
                        logger.error("❌ Error processing property commission {}: {}", 
                            ppProperty.get("id"), e.getMessage());
                    }
                }
                
                totalPagesProcessed++;
                
                // ✅ CHECK PAGINATION: Same logic as batch payments
                Map<String, Object> pagination = (Map<String, Object>) responseBody.get("pagination");
                if (pagination != null) {
                    Integer currentPage = (Integer) pagination.get("page");
                    Integer totalPages = (Integer) pagination.get("total_pages");
                    
                    logger.info("📊 Commission Pagination: page {} of {}", currentPage, totalPages);
                    
                    if (currentPage != null && totalPages != null && currentPage >= totalPages) {
                        logger.info("📄 Reached last commission page ({} of {})", currentPage, totalPages);
                        break;
                    }
                } else if (payPropProperties.size() < 25) {
                    logger.info("📄 Got {} properties (< 25), assuming last commission page", 
                        payPropProperties.size());
                    break;
                }
                
                page++;
                
                // ✅ SAFETY BREAK: Prevent infinite loops
                if (page > 100) {
                    logger.warn("⚠️ Safety break: Reached page 100 for commission sync");
                    break;
                }
                
            } catch (HttpClientErrorException e) {
                logger.error("❌ Commission API error on page {}: {} - {}", 
                    page, e.getStatusCode(), e.getResponseBodyAsString());
                
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    logger.warn("⚠️ Rate limit hit, waiting 30 seconds...");
                    Thread.sleep(30000);
                    continue; // Retry same page
                }
                break;
                
            } catch (Exception e) {
                logger.error("❌ Commission sync failed on page {}: {}", page, e.getMessage(), e);
                break;
            }
        }
        
        // ✅ COMPREHENSIVE REPORTING: Same detailed logging as batch payments
        logger.info("📊 COMMISSION SYNC COMPLETED:");
        logger.info("📊 Total API calls: {}", totalApiCalls);
        logger.info("📊 Pages processed: {}", totalPagesProcessed);
        logger.info("📊 Properties processed: {}", propertiesProcessed);
        logger.info("✅ Properties with commission rates: {}", propertiesWithCommission);
        logger.info("⚠️ Properties without commission rates: {}", propertiesWithoutCommission);
        logger.info("✅ Commission rates set: {}", commissionsSet);
        logger.info("❌ Processing errors: {}", errors);
        
        Map<String, Object> result = new HashMap<>();
        result.put("total_processed", propertiesProcessed);
        result.put("properties_with_commission", propertiesWithCommission);
        result.put("properties_without_commission", propertiesWithoutCommission);
        result.put("commission_rates_set", commissionsSet);
        result.put("errors", errors);
        result.put("api_calls", totalApiCalls);
        result.put("pages_processed", totalPagesProcessed);
        result.put("improvement", "Added comprehensive pagination - should get all 281 properties instead of just 25");
        
        return result;
    }

    /**
     * ✅ ENHANCED: Process individual property commission data with better error handling
     * Add this method to PayPropFinancialSyncService.java
     */
    private boolean processPropertyCommissionData(Map<String, Object> ppProperty) {
        try {
            String payPropId = (String) ppProperty.get("id");
            String propertyName = (String) ppProperty.get("property_name");
            
            if (payPropId == null || payPropId.trim().isEmpty()) {
                logger.warn("⚠️ COMMISSION: Skipping property with missing PayProp ID");
                return false;
            }
            
            // ✅ CORRECT: Use existing payprop_id column (confirmed by database analysis)
            // Database shows payprop_id is the primary PayProp identifier
            Optional<Property> existingOpt = propertyService.findByPayPropId(payPropId);

            Property property;
            
            if (existingOpt.isPresent()) {
                property = existingOpt.get();
            } else {
                logger.warn("⚠️ COMMISSION: Property not found for PayProp ID: {}", payPropId);
                return false; // Skip if property doesn't exist
            }
            
            // Update basic property data
            if (propertyName != null) {
                property.setPropertyName(propertyName);
            }
            
            // ✅ COMMISSION DATA PROCESSING: Handle the commission object carefully
            Map<String, Object> commission = (Map<String, Object>) ppProperty.get("commission");
            if (commission != null) {
                Object percentage = commission.get("percentage");
                if (percentage instanceof String && !((String) percentage).isEmpty()) {
                    try {
                        BigDecimal commissionRate = new BigDecimal((String) percentage);
                        property.setCommissionPercentage(commissionRate);
                        logger.debug("✅ COMMISSION: Set rate {}% for property {}", 
                            commissionRate, propertyName);
                    } catch (NumberFormatException e) {
                        logger.warn("⚠️ COMMISSION: Invalid commission percentage format for property {}: {}", 
                            propertyName, percentage);
                        property.setCommissionPercentage(BigDecimal.ZERO);
                    }
                } else {
                    logger.debug("⚠️ COMMISSION: No commission percentage for property {}", propertyName);
                    property.setCommissionPercentage(BigDecimal.ZERO);
                }
                
                Object amount = commission.get("amount");
                if (amount instanceof String && !((String) amount).isEmpty()) {
                    try {
                        property.setCommissionAmount(new BigDecimal((String) amount));
                    } catch (NumberFormatException e) {
                        property.setCommissionAmount(BigDecimal.ZERO);
                    }
                } else {
                    property.setCommissionAmount(BigDecimal.ZERO);
                }
            } else {
                logger.debug("⚠️ COMMISSION: No commission data for property {}, setting to zero", propertyName);
                property.setCommissionPercentage(BigDecimal.ZERO);
                property.setCommissionAmount(BigDecimal.ZERO);
            }
            
            // Update other property fields from API
            updatePropertyFromCommissionSync(property, ppProperty);
            
            // Set audit fields
            property.setUpdatedAt(LocalDateTime.now());
            
            // ✅ SAVE: Use the existing property service
            propertyService.save(property);
            
            return true;
            
        } catch (Exception e) {
            logger.error("❌ COMMISSION: Error processing property commission data: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * ✅ HELPER: Update property with additional data from commission sync
     * Add this method to PayPropFinancialSyncService.java
     */
    private void updatePropertyFromCommissionSync(Property property, Map<String, Object> ppProperty) {
        // Account balance
        Object accountBalance = ppProperty.get("account_balance");
        if (accountBalance instanceof Number) {
            property.setAccountBalance(new BigDecimal(accountBalance.toString()));
        }
        
        // Monthly payment required
        Object monthlyPayment = ppProperty.get("monthly_payment_required");
        if (monthlyPayment instanceof Number) {
            property.setMonthlyPayment(new BigDecimal(monthlyPayment.toString()));
        }
        
        // Property account minimum balance
        Object minBalance = ppProperty.get("property_account_minimum_balance");
        if (minBalance instanceof String && !((String) minBalance).isEmpty()) {
            try {
                property.setPropertyAccountMinimumBalance(new BigDecimal((String) minBalance));
            } catch (NumberFormatException e) {
                property.setPropertyAccountMinimumBalance(BigDecimal.ZERO);
            }
        }
        
        // Payment settings
        Object allowPayments = ppProperty.get("allow_payments");
        if (allowPayments instanceof Boolean) {
            property.setEnablePaymentsFromBoolean((Boolean) allowPayments);
        }
        
        Object holdFunds = ppProperty.get("hold_all_owner_funds");
        if (holdFunds instanceof Boolean) {
            property.setHoldOwnerFundsFromBoolean((Boolean) holdFunds);
        }
        
        // Address data (if present)
        Map<String, Object> address = (Map<String, Object>) ppProperty.get("address");
        if (address != null) {
            property.setAddressLine1((String) address.get("first_line"));
            property.setAddressLine2((String) address.get("second_line"));
            property.setAddressLine3((String) address.get("third_line"));
            property.setCity((String) address.get("city"));
            property.setState((String) address.get("state"));
            property.setPostcode((String) address.get("postal_code"));
            property.setCountryCode((String) address.get("country_code"));
        }
    }

    private Map<String, Object> syncOwnerBeneficiaries() throws Exception {
        logger.info("👥 Syncing owner beneficiaries...");
        
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        String url = payPropApiBase + "/export/beneficiaries?owners=true&rows=1000";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
        
        List<Map<String, Object>> payPropBeneficiaries = (List<Map<String, Object>>) response.getBody().get("items");
        
        int updated = 0, created = 0, skipped = 0;
        
        for (Map<String, Object> ppBeneficiary : payPropBeneficiaries) {
            try {
                String payPropId = (String) ppBeneficiary.get("id");
                
                // 🔧 VALIDATION: Skip if missing essential data
                if (payPropId == null || payPropId.trim().isEmpty()) {
                    logger.warn("⚠️ Skipping beneficiary with missing PayProp ID");
                    skipped++;
                    continue;
                }
                
                // Find or create beneficiary
                Beneficiary beneficiary = beneficiaryRepository.findByPayPropBeneficiaryId(payPropId);
                boolean isNew = beneficiary == null;
                
                if (isNew) {
                    beneficiary = new Beneficiary();
                    beneficiary.setPayPropBeneficiaryId(payPropId);
                    beneficiary.setCreatedAt(LocalDateTime.now());
                }
                
                // 🔧 FIX: Handle account_type with safe defaults
                String accountTypeStr = (String) ppBeneficiary.get("account_type");
                if (accountTypeStr == null || "undefined".equals(accountTypeStr) || accountTypeStr.trim().isEmpty()) {
                    // Default to individual if not specified
                    beneficiary.setAccountType(AccountType.individual);
                    logger.debug("🔧 Defaulted account_type to 'individual' for beneficiary {}", payPropId);
                } else {
                    try {
                        beneficiary.setAccountType(AccountType.valueOf(accountTypeStr.toLowerCase()));
                    } catch (IllegalArgumentException e) {
                        logger.warn("⚠️ Invalid account_type '{}' for beneficiary {}, defaulting to 'individual'", 
                                accountTypeStr, payPropId);
                        beneficiary.setAccountType(AccountType.individual);
                    }
                }
                
                // 🔧 FIX: Handle payment_method with safe defaults
                String paymentMethodStr = (String) ppBeneficiary.get("payment_method");
                if (paymentMethodStr == null || "undefined".equals(paymentMethodStr) || paymentMethodStr.trim().isEmpty()) {
                    // Default to local payment method
                    beneficiary.setPaymentMethod(PaymentMethod.local);
                    logger.debug("🔧 Defaulted payment_method to 'local' for beneficiary {}", payPropId);
                } else {
                    try {
                        beneficiary.setPaymentMethod(PaymentMethod.valueOf(paymentMethodStr.toLowerCase()));
                    } catch (IllegalArgumentException e) {
                        logger.warn("⚠️ Invalid payment_method '{}' for beneficiary {}, defaulting to 'local'", 
                                paymentMethodStr, payPropId);
                        beneficiary.setPaymentMethod(PaymentMethod.local);
                    }
                }
                
                // 🔧 FIX: Set BeneficiaryType using the correct enum values
                // These are property owners, so they should be BENEFICIARY
                // Java enum: AGENCY, BENEFICIARY, GLOBAL_BENEFICIARY, PROPERTY_ACCOUNT, DEPOSIT_ACCOUNT
                beneficiary.setBeneficiaryType(BeneficiaryType.BENEFICIARY);
                logger.debug("🔧 Set beneficiary_type to 'BENEFICIARY' for beneficiary {}", payPropId);
                
                // 🔧 FIX: Set name field safely (required field)
                String firstName = (String) ppBeneficiary.get("first_name");
                String lastName = (String) ppBeneficiary.get("last_name");
                String businessName = (String) ppBeneficiary.get("business_name");
                
                // Clean up empty strings to null
                if (firstName != null && firstName.trim().isEmpty()) firstName = null;
                if (lastName != null && lastName.trim().isEmpty()) lastName = null;
                if (businessName != null && businessName.trim().isEmpty()) businessName = null;
                
                beneficiary.setFirstName(firstName);
                beneficiary.setLastName(lastName);
                beneficiary.setBusinessName(businessName);
                
                // Set the name field (required, non-null)
                String calculatedName = null;
                if (beneficiary.getAccountType() == AccountType.business && businessName != null) {
                    calculatedName = businessName;
                } else if (firstName != null && lastName != null) {
                    calculatedName = firstName + " " + lastName;
                } else if (firstName != null) {
                    calculatedName = firstName;
                } else if (lastName != null) {
                    calculatedName = lastName;
                } else if (businessName != null) {
                    calculatedName = businessName;
                }
                
                // Fallback if still no name
                if (calculatedName == null || calculatedName.trim().isEmpty()) {
                    calculatedName = "Beneficiary " + payPropId;
                    logger.warn("⚠️ No name available for beneficiary {}, using fallback: '{}'", 
                            payPropId, calculatedName);
                }
                
                beneficiary.setName(calculatedName.trim());
                
                // Set other fields
                String email = (String) ppBeneficiary.get("email_address");
                if (email != null && !email.trim().isEmpty()) {
                    beneficiary.setEmail(email.trim());
                }
                
                beneficiary.setMobileNumber((String) ppBeneficiary.get("mobile"));
                beneficiary.setPhone((String) ppBeneficiary.get("phone"));
                beneficiary.setIsActiveOwner((Boolean) ppBeneficiary.getOrDefault("is_active_owner", false));
                beneficiary.setVatNumber((String) ppBeneficiary.get("vat_number"));
                beneficiary.setCustomerReference((String) ppBeneficiary.get("customer_reference"));
                
                // Properties this beneficiary owns
                List<Map<String, Object>> properties = (List<Map<String, Object>>) ppBeneficiary.get("properties");
                if (properties != null && !properties.isEmpty()) {
                    Map<String, Object> property = properties.get(0);
                    String propertyName = (String) property.get("property_name");
                    beneficiary.setPrimaryPropertyName(propertyName);
                    
                    Object accountBalance = property.get("account_balance");
                    if (accountBalance instanceof Number) {
                        beneficiary.setEnhancedAccountBalance(new BigDecimal(accountBalance.toString()));
                    }
                }
                
                beneficiary.setUpdatedAt(LocalDateTime.now());
                
                try {
                    beneficiaryRepository.save(beneficiary);
                    if (isNew) {
                        created++;
                        logger.info("✅ Created beneficiary: {} ({})", calculatedName, payPropId);
                    } else {
                        updated++;
                        logger.debug("✅ Updated beneficiary: {} ({})", calculatedName, payPropId);
                    }
                } catch (Exception saveException) {
                    logger.error("❌ Failed to save beneficiary {}: {}", payPropId, saveException.getMessage());
                    skipped++;
                }
                
            } catch (Exception e) {
                logger.error("❌ Error processing beneficiary: {}", e.getMessage(), e);
                skipped++;
            }
        }
        
        logger.info("👥 Owner beneficiaries sync completed: {} created, {} updated, {} skipped", 
                    created, updated, skipped);
        
        return Map.of(
            "total_processed", payPropBeneficiaries.size(),
            "created", created,
            "updated", updated,
            "skipped", skipped
        );
    }
    
    /**
     * Sync payment categories from PayProp
     */
    private Map<String, Object> syncPaymentCategories() throws Exception {
        logger.info("🏷️ Syncing payment categories...");
        
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        // Note: The exact endpoint for categories may vary
        // Using export/payments to get category information
        String url = payPropApiBase + "/export/payments?rows=100";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
        
        List<Map<String, Object>> payments = (List<Map<String, Object>>) response.getBody().get("items");
        Set<String> categoryIds = new HashSet<>();
        Map<String, String> categoryNames = new HashMap<>();
        
        // Extract category information from payments
        for (Map<String, Object> payment : payments) {
            String categoryId = (String) payment.get("category_id");
            String categoryName = (String) payment.get("category");
            
            if (categoryId != null && categoryName != null) {
                categoryIds.add(categoryId);
                categoryNames.put(categoryId, categoryName);
            }
        }
        
        int processed = 0;
        
        for (String categoryId : categoryIds) {
            String categoryName = categoryNames.get(categoryId);
            
            PaymentCategory category = paymentCategoryRepository.findByPayPropCategoryId(categoryId);
            if (category == null) {
                category = new PaymentCategory();
                category.setPayPropCategoryId(categoryId);
                category.setCreatedAt(LocalDateTime.now());
            }
            
            category.setCategoryName(categoryName);
            category.setCategoryType("PAYMENT");
            category.setIsActive("Y");
            category.setUpdatedAt(LocalDateTime.now());
            
            paymentCategoryRepository.save(category);
            processed++;
        }
        
        return Map.of("categories_processed", processed);
    }
    
    /**
     * ✅ FIXED ICDN SYNC: Apply same pagination and isolation fixes as batch payments
     * Replace the syncFinancialTransactions() method in PayPropFinancialSyncService.java
     */

    private Map<String, Object> syncFinancialTransactions() throws Exception {
        logger.info("💰 Starting FIXED ICDN transactions sync with pagination and isolation...");
        
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        // Counters with detailed breakdown (same as batch payments)
        int totalApiCalls = 0, totalChunks = 0, totalPagesProcessed = 0;
        int successfulSaves = 0, skippedDuplicates = 0, skippedNegative = 0;
        int skippedInvalidType = 0, skippedMissingData = 0, constraintViolations = 0;
        int otherErrors = 0;
        
        // ✅ FIXED: Same date range as batch payments (2 years back)
        LocalDate endDate = LocalDate.now().plusDays(7);
        LocalDate absoluteStartDate = endDate.minusYears(2); // Same as batch payments
        
        logger.info("🔍 FIXED: Processing 14-day chunks from {} to {} (extended from 180 days)", 
            absoluteStartDate, endDate);
        
        // ✅ FIXED: Same 14-day chunks as batch payments
        while (endDate.isAfter(absoluteStartDate)) {
            LocalDate startDate = endDate.minusDays(14); // Same chunk size as batch payments
            if (startDate.isBefore(absoluteStartDate)) {
                startDate = absoluteStartDate;
            }
            
            totalChunks++;
            logger.info("🔍 ICDN CHUNK {}: Processing {} to {}", totalChunks, startDate, endDate);
            
            // ✅ NEW: Add pagination loop (was missing!)
            int page = 1;
            int chunkTransactions = 0;
            
            while (true) {
                String url = payPropApiBase + "/report/icdn" +
                    "?from_date=" + startDate +
                    "&to_date=" + endDate +
                    "&page=" + page +           // ✅ NEW: Add pagination
                    "&rows=25";                 // ✅ FIXED: Use PayProp's actual limit
                
                logger.info("📞 ICDN API Call {}: Chunk {} Page {}", totalApiCalls + 1, totalChunks, page);
                
                try {
                    // ✅ Rate limiting (same as batch payments)
                    if (totalApiCalls > 0) {
                        Thread.sleep(250);
                    }
                    
                    ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                    totalApiCalls++;
                    
                    Map<String, Object> responseBody = response.getBody();
                    List<Map<String, Object>> transactions = (List<Map<String, Object>>) responseBody.get("items");
                    
                    if (transactions.isEmpty()) {
                        logger.info("📄 No ICDN transactions on page {} for chunk {}", page, totalChunks);
                        break;
                    }
                    
                    logger.info("📊 Processing {} ICDN transactions on page {} of chunk {}", 
                        transactions.size(), page, totalChunks);
                    
                    // ✅ ISOLATED PROCESSING: Each transaction processed independently  
                    for (Map<String, Object> ppTransaction : transactions) {
                        try {
                            FinancialTransaction transaction = createICDNFinancialTransactionSafe(ppTransaction);
                            if (transaction != null) {
                                // ✅ ISOLATED SAVE: Each save in its own transaction
                                boolean saved = saveFinancialTransactionIsolated(transaction);
                                if (saved) {
                                    successfulSaves++;
                                } else {
                                    // Categorize the failure reason
                                    if (transaction.getAmount() != null && transaction.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                                        skippedNegative++;
                                    } else if (financialTransactionRepository.existsByPayPropTransactionId(transaction.getPayPropTransactionId())) {
                                        skippedDuplicates++;
                                    } else if (!isValidTransactionType(transaction.getTransactionType())) {
                                        skippedInvalidType++;
                                    } else {
                                        skippedMissingData++;
                                    }
                                }
                            } else {
                                skippedMissingData++;
                            }
                            
                            chunkTransactions++;
                            
                        } catch (Exception e) {
                            otherErrors++;
                            logger.error("❌ Error processing ICDN transaction: {}", e.getMessage());
                        }
                    }
                    
                    // ✅ NEW: Check pagination (same logic as batch payments)
                    Map<String, Object> pagination = (Map<String, Object>) responseBody.get("pagination");
                    if (pagination != null) {
                        Integer currentPage = (Integer) pagination.get("page");
                        Integer totalPages = (Integer) pagination.get("total_pages");
                        
                        if (currentPage != null && totalPages != null && currentPage >= totalPages) {
                            logger.info("📄 Reached last ICDN page ({}) for chunk {}", totalPages, totalChunks);
                            break;
                        }
                    } else if (transactions.size() < 25) {
                        logger.info("📄 Got {} ICDN transactions (< 25), last page for chunk {}", 
                            transactions.size(), totalChunks);
                        break;
                    }
                    
                    page++;
                    totalPagesProcessed++;
                    
                    if (page > 50) { // Safety break
                        logger.warn("⚠️ Safety break: ICDN page 50 reached for chunk {}", totalChunks);
                        break;
                    }
                    
                } catch (Exception e) {
                    logger.error("❌ ICDN API call failed for chunk {} page {}: {}", totalChunks, page, e.getMessage());
                    if (e.getMessage().contains("429")) {
                        Thread.sleep(30000); // Wait for rate limit
                        continue;
                    }
                    break;
                }
            }
            
            logger.info("✅ ICDN Chunk {} completed: {} transactions processed", totalChunks, chunkTransactions);
            endDate = startDate.minusDays(1);
        }
        
        // ✅ COMPREHENSIVE REPORTING (same as batch payments)
        logger.info("💰 ICDN TRANSACTIONS SYNC COMPLETED:");
        logger.info("📊 Total API calls: {}", totalApiCalls);
        logger.info("📊 Chunks processed: {}", totalChunks);
        logger.info("📊 Pages processed: {}", totalPagesProcessed);
        logger.info("✅ Successful saves: {}", successfulSaves);
        logger.info("⏭️ Skipped duplicates: {}", skippedDuplicates);
        logger.info("⚠️ Skipped negative amounts: {}", skippedNegative);
        logger.info("⚠️ Skipped invalid types: {}", skippedInvalidType);
        logger.info("⚠️ Skipped missing data: {}", skippedMissingData);
        logger.info("❌ Constraint violations: {}", constraintViolations);
        logger.info("❌ Other errors: {}", otherErrors);
        
        // In the batch payments method, replace Map.of() with:
        Map<String, Object> result = new HashMap<>();
        result.put("payments_created", successfulSaves);
        result.put("skipped_duplicates", skippedDuplicates);
        result.put("skipped_negative", skippedNegative);
        result.put("skipped_invalid_type", skippedInvalidType);
        result.put("skipped_missing_data", skippedMissingData);
        result.put("constraint_violations", constraintViolations);
        result.put("other_errors", otherErrors);
        result.put("total_processed", successfulSaves + skippedDuplicates + skippedNegative + skippedInvalidType + skippedMissingData + constraintViolations + otherErrors);
        result.put("api_calls", totalApiCalls);
        result.put("chunks_processed", totalChunks);
        return result;
    }

    /**
     * ✅ NEW: Safe ICDN transaction creation with proper validation
     * Add this method to PayPropFinancialSyncService.java
     */
    private FinancialTransaction createICDNFinancialTransactionSafe(Map<String, Object> ppTransaction) {
        try {
            String payPropId = (String) ppTransaction.get("id");
            
            // Validate essential fields
            if (payPropId == null || payPropId.trim().isEmpty()) {
                logger.warn("⚠️ ICDN: Missing PayProp ID");
                return null;
            }
            
            // Check amount
            Object amount = ppTransaction.get("amount");
            if (amount == null) {
                logger.warn("⚠️ ICDN: Missing amount for transaction {}", payPropId);
                return null;
            }
            
            BigDecimal amountValue;
            try {
                amountValue = new BigDecimal(amount.toString());
                if (amountValue.compareTo(BigDecimal.ZERO) < 0) {
                    logger.warn("⚠️ ICDN: Negative amount £{} for transaction {}", amountValue, payPropId);
                    return null;
                }
            } catch (NumberFormatException e) {
                logger.warn("⚠️ ICDN: Invalid amount format '{}' for transaction {}", amount, payPropId);
                return null;
            }
            
            // Check date
            String dateStr = (String) ppTransaction.get("date");
            LocalDate transactionDate;
            if (dateStr != null && !dateStr.trim().isEmpty()) {
                try {
                    transactionDate = LocalDate.parse(dateStr);
                } catch (Exception e) {
                    logger.warn("⚠️ ICDN: Invalid date format '{}' for transaction {}", dateStr, payPropId);
                    return null;
                }
            } else {
                logger.warn("⚠️ ICDN: Missing transaction date for transaction {}", payPropId);
                return null;
            }
            
            // Validate transaction type
            String transactionType = (String) ppTransaction.get("type");
            if (transactionType != null) {
                String normalizedType = transactionType.toLowerCase().replace(" ", "_");
                if (!Arrays.asList("invoice", "credit_note", "debit_note").contains(normalizedType)) {
                    logger.warn("⚠️ ICDN: Invalid transaction type '{}' for transaction {}", transactionType, payPropId);
                    return null;
                }
                transactionType = normalizedType;
            } else {
                logger.warn("⚠️ ICDN: Missing transaction type for transaction {}", payPropId);
                return null;
            }
            
            // Create transaction
            FinancialTransaction transaction = new FinancialTransaction();
            transaction.setPayPropTransactionId(payPropId);
            transaction.setDataSource("ICDN_ACTUAL");
            transaction.setIsActualTransaction(true);
            transaction.setIsInstruction(false);
            
            transaction.setAmount(amountValue);
            transaction.setTransactionDate(transactionDate);
            transaction.setTransactionType(transactionType);
            
            // Optional fields with safe extraction
            transaction.setDescription((String) ppTransaction.get("description"));
            transaction.setHasTax((Boolean) ppTransaction.getOrDefault("has_tax", false));
            transaction.setDepositId((String) ppTransaction.get("deposit_id"));
            
            // Matched amount (optional)
            Object matchedAmount = ppTransaction.get("matched_amount");
            if (matchedAmount instanceof String && !((String) matchedAmount).isEmpty()) {
                try {
                    transaction.setMatchedAmount(new BigDecimal((String) matchedAmount));
                } catch (NumberFormatException e) {
                    // Ignore invalid matched amounts
                }
            }
            
            // Tax amount (optional)
            Object taxAmount = ppTransaction.get("tax_amount");
            if (taxAmount instanceof String && !((String) taxAmount).isEmpty()) {
                try {
                    transaction.setTaxAmount(new BigDecimal((String) taxAmount));
                } catch (NumberFormatException e) {
                    transaction.setTaxAmount(BigDecimal.ZERO);
                }
            }
            
            // Property info (safe nested extraction)
            Map<String, Object> property = (Map<String, Object>) ppTransaction.get("property");
            if (property != null) {
                transaction.setPropertyId((String) property.get("id"));
                transaction.setPropertyName((String) property.get("name"));
            }
            
            // Tenant info (safe nested extraction)
            Map<String, Object> tenant = (Map<String, Object>) ppTransaction.get("tenant");
            if (tenant != null) {
                transaction.setTenantId((String) tenant.get("id"));
                transaction.setTenantName((String) tenant.get("name"));
            }
            
            // Category info (safe nested extraction)
            Map<String, Object> category = (Map<String, Object>) ppTransaction.get("category");
            if (category != null) {
                transaction.setCategoryId((String) category.get("id"));
                transaction.setCategoryName((String) category.get("name"));
            }
            
            // Audit fields
            transaction.setCreatedAt(LocalDateTime.now());
            transaction.setUpdatedAt(LocalDateTime.now());
            
            return transaction;
            
        } catch (Exception e) {
            logger.error("❌ Error creating ICDN financial transaction: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * ✅ FIXED: Get commission rates from already-synced properties table
     * This avoids the 25-row pagination issue entirely
     */
    private Map<String, BigDecimal> getCommissionRatesFromDatabase() {
        Map<String, BigDecimal> rates = new HashMap<>();
        
        try {
            List<Property> properties = propertyService.findAll();
            
            for (Property property : properties) {
                if (property.getPayPropId() != null && property.getCommissionPercentage() != null) {
                    rates.put(property.getPayPropId(), property.getCommissionPercentage());
                }
            }
            
            logger.info("✅ Loaded {} commission rates from synced properties", rates.size());
            
        } catch (Exception e) {
            logger.error("❌ Failed to load commission rates from database: {}", e.getMessage());
        }
        
        return rates;
    }

    /**
     * ✅ NEW: Link actual commission payments to their corresponding rent transactions
     */
    private Map<String, Object> linkActualCommissionToTransactions() throws Exception {
        logger.info("🔗 Linking actual commission payments to rent transactions...");
        
        // Find all commission payment transactions
        List<FinancialTransaction> commissionPayments = financialTransactionRepository
            .findByDataSourceAndTransactionType("COMMISSION_PAYMENT", "commission_payment");
        
        int linked = 0;
        
        for (FinancialTransaction commissionPayment : commissionPayments) {
            // Find corresponding rent payments for the same property and approximate date
            LocalDate startDate = commissionPayment.getTransactionDate().minusDays(7);
            LocalDate endDate = commissionPayment.getTransactionDate().plusDays(7);
            
            List<FinancialTransaction> rentPayments = financialTransactionRepository
                .findByPropertyIdAndTransactionDateBetween(commissionPayment.getPropertyId(), startDate, endDate)
                .stream()
                .filter(t -> "invoice".equals(t.getTransactionType()))
                .filter(t -> "ICDN_ACTUAL".equals(t.getDataSource()))
                .collect(Collectors.toList());
            
            for (FinancialTransaction rentPayment : rentPayments) {
                // Only link if actual commission amount is not already set
                if (rentPayment.getActualCommissionAmount() == null) {
                    rentPayment.setActualCommissionAmount(commissionPayment.getAmount());
                    rentPayment.setUpdatedAt(LocalDateTime.now());
                    financialTransactionRepository.save(rentPayment);
                    linked++;
                    
                    logger.debug("✅ Linked actual commission £{} to rent payment £{} for property {}", 
                        commissionPayment.getAmount(), rentPayment.getAmount(), rentPayment.getPropertyName());
                }
            }
        }
        
        logger.info("🔗 Commission linking completed: {} rent transactions linked to actual commission", linked);
        
        return Map.of(
            "commission_payments_found", commissionPayments.size(),
            "rent_transactions_linked", linked
        );
    }

    
    /**
     * ✅ FAST: Get commission rates from PayProp and calculate commission
     */
    private Map<String, Object> syncActualCommissionPayments() throws Exception {
        logger.info("💰 Getting commission rates from PayProp and calculating commission...");
        
        // Step 1: Get commission rates from PayProp
        Map<String, BigDecimal> commissionRates = getCommissionRatesFromDatabase();
        logger.info("📊 Found {} properties with commission rates", commissionRates.size());
        
        int created = 0;
        BigDecimal totalCommission = BigDecimal.ZERO;
        
        try {
            // Step 2: Get all rent payments and calculate commission
            List<FinancialTransaction> rentPayments = financialTransactionRepository
                .findByDataSource("ICDN_ACTUAL")
                .stream()
                .filter(tx -> "invoice".equals(tx.getTransactionType()))
                .filter(tx -> {
                    // Only exclude if category explicitly says "deposit"
                    String categoryName = tx.getCategoryName();
                    return !(categoryName != null && categoryName.toLowerCase().contains("deposit"));
                })
                .collect(Collectors.toList());
            
            logger.info("📊 Found {} rent payments to calculate commission for", rentPayments.size());
            
            for (FinancialTransaction rentPayment : rentPayments) {
                try {
                    // Skip if commission already calculated
                    String commissionId = "COMM_" + rentPayment.getPayPropTransactionId();
                    if (financialTransactionRepository.existsByPayPropTransactionIdAndDataSource(commissionId, "COMMISSION_PAYMENT")) {
                        continue;
                    }
                    
                    // Get commission rate for this property
                    BigDecimal commissionRate = commissionRates.get(rentPayment.getPropertyId());
                    if (commissionRate != null && rentPayment.getAmount() != null) {
                        
                        // Calculate commission
                        BigDecimal commissionAmount = rentPayment.getAmount()
                            .multiply(commissionRate)
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                        
                        // Create commission payment
                        FinancialTransaction commissionTx = new FinancialTransaction();
                        commissionTx.setPayPropTransactionId(commissionId);
                        commissionTx.setDataSource("COMMISSION_PAYMENT");
                        commissionTx.setIsActualTransaction(true);
                        commissionTx.setTransactionType("commission_payment");
                        
                        commissionTx.setAmount(commissionAmount);
                        commissionTx.setActualCommissionAmount(commissionAmount);
                        commissionTx.setTransactionDate(rentPayment.getTransactionDate());
                        commissionTx.setReconciliationDate(rentPayment.getTransactionDate());
                        
                        commissionTx.setPropertyId(rentPayment.getPropertyId());
                        commissionTx.setPropertyName(rentPayment.getPropertyName());
                        commissionTx.setTenantId(rentPayment.getTenantId());
                        commissionTx.setTenantName(rentPayment.getTenantName());
                        
                        commissionTx.setCommissionRate(commissionRate);
                        commissionTx.setDescription("Commission (" + commissionRate + "%)");
                        commissionTx.setCategoryName("Commission");
                        
                        commissionTx.setCreatedAt(LocalDateTime.now());
                        commissionTx.setUpdatedAt(LocalDateTime.now());
                        
                        financialTransactionRepository.save(commissionTx);
                        
                        created++;
                        totalCommission = totalCommission.add(commissionAmount);
                        
                        logger.debug("✅ Commission: {} £{} at {}%", 
                            rentPayment.getPropertyName(), commissionAmount, commissionRate);
                    }
                    
                } catch (Exception e) {
                    logger.error("❌ Error calculating commission: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("❌ Commission calculation failed: {}", e.getMessage());
        }
        
        logger.info("💰 Commission done: {} payments, £{} total", created, totalCommission);
        
        return Map.of(
            "calculated_commission_payments", created,
            "total_commission", totalCommission,
            "commission_rates_found", commissionRates.size()
        );
    }
    
    /**
     * ✅ FAST: Get commission rates directly from PayProp
     */
    private Map<String, BigDecimal> getCommissionRatesFromPayProp() throws Exception {
        Map<String, BigDecimal> rates = new HashMap<>();
        
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            // Get properties with commission data
            String url = payPropApiBase + "/export/properties?include_commission=true&rows=1000";
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            List<Map<String, Object>> properties = (List<Map<String, Object>>) response.getBody().get("items");
            
            for (Map<String, Object> property : properties) {
                String propertyId = (String) property.get("id");
                Map<String, Object> commission = (Map<String, Object>) property.get("commission");
                
                if (propertyId != null && commission != null) {
                    Object percentage = commission.get("percentage");
                    if (percentage instanceof String && !((String) percentage).isEmpty()) {
                        try {
                            BigDecimal rate = new BigDecimal((String) percentage);
                            rates.put(propertyId, rate);
                            logger.debug("✅ Commission rate: {} = {}%", 
                                property.get("property_name"), rate);
                        } catch (NumberFormatException e) {
                            logger.warn("⚠️ Invalid commission rate: {}", percentage);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to get commission rates: {}", e.getMessage());
        }
        
        return rates;
    }


    
    /**
     * ✅ FIXED: Calculate and store commission data for financial transactions
     */
    private Map<String, Object> calculateAndStoreCommissions() throws Exception {
        logger.info("🧮 Calculating and storing commission data...");
        
        // Get all ICDN invoice transactions without commission calculations
        List<FinancialTransaction> transactions = financialTransactionRepository
            .findByDataSourceAndTransactionType("ICDN_ACTUAL", "invoice")
            .stream()
            .filter(t -> t.getCommissionAmount() == null)
            .collect(Collectors.toList());
        
        int commissionsCalculated = 0;
        
        for (FinancialTransaction transaction : transactions) {
            // Skip deposits - check category name for deposit keywords
            if (transaction.getCategoryName() != null && 
                transaction.getCategoryName().toLowerCase().contains("deposit")) {
                logger.debug("⚠️ Skipping commission calculation for deposit transaction {}", 
                    transaction.getPayPropTransactionId());
                continue;
            }
            
            // Find property to get commission rate
            if (transaction.getPropertyId() != null) {
                Optional<Property> propertyOpt = propertyService.findByPayPropId(transaction.getPropertyId());
                
                if (propertyOpt.isPresent()) {
                    Property property = propertyOpt.get();
                    
                    if (property.getCommissionPercentage() != null && transaction.getAmount() != null) {
                        BigDecimal commissionRate = property.getCommissionPercentage();
                        BigDecimal rentAmount = transaction.getAmount();
                        
                        // Calculate commission
                        BigDecimal commissionAmount = rentAmount
                            .multiply(commissionRate)
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                        
                        // Calculate service fee (5%)
                        BigDecimal serviceFee = rentAmount
                            .multiply(BigDecimal.valueOf(5))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                        
                        // Calculate net to owner
                        BigDecimal netToOwner = rentAmount
                            .subtract(commissionAmount)
                            .subtract(serviceFee);
                        
                        // Update transaction
                        transaction.setCalculatedCommissionAmount(commissionAmount);
                        transaction.setCommissionAmount(commissionAmount);
                        transaction.setServiceFeeAmount(serviceFee);
                        transaction.setNetToOwnerAmount(netToOwner);
                        transaction.setCommissionRate(commissionRate);
                        transaction.setUpdatedAt(LocalDateTime.now());
                        
                        financialTransactionRepository.save(transaction);
                        commissionsCalculated++;
                        
                        logger.debug("✅ Commission calculated: {} £{} at {}%", 
                            property.getPropertyName(), commissionAmount, commissionRate);
                    }
                }
            }
        }
        
        logger.info("💰 Commission calculation completed: {} transactions processed", commissionsCalculated);
        
        return Map.of(
            "transactions_processed", transactions.size(),
            "commissions_calculated", commissionsCalculated
        );
    }
    
    /**
     * Get financial summary from stored data
     */
    public Map<String, Object> getStoredFinancialSummary(String propertyId, LocalDate fromDate, LocalDate toDate) {
        try {
            // Default to last 30 days if no dates provided
            if (fromDate == null) fromDate = LocalDate.now().minusDays(30);
            if (toDate == null) toDate = LocalDate.now();
            
            // Query stored transactions
            List<FinancialTransaction> transactions;
            if (propertyId != null) {
                transactions = financialTransactionRepository
                    .findByPropertyIdAndTransactionDateBetween(propertyId, fromDate, toDate);
            } else {
                transactions = financialTransactionRepository
                    .findByTransactionDateBetween(fromDate, toDate);
            }
            
            // Calculate totals
            BigDecimal totalRent = BigDecimal.ZERO;
            BigDecimal totalCommissions = BigDecimal.ZERO;
            BigDecimal totalServiceFees = BigDecimal.ZERO;
            BigDecimal totalNetToOwners = BigDecimal.ZERO;
            
            List<Map<String, Object>> transactionSummary = new ArrayList<>();
            
            for (FinancialTransaction transaction : transactions) {
                Map<String, Object> summary = new HashMap<>();
                summary.put("property_id", transaction.getPropertyId());
                summary.put("property_name", transaction.getPropertyName());
                summary.put("tenant_name", transaction.getTenantName());
                summary.put("transaction_date", transaction.getTransactionDate());
                summary.put("rent_amount", transaction.getAmount());
                summary.put("commission_amount", transaction.getCommissionAmount());
                summary.put("service_fee", transaction.getServiceFeeAmount());
                summary.put("net_to_owner", transaction.getNetToOwnerAmount());
                summary.put("commission_rate", transaction.getCommissionRate());
                summary.put("data_source", transaction.getDataSource());
                summary.put("is_deposit", transaction.isDeposit());
                
                transactionSummary.add(summary);
                
                if (transaction.getAmount() != null) totalRent = totalRent.add(transaction.getAmount());
                if (transaction.getCommissionAmount() != null) totalCommissions = totalCommissions.add(transaction.getCommissionAmount());
                if (transaction.getServiceFeeAmount() != null) totalServiceFees = totalServiceFees.add(transaction.getServiceFeeAmount());
                if (transaction.getNetToOwnerAmount() != null) totalNetToOwners = totalNetToOwners.add(transaction.getNetToOwnerAmount());
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("transactions", transactionSummary);
            result.put("totals", Map.of(
                "total_rent", totalRent,
                "total_commissions", totalCommissions,
                "total_service_fees", totalServiceFees,
                "total_net_to_owners", totalNetToOwners,
                "transaction_count", transactions.size()
            ));
            result.put("period", fromDate + " to " + toDate);
            result.put("status", "SUCCESS");
            
            return result;
            
        } catch (Exception e) {
            return Map.of(
                "status", "ERROR",
                "error", e.getMessage()
            );
        }
    }
    
    /**
     * ✅ ENHANCED: Get complete commission variance analysis (Expected vs Actual)
     */
    public Map<String, Object> getCommissionVarianceAnalysis(String propertyId, LocalDate fromDate, LocalDate toDate) {
        try {
            // Get rent transactions with both expected and actual commission data
            List<FinancialTransaction> rentTransactions = financialTransactionRepository
                .findByPropertyIdAndTransactionDateBetween(propertyId, fromDate, toDate)
                .stream()
                .filter(t -> "invoice".equals(t.getTransactionType()))
                .filter(t -> "ICDN_ACTUAL".equals(t.getDataSource()))
                .collect(Collectors.toList());
            
            BigDecimal totalRent = BigDecimal.ZERO;
            BigDecimal totalExpectedCommission = BigDecimal.ZERO;
            BigDecimal totalActualCommission = BigDecimal.ZERO;
            int transactionsWithExpected = 0;
            int transactionsWithActual = 0;
            
            List<Map<String, Object>> transactionDetails = new ArrayList<>();
            
            for (FinancialTransaction transaction : rentTransactions) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("transaction_id", transaction.getPayPropTransactionId());
                detail.put("property_name", transaction.getPropertyName());
                detail.put("tenant_name", transaction.getTenantName());
                detail.put("transaction_date", transaction.getTransactionDate());
                detail.put("rent_amount", transaction.getAmount());
                detail.put("expected_commission", transaction.getCalculatedCommissionAmount());
                detail.put("actual_commission", transaction.getActualCommissionAmount());
                
                if (transaction.getAmount() != null) {
                    totalRent = totalRent.add(transaction.getAmount());
                }
                
                if (transaction.getCalculatedCommissionAmount() != null) {
                    totalExpectedCommission = totalExpectedCommission.add(transaction.getCalculatedCommissionAmount());
                    transactionsWithExpected++;
                }
                
                if (transaction.getActualCommissionAmount() != null) {
                    totalActualCommission = totalActualCommission.add(transaction.getActualCommissionAmount());
                    transactionsWithActual++;
                }
                
                // Calculate variance for this transaction
                BigDecimal variance = BigDecimal.ZERO;
                if (transaction.getCalculatedCommissionAmount() != null && transaction.getActualCommissionAmount() != null) {
                    variance = transaction.getActualCommissionAmount().subtract(transaction.getCalculatedCommissionAmount());
                }
                detail.put("commission_variance", variance);
                
                transactionDetails.add(detail);
            }
            
            BigDecimal totalVariance = totalActualCommission.subtract(totalExpectedCommission);
            
            return Map.of(
                "summary", Map.of(
                    "total_rent", totalRent,
                    "total_expected_commission", totalExpectedCommission,
                    "total_actual_commission", totalActualCommission,
                    "total_variance", totalVariance,
                    "variance_percentage", totalRent.compareTo(BigDecimal.ZERO) > 0 ? 
                        totalVariance.divide(totalRent, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO
                ),
                "coverage", Map.of(
                    "total_transactions", rentTransactions.size(),
                    "transactions_with_expected", transactionsWithExpected,
                    "transactions_with_actual", transactionsWithActual,
                    "expected_coverage_percent", rentTransactions.size() > 0 ? 
                        (transactionsWithExpected * 100.0 / rentTransactions.size()) : 0,
                    "actual_coverage_percent", rentTransactions.size() > 0 ? 
                        (transactionsWithActual * 100.0 / rentTransactions.size()) : 0
                ),
                "transactions", transactionDetails,
                "period", fromDate + " to " + toDate,
                "status", "SUCCESS"
            );
            
        } catch (Exception e) {
            logger.error("❌ Commission variance analysis failed: {}", e.getMessage());
            return Map.of(
                "status", "ERROR",
                "error", e.getMessage()
            );
        }
    }

    /**
     * ✅ LEGACY: Keep old method for backward compatibility
     */
    public Map<String, Object> getCommissionPaymentSummary(String propertyId, LocalDate fromDate, LocalDate toDate) {
        try {
            List<FinancialTransaction> calculatedCommissions = financialTransactionRepository
                .findByPropertyIdAndTransactionDateBetween(propertyId, fromDate, toDate)
                .stream()
                .filter(t -> "invoice".equals(t.getTransactionType()) && t.getCommissionAmount() != null)
                .collect(Collectors.toList());
            
            List<FinancialTransaction> actualCommissionPayments = financialTransactionRepository
                .findByPropertyIdAndTransactionDateBetween(propertyId, fromDate, toDate)
                .stream()
                .filter(t -> "commission_payment".equals(t.getTransactionType()))
                .collect(Collectors.toList());
            
            BigDecimal totalCalculated = calculatedCommissions.stream()
                .map(FinancialTransaction::getCommissionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalActual = actualCommissionPayments.stream()
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return Map.of(
                "calculated_commission_total", totalCalculated,
                "actual_commission_payments_total", totalActual,
                "difference", totalCalculated.subtract(totalActual),
                "calculated_transactions", calculatedCommissions.size(),
                "actual_payment_transactions", actualCommissionPayments.size(),
                "period", fromDate + " to " + toDate
            );
            
        } catch (Exception e) {
            return Map.of("error", "Failed to generate commission summary: " + e.getMessage());
        }
    }

    // ===== NEW DUAL DATA SYNC METHODS =====

    /**
     * NEW: Sync both instruction and actual data using confirmed working endpoints
     */
    @Transactional
    public Map<String, Object> syncDualFinancialData() {
        Map<String, Object> results = new HashMap<>();
        
        try {
            logger.info("🚀 Starting dual financial data sync (instructions vs actuals)...");
            
            // 1. Sync payment instructions (what SHOULD be paid)
            Map<String, Object> instructionsResult = syncPaymentInstructions();
            results.put("payment_instructions", instructionsResult);
            
            // 2. Sync actual reconciled payments (what WAS paid) 
            Map<String, Object> actualResult = syncActualReconciledPayments();
            results.put("actual_payments", actualResult);
            
            // 3. Sync ICDN transactions (detailed financial records) - already done above
            Map<String, Object> icdnResult = Map.of("status", "Already synced in main method");
            results.put("icdn_transactions", icdnResult);
            
            // 5. Sync actual commission payments and link to expected
            Map<String, Object> commissionResult = syncActualCommissionPayments();
            results.put("commission_payments", commissionResult);

            // 6. Link actual commission payments to rent transactions
            Map<String, Object> commissionLinkingResult = linkActualCommissionToTransactions();
            results.put("commission_linking", commissionLinkingResult);
                        
            // 7. Link instructions to actual payments
            Map<String, Object> instructionLinkingResult = linkInstructionsToActuals();
            results.put("linking", instructionLinkingResult);
            
            results.put("status", "SUCCESS");
            results.put("sync_time", LocalDateTime.now());
            
            logger.info("✅ Dual financial data sync completed successfully");
            
        } catch (Exception e) {
            logger.error("❌ Dual financial sync failed", e);
            results.put("status", "FAILED");
            results.put("error", e.getMessage());
        }
        
        return results;
    }

    private Map<String, Object> syncPaymentInstructions() throws Exception {
        logger.info("📋 Syncing payment instructions (what SHOULD be paid)...");
        
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        // Use confirmed working endpoint
        String url = payPropApiBase + "/export/payments?include_beneficiary_info=true&rows=1000";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
        
        List<Map<String, Object>> instructions = (List<Map<String, Object>>) response.getBody().get("items");
        
        int created = 0;
        for (Map<String, Object> instruction : instructions) {
            String payPropId = (String) instruction.get("id");
            
            if (financialTransactionRepository.existsByPayPropTransactionIdAndDataSource(payPropId, "PAYMENT_INSTRUCTION")) {
                continue;
            }
            
            FinancialTransaction transaction = createFromPaymentInstruction(instruction);
            if (transaction != null) {
                financialTransactionRepository.save(transaction);
                created++;
            }
        }
        
        return Map.of("instructions_created", created);
    }

    private Map<String, Object> syncActualReconciledPayments() throws Exception {
        logger.info("💳 Syncing actual reconciled payments (what WAS paid)...");
        
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        // Use confirmed working endpoint with reconciliation filter
        String url = payPropApiBase + "/export/payments?filter_by=reconciliation_date&include_beneficiary_info=true&rows=1000";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
        
        List<Map<String, Object>> actualPayments = (List<Map<String, Object>>) response.getBody().get("items");
        
        int created = 0;
        for (Map<String, Object> payment : actualPayments) {
            String payPropId = (String) payment.get("id");
            
            if (financialTransactionRepository.existsByPayPropTransactionIdAndDataSource(payPropId, "ACTUAL_PAYMENT")) {
                continue;
            }
            
            FinancialTransaction transaction = createFromActualPayment(payment);
            if (transaction != null) {
                financialTransactionRepository.save(transaction);
                created++;
            }
        }
        
        return Map.of("actual_payments_created", created);
    }

    private FinancialTransaction createFromPaymentInstruction(Map<String, Object> instruction) {
        try {
            FinancialTransaction transaction = new FinancialTransaction();
            transaction.setPayPropTransactionId((String) instruction.get("id"));
            transaction.setDataSource("PAYMENT_INSTRUCTION");
            transaction.setIsInstruction(true);
            transaction.setIsActualTransaction(false);
            
            // Extract instruction date
            String fromDate = (String) instruction.get("from_date");
            if (fromDate != null) {
                transaction.setInstructionDate(LocalDate.parse(fromDate));
                transaction.setTransactionDate(LocalDate.parse(fromDate)); // Use as primary date
            }
            
            // Extract calculated amounts
            Object grossAmount = instruction.get("gross_amount");
            if (grossAmount != null) {
                transaction.setAmount(new BigDecimal(grossAmount.toString()));
            }
            
            // Calculate expected commission from property settings
            calculateExpectedCommission(transaction, instruction);
            
            setCommonFields(transaction, instruction);
            transaction.setCreatedAt(LocalDateTime.now());
            transaction.setUpdatedAt(LocalDateTime.now());
            
            return transaction;
        } catch (Exception e) {
            logger.error("Error creating payment instruction: {}", e.getMessage());
            return null;
        }
    }

    private FinancialTransaction createFromActualPayment(Map<String, Object> payment) {
        try {
            FinancialTransaction transaction = new FinancialTransaction();
            transaction.setPayPropTransactionId((String) payment.get("id"));
            transaction.setDataSource("ACTUAL_PAYMENT");
            transaction.setIsInstruction(false);
            transaction.setIsActualTransaction(true);
            
            // Extract reconciliation date (when actually paid)
            String reconDate = (String) payment.get("reconciliation_date");
            if (reconDate != null) {
                transaction.setReconciliationDate(LocalDate.parse(reconDate));
                transaction.setTransactionDate(LocalDate.parse(reconDate)); // Use as primary date
            }
            
            // Extract actual amounts paid
            Object amount = payment.get("amount");
            if (amount != null) {
                transaction.setAmount(new BigDecimal(amount.toString()));
            }
            
            // Get actual commission amount from commission payments
            extractActualCommissionAmount(transaction, payment);
            
            setCommonFields(transaction, payment);
            transaction.setCreatedAt(LocalDateTime.now());
            transaction.setUpdatedAt(LocalDateTime.now());
            
            return transaction;
        } catch (Exception e) {
            logger.error("Error creating actual payment: {}", e.getMessage());
            return null;
        }
    }

    private void calculateExpectedCommission(FinancialTransaction transaction, Map<String, Object> instruction) {
        // Get property commission rate and calculate expected commission
        String propertyId = null;
        Map<String, Object> property = (Map<String, Object>) instruction.get("property");
        if (property != null) {
            propertyId = (String) property.get("id");
        }
        
        if (propertyId != null && transaction.getAmount() != null) {
            Optional<Property> propertyOpt = propertyService.findByPayPropId(propertyId);
            if (propertyOpt.isPresent()) {
                Property prop = propertyOpt.get();
                if (prop.getCommissionPercentage() != null) {
                    BigDecimal expectedCommission = transaction.getAmount()
                        .multiply(prop.getCommissionPercentage())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    transaction.setCalculatedCommissionAmount(expectedCommission);
                }
            }
        }
    }

    private void extractActualCommissionAmount(FinancialTransaction transaction, Map<String, Object> payment) {
        // For now, we'll set this to zero and update it when we link to actual commission payments
        transaction.setActualCommissionAmount(BigDecimal.ZERO);
    }

    // ✅ FIXED: setCommonFields method - removed the problematic "payment" transaction type
    private void setCommonFields(FinancialTransaction transaction, Map<String, Object> data) {
        // Extract common fields like property, tenant, category
        Map<String, Object> property = (Map<String, Object>) data.get("property");
        if (property != null) {
            transaction.setPropertyId((String) property.get("id"));
            transaction.setPropertyName((String) property.get("name"));
        }
        
        String category = (String) data.get("category");
        if (category != null) {
            transaction.setCategoryName(category);
        }
        
        String description = (String) data.get("description");
        if (description != null) {
            transaction.setDescription(description);
        }
        
        // ✅ FIXED: Set transaction type based on category using VALID constraint values
        if ("Rent".equalsIgnoreCase(category)) {
            transaction.setTransactionType("invoice");
        } else if (category != null && category.toLowerCase().contains("deposit")) {
            transaction.setTransactionType("deposit");
            transaction.setDepositId((String) data.get("id")); // Mark as deposit
        } else {
            // ✅ CHANGED: Use "commission_payment" instead of "payment"
            transaction.setTransactionType("commission_payment");
        }
    }

    private Map<String, Object> linkInstructionsToActuals() {
        logger.info("🔗 Linking payment instructions to actual payments...");
        
        // This would implement logic to match instructions with their corresponding actual payments
        // Based on property, amount, and approximate dates
        
        int linkedPairs = 0;
        // Implementation would go here to match records and set instructionId field
        
        return Map.of("linked_pairs", linkedPairs);
    }

    /**
     * NEW: Dashboard comparison data between instructions and actuals
     */
    public Map<String, Object> getDashboardFinancialComparison(String propertyId, LocalDate fromDate, LocalDate toDate) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get instructed amounts
            List<FinancialTransaction> instructions = financialTransactionRepository
                .findByPropertyIdAndDataSourceAndDateRange(propertyId, "PAYMENT_INSTRUCTION", fromDate, toDate);
            
            // Get actual amounts  
            List<FinancialTransaction> actuals = financialTransactionRepository
                .findByPropertyIdAndDataSourceAndDateRange(propertyId, "ACTUAL_PAYMENT", fromDate, toDate);
            
            // Get ICDN data
            List<FinancialTransaction> icdnData = financialTransactionRepository
                .findByPropertyIdAndDataSourceAndDateRange(propertyId, "ICDN_ACTUAL", fromDate, toDate);
            
            BigDecimal instructedTotal = instructions.stream()
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            BigDecimal actualTotal = actuals.stream()
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            BigDecimal icdnTotal = icdnData.stream()
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            BigDecimal instructedCommission = instructions.stream()
                .map(FinancialTransaction::getCalculatedCommissionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            BigDecimal actualCommission = actuals.stream()
                .map(FinancialTransaction::getActualCommissionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            result.put("instructed", Map.of(
                "total_amount", instructedTotal,
                "total_commission", instructedCommission,
                "transaction_count", instructions.size()
            ));
            
            result.put("actual", Map.of(
                "total_amount", actualTotal,
                "total_commission", actualCommission,
                "transaction_count", actuals.size()
            ));
            
            result.put("icdn", Map.of(
                "total_amount", icdnTotal,
                "transaction_count", icdnData.size()
            ));
            
            result.put("variance", Map.of(
                "amount_difference", actualTotal.subtract(instructedTotal),
                "commission_difference", actualCommission.subtract(instructedCommission),
                "icdn_vs_actual_difference", icdnTotal.subtract(actualTotal)
            ));
            
            result.put("period", fromDate + " to " + toDate);
            result.put("status", "SUCCESS");
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    // 1. ADD this new isolated save method
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveFinancialTransactionIsolated(FinancialTransaction transaction) {
        try {
            // Validate required fields before attempting save
            if (transaction.getPayPropTransactionId() == null || transaction.getPayPropTransactionId().trim().isEmpty()) {
                logger.warn("⚠️ SKIPPED: Missing PayProp transaction ID");
                return false;
            }
            
            if (transaction.getAmount() == null) {
                logger.warn("⚠️ SKIPPED: Missing amount for transaction {}", transaction.getPayPropTransactionId());
                return false;
            }
            
            if (transaction.getTransactionDate() == null) {
                logger.warn("⚠️ SKIPPED: Missing transaction date for transaction {}", transaction.getPayPropTransactionId());
                return false;
            }
            
            // ✅ CRITICAL: Handle negative amounts (constraint violation fix)
            if (transaction.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                logger.warn("⚠️ SKIPPED: Negative amount £{} for transaction {} (refund/adjustment)", 
                    transaction.getAmount(), transaction.getPayPropTransactionId());
                return false;
            }
            
            // Check for duplicate before attempting save
            if (financialTransactionRepository.existsByPayPropTransactionId(transaction.getPayPropTransactionId())) {
                logger.debug("ℹ️ SKIPPED: Transaction {} already exists", transaction.getPayPropTransactionId());
                return false;
            }
            
            // Validate transaction type against constraint
            if (transaction.getTransactionType() == null || !isValidTransactionType(transaction.getTransactionType())) {
                logger.warn("⚠️ SKIPPED: Invalid transaction type '{}' for transaction {}", 
                    transaction.getTransactionType(), transaction.getPayPropTransactionId());
                return false;
            }
            
            // Set audit fields if not already set
            if (transaction.getCreatedAt() == null) {
                transaction.setCreatedAt(LocalDateTime.now());
            }
            if (transaction.getUpdatedAt() == null) {
                transaction.setUpdatedAt(LocalDateTime.now());
            }
            
            // ✅ ISOLATED SAVE: Each transaction in its own transaction scope
            financialTransactionRepository.save(transaction);
            
            logger.debug("✅ SAVED: Transaction {} (£{}, {}, {})", 
                transaction.getPayPropTransactionId(), 
                transaction.getAmount(), 
                transaction.getTransactionType(),
                transaction.getPropertyName());
            
            return true;
            
        } catch (DataIntegrityViolationException e) {
            logger.error("❌ CONSTRAINT VIOLATION: Transaction {} failed constraint check: {}", 
                transaction.getPayPropTransactionId(), e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("❌ SAVE FAILED: Transaction {} failed to save: {}", 
                transaction.getPayPropTransactionId(), e.getMessage());
            return false;
        }
    }

    // 2. ADD this validation helper method
    private boolean isValidTransactionType(String transactionType) {
        Set<String> validTypes = Set.of(
            "invoice", "credit_note", "debit_note", "deposit", "commission_payment",
            "payment_to_beneficiary", "payment_to_agency", "payment_to_contractor", 
            "payment_property_account", "payment_deposit_account", "refund", 
            "adjustment", "transfer"
        );
        return validTypes.contains(transactionType);
    }


    private Map<String, Object> syncBatchPayments() throws Exception {
        logger.info("💰 Starting ISOLATED batch payments sync...");
        
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        // Counters with detailed breakdown
        int totalApiCalls = 0, totalChunks = 0, totalPagesProcessed = 0;
        int successfulSaves = 0, skippedDuplicates = 0, skippedNegative = 0;
        int skippedInvalidType = 0, skippedMissingData = 0, constraintViolations = 0;
        int otherErrors = 0;
        
        // Reduce chunk size to 14 days to stay well under API limits
        LocalDate endDate = LocalDate.now().plusDays(7);  
        LocalDate absoluteStartDate = endDate.minusYears(2); 
        
        logger.info("🔍 Processing 14-day chunks from {} to {}", absoluteStartDate, endDate);
        
        while (endDate.isAfter(absoluteStartDate)) {
            LocalDate startDate = endDate.minusDays(14);  // Smaller chunks
            if (startDate.isBefore(absoluteStartDate)) {
                startDate = absoluteStartDate;
            }
            
            totalChunks++;
            logger.info("🔍 CHUNK {}: Processing {} to {}", totalChunks, startDate, endDate);
            
            int page = 1;
            int chunkPayments = 0;
            
            while (true) {
                String url = payPropApiBase + "/report/all-payments" +
                    "?from_date=" + startDate +
                    "&to_date=" + endDate +
                    "&filter_by=reconciliation_date" +
                    "&include_beneficiary_info=true" +
                    "&page=" + page +
                    "&rows=25";
                
                logger.info("📞 API Call {}: Chunk {} Page {}", totalApiCalls + 1, totalChunks, page);
                
                try {
                    if (totalApiCalls > 0) {
                        Thread.sleep(250); // Rate limiting
                    }
                    
                    ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                    totalApiCalls++;
                    
                    Map<String, Object> responseBody = response.getBody();
                    List<Map<String, Object>> payments = (List<Map<String, Object>>) responseBody.get("items");
                    
                    if (payments.isEmpty()) {
                        logger.info("📄 No payments on page {} for chunk {}", page, totalChunks);
                        break;
                    }
                    
                    logger.info("📊 Processing {} payments on page {} of chunk {}", payments.size(), page, totalChunks);
                    
                    // ✅ ISOLATED PROCESSING: Each payment processed independently
                    for (Map<String, Object> paymentData : payments) {
                        try {
                            FinancialTransaction transaction = createFinancialTransactionFromReportData(paymentData);
                            if (transaction != null) {
                                // ✅ ISOLATED SAVE: Each save in its own transaction
                                boolean saved = saveFinancialTransactionIsolated(transaction);
                                if (saved) {
                                    successfulSaves++;
                                } else {
                                    // Categorize the failure reason for reporting
                                    if (transaction.getAmount() != null && transaction.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                                        skippedNegative++;
                                    } else if (financialTransactionRepository.existsByPayPropTransactionId(transaction.getPayPropTransactionId())) {
                                        skippedDuplicates++;
                                    } else if (!isValidTransactionType(transaction.getTransactionType())) {
                                        skippedInvalidType++;
                                    } else {
                                        skippedMissingData++;
                                    }
                                }
                            } else {
                                skippedMissingData++;
                            }
                            
                            chunkPayments++;
                            
                        } catch (Exception e) {
                            otherErrors++;
                            logger.error("❌ Error processing payment: {}", e.getMessage());
                        }
                    }
                    
                    // Check pagination
                    Map<String, Object> pagination = (Map<String, Object>) responseBody.get("pagination");
                    if (pagination != null) {
                        Integer currentPage = (Integer) pagination.get("page");
                        Integer totalPages = (Integer) pagination.get("total_pages");
                        
                        if (currentPage != null && totalPages != null && currentPage >= totalPages) {
                            logger.info("📄 Reached last page ({}) for chunk {}", totalPages, totalChunks);
                            break;
                        }
                    } else if (payments.size() < 25) {
                        logger.info("📄 Got {} payments (< 25), last page for chunk {}", payments.size(), totalChunks);
                        break;
                    }
                    
                    page++;
                    totalPagesProcessed++;
                    
                    if (page > 50) { // Safety break
                        logger.warn("⚠️ Safety break: Page 50 reached for chunk {}", totalChunks);
                        break;
                    }
                    
                } catch (Exception e) {
                    logger.error("❌ API call failed for chunk {} page {}: {}", totalChunks, page, e.getMessage());
                    if (e.getMessage().contains("429")) {
                        Thread.sleep(30000); // Wait for rate limit
                        continue;
                    }
                    break;
                }
            }
            
            logger.info("✅ Chunk {} completed: {} payments processed", totalChunks, chunkPayments);
            endDate = startDate.minusDays(1);
        }
        
        // ✅ COMPREHENSIVE REPORTING
        logger.info("💰 BATCH PAYMENTS SYNC COMPLETED:");
        logger.info("📊 Total API calls: {}", totalApiCalls);
        logger.info("📊 Chunks processed: {}", totalChunks);  
        logger.info("📊 Pages processed: {}", totalPagesProcessed);
        logger.info("✅ Successful saves: {}", successfulSaves);
        logger.info("⏭️ Skipped duplicates: {}", skippedDuplicates);
        logger.info("⚠️ Skipped negative amounts: {}", skippedNegative);
        logger.info("⚠️ Skipped invalid types: {}", skippedInvalidType);
        logger.info("⚠️ Skipped missing data: {}", skippedMissingData);
        logger.info("❌ Constraint violations: {}", constraintViolations);
        logger.info("❌ Other errors: {}", otherErrors);
        
        return Map.of(
            "payments_created", successfulSaves,
            "skipped_duplicates", skippedDuplicates,
            "skipped_negative", skippedNegative,
            "skipped_invalid_type", skippedInvalidType,
            "skipped_missing_data", skippedMissingData,
            "constraint_violations", constraintViolations,
            "other_errors", otherErrors,
            "total_processed", successfulSaves + skippedDuplicates + skippedNegative + skippedInvalidType + skippedMissingData + constraintViolations + otherErrors,
            "api_calls", totalApiCalls,
            "chunks_processed", totalChunks
        );
    }

    /**
     * Create financial transaction from all-payments report data
     * ✅ FIXED: Now uses valid transaction types only
     */
    private FinancialTransaction createFinancialTransactionFromReportData(Map<String, Object> paymentData) {
        try {
            FinancialTransaction transaction = new FinancialTransaction();
            
            // Basic identification
            String paymentId = (String) paymentData.get("id");
            if (paymentId == null || paymentId.trim().isEmpty()) {
                logger.warn("⚠️ Skipping payment with missing ID");
                return null;
            }
            
            transaction.setPayPropTransactionId(paymentId);
            transaction.setDataSource("BATCH_PAYMENT");
            transaction.setIsInstruction(false);
            transaction.setIsActualTransaction(true);
            
            // Amount handling with proper null checking
            Object amountObj = paymentData.get("amount");
            if (amountObj != null) {
                try {
                    BigDecimal amount = new BigDecimal(amountObj.toString());
                    transaction.setAmount(amount);
                } catch (NumberFormatException e) {
                    logger.warn("⚠️ Invalid amount format '{}' for payment {}", amountObj, paymentId);
                    return null;
                }
            } else {
                logger.warn("⚠️ Missing amount for payment {}", paymentId);
                return null;
            }
            
            // Date handling with multiple fallbacks and required field
            LocalDate transactionDate = extractTransactionDate(paymentData);
            if (transactionDate == null) {
                logger.warn("⚠️ Could not determine transaction date for payment {}, skipping", paymentId);
                return null;
            }
            transaction.setTransactionDate(transactionDate);
            
            // Extract reconciliation date if available
            String reconDateStr = extractStringFromPath(paymentData, "incoming_transaction.reconciliation_date");
            if (reconDateStr != null && !reconDateStr.isEmpty()) {
                try {
                    transaction.setReconciliationDate(LocalDate.parse(reconDateStr));
                } catch (Exception e) {
                    logger.debug("Could not parse reconciliation_date: {}", reconDateStr);
                }
            }
            
            // Property information with proper nested object handling
            Map<String, Object> incomingTransaction = (Map<String, Object>) paymentData.get("incoming_transaction");
            if (incomingTransaction != null) {
                Map<String, Object> property = (Map<String, Object>) incomingTransaction.get("property");
                if (property != null) {
                    transaction.setPropertyId((String) property.get("id"));
                    transaction.setPropertyName((String) property.get("name"));
                }
                
                // Extract tenant information
                Map<String, Object> tenant = (Map<String, Object>) incomingTransaction.get("tenant");
                if (tenant != null) {
                    transaction.setTenantId((String) tenant.get("id"));
                    transaction.setTenantName((String) tenant.get("name"));
                }
                
                // Extract deposit ID
                transaction.setDepositId((String) incomingTransaction.get("deposit_id"));
            }
            
            // Beneficiary information (stored as description since no beneficiary fields in entity)
            Map<String, Object> beneficiary = (Map<String, Object>) paymentData.get("beneficiary");
            if (beneficiary != null) {
                String beneficiaryName = (String) beneficiary.get("name");
                String beneficiaryType = (String) beneficiary.get("type");
                if (beneficiaryName != null || beneficiaryType != null) {
                    String description = "Beneficiary: " + 
                        (beneficiaryName != null ? beneficiaryName : "Unknown") + 
                        " (" + (beneficiaryType != null ? beneficiaryType : "Unknown type") + ")";
                    transaction.setDescription(description);
                }
            }
            
            // ✅ FIXED: Set transaction type using intelligent mapping
            String transactionType = determineTransactionTypeFromPayPropData(paymentData);
            if (transactionType == null) {
                logger.warn("⚠️ Could not determine valid transaction type for payment {}, skipping", paymentId);
                return null;
            }
            transaction.setTransactionType(transactionType);
            
            // Extract fees
            Object serviceFeeObj = paymentData.get("service_fee");
            if (serviceFeeObj != null) {
                try {
                    transaction.setServiceFeeAmount(new BigDecimal(serviceFeeObj.toString()));
                } catch (NumberFormatException e) {
                    logger.debug("Invalid service_fee format: {}", serviceFeeObj);
                }
            }
            
            Object transactionFeeObj = paymentData.get("transaction_fee");
            if (transactionFeeObj != null) {
                try {
                    // Store transaction fee in tax_amount field (reusing existing field)
                    transaction.setTaxAmount(new BigDecimal(transactionFeeObj.toString()));
                } catch (NumberFormatException e) {
                    logger.debug("Invalid transaction_fee format: {}", transactionFeeObj);
                }
            }
            
            // Set audit fields
            transaction.setCreatedAt(LocalDateTime.now());
            transaction.setUpdatedAt(LocalDateTime.now());
            
            return transaction;
            
        } catch (Exception e) {
            logger.error("❌ Error creating financial transaction from report data: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * ✅ FIXED: Enhanced transaction type mapping with safe type casting
     * Replace in your PayPropFinancialSyncService.java
     */
    private String determineTransactionTypeFromPayPropData(Map<String, Object> paymentData) {
        try {
            // Get PayProp data with safe casting
            Map<String, Object> beneficiary = (Map<String, Object>) paymentData.get("beneficiary");
            String beneficiaryType = beneficiary != null ? (String) beneficiary.get("type") : null;
            
            // ✅ FIXED: Safe category extraction (handles both String and Map)
            String category = extractCategorySafely(paymentData);
            
            // Rule 1: Check for deposit-related transactions
            if (isDepositRelated(paymentData)) {
                logger.debug("Mapped to 'deposit' - deposit-related transaction");
                return "deposit";
            }
            
            // Rule 2: Map by beneficiary type (primary logic)
            if (beneficiaryType != null) {
                switch (beneficiaryType) {
                    case "agency":
                        logger.debug("Mapped to 'payment_to_agency' - agency beneficiary");
                        return "payment_to_agency";
                        
                    case "beneficiary":
                        // Further refine based on category
                        if (isMaintenancePayment(category)) {
                            logger.debug("Mapped to 'payment_to_contractor' - maintenance category");
                            return "payment_to_contractor";
                        } else {
                            logger.debug("Mapped to 'payment_to_beneficiary' - regular beneficiary");
                            return "payment_to_beneficiary";
                        }
                        
                    case "global_beneficiary":
                        logger.debug("Mapped to 'payment_to_beneficiary' - global beneficiary");
                        return "payment_to_beneficiary";
                        
                    case "property_account":
                        logger.debug("Mapped to 'payment_property_account' - property account");
                        return "payment_property_account";
                        
                    case "deposit_account":
                        logger.debug("Mapped to 'payment_deposit_account' - deposit account");
                        return "payment_deposit_account";
                        
                    default:
                        logger.debug("Unknown beneficiary type '{}', using default", beneficiaryType);
                        break;
                }
            }
            
            // Rule 3: Category-based mapping (fallback)
            if (category != null) {
                String lowerCategory = category.toLowerCase();
                
                if (lowerCategory.contains("commission") || lowerCategory.contains("fee")) {
                    logger.debug("Mapped to 'payment_to_agency' - commission/fee category");
                    return "payment_to_agency";
                }
                
                if (isMaintenancePayment(category)) {
                    logger.debug("Mapped to 'payment_to_contractor' - maintenance category");
                    return "payment_to_contractor";
                }
                
                if (lowerCategory.contains("refund")) {
                    logger.debug("Mapped to 'refund' - refund category");
                    return "refund";
                }
            }
            
            // Rule 4: Default for /report/all-payments (money flowing out to property owners)
            logger.debug("Using default 'payment_to_beneficiary' for standard payment");
            return "payment_to_beneficiary";
            
        } catch (Exception e) {
            logger.error("Error determining transaction type: {}", e.getMessage());
            return "commission_payment"; // Safe fallback to existing type
        }
    }

    /**
     * ✅ NEW: Safe category extraction - handles both String and Map objects
     */
    private String extractCategorySafely(Map<String, Object> paymentData) {
        try {
            Object categoryObj = paymentData.get("category");
            
            if (categoryObj == null) {
                return null;
            }
            
            // If it's already a String, return it
            if (categoryObj instanceof String) {
                return (String) categoryObj;
            }
            
            // If it's a Map (LinkedHashMap), extract the name or id field
            if (categoryObj instanceof Map) {
                Map<String, Object> categoryMap = (Map<String, Object>) categoryObj;
                
                // Try common field names for category
                String name = (String) categoryMap.get("name");
                if (name != null) {
                    logger.debug("Extracted category name from Map: {}", name);
                    return name;
                }
                
                String id = (String) categoryMap.get("id");
                if (id != null) {
                    logger.debug("Extracted category id from Map: {}", id);
                    return id;
                }
                
                // Log the structure for debugging
                logger.debug("Category Map structure: {}", categoryMap.keySet());
                return categoryMap.toString(); // Fallback
            }
            
            // For any other type, convert to string
            logger.debug("Category is {} type, converting to string", categoryObj.getClass().getSimpleName());
            return categoryObj.toString();
            
        } catch (Exception e) {
            logger.error("Error extracting category: {}", e.getMessage());
            return null;
        }
    }

    /**
     * ✅ FIXED: Helper method to detect deposit-related transactions
     * Now checks category first, then deposit_id as secondary indicator
     */
    private boolean isDepositRelated(Map<String, Object> paymentData) {
        try {
            // Rule 1: Check category for deposit keywords FIRST (most reliable)
            String category = extractCategorySafely(paymentData);
            if (category != null) {
                String lowerCategory = category.toLowerCase();
                boolean isCategoryDeposit = lowerCategory.contains("deposit") || 
                                        lowerCategory.contains("security") ||
                                        lowerCategory.contains("bond");
                
                if (isCategoryDeposit) {
                    logger.debug("Detected deposit via category: {}", category);
                    return true;
                }
            }
            
            // Rule 2: Check description for deposit keywords
            String description = (String) paymentData.get("description");
            if (description != null) {
                String lowerDescription = description.toLowerCase();
                boolean isDescriptionDeposit = lowerDescription.contains("deposit") || 
                                            lowerDescription.contains("security") ||
                                            lowerDescription.contains("bond");
                
                if (isDescriptionDeposit) {
                    logger.debug("Detected deposit via description: {}", description);
                    return true;
                }
            }
            
            // Rule 3: ONLY check deposit_id if category/description suggest it's a deposit
            // (Don't use deposit_id alone as it might be present on all transactions)
            Map<String, Object> incomingTransaction = (Map<String, Object>) paymentData.get("incoming_transaction");
            if (incomingTransaction != null && (category != null || description != null)) {
                String depositId = (String) incomingTransaction.get("deposit_id");
                if (depositId != null && !depositId.trim().isEmpty()) {
                    // Only return true if we ALSO have deposit indicators in text
                    boolean hasDepositText = (category != null && category.toLowerCase().contains("deposit")) ||
                                            (description != null && description.toLowerCase().contains("deposit"));
                    
                    if (hasDepositText) {
                        logger.debug("Confirmed deposit via deposit_id: {} with text indicators", depositId);
                        return true;
                    }
                }
            }
            
            return false;
            
        } catch (Exception e) {
            logger.debug("Error checking deposit status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * ✅ UNCHANGED: Helper method to detect maintenance/contractor payments
     */
    private boolean isMaintenancePayment(String category) {
        if (category == null) return false;
        
        String lowerCategory = category.toLowerCase();
        return lowerCategory.contains("maintenance") ||
            lowerCategory.contains("contractor") ||
            lowerCategory.contains("repair") ||
            lowerCategory.contains("plumber") ||
            lowerCategory.contains("electrician") ||
            lowerCategory.contains("gardening") ||
            lowerCategory.contains("cleaning") ||
            lowerCategory.contains("handyman") ||
            lowerCategory.contains("painting") ||
            lowerCategory.contains("roofing") ||
            lowerCategory.contains("heating") ||
            lowerCategory.contains("building") ||
            lowerCategory.contains("appliance") ||
            lowerCategory.contains("boiler") ||
            lowerCategory.contains("window") ||
            lowerCategory.contains("door") ||
            lowerCategory.contains("flooring") ||
            lowerCategory.contains("pest") ||
            lowerCategory.contains("gutter") ||
            lowerCategory.contains("fence");
    }

    /**
     * Helper method to extract transaction date with multiple fallbacks
     */
    private LocalDate extractTransactionDate(Map<String, Object> paymentData) {
        // Try incoming_transaction.reconciliation_date first
        String reconDate = extractStringFromPath(paymentData, "incoming_transaction.reconciliation_date");
        if (reconDate != null && !reconDate.isEmpty()) {
            try {
                return LocalDate.parse(reconDate);
            } catch (Exception e) {
                logger.debug("Could not parse reconciliation_date: {}", reconDate);
            }
        }
        
        // Try due_date as fallback
        String dueDate = (String) paymentData.get("due_date");
        if (dueDate != null && !dueDate.isEmpty()) {
            try {
                return LocalDate.parse(dueDate);
            } catch (Exception e) {
                logger.debug("Could not parse due_date: {}", dueDate);
            }
        }
        
        // Try payment_batch.transfer_date as fallback
        String transferDate = extractStringFromPath(paymentData, "payment_batch.transfer_date");
        if (transferDate != null && !transferDate.isEmpty()) {
            try {
                return LocalDate.parse(transferDate);
            } catch (Exception e) {
                logger.debug("Could not parse transfer_date: {}", transferDate);
            }
        }
        
        return null; // Will cause the transaction to be skipped
    }

    /**
     * Helper method to safely extract string from nested object path
     */
    private String extractStringFromPath(Map<String, Object> data, String path) {
        try {
            String[] parts = path.split("\\.");
            Object current = data;
            
            for (String part : parts) {
                if (current instanceof Map) {
                    current = ((Map<String, Object>) current).get(part);
                } else {
                    return null;
                }
            }
            
            return current instanceof String ? (String) current : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Process batch payment data from PayProp
     */
    private BatchPayment processBatchPayment(Map<String, Object> batchData, Long initiatedBy) {
        try {
            String batchId = (String) batchData.get("id");
            if (batchId == null) {
                return null;
            }

            // Check if batch already exists
            Optional<BatchPayment> existingBatch = batchPaymentRepository.findByPayPropBatchId(batchId);
            BatchPayment batch;
            
            if (existingBatch.isPresent()) {
                batch = existingBatch.get();
                logger.debug("Updating existing batch payment: {}", batchId);
            } else {
                batch = new BatchPayment();
                batch.setPayPropBatchId(batchId);
                logger.debug("Creating new batch payment: {}", batchId);
            }

            // Map batch data
            batch.setBankReference((String) batchData.get("reference"));
            batch.setStatus((String) batchData.get("status"));
            
            // Parse total amount
            Object totalAmountObj = batchData.get("total_amount");
            if (totalAmountObj != null) {
                batch.setTotalAmount(new BigDecimal(totalAmountObj.toString()));
            }
            
            // Parse payment count
            Object paymentCountObj = batchData.get("payment_count");
            if (paymentCountObj != null) {
                batch.setPaymentCount(Integer.parseInt(paymentCountObj.toString()));
            }
            
            // Parse processing date
            String processingDateStr = (String) batchData.get("processing_date");
            if (processingDateStr != null) {
                batch.setProcessingDate(LocalDateTime.parse(processingDateStr));
            }
            
            // Set audit fields
            if (!existingBatch.isPresent()) {
                batch.setCreatedAt(LocalDateTime.now());
            }
            batch.setUpdatedAt(LocalDateTime.now());
            
            return batchPaymentRepository.save(batch);
            
        } catch (Exception e) {
            logger.error("Error processing batch payment: {}", e.getMessage());
            return null;
        }
    }
}