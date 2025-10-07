// PropertyBlockAssignment.java - Junction table for Property-Block assignments (independent of Portfolio)
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents the assignment of a Property to a Block.
 * This relationship is INDEPENDENT of portfolio assignments.
 *
 * Key Features:
 * - Property can be in a block WITHOUT being in a portfolio
 * - Property-block relationship persists when block moves between portfolios
 * - Enables block-level organization independent of ownership structures
 * - Properties can be assigned to blocks before portfolio assignment
 *
 * Use Cases:
 * - New building: Assign all apartments to block before assigning to owner portfolios
 * - Block transfer: Move block to new portfolio, property-block relationships persist
 * - Mixed ownership: Block in management portfolio, apartments in various owner portfolios
 */
@Entity
@Table(name = "property_block_assignments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"property_id", "block_id"}))
public class PropertyBlockAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id", nullable = false)
    private Block block;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "notes", length = 500)
    private String notes;

    // Audit fields
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    // Constructors
    public PropertyBlockAssignment() {}

    public PropertyBlockAssignment(Property property, Block block, Long assignedBy) {
        this.property = property;
        this.block = block;
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

    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }

    public Block getBlock() { return block; }
    public void setBlock(Block block) { this.block = block; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public Long getAssignedBy() { return assignedBy; }
    public void setAssignedBy(Long assignedBy) { this.assignedBy = assignedBy; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    @Override
    public String toString() {
        return String.format("PropertyBlockAssignment[id=%d, property=%s, block=%s, active=%s]",
                id,
                property != null ? property.getPropertyName() : "null",
                block != null ? block.getName() : "null",
                isActive);
    }
}
