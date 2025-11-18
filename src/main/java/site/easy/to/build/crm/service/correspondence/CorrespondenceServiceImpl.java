package site.easy.to.build.crm.service.correspondence;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.EmailCorrespondence;
import site.easy.to.build.crm.entity.EmailTemplate;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.enums.CorrespondenceStatus;
import site.easy.to.build.crm.repository.EmailCorrespondenceRepository;
import site.easy.to.build.crm.repository.EmailTemplateRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CorrespondenceServiceImpl implements CorrespondenceService {

    private static final Logger logger = LoggerFactory.getLogger(CorrespondenceServiceImpl.class);
    private static final Gson gson = new Gson();

    @Autowired
    private EmailCorrespondenceRepository correspondenceRepository;

    @Autowired
    private EmailTemplateRepository emailTemplateRepository;

    @Override
    public EmailCorrespondence logSentEmail(Customer customer, User sentBy, String subject, String body,
                                           List<String> allRecipients, List<String> attachmentFileIds,
                                           Long emailTemplateId, List<Long> documentTemplateIds,
                                           String gmailMessageId, String bulkSendBatchId) {

        logger.info("Logging sent email to customer {} ({})", customer.getCustomerId(), customer.getEmail());

        EmailCorrespondence correspondence = new EmailCorrespondence(customer, sentBy, subject, body);
        correspondence.setStatus(CorrespondenceStatus.SENT);
        correspondence.setSentDate(LocalDateTime.now());
        correspondence.setGmailMessageId(gmailMessageId);

        // Set JSON fields
        if (allRecipients != null && !allRecipients.isEmpty()) {
            correspondence.setRecipientsJson(gson.toJson(allRecipients));
        }

        if (attachmentFileIds != null && !attachmentFileIds.isEmpty()) {
            correspondence.setAttachmentUrlsJson(gson.toJson(attachmentFileIds));
            correspondence.setAttachmentCount(attachmentFileIds.size());
        }

        if (documentTemplateIds != null && !documentTemplateIds.isEmpty()) {
            correspondence.setDocumentTemplatesUsedJson(gson.toJson(documentTemplateIds));
        }

        // Set email template if provided
        if (emailTemplateId != null) {
            emailTemplateRepository.findById(emailTemplateId.intValue()).ifPresent(correspondence::setEmailTemplateUsed);
        }

        // Set bulk send info
        if (bulkSendBatchId != null && !bulkSendBatchId.isEmpty()) {
            correspondence.setIsBulkSend(true);
            correspondence.setBulkSendBatchId(bulkSendBatchId);
        }

        return correspondenceRepository.save(correspondence);
    }

    @Override
    public EmailCorrespondence logFailedEmail(Customer customer, User sentBy, String subject, String body,
                                             String errorMessage, String bulkSendBatchId) {

        logger.warn("Logging failed email to customer {} ({}): {}",
                   customer.getCustomerId(), customer.getEmail(), errorMessage);

        EmailCorrespondence correspondence = new EmailCorrespondence(customer, sentBy, subject, body);
        correspondence.setStatus(CorrespondenceStatus.FAILED);
        correspondence.setErrorMessage(errorMessage);

        if (bulkSendBatchId != null && !bulkSendBatchId.isEmpty()) {
            correspondence.setIsBulkSend(true);
            correspondence.setBulkSendBatchId(bulkSendBatchId);
        }

        return correspondenceRepository.save(correspondence);
    }

    @Override
    public List<EmailCorrespondence> getCustomerCorrespondence(Customer customer) {
        return correspondenceRepository.findByCustomerOrderBySentDateDesc(customer);
    }

    @Override
    public List<EmailCorrespondence> getCustomerCorrespondenceInDateRange(Customer customer,
                                                                          LocalDateTime startDate,
                                                                          LocalDateTime endDate) {
        return correspondenceRepository.findByCustomerAndDateRange(customer, startDate, endDate);
    }

    @Override
    public EmailCorrespondence getByGmailMessageId(String gmailMessageId) {
        return correspondenceRepository.findByGmailMessageId(gmailMessageId);
    }

    @Override
    public List<EmailCorrespondence> getBulkSendBatch(String bulkSendBatchId) {
        return correspondenceRepository.findByBulkSendBatchIdOrderBySentDateDesc(bulkSendBatchId);
    }

    @Override
    public long getCorrespondenceCount(Customer customer) {
        return correspondenceRepository.countByCustomer(customer);
    }

    @Override
    public long getFailedCount() {
        return correspondenceRepository.countByStatus(CorrespondenceStatus.FAILED);
    }

    @Override
    public List<EmailCorrespondence> searchCorrespondence(Customer customer, String searchTerm) {
        return correspondenceRepository.searchCorrespondence(customer, searchTerm);
    }

    @Override
    public List<EmailCorrespondence> getRecentCorrespondence(int days) {
        LocalDateTime sinceDate = LocalDateTime.now().minusDays(days);
        return correspondenceRepository.findRecentCorrespondence(sinceDate);
    }

    @Override
    public void updateStatus(Long correspondenceId, CorrespondenceStatus status) {
        correspondenceRepository.findById(correspondenceId).ifPresent(correspondence -> {
            correspondence.setStatus(status);
            correspondenceRepository.save(correspondence);
            logger.info("Updated correspondence {} status to {}", correspondenceId, status);
        });
    }

    @Override
    public String generateBulkSendBatchId() {
        return "BULK_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
