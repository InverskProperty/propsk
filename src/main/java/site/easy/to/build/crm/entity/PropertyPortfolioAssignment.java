// PropertyPortfolioAssignment.java - Junction table for Property-Portfolio many-to-many relationships
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "property_portfolio_assignments", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"property_id", "portfolio_id", "assignment_type"}))
public class PropertyPortfolioAssignment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id")
    private Block block;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false)
    private PortfolioAssignmentType assignmentType;
    
    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;
    
    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;
    
    @Column(name = "notes", length = 500)
    private String notes;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "display_order")
    private Integer displayOrder = 0;
    
    // PayProp sync tracking
    @Column(name = "sync_status")
    @Enumerated(EnumType.STRING)
    private SyncStatus syncStatus = SyncStatus.pending;
    
    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;
    
    // Audit fields
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "updated_by")
    private Long updatedBy;
    
    // Constructors
    public PropertyPortfolioAssignment() {}
    
    public PropertyPortfolioAssignment(Property property, Portfolio portfolio, 
                                     PortfolioAssignmentType assignmentType, Long assignedBy) {
        this.property = property;
        this.portfolio = portfolio;
        this.assignmentType = assignmentType;
        this.assignedBy = assignedBy;
        this.assignedAt = LocalDateTime.now();
        this.isActive = true;
        this.syncStatus = SyncStatus.pending;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }
    
    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }
    
    public Block getBlock() { return block; }
    public void setBlock(Block block) { this.block = block; }
    
    public PortfolioAssignmentType getAssignmentType() { return assignmentType; }
    public void setAssignmentType(PortfolioAssignmentType assignmentType) { this.assignmentType = assignmentType; }
    
    public Long getAssignedBy() { return assignedBy; }
    public void setAssignedBy(Long assignedBy) { this.assignedBy = assignedBy; }
    
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    
    public SyncStatus getSyncStatus() { return syncStatus; }
    public void setSyncStatus(SyncStatus syncStatus) { this.syncStatus = syncStatus; }
    
    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    
    // Business Logic Methods
    public boolean isActive() {
        return isActive != null && isActive;
    }
    
    public boolean isPrimaryAssignment() {
        return assignmentType == PortfolioAssignmentType.PRIMARY;
    }
    
    public boolean isSecondaryAssignment() {
        return assignmentType == PortfolioAssignmentType.SECONDARY;
    }
    
    public boolean isTagAssignment() {
        return assignmentType == PortfolioAssignmentType.TAG;
    }
    
    public boolean needsSync() {
        return syncStatus == SyncStatus.pending || syncStatus == SyncStatus.failed;
    }
    
    public boolean isBlockAssignment() {
        return block != null;
    }
    
    public boolean isPortfolioOnlyAssignment() {
        return block == null;
    }
    
    /**
     * Generate the expected PayProp tag name for this assignment
     * Format: PF-{PORTFOLIO} or PF-{PORTFOLIO}-BL-{BLOCK}
     */
    public String getExpectedPayPropTagName() {
        if (portfolio == null) return null;
        
        String portfolioTag = "PF-" + portfolio.getName().toUpperCase()
            .replaceAll("[^A-Z0-9]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
            
        if (block != null) {
            String blockTag = block.getName().toUpperCase()
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
            return portfolioTag + "-BL-" + blockTag;
        }
        
        return portfolioTag;
    }
    
    /**
     * Determine if this assignment should sync to PayProp
     * Hierarchical logic: Portfolio must have tags, and if block exists, block must have tags
     */
    public boolean shouldSyncToPayProp() {
        if (!isActive() || portfolio == null) {
            return false;
        }
        
        // Portfolio must have PayProp external IDs
        boolean portfolioHasTags = portfolio.getPayPropTags() != null && 
                                  !portfolio.getPayPropTags().trim().isEmpty();
        
        if (!portfolioHasTags) {
            return false;
        }
        
        // If assigned to a block, block must also have PayProp external IDs
        if (block != null) {
            boolean blockHasTags = block.getPayPropTags() != null && 
                                  !block.getPayPropTags().trim().isEmpty();
            return blockHasTags;
        }
        
        // Portfolio-only assignment is valid if portfolio has tags
        return true;
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
        }
        if (isActive == null) {
            isActive = true;
        }
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