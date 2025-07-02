package site.easy.to.build.crm.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.groups.Default;
import site.easy.to.build.crm.customValidations.customer.UniqueEmail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer")
public class Customer {

    public interface CustomerUpdateValidationGroupInclusion {}
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "name")
    @NotBlank(message = "Name is required", groups = {Default.class, CustomerUpdateValidationGroupInclusion.class})
    private String name;

    @Column(name = "email")
    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email format")
    @UniqueEmail
    private String email;

    @Column(name = "position")
    private String position;

    @Column(name = "phone")
    private String phone;

    @Column(name = "address")
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "country")
    @NotBlank(message = "Country is required", groups = {Default.class, CustomerUpdateValidationGroupInclusion.class})
    private String country;

    @Column(name = "description")
    private String description;

    @Column(name = "twitter")
    private String twitter;

    @Column(name = "facebook")
    private String facebook;

    @Column(name = "youtube")
    private String youtube;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable=false)
    @JsonIgnoreProperties("customer")
    private User user;

    @OneToOne
    @JoinColumn(name = "profile_id")
    @JsonIgnore
    private CustomerLoginInfo customerLoginInfo;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // EXISTING CUSTOMER TYPE CLASSIFICATION AND PAYPROP INTEGRATION FIELDS
    
    @Column(name = "customer_type", nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'REGULAR_CUSTOMER'")
    @Enumerated(EnumType.STRING)
    private CustomerType customerType = CustomerType.REGULAR_CUSTOMER;

    @Column(name = "payprop_entity_id", unique = true, length = 32)
    private String payPropEntityId;

    @Column(name = "payprop_customer_id", unique = true, length = 50) 
    private String payPropCustomerId;

    @Column(name = "payprop_synced", nullable = false)
    private Boolean payPropSynced = false;

    @Column(name = "payprop_last_sync")
    private LocalDateTime payPropLastSync;

    @Column(name = "is_property_owner")
    private Boolean isPropertyOwner = false;

    @Column(name = "is_tenant") 
    private Boolean isTenant = false;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id") 
    private Long entityId;

    @Column(name = "primary_entity")
    private Boolean primaryEntity;

    @Column(name = "is_contractor")
    private Boolean isContractor = false;

    // NEW PAYPROP-SPECIFIC FIELDS

    // PayProp Individual Fields
    @Column(name = "first_name", length = 50)
    private String firstName;

    @Column(name = "last_name", length = 50)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "id_number", length = 50)
    private String idNumber;

    @Column(name = "id_type", length = 20)
    private String idType;

    // PayProp Business Fields
    @Column(name = "business_name", length = 100)
    private String businessName;

    @Column(name = "registration_number", length = 50)
    private String registrationNumber;

    @Column(name = "vat_number", length = 20)
    private String vatNumber;

    // PayProp Contact Fields
    @Column(name = "mobile_number", length = 25)
    private String mobileNumber;

    @Column(name = "email_cc", columnDefinition = "TEXT")
    private String emailCc; // JSON array of CC emails

    // PayProp Address Fields
    @Column(name = "address_line_1", length = 100)
    private String addressLine1;

    @Column(name = "address_line_2", length = 100)
    private String addressLine2;

    @Column(name = "address_line_3", length = 100)
    private String addressLine3;

    @Column(name = "county", length = 50)
    private String county;

    @Column(name = "postcode", length = 20)
    private String postcode;

    @Column(name = "country_code", length = 2)
    private String countryCode = "GB";

    // PayProp Account Type
    @Column(name = "account_type")
    @Enumerated(EnumType.STRING)
    private AccountType accountType = AccountType.INDIVIDUAL;

    // PayProp Tenant Fields
    @Column(name = "invoice_lead_days")
    private Integer invoiceLeadDays = 0;

    @Column(name = "customer_reference", length = 50)
    private String customerReference;

    @Column(name = "notify_email", length = 1)
    private String notifyEmail = "Y";

    @Column(name = "notify_sms", length = 1)
    private String notifySms = "N";

    @Column(name = "has_bank_account")
    private Boolean hasBankAccount = false;

    // PayProp Property Owner Payment Fields
    @Column(name = "payment_method")
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Column(name = "bank_account_name", length = 100)
    private String bankAccountName;

    @Column(name = "bank_account_number", length = 20)
    private String bankAccountNumber;

    @Column(name = "bank_sort_code", length = 10)
    private String bankSortCode;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_branch_name", length = 100)
    private String bankBranchName;

    @Column(name = "bank_iban", length = 34)
    private String bankIban;

    @Column(name = "bank_swift_code", length = 11)
    private String bankSwiftCode;

    @Column(name = "international_account_number", length = 34)
    private String internationalAccountNumber;

    // Communication Preferences
    @Column(name = "communication_preferences", columnDefinition = "TEXT")
    private String communicationPreferences; // JSON

    // Property Assignment (for tenants)
    @Column(name = "assigned_property_id")
    private Long assignedPropertyId;

    @Column(name = "assignment_date")
    private LocalDateTime assignmentDate;

    @Column(name = "move_in_date")
    private LocalDate moveInDate;

    @Column(name = "move_out_date")
    private LocalDate moveOutDate;

    @Column(name = "monthly_rent", precision = 10, scale = 2)
    private BigDecimal monthlyRent;

    @Column(name = "deposit_amount", precision = 10, scale = 2)
    private BigDecimal depositAmount;

    // PayProp Sync Fields
    @Column(name = "payprop_entity_type", length = 20)
    private String payPropEntityType;

    @Column(name = "payprop_sync_status")
    @Enumerated(EnumType.STRING)
    private SyncStatus payPropSyncStatus = SyncStatus.PENDING;

    @Column(name = "payprop_last_sync_error", columnDefinition = "TEXT")
    private String payPropLastSyncError;

    @Column(name = "payprop_created_at")
    private LocalDateTime payPropCreatedAt;

    @Column(name = "payprop_updated_at")
    private LocalDateTime payPropUpdatedAt;

    // CONSTRUCTORS
    public Customer() {
    }

    public Customer(String name, String email, String position, String phone, String address, String city, String state, String country,
                    String description, String twitter, String facebook, String youtube, User user, CustomerLoginInfo customerLoginInfo,
                    LocalDateTime createdAt) {
        this.name = name;
        this.email = email;
        this.position = position;
        this.phone = phone;
        this.address = address;
        this.city = city;
        this.state = state;
        this.country = country;
        this.description = description;
        this.twitter = twitter;
        this.facebook = facebook;
        this.youtube = youtube;
        this.user = user;
        this.customerLoginInfo = customerLoginInfo;
        this.createdAt = createdAt;
    }

    // EXISTING GETTERS AND SETTERS (keeping all your existing ones)

    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTwitter() { return twitter; }
    public void setTwitter(String twitter) { this.twitter = twitter; }

    public String getFacebook() { return facebook; }
    public void setFacebook(String facebook) { this.facebook = facebook; }

    public String getYoutube() { return youtube; }
    public void setYoutube(String youtube) { this.youtube = youtube; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public CustomerLoginInfo getCustomerLoginInfo() { return customerLoginInfo; }
    public void setCustomerLoginInfo(CustomerLoginInfo customerLoginInfo) { this.customerLoginInfo = customerLoginInfo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public Boolean getPrimaryEntity() { return primaryEntity; }
    public void setPrimaryEntity(Boolean primaryEntity) { this.primaryEntity = primaryEntity; }

    // EXISTING CUSTOMER TYPE AND PAYPROP GETTERS/SETTERS

    public CustomerType getCustomerType() { return customerType; }
    public void setCustomerType(CustomerType customerType) { this.customerType = customerType; }

    public String getPayPropEntityId() { return payPropEntityId; }
    public void setPayPropEntityId(String payPropEntityId) { this.payPropEntityId = payPropEntityId; }

    public String getPayPropCustomerId() { return payPropCustomerId; }
    public void setPayPropCustomerId(String payPropCustomerId) { this.payPropCustomerId = payPropCustomerId; }

    public Boolean getPayPropSynced() { return payPropSynced; }
    public void setPayPropSynced(Boolean payPropSynced) { this.payPropSynced = payPropSynced; }

    public LocalDateTime getPayPropLastSync() { return payPropLastSync; }
    public void setPayPropLastSync(LocalDateTime payPropLastSync) { this.payPropLastSync = payPropLastSync; }

    public Boolean getIsPropertyOwner() { return isPropertyOwner; }
    public void setIsPropertyOwner(Boolean isPropertyOwner) { this.isPropertyOwner = isPropertyOwner; }

    public Boolean getIsTenant() { return isTenant; }
    public void setIsTenant(Boolean isTenant) { this.isTenant = isTenant; }

    public Boolean getIsContractor() { return isContractor; }
    public void setIsContractor(Boolean isContractor) { this.isContractor = isContractor; }

    // NEW PAYPROP FIELD GETTERS AND SETTERS

    // Individual Fields
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }

    public String getIdType() { return idType; }
    public void setIdType(String idType) { this.idType = idType; }

    // Business Fields
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public String getVatNumber() { return vatNumber; }
    public void setVatNumber(String vatNumber) { this.vatNumber = vatNumber; }

    // Contact Fields
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getEmailCc() { return emailCc; }
    public void setEmailCc(String emailCc) { this.emailCc = emailCc; }

    // Address Fields
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getAddressLine3() { return addressLine3; }
    public void setAddressLine3(String addressLine3) { this.addressLine3 = addressLine3; }

    public String getCounty() { return county; }
    public void setCounty(String county) { this.county = county; }

    public String getPostcode() { return postcode; }
    public void setPostcode(String postcode) { this.postcode = postcode; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    // Account Type
    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }

    // Tenant Fields
    public Integer getInvoiceLeadDays() { return invoiceLeadDays; }
    public void setInvoiceLeadDays(Integer invoiceLeadDays) { this.invoiceLeadDays = invoiceLeadDays; }

    public String getCustomerReference() { return customerReference; }
    public void setCustomerReference(String customerReference) { this.customerReference = customerReference; }

    public String getNotifyEmail() { return notifyEmail; }
    public void setNotifyEmail(String notifyEmail) { this.notifyEmail = notifyEmail; }

    public String getNotifySms() { return notifySms; }
    public void setNotifySms(String notifySms) { this.notifySms = notifySms; }

    public Boolean getHasBankAccount() { return hasBankAccount; }
    public void setHasBankAccount(Boolean hasBankAccount) { this.hasBankAccount = hasBankAccount; }

    // Property Owner Payment Fields
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getBankAccountName() { return bankAccountName; }
    public void setBankAccountName(String bankAccountName) { this.bankAccountName = bankAccountName; }

    public String getBankAccountNumber() { return bankAccountNumber; }
    public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }

    public String getBankSortCode() { return bankSortCode; }
    public void setBankSortCode(String bankSortCode) { this.bankSortCode = bankSortCode; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getBankBranchName() { return bankBranchName; }
    public void setBankBranchName(String bankBranchName) { this.bankBranchName = bankBranchName; }

    public String getBankIban() { return bankIban; }
    public void setBankIban(String bankIban) { this.bankIban = bankIban; }

    public String getBankSwiftCode() { return bankSwiftCode; }
    public void setBankSwiftCode(String bankSwiftCode) { this.bankSwiftCode = bankSwiftCode; }

    public String getInternationalAccountNumber() { return internationalAccountNumber; }
    public void setInternationalAccountNumber(String internationalAccountNumber) { this.internationalAccountNumber = internationalAccountNumber; }

    // Communication Preferences
    public String getCommunicationPreferences() { return communicationPreferences; }
    public void setCommunicationPreferences(String communicationPreferences) { this.communicationPreferences = communicationPreferences; }

    // Property Assignment
    public Long getAssignedPropertyId() { return assignedPropertyId; }
    public void setAssignedPropertyId(Long assignedPropertyId) { this.assignedPropertyId = assignedPropertyId; }

    public LocalDateTime getAssignmentDate() { return assignmentDate; }
    public void setAssignmentDate(LocalDateTime assignmentDate) { this.assignmentDate = assignmentDate; }

    public LocalDate getMoveInDate() { return moveInDate; }
    public void setMoveInDate(LocalDate moveInDate) { this.moveInDate = moveInDate; }

    public LocalDate getMoveOutDate() { return moveOutDate; }
    public void setMoveOutDate(LocalDate moveOutDate) { this.moveOutDate = moveOutDate; }

    public BigDecimal getMonthlyRent() { return monthlyRent; }
    public void setMonthlyRent(BigDecimal monthlyRent) { this.monthlyRent = monthlyRent; }

    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }

    // PayProp Sync Fields
    public String getPayPropEntityType() { return payPropEntityType; }
    public void setPayPropEntityType(String payPropEntityType) { this.payPropEntityType = payPropEntityType; }

    public SyncStatus getPayPropSyncStatus() { return payPropSyncStatus; }
    public void setPayPropSyncStatus(SyncStatus payPropSyncStatus) { this.payPropSyncStatus = payPropSyncStatus; }

    public String getPayPropLastSyncError() { return payPropLastSyncError; }
    public void setPayPropLastSyncError(String payPropLastSyncError) { this.payPropLastSyncError = payPropLastSyncError; }

    public LocalDateTime getPayPropCreatedAt() { return payPropCreatedAt; }
    public void setPayPropCreatedAt(LocalDateTime payPropCreatedAt) { this.payPropCreatedAt = payPropCreatedAt; }

    public LocalDateTime getPayPropUpdatedAt() { return payPropUpdatedAt; }
    public void setPayPropUpdatedAt(LocalDateTime payPropUpdatedAt) { this.payPropUpdatedAt = payPropUpdatedAt; }

    // HELPER METHODS FOR PAYPROP INTEGRATION (keeping existing)

    public boolean isPayPropEntity() {
        return customerType != null && customerType.isPayPropEntity();
    }

    public boolean needsPayPropSync() {
        return isPayPropEntity() && !Boolean.TRUE.equals(payPropSynced);
    }

    // HELPER METHODS FOR TYPE CHECKING (keeping existing)

    public boolean isOfType(CustomerType type) {
        return this.customerType == type;
    }

    public String getTypeDisplayName() {
        return customerType != null ? customerType.getDisplayName() : "Unknown";
    }

    // NEW PAYPROP VALIDATION METHODS

    public boolean isPayPropIndividualAccount() {
        return accountType == AccountType.INDIVIDUAL;
    }

    public boolean isPayPropBusinessAccount() {
        return accountType == AccountType.BUSINESS;
    }

    public boolean hasValidIndividualDetails() {
        return firstName != null && !firstName.trim().isEmpty() &&
               lastName != null && !lastName.trim().isEmpty();
    }

    public boolean hasValidBusinessDetails() {
        return businessName != null && !businessName.trim().isEmpty();
    }

    public boolean isReadyForPayPropSync() {
        if (!isPayPropEntity()) return false;
        
        // Check account type specific requirements
        if (isPayPropIndividualAccount()) {
            if (!hasValidIndividualDetails()) return false;
        } else if (isPayPropBusinessAccount()) {
            if (!hasValidBusinessDetails()) return false;
        }
        
        // Email is required for all PayProp entities
        if (email == null || email.trim().isEmpty()) return false;
        
        // Tenant-specific validation
        if (customerType == CustomerType.TENANT) {
            return email.length() <= 50; // PayProp tenant email limit
        }
        
        // Property owner specific validation
        if (customerType == CustomerType.PROPERTY_OWNER) {
            if (paymentMethod == null) return false;
            
            if (paymentMethod == PaymentMethod.LOCAL) {
                return bankAccountName != null && bankAccountNumber != null && bankSortCode != null;
            }
            
            if (paymentMethod == PaymentMethod.INTERNATIONAL) {
                boolean hasAddress = addressLine1 != null && city != null && countryCode != null;
                boolean hasIban = bankIban != null && !bankIban.trim().isEmpty();
                boolean hasAccountAndSwift = internationalAccountNumber != null && bankSwiftCode != null;
                return hasAddress && (hasIban || hasAccountAndSwift);
            }
        }
        
        return true;
    }

    public String getPayPropValidationError() {
        if (!isPayPropEntity()) return "Not a PayProp entity";
        
        if (isPayPropIndividualAccount() && !hasValidIndividualDetails()) {
            return "Missing first name or last name for individual account";
        }
        
        if (isPayPropBusinessAccount() && !hasValidBusinessDetails()) {
            return "Missing business name for business account";
        }
        
        if (email == null || email.trim().isEmpty()) {
            return "Email address is required";
        }
        
        if (customerType == CustomerType.TENANT && email.length() > 50) {
            return "Tenant email must be 50 characters or less";
        }
        
        if (customerType == CustomerType.PROPERTY_OWNER) {
            if (paymentMethod == null) {
                return "Payment method is required for property owners";
            }
            
            if (paymentMethod == PaymentMethod.LOCAL) {
                if (bankAccountName == null) return "Bank account name required for local payments";
                if (bankAccountNumber == null) return "Bank account number required for local payments";
                if (bankSortCode == null) return "Sort code required for local payments";
            }
            
            if (paymentMethod == PaymentMethod.INTERNATIONAL) {
                if (addressLine1 == null || city == null) return "Address required for international payments";
                if (bankIban == null && (internationalAccountNumber == null || bankSwiftCode == null)) {
                    return "IBAN or account number + SWIFT code required for international payments";
                }
            }
        }
        
        return null; // No validation errors
    }

    // UTILITY METHODS

    public String getFullName() {
        if (isPayPropBusinessAccount() && businessName != null) {
            return businessName;
        }
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return name; // Fallback to legacy name field
    }

    public String getPayPropDisplayName() {
        if (isPayPropBusinessAccount()) {
            return businessName != null ? businessName : name;
        }
        return getFullName();
    }

    public String getFullAddress() {
        StringBuilder address = new StringBuilder();
        if (addressLine1 != null && !addressLine1.trim().isEmpty()) {
            address.append(addressLine1);
        }
        if (addressLine2 != null && !addressLine2.trim().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(addressLine2);
        }
        if (addressLine3 != null && !addressLine3.trim().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(addressLine3);
        }
        if (city != null && !city.trim().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(city);
        }
        if (postcode != null && !postcode.trim().isEmpty()) {
            if (address.length() > 0) address.append(" ");
            address.append(postcode);
        }
        return address.toString();
    }

    public boolean hasValidBankDetails() {
        if (paymentMethod == null) return false;
        
        switch (paymentMethod) {
            case LOCAL:
                return bankAccountName != null && 
                       bankAccountNumber != null && 
                       bankSortCode != null;
            case INTERNATIONAL:
                boolean hasIban = bankIban != null && !bankIban.trim().isEmpty();
                boolean hasAccountAndSwift = internationalAccountNumber != null && 
                                           bankSwiftCode != null;
                return hasIban || hasAccountAndSwift;
            case CHEQUE:
                return true; // Cheque doesn't need bank details
            default:
                return false;
        }
    }

    // BOOLEAN HELPER METHODS FOR NOTIFICATIONS

    public Boolean getNotifyEmailAsBoolean() {
        return "Y".equalsIgnoreCase(notifyEmail);
    }

    public void setNotifyEmailFromBoolean(Boolean value) {
        this.notifyEmail = (value != null && value) ? "Y" : "N";
    }

    public Boolean getNotifySmsAsBoolean() {
        return "Y".equalsIgnoreCase(notifySms);
    }

    public void setNotifySmsFromBoolean(Boolean value) {
        this.notifySms = (value != null && value) ? "Y" : "N";
    }

    // LIFECYCLE CALLBACKS

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        
        // Set default PayProp values
        if (accountType == null) {
            accountType = AccountType.INDIVIDUAL;
        }
        if (countryCode == null) {
            countryCode = "GB";
        }
        if (invoiceLeadDays == null) {
            invoiceLeadDays = 0;
        }
        if (notifyEmail == null) {
            notifyEmail = "Y";
        }
        if (notifySms == null) {
            notifySms = "N";
        }
        if (hasBankAccount == null) {
            hasBankAccount = false;
        }
        if (payPropSyncStatus == null) {
            payPropSyncStatus = SyncStatus.PENDING;
        }
        
        // Generate customer reference if not set
        if (customerReference == null && customerId != null) {
            String prefix = "CU";
            if (customerType != null) {
                switch (customerType) {
                    case TENANT: prefix = "TN"; break;
                    case PROPERTY_OWNER: prefix = "PO"; break;
                    case CONTRACTOR: prefix = "CO"; break;
                }
            }
            customerReference = prefix + "_" + customerId;
        }
        
        // Set PayProp customer ID
        if (payPropCustomerId == null && customerReference != null) {
            payPropCustomerId = customerReference;
        }
        
        // Set PayProp entity type
        if (payPropEntityType == null) {
            if (customerType != null) {
                switch (customerType) {
                    case TENANT: payPropEntityType = "tenant"; break;
                    case PROPERTY_OWNER: payPropEntityType = "beneficiary"; break;
                    case CONTRACTOR: payPropEntityType = "contractor"; break;
                    default: payPropEntityType = "customer"; break;
                }
            }
        }
    }

    @PreUpdate
    protected void onUpdate() {
        // Update PayProp sync status if entity changed
        if (isPayPropEntity() && Boolean.TRUE.equals(payPropSynced)) {
            // Mark as needing re-sync if critical fields changed
            payPropSynced = false;
            payPropSyncStatus = SyncStatus.PENDING;
        }
    }

    @Override
    public String toString() {
        return "Customer{" +
               "customerId=" + customerId +
               ", name='" + name + '\'' +
               ", email='" + email + '\'' +
               ", customerType=" + customerType +
               ", accountType=" + accountType +
               ", payPropSynced=" + payPropSynced +
               '}';
    }
}