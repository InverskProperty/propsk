package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Staging table for multi-paste batch accumulation of transaction imports
 * Holds parsed transactions for review before committing to historical_transactions
 */
@Entity
@Table(name = "transaction_import_staging")
public class TransactionImportStaging {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, length = 100)
    private String batchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_source_id")
    private PaymentSource paymentSource;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "csv_line", columnDefinition = "TEXT")
    private String csvLine;

    // Parsed transaction fields
    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "transaction_type", length = 50)
    private String transactionType;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "bank_reference", length = 255)
    private String bankReference;

    @Column(name = "payment_method", length = 100)
    private String paymentMethod;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // Matched entities
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id")
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    // Review status
    @Column(name = "status", length = 50)
    private String status; // PENDING_REVIEW, APPROVED, REJECTED, AMBIGUOUS_PROPERTY, AMBIGUOUS_CUSTOMER, DUPLICATE

    @Column(name = "is_duplicate")
    private Boolean isDuplicate = false;

    @Column(name = "duplicate_of_transaction_id")
    private Long duplicateOfTransactionId;

    @Column(name = "user_note", columnDefinition = "TEXT")
    private String userNote;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Constructors
    public TransactionImportStaging() {
        this.createdAt = LocalDateTime.now();
        this.isDuplicate = false;
        this.status = "PENDING_REVIEW";
    }

    public TransactionImportStaging(String batchId, PaymentSource paymentSource) {
        this();
        this.batchId = batchId;
        this.paymentSource = paymentSource;
    }

    // Lifecycle callbacks
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.isDuplicate == null) {
            this.isDuplicate = false;
        }
        if (this.status == null) {
            this.status = "PENDING_REVIEW";
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public PaymentSource getPaymentSource() {
        return paymentSource;
    }

    public void setPaymentSource(PaymentSource paymentSource) {
        this.paymentSource = paymentSource;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getCsvLine() {
        return csvLine;
    }

    public void setCsvLine(String csvLine) {
        this.csvLine = csvLine;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBankReference() {
        return bankReference;
    }

    public void setBankReference(String bankReference) {
        this.bankReference = bankReference;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Property getProperty() {
        return property;
    }

    public void setProperty(Property property) {
        this.property = property;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsDuplicate() {
        return isDuplicate;
    }

    public void setIsDuplicate(Boolean isDuplicate) {
        this.isDuplicate = isDuplicate;
    }

    public Long getDuplicateOfTransactionId() {
        return duplicateOfTransactionId;
    }

    public void setDuplicateOfTransactionId(Long duplicateOfTransactionId) {
        this.duplicateOfTransactionId = duplicateOfTransactionId;
    }

    public String getUserNote() {
        return userNote;
    }

    public void setUserNote(String userNote) {
        this.userNote = userNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
