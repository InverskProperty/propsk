// PayPropFinancialSyncService.java - Comprehensive financial data sync service
package site.easy.to.build.crm.service.payprop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.property.PropertyService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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
        PropertyService propertyService
    ) {
        this.oAuth2Service = oAuth2Service;
        this.restTemplate = restTemplate;
        this.propertyRepository = propertyRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.paymentCategoryRepository = paymentCategoryRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.propertyService = propertyService;
    }
    
    /**
     * Main method to sync all comprehensive financial data from PayProp
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
            
            // 4. Sync Financial Transactions (ICDN)
            Map<String, Object> transactionsResult = syncFinancialTransactions();
            syncResults.put("transactions", transactionsResult);
            
            // 5. Calculate and store commission data
            Map<String, Object> commissionsResult = calculateAndStoreCommissions();
            syncResults.put("commissions", commissionsResult);
            
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
     * Sync properties with commission data from PayProp
     */
    private Map<String, Object> syncPropertiesWithCommission() throws Exception {
        logger.info("📊 Syncing properties with commission data...");
        
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        String url = payPropApiBase + "/export/properties?include_commission=true&rows=1000";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
        
        List<Map<String, Object>> payPropProperties = (List<Map<String, Object>>) response.getBody().get("items");
        
        int updated = 0, created = 0;
        
        for (Map<String, Object> ppProperty : payPropProperties) {
            String payPropId = (String) ppProperty.get("id");
            String propertyName = (String) ppProperty.get("property_name");
            
            // Find existing property by PayProp ID or create new
            Optional<Property> existingOpt = propertyService.findByPayPropId(payPropId);
            Property property;
            boolean isNew = false;
            
            if (existingOpt.isPresent()) {
                property = existingOpt.get();
            } else {
                property = new Property();
                property.setPayPropId(payPropId);
                isNew = true;
            }
            
            // Update property data
            property.setPayPropPropertyId(payPropId);
            property.setPropertyName(propertyName != null ? propertyName : "Unnamed Property");
            
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
            
            // Commission data
            Map<String, Object> commission = (Map<String, Object>) ppProperty.get("commission");
            if (commission != null) {
                Object percentage = commission.get("percentage");
                if (percentage instanceof String && !((String) percentage).isEmpty()) {
                    try {
                        property.setCommissionPercentage(new BigDecimal((String) percentage));
                    } catch (NumberFormatException e) {
                        property.setCommissionPercentage(BigDecimal.ZERO);
                    }
                }
                
                Object amount = commission.get("amount");
                if (amount instanceof String && !((String) amount).isEmpty()) {
                    try {
                        property.setCommissionAmount(new BigDecimal((String) amount));
                    } catch (NumberFormatException e) {
                        property.setCommissionAmount(BigDecimal.ZERO);
                    }
                }
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
            
            property.setUpdatedAt(LocalDateTime.now());
            if (isNew) {
                property.setCreatedAt(LocalDateTime.now());
            }
            
            propertyService.save(property);
            
            if (isNew) created++; else updated++;
        }
        
        return Map.of(
            "total_processed", payPropProperties.size(),
            "created", created,
            "updated", updated
        );
    }

    // FIXED: PayPropFinancialSyncService.java - Handle missing enum values
    // Only showing the fixed syncOwnerBeneficiaries method - replace this method in your service

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
    
    private Map<String, Object> syncFinancialTransactions() throws Exception {
        logger.info("💰 Syncing financial transactions (ICDN) with multiple date ranges...");
        
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        int created = 0, skipped = 0;
        LocalDate endDate = LocalDate.now();
        LocalDate absoluteStartDate = LocalDate.of(2024, 1, 1); // Go back to 2024
        
        // Sync in 90-day chunks (3 days buffer for API limit)
        while (endDate.isAfter(absoluteStartDate)) {
            LocalDate startDate = endDate.minusDays(90);
            if (startDate.isBefore(absoluteStartDate)) {
                startDate = absoluteStartDate;
            }
            
            logger.info("📅 Syncing ICDN transactions from {} to {}", startDate, endDate);
            
            String url = payPropApiBase + "/report/icdn" +
                "?from_date=" + startDate +
                "&to_date=" + endDate +
                "&rows=1000";
                
            try {
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                List<Map<String, Object>> transactions = (List<Map<String, Object>>) response.getBody().get("items");
                
                logger.info("📊 Found {} transactions in period {} to {}", transactions.size(), startDate, endDate);
                
                for (Map<String, Object> ppTransaction : transactions) {
                    try {
                        String payPropId = (String) ppTransaction.get("id");
                        
                        // Skip if missing essential data
                        if (payPropId == null || payPropId.trim().isEmpty()) {
                            logger.warn("⚠️ Skipping transaction with missing PayProp ID");
                            skipped++;
                            continue;
                        }
                        
                        // Check if transaction already exists
                        if (financialTransactionRepository.existsByPayPropTransactionId(payPropId)) {
                            skipped++;
                            continue;
                        }
                        
                        // Create new transaction
                        FinancialTransaction transaction = new FinancialTransaction();
                        transaction.setPayPropTransactionId(payPropId);
                        
                        // Handle amount (REQUIRED FIELD)
                        Object amount = ppTransaction.get("amount");
                        if (amount != null) {
                            try {
                                BigDecimal amountValue = new BigDecimal(amount.toString());
                                if (amountValue.compareTo(BigDecimal.ZERO) >= 0) {
                                    transaction.setAmount(amountValue);
                                } else {
                                    logger.warn("⚠️ Negative amount {} for transaction {}, skipping", amount, payPropId);
                                    skipped++;
                                    continue;
                                }
                            } catch (NumberFormatException e) {
                                logger.warn("⚠️ Invalid amount format '{}' for transaction {}, skipping", amount, payPropId);
                                skipped++;
                                continue;
                            }
                        } else {
                            logger.warn("⚠️ Missing amount for transaction {}, skipping", payPropId);
                            skipped++;
                            continue;
                        }
                        
                        // Handle matched amount (optional)
                        Object matchedAmount = ppTransaction.get("matched_amount");
                        if (matchedAmount instanceof String && !((String) matchedAmount).isEmpty()) {
                            try {
                                transaction.setMatchedAmount(new BigDecimal((String) matchedAmount));
                            } catch (NumberFormatException e) {
                                logger.warn("⚠️ Invalid matched_amount format '{}' for transaction {}", matchedAmount, payPropId);
                            }
                        }
                        
                        // Handle date (REQUIRED FIELD)
                        String dateStr = (String) ppTransaction.get("date");
                        if (dateStr != null && !dateStr.trim().isEmpty()) {
                            try {
                                transaction.setTransactionDate(LocalDate.parse(dateStr));
                            } catch (Exception e) {
                                logger.warn("⚠️ Invalid date format '{}' for transaction {}, skipping", dateStr, payPropId);
                                skipped++;
                                continue;
                            }
                        } else {
                            logger.warn("⚠️ Missing transaction date for transaction {}, skipping", payPropId);
                            skipped++;
                            continue;
                        }
                        
                        // Set transaction type
                        String transactionType = (String) ppTransaction.get("type");
                        if (transactionType != null && 
                            Arrays.asList("invoice", "credit_note", "debit_note").contains(transactionType)) {
                            transaction.setTransactionType(transactionType);
                        } else {
                            logger.warn("⚠️ Invalid transaction type '{}' for transaction {}, skipping", transactionType, payPropId);
                            skipped++;
                            continue;
                        }
                        
                        // Set optional fields
                        transaction.setDescription((String) ppTransaction.get("description"));
                        transaction.setHasTax((Boolean) ppTransaction.getOrDefault("has_tax", false));
                        transaction.setDepositId((String) ppTransaction.get("deposit_id"));
                        
                        // Tax amount (optional)
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
                        
                        transaction.setCreatedAt(LocalDateTime.now());
                        transaction.setUpdatedAt(LocalDateTime.now());
                        
                        financialTransactionRepository.save(transaction);
                        created++;
                        
                        logger.debug("✅ Created financial transaction: {} (£{})", payPropId, transaction.getAmount());
                        
                    } catch (Exception e) {
                        logger.error("❌ Error processing transaction: {}", e.getMessage(), e);
                        skipped++;
                    }
                }
                
            } catch (Exception e) {
                logger.error("❌ Failed to sync period {} to {}: {}", startDate, endDate, e.getMessage());
            }
            
            // Move to next period
            endDate = startDate.minusDays(1);
        }
        
        logger.info("💰 Financial transactions sync completed: {} created, {} skipped", created, skipped);
        
        return Map.of(
            "total_processed", created + skipped,
            "created", created,
            "skipped_existing", skipped,
            "date_range", absoluteStartDate + " to " + LocalDate.now()
        );
    }
    
    /**
     * Calculate and store commission data for financial transactions
     */
    private Map<String, Object> calculateAndStoreCommissions() throws Exception {
        logger.info("🧮 Calculating and storing commission data...");
        
        // Get all recent transactions without commission calculations
        List<FinancialTransaction> transactions = financialTransactionRepository
            .findByCommissionAmountIsNull();
        
        int commissionsCalculated = 0;
        
        for (FinancialTransaction transaction : transactions) {
            if (!"invoice".equals(transaction.getTransactionType())) {
                continue; // Only calculate commissions for invoices (rent payments)
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
                        transaction.setCommissionAmount(commissionAmount);
                        transaction.setServiceFeeAmount(serviceFee);
                        transaction.setNetToOwnerAmount(netToOwner);
                        transaction.setCommissionRate(commissionRate);
                        transaction.setUpdatedAt(LocalDateTime.now());
                        
                        financialTransactionRepository.save(transaction);
                        commissionsCalculated++;
                    }
                }
            }
        }
        
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
                summary.put("property_name", transaction.getPropertyName());
                summary.put("tenant_name", transaction.getTenantName());
                summary.put("transaction_date", transaction.getTransactionDate());
                summary.put("rent_amount", transaction.getAmount());
                summary.put("commission_amount", transaction.getCommissionAmount());
                summary.put("service_fee", transaction.getServiceFeeAmount());
                summary.put("net_to_owner", transaction.getNetToOwnerAmount());
                summary.put("commission_rate", transaction.getCommissionRate());
                
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
}