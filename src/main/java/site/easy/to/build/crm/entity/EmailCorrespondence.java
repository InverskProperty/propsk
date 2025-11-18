package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import site.easy.to.build.crm.entity.enums.CorrespondenceStatus;

import java.time.LocalDateTime;

/**
 * Tracks all email correspondence sent through the system
 * Provides an audit trail for customer communications
 */
@Entity
@Table(name = "email_correspondence")
@org.hibernate.annotations.Index(name = "idx_customer_sent_date", columnList = "customer_id,sent_date DESC")
@org.hibernate.annotations.Index(name = "idx_sent_date", columnList = "sent_date DESC")
public class EmailCorrespondence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "correspondence_id")
    private Long correspondenceId;

    /**
     * Primary recipient customer (for filtering in customer history)
     * For bulk emails, this is one of the recipients
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /**
     * User who sent the email
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by_user_id", nullable = false)
    private User sentBy;

    @Column(name = "sent_date", nullable = false)
    private LocalDateTime sentDate;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    /**
     * Email body (may contain HTML)
     */
    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    private String body;

    /**
     * JSON array of all recipient email addresses
     * Example: ["tenant1@example.com", "tenant2@example.com"]
     */
    @Column(name = "recipients_json", columnDefinition = "TEXT")
    private String recipientsJson;

    /**
     * JSON array of Google Drive file IDs/URLs for attachments
     * Example: ["1AbC...xyz", "2DeF...uvw"]
     */
    @Column(name = "attachment_urls_json", columnDefinition = "TEXT")
    private String attachmentUrlsJson;

    /**
     * Email template used for the body (if any)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_template_id")
    private EmailTemplate emailTemplateUsed;

    /**
     * JSON array of document template IDs used to generate attachments
     * Example: [1, 3, 5]
     */
    @Column(name = "document_templates_used_json", columnDefinition = "TEXT")
    private String documentTemplatesUsedJson;

    /**
     * Gmail API message ID (for linking back to sent email)
     */
    @Column(name = "gmail_message_id", length = 100)
    private String gmailMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CorrespondenceStatus status;

    /**
     * Error message if send failed
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Number of attachments included
     */
    @Column(name = "attachment_count")
    private Integer attachmentCount = 0;

    /**
     * Whether this was part of a bulk send operation
     */
    @Column(name = "is_bulk_send")
    private Boolean isBulkSend = false;

    /**
     * If part of bulk send, this groups related correspondence
     */
    @Column(name = "bulk_send_batch_id", length = 100)
    private String bulkSendBatchId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (sentDate == null) {
            sentDate = LocalDateTime.now();
        }
        if (status == null) {
            status = CorrespondenceStatus.DRAFT;
        }
    }

    // Constructors
    public EmailCorrespondence() {
    }

    public EmailCorrespondence(Customer customer, User sentBy, String subject, String body) {
        this.customer = customer;
        this.sentBy = sentBy;
        this.subject = subject;
        this.body = body;
        this.status = CorrespondenceStatus.DRAFT;
    }

    // Getters and Setters
    public Long getCorrespondenceId() {
        return correspondenceId;
    }

    public void setCorrespondenceId(Long correspondenceId) {
        this.correspondenceId = correspondenceId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public User getSentBy() {
        return sentBy;
    }

    public void setSentBy(User sentBy) {
        this.sentBy = sentBy;
    }

    public LocalDateTime getSentDate() {
        return sentDate;
    }

    public void setSentDate(LocalDateTime sentDate) {
        this.sentDate = sentDate;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getRecipientsJson() {
        return recipientsJson;
    }

    public void setRecipientsJson(String recipientsJson) {
        this.recipientsJson = recipientsJson;
    }

    public String getAttachmentUrlsJson() {
        return attachmentUrlsJson;
    }

    public void setAttachmentUrlsJson(String attachmentUrlsJson) {
        this.attachmentUrlsJson = attachmentUrlsJson;
    }

    public EmailTemplate getEmailTemplateUsed() {
        return emailTemplateUsed;
    }

    public void setEmailTemplateUsed(EmailTemplate emailTemplateUsed) {
        this.emailTemplateUsed = emailTemplateUsed;
    }

    public String getDocumentTemplatesUsedJson() {
        return documentTemplatesUsedJson;
    }

    public void setDocumentTemplatesUsedJson(String documentTemplatesUsedJson) {
        this.documentTemplatesUsedJson = documentTemplatesUsedJson;
    }

    public String getGmailMessageId() {
        return gmailMessageId;
    }

    public void setGmailMessageId(String gmailMessageId) {
        this.gmailMessageId = gmailMessageId;
    }

    public CorrespondenceStatus getStatus() {
        return status;
    }

    public void setStatus(CorrespondenceStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getAttachmentCount() {
        return attachmentCount;
    }

    public void setAttachmentCount(Integer attachmentCount) {
        this.attachmentCount = attachmentCount;
    }

    public Boolean getIsBulkSend() {
        return isBulkSend;
    }

    public void setIsBulkSend(Boolean isBulkSend) {
        this.isBulkSend = isBulkSend;
    }

    public String getBulkSendBatchId() {
        return bulkSendBatchId;
    }

    public void setBulkSendBatchId(String bulkSendBatchId) {
        this.bulkSendBatchId = bulkSendBatchId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "EmailCorrespondence{" +
                "correspondenceId=" + correspondenceId +
                ", subject='" + subject + '\'' +
                ", sentDate=" + sentDate +
                ", status=" + status +
                '}';
    }
}
