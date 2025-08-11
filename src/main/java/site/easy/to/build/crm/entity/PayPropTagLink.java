package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payprop_tag_links")
public class PayPropTagLink {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;
    
    @Column(name = "tag_id", nullable = false, length = 32)
    private String tagId;
    
    @Column(name = "tag_name", nullable = false, length = 100)
    private String tagName;
    
    // REMOVED: tag_external_id field - column doesn't exist in database
    // If needed in future, add migration: ALTER TABLE payprop_tag_links ADD COLUMN tag_external_id VARCHAR(32);
    
    @Column(name = "sync_status", length = 20)
    private String syncStatus = "PENDING";
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
    
    // Constructors
    public PayPropTagLink() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public PayPropTagLink(Portfolio portfolio, String tagId, String tagName) {
        this();
        this.portfolio = portfolio;
        this.tagId = tagId;
        this.tagName = tagName;
    }
    
    // Lifecycle methods
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    // Helper methods
    public boolean isSynced() {
        return "SYNCED".equals(syncStatus);
    }
    
    public void markAsSynced() {
        this.syncStatus = "SYNCED";
        this.syncedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public void markAsPending() {
        this.syncStatus = "PENDING";
        this.updatedAt = LocalDateTime.now();
    }
    
    public void markAsFailed(String reason) {
        this.syncStatus = "FAILED";
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Portfolio getPortfolio() {
        return portfolio;
    }
    
    public void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }
    
    public String getTagId() {
        return tagId;
    }
    
    public void setTagId(String tagId) {
        this.tagId = tagId;
    }
    
    public String getTagName() {
        return tagName;
    }
    
    public void setTagName(String tagName) {
        this.tagName = tagName;
    }
    
    // REMOVED: getTagExternalId() and setTagExternalId() methods
    // Column doesn't exist in database schema
    
    public String getSyncStatus() {
        return syncStatus;
    }
    
    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public LocalDateTime getSyncedAt() {
        return syncedAt;
    }
    
    public void setSyncedAt(LocalDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }
    
    // toString, equals, hashCode
    @Override
    public String toString() {
        return "PayPropTagLink{" +
                "id=" + id +
                ", tagId='" + tagId + '\'' +
                ", tagName='" + tagName + '\'' +
                ", syncStatus='" + syncStatus + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PayPropTagLink)) return false;
        PayPropTagLink that = (PayPropTagLink) o;
        return id != null ? id.equals(that.id) : that.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}