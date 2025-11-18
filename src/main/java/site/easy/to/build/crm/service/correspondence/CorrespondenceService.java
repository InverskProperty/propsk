package site.easy.to.build.crm.service.correspondence;

import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.EmailCorrespondence;
import site.easy.to.build.crm.entity.EmailTemplate;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.enums.CorrespondenceStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing email correspondence history
 */
public interface CorrespondenceService {

    /**
     * Log an email that was sent
     *
     * @param customer Primary recipient
     * @param sentBy User who sent the email
     * @param subject Email subject
     * @param body Email body
     * @param allRecipients All email addresses the message was sent to
     * @param attachmentFileIds Google Drive file IDs of attachments
     * @param emailTemplateId Email template used (nullable)
     * @param documentTemplateIds Document templates used (nullable)
     * @param gmailMessageId Gmail API message ID
     * @param bulkSendBatchId Batch ID if part of bulk send
     * @return Created correspondence record
     */
    EmailCorrespondence logSentEmail(Customer customer, User sentBy, String subject, String body,
                                    List<String> allRecipients, List<String> attachmentFileIds,
                                    Long emailTemplateId, List<Long> documentTemplateIds,
                                    String gmailMessageId, String bulkSendBatchId);

    /**
     * Log a failed email
     */
    EmailCorrespondence logFailedEmail(Customer customer, User sentBy, String subject, String body,
                                      String errorMessage, String bulkSendBatchId);

    /**
     * Get all correspondence for a customer
     */
    List<EmailCorrespondence> getCustomerCorrespondence(Customer customer);

    /**
     * Get correspondence in date range for a customer
     */
    List<EmailCorrespondence> getCustomerCorrespondenceInDateRange(Customer customer,
                                                                   LocalDateTime startDate,
                                                                   LocalDateTime endDate);

    /**
     * Get correspondence by Gmail message ID
     */
    EmailCorrespondence getByGmailMessageId(String gmailMessageId);

    /**
     * Get all correspondence in a bulk send batch
     */
    List<EmailCorrespondence> getBulkSendBatch(String bulkSendBatchId);

    /**
     * Get correspondence count for a customer
     */
    long getCorrespondenceCount(Customer customer);

    /**
     * Get failed correspondence count
     */
    long getFailedCount();

    /**
     * Search correspondence for a customer
     */
    List<EmailCorrespondence> searchCorrespondence(Customer customer, String searchTerm);

    /**
     * Get recent correspondence (last N days)
     */
    List<EmailCorrespondence> getRecentCorrespondence(int days);

    /**
     * Update correspondence status
     */
    void updateStatus(Long correspondenceId, CorrespondenceStatus status);

    /**
     * Generate a unique bulk send batch ID
     */
    String generateBulkSendBatchId();
}
