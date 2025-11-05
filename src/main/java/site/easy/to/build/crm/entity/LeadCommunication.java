package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Entity for tracking all communications with property leads.
 * Captures emails, SMS, calls, and other interactions.
 */
@Entity
@Table(name = "lead_communications")
public class LeadCommunication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    @NotNull(message = "Lead is required")
    private Lead lead;

    @Column(name = "communication_type", nullable = false)
    @NotNull(message = "Communication type is required")
    private String communicationType; // EMAIL, SMS, CALL, WHATSAPP, IN_PERSON

    @Column(name = "direction", nullable = false)
    @NotNull(message = "Direction is required")
    private String direction; // INBOUND, OUTBOUND

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "message_content", columnDefinition = "TEXT")
    private String messageContent;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "clicked_at")
    private LocalDateTime clickedAt;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    @Column(name = "status")
    private String status; // SENT, DELIVERED, FAILED, BOUNCED, OPENED, REPLIED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_template_id")
    private EmailTemplate emailTemplate;

    @Column(name = "gmail_message_id")
    private String gmailMessageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Constructors
    public LeadCommunication() {
    }

    public LeadCommunication(Lead lead, String communicationType, String direction) {
        this.lead = lead;
        this.communicationType = communicationType;
        this.direction = direction;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Lead getLead() {
        return lead;
    }

    public void setLead(Lead lead) {
        this.lead = lead;
    }

    public String getCommunicationType() {
        return communicationType;
    }

    public void setCommunicationType(String communicationType) {
        this.communicationType = communicationType;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(LocalDateTime openedAt) {
        this.openedAt = openedAt;
    }

    public LocalDateTime getClickedAt() {
        return clickedAt;
    }

    public void setClickedAt(LocalDateTime clickedAt) {
        this.clickedAt = clickedAt;
    }

    public LocalDateTime getRepliedAt() {
        return repliedAt;
    }

    public void setRepliedAt(LocalDateTime repliedAt) {
        this.repliedAt = repliedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public EmailTemplate getEmailTemplate() {
        return emailTemplate;
    }

    public void setEmailTemplate(EmailTemplate emailTemplate) {
        this.emailTemplate = emailTemplate;
    }

    public String getGmailMessageId() {
        return gmailMessageId;
    }

    public void setGmailMessageId(String gmailMessageId) {
        this.gmailMessageId = gmailMessageId;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(User createdByUser) {
        this.createdByUser = createdByUser;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Helper methods

    /**
     * Check if this is an email communication
     */
    public boolean isEmail() {
        return "EMAIL".equalsIgnoreCase(communicationType);
    }

    /**
     * Check if this is outbound communication
     */
    public boolean isOutbound() {
        return "OUTBOUND".equalsIgnoreCase(direction);
    }

    /**
     * Check if email was opened
     */
    public boolean wasOpened() {
        return openedAt != null;
    }

    /**
     * Check if recipient replied
     */
    public boolean wasReplied() {
        return repliedAt != null;
    }

    /**
     * Mark as delivered
     */
    public void markAsDelivered() {
        this.deliveredAt = LocalDateTime.now();
        this.status = "DELIVERED";
    }

    /**
     * Mark as opened
     */
    public void markAsOpened() {
        this.openedAt = LocalDateTime.now();
        this.status = "OPENED";
    }

    /**
     * Mark as replied
     */
    public void markAsReplied() {
        this.repliedAt = LocalDateTime.now();
        this.status = "REPLIED";
    }

    /**
     * Mark as failed
     */
    public void markAsFailed() {
        this.status = "FAILED";
    }
}
