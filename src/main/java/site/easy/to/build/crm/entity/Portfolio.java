// Portfolio.java - Main portfolio entity with PayProp two-way sync support
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "portfolios")
public class Portfolio {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false, length = 255)
    @NotBlank
    @Size(min = 2, max = 255)
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "portfolio_type")
    private PortfolioType portfolioType;
    
    // PayProp Integration Fields - CRITICAL for two-way sync
    @Column(name = "payprop_tags", columnDefinition = "TEXT")
    private String payPropTags; // Keep temporarily for backward compatibility
    
    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PayPropTagLink> payPropTagLinks = new ArrayList<>();
    
    @Column(name = "payprop_tag_names", columnDefinition = "TEXT") 
    private String payPropTagNames; // Human-readable tag names for UI
    
    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;
    
    @Column(name = "sync_status")
    @Enumerated(EnumType.STRING)
    private SyncStatus syncStatus = SyncStatus.pending;
    
    // Ownership and Access Control
    @Column(name = "created_by", nullable = false)
    private Long createdBy; // User who created this portfolio
    
    @Column(name = "property_owner_id")
    private Integer propertyOwnerId; // NULL = shared/employee portfolio
    
    @Column(name = "is_shared")
    private String isShared = "N"; // Y = visible to all employees, N = owner-specific
    
    @Column(name = "is_active")
    private String isActive = "Y";
    
    // Portfolio Settings
    @Column(name = "auto_assign_new_properties")
    private String autoAssignNewProperties = "N"; // Auto-assign based on rules
    
    @Column(name = "assignment_rules", columnDefinition = "TEXT")
    private String assignmentRules; // JSON rules for auto-assignment
    
    // Financial Tracking
    @Column(name = "target_monthly_income", precision = 12, scale = 2)
    private BigDecimal targetMonthlyIncome;
    
    @Column(name = "target_occupancy_rate", precision = 5, scale = 2)
    private BigDecimal targetOccupancyRate;
    
    // Display Settings
    @Column(name = "color_code", length = 7)
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    private String colorCode; // Hex color for UI display
    
    @Column(name = "display_order")
    private Integer displayOrder = 0;
    
    // Audit fields
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "updated_by")
    private Long updatedBy;
    
    // Relationships
    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Block> blocks;
    
    // Note: Property relationships now handled via PropertyPortfolioAssignment junction table
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_owner_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer propertyOwner;
    
    // Constructors
    public Portfolio() {}
    
    public Portfolio(String name, Long createdBy) {
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public Portfolio(String name, Long createdBy, Integer propertyOwnerId) {
        this(name, createdBy);
        this.propertyOwnerId = propertyOwnerId;
        this.isShared = "N";
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public PortfolioType getPortfolioType() { return portfolioType; }
    public void setPortfolioType(PortfolioType portfolioType) { this.portfolioType = portfolioType; }
    
    // FIXED: Add the missing getter/setter that was referenced
    public String getPayPropTags() { return payPropTags; }
    public void setPayPropTags(String payPropTags) { this.payPropTags = payPropTags; }
    
    public List<PayPropTagLink> getPayPropTagLinks() { return payPropTagLinks; }
    public void setPayPropTagLinks(List<PayPropTagLink> payPropTagLinks) { this.payPropTagLinks = payPropTagLinks; }
    
    public String getPayPropTagNames() { return payPropTagNames; }
    public void setPayPropTagNames(String payPropTagNames) { this.payPropTagNames = payPropTagNames; }
    
    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    
    public SyncStatus getSyncStatus() { return syncStatus; }
    public void setSyncStatus(SyncStatus syncStatus) { this.syncStatus = syncStatus; }
    
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    
    public Integer getPropertyOwnerId() { return propertyOwnerId; }
    public void setPropertyOwnerId(Integer propertyOwnerId) { this.propertyOwnerId = propertyOwnerId; }
    
    public String getIsShared() { return isShared; }
    public void setIsShared(String isShared) { this.isShared = isShared; }
    
    public String getIsActive() { return isActive; }
    public void setIsActive(String isActive) { this.isActive = isActive; }
    
    public String getAutoAssignNewProperties() { return autoAssignNewProperties; }
    public void setAutoAssignNewProperties(String autoAssignNewProperties) { this.autoAssignNewProperties = autoAssignNewProperties; }
    
    public String getAssignmentRules() { return assignmentRules; }
    public void setAssignmentRules(String assignmentRules) { this.assignmentRules = assignmentRules; }
    
    public BigDecimal getTargetMonthlyIncome() { return targetMonthlyIncome; }
    public void setTargetMonthlyIncome(BigDecimal targetMonthlyIncome) { this.targetMonthlyIncome = targetMonthlyIncome; }
    
    public BigDecimal getTargetOccupancyRate() { return targetOccupancyRate; }
    public void setTargetOccupancyRate(BigDecimal targetOccupancyRate) { this.targetOccupancyRate = targetOccupancyRate; }
    
    public String getColorCode() { return colorCode; }
    public void setColorCode(String colorCode) { this.colorCode = colorCode; }
    
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    
    public List<Block> getBlocks() { return blocks; }
    public void setBlocks(List<Block> blocks) { this.blocks = blocks; }
    
    // Note: Property getters/setters removed - use PortfolioAssignmentService for property relationships
    
    public Customer getPropertyOwner() { return propertyOwner; }
    public void setPropertyOwner(Customer propertyOwner) { this.propertyOwner = propertyOwner; }
    
    // Business Logic Methods
    public boolean isSharedPortfolio() {
        return "Y".equalsIgnoreCase(isShared);
    }
    
    public boolean isActive() {
        return "Y".equalsIgnoreCase(isActive);
    }
    
    public boolean isOwnerSpecific() {
        return propertyOwnerId != null;
    }
    
    public boolean isAutoAssignEnabled() {
        return "Y".equalsIgnoreCase(autoAssignNewProperties);
    }
    
    // FIXED: Update the isSyncedWithPayProp method to handle both approaches
    public boolean isSyncedWithPayProp() {
        // Check either the old string field or new relationship approach
        boolean hasStringTags = payPropTags != null && !payPropTags.trim().isEmpty();
        boolean hasTagLinks = payPropTagLinks != null && !payPropTagLinks.isEmpty();
        return syncStatus == SyncStatus.synced && (hasStringTags || hasTagLinks);
    }
    
    // FIXED: Changed SyncStatus.error to SyncStatus.failed
    public boolean needsPayPropSync() {
        return syncStatus == SyncStatus.pending || syncStatus == SyncStatus.failed;
    }
    
    // PayProp Tag Utilities
    public List<String> getPayPropTagList() {
        if (payPropTags == null || payPropTags.trim().isEmpty()) {
            return List.of();
        }
        return List.of(payPropTags.split(","));
    }
    
    public void addPayPropTag(String tagId) {
        if (payPropTags == null || payPropTags.trim().isEmpty()) {
            payPropTags = tagId;
        } else {
            List<String> tags = new java.util.ArrayList<>(getPayPropTagList());
            if (!tags.contains(tagId)) {
                tags.add(tagId);
                payPropTags = String.join(",", tags);
            }
        }
    }
    
    public void removePayPropTag(String tagId) {
        if (payPropTags != null) {
            List<String> tags = new java.util.ArrayList<>(getPayPropTagList());
            tags.remove(tagId);
            payPropTags = tags.isEmpty() ? null : String.join(",", tags);
        }
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (displayOrder == null) {
            displayOrder = 0;
        }
        if (syncStatus == null) {
            syncStatus = SyncStatus.pending;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}