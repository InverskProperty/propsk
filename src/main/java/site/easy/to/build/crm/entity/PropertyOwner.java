// PropertyOwner.java - FIXED: Resolved customer_id column conflict
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "property_owners")
public class PropertyOwner {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // PayProp Integration Fields
    @Column(name = "payprop_id", length = 32, unique = true)
    @Pattern(regexp = "^[a-zA-Z0-9]+$")
    private String payPropId;
    
    // FIXED: Renamed column to avoid conflict with foreign key
    @Column(name = "payprop_customer_id", length = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$")
    private String payPropCustomerId;
    
    // Required Conditional Fields (PayProp Beneficiary specs)
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;
    
    @Column(name = "first_name", length = 100)
    @Size(min = 1, max = 100)
    private String firstName;
    
    @Column(name = "last_name", length = 100)
    @Size(min = 1, max = 100)
    private String lastName;
    
    @Column(name = "business_name", length = 50)
    @Size(max = 50)
    private String businessName;
    
    // Contact Details
    @Column(name = "email_address", length = 100)
    @Email
    @Size(max = 100)
    private String emailAddress;
    
    @Column(name = "email_cc")
    @ElementCollection
    @CollectionTable(name = "property_owner_email_cc", joinColumns = @JoinColumn(name = "property_owner_id"))
    @Size(max = 10)
    private List<@Email String> emailCc;
    
    @Column(name = "mobile", length = 25)
    @Pattern(regexp = "^[1-9]\\d+$")
    @Size(max = 25)
    private String mobile;
    
    @Column(name = "phone", length = 15)
    @Size(max = 15)
    private String phone;
    
    @Column(name = "fax", length = 15)
    @Size(max = 15)
    private String fax;
    
    // Address fields (Required for international payments)
    @Column(name = "address_line_1", length = 50)
    @Size(min = 1, max = 50)
    private String addressLine1;
    
    @Column(name = "address_line_2", length = 50)
    @Size(max = 50)
    private String addressLine2;
    
    @Column(name = "address_line_3", length = 50)
    @Size(max = 50)
    private String addressLine3;
    
    @Column(name = "city", length = 50)
    @Size(min = 1, max = 50)
    private String city;
    
    @Column(name = "state", length = 50)
    @Size(min = 1, max = 50)
    private String state;
    
    @Column(name = "country", length = 2)
    private String country = "UK";
    
    @Column(name = "postal_code", length = 10)
    @Size(min = 1, max = 10)
    private String postalCode;
    
    // Identity and Tax Information
    @Column(name = "id_number", length = 50)
    @Size(max = 50)
    private String idNumber;
    
    @Column(name = "id_type_id", length = 32)
    @Size(max = 32)
    private String idTypeId;
    
    @Column(name = "vat_number", length = 50)
    @Size(max = 50)
    private String vatNumber;
    
    // Bank Account Details - Local Payment Method
    @Column(name = "bank_account_name", length = 50)
    @Size(min = 1, max = 50)
    private String bankAccountName;
    
    @Column(name = "bank_account_number", length = 8)
    @Pattern(regexp = "^\\d+$")
    @Size(min = 3, max = 8)
    private String bankAccountNumber;
    
    @Column(name = "branch_code", length = 6)
    @Pattern(regexp = "^\\d+$")
    @Size(min = 6, max = 6)
    private String branchCode;
    
    @Column(name = "bank_name", length = 50)
    @Size(max = 50)
    private String bankName;
    
    @Column(name = "branch_name", length = 50)
    @Size(max = 50)
    private String branchName;
    
    // International Bank Account Details
    @Column(name = "swift_code", length = 11)
    @Size(min = 8, max = 11)
    private String swiftCode;
    
    @Column(name = "bank_country_code", length = 2)
    private String bankCountryCode;
    
    @Column(name = "iban", length = 34)
    @Pattern(regexp = "^[A-Za-z0-9]+$")
    @Size(max = 34)
    private String iban;
    
    @Column(name = "international_account_number")
    @Pattern(regexp = "^[a-zA-Z0-9\\- ]+$")
    private String internationalAccountNumber;
    
    // Communication Preferences
    @Column(name = "email_enabled")
    private Boolean emailEnabled = true;
    
    @Column(name = "payment_advice_enabled")
    private Boolean paymentAdviceEnabled = true;
    
    // Ownership and Management (Local CRM fields)
    @Column(name = "relationship_type")
    private String relationshipType;
    
    @Column(name = "is_primary_owner")
    private String isPrimaryOwner;
    
    @Column(name = "ownership_percentage", precision = 5, scale = 2)
    @DecimalMin("0.01")
    @DecimalMax("100")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal ownershipPercentage;
    
    @Column(name = "start_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    
    @Column(name = "end_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    
    private String status;
    
    // Management and Financial Rights
    @Column(name = "financial_responsibilities")
    private String financialResponsibilities;
    
    @Column(name = "receive_rent_payments")
    private String receiveRentPayments;
    
    @Column(name = "receive_statements")
    private String receiveStatements;
    
    @Column(name = "rent_collection_rights")
    private String rentCollectionRights;
    
    @Column(name = "management_rights")
    private String managementRights;
    
    @Column(name = "maintenance_responsibilities")
    private String maintenanceResponsibilities;
    
    @Column(name = "legal_status")
    private String legalStatus;
    
    @Column(name = "contact_for_emergencies")
    private String contactForEmergencies;
    
    @Column(name = "customer_reference", length = 50)
    @Size(max = 50)
    private String customerReference;
    
    @Column(name = "comment", columnDefinition = "TEXT", length = 6000)
    @Size(max = 6000)
    private String comment;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    // Audit fields
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by")
    private Long createdBy;
    
    @Column(name = "updated_by")
    private Long updatedBy;
    
    // Foreign Keys (keeping original structure)
    @Column(name = "customer_id_fk", nullable = false)
    private Integer customerIdFk;
    
    @Column(name = "property_id", nullable = false)
    private Long propertyId;
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", insertable = false, updatable = false)
    private Property property;

    // FIXED: Use read-only relationship to avoid column duplication
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id_fk", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;
    
    // Constructors
    public PropertyOwner() {}
    
    public PropertyOwner(AccountType accountType, PaymentMethod paymentMethod, Integer customerIdFk, Long propertyId) {
        this.customerIdFk = customerIdFk;
        this.propertyId = propertyId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Customer relationship getter/setter
    public Customer getCustomer() { 
        return customer; 
    }

    public void setCustomer(Customer customer) { 
        this.customer = customer;
        // Update the foreign key field when setting the customer
        this.customerIdFk = customer != null ? customer.getCustomerId() : null;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getPayPropId() { return payPropId; }
    public void setPayPropId(String payPropId) { this.payPropId = payPropId; }
    
    // FIXED: Updated getter/setter names
    public String getPayPropCustomerId() { return payPropCustomerId; }
    public void setPayPropCustomerId(String payPropCustomerId) { this.payPropCustomerId = payPropCustomerId; }
    
    // Legacy method for compatibility - delegates to PayProp field
    public String getCustomerId() { return payPropCustomerId; }
    public void setCustomerId(String customerId) { this.payPropCustomerId = customerId; }
    
    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }
    
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    
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
    
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
    public String getFax() { return fax; }
    public void setFax(String fax) { this.fax = fax; }
    
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
    
    public String getAddressLine3() { return addressLine3; }
    public void setAddressLine3(String addressLine3) { this.addressLine3 = addressLine3; }
    
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    
    public String getIdTypeId() { return idTypeId; }
    public void setIdTypeId(String idTypeId) { this.idTypeId = idTypeId; }
    
    public String getVatNumber() { return vatNumber; }
    public void setVatNumber(String vatNumber) { this.vatNumber = vatNumber; }
    
    public String getBankAccountName() { return bankAccountName; }
    public void setBankAccountName(String bankAccountName) { this.bankAccountName = bankAccountName; }
    
    public String getBankAccountNumber() { return bankAccountNumber; }
    public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }
    
    public String getBranchCode() { return branchCode; }
    public void setBranchCode(String branchCode) { this.branchCode = branchCode; }
    
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    
    public String getSwiftCode() { return swiftCode; }
    public void setSwiftCode(String swiftCode) { this.swiftCode = swiftCode; }
    
    public String getBankCountryCode() { return bankCountryCode; }
    public void setBankCountryCode(String bankCountryCode) { this.bankCountryCode = bankCountryCode; }
    
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }
    
    public String getInternationalAccountNumber() { return internationalAccountNumber; }
    public void setInternationalAccountNumber(String internationalAccountNumber) { this.internationalAccountNumber = internationalAccountNumber; }
    
    public Boolean getEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(Boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    
    public Boolean getPaymentAdviceEnabled() { return paymentAdviceEnabled; }
    public void setPaymentAdviceEnabled(Boolean paymentAdviceEnabled) { this.paymentAdviceEnabled = paymentAdviceEnabled; }
    
    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }
    
    public String getIsPrimaryOwner() { return isPrimaryOwner; }
    public void setIsPrimaryOwner(String isPrimaryOwner) { this.isPrimaryOwner = isPrimaryOwner; }
    
    public BigDecimal getOwnershipPercentage() { return ownershipPercentage; }
    public void setOwnershipPercentage(BigDecimal ownershipPercentage) { this.ownershipPercentage = ownershipPercentage; }
    
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getFinancialResponsibilities() { return financialResponsibilities; }
    public void setFinancialResponsibilities(String financialResponsibilities) { this.financialResponsibilities = financialResponsibilities; }
    
    public String getReceiveRentPayments() { return receiveRentPayments; }
    public void setReceiveRentPayments(String receiveRentPayments) { this.receiveRentPayments = receiveRentPayments; }
    
    public String getReceiveStatements() { return receiveStatements; }
    public void setReceiveStatements(String receiveStatements) { this.receiveStatements = receiveStatements; }
    
    public String getRentCollectionRights() { return rentCollectionRights; }
    public void setRentCollectionRights(String rentCollectionRights) { this.rentCollectionRights = rentCollectionRights; }
    
    public String getManagementRights() { return managementRights; }
    public void setManagementRights(String managementRights) { this.managementRights = managementRights; }
    
    public String getMaintenanceResponsibilities() { return maintenanceResponsibilities; }
    public void setMaintenanceResponsibilities(String maintenanceResponsibilities) { this.maintenanceResponsibilities = maintenanceResponsibilities; }
    
    public String getLegalStatus() { return legalStatus; }
    public void setLegalStatus(String legalStatus) { this.legalStatus = legalStatus; }
    
    public String getContactForEmergencies() { return contactForEmergencies; }
    public void setContactForEmergencies(String contactForEmergencies) { this.contactForEmergencies = contactForEmergencies; }
    
    public String getCustomerReference() { return customerReference; }
    public void setCustomerReference(String customerReference) { this.customerReference = customerReference; }
    
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    
    public Integer getCustomerIdFk() { return customerIdFk; }
    public void setCustomerIdFk(Integer customerIdFk) { this.customerIdFk = customerIdFk; }
    
    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }
    
    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }
    
    // Utility methods
    public String getFullName() {
        if (accountType == AccountType.BUSINESS) {
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
    
    public boolean isPrimary() {
        return "Y".equalsIgnoreCase(isPrimaryOwner);
    }
    
    public boolean canReceiveRentPayments() {
        return "Y".equalsIgnoreCase(receiveRentPayments);
    }
    
    public boolean canReceiveStatements() {
        return "Y".equalsIgnoreCase(receiveStatements);
    }
    
    public boolean isEmergencyContact() {
        return "Y".equalsIgnoreCase(contactForEmergencies);
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}