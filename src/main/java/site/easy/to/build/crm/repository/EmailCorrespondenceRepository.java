package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.EmailCorrespondence;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.enums.CorrespondenceStatus;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmailCorrespondenceRepository extends JpaRepository<EmailCorrespondence, Long> {

    /**
     * Find all correspondence for a specific customer, ordered by sent date descending
     */
    List<EmailCorrespondence> findByCustomerOrderBySentDateDesc(Customer customer);

    /**
     * Find correspondence with pagination
     */
    Page<EmailCorrespondence> findByCustomer(Customer customer, Pageable pageable);

    /**
     * Find correspondence sent by a specific user
     */
    List<EmailCorrespondence> findBySentByOrderBySentDateDesc(User sentBy);

    /**
     * Find correspondence by status
     */
    List<EmailCorrespondence> findByStatus(CorrespondenceStatus status);

    /**
     * Find correspondence by Gmail message ID
     */
    EmailCorrespondence findByGmailMessageId(String gmailMessageId);

    /**
     * Find correspondence in a date range for a customer
     */
    @Query("SELECT ec FROM EmailCorrespondence ec " +
           "WHERE ec.customer = :customer " +
           "AND ec.sentDate BETWEEN :startDate AND :endDate " +
           "ORDER BY ec.sentDate DESC")
    List<EmailCorrespondence> findByCustomerAndDateRange(
        @Param("customer") Customer customer,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find all correspondence in a bulk send batch
     */
    List<EmailCorrespondence> findByBulkSendBatchIdOrderBySentDateDesc(String bulkSendBatchId);

    /**
     * Count correspondence for a customer
     */
    long countByCustomer(Customer customer);

    /**
     * Count failed correspondence
     */
    long countByStatus(CorrespondenceStatus status);

    /**
     * Find recent correspondence (last N days)
     */
    @Query("SELECT ec FROM EmailCorrespondence ec " +
           "WHERE ec.sentDate >= :sinceDate " +
           "ORDER BY ec.sentDate DESC")
    List<EmailCorrespondence> findRecentCorrespondence(@Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Search correspondence by subject or body
     */
    @Query("SELECT ec FROM EmailCorrespondence ec " +
           "WHERE ec.customer = :customer " +
           "AND (LOWER(ec.subject) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(ec.body) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "ORDER BY ec.sentDate DESC")
    List<EmailCorrespondence> searchCorrespondence(
        @Param("customer") Customer customer,
        @Param("searchTerm") String searchTerm
    );
}
