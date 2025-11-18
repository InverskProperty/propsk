package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import site.easy.to.build.crm.entity.enums.CustomerType;
import site.easy.to.build.crm.entity.enums.DocumentCategory;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a Google Docs template that can be used to generate personalized documents
 * for customers (e.g., Section 48 notices, tenancy agreements, etc.)
 */
@Entity
@Table(name = "document_templates")
public class DocumentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * The Google Docs file ID of the template document
     * This document should contain merge field placeholders like {{customer_name}}
     */
    @Column(name = "google_docs_template_id", nullable = false, length = 100)
    private String googleDocsTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private DocumentCategory category;

    /**
     * JSON array of available merge fields for this template
     * Example: ["customer_name", "property_address", "rent_amount"]
     */
    @Column(name = "merge_fields_json", columnDefinition = "TEXT")
    private String mergeFieldsJson;

    /**
     * Customer types this template is applicable to
     * Empty set means applicable to all types
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "document_template_customer_types",
                     joinColumns = @JoinColumn(name = "template_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type")
    private Set<CustomerType> applicableToCustomerTypes = new HashSet<>();

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    /**
     * Folder ID in Google Drive where generated documents should be stored
     * If null, documents will be stored in the customer's folder
     */
    @Column(name = "target_folder_id", length = 100)
    private String targetFolderId;

    /**
     * Whether to automatically convert generated documents to PDF
     */
    @Column(name = "auto_convert_to_pdf", nullable = false)
    private Boolean autoConvertToPdf = true;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public DocumentTemplate() {
    }

    public DocumentTemplate(String name, String googleDocsTemplateId, DocumentCategory category) {
        this.name = name;
        this.googleDocsTemplateId = googleDocsTemplateId;
        this.category = category;
    }

    // Getters and Setters
    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
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

    public String getGoogleDocsTemplateId() {
        return googleDocsTemplateId;
    }

    public void setGoogleDocsTemplateId(String googleDocsTemplateId) {
        this.googleDocsTemplateId = googleDocsTemplateId;
    }

    public DocumentCategory getCategory() {
        return category;
    }

    public void setCategory(DocumentCategory category) {
        this.category = category;
    }

    public String getMergeFieldsJson() {
        return mergeFieldsJson;
    }

    public void setMergeFieldsJson(String mergeFieldsJson) {
        this.mergeFieldsJson = mergeFieldsJson;
    }

    public Set<CustomerType> getApplicableToCustomerTypes() {
        return applicableToCustomerTypes;
    }

    public void setApplicableToCustomerTypes(Set<CustomerType> applicableToCustomerTypes) {
        this.applicableToCustomerTypes = applicableToCustomerTypes;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public String getTargetFolderId() {
        return targetFolderId;
    }

    public void setTargetFolderId(String targetFolderId) {
        this.targetFolderId = targetFolderId;
    }

    public Boolean getAutoConvertToPdf() {
        return autoConvertToPdf;
    }

    public void setAutoConvertToPdf(Boolean autoConvertToPdf) {
        this.autoConvertToPdf = autoConvertToPdf;
    }

    /**
     * Helper method to check if template is applicable to a customer type
     */
    public boolean isApplicableTo(CustomerType customerType) {
        return applicableToCustomerTypes.isEmpty() || applicableToCustomerTypes.contains(customerType);
    }

    @Override
    public String toString() {
        return "DocumentTemplate{" +
                "templateId=" + templateId +
                ", name='" + name + '\'' +
                ", category=" + category +
                ", active=" + active +
                '}';
    }
}
