// Contractor.java
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "contractors")
public class Contractor {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "company_name", nullable = false)
    @NotBlank
    private String companyName;
    
    @Column(name = "contact_person")
    private String contactPerson;
    
    @Column(name = "job_title")
    private String jobTitle;
    
    @Column(name = "email_address")
    @Email
    private String emailAddress;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column(name = "mobile_number")
    private String mobileNumber;
    
    @Column(name = "fax_number")
    private String faxNumber;
    
    @Column(name = "emergency_contact")
    private String emergencyContact;
    
    // Address details
    @Column(name = "address_line_1")
    private String addressLine1;
    
    @Column(name = "address_line_2")
    private String addressLine2;
    
    @Column(name = "address_line_3")
    private String addressLine3;
    
    private String city;
    private String county;
    private String country;
    private String postcode;
    
    private String website;
    
    // Company details
    @Column(name = "company_registration")
    private String companyRegistration;
    
    @Column(name = "vat_number")
    private String vatNumber;
    
    // Financial details
    @Column(name = "account_name")
    private String accountName;
    
    @Column(name = "account_number")
    private String accountNumber;
    
    @Column(name = "sort_code")
    private String sortCode;
    
    @Column(name = "bank_name")
    private String bankName;
    
    @Column(name = "payment_terms")
    private String paymentTerms;
    
    // Pricing
    @Column(name = "hourly_rate", precision = 10, scale = 2)
    @DecimalMin("0.01")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal hourlyRate;
    
    @Column(name = "call_out_charge", precision = 10, scale = 2)
    @DecimalMin("0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal callOutCharge;
    
    @Column(name = "minimum_charge", precision = 10, scale = 2)
    @DecimalMin("0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal minimumCharge;
    
    // Availability
    @Column(name = "available_24_7")
    private String available247;
    
    @Column(name = "working_days")
    private String workingDays;
    
    @Column(name = "working_hours")
    private String workingHours;
    
    @Column(name = "average_response_time")
    private Integer averageResponseTime;
    
    // Certifications and Insurance
    @Column(name = "gas_safe_number")
    private String gasSafeNumber;
    
    @Column(name = "gas_safe_expiry")
    private LocalDateTime gasSafeExpiry;
    
    @Column(name = "niceic_number")
    private String niceicNumber;
    
    @Column(name = "niceic_expiry")
    private LocalDateTime niceicExpiry;
    
    @Column(name = "insurance_amount", precision = 15, scale = 2)
    @DecimalMin("0")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal insuranceAmount;
    
    @Column(name = "insurance_expiry")
    private LocalDateTime insuranceExpiry;
    
    // Performance metrics
    @Column(name = "rating", precision = 3, scale = 2)
    @DecimalMin("0")
    @DecimalMax("5")
    @Digits(integer = 1, fraction = 2)
    private BigDecimal rating;
    
    @Column(name = "total_jobs")
    @Min(0)
    private Integer totalJobs;
    
    @Column(name = "completed_jobs")
    @Min(0)
    private Integer completedJobs;
    
    @Column(name = "preferred_contractor")
    private String preferredContractor;
    
    private String status;
    private String tags;
    
    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;
    
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
    
    // Bidirectional relationship with Customer
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id")
    private Customer customer;

    // Add this getter/setter
    public Customer getCustomer() { 
        return customer; 
    }

    public void setCustomer(Customer customer) { 
        this.customer = customer; 
    }

    // Constructors
    public Contractor() {}
    
    public Contractor(String companyName, String contactPerson, String emailAddress) {
        this.companyName = companyName;
        this.contactPerson = contactPerson;
        this.emailAddress = emailAddress;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    
    public String getFaxNumber() { return faxNumber; }
    public void setFaxNumber(String faxNumber) { this.faxNumber = faxNumber; }
    
    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }
    
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
    
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    
    public String getCompanyRegistration() { return companyRegistration; }
    public void setCompanyRegistration(String companyRegistration) { this.companyRegistration = companyRegistration; }
    
    public String getVatNumber() { return vatNumber; }
    public void setVatNumber(String vatNumber) { this.vatNumber = vatNumber; }
    
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    
    public String getSortCode() { return sortCode; }
    public void setSortCode(String sortCode) { this.sortCode = sortCode; }
    
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    
    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
    
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }
    
    public BigDecimal getCallOutCharge() { return callOutCharge; }
    public void setCallOutCharge(BigDecimal callOutCharge) { this.callOutCharge = callOutCharge; }
    
    public BigDecimal getMinimumCharge() { return minimumCharge; }
    public void setMinimumCharge(BigDecimal minimumCharge) { this.minimumCharge = minimumCharge; }
    
    public String getAvailable247() { return available247; }
    public void setAvailable247(String available247) { this.available247 = available247; }
    
    public String getWorkingDays() { return workingDays; }
    public void setWorkingDays(String workingDays) { this.workingDays = workingDays; }
    
    public String getWorkingHours() { return workingHours; }
    public void setWorkingHours(String workingHours) { this.workingHours = workingHours; }
    
    public Integer getAverageResponseTime() { return averageResponseTime; }
    public void setAverageResponseTime(Integer averageResponseTime) { this.averageResponseTime = averageResponseTime; }
    
    public String getGasSafeNumber() { return gasSafeNumber; }
    public void setGasSafeNumber(String gasSafeNumber) { this.gasSafeNumber = gasSafeNumber; }
    
    public LocalDateTime getGasSafeExpiry() { return gasSafeExpiry; }
    public void setGasSafeExpiry(LocalDateTime gasSafeExpiry) { this.gasSafeExpiry = gasSafeExpiry; }
    
    public String getNiceicNumber() { return niceicNumber; }
    public void setNiceicNumber(String niceicNumber) { this.niceicNumber = niceicNumber; }
    
    public LocalDateTime getNiceicExpiry() { return niceicExpiry; }
    public void setNiceicExpiry(LocalDateTime niceicExpiry) { this.niceicExpiry = niceicExpiry; }
    
    public BigDecimal getInsuranceAmount() { return insuranceAmount; }
    public void setInsuranceAmount(BigDecimal insuranceAmount) { this.insuranceAmount = insuranceAmount; }
    
    public LocalDateTime getInsuranceExpiry() { return insuranceExpiry; }
    public void setInsuranceExpiry(LocalDateTime insuranceExpiry) { this.insuranceExpiry = insuranceExpiry; }
    
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    
    public Integer getTotalJobs() { return totalJobs; }
    public void setTotalJobs(Integer totalJobs) { this.totalJobs = totalJobs; }
    
    public Integer getCompletedJobs() { return completedJobs; }
    public void setCompletedJobs(Integer completedJobs) { this.completedJobs = completedJobs; }
    
    public String getPreferredContractor() { return preferredContractor; }
    public void setPreferredContractor(String preferredContractor) { this.preferredContractor = preferredContractor; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    
    public String getInternalNotes() { return internalNotes; }
    public void setInternalNotes(String internalNotes) { this.internalNotes = internalNotes; }
    
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
    
    // Utility methods
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
    
    public boolean isAvailable247() {
        return "Y".equalsIgnoreCase(available247);
    }
    
    public boolean isPreferred() {
        return "Y".equalsIgnoreCase(preferredContractor);
    }
    
    public boolean isEmergencyContactAvailable() {
        return "Y".equalsIgnoreCase(emergencyContact);
    }
    
    public boolean isGasSafeCertified() {
        return gasSafeNumber != null && !gasSafeNumber.trim().isEmpty() 
               && gasSafeExpiry != null && gasSafeExpiry.isAfter(LocalDateTime.now());
    }
    
    public boolean isNiceicCertified() {
        return niceicNumber != null && !niceicNumber.trim().isEmpty() 
               && niceicExpiry != null && niceicExpiry.isAfter(LocalDateTime.now());
    }
    
    public boolean isInsuranceValid() {
        return insuranceExpiry != null && insuranceExpiry.isAfter(LocalDateTime.now());
    }
    
    public double getCompletionRate() {
        if (totalJobs == null || totalJobs == 0) return 0.0;
        if (completedJobs == null) return 0.0;
        return (double) completedJobs / totalJobs * 100;
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