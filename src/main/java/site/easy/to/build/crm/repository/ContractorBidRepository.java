package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.ContractorBid;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Ticket;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractorBidRepository extends JpaRepository<ContractorBid, Long> {
    
    // ===== BASIC QUERIES =====
    
    /**
     * Find all bids for a specific ticket
     */
    List<ContractorBid> findByTicket(Ticket ticket);
    
    /**
     * Find all bids for a specific ticket ID
     */
    List<ContractorBid> findByTicketTicketId(Integer ticketId);
    
    /**
     * Find all bids by a specific contractor
     */
    List<ContractorBid> findByContractor(Customer contractor);
    
    /**
     * Find all bids by a specific contractor ID
     */
    List<ContractorBid> findByContractorCustomerId(Integer contractorId);
    
    /**
     * Find specific bid by ticket and contractor
     */
    Optional<ContractorBid> findByTicketAndContractor(Ticket ticket, Customer contractor);
    
    /**
     * Find specific bid by ticket ID and contractor ID
     */
    Optional<ContractorBid> findByTicketTicketIdAndContractorCustomerId(Integer ticketId, Integer contractorId);

    // ===== STATUS-BASED QUERIES =====
    
    /**
     * Find bids by status
     */
    List<ContractorBid> findByStatus(String status);
    
    /**
     * Find bids by status for specific ticket
     */
    List<ContractorBid> findByTicketTicketIdAndStatus(Integer ticketId, String status);
    
    /**
     * Find submitted bids for a ticket (excluding invited)
     */
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.ticket.ticketId = :ticketId " +
           "AND cb.status IN ('submitted', 'under-review', 'accepted', 'rejected')")
    List<ContractorBid> findSubmittedBidsForTicket(@Param("ticketId") Integer ticketId);
    
    /**
     * Find pending bids (invited or submitted)
     */
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.status IN ('invited', 'submitted', 'under-review')")
    List<ContractorBid> findPendingBids();
    
    /**
     * Find accepted bids
     */
    List<ContractorBid> findByStatus(String status, Pageable pageable);
    
    /**
     * Find accepted bid for a ticket
     */
    Optional<ContractorBid> findByTicketTicketIdAndStatus(Integer ticketId, String status);

    // ===== TIME-BASED QUERIES =====
    
    /**
     * Find bids submitted within date range
     */
    List<ContractorBid> findBySubmittedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find bids invited within date range
     */
    List<ContractorBid> findByInvitedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find overdue bid responses (invited but not submitted within timeframe)
     */
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.status = 'invited' " +
           "AND cb.invitedAt < :deadlineDate")
    List<ContractorBid> findOverdueBidResponses(@Param("deadlineDate") LocalDateTime deadlineDate);
    
    /**
     * Find recent bids for contractor
     */
    List<ContractorBid> findByContractorCustomerIdOrderByCreatedAtDesc(Integer contractorId, Pageable pageable);

    // ===== AMOUNT-BASED QUERIES =====
    
    /**
     * Find bids within amount range for a ticket
     */
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.ticket.ticketId = :ticketId " +
           "AND cb.bidAmount BETWEEN :minAmount AND :maxAmount")
    List<ContractorBid> findBidsInAmountRange(@Param("ticketId") Integer ticketId,
                                             @Param("minAmount") BigDecimal minAmount,
                                             @Param("maxAmount") BigDecimal maxAmount);
    
    /**
     * Find lowest bid for a ticket
     */
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.ticket.ticketId = :ticketId " +
           "AND cb.status IN ('submitted', 'under-review') " +
           "ORDER BY cb.bidAmount ASC")
    List<ContractorBid> findLowestBidsForTicket(@Param("ticketId") Integer ticketId, Pageable pageable);
    
    /**
     * Find highest bid for a ticket
     */
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.ticket.ticketId = :ticketId " +
           "AND cb.status IN ('submitted', 'under-review') " +
           "ORDER BY cb.bidAmount DESC")
    List<ContractorBid> findHighestBidsForTicket(@Param("ticketId") Integer ticketId, Pageable pageable);

    // ===== ANALYTICS QUERIES =====
    
    /**
     * Count bids by status
     */
    long countByStatus(String status);
    
    /**
     * Count bids for a ticket
     */
    long countByTicketTicketId(Integer ticketId);
    
    /**
     * Count bids by contractor
     */
    long countByContractorCustomerId(Integer contractorId);
    
    /**
     * Get average bid amount for a ticket
     */
    @Query("SELECT AVG(cb.bidAmount) FROM ContractorBid cb WHERE cb.ticket.ticketId = :ticketId " +
           "AND cb.status IN ('submitted', 'under-review')")
    BigDecimal getAverageBidAmountForTicket(@Param("ticketId") Integer ticketId);
    
    /**
     * Count bids by contractor and status
     */
    long countByContractorCustomerIdAndStatus(Integer contractorId, String status);

    // ===== CONTRACTOR PERFORMANCE QUERIES =====
    
    /**
     * Find contractor's bid win rate
     */
    @Query("SELECT " +
           "COUNT(CASE WHEN cb.status = 'accepted' THEN 1 END) as wonBids, " +
           "COUNT(cb) as totalBids " +
           "FROM ContractorBid cb WHERE cb.contractor.customerId = :contractorId")
    Object[] getContractorBidWinRate(@Param("contractorId") Integer contractorId);
    
    /**
     * Find contractor's average bid amount
     */
    @Query("SELECT AVG(cb.bidAmount) FROM ContractorBid cb " +
           "WHERE cb.contractor.customerId = :contractorId AND cb.status IN ('submitted', 'under-review', 'accepted')")
    BigDecimal getContractorAverageBidAmount(@Param("contractorId") Integer contractorId);
    
    /**
     * Find contractor's recent performance
     */
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.contractor.customerId = :contractorId " +
           "AND cb.status IN ('accepted', 'rejected') " +
           "ORDER BY cb.reviewedAt DESC")
    List<ContractorBid> getContractorRecentPerformance(@Param("contractorId") Integer contractorId, Pageable pageable);

    // ===== COMPLEX SEARCH QUERIES =====
    
    /**
     * Search bids with multiple criteria
     */
    @Query("SELECT cb FROM ContractorBid cb WHERE " +
           "(:ticketId IS NULL OR cb.ticket.ticketId = :ticketId) AND " +
           "(:contractorId IS NULL OR cb.contractor.customerId = :contractorId) AND " +
           "(:status IS NULL OR cb.status = :status) AND " +
           "(:minAmount IS NULL OR cb.bidAmount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR cb.bidAmount <= :maxAmount) AND " +
           "(:startDate IS NULL OR cb.submittedAt >= :startDate) AND " +
           "(:endDate IS NULL OR cb.submittedAt <= :endDate)")
    List<ContractorBid> searchBids(@Param("ticketId") Integer ticketId,
                                  @Param("contractorId") Integer contractorId,
                                  @Param("status") String status,
                                  @Param("minAmount") BigDecimal minAmount,
                                  @Param("maxAmount") BigDecimal maxAmount,
                                  @Param("startDate") LocalDateTime startDate,
                                  @Param("endDate") LocalDateTime endDate,
                                  Pageable pageable);

    // ===== SPECIALIZED QUERIES =====
    
    /**
     * Find bids that need follow-up (invited over X days ago with no response)
     */
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.status = 'invited' " +
           "AND cb.invitedAt < :followUpDate " +
           "ORDER BY cb.invitedAt ASC")
    List<ContractorBid> findBidsNeedingFollowUp(@Param("followUpDate") LocalDateTime followUpDate);
    
    /**
     * Find emergency bids (for urgent tickets)
     */
    @Query("SELECT cb FROM ContractorBid cb WHERE " +
           "cb.ticket.priority IN ('urgent', 'critical', 'emergency') " +
           "AND cb.status IN ('invited', 'submitted', 'under-review')")
    List<ContractorBid> findEmergencyBids();
    
    /**
     * Find bids for maintenance tickets only
     */
    @Query("SELECT cb FROM ContractorBid cb WHERE " +
           "cb.ticket.type IN ('maintenance', 'emergency')")
    List<ContractorBid> findMaintenanceBids();
    
    /**
     * Find competitive bids (tickets with multiple bids)
     */
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.ticket.ticketId IN " +
           "(SELECT cb2.ticket.ticketId FROM ContractorBid cb2 " +
           "WHERE cb2.status IN ('submitted', 'under-review') " +
           "GROUP BY cb2.ticket.ticketId HAVING COUNT(cb2) > 1)")
    List<ContractorBid> findCompetitiveBids();
    
    /**
     * Find contractor's speciality bids (by maintenance category)
     */
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.contractor.customerId = :contractorId " +
           "AND cb.ticket.maintenanceCategory = :category")
    List<ContractorBid> findContractorBidsByCategory(@Param("contractorId") Integer contractorId,
                                                   @Param("category") String category);

    // ===== REPORTING QUERIES =====
    
    /**
     * Get bid statistics by month
     */
    @Query("SELECT YEAR(cb.submittedAt), MONTH(cb.submittedAt), COUNT(cb), AVG(cb.bidAmount) " +
           "FROM ContractorBid cb WHERE cb.submittedAt IS NOT NULL " +
           "GROUP BY YEAR(cb.submittedAt), MONTH(cb.submittedAt) " +
           "ORDER BY YEAR(cb.submittedAt) DESC, MONTH(cb.submittedAt) DESC")
    List<Object[]> getBidStatisticsByMonth();
    
    /**
     * Get top performing contractors by acceptance rate
     */
    @Query("SELECT cb.contractor.customerId, cb.contractor.name, " +
           "COUNT(CASE WHEN cb.status = 'accepted' THEN 1 END) * 100.0 / COUNT(cb) as acceptanceRate, " +
           "COUNT(cb) as totalBids " +
           "FROM ContractorBid cb " +
           "WHERE cb.status IN ('accepted', 'rejected') " +
           "GROUP BY cb.contractor.customerId, cb.contractor.name " +
           "HAVING COUNT(cb) >= :minBids " +
           "ORDER BY acceptanceRate DESC")
    List<Object[]> getTopPerformingContractors(@Param("minBids") long minBids, Pageable pageable);
    
    /**
     * Delete all bids for a specific ticket (cleanup)
     */
    void deleteByTicketTicketId(Integer ticketId);
    
    /**
     * Delete all bids for a specific contractor (cleanup)
     */
    void deleteByContractorCustomerId(Integer contractorId);
}