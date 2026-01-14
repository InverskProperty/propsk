package site.easy.to.build.crm.dto.expense;

import site.easy.to.build.crm.entity.ExpenseDocument;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for displaying expense documents in the portal.
 * Combines expense document metadata with transaction details.
 */
public class ExpenseDocumentDTO {

    // ===== DOCUMENT INFO =====

    private Long documentId;
    private String documentType;
    private String documentStatus;
    private String documentNumber;
    private String documentDescription;
    private String vendorName;

    // ===== DOWNLOAD INFO =====

    private boolean hasGeneratedInvoice;
    private boolean hasReceipt;
    private String generatedInvoicePath;
    private Integer googleDriveFileId;
    private String driveFileId;

    // ===== TRANSACTION INFO =====

    private Long transactionId;
    private LocalDate transactionDate;
    private String transactionDescription;
    private String transactionCategory;
    private BigDecimal amount;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount;

    // ===== PROPERTY INFO =====

    private Long propertyId;
    private String propertyName;

    // ===== METADATA =====

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ===== CONSTRUCTORS =====

    public ExpenseDocumentDTO() {}

    /**
     * Create from ExpenseDocument entity
     */
    public static ExpenseDocumentDTO fromEntity(ExpenseDocument entity) {
        ExpenseDocumentDTO dto = new ExpenseDocumentDTO();
        dto.setDocumentId(entity.getId());
        dto.setDocumentType(entity.getDocumentType() != null ? entity.getDocumentType().name() : null);
        dto.setDocumentStatus(entity.getStatus() != null ? entity.getStatus().name() : null);
        dto.setDocumentNumber(entity.getDocumentNumber());
        dto.setDocumentDescription(entity.getDocumentDescription());
        dto.setVendorName(entity.getVendorName());
        dto.setHasGeneratedInvoice(entity.isGeneratedDocument());
        dto.setHasReceipt(entity.isUploadedDocument());
        dto.setGeneratedInvoicePath(entity.getGeneratedDocumentPath());
        dto.setGoogleDriveFileId(entity.getGoogleDriveFileId());
        dto.setDriveFileId(entity.getGeneratedDriveFileId());
        dto.setTransactionId(entity.getUnifiedTransactionId());
        dto.setPropertyId(entity.getPropertyId());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    // ===== UTILITY METHODS =====

    /**
     * Check if this expense has any downloadable documents
     */
    public boolean hasDocuments() {
        return hasGeneratedInvoice || hasReceipt;
    }

    /**
     * Get display type (for UI)
     */
    public String getDisplayType() {
        if (documentType == null) return "Document";
        switch (documentType) {
            case "INVOICE": return "Expense Invoice";
            case "RECEIPT": return "Receipt";
            case "QUOTE": return "Quote";
            case "CREDIT_NOTE": return "Credit Note";
            case "VENDOR_INVOICE": return "Vendor Invoice";
            default: return "Document";
        }
    }

    /**
     * Get status badge class for UI
     */
    public String getStatusBadgeClass() {
        if (documentStatus == null) return "badge-secondary";
        switch (documentStatus) {
            case "AVAILABLE": return "badge-success";
            case "PENDING": return "badge-warning";
            case "ARCHIVED": return "badge-secondary";
            case "ERROR": return "badge-danger";
            default: return "badge-secondary";
        }
    }

    // ===== GETTERS AND SETTERS =====

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getDocumentStatus() { return documentStatus; }
    public void setDocumentStatus(String documentStatus) { this.documentStatus = documentStatus; }

    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }

    public String getDocumentDescription() { return documentDescription; }
    public void setDocumentDescription(String documentDescription) { this.documentDescription = documentDescription; }

    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }

    public boolean isHasGeneratedInvoice() { return hasGeneratedInvoice; }
    public void setHasGeneratedInvoice(boolean hasGeneratedInvoice) { this.hasGeneratedInvoice = hasGeneratedInvoice; }

    public boolean isHasReceipt() { return hasReceipt; }
    public void setHasReceipt(boolean hasReceipt) { this.hasReceipt = hasReceipt; }

    public String getGeneratedInvoicePath() { return generatedInvoicePath; }
    public void setGeneratedInvoicePath(String generatedInvoicePath) { this.generatedInvoicePath = generatedInvoicePath; }

    public Integer getGoogleDriveFileId() { return googleDriveFileId; }
    public void setGoogleDriveFileId(Integer googleDriveFileId) { this.googleDriveFileId = googleDriveFileId; }

    public String getDriveFileId() { return driveFileId; }
    public void setDriveFileId(String driveFileId) { this.driveFileId = driveFileId; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public String getTransactionDescription() { return transactionDescription; }
    public void setTransactionDescription(String transactionDescription) { this.transactionDescription = transactionDescription; }

    public String getTransactionCategory() { return transactionCategory; }
    public void setTransactionCategory(String transactionCategory) { this.transactionCategory = transactionCategory; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getVatAmount() { return vatAmount; }
    public void setVatAmount(BigDecimal vatAmount) { this.vatAmount = vatAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }

    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
