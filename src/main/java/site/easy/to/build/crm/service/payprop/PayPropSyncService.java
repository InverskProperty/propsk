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
import site.easy.to.build.crm.service.property.PropertyOwnerService; // Assuming this exists

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
    
    @Value("${payprop.api.base-url:https://uk.payprop.com/api/agency/v1.1}")
    private String payPropApiBase;
    
    @Value("${payprop.api.key}")
    private String apiKey;
    
    @Autowired
    public PayPropSyncService(PropertyService propertyService, 
                             TenantService tenantService,
                             PropertyOwnerService propertyOwnerService,
                             RestTemplate restTemplate) {
        this.propertyService = propertyService;
        this.tenantService = tenantService;
        this.propertyOwnerService = propertyOwnerService;
        this.restTemplate = restTemplate;
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
            
            // Make API call to PayProp
            HttpHeaders headers = createHeaders();
            HttpEntity<PayPropPropertyDTO> request = new HttpEntity<>(dto, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                payPropApiBase + "/entity/property", 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String payPropId = (String) response.getBody().get("id");
                propertyService.markPropertyAsSynced(propertyId, payPropId);
                return payPropId;
            }
            
            throw new RuntimeException("Failed to create property in PayProp");
            
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        }
    }
    
    public void updatePropertyInPayProp(Long propertyId) {
        Property property = propertyService.findById(propertyId);
        if (property == null || !property.isPayPropSynced()) {
            throw new IllegalArgumentException("Property not synced with PayProp");
        }
        
        try {
            PayPropPropertyDTO dto = convertPropertyToPayPropFormat(property);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<PayPropPropertyDTO> request = new HttpEntity<>(dto, headers);
            
            restTemplate.put(
                payPropApiBase + "/entity/property/" + property.getPayPropId(), 
                request
            );
            
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Failed to update property in PayProp: " + e.getResponseBodyAsString(), e);
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
            
            HttpHeaders headers = createHeaders();
            HttpEntity<PayPropTenantDTO> request = new HttpEntity<>(dto, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                payPropApiBase + "/entity/tenant", 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String payPropId = (String) response.getBody().get("id");
                tenant.setPayPropId(payPropId);
                tenantService.save(tenant);
                return payPropId;
            }
            
            throw new RuntimeException("Failed to create tenant in PayProp");
            
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        }
    }
    
    public void updateTenantInPayProp(Long tenantId) {
        Tenant tenant = tenantService.findById(tenantId);
        if (tenant == null || tenant.getPayPropId() == null) {
            throw new IllegalArgumentException("Tenant not synced with PayProp");
        }
        
        try {
            PayPropTenantDTO dto = convertTenantToPayPropFormat(tenant);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<PayPropTenantDTO> request = new HttpEntity<>(dto, headers);
            
            restTemplate.put(
                payPropApiBase + "/entity/tenant/" + tenant.getPayPropId(), 
                request
            );
            
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Failed to update tenant in PayProp: " + e.getResponseBodyAsString(), e);
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
            
            HttpHeaders headers = createHeaders();
            HttpEntity<PayPropBeneficiaryDTO> request = new HttpEntity<>(dto, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                payPropApiBase + "/entity/beneficiary", 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String payPropId = (String) response.getBody().get("id");
                owner.setPayPropId(payPropId);
                propertyOwnerService.save(owner);
                return payPropId;
            }
            
            throw new RuntimeException("Failed to create beneficiary in PayProp");
            
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
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
        
        if (tenant.getAccountType() == AccountType.INDIVIDUAL) {
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
        // FIXED: Changed from getCustomerId() to getPayPropCustomerId()
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
        
        if (owner.getAccountType() == AccountType.INDIVIDUAL) {
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
        dto.setCustomer_id(owner.getCustomerId());
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
        if (owner.getPaymentMethod() == PaymentMethod.INTERNATIONAL || 
            owner.getPaymentMethod() == PaymentMethod.CHEQUE) {
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
        
        if (owner.getPaymentMethod() == PaymentMethod.LOCAL) {
            bankAccount.setAccount_number(owner.getBankAccountNumber());
            bankAccount.setBranch_code(owner.getBranchCode());
            bankAccount.setBank_name(owner.getBankName());
            bankAccount.setBranch_name(owner.getBranchName());
        } else if (owner.getPaymentMethod() == PaymentMethod.INTERNATIONAL) {
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
        if (tenant.getAccountType() == AccountType.INDIVIDUAL) {
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
        if (owner.getAccountType() == AccountType.INDIVIDUAL) {
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
        if (owner.getPaymentMethod() == PaymentMethod.INTERNATIONAL) {
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
        
        if (owner.getPaymentMethod() == PaymentMethod.LOCAL) {
            return owner.getBankAccountName() != null && 
                   owner.getBankAccountNumber() != null && 
                   owner.getBranchCode() != null;
        }
        
        return true;
    }
    
    // ===== UTILITY METHODS =====
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "APIkey " + apiKey);
        return headers;
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
    
    public void syncAllReadyProperties() {
        List<Property> readyProperties = propertyService.findPropertiesReadyForSync();
        System.out.println("Found " + readyProperties.size() + " properties ready for sync");
        
        for (Property property : readyProperties) {
            try {
                String payPropId = syncPropertyToPayProp(property.getId());
                System.out.println("Successfully synced property " + property.getId() + " -> " + payPropId);
            } catch (Exception e) {
                System.err.println("Failed to sync property " + property.getId() + ": " + e.getMessage());
            }
        }
    }
    
    public void syncAllReadyTenants() {
        List<Tenant> readyTenants = tenantService.findTenantsReadyForPayPropSync();
        System.out.println("Found " + readyTenants.size() + " tenants ready for sync");
        
        for (Tenant tenant : readyTenants) {
            try {
                String payPropId = syncTenantToPayProp(tenant.getId());
                System.out.println("Successfully synced tenant " + tenant.getId() + " -> " + payPropId);
            } catch (Exception e) {
                System.err.println("Failed to sync tenant " + tenant.getId() + ": " + e.getMessage());
            }
        }
    }
    
    public void checkSyncStatus() {
        long totalProperties = propertyService.getTotalProperties();
        List<Property> needsSync = propertyService.findPropertiesNeedingSync();
        List<Property> synced = propertyService.findPropertiesByPayPropSyncStatus(true);
        List<Property> readyForSync = propertyService.findPropertiesReadyForSync();
        
        long totalTenants = tenantService.getTotalTenants();
        List<Tenant> tenantsNeedingSync = tenantService.findByPayPropIdIsNull();
        List<Tenant> tenantsSynced = tenantService.findByPayPropIdIsNotNull();
        
        System.out.println("=== PayProp Sync Status ===");
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
}

// ===== ADDITIONAL DTO CLASSES =====

class PayPropTenantDTO {
    private String account_type;
    private String first_name;
    private String last_name;
    private String business_name;
    private String email_address;
    private String mobile_number;
    private String phone;
    private String fax;
    private String customer_id;
    private String customer_reference;
    private String comment;
    private LocalDate date_of_birth;
    private String id_number;
    private String vat_number;
    private Boolean notify_email;
    private Boolean notify_sms;
    private PayPropAddressDTO address;
    private PayPropBankAccountDTO bank_account;
    private Boolean has_bank_account;
    
    // Getters and setters
    public String getAccount_type() { return account_type; }
    public void setAccount_type(String account_type) { this.account_type = account_type; }
    
    public String getFirst_name() { return first_name; }
    public void setFirst_name(String first_name) { this.first_name = first_name; }
    
    public String getLast_name() { return last_name; }
    public void setLast_name(String last_name) { this.last_name = last_name; }
    
    public String getBusiness_name() { return business_name; }
    public void setBusiness_name(String business_name) { this.business_name = business_name; }
    
    public String getEmail_address() { return email_address; }
    public void setEmail_address(String email_address) { this.email_address = email_address; }
    
    public String getMobile_number() { return mobile_number; }
    public void setMobile_number(String mobile_number) { this.mobile_number = mobile_number; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
    public String getFax() { return fax; }
    public void setFax(String fax) { this.fax = fax; }
    
    public String getCustomer_id() { return customer_id; }
    public void setCustomer_id(String customer_id) { this.customer_id = customer_id; }
    
    public String getCustomer_reference() { return customer_reference; }
    public void setCustomer_reference(String customer_reference) { this.customer_reference = customer_reference; }
    
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    
    public LocalDate getDate_of_birth() { return date_of_birth; }
    public void setDate_of_birth(LocalDate date_of_birth) { this.date_of_birth = date_of_birth; }
    
    public String getId_number() { return id_number; }
    public void setId_number(String id_number) { this.id_number = id_number; }
    
    public String getVat_number() { return vat_number; }
    public void setVat_number(String vat_number) { this.vat_number = vat_number; }
    
    public Boolean getNotify_email() { return notify_email; }
    public void setNotify_email(Boolean notify_email) { this.notify_email = notify_email; }
    
    public Boolean getNotify_sms() { return notify_sms; }
    public void setNotify_sms(Boolean notify_sms) { this.notify_sms = notify_sms; }
    
    public PayPropAddressDTO getAddress() { return address; }
    public void setAddress(PayPropAddressDTO address) { this.address = address; }
    
    public PayPropBankAccountDTO getBank_account() { return bank_account; }
    public void setBank_account(PayPropBankAccountDTO bank_account) { this.bank_account = bank_account; }
    
    public Boolean getHas_bank_account() { return has_bank_account; }
    public void setHas_bank_account(Boolean has_bank_account) { this.has_bank_account = has_bank_account; }
}

class PayPropBeneficiaryDTO {
    private String account_type;
    private String payment_method;
    private String first_name;
    private String last_name;
    private String business_name;
    private String email_address;
    private String mobile;
    private String phone;
    private String fax;
    private String customer_id;
    private String customer_reference;
    private String comment;
    private String id_number;
    private String vat_number;
    private PayPropAddressDTO address;
    private PayPropBankAccountDTO bank_account;
    private PayPropCommunicationDTO communication_preferences;
    
    // Getters and setters
    public String getAccount_type() { return account_type; }
    public void setAccount_type(String account_type) { this.account_type = account_type; }
    
    public String getPayment_method() { return payment_method; }
    public void setPayment_method(String payment_method) { this.payment_method = payment_method; }
    
    public String getFirst_name() { return first_name; }
    public void setFirst_name(String first_name) { this.first_name = first_name; }
    
    public String getLast_name() { return last_name; }
    public void setLast_name(String last_name) { this.last_name = last_name; }
    
    public String getBusiness_name() { return business_name; }
    public void setBusiness_name(String business_name) { this.business_name = business_name; }
    
    public String getEmail_address() { return email_address; }
    public void setEmail_address(String email_address) { this.email_address = email_address; }
    
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
    public String getFax() { return fax; }
    public void setFax(String fax) { this.fax = fax; }
    
    public String getCustomer_id() { return customer_id; }
    public void setCustomer_id(String customer_id) { this.customer_id = customer_id; }
    
    public String getCustomer_reference() { return customer_reference; }
    public void setCustomer_reference(String customer_reference) { this.customer_reference = customer_reference; }
    
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    
    public String getId_number() { return id_number; }
    public void setId_number(String id_number) { this.id_number = id_number; }
    
    public String getVat_number() { return vat_number; }
    public void setVat_number(String vat_number) { this.vat_number = vat_number; }
    
    public PayPropAddressDTO getAddress() { return address; }
    public void setAddress(PayPropAddressDTO address) { this.address = address; }
    
    public PayPropBankAccountDTO getBank_account() { return bank_account; }
    public void setBank_account(PayPropBankAccountDTO bank_account) { this.bank_account = bank_account; }
    
    public PayPropCommunicationDTO getCommunication_preferences() { return communication_preferences; }
    public void setCommunication_preferences(PayPropCommunicationDTO communication_preferences) { this.communication_preferences = communication_preferences; }
}

class PayPropBankAccountDTO {
    private String account_name;
    private String account_number;
    private String branch_code;
    private String bank_name;
    private String branch_name;
    private String iban;
    private String swift_code;
    private String country_code;
    
    // Getters and setters
    public String getAccount_name() { return account_name; }
    public void setAccount_name(String account_name) { this.account_name = account_name; }
    
    public String getAccount_number() { return account_number; }
    public void setAccount_number(String account_number) { this.account_number = account_number; }
    
    public String getBranch_code() { return branch_code; }
    public void setBranch_code(String branch_code) { this.branch_code = branch_code; }
    
    public String getBank_name() { return bank_name; }
    public void setBank_name(String bank_name) { this.bank_name = bank_name; }
    
    public String getBranch_name() { return branch_name; }
    public void setBranch_name(String branch_name) { this.branch_name = branch_name; }
    
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }
    
    public String getSwift_code() { return swift_code; }
    public void setSwift_code(String swift_code) { this.swift_code = swift_code; }
    
    public String getCountry_code() { return country_code; }
    public void setCountry_code(String country_code) { this.country_code = country_code; }
}

class PayPropCommunicationDTO {
    private PayPropEmailDTO email;
    
    public PayPropEmailDTO getEmail() { return email; }
    public void setEmail(PayPropEmailDTO email) { this.email = email; }
}

class PayPropEmailDTO {
    private Boolean enabled;
    private Boolean payment_advice;
    
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    
    public Boolean getPayment_advice() { return payment_advice; }
    public void setPayment_advice(Boolean payment_advice) { this.payment_advice = payment_advice; }
}

class PayPropPropertyDTO {
    private String name;
    private String customer_id;
    private String customer_reference;
    private String agent_name;
    private String notes;
    private PayPropAddressDTO address;
    private PayPropSettingsDTO settings;
    
    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getCustomer_id() { return customer_id; }
    public void setCustomer_id(String customer_id) { this.customer_id = customer_id; }
    
    public String getCustomer_reference() { return customer_reference; }
    public void setCustomer_reference(String customer_reference) { this.customer_reference = customer_reference; }
    
    public String getAgent_name() { return agent_name; }
    public void setAgent_name(String agent_name) { this.agent_name = agent_name; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public PayPropAddressDTO getAddress() { return address; }
    public void setAddress(PayPropAddressDTO address) { this.address = address; }
    
    public PayPropSettingsDTO getSettings() { return settings; }
    public void setSettings(PayPropSettingsDTO settings) { this.settings = settings; }
}

class PayPropAddressDTO {
    private String address_line_1;
    private String address_line_2;
    private String address_line_3;
    private String city;
    private String state;
    private String postal_code;
    private String country_code;
    
    // Getters and setters
    public String getAddress_line_1() { return address_line_1; }
    public void setAddress_line_1(String address_line_1) { this.address_line_1 = address_line_1; }
    
    public String getAddress_line_2() { return address_line_2; }
    public void setAddress_line_2(String address_line_2) { this.address_line_2 = address_line_2; }
    
    public String getAddress_line_3() { return address_line_3; }
    public void setAddress_line_3(String address_line_3) { this.address_line_3 = address_line_3; }
    
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    
    public String getPostal_code() { return postal_code; }
    public void setPostal_code(String postal_code) { this.postal_code = postal_code; }
    
    public String getCountry_code() { return country_code; }
    public void setCountry_code(String country_code) { this.country_code = country_code; }
}

class PayPropSettingsDTO {
    private Boolean enable_payments;
    private Boolean hold_owner_funds;
    private BigDecimal monthly_payment;
    private BigDecimal minimum_balance;
    private LocalDate listing_from;
    private LocalDate listing_to;
    
    // Getters and setters
    public Boolean getEnable_payments() { return enable_payments; }
    public void setEnable_payments(Boolean enable_payments) { this.enable_payments = enable_payments; }
    
    public Boolean getHold_owner_funds() { return hold_owner_funds; }
    public void setHold_owner_funds(Boolean hold_owner_funds) { this.hold_owner_funds = hold_owner_funds; }
    
    public BigDecimal getMonthly_payment() { return monthly_payment; }
    public void setMonthly_payment(BigDecimal monthly_payment) { this.monthly_payment = monthly_payment; }
    
    public BigDecimal getMinimum_balance() { return minimum_balance; }
    public void setMinimum_balance(BigDecimal minimum_balance) { this.minimum_balance = minimum_balance; }
    
    public LocalDate getListing_from() { return listing_from; }
    public void setListing_from(LocalDate listing_from) { this.listing_from = listing_from; }
    
    public LocalDate getListing_to() { return listing_to; }
    public void setListing_to(LocalDate listing_to) { this.listing_to = listing_to; }
}