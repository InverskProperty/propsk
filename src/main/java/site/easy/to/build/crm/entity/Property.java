// Property.java - Migration Safe Version with Portfolio/Block Support
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "properties")
public class Property {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // PayProp Integration Fields - REQUIRED
    @Column(name = "payprop_id", length = 32, unique = true)
    @Pattern(regexp = "^[a-zA-Z0-9]+$")
    private String payPropId;
    
    @Column(name = "customer_id", length = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$")
    private String customerId; // Internal ID for PayProp sync
    
    @Column(name = "customer_reference", length = 50)
    @Size(max = 50)
    private String customerReference; // Visible identifier in PayProp
    
    // Required Property Information
    @Column(name = "property_name", nullable = false, length = 255)
    @NotBlank(message = "Property name is required")
    @Pattern(regexp = "^.*\\S.*$", message = "Property name must contain non-whitespace characters")
    private String propertyName;

    // Add this setter to handle PayProp data with blank names
    public void setPropertyName(String propertyName) {
        if (propertyName == null || propertyName.trim().isEmpty()) {
            this.propertyName = "Unnamed Property";
        } else {
            this.propertyName = propertyName.trim();
        }
    }

    // Address fields - PayProp expects separate fields
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
    
    @Column(name = "country_code", length = 2)
    private String countryCode = "UK"; // PayProp default
    
    @Column(name = "postcode", length = 10)
    @Size(max = 10)
    private String postcode;
    
    // Property Details
    @Column(name = "property_type")
    private String propertyType;
    
    private Integer bedrooms;
    private Integer bathrooms;
    private String furnished;
    
    @Column(name = "epc_rating")
    private String epcRating;
    
    @Column(name = "council_tax_band")
    private String councilTaxBand;
    
    // PayProp Financial Fields - CRITICAL
    @Column(name = "monthly_payment", precision = 10, scale = 2)
    @DecimalMin("0.01")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal monthlyPayment; // REQUIRED by PayProp if settings provided
    
    @Column(name = "deposit_amount", precision = 10, scale = 2)
    @DecimalMin("0.01")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal depositAmount;
    
    @Column(name = "property_account_minimum_balance", precision = 10, scale = 2)
    @DecimalMin("0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal propertyAccountMinimumBalance = BigDecimal.ZERO;

    // Portfolio and Block Relationships - NEW FIELDS
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id")
    private Block block;

    @Column(name = "portfolio_tags", columnDefinition = "TEXT")
    private String portfolioTags; // Local tags for portfolio assignment

    @Column(name = "portfolio_assignment_date")
    private LocalDateTime portfolioAssignmentDate;

    @Column(name = "block_assignment_date")
    private LocalDateTime blockAssignmentDate;
    
    // 🔧 FIXED: Keep as String for now to avoid migration issues
    @Column(name = "enable_payments", length = 1) // Changed back to String
    private String enablePayments = "Y";
    
    @Column(name = "hold_owner_funds", length = 1) // Changed back to String  
    private String holdOwnerFunds = "N";
    
    @Column(name = "is_archived", length = 1) // Changed back to String
    private String isArchived = "N";
    
    // PayProp Management Fields
    @Column(name = "agent_name", length = 50)
    private String agentName; // For portfolio subdivision
    
    @Column(name = "service_level")
    private String serviceLevel;
    
    @Column(name = "listed_from")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate listedFrom;
    
    @Column(name = "listed_until")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate listedUntil;
    
    // Legacy fields - Keep for now but migrate away from
    @Column(name = "status") // Will be phased out
    private String status;
    
    @Column(name = "allow_payments") // Will be phased out
    private String allowPayments;
    
    @Column(name = "approval_required")
    private String approvalRequired;
    
    @Column(name = "monthly_payment_required", precision = 10, scale = 2)
    private BigDecimal monthlyPaymentRequired;
    
    private String tags;
    
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;
    
    // Audit fields
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by")
    private Long createdBy;
    
    @Column(name = "updated_by")
    private Long updatedBy;
    
    // Foreign key (keeping original structure)
    @Column(name = "property_owner_id")
    private Integer propertyOwnerId;
    
    // Constructors
    public Property() {}
    
    public Property(String propertyName) {
        this.propertyName = propertyName;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.enablePayments = "Y";
        this.holdOwnerFunds = "N";
        this.isArchived = "N";
        this.countryCode = "UK";
    }
    
    // 🔧 FIXED: PayProp-compatible getters that convert String to Boolean
    public Boolean getEnablePaymentsAsBoolean() {
        return "Y".equalsIgnoreCase(enablePayments);
    }
    
    public void setEnablePaymentsFromBoolean(Boolean value) {
        this.enablePayments = (value != null && value) ? "Y" : "N";
    }
    
    public Boolean getHoldOwnerFundsAsBoolean() {
        return "Y".equalsIgnoreCase(holdOwnerFunds);
    }
    
    public void setHoldOwnerFundsFromBoolean(Boolean value) {
        this.holdOwnerFunds = (value != null && value) ? "Y" : "N";
    }
    
    public Boolean getIsArchivedAsBoolean() {
        return "Y".equalsIgnoreCase(isArchived);
    }
    
    public void setIsArchivedFromBoolean(Boolean value) {
        this.isArchived = (value != null && value) ? "Y" : "N";
    }
    
    // Basic getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getPayPropId() { return payPropId; }
    public void setPayPropId(String payPropId) { this.payPropId = payPropId; }
    
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    
    public String getCustomerReference() { return customerReference; }
    public void setCustomerReference(String customerReference) { this.customerReference = customerReference; }
    
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
    
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    
    public String getPostcode() { return postcode; }
    public void setPostcode(String postcode) { this.postcode = postcode; }
    
    public String getPropertyType() { return propertyType; }
    public void setPropertyType(String propertyType) { this.propertyType = propertyType; }
    
    public Integer getBedrooms() { return bedrooms; }
    public void setBedrooms(Integer bedrooms) { this.bedrooms = bedrooms; }
    
    public Integer getBathrooms() { return bathrooms; }
    public void setBathrooms(Integer bathrooms) { this.bathrooms = bathrooms; }
    
    public String getFurnished() { return furnished; }
    public void setFurnished(String furnished) { this.furnished = furnished; }
    
    public String getEpcRating() { return epcRating; }
    public void setEpcRating(String epcRating) { this.epcRating = epcRating; }
    
    public String getCouncilTaxBand() { return councilTaxBand; }
    public void setCouncilTaxBand(String councilTaxBand) { this.councilTaxBand = councilTaxBand; }
    
    public BigDecimal getMonthlyPayment() { return monthlyPayment; }
    public void setMonthlyPayment(BigDecimal monthlyPayment) { this.monthlyPayment = monthlyPayment; }
    
    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }
    
    public BigDecimal getPropertyAccountMinimumBalance() { return propertyAccountMinimumBalance; }
    public void setPropertyAccountMinimumBalance(BigDecimal propertyAccountMinimumBalance) { this.propertyAccountMinimumBalance = propertyAccountMinimumBalance; }
    
    // Portfolio and Block getters/setters - NEW
    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }

