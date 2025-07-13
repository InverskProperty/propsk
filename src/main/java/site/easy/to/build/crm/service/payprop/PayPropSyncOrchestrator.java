// PayPropSyncOrchestrator.java - Updated with Payment Sync Integration
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
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.payprop.PayPropSyncService.PayPropExportResult;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.assignment.CustomerPropertyAssignmentService;
import site.easy.to.build.crm.service.drive.CustomerDriveOrganizationService;
import site.easy.to.build.crm.service.drive.GoogleDriveFileService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.service.payprop.SyncResult;
import site.easy.to.build.crm.service.payprop.SyncResultType;

import java.math.BigDecimal;
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
                                 GoogleDriveFileService googleDriveFileService) {
       this.payPropSyncService = payPropSyncService;
       this.syncLogger = syncLogger;
       this.customerService = customerService;
       this.propertyService = propertyService;
       this.authenticationUtils = authenticationUtils;
       this.assignmentService = assignmentService;
       this.customerDriveOrganizationService = customerDriveOrganizationService;
       this.googleDriveFileService = googleDriveFileService;
   }

   // ===== MAIN SYNC ORCHESTRATION =====

   /**
    * Complete two-way synchronization using unified Customer entity with PAYMENT SYNC
    */
   public UnifiedSyncResult performUnifiedSync(OAuthUser oAuthUser, Long initiatedBy) {
       UnifiedSyncResult result = new UnifiedSyncResult();
       syncLogger.logSyncStart("UNIFIED_SYNC", initiatedBy);
       
       try {
           // Step 1: Sync Properties (foundation)
           result.setPropertiesResult(syncPropertiesFromPayProp(initiatedBy));
           
           // Step 2: Get payment relationships 
           Map<String, PropertyRelationship> relationships = extractRelationshipsFromPayments();
           
           // Step 3: Sync Property Owners as Customers
           result.setPropertyOwnersResult(syncPropertyOwnersAsCustomers(initiatedBy, relationships));
           
           // Step 4: Sync Tenants as Customers  
           result.setTenantsResult(syncTenantsAsCustomers(initiatedBy));

           // Step 5: Sync Contractors as Customers (standalone entities)
           result.setContractorsResult(syncContractorsAsCustomers(initiatedBy));

           // Step 6: Establish property assignments via junction table
           result.setRelationshipsResult(establishPropertyAssignments(relationships));

           // Step 7: Establish tenant relationships via junction table
           result.setTenantRelationshipsResult(establishTenantPropertyRelationships());

           // Step 7.5: Sync Payment Data (NEW)
           result.setPaymentCategoriesResult(syncPaymentCategories(initiatedBy));
           result.setPaymentsResult(syncPayments(initiatedBy));  
           result.setBeneficiaryBalancesResult(syncBeneficiaryBalances(initiatedBy));

           // Step 8: Sync PayProp files to Google Drive
           if (oAuthUser != null) {
               result.setFilesResult(syncPayPropFiles(oAuthUser, initiatedBy));
           } else {
               log.warn("⚠️ No OAuthUser provided - skipping file sync");
               result.setFilesResult(SyncResult.partial("File sync skipped - no OAuth user", Map.of()));
           }
           
           syncLogger.logSyncComplete("UNIFIED_SYNC", result.isOverallSuccess(), result.getSummary());
           
       } catch (Exception e) {
           syncLogger.logSyncError("UNIFIED_SYNC", e);
           result.setOverallError(e.getMessage());
       }
       
       return result;
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

           // Step 7.5: Sync Payment Data (NEW)
           result.setPaymentCategoriesResult(syncPaymentCategories(initiatedBy));
           result.setPaymentsResult(syncPayments(initiatedBy));  
           result.setBeneficiaryBalancesResult(syncBeneficiaryBalances(initiatedBy));

           // Step 8: Enhanced occupancy detection
           result.setOccupancyResult(detectOccupancyFromTenancies(initiatedBy));

           // Step 9: Sync PayProp files
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

   // ===== NEW PAYMENT SYNC METHODS =====

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
    * Step 7.5b: Sync all payments
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
       // Create the expected settings object from flattened PayProp data
       Map<String, Object> settings = new HashMap<>();
       
       // Map PayProp fields to settings structure
       if (payPropData.get("monthly_payment_required") != null) {
           settings.put("monthly_payment", payPropData.get("monthly_payment_required"));
       }
       
       if (payPropData.get("allow_payments") != null) {
           settings.put("enable_payments", payPropData.get("allow_payments"));
       }
       
       if (payPropData.get("hold_all_owner_funds") != null) {
           settings.put("hold_owner_funds", payPropData.get("hold_all_owner_funds"));
       }
       
       if (payPropData.get("property_account_minimum_balance") != null) {
           String minBalanceStr = (String) payPropData.get("property_account_minimum_balance");
           try {
               Double minBalance = Double.parseDouble(minBalanceStr);
               settings.put("minimum_balance", minBalance);
           } catch (NumberFormatException e) {
               settings.put("minimum_balance", 0.0);
           }
       }
       
       // Add default values for missing fields
       settings.putIfAbsent("monthly_payment", 0.0);
       settings.putIfAbsent("enable_payments", true);
       settings.putIfAbsent("hold_owner_funds", false);
       settings.putIfAbsent("minimum_balance", 0.0);
       settings.putIfAbsent("verify_payments", false);
       
       // Add the settings object to the property data
       payPropData.put("settings", settings);
       
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
       
       log.info("✅ Transformed property {} - Monthly rent: {}, Occupied: {}", 
               payPropData.get("id"), 
               settings.get("monthly_payment"), 
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
           Customer customer = createCustomerFromContractorData(beneficiaryData);
           customer.setCreatedAt(LocalDateTime.now());
           customerService.save(customer);
           return true;
       }
   }

   private Customer createCustomerFromContractorData(Map<String, Object> data) {
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
           return !googleDriveFileService.findByCustomerIdAndPayPropExternalId(
               customer.getCustomerId(), externalId).isEmpty();
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
           Customer customer = createCustomerFromBeneficiaryData(beneficiaryData, relationship);
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
           Customer customer = createCustomerFromTenantData(tenantData);
           customer.setCreatedAt(LocalDateTime.now());
           customerService.save(customer);
           return true;
       }
   }

   // ===== CUSTOMER MAPPING METHODS =====

   private Customer createCustomerFromBeneficiaryData(Map<String, Object> data, PropertyRelationship relationship) {
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
       
       return customer;
   }

   private Customer createCustomerFromTenantData(Map<String, Object> data) {
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
       
       // ✅ Settings mapping including monthly_payment
       Map<String, Object> settings = (Map<String, Object>) data.get("settings");
       if (settings != null) {
           // Monthly payment - this is the critical field you're missing!
           Object monthlyPayment = settings.get("monthly_payment");
           if (monthlyPayment instanceof Number) {
               property.setMonthlyPayment(new BigDecimal(monthlyPayment.toString()));
               log.info("✅ Mapped monthly_payment: {} for property {}", monthlyPayment, property.getPayPropId());
           }
           
           // Other settings fields
           Object enablePayments = settings.get("enable_payments");
           if (enablePayments instanceof Boolean) {
               property.setEnablePaymentsFromBoolean((Boolean) enablePayments);
           }
           
           Object holdOwnerFunds = settings.get("hold_owner_funds");
           if (holdOwnerFunds instanceof Boolean) {
               property.setHoldOwnerFundsFromBoolean((Boolean) holdOwnerFunds);
           }
           
           Object verifyPayments = settings.get("verify_payments");
           if (verifyPayments instanceof Boolean) {
               property.setVerifyPaymentsFromBoolean((Boolean) verifyPayments);
           }
           
           Object minimumBalance = settings.get("minimum_balance");
           if (minimumBalance instanceof Number) {
               property.setPropertyAccountMinimumBalance(new BigDecimal(minimumBalance.toString()));
           }
           
           // Date fields
           String listingFrom = (String) settings.get("listing_from");
           if (listingFrom != null) {
               try {
                   property.setListedFrom(LocalDate.parse(listingFrom));
               } catch (Exception e) {
                   log.warn("Could not parse listing_from date: {}", listingFrom);
               }
           }
           
           String listingTo = (String) settings.get("listing_to");
           if (listingTo != null) {
               try {
                   property.setListedUntil(LocalDate.parse(listingTo));
               } catch (Exception e) {
                   log.warn("Could not parse listing_to date: {}", listingTo);
               }
           }
       } else {
           log.info("ℹ️ No settings object found in PayProp data for property {} - using transformation", data.get("id"));
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
       
       // ✅ Update settings including monthly_payment
       Map<String, Object> settings = (Map<String, Object>) data.get("settings");
       if (settings != null) {
           // Monthly payment - this is the critical field you're missing!
           Object monthlyPayment = settings.get("monthly_payment");
           if (monthlyPayment instanceof Number) {
               property.setMonthlyPayment(new BigDecimal(monthlyPayment.toString()));
               log.info("✅ Updated monthly_payment: {} for property {}", monthlyPayment, property.getPayPropId());
           }
           
           // Other settings updates
           Object enablePayments = settings.get("enable_payments");
           if (enablePayments instanceof Boolean) {
               property.setEnablePaymentsFromBoolean((Boolean) enablePayments);
           }
           
           Object holdOwnerFunds = settings.get("hold_owner_funds");
           if (holdOwnerFunds instanceof Boolean) {
               property.setHoldOwnerFundsFromBoolean((Boolean) holdOwnerFunds);
           }
           
           Object verifyPayments = settings.get("verify_payments");
           if (verifyPayments instanceof Boolean) {
               property.setVerifyPaymentsFromBoolean((Boolean) verifyPayments);
           }
           
           Object minimumBalance = settings.get("minimum_balance");
           if (minimumBalance instanceof Number) {
               property.setPropertyAccountMinimumBalance(new BigDecimal(minimumBalance.toString()));
           }
           
           // Date fields
           String listingFrom = (String) settings.get("listing_from");
           if (listingFrom != null) {
               try {
                   property.setListedFrom(LocalDate.parse(listingFrom));
               } catch (Exception e) {
                   log.warn("Could not parse listing_from date: {}", listingFrom);
               }
           }
           
           String listingTo = (String) settings.get("listing_to");
           if (listingTo != null) {
               try {
                   property.setListedUntil(LocalDate.parse(listingTo));
               } catch (Exception e) {
                   log.warn("Could not parse listing_to date: {}", listingTo);
               }
           }
       } else {
           log.info("ℹ️ No settings object found in PayProp data during update for property {} - using transformation", property.getPayPropId());
       }
   }

   // ===== RESULT CLASSES =====

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
       
       // ✅ NEW PAYMENT SYNC RESULT FIELDS
       private SyncResult paymentCategoriesResult;
       private SyncResult paymentsResult;
       private SyncResult beneficiaryBalancesResult;

       public SyncResult getOccupancyResult() { return occupancyResult; }
       public void setOccupancyResult(SyncResult occupancyResult) { this.occupancyResult = occupancyResult; }

       // ✅ NEW PAYMENT SYNC GETTERS/SETTERS
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

       // ✅ UPDATED: Include payment sync results in overall success
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
               (beneficiaryBalancesResult == null || beneficiaryBalancesResult.isSuccess());
       }

       // ✅ UPDATED: Include payment sync status in summary
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
           summary.append("Beneficiary Balances: ").append(beneficiaryBalancesResult != null ? beneficiaryBalancesResult.getMessage() : "skipped").append("; ");
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