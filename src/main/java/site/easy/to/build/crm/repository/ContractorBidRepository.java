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
    List<ContractorBid> findByTicketTicketId(Integer ticketId);
    List<ContractorBid> findByContractorCustomerId(Integer contractorId);
    Optional<ContractorBid> findByTicketAndContractor(Ticket ticket, Customer contractor);
    Optional<ContractorBid> findByTicketTicketIdAndContractorCustomerId(Integer ticketId, Integer contractorId);
    
    // ===== STATUS-BASED QUERIES =====
    List<ContractorBid> findByStatus(String status);
    List<ContractorBid> findByTicketTicketIdAndStatus(Integer ticketId, String status);
    
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.ticket.ticketId = :ticketId " +
           "AND cb.status IN ('submitted', 'under-review', 'accepted', 'rejected')")
    List<ContractorBid> findSubmittedBidsForTicket(@Param("ticketId") Integer ticketId);
    
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.status IN ('invited', 'submitted', 'under-review')")
    List<ContractorBid> findPendingBids();
    
    // ===== ANALYTICS QUERIES =====
    long countByTicketTicketId(Integer ticketId);
    long countByContractorCustomerId(Integer contractorId);
    
    @Query("SELECT AVG(cb.bidAmount) FROM ContractorBid cb WHERE cb.ticket.ticketId = :ticketId " +
           "AND cb.status IN ('submitted', 'under-review')")
    BigDecimal getAverageBidAmountForTicket(@Param("ticketId") Integer ticketId);
    
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.ticket.ticketId = :ticketId " +
           "AND cb.status IN ('submitted', 'under-review') ORDER BY cb.bidAmount ASC")
    List<ContractorBid> findLowestBidsForTicket(@Param("ticketId") Integer ticketId, Pageable pageable);
    
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.ticket.ticketId = :ticketId " +
           "AND cb.status IN ('submitted', 'under-review') ORDER BY cb.bidAmount DESC")
    List<ContractorBid> findHighestBidsForTicket(@Param("ticketId") Integer ticketId, Pageable pageable);
    
    // ===== CONTRACTOR PERFORMANCE =====
    @Query("SELECT " +
           "COUNT(CASE WHEN cb.status = 'accepted' THEN 1 END) as wonBids, " +
           "COUNT(cb) as totalBids " +
           "FROM ContractorBid cb WHERE cb.contractor.customerId = :contractorId")
    Object[] getContractorBidWinRate(@Param("contractorId") Integer contractorId);
    
    @Query("SELECT AVG(cb.bidAmount) FROM ContractorBid cb " +
           "WHERE cb.contractor.customerId = :contractorId AND cb.status IN ('submitted', 'under-review', 'accepted')")
    BigDecimal getContractorAverageBidAmount(@Param("contractorId") Integer contractorId);
    
    List<ContractorBid> findByContractorCustomerIdOrderByCreatedAtDesc(Integer contractorId, Pageable pageable);
    
    // ===== TIME-BASED QUERIES =====
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.status = 'invited' AND cb.invitedAt < :deadlineDate")
    List<ContractorBid> findOverdueBidResponses(@Param("deadlineDate") LocalDateTime deadlineDate);
    
    @Query("SELECT cb FROM ContractorBid cb WHERE cb.status = 'invited' " +
           "AND cb.invitedAt < :followUpDate ORDER BY cb.invitedAt ASC")
    List<ContractorBid> findBidsNeedingFollowUp(@Param("followUpDate") LocalDateTime followUpDate);
    
    // ===== CLEANUP METHODS =====
    void deleteByTicketTicketId(Integer ticketId);
    void deleteByContractorCustomerId(Integer contractorId);
}