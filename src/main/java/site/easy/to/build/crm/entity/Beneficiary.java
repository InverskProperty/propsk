// Beneficiary.java - Payment recipients (property owners, contractors, etc.)
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "beneficiaries")
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== PAYPROP INTEGRATION =====
    
    @Column(name = "pay_prop_beneficiary_id", unique = true)
    private String payPropBeneficiaryId;
    
    @Column(name = "pay_prop_customer_id")
    private String payPropCustomerId;

    // ===== BENEFICIARY DETAILS =====
    
    @Column(name = "name", nullable = false, length = 200)
    private String name;
    
    @Column(name = "email", length = 100)
    private String email;
    
    @Column(name = "phone", length = 20)
    private String phone;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "beneficiary_type")
    private BeneficiaryType beneficiaryType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type")
    private AccountType accountType;

    // ===== BUSINESS DETAILS =====
    
    @Column(name = "business_name", length = 200)
    private String businessName;
    
    @Column(name = "first_name", length = 100)
    private String firstName;
    
    @Column(name = "last_name", length = 100)
    private String lastName;

    // ===== ADDRESS =====
    
    @Column(name = "address_line1", length = 255)
    private String addressLine1;
    
    @Column(name = "address_line2", length = 255)
    private String addressLine2;
    
    @Column(name = "address_line3", length = 255)
    private String addressLine3;
    
    @Column(name = "city", length = 100)
    private String city;
    
    @Column(name = "state", length = 100)
    private String state;
    
    @Column(name = "postal_code", length = 20)
    private String postalCode;
    
    @Column(name = "country_code", length = 10)
    private String countryCode;

    // ===== BANK DETAILS =====
    
    @Column(name = "bank_account_name", length = 200)
    private String bankAccountName;
    
    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;
    
    @Column(name = "bank_sort_code", length = 20)
    private String bankSortCode;
    
    @Column(name = "bank_name", length = 100)
    private String bankName;
    
    @Column(name = "iban", length = 50)
    private String iban;
    
    @Column(name = "swift_code", length = 20)
    private String swiftCode;

    // ===== PAYMENT SETTINGS =====
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;
    
    @Column(name = "is_active", length = 1, columnDefinition = "varchar(1) default 'Y'")
    private String isActive = "Y";

    // ===== AUDIT FIELDS =====
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by")
    private Long createdBy;
    
    @Column(name = "updated_by")
    private Long updatedBy;

    // ===== CONSTRUCTORS =====
    
    public Beneficiary() {}

    public Beneficiary(String payPropBeneficiaryId, String name, BeneficiaryType beneficiaryType) {
        this.payPropBeneficiaryId = payPropBeneficiaryId;
        this.name = name;
        this.beneficiaryType = beneficiaryType;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ===== GETTERS AND SETTERS =====
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPayPropBeneficiaryId() { return payPropBeneficiaryId; }
    public void setPayPropBeneficiaryId(String payPropBeneficiaryId) { this.payPropBeneficiaryId = payPropBeneficiaryId; }

    public String getPayPropCustomerId() { return payPropCustomerId; }
    public void setPayPropCustomerId(String payPropCustomerId) { this.payPropCustomerId = payPropCustomerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public BeneficiaryType getBeneficiaryType() { return beneficiaryType; }
    public void setBeneficiaryType(BeneficiaryType beneficiaryType) { this.beneficiaryType = beneficiaryType; }

    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

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

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getBankAccountName() { return bankAccountName; }
    public void setBankAccountName(String bankAccountName) { this.bankAccountName = bankAccountName; }

    public String getBankAccountNumber() { return bankAccountNumber; }
    public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }

    public String getBankSortCode() { return bankSortCode; }
    public void setBankSortCode(String bankSortCode) { this.bankSortCode = bankSortCode; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }

    public String getSwiftCode() { return swiftCode; }
    public void setSwiftCode(String swiftCode) { this.swiftCode = swiftCode; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getIsActive() { return isActive; }
    public void setIsActive(String isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    // ===== UTILITY METHODS =====
    
    /**
     * Get full name for display
     */
    public String getDisplayName() {
        if (accountType == AccountType.business && businessName != null) {
            return businessName;
        } else if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else {
            return name;
        }
    }
    
    /**
     * Get full address as string
     */
    public String getFullAddress() {
        StringBuilder address = new StringBuilder();
        if (addressLine1 != null) address.append(addressLine1);
        if (addressLine2 != null) address.append(", ").append(addressLine2);
        if (addressLine3 != null) address.append(", ").append(addressLine3);
        if (city != null) address.append(", ").append(city);
        if (postalCode != null) address.append(" ").append(postalCode);
        return address.toString();
    }

    @Override
    public String toString() {
        return "Beneficiary{" +
                "id=" + id +
                ", payPropBeneficiaryId='" + payPropBeneficiaryId + '\'' +
                ", name='" + name + '\'' +
                ", beneficiaryType=" + beneficiaryType +
                ", accountType=" + accountType +
                '}';
    }
}