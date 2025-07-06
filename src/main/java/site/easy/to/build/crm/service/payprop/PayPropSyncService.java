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
    
    // ===== TENANT SYNC METHODS =====
    
    public String syncTenantToPayProp(Long tenantId) {
        Tenant tenant = tenantService.findById(tenantId);
        if (tenant == null) {
            throw new IllegalArgumentException("Tenant not found");
        }
        
        // FIXED: Use your actual validation method
        if (!isValidForPayPropSync(tenant)) {
            throw new IllegalArgumentException("Tenant not ready for sync - missing required fields");
        }
        
        try {
            PayPropTenantDTO dto = convertTenantToPayPropFormat(tenant);
            
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<PayPropTenantDTO> request = new HttpEntity<>(dto, headers);
            
            System.out.println("👤 Syncing tenant to PayProp: " + tenant.getFirstName() + " " + tenant.getLastName());
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                payPropApiBase + "/entity/tenant", 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String payPropId = (String) response.getBody().get("id");
                
                // FIXED: Update tenant with PayProp ID using duplicate key handling
                tenant.setPayPropId(payPropId);
                try {
                    tenantService.save(tenant);
                    System.out.println("✅ Tenant synced successfully! PayProp ID: " + payPropId);
                } catch (DataIntegrityViolationException e) {
                    log.warn("Tenant with PayProp ID {} already exists when saving sync result, skipping save", payPropId);
                    System.out.println("⚠️ Tenant synced to PayProp but already exists locally with PayProp ID: " + payPropId);
                }
                
                return payPropId;
            }
            
            throw new RuntimeException("Failed to create tenant in PayProp");
            
        } catch (HttpClientErrorException e) {
            System.err.println("❌ PayProp API error: " + e.getResponseBodyAsString());
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            System.err.println("❌ Tenant sync failed: " + e.getMessage());
            throw new RuntimeException("Tenant sync failed", e);
        }
    }
    
    public void updateTenantInPayProp(Long tenantId) {
        Tenant tenant = tenantService.findById(tenantId);
        if (tenant == null || tenant.getPayPropId() == null) {
            throw new IllegalArgumentException("Tenant not synced with PayProp");
        }
        
        try {
            PayPropTenantDTO dto = convertTenantToPayPropFormat(tenant);
            
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<PayPropTenantDTO> request = new HttpEntity<>(dto, headers);
            
            restTemplate.put(
                payPropApiBase + "/entity/tenant/" + tenant.getPayPropId(), 
                request
            );
            
            // FIXED: Add duplicate key handling for update save
            try {
                tenantService.save(tenant);
                System.out.println("✅ Tenant updated in PayProp: " + tenant.getPayPropId());
            } catch (DataIntegrityViolationException e) {
                log.warn("Tenant with PayProp ID {} already exists when saving update, skipping save", tenant.getPayPropId());
                System.out.println("✅ Tenant updated in PayProp (local save skipped due to duplicate): " + tenant.getPayPropId());
            }
            
        } catch (HttpClientErrorException e) {
            System.err.println("❌ Failed to update tenant in PayProp: " + e.getResponseBodyAsString());
            throw new RuntimeException("Failed to update tenant in PayProp: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("Tenant update failed", e);
        }
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
        dto.setCustomer_id(property.getCustomerId());
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
        dto.setAddress(address);
        
        // Convert settings to nested structure
        PayPropSettingsDTO settings = new PayPropSettingsDTO();
        
        // FIXED: Handle your actual boolean field conversion safely
        try {
            Boolean enablePayments = convertYNToBoolean(property.getEnablePayments());
            if (enablePayments != null) {
                settings.setEnable_payments(enablePayments);
            }
        } catch (Exception e) {
            System.err.println("Could not convert enable_payments: " + e.getMessage());
        }
        
        try {
            Boolean holdOwnerFunds = convertYNToBoolean(property.getHoldOwnerFunds());
            if (holdOwnerFunds != null) {
                settings.setHold_owner_funds(holdOwnerFunds);
            }
        } catch (Exception e) {
            System.err.println("Could not convert hold_owner_funds: " + e.getMessage());
        }
        settings.setMonthly_payment(property.getMonthlyPayment());
        settings.setMinimum_balance(property.getPropertyAccountMinimumBalance());
        settings.setListing_from(property.getListedFrom());
        settings.setListing_to(property.getListedUntil());
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
        
        // FIXED: Simple assignment - DTO expects Boolean, convertYNToBoolean returns Boolean
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
                    return false;
                }
            } else if ("business".equals(accountType)) {
                if (tenant.getBusinessName() == null || tenant.getBusinessName().trim().isEmpty()) {
                    return false;
                }
            }
        }
        
        // FIXED: Handle bank account validation for your bit(1) field
        Boolean hasBankAccount = convertBitToBoolean(tenant.getHasBankAccount());
        if (Boolean.TRUE.equals(hasBankAccount)) {
            return tenant.getAccountName() != null && 
                   tenant.getAccountNumber() != null && 
                   tenant.getSortCode() != null;
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
                    return false;
                }
            } else if ("business".equals(accountType)) {
                if (owner.getBusinessName() == null || owner.getBusinessName().trim().isEmpty()) {
                    return false;
                }
            }
        }
        
        // Validate payment method specific requirements
        if (owner.getPaymentMethod() != null) {
            String paymentMethod = owner.getPaymentMethod().toString().toLowerCase();
            
            if ("international".equals(paymentMethod)) {
                // Address is required for international
                if (owner.getAddressLine1() == null || owner.getCity() == null || 
                    owner.getState() == null || owner.getPostalCode() == null) {
                    return false;
                }
                
                // Either IBAN or account number + SWIFT required
                boolean hasIban = owner.getIban() != null && !owner.getIban().trim().isEmpty();
                boolean hasAccountAndSwift = owner.getInternationalAccountNumber() != null && 
                                           owner.getSwiftCode() != null;
                
                if (!hasIban && !hasAccountAndSwift) {
                    return false;
                }
            } else if ("local".equals(paymentMethod)) {
                return owner.getBankAccountName() != null && 
                       owner.getBankAccountNumber() != null && 
                       owner.getBranchCode() != null;
            }
        }
        
        return true;
    }
    
    // ===== UTILITY METHODS (FIXED FOR YOUR DATABASE) =====
    
    /**
     * Convert Y/N enum to boolean - FIXED to ensure Boolean return type
     */
    private Boolean convertYNToBoolean(String ynValue) {
        if (ynValue == null) return null;
        String trimmed = ynValue.trim().toUpperCase();
        return "Y".equals(trimmed) || "YES".equals(trimmed) || "TRUE".equals(trimmed);
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
    
    // ===== BULK SYNC METHODS WITH DUPLICATE KEY HANDLING AND SEPARATE TRANSACTIONS =====
    
    public void syncAllReadyProperties() {
        if (!oAuth2Service.hasValidTokens()) {
            throw new IllegalStateException("No valid OAuth2 tokens. Please authorize PayProp first.");
        }
        
        // FIXED: Use your actual repository method
        List<Property> readyProperties = propertyService.findAll().stream()
            .filter(p -> p.getPayPropId() == null) // Not yet synced
            .toList();
            
        System.out.println("📋 Found " + readyProperties.size() + " properties ready for sync");
        
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
    
    public void syncAllReadyTenants() {
        if (!oAuth2Service.hasValidTokens()) {
            throw new IllegalStateException("No valid OAuth2 tokens. Please authorize PayProp first.");
        }
        
        // FIXED: Use your actual repository method
        List<Tenant> readyTenants = tenantService.findAll().stream()
            .filter(t -> t.getPayPropId() == null) // Not yet synced
            .toList();
            
        System.out.println("📋 Found " + readyTenants.size() + " tenants ready for sync");
        
        int successCount = 0;
        int errorCount = 0;
        int duplicateCount = 0;
        
        for (Tenant tenant : readyTenants) {
            try {
                // Each tenant sync in its own transaction
                String payPropId = syncTenantToPayPropInSeparateTransaction(tenant.getId());
                System.out.println("✅ Successfully synced tenant " + tenant.getId() + " -> " + payPropId);
                successCount++;
            } catch (DataIntegrityViolationException e) {
                log.warn("Tenant {} already has PayProp ID during bulk sync, skipping", tenant.getId());
                duplicateCount++;
            } catch (Exception e) {
                System.err.println("❌ Failed to sync tenant " + tenant.getId() + ": " + e.getMessage());
                errorCount++;
            }
        }
        
        System.out.println("🏁 Tenant sync completed. Success: " + successCount + 
                          ", Errors: " + errorCount + ", Duplicates: " + duplicateCount);
    }
    
    /**
     * Sync tenant in separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String syncTenantToPayPropInSeparateTransaction(Long tenantId) {
        return syncTenantToPayProp(tenantId);
    }
    
    public void syncAllReadyBeneficiaries() {
        if (!oAuth2Service.hasValidTokens()) {
            throw new IllegalStateException("No valid OAuth2 tokens. Please authorize PayProp first.");
        }
        
        // FIXED: Use your actual repository method
        List<PropertyOwner> readyOwners = propertyOwnerService.findAll().stream()
            .filter(o -> o.getPayPropId() == null) // Not yet synced
            .toList();
            
        System.out.println("📋 Found " + readyOwners.size() + " beneficiaries ready for sync");
        
        int successCount = 0;
        int errorCount = 0;
        int duplicateCount = 0;
        
        for (PropertyOwner owner : readyOwners) {
            try {
                // Each beneficiary sync in its own transaction
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
            // FIXED: Use your actual repository methods
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
            
            System.out.println();
            System.out.println("PROPERTIES:");
            System.out.println("  Total: " + totalProperties);
            System.out.println("  Needs Sync: " + needsSync.size());
            System.out.println("  Already Synced: " + synced.size());
            System.out.println();
            System.out.println("TENANTS:");
            System.out.println("  Total: " + totalTenants);
            System.out.println("  Needs Sync: " + tenantsNeedingSync.size());
            System.out.println("  Already Synced: " + tenantsSynced.size());
            
        } catch (Exception e) {
            System.err.println("⚠️ Error checking entity status: " + e.getMessage());
            System.out.println("PROPERTIES: Unable to check (error occurred)");
            System.out.println("TENANTS: Unable to check (error occurred)");
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