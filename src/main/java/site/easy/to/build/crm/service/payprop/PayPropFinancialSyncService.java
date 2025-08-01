// PayPropFinancialSyncService.java - Revised with reduced redundancy
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
import java.util.function.Function;
import java.util.stream.Collectors;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropFinancialSyncService {
    
    private static final Logger logger = LoggerFactory.getLogger(PayPropFinancialSyncService.class);
    
    private final PayPropOAuth2Service oAuth2Service;
    private final RestTemplate restTemplate;
    private final PayPropApiClient apiClient;
    
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
        PayPropApiClient apiClient,
        PropertyRepository propertyRepository,
        BeneficiaryRepository beneficiaryRepository,
        PaymentCategoryRepository paymentCategoryRepository,
        FinancialTransactionRepository financialTransactionRepository,
        BatchPaymentRepository batchPaymentRepository,
        PropertyService propertyService
    ) {
        this.oAuth2Service = oAuth2Service;
        this.restTemplate = restTemplate;
        this.apiClient = apiClient;
        this.propertyRepository = propertyRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.paymentCategoryRepository = paymentCategoryRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.batchPaymentRepository = batchPaymentRepository;
        this.propertyService = propertyService;
    }
    
    /**
     * Main method to sync all comprehensive financial data from PayProp
     */
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
            
            // 4. Sync Financial Transactions (ICDN) - Different API pattern
            Map<String, Object> transactionsResult = syncFinancialTransactions();
            syncResults.put("transactions", transactionsResult);
            
            // 5. Sync Batch Payments - Different API pattern with date chunks
            Map<String, Object> batchPaymentsResult = syncBatchPayments();
            syncResults.put("batch_payments", batchPaymentsResult);
            
            // 7. Calculate and store commission data
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
     * Generic paginated sync handler using PayPropApiClient
     */
    private Map<String, Object> syncPaginatedData(
            String endpoint, 
            Function<Map<String, Object>, SyncItemResult> processor,
            String dataType) {
        
        logger.info("📥 Syncing {} with pagination...", dataType);
        
        int created = 0, updated = 0, skipped = 0, errors = 0;
        
        try {
            // Use PayPropApiClient to handle ALL pagination complexity
            List<Map<String, Object>> allItems = apiClient.fetchAllPages(endpoint, item -> item);
            
            logger.info("📊 Processing {} {}s", allItems.size(), dataType);
            
            for (Map<String, Object> item : allItems) {
                try {
                    SyncItemResult result = processor.apply(item);
                    switch (result) {
                        case CREATED: created++; break;
                        case UPDATED: updated++; break;
                        case SKIPPED: skipped++; break;
                        case ERROR: errors++; break;
                    }
                } catch (Exception e) {
                    errors++;
                    logger.error("❌ Error processing {}: {}", dataType, e.getMessage());
                }
            }
            
            // Comprehensive reporting
            logger.info("📊 {} SYNC COMPLETED:", dataType.toUpperCase());
            logger.info("✅ Created: {}", created);
            logger.info("✅ Updated: {}", updated);
            logger.info("⚠️ Skipped: {}", skipped);
            logger.info("❌ Errors: {}", errors);
            
            Map<String, Object> result = new HashMap<>();
            result.put("total_processed", allItems.size());
            result.put("created", created);
            result.put("updated", updated);
            result.put("skipped", skipped);
            result.put("errors", errors);
            result.put("api_calls", "handled_by_apiClient");
            result.put("improvement", "Using PayPropApiClient for pagination");
            
            return result;
            
        } catch (Exception e) {
            logger.error("❌ {} sync failed: {}", dataType, e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            errorResult.put("status", "FAILED");
            return errorResult;
        }
    }

    /**
     * Sync owner beneficiaries - SIMPLIFIED
     */
    private Map<String, Object> syncOwnerBeneficiaries() throws Exception {
        return syncPaginatedData(
            "/export/beneficiaries?owners=true",
            this::processBeneficiaryWrapper,
            "beneficiary"
        );
    }
    
    /**
     * Wrapper to convert boolean result to enum
     */
    private SyncItemResult processBeneficiaryWrapper(Map<String, Object> beneficiaryData) {
        try {
            boolean isNew = processBeneficiaryInNewTransaction(beneficiaryData);
            return isNew ? SyncItemResult.CREATED : SyncItemResult.UPDATED;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("missing PayProp ID")) {
                return SyncItemResult.SKIPPED;
            }
            return SyncItemResult.ERROR;
        }
    }

    /**
     * Process beneficiary data in a new transaction - UNCHANGED
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processBeneficiaryInNewTransaction(Map<String, Object> beneficiaryData) {
        try {
            String payPropId = (String) beneficiaryData.get("id");
            
            // VALIDATION: Skip if missing essential data
            if (payPropId == null || payPropId.trim().isEmpty()) {
                logger.warn("⚠️ Skipping beneficiary with missing PayProp ID");
                return false;
            }
            
            // Find or create beneficiary
            Beneficiary beneficiary = beneficiaryRepository.findByPayPropBeneficiaryId(payPropId);
            boolean isNew = beneficiary == null;
            
            if (isNew) {
                beneficiary = new Beneficiary();
                beneficiary.setPayPropBeneficiaryId(payPropId);
                beneficiary.setCreatedAt(LocalDateTime.now());
            }
            
            // Handle account_type with safe defaults
            String accountTypeStr = (String) beneficiaryData.get("account_type");
            if (accountTypeStr == null || "undefined".equals(accountTypeStr) || accountTypeStr.trim().isEmpty()) {
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
            
            // Handle payment_method with safe defaults
            String paymentMethodStr = (String) beneficiaryData.get("payment_method");
            if (paymentMethodStr == null || "undefined".equals(paymentMethodStr) || paymentMethodStr.trim().isEmpty()) {
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
            
            // Set BeneficiaryType
            beneficiary.setBeneficiaryType(BeneficiaryType.BENEFICIARY);
            
            // Set name field safely
            String firstName = (String) beneficiaryData.get("first_name");
            String lastName = (String) beneficiaryData.get("last_name");
            String businessName = (String) beneficiaryData.get("business_name");
            
            // Clean up empty strings
            if (firstName != null && firstName.trim().isEmpty()) firstName = null;
            if (lastName != null && lastName.trim().isEmpty()) lastName = null;
            if (businessName != null && businessName.trim().isEmpty()) businessName = null;
            
            beneficiary.setFirstName(firstName);
            beneficiary.setLastName(lastName);
            beneficiary.setBusinessName(businessName);
            
            // Calculate name
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
            
            if (calculatedName == null || calculatedName.trim().isEmpty()) {
                calculatedName = "Beneficiary " + payPropId;
                logger.warn("⚠️ No name available for beneficiary {}, using fallback: '{}'", 
                        payPropId, calculatedName);
            }
            
            beneficiary.setName(calculatedName.trim());
            
            // Set other fields
            String email = (String) beneficiaryData.get("email_address");
            if (email != null && !email.trim().isEmpty()) {
                beneficiary.setEmail(email.trim());
            }
            
            beneficiary.setMobileNumber((String) beneficiaryData.get("mobile"));
            beneficiary.setPhone((String) beneficiaryData.get("phone"));
            beneficiary.setIsActiveOwner((Boolean) beneficiaryData.getOrDefault("is_active_owner", false));
            beneficiary.setVatNumber((String) beneficiaryData.get("vat_number"));
            beneficiary.setCustomerReference((String) beneficiaryData.get("customer_reference"));
            
            // Properties this beneficiary owns
            List<Map<String, Object>> properties = (List<Map<String, Object>>) beneficiaryData.get("properties");
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
                    logger.info("✅ Created beneficiary: {} ({})", calculatedName, payPropId);
                } else {
                    logger.debug("✅ Updated beneficiary: {} ({})", calculatedName, payPropId);
                }
                return isNew;
            } catch (Exception saveException) {
                logger.error("❌ Failed to save beneficiary {}: {}", payPropId, saveException.getMessage());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("❌ Error processing beneficiary data: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sync properties with commission - SIMPLIFIED
     */
    private Map<String, Object> syncPropertiesWithCommission() throws Exception {
        return syncPaginatedData(
            "/export/properties?include_commission=true",
            this::processPropertyCommissionWrapper,
            "property"
        );
    }
    
    /**
     * Wrapper for property commission processing
     */
    private SyncItemResult processPropertyCommissionWrapper(Map<String, Object> propertyData) {
        try {
            boolean processed = processPropertyCommissionDataInNewTransaction(propertyData);
            if (!processed) {
                return SyncItemResult.SKIPPED;
            }
            
            // Check if commission was found
            Map<String, Object> commission = (Map<String, Object>) propertyData.get("commission");
            if (commission != null && commission.get("percentage") != null) {
                return SyncItemResult.UPDATED; // Properties are pre-existing, just updating commission
            }
            return SyncItemResult.UPDATED;
        } catch (Exception e) {
            return SyncItemResult.ERROR;
        }
    }

    /**
     * Process property commission data - UNCHANGED
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private boolean processPropertyCommissionDataInNewTransaction(Map<String, Object> ppProperty) {
        try {
            String payPropId = (String) ppProperty.get("id");
            String propertyName = (String) ppProperty.get("property_name");
            
            logger.info("🔍 Processing property: ID='{}', Name='{}'", payPropId, propertyName);
            
            if (payPropId == null || payPropId.trim().isEmpty()) {
                logger.warn("⚠️ COMMISSION: Skipping property with missing PayProp ID");
                return false;
            }
            
            Optional<Property> existingOpt = propertyService.findByPayPropId(payPropId);
            if (!existingOpt.isPresent()) {
                logger.warn("⚠️ COMMISSION: Property not found for PayProp ID: {}", payPropId);
                return false;
            }
            
            Property property = existingOpt.get();
            
            // Update property name if valid
            if (propertyName != null && !propertyName.trim().isEmpty()) {
                if (propertyName.matches("^.*\\S.*$")) {
                    property.setPropertyName(propertyName.trim());
                    logger.debug("✅ Updated property name: {}", propertyName.trim());
                } else {
                    logger.warn("⚠️ Invalid property name pattern from PayProp: '{}', keeping existing: '{}'", 
                        propertyName, property.getPropertyName());
                }
            } else {
                logger.warn("⚠️ Blank property name from PayProp for ID: {}, keeping existing: '{}'", 
                    payPropId, property.getPropertyName());
            }
            
            // Handle commission
            Map<String, Object> commission = (Map<String, Object>) ppProperty.get("commission");
            if (commission != null) {
                Object percentage = commission.get("percentage");
                if (percentage instanceof String && !((String) percentage).trim().isEmpty()) {
                    try {
                        BigDecimal commissionRate = new BigDecimal(((String) percentage).trim());
                        if (commissionRate.compareTo(BigDecimal.ZERO) >= 0 && 
                            commissionRate.compareTo(BigDecimal.valueOf(100)) <= 0) {
                            property.setCommissionPercentage(commissionRate);
                            logger.debug("✅ Set commission rate: {}%", commissionRate);
                        } else {
                            logger.warn("⚠️ Commission rate out of range (0-100): {}%, skipping", commissionRate);
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("⚠️ Invalid commission percentage format: '{}', skipping", percentage);
                    }
                }
            }
            
            // Update additional property data
            updatePropertyFromCommissionSync(property, ppProperty);
            
            try {
                propertyService.save(property);
                logger.debug("✅ Successfully saved property: {}", property.getPropertyName());
                return true;
            } catch (jakarta.validation.ConstraintViolationException e) {
                logger.error("❌ VALIDATION FAILED for property {} ({}): {}", 
                    property.getPayPropId(), 
                    property.getPropertyName(),
                    e.getConstraintViolations().stream()
                        .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage() + " (value: '" + cv.getInvalidValue() + "')")
                        .collect(Collectors.joining("; ")));
                return false;
            }
            
        } catch (Exception e) {
            logger.error("❌ Error processing property commission data: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Update property with additional data - UNCHANGED
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
        
        // Address data
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
        
    /**
     * Sync payment categories - UNCHANGED (not paginated)
     */
    private Map<String, Object> syncPaymentCategories() throws Exception {
        logger.info("🏷️ Syncing payment categories...");
        
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
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
     * Sync financial transactions (ICDN) - SPECIAL CASE: Uses date chunking
     */
    private Map<String, Object> syncFinancialTransactions() throws Exception {
        logger.info("💰 Starting ICDN transactions sync with date chunking...");
        
        // This one is different - it uses date chunks instead of simple pagination
        // So we keep the original implementation but extract the common parts
        
        int successfulSaves = 0, skippedDuplicates = 0, skippedNegative = 0;
        int skippedInvalidType = 0, skippedMissingData = 0, otherErrors = 0;
        
        LocalDate endDate = LocalDate.now().plusDays(7);
        LocalDate absoluteStartDate = endDate.minusYears(2);
        
        logger.info("🔍 Processing 14-day chunks from {} to {}", absoluteStartDate, endDate);
        
        int totalChunks = 0;
        
        while (endDate.isAfter(absoluteStartDate)) {
            LocalDate startDate = endDate.minusDays(14);
            if (startDate.isBefore(absoluteStartDate)) {
                startDate = absoluteStartDate;
            }
            
            totalChunks++;
            logger.info("🔍 ICDN CHUNK {}: Processing {} to {}", totalChunks, startDate, endDate);
            
            // Use apiClient for pagination within each date chunk
            String endpoint = "/report/icdn?from_date=" + startDate + "&to_date=" + endDate;
            List<Map<String, Object>> transactions = apiClient.fetchAllPages(endpoint, item -> item);
            
            logger.info("📊 Processing {} ICDN transactions for chunk {}", transactions.size(), totalChunks);
            
            for (Map<String, Object> ppTransaction : transactions) {
                try {
                    FinancialTransaction transaction = createICDNFinancialTransactionSafe(ppTransaction);
                    if (transaction != null) {
                        boolean saved = saveFinancialTransactionIsolated(transaction);
                        if (saved) {
                            successfulSaves++;
                        } else {
                            // Categorize the failure
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
                } catch (Exception e) {
                    otherErrors++;
                    logger.error("❌ Error processing ICDN transaction: {}", e.getMessage());
                }
            }
            
            endDate = startDate.minusDays(1);
        }
        
        // Comprehensive reporting
        logger.info("💰 ICDN TRANSACTIONS SYNC COMPLETED:");
        logger.info("📊 Chunks processed: {}", totalChunks);
        logger.info("✅ Successful saves: {}", successfulSaves);
        logger.info("⏭️ Skipped duplicates: {}", skippedDuplicates);
        logger.info("⚠️ Skipped negative amounts: {}", skippedNegative);
        logger.info("⚠️ Skipped invalid types: {}", skippedInvalidType);
        logger.info("⚠️ Skipped missing data: {}", skippedMissingData);
        logger.info("❌ Other errors: {}", otherErrors);
        
        Map<String, Object> result = new HashMap<>();
        result.put("payments_created", successfulSaves);
        result.put("skipped_duplicates", skippedDuplicates);
        result.put("skipped_negative", skippedNegative);
        result.put("skipped_invalid_type", skippedInvalidType);
        result.put("skipped_missing_data", skippedMissingData);
        result.put("other_errors", otherErrors);
        result.put("total_processed", successfulSaves + skippedDuplicates + skippedNegative + skippedInvalidType + skippedMissingData + otherErrors);
        result.put("chunks_processed", totalChunks);
        return result;
    }

    /**
     * Create ICDN transaction safely - UNCHANGED
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
            
            // Optional fields
            transaction.setDescription((String) ppTransaction.get("description"));
            transaction.setHasTax((Boolean) ppTransaction.getOrDefault("has_tax", false));
            transaction.setDepositId((String) ppTransaction.get("deposit_id"));
            
            // Matched amount
            Object matchedAmount = ppTransaction.get("matched_amount");
            if (matchedAmount instanceof String && !((String) matchedAmount).isEmpty()) {
                try {
                    transaction.setMatchedAmount(new BigDecimal((String) matchedAmount));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
            
            // Tax amount
            Object taxAmount = ppTransaction.get("tax_amount");
            if (taxAmount instanceof String && !((String) taxAmount).isEmpty()) {
                try {
                    transaction.setTaxAmount(new BigDecimal((String) taxAmount));
                } catch (NumberFormatException e) {
                    transaction.setTaxAmount(BigDecimal.ZERO);
                }
            }
            
            // Property info
            Map<String, Object> property = (Map<String, Object>) ppTransaction.get("property");
            if (property != null) {
                transaction.setPropertyId((String) property.get("id"));
                transaction.setPropertyName((String) property.get("name"));
            }
            
            // Tenant info
            Map<String, Object> tenant = (Map<String, Object>) ppTransaction.get("tenant");
            if (tenant != null) {
                transaction.setTenantId((String) tenant.get("id"));
                transaction.setTenantName((String) tenant.get("name"));
            }
            
            // Category info
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
     * Sync batch payments - SPECIAL CASE: Uses date chunking + pagination
     */
    private Map<String, Object> syncBatchPayments() throws Exception {
        logger.info("💰 Starting batch payments sync with date chunking...");
        
        int successfulSaves = 0, skippedDuplicates = 0, skippedNegative = 0;
        int skippedInvalidType = 0, skippedMissingData = 0, otherErrors = 0;
        
        LocalDate endDate = LocalDate.now().plusDays(7);  
        LocalDate absoluteStartDate = endDate.minusYears(2); 
        
        logger.info("🔍 Processing 14-day chunks from {} to {}", absoluteStartDate, endDate);
        
        int totalChunks = 0;
        
        while (endDate.isAfter(absoluteStartDate)) {
            LocalDate startDate = endDate.minusDays(14);
            if (startDate.isBefore(absoluteStartDate)) {
                startDate = absoluteStartDate;
            }
            
            totalChunks++;
            logger.info("🔍 BATCH CHUNK {}: Processing {} to {}", totalChunks, startDate, endDate);
            
            // Use apiClient for pagination within each date chunk
            String endpoint = "/report/all-payments" +
                "?from_date=" + startDate +
                "&to_date=" + endDate +
                "&filter_by=reconciliation_date" +
                "&include_beneficiary_info=true";
                
            List<Map<String, Object>> payments = apiClient.fetchAllPages(endpoint, item -> item);
            
            logger.info("📊 Processing {} batch payments for chunk {}", payments.size(), totalChunks);
            
            for (Map<String, Object> paymentData : payments) {
                try {
                    FinancialTransaction transaction = createFinancialTransactionFromReportData(paymentData);
                    if (transaction != null) {
                        boolean saved = saveFinancialTransactionIsolated(transaction);
                        if (saved) {
                            successfulSaves++;
                        } else {
                            // Categorize the failure
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
                } catch (Exception e) {
                    otherErrors++;
                    logger.error("❌ Error processing payment: {}", e.getMessage());
                }
            }
            
            endDate = startDate.minusDays(1);
        }
        
        // Comprehensive reporting
        logger.info("💰 BATCH PAYMENTS SYNC COMPLETED:");
        logger.info("📊 Chunks processed: {}", totalChunks);
        logger.info("✅ Successful saves: {}", successfulSaves);
        logger.info("⏭️ Skipped duplicates: {}", skippedDuplicates);
        logger.info("⚠️ Skipped negative amounts: {}", skippedNegative);
        logger.info("⚠️ Skipped invalid types: {}", skippedInvalidType);
        logger.info("⚠️ Skipped missing data: {}", skippedMissingData);
        logger.info("❌ Other errors: {}", otherErrors);
        
        Map<String, Object> result = new HashMap<>();
        result.put("payments_created", successfulSaves);
        result.put("skipped_duplicates", skippedDuplicates);
        result.put("skipped_negative", skippedNegative);
        result.put("skipped_invalid_type", skippedInvalidType);
        result.put("skipped_missing_data", skippedMissingData);
        result.put("other_errors", otherErrors);
        result.put("total_processed", successfulSaves + skippedDuplicates + skippedNegative + skippedInvalidType + skippedMissingData + otherErrors);
        result.put("chunks_processed", totalChunks);
        return result;
    }

    /**
     * Save financial transaction in isolation - UNCHANGED
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveFinancialTransactionIsolated(FinancialTransaction transaction) {
        try {
            // Validate required fields
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
            
            // Log negative amounts but store them
            if (transaction.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                logger.info("💰 STORING: Negative amount £{} for transaction {} - {} ({})", 
                    transaction.getAmount(), 
                    transaction.getPayPropTransactionId(),
                    transaction.getTransactionType(),
                    transaction.getCategoryName());
            }
            
            // Check for duplicate
            if (financialTransactionRepository.existsByPayPropTransactionId(transaction.getPayPropTransactionId())) {
                logger.debug("ℹ️ SKIPPED: Transaction {} already exists", transaction.getPayPropTransactionId());
                return false;
            }
            
            // Validate transaction type
            if (transaction.getTransactionType() == null || !isValidTransactionType(transaction.getTransactionType())) {
                logger.warn("⚠️ SKIPPED: Invalid transaction type '{}' for transaction {}", 
                    transaction.getTransactionType(), transaction.getPayPropTransactionId());
                return false;
            }
            
            // Set audit fields
            if (transaction.getCreatedAt() == null) {
                transaction.setCreatedAt(LocalDateTime.now());
            }
            if (transaction.getUpdatedAt() == null) {
                transaction.setUpdatedAt(LocalDateTime.now());
            }
            
            // Save
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

    /**
     * Check if transaction type is valid - UNCHANGED
     */
    private boolean isValidTransactionType(String transactionType) {
        Set<String> validTypes = Set.of(
            "invoice", "credit_note", "debit_note", "deposit", "commission_payment",
            "payment_to_beneficiary", "payment_to_agency", "payment_to_contractor", 
            "payment_property_account", "payment_deposit_account", "refund", 
            "adjustment", "transfer"
        );
        return validTypes.contains(transactionType);
    }

    /**
     * Create financial transaction from report data - UNCHANGED (keeping all specific logic)
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
            
            // Amount handling
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
            
            // Date handling
            LocalDate transactionDate = extractTransactionDate(paymentData);
            if (transactionDate == null) {
                logger.warn("⚠️ Could not determine transaction date for payment {}, skipping", paymentId);
                return null;
            }
            transaction.setTransactionDate(transactionDate);
            
            // Extract reconciliation date
            String reconDateStr = extractStringFromPath(paymentData, "incoming_transaction.reconciliation_date");
            if (reconDateStr != null && !reconDateStr.isEmpty()) {
                try {
                    transaction.setReconciliationDate(LocalDate.parse(reconDateStr));
                } catch (Exception e) {
                    logger.debug("Could not parse reconciliation_date: {}", reconDateStr);
                }
            }
            
            // Property information
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
            
            // Beneficiary information
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
            
            // Set transaction type
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
     * Determine transaction type - UNCHANGED (complex business logic)
     */
    private String determineTransactionTypeFromPayPropData(Map<String, Object> paymentData) {
        try {
            // Get PayProp data
            Map<String, Object> beneficiary = (Map<String, Object>) paymentData.get("beneficiary");
            String beneficiaryType = beneficiary != null ? (String) beneficiary.get("type") : null;
            
            String category = extractCategorySafely(paymentData);
            
            // Check for deposit-related transactions
            if (isDepositRelated(paymentData)) {
                logger.debug("Mapped to 'deposit' - deposit-related transaction");
                return "deposit";
            }
            
            // Map by beneficiary type
            if (beneficiaryType != null) {
                switch (beneficiaryType) {
                    case "agency":
                        logger.debug("Mapped to 'payment_to_agency' - agency beneficiary");
                        return "payment_to_agency";
                        
                    case "beneficiary":
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
            
            // Category-based mapping
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
            
            // Default
            logger.debug("Using default 'payment_to_beneficiary' for standard payment");
            return "payment_to_beneficiary";
            
        } catch (Exception e) {
            logger.error("Error determining transaction type: {}", e.getMessage());
            return "commission_payment";
        }
    }

    /**
     * Extract category safely - UNCHANGED
     */
    private String extractCategorySafely(Map<String, Object> paymentData) {
        try {
            Object categoryObj = paymentData.get("category");
            
            if (categoryObj == null) {
                return null;
            }
            
            if (categoryObj instanceof String) {
                return (String) categoryObj;
            }
            
            if (categoryObj instanceof Map) {
                Map<String, Object> categoryMap = (Map<String, Object>) categoryObj;
                
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
                
                logger.debug("Category Map structure: {}", categoryMap.keySet());
                return categoryMap.toString();
            }
            
            logger.debug("Category is {} type, converting to string", categoryObj.getClass().getSimpleName());
            return categoryObj.toString();
            
        } catch (Exception e) {
            logger.error("Error extracting category: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if deposit related - UNCHANGED
     */
    private boolean isDepositRelated(Map<String, Object> paymentData) {
        try {
            // Check category
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
            
            // Check description
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
            
            // Check deposit_id with context
            Map<String, Object> incomingTransaction = (Map<String, Object>) paymentData.get("incoming_transaction");
            if (incomingTransaction != null && (category != null || description != null)) {
                String depositId = (String) incomingTransaction.get("deposit_id");
                if (depositId != null && !depositId.trim().isEmpty()) {
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
     * Check if maintenance payment - UNCHANGED
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
     * Extract transaction date - UNCHANGED
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
        
        // Try due_date
        String dueDate = (String) paymentData.get("due_date");
        if (dueDate != null && !dueDate.isEmpty()) {
            try {
                return LocalDate.parse(dueDate);
            } catch (Exception e) {
                logger.debug("Could not parse due_date: {}", dueDate);
            }
        }
        
        // Try payment_batch.transfer_date
        String transferDate = extractStringFromPath(paymentData, "payment_batch.transfer_date");
        if (transferDate != null && !transferDate.isEmpty()) {
            try {
                return LocalDate.parse(transferDate);
            } catch (Exception e) {
                logger.debug("Could not parse transfer_date: {}", transferDate);
            }
        }
        
        return null;
    }

    /**
     * Extract string from path - UNCHANGED
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
     * Get commission rates from database - SIMPLIFIED
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
     * Calculate and store commissions - UNCHANGED (business logic)
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
            // Skip deposits
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
     * Sync actual commission payments - UNCHANGED
     */
    private Map<String, Object> syncActualCommissionPayments() throws Exception {
        logger.info("💰 Getting commission rates from PayProp and calculating commission...");
        
        Map<String, BigDecimal> commissionRates = getCommissionRatesFromDatabase();
        logger.info("📊 Found {} properties with commission rates", commissionRates.size());
        
        int created = 0;
        BigDecimal totalCommission = BigDecimal.ZERO;
        
        try {
            List<FinancialTransaction> rentPayments = financialTransactionRepository
                .findByDataSource("ICDN_ACTUAL")
                .stream()
                .filter(tx -> "invoice".equals(tx.getTransactionType()))
                .filter(tx -> {
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
     * Link actual commission payments to rent transactions - UNCHANGED
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
     * Get stored financial summary - UNCHANGED (reporting method)
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
     * Get commission variance analysis - UNCHANGED (reporting method)
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
     * Get commission payment summary - UNCHANGED (reporting method)
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

    // ===== DUAL DATA SYNC METHODS (Instructions vs Actuals) =====

    /**
     * Sync dual financial data - UNCHANGED (orchestration method)
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
            
            // 3. Sync ICDN transactions (detailed financial records)
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

    /**
     * Sync payment instructions - SIMPLIFIED using apiClient
     */
    private Map<String, Object> syncPaymentInstructions() throws Exception {
        logger.info("📋 Syncing payment instructions (what SHOULD be paid)...");
        
        // Use apiClient for pagination
        List<Map<String, Object>> instructions = apiClient.fetchAllPages(
            "/export/payments?include_beneficiary_info=true", 
            item -> item
        );
        
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

    /**
     * Sync actual reconciled payments - SIMPLIFIED using apiClient
     */
    private Map<String, Object> syncActualReconciledPayments() throws Exception {
        logger.info("💳 Syncing actual reconciled payments (what WAS paid)...");
        
        // Use apiClient for pagination
        List<Map<String, Object>> actualPayments = apiClient.fetchAllPages(
            "/export/payments?filter_by=reconciliation_date&include_beneficiary_info=true",
            item -> item
        );
        
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

    /**
     * Create transaction from payment instruction - UNCHANGED
     */
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
                transaction.setTransactionDate(LocalDate.parse(fromDate));
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

    /**
     * Create transaction from actual payment - UNCHANGED
     */
    private FinancialTransaction createFromActualPayment(Map<String, Object> payment) {
        try {
            FinancialTransaction transaction = new FinancialTransaction();
            transaction.setPayPropTransactionId((String) payment.get("id"));
            transaction.setDataSource("ACTUAL_PAYMENT");
            transaction.setIsInstruction(false);
            transaction.setIsActualTransaction(true);
            
            // Extract reconciliation date
            String reconDate = (String) payment.get("reconciliation_date");
            if (reconDate != null) {
                transaction.setReconciliationDate(LocalDate.parse(reconDate));
                transaction.setTransactionDate(LocalDate.parse(reconDate));
            }
            
            // Extract actual amounts paid
            Object amount = payment.get("amount");
            if (amount != null) {
                transaction.setAmount(new BigDecimal(amount.toString()));
            }
            
            // Get actual commission amount
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

    /**
     * Calculate expected commission - UNCHANGED
     */
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

    /**
     * Extract actual commission amount - UNCHANGED
     */
    private void extractActualCommissionAmount(FinancialTransaction transaction, Map<String, Object> payment) {
        // For now, we'll set this to zero and update it when we link to actual commission payments
        transaction.setActualCommissionAmount(BigDecimal.ZERO);
    }

    /**
     * Set common fields - UNCHANGED
     */
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
        
        // Set transaction type based on category
        if ("Rent".equalsIgnoreCase(category)) {
            transaction.setTransactionType("invoice");
        } else if (category != null && category.toLowerCase().contains("deposit")) {
            transaction.setTransactionType("deposit");
            transaction.setDepositId((String) data.get("id"));
        } else {
            transaction.setTransactionType("commission_payment");
        }
    }

    /**
     * Link instructions to actuals - UNCHANGED
     */
    private Map<String, Object> linkInstructionsToActuals() {
        logger.info("🔗 Linking payment instructions to actual payments...");
        
        // This would implement logic to match instructions with their corresponding actual payments
        // Based on property, amount, and approximate dates
        
        int linkedPairs = 0;
        // Implementation would go here to match records and set instructionId field
        
        return Map.of("linked_pairs", linkedPairs);
    }

    /**
     * Get dashboard financial comparison - UNCHANGED (reporting method)
     */
    public Map<String, Object> getDashboardFinancialComparison(String propertyId, LocalDate fromDate, LocalDate toDate) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get instructed amounts
            List<FinancialTransaction> instructions = financialTransactionRepository
                .findByPropertyIdAndDataSource(propertyId, "PAYMENT_INSTRUCTION")
                .stream()
                .filter(t -> t.getTransactionDate() != null)
                .filter(t -> !t.getTransactionDate().isBefore(fromDate) && !t.getTransactionDate().isAfter(toDate))
                .collect(Collectors.toList());
            
            // Get actual amounts
            List<FinancialTransaction> actuals = financialTransactionRepository
                .findByPropertyIdAndDataSource(propertyId, "ACTUAL_PAYMENT")
                .stream()
                .filter(t -> t.getTransactionDate() != null)
                .filter(t -> !t.getTransactionDate().isBefore(fromDate) && !t.getTransactionDate().isAfter(toDate))
                .collect(Collectors.toList());
            
            // Get ICDN data
            List<FinancialTransaction> icdnData = financialTransactionRepository
                .findByPropertyIdAndTransactionDateBetween(propertyId, fromDate, toDate)
                .stream()
                .filter(t -> "ICDN_ACTUAL".equals(t.getDataSource()))
                .collect(Collectors.toList());
            
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

    /**
     * Enum for sync item results
     */
    private enum SyncItemResult {
        CREATED,
        UPDATED,
        SKIPPED,
        ERROR
    }
}