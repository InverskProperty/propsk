package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Payment Source - represents isolated transaction data sources (bank accounts, old systems, etc.)
 * Each payment source maintains its own transaction history with internal deduplication only
 */
@Entity
@Table(name = "payment_sources")
public class PaymentSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_type", length = 50)
    private String sourceType; // BANK_STATEMENT, OLD_ACCOUNT, PAYPROP, etc.

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "last_import_date")
    private LocalDateTime lastImportDate;

    @Column(name = "total_transactions")
    private Integer totalTransactions = 0;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Constructors
    public PaymentSource() {
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
        this.totalTransactions = 0;
    }

    public PaymentSource(String name, String sourceType, User createdBy) {
        this();
        this.name = name;
        this.sourceType = sourceType;
        this.createdBy = createdBy;
    }

    // Lifecycle callbacks
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
        if (this.totalTransactions == null) {
            this.totalTransactions = 0;
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getLastImportDate() {
        return lastImportDate;
    }

    public void setLastImportDate(LocalDateTime lastImportDate) {
        this.lastImportDate = lastImportDate;
    }

    public Integer getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(Integer totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    // Utility methods
    public void incrementTransactionCount() {
        if (this.totalTransactions == null) {
            this.totalTransactions = 0;
        }
        this.totalTransactions++;
    }

    public void updateLastImportDate() {
        this.lastImportDate = LocalDateTime.now();
    }
}
