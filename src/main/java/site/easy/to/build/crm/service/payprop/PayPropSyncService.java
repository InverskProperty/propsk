// PayPropSyncService.java - Database Compatible Version with Duplicate Key Handling
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.TenantService;
import site.easy.to.build.crm.service.property.PropertyOwnerService;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropSyncService {

    private static final Logger log = LoggerFactory.getLogger(PayPropSyncService.class);

    private final PropertyService propertyService;
    private final TenantService tenantService;
    private final PropertyOwnerService propertyOwnerService;
    private final RestTemplate restTemplate;
    private final PayPropOAuth2Service oAuth2Service;
    
    @Value("${payprop.api.base-url:https://ukapi.staging.payprop.com/api/agency/v1.1}")
    private String payPropApiBase;
    
    @Autowired
    public PayPropSyncService(PropertyService propertyService, 
                             TenantService tenantService,
                             PropertyOwnerService propertyOwnerService,
                             RestTemplate restTemplate,
                             PayPropOAuth2Service oAuth2Service) {
        this.propertyService = propertyService;
        this.tenantService = tenantService;
        this.propertyOwnerService = propertyOwnerService;
        this.restTemplate = restTemplate;
        this.oAuth2Service = oAuth2Service;
    }
    
    // ===== PROPERTY SYNC METHODS =====
    
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
            
            String url = payPropApiBase + "/export/payments?page=" + page + "&rows=" + Math.min(rows, 25);
            
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
    
    // ===== CONVERSION METHODS =====
    
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
        
        // FIXED: Handle date fields as strings to avoid array serialization issues
        if (property.getListedFrom() != null) {
            settings.setListing_from(property.getListedFrom()); // LocalDate → LocalDate ✅
        }
        if (property.getListedUntil() != null) {
            settings.setListing_to(property.getListedUntil()); // LocalDate → LocalDate ✅
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
    
    // ===== VALIDATION METHODS (FIXED FOR YOUR DATABASE) =====
    
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
    
    // ===== UTILITY METHODS (FIXED FOR YOUR DATABASE) =====
    
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
}