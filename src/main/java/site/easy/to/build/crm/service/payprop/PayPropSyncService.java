// Updated PayPropSyncService.java - OAuth2 Integration
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.TenantService;
import site.easy.to.build.crm.service.property.PropertyOwnerService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropSyncService {

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
        if (property == null || !property.isReadyForPayPropSync()) {
            throw new IllegalArgumentException("Property not ready for sync");
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
                propertyService.markPropertyAsSynced(propertyId, payPropId);
                
                System.out.println("✅ Property synced successfully! PayProp ID: " + payPropId);
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
        if (property == null || !property.isPayPropSynced()) {
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
            
            System.out.println("✅ Property updated in PayProp: " + property.getPayPropId());
            
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
        
        // Validate tenant is ready for PayProp sync
        if (!isTenanReadyForSync(tenant)) {
            throw new IllegalArgumentException("Tenant not ready for sync - missing required fields");
        }
        
        try {
            PayPropTenantDTO dto = convertTenantToPayPropFormat(tenant);
            
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<PayPropTenantDTO> request = new HttpEntity<>(dto, headers);
            
            System.out.println("👤 Syncing tenant to PayProp: " + tenant.getFullName());
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                payPropApiBase + "/entity/tenant", 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String payPropId = (String) response.getBody().get("id");
                tenant.setPayPropId(payPropId);
                tenantService.save(tenant);
                
                System.out.println("✅ Tenant synced successfully! PayProp ID: " + payPropId);
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
            
            System.out.println("✅ Tenant updated in PayProp: " + tenant.getPayPropId());
            
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
        
        // Validate owner is ready for PayProp sync
        if (!isBeneficiaryReadyForSync(owner)) {
            throw new IllegalArgumentException("Beneficiary not ready for sync - missing required fields");
        }
        
        try {
            PayPropBeneficiaryDTO dto = convertBeneficiaryToPayPropFormat(owner);
            
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<PayPropBeneficiaryDTO> request = new HttpEntity<>(dto, headers);
            
            System.out.println("🏦 Syncing beneficiary to PayProp: " + owner.getFullName());
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                payPropApiBase + "/entity/beneficiary", 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String payPropId = (String) response.getBody().get("id");
                owner.setPayPropId(payPropId);
                propertyOwnerService.save(owner);
                
                System.out.println("✅ Beneficiary synced successfully! PayProp ID: " + payPropId);
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
        settings.setEnable_payments(property.getEnablePaymentsAsBoolean());
        settings.setHold_owner_funds(property.getHoldOwnerFundsAsBoolean());
        settings.setMonthly_payment(property.getMonthlyPayment());
        settings.setMinimum_balance(property.getPropertyAccountMinimumBalance());
        settings.setListing_from(property.getListedFrom());
        settings.setListing_to(property.getListedUntil());
        dto.setSettings(settings);
        
        return dto;
    }
    
    private PayPropTenantDTO convertTenantToPayPropFormat(Tenant tenant) {
        PayPropTenantDTO dto = new PayPropTenantDTO();
        
        // Account type and conditional fields
        dto.setAccount_type(tenant.getAccountType().getValue());
        
        if (tenant.getAccountType() == AccountType.individual) {
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
        dto.setNotify_email(tenant.getNotifyEmailAsBoolean());
        dto.setNotify_sms(tenant.getNotifyTextAsBoolean());
        
        // Address
        PayPropAddressDTO address = new PayPropAddressDTO();
        address.setAddress_line_1(tenant.getAddressLine1());
        address.setAddress_line_2(tenant.getAddressLine2());
        address.setAddress_line_3(tenant.getAddressLine3());
        address.setCity(tenant.getCity());
        address.setPostal_code(tenant.getPostcode());
        address.setCountry_code(tenant.getCountry());
        dto.setAddress(address);
        
        // Bank account (if present)
        if (tenant.getHasBankAccount() != null && tenant.getHasBankAccount()) {
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
        
        // Account type and conditional fields
        dto.setAccount_type(owner.getAccountType().getValue());
        dto.setPayment_method(owner.getPaymentMethod().getPayPropCode());
        
        if (owner.getAccountType() == AccountType.individual) {
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
        email.setEnabled(owner.getEmailEnabled());
        email.setPayment_advice(owner.getPaymentAdviceEnabled());
        communication.setEmail(email);
        dto.setCommunication_preferences(communication);
        
        // Address (required for international payments and cheque)
        if (owner.getPaymentMethod() == PaymentMethod.international || 
            owner.getPaymentMethod() == PaymentMethod.cheque) {
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
        
        if (owner.getPaymentMethod() == PaymentMethod.local) {
            bankAccount.setAccount_number(owner.getBankAccountNumber());
            bankAccount.setBranch_code(owner.getBranchCode());
            bankAccount.setBank_name(owner.getBankName());
            bankAccount.setBranch_name(owner.getBranchName());
        } else if (owner.getPaymentMethod() == PaymentMethod.international) {
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
    
    // ===== VALIDATION METHODS =====
    
    private boolean isTenanReadyForSync(Tenant tenant) {
        // Check account type specific requirements
        if (tenant.getAccountType() == AccountType.individual) {
            if (tenant.getFirstName() == null || tenant.getFirstName().trim().isEmpty() ||
                tenant.getLastName() == null || tenant.getLastName().trim().isEmpty()) {
                return false;
            }
        } else {
            if (tenant.getBusinessName() == null || tenant.getBusinessName().trim().isEmpty()) {
                return false;
            }
        }
        
        // Email is optional but if bank account is required, validate it
        if (tenant.getHasBankAccount() != null && tenant.getHasBankAccount()) {
            return tenant.getAccountName() != null && 
                   tenant.getAccountNumber() != null && 
                   tenant.getSortCode() != null;
        }
        
        return true;
    }
    
    private boolean isBeneficiaryReadyForSync(PropertyOwner owner) {
        // Check account type specific requirements
        if (owner.getAccountType() == AccountType.individual) {
            if (owner.getFirstName() == null || owner.getFirstName().trim().isEmpty() ||
                owner.getLastName() == null || owner.getLastName().trim().isEmpty()) {
                return false;
            }
        } else {
            if (owner.getBusinessName() == null || owner.getBusinessName().trim().isEmpty()) {
                return false;
            }
        }
        
        // Validate payment method specific requirements
        if (owner.getPaymentMethod() == PaymentMethod.international) {
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
        }
        
        if (owner.getPaymentMethod() == PaymentMethod.local) {
            return owner.getBankAccountName() != null && 
                   owner.getBankAccountNumber() != null && 
                   owner.getBranchCode() != null;
        }
        
        return true;
    }
    
    // ===== UTILITY METHODS =====
    
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
    
    // ===== BULK SYNC METHODS =====
    
    public void syncAllReadyProperties() {
        if (!oAuth2Service.hasValidTokens()) {
            throw new IllegalStateException("No valid OAuth2 tokens. Please authorize PayProp first.");
        }
        
        List<Property> readyProperties = propertyService.findPropertiesReadyForSync();
        System.out.println("📋 Found " + readyProperties.size() + " properties ready for sync");
        
        int successCount = 0;
        int errorCount = 0;
        
        for (Property property : readyProperties) {
            try {
                String payPropId = syncPropertyToPayProp(property.getId());
                System.out.println("✅ Successfully synced property " + property.getId() + " -> " + payPropId);
                successCount++;
            } catch (Exception e) {
                System.err.println("❌ Failed to sync property " + property.getId() + ": " + e.getMessage());
                errorCount++;
            }
        }
        
        System.out.println("🏁 Property sync completed. Success: " + successCount + ", Errors: " + errorCount);
    }
    
    public void syncAllReadyTenants() {
        if (!oAuth2Service.hasValidTokens()) {
            throw new IllegalStateException("No valid OAuth2 tokens. Please authorize PayProp first.");
        }
        
        List<Tenant> readyTenants = tenantService.findTenantsReadyForPayPropSync();
        System.out.println("📋 Found " + readyTenants.size() + " tenants ready for sync");
        
        int successCount = 0;
        int errorCount = 0;
        
        for (Tenant tenant : readyTenants) {
            try {
                String payPropId = syncTenantToPayProp(tenant.getId());
                System.out.println("✅ Successfully synced tenant " + tenant.getId() + " -> " + payPropId);
                successCount++;
            } catch (Exception e) {
                System.err.println("❌ Failed to sync tenant " + tenant.getId() + ": " + e.getMessage());
                errorCount++;
            }
        }
        
        System.out.println("🏁 Tenant sync completed. Success: " + successCount + ", Errors: " + errorCount);
    }
    
    public void syncAllReadyBeneficiaries() {
        if (!oAuth2Service.hasValidTokens()) {
            throw new IllegalStateException("No valid OAuth2 tokens. Please authorize PayProp first.");
        }
        
        List<PropertyOwner> readyOwners = propertyOwnerService.findPropertyOwnersReadyForSync();
        System.out.println("📋 Found " + readyOwners.size() + " beneficiaries ready for sync");
        
        int successCount = 0;
        int errorCount = 0;
        
        for (PropertyOwner owner : readyOwners) {
            try {
                String payPropId = syncBeneficiaryToPayProp(owner.getId());
                System.out.println("✅ Successfully synced beneficiary " + owner.getId() + " -> " + payPropId);
                successCount++;
            } catch (Exception e) {
                System.err.println("❌ Failed to sync beneficiary " + owner.getId() + ": " + e.getMessage());
                errorCount++;
            }
        }
        
        System.out.println("🏁 Beneficiary sync completed. Success: " + successCount + ", Errors: " + errorCount);
    }
    
    public void checkSyncStatus() {
        System.out.println("=== PayProp OAuth2 Sync Status ===");
        System.out.println("OAuth2 Status: " + (oAuth2Service.hasValidTokens() ? "✅ Authorized" : "❌ Not Authorized"));
        
        if (oAuth2Service.hasValidTokens()) {
            PayPropOAuth2Service.PayPropTokens tokens = oAuth2Service.getCurrentTokens();
            System.out.println("Token Expires: " + tokens.getExpiresAt());
            System.out.println("Scopes: " + tokens.getScopes());
        }
        
        long totalProperties = propertyService.getTotalProperties();
        List<Property> needsSync = propertyService.findPropertiesNeedingSync();
        List<Property> synced = propertyService.findPropertiesByPayPropSyncStatus(true);
        List<Property> readyForSync = propertyService.findPropertiesReadyForSync();
        
        long totalTenants = tenantService.getTotalTenants();
        List<Tenant> tenantsNeedingSync = tenantService.findByPayPropIdIsNull();
        List<Tenant> tenantsSynced = tenantService.findByPayPropIdIsNotNull();
        
        System.out.println();
        System.out.println("PROPERTIES:");
        System.out.println("  Total: " + totalProperties);
        System.out.println("  Needs Sync: " + needsSync.size());
        System.out.println("  Already Synced: " + synced.size());
        System.out.println("  Ready for Sync: " + readyForSync.size());
        System.out.println();
        System.out.println("TENANTS:");
        System.out.println("  Total: " + totalTenants);
        System.out.println("  Needs Sync: " + tenantsNeedingSync.size());
        System.out.println("  Already Synced: " + tenantsSynced.size());
    }
    
    // Export result wrapper
    public static class PayPropExportResult {
        private List<Map<String, Object>> items;
        private Map<String, Object> pagination;
        
        public List<Map<String, Object>> getItems() { return items; }
        public void setItems(List<Map<String, Object>> items) { this.items = items; }
        
        public Map<String, Object> getPagination() { return pagination; }
        public void setPagination(Map<String, Object> pagination) { this.pagination = pagination; }
    }
}