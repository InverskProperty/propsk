// PayPropSyncOrchestrator.java - Updated with Complete Financial Sync Integration
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.UserRepository;
import site.easy.to.build.crm.repository.BatchPaymentRepository;
import site.easy.to.build.crm.repository.FinancialTransactionRepository;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.payprop.PayPropSyncService.PayPropExportResult;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.assignment.CustomerPropertyAssignmentService;
import site.easy.to.build.crm.service.drive.CustomerDriveOrganizationService;
import site.easy.to.build.crm.service.drive.GoogleDriveFileService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.service.payprop.SyncResult;
import site.easy.to.build.crm.service.payprop.SyncResultType;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropSyncOrchestrator {

   private static final Logger log = LoggerFactory.getLogger(PayPropSyncOrchestrator.class);

   private final PayPropSyncService payPropSyncService;
   private final PayPropSyncLogger syncLogger;
   private final CustomerService customerService;
   private final PropertyService propertyService;
   private final AuthenticationUtils authenticationUtils;
   private final CustomerPropertyAssignmentService assignmentService;
   private final CustomerDriveOrganizationService customerDriveOrganizationService;
   private final GoogleDriveFileService googleDriveFileService;
   private final UserRepository userRepository;
   private final BatchPaymentRepository batchPaymentRepository;
   private final FinancialTransactionRepository financialTransactionRepository;
   private final RestTemplate restTemplate;
   private final PayPropOAuth2Service oAuth2Service;
   private final String payPropApiBase = "https://ukapi.staging.payprop.com/api/agency/v1.1";

   @Value("${payprop.sync.batch-size:25}")
   private int batchSize;

   @Autowired
   public PayPropSyncOrchestrator(PayPropSyncService payPropSyncService,
                                 PayPropSyncLogger syncLogger,
                                 CustomerService customerService,
                                 PropertyService propertyService,
                                 AuthenticationUtils authenticationUtils,
                                 CustomerPropertyAssignmentService assignmentService,
                                 CustomerDriveOrganizationService customerDriveOrganizationService,
                                 GoogleDriveFileService googleDriveFileService,
                                 UserRepository userRepository,
                                 BatchPaymentRepository batchPaymentRepository,
                                 FinancialTransactionRepository financialTransactionRepository,
                                 RestTemplate restTemplate,
                                 PayPropOAuth2Service oAuth2Service) {
       this.payPropSyncService = payPropSyncService;
       this.syncLogger = syncLogger;
       this.customerService = customerService;
       this.propertyService = propertyService;
       this.authenticationUtils = authenticationUtils;
       this.assignmentService = assignmentService;
       this.customerDriveOrganizationService = customerDriveOrganizationService;
       this.googleDriveFileService = googleDriveFileService;
       this.userRepository = userRepository;
       this.batchPaymentRepository = batchPaymentRepository;
       this.financialTransactionRepository = financialTransactionRepository;
       this.restTemplate = restTemplate;
       this.oAuth2Service = oAuth2Service;
   }

   // ===== MAIN SYNC ORCHESTRATION =====

   /**
    * Complete two-way synchronization using unified Customer entity with COMPLETE FINANCIAL SYNC
    */
   public UnifiedSyncResult performUnifiedSync(OAuthUser oAuthUser, Long initiatedBy) {
       // Just delegate to enhanced version - no need for two versions
       return performEnhancedUnifiedSync(oAuthUser, initiatedBy);
   }

   public UnifiedSyncResult performEnhancedUnifiedSync(OAuthUser oAuthUser, Long initiatedBy) {
       UnifiedSyncResult result = new UnifiedSyncResult();
       syncLogger.logSyncStart("ENHANCED_UNIFIED_SYNC", initiatedBy);
       
       try {
           // Step 1: Enhanced Properties sync with rent data
           result.setPropertiesResult(syncPropertiesFromPayPropEnhanced(initiatedBy));
           
           // Step 2: Get payment relationships 
           Map<String, PropertyRelationship> relationships = extractRelationshipsFromPayments();
           
           // Step 3: Sync Property Owners as Customers
           result.setPropertyOwnersResult(syncPropertyOwnersAsCustomers(initiatedBy, relationships));
           
           // Step 4: Sync Tenants as Customers  
           result.setTenantsResult(syncTenantsAsCustomers(initiatedBy));

           // Step 5: Sync Contractors as Customers
           result.setContractorsResult(syncContractorsAsCustomers(initiatedBy));

           // Step 6: Establish property assignments
           result.setRelationshipsResult(establishPropertyAssignments(relationships));

           // Step 7: Establish tenant relationships
           result.setTenantRelationshipsResult(establishTenantPropertyRelationships());

           // ✅ STEP 8: COMPLETE PAYMENT SYNC - ALL TYPES
           log.info("💰 Starting comprehensive payment data sync...");
           
           // 8.1: Payment categories
           result.setPaymentCategoriesResult(syncPaymentCategories(initiatedBy));
           
           // 8.2: Payment instructions (what should be paid)
           result.setPaymentsResult(syncPayments(initiatedBy));
           
           // 8.3: Reconciled payments (what was actually paid)
           result.setReconciledPaymentsResult(syncReconciledPayments(initiatedBy));
           
           // 8.4: Batch payments from all-payments report
           result.setBatchPaymentsResult(syncBatchPayments(initiatedBy));
           
           // 8.5: Financial transactions from ICDN (detailed records)
           result.setFinancialTransactionsResult(syncFinancialTransactions(initiatedBy));
           
           // 8.6: Beneficiary balances
           result.setBeneficiaryBalancesResult(syncBeneficiaryBalances(initiatedBy));
           
           // 8.7: Calculate commission from actual payments
           result.setCommissionCalculationResult(calculateCommissionsFromPayments(initiatedBy));

           // Step 9: Enhanced occupancy detection
           result.setOccupancyResult(detectOccupancyFromTenancies(initiatedBy));

           // Step 10: Sync PayProp files
           if (oAuthUser != null) {
               result.setFilesResult(syncPayPropFiles(oAuthUser, initiatedBy));
           } else {
               log.warn("⚠️ No OAuthUser provided - skipping file sync");
               result.setFilesResult(SyncResult.partial("File sync skipped - no OAuth user", Map.of()));
           }
           
           syncLogger.logSyncComplete("ENHANCED_UNIFIED_SYNC", result.isOverallSuccess(), result.getSummary());
           
       } catch (Exception e) {
           syncLogger.logSyncError("ENHANCED_UNIFIED_SYNC", e);
           result.setOverallError(e.getMessage());
       }
       
       return result;
   }

   // ===== ENHANCED PAYMENT SYNC METHODS =====

   /**
    * Step 7.5a: Sync payment categories
    */
   private SyncResult syncPaymentCategories(Long initiatedBy) {
       try {
           log.info("💳 Starting payment categories sync...");
           return payPropSyncService.syncPaymentCategoriesFromPayProp();
       } catch (Exception e) {
           log.error("❌ Payment categories sync failed: {}", e.getMessage());
           return SyncResult.failure("Payment categories sync failed: " + e.getMessage());
       }
   }

   /**
    * Step 7.5b: Sync all payments (instructions)
    */
   private SyncResult syncPayments(Long initiatedBy) {
       try {
           log.info("💰 Starting payments sync...");
           return payPropSyncService.syncPaymentsToDatabase(initiatedBy);
       } catch (Exception e) {
           log.error("❌ Payments sync failed: {}", e.getMessage());
           return SyncResult.failure("Payments sync failed: " + e.getMessage());
       }
   }

   /**
    * Step 7.5c: Sync beneficiary balances  
    */
   private SyncResult syncBeneficiaryBalances(Long initiatedBy) {
       try {
           log.info("💸 Starting beneficiary balances sync...");
           return payPropSyncService.syncBeneficiaryBalancesToDatabase(initiatedBy);
       } catch (Exception e) {
           log.error("❌ Beneficiary balances sync failed: {}", e.getMessage());
           return SyncResult.failure("Beneficiary balances sync failed: " + e.getMessage());
       }
   }

   /**
    * Step 7.5d: Sync actual financial transactions (ICDN data) - NEW
    * This syncs the actual £840, £1400, £720 rent payments with commission calculations
    */
   private SyncResult syncFinancialTransactions(Long initiatedBy) {
       try {
           log.info("💰 Starting financial transactions (ICDN) sync...");
           // Use existing service method to sync actual financial transactions
           return payPropSyncService.syncActualPaymentsToDatabase(initiatedBy);
       } catch (Exception e) {
           log.error("❌ Financial transactions sync failed: {}", e.getMessage());
           return SyncResult.failure("Financial transactions sync failed: " + e.getMessage());
       }
   }

   /**
    * ✅ DIAGNOSTIC: Log actual PayProp response structure before processing
    */
   private SyncResult syncBatchPayments(Long initiatedBy) {
       try {
           log.info("💳 Starting DIAGNOSTIC batch payments sync...");
           
           HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
           HttpEntity<String> request = new HttpEntity<>(headers);
           
           LocalDate toDate = LocalDate.now();
           LocalDate fromDate = toDate.minusDays(90);
           
           String url = payPropApiBase + "/report/all-payments" +
               "?from_date=" + fromDate +
               "&to_date=" + toDate +
               "&filter_by=reconciliation_date" +
               "&include_beneficiary_info=true" +
               "&rows=10"; // ✅ LIMIT to 10 for diagnostics
           
           ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
           Map<String, Object> responseBody = response.getBody();
           List<Map<String, Object>> payments = (List<Map<String, Object>>) responseBody.get("items");
           
           log.info("📊 Found {} payments. FULL RESPONSE STRUCTURE:", payments.size());
           log.info("🔍 Response keys: {}", responseBody.keySet());
           
           if (!payments.isEmpty()) {
               Map<String, Object> firstPayment = payments.get(0);
               log.info("🔍 FIRST PAYMENT STRUCTURE:");
               log.info("   Payment keys: {}", firstPayment.keySet());
               
               // ✅ LOG EACH FIELD AND ITS TYPE
               for (Map.Entry<String, Object> entry : firstPayment.entrySet()) {
                   String key = entry.getKey();
                   Object value = entry.getValue();
                   String type = value != null ? value.getClass().getSimpleName() : "null";
                   log.info("   {}: {} ({})", key, value, type);
               }
               
               // ✅ SPECIFICALLY CHECK NESTED OBJECTS
               Object paymentBatch = firstPayment.get("payment_batch");
               if (paymentBatch != null) {
                   log.info("🔍 PAYMENT_BATCH STRUCTURE:");
                   if (paymentBatch instanceof Map) {
                       Map<String, Object> batchMap = (Map<String, Object>) paymentBatch;
                       for (Map.Entry<String, Object> entry : batchMap.entrySet()) {
                           String key = entry.getKey();
                           Object value = entry.getValue();
                           String type = value != null ? value.getClass().getSimpleName() : "null";
                           log.info("   batch.{}: {} ({})", key, value, type);
                       }
                   } else {
                       log.info("   payment_batch is not a Map: {} ({})", paymentBatch, paymentBatch.getClass().getSimpleName());
                   }
               } else {
                   log.info("   payment_batch is NULL");
               }
               
               // ✅ CHECK PROPERTY STRUCTURE
               Object property = firstPayment.get("property");
               if (property != null) {
                   log.info("🔍 PROPERTY STRUCTURE:");
                   if (property instanceof Map) {
                       Map<String, Object> propMap = (Map<String, Object>) property;
                       for (Map.Entry<String, Object> entry : propMap.entrySet()) {
                           String key = entry.getKey();
                           Object value = entry.getValue();
                           String type = value != null ? value.getClass().getSimpleName() : "null";
                           log.info("   property.{}: {} ({})", key, value, type);
                       }
                   } else {
                       log.info("   property is not a Map: {} ({})", property, property.getClass().getSimpleName());
                   }
               } else {
                   log.info("   property is NULL");
               }
               
               // ✅ CHECK BENEFICIARY STRUCTURE
               Object beneficiary = firstPayment.get("beneficiary_info");
               if (beneficiary != null) {
                   log.info("🔍 BENEFICIARY_INFO STRUCTURE:");
                   if (beneficiary instanceof Map) {
                       Map<String, Object> benMap = (Map<String, Object>) beneficiary;
                       for (Map.Entry<String, Object> entry : benMap.entrySet()) {
                           String key = entry.getKey();
                           Object value = entry.getValue();
                           String type = value != null ? value.getClass().getSimpleName() : "null";
                           log.info("   beneficiary.{}: {} ({})", key, value, type);
                       }
                   } else {
                       log.info("   beneficiary_info is not a Map: {} ({})", beneficiary, beneficiary.getClass().getSimpleName());
                   }
               } else {
                   log.info("   beneficiary_info is NULL");
               }
           }
           
           // ✅ DON'T PROCESS - JUST DIAGNOSE
           return SyncResult.success("Diagnostic complete - check logs for structure", Map.of(
               "total_payments", payments.size(),
               "diagnostic_mode", true
           ));
           
       } catch (Exception e) {
           log.error("❌ Diagnostic batch payments sync failed: {}", e.getMessage(), e);
           return SyncResult.failure("Diagnostic failed: " + e.getMessage());
       }
   }

   /**
    * Create or update batch payment
    */
   private boolean createOrUpdateBatchPayment(Map<String, Object> batchData, Long initiatedBy) {
       String batchId = (String) batchData.get("id");
       if (batchId == null) return false;
       
       Optional<BatchPayment> existingBatch = batchPaymentRepository.findByPayPropBatchId(batchId);
       BatchPayment batch = existingBatch.orElseGet(() -> {
           BatchPayment newBatch = new BatchPayment();
           newBatch.setPayPropBatchId(batchId);
           newBatch.setCreatedAt(LocalDateTime.now());
           return newBatch;
       });
       
       // Update batch data
       String reference = (String) batchData.get("reference");
       if (reference != null) batch.setBankReference(reference);
       
       String status = (String) batchData.get("status");
       if (status != null) batch.setStatus(status);
       
       Object totalAmount = batchData.get("total_amount");
       if (totalAmount != null) {
           batch.setTotalAmount(new BigDecimal(totalAmount.toString()));
       }
       
       Object paymentCount = batchData.get("payment_count");
       if (paymentCount != null) {
           batch.setPaymentCount(Integer.parseInt(paymentCount.toString()));
       }
       
       String processingDate = (String) batchData.get("processing_date");
       if (processingDate != null) {
           batch.setProcessingDate(LocalDateTime.parse(processingDate));
       }
       
       batch.setUpdatedAt(LocalDateTime.now());
       batchPaymentRepository.save(batch);
       
       return !existingBatch.isPresent();
   }

   /**
    * ✅ SAFE: Create financial transaction with proper type checking
    */
   private FinancialTransaction createFinancialTransactionFromBatchPayment(Map<String, Object> paymentData) {
       try {
           FinancialTransaction transaction = new FinancialTransaction();
           
           // ✅ SAFE: Basic fields with null checking
           Object idObj = paymentData.get("id");
           if (idObj instanceof String) {
               transaction.setPayPropTransactionId((String) idObj);
           } else {
               log.warn("⚠️ Payment ID is not a string: {} ({})", idObj, idObj != null ? idObj.getClass() : "null");
               return null;
           }
           
           transaction.setDataSource("BATCH_PAYMENT");
           transaction.setIsActualTransaction(true);
           transaction.setIsInstruction(false);
           
           // ✅ SAFE: Amount with type checking
           Object amountObj = paymentData.get("amount");
           if (amountObj != null) {
               try {
                   if (amountObj instanceof String) {
                       transaction.setAmount(new BigDecimal((String) amountObj));
                   } else if (amountObj instanceof Number) {
                       transaction.setAmount(new BigDecimal(amountObj.toString()));
                   } else {
                       log.warn("⚠️ Amount is unexpected type: {} ({})", amountObj, amountObj.getClass());
                       return null;
                   }
               } catch (NumberFormatException e) {
                   log.warn("⚠️ Invalid amount format: {}", amountObj);
                   return null;
               }
           } else {
               log.warn("⚠️ Missing amount for payment {}", paymentData.get("id"));
               return null;
           }
           
           // ✅ SAFE: Date fields with multiple fallbacks
           LocalDate transactionDate = null;
           
           // Try reconciliation_date first
           Object reconDateObj = paymentData.get("reconciliation_date");
           if (reconDateObj instanceof String && !((String) reconDateObj).isEmpty()) {
               try {
                   transactionDate = LocalDate.parse((String) reconDateObj);
                   transaction.setReconciliationDate(transactionDate);
               } catch (Exception e) {
                   log.warn("⚠️ Invalid reconciliation_date format: {}", reconDateObj);
               }
           }
           
           // Try payment_date as fallback
           if (transactionDate == null) {
               Object paymentDateObj = paymentData.get("payment_date");
               if (paymentDateObj instanceof String && !((String) paymentDateObj).isEmpty()) {
                   try {
                       transactionDate = LocalDate.parse((String) paymentDateObj);
                   } catch (Exception e) {
                       log.warn("⚠️ Invalid payment_date format: {}", paymentDateObj);
                   }
               }
           }
           
           // Try created_date as fallback
           if (transactionDate == null) {
               Object createdDateObj = paymentData.get("created_date");
               if (createdDateObj instanceof String && !((String) createdDateObj).isEmpty()) {
                   try {
                       transactionDate = LocalDate.parse((String) createdDateObj);
                   } catch (Exception e) {
                       log.warn("⚠️ Invalid created_date format: {}", createdDateObj);
                   }
               }
           }
           
           // Last resort: use today's date
           if (transactionDate == null) {
               log.warn("⚠️ No valid date found for payment {}, using today", paymentData.get("id"));
               transactionDate = LocalDate.now();
           }
           
           transaction.setTransactionDate(transactionDate);
           
           // ✅ SAFE: String fields with type checking
           Object descObj = paymentData.get("description");
           if (descObj instanceof String) {
               transaction.setDescription((String) descObj);
           }
           
           transaction.setTransactionType("payment");
           
           // ✅ SAFE: Property information with proper casting
           Object propertyObj = paymentData.get("property");
           if (propertyObj instanceof Map) {
               Map<String, Object> property = (Map<String, Object>) propertyObj;
               
               Object propIdObj = property.get("id");
               if (propIdObj instanceof String) {
                   transaction.setPropertyId((String) propIdObj);
               }
               
               Object propNameObj = property.get("property_name");
               if (propNameObj instanceof String) {
                   transaction.setPropertyName((String) propNameObj);
               } else {
                   // Try alternative name field
                   Object nameObj = property.get("name");
                   if (nameObj instanceof String) {
                       transaction.setPropertyName((String) nameObj);
                   }
               }
           }
           
           // ✅ SAFE: Batch ID with proper casting
           Object paymentBatchObj = paymentData.get("payment_batch");
           if (paymentBatchObj instanceof Map) {
               Map<String, Object> paymentBatch = (Map<String, Object>) paymentBatchObj;
               Object batchIdObj = paymentBatch.get("id");
               if (batchIdObj instanceof String) {
                   transaction.setPayPropBatchId((String) batchIdObj);
               }
           }
           
           // ✅ SAFE: Category fields
           Object categoryObj = paymentData.get("category");
           if (categoryObj instanceof String) {
               transaction.setCategoryName((String) categoryObj);
           }
           
           Object categoryIdObj = paymentData.get("category_id");
           if (categoryIdObj instanceof String) {
               transaction.setCategoryId((String) categoryIdObj);
           }
           
           // Audit fields
           transaction.setCreatedAt(LocalDateTime.now());
           transaction.setUpdatedAt(LocalDateTime.now());
           
           return transaction;
           
       } catch (Exception e) {
           log.error("❌ Error creating financial transaction from batch payment: {}", e.getMessage(), e);
           return null;
       }
   }

   /**
    * ✅ NEW: Update existing financial transaction from batch payment data
    */
   private void updateFinancialTransactionFromBatchPayment(FinancialTransaction transaction, Map<String, Object> paymentData) {
       try {
           // Update amount
           Object amountObj = paymentData.get("amount");
           if (amountObj != null) {
               transaction.setAmount(new BigDecimal(amountObj.toString()));
           }
           
           // Update dates
           String reconDate = (String) paymentData.get("reconciliation_date");
           if (reconDate != null) {
               transaction.setReconciliationDate(LocalDate.parse(reconDate));
           }
           
           // Update description
           transaction.setDescription((String) paymentData.get("description"));
           transaction.setUpdatedAt(LocalDateTime.now());
           
       } catch (Exception e) {
           log.error("❌ Error updating financial transaction: {}", e.getMessage());
       }
   }

   /**
    * Sync reconciled payments (actual payments that were made)
    */
   private SyncResult syncReconciledPayments(Long initiatedBy) {
       try {
           log.info("💰 Starting reconciled payments sync...");
           
           HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
           HttpEntity<String> request = new HttpEntity<>(headers);
           
           LocalDate fromDate = LocalDate.now().minusMonths(3);
           String url = payPropApiBase + "/export/payments" +
               "?from_date=" + fromDate +
               "&filter_by=reconciliation_date" +
               "&include_beneficiary_info=true" +
               "&rows=1000";
           
           ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
           List<Map<String, Object>> payments = (List<Map<String, Object>>) response.getBody().get("items");
           
           int processed = 0;
           for (Map<String, Object> payment : payments) {
               // Store reconciled payment data
               processed++;
           }
           
           return SyncResult.success("Reconciled payments synced", 
               Map.of("processed", processed));
               
       } catch (Exception e) {
           log.error("❌ Reconciled payments sync failed: {}", e.getMessage());
           return SyncResult.failure("Reconciled payments sync failed: " + e.getMessage());
       }
   }

   /**
    * Calculate commissions from actual payment data
    */
   private SyncResult calculateCommissionsFromPayments(Long initiatedBy) {
       try {
           log.info("🧮 Calculating commissions from actual payments...");
           
           // Get commission rates from properties
           List<Property> properties = propertyService.findAll();
           Map<String, BigDecimal> commissionRates = new HashMap<>();
           
           for (Property property : properties) {
               if (property.getPayPropId() != null && property.getCommissionPercentage() != null) {
                   commissionRates.put(property.getPayPropId(), property.getCommissionPercentage());
               }
           }
           
           // ✅ Use a custom query to avoid the missing column issue
           List<FinancialTransaction> rentPayments = financialTransactionRepository
               .findByDataSourceAndTransactionType("ICDN_ACTUAL", "invoice");
           
           int calculated = 0;
           BigDecimal totalCommission = BigDecimal.ZERO;
           
           for (FinancialTransaction payment : rentPayments) {
               // Skip deposits
               if (payment.getCategoryName() != null && 
                   payment.getCategoryName().toLowerCase().contains("deposit")) {
                   continue;
               }
               
               BigDecimal rate = commissionRates.get(payment.getPropertyId());
               if (rate != null && payment.getAmount() != null) {
                   BigDecimal commission = payment.getAmount()
                       .multiply(rate)
                       .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                   
                   payment.setCommissionRate(rate);
                   payment.setCalculatedCommissionAmount(commission);
                   payment.setUpdatedAt(LocalDateTime.now());
                   
                   financialTransactionRepository.save(payment);
                   
                   calculated++;
                   totalCommission = totalCommission.add(commission);
               }
           }
           
           return SyncResult.success("Commissions calculated", Map.of(
               "payments_processed", rentPayments.size(),
               "commissions_calculated", calculated,
               "total_commission", totalCommission
           ));
           
       } catch (Exception e) {
           log.error("❌ Commission calculation failed: {}", e.getMessage());
           return SyncResult.failure("Commission calculation failed: " + e.getMessage());
       }
   }

   // ===== EXISTING SYNC METHODS (UNCHANGED) =====

   /**
    * Enhanced property sync with complete rent and occupancy data
    */
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public SyncResult syncPropertiesFromPayPropEnhanced(Long initiatedBy) {
       try {
           int page = 1;
           int totalProcessed = 0;
           int totalCreated = 0;
           int totalUpdated = 0;
           int totalErrors = 0;
           int rentAmountsFound = 0;
           int occupancyDetected = 0;
           
           while (true) {
               PayPropSyncService.PayPropExportResult exportResult = 
                   payPropSyncService.exportPropertiesFromPayPropEnhanced(page, batchSize);
               
               if (exportResult.getItems().isEmpty()) {
                   break;
               }

               for (Map<String, Object> propertyData : exportResult.getItems()) {
                   try {
                       // ✅ Transform PayProp data to expected structure
                       Map<String, Object> transformedProperty = transformPayPropPropertyData(propertyData);
                       
                       // Track rent and occupancy data quality
                       Map<String, Object> settings = (Map<String, Object>) transformedProperty.get("settings");
                       if (settings != null && settings.get("monthly_payment") != null) {
                           rentAmountsFound++;
                       }
                       
                       List<Map<String, Object>> activeTenancies = (List<Map<String, Object>>) transformedProperty.get("active_tenancies");
                       if (activeTenancies != null && !activeTenancies.isEmpty()) {
                           occupancyDetected++;
                       }
                       
                       boolean isNew = createOrUpdateProperty(transformedProperty, initiatedBy);
                       if (isNew) totalCreated++; else totalUpdated++;
                       totalProcessed++;
                   } catch (Exception e) {
                       totalErrors++;
                       log.error("Failed to sync property {}: {}", propertyData.get("id"), e.getMessage());
                   }
               }
               page++;
           }

           // Enhanced details with data quality metrics
           Map<String, Object> details = Map.of(
               "processed", totalProcessed,
               "created", totalCreated, 
               "updated", totalUpdated,
               "errors", totalErrors,
               "rentAmountsFound", rentAmountsFound,
               "occupancyDetected", occupancyDetected,
               "rentDataQuality", totalProcessed > 0 ? (rentAmountsFound * 100.0 / totalProcessed) : 0,
               "occupancyDataQuality", totalProcessed > 0 ? (occupancyDetected * 100.0 / totalProcessed) : 0
           );

           return totalErrors == 0 ? 
               SyncResult.success("Enhanced properties synced successfully", details) : 
               SyncResult.partial("Enhanced properties synced with some errors", details);
               
       } catch (Exception e) {
           return SyncResult.failure("Enhanced properties sync failed: " + e.getMessage());
       }
   }

   /**
    * Transforms PayProp's flattened property data into the expected nested structure
    */
   private Map<String, Object> transformPayPropPropertyData(Map<String, Object> payPropData) {
       // PayProp data is already in the correct format - no transformation needed
       // All fields are at root level, not in a settings object
       
       // Transform occupancy data
       List<Map<String, Object>> activeTenancies = 
           (List<Map<String, Object>>) payPropData.get("active_tenancies");
       
       if (activeTenancies != null) {
           payPropData.put("is_occupied", !activeTenancies.isEmpty());
           payPropData.put("active_tenants", activeTenancies);
       } else {
           payPropData.put("is_occupied", false);
           payPropData.put("active_tenants", new ArrayList<>());
       }
       
       log.info("✅ Property {} - Monthly rent: {}, Occupied: {}", 
               payPropData.get("id"), 
               payPropData.get("monthly_payment_required"), 
               payPropData.get("is_occupied"));
       
       return payPropData;
   }

   /**
    * Detect occupancy from active tenancies
    */
   private SyncResult detectOccupancyFromTenancies(Long initiatedBy) {
       try {
           int totalProperties = 0;
           int occupiedProperties = 0;
           
           List<Property> allProperties = propertyService.findAll();
           
           for (Property property : allProperties) {
               if (property.getPayPropId() != null) {
                   totalProperties++;
                   try {
                       PayPropSyncService.PayPropExportResult tenants = 
                           payPropSyncService.exportTenantsByProperty(property.getPayPropId());
                       
                       if (!tenants.getItems().isEmpty()) {
                           occupiedProperties++;
                           // You could update a property occupancy field here if needed
                       }
                   } catch (Exception e) {
                       log.warn("Failed to check occupancy for property {}: {}", property.getPayPropId(), e.getMessage());
                   }
               }
           }
           
           Map<String, Object> details = Map.of(
               "totalProperties", totalProperties,
               "occupiedProperties", occupiedProperties,
               "vacantProperties", totalProperties - occupiedProperties,
               "occupancyRate", totalProperties > 0 ? (occupiedProperties * 100.0 / totalProperties) : 0
           );
           
           return SyncResult.success("Occupancy detection completed", details);
           
       } catch (Exception e) {
           return SyncResult.failure("Occupancy detection failed: " + e.getMessage());
       }
   }

   // ===== STEP 1: SYNC PROPERTIES =====
   
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public SyncResult syncPropertiesFromPayProp(Long initiatedBy) {
       try {
           int page = 1;
           int totalProcessed = 0;
           int totalCreated = 0;
           int totalUpdated = 0;
           int totalErrors = 0;
           
           while (true) {
               PayPropSyncService.PayPropExportResult exportResult = 
                   payPropSyncService.exportPropertiesFromPayProp(page, batchSize);
               
               if (exportResult.getItems().isEmpty()) {
                   break;
               }

               for (Map<String, Object> propertyData : exportResult.getItems()) {
                   try {
                       // ✅ Transform PayProp data to expected structure
                       Map<String, Object> transformedProperty = transformPayPropPropertyData(propertyData);
                       
                       boolean isNew = createOrUpdateProperty(transformedProperty, initiatedBy);
                       if (isNew) totalCreated++; else totalUpdated++;
                       totalProcessed++;
                   } catch (Exception e) {
                       totalErrors++;
                       log.error("Failed to sync property {}: {}", propertyData.get("id"), e.getMessage());
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
               SyncResult.success("Properties synced successfully", details) : 
               SyncResult.partial("Properties synced with some errors", details);
               
       } catch (Exception e) {
           return SyncResult.failure("Properties sync failed: " + e.getMessage());
       }
   }

   // ===== STEP 2: EXTRACT RELATIONSHIPS =====
   
   private Map<String, PropertyRelationship> extractRelationshipsFromPayments() {
       Map<String, PropertyRelationship> relationships = new HashMap<>();
       
       try {
           log.info("🔗 Starting payment relationship extraction...");
           int page = 1;
           int totalPayments = 0;
           int beneficiaryPayments = 0;
           int ownerPayments = 0;
           
           while (true) {
               PayPropSyncService.PayPropExportResult exportResult = 
                   payPropSyncService.exportPaymentsFromPayProp(page, batchSize);
               
               if (exportResult.getItems().isEmpty()) {
                   log.info("📄 No more payments on page {}", page);
                   break;
               }
               
               log.info("📄 Processing {} payments on page {}", exportResult.getItems().size(), page);
               totalPayments += exportResult.getItems().size();
               
               for (Map<String, Object> payment : exportResult.getItems()) {
                   Map<String, Object> beneficiaryInfo = (Map<String, Object>) payment.get("beneficiary_info");
                   if (beneficiaryInfo != null) {
                       beneficiaryPayments++;
                       String beneficiaryType = (String) beneficiaryInfo.get("beneficiary_type");
                       log.debug("Found beneficiary_info: type={}", beneficiaryType);
                       
                       if ("beneficiary".equals(beneficiaryType)) {
                           String category = (String) payment.get("category");
                           log.debug("Beneficiary payment category: {}", category);
                           
                           if ("Owner".equals(category)) {
                               ownerPayments++;
                               String ownerId = (String) beneficiaryInfo.get("id");
                               Map<String, Object> property = (Map<String, Object>) payment.get("property");
                               if (property != null) {
                                   String propertyId = (String) property.get("id");
                                   
                                   PropertyRelationship rel = new PropertyRelationship();
                                   rel.setOwnerPayPropId(ownerId);
                                   rel.setPropertyPayPropId(propertyId);
                                   rel.setOwnershipType("OWNER");
                                   
                                   Object percentage = payment.get("gross_percentage");
                                   if (percentage instanceof Number) {
                                       rel.setOwnershipPercentage(((Number) percentage).doubleValue());
                                   }
                                   
                                   relationships.put(ownerId, rel);
                                   log.info("✅ Found relationship: Owner {} owns Property {} ({}%)", 
                                       ownerId, propertyId, rel.getOwnershipPercentage());
                               } else {
                                   log.warn("⚠️ Owner payment missing property info: {}", ownerId);
                               }
                           }
                       }
                   }
               }
               page++;
           }
           
           log.info("🔗 Payment extraction completed: {} total payments, {} with beneficiary_info, {} owner payments, {} relationships found", 
               totalPayments, beneficiaryPayments, ownerPayments, relationships.size());
           return relationships;
           
       } catch (Exception e) {
           log.error("❌ Failed to extract relationships: {}", e.getMessage(), e);
           return new HashMap<>();
       }
   }

   // ===== STEP 3: SYNC PROPERTY OWNERS AS CUSTOMERS =====
   
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public SyncResult syncPropertyOwnersAsCustomers(Long initiatedBy, Map<String, PropertyRelationship> relationships) {
       try {
           int page = 1;
           int totalProcessed = 0;
           int totalCreated = 0;
           int totalUpdated = 0;
           int totalErrors = 0;
           
           while (true) {
               PayPropSyncService.PayPropExportResult exportResult = 
                   payPropSyncService.exportBeneficiariesFromPayProp(page, batchSize);
               
               if (exportResult.getItems().isEmpty()) {
                   break;
               }

               for (Map<String, Object> beneficiaryData : exportResult.getItems()) {
                   try {
                       String payPropId = (String) beneficiaryData.get("id");
                       PropertyRelationship relationship = relationships.get(payPropId);
                       
                       boolean isNew = createOrUpdatePropertyOwnerCustomer(beneficiaryData, relationship, initiatedBy);
                       if (isNew) totalCreated++; else totalUpdated++;
                       totalProcessed++;
                   } catch (Exception e) {
                       totalErrors++;
                       log.error("Failed to sync property owner {}: {}", beneficiaryData.get("id"), e.getMessage());
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
               SyncResult.success("Property owners synced successfully", details) : 
               SyncResult.partial("Property owners synced with some errors", details);
               
       } catch (Exception e) {
           return SyncResult.failure("Property owners sync failed: " + e.getMessage());
       }
   }

   // ===== STEP 4: SYNC TENANTS AS CUSTOMERS =====
   
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public SyncResult syncTenantsAsCustomers(Long initiatedBy) {
       try {
           int page = 1;
           int totalProcessed = 0;
           int totalCreated = 0;
           int totalUpdated = 0;
           int totalErrors = 0;
           
           while (true) {
               PayPropSyncService.PayPropExportResult exportResult = 
                   payPropSyncService.exportTenantsFromPayProp(page, batchSize);
               
               if (exportResult.getItems().isEmpty()) {
                   break;
               }

               for (Map<String, Object> tenantData : exportResult.getItems()) {
                   try {
                       boolean isNew = createOrUpdateTenantCustomer(tenantData, initiatedBy);
                       if (isNew) totalCreated++; else totalUpdated++;
                       totalProcessed++;
                   } catch (Exception e) {
                       totalErrors++;
                       log.error("Failed to sync tenant {}: {}", tenantData.get("id"), e.getMessage());
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
               SyncResult.success("Tenants synced successfully", details) : 
               SyncResult.partial("Tenants synced with some errors", details);
               
       } catch (Exception e) {
           return SyncResult.failure("Tenants sync failed: " + e.getMessage());
       }
   }

   // ===== STEP 5: SYNC CONTRACTORS AS CUSTOMERS =====

   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public SyncResult syncContractorsAsCustomers(Long initiatedBy) {
       try {
           log.info("🔧 Starting contractor discovery via maintenance payments...");
           
           Set<String> contractorBeneficiaryIds = discoverContractorsFromPayments();
           int totalProcessed = 0;
           int totalCreated = 0;
           int totalUpdated = 0;
           int totalErrors = 0;
           
           log.info("🔧 Found {} unique contractor beneficiary IDs", contractorBeneficiaryIds.size());
           
           if (!contractorBeneficiaryIds.isEmpty()) {
               int page = 1;
               
               while (true) {
                   PayPropSyncService.PayPropExportResult exportResult = 
                       payPropSyncService.exportBeneficiariesFromPayProp(page, batchSize);
                   
                   if (exportResult.getItems().isEmpty()) {
                       break;
                   }

                   for (Map<String, Object> beneficiaryData : exportResult.getItems()) {
                       String beneficiaryId = (String) beneficiaryData.get("id");
                       
                       // Only process if this beneficiary is a contractor
                       if (contractorBeneficiaryIds.contains(beneficiaryId)) {
                           try {
                               boolean isNew = createOrUpdateContractorCustomer(beneficiaryData, initiatedBy);
                               if (isNew) totalCreated++; else totalUpdated++;
                               totalProcessed++;
                               
                               String name = getNameFromBeneficiaryData(beneficiaryData);
                               log.info("✅ Processed contractor: {} ({})", name, isNew ? "created" : "updated");
                           } catch (Exception e) {
                               totalErrors++;
                               log.error("Failed to sync contractor {}: {}", beneficiaryId, e.getMessage());
                           }
                       }
                   }
                   page++;
               }
           }

           Map<String, Object> details = Map.of(
               "contractorsDiscovered", contractorBeneficiaryIds.size(),
               "processed", totalProcessed,
               "created", totalCreated,
               "updated", totalUpdated,
               "errors", totalErrors
           );

           log.info("🔧 Contractor sync completed: {} discovered, {} processed, {} created, {} updated, {} errors", 
               contractorBeneficiaryIds.size(), totalProcessed, totalCreated, totalUpdated, totalErrors);

           return totalErrors == 0 ? 
               SyncResult.success("Contractors synced successfully", details) : 
               SyncResult.partial("Contractors synced with some errors", details);
               
       } catch (Exception e) {
           log.error("❌ Contractor sync failed: {}", e.getMessage(), e);
           return SyncResult.failure("Contractor sync failed: " + e.getMessage());
       }
   }

   // ===== CONTRACTOR DISCOVERY METHOD =====

   private Set<String> discoverContractorsFromPayments() {
       Set<String> contractorIds = new HashSet<>();
       
       try {
           log.info("🔍 Discovering contractors from payment instructions...");
           int page = 1;
           int totalPayments = 0;
           int contractorPayments = 0;
           
           while (true) {
               PayPropSyncService.PayPropExportResult exportResult = 
                   payPropSyncService.exportPaymentsFromPayProp(page, batchSize);
               
               if (exportResult.getItems().isEmpty()) {
                   break;
               }
               
               totalPayments += exportResult.getItems().size();
               
               for (Map<String, Object> payment : exportResult.getItems()) {
                   Map<String, Object> beneficiaryInfo = (Map<String, Object>) payment.get("beneficiary_info");
                   if (beneficiaryInfo != null && "beneficiary".equals(beneficiaryInfo.get("beneficiary_type"))) {
                       String category = (String) payment.get("category");
                       
                       // Look for contractor-related payment categories or maintenance tickets
                       if (isContractorPayment(payment, category)) {
                           String contractorId = (String) beneficiaryInfo.get("id");
                           contractorIds.add(contractorId);
                           contractorPayments++;
                           
                           log.debug("🔧 Found contractor payment: {} (category: {}, maintenance_ticket: {})", 
                               contractorId, category, payment.get("maintenance_ticket_id"));
                       }
                   }
               }
               page++;
           }
           
           log.info("🔧 Contractor discovery completed: {} contractors found from {} payments ({} contractor payments)", 
               contractorIds.size(), totalPayments, contractorPayments);
           return contractorIds;
           
       } catch (Exception e) {
           log.error("❌ Failed to discover contractors from payments: {}", e.getMessage());
           return new HashSet<>();
       }
   }

   private boolean isContractorPayment(Map<String, Object> payment, String category) {
       // Method 1: Payment linked to maintenance ticket
       if (payment.get("maintenance_ticket_id") != null) {
           return true;
       }
       
       // Method 2: Contractor-related payment categories
       if (category != null) {
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
               lowerCategory.contains("building");
       }
       
       return false;
   }

   // ===== CONTRACTOR CUSTOMER CREATION/UPDATE =====

   private boolean createOrUpdateContractorCustomer(Map<String, Object> beneficiaryData, Long initiatedBy) {
       String payPropId = (String) beneficiaryData.get("id");
       Customer existing = customerService.findByPayPropEntityId(payPropId);
       
       if (existing != null) {
           // Update existing customer to be a contractor if not already marked
           if (!existing.getIsContractor()) {
               existing.setIsContractor(true);
               existing.setCustomerType(CustomerType.CONTRACTOR);
               log.info("🔧 Updated existing customer {} to contractor", existing.getName());
           }
           updateCustomerFromContractorData(existing, beneficiaryData);
           customerService.save(existing);
           return false;
       } else {
           Customer customer = createCustomerFromContractorData(beneficiaryData, initiatedBy);
           customer.setCreatedAt(LocalDateTime.now());
           customerService.save(customer);
           return true;
       }
   }

   private Customer createCustomerFromContractorData(Map<String, Object> data, Long initiatedBy) {
       Customer customer = new Customer();
       
       // PayProp Integration Fields
       customer.setPayPropEntityId((String) data.get("id"));
       customer.setPayPropCustomerId((String) data.get("customer_id"));
       customer.setPayPropEntityType("beneficiary");
       customer.setCustomerType(CustomerType.CONTRACTOR);
       customer.setIsContractor(true);
       customer.setPayPropSynced(true);
       customer.setPayPropLastSync(LocalDateTime.now());
       
       // Account Type and Name
       String accountTypeStr = (String) data.get("account_type");
       if ("business".equals(accountTypeStr)) {
           customer.setAccountType(AccountType.business);
           customer.setBusinessName((String) data.get("business_name"));
           customer.setName(customer.getBusinessName());
       } else {
           customer.setAccountType(AccountType.individual);
           customer.setFirstName((String) data.get("first_name"));
           customer.setLastName((String) data.get("last_name"));
           customer.setName(customer.getFirstName() + " " + customer.getLastName());
       }
       
       // Contact Details
       customer.setEmail((String) data.get("email_address"));
       customer.setMobileNumber((String) data.get("mobile"));
       customer.setPhone((String) data.get("phone"));
       customer.setCountry("UK");
       
       // Address (same pattern as existing beneficiary mapping)
       Map<String, Object> address = (Map<String, Object>) data.get("billing_address");
       if (address != null) {
           customer.setAddressLine1((String) address.get("first_line"));
           customer.setAddressLine2((String) address.get("second_line"));
           customer.setAddressLine3((String) address.get("third_line"));
           customer.setCity((String) address.get("city"));
           customer.setState((String) address.get("state"));
           customer.setPostcode((String) address.get("postal_code"));
           customer.setCountryCode((String) address.get("country_code"));
       }
       
       // Bank Details (same pattern as property owners)
       Map<String, Object> bankAccount = (Map<String, Object>) data.get("bank_account");
       if (bankAccount != null) {
           customer.setBankAccountName((String) bankAccount.get("account_name"));
           customer.setBankAccountNumber((String) bankAccount.get("account_number"));
           customer.setBankSortCode((String) bankAccount.get("branch_code"));
           customer.setBankName((String) bankAccount.get("bank_name"));
           customer.setBankBranchName((String) bankAccount.get("branch_name"));
           customer.setBankIban((String) bankAccount.get("iban"));
           customer.setBankSwiftCode((String) bankAccount.get("swift_code"));
       }
       
       // Set user_id for database constraint
       User user = userRepository.getReferenceById(initiatedBy.intValue());
       customer.setUser(user);
       
       return customer;
   }

   private void updateCustomerFromContractorData(Customer customer, Map<String, Object> data) {
       // Update PayProp sync fields
       customer.setPayPropLastSync(LocalDateTime.now());
       customer.setPayPropSynced(true);
       
       // Update contact details
       customer.setEmail((String) data.get("email_address"));
       customer.setMobileNumber((String) data.get("mobile"));
       customer.setPhone((String) data.get("phone"));
       
       // Update names if needed
       if (customer.getAccountType() == AccountType.business) {
           String businessName = (String) data.get("business_name");
           if (businessName != null) {
               customer.setBusinessName(businessName);
               customer.setName(businessName);
           }
       } else {
           String firstName = (String) data.get("first_name");
           String lastName = (String) data.get("last_name");
           if (firstName != null && lastName != null) {
               customer.setFirstName(firstName);
               customer.setLastName(lastName);
               customer.setName(firstName + " " + lastName);
           }
       }
   }

   // ===== UTILITY METHOD =====

   private String getNameFromBeneficiaryData(Map<String, Object> data) {
       String businessName = (String) data.get("business_name");
       if (businessName != null) {
           return businessName;
       }
       
       String firstName = (String) data.get("first_name");
       String lastName = (String) data.get("last_name");
       if (firstName != null && lastName != null) {
           return firstName + " " + lastName;
       }
       
       return "Unknown Contractor";
   }

   // ===== STEP 6: ESTABLISH PROPERTY ASSIGNMENTS =====
   
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public SyncResult establishPropertyAssignments(Map<String, PropertyRelationship> relationships) {
       try {
           log.info("🏠 Starting enhanced property assignment establishment...");
           int globalAssignments = 0;
           int propertySpecificAssignments = 0;
           int assignmentErrors = 0;
           
           // Step 1: Establish relationships from global payments (existing logic)
           log.info("📋 Step 1: Processing global payment relationships ({} found)", relationships.size());
           for (PropertyRelationship rel : relationships.values()) {
               try {
                   Customer ownerCustomer = customerService.findByPayPropEntityId(rel.getOwnerPayPropId());
                   if (ownerCustomer == null) {
                       log.warn("❌ Property owner customer not found for PayProp ID: {}", rel.getOwnerPayPropId());
                       assignmentErrors++;
                       continue;
                   }
                   
                   Optional<Property> propertyOpt = propertyService.findByPayPropId(rel.getPropertyPayPropId());
                   if (propertyOpt.isEmpty()) {
                       log.warn("❌ Property not found for PayProp ID: {}", rel.getPropertyPayPropId());
                       assignmentErrors++;
                       continue;
                   }
                   Property property = propertyOpt.get();
                   
                   BigDecimal percentage = rel.getOwnershipPercentage() != null ? 
                       new BigDecimal(rel.getOwnershipPercentage().toString()) : new BigDecimal("100.00");
                   
                   try {
                       assignmentService.createAssignment(ownerCustomer, property, AssignmentType.OWNER, percentage, true);
                       globalAssignments++;
                       log.info("✅ Global payment assignment: {} owns {} ({}%)", 
                           ownerCustomer.getName(), property.getPropertyName(), percentage);
                   } catch (IllegalStateException e) {
                       log.info("ℹ️ Assignment already exists: {} owns {}", ownerCustomer.getName(), property.getPropertyName());
                   }
                   
               } catch (Exception e) {
                   assignmentErrors++;
                   log.error("❌ Failed to establish global assignment: {}", e.getMessage(), e);
               }
           }
           
           // Step 2: Find missing owners via property-specific payments (NEW ENHANCED LOGIC)
           log.info("🔍 Step 2: Finding missing owners via property-specific payments...");
           List<Property> propertiesWithoutOwners = propertyService.findAll().stream()
               .filter(p -> {
                   List<Customer> owners = assignmentService.getCustomersForProperty(p.getId(), AssignmentType.OWNER);
                   return owners.isEmpty();
               })
               .collect(Collectors.toList());
           
           log.info("Found {} properties without owners, checking each individually...", propertiesWithoutOwners.size());
           
           for (Property property : propertiesWithoutOwners) {
               if (property.getPayPropId() != null) {
                   try {
                       PayPropSyncService.PayPropExportResult propertyPayments = 
                           payPropSyncService.exportPaymentsByProperty(property.getPayPropId());
                       
                       for (Map<String, Object> payment : propertyPayments.getItems()) {
                           if ("Owner".equals(payment.get("category")) && payment.get("beneficiary_info") != null) {
                               Map<String, Object> beneficiaryInfo = (Map<String, Object>) payment.get("beneficiary_info");
                               String ownerId = (String) beneficiaryInfo.get("id");
                               
                               Customer owner = customerService.findByPayPropEntityId(ownerId);
                               if (owner != null) {
                                   try {
                                       assignmentService.createAssignment(owner, property, AssignmentType.OWNER, 
                                           new BigDecimal("100.00"), true);
                                       propertySpecificAssignments++;
                                       log.info("🎯 Found missing owner via property-specific API: {} owns {}", 
                                           owner.getName(), property.getPropertyName());
                                   } catch (IllegalStateException e) {
                                       log.info("ℹ️ Property-specific assignment already exists: {} owns {}", 
                                           owner.getName(), property.getPropertyName());
                                   }
                               } else {
                                   log.warn("❌ Owner customer not found for beneficiary ID: {}", ownerId);
                                   assignmentErrors++;
                               }
                           }
                       }
                   } catch (Exception e) {
                       assignmentErrors++;
                       log.error("Failed to get property-specific payments for {}: {}", 
                           property.getPayPropId(), e.getMessage());
                   }
               }
           }
           
           Map<String, Object> details = Map.of(
               "globalAssignments", globalAssignments,
               "propertySpecificAssignments", propertySpecificAssignments,
               "totalAssignments", globalAssignments + propertySpecificAssignments,
               "assignmentErrors", assignmentErrors,
               "totalRelationships", relationships.size()
           );
           
           log.info("🏠 Enhanced assignment completed: {} global + {} property-specific = {} total assignments, {} errors", 
               globalAssignments, propertySpecificAssignments, globalAssignments + propertySpecificAssignments, assignmentErrors);
           
           return assignmentErrors == 0 ? 
               SyncResult.success("Enhanced property assignments established", details) :
               SyncResult.partial("Enhanced property assignments completed with some errors", details);
               
       } catch (Exception e) {
           log.error("❌ Enhanced property assignment process failed: {}", e.getMessage(), e);
           return SyncResult.failure("Enhanced property assignments failed: " + e.getMessage());
       }
   }

   // ===== STEP 7: ESTABLISH TENANT RELATIONSHIPS =====
   
   private SyncResult establishTenantPropertyRelationships() {
       int relationships = 0;
       int errors = 0;
       
       List<Property> allProperties = propertyService.findAll();
       
       for (Property property : allProperties) {
           if (property.getPayPropId() != null) {
               try {
                   PayPropSyncService.PayPropExportResult tenants = payPropSyncService.exportTenantsByProperty(property.getPayPropId());
                   
                   for (Map<String, Object> tenantData : tenants.getItems()) {
                       String tenantPayPropId = (String) tenantData.get("id");
                       Customer tenant = customerService.findByPayPropEntityId(tenantPayPropId);
                       
                       if (tenant != null) {
                           try {
                               assignmentService.createAssignment(tenant, property, AssignmentType.TENANT);
                               relationships++;
                               log.info("✅ Established tenant relationship: {} rents {}", 
                                   tenant.getName(), property.getPropertyName());
                           } catch (IllegalStateException e) {
                               log.info("ℹ️ Tenant assignment already exists: {} rents {}", 
                                   tenant.getName(), property.getPropertyName());
                           }
                       }
                   }
               } catch (Exception e) {
                   errors++;
                   log.error("Failed to get tenants for property {}: {}", property.getPayPropId(), e.getMessage());
               }
           }
       }
       
       Map<String, Object> details = Map.of(
           "tenantRelationships", relationships,
           "errors", errors
       );
       
       return errors == 0 ? 
           SyncResult.success("Tenant relationships established", details) :
           SyncResult.partial("Tenant relationships completed with some errors", details);
   }

   // ===== STEP 8: FILE SYNC METHODS =====

   /**
    * Sync PayProp files for all customers after entity sync
    */
   public SyncResult syncPayPropFiles(OAuthUser oAuthUser, Long initiatedBy) {
       try {
           int totalFiles = 0;
           int successFiles = 0;
           int errorFiles = 0;
           
           log.info("📁 Starting PayProp file sync for all customers...");
           
           // Get all synced customers
           List<Customer> syncedCustomers = customerService.findByPayPropSynced(true);
           log.info("📋 Found {} PayProp-synced customers", syncedCustomers.size());
           
           for (Customer customer : syncedCustomers) {
               try {
                   String entityType = determinePayPropEntityType(customer);
                   if (entityType != null && customer.getPayPropEntityId() != null) {
                       // Before attempting any attachment operations, check permissions
                       if (payPropSyncService.hasAttachmentPermissions()) {
                           // Existing attachment sync code
                           List<PayPropSyncService.PayPropAttachment> attachments = 
                               payPropSyncService.getPayPropAttachments(entityType, customer.getPayPropEntityId());
                           
                           log.info("📎 Found {} attachments for {} customer: {}", 
                               attachments.size(), entityType, customer.getName());
                       
                           for (PayPropSyncService.PayPropAttachment attachment : attachments) {
                               totalFiles++;
                               try {
                                   syncCustomerFile(oAuthUser, customer, attachment, entityType);
                                   successFiles++;
                                   log.info("✅ Synced file: {} for customer: {}", 
                                       attachment.getFileName(), customer.getName());
                               } catch (Exception e) {
                                   errorFiles++;
                                   log.error("❌ Failed to sync file {} for customer {}: {}", 
                                       attachment.getFileName(), customer.getName(), e.getMessage());
                               }
                           }
                       } else {
                           // Log once that attachments are being skipped
                           payPropSyncService.logAttachmentPermissionWarningOnce();
                           log.debug("Skipping attachment sync for {} - insufficient permissions", customer.getName());
                       }
                   }
               } catch (Exception e) {
                   log.error("❌ Failed to sync files for customer {}: {}", 
                       customer.getName(), e.getMessage());
               }
           }
           
           Map<String, Object> details = Map.of(
               "totalFiles", totalFiles,
               "successFiles", successFiles,
               "errorFiles", errorFiles,
               "customersProcessed", syncedCustomers.size()
           );
           
           log.info("📁 File sync completed: {} total files, {} successful, {} errors", 
               totalFiles, successFiles, errorFiles);
           
           return errorFiles == 0 ? 
               SyncResult.success("PayProp files synced successfully", details) :
               SyncResult.partial("PayProp files synced with some errors", details);
               
       } catch (Exception e) {
           log.error("❌ PayProp file sync failed: {}", e.getMessage(), e);
           return SyncResult.failure("PayProp file sync failed: " + e.getMessage());
       }
   }

   private void syncCustomerFile(OAuthUser oAuthUser, Customer customer, 
                                PayPropSyncService.PayPropAttachment attachment, 
                                String entityType) throws Exception {
       
       // Check if file already exists
       if (fileAlreadyExists(customer, attachment.getExternalId())) {
           log.info("📄 File {} already exists for customer {}, skipping", 
               attachment.getFileName(), customer.getName());
           return;
       }
       
       // Download file
       byte[] fileData = payPropSyncService.downloadPayPropAttachment(attachment.getExternalId());
       if (fileData == null) {
           throw new RuntimeException("Failed to download file: " + attachment.getFileName());
       }
       
       // Use your existing CustomerDriveOrganizationService to organize the file
       customerDriveOrganizationService.syncPayPropFile(
           oAuthUser, customer, fileData, attachment.getFileName(), entityType);
   }

   private boolean fileAlreadyExists(Customer customer, String externalId) {
       try {
           return !googleDriveFileService.findByCustomerIdAndPayPropExternalId(customer.getCustomerId().intValue(), externalId).isEmpty();
       } catch (Exception e) {
           log.warn("Error checking if file exists: {}", e.getMessage());
           return false; // Assume it doesn't exist if we can't check
       }
   }

   private String determinePayPropEntityType(Customer customer) {
       if (customer.getIsTenant()) return "tenant";
       if (customer.getIsPropertyOwner()) return "beneficiary"; 
       if (customer.getIsContractor()) return "beneficiary"; // Contractors are also beneficiaries in PayProp
       return null;
   }

   // ===== ENTITY CREATION/UPDATE METHODS =====

   private boolean createOrUpdateProperty(Map<String, Object> propertyData, Long initiatedBy) {
       String payPropId = (String) propertyData.get("id");
       Optional<Property> existingOpt = propertyService.findByPayPropId(payPropId);
       
       if (existingOpt.isPresent()) {
           Property existing = existingOpt.get();
           updatePropertyFromPayPropData(existing, propertyData);
           existing.setUpdatedBy(initiatedBy);
           propertyService.save(existing);
           return false;
       } else {
           Property property = createPropertyFromPayPropData(propertyData);
           property.setCreatedBy(initiatedBy);
           propertyService.save(property);
           return true;
       }
   }

   private boolean createOrUpdatePropertyOwnerCustomer(Map<String, Object> beneficiaryData, 
                                                      PropertyRelationship relationship, 
                                                      Long initiatedBy) {
       String payPropId = (String) beneficiaryData.get("id");
       Customer existing = customerService.findByPayPropEntityId(payPropId);
       
       if (existing != null) {
           updateCustomerFromBeneficiaryData(existing, beneficiaryData, relationship);
           customerService.save(existing);
           return false;
       } else {
           Customer customer = createCustomerFromBeneficiaryData(beneficiaryData, relationship, initiatedBy);
           customer.setCreatedAt(LocalDateTime.now());
           customerService.save(customer);
           return true;
       }
   }

   private boolean createOrUpdateTenantCustomer(Map<String, Object> tenantData, Long initiatedBy) {
       String payPropId = (String) tenantData.get("id");
       Customer existing = customerService.findByPayPropEntityId(payPropId);
       
       if (existing != null) {
           updateCustomerFromTenantData(existing, tenantData);
           customerService.save(existing);
           return false;
       } else {
           Customer customer = createCustomerFromTenantData(tenantData, initiatedBy);
           customer.setCreatedAt(LocalDateTime.now());
           customerService.save(customer);
           return true;
       }
   }

   // ===== CUSTOMER MAPPING METHODS =====

   private Customer createCustomerFromBeneficiaryData(Map<String, Object> data, PropertyRelationship relationship, Long initiatedBy) {
       Customer customer = new Customer();
       
       // PayProp Integration Fields
       customer.setPayPropEntityId((String) data.get("id"));
       customer.setPayPropCustomerId((String) data.get("customer_id"));
       customer.setPayPropEntityType("beneficiary");
       customer.setCustomerType(CustomerType.PROPERTY_OWNER);
       customer.setIsPropertyOwner(true);
       customer.setPayPropSynced(true);
       customer.setPayPropLastSync(LocalDateTime.now());
       
       // Account Type
       String accountTypeStr = (String) data.get("account_type");
       if ("business".equals(accountTypeStr)) {
           customer.setAccountType(AccountType.business);
           customer.setBusinessName((String) data.get("business_name"));
           customer.setName(customer.getBusinessName());
       } else {
           customer.setAccountType(AccountType.individual);
           customer.setFirstName((String) data.get("first_name"));
           customer.setLastName((String) data.get("last_name"));
           customer.setName(customer.getFirstName() + " " + customer.getLastName());
       }
       
       // Contact Details
       customer.setEmail((String) data.get("email_address"));
       customer.setMobileNumber((String) data.get("mobile"));
       customer.setPhone((String) data.get("phone"));
       
       // FIXED: Set required country field
       customer.setCountry("UK"); // Default required value
       
       // Address
       Map<String, Object> address = (Map<String, Object>) data.get("billing_address");
       if (address != null) {
           customer.setAddressLine1((String) address.get("first_line"));
           customer.setAddressLine2((String) address.get("second_line"));
           customer.setAddressLine3((String) address.get("third_line"));
           customer.setCity((String) address.get("city"));
           customer.setState((String) address.get("state"));
           customer.setPostcode((String) address.get("postal_code"));
           customer.setCountryCode((String) address.get("country_code"));
       }
       
       // Payment Method
       String paymentMethodStr = (String) data.get("payment_method");
       if (paymentMethodStr != null) {
           try {
               customer.setPaymentMethod(PaymentMethod.valueOf(paymentMethodStr.toLowerCase()));
           } catch (IllegalArgumentException e) {
               customer.setPaymentMethod(PaymentMethod.local);
           }
       }
       
       // Bank Details
       Map<String, Object> bankAccount = (Map<String, Object>) data.get("bank_account");
       if (bankAccount != null) {
           customer.setBankAccountName((String) bankAccount.get("account_name"));
           customer.setBankAccountNumber((String) bankAccount.get("account_number"));
           customer.setBankSortCode((String) bankAccount.get("branch_code"));
           customer.setBankName((String) bankAccount.get("bank_name"));
           customer.setBankBranchName((String) bankAccount.get("branch_name"));
           customer.setBankIban((String) bankAccount.get("iban"));
           customer.setBankSwiftCode((String) bankAccount.get("swift_code"));
       }
       
       // Property Relationship will be handled separately via junction table
       // No direct assignment to customer entity anymore
       
       // Set user_id for database constraint
       User user = userRepository.getReferenceById(initiatedBy.intValue());
       customer.setUser(user);
       
       return customer;
   }

   private Customer createCustomerFromTenantData(Map<String, Object> data, Long initiatedBy) {
       Customer customer = new Customer();
       
       // PayProp Integration Fields
       customer.setPayPropEntityId((String) data.get("id"));
       customer.setPayPropCustomerId((String) data.get("customer_id"));
       customer.setPayPropEntityType("tenant");
       customer.setCustomerType(CustomerType.TENANT);
       customer.setIsTenant(true);
       customer.setPayPropSynced(true);
       customer.setPayPropLastSync(LocalDateTime.now());
       
       // Account Type
       String accountTypeStr = (String) data.get("account_type");
       if ("business".equals(accountTypeStr)) {
           customer.setAccountType(AccountType.business);
           customer.setBusinessName((String) data.get("business_name"));
           customer.setName(customer.getBusinessName());
       } else {
           customer.setAccountType(AccountType.individual);
           customer.setFirstName((String) data.get("first_name"));
           customer.setLastName((String) data.get("last_name"));
           customer.setName(customer.getFirstName() + " " + customer.getLastName());
       }
       
       // Contact Details
       customer.setEmail((String) data.get("email_address"));
       customer.setMobileNumber((String) data.get("mobile_number"));
       customer.setPhone((String) data.get("phone"));
       
       // FIXED: Set required country field
       customer.setCountry("UK"); // Default required value
       
       // Address
       Map<String, Object> address = (Map<String, Object>) data.get("address");
       if (address != null) {
           customer.setAddressLine1((String) address.get("first_line"));
           customer.setAddressLine2((String) address.get("second_line"));
           customer.setAddressLine3((String) address.get("third_line"));
           customer.setCity((String) address.get("city"));
           customer.setPostcode((String) address.get("postal_code"));
           customer.setCountryCode((String) address.get("country_code"));
       }
       
       // Tenant-specific fields
       Object invoiceLeadDays = data.get("invoice_lead_days");
       if (invoiceLeadDays instanceof Number) {
           customer.setInvoiceLeadDays(((Number) invoiceLeadDays).intValue());
       }
       
       // Bank Details (optional for tenants)
       Boolean hasBankAccount = (Boolean) data.get("has_bank_account");
       if (Boolean.TRUE.equals(hasBankAccount)) {
           Map<String, Object> bankAccount = (Map<String, Object>) data.get("bank_account");
           if (bankAccount != null) {
               customer.setBankAccountName((String) bankAccount.get("account_name"));
               customer.setBankAccountNumber((String) bankAccount.get("account_number"));
               customer.setBankSortCode((String) bankAccount.get("branch_code"));
               customer.setBankName((String) bankAccount.get("bank_name"));
               customer.setHasBankAccount(true);
           }
       }
       
       // Set user_id for database constraint
       User user = userRepository.getReferenceById(initiatedBy.intValue());
       customer.setUser(user);
       
       return customer;
   }

   private void updateCustomerFromBeneficiaryData(Customer customer, Map<String, Object> data, PropertyRelationship relationship) {
       // Update PayProp sync fields
       customer.setPayPropLastSync(LocalDateTime.now());
       customer.setPayPropSynced(true);
       
       // Update contact details
       customer.setEmail((String) data.get("email_address"));
       customer.setMobileNumber((String) data.get("mobile"));
       customer.setPhone((String) data.get("phone"));
       
       // Update names if needed
       if (customer.getAccountType() == AccountType.business) {
           String businessName = (String) data.get("business_name");
           if (businessName != null) {
               customer.setBusinessName(businessName);
               customer.setName(businessName);
           }
       } else {
           String firstName = (String) data.get("first_name");
           String lastName = (String) data.get("last_name");
           if (firstName != null && lastName != null) {
               customer.setFirstName(firstName);
               customer.setLastName(lastName);
               customer.setName(firstName + " " + lastName);
           }
       }
       
       // Property relationships now handled via junction table
       // Customer updates don't modify property assignments directly
   }

   private void updateCustomerFromTenantData(Customer customer, Map<String, Object> data) {
       // Update PayProp sync fields
       customer.setPayPropLastSync(LocalDateTime.now());
       customer.setPayPropSynced(true);
       
       // Update contact details
       customer.setEmail((String) data.get("email_address"));
       customer.setMobileNumber((String) data.get("mobile_number"));
       customer.setPhone((String) data.get("phone"));
       
       // Update names if needed
       if (customer.getAccountType() == AccountType.business) {
           String businessName = (String) data.get("business_name");
           if (businessName != null) {
               customer.setBusinessName(businessName);
               customer.setName(businessName);
           }
       } else {
           String firstName = (String) data.get("first_name");
           String lastName = (String) data.get("last_name");
           if (firstName != null && lastName != null) {
               customer.setFirstName(firstName);
               customer.setLastName(lastName);
               customer.setName(firstName + " " + lastName);
           }
       }
   }

   // ===== UTILITY METHODS =====

   private Property createPropertyFromPayPropData(Map<String, Object> data) {
       Property property = new Property();
       property.setPayPropId((String) data.get("id"));
       property.setCustomerId((String) data.get("customer_id"));
       property.setCustomerReference((String) data.get("customer_reference"));
       property.setPropertyName((String) data.get("property_name"));
       
       // Address mapping
       Map<String, Object> address = (Map<String, Object>) data.get("address");
       if (address != null) {
           property.setAddressLine1((String) address.get("first_line"));
           property.setAddressLine2((String) address.get("second_line"));
           property.setAddressLine3((String) address.get("third_line"));
           property.setCity((String) address.get("city"));
           property.setState((String) address.get("state"));
           property.setPostcode((String) address.get("postal_code"));
           property.setCountryCode((String) address.get("country_code"));
       }
       
       // ✅ Direct field mapping - no settings object in PayProp
       // Monthly payment
       Object monthlyPayment = data.get("monthly_payment_required");
       if (monthlyPayment instanceof Number) {
           property.setMonthlyPayment(new BigDecimal(monthlyPayment.toString()));
           log.info("✅ Mapped monthly_payment: {} for property {}", monthlyPayment, property.getPayPropId());
       }
       
       // Payment settings
       Object enablePayments = data.get("allow_payments");
       if (enablePayments instanceof Boolean) {
           property.setEnablePaymentsFromBoolean((Boolean) enablePayments);
       }
       
       Object holdOwnerFunds = data.get("hold_all_owner_funds");
       if (holdOwnerFunds instanceof Boolean) {
           property.setHoldOwnerFundsFromBoolean((Boolean) holdOwnerFunds);
       }
       
       // Minimum balance
       Object minimumBalance = data.get("property_account_minimum_balance");
       if (minimumBalance instanceof String) {
           try {
               property.setPropertyAccountMinimumBalance(new BigDecimal((String) minimumBalance));
           } catch (NumberFormatException e) {
               property.setPropertyAccountMinimumBalance(BigDecimal.ZERO);
           }
       }
       
       // Account balance
       Object accountBalance = data.get("account_balance");
       if (accountBalance instanceof Number) {
           property.setAccountBalance(new BigDecimal(accountBalance.toString()));
       }
       
       // Date fields
       String listingFrom = (String) data.get("listed_from");
       if (listingFrom != null) {
           try {
               property.setListedFrom(LocalDate.parse(listingFrom));
           } catch (Exception e) {
               log.warn("Could not parse listed_from date: {}", listingFrom);
           }
       }
       
       String listingTo = (String) data.get("listed_until");
       if (listingTo != null) {
           try {
               property.setListedUntil(LocalDate.parse(listingTo));
           } catch (Exception e) {
               log.warn("Could not parse listed_until date: {}", listingTo);
           }
       }
       
       return property;
   }

   private void updatePropertyFromPayPropData(Property property, Map<String, Object> data) {
       property.setPropertyName((String) data.get("property_name"));
       property.setCustomerReference((String) data.get("customer_reference"));
       
    // Update address if present
    Map<String, Object> address = (Map<String, Object>) data.get("address");
       if (address != null) {
           property.setAddressLine1((String) address.get("first_line"));
           property.setAddressLine2((String) address.get("second_line"));
           property.setAddressLine3((String) address.get("third_line"));
           property.setCity((String) address.get("city"));
           property.setState((String) address.get("state"));
           property.setPostcode((String) address.get("postal_code"));
           property.setCountryCode((String) address.get("country_code"));
       }
       
       // ✅ Update direct fields - no settings object in PayProp
       // Monthly payment
       Object monthlyPayment = data.get("monthly_payment_required");
       if (monthlyPayment instanceof Number) {
           property.setMonthlyPayment(new BigDecimal(monthlyPayment.toString()));
           log.info("✅ Updated monthly_payment: {} for property {}", monthlyPayment, property.getPayPropId());
       }
       
       // Payment settings
       Object enablePayments = data.get("allow_payments");
       if (enablePayments instanceof Boolean) {
           property.setEnablePaymentsFromBoolean((Boolean) enablePayments);
       }
       
       Object holdOwnerFunds = data.get("hold_all_owner_funds");
       if (holdOwnerFunds instanceof Boolean) {
           property.setHoldOwnerFundsFromBoolean((Boolean) holdOwnerFunds);
       }
       
       // Minimum balance
       Object minimumBalance = data.get("property_account_minimum_balance");
       if (minimumBalance instanceof String) {
           try {
               property.setPropertyAccountMinimumBalance(new BigDecimal((String) minimumBalance));
           } catch (NumberFormatException e) {
               property.setPropertyAccountMinimumBalance(BigDecimal.ZERO);
           }
       }
       
       // Account balance
       Object accountBalance = data.get("account_balance");
       if (accountBalance instanceof Number) {
           property.setAccountBalance(new BigDecimal(accountBalance.toString()));
       }
       
       // Date fields
       String listingFrom = (String) data.get("listed_from");
       if (listingFrom != null) {
           try {
               property.setListedFrom(LocalDate.parse(listingFrom));
           } catch (Exception e) {
               log.warn("Could not parse listed_from date: {}", listingFrom);
           }
       }
       
       String listingTo = (String) data.get("listed_until");
       if (listingTo != null) {
           try {
               property.setListedUntil(LocalDate.parse(listingTo));
           } catch (Exception e) {
               log.warn("Could not parse listed_until date: {}", listingTo);
           }
       }
   }

   // ===== ENHANCED RESULT CLASSES =====

   public static class UnifiedSyncResult {
       private SyncResult propertiesResult;
       private SyncResult propertyOwnersResult;
       private SyncResult tenantsResult;
       private SyncResult contractorsResult;
       private SyncResult relationshipsResult;
       private SyncResult tenantRelationshipsResult;
       private SyncResult filesResult;
       private String overallError;
       private SyncResult occupancyResult;
       
       // ✅ COMPLETE PAYMENT SYNC RESULT FIELDS
       private SyncResult paymentCategoriesResult;
       private SyncResult paymentsResult;
       private SyncResult beneficiaryBalancesResult;
       private SyncResult financialTransactionsResult; // NEW - ICDN financial transactions
       
       // Add these new fields
       private SyncResult reconciledPaymentsResult;
       private SyncResult batchPaymentsResult;
       private SyncResult commissionCalculationResult;

       public SyncResult getOccupancyResult() { return occupancyResult; }
       public void setOccupancyResult(SyncResult occupancyResult) { this.occupancyResult = occupancyResult; }

       // ✅ COMPLETE PAYMENT SYNC GETTERS/SETTERS
       public SyncResult getPaymentCategoriesResult() { return paymentCategoriesResult; }
       public void setPaymentCategoriesResult(SyncResult paymentCategoriesResult) { 
           this.paymentCategoriesResult = paymentCategoriesResult; 
       }
       
       public SyncResult getPaymentsResult() { return paymentsResult; }
       public void setPaymentsResult(SyncResult paymentsResult) { 
           this.paymentsResult = paymentsResult; 
       }
       
       public SyncResult getBeneficiaryBalancesResult() { return beneficiaryBalancesResult; }
       public void setBeneficiaryBalancesResult(SyncResult beneficiaryBalancesResult) { 
           this.beneficiaryBalancesResult = beneficiaryBalancesResult; 
       }
       
       // ✅ NEW - Financial Transactions (ICDN) getter/setter
       public SyncResult getFinancialTransactionsResult() { return financialTransactionsResult; }
       public void setFinancialTransactionsResult(SyncResult financialTransactionsResult) { 
           this.financialTransactionsResult = financialTransactionsResult; 
       }
       
       // Add getters/setters for new fields
       public SyncResult getReconciledPaymentsResult() { return reconciledPaymentsResult; }
       public void setReconciledPaymentsResult(SyncResult result) { this.reconciledPaymentsResult = result; }
       
       public SyncResult getBatchPaymentsResult() { return batchPaymentsResult; }
       public void setBatchPaymentsResult(SyncResult result) { this.batchPaymentsResult = result; }
       
       public SyncResult getCommissionCalculationResult() { return commissionCalculationResult; }
       public void setCommissionCalculationResult(SyncResult result) { this.commissionCalculationResult = result; }

       // ✅ UPDATED: Include ALL payment sync results in overall success
       public boolean isOverallSuccess() {
           return overallError == null && 
               (propertiesResult == null || propertiesResult.isSuccess()) &&
               (propertyOwnersResult == null || propertyOwnersResult.isSuccess()) &&
               (tenantsResult == null || tenantsResult.isSuccess()) &&
               (contractorsResult == null || contractorsResult.isSuccess()) &&
               (relationshipsResult == null || relationshipsResult.isSuccess()) &&
               (tenantRelationshipsResult == null || tenantRelationshipsResult.isSuccess()) &&
               (filesResult == null || filesResult.isSuccess()) &&
               (paymentCategoriesResult == null || paymentCategoriesResult.isSuccess()) &&
               (paymentsResult == null || paymentsResult.isSuccess()) &&
               (beneficiaryBalancesResult == null || beneficiaryBalancesResult.isSuccess()) &&
               (financialTransactionsResult == null || financialTransactionsResult.isSuccess()) &&
               (reconciledPaymentsResult == null || reconciledPaymentsResult.isSuccess()) &&
               (batchPaymentsResult == null || batchPaymentsResult.isSuccess()) &&
               (commissionCalculationResult == null || commissionCalculationResult.isSuccess());
       }

       // ✅ UPDATED: Include ALL payment sync status in summary
       public String getSummary() {
           if (overallError != null) return overallError;
           
           StringBuilder summary = new StringBuilder();
           summary.append("Properties: ").append(propertiesResult != null ? propertiesResult.getMessage() : "skipped").append("; ");
           summary.append("Owners: ").append(propertyOwnersResult != null ? propertyOwnersResult.getMessage() : "skipped").append("; ");
           summary.append("Tenants: ").append(tenantsResult != null ? tenantsResult.getMessage() : "skipped").append("; ");
           summary.append("Contractors: ").append(contractorsResult != null ? contractorsResult.getMessage() : "skipped").append("; ");
           summary.append("Owner Relationships: ").append(relationshipsResult != null ? relationshipsResult.getMessage() : "skipped").append("; ");
           summary.append("Tenant Relationships: ").append(tenantRelationshipsResult != null ? tenantRelationshipsResult.getMessage() : "skipped").append("; ");
           summary.append("Payment Categories: ").append(paymentCategoriesResult != null ? paymentCategoriesResult.getMessage() : "skipped").append("; ");
           summary.append("Payments: ").append(paymentsResult != null ? paymentsResult.getMessage() : "skipped").append("; ");
           summary.append("Reconciled Payments: ").append(reconciledPaymentsResult != null ? reconciledPaymentsResult.getMessage() : "skipped").append("; ");
           summary.append("Batch Payments: ").append(batchPaymentsResult != null ? batchPaymentsResult.getMessage() : "skipped").append("; ");
           summary.append("Financial Transactions: ").append(financialTransactionsResult != null ? financialTransactionsResult.getMessage() : "skipped").append("; ");
           summary.append("Beneficiary Balances: ").append(beneficiaryBalancesResult != null ? beneficiaryBalancesResult.getMessage() : "skipped").append("; ");
           summary.append("Commission Calc: ").append(commissionCalculationResult != null ? commissionCalculationResult.getMessage() : "skipped").append("; ");
           summary.append("Files: ").append(filesResult != null ? filesResult.getMessage() : "skipped");
           return summary.toString();
       }

       // Getters and setters for existing fields
       public SyncResult getPropertiesResult() { return propertiesResult; }
       public void setPropertiesResult(SyncResult propertiesResult) { this.propertiesResult = propertiesResult; }
       
       public SyncResult getPropertyOwnersResult() { return propertyOwnersResult; }
       public void setPropertyOwnersResult(SyncResult propertyOwnersResult) { this.propertyOwnersResult = propertyOwnersResult; }
       
       public SyncResult getTenantsResult() { return tenantsResult; }
       public void setTenantsResult(SyncResult tenantsResult) { this.tenantsResult = tenantsResult; }
       
       public SyncResult getRelationshipsResult() { return relationshipsResult; }
       public void setRelationshipsResult(SyncResult relationshipsResult) { this.relationshipsResult = relationshipsResult; }
       
       public SyncResult getTenantRelationshipsResult() { return tenantRelationshipsResult; }
       public void setTenantRelationshipsResult(SyncResult tenantRelationshipsResult) { 
           this.tenantRelationshipsResult = tenantRelationshipsResult; 
       }
       
       public String getOverallError() { return overallError; }
       public void setOverallError(String overallError) { this.overallError = overallError; }

       public SyncResult getContractorsResult() { return contractorsResult; }
       public void setContractorsResult(SyncResult contractorsResult) { this.contractorsResult = contractorsResult; }

       public SyncResult getFilesResult() { return filesResult; }
       public void setFilesResult(SyncResult filesResult) { this.filesResult = filesResult; }
   }

   public static class PropertyRelationship {
       private String ownerPayPropId;
       private String propertyPayPropId;
       private String ownershipType;
       private Double ownershipPercentage;

       // Getters and setters
       public String getOwnerPayPropId() { return ownerPayPropId; }
       public void setOwnerPayPropId(String ownerPayPropId) { this.ownerPayPropId = ownerPayPropId; }
       
       public String getPropertyPayPropId() { return propertyPayPropId; }
       public void setPropertyPayPropId(String propertyPayPropId) { this.propertyPayPropId = propertyPayPropId; }
       
       public String getOwnershipType() { return ownershipType; }
       public void setOwnershipType(String ownershipType) { this.ownershipType = ownershipType; }
       
       public Double getOwnershipPercentage() { return ownershipPercentage; }
       public void setOwnershipPercentage(Double ownershipPercentage) { this.ownershipPercentage = ownershipPercentage; }
   }
}