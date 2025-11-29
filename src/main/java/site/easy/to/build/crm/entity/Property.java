// Property.java - Migration Safe Version with Portfolio/Block Support
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "properties")
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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
    
    @Column(name = "state", length = 50)
    @Size(max = 50)
    private String state;
    
    // Property Details
    @Column(name = "property_type")
    private String propertyType;

    private Integer bedrooms;
    private Integer bathrooms;

    @Column(name = "size_sqm", precision = 10, scale = 2)
    @DecimalMin(value = "0.00", message = "Size must not be negative")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal sizeSqm; // Property size in square meters

    private String furnished;
    
    @Column(name = "epc_rating")
    private String epcRating;
    
    @Column(name = "council_tax_band")
    private String councilTaxBand;
    
    // PayProp Financial Fields - CRITICAL
    @Column(name = "monthly_payment", precision = 10, scale = 2)
    @DecimalMin(value = "0.00", message = "Monthly payment must not be negative")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal monthlyPayment; // Can be null for vacant properties
    
    @Column(name = "deposit_amount", precision = 10, scale = 2)
    @DecimalMin(value = "0.00", message = "Deposit amount must not be negative")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal depositAmount; // Can be null
    
    @Column(name = "property_account_minimum_balance", precision = 10, scale = 2)
    @DecimalMin("0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal propertyAccountMinimumBalance = BigDecimal.ZERO;

    // NEW: Additional PayProp Financial Fields
    @Column(name = "commission_percentage", precision = 5, scale = 2)
    private BigDecimal commissionPercentage;

    @Column(name = "commission_amount", precision = 10, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "service_fee_percentage", precision = 5, scale = 2)
    private BigDecimal serviceFeePercentage;

    @Column(name = "service_fee_amount", precision = 10, scale = 2)
    private BigDecimal serviceFeeAmount;

    @Column(name = "account_balance", precision = 10, scale = 2)
    private BigDecimal accountBalance;

    // NEW: Block Property Flag
    // When true, this property represents a block itself (for financial tracking)
    // Block properties receive service charges and pay block-level expenses
    @Column(name = "is_block_property")
    private Boolean isBlockProperty = false;

    // NEW: Block Balance Contribution
    // When true, owner payments are routed through the block property balance
    @Column(name = "use_block_balance")
    private Boolean useBlockBalance = false;

    @Column(name = "balance_contribution_percentage", precision = 5, scale = 2)
    private BigDecimal balanceContributionPercentage = BigDecimal.ZERO; // Percentage of owner payment held as balance

    // NEW: PayProp Financial Tracking Date Range
    // Tracks the period during which PayProp managed financials for this property
    // This solves the problem of overlapping financial data from historical_transactions and PayProp
    @Column(name = "payprop_manages_financials")
    private Boolean payPropManagesFinancials = false;

    @Column(name = "payprop_financial_from")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate payPropFinancialFrom; // Date when PayProp started managing financials (based on first incoming payment)

    @Column(name = "payprop_financial_to")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate payPropFinancialTo; // Date when PayProp stopped managing financials (NULL = ongoing)

    @Column(name = "financial_tracking_manual_override")
    private Boolean financialTrackingManualOverride = false; // Prevents automatic sync from overwriting manual changes

    // Property Valuation and Purchase Information
    @Column(name = "purchase_price", precision = 12, scale = 2)
    @Digits(integer = 10, fraction = 2)
    private BigDecimal purchasePrice;
    
    @Column(name = "purchaser_costs", precision = 10, scale = 2)
    @Digits(integer = 8, fraction = 2)
    private BigDecimal purchaserCosts;
    
    @Column(name = "estimated_current_value", precision = 12, scale = 2)
    @Digits(integer = 10, fraction = 2)
    private BigDecimal estimatedCurrentValue;
    
    @Column(name = "purchase_date")
    private LocalDate purchaseDate;
    
    @Column(name = "last_valuation_date")
    private LocalDate lastValuationDate;

    @Column(name = "mortgage_amount", precision = 12, scale = 2)
    @Digits(integer = 10, fraction = 2)
    private BigDecimal mortgageAmount; // Outstanding mortgage amount

    @Column(name = "mortgage_interest_rate", precision = 5, scale = 2)
    @Digits(integer = 3, fraction = 2)
    private BigDecimal mortgageInterestRate; // Annual interest rate percentage

    @Column(name = "payprop_property_id", length = 100)
    private String payPropPropertyId;

    // Data Source Tracking - NEW
    @Column(name = "data_source", nullable = false)
    @Enumerated(EnumType.STRING)
    private DataSource dataSource = DataSource.MANUAL;

    @Column(name = "external_reference", length = 100)
    private String externalReference; // For uploaded data with original system IDs

    @Column(name = "upload_batch_id")
    private Long uploadBatchId; // Links to HistoricalDataUpload.uploadId

    // Block Relationships - Direct relationship maintained for hierarchy
    // Note: Portfolio relationships now handled via PropertyPortfolioAssignment junction table

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id")
    private Block block;

    // Letting Instructions - tracks all letting periods for this property
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"property", "leads", "viewings", "tasks", "invoices"})
    private java.util.List<LettingInstruction> lettingInstructions = new java.util.ArrayList<>();

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
    
    @Column(name = "verify_payments", length = 1) // PayProp verify_payments field
    private String verifyPayments = "N";
    
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
    private Long propertyOwnerId;

    // ============================================================
    // Property Vacancy Tracking Fields
    // ============================================================

    @Column(name = "occupancy_status")
    @Enumerated(EnumType.STRING)
    private OccupancyStatus occupancyStatus = OccupancyStatus.OCCUPIED;

    @Column(name = "notice_given_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate noticeGivenDate;

    @Column(name = "expected_vacancy_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expectedVacancyDate;

    @Column(name = "advertising_start_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate advertisingStartDate;

    @Column(name = "available_from_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate availableFromDate;

    @Column(name = "last_occupancy_change")
    private LocalDateTime lastOccupancyChange;

    // Constructors
    public Property() {}
    
    public Property(String propertyName) {
        this.propertyName = propertyName;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.enablePayments = "Y";
        this.holdOwnerFunds = "N";
        this.verifyPayments = "N";
        this.isArchived = "N";
        this.countryCode = "UK";
    }

    // Add these @Transient fields (not stored in database)
    @Transient
    private Integer emergencyMaintenanceCount;

    @Transient 
    private Integer urgentMaintenanceCount;

    @Transient
    private Integer routineMaintenanceCount;

    // Add these getter/setter methods
    public Integer getEmergencyMaintenanceCount() {
        return emergencyMaintenanceCount != null ? emergencyMaintenanceCount : 0;
    }

    public void setEmergencyMaintenanceCount(Integer emergencyMaintenanceCount) {
        this.emergencyMaintenanceCount = emergencyMaintenanceCount;
    }

    public Integer getUrgentMaintenanceCount() {
        return urgentMaintenanceCount != null ? urgentMaintenanceCount : 0;
    }

    public void setUrgentMaintenanceCount(Integer urgentMaintenanceCount) {
        this.urgentMaintenanceCount = urgentMaintenanceCount;
    }

    public Integer getRoutineMaintenanceCount() {
        return routineMaintenanceCount != null ? routineMaintenanceCount : 0;
    }

    public void setRoutineMaintenanceCount(Integer routineMaintenanceCount) {
        this.routineMaintenanceCount = routineMaintenanceCount;
    }

    public Integer getTotalMaintenanceCount() {
        return getEmergencyMaintenanceCount() + getUrgentMaintenanceCount() + getRoutineMaintenanceCount();
    }

    // Add this setter to handle PayProp data with blank names
    public void setPropertyName(String propertyName) {
        if (propertyName == null || propertyName.trim().isEmpty()) {
            this.propertyName = "Unnamed Property";
        } else {
            this.propertyName = propertyName.trim();
        }
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
    
    public Boolean getVerifyPaymentsAsBoolean() {
        return "Y".equalsIgnoreCase(verifyPayments);
    }
    
    public void setVerifyPaymentsFromBoolean(Boolean value) {
        this.verifyPayments = (value != null && value) ? "Y" : "N";
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
    
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    
    public String getPropertyType() { return propertyType; }
    public void setPropertyType(String propertyType) { this.propertyType = propertyType; }
    
    public Integer getBedrooms() { return bedrooms; }
    public void setBedrooms(Integer bedrooms) { this.bedrooms = bedrooms; }
    
    public Integer getBathrooms() { return bathrooms; }
    public void setBathrooms(Integer bathrooms) { this.bathrooms = bathrooms; }

    public BigDecimal getSizeSqm() { return sizeSqm; }
    public void setSizeSqm(BigDecimal sizeSqm) { this.sizeSqm = sizeSqm; }

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
    
    // NEW: Commission and PayProp getters/setters
    public BigDecimal getCommissionPercentage() { return commissionPercentage; }
    public void setCommissionPercentage(BigDecimal commissionPercentage) { this.commissionPercentage = commissionPercentage; }

    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }

    public BigDecimal getServiceFeePercentage() { return serviceFeePercentage; }
    public void setServiceFeePercentage(BigDecimal serviceFeePercentage) { this.serviceFeePercentage = serviceFeePercentage; }

    public BigDecimal getServiceFeeAmount() { return serviceFeeAmount; }
    public void setServiceFeeAmount(BigDecimal serviceFeeAmount) { this.serviceFeeAmount = serviceFeeAmount; }

    public BigDecimal getAccountBalance() { return accountBalance; }
    public void setAccountBalance(BigDecimal accountBalance) { this.accountBalance = accountBalance; }

    public Boolean getIsBlockProperty() { return isBlockProperty; }
    public void setIsBlockProperty(Boolean isBlockProperty) { this.isBlockProperty = isBlockProperty; }

    public Boolean getUseBlockBalance() { return useBlockBalance; }
    public void setUseBlockBalance(Boolean useBlockBalance) { this.useBlockBalance = useBlockBalance; }

    public BigDecimal getBalanceContributionPercentage() { return balanceContributionPercentage; }
    public void setBalanceContributionPercentage(BigDecimal balanceContributionPercentage) { this.balanceContributionPercentage = balanceContributionPercentage; }

    public boolean isUsingBlockBalance() {
        return useBlockBalance != null && useBlockBalance;
    }

    // PayProp Financial Tracking Date Range Getters/Setters
    public Boolean getPayPropManagesFinancials() { return payPropManagesFinancials; }
    public void setPayPropManagesFinancials(Boolean payPropManagesFinancials) {
        this.payPropManagesFinancials = payPropManagesFinancials;
    }

    public LocalDate getPayPropFinancialFrom() { return payPropFinancialFrom; }
    public void setPayPropFinancialFrom(LocalDate payPropFinancialFrom) {
        this.payPropFinancialFrom = payPropFinancialFrom;
    }

    public LocalDate getPayPropFinancialTo() { return payPropFinancialTo; }
    public void setPayPropFinancialTo(LocalDate payPropFinancialTo) {
        this.payPropFinancialTo = payPropFinancialTo;
    }

    public Boolean getFinancialTrackingManualOverride() { return financialTrackingManualOverride; }
    public void setFinancialTrackingManualOverride(Boolean financialTrackingManualOverride) {
        this.financialTrackingManualOverride = financialTrackingManualOverride;
    }

    // Property Valuation and Purchase Information Getters/Setters
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }
    
    public BigDecimal getPurchaserCosts() { return purchaserCosts; }
    public void setPurchaserCosts(BigDecimal purchaserCosts) { this.purchaserCosts = purchaserCosts; }
    
    public BigDecimal getEstimatedCurrentValue() { return estimatedCurrentValue; }
    public void setEstimatedCurrentValue(BigDecimal estimatedCurrentValue) { this.estimatedCurrentValue = estimatedCurrentValue; }
    
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    
    public LocalDate getLastValuationDate() { return lastValuationDate; }
    public void setLastValuationDate(LocalDate lastValuationDate) { this.lastValuationDate = lastValuationDate; }

    public BigDecimal getMortgageAmount() { return mortgageAmount; }
    public void setMortgageAmount(BigDecimal mortgageAmount) { this.mortgageAmount = mortgageAmount; }

    public BigDecimal getMortgageInterestRate() { return mortgageInterestRate; }
    public void setMortgageInterestRate(BigDecimal mortgageInterestRate) { this.mortgageInterestRate = mortgageInterestRate; }

    public String getPayPropPropertyId() { return payPropPropertyId; }
    public void setPayPropPropertyId(String payPropPropertyId) { this.payPropPropertyId = payPropPropertyId; }
    
    // Block getters/setters - Direct relationship maintained for hierarchy
    // ❌ DEPRECATED: Direct portfolio getters/setters - use PortfolioAssignmentService for portfolio relationships
    @Deprecated
    public Portfolio getPortfolio() { 
        // Return null since direct assignment is disabled - use PortfolioAssignmentService.findPrimaryPortfolioForProperty()
        return null; 
    }
    
    @Deprecated
    public void setPortfolio(Portfolio portfolio) { 
        // No-op since direct assignment is disabled - use PortfolioAssignmentService.assignProperty()
        // This method is kept only for compilation compatibility
    }

    public Block getBlock() { return block; }
    public void setBlock(Block block) { this.block = block; }

    public java.util.List<LettingInstruction> getLettingInstructions() { return lettingInstructions; }
    public void setLettingInstructions(java.util.List<LettingInstruction> lettingInstructions) {
        this.lettingInstructions = lettingInstructions;
    }

    // Helper method to add a letting instruction
    public void addLettingInstruction(LettingInstruction instruction) {
        lettingInstructions.add(instruction);
        instruction.setProperty(this);
    }

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
    
    public String getVerifyPayments() { return verifyPayments; }
    public void setVerifyPayments(String verifyPayments) { this.verifyPayments = verifyPayments; }
    
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
    
    public Long getPropertyOwnerId() { return propertyOwnerId; }
    public void setPropertyOwnerId(Long propertyOwnerId) { this.propertyOwnerId = propertyOwnerId; }

    // Data Source Tracking getters and setters
    public DataSource getDataSource() { return dataSource; }
    public void setDataSource(DataSource dataSource) { this.dataSource = dataSource; }

    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }

    public Long getUploadBatchId() { return uploadBatchId; }
    public void setUploadBatchId(Long uploadBatchId) { this.uploadBatchId = uploadBatchId; }
        
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

    // NEW: Commission utility methods
    /**
     * Check if property has commission rate configured
     */
    public boolean hasCommissionRate() {
        return commissionPercentage != null && commissionPercentage.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Get commission rate as percentage (e.g., 10.5 for 10.5%)
     */
    public double getCommissionRateAsPercent() {
        return hasCommissionRate() ? commissionPercentage.doubleValue() : 0.0;
    }

    /**
     * Calculate commission amount for a given rent amount
     */
    public BigDecimal calculateCommission(BigDecimal rentAmount) {
        if (!hasCommissionRate() || rentAmount == null) {
            return BigDecimal.ZERO;
        }
        return rentAmount.multiply(commissionPercentage)
                .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * @deprecated Use PropertyService.isPropertyOccupied() instead for accurate PayProp-based occupancy status
     * This legacy method uses status field which may not be accurate for PayProp properties
     */
    @Deprecated(since = "1.0", forRemoval = true)
    public Boolean isOccupied() {
        // LEGACY METHOD: Uses status field - may not be accurate for PayProp properties
        // For accurate occupancy status, use PropertyService.isPropertyOccupied(payPropId)
        if (status != null) {
            return "occupied".equalsIgnoreCase(status) || 
                "rented".equalsIgnoreCase(status) || 
                "let".equalsIgnoreCase(status);
        }
        // Fallback: if property is active and has monthly payment, likely occupied
        return isActive() && monthlyPayment != null && monthlyPayment.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if property is set up for PayProp sync
     */
    public boolean isPayPropConfigured() {
        return payPropPropertyId != null && !payPropPropertyId.trim().isEmpty();
    }

    // Property Valuation Helper Methods
    /**
     * Calculate total acquisition cost (purchase price + purchaser costs)
     */
    public BigDecimal getTotalAcquisitionCost() {
        BigDecimal purchase = purchasePrice != null ? purchasePrice : BigDecimal.ZERO;
        BigDecimal costs = purchaserCosts != null ? purchaserCosts : BigDecimal.ZERO;
        return purchase.add(costs);
    }

    /**
     * Calculate estimated capital gain (current value - total acquisition cost)
     */
    public BigDecimal getEstimatedCapitalGain() {
        if (estimatedCurrentValue == null) {
            return BigDecimal.ZERO;
        }
        return estimatedCurrentValue.subtract(getTotalAcquisitionCost());
    }

    /**
     * Calculate estimated capital gain percentage
     */
    public BigDecimal getEstimatedCapitalGainPercentage() {
        BigDecimal totalCost = getTotalAcquisitionCost();
        if (totalCost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getEstimatedCapitalGain()
                .multiply(BigDecimal.valueOf(100))
                .divide(totalCost, 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculate annual rental yield based on monthly payment
     */
    public BigDecimal getAnnualRentalYield() {
        if (monthlyPayment == null || getTotalAcquisitionCost().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal annualRent = monthlyPayment.multiply(BigDecimal.valueOf(12));
        return annualRent
                .multiply(BigDecimal.valueOf(100))
                .divide(getTotalAcquisitionCost(), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculate current rental yield based on estimated current value
     */
    public BigDecimal getCurrentRentalYield() {
        if (monthlyPayment == null || estimatedCurrentValue == null || 
            estimatedCurrentValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal annualRent = monthlyPayment.multiply(BigDecimal.valueOf(12));
        return annualRent
                .multiply(BigDecimal.valueOf(100))
                .divide(estimatedCurrentValue, 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Check if property has valuation data
     */
    public boolean hasValuationData() {
        return purchasePrice != null || estimatedCurrentValue != null;
    }

    // NEW: Advanced ROI and Yield Calculations

    /**
     * Calculate annual mortgage payment
     */
    public BigDecimal getAnnualMortgagePayment() {
        if (mortgageAmount == null || mortgageInterestRate == null ||
            mortgageAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        // Simple interest calculation: (mortgage * rate) / 100
        return mortgageAmount.multiply(mortgageInterestRate)
                .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculate gross rental yield (annual rent / property value) * 100
     * Uses purchase price as property value
     */
    public BigDecimal getGrossYield() {
        return getAnnualRentalYield(); // Already implemented
    }

    /**
     * Calculate net rental yield (annual rent - costs) / property value * 100
     * Costs = mortgage interest + estimated annual expenses (15% of rent as default)
     */
    public BigDecimal getNetYield() {
        if (monthlyPayment == null || getTotalAcquisitionCost().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal annualRent = monthlyPayment.multiply(BigDecimal.valueOf(12));
        BigDecimal mortgageInterest = getAnnualMortgagePayment();

        // Estimate annual expenses as 15% of annual rent (maintenance, insurance, etc.)
        BigDecimal estimatedExpenses = annualRent.multiply(BigDecimal.valueOf(0.15));

        BigDecimal netIncome = annualRent.subtract(mortgageInterest).subtract(estimatedExpenses);

        return netIncome
                .multiply(BigDecimal.valueOf(100))
                .divide(getTotalAcquisitionCost(), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculate cash-on-cash return (annual cash flow / cash invested) * 100
     * Cash invested = purchase price + costs - mortgage
     */
    public BigDecimal getCashOnCashReturn() {
        BigDecimal totalCost = getTotalAcquisitionCost();
        BigDecimal mortgage = mortgageAmount != null ? mortgageAmount : BigDecimal.ZERO;
        BigDecimal cashInvested = totalCost.subtract(mortgage);

        if (cashInvested.compareTo(BigDecimal.ZERO) == 0 || monthlyPayment == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal annualRent = monthlyPayment.multiply(BigDecimal.valueOf(12));
        BigDecimal mortgageInterest = getAnnualMortgagePayment();
        BigDecimal estimatedExpenses = annualRent.multiply(BigDecimal.valueOf(0.15));

        BigDecimal annualCashFlow = annualRent.subtract(mortgageInterest).subtract(estimatedExpenses);

        return annualCashFlow
                .multiply(BigDecimal.valueOf(100))
                .divide(cashInvested, 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculate total ROI including capital appreciation
     * (Annual income + capital gain) / investment * 100
     */
    public BigDecimal getTotalROI() {
        BigDecimal totalCost = getTotalAcquisitionCost();
        if (totalCost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal annualIncome = monthlyPayment != null ?
            monthlyPayment.multiply(BigDecimal.valueOf(12)) : BigDecimal.ZERO;
        BigDecimal capitalGain = getEstimatedCapitalGain();

        BigDecimal totalReturn = annualIncome.add(capitalGain);

        return totalReturn
                .multiply(BigDecimal.valueOf(100))
                .divide(totalCost, 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculate loan-to-value ratio
     */
    public BigDecimal getLoanToValueRatio() {
        if (mortgageAmount == null || estimatedCurrentValue == null ||
            estimatedCurrentValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return mortgageAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(estimatedCurrentValue, 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculate equity in property
     */
    public BigDecimal getEquity() {
        if (estimatedCurrentValue == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal mortgage = mortgageAmount != null ? mortgageAmount : BigDecimal.ZERO;
        return estimatedCurrentValue.subtract(mortgage);
    }

    // NEW: PayProp Financial Tracking Utility Methods
    /**
     * Check if PayProp manages financials for this property on a specific date
     * @param date The date to check (if null, checks current date)
     * @return true if PayProp was/is managing financials on that date
     */
    public boolean isPayPropManagingFinancialsOn(LocalDate date) {
        if (payPropManagesFinancials == null || !payPropManagesFinancials) {
            return false;
        }

        LocalDate checkDate = (date != null) ? date : LocalDate.now();

        // Check if date falls within the PayProp management period
        boolean afterStart = (payPropFinancialFrom == null) ||
                            !checkDate.isBefore(payPropFinancialFrom);
        boolean beforeEnd = (payPropFinancialTo == null) ||
                           !checkDate.isAfter(payPropFinancialTo);

        return afterStart && beforeEnd;
    }

    /**
     * Check if PayProp currently manages financials for this property
     * @return true if PayProp is actively managing financials now
     */
    public boolean isPayPropCurrentlyManagingFinancials() {
        return isPayPropManagingFinancialsOn(LocalDate.now());
    }

    /**
     * Get the financial data source to use for a specific date
     * @param date The date to check
     * @return "PAYPROP" if PayProp manages that period, "HISTORICAL" otherwise
     */
    public String getFinancialDataSourceFor(LocalDate date) {
        return isPayPropManagingFinancialsOn(date) ? "PAYPROP" : "HISTORICAL";
    }

    /**
     * Check if financial tracking dates are manually set
     * @return true if user manually set the dates (prevents auto-sync overwrites)
     */
    public boolean hasManualFinancialTracking() {
        return financialTrackingManualOverride != null && financialTrackingManualOverride;
    }

    /**
     * Get a human-readable description of the PayProp financial tracking status
     * @return Status string like "PayProp managed from 2025-06-17 onwards" or "Not managed by PayProp"
     */
    public String getFinancialTrackingStatusDescription() {
        if (payPropManagesFinancials == null || !payPropManagesFinancials) {
            return "Not managed by PayProp";
        }

        StringBuilder status = new StringBuilder("PayProp managed");

        if (payPropFinancialFrom != null) {
            status.append(" from ").append(payPropFinancialFrom);
        }

        if (payPropFinancialTo != null) {
            status.append(" to ").append(payPropFinancialTo);
        } else {
            status.append(" onwards");
        }

        if (hasManualFinancialTracking()) {
            status.append(" (manually set)");
        }

        return status.toString();
    }

    // ============================================================
    // Getters and Setters for Vacancy Tracking Fields
    // ============================================================

    public OccupancyStatus getOccupancyStatus() {
        return occupancyStatus;
    }

    public void setOccupancyStatus(OccupancyStatus occupancyStatus) {
        this.occupancyStatus = occupancyStatus;
        this.lastOccupancyChange = LocalDateTime.now();
    }

    public LocalDate getNoticeGivenDate() {
        return noticeGivenDate;
    }

    public void setNoticeGivenDate(LocalDate noticeGivenDate) {
        this.noticeGivenDate = noticeGivenDate;
    }

    public LocalDate getExpectedVacancyDate() {
        return expectedVacancyDate;
    }

    public void setExpectedVacancyDate(LocalDate expectedVacancyDate) {
        this.expectedVacancyDate = expectedVacancyDate;
    }

    public LocalDate getAdvertisingStartDate() {
        return advertisingStartDate;
    }

    public void setAdvertisingStartDate(LocalDate advertisingStartDate) {
        this.advertisingStartDate = advertisingStartDate;
    }

    public LocalDate getAvailableFromDate() {
        return availableFromDate;
    }

    public void setAvailableFromDate(LocalDate availableFromDate) {
        this.availableFromDate = availableFromDate;
    }

    public LocalDateTime getLastOccupancyChange() {
        return lastOccupancyChange;
    }

    public void setLastOccupancyChange(LocalDateTime lastOccupancyChange) {
        this.lastOccupancyChange = lastOccupancyChange;
    }

    // ============================================================
    // Helper Methods for Vacancy Tracking
    // ============================================================

    /**
     * Check if property is available for new lettings
     */
    public boolean isAvailableForLetting() {
        return occupancyStatus != null && occupancyStatus.isAvailableForLetting();
    }

    /**
     * Check if property requires marketing attention
     */
    public boolean requiresMarketingAttention() {
        return occupancyStatus != null && occupancyStatus.requiresMarketingAttention();
    }

    /**
     * Mark notice as given
     */
    public void markNoticeGiven(LocalDate noticeDate, LocalDate expectedVacancy) {
        this.occupancyStatus = OccupancyStatus.NOTICE_GIVEN;
        this.noticeGivenDate = noticeDate;
        this.expectedVacancyDate = expectedVacancy;
        this.lastOccupancyChange = LocalDateTime.now();
    }

    /**
     * Start advertising the property
     */
    public void startAdvertising() {
        this.occupancyStatus = OccupancyStatus.ADVERTISING;
        this.advertisingStartDate = LocalDate.now();
        this.lastOccupancyChange = LocalDateTime.now();
    }

    /**
     * Mark property as available
     */
    public void markAvailable(LocalDate availableFrom) {
        this.occupancyStatus = OccupancyStatus.AVAILABLE;
        this.availableFromDate = availableFrom;
        this.lastOccupancyChange = LocalDateTime.now();
    }

    /**
     * Mark property as occupied (after tenant move-in)
     */
    public void markOccupied() {
        this.occupancyStatus = OccupancyStatus.OCCUPIED;
        this.noticeGivenDate = null;
        this.expectedVacancyDate = null;
        this.advertisingStartDate = null;
        this.availableFromDate = null;
        this.lastOccupancyChange = LocalDateTime.now();
    }

    /**
     * Get days until expected vacancy
     */
    public Long getDaysUntilVacancy() {
        if (expectedVacancyDate == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expectedVacancyDate);
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
        if (verifyPayments == null) {
            verifyPayments = "N";
        }
        if (isArchived == null) {
            isArchived = "N";
        }
        if (countryCode == null) {
            countryCode = "UK";
        }
        if (occupancyStatus == null) {
            occupancyStatus = OccupancyStatus.OCCUPIED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}