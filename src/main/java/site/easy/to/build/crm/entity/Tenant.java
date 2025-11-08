package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tenant Entity - PayProp Integration Only
 *
 * IMPORTANT: This entity is used ONLY for PayProp synchronization.
 * For internal tenant tracking, use Customer entity with isTenant=true.
 *
 * This class maintains data structure compatible with PayProp's tenant API
 * and handles PayProp-specific fields like payPropId, payPropCustomerId, etc.
 *
 * Relationship to Customer:
 * - A Tenant record links to a Customer record (via customer_id)
 * - Customer.isTenant=true indicates an internal tenant
 * - Tenant entity is for external PayProp sync data
 */
@Entity
@Table(name = "tenants")
public class Tenant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // PayProp Integration Fields
    @Column(name = "payprop_id", length = 32, unique = true)
    @Pattern(regexp = "^[a-zA-Z0-9]+$")
    private String payPropId;
    
    @Column(name = "payprop_customer_id", length = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$")
    private String payPropCustomerId;
    
    // Required Conditional Fields
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;
    
    // FIXED: Individual name fields - optional, can be blank
    @Column(name = "first_name", length = 50)
    @Size(max = 50, message = "First name cannot exceed 50 characters")
    private String firstName;

    @Column(name = "last_name", length = 50)
    @Size(max = 50, message = "Last name cannot exceed 50 characters")
    private String lastName;
    
    // FIXED: Business name validation - controller ensures this is populated
    @Column(name = "business_name", length = 50)
    @Size(min = 2, max = 50, message = "Business name must be 2-50 characters")
    private String businessName;
    
    // Contact Details
    @Column(name = "email_address", length = 50)
    @Email(message = "Invalid email format")
    @Size(max = 50)
    private String emailAddress;
    
    @ElementCollection
    @CollectionTable(name = "tenant_email_cc", joinColumns = @JoinColumn(name = "tenant_id"))
    @Size(max = 10, message = "Maximum 10 CC email addresses allowed")
    private List<@Email String> emailCc;
    
    @Column(name = "phone_number", length = 15)
    @Size(max = 15)
    private String phoneNumber;
    
    // FIXED: Mobile number pattern for international numbers, optional (blank allowed)
    // Pattern only validates if value is not null/empty
    @Pattern(
        regexp = "^$|^(\\+[1-9]\\d{1,14}|0[1-9]\\d{8,10}|[1-9]\\d+)$",
        message = "Invalid mobile number format"
    )
    @Size(max = 15, message = "Mobile number cannot exceed 15 characters")
    private String mobileNumber;
    
    @Column(name = "fax_number", length = 15)
    @Size(max = 15)
    private String faxNumber;
    
    // Address fields
    @Column(name = "address_line_1", length = 50)
    @Size(max = 50)
    private String addressLine1;
    
    @Column(name = "address_line_2", length = 50)
    @Size(max = 50)
    private String addressLine2;
    
    @Column(name = "address_line_3", length = 50)
    @Size(max = 50)
    private String addressLine3;
    
    @Column(name = "city", length = 50)
    @Size(max = 50)
    private String city;
    
    @Column(name = "county", length = 50)
    @Size(max = 50)
    private String county;
    
    @Column(name = "country", length = 2)
    private String country = "UK";
    
    @Column(name = "postcode", length = 10)
    @Size(max = 10)
    private String postcode;
    
    // Personal details
    @Column(name = "date_of_birth")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;
    
    @Column(name = "id_number", length = 15)
    @Size(max = 15)
    private String idNumber;
    
    @Column(name = "id_type_id", length = 32)
    @Size(max = 32)
    private String idTypeId;
    
    private String occupation;
    
    @Column(name = "monthly_income")
    private String monthlyIncome;
    
    private String employer;
    
    @Column(name = "passport_or_licence")
    private String passportOrLicence;
    
    @Column(name = "vat_number", length = 50)
    @Size(max = 50)
    private String vatNumber;
    
    // Tenancy details
    @Column(name = "tenant_type")
    private String tenantType;
    
    @Column(name = "tenancy_status")
    private String tenancyStatus;
    
    @Column(name = "move_in_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate moveInDate;
    
    @Column(name = "move_out_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate moveOutDate;
    
    // Legacy fields (keeping for existing data compatibility)
    @Column(name = "rent_amount")
    private String rentAmount;
    
    @Column(name = "rental_frequency")
    private String rentalFrequency;
    
    @Column(name = "deposit_amount")
    private String depositAmount;
    
    @Column(name = "deposit_paid")
    private String depositPaid;
    
    @Column(name = "invoice_lead_days")
    @Min(value = 0, message = "Invoice lead days cannot be negative")
    @Max(value = 31, message = "Invoice lead days cannot exceed 31")
    private Integer invoiceLeadDays = 0;
    
    // Financial details (for record keeping only per PayProp specs)
    @Column(name = "account_name", length = 50)
    @Size(min = 1, max = 50)
    private String accountName;
    
    @Column(name = "account_number", length = 8)
    @Pattern(regexp = "^\\d+$")
    @Size(min = 3, max = 8)
    private String accountNumber;
    
    @Column(name = "sort_code", length = 6)
    @Pattern(regexp = "^\\d+$")
    @Size(min = 6, max = 6)
    private String sortCode;
    
    @Column(name = "bank_name", length = 50)
    @Size(max = 50)
    private String bankName;
    
    @Column(name = "branch_name", length = 50)
    @Size(max = 50)
    private String branchName;
    
    @Column(name = "has_bank_account")
    private Boolean hasBankAccount;
    
    // Emergency contact
    @Column(name = "emergency_contact_name")
    private String emergencyContactName;
    
    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;
    
    // Guarantor details
    @Column(name = "guarantor_required")
    private String guarantorRequired;
    
    @Column(name = "guarantor_name")
    private String guarantorName;
    
    @Column(name = "guarantor_contact")
    private String guarantorContact;
    
    // Preferences and restrictions
    @Column(name = "dss_accepted")
    private String dssAccepted;
    
    private String smoker;
    
    @Column(name = "pet_owner")
    private String petOwner;
    
    @Column(name = "pet_details")
    private String petDetails;
    
    // Keep as String to avoid migration issues
    @Column(name = "notify_email", length = 1)
    private String notifyEmail = "Y";
    
    @Column(name = "notify_text", length = 1) 
    private String notifyText = "Y";
    
    private String status;
    private String tags;
    
    @Column(name = "notes", columnDefinition = "TEXT", length = 6000)
    @Size(max = 6000)
    private String notes;
    
    @Column(name = "comment", columnDefinition = "TEXT", length = 6000)
    @Size(max = 6000)
    private String comment;
    
    @Column(name = "customer_reference", length = 50)
    @Size(max = 50)
    private String customerReference;
    
    // Audit fields
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by")
    private Long createdBy;
    
    @Column(name = "updated_by")
    private Long updatedBy;
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id")
    private Property property;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id")
    private Customer customer;
    
    // Constructors
    public Tenant() {}
    
    public Tenant(AccountType accountType, String firstName, String lastName) {
        this.accountType = accountType;
        this.firstName = firstName;
        this.lastName = lastName;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Customer relationship getter/setter
    public Customer getCustomer() { 
        return customer; 
    }

    public void setCustomer(Customer customer) { 
        this.customer = customer; 
    }
    
    // PayProp-compatible getters that convert String to Boolean
    public Boolean getNotifyEmailAsBoolean() {
        return "Y".equalsIgnoreCase(notifyEmail);
    }
    
    public void setNotifyEmailFromBoolean(Boolean value) {
        this.notifyEmail = (value != null && value) ? "Y" : "N";
    }
    
    public Boolean getNotifyTextAsBoolean() {
        return "Y".equalsIgnoreCase(notifyText);
    }
    
    public void setNotifyTextFromBoolean(Boolean value) {
        this.notifyText = (value != null && value) ? "Y" : "N";
    }
    
    // All getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getPayPropId() { return payPropId; }
    public void setPayPropId(String payPropId) { this.payPropId = payPropId; }
    
    public String getPayPropCustomerId() { 
        return payPropCustomerId; 
    }

    public void setPayPropCustomerId(String payPropCustomerId) { 
        this.payPropCustomerId = payPropCustomerId; 
    }
    
    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
    
    public List<String> getEmailCc() { return emailCc; }
    public void setEmailCc(List<String> emailCc) { this.emailCc = emailCc; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    
    public String getFaxNumber() { return faxNumber; }
    public void setFaxNumber(String faxNumber) { this.faxNumber = faxNumber; }
    
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
    
    public String getAddressLine3() { return addressLine3; }
    public void setAddressLine3(String addressLine3) { this.addressLine3 = addressLine3; }
    
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public String getCounty() { return county; }
    public void setCounty(String county) { this.county = county; }
    
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    
    public String getPostcode() { return postcode; }
    public void setPostcode(String postcode) { this.postcode = postcode; }
    
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    
    public String getIdTypeId() { return idTypeId; }
    public void setIdTypeId(String idTypeId) { this.idTypeId = idTypeId; }
    
    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }
    
    public String getMonthlyIncome() { return monthlyIncome; }
    public void setMonthlyIncome(String monthlyIncome) { this.monthlyIncome = monthlyIncome; }
    
    public String getEmployer() { return employer; }
    public void setEmployer(String employer) { this.employer = employer; }
    
    public String getPassportOrLicence() { return passportOrLicence; }
    public void setPassportOrLicence(String passportOrLicence) { this.passportOrLicence = passportOrLicence; }
    
    public String getVatNumber() { return vatNumber; }
    public void setVatNumber(String vatNumber) { this.vatNumber = vatNumber; }
    
    public String getTenantType() { return tenantType; }
    public void setTenantType(String tenantType) { this.tenantType = tenantType; }
    
    public String getTenancyStatus() { return tenancyStatus; }
    public void setTenancyStatus(String tenancyStatus) { this.tenancyStatus = tenancyStatus; }
    
    public LocalDate getMoveInDate() { return moveInDate; }
    public void setMoveInDate(LocalDate moveInDate) { this.moveInDate = moveInDate; }
    
    public LocalDate getMoveOutDate() { return moveOutDate; }
    public void setMoveOutDate(LocalDate moveOutDate) { this.moveOutDate = moveOutDate; }
    
    public String getRentAmount() { return rentAmount; }
    public void setRentAmount(String rentAmount) { this.rentAmount = rentAmount; }
    
    public String getRentalFrequency() { return rentalFrequency; }
    public void setRentalFrequency(String rentalFrequency) { this.rentalFrequency = rentalFrequency; }
    
    public String getDepositAmount() { return depositAmount; }
    public void setDepositAmount(String depositAmount) { this.depositAmount = depositAmount; }
    
    public String getDepositPaid() { return depositPaid; }
    public void setDepositPaid(String depositPaid) { this.depositPaid = depositPaid; }
    
    public Integer getInvoiceLeadDays() { return invoiceLeadDays; }
    public void setInvoiceLeadDays(Integer invoiceLeadDays) { this.invoiceLeadDays = invoiceLeadDays; }
    
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    
    public String getSortCode() { return sortCode; }
    public void setSortCode(String sortCode) { this.sortCode = sortCode; }
    
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    
    public Boolean getHasBankAccount() { return hasBankAccount; }
    public void setHasBankAccount(Boolean hasBankAccount) { this.hasBankAccount = hasBankAccount; }
    
    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }
    
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }
    
    public String getGuarantorRequired() { return guarantorRequired; }
    public void setGuarantorRequired(String guarantorRequired) { this.guarantorRequired = guarantorRequired; }
    
    public String getGuarantorName() { return guarantorName; }
    public void setGuarantorName(String guarantorName) { this.guarantorName = guarantorName; }
    
    public String getGuarantorContact() { return guarantorContact; }
    public void setGuarantorContact(String guarantorContact) { this.guarantorContact = guarantorContact; }
    
    public String getDssAccepted() { return dssAccepted; }
    public void setDssAccepted(String dssAccepted) { this.dssAccepted = dssAccepted; }
    
    public String getSmoker() { return smoker; }
    public void setSmoker(String smoker) { this.smoker = smoker; }
    
    public String getPetOwner() { return petOwner; }
    public void setPetOwner(String petOwner) { this.petOwner = petOwner; }
    
    public String getPetDetails() { return petDetails; }
    public void setPetDetails(String petDetails) { this.petDetails = petDetails; }
    
    // String getters/setters for database compatibility
    public String getNotifyEmail() { return notifyEmail; }
    public void setNotifyEmail(String notifyEmail) { this.notifyEmail = notifyEmail; }
    
    public String getNotifyText() { return notifyText; }
    public void setNotifyText(String notifyText) { this.notifyText = notifyText; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    
    public String getCustomerReference() { return customerReference; }
    public void setCustomerReference(String customerReference) { this.customerReference = customerReference; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    
    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }
    
    // Utility Methods
    public String getFullName() {
        if (accountType == AccountType.business && businessName != null && !businessName.trim().isEmpty()) {
            return businessName;
        }
        StringBuilder name = new StringBuilder();
        if (firstName != null && !firstName.trim().isEmpty()) {
            name.append(firstName);
        }
        if (lastName != null && !lastName.trim().isEmpty()) {
            if (name.length() > 0) name.append(" ");
            name.append(lastName);
        }
        return name.toString();
    }
    
    public String getFullAddress() {
        StringBuilder address = new StringBuilder();
        if (addressLine1 != null && !addressLine1.trim().isEmpty()) {
            address.append(addressLine1);
        }
        if (addressLine2 != null && !addressLine2.trim().isEmpty()) {
            address.append(", ").append(addressLine2);
        }
        if (addressLine3 != null && !addressLine3.trim().isEmpty()) {
            address.append(", ").append(addressLine3);
        }
        if (city != null && !city.trim().isEmpty()) {
            address.append(", ").append(city);
        }
        if (postcode != null && !postcode.trim().isEmpty()) {
            address.append(" ").append(postcode);
        }
        return address.toString();
    }
    
    // PayProp Business Logic
    public boolean isPayPropSynced() {
        return payPropId != null && !payPropId.trim().isEmpty();
    }
    
    public boolean isIndividualAccount() {
        return accountType == AccountType.individual;
    }
    
    public boolean isBusinessAccount() {
        return accountType == AccountType.business;
    }
    
    // FIXED: Updated for PayProp business account best practice
    public boolean isReadyForPayPropSync() {
        boolean hasRequiredFields = payPropCustomerId != null && !payPropCustomerId.trim().isEmpty() &&
                                   accountType != null;
        
        // For PayProp best practice (always business accounts), require business name
        if (accountType == AccountType.business) {
            return hasRequiredFields && 
                   businessName != null && !businessName.trim().isEmpty();
        } else {
            // Legacy individual account support
            return hasRequiredFields && 
                   firstName != null && !firstName.trim().isEmpty() &&
                   lastName != null && !lastName.trim().isEmpty();
        }
    }
    
    public boolean hasBankDetails() {
        return accountName != null && !accountName.trim().isEmpty() &&
               accountNumber != null && !accountNumber.trim().isEmpty() &&
               sortCode != null && !sortCode.trim().isEmpty();
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (payPropCustomerId == null) {
            payPropCustomerId = "TENANT_" + System.currentTimeMillis();
        }
        if (notifyEmail == null) {
            notifyEmail = "Y";
        }
        if (notifyText == null) {
            notifyText = "Y";
        }
        if (country == null) {
            country = "UK";
        }
        if (invoiceLeadDays == null) {
            invoiceLeadDays = 0;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}