    public Block getBlock() { return block; }
    public void setBlock(Block block) { this.block = block; }

    public String getPortfolioTags() { return portfolioTags; }
    public void setPortfolioTags(String portfolioTags) { this.portfolioTags = portfolioTags; }

    public LocalDateTime getPortfolioAssignmentDate() { return portfolioAssignmentDate; }
    public void setPortfolioAssignmentDate(LocalDateTime portfolioAssignmentDate) { 
        this.portfolioAssignmentDate = portfolioAssignmentDate; 
    }

    public LocalDateTime getBlockAssignmentDate() { return blockAssignmentDate; }
    public void setBlockAssignmentDate(LocalDateTime blockAssignmentDate) { 
        this.blockAssignmentDate = blockAssignmentDate; 
    }
    
    // String getters/setters for database compatibility
    public String getEnablePayments() { return enablePayments; }
    public void setEnablePayments(String enablePayments) { this.enablePayments = enablePayments; }
    
    public String getHoldOwnerFunds() { return holdOwnerFunds; }
    public void setHoldOwnerFunds(String holdOwnerFunds) { this.holdOwnerFunds = holdOwnerFunds; }
    
    public String getIsArchived() { return isArchived; }
    public void setIsArchived(String isArchived) { this.isArchived = isArchived; }
    
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    
    public String getServiceLevel() { return serviceLevel; }
    public void setServiceLevel(String serviceLevel) { this.serviceLevel = serviceLevel; }

    // This getter is MISSING and needs to be added back
    public String getPropertyName() { 
        return propertyName; 
    }
    
    public LocalDate getListedFrom() { return listedFrom; }
    public void setListedFrom(LocalDate listedFrom) { this.listedFrom = listedFrom; }
    
    public LocalDate getListedUntil() { return listedUntil; }
    public void setListedUntil(LocalDate listedUntil) { this.listedUntil = listedUntil; }
    
    // Legacy getters/setters - keep for compatibility
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getAllowPayments() { return allowPayments; }
    public void setAllowPayments(String allowPayments) { this.allowPayments = allowPayments; }
    
    public String getApprovalRequired() { return approvalRequired; }
    public void setApprovalRequired(String approvalRequired) { this.approvalRequired = approvalRequired; }
    
    public BigDecimal getMonthlyPaymentRequired() { return monthlyPaymentRequired; }
    public void setMonthlyPaymentRequired(BigDecimal monthlyPaymentRequired) { this.monthlyPaymentRequired = monthlyPaymentRequired; }
    
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    
    public Integer getPropertyOwnerId() { return propertyOwnerId; }
    public void setPropertyOwnerId(Integer propertyOwnerId) { this.propertyOwnerId = propertyOwnerId; }
        
    // PayProp Utility Methods
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
    
    public boolean isActive() {
        return !"Y".equalsIgnoreCase(isArchived);
    }
    
    public boolean isReadyForPayPropSync() {
        return propertyName != null && !propertyName.trim().isEmpty() &&
               customerId != null && !customerId.trim().isEmpty() &&
               monthlyPayment != null && monthlyPayment.compareTo(BigDecimal.ZERO) > 0;
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (listedFrom == null) {
            listedFrom = LocalDate.now();
        }
        if (customerId == null) {
            customerId = "PROP_" + System.currentTimeMillis();
        }
        if (enablePayments == null) {
            enablePayments = "Y";
        }
        if (holdOwnerFunds == null) {
            holdOwnerFunds = "N";
        }
        if (isArchived == null) {
            isArchived = "N";
        }
        if (countryCode == null) {
            countryCode = "UK";
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}