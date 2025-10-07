// BlockPortfolioAssignment.java - Junction table for Block-Portfolio many-to-many relationships
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents the assignment of a Block to a Portfolio.
 * Enables blocks to move between portfolios and exist in multiple portfolios.
 *
 * Key Features:
 * - Blocks can be in 0, 1, or multiple portfolios
 * - Assignment types: PRIMARY (main portfolio) or SHARED (additional portfolios)
 * - Full audit trail of block movements
 * - Blocks can exist independently (not assigned to any portfolio)
 */
@Entity
@Table(name = "block_portfolio_assignments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"block_id", "portfolio_id", "assignment_type"}))
public class BlockPortfolioAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id", nullable = false)
    private Block block;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false)
    private PortfolioAssignmentType assignmentType = PortfolioAssignmentType.PRIMARY;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    // Audit fields
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    // Constructors
    public BlockPortfolioAssignment() {}

    public BlockPortfolioAssignment(Block block, Portfolio portfolio,
                                   PortfolioAssignmentType assignmentType, Long assignedBy) {
        this.block = block;
        this.portfolio = portfolio;
        this.assignmentType = assignmentType;
        this.assignedBy = assignedBy;
        this.assignedAt = LocalDateTime.now();
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Lifecycle callbacks
    @PrePersist
    public void prePersist() {
        if (this.assignedAt == null) {
            this.assignedAt = LocalDateTime.now();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Block getBlock() { return block; }
    public void setBlock(Block block) { this.block = block; }

    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }

    public PortfolioAssignmentType getAssignmentType() { return assignmentType; }
    public void setAssignmentType(PortfolioAssignmentType assignmentType) { this.assignmentType = assignmentType; }

    public Long getAssignedBy() { return assignedBy; }
    public void setAssignedBy(Long assignedBy) { this.assignedBy = assignedBy; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    @Override
    public String toString() {
        return String.format("BlockPortfolioAssignment[id=%d, block=%s, portfolio=%s, type=%s, active=%s]",
                id,
                block != null ? block.getName() : "null",
                portfolio != null ? portfolio.getName() : "null",
                assignmentType,
                isActive);
    }
}
