package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.EmailTemplate;
import site.easy.to.build.crm.entity.Lead;
import site.easy.to.build.crm.entity.LeadCommunication;
import site.easy.to.build.crm.entity.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for LeadCommunication entity.
 * Handles communication tracking data access.
 */
@Repository
public interface LeadCommunicationRepository extends JpaRepository<LeadCommunication, Long> {

    /**
     * Find all communications for a specific lead (most recent first)
     */
    List<LeadCommunication> findByLeadOrderByCreatedAtDesc(Lead lead);

    /**
     * Find communications by lead and type
     */
    List<LeadCommunication> findByLeadAndCommunicationTypeOrderByCreatedAtDesc(Lead lead, String communicationType);

    /**
     * Find communications by lead and direction
     */
    List<LeadCommunication> findByLeadAndDirectionOrderByCreatedAtDesc(Lead lead, String direction);

    /**
     * Find communications by type
     */
    List<LeadCommunication> findByCommunicationTypeOrderByCreatedAtDesc(String communicationType);

    /**
     * Find communications by direction
     */
    List<LeadCommunication> findByDirectionOrderByCreatedAtDesc(String direction);

    /**
     * Find communications by status
     */
    List<LeadCommunication> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * Find communications sent between dates
     */
    @Query("SELECT lc FROM LeadCommunication lc WHERE lc.sentAt BETWEEN :startDate AND :endDate ORDER BY lc.sentAt DESC")
    List<LeadCommunication> findBySentAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find emails that were opened
     */
    @Query("SELECT lc FROM LeadCommunication lc WHERE lc.communicationType = 'EMAIL' " +
           "AND lc.openedAt IS NOT NULL ORDER BY lc.openedAt DESC")
    List<LeadCommunication> findOpenedEmails();

    /**
     * Find emails that were clicked
     */
    @Query("SELECT lc FROM LeadCommunication lc WHERE lc.communicationType = 'EMAIL' " +
           "AND lc.clickedAt IS NOT NULL ORDER BY lc.clickedAt DESC")
    List<LeadCommunication> findClickedEmails();

    /**
     * Find emails that received replies
     */
    @Query("SELECT lc FROM LeadCommunication lc WHERE lc.communicationType = 'EMAIL' " +
           "AND lc.repliedAt IS NOT NULL ORDER BY lc.repliedAt DESC")
    List<LeadCommunication> findRepliedEmails();

    /**
     * Find communications using a specific template
     */
    List<LeadCommunication> findByEmailTemplateOrderByCreatedAtDesc(EmailTemplate template);

    /**
     * Find communications by Gmail message ID
     */
    LeadCommunication findByGmailMessageId(String gmailMessageId);

    /**
     * Find communications created by a specific user
     */
    List<LeadCommunication> findByCreatedByUserOrderByCreatedAtDesc(User user);

    /**
     * Count communications for a lead
     */
    long countByLead(Lead lead);

    /**
     * Count communications by type for a lead
     */
    long countByLeadAndCommunicationType(Lead lead, String communicationType);

    /**
     * Get recent communications (last N days)
     */
    @Query("SELECT lc FROM LeadCommunication lc WHERE lc.createdAt >= :sinceDate ORDER BY lc.createdAt DESC")
    List<LeadCommunication> findRecentCommunications(@Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Find outbound communications that haven't been delivered
     */
    @Query("SELECT lc FROM LeadCommunication lc WHERE lc.direction = 'OUTBOUND' " +
           "AND lc.status IN ('SENT') AND lc.deliveredAt IS NULL " +
           "AND lc.sentAt < :cutoffTime ORDER BY lc.sentAt ASC")
    List<LeadCommunication> findUndeliveredOutbound(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find failed communications
     */
    @Query("SELECT lc FROM LeadCommunication lc WHERE lc.status IN ('FAILED', 'BOUNCED') ORDER BY lc.createdAt DESC")
    List<LeadCommunication> findFailedCommunications();

    /**
     * Calculate email open rate for a lead
     */
    @Query("SELECT CAST(COUNT(CASE WHEN lc.openedAt IS NOT NULL THEN 1 END) AS double) / COUNT(*) * 100 " +
           "FROM LeadCommunication lc WHERE lc.lead = :lead AND lc.communicationType = 'EMAIL' AND lc.direction = 'OUTBOUND'")
    Double calculateEmailOpenRateForLead(@Param("lead") Lead lead);

    /**
     * Find recent inbound communications (potential leads replying)
     */
    @Query("SELECT lc FROM LeadCommunication lc WHERE lc.direction = 'INBOUND' " +
           "AND lc.createdAt >= :sinceDate ORDER BY lc.createdAt DESC")
    List<LeadCommunication> findRecentInbound(@Param("sinceDate") LocalDateTime sinceDate);
}
