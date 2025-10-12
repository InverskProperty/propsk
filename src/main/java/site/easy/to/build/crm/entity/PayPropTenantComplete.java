package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PayProp Tenant Complete Entity
 * Represents complete tenant data imported from PayProp /export/tenants
 * Used for reporting and analytics (tenant turnover, occupancy, etc.)
 */
@Entity
@Table(name = "payprop_export_tenants_complete")
public class PayPropTenantComplete {

    @Id
    @Column(name = "payprop_id", length = 50)
    private String payPropId;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "business_name", length = 255)
    private String businessName;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "email_cc", length = 500)
    private String emailCc;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "mobile", length = 50)
    private String mobile;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "id_number", length = 50)
    private String idNumber;

    @Column(name = "id_type", length = 50)
    private String idType;

    @Column(name = "nationality", length = 100)
    private String nationality;

    @Column(name = "occupation", length = 255)
    private String occupation;

    @Column(name = "employer", length = 255)
    private String employer;

    // Address fields (flattened from nested object)
    @Column(name = "address_id", length = 50)
    private String addressId;

    @Column(name = "address_first_line", length = 255)
    private String addressFirstLine;

    @Column(name = "address_second_line", length = 255)
    private String addressSecondLine;

    @Column(name = "address_third_line", length = 255)
    private String addressThirdLine;

    @Column(name = "address_city", length = 100)
    private String addressCity;

    @Column(name = "address_state", length = 100)
    private String addressState;

    @Column(name = "address_country_code", length = 10)
    private String addressCountryCode;

    @Column(name = "address_postal_code", length = 20)
    private String addressPostalCode;

    @Column(name = "address_zip_code", length = 20)
    private String addressZipCode;

    @Column(name = "address_phone", length = 50)
    private String addressPhone;

    @Column(name = "address_email", length = 255)
    private String addressEmail;

    // Emergency contact
    @Column(name = "emergency_contact_name", length = 255)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 50)
    private String emergencyContactPhone;

    @Column(name = "emergency_contact_relationship", length = 100)
    private String emergencyContactRelationship;

    // Tenancy details - CRITICAL FOR TURNOVER REPORTING
    @Column(name = "current_property_id", length = 50)
    private String currentPropertyId;

    @Column(name = "current_deposit_id", length = 50)
    private String currentDepositId;

    @Column(name = "tenancy_start_date")
    private LocalDate tenancyStartDate;

    @Column(name = "tenancy_end_date")
    private LocalDate tenancyEndDate;

    @Column(name = "monthly_rent_amount", precision = 10, scale = 2)
    private BigDecimal monthlyRentAmount;

    @Column(name = "deposit_amount", precision = 10, scale = 2)
    private BigDecimal depositAmount;

    // Preferences
    @Column(name = "notify_email")
    private Boolean notifyEmail;

    @Column(name = "notify_sms")
    private Boolean notifySms;

    @Column(name = "preferred_contact_method", length = 50)
    private String preferredContactMethod;

    // Status and meta
    @Column(name = "tenant_status", length = 50)
    private String tenantStatus;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "credit_score", precision = 10, scale = 2)
    private BigDecimal creditScore;

    @Column(name = "reference", length = 255)
    private String reference;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "properties_json", columnDefinition = "TEXT")
    private String propertiesJson;

    @Column(name = "imported_at")
    private LocalDateTime importedAt;

    @Column(name = "sync_status", length = 20)
    private String syncStatus;

    // Constructors
    public PayPropTenantComplete() {}

    // Getters and Setters
    public String getPayPropId() {
        return payPropId;
    }

    public void setPayPropId(String payPropId) {
        this.payPropId = payPropId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmailCc() {
        return emailCc;
    }

    public void setEmailCc(String emailCc) {
        this.emailCc = emailCc;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public String getIdType() {
        return idType;
    }

    public void setIdType(String idType) {
        this.idType = idType;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getOccupation() {
        return occupation;
    }

    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }

    public String getEmployer() {
        return employer;
    }

    public void setEmployer(String employer) {
        this.employer = employer;
    }

    public String getAddressId() {
        return addressId;
    }

    public void setAddressId(String addressId) {
        this.addressId = addressId;
    }

    public String getAddressFirstLine() {
        return addressFirstLine;
    }

    public void setAddressFirstLine(String addressFirstLine) {
        this.addressFirstLine = addressFirstLine;
    }

    public String getAddressSecondLine() {
        return addressSecondLine;
    }

    public void setAddressSecondLine(String addressSecondLine) {
        this.addressSecondLine = addressSecondLine;
    }

    public String getAddressThirdLine() {
        return addressThirdLine;
    }

    public void setAddressThirdLine(String addressThirdLine) {
        this.addressThirdLine = addressThirdLine;
    }

    public String getAddressCity() {
        return addressCity;
    }

    public void setAddressCity(String addressCity) {
        this.addressCity = addressCity;
    }

    public String getAddressState() {
        return addressState;
    }

    public void setAddressState(String addressState) {
        this.addressState = addressState;
    }

    public String getAddressCountryCode() {
        return addressCountryCode;
    }

    public void setAddressCountryCode(String addressCountryCode) {
        this.addressCountryCode = addressCountryCode;
    }

    public String getAddressPostalCode() {
        return addressPostalCode;
    }

    public void setAddressPostalCode(String addressPostalCode) {
        this.addressPostalCode = addressPostalCode;
    }

    public String getAddressZipCode() {
        return addressZipCode;
    }

    public void setAddressZipCode(String addressZipCode) {
        this.addressZipCode = addressZipCode;
    }

    public String getAddressPhone() {
        return addressPhone;
    }

    public void setAddressPhone(String addressPhone) {
        this.addressPhone = addressPhone;
    }

    public String getAddressEmail() {
        return addressEmail;
    }

    public void setAddressEmail(String addressEmail) {
        this.addressEmail = addressEmail;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public void setEmergencyContactPhone(String emergencyContactPhone) {
        this.emergencyContactPhone = emergencyContactPhone;
    }

    public String getEmergencyContactRelationship() {
        return emergencyContactRelationship;
    }

    public void setEmergencyContactRelationship(String emergencyContactRelationship) {
        this.emergencyContactRelationship = emergencyContactRelationship;
    }

    public String getCurrentPropertyId() {
        return currentPropertyId;
    }

    public void setCurrentPropertyId(String currentPropertyId) {
        this.currentPropertyId = currentPropertyId;
    }

    public String getCurrentDepositId() {
        return currentDepositId;
    }

    public void setCurrentDepositId(String currentDepositId) {
        this.currentDepositId = currentDepositId;
    }

    public LocalDate getTenancyStartDate() {
        return tenancyStartDate;
    }

    public void setTenancyStartDate(LocalDate tenancyStartDate) {
        this.tenancyStartDate = tenancyStartDate;
    }

    public LocalDate getTenancyEndDate() {
        return tenancyEndDate;
    }

    public void setTenancyEndDate(LocalDate tenancyEndDate) {
        this.tenancyEndDate = tenancyEndDate;
    }

    public BigDecimal getMonthlyRentAmount() {
        return monthlyRentAmount;
    }

    public void setMonthlyRentAmount(BigDecimal monthlyRentAmount) {
        this.monthlyRentAmount = monthlyRentAmount;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount;
    }

    public Boolean getNotifyEmail() {
        return notifyEmail;
    }

    public void setNotifyEmail(Boolean notifyEmail) {
        this.notifyEmail = notifyEmail;
    }

    public Boolean getNotifySms() {
        return notifySms;
    }

    public void setNotifySms(Boolean notifySms) {
        this.notifySms = notifySms;
    }

    public String getPreferredContactMethod() {
        return preferredContactMethod;
    }

    public void setPreferredContactMethod(String preferredContactMethod) {
        this.preferredContactMethod = preferredContactMethod;
    }

    public String getTenantStatus() {
        return tenantStatus;
    }

    public void setTenantStatus(String tenantStatus) {
        this.tenantStatus = tenantStatus;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public BigDecimal getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(BigDecimal creditScore) {
        this.creditScore = creditScore;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getPropertiesJson() {
        return propertiesJson;
    }

    public void setPropertiesJson(String propertiesJson) {
        this.propertiesJson = propertiesJson;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(LocalDateTime importedAt) {
        this.importedAt = importedAt;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    // Utility methods
    public String getFullName() {
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        if (businessName != null && !businessName.trim().isEmpty()) {
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

    /**
     * Check if tenant is currently active (no end date or end date in future)
     */
    public boolean isCurrentlyActive() {
        if (tenancyEndDate == null) {
            return true; // No end date means still active
        }
        return tenancyEndDate.isAfter(LocalDate.now());
    }

    /**
     * Check if tenant has moved out (end date in past)
     */
    public boolean hasMovedOut() {
        if (tenancyEndDate == null) {
            return false;
        }
        return tenancyEndDate.isBefore(LocalDate.now()) || tenancyEndDate.isEqual(LocalDate.now());
    }

    /**
     * Check if tenant is moving out soon (within specified days)
     */
    public boolean isMovingOutSoon(int days) {
        if (tenancyEndDate == null) {
            return false;
        }
        LocalDate futureDate = LocalDate.now().plusDays(days);
        return tenancyEndDate.isAfter(LocalDate.now()) &&
               (tenancyEndDate.isBefore(futureDate) || tenancyEndDate.isEqual(futureDate));
    }
}
